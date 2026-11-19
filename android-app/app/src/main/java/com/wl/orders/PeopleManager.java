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

// Talks to your own PHP files for the People page (roles + people). Same idea as
// OrdersManager: every call sends the login token, and answers through ApiCallback.
// A "session expired" answer arrives as OrdersManager.SESSION_EXPIRED.
public class PeopleManager {

    private final Context context;
    private final String token;

    public PeopleManager(Context context, String token) {
        this.context = context;
        this.token = token;
    }

    // All roles from wpeople_type (reply: "roles": [{id, wrole}, ...]).
    public void getRoles(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_PEOPLE_ROLES_URL, null, 20000, 1, callback);
    }

    // All people from wpeople, newest first (reply: "people": [...]).
    public void getPeople(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_PEOPLE_URL, null, 20000, 1, callback);
    }

    // Saves a new role into wpeople_type.
    public void addRole(String role, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("wrole", role);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        // 0 retries: a second automatic try after a timeout could save the role twice.
        send(Request.Method.POST, ApiConfig.ADD_PEOPLE_ROLE_URL, body, 20000, 0, callback);
    }

    // Saves a new store (just a name) into wbstore.
    public void addStore(String name, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        // 0 retries: a second automatic try after a timeout could save the store twice.
        send(Request.Method.POST, ApiConfig.ADD_STORE_URL, body, 20000, 0, callback);
    }

    // Saves a new person into wpeople. imageBase64 = the JPEG photo as base64, or "" for no photo.
    public void addPerson(String name, String number, int roleId, String imageBase64, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("number", number);
            body.put("role_id", roleId);
            body.put("image", imageBase64);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        // Longer wait (the photo is uploaded with it) and 0 retries so nobody is saved twice.
        send(Request.Method.POST, ApiConfig.ADD_PERSON_URL, body, 45000, 0, callback);
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
                    // {"success":false,"message":"Server error"} -> "Server error"
                    return new JSONObject(raw).optString("message", raw);
                } catch (JSONException e) {
                    return raw.length() > 200 ? raw.substring(0, 200) : raw;
                }
            }
        }
        return error.getMessage() != null ? error.getMessage() : "Network error";
    }
}
