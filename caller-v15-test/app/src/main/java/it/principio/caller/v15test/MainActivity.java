package it.principio.caller.v15test;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private EditText url,pin; private TextView status,diag;
  @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(build());refresh();}

  private View build(){
    ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(20),dp(20),dp(30)); root.setBackgroundColor(Color.WHITE); sv.addView(root);
    TextView brand=t("PRINCIPIO",26,true);root.addView(brand); TextView sub=t("Caller V1.5 TEST · Session Fix",14,false);sub.setTextColor(Color.DKGRAY);root.addView(sub);
    root.addView(t("\nVersione di test separata dalla V1.4 stabile. La chiamata viene lasciata libera immediatamente; il numero viene messo in coda locale e inviato al Manager.\n",14,false));
    root.addView(t("URL Manager",14,true)); url=new EditText(this);url.setSingleLine(true);url.setHint("https://www.principio-roccalumera.com/manager");String saved=SessionStore.url(this);if(!saved.isEmpty())url.setText(saved.replaceAll("/public/api\\.php$",""));root.addView(url);
    root.addView(t("PIN operatore / admin",14,true));pin=new EditText(this);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setHint("PIN usato nel Manager");root.addView(pin);
    Button connect=b("COLLEGA AL MANAGER");connect.setOnClickListener(v->connect());root.addView(connect,mt(14));
    Button role=b("ATTIVA IDENTIFICAZIONE CHIAMATE");role.setOnClickListener(v->role());root.addView(role,mt(10));
    Button contacts=b("CONSENTI ANCHE I CONTATTI");contacts.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},201));root.addView(contacts,mt(10));
    Button test=b("TEST INVIO DIRETTO");test.setOnClickListener(v->test());root.addView(test,mt(10));
    Button flush=b("INVIA ORA I NUMERI IN ATTESA");flush.setOnClickListener(v->enqueue());root.addView(flush,mt(10));
    status=t("",15,false);status.setPadding(0,dp(18),0,0);root.addView(status);diag=t("",12,false);diag.setTextColor(Color.DKGRAY);diag.setPadding(0,dp(12),0,0);root.addView(diag);
    TextView note=t("\nNOVITÀ V1.5 TEST: se il Manager rifiuta il token, l'app NON continua a mostrarsi collegata. Compare 'Sessione Manager da rinnovare'; i numeri restano in coda. Dopo un nuovo collegamento la coda riparte automaticamente.",12,false);note.setTextColor(Color.DKGRAY);root.addView(note);
    return sv;
  }
  private void connect(){String raw=url.getText().toString().trim(),p=pin.getText().toString().trim();if(raw.isEmpty()||p.isEmpty()){status.setText("Inserisci URL e PIN.");return;}status.setText("Collegamento...");io.execute(()->{try{JSONObject r=ApiClient.login(raw,p);String tok=r==null?"":r.optString("token","");if(tok.isEmpty())throw new Exception("Token mancante");SessionStore.saveLogin(this,ApiClient.normalize(raw),tok);runOnUiThread(()->{pin.setText("");refresh();enqueue();});}catch(Exception e){runOnUiThread(()->status.setText("Errore collegamento: "+e.getMessage()));}});}
  private void role(){RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);if(rm==null||!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)){status.setText("Ruolo identificazione chiamate non disponibile.");return;}if(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)){refresh();return;}startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),101);}
  private void test(){io.execute(()->{try{ApiClient.sendIncoming(this,"+393331234567");Diagnostics.sent(this,"+393331234567");runOnUiThread(this::refresh);}catch(Exception e){Diagnostics.error(this,e.getClass().getSimpleName()+": "+e.getMessage());runOnUiThread(this::refresh);}});}
  private void enqueue(){Constraints c=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(CallerUploadWorker.class).setConstraints(c).build());status.postDelayed(this::refresh,1200);}
  private void refresh(){if(status==null)return;RoleManager rm=(RoleManager)getSystemService(ROLE_SERVICE);boolean role=rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)&&rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);boolean contacts=checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;boolean configured=!SessionStore.url(this).isEmpty()&&!SessionStore.token(this).isEmpty();String first=SessionStore.invalid(this)?"! Sessione Manager da rinnovare":(configured?"✓ Manager collegato":"○ Manager non collegato");status.setText(first+"\n"+(role?"✓":"○")+" Identificazione chiamate\n"+(contacts?"✓":"○")+" Contatti consentiti\n"+(PendingCallStore.count(this)>0?"!":"✓")+" In attesa di invio: "+PendingCallStore.count(this));String reason=SessionStore.reason(this);diag.setText((reason.isEmpty()?"":"Motivo sessione: "+reason+"\n")+"Ultimo invio: "+Diagnostics.sent(this)+"\nUltimo errore: "+Diagnostics.error(this));}
  @Override protected void onResume(){super.onResume();refresh();}
  @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
  private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(25,25,25));if(bold)v.setTypeface(null,1);return v;}
  private Button b(String s){Button v=new Button(this);v.setText(s);v.setAllCaps(false);v.setMinHeight(dp(54));return v;}
  private LinearLayout.LayoutParams mt(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
