package paradox.dl;
import java.io.*;
/** Desktop runner: java paradox.dl.MuxCli <video|-> <audio|-> <out.mp4>  -> prints the result facts. */
public class MuxCli { public static void main(String[] a) throws Exception {
  String v=a[0].equals("-")? null: a[0], au=a[1].equals("-")? null: a[1];
  OutputStream o=new BufferedOutputStream(new FileOutputStream(a[2]), 1<<20);
  final long[] calls={0};
  Mp4Mux.Result r; try { r=Mp4Mux.mux(v, au, o, new Mp4Mux.Progress(){ public void at(long d, long t){ calls[0]++; } public boolean cancelled(){ return false; } }); } finally { o.close(); }
  System.out.println("bytes="+r.bytes+" video="+r.videoCodec+" "+r.width+"x"+r.height+" depth="+r.bitDepth+" hdr="+r.hdr+" vsec="+String.format("%.3f",r.videoSeconds)+" audio="+r.audioCodec+" asec="+String.format("%.3f",r.audioSeconds)+" progress_calls="+calls[0]); } }
