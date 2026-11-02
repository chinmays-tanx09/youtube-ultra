package paradox.dl;
import java.lang.reflect.*; import java.util.*;
/** Download with my account: a bridge to the signed-in session and the stream data the patched YouTube app
 *  already holds.
 *
 *  The app's extension records the Authorization / Visitor / PageId headers of YouTube's own requests in
 *  memory (AuthUtils, SpoofVideoStreamsPatch), keeps the player answer of every video it plays
 *  (StreamOrDetailsDataRequest cache) and has a download-mode player request that uses those headers
 *  (StreamOrDetailsDataRequest.fetchDownloadStream, which also deciphers stream URLs). This class only calls
 *  into that code by reflection, in the same process. Nothing is copied to disk, logged or sent anywhere else:
 *  the headers go only to YouTube's own player endpoint, exactly as the app sends them when it plays a video.
 *
 *  YouTube's servers decide what the account may play. A members-only video comes back unplayable for an
 *  account without the membership, so nothing here gets around a paywall. */
public final class Account {
  static String AUTH="app.morphe.extension.shared.innertube.utils.AuthUtils";
  static String SPOOF="app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch";
  static String YT_SPOOF="app.morphe.extension.youtube.patches.spoof.SpoofVideoStreamsPatch";
  static String REQ="app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest";
  static String CLIENT="app.morphe.extension.shared.spoof.ClientType";
  static String JS="app.morphe.extension.shared.spoof.js.JavaScriptEngineSupport";
  /** The app's own clients tried for a download, after the player's own client. visionOS is the app's default
   *  player client (direct links, no PO token); TV Simply + Android Studio are the app's download clients. */
  static final String[] ORDER={"VISIONOS_1_02","VISIONOS_1_03","ANDROID_CREATOR","TV_SIMPLY","TV_DASH","ANDROID_VR_DASH","ANDROID_XR_DASH","ANDROID"};
  static final String AUTH_HEADER="Authorization";

  public static final class Result { public final byte[] player; public final String userAgent, client;
    Result(byte[] p, String ua, String c){ player=p; userAgent=ua; client=c; } }

  static Class<?> cls(String n) throws ClassNotFoundException { return Class.forName(n, true, Account.class.getClassLoader()); }
  static boolean hasAuth(Object m){ if (!(m instanceof Map)) return false; Object a=((Map<?,?>)m).get(AUTH_HEADER); return a instanceof String && !((String)a).isEmpty(); }
  @SuppressWarnings("unchecked") static Map<String,String> copy(Object m){ Map<String,String> out=new HashMap<>(); for (Map.Entry<?,?> e: ((Map<?,?>)m).entrySet()) if (e.getKey() instanceof String && e.getValue() instanceof String) out.put((String)e.getKey(), (String)e.getValue()); return out; }

  /** The signed-in request headers, or null when the app is signed out / incognito or the extension is absent. */
  public static Map<String,String> headers(){
    Class<?> a; try { a=cls(AUTH); if (Boolean.TRUE.equals(a.getMethod("isNotLoggedIn").invoke(null))) return null; } catch (Throwable t){ return null; }
    Map<String,String> out=null;
    try { Object cur=cls(SPOOF).getField("currentVideoRequestHeader").get(null); if (hasAuth(cur)) out=copy(cur); } catch (Throwable ignore){}
    if (out==null) try { Object m=a.getMethod("getRequestHeader").invoke(null); if (hasAuth(m)) out=copy(m); } catch (Throwable ignore){}
    if (out!=null && !out.containsKey("X-Goog-Visitor-Id")) { String v=visitor(); if (v!=null) out.put("X-Goog-Visitor-Id", v); }
    return out; }
  public static boolean signedIn(){ return headers()!=null; }
  /** The app's visitor id (also known when signed out), or null. */
  static String visitor(){ try { Object v=cls(AUTH).getMethod("getVisitorId").invoke(null); return v instanceof String && !((String)v).isEmpty()? (String)v: null; } catch (Throwable t){ return null; } }
  /** Headers for the app's download request: the account when signed in, else none (no-sign-in clients). */
  static Map<String,String> requestHeaders(){ Map<String,String> h=headers(); return h!=null? h: new HashMap<String,String>(); }

  static boolean flag(Object c, String f){ try { return Boolean.TRUE.equals(c.getClass().getField(f).get(c)); } catch (Throwable t){ return false; } }
  static String str(Object o, String field){ try { Object v=o.getClass().getField(field).get(o); return v instanceof String? (String)v: null; } catch (Throwable t){ return null; } }
  static String name(Object c){ return c instanceof Enum? ((Enum<?>)c).name(): String.valueOf(c); }
  static String label(Object c){ String f=str(c,"friendlyName"); return f!=null? f: name(c); }
  static Object[] values(Class<?> ct){ try { Object v=ct.getMethod("values").invoke(null); if (v instanceof Object[]) return (Object[])v; } catch (Throwable ignore){} Object[] e=ct.getEnumConstants(); return e!=null? e: new Object[0]; }
  static boolean jsEngine(){ try { return !Boolean.FALSE.equals(cls(JS).getMethod("supportsJavaScriptEngine").invoke(null)); } catch (Throwable t){ return true; } }

