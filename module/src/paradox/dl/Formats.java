package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.MediaFormat;
import java.util.*;
/** Picks the streams for a download. Every video is saved as MP4 and every audio download as MP3:
 *  - video: H.264 (avc1) + AAC, remuxed into MP4 with no re-encode (plays everywhere). Heights YouTube only serves as
 *    VP9/AV1 (1440p, 4K) are offered as AV1 + AAC in MP4 only on phones that can write AV1 into MP4 (Android 14+) and
 *    decode it in hardware; VP9 and Opus can't go into an MP4 with Android's muxer, so they are never used for video.
 *  - audio: the best original audio track (Opus or AAC) is downloaded and converted to MP3 at 320/192/128 kbps.
 *  Specs: "v:<height>[:mp4|:av1]", "p:<height>" (single-file MP4), "a:mp3:<kbps>", "a:rank:<0..2>"; older specs
 *  ("v:<h>:webm", "a:itag:<n>", "a:m4a", "a:opus") from v10.8 queues still work and now give MP4 / MP3. */
public final class Formats {
  public static final class Pick { public String videoUrl, audioUrl, ext, label, codec=""; public long videoLen, audioLen; public boolean audioOnly, progressive; public int kbps, height, wanted; public long durationUs; }
  /** Tallest AV1 height the phone can save as MP4; -1 = ask the phone (Codecs). Tests set it directly. */
  public static volatile int av1MaxHeight=-1;
  static int av1Max(){ int v=av1MaxHeight; if (v>=0) return v; try { return Codecs.av1Mp4MaxHeight(); } catch (Throwable t){ return 0; } }
  public static int height(VideoStream v){ try { return v.getHeight()>0? v.getHeight(): Integer.parseInt(v.getResolution().replaceAll("p.*","")); } catch(Exception e){ return 0; } }
  static String codec(Stream s){ try { return String.valueOf(((VideoStream)s).getCodec()); } catch (Throwable t){ return ""; } }
  static boolean isMp4(VideoStream v){ return v.getFormat()==MediaFormat.MPEG_4 && codec(v).startsWith("avc"); }
  static boolean isAv1(VideoStream v){ return v.getFormat()==MediaFormat.MPEG_4 && codec(v).startsWith("av01"); }
  static long len(Stream s){ try { if (s.getItagItem()!=null) return s.getItagItem().getContentLength(); } catch(Exception e){} return -1; }
  static boolean ok(Stream s){ return s.isUrl() && s.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP; }
  static boolean original(AudioStream a){ String tr=null; try { tr=a.getAudioTrackType()==null? null: a.getAudioTrackType().name(); } catch(Throwable t){} return tr==null || tr.equals("ORIGINAL"); }
  /** Best audio of one family: m4a=true -> AAC in M4A (the only audio that goes into an MP4), false -> Opus. */
  public static AudioStream audio(StreamInfo i, boolean m4a){
    AudioStream best=null;
    for (AudioStream a: i.getAudioStreams()) { if (!ok(a)) continue;
      boolean isM4a=a.getFormat()==MediaFormat.M4A; boolean isOpus=a.getFormat()==MediaFormat.WEBMA_OPUS || a.getFormat()==MediaFormat.WEBMA;
      if (m4a? !isM4a : !isOpus) continue; if (!original(a)) continue;
      if (best==null || a.getAverageBitrate()>best.getAverageBitrate()) best=a; }
    if (best==null) for (AudioStream a: i.getAudioStreams()) if (ok(a) && (m4a? a.getFormat()==MediaFormat.M4A : a.getFormat()!=MediaFormat.M4A) && (best==null || a.getAverageBitrate()>best.getAverageBitrate())) best=a;
    return best; }
  /** Source for MP3: the highest-bitrate original track of any format (Opus 160k usually beats AAC 128k). */
  public static AudioStream bestAudio(StreamInfo i){ AudioStream best=null;
    for (AudioStream a: i.getAudioStreams()) if (ok(a) && original(a) && (best==null || a.getAverageBitrate()>best.getAverageBitrate())) best=a;
    if (best==null) for (AudioStream a: i.getAudioStreams()) if (ok(a) && (best==null || a.getAverageBitrate()>best.getAverageBitrate())) best=a;
    return best; }
  static boolean better(VideoStream v, VideoStream cur){ return cur==null || v.getFps()>cur.getFps() || (v.getFps()==cur.getFps() && len(v)>len(cur)); }
  /** height -> best H.264 stream, and height -> best AV1 stream (only heights above the tallest H.264, only when the phone can do it). */
  static TreeMap<Integer,VideoStream> avc(StreamInfo i){ TreeMap<Integer,VideoStream> m=new TreeMap<>(Collections.reverseOrder());
    for (VideoStream v: i.getVideoOnlyStreams()) { if (!ok(v) || !isMp4(v)) continue; int h=height(v); if (h<=0) continue; if (better(v, m.get(h))) m.put(h,v); } return m; }
  static TreeMap<Integer,VideoStream> av1(StreamInfo i, int aboveHeight){ TreeMap<Integer,VideoStream> m=new TreeMap<>(Collections.reverseOrder()); int max=av1Max();
    if (max<=0) return m;
    for (VideoStream v: i.getVideoOnlyStreams()) { if (!ok(v) || !isAv1(v)) continue; int h=height(v); if (h<=aboveHeight || h>max) continue; if (better(v, m.get(h))) m.put(h,v); } return m; }
  static String vlabel(int h, boolean av1){ String t; switch(h){ case 4320: t="8K"; break; case 2160: t="4K"; break; case 1440: t="QHD"; break; case 1080: t="Full HD"; break; case 720: t="HD"; break; default: t=null; }
    if (av1) t= (t==null? "": t+" · ")+"AV1"; return t==null? h+"p": h+"p ("+t+")"; }
  /** Video quality rows, tallest first: {spec, label, size}. All of them save as .mp4. */
  public static List<String[]> videoChoices(StreamInfo i){ List<String[]> out=new ArrayList<>(); AudioStream a=audio(i,true);
    if (a!=null) { TreeMap<Integer,VideoStream> h264=avc(i); int top= h264.isEmpty()? 0: h264.firstKey();
      for (Map.Entry<Integer,VideoStream> e: av1(i, top).entrySet()) out.add(row(e.getKey(), e.getValue(), a, true));
      for (Map.Entry<Integer,VideoStream> e: h264.entrySet()) out.add(row(e.getKey(), e.getValue(), a, false)); }
    if (out.isEmpty()) for (VideoStream v: i.getVideoStreams()) if (ok(v) && v.getFormat()==MediaFormat.MPEG_4) out.add(new String[]{"p:"+height(v), vlabel(height(v),false), String.valueOf(len(v))});
    return out; }
  static String[] row(int h, VideoStream v, AudioStream a, boolean av1){ String fps= v.getFps()>30? " "+v.getFps()+"fps": "";
    return new String[]{"v:"+h+":"+(av1?"av1":"mp4"), vlabel(h,av1)+fps, String.valueOf(len(v)+len(a))}; }
  /** Audio rows (always MP3): {spec, label, estimated size}. Row index = the remembered "rank". */
  public static List<String[]> audioChoices(StreamInfo i){ List<String[]> out=new ArrayList<>(); if (bestAudio(i)==null) return out; long secs=Math.max(0,i.getDuration());
    String[] names={"Best","Standard","Smaller"};
    for (int k=0;k<Mp3.KBPS.length;k++){ int kb=Mp3.KBPS[k]; out.add(new String[]{"a:mp3:"+kb, "MP3 "+kb+" kbps ("+names[k]+")", String.valueOf(secs>0? secs*kb*1000/8+2048: 0)}); }
    return out; }
  static int kbpsFor(int sourceKbps){ return sourceKbps>=140? 320: sourceKbps>=96? 192: 128; }
  public static Pick pick(StreamInfo i, String spec){
    Pick p=new Pick(); String[] s=spec.split(":"); p.durationUs=Math.max(0,i.getDuration())*1_000_000L;
    if (s[0].equals("a")) { AudioStream a=bestAudio(i); if (a==null) return null; int kb=320;
      try { if (s[1].equals("mp3")) kb=Integer.parseInt(s[2]);
        else if (s[1].equals("rank")) kb=Mp3.KBPS[Math.max(0,Math.min(Integer.parseInt(s[2]), Mp3.KBPS.length-1))];
        else if (s[1].equals("itag")) { int it=Integer.parseInt(s[2]); for (AudioStream x: i.getAudioStreams()) if (ok(x) && x.getItag()==it) { kb=kbpsFor(x.getAverageBitrate()); break; } } } catch (Exception e){ kb=320; }
      if (kb!=320 && kb!=192 && kb!=128) kb=320;
      p.audioOnly=true; p.audioUrl=a.getContent(); p.audioLen=len(a); p.ext=Names.MP3; p.kbps=kb; p.label="MP3 "+kb+"k"; p.codec=a.getFormat()==MediaFormat.M4A? "aac": "opus"; return p; }
    if (s[0].equals("p")) return progressive(i, p, s.length>1? Integer.parseInt(s[1]): 9999);
    int want=Integer.parseInt(s[1]); p.wanted=want;
    AudioStream a=audio(i,true); if (a==null) return progressive(i, p, want);   // no AAC: only the single-file MP4 can be saved as MP4
    TreeMap<Integer,VideoStream> h264=avc(i); int top= h264.isEmpty()? 0: h264.firstKey();
    VideoStream best=null; boolean isAv1=false;
    for (Map.Entry<Integer,VideoStream> e: av1(i, top).entrySet()) if (e.getKey()<=want) { best=e.getValue(); isAv1=true; break; }
    if (best==null) for (Map.Entry<Integer,VideoStream> e: h264.entrySet()) if (e.getKey()<=want) { best=e.getValue(); break; }
    if (best==null && !h264.isEmpty()) best=h264.lastEntry().getValue();        // asked for less than the smallest: take the smallest
    if (best==null) return progressive(i, p, want);
    p.videoUrl=best.getContent(); p.videoLen=len(best); p.audioUrl=a.getContent(); p.audioLen=len(a); p.ext=Names.MP4; p.height=height(best);
    p.codec= isAv1? "av1": "h264"; p.label=p.height+"p"+(best.getFps()>30? best.getFps():"")+(isAv1? " AV1": "");
    return p; }
  static Pick progressive(StreamInfo i, Pick p, int want){ VideoStream best=null;
    for (VideoStream v: i.getVideoStreams()) if (ok(v) && v.getFormat()==MediaFormat.MPEG_4) { int h=height(v); if (best==null || (h<=want && (height(best)>want || h>height(best))) || (height(best)>want && h<height(best))) best=v; }
    if (best==null) return null;
    p.progressive=true; p.videoUrl=best.getContent(); p.videoLen=len(best); p.ext=Names.MP4; p.height=height(best); p.codec="h264"; p.label=p.height+"p"; return p; }
}
