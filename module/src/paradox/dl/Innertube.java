package paradox.dl;
import com.grack.nanojson.*;
import java.io.*; import java.net.*; import java.util.*;
/** Our own small innertube /player client (JSON), used when the app's player data and the app's own
 *  download requests give nothing usable. Two ways in:
 *   - with the account: the signed-in session the app already holds (Authorization / Visitor / PageId headers,
 *     see Account.headers), sent only to YouTube's own player endpoint, exactly like the app's requests;
 *   - without sign-in: the same clients as yt-dlp / NewPipe use that need neither a PO token nor JavaScript.
 *  Every answer is read into a PlayerProto.Player, so the rest of the download code is shared. */
public final class Innertube {
  public static final class Client {
    public final String key, label, name, version, make, model, os, osVersion, sdk, userAgent; public final int id;
    Client(String key, String label, int id, String name, String version, String make, String model, String os, String osVersion, String sdk, String ua){
      this.key=key; this.label=label; this.id=id; this.name=name; this.version=version; this.make=make; this.model=model; this.os=os; this.osVersion=osVersion; this.sdk=sdk; userAgent=ua; } }
  public static final Client ANDROID_VR=new Client("own:android_vr","Android VR",28,"ANDROID_VR","1.65.10","Oculus","Quest 3","Android","12L","32",
      "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip");
  public static final Client VISIONOS=new Client("own:visionos","visionOS",101,"VISIONOS","1.02","Apple","RealityDevice14,1","visionOS","26.6.1",null,
      "com.google.visionosyoutube/1.02 (RealityDevice14,1; U; CPU visionOS 26_6_1 like Mac OS X; en_US) gzip");
  public static final Client IOS=new Client("own:ios","iOS",5,"IOS","20.10.4","Apple","iPhone16,2","iPhone","18.3.2.22D82",null,
      "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)");
  public static final Client ANDROID=new Client("own:android","Android",3,"ANDROID","20.10.38",null,null,"Android","11","30",
      "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip");
  /** Order with the account (direct links first, no PO token / JavaScript needed). */
  public static final List<Client> SIGNED_IN=Collections.unmodifiableList(Arrays.asList(ANDROID_VR, VISIONOS, IOS, ANDROID));
  /** Order without sign-in. */
  public static final List<Client> ANONYMOUS=Collections.unmodifiableList(Arrays.asList(ANDROID_VR, VISIONOS));

