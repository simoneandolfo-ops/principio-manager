package it.principio.caller;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PendingCallStore {
    private static final String KEY_QUEUE = "pending_call_queue_v11";
    private static final String KEY_LAST_INTERCEPT_PHONE = "last_intercept_phone";
    private static final String KEY_LAST_INTERCEPT_AT = "last_intercept_at";
    private static final String KEY_LAST_SENT_PHONE = "last_sent_phone";
    private static final String KEY_LAST_SENT_AT = "last_sent_at";
    private static final String KEY_LAST_ERROR = "last_send_error";

    public static final class Item {
        public final String id;
        public final String phone;
        public final long createdAt;

        Item(String id, String phone, long createdAt) {
            this.id = id;
            this.phone = phone;
            this.createdAt = createdAt;
        }
    }

    private PendingCallStore() {}

    public static synchronized Item enqueue(Context context, String phone) {
        SharedPreferences p = ApiClient.prefs(context);
        JSONArray arr = readArray(p);
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("phone", phone);
            o.put("createdAt", now);
            arr.put(o);
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile salvare la chiamata", e);
        }
        boolean ok = p.edit()
                .putString(KEY_QUEUE, arr.toString())
                .putString(KEY_LAST_INTERCEPT_PHONE, phone)
                .putLong(KEY_LAST_INTERCEPT_AT, now)
                .commit();
        if (!ok) throw new IllegalStateException("Salvataggio chiamata non riuscito");
        return new Item(id, phone, now);
    }

    public static synchronized List<Item> all(Context context) {
        JSONArray arr = readArray(ApiClient.prefs(context));
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "");
            String phone = o.optString("phone", "");
            long createdAt = o.optLong("createdAt", 0L);
            if (!id.isEmpty() && !phone.isEmpty()) out.add(new Item(id, phone, createdAt));
        }
        return out;
    }

    public static synchronized int count(Context context) {
        return all(context).size();
    }

    public static synchronized void markSent(Context context, String id, String phone) {
        SharedPreferences p = ApiClient.prefs(context);
        JSONArray arr = readArray(p);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (!id.equals(o.optString("id", ""))) next.put(o);
        }
        p.edit()
                .putString(KEY_QUEUE, next.toString())
                .putString(KEY_LAST_SENT_PHONE, phone)
                .putLong(KEY_LAST_SENT_AT, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .commit();
    }

    public static synchronized void markError(Context context, String message) {
        ApiClient.prefs(context).edit()
                .putString(KEY_LAST_ERROR, message == null ? "Errore invio" : message)
                .commit();
    }

    public static String lastInterceptPhone(Context c) {
        return ApiClient.prefs(c).getString(KEY_LAST_INTERCEPT_PHONE, "");
    }

    public static long lastInterceptAt(Context c) {
        return ApiClient.prefs(c).getLong(KEY_LAST_INTERCEPT_AT, 0L);
    }

    public static String lastSentPhone(Context c) {
        return ApiClient.prefs(c).getString(KEY_LAST_SENT_PHONE, "");
    }

    public static long lastSentAt(Context c) {
        return ApiClient.prefs(c).getLong(KEY_LAST_SENT_AT, 0L);
    }

    public static String lastError(Context c) {
        return ApiClient.prefs(c).getString(KEY_LAST_ERROR, "");
    }

    private static JSONArray readArray(SharedPreferences p) {
        try {
            return new JSONArray(p.getString(KEY_QUEUE, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
