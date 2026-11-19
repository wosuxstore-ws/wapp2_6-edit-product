package com.wl.orders;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;

// "Edit Product": opened by the pencil icon on a Home product tile. Shows the product photo on
// top, then loads the product from get_product.php and saves title, original price, discounted
// price and delivery days back into the `products` table through update_product.php.
// Nothing but your own server is used.
public class EditProductActivity extends AppCompatActivity {

    // In: which product to edit, and what the photo area should show (all given by Home).
    public static final String EXTRA_PRODUCT_ID = "product_id";
    public static final String EXTRA_PRODUCT_NAME = "product_name";
    public static final String EXTRA_IMAGE_URL = "image_url"; // "" = the product has no photo
    public static final String EXTRA_ICON = "icon";           // fallback icon (drawable id)
    public static final String EXTRA_BG = "bg";               // fallback pastel (color id)
    public static final String EXTRA_FG = "fg";               // fallback icon tint (color id)
    // Out (setResult RESULT_OK): what Home needs to refresh the tile.
    public static final String RESULT_PRODUCT_ID = "product_id";
    public static final String RESULT_TITLE = "title";
    public static final String RESULT_PRICE = "price";

    private static final String AMOUNT_PATTERN = "\\d{1,9}(\\.\\d{1,2})?";

    private CardView heroCard;
    private ImageView heroIcon;
    private ImageView heroPhoto;
    private EditText titleInput;
    private EditText originalInput;
    private EditText discountInput;
    private EditText daysInput;
    private TextView priceFooter;
    private TextView saveButton;
    private TextView loadMessage;
    private View formGroup;

    private OrdersManager ordersManager;
    private SessionManager sessionManager;

    private ImageLoader.ImageContainer heroSmallRequest;
    private ImageLoader.ImageContainer heroLargeRequest;
    private boolean heroLargeShown = false;

