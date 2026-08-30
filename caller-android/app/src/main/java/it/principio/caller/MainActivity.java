package it.principio.caller;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText urlField;
    private EditText pinField;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshStatus();
    }

    private View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView brand = label("PRINCIPIO",25,true); root.addView(brand);
        TextView subtitle = label("Caller Bridge",14,false); subtitle.setTextColor(Color.rgb(120,120,120)); root.addView(subtitle);
        root.addView(label("\nCollega questo telefono a Principio Manager. Quando arriva una chiamata, il numero viene inviato al Manager senza bloccare la telefonata.\n",15,false));

        root.addView(label("URL Manager",13,true));
        urlField = new EditText(this); urlField.setSingleLine(true); urlField.setHint("https://dominio.it/manager");
        String saved = ApiClient.getManagerUrl(this);
        if (!saved.isEmpty()) urlField.setText(saved.replaceAll("/public/api\\.php$", ""));
        root.addView(urlField, full());

        root.addView(label("PIN operatore / admin",13,true));
        pinField = new EditText(this); pinField.setInputType(2|16); pinField.setHint("PIN usato nel Manager"); root.addView(pinField, full());

        Button connect = button("Collega al Manager"); connect.setOnClickListener(v -> connect()); root.addView(connect, marginTop(16));
        Button role = button("Attiva identificazione chiamate"); role.setOnClickListener(v -> requestCallScreeningRole()); root.addView(role, marginTop(10));
        Button contacts = button("Consenti anche i contatti"); contacts.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},201)); root.addView(contacts, marginTop(10));
        Button test = button("Test invio numero"); test.setOnClickListener(v -> testSend()); root.addView(test, marginTop(10));

        status = label("",14,false); status.setPadding(0,dp(22),0,0); root.addView(status);
        TextView note = label("\nLa chiamata non viene bloccata né silenziata. Il ruolo Identificazione chiamate può essere assegnato a una sola app alla volta.",12,false); note.setTextColor(Color.rgb(110,110,110)); root.addView(note);
        return scroll;
    }

    private void connect() {
        final String rawUrl=urlField.getText().toString().trim(), pin=pinField.getText().toString().trim();
        if(rawUrl.isEmpty()||pin.isEmpty()){setStatus("Inserisci URL e PIN.");return;}
        setStatus("Collegamento...");
        io.execute(() -> {
            try {
                JSONObject result=ApiClient.login(rawUrl,pin);
                String token=result.optString("token","");
                if(token.isEmpty()) throw new IllegalStateException("Token mancante.");
                ApiClient.saveConnection(this,rawUrl,token);
                runOnUiThread(() -> { pinField.setText(""); setStatus("✓ Manager collegato"); refreshStatus(); });
            } catch(Exception e){ runOnUiThread(() -> setStatus("Errore: "+e.getMessage())); }
        });
    }

    private void requestCallScreeningRole() {
        RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);
        if(rm==null||!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)){setStatus("Questo telefono non espone il ruolo Call Screening.");return;}
        if(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)){setStatus("✓ Principio Caller è già attivo.");return;}
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),101);
    }

    private void testSend() {
        setStatus("Invio test...");
        io.execute(() -> {
            try {
                JSONObject out=ApiClient.sendIncoming(this,"+393331234567");
                String who=out==null?"":out.optString("customerName","");
                runOnUiThread(() -> setStatus("✓ Test ricevuto dal Manager"+(who.isEmpty()?"":" · "+who)));
            } catch(Exception e){ runOnUiThread(() -> setStatus("Errore test: "+e.getMessage())); }
        });
    }

    private void refreshStatus() {
        if(status==null)return;
        RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);
        boolean role=rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)&&rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        boolean contacts=checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        boolean connected=!ApiClient.getManagerUrl(this).isEmpty()&&!ApiClient.getToken(this).isEmpty();
        status.setText((connected?"✓":"○")+" Manager collegato\n"+(role?"✓":"○")+" Identificazione chiamate\n"+(contacts?"✓":"○")+" Contatti consentiti");
    }

    private void setStatus(String s){status.setText(s);}    
    @Override protected void onResume(){super.onResume();refreshStatus();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private TextView label(String text,int sp,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(Color.rgb(25,25,25));if(bold)t.setTypeface(null,1);return t;}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(52));return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams marginTop(int top){LinearLayout.LayoutParams p=full();p.topMargin=dp(top);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
