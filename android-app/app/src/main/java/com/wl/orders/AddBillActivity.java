package com.wl.orders;

import android.app.DatePickerDialog;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wl.orders.models.Person;
import com.wl.orders.models.WalletBank;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

// Reports > "Add Bill": bill photo (taken with the camera OR picked from the gallery), the person
// (only people with role_id = 4 from the wpeople table, with a search box), amount, paid,
// payment method, status and the date. Everything goes to YOUR server (add_bill.php) and is
// saved in the wbbills table - no Firebase or other service. The server adds the exact
// date and time the bill was recorded (created_at) by itself.
//
// The photo is shrunk on the phone first (max 1600 px, JPEG). No permission is needed: the camera
// app does the shooting, the gallery picker hands us just the one picture the user chose.
public class AddBillActivity extends AppCompatActivity {

    private static final String STATE_CAMERA_FILE = "camera_file";
    private static final int MAX_SIDE_PX = 1600;   // bills have small print: more detail than a profile photo
    private static final int JPEG_QUALITY = 85;

    // 15000 or 15000.50 - digits and one dot only (the server checks the same rule).
    private static final String MONEY_PATTERN = "\\d{1,13}(\\.\\d{1,2})?";

    // The values saved in the wbbills.status column.
    private static final String STATUS_UNPAID = "unpaid";
    private static final String STATUS_PARTIAL = "partial";
    private static final String STATUS_PAID = "paid";

    private ReportsManager reportsManager;
    private SessionManager sessionManager;

    private EditText amountInput;
    private EditText paidInput;
    private TextView personNameView;
    private ImageView methodLogo;
    private TextView methodNameView;
    private TextView statusUnpaid;
    private TextView statusPartial;
    private TextView statusPaid;
    private TextView dateText;
    private TextView saveButton;
    private ImageView photoPreview;
    private View photoPlaceholder;
    private TextView removePhoto;

    private final List<Person> people = new ArrayList<>();
    private boolean peopleLoaded = false;
    private Person selectedPerson;

    // "Cash" first, then every bank / wallet from the wallets_and_banks table.
    private final List<WalletBank> methods = new ArrayList<>();
    private boolean banksLoaded = false;
    private WalletBank selectedMethod;

    private String status = STATUS_UNPAID;
    private boolean statusChosenByUser = false; // until then the status follows amount / paid by itself

    private final Calendar billDate = Calendar.getInstance(); // starts as today

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
        setContentView(R.layout.activity_add_bill);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        reportsManager = new ReportsManager(this, sessionManager.getToken());

        // The camera app may have been running while Android cleared us from memory.
        if (savedInstanceState != null) {
            String path = savedInstanceState.getString(STATE_CAMERA_FILE);
            if (path != null) cameraFile = new File(path);
        }

        amountInput = findViewById(R.id.billAmount);
        paidInput = findViewById(R.id.billPaid);
        personNameView = findViewById(R.id.billPersonName);
        methodLogo = findViewById(R.id.methodLogo);
        methodNameView = findViewById(R.id.methodName);
        statusUnpaid = findViewById(R.id.statusUnpaid);
        statusPartial = findViewById(R.id.statusPartial);
        statusPaid = findViewById(R.id.statusPaid);
        dateText = findViewById(R.id.dateText);
        saveButton = findViewById(R.id.saveButton);
        photoPreview = findViewById(R.id.billPreview);
        photoPlaceholder = findViewById(R.id.billPlaceholder);
        removePhoto = findViewById(R.id.removePhoto);

