package com.wl.orders;

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

// Talks to your own PHP files for the Reports page (accounts). Same idea as PeopleManager:
// every call sends the login token and answers through ApiCallback. A "session expired"
// answer arrives as OrdersManager.SESSION_EXPIRED.
public class ReportsManager {

    private final Context context;
    private final String token;

    public ReportsManager(Context context, String token) {
        this.context = context;
        this.token = token;
    }

    // All banks / wallets from wallets_and_banks (reply: "items": [{id, name, image}, ...]).
    public void getWalletsBanks(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_WALLETS_BANKS_URL, null, 20000, 1, callback);
    }

    // All accounts from wbaccounts, newest first (reply: "accounts": [...], "total": "...").
    public void getAccounts(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_ACCOUNTS_URL, null, 20000, 1, callback);
    }

    // Saves a new account into wbaccounts. amount is sent as text ("15000.50") so no rounding happens.
    public void addAccount(String accountName, int walletBankId, String amount, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("account_name", accountName);
            body.put("wallet_bank_id", walletBankId);
            body.put("amount", amount);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        // 0 retries: a second automatic try after a timeout could save the account twice.
        send(Request.Method.POST, ApiConfig.ADD_ACCOUNT_URL, body, 20000, 0, callback);
    }

    // The people offered on the Add Bill screen: wpeople rows with role_id = 4 (reply: "people": [{id, name, number, image}, ...]).
    public void getBillPeople(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_BILL_PEOPLE_URL, null, 20000, 1, callback);
    }

    // Saves a new bill into wbbills. Money is sent as text ("15000.50") so no rounding happens.
    // status = unpaid | partial | paid, billDate = "2026-11-15", imageBase64 = the JPEG bill photo as base64 or "".
    public void addBill(int personId, String amount, String paid, String paymentMethod, String status,
                        String billDate, String imageBase64, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("person_id", personId);
            body.put("amount", amount);
            body.put("paid", paid);
            body.put("payment_method", paymentMethod);
            body.put("status", status);
            body.put("bill_date", billDate);
            body.put("image", imageBase64);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        // Longer wait (the photo is uploaded with it) and 0 retries so a bill is never saved twice.
        send(Request.Method.POST, ApiConfig.ADD_BILL_URL, body, 60000, 0, callback);
    }

    private void send(int method, String url, JSONObject body, int timeoutMs, int retries, ApiCallback callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                method, url, body,
                callback::onSuccess,
                error -> callback.onError(parseError(error))) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Auth-Token", token);
                return headers;
            }
        };
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(timeoutMs, retries, 1f));
        VolleySingleton.getInstance(context).addToRequestQueue(request);
    }

    private String parseError(VolleyError error) {
        if (error.networkResponse != null) {
            if (error.networkResponse.statusCode == 401) {
                return OrdersManager.SESSION_EXPIRED;
            }
            if (error.networkResponse.data != null) {
                String raw = new String(error.networkResponse.data);
                try {
                    return new JSONObject(raw).optString("message", raw);
                } catch (JSONException e) {
                    return raw.length() > 200 ? raw.substring(0, 200) : raw;
                }
            }
        }
        return error.getMessage() != null ? error.getMessage() : "Network error";
    }
}
