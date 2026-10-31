package paradox.dl;
import android.app.Activity; import android.app.AlertDialog; import android.content.Context; import android.content.Intent;
import android.content.pm.PackageInfo; import android.content.pm.PackageManager; import android.content.pm.Signature; import android.content.pm.SigningInfo;
import android.net.Uri; import android.os.Build; import android.os.Handler; import android.os.Looper; import android.util.Log; import android.widget.Toast;
import java.security.MessageDigest;
/** Anti-tamper: the official build is signed with one certificate. A copy re-signed with any other key shows a notice and closes.
 *  Fails open: if the signature can't be read, nothing happens. */
public final class Guard {
  public static final String CERT_SHA256="0a78f9ca9c1ee7de0eeb59d7eef68251b0dee694b55028dd3dedd644a833ad62";
  public static final String OFFICIAL="https://github.com/chinmays-tanx09/youtube-ultra/releases/latest";
  static final String TAG="ParadoxGuard";
  static volatile int state; // 0 unchecked, 1 genuine/unknown, 2 modified
  static volatile boolean shown;
  /** true = carry on. false = modified copy (notice shown, app closing). */
  public static boolean check(Activity a){
    try { if (a==null) return true;
      if (state==0) state = verify(a)==2 ? 2 : 1;
      if (state!=2) return true;
      Log.w(TAG,"signature mismatch: modified copy");
      block(a); return false;
    } catch (Throwable t){ Log.e(TAG,"check",t); return true; } }
  /** 1 match, 2 mismatch, 0 unknown. */
  static int verify(Context c){
    try { PackageManager pm=c.getPackageManager(); Signature[] sigs;
      if (Build.VERSION.SDK_INT>=28){ PackageInfo pi=pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES); SigningInfo si=pi.signingInfo; if (si==null) return 0;
        sigs= si.hasMultipleSigners()? si.getApkContentsSigners(): si.getSigningCertificateHistory(); }
      else { PackageInfo pi=pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES); sigs=pi.signatures; }
      return match(sigs); } catch (Throwable t){ Log.e(TAG,"verify",t); return 0; } }
  static int match(Signature[] sigs) throws Exception {
    if (sigs==null || sigs.length==0) return 0; int seen=0;
    for (Signature s: sigs){ if (s==null) continue; byte[] der=s.toByteArray(); if (der==null || der.length==0) continue; seen++; if (CERT_SHA256.equals(hex(der))) return 1; }
    return seen==0? 0: 2; }
  public static String hex(byte[] der) throws Exception {
    byte[] d=MessageDigest.getInstance("SHA-256").digest(der); StringBuilder sb=new StringBuilder(64);
    for (byte b: d){ sb.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16)); } return sb.toString(); }
  static void block(final Activity a){
    if (shown) return; shown=true;
    final Handler h=new Handler(Looper.getMainLooper());
    h.postDelayed(() -> kill(a), 60000);
    try {
      new AlertDialog.Builder(a).setTitle("Modified copy")
        .setMessage("This copy of YouTube Ultra was modified and re-signed by someone else, so it won't run.\n\nDownload the official YouTube Ultra from:\ngithub.com/chinmays-tanx09/youtube-ultra")
        .setCancelable(false)
        .setPositiveButton("Download official", (d,w) -> { open(a); h.postDelayed(() -> kill(a), 400); })
        .setNegativeButton("Close", (d,w) -> kill(a)).show();
    } catch (Throwable t){ Log.e(TAG,"dialog",t);
      try { Toast.makeText(a.getApplicationContext(),"Modified copy. Download the official YouTube Ultra from github.com/chinmays-tanx09/youtube-ultra",Toast.LENGTH_LONG).show(); } catch (Throwable t2){}
      h.postDelayed(() -> kill(a), 3500); } }
  static void open(Activity a){ try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable t){ Log.e(TAG,"open",t); } }
  static void kill(Activity a){ try { a.finishAffinity(); } catch (Throwable t){} android.os.Process.killProcess(android.os.Process.myPid()); System.exit(0); }
  /** MicroG sheet "Download" button: open the official release page (MicroG APK is attached there). */
  public static void getMicrog(Activity a){
    try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    catch (Throwable t){ Log.e(TAG,"microg page",t); try { Toast.makeText(a,"Open github.com/chinmays-tanx09/youtube-ultra to get MicroG",Toast.LENGTH_LONG).show(); } catch (Throwable t2){} } }
}
