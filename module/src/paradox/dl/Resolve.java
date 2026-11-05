package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.services.youtube.ItagItem; import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import java.util.*; import java.util.concurrent.Callable; import java.util.regex.*;
/** Finds the streams for a video (normal videos and Shorts alike), trying routes in this order and moving on
 *  automatically whenever one gives nothing usable:
 *   1. the player answer the app already received for this video (what is playing on screen right now);
 *   2. with the account (signed in): our own player request with the app's session (Android VR, visionOS, iOS, Android);
 *   3. the app's own download request (its player client, visionOS, Android Studio, TV Simply, ...);
 *   4. without sign-in: our own player request (Android VR, visionOS);
 *   5. the public extractor (NewPipe) - before v10.10 this went first, and YouTube's "temporarily blocked
 *      anonymous access" from busy networks stopped every download there.
 *  A route only counts when YouTube says the video is playable, it has direct links, and one of those links
 *  actually answers (a 2 KB range request), so a route whose links YouTube refuses is skipped too.
 *  The result is a normal StreamInfo, so the quality sheet, Formats, the engine and URL refresh all work the
 *  same for every route. When nothing works, the error lists what each route said. */
public final class Resolve {
  public static final String MEMBERS="This video needs a channel membership on your account";
  public static final String MEMBERS_SIGNED_OUT=MEMBERS+". Sign in to YouTube Ultra with that account to download it.";
  /** An error whose message is meant to be shown as is. */
  public static class Plain extends java.io.IOException { public Plain(String m){ super(m); } }
  /** Nothing worked: the message lists each route and what YouTube said. */
  public static final class Failed extends Plain { public final List<String> tried;
    Failed(List<String> t){ super(message(t)); tried=Collections.unmodifiableList(new ArrayList<>(t)); } }
  static String message(List<String> tried){ StringBuilder b=new StringBuilder("YouTube didn't give a download link for this video. Tried:");
    for (String t: tried) b.append("\n\u2022 ").append(t);
    b.append("\n\nOpen the video, let it play for a moment, then tap Download again."); return b.toString(); }

