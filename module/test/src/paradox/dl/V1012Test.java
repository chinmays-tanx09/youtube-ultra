package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import java.util.*;
/** v10.12: original audio track (never a dubbed one), refresh keeps the track, list can't lose finished downloads (pure parts), Shorts-quality/auto-scroll are smali (not here). */
public class V1012Test {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static String u(int itag, String xtags){ return "https://rr1---sn-fake.googlevideo.com/videoplayback?expire=1&id=o-FAKE&itag="+itag+(xtags==null? "": "&xtags="+xtags)+"&c=ANDROID_VR"; }
  static AudioStream a(int itag, int kbps, String xtags, AudioTrackType type, String name) throws Exception {
    ItagItem it=new ItagItem(ItagItem.getItag(itag)); it.setContentLength(3_000_000);
    AudioStream.Builder b=new AudioStream.Builder().setId(itag+"-"+kbps+"-"+xtags).setContent(u(itag,xtags), true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setAverageBitrate(kbps).setItagItem(it);
    if (type!=null) b.setAudioTrackType(type); if (name!=null) b.setAudioTrackName(name); return b.build(); }
  static StreamInfo info(List<AudioStream> au) throws Exception {
    StreamInfo s=new StreamInfo(ServiceList.YouTube.getServiceId(), "https://youtu.be/FAKE", "https://youtu.be/FAKE", StreamType.VIDEO_STREAM, "FAKE", "Test", 0);
    s.setDuration(200); s.setVideoStreams(new ArrayList<VideoStream>()); s.setVideoOnlyStreams(new ArrayList<VideoStream>()); s.setAudioStreams(au); return s; }
  static final String HI="acont%3Ddubbed-auto%3Alang%3Dhi", BN="acont%3Ddubbed-auto%3Alang%3Dbn", EN="acont%3Doriginal%3Alang%3Den-US";
  public static void main(String[] args) throws Exception {
    // 1. classification
    check(Formats.trackRank("ORIGINAL",null,null)==0 && Formats.trackRank("DUBBED",null,null)==2 && Formats.trackRank("DESCRIPTIVE",null,null)==2 && Formats.trackRank("SECONDARY",null,null)==2, "type enum ranks");
    check(Formats.trackRank(null,null,u(140,EN))==0 && Formats.trackRank(null,null,u(140,HI))==2 && Formats.trackRank(null,null,u(140,null))==1, "xtags ranks (original / dubbed-auto / unmarked)");
    check(Formats.trackRank(null,"English (United States) original",u(140,null))==0 && Formats.trackRank(null,"Hindi (auto-dubbed)",u(140,null))==2 && Formats.trackRank(null,"Spanish descriptive",u(140,null))==2, "display-name ranks");
    check(Formats.xtags(u(140,HI)).equals("acont=dubbed-auto:lang=hi") && Formats.xtags(u(140,null)).equals("") && Formats.xtags(null).equals("") && Formats.xtags("https://x/?xtags=acont%3Doriginal").equals("acont=original") && Formats.xtags("https://x/?a=b&notxtags=zzz").equals(""), "xtags decode / absent / null");
    // 2. picks: dubbed tracks listed first and with a HIGHER bitrate still lose to the original
    List<AudioStream> au=Arrays.asList(a(140,129,HI,null,null), a(140,129,BN,null,null), a(140,128,EN,null,null), a(251,170,HI,null,null), a(251,160,EN,null,null));
    StreamInfo si=info(au);
    AudioStream m=Formats.audio(si,true); check(m!=null && m.getContent().contains("xtags="+EN) && m.getItag()==140, "MP4 audio = the original AAC track, not the first/highest dubbed one: "+(m==null?null:m.getContent()));
    AudioStream b=Formats.bestAudio(si); check(b!=null && b.getContent().contains("xtags="+EN) && b.getItag()==251, "MP3 source = original Opus (160k) not dubbed Opus (170k)");
    // unmarked original + marked dubbed -> the unmarked one
    si=info(Arrays.asList(a(140,129,HI,null,null), a(140,128,null,null,null)));
    m=Formats.audio(si,true); check(m!=null && !m.getContent().contains("xtags="), "unmarked track beats a dubbed one");
    // single-track video unchanged: highest bitrate
    si=info(Arrays.asList(a(140,128,null,null,null), a(251,160,null,null,null), a(250,70,null,null,null)));
    b=Formats.bestAudio(si); check(b!=null && b.getItag()==251, "single-track video: best bitrate as before");
    // NewPipe-typed tracks
    si=info(Arrays.asList(a(140,128,null,AudioTrackType.DUBBED,null), a(140,128,null,AudioTrackType.ORIGINAL,null)));
    m=Formats.audio(si,true); check(m!=null && m.getAudioTrackType()==AudioTrackType.ORIGINAL, "typed ORIGINAL beats typed DUBBED");
    // only dubbed tracks exist -> still returns one (never null)
    si=info(Arrays.asList(a(140,128,HI,null,null))); check(Formats.audio(si,true)!=null, "only a dubbed track: still downloadable");
    // 3. the account / innertube route: audioTrack JSON -> original
    String json="{\"playabilityStatus\":{\"status\":\"OK\"},\"streamingData\":{\"adaptiveFormats\":["
      +"{\"itag\":140,\"url\":\""+u(140,null).replace("&c=ANDROID_VR","&c=A&lang=hi")+"\",\"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\",\"bitrate\":130000,\"averageBitrate\":129000,\"audioTrack\":{\"displayName\":\"Hindi (auto-dubbed)\",\"id\":\"hi.10\",\"audioIsDefault\":false}},"
      +"{\"itag\":140,\"url\":\""+u(140,null).replace("&c=ANDROID_VR","&c=A&lang=en")+"\",\"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\",\"bitrate\":130000,\"averageBitrate\":128000,\"audioTrack\":{\"displayName\":\"English (United States) original\",\"id\":\"en-US.4\",\"audioIsDefault\":true}},"
      +"{\"itag\":140,\"url\":\""+u(140,null).replace("&c=ANDROID_VR","&c=A&lang=bn")+"\",\"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\",\"bitrate\":130000,\"averageBitrate\":129000,\"audioTrack\":{\"displayName\":\"Bengali (auto-dubbed)\",\"id\":\"bn.10\",\"audioIsDefault\":false}}]},"
      +"\"videoDetails\":{\"videoId\":\"FAKE\",\"title\":\"T\",\"lengthSeconds\":\"31\",\"channelId\":\"UC\",\"author\":\"A\"}}";
    PlayerProto.Player pl=Innertube.parse(json); check(pl!=null && pl.formats.size()==3 && pl.formats.get(1).audioIsDefault==1 && pl.formats.get(0).audioIsDefault==0 && pl.formats.get(1).audioTrackName.contains("original"), "innertube JSON audioTrack parsed");
    StreamInfo ti=Resolve.toInfo(pl,"FAKE","https://youtu.be/FAKE"); check(ti!=null && ti.getAudioStreams().size()==3, "toInfo keeps all three tracks");
    m=Formats.audio(ti,true); check(m!=null && m.getContent().contains("lang=en") && m.getAudioTrackType()==AudioTrackType.ORIGINAL, "account route: original English track chosen over the first-listed Hindi/Bengali dubs: "+(m==null?null:m.getContent()));
    int dub=0, orig=0; for (AudioStream x: ti.getAudioStreams()) { if (x.getAudioTrackType()==AudioTrackType.DUBBED) dub++; if (x.getAudioTrackType()==AudioTrackType.ORIGINAL) orig++; } check(dub==2 && orig==1, "track types set: 2 dubbed, 1 original");
    // 4. refresh (403 -> fresh extraction) keeps the chosen track: same itag exists once per language
    String chosen=m.getContent(); String same=DownloadService.sameStream(ti, chosen); check(same!=null && same.equals(chosen), "refresh stays on the original track (was: first stream with that itag)");
    si=info(Arrays.asList(a(140,129,HI,null,null), a(140,128,EN,null,null))); String old=u(140,EN); same=DownloadService.sameStream(si, old+"&expire=0"); check(same!=null && same.contains("xtags="+EN), "refresh with changed URL still picks the original xtags track");
    same=DownloadService.sameStream(si, u(140,HI)); check(same!=null && same.contains("xtags="+HI), "refresh of a dubbed choice (user spec by itag) stays on that track");
    // 5. download list robustness (pure parts of Store)
    List<Store.Item> out=new ArrayList<>(); check(Store.parseInto("not json {{", out)==-1 && out.isEmpty(), "corrupt file -> -1 (caller keeps it aside and reads the .bak; never overwrites with an empty list)");
    out.clear(); int bad=Store.parseInto("[{\"id\":\"a\",\"url\":\"u\",\"spec\":\"v:720:mp4\",\"title\":\"One\",\"status\":\"done\",\"path\":\"content://media/external_primary/video/media/9\"}, 42, {\"id\":\"\"}, {\"id\":\"b\",\"status\":\"downloading\",\"total\":100,\"done\":50}]", out);
    check(bad==2 && out.size()==2 && out.get(0).id.equals("a") && out.get(0).status.equals("done") && out.get(1).status.equals("paused") && out.get(1).progress==50, "one bad entry no longer drops the rest; interrupted -> paused: bad="+bad+" n="+out.size());
    Store.Item sv=Store.savedItem("content://media/external_primary/video/media/77","My Clip [4K].mp4",123456,1700000000L,false);
    check(sv.status.equals("done") && sv.title.equals("My Clip [4K]") && sv.total==123456 && sv.created==1700000000000L && !sv.audio && sv.path.endsWith("/77") && sv.id.equals(Store.savedItem(sv.path,"x.mp4",1,1,false).id), "saved file -> finished item with a stable id");
    check(Store.savedItem("/sdcard/Music/YouTube Ultra/a.mp3","a.mp3",5,0,true).audio, "mp3 -> audio item");
    System.out.println("V1012Test: "+pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1);
  }
}
