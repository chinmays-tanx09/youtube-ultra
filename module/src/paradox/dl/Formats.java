package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.MediaFormat;
import java.util.*;
/** Picks stream pairs for a choice spec. Spec: "v:<height>" (best video <= height, mp4 preferred up to 1080), "a:m4a", "a:opus". */
public final class Formats {
  public static final class Pick { public String videoUrl, audioUrl, ext, label; public long videoLen, audioLen; public boolean audioOnly, progressive; }
  public static int height(VideoStream v){ try { return v.getHeight()>0? v.getHeight(): Integer.parseInt(v.getResolution().replaceAll("p.*","")); } catch(Exception e){ return 0; } }
  static boolean isMp4(VideoStream v){ return v.getFormat()==MediaFormat.MPEG_4 && String.valueOf(v.getCodec()).startsWith("avc"); }
  static boolean isWebm(VideoStream v){ return v.getFormat()==MediaFormat.WEBM && String.valueOf(v.getCodec()).startsWith("vp9"); }
  static long len(Stream s){ try { if (s.getItagItem()!=null) return s.getItagItem().getContentLength(); } catch(Exception e){} return -1; }
  static boolean ok(Stream s){ return s.isUrl() && s.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP; }
  public static AudioStream audio(StreamInfo i, boolean m4a){
    AudioStream best=null;
    for (AudioStream a: i.getAudioStreams()) { if (!ok(a)) continue;
      boolean isM4a=a.getFormat()==MediaFormat.M4A; boolean isOpus=a.getFormat()==MediaFormat.WEBMA_OPUS || a.getFormat()==MediaFormat.WEBMA;
      if (m4a? !isM4a : !isOpus) continue;
      String tr=null; try { tr=a.getAudioTrackType()==null? null: a.getAudioTrackType().name(); } catch(Throwable t){}
      if (tr!=null && !tr.equals("ORIGINAL")) continue;
      if (best==null || a.getAverageBitrate()>best.getAverageBitrate()) best=a; }
    if (best==null) for (AudioStream a: i.getAudioStreams()) if (ok(a) && (m4a? a.getFormat()==MediaFormat.M4A : a.getFormat()!=MediaFormat.M4A) && (best==null || a.getAverageBitrate()>best.getAverageBitrate())) best=a;
    return best;
  }
  /** Distinct choices for UI: list of {spec,label,sizeBytes}. */
  public static List<String[]> choices(StreamInfo i){
    TreeMap<Integer,VideoStream> byH=new TreeMap<>(Collections.reverseOrder());
    for (VideoStream v: i.getVideoOnlyStreams()) { if (!ok(v)) continue; int h=height(v); if (h<=0) continue;
      VideoStream cur=byH.get(h);
      boolean better = cur==null || (isMp4(v) && !isMp4(cur)) || (isMp4(v)==isMp4(cur) && v.getFps()>cur.getFps());
      if (!isMp4(v) && !isWebm(v)) continue;
      if (better) byH.put(h, v); }
    AudioStream am=audio(i,true), ao=audio(i,false);
    List<String[]> out=new ArrayList<>();
    for (Map.Entry<Integer,VideoStream> e: byH.entrySet()) { VideoStream v=e.getValue(); boolean mp4=isMp4(v);
      AudioStream a= mp4? am: (ao!=null? ao: am); long sz=len(v)+(a!=null? len(a):0);
      String fps= v.getFps()>30? String.valueOf(v.getFps()):"";
      out.add(new String[]{"v:"+e.getKey()+":"+(mp4?"mp4":"webm"), e.getKey()+"p"+fps+"  "+(mp4?"MP4":"WEBM"), String.valueOf(sz)}); }
    if (am!=null) out.add(new String[]{"a:m4a", "Audio  M4A "+am.getAverageBitrate()+"k", String.valueOf(len(am))});
    if (ao!=null) out.add(new String[]{"a:opus", "Audio  OPUS "+ao.getAverageBitrate()+"k", String.valueOf(len(ao))});
    if (out.isEmpty()) for (VideoStream v: i.getVideoStreams()) if (ok(v)) { out.add(new String[]{"p:"+height(v), height(v)+"p  MP4 (single file)", String.valueOf(len(v))}); }
    return out;
  }
  static String vlabel(int h){ switch(h){ case 4320: return "4320p (8K)"; case 2160: return "2160p (4K)"; case 1440: return "1440p (QHD)"; case 1080: return "1080p (Full HD)"; case 720: return "720p (HD)"; default: return h+"p"; } }
  /** Video quality rows: {spec, label, size}. */
  public static List<String[]> videoChoices(StreamInfo i){ List<String[]> out=new ArrayList<>();
    for (String[] c: choices(i)) if (c[0].startsWith("v:")||c[0].startsWith("p:")) { int h=Integer.parseInt(c[0].split(":")[1]); String fps=c[1].replaceAll("^\\d+p(\\d*).*$","$1"); out.add(new String[]{c[0], vlabel(h)+(fps.isEmpty()?"":" "+fps+"fps"), c[2]}); }
    return out; }
  /** Audio quality rows (original track only), best first: {spec "a:itag:<n>", label, size}. */
  public static List<String[]> audioChoices(StreamInfo i){ List<AudioStream> as=new ArrayList<>();
    for (AudioStream a: i.getAudioStreams()) { if (!ok(a)) continue; String tr=null; try { tr=a.getAudioTrackType()==null? null: a.getAudioTrackType().name(); } catch(Throwable t){} if (tr!=null && !tr.equals("ORIGINAL")) continue; as.add(a); }
    Collections.sort(as, (x,y) -> y.getAverageBitrate()-x.getAverageBitrate());
    List<String[]> out=new ArrayList<>(); Set<Integer> seen=new HashSet<>();
    for (AudioStream a: as){ int br=a.getAverageBitrate(); int bucket= br>=140? 0: br>=96? 1: 2; if (!seen.add(bucket)) continue;
      String name= bucket==0? "High": bucket==1? "Medium": "Low"; String fmt= a.getFormat()==MediaFormat.M4A? "M4A":"Opus";
      out.add(new String[]{"a:itag:"+a.getItag(), name+" · "+br+" kbps ("+fmt+")", String.valueOf(len(a))}); }
    return out; }
  public static Pick pick(StreamInfo i, String spec){
    Pick p=new Pick(); String[] s=spec.split(":");
    if (s[0].equals("a") && s[1].equals("rank")) { List<String[]> ac=audioChoices(i); if (ac.isEmpty()) return null; return pick(i, ac.get(Math.min(Integer.parseInt(s[2]), ac.size()-1))[0]); }
    if (s[0].equals("a") && s[1].equals("itag")) { int it=Integer.parseInt(s[2]); AudioStream a=null; for (AudioStream x: i.getAudioStreams()) if (ok(x) && x.getItag()==it) { a=x; break; }
      if (a==null) a=audio(i,true); if (a==null) a=audio(i,false); if (a==null) return null;
      p.audioOnly=true; p.audioUrl=a.getContent(); p.audioLen=len(a); p.ext= a.getFormat()==MediaFormat.M4A? "m4a":"opus"; p.label=a.getAverageBitrate()+"kbps"; return p; }
    if (s[0].equals("a")) { AudioStream a=audio(i, s[1].equals("m4a")); if (a==null) a=audio(i, !s[1].equals("m4a")); if (a==null) return null;
      p.audioOnly=true; p.audioUrl=a.getContent(); p.audioLen=len(a); p.ext= a.getFormat()==MediaFormat.M4A? "m4a":"opus"; p.label=a.getAverageBitrate()+"kbps"; return p; }
    if (s[0].equals("p")) { for (VideoStream v: i.getVideoStreams()) if (ok(v)) { p.progressive=true; p.videoUrl=v.getContent(); p.videoLen=len(v); p.ext="mp4"; p.label=height(v)+"p"; return p; } return null; }
    int want=Integer.parseInt(s[1]); boolean wantMp4= s.length<3 || s[2].equals("mp4");
    VideoStream best=null;
    for (VideoStream v: i.getVideoOnlyStreams()) { if (!ok(v)) continue; if (!(isMp4(v)||isWebm(v))) continue; int h=height(v); if (h>want) continue;
      if (best==null) { best=v; continue; }
      int bh=height(best);
      if (h>bh) best=v; else if (h==bh) { if (isMp4(v)==wantMp4 && isMp4(best)!=wantMp4) best=v; else if (isMp4(v)==isMp4(best) && v.getFps()>best.getFps()) best=v; } }
    if (best==null) { for (VideoStream v: i.getVideoStreams()) if (ok(v)) { p.progressive=true; p.videoUrl=v.getContent(); p.videoLen=len(v); p.ext="mp4"; p.label=height(v)+"p"; return p; } return null; }
    boolean mp4=isMp4(best); AudioStream a= mp4? audio(i,true): audio(i,false); if (a==null) a=audio(i,!mp4);
    if (a==null) return null;
    boolean aMp4= a.getFormat()==MediaFormat.M4A;
    p.videoUrl=best.getContent(); p.videoLen=len(best); p.audioUrl=a.getContent(); p.audioLen=len(a);
    p.ext= (mp4 && aMp4)? "mp4": "webm"; p.label=height(best)+"p"+(best.getFps()>30? best.getFps():"");
    return p;
  }
}
