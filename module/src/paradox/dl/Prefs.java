package paradox.dl;
import android.content.*;
/** "Remember my settings for 30 days" storage. */
public final class Prefs {
  static final long DAYS30=30L*24*3600*1000;
  static SharedPreferences p(Context c){ return c.getSharedPreferences("ytpm_download", Context.MODE_PRIVATE); }
  public static String get(Context c, String key){ SharedPreferences s=p(c); long exp=s.getLong(key+"_exp",0); if (exp<System.currentTimeMillis()) return null; return s.getString(key,null); }
  public static void put(Context c, String key, String v){ p(c).edit().putString(key,v).putLong(key+"_exp", System.currentTimeMillis()+DAYS30).apply(); }
  public static void clear(Context c, String key){ p(c).edit().remove(key).remove(key+"_exp").apply(); }
  public static void putForever(Context c, String key, String v){ p(c).edit().putString(key,v).putLong(key+"_exp", Long.MAX_VALUE).apply(); }
  public static void reset(Context c){ p(c).edit().clear().apply(); }
  public static boolean any(Context c){ return get(c,"type")!=null || get(c,"video")!=null || get(c,"audio")!=null; }
}