  /** One way to get a player answer for a video id. */
  public interface Route { String key(); String label(); Got get(String id) throws Exception; }
  public static final class Got { public final PlayerProto.Player player; public final String userAgent; public final String note;
    public Got(PlayerProto.Player p, String ua){ player=p; userAgent=ua; note=null; }
    public Got(String note){ player=null; userAgent=null; this.note=note; } }
  /** Range-probes one stream URL; returns the HTTP status (or -1 when the network failed). Replaced in tests. */
  public interface Probe { int status(String url, String userAgent); }
  public static Probe probe=new Probe(){ public int status(String url, String ua){
    try { java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000);
      c.setRequestProperty("User-Agent", ua==null? Net.UA: ua); c.setRequestProperty("Range","bytes=0-2047");
      int code=c.getResponseCode(); try { java.io.InputStream in= code>=400? c.getErrorStream(): c.getInputStream(); if (in!=null) { byte[] b=new byte[4096]; while (in.read(b)>0) {} in.close(); } } catch (Exception ignore){}
      c.disconnect(); return code; } catch (Exception e){ return -1; } } };
  /** Title / channel for answers that carry no video details (the app's player answer has none). Replaced in tests. */
  public interface Meta { String[] titleAndAuthor(String id); }
  public static Meta meta=new Meta(){ public String[] titleAndAuthor(String id){
    try { java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL("https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D"+id).openConnection();
      c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setRequestProperty("User-Agent", Net.UA); if (c.getResponseCode()!=200) return null;
      java.io.InputStream in=c.getInputStream(); java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while ((n=in.read(b))>0) o.write(b,0,n); in.close();
      com.grack.nanojson.JsonObject j=com.grack.nanojson.JsonParser.object().from(o.toString("UTF-8"));
      return new String[]{ j.getString("title",""), j.getString("author_name","") }; } catch (Exception e){ return null; } } };
  /** Time for routes 1-4 together; the public extractor always gets its turn after. */
  static long BUDGET_MS=45000;

  public static StreamInfo info(String url) throws Exception { return info(url, null); }
  /** failedUrl: a stream link that just stopped working (refresh after HTTP 403): the route that gave it is skipped. */
  public static StreamInfo info(final String url, String failedUrl) throws Exception {
    Net.init(); String id=videoId(url); final String pub= id!=null? "https://www.youtube.com/watch?v="+id: url;
    Map<String,String> h=Account.headers();
    return resolve(url, id, routes(h), new Callable<StreamInfo>(){ public StreamInfo call() throws Exception { return StreamInfo.getInfo(ServiceList.YouTube, pub); } }, Net.sourceOf(failedUrl), h!=null); }

  /** The routes in the order they are tried. */
  public static List<Route> routes(final Map<String,String> account){
    List<Route> r=new ArrayList<>();
    r.add(new Route(){ public String key(){ return "player"; } public String label(){ return "The app's player"; }
      public Got get(String id){ Account.Result a=Account.playerData(id, 6000); if (a==null) return new Got("no copy of this video yet");
        PlayerProto.Player p=PlayerProto.parse(a.player); return p==null? new Got("unreadable answer"): new Got(p, null); } });
    if (account!=null) for (final Innertube.Client c: Innertube.SIGNED_IN) r.add(new Route(){ public String key(){ return c.key+"+account"; } public String label(){ return "Your account ("+c.label+")"; }
      public Got get(String id) throws Exception { return new Got(Innertube.player(c, id, account), c.userAgent); } });
    for (final Object c: Account.clientOrder(account!=null)) { final boolean acc= account!=null && Account.flag(c,"canLogin");
      r.add(new Route(){ public String key(){ return "app:"+Account.name(c); } public String label(){ return (acc? "Your account in the app (": "The app (")+Account.label(c)+")"; }
        public Got get(String id){ Account.Result a=Account.fetchWith(id, c, account!=null? account: Account.requestHeaders()); if (a==null) return new Got("no streams");
          PlayerProto.Player p=PlayerProto.parse(a.player); return p==null? new Got("unreadable answer"): new Got(p, a.userAgent); } }); }
    final String visitor=Account.visitor();
    for (final Innertube.Client c: Innertube.ANONYMOUS) r.add(new Route(){ public String key(){ return c.key; } public String label(){ return "No sign-in ("+c.label+")"; }
      public Got get(String id) throws Exception { Map<String,String> v=new HashMap<>(); if (visitor!=null) v.put("X-Goog-Visitor-Id", visitor);
        return new Got(Innertube.player(c, id, v.isEmpty()? null: noAuth(v)), c.userAgent); } });
    return r; }
  /** Visitor id only: never an Authorization header on the no-sign-in route. */
  static Map<String,String> noAuth(Map<String,String> v){ Map<String,String> m=new HashMap<>(v); m.remove("Authorization"); m.remove("X-Goog-PageId"); return m; }

  /** v10.8 entry point (tests): routes of the signed-in session, then the public extractor. */
  static StreamInfo resolve(String url, Callable<StreamInfo> publicPath) throws Exception {
    Map<String,String> h=Account.headers(); return resolve(url, videoId(url), routes(h), publicPath, null, h!=null); }

  static StreamInfo resolve(String url, String id, List<Route> routes, Callable<StreamInfo> publicPath, String skipKey, boolean signedIn) throws Exception {
    List<String> tried=new ArrayList<>(); boolean members=false; long end=System.currentTimeMillis()+BUDGET_MS;
    if (id!=null) for (Route r: routes) {
      if (r.key().equals(skipKey)) { tried.add(r.label()+": skipped, its links just stopped working"); continue; }
      if (System.currentTimeMillis()>end) { tried.add("(stopped trying the other routes: too slow)"); break; }
      String why;
      try { Got g=r.get(id);
        if (g==null || g.player==null) why= g!=null && g.note!=null? g.note: "no answer";
        else if (!g.player.playable()) { why=reason(g.player); if (membersOnly(g.player.reason)) members=true; }
        else { StreamInfo s=toInfo(g.player, id, url);
          if (s==null || !usable(s)) why= hasCipherOnly(g.player)? "only locked links": "no direct download links";
          else { String ua= g.userAgent!=null? g.userAgent: uaFromUrls(s);
            int code=probe(s, ua);
            if (code==401 || code==403 || code==404 || code==410) why="YouTube refused its links (HTTP "+code+")";
            else { if (fillMeta(g.player, id)) s=toInfo(g.player, id, url); register(s, ua, r.key()); Account.log("resolve: "+r.key()); return s; } } }
      } catch (Exception e){ why=shortErr(e); }
      tried.add(r.label()+": "+why); Account.log("resolve: "+r.key()+" -> "+why); }
    Exception first=null;
    try { StreamInfo i=publicPath.call(); if (usable(i)) return i; tried.add("Public: no downloadable formats"); }
    catch (Exception e){ first=e; if (membersOnly(e)) members=true; tried.add("Public: "+shortErr(e)); }
    if (members) throw new Plain(signedIn? MEMBERS: MEMBERS_SIGNED_OUT);
    if (id==null && first!=null) throw first;
    throw new Failed(tried); }

  static int probe(StreamInfo s, String ua){ String u=null;
    for (AudioStream a: s.getAudioStreams()) { u=a.getContent(); break; }
    if (u==null) for (VideoStream v: s.getVideoStreams()) { u=v.getContent(); break; }
    if (u==null) for (VideoStream v: s.getVideoOnlyStreams()) { u=v.getContent(); break; }
    return u==null? -1: probe.status(u, ua); }
  static void register(StreamInfo s, String ua, String key){
    for (VideoStream v: s.getVideoStreams()) Net.track(v.getContent(), ua, key);
    for (VideoStream v: s.getVideoOnlyStreams()) Net.track(v.getContent(), ua, key);
    for (AudioStream a: s.getAudioStreams()) Net.track(a.getContent(), ua, key); }
  /** The app's player answer has no title: ask oEmbed, else keep "YouTube video". */
  static boolean fillMeta(PlayerProto.Player p, String id){
    if (!p.title.isEmpty()) return false; try { String[] m=meta.titleAndAuthor(id); if (m==null) return false; boolean ch=false;
      if (m[0]!=null && !m[0].isEmpty()) { p.title=m[0]; ch=true; } if (m.length>1 && m[1]!=null && !m[1].isEmpty() && p.author.isEmpty()) { p.author=m[1]; ch=true; } return ch; } catch (Throwable t){ return false; } }
  static boolean hasCipherOnly(PlayerProto.Player p){ boolean cipher=false; for (PlayerProto.Fmt f: p.formats) { if (f.hasUrl()) return false; if (!f.signatureCipher.isEmpty()) cipher=true; } return cipher; }
  /** c= in a googlevideo link names the client (VISIONOS, ANDROID_VR, ...): its User-Agent goes with the download. */
  static String uaFromUrls(StreamInfo s){ String u=null; for (AudioStream a: s.getAudioStreams()) { u=a.getContent(); break; }
    if (u==null) for (VideoStream v: s.getVideoOnlyStreams()) { u=v.getContent(); break; }
    String c=query(u, "c"); return c==null? null: Account.userAgentFor(c); }
  static String query(String url, String k){ if (url==null) return null; Matcher m=Pattern.compile("[?&]"+Pattern.quote(k)+"=([^&#]*)").matcher(url); return m.find()? m.group(1): null; }

  static String reason(PlayerProto.Player p){ String r=p.reason==null? "": p.reason.trim();
    String l=r.toLowerCase(Locale.US);
    if (l.contains("not a bot")) return "YouTube asked to confirm it's not a bot";
    if (r.isEmpty()) { switch (p.status) { case PlayerProto.LOGIN_REQUIRED: return "YouTube asked to sign in"; case PlayerProto.UNPLAYABLE: return "not playable"; default: return "YouTube said no"; } }
    return clip(r); }
  static String shortErr(Throwable e){ String m=String.valueOf(e.getMessage()); String l=m.toLowerCase(Locale.US);
    if (l.contains("blocked anonymous")) return "YouTube blocked anonymous access from this network for now";
    if (l.contains("not a bot")) return "YouTube asked to confirm it's not a bot";
    if (e instanceof java.net.SocketTimeoutException) return "timed out"; if (e instanceof java.net.UnknownHostException) return "no internet";
    if (e.getMessage()==null) return e.getClass().getSimpleName(); return clip(m); }
  static String clip(String s){ s=s.replace('\n',' ').trim(); return s.length()>110? s.substring(0,107)+"\u2026": s; }

  static boolean membersOnly(String m){ if (m==null) return false; m=m.toLowerCase(Locale.US); return m.contains("members-only") || m.contains("members of the channel") || m.contains("join this channel") || m.contains("channel membership"); }
  static boolean membersOnly(Throwable e){ for (Throwable t=e; t!=null; t=t.getCause()) { if (t instanceof PaidContentException) return true; if (membersOnly(t.getMessage())) return true; } return false; }
  static boolean usable(StreamInfo i){ return i!=null && (!Formats.videoChoices(i).isEmpty() || !Formats.audioChoices(i).isEmpty()); }

  static final Pattern BARE_ID=Pattern.compile("^[A-Za-z0-9_-]{11}$");
  static final Pattern ID_IN_URL=Pattern.compile("(?:[?&#](?:v|vi|video_id)=|/shorts/|youtu\\.be/|/embed/|/live/|/v/|/e/|/watch/|vnd\\.youtube:(?://)?)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])");
  /** Video id from any YouTube link: watch?v=, youtu.be/, /shorts/, /live/, /embed/, music.youtube, m.youtube, a bare id. */
  public static String videoId(String url){
    if (url==null) return null; String s=url.trim(); if (BARE_ID.matcher(s).matches()) return s;
    Matcher m=ID_IN_URL.matcher(s); if (m.find()) return m.group(1);
    try { return YoutubeStreamLinkHandlerFactory.getInstance().getId(s); } catch (Throwable t){ return null; } }

  /** StreamInfo from an account fetch, or null when it is missing / not playable / has no direct streams (v10.8 API). */
  static StreamInfo signed(String id, String url, Account.Result r){
    if (r==null) return null; PlayerProto.Player p=PlayerProto.parse(r.player); if (p==null || !p.playable()) return null;
    StreamInfo s=toInfo(p, id, url); if (s==null || !usable(s)) return null;
    register(s, r.userAgent, "app"); return s; }

  /** A video-only adaptive stream the extractor's itag table lacks (e.g. 571/402 AV1 8K, 330-337 VP9 HDR, 694-702 AV1 HDR). */
  static ItagItem extraItag(PlayerProto.Fmt f){
    if (!f.adaptive || f.height<=0 || !f.mimeType.startsWith("video/")) return null;
    org.schabi.newpipe.extractor.MediaFormat mf= f.mimeType.startsWith("video/webm")? org.schabi.newpipe.extractor.MediaFormat.WEBM: f.mimeType.startsWith("video/mp4")? org.schabi.newpipe.extractor.MediaFormat.MPEG_4: null;
    if (mf==null) return null;
    try { return new ItagItem(f.itag, ItagItem.ItagType.VIDEO_ONLY, mf, f.qualityLabel.isEmpty()? f.height+"p": f.qualityLabel, f.fps>0? f.fps: 30); } catch (Throwable t){ return null; } }
  /** Same shape as the public extractor's output: progressive / video-only / audio lists of direct HTTP streams. */
  public static StreamInfo toInfo(PlayerProto.Player p, String id, String url){
    try {
      StreamInfo s=new StreamInfo(ServiceList.YouTube.getServiceId(), url, url, StreamType.VIDEO_STREAM, p.videoId.isEmpty()? id: p.videoId, p.title.isEmpty()? "YouTube video": p.title, 0);
      s.setUploaderName(p.author); if (!p.channelId.isEmpty()) s.setUploaderUrl("https://www.youtube.com/channel/"+p.channelId); s.setDuration(p.lengthSeconds);
      List<Image> th=new ArrayList<>(); for (PlayerProto.Thumb t: p.thumbs) th.add(new Image(t.url, t.height>0? t.height: Image.HEIGHT_UNKNOWN, t.width>0? t.width: Image.WIDTH_UNKNOWN, Image.ResolutionLevel.fromHeight(t.height)));
      if (th.isEmpty()) th.add(new Image("https://i.ytimg.com/vi/"+s.getId()+"/hqdefault.jpg", 360, 480, Image.ResolutionLevel.MEDIUM));
      s.setThumbnails(th);
      List<VideoStream> muxed=new ArrayList<>(), vonly=new ArrayList<>(); List<AudioStream> audio=new ArrayList<>();
      for (PlayerProto.Fmt f: p.formats) { if (!f.hasUrl()) continue;
        ItagItem it; if (ItagItem.isSupported(f.itag)) { try { it=new ItagItem(ItagItem.getItag(f.itag)); } catch (Exception e){ continue; } }
        else { it=extraItag(f); if (it==null) continue; }   // v10.11: 8K, HDR and newer AV1 itags the extractor's table doesn't know
        if (f.bitrate>0) it.setBitrate(f.bitrate); if (f.width>0) it.setWidth(f.width); if (f.height>0) it.setHeight(f.height);
        String codec=f.codec(); if (codec!=null) it.setCodec(codec); if (f.contentLength>0) it.setContentLength(f.contentLength); if (f.approxDurationMs>0) it.setApproxDurationMs(f.approxDurationMs);
        if (!f.qualityLabel.isEmpty()) it.setQuality(f.qualityLabel);
        if (it.itagType==ItagItem.ItagType.AUDIO) {
          if (f.sampleRate>0) it.setSampleRate((int)f.sampleRate); if (f.channels>0) it.setAudioChannels(f.channels);
          AudioStream.Builder ab=new AudioStream.Builder().setId(String.valueOf(f.itag)).setContent(f.url, true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(f.averageBitrate>0? f.averageBitrate/1000: it.getAverageBitrate()).setItagItem(it);
          // v10.12: keep which language track this is, so the ORIGINAL one can be picked (the same itag exists once per dubbed language)
          int rk=Formats.trackRank(null, f.audioTrackName, f.url); org.schabi.newpipe.extractor.stream.AudioTrackType tt=null;
          if (rk==0 || (rk==1 && f.audioIsDefault==1)) tt=org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL;
          else if (rk==2 || f.audioIsDefault==0) tt= (f.audioTrackName.toLowerCase(Locale.ROOT).contains("descriptive") || Formats.xtags(f.url).contains("descriptive"))? org.schabi.newpipe.extractor.stream.AudioTrackType.DESCRIPTIVE: org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED;
          if (!f.audioTrackId.isEmpty()) ab.setAudioTrackId(f.audioTrackId); if (!f.audioTrackName.isEmpty()) ab.setAudioTrackName(f.audioTrackName); if (tt!=null) ab.setAudioTrackType(tt);
          audio.add(ab.build());
        } else {
          if (f.fps>0) it.setFps(f.fps);
          String res=!f.qualityLabel.isEmpty()? f.qualityLabel: (f.height>0? f.height+"p": it.getResolutionString());
          VideoStream v=new VideoStream.Builder().setId(String.valueOf(f.itag)).setContent(f.url, true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(it.itagType==ItagItem.ItagType.VIDEO_ONLY).setResolution(res).setItagItem(it).build();
          if (it.itagType==ItagItem.ItagType.VIDEO_ONLY) vonly.add(v); else muxed.add(v);
        } }
      s.setVideoStreams(muxed); s.setVideoOnlyStreams(vonly); s.setAudioStreams(audio);
      return s;
    } catch (Exception e){ Account.log("account: could not read streams: "+e.getClass().getSimpleName()); return null; } }
}
