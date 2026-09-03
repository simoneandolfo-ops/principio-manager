package it.principio.caller.v15test;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public final class SessionStore {
  private static final String PREFS="principio_caller_v15_test";
  private static final String K_URL="manager_url", K_TOKEN="operator_token", K_DEVICE="device_id", K_INVALID="session_invalid", K_REASON="session_reason";
  private SessionStore(){}
  private static SharedPreferences p(Context c){ return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }
  public static String url(Context c){ return p(c).getString(K_URL,""); }
  public static String token(Context c){ return p(c).getString(K_TOKEN,""); }
  public static boolean invalid(Context c){ return p(c).getBoolean(K_INVALID,false); }
  public static String reason(Context c){ return p(c).getString(K_REASON,""); }
  public static void saveLogin(Context c,String url,String token){ p(c).edit().putString(K_URL,url).putString(K_TOKEN,token).putBoolean(K_INVALID,false).putString(K_REASON,"").apply(); }
  public static void markInvalid(Context c,String reason){ p(c).edit().putBoolean(K_INVALID,true).putString(K_REASON,reason==null?"":reason).apply(); }
  public static void clearInvalid(Context c){ p(c).edit().putBoolean(K_INVALID,false).putString(K_REASON,"").apply(); }
  public static String device(Context c){ String id=p(c).getString(K_DEVICE,""); if(id==null||id.isEmpty()){ id=UUID.randomUUID().toString(); p(c).edit().putString(K_DEVICE,id).apply(); } return id; }
}
