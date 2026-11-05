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
  private final Handler h=new Handler(Looper.getMainLooper()); private int active; private int lastStartId; private volatile boolean timedOut; private volatile String curText="Preparing…"; private volatile int curP=-1;
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
  public static void start(Context c, String id){ Intent i=new Intent(c, DownloadService.class).putExtra("id", id);
    try { if (Build.VERSION.SDK_INT>=26) c.startForegroundService(i); else c.startService(i); }
    catch (Throwable t){ Log.e(TAG,"start service",t);   // v10.12: Android refusing a foreground start used to crash the app; now the item says so and can be retried
      try { Store.Item it=Store.get(c,id); if (it!=null){ it.status="failed"; it.error="Android would not start the download service ("+t.getClass().getSimpleName()+"). Open YouTube Ultra and tap Retry."; Store.save(c); Store.notifyChanged(); } } catch (Throwable t2){} } }
  public static void cancel(Context c, String id){ c.startService(new Intent(c, DownloadService.class).putExtra("cancel", id)); }
  @Override public IBinder onBind(Intent i){ return null; }
  Notification note(String text, int p){ curText=text; curP=p;
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT>=26 && nm.getNotificationChannel(CH)==null) nm.createNotificationChannel(new NotificationChannel(CH,"Downloads",NotificationManager.IMPORTANCE_LOW));
    PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this, DownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b= Build.VERSION.SDK_INT>=26? new Notification.Builder(this,CH): new Notification.Builder(this);
    b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
    if (p>=0) b.setProgress(100,p,false); return b.build(); }
  void fg(String text, int p){ try { Notification n=note(text,p); if (Build.VERSION.SDK_INT>=29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(NID,n); }
    catch (Throwable t){ Log.e(TAG,"startForeground",t); } }   // v10.12: a refused foreground promotion no longer kills the app
  /** Android 15: a data-sync foreground service gets ~6 h in total, then onTimeout; stop cleanly and keep the files so Resume continues. */
  public void onTimeout(int startId, int fgsType){ timedOut=true; for (Engine e: running.values()) e.cancelled=true; h.postDelayed(new Runnable(){ public void run(){ maybeStop(); }}, 3000); }
  @Override public int onStartCommand(Intent in, int f, int sid){
    lastStartId=sid;
    if (in!=null && in.getStringExtra("cancel")!=null){ Engine e=running.get(in.getStringExtra("cancel")); if (e!=null) e.cancelled=true; else maybeStop(); return START_NOT_STICKY; }   // v10.12: a cancel is a plain startService, no foreground promotion / "Preparing…" flash
    boolean busy; synchronized(this){ busy= active>0; } if (busy) fg(curText, curP); else fg("Preparing…", -1);   // v10.12: a second Download no longer resets a running progress notification to "Preparing…"
    final String id= in==null? null: in.getStringExtra("id"); if (id==null){ maybeStop(); return START_NOT_STICKY; }
    final Store.Item it=Store.get(this,id); if (it==null || running.containsKey(id)) { maybeStop(); return START_NOT_STICKY; }
    final Engine eng=new Engine(); running.put(id, eng); synchronized(this){ active++; } lock(true); it.status="queued"; it.error=""; Store.notifyChanged();
    pool.submit(new Runnable(){ public void run(){ try { work(it, eng); } catch(Throwable t){ Log.e(TAG,"download failed",t); it.status= timedOut? "paused": eng.cancelled && eng.error==null? "cancelled":"failed"; it.error= timedOut? "Android pauses a download service after about 6 hours. Tap Resume to continue.": eng.error!=null? eng.error: String.valueOf(t.getMessage()); if (it.status.equals("failed")) failNote(it); }
      finally { running.remove(it.id); if (Store.get(DownloadService.this, it.id)==null) cleanup(it.id); Store.save(DownloadService.this); Store.notifyChanged(); synchronized(DownloadService.this){ active--; } h.post(new Runnable(){ public void run(){ maybeStop(); }}); } } });
    return START_NOT_STICKY; }
  void maybeStop(){ synchronized(this){ if (active>0) return; } lock(false); stopForeground(true); try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NID); } catch (Throwable t){}
    stopSelfResult(lastStartId); }   // v10.12: stopSelfResult(lastStartId) never stops over a start that arrived meanwhile; the progress notification is always removed
  /** v10.12: a failed download leaves a visible, dismissible note instead of silence. */
  void failNote(Store.Item it){ try { NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      Notification.Builder b= Build.VERSION.SDK_INT>=26? new Notification.Builder(this,CH): new Notification.Builder(this); String why= it.error==null? "": it.error; if (why.length()>140) why=why.substring(0,140)+"…";
      b.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Download failed").setContentText(it.title+(why.isEmpty()? "": ": "+why)).setAutoCancel(true)
       .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, DownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
      nm.notify(it.id.hashCode(), b.build()); } catch (Throwable t){} }
  File tmpDir(String id){ return new File(getExternalCacheDir()!=null? getExternalCacheDir(): getCacheDir(), "dl_"+id); }
  /** Removed from the list: drop its partial files. */
  void cleanup(String id){ try { File d=tmpDir(id); File[] fs=d.listFiles(); if (fs!=null) for (File x: fs) x.delete(); d.delete(); } catch (Throwable t){} }
  static int itagOf(String url){ try { java.util.regex.Matcher m=java.util.regex.Pattern.compile("[?&]itag=(\\d+)").matcher(url); return m.find()? Integer.parseInt(m.group(1)): -1; } catch (Throwable t){ return -1; } }
  /** Same stream (itag) from a fresh extraction, so a refreshed URL never switches to a different file mid-download. */
  static String sameStream(StreamInfo i, String oldUrl){ int it=itagOf(oldUrl); if (it<0) return null;
    List<Stream> all=new ArrayList<>(); all.addAll(i.getVideoOnlyStreams()); all.addAll(i.getAudioStreams()); all.addAll(i.getVideoStreams());
    String oldTags=Formats.xtags(oldUrl); String sameItag=null; AudioStream bestSame=null, bestAny=null;
    for (Stream s: all) { try { if (s.isUrl() && s.getItagItem()!=null && s.getItagItem().id==it) {
        // v10.12: YouTube serves one URL per language for the same audio itag; stay on the track that was chosen (never swap to a dubbed one on refresh)
        if (s instanceof AudioStream) { AudioStream au=(AudioStream)s; if (Formats.betterAudio(au, bestAny)) bestAny=au; if (Formats.xtags(au.getContent()).equals(oldTags) && Formats.betterAudio(au, bestSame)) bestSame=au; }
        else if (sameItag==null) sameItag=s.getContent(); } } catch (Throwable t){} }
    AudioStream pick= bestSame!=null? bestSame: bestAny; return pick!=null? pick.getContent(): sameItag; }
  static String clean(String s){ return Names.clean(s, "video"); }
  /** Thumbnail as JPEG bytes for the MP3 cover (YouTube may serve WebP: re-encoded; large images scaled to 720 px). */
  static byte[] cover(String path){ try { if (path==null || path.isEmpty()) return null; File f=new File(path); if (!f.isFile() || f.length()==0 || f.length()>8_000_000) return null;
      byte[] b=new byte[(int)f.length()]; DataInputStream in=new DataInputStream(new FileInputStream(f)); try { in.readFully(b); } finally { in.close(); }
      if (Id3.isJpeg(b) && b.length<400_000) return b;
      android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeByteArray(b,0,b.length); if (bm==null) return null;
      int w=bm.getWidth(), hh=bm.getHeight(); if (w>720){ android.graphics.Bitmap s2=android.graphics.Bitmap.createScaledBitmap(bm,720,Math.max(1,hh*720/w),true); bm.recycle(); bm=s2; }
      ByteArrayOutputStream o=new ByteArrayOutputStream(); bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o); bm.recycle(); return o.toByteArray();
    } catch (Throwable t){ Log.w(TAG,"cover",t); return null; } }
  void work(final Store.Item it, final Engine eng) throws Exception {
    Net.init(); it.status="resolving"; Store.notifyChanged();
    final StreamInfo[] info={Resolve.info(it.url)}; it.title=info[0].getName(); it.channel=info[0].getUploaderName();
    final StreamInfo ti=info[0]; final Thread thumbs=new Thread(new Runnable(){ public void run(){ try { java.util.List<org.schabi.newpipe.extractor.Image> th=ti.getThumbnails(); String tu=null; int best=0; for (org.schabi.newpipe.extractor.Image im: th){ int w=im.getWidth(); if (tu==null || (w>best && w<=720)) { tu=im.getUrl(); best=w; } }
      if (tu!=null){ File td=new File(getFilesDir(),"thumbs"); td.mkdirs(); File tf=new File(td, it.id+".jpg"); java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(tu).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        InputStream tin=c.getInputStream(); FileOutputStream to=new FileOutputStream(tf); byte[] bb=new byte[16384]; int nn; while((nn=tin.read(bb))>0) to.write(bb,0,nn); to.close(); tin.close(); it.thumb=tf.getAbsolutePath(); Store.notifyChanged(); } } catch(Throwable t){ Log.w(TAG,"thumb",t); } } }); thumbs.start();
    final Formats.Pick[] pk={Formats.pick(info[0], it.spec)}; if (pk[0]==null) throw new IOException("format not available");
    it.label=pk[0].label;
    final List<String> parts=new ArrayList<>(); List<Long> lens=new ArrayList<>();
    if (pk[0].videoUrl!=null){ parts.add("v"); lens.add(pk[0].videoLen);} if (pk[0].audioUrl!=null){ parts.add("a"); lens.add(pk[0].audioLen);} 
    File tmp=tmpDir(it.id); tmp.mkdirs();
    File[] files=new File[parts.size()]; long[] L=new long[parts.size()];
    for (int i=0;i<files.length;i++){ files[i]=new File(tmp, parts.get(i)+".part"); L[i]=lens.get(i); }
    Engine.UrlSource src=new Engine.UrlSource(){ public String url(int p){ return parts.get(p).equals("v")? pk[0].videoUrl: pk[0].audioUrl; }
      public String refresh(int p) throws Exception { String old=url(p); info[0]=Resolve.info(it.url, old); String same=sameStream(info[0], old);
        if (same!=null) { if (parts.get(p).equals("v")) pk[0].videoUrl=same; else pk[0].audioUrl=same; return same; }
        throw new IOException("stream no longer available"); } };
    checkSpace(tmp, files, L, pk[0]);
    it.status="downloading"; Store.notifyChanged();
    final long t0=SystemClock.elapsedRealtime(); final long[] last={-1,t0};
    final boolean[] stopped={false}; Timer tm=new Timer(true); tm.scheduleAtFixedRate(new TimerTask(){ public void run(){ if (stopped[0]) return; long now=SystemClock.elapsedRealtime(), d=eng.done.get(); if (last[0]<0){ last[0]=eng.resumed; }
      double inst=(d-last[0])*1000.0/Math.max(1,now-last[1]); it.speed= it.speed==0? inst: it.speed*0.6+inst*0.4; last[0]=d; last[1]=now;
      it.done=d; it.total=eng.total; it.progress= eng.total>0? (int)(d*100/eng.total):0; Store.notifyChanged();
      if (stopped[0]) return; try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NID, note(it.title+" · "+it.progress+"% · "+String.format(Locale.US,"%.1f MB/s", it.speed/1048576), it.progress)); } catch(Exception e){} } }, 500, 700);
    try { eng.run(src, L, files); } finally { stopped[0]=true; tm.cancel(); }
    long secs=Math.max(1, SystemClock.elapsedRealtime()-t0);
    long got=eng.total-eng.resumed; it.done=eng.total; it.total=eng.total; it.progress=100; it.speed=got*1000.0/secs;
    Log.i(TAG, "downloaded "+got+" bytes (resumed from "+eng.resumed+") in "+secs+" ms = "+String.format(Locale.US,"%.2f MB/s", got*1000.0/secs/1048576)+" connections="+Engine.CONNECTIONS);
    final boolean audio=pk[0].audioOnly;
    // Video: H.264 (or AV1) + AAC remuxed into a real .mp4.  Audio: decoded and re-encoded to a real .mp3.
    String ext= audio? Names.MP3: Names.MP4; String mime=Names.mime(ext);
    String name=Names.file(it.title, ext);
    String vPath= pk[0].videoUrl!=null? files[0].getAbsolutePath(): null; String aPath= pk[0].audioUrl!=null? files[files.length-1].getAbsolutePath(): null;
    File mp3=null;
    if (audio) {
      it.status="converting"; it.progress=0; Store.notifyChanged();
      try { thumbs.join(8000); } catch (InterruptedException e){}
      byte[] id3=Id3.tag(it.title, it.channel, null, cover(it.thumb));
      mp3=new File(tmp, "out.mp3");
      ToMp3.convert(aPath, mp3, pk[0].kbps, id3, pk[0].durationUs, new ToMp3.Progress(){
        public void at(int pc){ it.progress=pc; Store.notifyChanged();
          try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NID, note(it.title+" · converting to MP3 · "+pc+"%", pc)); } catch(Exception e){} }
        public boolean cancelled(){ return eng.cancelled; } });
      it.total=mp3.length(); it.done=it.total;
    }
    it.status="merging"; it.progress=100; Store.notifyChanged();
    if (Build.VERSION.SDK_INT>=29) {
      Uri coll= audio? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY): MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name); cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
      cv.put(MediaStore.MediaColumns.RELATIVE_PATH, (audio? Environment.DIRECTORY_MUSIC: Environment.DIRECTORY_MOVIES)+"/"+DIR); cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
      cv.put(MediaStore.MediaColumns.TITLE, it.title);
      Uri u=getContentResolver().insert(coll, cv); if (u==null) throw new IOException("MediaStore insert failed");
      try { ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"rw");
        if (pfd==null) throw new IOException("MediaStore open failed");
        if (audio || pk[0].progressive) copy(audio? mp3: files[0], new ParcelFileDescriptor.AutoCloseOutputStream(pfd));   // closes pfd
        else if (pk[0].ownMux()) { try { OutputStream o=new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor()), 1<<20); ownMux(it, eng, vPath, aPath, o); o.close(); } finally { pfd.close(); } }
        else { boolean okMux=false; Exception first=null; try { Mux.mp4(vPath, aPath, pfd.getFileDescriptor(), null); okMux=true; } catch (Exception e){ first=e; } finally { pfd.close(); }
          if (!okMux) { Log.w(TAG, "MediaMuxer failed, using our own MP4 writer", first);   // v10.11: same H.264 + AAC through Mp4Mux
            ParcelFileDescriptor p2=getContentResolver().openFileDescriptor(u,"rwt"); if (p2==null) throw first;
            try { OutputStream o=new BufferedOutputStream(new FileOutputStream(p2.getFileDescriptor()), 1<<20); ownMux(it, eng, vPath, aPath, o); o.close(); } catch (Exception e2){ Log.w(TAG,"own mux fallback failed",e2); throw first; } finally { p2.close(); } } }
        ContentValues done=new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING, 0); getContentResolver().update(u, done, null, null);
      } catch (Exception e) { getContentResolver().delete(u,null,null); throw e; }
      it.path=u.toString();
    } else {
      File dir=new File(Environment.getExternalStoragePublicDirectory(audio? Environment.DIRECTORY_MUSIC: Environment.DIRECTORY_MOVIES), DIR); dir.mkdirs();
      File out=new File(dir, name); int n=1; while (out.exists()) out=new File(dir, Names.numbered(name, n++));
      try {
        if (audio) copy(mp3, new FileOutputStream(out)); else if (pk[0].progressive) copy(files[0], new FileOutputStream(out));
        else if (pk[0].ownMux()) { OutputStream o=new BufferedOutputStream(new FileOutputStream(out), 1<<20); try { ownMux(it, eng, vPath, aPath, o); } finally { o.close(); } }
        else { try { Mux.mp4(vPath, aPath, null, out.getAbsolutePath()); }
          catch (Exception e){ Log.w(TAG, "MediaMuxer failed, using our own MP4 writer", e); OutputStream o=new BufferedOutputStream(new FileOutputStream(out), 1<<20); try { ownMux(it, eng, vPath, aPath, o); } catch (Exception e2){ throw e; } finally { o.close(); } } }
      } catch (Exception e){ out.delete(); throw e; }
      it.path=out.getAbsolutePath(); MediaScannerConnection.scanFile(this, new String[]{it.path}, new String[]{mime}, null);
    }
    if (mp3!=null) mp3.delete();
    for (File x: files) { x.delete(); Engine.mapFile(x).delete(); } tmp.delete();
    it.status="done"; Store.save(this);
    try { NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      Intent play=new Intent(this, OpenActivity.class).putExtra("path", it.path).putExtra("title", it.title).putExtra("audio", audio).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
      Notification.Builder b= Build.VERSION.SDK_INT>=26? new Notification.Builder(this,CH): new Notification.Builder(this);
      b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Downloaded").setContentText(it.title).setAutoCancel(true)
       .setContentIntent(PendingIntent.getActivity(this, it.id.hashCode(), play, PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
      nm.notify(it.id.hashCode(), b.build()); } catch(Exception e){}
  }
  /** v10.11: VP9 / AV1 (+ AAC) into <title>.mp4 with our own muxer, with a percentage while it copies. */
  void ownMux(final Store.Item it, final Engine eng, String vPath, String aPath, OutputStream o) throws IOException {
    it.status="merging"; it.progress=0; Store.notifyChanged(); final long[] lastPc={-1};
    try {
      Mp4Mux.Result r=Mp4Mux.mux(vPath, aPath, o, new Mp4Mux.Progress(){
        public void at(long done, long total){ int pc= total>0? (int)(done*100/total): 0; if (pc==lastPc[0]) return; lastPc[0]=pc; it.progress=pc; Store.notifyChanged();
          try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NID, note(it.title+" · saving MP4 · "+pc+"%", pc)); } catch(Exception e){} }
        public boolean cancelled(){ return eng.cancelled; } });
      Log.i(TAG, "own mux: "+r.videoCodec+" "+r.width+"x"+r.height+" "+r.bitDepth+"-bit hdr="+r.hdr+" video "+r.videoSeconds+" s, "+r.audioCodec+" "+r.audioSeconds+" s, "+r.bytes+" bytes");
    } catch (Mp4Mux.Failed f){ throw new ToMp3.Failed("MP4 save failed: "+f.getMessage(), f); }
    it.progress=100; Store.notifyChanged(); }
  /** Not enough room = a clear message before downloading gigabytes (the parts and the finished MP4 sit on the same storage). */
  static void checkSpace(File tmp, File[] files, long[] L, Formats.Pick pk) throws IOException {
    long total=0; for (long x: L){ if (x<=0) return; total+=x; }        // size unknown: the engine finds out, skip the check
    boolean resuming=false; for (File f: files) if (Engine.mapFile(f).exists()) resuming=true;
    long out= pk.audioOnly? Math.max(4L<<20, total/2): total;           // MP3 is smaller than its source; MP4 is about the size of both parts
    long need= (resuming? 0: total) + out + (64L<<20);
    long free=tmp.getUsableSpace(); if (free<=0) return;
    if (free<need) throw new Plain("Not enough free space: this "+(pk.audioOnly? "audio": pk.label)+" download needs about "+Ui.size(need)+" free, and the phone has "+Ui.size(free)+". Free some space or pick a lower quality.");
  }
  static final class Plain extends IOException { Plain(String m){ super(m); } }
  static void copy(File f, OutputStream o) throws IOException { FileInputStream in=new FileInputStream(f); try { byte[] b=new byte[1<<20]; int n; while((n=in.read(b))>0) o.write(b,0,n); } finally { in.close(); o.close(); } }
}
