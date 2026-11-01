package paradox.dl;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest;
import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.exceptions.*;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.Callable;
/** "Download with my account" tests. Runs on a plain JVM against stubs of the app's extension and saved
 *  player-response fixtures that contain only fake URLs / ids. Run: see module/test/README.md */
public class AccountDownloadTest {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static String fakeUrl(int itag, long clen){ return "https://rr1---sn-fake00.googlevideo.com/videoplayback?expire=1700000000&id=o-FAKEVIDEO&itag="+itag+"&clen="+clen+"&n=FAKE_N&sig=FAKE_SIG"; }
  static Pb fmt(int itag, String mime, int w, int h, int fps, long clen, int br, String ql){ Pb f=new Pb().v(1,itag).s(2,fakeUrl(itag,clen)).s(5,mime).v(6,br); if (w>0) f.v(7,w).v(8,h); f.v(12,clen); if (fps>0) f.v(25,fps); if (ql!=null) f.s(26,ql); f.v(31,br-1000).v(44,1234000); return f; }

  static byte[] signedFixture(){
    Pb sd=new Pb()
      .m(2, fmt(18, "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", 640, 360, 30, 9_000_000, 500_000, "360p"))
      .m(3, fmt(137, "video/mp4; codecs=\"avc1.640028\"", 1920, 1080, 30, 50_000_000, 4_000_000, "1080p"))
      .m(3, fmt(248, "video/webm; codecs=\"vp9\"", 1920, 1080, 30, 30_000_000, 2_600_000, "1080p"))
      .m(3, fmt(136, "video/mp4; codecs=\"avc1.4d401f\"", 1280, 720, 30, 20_000_000, 1_500_000, "720p"))
      .m(3, fmt(247, "video/webm; codecs=\"vp9\"", 1280, 720, 30, 15_000_000, 1_200_000, "720p"))
      .m(3, new Pb().v(1,140).s(2,fakeUrl(140,3_000_000)).s(5,"audio/mp4; codecs=\"mp4a.40.2\"").v(6,130_000).v(12,3_000_000).v(31,129_000).v(44,1234000).v(45,44100).v(46,2))
      .m(3, new Pb().v(1,251).s(2,fakeUrl(251,2_800_000)).s(5,"audio/webm; codecs=\"opus\"").v(6,150_000).v(12,2_800_000).v(31,141_000).v(44,1234000).v(45,48000).v(46,2))
      .m(3, new Pb().v(1,22).s(5,"video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"").s(48,"s=FAKECIPHER&sp=sig&url=https%3A%2F%2Ffake"))   // cipher only: skipped
      .m(3, new Pb().v(1,9999).s(2,fakeUrl(9999,1)).s(5,"video/mp4; codecs=\"avc1\""))                                                 // unknown itag: skipped
      .s(15, "https://rr1---sn-fake00.googlevideo.com/videoplayback?sabr=1&fake=1");
    Pb details=new Pb().s(1,"fAkEmEmB3r0").s(15,"Members stream #12 (fake)").v(16,1234).s(19,"UCfakechannel000000000")
      .m(25, new Pb().m(1, new Pb().s(1,"https://i.ytimg.com/vi/fAkEmEmB3r0/default.jpg").v(2,120).v(3,90)).m(1, new Pb().s(1,"https://i.ytimg.com/vi/fAkEmEmB3r0/hqdefault.jpg").v(2,480).v(3,360)))
      .s(33,"Fake Channel");
    return new Pb().m(1, new Pb().s(9,"FAKEVISITORDATA").f32(3, 7)).m(2, new Pb().v(1,0)).m(4, sd).m(11, details)
      .m(15, new Pb().m(55405814, new Pb().v(4,0))).bytes(); }
  static byte[] lockedFixture(){
    return new Pb().m(1, new Pb().s(9,"FAKEVISITORDATA")).m(2, new Pb().v(1,3).s(2,"Join this channel to get access to members-only content like this video, and other exclusive perks."))
      .m(11, new Pb().s(1,"fAkEmEmB3r0").s(15,"Members stream #12 (fake)")).bytes(); }

  static void reset(){ AuthUtils.authorization=""; AuthUtils.visitorId=""; AuthUtils.pageId=""; AuthUtils.incognito=false;
    app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.currentVideoRequestHeader=null; StreamOrDetailsDataRequest.responses.clear(); StreamOrDetailsDataRequest.calls.clear(); }
  static void signIn(){ AuthUtils.authorization="Bearer FAKE_TOKEN_FOR_TESTS"; AuthUtils.visitorId="FAKEVISITOR"; AuthUtils.pageId=""; }
  static Callable<StreamInfo> throwing(final Exception e){ return () -> { throw e; }; }
  static final String URL="https://www.youtube.com/watch?v=fAkEmEmB3r0";

  public static void main(String[] a) throws Exception {
    Formats.av1MaxHeight=0;   // plain JVM: no MediaCodec; FormatsTest covers the AV1 paths
    Path dir=Paths.get(a.length>0? a[0]: "module/test/fixtures");
    if (a.length>1 && a[1].equals("--write-fixtures")) { Files.write(dir.resolve("player_signed_in.pb"), signedFixture()); Files.write(dir.resolve("player_members_locked.pb"), lockedFixture()); System.out.println("fixtures written"); return; }
    byte[] signed=Files.readAllBytes(dir.resolve("player_signed_in.pb")), locked=Files.readAllBytes(dir.resolve("player_members_locked.pb"));
    check(Arrays.equals(signed, signedFixture()) && Arrays.equals(locked, lockedFixture()), "saved fixtures match their generator");

    // 1. protobuf reading
    PlayerProto.Player p=PlayerProto.parse(signed);
    check(p!=null && p.status==PlayerProto.OK && p.playable(), "signed fixture playable");
    check(p.formats.size()==9, "9 formats read, got "+(p==null? -1: p.formats.size()));
    check(p.videoId.equals("fAkEmEmB3r0") && p.title.startsWith("Members stream") && p.author.equals("Fake Channel") && p.lengthSeconds==1234 && p.thumbs.size()==2, "video details");
    PlayerProto.Fmt f137=null; for (PlayerProto.Fmt x: p.formats) if (x.itag==137) f137=x;
    check(f137!=null && f137.adaptive && f137.height==1080 && f137.contentLength==50_000_000 && "avc1.640028".equals(f137.codec()), "itag 137 fields");
    check(!p.formats.get(0).adaptive && p.formats.get(0).itag==18, "progressive format flagged");
    PlayerProto.Player lp=PlayerProto.parse(locked);
    check(lp!=null && lp.status==PlayerProto.LOGIN_REQUIRED && !lp.playable() && lp.reason.contains("members-only"), "locked fixture not playable");
    check(PlayerProto.parse(new byte[]{0x0a, 0x7f, 0x01})==null, "truncated input -> null, no crash");

    // 2. stream selection on the account response
    StreamInfo si=Resolve.toInfo(p, "fAkEmEmB3r0", URL);
    check(si!=null && si.getVideoOnlyStreams().size()==4 && si.getAudioStreams().size()==2 && si.getVideoStreams().size()==1, "4 video-only, 2 audio, 1 progressive (cipher-only + unknown itag skipped)");
    List<String[]> vc=Formats.videoChoices(si);
    check(vc.size()==2 && vc.get(0)[0].equals("v:1080:mp4") && vc.get(1)[0].equals("v:720:mp4"), "video rows 1080 MP4, 720 MP4: "+rows(vc));
    check(vc.get(0)[2].equals(String.valueOf(50_000_000+3_000_000)), "1080 size = video + m4a");
    Formats.Pick pk=Formats.pick(si, "v:1080:mp4");
    check(pk!=null && pk.videoUrl.contains("itag=137") && pk.audioUrl.contains("itag=140") && pk.videoLen==50_000_000 && pk.audioLen==3_000_000 && pk.ext.equals("mp4"), "pick 1080 mp4 -> 137 + 140");
    Formats.Pick pw=Formats.pick(si, "v:1080:webm");
    check(pw!=null && pw.videoUrl.contains("itag=137") && pw.audioUrl.contains("itag=140") && pw.ext.equals("mp4"), "old v10.8 'webm' spec now saves MP4 -> 137 + 140");
    Formats.Pick p720=Formats.pick(si, "v:720:mp4");
    check(p720!=null && p720.videoUrl.contains("itag=136"), "pick 720 -> 136");
    List<String[]> ac=Formats.audioChoices(si);
    check(ac.size()==3 && ac.get(0)[0].equals("a:mp3:320") && ac.get(1)[0].equals("a:mp3:192") && ac.get(2)[0].equals("a:mp3:128"), "audio rows are MP3 320/192/128: "+rows(ac));
    Formats.Pick pa=Formats.pick(si, "a:itag:140");
    check(pa!=null && pa.audioOnly && pa.ext.equals("mp3") && (pa.kbps==192 || pa.kbps==320), "old v10.8 'a:itag:140' spec now saves MP3 (kbps "+(pa==null?0:pa.kbps)+")");
    check(DownloadService.sameStream(si, fakeUrl(137, 1).replace("FAKEVIDEO","OLD"))!=null && DownloadService.sameStream(si, fakeUrl(137,1)).contains("itag=137"), "URL refresh finds same itag");
    check(si.getName().startsWith("Members stream") && si.getUploaderName().equals("Fake Channel") && si.getThumbnails().size()==2, "title/channel/thumbs carried");

    // 3. resolve(): signed out
    reset();
    check(Account.headers()==null, "signed out -> no headers");
    try { Resolve.resolve(URL, throwing(new PaidContentException("This video is only available for members of the channel of this video"))); check(false, "signed-out members should throw"); }
    catch (Resolve.Plain e){ check(e.getMessage().equals(Resolve.MEMBERS_SIGNED_OUT), "signed-out members message: "+e.getMessage()); }
    check(StreamOrDetailsDataRequest.calls.isEmpty(), "signed out -> account path never called");
    Exception bot=new SignInConfirmNotBotException("Sign in to confirm that you're not a bot");
    try { Resolve.resolve(URL, throwing(bot)); check(false,"should throw"); } catch (Exception e){ check(e==bot, "signed out, other error -> original error unchanged"); }

    // 4. signed in, membership on the account: public extractor fails, account path succeeds
    reset(); signIn(); StreamOrDetailsDataRequest.responses.put(ClientType.ANDROID_CREATOR, signed);
    StreamInfo r=Resolve.resolve(URL, throwing(new PaidContentException("This video is only available for members of the channel of this video")));
    check(r!=null && Formats.videoChoices(r).size()==2, "signed-in members video resolves");
    check(StreamOrDetailsDataRequest.calls.equals(Arrays.asList("fAkEmEmB3r0|TV_SIMPLY|Bearer FAKE_TOKEN_FOR_TESTS", "fAkEmEmB3r0|ANDROID_CREATOR|Bearer FAKE_TOKEN_FOR_TESTS")), "clients tried in the app's order with the app's session: "+StreamOrDetailsDataRequest.calls);
    Formats.Pick rp=Formats.pick(r, "v:1080:mp4");
    check(Net.ua(rp.videoUrl).equals(ClientType.ANDROID_CREATOR.userAgent) && Net.ua(rp.audioUrl).equals(ClientType.ANDROID_CREATOR.userAgent), "engine uses the fetching client's User-Agent");
    check(Net.ua("https://example.com/other").equals(Net.UA), "other URLs keep the default User-Agent");

    // 5. signed in, "sign in to confirm you're not a bot" -> account path
    reset(); signIn(); StreamOrDetailsDataRequest.responses.put(ClientType.TV_SIMPLY, signed);
    check(Resolve.resolve(URL, throwing(bot))!=null, "bot-check error -> account path");

    // 6. signed in, no membership: the app returns nothing (or an unplayable response) -> plain message
    reset(); signIn();
    try { Resolve.resolve(URL, throwing(new PaidContentException("This video is only available for members of the channel of this video"))); check(false,"should throw"); }
    catch (Resolve.Plain e){ check(e.getMessage().equals(Resolve.MEMBERS), "no membership -> plain message"); }
    reset(); signIn(); StreamOrDetailsDataRequest.responses.put(ClientType.TV_SIMPLY, locked); StreamOrDetailsDataRequest.responses.put(ClientType.ANDROID_CREATOR, locked);
    try { Resolve.resolve(URL, throwing(new ContentNotAvailableException("Join this channel to get access to members-only content like this video"))); check(false,"should throw"); }
    catch (Resolve.Plain e){ check(e.getMessage().equals(Resolve.MEMBERS), "unplayable account response -> plain message"); }

    // 7. public videos unchanged: the public extractor's result is used, account path untouched
    reset(); signIn(); final StreamInfo pub=Resolve.toInfo(PlayerProto.parse(signed), "fAkEmEmB3r0", URL);
    check(Resolve.resolve(URL, () -> pub)==pub && StreamOrDetailsDataRequest.calls.isEmpty(), "public success -> no account call");

    // 8. session sources
    reset(); signIn(); AuthUtils.incognito=true;
    check(Account.headers()==null, "incognito -> treated as signed out");
    reset(); signIn(); Map<String,String> cur=new HashMap<>(); cur.put("Authorization","Bearer FAKE_PLAYER_HEADER"); cur.put("X-Goog-PageId","FAKEPAGE");
    app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.currentVideoRequestHeader=cur;
    Map<String,String> h=Account.headers(); check(h!=null && h.get("Authorization").equals("Bearer FAKE_PLAYER_HEADER") && h.get("X-Goog-PageId").equals("FAKEPAGE"), "current player headers (incl. brand-account PageId) preferred");
    h.put("Authorization","changed"); check(cur.get("Authorization").equals("Bearer FAKE_PLAYER_HEADER"), "headers are copied, app's map untouched");
    reset(); AuthUtils.authorization=""; app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.currentVideoRequestHeader=cur;
    check(Account.headers()==null, "stale player headers ignored once the app is signed out");

    System.out.println(pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1);
  }
  static String rows(List<String[]> l){ StringBuilder b=new StringBuilder(); for (String[] r: l) b.append(Arrays.toString(r)); return b.toString(); }
}
