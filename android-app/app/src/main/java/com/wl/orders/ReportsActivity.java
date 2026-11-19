package com.wl.orders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.wl.orders.models.Account;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// The "Reports" item in the bottom bar lands here:
//   - Add Account -> AddAccountActivity, saves an account into the wbaccounts table
//   - Add Bill    -> AddBillActivity, saves a bill (with its photo) into the wbbills table
// and below it the total and the list of every account saved so far (pull down to refresh).
public class ReportsActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyView;
    private TextView totalValue;
    private AccountAdapter adapter;
    private ReportsManager reportsManager;
    private SessionManager sessionManager;

    // AddAccountActivity answers RESULT_OK after saving -> reload the list.
    private final ActivityResultLauncher<Intent> addAccountLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && reportsManager != null) {
                    loadAccounts();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        reportsManager = new ReportsManager(this, sessionManager.getToken());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        emptyView = findViewById(R.id.emptyView);
        totalValue = findViewById(R.id.totalValue);
        RecyclerView recyclerView = findViewById(R.id.accountsRecyclerView);

        adapter = new AccountAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddAccount).setOnClickListener(v ->
                addAccountLauncher.launch(new Intent(this, AddAccountActivity.class)));
        findViewById(R.id.btnAddBill).setOnClickListener(v ->
                startActivity(new Intent(this, AddBillActivity.class)));
        swipeRefresh.setOnRefreshListener(this::loadAccounts);

        loadAccounts();
    }

    // ---------------------------------------------------------------- Accounts list

    private void loadAccounts() {
        swipeRefresh.setRefreshing(true);
        reportsManager.getAccounts(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (!response.getBoolean("success")) {
                        Toast.makeText(ReportsActivity.this,
                                response.optString("message", "Couldn't load accounts"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONArray array = response.getJSONArray("accounts");
                    List<Account> list = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        Account a = new Account();
                        a.id = o.getInt("id");
                        a.name = Format.str(o, "account_name");
                        a.walletBankId = o.optInt("wallet_bank_id", 0);
                        a.walletBankName = Format.str(o, "wallet_bank");
                        a.image = Format.str(o, "image");
                        a.amount = Format.str(o, "amount");
                        list.add(a);
                    }
                    adapter.setAccounts(list);
                    totalValue.setText(Format.moneyFixed(Format.str(response, "total")));
                    emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                } catch (JSONException e) {
                    Toast.makeText(ReportsActivity.this, "Unexpected response from server", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                handleError(message);
            }
        });
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
