package paradox.dl;
import java.io.*; import java.net.*; import java.nio.ByteBuffer; import java.nio.channels.FileChannel;
import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.*;
/** Multi-connection range downloader: each stream is split into fixed chunks fetched in parallel
 *  (googlevideo &range=a-b, kept under YouTube's ~10 MB per-request burst limit), written at their offsets
 *  into a pre-allocated file. Connections are kept alive between chunks, finished chunks are recorded in a
 *  small ".map" file next to each part so a paused/failed download resumes where it stopped, and an expired
 *  URL is refreshed once for all threads (same itag). */
public final class Engine {
  public interface UrlSource { String url(int part) throws Exception; String refresh(int part) throws Exception; }
  public static int CONNECTIONS=16; public static final int CHUNK=4*1024*1024; static final int RETRIES=8;
  static final int MAGIC=0x50444c32; static final int HDR=20; // magic, length(8), chunk(4), count(4)
  static { try { if (System.getProperty("http.maxConnections")==null) System.setProperty("http.maxConnections","32"); System.setProperty("http.keepAlive","true"); } catch (Throwable ignore) {} }
  public final AtomicLong done=new AtomicLong(); public volatile long total, resumed; public volatile boolean cancelled; public volatile String error;
  static long probeLength(String url) throws IOException {
    HttpURLConnection c=open(url, 0, 0); try { int code=c.getResponseCode(); if (code>=400) throw new IOException("HTTP "+code);
      String cr=c.getHeaderField("Content-Range"); if (cr!=null && cr.contains("/")) { String t=cr.substring(cr.lastIndexOf('/')+1).trim(); if (!t.equals("*")) return Long.parseLong(t); }
      String cl=c.getHeaderField("Content-Length"); return cl==null? -1: Long.parseLong(cl);
    } finally { c.disconnect(); } }
  static boolean google(String u){ return u.contains("googlevideo.com/videoplayback"); }
  static HttpURLConnection open(String url, long a, long b) throws IOException {
    String u= google(url)? url+"&range="+a+"-"+b : url;
    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
    c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setUseCaches(false); c.setRequestProperty("User-Agent", Net.ua(url));
    c.setRequestProperty("Accept-Encoding", "identity"); c.setRequestProperty("Connection", "keep-alive");
    if (!google(url)) c.setRequestProperty("Range","bytes="+a+"-"+b);
    return c; }
  public static File mapFile(File part){ return new File(part.getPath()+".map"); }
  /** Chunks already on disk for this part, or null when there is nothing valid to resume. */
  static boolean[] loadMap(File part, long len, int count){
    File m=mapFile(part); if (!part.exists() || part.length()!=len || !m.exists() || m.length()!=HDR+count) return null;
    try { DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(m))); try {
        if (in.readInt()!=MAGIC || in.readLong()!=len || in.readInt()!=CHUNK || in.readInt()!=count) return null;
        boolean[] have=new boolean[count]; for (int k=0;k<count;k++) have[k]= in.read()==1; return have; } finally { in.close(); }
    } catch (IOException e) { return null; } }
  /** Download all parts (index i -> files[i]) concurrently sharing one connection pool. */
  public void run(final UrlSource src, final long[] lens, final File[] files) throws Exception {
    final int n=files.length; final String[] urls=new String[n]; final long[] L=new long[n]; final int[] ver=new int[n];
    for (int i=0;i<n;i++){ urls[i]=src.url(i); L[i]= lens[i]>0? lens[i]: probeLength(urls[i]); if (L[i]<=0) throw new IOException("unknown size"); }
    long t=0; for (long x: L) t+=x; total=t;
    final FileChannel[] ch=new FileChannel[n]; final RandomAccessFile[] raf=new RandomAccessFile[n]; final RandomAccessFile[] map=new RandomAccessFile[n];
    final List<long[]> jobs=new ArrayList<>(); // {part, start, end, chunkIndex}
    for (int i=0;i<n;i++){ int count=(int)((L[i]+CHUNK-1)/CHUNK);
      boolean[] have=loadMap(files[i], L[i], count);
      if (have==null){ mapFile(files[i]).delete(); files[i].delete(); have=new boolean[count];
        DataOutputStream o=new DataOutputStream(new FileOutputStream(mapFile(files[i]))); o.writeInt(MAGIC); o.writeLong(L[i]); o.writeInt(CHUNK); o.writeInt(count); o.write(new byte[count]); o.close(); }
      raf[i]=new RandomAccessFile(files[i],"rw"); if (raf[i].length()!=L[i]) raf[i].setLength(L[i]); ch[i]=raf[i].getChannel(); map[i]=new RandomAccessFile(mapFile(files[i]),"rw");
      for (int k=0;k<count;k++){ long a=(long)k*CHUNK, b=Math.min(L[i], a+CHUNK)-1; if (have[k]) done.addAndGet(b-a+1); else jobs.add(new long[]{i, a, b, k}); } }
    resumed=done.get();
    final AtomicInteger next=new AtomicInteger(); final Object refreshLock=new Object();
    int threads=Math.max(1, Math.min(CONNECTIONS, jobs.size())); ExecutorService ex=Executors.newFixedThreadPool(threads);
    List<Future<?>> fs=new ArrayList<>();
    for (int k=0;k<threads;k++) fs.add(ex.submit(new Callable<Void>(){ public Void call() throws Exception {
      byte[] buf=new byte[128*1024];
      while (!cancelled) { int j=next.getAndIncrement(); if (j>=jobs.size()) return null; long[] job=jobs.get(j); int part=(int)job[0];
        long pos=job[1]; int attempt=0;
        while (pos<=job[2]) { if (cancelled) return null;
          String u; int v; synchronized(refreshLock){ u=urls[part]; v=ver[part]; }
          HttpURLConnection c=null; boolean ok=false; try { c=open(u, pos, job[2]); int code=c.getResponseCode();
            if (code==403 || code==410) { synchronized(refreshLock){ if (ver[part]==v) { urls[part]=src.refresh(part); ver[part]++; } } throw new IOException("HTTP "+code+" (refreshed)"); }
            if (code>=400) throw new IOException("HTTP "+code);
            InputStream in=c.getInputStream(); int r;
            while (pos<=job[2] && (r=in.read(buf))>0) { int take=(int)Math.min(r, job[2]-pos+1); ByteBuffer bb=ByteBuffer.wrap(buf,0,take); long p=pos; while (bb.hasRemaining()) p+=ch[part].write(bb, p); pos+=take; done.addAndGet(take); if (cancelled) break; }
            if (pos>job[2]) { in.close(); ok=true; } // whole range read: the connection goes back to the pool for the next chunk
            else if (!cancelled) throw new IOException("short read");
          } catch (IOException e) { if (++attempt>RETRIES) { error=e.getMessage(); cancelled=true; throw e; }
            String m=String.valueOf(e.getMessage()); if (!m.contains("refreshed") && attempt>1) Thread.sleep(Math.min(3000L, 400L*attempt)); }
          finally { if (c!=null && !ok) c.disconnect(); } }
        if (pos>job[2]) synchronized(map[part]) { map[part].seek(HDR+job[3]); map[part].write(1); }
      } return null; } }));
    Exception first=null; for (Future<?> f: fs) { try { f.get(); } catch (ExecutionException e){ if (first==null) first=(Exception)e.getCause(); } }
    ex.shutdownNow(); for (int i=0;i<n;i++){ try{ ch[i].close(); raf[i].close(); map[i].close(); }catch(Exception ignore){} }
    if (first!=null) throw first; if (cancelled) throw new InterruptedException("cancelled");
    for (int i=0;i<n;i++) mapFile(files[i]).delete();
  }
}
