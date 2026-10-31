package paradox.dl;
import android.app.*; import android.content.*; import android.content.pm.PackageInstaller; import android.content.res.AssetFileDescriptor;
import android.graphics.*; import android.graphics.drawable.*; import android.net.Uri; import android.os.*; import android.provider.Settings; import android.util.Log;
import android.view.*; import android.widget.*; import java.io.*;
/** "MicroG needed to sign in" bottom sheet, same components as the download sheet. Install streams the stored asset into a PackageInstaller session. */
public class MicrogActivity extends Activity {
  static final String ACTION="app.paradox.youtube.MICROG_INSTALL_STATUS";
  Ui.Pal P; FrameLayout root; Ui.Sheet sheet; final Handler h=new Handler(Looper.getMainLooper());
  boolean waitingPermission, working, done; Ui.SheenBar bar; TextView pct, stateText; BroadcastReceiver rcv, added; int sessionId=-1;
  final Runnable poll=new Runnable(){ public void run(){ if (done || isFinishing()) return; if (Microg.installed(MicrogActivity.this)){ installed(); return; } if (working) h.postDelayed(this, 1500); } };
  int dp(float v){ return Ui.dp(this,v); }
  @Override protected void onCreate(Bundle b){ super.onCreate(b); overridePendingTransition(0,0);
    if (Microg.installed(this)){ finish(); return; }
    P=Ui.pal(this); getWindow().setBackgroundDrawable(new ColorDrawable(0));
    root=new FrameLayout(this); setContentView(root); Ui.edgeToEdge(this, false, !P.dark);
    sheet=new Ui.Sheet(this, P, root, () -> { finish(); overridePendingTransition(0,0); });
    sheet.shade.setOnClickListener(v -> { if (!working) later(); });
    rcv=new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){ onStatus(i); } };
    IntentFilter f=new IntentFilter(ACTION);
    if (Build.VERSION.SDK_INT>=33) registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(rcv, f);
    added=new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){ Uri d=i.getData(); if (d!=null && Microg.PKG.equals(d.getSchemeSpecificPart()) && !done){ Log.i(Microg.TAG,"package added"); installed(); } } };
    IntentFilter pf=new IntentFilter(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REPLACED); pf.addDataScheme("package");
    if (Build.VERSION.SDK_INT>=33) registerReceiver(added, pf, Context.RECEIVER_EXPORTED); else registerReceiver(added, pf);
    ask(null); sheet.show();
    // Android 11 restarts the app when "Install unknown apps" is granted: resume the install once, on the next open.
    SharedPreferences sp=getSharedPreferences("paradox_microg",0);
    if (sp.getBoolean("pending",false)){ sp.edit().putBoolean("pending",false).apply(); if (canInstall()) h.postDelayed(this::install, 600); } }
  @Override protected void onDestroy(){ super.onDestroy(); h.removeCallbacks(poll); try { if (rcv!=null) unregisterReceiver(rcv); } catch (Throwable t){} try { if (added!=null) unregisterReceiver(added); } catch (Throwable t){} }
  @Override public void onBackPressed(){ if (working) return; if (sheet!=null && sheet.isOpen()) later(); else super.onBackPressed(); }
  @Override protected void onResume(){ super.onResume(); if (sheet==null) return;
    if (!done && Microg.installed(this)){ installed(); return; }
    if (waitingPermission && canInstall()){ waitingPermission=false; getSharedPreferences("paradox_microg",0).edit().putBoolean("pending",false).apply(); Log.i(Microg.TAG,"install permission granted"); install(); } }
  void later(){ Log.i(Microg.TAG,"later"); sheet.dismiss(null); }

  // ------------------------------------------------------------ views (download-sheet components)
  View top(String title, String body){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(24),dp(4),dp(24),dp(8));
    FrameLayout tile=new FrameLayout(this); tile.setBackground(Ui.round(P.chip, dp(14), 0)); ImageView ic=new ImageView(this);
    try (InputStream in=getAssets().open("microg_icon.png")){ ic.setImageBitmap(BitmapFactory.decodeStream(in)); } catch (Throwable t){ ic.setImageDrawable(new Ui.Icon(Ui.I_DOWNLOAD,P.text)); }
    tile.addView(ic,new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER)); r.addView(tile,new LinearLayout.LayoutParams(dp(52),dp(52)));
    LinearLayout col=new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(16),0,0,0);
    col.addView(Ui.medium(this,title,20,P.text)); TextView s=Ui.text(this,body,14,P.sub,false); s.setPadding(0,dp(4),0,0); s.setLineSpacing(dp(2),1f); col.addView(s);
    r.addView(col,new LinearLayout.LayoutParams(0,-2,1f)); Ui.enter(r,0); return r; }
  TextView pill(String s, boolean primary){ TextView t=Ui.medium(this,s,15,primary? P.bg: P.text); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(44)); t.setPadding(dp(20),0,dp(20),0);
    GradientDrawable g=Ui.round(primary? P.text: P.chip, dp(22), 0); GradientDrawable m=Ui.round(0xFFFFFFFF, dp(22), 0);
    t.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(primary? (P.dark? 0x33000000: 0x33FFFFFF): P.ripple), g, m));
    t.setClickable(true); t.setFocusable(true); t.setStateListAnimator(Ui.pressAnim(0.96f)); return t; }
  View pills(String a, View.OnClickListener ca, String b, View.OnClickListener cb, int idx){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(dp(24),dp(12),dp(24),dp(4));
    if (b!=null){ TextView l=pill(b,false); l.setOnClickListener(cb); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f); lp.rightMargin=dp(12); r.addView(l,lp); }
    TextView p=pill(a,true); p.setOnClickListener(ca); r.addView(p,new LinearLayout.LayoutParams(0,-2,1f)); Ui.enter(r,idx); return r; }
  void base(){ View h0=new View(this); h0.setBackground(Ui.round(P.track, dp(2), 0)); LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(dp(36),dp(4)); hl.gravity=Gravity.CENTER_HORIZONTAL; hl.topMargin=dp(12); hl.bottomMargin=dp(12); sheet.body.removeAllViews(); sheet.body.addView(h0,hl); }
  void ask(final String note){ sheet.morph(() -> { base();
    sheet.body.addView(top("MicroG needed to sign in","Install MicroG (from the YouTube Ultra release page) to sign in. You can keep watching without it."));
    if (note!=null){ TextView n=Ui.text(this,note,13,P.sub,false); n.setPadding(dp(24),dp(4),dp(24),0); sheet.body.addView(n); }
    sheet.body.addView(pills("Download", v -> onInstall(), "Later", v -> later(), 1)); }); }
  void needPermission(){ sheet.morph(() -> { base();
    sheet.body.addView(top("Allow installs","Turn on \u201cAllow from this source\u201d for YouTube Ultra, then come back. MicroG installs right after."));
    sheet.body.addView(pills("Open settings", v -> openUnknownSources(), "Later", v -> later(), 1)); }); }
  void progress(String title, String line){ sheet.morph(() -> { base();
    sheet.body.addView(top(title,"MicroG RE 7.2.2 \u00b7 bundled with YouTube Ultra"));
    LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(24),dp(10),dp(24),dp(8));
    LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.HORIZONTAL); stateText=Ui.text(this,line,14,P.text,false); t.addView(stateText,new LinearLayout.LayoutParams(0,-2,1f));
    pct=Ui.text(this,"",14,P.sub,false); t.addView(pct); r.addView(t);
    bar=new Ui.SheenBar(this, P.text, P.track); LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,dp(4)); bl.topMargin=dp(10); r.addView(bar,bl);
    Ui.enter(r,1); sheet.body.addView(r); }); }
  void installed(){ if (done) return; h.removeCallbacks(poll); done=true; working=false; Log.i(Microg.TAG,"installed"); sheet.morph(() -> { base();
    sheet.body.addView(top("Installed \u2713","MicroG is ready. Sign in from your account icon."));
    sheet.body.addView(pills("Done", v -> sheet.dismiss(null), null, null, 1)); }); }

  // ------------------------------------------------------------ install
  boolean canInstall(){ return Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls(); }
  /** "Download" opens the official release page, where MicroG RE is attached (MicroG is no longer bundled in the APK). */
  void onInstall(){ Guard.getMicrog(this); }
  void openUnknownSources(){ waitingPermission=true; getSharedPreferences("paradox_microg",0).edit().putBoolean("pending",true).apply(); try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+getPackageName()))); }
    catch (Throwable t){ try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); } catch (Throwable t2){ Log.e(Microg.TAG,"settings",t2); } } }
  void install(){ if (working) return; working=true; progress("Installing MicroG","Preparing\u2026");
    new Thread(() -> { PackageInstaller.Session s=null; try {
      PackageInstaller pi=getPackageManager().getPackageInstaller();
      long len; InputStream in; try { AssetFileDescriptor fd=getAssets().openFd(Microg.ASSET); len=fd.getLength(); in=fd.createInputStream(); } catch (IOException e){ len=-1; in=getAssets().open(Microg.ASSET); }
      PackageInstaller.SessionParams sp=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL); sp.setAppPackageName(Microg.PKG); if (len>0) sp.setSize(len);
      sessionId=pi.createSession(sp); s=pi.openSession(sessionId);
      final long total=len; long copied=0; int lastPct=-1; byte[] buf=new byte[1<<20];
      try (OutputStream out=s.openWrite("microg.apk",0,len); InputStream src=in){ int r; while ((r=src.read(buf))>0){ out.write(buf,0,r); copied+=r;
          int p= total>0? (int)(copied*100/total): 0; if (p!=lastPct){ lastPct=p; final int fp=p; h.post(() -> setProg(fp,"Copying MicroG\u2026")); } } s.fsync(out); }
      h.post(() -> setProg(100,"Waiting for Android\u2026"));
      Intent cb=new Intent(ACTION).setPackage(getPackageName());
      int fl=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31? PendingIntent.FLAG_MUTABLE: 0);
      PendingIntent pend=PendingIntent.getBroadcast(this, 7301, cb, fl);
      Log.i(Microg.TAG,"commit session "+sessionId+" bytes="+copied); s.commit(pend.getIntentSender()); s.close(); s=null;
    } catch (Throwable t){ Log.e(Microg.TAG,"install failed",t); try { if (s!=null) s.abandon(); } catch (Throwable t2){} final String m=String.valueOf(t.getMessage()); h.post(() -> { working=false; ask("Couldn't install: "+m); }); } }).start(); }
  void setProg(int p, String line){ if (bar!=null) bar.setProgress(p, p<100 || line.startsWith("Waiting")); if (pct!=null) pct.setText(p+"%"); if (stateText!=null) stateText.setText(line); }
  void onStatus(Intent i){ int st=i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999); String msg=i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE); Log.i(Microg.TAG,"status "+st+" "+msg);
    if (st==PackageInstaller.STATUS_PENDING_USER_ACTION){ Intent confirm=(Intent)i.getParcelableExtra(Intent.EXTRA_INTENT);
      if (confirm!=null){ setProg(100,"Confirm in the Android prompt"); h.removeCallbacks(poll); h.postDelayed(poll, 1500); try { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(confirm); } catch (Throwable t){ Log.e(Microg.TAG,"confirm",t); working=false; ask("Couldn't open the install prompt."); } }
      return; }
    if (st==PackageInstaller.STATUS_SUCCESS || Microg.installed(this)){ installed(); return; }
    working=false;
    if (st==PackageInstaller.STATUS_FAILURE_ABORTED){ ask("Install cancelled. Tap Install to try again."); return; }
    if (st==PackageInstaller.STATUS_FAILURE_CONFLICT){ ask("Another MicroG build with a different signature is in the way. Uninstall it, then try again."); return; }
    ask("Couldn't install"+(msg!=null? ": "+msg: ".")); }
}
