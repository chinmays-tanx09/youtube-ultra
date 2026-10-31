package paradox.dl;
import java.lang.reflect.*; import java.util.*;
/** Download with my account: a bridge to the signed-in session the patched YouTube app already holds.
 *
 *  The app's extension records the Authorization / Visitor / PageId headers of YouTube's own requests in
 *  memory (AuthUtils, SpoofVideoStreamsPatch) and has a download-mode player request that uses them
 *  (StreamOrDetailsDataRequest.fetchDownloadStream, which also deciphers stream URLs). This class only calls
 *  into that code by reflection, in the same process. Nothing is copied to disk, logged or sent anywhere else:
 *  the headers go only to YouTube's own player endpoint, exactly as the app sends them when it plays a video.
 *
 *  YouTube's servers decide what the account may play. A members-only video comes back unplayable for an
 *  account without the membership, so nothing here gets around a paywall. When signed out, headers() is null
 *  and downloads work exactly as before (public extractor only). */
public final class Account {
  static String AUTH="app.morphe.extension.shared.innertube.utils.AuthUtils";
  static String SPOOF="app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch";
  static String YT_SPOOF="app.morphe.extension.youtube.patches.spoof.SpoofVideoStreamsPatch";
  static String REQ="app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest";
  static String CLIENT="app.morphe.extension.shared.spoof.ClientType";
  static final String[] FALLBACK_CLIENTS={"ANDROID_CREATOR","TV_SIMPLY"};
  static final String AUTH_HEADER="Authorization";

  public static final class Result { public final byte[] player; public final String userAgent, client;
    Result(byte[] p, String ua, String c){ player=p; userAgent=ua; client=c; } }

  static Class<?> cls(String n) throws ClassNotFoundException { return Class.forName(n, true, Account.class.getClassLoader()); }
  static boolean hasAuth(Object m){ if (!(m instanceof Map)) return false; Object a=((Map<?,?>)m).get(AUTH_HEADER); return a instanceof String && !((String)a).isEmpty(); }
  @SuppressWarnings("unchecked") static Map<String,String> copy(Object m){ Map<String,String> out=new HashMap<>(); for (Map.Entry<?,?> e: ((Map<?,?>)m).entrySet()) if (e.getKey() instanceof String && e.getValue() instanceof String) out.put((String)e.getKey(), (String)e.getValue()); return out; }

  /** The signed-in request headers, or null when the app is signed out / incognito or the extension is absent. */
  public static Map<String,String> headers(){
    Class<?> a; try { a=cls(AUTH); if (Boolean.TRUE.equals(a.getMethod("isNotLoggedIn").invoke(null))) return null; } catch (Throwable t){ return null; }
    try { Object cur=cls(SPOOF).getField("currentVideoRequestHeader").get(null); if (hasAuth(cur)) return copy(cur); } catch (Throwable ignore){}
    try { Object m=a.getMethod("getRequestHeader").invoke(null); if (hasAuth(m)) return copy(m); } catch (Throwable ignore){}
    return null; }
  public static boolean signedIn(){ return headers()!=null; }

  /** The clients the app itself uses for downloads, in its order. */
  static List<Object> clients(Class<?> ct){ List<Object> out=new ArrayList<>();
    try { Object l=cls(YT_SPOOF).getField("AVAILABLE_CLIENTS").get(null); if (l instanceof List) for (Object o: (List<?>)l) if (o!=null && !out.contains(o)) out.add(o); } catch (Throwable ignore){}
    for (String n: FALLBACK_CLIENTS) { try { Object o=ct.getField(n).get(null); if (o!=null && !out.contains(o)) out.add(o); } catch (Throwable ignore){} }
    return out; }
  static String str(Object o, String field){ try { Object v=o.getClass().getField(field).get(o); return v instanceof String? (String)v: null; } catch (Throwable t){ return null; } }

  /** Player response (protobuf) for this video, fetched with the signed-in headers; null when no client could. */
  public static Result fetch(String videoId, Map<String,String> h){
    if (videoId==null || h==null) return null;
    try { Class<?> ct=cls(CLIENT); Method f=cls(REQ).getMethod("fetchDownloadStream", String.class, ct, Map.class);
      for (Object c: clients(ct)) { String name=str(c,"friendlyName"); if (name==null) name=String.valueOf(c);
        try { Object sd=f.invoke(null, videoId, c, h); if (sd==null) { log("account: "+name+" gave no stream"); continue; }
          Object b=sd.getClass().getMethod("streamingData").invoke(sd); if (!(b instanceof byte[]) || ((byte[])b).length==0) continue;
          log("account: streams from "+name); return new Result((byte[])b, str(c,"userAgent"), name);
        } catch (Throwable t){ log("account: "+name+" failed: "+t.getClass().getSimpleName()); } }
    } catch (Throwable t){ log("account: extension not available: "+t.getClass().getSimpleName()); }
    return null; }

  /** Never pass headers, tokens or URLs here. */
  static void log(String m){ try { android.util.Log.i("ParadoxDL", m); } catch (Throwable ignore){} }
}
