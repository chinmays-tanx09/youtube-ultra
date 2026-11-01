package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import java.nio.charset.StandardCharsets; import java.util.*;
/** v10.9: every video saves as MP4 (H.264/AV1 + AAC, never VP9/Opus), every audio download as MP3; file names; intro rules. */
public class FormatsTest {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static String url(int itag){ return "https://rr1---sn-fake.googlevideo.com/videoplayback?itag="+itag+"&id=FAKE"; }
  static VideoStream v(int itag, String codec, int h, int fps, long clen, boolean videoOnly) throws Exception {
    ItagItem it=new ItagItem(ItagItem.getItag(itag)); it.setCodec(codec); it.setHeight(h); it.setWidth(h*16/9); if (fps>0) it.setFps(fps); it.setContentLength(clen);
    return new VideoStream.Builder().setId(String.valueOf(itag)).setContent(url(itag), true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
      .setIsVideoOnly(videoOnly).setResolution(h+"p"+(fps>30? fps: "")).setItagItem(it).build(); }
  static AudioStream a(int itag, int kbps, long clen, AudioTrackType type) throws Exception {
    ItagItem it=new ItagItem(ItagItem.getItag(itag)); it.setContentLength(clen);
    AudioStream.Builder b=new AudioStream.Builder().setId(String.valueOf(itag)).setContent(url(itag), true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setAverageBitrate(kbps).setItagItem(it);
    if (type!=null) b.setAudioTrackType(type); return b.build(); }
  static StreamInfo info(List<VideoStream> muxed, List<VideoStream> vonly, List<AudioStream> au) throws Exception {
    StreamInfo s=new StreamInfo(ServiceList.YouTube.getServiceId(), "https://youtu.be/FAKE", "https://youtu.be/FAKE", StreamType.VIDEO_STREAM, "FAKE", "Test video", 0);
    s.setDuration(200); s.setVideoStreams(muxed); s.setVideoOnlyStreams(vonly); s.setAudioStreams(au); return s; }
  static String rows(List<String[]> l){ StringBuilder b=new StringBuilder(); for (String[] r: l) b.append(r[0]).append('=').append(r[1]).append(' '); return b.toString().trim(); }
  static boolean has(String u, int itag){ return u!=null && u.contains("itag="+itag+"&"); }
  public static void main(String[] args) throws Exception {
    // A typical 4K upload: H.264 up to 1080p (+1080p60), VP9 and AV1 above that, AAC 128k + Opus 160k (+ a dubbed track).
    List<VideoStream> vo=Arrays.asList(
      v(137,"avc1.640028",1080,30,50_000_000,true), v(299,"avc1.64002a",1080,60,70_000_000,true), v(136,"avc1.4d401f",720,30,20_000_000,true), v(135,"avc1.4d401e",480,30,9_000_000,true),
      v(248,"vp9",1080,30,30_000_000,true), v(271,"vp9",1440,30,90_000_000,true), v(313,"vp9",2160,30,200_000_000,true),
      v(399,"av01.0.08M.08",1080,30,25_000_000,true), v(400,"av01.0.12M.08",1440,30,60_000_000,true), v(401,"av01.0.12M.08",2160,30,150_000_000,true));
    List<AudioStream> au=Arrays.asList(a(140,128,3_000_000,AudioTrackType.ORIGINAL), a(251,160,3_500_000,AudioTrackType.ORIGINAL), a(250,70,1_500_000,AudioTrackType.ORIGINAL), a(600,200,4_000_000,AudioTrackType.DUBBED));
    List<VideoStream> mux=Collections.singletonList(v(18,"avc1.42001E, mp4a.40.2",360,30,9_000_000,false));
    StreamInfo si=info(mux, vo, au);

    // 1. Phone without AV1-in-MP4 (Android <= 13, or no hardware AV1 decoder): H.264 only, never VP9.
    Formats.av1MaxHeight=0;
    List<String[]> vc=Formats.videoChoices(si);
    check(rows(vc).equals("v:1080:mp4=1080p (Full HD) 60fps v:720:mp4=720p (HD) v:480:mp4=480p"), "no-AV1 rows are H.264 only: "+rows(vc));
    check(vc.get(0)[2].equals(String.valueOf(70_000_000+3_000_000)), "1080p60 size = video + AAC: "+vc.get(0)[2]);
    Formats.Pick p=Formats.pick(si,"v:1080:mp4");
    check(p!=null && has(p.videoUrl,299) && has(p.audioUrl,140) && p.ext.equals("mp4") && p.codec.equals("h264") && p.label.equals("1080p60"), "1080 -> 299 (60fps H.264) + 140 AAC, .mp4");
    p=Formats.pick(si,"v:2160:mp4");
    check(p!=null && has(p.videoUrl,299) && p.height==1080 && p.wanted==2160 && p.ext.equals("mp4"), "remembered 2160 on a no-AV1 phone -> best H.264 (1080p), still .mp4");
    for (String sp: new String[]{"v:1080:webm","v:1440:webm","v:2160:av1","v:720:webm"}) { p=Formats.pick(si,sp);
      check(p!=null && p.ext.equals("mp4") && !has(p.videoUrl,248) && !has(p.videoUrl,271) && !has(p.videoUrl,313) && has(p.audioUrl,140), sp+" -> MP4 with H.264 + AAC, never VP9/Opus"); }
    p=Formats.pick(si,"v:720:mp4"); check(p!=null && has(p.videoUrl,136), "720 -> 136");
    p=Formats.pick(si,"v:144:mp4"); check(p!=null && has(p.videoUrl,135), "below the smallest -> smallest H.264 (480p)");

    // 2. Phone that can write AV1 into MP4 up to 4K (Android 14+, hardware AV1).
    Formats.av1MaxHeight=2160;
    vc=Formats.videoChoices(si);
    check(rows(vc).equals("v:2160:av1=2160p (4K · AV1) v:1440:av1=1440p (QHD · AV1) v:1080:mp4=1080p (Full HD) 60fps v:720:mp4=720p (HD) v:480:mp4=480p"), "AV1 phone adds 1440p/4K as AV1 MP4: "+rows(vc));
    p=Formats.pick(si,"v:2160:av1"); check(p!=null && has(p.videoUrl,401) && has(p.audioUrl,140) && p.ext.equals("mp4") && p.codec.equals("av1"), "4K -> 401 AV1 + 140 AAC in .mp4");
    p=Formats.pick(si,"v:1440:av1"); check(p!=null && has(p.videoUrl,400) && p.codec.equals("av1"), "1440 -> 400 AV1");
    p=Formats.pick(si,"v:1080:mp4"); check(p!=null && has(p.videoUrl,299) && p.codec.equals("h264"), "1080 stays H.264 even when AV1 1080p exists");
    p=Formats.pick(si,"v:2160:mp4"); check(p!=null && has(p.videoUrl,401), "remembered 2160 on an AV1 phone -> 4K AV1 MP4");
    // 3. Hardware AV1 only up to 1440p.
    Formats.av1MaxHeight=1440;
    check(rows(Formats.videoChoices(si)).startsWith("v:1440:av1=1440p (QHD · AV1) v:1080:mp4"), "AV1 capped at the decoder's max (1440p)");
    p=Formats.pick(si,"v:2160:av1"); check(p!=null && has(p.videoUrl,400), "4K asked, decoder max 1440 -> 400");
    Formats.av1MaxHeight=0;

    // 4. No AAC at all (Opus only): an MP4 can't hold Opus, so the single-file MP4 (itag 18) is used.
    StreamInfo noAac=info(mux, vo, Arrays.asList(a(251,160,3_500_000,null)));
    check(rows(Formats.videoChoices(noAac)).equals("p:360=360p"), "no AAC -> only the single-file MP4 row: "+rows(Formats.videoChoices(noAac)));
    p=Formats.pick(noAac,"v:1080:mp4"); check(p!=null && p.progressive && has(p.videoUrl,18) && p.ext.equals("mp4"), "no AAC -> itag 18 single-file MP4");
    check(Formats.pick(info(new ArrayList<VideoStream>(), vo, Arrays.asList(a(251,160,1,null))),"v:1080:mp4")==null, "no AAC and no single-file MP4 -> no pick (clear error, no .webm)");

    // 5. Audio -> MP3.
    List<String[]> ac=Formats.audioChoices(si);
    check(rows(ac).equals("a:mp3:320=MP3 320 kbps (Best) a:mp3:192=MP3 192 kbps (Standard) a:mp3:128=MP3 128 kbps (Smaller)"), "audio rows: "+rows(ac));
    check(ac.get(0)[2].equals(String.valueOf(200L*320*1000/8+2048)) && ac.get(2)[2].equals(String.valueOf(200L*128*1000/8+2048)), "MP3 size estimate from duration");
    p=Formats.pick(si,"a:mp3:192"); check(p!=null && p.audioOnly && p.ext.equals("mp3") && p.kbps==192 && has(p.audioUrl,251) && p.codec.equals("opus") && p.durationUs==200_000_000L, "MP3 192 from the best ORIGINAL track (251 Opus 160k, not the dubbed 200k)");
    int[][] ranks={{0,320},{1,192},{2,128},{9,128}}; for (int[] r: ranks){ p=Formats.pick(si,"a:rank:"+r[0]); check(p!=null && p.kbps==r[1] && p.ext.equals("mp3"), "remembered rank "+r[0]+" -> "+r[1]+" kbps"); }
    String[][] legacy={{"a:itag:251","320"},{"a:itag:140","192"},{"a:itag:250","128"},{"a:m4a","320"},{"a:opus","320"},{"a:mp3:999","320"}};
    for (String[] l: legacy){ p=Formats.pick(si,l[0]); check(p!=null && p.ext.equals("mp3") && String.valueOf(p.kbps).equals(l[1]), "old spec "+l[0]+" -> MP3 "+l[1]+" (got "+(p==null?0:p.kbps)+")"); }
    p=Formats.pick(info(mux, vo, Arrays.asList(a(140,128,3_000_000,null))),"a:mp3:320"); check(p!=null && has(p.audioUrl,140) && p.codec.equals("aac"), "AAC-only video -> MP3 from AAC");

    // 6. File names.
    check(Names.file("My Video","mp4").equals("My Video.mp4") && Names.file("My Song","mp3").equals("My Song.mp3"), "<title>.mp4 / <title>.mp3");
    check(Names.file("AC/DC: Back In Black? \"Live\" <2024> | *HQ*","mp4").equals("AC_DC_ Back In Black_ _Live_ _2024_ _ _HQ_.mp4"), "illegal characters -> _: "+Names.file("AC/DC: Back In Black? \"Live\" <2024> | *HQ*","mp4"));
    check(Names.file("  ..hidden.  ","mp3").equals("hidden.mp3"), "no leading/trailing dots or spaces: "+Names.file("  ..hidden.  ","mp3"));
    check(Names.file("line1\nline2\ttab","mp4").equals("line1 line2 tab.mp4"), "control chars -> space");
    check(Names.file("a\u200Fb\u200Bc","mp4").equals("abc.mp4"), "invisible bidi/zero-width chars dropped");
    check(Names.file("Kesariya 🔥 केसरिया","mp3").equals("Kesariya 🔥 केसरिया.mp3"), "emoji and Hindi kept");
    check(Names.file("","mp4").equals("video.mp4") && Names.file(null,"mp3").equals("audio.mp3") && Names.file("???","mp4").equals("___.mp4") && Names.file(" . ","mp3").equals("audio.mp3"), "empty titles -> video.mp4 / audio.mp3");
    StringBuilder longT=new StringBuilder(); for (int k=0;k<120;k++) longT.append("केस😀");
    String ln=Names.file(longT.toString(),"mp3"); byte[] lb=ln.getBytes(StandardCharsets.UTF_8);
    check(lb.length<=Names.MAX_BYTES && ln.endsWith(".mp3") && new String(lb,StandardCharsets.UTF_8).equals(ln) && !ln.contains("\uFFFD") && Character.isHighSurrogate(ln.charAt(ln.length()-5))==false, "long multi-byte title cut to <=200 UTF-8 bytes on a character boundary ("+lb.length+")");
    check(Names.mime("mp4").equals("video/mp4") && Names.mime("mp3").equals("audio/mpeg"), "MIME types video/mp4, audio/mpeg");
    check(Names.numbered("Song.mp3",2).equals("Song (2).mp3"), "legacy collision name");

    // 7. ID3 tag structure (full check with ffprobe in Mp3RoundTrip).
    byte[] tag=Id3.tag("केसरिया 🔥", "Arijit Singh", null, new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,1,2,3});
    int size=((tag[6]&0x7f)<<21)|((tag[7]&0x7f)<<14)|((tag[8]&0x7f)<<7)|(tag[9]&0x7f);
    check(tag[0]=='I' && tag[1]=='D' && tag[2]=='3' && tag[3]==3 && size==tag.length-10, "ID3v2.3 header + syncsafe size");
    String ts=new String(tag, StandardCharsets.ISO_8859_1); check(ts.contains("TIT2") && ts.contains("TPE1") && ts.contains("APIC") && ts.contains("image/jpeg") && !ts.contains("TALB"), "TIT2/TPE1/APIC present, empty album skipped");
    check(!new String(Id3.tag("t","a",null,new byte[]{1,2,3,4,5,6,7,8,9,10}),StandardCharsets.ISO_8859_1).contains("APIC"), "non-image cover is skipped");

    // 8. Intro: fit inside the screen, first run only.
    int[] r=IntroActivity.fitInside(1080,2400,1080,1920); check(r[0]==1080 && r[1]==1920, "portrait phone: full width, letterboxed: "+r[0]+"x"+r[1]);
    r=IntroActivity.fitInside(2400,1080,1080,1920); check(r[0]==607 && r[1]==1080, "landscape phone: full height, pillarboxed, not cropped: "+r[0]+"x"+r[1]);
    r=IntroActivity.fitInside(1600,2560,1080,1920); check(r[0]==1440 && r[1]==2560, "tablet portrait: full height: "+r[0]+"x"+r[1]);
    r=IntroActivity.fitInside(1920,1080,1920,1080); check(r[0]==1920 && r[1]==1080, "same aspect fills");
    check(IntroActivity.shouldPlay(-1, 1552692684L), "fresh install (nothing stored) -> intro plays");
    check(!IntroActivity.shouldPlay(1552692684L, 1552692684L), "already shown for this version -> straight to home");
    check(IntroActivity.shouldPlay(-1, 1552692684L), "update from v10.8 (no value stored yet) -> plays once");
    check(IntroActivity.shouldPlay(1552692684L, 1552692685L), "next update -> plays once again");
    check(!IntroActivity.shouldPlay(-1, 0), "unknown versionCode -> no intro (fail safe)");

    System.out.println("FormatsTest: "+pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1);
  }
}
