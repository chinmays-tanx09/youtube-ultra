package paradox.dl;
import android.app.Activity; import android.content.*; import android.os.*; import android.util.Log;
/** Bundled MicroG (assets/microg.apk, stored) for sign-in. Called from RVX GmsCoreSupport.checkGmsCore when GmsCore is missing:
 *  replaces RVX's toast + browser + System.exit with our optional sheet, so the app keeps running signed out. */
public final class Microg {
  public static final String PKG="app.revanced.android.gms", ASSET="microg.apk", TAG="ParadoxMicroG";
  static volatile boolean offeredThisStart;
  public static boolean installed(Context c){ try { c.getPackageManager().getPackageInfo(PKG,0); return true; } catch (Throwable t){ return false; } }
  /** At most once per app start (process). Never blocks, never exits. */
  public static void missing(final Activity a){
    if (!Guard.check(a)) return; // anti-tamper: modified/re-signed copies stop here
    try { if (a==null || offeredThisStart) return; offeredThisStart=true;
      if (installed(a)){ Log.i(TAG,"present"); return; }
      Log.i(TAG,"missing: offering bundled MicroG");
      new Handler(Looper.getMainLooper()).postDelayed(() -> { try { if (a.isFinishing() || installed(a)) return;
          a.startActivity(new Intent(a, MicrogActivity.class)); a.overridePendingTransition(0,0); } catch (Throwable t){ Log.e(TAG,"open sheet",t); } }, 700);
    } catch (Throwable t){ Log.e(TAG,"missing",t); } }
}
