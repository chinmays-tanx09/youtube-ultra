package paradox.dl;
import android.content.Context; import org.json.*; import java.io.*; import java.util.*;
public final class Store {
  public static final class Item { public String id, url, spec, title="Resolving…", status="queued", path, error, label="", thumb="", channel=""; public long total, done, created; public double speed; public int progress; public boolean audio;
    JSONObject json() throws JSONException { JSONObject o=new JSONObject(); o.put("id",id).put("url",url).put("spec",spec).put("title",title).put("status",status).put("path",path==null?"":path).put("error",error==null?"":error).put("label",label).put("total",total).put("done",done).put("created",created).put("audio",audio).put("thumb",thumb).put("channel",channel); return o; }
    static Item from(JSONObject o){ Item i=new Item(); i.id=o.optString("id"); i.url=o.optString("url"); i.spec=o.optString("spec"); i.title=o.optString("title"); i.status=o.optString("status"); i.path=o.optString("path"); i.error=o.optString("error"); i.label=o.optString("label"); i.total=o.optLong("total"); i.done=o.optLong("done"); i.created=o.optLong("created"); i.audio=o.optBoolean("audio"); i.thumb=o.optString("thumb"); i.channel=o.optString("channel");
      if (i.status.equals("downloading")||i.status.equals("merging")||i.status.equals("converting")||i.status.equals("resolving")||i.status.equals("queued")) i.status="paused"; if (i.total>0) i.progress=(int)(i.done*100/i.total); return i; } }
  private static final List<Item> items=new ArrayList<>(); private static boolean loaded;
  public interface Listener { void changed(); }
  private static final List<Listener> ls=new ArrayList<>();
  public static synchronized void listen(Listener l){ ls.add(l);} public static synchronized void unlisten(Listener l){ ls.remove(l);} 
  public static void notifyChanged(){ List<Listener> c; synchronized(Store.class){ c=new ArrayList<>(ls);} for (Listener l: c) l.changed(); }
  static File file(Context c){ return new File(c.getFilesDir(),"paradox_downloads.json"); }
  static File bak(Context c){ return new File(c.getFilesDir(),"paradox_downloads.bak"); }
  static File ignoredFile(Context c){ return new File(c.getFilesDir(),"paradox_downloads_ignored.json"); }
  public static synchronized List<Item> all(Context c){ load(c); return new ArrayList<>(items); }
  public static synchronized Item get(Context c, String id){ load(c); for (Item i: items) if (i.id.equals(id)) return i; return null; }
  public static synchronized Item add(Context c, String url, String spec, String title){ load(c); Item i=new Item(); i.id=UUID.randomUUID().toString(); i.url=url; i.spec=spec; if (title!=null) i.title=title; i.created=System.currentTimeMillis(); i.audio=spec.startsWith("a:"); items.add(0,i); save(c); return i; }
  public static synchronized void remove(Context c, Item i){ load(c); items.remove(i); if (i.path!=null && !i.path.isEmpty()) ignore(c, i.path); save(c); }
  // ---- v10.12: the list can no longer lose finished downloads -------------------------------------------------------------------
  // Before: any read error made load() give up with an empty list and the next save() overwrote the file, so every earlier download vanished from the
  // gallery; one bad entry also dropped all entries after it; a failed rename kept the old file. Now: per-entry parsing, a .bak copy, a corrupt file is kept aside.
  static String readAll(File f) throws IOException { FileInputStream in=new FileInputStream(f); try { byte[] b=new byte[(int)f.length()]; int o=0,n; while(o<b.length&&(n=in.read(b,o,b.length-o))>0) o+=n; return new String(b,0,o,"UTF-8"); } finally { in.close(); } }
  static int parseInto(String text, List<Item> out){ int bad=0; try { JSONArray a=new JSONArray(text); for (int k=0;k<a.length();k++) { try { Item it=Item.from(a.getJSONObject(k)); if (it.id!=null && !it.id.isEmpty()) out.add(it); else bad++; } catch (Exception e){ bad++; } } } catch (Exception e){ return -1; } return bad; }
  static void load(Context c){ if (loaded) return; File f=file(c), b=bak(c); List<Item> got=new ArrayList<>(); boolean ok=false;
    if (f.exists()) { try { ok= parseInto(readAll(f), got)>=0; } catch (Exception e){ ok=false; }
      if (!ok) { try { f.renameTo(new File(c.getFilesDir(),"paradox_downloads.corrupt")); } catch (Exception e){} got.clear(); } }
    if (!ok && b.exists()) { try { ok= parseInto(readAll(b), got)>=0; } catch (Exception e){ ok=false; } if (!ok) got.clear(); }
    items.clear(); items.addAll(got); loaded=true; }
  public static synchronized void save(Context c){ try { load(c); JSONArray a=new JSONArray(); for (Item i: items) { try { a.put(i.json()); } catch (Exception e){} }
      File t=new File(c.getFilesDir(),"paradox_downloads.tmp"); FileOutputStream o=new FileOutputStream(t); try { o.write(a.toString().getBytes("UTF-8")); o.flush(); o.getFD().sync(); } finally { o.close(); }
      File f=file(c); if (f.exists() && f.length()>2) { try { copyFile(f, bak(c)); } catch (Exception e){} }
      if (!t.renameTo(f)) { copyFile(t, f); t.delete(); } } catch(Exception e){ android.util.Log.w("ParadoxDL","store save",e); } }
  static void copyFile(File a, File b) throws IOException { FileInputStream in=new FileInputStream(a); FileOutputStream o=new FileOutputStream(b); try { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) o.write(buf,0,n); } finally { in.close(); o.close(); } }
  static synchronized Set<String> ignored(Context c){ Set<String> s=new HashSet<>(); try { File f=ignoredFile(c); if (f.exists()) { JSONArray a=new JSONArray(readAll(f)); for (int k=0;k<a.length();k++) s.add(a.getString(k)); } } catch (Exception e){} return s; }
  static synchronized void ignore(Context c, String path){ try { Set<String> s=ignored(c); if (!s.add(path)) return; JSONArray a=new JSONArray(); for (String x: s) a.put(x); FileOutputStream o=new FileOutputStream(ignoredFile(c)); try { o.write(a.toString().getBytes("UTF-8")); } finally { o.close(); } } catch (Exception e){} }

  /** One saved file as a finished item (what adoptSaved adds). */
  static Item savedItem(String path, String name, long size, long addedSec, boolean audio){ Item i=new Item(); i.id=UUID.nameUUIDFromBytes(("saved:"+path).getBytes()).toString(); i.url=""; i.spec= audio? "a:saved": "v:saved"; i.audio=audio; i.status="done"; i.path=path;
    String t=name==null? "": name; int d=t.lastIndexOf('.'); if (d>0) t=t.substring(0,d); i.title=t.isEmpty()? "Saved file": t; i.total=size; i.done=size; i.progress=100; i.created= addedSec>0? addedSec*1000: System.currentTimeMillis(); return i; }
  /** v10.12: every file YouTube Ultra saved (Movies/YouTube Ultra, Music/YouTube Ultra) is also listed here, even when its entry was lost
   *  (cleared app data, an older version, a failed save). Runs off the main thread. Returns true if something was added. */
  public static boolean adoptSaved(Context c){ boolean changed=false;
    try { Set<String> known=new HashSet<>(); for (Item i: all(c)) if (i.path!=null) known.add(i.path); Set<String> skip=ignored(c); List<Item> add=new ArrayList<>();
      if (android.os.Build.VERSION.SDK_INT>=29) {
        for (int k=0;k<2;k++) { boolean audio=k==1; android.net.Uri coll= audio? android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY): android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
          String rel=(audio? android.os.Environment.DIRECTORY_MUSIC: android.os.Environment.DIRECTORY_MOVIES)+"/"+DownloadService.DIR+"/%";
          android.database.Cursor cu=null; try { cu=c.getContentResolver().query(coll, new String[]{android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.DATE_ADDED}, android.provider.MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?", new String[]{rel}, android.provider.MediaStore.MediaColumns.DATE_ADDED+" DESC");
            while (cu!=null && cu.moveToNext()) { String path=android.content.ContentUris.withAppendedId(coll, cu.getLong(0)).toString(); if (known.contains(path) || skip.contains(path)) continue; long size=cu.getLong(2); if (size<=0) continue; add.add(savedItem(path, cu.getString(1), size, cu.getLong(3), audio)); known.add(path); } }
          catch (Throwable t){ android.util.Log.w("ParadoxDL","adopt query",t); } finally { if (cu!=null) cu.close(); } }
      } else {
        for (int k=0;k<2;k++) { boolean audio=k==1; File dir=new File(android.os.Environment.getExternalStoragePublicDirectory(audio? android.os.Environment.DIRECTORY_MUSIC: android.os.Environment.DIRECTORY_MOVIES), DownloadService.DIR); File[] fs=dir.listFiles(); if (fs==null) continue;
          for (File f: fs) { String n=f.getName().toLowerCase(Locale.ROOT); if (!f.isFile() || !(n.endsWith(".mp4") || n.endsWith(".mp3")) || f.length()<=0) continue; String path=f.getAbsolutePath(); if (known.contains(path) || skip.contains(path)) continue; add.add(savedItem(path, f.getName(), f.length(), f.lastModified()/1000, n.endsWith(".mp3"))); known.add(path); } } }
      if (!add.isEmpty()) { synchronized(Store.class){ load(c); for (Item i: add) { boolean dup=false; for (Item x: items) if (x.id.equals(i.id) || i.path.equals(x.path)) { dup=true; break; } if (!dup) { items.add(i); changed=true; } } if (changed) { Collections.sort(items, new Comparator<Item>(){ public int compare(Item a, Item b){ return Long.compare(b.created, a.created); } }); save(c); } } }
    } catch (Throwable t){ android.util.Log.w("ParadoxDL","adoptSaved",t); }
    return changed; }
}
