package com.wl.orders;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

// Shows a bank / wallet picture from YOUR server (public_html/api/banks_and_wallets/) with
// Volley's ImageLoader - no Glide/Picasso. Until the picture arrives (or if it can't be
// loaded) the first letters of the name are shown instead. Works with JPG, PNG and WebP.
public final class BankLogo {

    private BankLogo() {}

    // logo = the ImageView, fallback = the initials TextView under it (may be null).
    public static void load(ImageView logo, TextView fallback, String image, String name) {
        cancel(logo);
        logo.setImageDrawable(null);
        logo.setVisibility(View.GONE);
        if (fallback != null) {
            fallback.setText(initials(name));
            fallback.setVisibility(View.VISIBLE);
        }

        String url = ApiConfig.bankImageUrl(image);
        if (url.isEmpty()) return;

        ImageLoader loader = VolleySingleton.getInstance(logo.getContext().getApplicationContext())
                .getImageLoader();
        ImageLoader.ImageContainer container = loader.get(url, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                Bitmap bitmap = response.getBitmap();
                if (bitmap != null) { // null = the first "still loading" callback
                    logo.setImageBitmap(bitmap);
                    logo.setVisibility(View.VISIBLE);
                    if (fallback != null) fallback.setVisibility(View.GONE);
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                // Keep the initials. Common causes: wrong folder, file missing, 403, SVG file.
            }
        }, 160, 160);
        logo.setTag(container);
    }

    // Call when a list row is re-used, so the old row's picture doesn't land in the new one.
    public static void cancel(ImageView logo) {
        Object old = logo.getTag();
        if (old instanceof ImageLoader.ImageContainer) {
            ((ImageLoader.ImageContainer) old).cancelRequest();
        }
        logo.setTag(null);
    }

    // "Meezan Bank" -> "MB", "Easypaisa" -> "E"
    static String initials(String name) {
        if (name == null) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String s = parts[0].substring(0, 1);
        if (parts.length > 1 && !parts[1].isEmpty()) s += parts[1].substring(0, 1);
        return s.toUpperCase();
    }
}
