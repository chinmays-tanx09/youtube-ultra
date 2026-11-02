package paradox.dl;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest;
import app.morphe.extension.shared.spoof.js.JavaScriptEngineSupport;
import org.schabi.newpipe.extractor.stream.*;
import java.util.*;
/** v10.10: Shorts ids, route order + automatic fallback, the player's own data, link probing, refresh skip,
 *  our innertube client (JSON), and the full error text. No network: every outside call is stubbed. */
public class RoutesTest {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static final String SHORT="https://youtube.com/shorts/aBcDeFgHiJk?si=FAKEshare";
  static String url(int itag, String c){ return "https://rr2---sn-fake11.googlevideo.com/videoplayback?expire=1700000000&id=o-FAKE&itag="+itag+"&c="+c+"&clen=1000"; }
  /** A player answer like the app's player cache holds: streaming data only (no video details). */
  static byte[] playerOnly(String c){
    Pb sd=new Pb().m(3, new Pb().v(1,137).s(2,url(137,c)).s(5,"video/mp4; codecs=\"avc1.640028\"").v(6,4_000_000).v(7,1080).v(8,1920).v(12,9_000_000).v(25,30).s(26,"1080p"))
      .m(3, new Pb().v(1,140).s(2,url(140,c)).s(5,"audio/mp4; codecs=\"mp4a.40.2\"").v(6,130_000).v(12,500_000).v(31,129_000).v(45,44100).v(46,2));
    return new Pb().m(2, new Pb().v(1,0)).m(4, sd).bytes(); }
  static final String SHORT_JSON="{\"playabilityStatus\":{\"status\":\"OK\"},\"streamingData\":{\"adaptiveFormats\":["
    +"{\"itag\":137,\"url\":\""+url(137,"ANDROID_VR")+"\",\"mimeType\":\"video/mp4; codecs=\\\"avc1.640028\\\"\",\"bitrate\":4000000,\"width\":1080,\"height\":1920,\"contentLength\":\"9000000\",\"fps\":30,\"qualityLabel\":\"1080p\",\"approxDurationMs\":\"31000\"},"
    +"{\"itag\":140,\"url\":\""+url(140,"ANDROID_VR")+"\",\"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\",\"bitrate\":130000,\"contentLength\":\"500000\",\"averageBitrate\":129000,\"audioSampleRate\":\"44100\",\"audioChannels\":2},"
    +"{\"itag\":22,\"signatureCipher\":\"s=FAKE&url=https%3A%2F%2Ffake\",\"mimeType\":\"video/mp4\"}]},"
    +"\"videoDetails\":{\"videoId\":\"aBcDeFgHiJk\",\"title\":\"A fake Short \u2728\",\"lengthSeconds\":\"31\",\"channelId\":\"UCfake\",\"author\":\"Fake Shorts\",\"thumbnail\":{\"thumbnails\":[{\"url\":\"https://i.ytimg.com/vi/aBcDeFgHiJk/hq2.jpg\",\"width\":480,\"height\":360}]}}}";
  static final List<String> own=new ArrayList<>(), probes=new ArrayList<>();
  static String ownAnswer=AccountDownloadTest.BOT_JSON;
  static void reset(){ AccountDownloadTest.reset(); own.clear(); probes.clear(); ownAnswer=AccountDownloadTest.BOT_JSON;
    Innertube.http=(u,h,b) -> { own.add(h.get("X-YouTube-Client-Name")+"|"+h.get("Authorization")); return new Innertube.Reply(200, ownAnswer); };
    Resolve.probe=(u,ua) -> { probes.add(u); return 206; }; Resolve.meta=id -> new String[]{"Title from oEmbed", "Channel from oEmbed"}; }
  static void signIn(){ AuthUtils.authorization="Bearer FAKE_TOKEN_FOR_TESTS"; AuthUtils.visitorId="FAKEVISITOR"; AuthUtils.pageId=""; }
  static List<String> keys(List<Resolve.Route> rs){ List<String> k=new ArrayList<>(); for (Resolve.Route r: rs) k.add(r.key()); return k; }
  static java.util.concurrent.Callable<StreamInfo> publicFails(final String m){ return () -> { throw new org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException(m); }; }
  static final String BLOCKED="YouTube probably temporarily blocked anonymous watch access with this IP , got error : LOGIN_REQUIRED: Sign in to confirm you\u2019re not a bot";

