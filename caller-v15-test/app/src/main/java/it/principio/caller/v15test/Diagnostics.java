package it.principio.caller.v15test;

import android.content.Context;
import android.content.SharedPreferences;

public final class Diagnostics {
  private static SharedPreferences p(Context c){ return c.getSharedPreferences("principio_caller_v15_diag",Context.MODE_PRIVATE); }
  public static void intercepted(Context c,String v){ p(c).edit().putString("last_intercept",v+" · "+System.currentTimeMillis()).apply(); }
  public static void sent(Context c,String v){ p(c).edit().putString("last_sent",v+" · "+System.currentTimeMillis()).putString("last_error","").apply(); }
  public static void error(Context c,String v){ p(c).edit().putString("last_error",v).apply(); }
  public static String intercept(Context c){return p(c).getString("last_intercept","");}
  public static String sent(Context c){return p(c).getString("last_sent","");}
  public static String error(Context c){return p(c).getString("last_error","");}
}
