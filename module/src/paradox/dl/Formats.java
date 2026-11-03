package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.MediaFormat;
import java.util.*;
/** Picks the streams for a download. Every video is saved as MP4 and every audio download as MP3:
 *  - video: every resolution the video has, up to its highest (1440p, 4K, 8K, 60fps, HDR), saved as <title>.mp4:
 *    heights YouTube serves as H.264 (avc1, up to 1080p) use H.264 + AAC through Android's MediaMuxer (plays everywhere);
 *    taller ones (and HDR) use AV1 when the phone has a hardware AV1 decoder for that size, else VP9, else AV1, + AAC,
 *    written by our own muxer (Mp4Mux) - no re-encode, any Android version.
 *  - audio: the best original audio track (Opus or AAC) is downloaded and converted to MP3 at 320/192/128 kbps.
 *  Specs: "v:<height>[:mp4|:av1|:vp9|:hdr|:any]", "p:<height>" (single-file MP4), "a:mp3:<kbps>", "a:rank:<0..2>"; older specs
 *  ("v:<h>:webm", "a:itag:<n>", "a:m4a", "a:opus") from older queues still work and give MP4 / MP3. */
public final class Formats {
  public static final class Pick { public String videoUrl, audioUrl, ext, label, codec=""; public long videoLen, audioLen; public boolean audioOnly, progressive, hdr; public int kbps, height, wanted, fps; public long durationUs;
    /** true = our own muxer (VP9 / AV1), false = Android's MediaMuxer (H.264). */
    public boolean ownMux(){ return codec.equals("vp9") || codec.equals("av1"); } }
  /** Tallest AV1 / VP9 video the phone decodes in hardware; -1 = ask the phone (Codecs). Tests set them directly. */
  public static volatile int av1MaxHeight=-1, vp9MaxHeight=-1;
  static int av1Max(){ int v=av1MaxHeight; if (v>=0) return v; try { return Codecs.hwMaxHeight(Codecs.AV1); } catch (Throwable t){ return 0; } }
  static int vp9Max(){ int v=vp9MaxHeight; if (v>=0) return v; try { return Codecs.hwMaxHeight(Codecs.VP9); } catch (Throwable t){ return 0; } }
  /** The quality's "p" number: YouTube's own label ("1080p60", "480p" for a 1080x608 stream, "1080p" for a 1080x1920 Short), else the pixel height. */
  public static int height(VideoStream v){ try { String r=v.getResolution(); if (r!=null && r.matches("^\\d{3,4}p.*")) return Integer.parseInt(r.substring(0, r.indexOf('p'))); } catch (Exception e){}
    try { return v.getHeight()>0? v.getHeight(): 0; } catch(Exception e){ return 0; } }
  static String codec(Stream s){ try { String c=((VideoStream)s).getCodec(); return c==null? "": c.toLowerCase(Locale.ROOT); } catch (Throwable t){ return ""; } }
  static int itag(Stream s){ try { return s.getItagItem()!=null? s.getItagItem().id: -1; } catch (Throwable t){ return -1; } }
  static final Set<Integer> VP9_ITAGS=new HashSet<>(Arrays.asList(242,243,244,245,246,247,248,271,272,278,302,303,308,313,315,330,331,332,333,334,335,336,337)),
    AV1_ITAGS=new HashSet<>(Arrays.asList(394,395,396,397,398,399,400,401,402,571,694,695,696,697,698,699,700,701,702)),
    AVC_ITAGS=new HashSet<>(Arrays.asList(133,134,135,136,137,160,212,264,266,298,299,304,305)),
    HDR_ITAGS=new HashSet<>(Arrays.asList(330,331,332,333,334,335,336,337,694,695,696,697,698,699,700,701,702));
  /** "avc" / "vp9" / "av1" / null (anything else, e.g. 3GP). */
  static String family(VideoStream v){ String c=codec(v); int it=itag(v);
    if (c.startsWith("avc")) return v.getFormat()==MediaFormat.MPEG_4? "avc": null;
    if (c.startsWith("av01")) return "av1";
    if (c.startsWith("vp9") || c.startsWith("vp09")) return "vp9";
    if (c.isEmpty() || c.equals("null")) { if (AVC_ITAGS.contains(it)) return "avc"; if (VP9_ITAGS.contains(it)) return "vp9"; if (AV1_ITAGS.contains(it)) return "av1"; }
    return null; }
  /** HDR: VP9 profile 2 ("vp9.2", "vp09.02..."), 10-bit AV1 on YouTube ("av01.0.13M.10"), or a known HDR itag. */
  static boolean hdr(VideoStream v){ String c=codec(v); if (HDR_ITAGS.contains(itag(v))) return true;
    if (c.startsWith("vp9.2") || c.startsWith("vp09.02") || c.startsWith("vp09.03")) return true;
    if (c.startsWith("av01")) { String[] p=c.split("\\."); return p.length>3 && (p[3].equals("10") || p[3].equals("12")); }
    return false; }
  static boolean isMp4(VideoStream v){ return "avc".equals(family(v)); }
  static boolean isAv1(VideoStream v){ return "av1".equals(family(v)); }
  static long len(Stream s){ try { if (s.getItagItem()!=null) return s.getItagItem().getContentLength(); } catch(Exception e){} return -1; }
  /** Known length, else bitrate x duration (YouTube's own estimate), else -1. */
  static long est(Stream s, long secs){ long l=len(s); if (l>0) return l; try { int br=s.getItagItem()!=null? s.getItagItem().getBitrate(): 0; if (br>0 && secs>0) return br*secs/8; } catch (Exception e){} return -1; }
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
  /** height -> best stream of one family (highest fps, then biggest), SDR or HDR only. */
  static TreeMap<Integer,VideoStream> best(StreamInfo i, String fam, boolean wantHdr){ TreeMap<Integer,VideoStream> m=new TreeMap<>(Collections.reverseOrder());
    for (VideoStream v: i.getVideoOnlyStreams()) { if (!ok(v) || !fam.equals(family(v)) || hdr(v)!=wantHdr) continue; int h=height(v); if (h<=0) continue; if (better(v, m.get(h))) m.put(h,v); } return m; }
  static TreeMap<Integer,VideoStream> avc(StreamInfo i){ return best(i, "avc", false); }
  /** One quality row. */
  public static final class Row { public String spec, label; public long size; public VideoStream v; public String codec; public boolean hdr; public int h;
    String[] arr(){ return new String[]{spec, label, String.valueOf(size)}; } }
  /** Above the H.264 heights (and for HDR): AV1 if the phone decodes that size in hardware, else VP9, else AV1. */
  static Object[] high(TreeMap<Integer,VideoStream> av1, TreeMap<Integer,VideoStream> vp9, int h){
    VideoStream a=av1.get(h), v=vp9.get(h);
    if (a!=null && av1Max()>=h) return new Object[]{a,"av1"};
    if (v!=null) return new Object[]{v,"vp9"};
    if (a!=null) return new Object[]{a,"av1"};
    return null; }
  static String vlabel(int h, String codec, boolean hdr){ String t; switch(h){ case 4320: t="8K"; break; case 2160: t="4K"; break; case 1440: t="QHD"; break; case 1080: t="Full HD"; break; case 720: t="HD"; break; default: t=null; }
    List<String> tags=new ArrayList<>(); if (t!=null) tags.add(t); if (hdr) tags.add("HDR"); if (codec.equals("av1")) tags.add("AV1"); else if (codec.equals("vp9")) tags.add("VP9");
    if (tags.isEmpty()) return h+"p"; StringBuilder b=new StringBuilder(); for (String x: tags){ if (b.length()>0) b.append(" · "); b.append(x); } return h+"p ("+b+")"; }
  /** Every video quality row (video + AAC), tallest first; an HDR row follows the normal row of the same height. */
  public static List<Row> rows(StreamInfo i){ List<Row> out=new ArrayList<>(); AudioStream a=audio(i,true); if (a==null) return out;
    TreeMap<Integer,VideoStream> h264=avc(i), av1=best(i,"av1",false), vp9=best(i,"vp9",false), av1h=best(i,"av1",true), vp9h=best(i,"vp9",true);
    TreeSet<Integer> hs=new TreeSet<>(Collections.reverseOrder()); hs.addAll(h264.keySet()); hs.addAll(av1.keySet()); hs.addAll(vp9.keySet()); hs.addAll(av1h.keySet()); hs.addAll(vp9h.keySet());
    long secs=Math.max(0,i.getDuration());
    int topHdr=Math.max(av1h.isEmpty()? 0: av1h.firstKey(), vp9h.isEmpty()? 0: vp9h.firstKey());   // HDR rows: 1080p and up, or the tallest HDR when it's lower
    for (int h: hs){
      if (h264.containsKey(h)) out.add(row(h, h264.get(h), a, "h264", false, secs));
      else { Object[] x=high(av1, vp9, h); if (x!=null) out.add(row(h, (VideoStream)x[0], a, (String)x[1], false, secs)); }
      if (h>=1080 || (!av1h.isEmpty() || !vp9h.isEmpty()) && h==topHdr) { Object[] y=high(av1h, vp9h, h); if (y!=null) out.add(row(h, (VideoStream)y[0], a, (String)y[1], true, secs)); } }
    return out; }
  static Row row(int h, VideoStream v, AudioStream a, String codec, boolean hdr, long secs){ Row r=new Row(); r.v=v; r.codec=codec; r.hdr=hdr; r.h=h;
    String fps= v.getFps()>30? " "+v.getFps()+"fps": "";
    r.spec="v:"+h+":"+(hdr? "hdr": codec.equals("h264")? "mp4": codec); r.label=vlabel(h, codec, hdr)+fps;
    long lv=len(v), la=len(a); if (lv>0 && la>0) r.size=lv+la; else { long ev=est(v,secs), ea=est(a,secs); r.size= ev>0 && ea>0? ev+ea: 0; }
    return r; }
  /** Video quality rows, tallest first: {spec, label, size}. All of them save as .mp4. */
  public static List<String[]> videoChoices(StreamInfo i){ List<String[]> out=new ArrayList<>();
    for (Row r: rows(i)) out.add(r.arr());
    if (out.isEmpty()) for (VideoStream v: i.getVideoStreams()) if (ok(v) && v.getFormat()==MediaFormat.MPEG_4) out.add(new String[]{"p:"+height(v), vlabel(height(v),"h264",false), String.valueOf(len(v))});
    return out; }
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
    int want=Integer.parseInt(s[1]); p.wanted=want; String hint= s.length>2? s[2]: "";
    AudioStream a=audio(i,true); if (a==null) return progressive(i, p, want);   // no AAC: only the single-file MP4 can be saved as MP4
    List<Row> rows=rows(i); if (rows.isEmpty()) return progressive(i, p, want);
    Row best=null;
    for (Row r: rows) if (r.spec.equals(spec)) { best=r; break; }                       // exactly the row that was tapped
    if (best==null && hint.equals("hdr")) for (Row r: rows) if (r.hdr && r.h<=want) { best=r; break; }
    if (best==null) for (Row r: rows) if (!r.hdr && r.h<=want) { best=r; break; }      // tallest normal row that fits
    if (best==null) for (int k=rows.size()-1;k>=0;k--) if (!rows.get(k).hdr) { best=rows.get(k); break; }   // asked for less than the smallest
    if (best==null) best=rows.get(rows.size()-1);
    VideoStream v=best.v;
    p.videoUrl=v.getContent(); p.videoLen=len(v); p.audioUrl=a.getContent(); p.audioLen=len(a); p.ext=Names.MP4; p.height=height(v); p.fps=v.getFps(); p.hdr=best.hdr;
    p.codec=best.codec; p.label=p.height+"p"+(v.getFps()>30? v.getFps():"")+(best.codec.equals("av1")? " AV1": best.codec.equals("vp9")? " VP9": "")+(best.hdr? " HDR": "");
    return p; }
  static Pick progressive(StreamInfo i, Pick p, int want){ VideoStream best=null;
    for (VideoStream v: i.getVideoStreams()) if (ok(v) && v.getFormat()==MediaFormat.MPEG_4) { int h=height(v); if (best==null || (h<=want && (height(best)>want || h>height(best))) || (height(best)>want && h<height(best))) best=v; }
    if (best==null) return null;
    p.progressive=true; p.videoUrl=best.getContent(); p.videoLen=len(best); p.ext=Names.MP4; p.height=height(best); p.codec="h264"; p.label=p.height+"p"; return p; }
}