    private int productId;
    private boolean daysIsNumber = true;
    private boolean loaded = false;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, 0);
        if (productId <= 0) {
            finish();
            return;
        }
        ordersManager = new OrdersManager(this, sessionManager.getToken());

        heroCard = findViewById(R.id.heroCard);
        heroIcon = findViewById(R.id.heroIcon);
        heroPhoto = findViewById(R.id.heroPhoto);
        titleInput = findViewById(R.id.productTitleInput);
        originalInput = findViewById(R.id.productOriginalInput);
        discountInput = findViewById(R.id.productDiscountInput);
        daysInput = findViewById(R.id.productDaysInput);
        priceFooter = findViewById(R.id.priceFooter);
        saveButton = findViewById(R.id.saveButton);
        loadMessage = findViewById(R.id.loadMessage);
        formGroup = findViewById(R.id.formGroup);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());
        loadMessage.setOnClickListener(v -> {
            if (!loaded) loadProduct(); // "Tap to retry"
        });

        // Tapping anywhere on a row (not only on the number) starts typing in it.
        findViewById(R.id.rowOriginal).setOnClickListener(v -> focusField(originalInput));
        findViewById(R.id.rowDiscount).setOnClickListener(v -> focusField(discountInput));
        findViewById(R.id.rowDays).setOnClickListener(v -> focusField(daysInput));

        // The note under the price card ("Customers pay ... (20% off)") follows what is typed.
        TextWatcher priceWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updatePriceFooter(); }
        };
        originalInput.addTextChangedListener(priceWatcher);
        discountInput.addTextChangedListener(priceWatcher);
        updatePriceFooter();

        // Keyboard "Done" on the last field saves, same as tapping the button.
        daysInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                save();
                return true;
            }
            return false;
        });

        bindHero();
        loadProduct();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (heroSmallRequest != null) heroSmallRequest.cancelRequest();
        if (heroLargeRequest != null) heroLargeRequest.cancelRequest();
    }

    // ---------------------------------------------------------------- Photo

    // Same look as the Home tile until the photo arrives: tinted category icon on a pastel color.
    private void bindHero() {
        Intent in = getIntent();
        int icon = in.getIntExtra(EXTRA_ICON, R.drawable.ic_bag);
        int bg = in.getIntExtra(EXTRA_BG, R.color.tile_blue_bg);
        int fg = in.getIntExtra(EXTRA_FG, R.color.tile_blue_fg);
        heroCard.setCardBackgroundColor(ContextCompat.getColor(this, bg));
        heroIcon.setImageResource(icon);
        ImageViewCompat.setImageTintList(heroIcon,
                ColorStateList.valueOf(ContextCompat.getColor(this, fg)));

        String name = in.getStringExtra(EXTRA_PRODUCT_NAME);
        if (name != null) heroPhoto.setContentDescription(name);

        String url = in.getStringExtra(EXTRA_IMAGE_URL);
        if (url == null || url.isEmpty()) return;

        // Two sizes: 640 is what Home already loaded, so it normally shows instantly from memory;
        // 1080 is the sharp version that replaces it a moment later.
        ImageLoader loader = VolleySingleton.getInstance(getApplicationContext()).getImageLoader();
        heroSmallRequest = loader.get(url, new HeroListener(false), 640, 640);
        heroLargeRequest = loader.get(url, new HeroListener(true), 1080, 1080);
    }

    private class HeroListener implements ImageLoader.ImageListener {
        private final boolean large;

        HeroListener(boolean large) {
            this.large = large;
        }

        @Override
        public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
            Bitmap bitmap = response.getBitmap();
            if (bitmap == null || isFinishing() || isDestroyed()) return; // null = still loading
            if (!large && heroLargeShown) return; // the sharp one is already on screen
            if (large) heroLargeShown = true;
            heroPhoto.setImageBitmap(bitmap);
            heroPhoto.setVisibility(View.VISIBLE);
            heroIcon.setVisibility(View.GONE);
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            // Keep the icon. Common causes: wrong PRODUCT_IMAGE_BASE_URL, file missing, 403.
        }
    }

    // ---------------------------------------------------------------- Load

    private void loadProduct() {
        formGroup.setVisibility(View.GONE);
        loadMessage.setText("Loading…");
        loadMessage.setVisibility(View.VISIBLE);

        ordersManager.getProduct(productId, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) {
                        loadMessage.setText(response.optString("message", "Couldn't load the product")
                                + ". Tap to retry.");
                        return;
                    }
                    JSONObject p = response.getJSONObject("product");

                    // The server says whether delivery_days is a number column or free text.
                    daysIsNumber = response.optBoolean("delivery_days_is_number", true);
                    daysInput.setInputType(daysIsNumber
                            ? InputType.TYPE_CLASS_NUMBER
                            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    daysInput.setHint(daysIsNumber ? "e.g. 3" : "e.g. 3-5 days");
                    titleInput.setFilters(new InputFilter[]{
                            new InputFilter.LengthFilter(response.optInt("title_max_length", 255))});

                    titleInput.setText(Format.str(p, "title"));
                    originalInput.setText(plainNumber(Format.str(p, "original_amount")));
                    discountInput.setText(plainNumber(Format.str(p, "discounted_amount")));
                    daysInput.setText(Format.str(p, "delivery_days"));

                    loaded = true;
                    saveButton.setEnabled(true);
                    loadMessage.setVisibility(View.GONE);
                    formGroup.setVisibility(View.VISIBLE);
                } catch (JSONException e) {
                    loadMessage.setText("Unexpected response from server. Tap to retry.");
                }
            }

            @Override
            public void onError(String message) {
                if (handleSessionExpired(message)) return;
                loadMessage.setText("Couldn't reach server. Tap to retry.");
            }
        });
    }

    // "14100.00" -> "14100", "12.50" stays "12.50", "0.00" / "" -> "" (no discount = empty box)
    private static String plainNumber(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() == 0) return "";
            return raw.matches("\\d+\\.0+") ? raw.substring(0, raw.indexOf('.')) : raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    // ---------------------------------------------------------------- Price note

    // A valid amount typed in the box, or null (empty / not a number yet).
    private static BigDecimal parseAmount(EditText field) {
        String s = field.getText().toString().trim();
        return s.matches(AMOUNT_PATTERN) ? new BigDecimal(s) : null;
    }

    // The grey note under the price card: what customers will pay, the % off, or what is wrong.
    private void updatePriceFooter() {
        BigDecimal original = parseAmount(originalInput);
        BigDecimal discount = parseAmount(discountInput);

        int color = ContextCompat.getColor(this, R.color.home_nav_inactive);
        String text;
        if (original == null || original.signum() <= 0) {
            text = "Leave the discounted price empty if there is no discount.";
        } else if (discount != null && discount.signum() > 0) {
            if (discount.compareTo(original) >= 0) {
                text = "The discounted price must be lower than the original price.";
                color = Color.parseColor("#E5484D");
            } else {
                BigDecimal percent = original.subtract(discount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(original, 1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                text = "Customers pay " + Format.money(discount.toPlainString())
                        + " (" + percent.toPlainString() + "% off).";
            }
        } else {
            text = "Customers pay " + Format.money(original.toPlainString())
                    + ". Leave the discounted price empty for no discount.";
        }
        priceFooter.setText(text);
        priceFooter.setTextColor(color);
    }

    private void focusField(EditText field) {
        field.requestFocus();
        field.setSelection(field.getText().length());
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    // ---------------------------------------------------------------- Save

    private void save() {
        if (saving || !loaded) return;

        String title = titleInput.getText().toString().trim();
        String original = originalInput.getText().toString().trim();
        String discount = discountInput.getText().toString().trim();
        String days = daysInput.getText().toString().trim();

        if (title.isEmpty()) {
            titleInput.setError("Enter the product title");
            titleInput.requestFocus();
            return;
        }
        // 1500 or 1499.50 - digits and one dot only, so nothing is ever misread as another number.
        if (!original.matches(AMOUNT_PATTERN) || new BigDecimal(original).signum() <= 0) {
            originalInput.setError("Enter a price like 1500 or 1499.50");
            originalInput.requestFocus();
            return;
        }
        if (!discount.isEmpty()) {
            if (!discount.matches(AMOUNT_PATTERN)) {
                discountInput.setError("Enter a price like 1200 or 1199.50, or leave empty");
                discountInput.requestFocus();
                return;
            }
            BigDecimal d = new BigDecimal(discount);
            if (d.signum() > 0 && d.compareTo(new BigDecimal(original)) >= 0) {
                discountInput.setError("Must be lower than the original price");
                discountInput.requestFocus();
                return;
            }
        }
        if (days.isEmpty()) {
            daysInput.setError("Enter the delivery days");
            daysInput.requestFocus();
            return;
        }
        if (daysIsNumber && !days.matches("\\d{1,4}")) {
            daysInput.setError("Enter a whole number, like 3");
            daysInput.requestFocus();
            return;
        }

        setSaving(true);
        ordersManager.updateProduct(productId, title, original, discount, days, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                setSaving(false);
                if (!response.optBoolean("success", false)) {
                    toast(response.optString("message", "Couldn't save the product"));
                    return;
                }
                JSONObject p = response.optJSONObject("product");
                Intent result = new Intent();
                result.putExtra(RESULT_PRODUCT_ID, productId);
                result.putExtra(RESULT_TITLE, p == null ? title : Format.str(p, "title"));
                result.putExtra(RESULT_PRICE, p == null ? original : Format.str(p, "price"));
                setResult(RESULT_OK, result); // Home updates this product's tile
                toast("Product updated");
                finish();
            }

            @Override
            public void onError(String message) {
                setSaving(false);
                if (handleSessionExpired(message)) return;
                toast("Couldn't save: " + message);
            }
        });
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setEnabled(!value);
        saveButton.setText(value ? "Saving…" : "Save changes");
    }

    // ---------------------------------------------------------------- Helpers

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Returns true (and sends the user to the login screen) when the login token is no longer valid.
    private boolean handleSessionExpired(String message) {
        if (!OrdersManager.SESSION_EXPIRED.equals(message)) return false;
        sessionManager.clear();
        Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
        goToLogin();
        return true;
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
