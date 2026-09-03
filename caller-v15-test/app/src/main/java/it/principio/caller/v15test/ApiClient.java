package it.principio.caller.v15test;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
  public static final class ApiException extends Exception {
    public final int httpCode; public final boolean authError;
    ApiException(String m,int c,boolean a){ super(m); httpCode=c; authError=a; }
  }
  private ApiClient(){}
  public static String normalize(String raw){ String v=raw==null?"":raw.trim(); while(v.endsWith("/"))v=v.substring(0,v.length()-1); if(v.endsWith("/public/api.php"))return v; if(v.endsWith("/public"))return v+"/api.php"; return v+"/public/api.php"; }
  private static JSONObject call(String endpoint,String fn,JSONArray args) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
    c.setConnectTimeout(5000); c.setReadTimeout(7000); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
    JSONObject body=new JSONObject(); body.put("fn",fn); body.put("args",args);
    try(OutputStream o=c.getOutputStream()){ o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
    int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); StringBuilder s=new StringBuilder();
    if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){ String line; while((line=r.readLine())!=null)s.append(line); }
    JSONObject j=new JSONObject(s.length()==0?"{}":s.toString());
    if(code<200||code>=300||!j.optBoolean("ok",false)){
      String msg=j.optString("error","Errore Manager ("+code+")"); String l=msg.toLowerCase();
      boolean auth=code==401||code==403||l.contains("token")||l.contains("session")||l.contains("operatore")||l.contains("operator")||l.contains("autentic");
      throw new ApiException(msg,code,auth);
    }
    return j.optJSONObject("result");
  }
  public static JSONObject login(String rawUrl,String pin) throws Exception { return call(normalize(rawUrl),"loginOperator",new JSONArray().put(pin)); }
  public static JSONObject sendIncoming(Context c,String phone) throws Exception {
    if(SessionStore.url(c).isEmpty()||SessionStore.token(c).isEmpty()) throw new ApiException("Principio Caller non configurato",401,true);
    try { JSONObject out=call(SessionStore.url(c),"callerRegisterIncoming",new JSONArray().put(phone).put(SessionStore.device(c)).put(SessionStore.token(c))); SessionStore.clearInvalid(c); return out; }
    catch(ApiException e){ if(e.authError)SessionStore.markInvalid(c,e.getMessage()); throw e; }
  }
}