  /** The app's clients to try, best first: the player's own client, visionOS, then the app's download clients
   *  and the rest. Skipped: SABR-only clients (no direct links), JavaScript clients when the phone has no
   *  JavaScript engine, sign-in clients when signed out, non-player endpoints. */
  public static List<Object> clientOrder(boolean signedIn){
    List<Object> out=new ArrayList<>(); Class<?> ct; try { ct=cls(CLIENT); } catch (Throwable t){ return out; }
    List<Object> want=new ArrayList<>();
    try { Object p=cls(SPOOF).getMethod("getPreferredClient").invoke(null); if (p!=null) want.add(p); } catch (Throwable ignore){}
    for (String n: ORDER) { try { want.add(ct.getField(n).get(null)); } catch (Throwable ignore){} }
    try { Object l=cls(YT_SPOOF).getField("AVAILABLE_CLIENTS").get(null); if (l instanceof List) want.addAll((List<?>)l); } catch (Throwable ignore){}
    boolean js=jsEngine();
    for (Object c: want) { if (c==null || out.contains(c)) continue;
      if (flag(c,"requireSABR")) continue;
      try { if (!c.getClass().getField("usePlayerEndpoint").getBoolean(c)) continue; } catch (Throwable ignore){}
      if (flag(c,"requireJS") && !js) continue;
      if (flag(c,"canLogin") && flag(c,"requireLogin") && !signedIn) continue;
      out.add(c); }
    return out; }

  /** One download-mode player request through the app's own code. Null when the client gave nothing. */
  public static Result fetchWith(String videoId, Object client, Map<String,String> h){
    if (videoId==null || client==null) return null;
    try { Class<?> ct=cls(CLIENT); Method f=cls(REQ).getMethod("fetchDownloadStream", String.class, ct, Map.class);
      Object sd=f.invoke(null, videoId, client, h==null? new HashMap<String,String>(): h);
      byte[] b= sd==null? null: bytes(sd); if (b==null) { log("account: "+label(client)+" gave no stream"); return null; }
      log("account: streams from "+label(client)); return new Result(b, str(client,"userAgent"), label(client));
    } catch (Throwable t){ log("account: "+label(client)+" failed: "+t.getClass().getSimpleName()); return null; } }
  static byte[] bytes(Object sd){ try { Object b=sd.getClass().getMethod("streamingData").invoke(sd); return b instanceof byte[] && ((byte[])b).length>0? (byte[])b: null; } catch (Throwable t){ return null; } }

  /** v10.8 behaviour, kept for callers: the app's download clients in order, first answer wins. */
  public static Result fetch(String videoId, Map<String,String> h){
    if (videoId==null || h==null) return null;
    for (Object c: clientOrder(true)) { Result r=fetchWith(videoId, c, h); if (r!=null) return r; }
    return null; }

  /** The player answer the app already received for this video (it is kept for the last ~50 videos played),
   *  waiting up to waitMs if that request is still running. Null when the app has none. */
  public static Result playerData(String videoId, long waitMs){
    if (videoId==null) return null;
    try { Class<?> rq=cls(REQ); Object r=rq.getMethod("getStreamRequestForVideoId", String.class).invoke(null, videoId); if (r==null) return null;
      Method done=rq.getMethod("fetchIsDone"); long end=System.currentTimeMillis()+waitMs;
      while (!Boolean.TRUE.equals(done.invoke(r)) && System.currentTimeMillis()<end) Thread.sleep(150);
      if (!Boolean.TRUE.equals(done.invoke(r))) { log("player data: still loading"); return null; }
      Object sd=rq.getMethod("getStream").invoke(r); byte[] b= sd==null? null: bytes(sd); if (b==null) return null;
      return new Result(b, null, "player");
    } catch (Throwable t){ log("player data: "+t.getClass().getSimpleName()); return null; } }

  /** User-Agent of the app's client whose clientName is the stream URL's c= value (VISIONOS, ANDROID_VR, ...). */
  public static String userAgentFor(String clientName){
    if (clientName==null || clientName.isEmpty()) return null;
    try { Class<?> ct=cls(CLIENT); Object pref=null; try { pref=cls(SPOOF).getMethod("getPreferredClient").invoke(null); } catch (Throwable ignore){}
      if (pref!=null && clientName.equals(str(pref,"clientName"))) return str(pref,"userAgent");
      for (Object c: values(ct)) if (clientName.equals(str(c,"clientName"))) return str(c,"userAgent");
    } catch (Throwable ignore){}
    for (Innertube.Client c: new Innertube.Client[]{Innertube.ANDROID_VR, Innertube.VISIONOS, Innertube.IOS, Innertube.ANDROID}) if (c.name.equals(clientName)) return c.userAgent;
    return null; }

  /** Never pass headers, tokens or URLs here. */
  static void log(String m){ try { android.util.Log.i("ParadoxDL", m); } catch (Throwable ignore){} }
}
