package app.morphe.extension.shared.innertube.utils;
import java.util.*;
/** Test stub with the same public surface the module calls on the app's extension. Fake values only. */
public class AuthUtils {
  public static String authorization="", visitorId="", pageId=""; public static boolean incognito;
  public static String getVisitorId(){ return visitorId; }
  public static boolean isNotLoggedIn(){ return authorization.isEmpty() || (pageId.isEmpty() && incognito); }
  public static Map<String,String> getRequestHeader(){ Map<String,String> m=new HashMap<>(); m.put("Authorization", authorization); m.put("X-Goog-Visitor-Id", visitorId); m.put("X-Goog-PageId", pageId); return m; }
}
