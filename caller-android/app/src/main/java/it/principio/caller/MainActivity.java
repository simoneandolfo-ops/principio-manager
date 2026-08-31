package it.principio.caller;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText urlField;
    private EditText pinField;
    private TextView status;
    private TextView diagnostics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshStatus();
        CallerUploadWorker.enqueue(this);
    }

    private android.view.View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        root.addView(label("PRINCIPIO", 25, true));
        TextView subtitle = label("Caller Bridge · 1.2", 14, false);
        subtitle.setTextColor(Color.rgb(120,120,120));
        root.addView(subtitle);

        root.addView(label("\nIl numero viene salvato subito e inviato direttamente mentre Android gestisce la chiamata. La coda background resta come sicurezza.\n", 15, false));

        root.addView(label("URL Manager", 13, true));
        urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setHint("https://dominio.it/manager");
        String saved = ApiClient.getManagerUrl(this);
        if (!saved.isEmpty()) urlField.setText(saved.replaceAll("/public/api\\.php$", ""));
        root.addView(urlField, full());

        root.addView(label("PIN operatore / admin", 13, true));
        pinField = new EditText(this);
        pinField.setInputType(2 | 16);
        pinField.setHint("PIN usato nel Manager");
        root.addView(pinField, full());

        Button connect = button("COLLEGA AL MANAGER");
        connect.setOnClickListener(v -> connect());
        root.addView(connect, marginTop(16));

        Button role = button("ATTIVA IDENTIFICAZIONE CHIAMATE");
        role.setOnClickListener(v -> requestCallScreeningRole());
        root.addView(role, marginTop(10));

        Button contacts = button("CONSENTI ANCHE I CONTATTI");
        contacts.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 201));
        root.addView(contacts, marginTop(10));

        Button test = button("TEST INVIO DIRETTO");
        test.setOnClickListener(v -> testDirect());
        root.addView(test, marginTop(10));

        Button retry = button("INVIA ORA I NUMERI IN ATTESA");
        retry.setOnClickListener(v -> flushQueueDirect());
        root.addView(retry, marginTop(10));

        status = label("", 14, false);
        status.setPadding(0, dp(22), 0, 0);
        root.addView(status);

        diagnostics = label("", 13, false);
        diagnostics.setPadding(0, dp(12), 0, 0);
        diagnostics.setTextColor(Color.rgb(90,90,90));
        root.addView(diagnostics);

        TextView note = label("\nLa telefonata non viene bloccata né registrata. Se l'invio diretto fallisce, il numero resta sul telefono finché non viene confermato dal Manager.", 12, false);
        note.setTextColor(Color.rgb(110,110,110));
        root.addView(note);
        return scroll;
    }

    private void connect() {
        final String rawUrl = urlField.getText().toString().trim();
        final String pin = pinField.getText().toString().trim();
        if (rawUrl.isEmpty() || pin.isEmpty()) { setStatus("Inserisci URL e PIN."); return; }
        setStatus("Collegamento...");
        io.execute(() -> {
            try {
                JSONObject result = ApiClient.login(rawUrl, pin);
                String token = result.optString("token", "");
                if (token.isEmpty()) throw new IllegalStateException("Token mancante.");
                ApiClient.saveConnection(this, rawUrl, token);
                runOnUiThread(() -> { pinField.setText(""); setStatus("✓ Manager collegato"); refreshStatus(); });
                flushQueueDirect();
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Errore: " + e.getMessage()));
            }
        });
    }

    private void requestCallScreeningRole() {
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { setStatus("Questo telefono non espone il ruolo Call Screening."); return; }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) { setStatus("✓ Principio Caller è già il servizio di identificazione chiamate."); return; }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 101);
    }

    private void testDirect() {
        setStatus("Test invio diretto...");
        io.execute(() -> {
            PendingCallStore.Item item = null;
            try {
                item = PendingCallStore.enqueue(this, "+393331234567");
                ApiClient.sendIncoming(this, item.phone, item.id);
                PendingCallStore.markSent(this, item.id, item.phone);
                runOnUiThread(() -> { setStatus("✓ Test ricevuto dal Manager"); refreshStatus(); });
            } catch (Exception e) {
                PendingCallStore.markError(this, e.getMessage());
                runOnUiThread(() -> { setStatus("Errore invio: " + e.getMessage()); refreshStatus(); });
            }
        });
    }

    private void flushQueueDirect() {
        setStatus("Invio diretto della coda...");
        io.execute(() -> {
            try {
                List<PendingCallStore.Item> items = PendingCallStore.all(this);
                int sent = 0;
                for (PendingCallStore.Item item : items) {
                    ApiClient.sendIncoming(this, item.phone, item.id);
                    PendingCallStore.markSent(this, item.id, item.phone);
                    sent++;
                }
                final int total = sent;
                runOnUiThread(() -> { setStatus("✓ Inviati: " + total); refreshStatus(); });
            } catch (Exception e) {
                PendingCallStore.markError(this, e.getMessage());
                runOnUiThread(() -> { setStatus("Errore invio: " + e.getMessage()); refreshStatus(); });
            }
        });
    }

    private void refreshStatus() {
        if (status == null) return;
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        boolean role = rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean connected = !ApiClient.getManagerUrl(this).isEmpty() && !ApiClient.getToken(this).isEmpty();
        int pending = PendingCallStore.count(this);
        status.setText((connected ? "✓" : "○") + " Manager collegato\n" + (role ? "✓" : "○") + " Identificazione chiamate\n" + (contacts ? "✓" : "○") + " Contatti consentiti\n" + (pending == 0 ? "✓" : "!") + " In attesa di invio: " + pending);

        StringBuilder d = new StringBuilder();
        long interceptedAt = PendingCallStore.lastInterceptAt(this);
        long sentAt = PendingCallStore.lastSentAt(this);
        if (interceptedAt > 0) d.append("Ultima intercettata: ").append(PendingCallStore.lastInterceptPhone(this)).append(" · ").append(formatTime(interceptedAt)).append("\n");
        if (sentAt > 0) d.append("Ultimo invio riuscito: ").append(PendingCallStore.lastSentPhone(this)).append(" · ").append(formatTime(sentAt)).append("\n");
        String err = PendingCallStore.lastError(this);
        if (!err.isEmpty()) d.append("Ultimo errore: ").append(err);
        if (d.length() == 0) d.append("Nessuna chiamata ancora intercettata.");
        diagnostics.setText(d.toString().trim());
    }

    private String formatTime(long millis) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(millis)); }
    private void setStatus(String s) { status.setText(s); }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }
    @Override protected void onDestroy() { io.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private TextView label(String text, int sp, boolean bold) { TextView t = new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(Color.rgb(25,25,25)); if (bold) t.setTypeface(null,1); return t; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(52)); return b; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams marginTop(int top) { LinearLayout.LayoutParams p = full(); p.topMargin = dp(top); return p; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