  public static final String ENDPOINT="https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false";
  public static final class Reply { public final int code; public final String body; public Reply(int c, String b){ code=c; body=b; } }
  public interface Http { Reply post(String url, Map<String,String> headers, String body) throws IOException; }
  /** Replaced in tests. */
  public static Http http=new Http(){ public Reply post(String url, Map<String,String> headers, String body) throws IOException {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setUseCaches(false);
    c.setRequestMethod("POST"); c.setDoOutput(true); for (Map.Entry<String,String> e: headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
    byte[] d=body.getBytes("UTF-8"); c.setFixedLengthStreamingMode(d.length); OutputStream o=c.getOutputStream(); o.write(d); o.close();
    int code=c.getResponseCode(); InputStream in= code>=400? c.getErrorStream(): c.getInputStream(); String s="";
    if (in!=null){ ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n; while ((n=in.read(b))>0) bo.write(b,0,n); in.close(); s=bo.toString("UTF-8"); }
    c.disconnect(); return new Reply(code, s); } };

  /** The request body, the same shape the app's own player requests use. */
  public static String body(Client c, String videoId, String visitorData){
    JsonStringWriter w=JsonWriter.string().object().object("context").object("client")
      .value("clientName", c.name).value("clientVersion", c.version);
    if (c.make!=null) w.value("deviceMake", c.make); if (c.model!=null) w.value("deviceModel", c.model);
    w.value("osName", c.os).value("osVersion", c.osVersion); if (c.sdk!=null) w.value("androidSdkVersion", Integer.parseInt(c.sdk));
    w.value("hl","en").value("gl","US"); if (visitorData!=null && !visitorData.isEmpty()) w.value("visitorData", visitorData);
    w.end().object("user").value("lockedSafetyMode", false).end().end()
     .value("videoId", videoId).value("contentCheckOk", true).value("racyCheckOk", true);
    return w.end().done(); }

  /** Request headers. account==null: no sign-in (only the visitor id, when known). */
  public static Map<String,String> headers(Client c, Map<String,String> account, String visitor){
    Map<String,String> h=new LinkedHashMap<>(); h.put("Content-Type","application/json"); h.put("User-Agent", c.userAgent);
    h.put("X-YouTube-Client-Name", String.valueOf(c.id)); h.put("X-YouTube-Client-Version", c.version);
    if (visitor!=null && !visitor.isEmpty()) h.put("X-Goog-Visitor-Id", visitor);
    if (account!=null) { String a=account.get("Authorization"); if (a!=null && !a.isEmpty()) { h.put("Authorization", a); h.put("X-Goog-Api-Format-Version","2"); }
      String pg=account.get("X-Goog-PageId"); if (pg!=null && !pg.isEmpty()) h.put("X-Goog-PageId", pg); }
    return h; }

  /** One player request. Returns the parsed answer (maybe not playable, with YouTube's reason), never null. */
  public static PlayerProto.Player player(Client c, String videoId, Map<String,String> account) throws IOException {
    String visitor= account!=null? account.get("X-Goog-Visitor-Id"): null;
    Reply r=http.post(ENDPOINT, headers(c, account, visitor), body(c, videoId, visitor));
    PlayerProto.Player p=parse(r.body);
    if (p==null) { p=new PlayerProto.Player(); p.status=PlayerProto.ERROR; p.reason="HTTP "+r.code; }
    else if (r.code>=400 && p.reason.isEmpty()) { p.status=PlayerProto.ERROR; p.reason="HTTP "+r.code; }
    return p; }

  static int status(String s){ if (s==null) return -1; switch (s) { case "OK": return PlayerProto.OK; case "ERROR": return PlayerProto.ERROR; case "UNPLAYABLE": return PlayerProto.UNPLAYABLE; case "LOGIN_REQUIRED": return PlayerProto.LOGIN_REQUIRED; default: return 9; } }
  static long num(JsonObject o, String k){ Object v=o.get(k); if (v instanceof Number) return ((Number)v).longValue(); if (v instanceof String) { try { return Long.parseLong((String)v); } catch (NumberFormatException e){ return 0; } } return 0; }

  /** JSON player response -> the same structure PlayerProto reads from the app's protobuf answers. */
  public static PlayerProto.Player parse(String json){
    if (json==null || json.isEmpty()) return null;
    JsonObject j; try { j=JsonParser.object().from(json); } catch (Exception e){ return null; }
    PlayerProto.Player p=new PlayerProto.Player();
    JsonObject ps=j.getObject("playabilityStatus"); if (!ps.isEmpty()) { p.status=status(ps.getString("status")); p.reason=ps.getString("reason",""); }
    JsonObject err=j.getObject("error"); if (!err.isEmpty() && p.status<0) { p.status=PlayerProto.ERROR; p.reason=err.getString("message","error "+num(err,"code")); }
    JsonObject sd=j.getObject("streamingData");
    for (String list: new String[]{"formats","adaptiveFormats"}) for (Object o: sd.getArray(list)) { if (!(o instanceof JsonObject)) continue; JsonObject f=(JsonObject)o;
      PlayerProto.Fmt x=new PlayerProto.Fmt(); x.adaptive=list.equals("adaptiveFormats"); x.itag=(int)num(f,"itag"); x.url=f.getString("url",""); x.mimeType=f.getString("mimeType","");
      x.bitrate=(int)num(f,"bitrate"); x.width=(int)num(f,"width"); x.height=(int)num(f,"height"); x.contentLength=num(f,"contentLength"); x.fps=(int)num(f,"fps");
      x.qualityLabel=f.getString("qualityLabel",""); x.averageBitrate=(int)num(f,"averageBitrate"); x.approxDurationMs=num(f,"approxDurationMs");
      x.sampleRate=num(f,"audioSampleRate"); x.channels=(int)num(f,"audioChannels"); x.signatureCipher=f.getString("signatureCipher", f.getString("cipher",""));
      p.formats.add(x); }
    JsonObject vd=j.getObject("videoDetails");
    p.videoId=vd.getString("videoId",""); p.title=vd.getString("title",""); p.author=vd.getString("author",""); p.channelId=vd.getString("channelId",""); p.lengthSeconds=num(vd,"lengthSeconds");
    for (Object o: vd.getObject("thumbnail").getArray("thumbnails")) { if (!(o instanceof JsonObject)) continue; JsonObject t=(JsonObject)o;
      PlayerProto.Thumb th=new PlayerProto.Thumb(); th.url=t.getString("url",""); th.width=(int)num(t,"width"); th.height=(int)num(t,"height"); if (!th.url.isEmpty()) p.thumbs.add(th); }
    return p; }
}
