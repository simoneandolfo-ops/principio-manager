package it.principio.caller.v15test;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class PendingCallStore {
  private static final String PREFS="principio_caller_v15_queue";
  private static final String KEY="pending_call_queue_v11";
  public static final class Item { public final String phone; public final long at; Item(String p,long a){phone=p;at=a;} }
  private PendingCallStore(){}
  private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
  public static synchronized void add(Context c,String phone){ List<Item> list=all(c); list.add(new Item(phone,System.currentTimeMillis())); save(c,list); }
  public static synchronized List<Item> all(Context c){ List<Item> out=new ArrayList<>(); try{ JSONArray a=new JSONArray(p(c).getString(KEY,"[]")); for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i); out.add(new Item(o.optString("phone",""),o.optLong("at",0)));}}catch(Exception ignored){} return out; }
  public static synchronized int count(Context c){return all(c).size();}
  public static synchronized void removeFirst(Context c){ List<Item> l=all(c); if(!l.isEmpty()){l.remove(0);save(c,l);} }
  private static void save(Context c,List<Item> list){ JSONArray a=new JSONArray(); try{for(Item i:list){JSONObject o=new JSONObject();o.put("phone",i.phone);o.put("at",i.at);a.put(o);}}catch(Exception ignored){} p(c).edit().putString(KEY,a.toString()).apply(); }
}
