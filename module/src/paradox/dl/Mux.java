package paradox.dl;
import android.media.*; import java.io.*; import java.nio.ByteBuffer; import java.util.*;
/** Remux (no re-encode) the downloaded video track (H.264, or AV1 on Android 14+) and AAC audio track into one MP4 with MediaMuxer. */
public final class Mux {
  public static void merge(String video, String audio, FileDescriptor outFd, String outPath, int kind) throws IOException { mp4(video, audio, outFd, outPath); }
  public static void mp4(String video, String audio, FileDescriptor outFd, String outPath) throws IOException {
    List<MediaExtractor> exs=new ArrayList<>(); List<Integer> dst=new ArrayList<>(), src=new ArrayList<>(); MediaMuxer mx=null; boolean started=false;
    try {
      String[][] want={{video,"video/"},{audio,"audio/"}};
      for (String[] w: want) { if (w[0]==null) continue; MediaExtractor e=new MediaExtractor(); exs.add(e);
        try { e.setDataSource(w[0]); } catch (Exception x){ throw new ToMp3.Failed("MP4 save failed: the downloaded "+(w[1].equals("video/")?"video":"audio")+" can't be read", x); }
        int t=-1; for (int k=0;k<e.getTrackCount();k++){ String m=e.getTrackFormat(k).getString(MediaFormat.KEY_MIME); if (m!=null && m.startsWith(w[1])) { t=k; break; } }
        if (t<0) throw new ToMp3.Failed("MP4 save failed: no "+(w[1].equals("video/")?"video":"audio")+" track in the download");
        e.selectTrack(t); src.add(t); }
      mx= outFd!=null? new MediaMuxer(outFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4): new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      int maxIn=1<<20;
      for (int k=0;k<exs.size();k++){ MediaFormat f=exs.get(k).getTrackFormat(src.get(k)); String mime=f.getString(MediaFormat.KEY_MIME);
        if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxIn=Math.max(maxIn, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        try { dst.add(mx.addTrack(f)); } catch (Exception x){ throw new ToMp3.Failed("MP4 save failed: this phone can't put "+mime+" into an MP4", x); } }
      mx.start(); started=true;
      ByteBuffer buf=ByteBuffer.allocateDirect(Math.max(maxIn, 8*1024*1024)); MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      boolean[] eos=new boolean[exs.size()]; long[] count=new long[exs.size()];
      while (true) { int pick=-1; long best=Long.MAX_VALUE;
        for (int k=0;k<exs.size();k++) if (!eos[k]) { long ts=exs.get(k).getSampleTime(); if (ts<0){ eos[k]=true; continue; } if (ts<best){ best=ts; pick=k; } }
        if (pick<0) break; MediaExtractor e=exs.get(pick);
        buf.clear(); int size=e.readSampleData(buf,0); if (size<0){ eos[pick]=true; continue; }
        info.offset=0; info.size=size; info.presentationTimeUs=e.getSampleTime();
        info.flags= (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0? MediaCodec.BUFFER_FLAG_KEY_FRAME: 0;
        mx.writeSampleData(dst.get(pick), buf, info); count[pick]++; e.advance(); }
      for (long c: count) if (c==0) throw new ToMp3.Failed("MP4 save failed: a downloaded track was empty");
      mx.stop(); started=false;
    } catch (ToMp3.Failed f){ throw f; }
    catch (Exception x){ throw new ToMp3.Failed("MP4 save failed: "+x.getClass().getSimpleName()+(x.getMessage()==null? "": " ("+x.getMessage()+")"), x); }
    finally { try { if (mx!=null){ if (started) mx.stop(); mx.release(); } } catch (Exception x){} for (MediaExtractor e: exs) try { e.release(); } catch (Exception x){} }
  }
}
