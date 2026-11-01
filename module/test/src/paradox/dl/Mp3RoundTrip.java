package paradox.dl;
import java.io.*; import java.nio.*; import java.nio.file.*;
/** Drives the real LAME bridge (host build of libparadoxmp3.so) exactly the way ToMp3 does after MediaCodec decoding:
 *  interleaved PCM chunks from a direct buffer -> Mp3.Encoder (ID3v2 + frames + Info frame).
 *  args: pcmFile rate channels float(0|1) out.mp3 kbps title artist [cover.jpg] */
public class Mp3RoundTrip {
  public static void main(String[] a) throws Exception {
    byte[] pcm=Files.readAllBytes(Paths.get(a[0])); int rate=Integer.parseInt(a[1]), ch=Integer.parseInt(a[2]); boolean fl=a[3].equals("1");
    byte[] cover= a.length>8? Files.readAllBytes(Paths.get(a[8])): null;
    if (!Mp3.load()) throw new IOException("load failed: "+Mp3.loadError);
    long t0=System.nanoTime();
    Mp3.Encoder e=new Mp3.Encoder(new File(a[4]), rate, ch, Integer.parseInt(a[5]), Id3.tag(a[6], a[7], null, cover));
    int frame=(fl?4:2)*ch, chunk=frame*4096; ByteBuffer d=ByteBuffer.allocateDirect(chunk+64);
    for (int off=0; off<pcm.length; off+=chunk){ int n=Math.min(chunk, pcm.length-off); d.clear(); d.position(16); d.put(pcm, off, n); e.pcm(d, 16, n, ch, fl); }   // non-zero offset like MediaCodec's BufferInfo.offset
    e.finish();
    System.out.println("encoded "+pcm.length/frame+" frames in "+(System.nanoTime()-t0)/1_000_000+" ms with "+Mp3.nVersion());
  }
}
