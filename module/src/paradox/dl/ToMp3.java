package paradox.dl;
import android.media.*; import android.os.Build; import java.io.*; import java.nio.ByteBuffer;
/** Decodes the downloaded audio stream (Opus/WebM or AAC/M4A) with the phone's MediaCodec and encodes it to MP3 with LAME. */
public final class ToMp3 {
  public interface Progress { void at(int percent); boolean cancelled(); }
  /** Thrown with a message that is shown to the user as-is. */
  public static final class Failed extends IOException { public Failed(String m){ super(m); } public Failed(String m, Throwable c){ super(m, c); } }
  static final long STALL_MS=20000;
  public static void convert(String src, File out, int kbps, byte[] id3, long durationUs, Progress p) throws IOException {
    if (!Mp3.load()) throw new Failed("MP3 conversion failed: the MP3 encoder is missing for this phone's CPU");
    MediaExtractor ex=new MediaExtractor(); MediaCodec dec=null; Mp3.Encoder enc=null; boolean ok=false;
    try {
      try { ex.setDataSource(src); } catch (Exception e){ throw new Failed("MP3 conversion failed: the downloaded audio can't be read", e); }
      int track=-1; MediaFormat fmt=null;
      for (int t=0;t<ex.getTrackCount();t++){ MediaFormat f=ex.getTrackFormat(t); String m=f.getString(MediaFormat.KEY_MIME); if (m!=null && m.startsWith("audio/")) { track=t; fmt=f; break; } }
      if (track<0) throw new Failed("MP3 conversion failed: no audio track in the download");
      ex.selectTrack(track); String mime=fmt.getString(MediaFormat.KEY_MIME);
      if (durationUs<=0 && fmt.containsKey(MediaFormat.KEY_DURATION)) durationUs=fmt.getLong(MediaFormat.KEY_DURATION);
      try { dec=MediaCodec.createDecoderByType(mime); dec.configure(fmt, null, null, 0); dec.start(); }
      catch (Exception e){ throw new Failed("MP3 conversion failed: this phone can't decode "+mime, e); }
      int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE): 48000;
      int ch=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT): 2;
      boolean isFloat=false; MediaCodec.BufferInfo bi=new MediaCodec.BufferInfo(); boolean inEos=false, outEos=false; int last=-1;
      long lastOut=System.currentTimeMillis();
      while (!outEos){
        if (p!=null && p.cancelled()) throw new InterruptedIOException("cancelled");
        if (!inEos){ int ii=dec.dequeueInputBuffer(10000);
          if (ii>=0){ ByteBuffer ib=dec.getInputBuffer(ii); int n=ex.readSampleData(ib,0);
            if (n<0){ dec.queueInputBuffer(ii,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos=true; }
            else { dec.queueInputBuffer(ii,0,n,Math.max(0,ex.getSampleTime()),0); ex.advance(); } } }
        int oi=dec.dequeueOutputBuffer(bi,10000);
        if (oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){ MediaFormat of=dec.getOutputFormat();
          if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          if (Build.VERSION.SDK_INT>=24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING)) isFloat= of.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_FLOAT; }
        else if (oi>=0){ lastOut=System.currentTimeMillis();
          if (bi.size>0){ if (enc==null) enc=new Mp3.Encoder(out, rate, ch, kbps, id3);
            ByteBuffer ob=dec.getOutputBuffer(oi); if (ob!=null) enc.pcm(ob, bi.offset, bi.size, ch, isFloat);
            if (durationUs>0 && p!=null){ int pc=(int)Math.min(99, Math.max(0, bi.presentationTimeUs*100/durationUs)); if (pc!=last){ last=pc; p.at(pc); } } }
          if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) outEos=true;
          dec.releaseOutputBuffer(oi,false); }
        else if (System.currentTimeMillis()-lastOut>STALL_MS) throw new Failed("MP3 conversion failed: the phone's audio decoder stopped responding");
      }
      if (enc==null) throw new Failed("MP3 conversion failed: the download had no audio samples");
      enc.finish(); if (p!=null) p.at(100); ok=true;
    } catch (Failed | InterruptedIOException e){ throw e; }
    catch (IOException e){ throw new Failed("MP3 conversion failed: "+e.getMessage(), e); }
    catch (Exception e){ throw new Failed("MP3 conversion failed: "+e.getClass().getSimpleName()+(e.getMessage()==null? "": " ("+e.getMessage()+")"), e); }
    finally {
      try { if (enc!=null) enc.close(); } catch (Exception e){}
      try { if (dec!=null){ dec.stop(); dec.release(); } } catch (Exception e){}
      try { ex.release(); } catch (Exception e){}
      if (!ok) out.delete(); }
  }
}