        // Payment methods: Cash is always there, the banks / wallets are added when they arrive.
        WalletBank cash = new WalletBank();
        cash.id = 0;
        cash.name = "Cash";
        cash.image = "";
        methods.add(cash);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnCamera).setOnClickListener(v -> onCameraClicked());
        findViewById(R.id.btnGallery).setOnClickListener(v -> pickImage.launch("image/*"));
        removePhoto.setOnClickListener(v -> setPhoto(null, null));
        findViewById(R.id.personField).setOnClickListener(v -> showPersonPicker());
        findViewById(R.id.methodField).setOnClickListener(v -> showMethodPicker());
        findViewById(R.id.dateField).setOnClickListener(v -> showDatePicker());
        statusUnpaid.setOnClickListener(v -> chooseStatus(STATUS_UNPAID));
        statusPartial.setOnClickListener(v -> chooseStatus(STATUS_PARTIAL));
        statusPaid.setOnClickListener(v -> chooseStatus(STATUS_PAID));
        saveButton.setOnClickListener(v -> save());

        TextWatcher moneyWatcher = new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                suggestStatus();
            }
        };
        amountInput.addTextChangedListener(moneyWatcher);
        paidInput.addTextChangedListener(moneyWatcher);

        setStatus(STATUS_UNPAID);
        showDate();
        loadPeople();
        loadBanks();
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
            File dir = new File(getCacheDir(), "bill_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                toast("Couldn't prepare the camera");
                return;
            }
            cameraFile = File.createTempFile("bill_", ".jpg", dir);
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
            PhotoShrinker.Result result;
            try {
                result = PhotoShrinker.shrink(getContentResolver(), uri, MAX_SIDE_PX, JPEG_QUALITY);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                result = null;
            }
            if (deleteAfter != null) deleteAfter.delete();

            final PhotoShrinker.Result finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalResult == null) {
                    toast("Couldn't use that photo. Please try another one.");
                } else {
                    setPhoto(finalResult.bitmap, finalResult.jpeg);
                }
            });
        }).start();
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

    // ---------------------------------------------------------------- People (role_id = 4)

    // Fills the "Select person" list from the wpeople table (only role_id = 4, chosen by the server).
    private void loadPeople() {
        reportsManager.getBillPeople(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) {
                        toast(response.optString("message", "Couldn't load people"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("people");
                    people.clear();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        Person p = new Person();
                        p.id = o.getInt("id");
                        p.name = Format.str(o, "name");
                        p.number = Format.str(o, "number");
                        p.image = Format.str(o, "image");
                        p.role = "";
                        people.add(p);
                    }
                    peopleLoaded = true;
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

    private void showPersonPicker() {
        if (!peopleLoaded) {
            toast("Loading people…");
            loadPeople();
            return;
        }
        if (people.isEmpty()) {
            toast("No people to pick yet. Add one on the People page first.");
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_pick_person, null);
        sheet.findViewById(R.id.personSheetClose).setOnClickListener(v -> dialog.dismiss());

        TextView emptyView = sheet.findViewById(R.id.personEmpty);
        RecyclerView list = sheet.findViewById(R.id.personList);
        PersonPickAdapter adapter = new PersonPickAdapter(people, selectedPerson == null ? -1 : selectedPerson.id,
                person -> {
                    selectPerson(person);
                    dialog.dismiss();
                });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // The search box at the top of the list.
        EditText search = sheet.findViewById(R.id.personSearch);
        search.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                int left = adapter.filter(s.toString());
                emptyView.setVisibility(left == 0 ? View.VISIBLE : View.GONE);
            }
        });

        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) {
            // Lift the popup above the keyboard while typing in the search box.
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void selectPerson(Person person) {
        selectedPerson = person;
        personNameView.setText(person.number.isEmpty() ? person.name : person.name + "  •  " + person.number);
    }

    // ---------------------------------------------------------------- Payment method

    // Adds the banks / wallets from the wallets_and_banks table after "Cash".
    private void loadBanks() {
        reportsManager.getWalletsBanks(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) return;
                    JSONArray array = response.getJSONArray("items");
                    while (methods.size() > 1) methods.remove(methods.size() - 1); // keep only Cash
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        WalletBank b = new WalletBank();
                        b.id = o.getInt("id");
                        b.name = Format.str(o, "name");
                        b.image = Format.str(o, "image");
                        methods.add(b);
                    }
                    banksLoaded = true;
                } catch (JSONException e) {
                    // Cash still works; the picker says the banks are missing when it is opened.
                }
            }

            @Override
            public void onError(String message) {
                // Only a finished login matters here; any other problem is reported when the picker opens.
                if (OrdersManager.SESSION_EXPIRED.equals(message)) handleError(message);
            }
        });
    }

    private void showMethodPicker() {
        if (!banksLoaded) {
            toast("Couldn't load banks and wallets yet - only Cash for now");
            loadBanks();
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_pick_bank, null);
        ((TextView) sheet.findViewById(R.id.bankSheetTitle)).setText("Payment method");
        sheet.findViewById(R.id.bankSheetClose).setOnClickListener(v -> dialog.dismiss());

        RecyclerView list = sheet.findViewById(R.id.bankList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new BankOptionAdapter(methods, selectedMethod == null ? -1 : selectedMethod.id, method -> {
            selectMethod(method);
            dialog.dismiss();
        }));

        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void selectMethod(WalletBank method) {
        selectedMethod = method;
        methodNameView.setText(method.name);
        BankLogo.load(methodLogo, null, method.image, method.name);
    }

    // ---------------------------------------------------------------- Status

    private void chooseStatus(String value) {
        statusChosenByUser = true;
        setStatus(value);
    }

    private void setStatus(String value) {
        status = value;
        styleChip(statusUnpaid, STATUS_UNPAID.equals(value));
        styleChip(statusPartial, STATUS_PARTIAL.equals(value));
        styleChip(statusPaid, STATUS_PAID.equals(value));
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.home_blue : R.color.home_chip_text));
    }

    // Until the user taps a status themselves, it follows the money: nothing paid = Unpaid,
    // all of it paid = Paid, anything in between = Partial.
    private void suggestStatus() {
        if (statusChosenByUser) return;
        BigDecimal amount = parseMoney(amountInput.getText().toString());
        BigDecimal paid = parseMoney(paidInput.getText().toString());
        if (paid.signum() <= 0) {
            setStatus(STATUS_UNPAID);
        } else if (amount.signum() > 0 && paid.compareTo(amount) >= 0) {
            setStatus(STATUS_PAID);
        } else {
            setStatus(STATUS_PARTIAL);
        }
    }

    // ---------------------------------------------------------------- Date

    private void showDatePicker() {
        new DatePickerDialog(this, (picker, year, month, day) -> {
            billDate.set(year, month, day);
            showDate();
        }, billDate.get(Calendar.YEAR), billDate.get(Calendar.MONTH), billDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDate() {
        dateText.setText(new SimpleDateFormat("MMM d, yyyy", Locale.US).format(billDate.getTime()));
    }

    // The date in the form MySQL's DATE column takes: 2026-11-15
    private String billDateForServer() {
        return String.format(Locale.US, "%04d-%02d-%02d",
                billDate.get(Calendar.YEAR), billDate.get(Calendar.MONTH) + 1, billDate.get(Calendar.DAY_OF_MONTH));
    }

    // ---------------------------------------------------------------- Save

    private void save() {
        if (saving) return;

        String amount = amountInput.getText().toString().trim();
        String paid = paidInput.getText().toString().trim();
        if (paid.isEmpty()) paid = "0";

        if (selectedPerson == null) {
            toast("Please select a person");
            return;
        }
        if (!amount.matches(MONEY_PATTERN) || parseMoney(amount).signum() <= 0) {
            amountInput.setError("Enter the bill amount, like 15000 or 15000.50");
            amountInput.requestFocus();
            return;
        }
        if (!paid.matches(MONEY_PATTERN)) {
            paidInput.setError("Enter what is paid, like 2000 or 2000.50 (or leave it empty)");
            paidInput.requestFocus();
            return;
        }
        BigDecimal paidValue = parseMoney(paid);
        if (paidValue.compareTo(parseMoney(amount)) > 0) {
            paidInput.setError("Paid can't be more than the bill amount");
            paidInput.requestFocus();
            return;
        }
        // Money was paid, so say how. An unpaid bill needs no payment method yet.
        if (paidValue.signum() > 0 && selectedMethod == null) {
            toast("Please select the payment method");
            return;
        }

        setSaving(true);
        String image = photoJpeg == null ? "" : Base64.encodeToString(photoJpeg, Base64.NO_WRAP);
        String method = selectedMethod == null ? "" : selectedMethod.name;
        reportsManager.addBill(selectedPerson.id, amount, paid, method, status, billDateForServer(), image,
                new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        setSaving(false);
                        if (response.optBoolean("success", false)) {
                            toast("Bill added");
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            toast(response.optString("message", "Couldn't save the bill"));
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
        saveButton.setText(value ? "Saving…" : "Save bill");
    }

    // ---------------------------------------------------------------- Helpers

    // "" or anything that isn't a number counts as 0.
    private static BigDecimal parseMoney(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

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

    // TextWatcher where only afterTextChanged has to be written.
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
