package paradox.dl;
import java.io.*; import java.nio.ByteBuffer;
/** MP3 encoding with LAME 3.100 (LGPL-2.0+, bundled as libparadoxmp3.so for arm64-v8a and armeabi-v7a).
 *  File layout: ID3v2 tag, then CBR MP3 frames; the first frame is LAME's "Info" frame (exact duration for seeking), rewritten at the end. */
public final class Mp3 {
  static native long nOpen(int sampleRate, int channels, int kbps, int quality);
  /** pcm: direct buffer of interleaved samples (16-bit signed, or 32-bit float when isFloat); returns MP3 bytes written to out, or negative on error. */
  static native int nEncode(long h, ByteBuffer pcm, int offset, int bytes, int channels, boolean isFloat, byte[] out);
  static native int nFlush(long h, byte[] out);
  static native byte[] nInfoFrame(long h);
  static native void nClose(long h);
  static native String nVersion();
  private static volatile Boolean loaded; static String loadError;
  /** Loads the native encoder once; false (with loadError) when it is missing for this phone's CPU. */
  public static synchronized boolean load(){
    if (loaded!=null) return loaded;
    try { System.loadLibrary("paradoxmp3"); nVersion(); loaded=true; }
    catch (Throwable t){ loadError=t.getClass().getSimpleName()+": "+t.getMessage(); loaded=false; }
    return loaded; }
  public static final int[] KBPS={320,192,128};
  public static final class Encoder implements Closeable {
    final RandomAccessFile f; long h; final long audioStart; byte[] out=new byte[256*1024]; long pcmBytes; boolean closed;
    public Encoder(File file, int sampleRate, int channels, int kbps, byte[] id3) throws IOException {
      if (!load()) throw new IOException("MP3 encoder not available on this phone ("+loadError+")");
      h=nOpen(sampleRate, Math.max(1,channels), kbps, 2); if (h==0) throw new IOException("MP3 encoder can't take "+sampleRate+" Hz / "+channels+" ch at "+kbps+" kbps");
      f=new RandomAccessFile(file,"rw"); f.setLength(0); if (id3!=null) f.write(id3); audioStart=f.getFilePointer(); }
    /** Interleaved PCM from a direct buffer (MediaCodec output buffers are direct). */
    public void pcm(ByteBuffer direct, int offset, int bytes, int channels, boolean isFloat) throws IOException {
      if (bytes<=0) return; int frameBytes=(isFloat?4:2)*Math.max(1,channels); int samples=bytes/frameBytes;
      int need=samples*5/4+7200; if (out.length<need) out=new byte[need];
      int n=nEncode(h, direct, offset, samples*frameBytes, channels, isFloat, out); if (n<0) throw new IOException("MP3 encoder error "+n);
      if (n>0) f.write(out,0,n); pcmBytes+=bytes; }
    /** Flushes the encoder and writes the Info frame. */
    public void finish() throws IOException {
      int n=nFlush(h,out); if (n<0) throw new IOException("MP3 encoder error "+n); if (n>0) f.write(out,0,n);
      byte[] tag=nInfoFrame(h); if (tag!=null && tag.length>0){ long end=f.getFilePointer(); f.seek(audioStart); f.write(tag); f.seek(end); }
      close(); }
    @Override public void close() throws IOException { if (closed) return; closed=true; try { if (h!=0) nClose(h); h=0; } finally { f.close(); } }
  }
}
