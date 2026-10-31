package paradox.dl;
import android.content.Context; import org.json.*; import java.io.*; import java.util.*;
public final class Store {
  public static final class Item { public String id, url, spec, title="Resolving…", status="queued", path, error, label="", thumb="", channel=""; public long total, done, created; public double speed; public int progress; public boolean audio;
    JSONObject json() throws JSONException { JSONObject o=new JSONObject(); o.put("id",id).put("url",url).put("spec",spec).put("title",title).put("status",status).put("path",path==null?"":path).put("error",error==null?"":error).put("label",label).put("total",total).put("done",done).put("created",created).put("audio",audio).put("thumb",thumb).put("channel",channel); return o; }
    static Item from(JSONObject o){ Item i=new Item(); i.id=o.optString("id"); i.url=o.optString("url"); i.spec=o.optString("spec"); i.title=o.optString("title"); i.status=o.optString("status"); i.path=o.optString("path"); i.error=o.optString("error"); i.label=o.optString("label"); i.total=o.optLong("total"); i.done=o.optLong("done"); i.created=o.optLong("created"); i.audio=o.optBoolean("audio"); i.thumb=o.optString("thumb"); i.channel=o.optString("channel");
      if (i.status.equals("downloading")||i.status.equals("merging")||i.status.equals("resolving")||i.status.equals("queued")) i.status="paused"; if (i.total>0) i.progress=(int)(i.done*100/i.total); return i; } }
  private static final List<Item> items=new ArrayList<>(); private static boolean loaded;
  public interface Listener { void changed(); }
  private static final List<Listener> ls=new ArrayList<>();
  public static synchronized void listen(Listener l){ ls.add(l);} public static synchronized void unlisten(Listener l){ ls.remove(l);} 
  public static void notifyChanged(){ List<Listener> c; synchronized(Store.class){ c=new ArrayList<>(ls);} for (Listener l: c) l.changed(); }
  static File file(Context c){ return new File(c.getFilesDir(),"paradox_downloads.json"); }
  public static synchronized List<Item> all(Context c){ load(c); return new ArrayList<>(items); }
  public static synchronized Item get(Context c, String id){ load(c); for (Item i: items) if (i.id.equals(id)) return i; return null; }
  public static synchronized Item add(Context c, String url, String spec, String title){ load(c); Item i=new Item(); i.id=UUID.randomUUID().toString(); i.url=url; i.spec=spec; if (title!=null) i.title=title; i.created=System.currentTimeMillis(); i.audio=spec.startsWith("a:"); items.add(0,i); save(c); return i; }
  public static synchronized void remove(Context c, Item i){ load(c); items.remove(i); save(c); }
  static void load(Context c){ if (loaded) return; loaded=true; try { File f=file(c); if (!f.exists()) return; FileInputStream in=new FileInputStream(f); byte[] b=new byte[(int)f.length()]; int o=0,n; while(o<b.length&&(n=in.read(b,o,b.length-o))>0) o+=n; in.close();
      JSONArray a=new JSONArray(new String(b,"UTF-8")); for (int k=0;k<a.length();k++) items.add(Item.from(a.getJSONObject(k))); } catch(Exception e){} }
  public static synchronized void save(Context c){ try { JSONArray a=new JSONArray(); for (Item i: items) a.put(i.json()); File t=new File(c.getFilesDir(),"paradox_downloads.tmp"); FileOutputStream o=new FileOutputStream(t); o.write(a.toString().getBytes("UTF-8")); o.close(); t.renameTo(file(c)); } catch(Exception e){} }
}
