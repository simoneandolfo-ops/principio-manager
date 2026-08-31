package it.principio.caller;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ApiClient {
    private static final String PREFS = "principio_caller";
    private static final String KEY_URL = "manager_url";
    private static final String KEY_TOKEN = "operator_token";
    private static final String KEY_DEVICE = "device_id";

    private ApiClient() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String normalizeManagerUrl(String raw) {
        String v = raw == null ? "" : raw.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length()-1);
        if (v.endsWith("/public")) return v + "/api.php";
        if (v.endsWith("/public/api.php")) return v;
        return v + "/public/api.php";
    }

    public static String getManagerUrl(Context c) {
        return prefs(c).getString(KEY_URL, "");
    }

    public static String getToken(Context c) {
        return prefs(c).getString(KEY_TOKEN, "");
    }

    public static String getDeviceId(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString(KEY_DEVICE, "");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            p.edit().putString(KEY_DEVICE, id).apply();
        }
        return id;
    }

    public static void saveConnection(Context c, String rawUrl, String token) {
        prefs(c).edit()
                .putString(KEY_URL, normalizeManagerUrl(rawUrl))
                .putString(KEY_TOKEN, token == null ? "" : token)
                .apply();
    }

    public static JSONObject call(String endpoint, String fn, JSONArray args) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3500);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject body = new JSONObject();
        body.put("fn", fn);
        body.put("args", args);

        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }

        int code = conn.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (input != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) text.append(line);
            }
        }
        JSONObject json = new JSONObject(text.length() == 0 ? "{}" : text.toString());
        if (code < 200 || code >= 300 || !json.optBoolean("ok", false)) {
            throw new IllegalStateException(json.optString("error", "Errore Manager (" + code + ")"));
        }
        return json.optJSONObject("result");
    }

    public static JSONObject login(String rawUrl, String pin) throws Exception {
        String endpoint = normalizeManagerUrl(rawUrl);
        JSONArray args = new JSONArray().put(pin);
        JSONObject result = call(endpoint, "loginOperator", args);
        if (result == null) throw new IllegalStateException("Risposta login non valida.");
        return result;
    }

    public static JSONObject sendIncoming(Context c, String phone) throws Exception {
        return sendIncoming(c, phone, "");
    }

    public static JSONObject sendIncoming(Context c, String phone, String eventKey) throws Exception {
        String endpoint = getManagerUrl(c);
        String token = getToken(c);
        if (endpoint.isEmpty() || token.isEmpty()) {
            throw new IllegalStateException("Principio Caller non configurato.");
        }
        JSONArray args = new JSONArray()
                .put(phone)
                .put(getDeviceId(c))
                .put(token)
                .put(eventKey == null ? "" : eventKey);
        return call(endpoint, "callerRegisterIncoming", args);
    }
}
