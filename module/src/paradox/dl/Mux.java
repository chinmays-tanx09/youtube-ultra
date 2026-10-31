package paradox.dl;
import android.media.*; import java.io.*; import java.nio.ByteBuffer; import java.util.*;
/** Remux (no re-encode) separate video and audio tracks into one container with MediaMuxer. */
public final class Mux {
  public static void merge(String video, String audio, FileDescriptor outFd, String outPath, int kind) throws IOException {
    int fmt= kind==1? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM : kind==2? 4 /*MUXER_OUTPUT_OGG*/ : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    MediaMuxer mx= outFd!=null? new MediaMuxer(outFd, fmt): new MediaMuxer(outPath, fmt);
    List<MediaExtractor> exs=new ArrayList<>(); List<Integer> dst=new ArrayList<>();
    for (String src: new String[]{video, audio}) { if (src==null) continue; MediaExtractor e=new MediaExtractor(); e.setDataSource(src);
      for (int t=0;t<e.getTrackCount();t++){ MediaFormat f=e.getTrackFormat(t); String mime=f.getString(MediaFormat.KEY_MIME);
        if (mime!=null && (mime.startsWith("video/")||mime.startsWith("audio/"))) { e.selectTrack(t); dst.add(mx.addTrack(f)); exs.add(e); break; } } }
    mx.start(); ByteBuffer buf=ByteBuffer.allocate(16*1024*1024); MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    boolean[] eos=new boolean[exs.size()];
    while (true) { int pick=-1; long best=Long.MAX_VALUE;
      for (int k=0;k<exs.size();k++) if (!eos[k]) { long ts=exs.get(k).getSampleTime(); if (ts<0){ eos[k]=true; continue; } if (ts<best){ best=ts; pick=k; } }
      if (pick<0) break; MediaExtractor e=exs.get(pick);
      buf.clear(); int size=e.readSampleData(buf,0); if (size<0){ eos[pick]=true; continue; }
      info.offset=0; info.size=size; info.presentationTimeUs=e.getSampleTime();
      info.flags= (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0? MediaCodec.BUFFER_FLAG_KEY_FRAME: 0;
      mx.writeSampleData(dst.get(pick), buf, info); e.advance(); }
    mx.stop(); mx.release(); for (MediaExtractor e: exs) e.release();
  }
}
