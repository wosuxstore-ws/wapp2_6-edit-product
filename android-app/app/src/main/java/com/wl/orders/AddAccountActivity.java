package com.wl.orders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wl.orders.models.WalletBank;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// "Add Account": account name, bank / wallet (list from the wallets_and_banks table, with
// the pictures from api/banks_and_wallets/) and the amount. Saved into the wbaccounts table.
public class AddAccountActivity extends AppCompatActivity {

    private EditText nameInput;
    private EditText amountInput;
    private ImageView bankLogo;
    private TextView bankNameView;
    private TextView saveButton;

    private ReportsManager reportsManager;
    private SessionManager sessionManager;

    private final List<WalletBank> banks = new ArrayList<>();
    private boolean banksLoaded = false;
    private WalletBank selectedBank;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        reportsManager = new ReportsManager(this, sessionManager.getToken());

        nameInput = findViewById(R.id.accountNameInput);
        amountInput = findViewById(R.id.accountAmountInput);
        bankLogo = findViewById(R.id.selectedBankLogo);
        bankNameView = findViewById(R.id.selectedBankName);
        saveButton = findViewById(R.id.saveButton);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.bankField).setOnClickListener(v -> showBankPicker());
        saveButton.setOnClickListener(v -> save());

        loadBanks();
    }

    // ---------------------------------------------------------------- Banks & wallets

    // Fills the "Select bank or wallet" list from the wallets_and_banks table.
    private void loadBanks() {
        reportsManager.getWalletsBanks(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) {
                        toast(response.optString("message", "Couldn't load banks and wallets"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("items");
                    banks.clear();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        WalletBank b = new WalletBank();
                        b.id = o.getInt("id");
                        b.name = Format.str(o, "name");
                        b.image = Format.str(o, "image");
                        banks.add(b);
                    }
                    banksLoaded = true;
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

    private void showBankPicker() {
        if (!banksLoaded) {
            toast("Loading banks and wallets…");
            loadBanks();
            return;
        }
        if (banks.isEmpty()) {
            toast("No banks or wallets found in the wallets_and_banks table");
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_pick_bank, null);
        sheet.findViewById(R.id.bankSheetClose).setOnClickListener(v -> dialog.dismiss());

        RecyclerView list = sheet.findViewById(R.id.bankList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new BankOptionAdapter(banks, selectedBank == null ? -1 : selectedBank.id, bank -> {
            selectBank(bank);
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

    private void selectBank(WalletBank bank) {
        selectedBank = bank;
        bankNameView.setText(bank.name);
        BankLogo.load(bankLogo, null, bank.image, bank.name);
    }

    // ---------------------------------------------------------------- Save

    private void save() {
        if (saving) return;

        String name = nameInput.getText().toString().trim();
        String amount = amountInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Enter an account name");
            nameInput.requestFocus();
            return;
        }
        if (selectedBank == null) {
            toast("Please select a bank or wallet");
            return;
        }
        // 15000 or 15000.50 - digits and one dot only, so nothing is ever misread as another number.
        if (!amount.matches("\\d{1,13}(\\.\\d{1,2})?")) {
            amountInput.setError("Enter an amount like 15000 or 15000.50");
            amountInput.requestFocus();
            return;
        }

        setSaving(true);
        reportsManager.addAccount(name, selectedBank.id, amount, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                setSaving(false);
                if (response.optBoolean("success", false)) {
                    toast("Account added");
                    setResult(RESULT_OK); // the Reports list reloads itself
                    finish();
                } else {
                    toast(response.optString("message", "Couldn't save the account"));
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
        saveButton.setText(value ? "Saving…" : "Save account");
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
