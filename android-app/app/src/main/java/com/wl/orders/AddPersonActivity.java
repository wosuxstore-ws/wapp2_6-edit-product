package com.wl.orders;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wl.orders.models.Role;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// "Add People": name, number, role (from the wpeople_type table) and an optional photo that
// is either taken with the phone's camera or picked from the gallery. Everything goes to
// YOUR server (add_person.php) - no Firebase or other service.
//
// The photo is shrunk on the phone first (max 800 px, JPEG) so the upload is only ~100-200 KB.
// No permission is needed: the camera app does the shooting, the gallery picker hands us
// just the one picture the user chose.
public class AddPersonActivity extends AppCompatActivity {

    private static final String STATE_CAMERA_FILE = "camera_file";
    private static final int MAX_SIDE_PX = 800;
    private static final int JPEG_QUALITY = 80;

    private PeopleManager peopleManager;
    private SessionManager sessionManager;

    private EditText nameInput;
    private EditText numberInput;
    private TextView roleField;
    private TextView saveButton;
    private ImageView photoPreview;
    private View photoPlaceholder;
    private TextView removePhoto;

    private final List<Role> roles = new ArrayList<>();
    private boolean rolesLoaded = false;
    private Role selectedRole;

    private byte[] photoJpeg;   // the shrunk photo ready to upload; null = no photo
    private File cameraFile;    // where the camera app is saving the picture right now
    private boolean saving = false;

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (cameraFile == null) return;
                if (success) {
                    loadPhoto(Uri.fromFile(cameraFile), cameraFile);
                } else {
                    cameraFile.delete(); // user backed out of the camera
                }
            });

    // CAMERA is declared in the manifest (QR scanner), so Android insists it is granted before
    // the phone's camera app is opened for a photo. Ask first, then open the camera.
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else toast("Camera permission is needed to take a photo");
            });

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadPhoto(uri, null);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_person);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        peopleManager = new PeopleManager(this, sessionManager.getToken());

        // The camera app may have been running while Android cleared us from memory.
        if (savedInstanceState != null) {
            String path = savedInstanceState.getString(STATE_CAMERA_FILE);
            if (path != null) cameraFile = new File(path);
        }

        nameInput = findViewById(R.id.personName);
        numberInput = findViewById(R.id.personNumber);
        roleField = findViewById(R.id.personRole);
        saveButton = findViewById(R.id.saveButton);
        photoPreview = findViewById(R.id.photoPreview);
        photoPlaceholder = findViewById(R.id.photoPlaceholder);
        removePhoto = findViewById(R.id.removePhoto);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnCamera).setOnClickListener(v -> onCameraClicked());
        findViewById(R.id.btnGallery).setOnClickListener(v -> pickImage.launch("image/*"));
        removePhoto.setOnClickListener(v -> setPhoto(null, null));
        roleField.setOnClickListener(v -> showRolePicker());
        saveButton.setOnClickListener(v -> save());

        loadRoles();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraFile != null) outState.putString(STATE_CAMERA_FILE, cameraFile.getAbsolutePath());
    }

    // ---------------------------------------------------------------- Photo

    private void onCameraClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    // Opens the phone's own camera app and asks it to save the picture in our cache folder.
    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "person_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                toast("Couldn't prepare the camera");
                return;
            }
            cameraFile = File.createTempFile("person_", ".jpg", dir);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraFile);
            takePicture.launch(uri);
        } catch (IOException e) {
            toast("Couldn't prepare the camera");
        } catch (ActivityNotFoundException e) {
            toast("No camera app found on this phone");
        }
    }

    // Shrinks the chosen picture on a background thread, then shows it.
    // deleteAfter = the temporary camera file (removed once we have our own copy), or null.
    private void loadPhoto(Uri uri, File deleteAfter) {
        new Thread(() -> {
            Bitmap bitmap = null;
            byte[] jpeg = null;
            try {
                bitmap = decodeScaled(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
                jpeg = out.toByteArray();
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                bitmap = null;
                jpeg = null;
            }
            if (deleteAfter != null) deleteAfter.delete();

            final Bitmap finalBitmap = bitmap;
            final byte[] finalJpeg = jpeg;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalBitmap == null) {
                    toast("Couldn't use that photo. Please try another one.");
                } else {
                    setPhoto(finalBitmap, finalJpeg);
                }
            });
        }).start();
    }

    // Reads the picture, scaled down so its longest side is at most MAX_SIDE_PX.
    private Bitmap decodeScaled(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open the photo");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not a picture");

        // Cheap first step: let the decoder skip pixels (a 12 MP camera shot is never loaded in full).
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open the photo");
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) throw new IOException("Can't read the photo");

        // Exact size + the rotation flag (phones often store portrait shots sideways).
        int degrees = readRotation(uri);
        float scale = Math.min(1f, (float) MAX_SIDE_PX / Math.max(bitmap.getWidth(), bitmap.getHeight()));
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

    private int readRotation(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
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

    // bitmap == null removes the photo.
    private void setPhoto(Bitmap bitmap, byte[] jpeg) {
        photoJpeg = jpeg;
        if (bitmap != null) {
            photoPreview.setImageBitmap(bitmap);
            photoPreview.setVisibility(View.VISIBLE);
            photoPlaceholder.setVisibility(View.GONE);
            removePhoto.setVisibility(View.VISIBLE);
        } else {
            photoPreview.setImageDrawable(null);
            photoPreview.setVisibility(View.GONE);
            photoPlaceholder.setVisibility(View.VISIBLE);
            removePhoto.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- Roles

    // Fills the "Select role" list from the wpeople_type table.
    private void loadRoles() {
        peopleManager.getRoles(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) {
                        toast(response.optString("message", "Couldn't load roles"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("roles");
                    roles.clear();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        Role role = new Role();
                        role.id = o.getInt("id");
                        role.name = Format.str(o, "wrole");
                        roles.add(role);
                    }
                    rolesLoaded = true;
                } catch (JSONException e) {
                    toast("Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                handleError(message);
            }
        });
    }

    private void showRolePicker() {
        if (!rolesLoaded) {
            toast("Loading roles…");
            loadRoles();
            return;
        }
        if (roles.isEmpty()) {
            toast("No roles yet. Go back and tap “Add Role” first.");
            return;
        }

        String[] names = new String[roles.size()];
        int checked = -1;
        for (int i = 0; i < roles.size(); i++) {
            names[i] = roles.get(i).name;
            if (selectedRole != null && selectedRole.id == roles.get(i).id) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select role")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    selectedRole = roles.get(which);
                    roleField.setText(selectedRole.name);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- Save

    private void save() {
        if (saving) return;

        String name = nameInput.getText().toString().trim();
        String number = numberInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Enter a name");
            nameInput.requestFocus();
            return;
        }
        if (!number.matches("[0-9+\\-\\s()]{5,20}")) {
            numberInput.setError("Enter a valid number");
            numberInput.requestFocus();
            return;
        }
        if (selectedRole == null) {
            toast("Please select a role");
            return;
        }

        setSaving(true);
        String image = photoJpeg == null ? "" : Base64.encodeToString(photoJpeg, Base64.NO_WRAP);
        peopleManager.addPerson(name, number, selectedRole.id, image, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                setSaving(false);
                if (response.optBoolean("success", false)) {
                    toast("Person added");
                    setResult(RESULT_OK); // the People list reloads itself
                    finish();
                } else {
                    toast(response.optString("message", "Couldn't save the person"));
                }
            }

            @Override
            public void onError(String message) {
                setSaving(false);
                handleError(message);
            }
        });
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setEnabled(!value);
        saveButton.setText(value ? "Saving…" : "Save person");
    }

    // ---------------------------------------------------------------- Helpers

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void handleError(String message) {
        if (OrdersManager.SESSION_EXPIRED.equals(message)) {
            sessionManager.clear();
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            goToLogin();
            return;
        }
        Toast.makeText(this, "Couldn't reach server: " + message, Toast.LENGTH_LONG).show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