  public static void main(String[] a) throws Exception {
    // 1. video ids: Shorts and every other link shape
    String[][] ids={{"https://youtube.com/shorts/aBcDeFgHiJk?si=x","aBcDeFgHiJk"},{"https://www.youtube.com/shorts/aBcDeFgHiJk","aBcDeFgHiJk"},{"https://m.youtube.com/shorts/aBcDeFgHiJk?feature=share","aBcDeFgHiJk"},
      {"youtube.com/shorts/aBc-_FgHiJk","aBc-_FgHiJk"},{"https://youtu.be/aBcDeFgHiJk?si=abc","aBcDeFgHiJk"},{"https://www.youtube.com/watch?v=aBcDeFgHiJk&t=3s","aBcDeFgHiJk"},
      {"https://www.youtube.com/watch?feature=share&v=aBcDeFgHiJk","aBcDeFgHiJk"},{"https://music.youtube.com/watch?v=aBcDeFgHiJk&list=RDx","aBcDeFgHiJk"},{"https://www.youtube.com/live/aBcDeFgHiJk?si=1","aBcDeFgHiJk"},
      {"https://www.youtube.com/embed/aBcDeFgHiJk","aBcDeFgHiJk"},{"vnd.youtube://aBcDeFgHiJk","aBcDeFgHiJk"},{"aBcDeFgHiJk","aBcDeFgHiJk"},{"https://example.com/nothing",null},{"https://youtube.com/shorts/tooShort",null}};
    for (String[] c: ids) { String got=Resolve.videoId(c[0]); check(Objects.equals(got, c[1]), "video id of "+c[0]+" = "+c[1]+", got "+got); }

    // 2. route order
    reset(); signIn();
    List<String> k=keys(Resolve.routes(Account.headers()));
    List<String> want=Arrays.asList("player","own:android_vr+account","own:visionos+account","own:ios+account","own:android+account",
      "app:VISIONOS_1_02","app:VISIONOS_1_03","app:ANDROID_CREATOR","app:TV_SIMPLY","app:TV_DASH","app:ANDROID_VR_DASH","app:ANDROID","own:android_vr","own:visionos");
    check(k.equals(want), "signed-in route order: "+k);
    check(!k.contains("app:ANDROID_VR_SABR") && !k.contains("app:GET_CHANNEL_FROM_ID"), "SABR-only and non-player clients never tried");
    app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.preferred=ClientType.TV_DASH; k=keys(Resolve.routes(Account.headers()));
    check(k.get(5).equals("app:TV_DASH"), "the player's own client goes first among the app's clients: "+k);
    app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch.preferred=ClientType.ANDROID_VR_SABR; k=keys(Resolve.routes(Account.headers()));
    check(k.get(5).equals("app:VISIONOS_1_02"), "a SABR player client is skipped (no direct links): "+k);
    reset(); signIn(); JavaScriptEngineSupport.supported=false; k=keys(Resolve.routes(Account.headers()));
    check(!k.contains("app:TV_SIMPLY") && !k.contains("app:TV_DASH"), "no JavaScript engine -> TV clients skipped: "+k);
    reset(); k=keys(Resolve.routes(Account.headers()));
    check(k.equals(Arrays.asList("player","app:VISIONOS_1_02","app:VISIONOS_1_03","app:TV_SIMPLY","app:TV_DASH","app:ANDROID_VR_DASH","own:android_vr","own:visionos")), "signed-out route order (no account routes): "+k);

    // 3. the Short that is on screen: the app's player data is used, nothing else is asked
    reset(); signIn(); StreamOrDetailsDataRequest.streamCache.put("aBcDeFgHiJk", new StreamOrDetailsDataRequest(playerOnly("VISIONOS"), true)); boolean[] pub={false};
    StreamInfo s=Resolve.resolve(SHORT, () -> { pub[0]=true; return null; });
    check(s!=null && Formats.videoChoices(s).size()==1 && Formats.audioChoices(s).size()==3, "Short resolved from the app's player data");
    check(own.isEmpty() && StreamOrDetailsDataRequest.calls.isEmpty() && !pub[0], "player data used -> no request to YouTube at all (own "+own+", app "+StreamOrDetailsDataRequest.calls+")");
    check(s.getName().equals("Title from oEmbed") && s.getUploaderName().equals("Channel from oEmbed"), "player data has no title -> oEmbed title/channel: "+s.getName());
    Formats.Pick pk=Formats.pick(s, "v:1080:mp4"); check(pk!=null && pk.videoUrl.contains("itag=137") && pk.audioUrl.contains("itag=140"), "Short 1080 MP4 pick");
    check(Net.ua(pk.videoUrl).equals(ClientType.VISIONOS_1_02.userAgent), "User-Agent from the link's client (c=VISIONOS): "+Net.ua(pk.videoUrl));
    check(probes.size()==1 && probes.get(0).contains("itag=140"), "one link probed before use");
    check("player".equals(Net.sourceOf(pk.videoUrl)), "link remembers its route");
    // still loading: not waited forever, falls through
    reset(); signIn(); StreamOrDetailsDataRequest.streamCache.put("aBcDeFgHiJk", new StreamOrDetailsDataRequest(playerOnly("VISIONOS"), false)); StreamOrDetailsDataRequest.responses.put(ClientType.VISIONOS_1_02, playerOnly("VISIONOS"));
    long t0=System.currentTimeMillis(); s=Resolve.resolve(SHORT, publicFails("x")); long dt=System.currentTimeMillis()-t0;
    check(s!=null && StreamOrDetailsDataRequest.calls.size()==1 && dt<9000, "player request still running -> waited "+dt+" ms, then next routes");

    // 4. automatic fallback: own signed-in request works (JSON client)
    reset(); signIn(); ownAnswer=SHORT_JSON; s=Resolve.resolve(SHORT, publicFails("x"));
    check(s!=null && own.size()==1 && own.get(0).equals("28|Bearer FAKE_TOKEN_FOR_TESTS") && StreamOrDetailsDataRequest.calls.isEmpty(), "player data missing -> own Android VR request with the account: "+own);
    check(s.getName().equals("A fake Short \u2728") && s.getUploaderName().equals("Fake Shorts") && s.getDuration()==31, "JSON video details carried");
    pk=Formats.pick(s, "v:1080:mp4"); check(pk!=null && Net.ua(pk.videoUrl).equals(Innertube.ANDROID_VR.userAgent), "engine uses Android VR User-Agent");

    // 5. a route whose links YouTube refuses (403) is skipped; refresh skips the route that just failed
    reset(); signIn(); StreamOrDetailsDataRequest.streamCache.put("aBcDeFgHiJk", new StreamOrDetailsDataRequest(playerOnly("TVHTML5"), true));
    StreamOrDetailsDataRequest.responses.put(ClientType.ANDROID_CREATOR, playerOnly("ANDROID_CREATOR"));
    Resolve.probe=(u,ua) -> { probes.add(u); return u.contains("c=TVHTML5&")? 403: 206; };
    s=Resolve.resolve(SHORT, publicFails("x"));
    check(s!=null && s.getAudioStreams().get(0).getContent().contains("c=ANDROID_CREATOR"), "403 on the player's links -> next route that works (Android Studio)");
    String good=s.getAudioStreams().get(0).getContent();
    check("app:ANDROID_CREATOR".equals(Net.sourceOf(good)), "route recorded for refresh");
    StreamOrDetailsDataRequest.responses.put(ClientType.TV_SIMPLY, playerOnly("TVHTML5_SIMPLY")); StreamOrDetailsDataRequest.calls.clear();
    s=Resolve.resolve(SHORT, Resolve.videoId(SHORT), Resolve.routes(Account.headers()), publicFails("x"), Net.sourceOf(good), true);
    check(s!=null && s.getAudioStreams().get(0).getContent().contains("c=TVHTML5_SIMPLY") && !StreamOrDetailsDataRequest.calls.toString().contains("ANDROID_CREATOR"), "refresh after 403 skips the failed route: "+StreamOrDetailsDataRequest.calls);

    // 6. nothing works: one clear, complete message with every route's reason
    reset(); signIn(); try { Resolve.resolve(SHORT, publicFails(BLOCKED)); check(false, "should fail"); }
    catch (Resolve.Failed e){ String m=e.getMessage();
      check(m.contains("The app's player: no copy of this video yet") && m.contains("Your account (Android VR): YouTube asked to confirm it's not a bot") && m.contains("The app (visionOS 1.02): no streams")
        && m.contains("Your account in the app (Android Studio): no streams") && m.contains("Public: YouTube blocked anonymous access from this network for now") && m.contains("let it play"), "full error lists every route:\n"+m);
      check(e.tried.size()==15 && !m.contains("anonym\u2026"), "15 routes reported, nothing cut off ("+e.tried.size()+")"); }
    reset(); signIn(); ownAnswer="{\"playabilityStatus\":{\"status\":\"LOGIN_REQUIRED\",\"reason\":\"Join this channel to get access to members-only content like this video, and other exclusive perks.\"}}";
    try { Resolve.resolve(SHORT, publicFails("x")); check(false,"members should fail"); } catch (Resolve.Plain e){ check(e.getMessage().equals(Resolve.MEMBERS), "members-only reason from a route -> membership message"); }
    // slow routes: the budget stops them, the public extractor still gets its turn
    reset(); signIn(); Resolve.BUDGET_MS=300; Innertube.http=(u,h,b) -> { try { Thread.sleep(200); } catch (InterruptedException ie){} return new Innertube.Reply(200, AccountDownloadTest.BOT_JSON); };
    final StreamInfo pubInfo=Resolve.toInfo(Innertube.parse(SHORT_JSON), "aBcDeFgHiJk", SHORT); t0=System.currentTimeMillis();
    s=Resolve.resolve(SHORT, () -> pubInfo); dt=System.currentTimeMillis()-t0; check(s==pubInfo && dt<2000, "time budget respected ("+dt+" ms), public extractor still tried");

    // 7. our innertube client
    Map<String,String> acc=new HashMap<>(); acc.put("Authorization","Bearer FAKE_TOKEN_FOR_TESTS"); acc.put("X-Goog-PageId","FAKEPAGE"); acc.put("X-Goog-Visitor-Id","FAKEVISITOR");
    Map<String,String> h=Innertube.headers(Innertube.ANDROID_VR, acc, "FAKEVISITOR");
    check("Bearer FAKE_TOKEN_FOR_TESTS".equals(h.get("Authorization")) && "FAKEPAGE".equals(h.get("X-Goog-PageId")) && "28".equals(h.get("X-YouTube-Client-Name")) && h.get("User-Agent").contains("vr.oculus"), "signed-in headers");
    h=Innertube.headers(Innertube.VISIONOS, null, "FAKEVISITOR"); check(!h.containsKey("Authorization") && !h.containsKey("X-Goog-PageId") && "FAKEVISITOR".equals(h.get("X-Goog-Visitor-Id")), "no-sign-in headers carry no account");
    check(!Resolve.noAuth(acc).containsKey("Authorization"), "no-sign-in route strips the account header");
    com.grack.nanojson.JsonObject body=com.grack.nanojson.JsonParser.object().from(Innertube.body(Innertube.ANDROID_VR, "aBcDeFgHiJk", "FAKEVISITOR"));
    com.grack.nanojson.JsonObject cl=body.getObject("context").getObject("client");
    check(body.getString("videoId").equals("aBcDeFgHiJk") && cl.getString("clientName").equals("ANDROID_VR") && cl.getInt("androidSdkVersion")==32 && cl.getString("visitorData").equals("FAKEVISITOR") && body.getBoolean("contentCheckOk"), "request body: "+body);
    PlayerProto.Player p=Innertube.parse(SHORT_JSON);
    check(p.playable() && p.formats.size()==3 && p.formats.get(0).itag==137 && p.formats.get(0).contentLength==9_000_000 && p.formats.get(1).sampleRate==44100 && !p.formats.get(2).hasUrl() && !p.formats.get(2).signatureCipher.isEmpty(), "JSON formats read");
    p=Innertube.parse(AccountDownloadTest.BOT_JSON); check(!p.playable() && p.status==PlayerProto.LOGIN_REQUIRED && Resolve.reason(p).equals("YouTube asked to confirm it's not a bot"), "bot check read as a reason");
    p=Innertube.parse("{\"error\":{\"code\":400,\"message\":\"Request contains an invalid argument.\"}}"); check(p!=null && !p.playable() && p.reason.contains("invalid argument"), "API error -> reason");
    check(Innertube.parse("not json")==null, "garbage -> null");
    Innertube.http=(u,hh,b) -> new Innertube.Reply(401, ""); p=Innertube.player(Innertube.ANDROID, "aBcDeFgHiJk", acc); check(!p.playable() && p.reason.equals("HTTP 401"), "HTTP error -> reason");
    check(Resolve.shortErr(new Exception(BLOCKED)).equals("YouTube blocked anonymous access from this network for now"), "NewPipe's cut-off error explained in full words");

    System.out.println("RoutesTest: "+pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1); }
}
