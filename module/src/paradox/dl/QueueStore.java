package paradox.dl;
import org.json.*; import java.io.*; import java.util.*;
/** v10.13: the video queue and the offline Watch later list. Two ordered lists of YouTube videos, kept in app-private storage
 *  (files/ytultra_queue.json) with the same care as the downloads list: per-entry parsing (one bad entry never drops the rest), a .bak copy,
 *  a corrupt file is kept aside, atomic write with fsync. Pure Java + org.json (unit-tested on the JVM). */
public final class QueueStore {
  public static final String QUEUE="queue", LATER="later";
  public static final class Entry { public String id, title="", channel="", thumb=""; public long added;
    JSONObject json() throws JSONException { return new JSONObject().put("id",id).put("title",title).put("channel",channel).put("thumb",thumb).put("added",added); }
    static Entry from(JSONObject o){ Entry e=new Entry(); e.id=o.optString("id"); e.title=o.optString("title"); e.channel=o.optString("channel"); e.thumb=o.optString("thumb"); e.added=o.optLong("added"); return e; } }
  private final File file, bak, tmp, corrupt;
  private final List<Entry> queue=new ArrayList<>(), later=new ArrayList<>();
  private boolean loaded;
  public QueueStore(File dir){ file=new File(dir,"ytultra_queue.json"); bak=new File(dir,"ytultra_queue.bak"); tmp=new File(dir,"ytultra_queue.tmp"); corrupt=new File(dir,"ytultra_queue.corrupt"); }
  static boolean validId(String id){ return id!=null && id.matches("[A-Za-z0-9_-]{11}"); }
  static void copy(File a, File b) throws IOException { FileInputStream in=new FileInputStream(a); FileOutputStream o=new FileOutputStream(b); try { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) o.write(buf,0,n); o.flush(); o.getFD().sync(); } finally { in.close(); o.close(); } }
  static String readStream(InputStream in) throws IOException { try { ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) b.write(buf,0,n); return new String(b.toByteArray(),"UTF-8"); } finally { in.close(); } }
  static String read(File f) throws IOException { FileInputStream in=new FileInputStream(f); try { ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) b.write(buf,0,n); return new String(b.toByteArray(),"UTF-8"); } finally { in.close(); } }
  /** @return false when the text is not a usable document at all; bad entries are skipped, not fatal. */
  static boolean parse(String text, List<Entry> q, List<Entry> l){ try { JSONObject o=new JSONObject(text);
      for (int k=0;k<2;k++) { JSONArray a=o.optJSONArray(k==0? QUEUE: LATER); if (a==null) continue; List<Entry> dst= k==0? q: l; Set<String> seen=new HashSet<>();
        for (int i=0;i<a.length();i++) { try { Entry e=Entry.from(a.getJSONObject(i)); if (validId(e.id) && seen.add(e.id)) dst.add(e); } catch (Exception ex){} } }
      return true; } catch (Exception e){ return false; } }
  private void load(){ if (loaded) return; List<Entry> q=new ArrayList<>(), l=new ArrayList<>(); boolean ok=false;
    if (file.exists()) { try { ok=parse(read(file),q,l); } catch (Exception e){ ok=false; } if (!ok) { try { file.renameTo(corrupt); } catch (Exception e){} q.clear(); l.clear(); } }
    if (!ok && bak.exists()) { try { ok=parse(read(bak),q,l); } catch (Exception e){ ok=false; } if (!ok) { q.clear(); l.clear(); } }
    queue.clear(); queue.addAll(q); later.clear(); later.addAll(l); loaded=true; }
  private boolean save(){ try { JSONObject o=new JSONObject(); for (int k=0;k<2;k++) { JSONArray a=new JSONArray(); for (Entry e: (k==0? queue: later)) { try { a.put(e.json()); } catch (Exception ex){} } o.put(k==0? QUEUE: LATER, a); }
      FileOutputStream f=new FileOutputStream(tmp); try { f.write(o.toString().getBytes("UTF-8")); f.flush(); f.getFD().sync(); } finally { f.close(); }
      if (file.exists() && file.length()>2) { try { copy(file,bak); } catch (Exception e){} }
      if (!tmp.renameTo(file)) { copy(tmp,file); tmp.delete(); } return true; } catch (Exception e){ return false; } }
  private List<Entry> list(String name){ return QUEUE.equals(name)? queue: later; }
  public synchronized List<Entry> all(String name){ load(); return new ArrayList<>(list(name)); }
  public synchronized int size(String name){ load(); return list(name).size(); }
  public synchronized int indexOf(String name, String id){ load(); List<Entry> l=list(name); for (int i=0;i<l.size();i++) if (l.get(i).id.equals(id)) return i; return -1; }
  /** Adds at the end; a video already in the list is not added twice (returns false). */
  public synchronized boolean add(String name, String id, String title, String channel){ load(); if (!validId(id) || indexOf(name,id)>=0) return false;
    Entry e=new Entry(); e.id=id; e.title= title==null? "": title; e.channel= channel==null? "": channel; e.thumb="https://i.ytimg.com/vi/"+id+"/mqdefault.jpg"; e.added=System.currentTimeMillis(); list(name).add(e); return save(); }
  public synchronized void setTitle(String name, String id, String title, String channel){ load(); int i=indexOf(name,id); if (i<0) return; Entry e=list(name).get(i); if (title!=null && !title.isEmpty()) e.title=title; if (channel!=null && !channel.isEmpty()) e.channel=channel; save(); }
  public synchronized boolean remove(String name, String id){ load(); int i=indexOf(name,id); if (i<0) return false; list(name).remove(i); return save(); }
  public synchronized void clear(String name){ load(); list(name).clear(); save(); }
  /** Moves the entry by delta places (-1 up, +1 down), clamped. */
  public synchronized boolean move(String name, String id, int delta){ load(); List<Entry> l=list(name); int i=indexOf(name,id); if (i<0) return false; int j=Math.max(0,Math.min(l.size()-1,i+delta)); if (j==i) return false; Entry e=l.remove(i); l.add(j,e); return save(); }
  public synchronized boolean moveToFront(String name, String id){ load(); List<Entry> l=list(name); int i=indexOf(name,id); if (i<0) return false; if (i==0) return true; l.add(0,l.remove(i)); return save(); }
  /** Index of the entry to play after the one with this id (wraps never: -1 at the end). */
  public synchronized int nextIndex(String name, String id){ load(); int i=indexOf(name,id); int n=list(name).size(); return (i>=0 && i+1<n)? i+1: -1; }
  public synchronized int prevIndex(String name, String id){ load(); int i=indexOf(name,id); return i>0? i-1: -1; }
  /** Drops the whole in-memory copy so the next call reads the file again (tests, and after another process wrote). */
  public synchronized void reload(){ loaded=false; }
}
