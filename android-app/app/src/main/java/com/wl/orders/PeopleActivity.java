package com.wl.orders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wl.orders.models.Person;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// The "People" item in the bottom bar lands here: two buttons on top
//   - Add Role   -> small popup, saves a role into the wpeople_type table
//   - Add People -> AddPersonActivity, saves a person into the wpeople table
//   - Add Store  -> small popup, saves a store name into the wbstore table
// and below them the list of everyone saved so far (pull down to refresh).
public class PeopleActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyView;
    private PeopleAdapter adapter;
    private PeopleManager peopleManager;
    private SessionManager sessionManager;

    // AddPersonActivity answers RESULT_OK after saving -> reload the list.
    private final ActivityResultLauncher<Intent> addPersonLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && peopleManager != null) {
                    loadPeople();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        peopleManager = new PeopleManager(this, sessionManager.getToken());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        emptyView = findViewById(R.id.emptyView);
        RecyclerView recyclerView = findViewById(R.id.peopleRecyclerView);

        adapter = new PeopleAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddRole).setOnClickListener(v -> showAddRoleSheet());
        findViewById(R.id.btnAddPerson).setOnClickListener(v ->
                addPersonLauncher.launch(new Intent(this, AddPersonActivity.class)));
        findViewById(R.id.btnAddStore).setOnClickListener(v -> showAddStoreSheet());
        swipeRefresh.setOnRefreshListener(this::loadPeople);

        loadPeople();
    }

    // ---------------------------------------------------------------- Add Role popup

    private void showAddRoleSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_add_role, null);

        EditText input = sheet.findViewById(R.id.roleInput);
        TextView saveButton = sheet.findViewById(R.id.roleSave);
        sheet.findViewById(R.id.roleClose).setOnClickListener(v -> dialog.dismiss());

        Runnable save = () -> {
            String role = input.getText().toString().trim();
            if (role.isEmpty()) {
                input.setError("Enter a role");
                return;
            }
            saveButton.setEnabled(false);
            saveButton.setText("Saving…");
            peopleManager.addRole(role, new ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save role");
                    if (response.optBoolean("success", false)) {
                        Toast.makeText(PeopleActivity.this, "Role added", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        // e.g. "This role already exists" - shown right under the field
                        input.setError(response.optString("message", "Couldn't add the role"));
                    }
                }

                @Override
                public void onError(String message) {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save role");
                    handleError(message);
                }
            });
        };
        saveButton.setOnClickListener(v -> save.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            save.run();
            return true;
        });

        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            input.requestFocus();
        });
        dialog.show();
    }

    // ---------------------------------------------------------------- Add Store popup

    private void showAddStoreSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_add_store, null);

        EditText input = sheet.findViewById(R.id.storeInput);
        TextView saveButton = sheet.findViewById(R.id.storeSave);
        sheet.findViewById(R.id.storeClose).setOnClickListener(v -> dialog.dismiss());

        Runnable save = () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter a store name");
                return;
            }
            saveButton.setEnabled(false);
            saveButton.setText("Saving…");
            peopleManager.addStore(name, new ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save store");
                    if (response.optBoolean("success", false)) {
                        Toast.makeText(PeopleActivity.this, "Store added", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        // e.g. "This store already exists" - shown right under the field
                        input.setError(response.optString("message", "Couldn't add the store"));
                    }
                }

                @Override
                public void onError(String message) {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save store");
                    handleError(message);
                }
            });
        };
        saveButton.setOnClickListener(v -> save.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            save.run();
            return true;
        });

        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            input.requestFocus();
        });
        dialog.show();
    }

    // ---------------------------------------------------------------- People list

    private void loadPeople() {
        swipeRefresh.setRefreshing(true);
        peopleManager.getPeople(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (!response.getBoolean("success")) {
                        Toast.makeText(PeopleActivity.this,
                                response.optString("message", "Couldn't load people"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONArray array = response.getJSONArray("people");
                    List<Person> list = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        Person p = new Person();
                        p.id = o.getInt("id");
                        p.name = Format.str(o, "name");
                        p.number = Format.str(o, "number");
                        p.image = Format.str(o, "image");
                        p.roleId = o.optInt("role_id", 0);
                        p.role = Format.str(o, "role");
                        list.add(p);
                    }
                    adapter.setPeople(list);
                    emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                } catch (JSONException e) {
                    Toast.makeText(PeopleActivity.this, "Unexpected response from server", Toast.LENGTH_SHORT).show();
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
