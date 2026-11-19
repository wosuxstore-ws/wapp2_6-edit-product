package com.wl.orders;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Shrinks a camera or gallery picture on the phone before it is uploaded to YOUR server, so a
// 12 MP shot becomes a few hundred KB. Same steps as the Add People photo: skip pixels while
// decoding, fix the sideways rotation, make see-through PNGs white, then save as JPEG.
// Call it from a background thread.
public final class PhotoShrinker {

    private PhotoShrinker() {}

    public static final class Result {
        public final Bitmap bitmap; // for the preview on screen
        public final byte[] jpeg;   // what gets uploaded

        Result(Bitmap bitmap, byte[] jpeg) {
            this.bitmap = bitmap;
            this.jpeg = jpeg;
        }
    }

    // The longest side of the picture becomes at most maxSidePx.
    public static Result shrink(ContentResolver resolver, Uri uri, int maxSidePx, int jpegQuality)
            throws IOException {
        Bitmap bitmap = decodeScaled(resolver, uri, maxSidePx);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
        return new Result(bitmap, out.toByteArray());
    }

    private static Bitmap decodeScaled(ContentResolver resolver, Uri uri, int maxSidePx) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open the photo");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not a picture");

        // Cheap first step: let the decoder skip pixels (a 12 MP camera shot is never loaded in full).
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open the photo");
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) throw new IOException("Can't read the photo");

        // Exact size + the rotation flag (phones often store portrait shots sideways).
        int degrees = readRotation(resolver, uri);
        float scale = Math.min(1f, (float) maxSidePx / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (degrees != 0 || scale < 1f) {
            Matrix matrix = new Matrix();
            if (scale < 1f) matrix.postScale(scale, scale);
            if (degrees != 0) matrix.postRotate(degrees);
            Bitmap changed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (changed != bitmap) bitmap.recycle();
            bitmap = changed;
        }

        // A PNG with a see-through background would turn black as a JPEG: paint it white first.
        if (bitmap.hasAlpha()) {
            Bitmap flat = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flat);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = flat;
        }
        return bitmap;
    }

    private static int readRotation(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
