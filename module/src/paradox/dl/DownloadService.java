package paradox.dl;
import android.app.*; import android.content.*; import android.os.*; import android.content.pm.ServiceInfo; import android.media.MediaScannerConnection;
import android.provider.MediaStore; import android.net.Uri; import android.util.Log;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*;
import java.io.*; import java.util.*; import java.util.concurrent.*;
public class DownloadService extends Service {
  static final String CH="paradox_dl", TAG="ParadoxDL"; static final int NID=7301;
  public static final String DIR="YouTube Ultra";
  private final ExecutorService pool=Executors.newFixedThreadPool(2);
  private final Map<String,Engine> running=new ConcurrentHashMap<>();
  private final Handler h=new Handler(Looper.getMainLooper()); private int active;
  private PowerManager.WakeLock wake; private android.net.wifi.WifiManager.WifiLock wifi;
  /** Keep the CPU and Wi-Fi at full speed while anything is downloading (screen off / app in background). */
  synchronized void lock(boolean on){ try {
    if (on){ if (wake==null){ wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "paradox:download"); wake.setReferenceCounted(false); }
      if (!wake.isHeld()) wake.acquire(4L*3600*1000);
      if (wifi==null){ android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE); if (wm!=null){ wifi=wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "paradox:download"); wifi.setReferenceCounted(false); } }
      if (wifi!=null && !wifi.isHeld()) wifi.acquire();
    } else { if (wake!=null && wake.isHeld()) wake.release(); if (wifi!=null && wifi.isHeld()) wifi.release(); }
  } catch (Throwable t){ Log.w(TAG,"lock",t); } }
  @Override public void onDestroy(){ lock(false); super.onDestroy(); }
  public static void start(Context c, String id){ Intent i=new Intent(c, DownloadService.class).putExtra("id", id); if (Build.VERSION.SDK_INT>=26) c.startForegroundService(i); else c.startService(i); }
  public static void cancel(Context c, String id){ c.startService(new Intent(c, DownloadService.class).putExtra("cancel", id)); }
  @Override public IBinder onBind(Intent i){ return null; }
  Notification note(String text, int p){
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT>=26 && nm.getNotificationChannel(CH)==null) nm.createNotificationChannel(new NotificationChannel(CH,"Downloads",NotificationManager.IMPORTANCE_LOW));
    PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this, DownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b= Build.VERSION.SDK_INT>=26? new Notification.Builder(this,CH): new Notification.Builder(this);
    b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
    if (p>=0) b.setProgress(100,p,false); return b.build(); }
  void fg(String text, int p){ Notification n=note(text,p); if (Build.VERSION.SDK_INT>=29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(NID,n); }
  @Override public int onStartCommand(Intent in, int f, int sid){
    fg("Preparing…", -1);
    if (in!=null && in.getStringExtra("cancel")!=null){ Engine e=running.get(in.getStringExtra("cancel")); if (e!=null) e.cancelled=true; else maybeStop(); return START_NOT_STICKY; }
    final String id= in==null? null: in.getStringExtra("id"); if (id==null){ maybeStop(); return START_NOT_STICKY; }
    final Store.Item it=Store.get(this,id); if (it==null || running.containsKey(id)) { maybeStop(); return START_NOT_STICKY; }
    final Engine eng=new Engine(); running.put(id, eng); synchronized(this){ active++; } lock(true); it.status="queued"; it.error=""; Store.notifyChanged();
    pool.submit(new Runnable(){ public void run(){ try { work(it, eng); } catch(Throwable t){ Log.e(TAG,"download failed",t); it.status= eng.cancelled && eng.error==null? "cancelled":"failed"; it.error= eng.error!=null? eng.error: String.valueOf(t.getMessage()); }
      finally { running.remove(it.id); if (Store.get(DownloadService.this, it.id)==null) cleanup(it.id); Store.save(DownloadService.this); Store.notifyChanged(); synchronized(DownloadService.this){ active--; } h.post(new Runnable(){ public void run(){ maybeStop(); }}); } } });
    return START_NOT_STICKY; }
  void maybeStop(){ synchronized(this){ if (active>0) return; } lock(false); stopForeground(true); stopSelf(); }
  File tmpDir(String id){ return new File(getExternalCacheDir()!=null? getExternalCacheDir(): getCacheDir(), "dl_"+id); }
  /** Removed from the list: drop its partial files. */
  void cleanup(String id){ try { File d=tmpDir(id); File[] fs=d.listFiles(); if (fs!=null) for (File x: fs) x.delete(); d.delete(); } catch (Throwable t){} }
  static int itagOf(String url){ try { java.util.regex.Matcher m=java.util.regex.Pattern.compile("[?&]itag=(\\d+)").matcher(url); return m.find()? Integer.parseInt(m.group(1)): -1; } catch (Throwable t){ return -1; } }
  /** Same stream (itag) from a fresh extraction, so a refreshed URL never switches to a different file mid-download. */
  static String sameStream(StreamInfo i, String oldUrl){ int it=itagOf(oldUrl); if (it<0) return null;
    List<Stream> all=new ArrayList<>(); all.addAll(i.getVideoOnlyStreams()); all.addAll(i.getAudioStreams()); all.addAll(i.getVideoStreams());
    for (Stream s: all) { try { if (s.isUrl() && s.getItagItem()!=null && s.getItagItem().id==it) return s.getContent(); } catch (Throwable t){} } return null; }
  static String clean(String s){ String r=s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim(); if (r.length()>120) r=r.substring(0,120); return r.isEmpty()? "video": r; }
  void work(final Store.Item it, final Engine eng) throws Exception {
    Net.init(); it.status="resolving"; Store.notifyChanged();
    final StreamInfo[] info={StreamInfo.getInfo(ServiceList.YouTube, it.url)}; it.title=info[0].getName(); it.channel=info[0].getUploaderName();
    final StreamInfo ti=info[0]; new Thread(new Runnable(){ public void run(){ try { java.util.List<org.schabi.newpipe.extractor.Image> th=ti.getThumbnails(); String tu=null; int best=0; for (org.schabi.newpipe.extractor.Image im: th){ int w=im.getWidth(); if (tu==null || (w>best && w<=720)) { tu=im.getUrl(); best=w; } }
      if (tu!=null){ File td=new File(getFilesDir(),"thumbs"); td.mkdirs(); File tf=new File(td, it.id+".jpg"); java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(tu).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        InputStream tin=c.getInputStream(); FileOutputStream to=new FileOutputStream(tf); byte[] bb=new byte[16384]; int nn; while((nn=tin.read(bb))>0) to.write(bb,0,nn); to.close(); tin.close(); it.thumb=tf.getAbsolutePath(); Store.notifyChanged(); } } catch(Throwable t){ Log.w(TAG,"thumb",t); } } }).start();
    final Formats.Pick[] pk={Formats.pick(info[0], it.spec)}; if (pk[0]==null) throw new IOException("format not available");
    it.label=pk[0].label;
    final List<String> parts=new ArrayList<>(); List<Long> lens=new ArrayList<>();
    if (pk[0].videoUrl!=null){ parts.add("v"); lens.add(pk[0].videoLen);} if (pk[0].audioUrl!=null){ parts.add("a"); lens.add(pk[0].audioLen);} 
    File tmp=tmpDir(it.id); tmp.mkdirs();
    File[] files=new File[parts.size()]; long[] L=new long[parts.size()];
    for (int i=0;i<files.length;i++){ files[i]=new File(tmp, parts.get(i)+".part"); L[i]=lens.get(i); }
    Engine.UrlSource src=new Engine.UrlSource(){ public String url(int p){ return parts.get(p).equals("v")? pk[0].videoUrl: pk[0].audioUrl; }
      public String refresh(int p) throws Exception { String old=url(p); info[0]=StreamInfo.getInfo(ServiceList.YouTube, it.url); String same=sameStream(info[0], old);
        if (same!=null) { if (parts.get(p).equals("v")) pk[0].videoUrl=same; else pk[0].audioUrl=same; return same; }
        throw new IOException("stream no longer available"); } };
    it.status="downloading"; Store.notifyChanged();
    final long t0=SystemClock.elapsedRealtime(); final long[] last={-1,t0};
    Timer tm=new Timer(); tm.scheduleAtFixedRate(new TimerTask(){ public void run(){ long now=SystemClock.elapsedRealtime(), d=eng.done.get(); if (last[0]<0){ last[0]=eng.resumed; }
      double inst=(d-last[0])*1000.0/Math.max(1,now-last[1]); it.speed= it.speed==0? inst: it.speed*0.6+inst*0.4; last[0]=d; last[1]=now;
      it.done=d; it.total=eng.total; it.progress= eng.total>0? (int)(d*100/eng.total):0; Store.notifyChanged();
      try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NID, note(it.title+" · "+it.progress+"% · "+String.format(Locale.US,"%.1f MB/s", it.speed/1048576), it.progress)); } catch(Exception e){} } }, 500, 700);
    try { eng.run(src, L, files); } finally { tm.cancel(); }
    long secs=Math.max(1, SystemClock.elapsedRealtime()-t0);
    long got=eng.total-eng.resumed; it.done=eng.total; it.total=eng.total; it.progress=100; it.speed=got*1000.0/secs;
    Log.i(TAG, "downloaded "+got+" bytes (resumed from "+eng.resumed+") in "+secs+" ms = "+String.format(Locale.US,"%.2f MB/s", got*1000.0/secs/1048576)+" connections="+Engine.CONNECTIONS);
    it.status="merging"; Store.notifyChanged();
    boolean audio=pk[0].audioOnly; boolean opus= audio && pk[0].ext.equals("opus"); boolean webm=!audio && pk[0].ext.endsWith("webm");
    boolean ogg= opus && Build.VERSION.SDK_INT>=29;
    String ext= audio? (pk[0].ext.equals("m4a")? "m4a": (ogg? "opus":"webm")) : (webm? "webm":"mp4");
    String mime= audio? (ext.equals("m4a")? "audio/mp4": ext.equals("opus")? "audio/ogg":"audio/webm") : (webm? "video/webm":"video/mp4");
    int muxFmt= audio? (ext.equals("m4a")? 0: ogg? 2: 1) : (webm? 1: 0);
    String name=clean(it.title)+(audio? "": " ["+pk[0].label+"]")+"."+ext;
    String vPath= pk[0].videoUrl!=null? files[0].getAbsolutePath(): null; String aPath= pk[0].audioUrl!=null? files[files.length-1].getAbsolutePath(): null;
    if (Build.VERSION.SDK_INT>=29) {
      Uri coll= audio? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY): MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name); cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
      cv.put(MediaStore.MediaColumns.RELATIVE_PATH, (audio? Environment.DIRECTORY_MUSIC: Environment.DIRECTORY_MOVIES)+"/"+DIR); cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
      if (!audio) cv.put(MediaStore.MediaColumns.TITLE, it.title);
      Uri u=getContentResolver().insert(coll, cv); if (u==null) throw new IOException("MediaStore insert failed");
      try { ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"rw");
        if (pk[0].progressive) copy(files[0], new FileOutputStream(pfd.getFileDescriptor()));
        else Mux.merge(vPath, aPath, pfd.getFileDescriptor(), null, muxFmt);
        pfd.close();
        ContentValues done=new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING, 0); getContentResolver().update(u, done, null, null);
      } catch (Exception e) { getContentResolver().delete(u,null,null); throw e; }
      it.path=u.toString();
    } else {
      File dir=new File(Environment.getExternalStoragePublicDirectory(audio? Environment.DIRECTORY_MUSIC: Environment.DIRECTORY_MOVIES), DIR); dir.mkdirs();
      File out=new File(dir, name); int n=1; while (out.exists()) out=new File(dir, name.replaceFirst("(\\.[^.]+)$"," ("+(n++)+")$1"));
      if (pk[0].progressive) copy(files[0], new FileOutputStream(out)); else Mux.merge(vPath, aPath, null, out.getAbsolutePath(), muxFmt);
      it.path=out.getAbsolutePath(); MediaScannerConnection.scanFile(this, new String[]{it.path}, null, null);
    }
    for (File x: files) { x.delete(); Engine.mapFile(x).delete(); } tmp.delete();
    it.status="done"; Store.save(this);
    try { NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      Intent play=new Intent(this, OpenActivity.class).putExtra("path", it.path).putExtra("title", it.title).putExtra("audio", audio).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
      Notification.Builder b= Build.VERSION.SDK_INT>=26? new Notification.Builder(this,CH): new Notification.Builder(this);
      b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Downloaded").setContentText(it.title).setAutoCancel(true)
       .setContentIntent(PendingIntent.getActivity(this, it.id.hashCode(), play, PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
      nm.notify(it.id.hashCode(), b.build()); } catch(Exception e){}
  }
  static void copy(File f, OutputStream o) throws IOException { FileInputStream in=new FileInputStream(f); byte[] b=new byte[1<<20]; int n; while((n=in.read(b))>0) o.write(b,0,n); in.close(); o.close(); }
}
