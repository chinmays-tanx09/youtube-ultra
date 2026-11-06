package paradox.dl;
import android.app.Activity; import android.content.*; import android.net.Uri; import android.os.*; import android.util.Log; import android.widget.Toast;
import java.io.File; import java.util.*;
/** v10.13: what the Queue and Watch later pages do — the shared state of "what is playing from which list", playing an entry (the downloaded copy
 *  when there is one, else YouTube), and adding videos. */
public final class QueueRun {
  static final String TAG="YouTubeUltraQueue";
  private static QueueStore st; private static File stDir;
  static volatile String list, id; static volatile boolean online;
  public static synchronized QueueStore store(Context c){ File d=c.getApplicationContext().getFilesDir(); if (st==null || !d.equals(stDir)) { st=new QueueStore(d); stDir=d; } return st; }
  public static boolean onlineActive(){ return online && id!=null && list!=null; }
  public static String activeId(){ return id; }
  public static void stop(){ list=null; id=null; online=false; }
  /** A finished download of this video that still exists, else null. */
  public static Store.Item downloaded(Context c, String vid){ if (vid==null) return null;
    for (Store.Item i: Store.all(c)) { if (!"done".equals(i.status) || i.audio || i.path==null || i.path.isEmpty()) continue; String v=null; try { v= i.url==null||i.url.isEmpty()? null: Resolve.videoId(i.url); } catch (Throwable t){}
      if (vid.equals(v)) { try { if (Open.exists(c, Open.uri(c,i.path))) return i; } catch (Throwable t){} } }
    return null; }
  public static void play(Context c, String name, int index){ List<QueueStore.Entry> l=store(c).all(name); if (index<0 || index>=l.size()) return; play(c, name, l.get(index)); }
  static void play(Context c, String name, QueueStore.Entry e){ list=name; id=e.id; Store.Item d=downloaded(c,e.id);
    if (d!=null) { online=false; Intent i=new Intent(c, OfflinePlayerActivity.class).putExtra("list",name).putExtra("id",e.id).putExtra("path",d.path); if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
    else { online=true; Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v="+e.id)).setPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); } }
  /** Plays the entry after (dir>0) or before (dir<0) the one playing. Returns false at the end of the list. */
  public static boolean advance(Context c, int dir, boolean auto){ String name=list, cur=id; if (name==null || cur==null) return false; QueueStore s=store(c);
    int idx= dir>0? s.nextIndex(name,cur): s.prevIndex(name,cur);
    if (idx<0) { if (auto) { stop(); toast(c,"End of the list"); } return false; }
    List<QueueStore.Entry> l=s.all(name); play(c,name,l.get(idx)); return true; }
  static void toast(final Context c, final String m){ new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c,m,Toast.LENGTH_SHORT).show()); }
  /** Title and channel from YouTube's public oEmbed (no account, no key). {title, author} or nulls. */
  public static String[] meta(String vid){ try { java.net.HttpURLConnection u=(java.net.HttpURLConnection)new java.net.URL("https://www.youtube.com/oembed?format=json&url="+java.net.URLEncoder.encode("https://www.youtube.com/watch?v="+vid,"UTF-8")).openConnection();
      u.setConnectTimeout(8000); u.setReadTimeout(8000); u.setRequestProperty("User-Agent", Net.UA); if (u.getResponseCode()!=200) return new String[]{null,null};
      org.json.JSONObject o=new org.json.JSONObject(QueueStore.readStream(u.getInputStream())); return new String[]{o.optString("title",null), o.optString("author_name",null)}; } catch (Throwable t){ return new String[]{null,null}; } }
  /** Adds a video link to a list; fetches the title in the background. Returns a one-line message for a toast. */
  public static String add(final Context c, String name, String text, String knownTitle){ final String vid; try { vid=Resolve.videoId(DownloadActivity.findUrl(text)!=null? DownloadActivity.findUrl(text): text); } catch (Throwable t){ return "No video link"; }
    if (vid==null || !QueueStore.validId(vid)) return "No video link";
    final String label= QueueStore.LATER.equals(name)? "Watch later": "Queue"; final QueueStore s=store(c);
    boolean added=s.add(name, vid, knownTitle, null);
    if (!added && s.indexOf(name,vid)>=0) return "Already in "+label;
    if (!added) return "Couldn't save to "+label;
    final String nm=name; new Thread(() -> { String[] m=meta(vid); if (m[0]!=null) s.setTitle(nm, vid, knownTitle!=null&&!knownTitle.isEmpty()? null: m[0], m[1]); else if (m[1]!=null) s.setTitle(nm, vid, null, m[1]); Store.notifyChanged(); }).start();
    if (QueueStore.LATER.equals(name) && Ultra.flag(c, Ultra.AUTODL_KEY, false) && downloaded(c,vid)==null) startDownload(c, vid);
    return "Added to "+label; }
  /** Downloads the video with the quality the person chose in Download settings (else 720p MP4). */
  public static boolean startDownload(Context c, String vid){ try { String q=Prefs.get(c,"video"); int want=720; try { if (q!=null) want=Integer.parseInt(q); } catch (Exception e){}
      Store.Item it=Store.add(c, "https://www.youtube.com/watch?v="+vid, "v:"+want+":mp4", null); DownloadService.start(c, it.id); Store.notifyChanged(); return true; } catch (Throwable t){ Log.w(TAG,"download",t); return false; } }
}
