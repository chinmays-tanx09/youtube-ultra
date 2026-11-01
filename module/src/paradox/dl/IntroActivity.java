package paradox.dl;
import android.app.*; import android.content.*; import android.content.pm.*; import android.content.res.AssetFileDescriptor; import android.content.res.Configuration; import android.graphics.drawable.Icon;
import android.media.MediaPlayer; import android.os.*; import android.view.*; import java.util.*;
/** Launcher: on the first launch after a fresh install, and once after each update, plays the 3 s intro (portrait or landscape,
 *  fitted and centred on black), then opens the YouTube home screen. Every other launch goes straight to home. */
public class IntroActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {
  MediaPlayer mp; boolean gone; int resumeAt; final Handler h=new Handler(Looper.getMainLooper()); Fit fit;
  public static final String HOME="com.google.android.youtube.app.honeycomb.Shell$HomeActivity";
  static final String PREFS="paradox_intro", VC="paradox_intro_vc";
  @Override protected void onCreate(Bundle b){ super.onCreate(b); Ui.black(this);
    if (!Guard.check(this)) return; // anti-tamper: modified/re-signed copies stop here
    boolean replay= b!=null && b.getBoolean("playing") && !b.getBoolean("gone");   // recreated mid-intro (e.g. rotation without configChanges)
    if (!replay && !firstRun(this)){ next(); return; }
    if (b!=null && !replay){ next(); return; }
    resumeAt= b==null? 0: b.getInt("pos",0);
    markSeen(this);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    if (Build.VERSION.SDK_INT>=28) try { WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; getWindow().setAttributes(lp); } catch(Throwable t){}
    SurfaceView sv=new SurfaceView(this); sv.setOnClickListener(this); fit=new Fit(this); fit.setBackgroundColor(0xFF000000); fit.setOnClickListener(this);
    fit.addView(sv); setContentView(fit); sv.getHolder().addCallback(this);
    h.postDelayed(this::next, 4500); removeShortcuts(); }
  /** True on the first launch after a fresh install and on the first launch after each update (the versionCode last shown differs). */
  static boolean firstRun(Context c){ try { long shown=c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(VC,-1); return shouldPlay(shown, versionCode(c)); } catch (Throwable t){ return false; } }
  /** Pure rule: play when the intro was never shown for this exact versionCode. */
  static boolean shouldPlay(long shownVc, long currentVc){ return currentVc>0 && shownVc!=currentVc; }
  static long versionCode(Context c) throws Exception { PackageInfo pi=c.getPackageManager().getPackageInfo(c.getPackageName(), 0); return Build.VERSION.SDK_INT>=28? pi.getLongVersionCode(): pi.versionCode; }
  static void markSeen(Context c){ try { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(VC, versionCode(c)).apply(); } catch (Throwable t){} }
  @Override protected void onSaveInstanceState(Bundle o){ super.onSaveInstanceState(o); o.putBoolean("gone",gone); o.putBoolean("playing", mp!=null);
    try { if (mp!=null) o.putInt("pos", mp.getCurrentPosition()); } catch(Exception e){} }
  /** Rotation while the intro plays (configChanges in the manifest): just re-fit, the video keeps playing. */
  @Override public void onConfigurationChanged(Configuration c){ super.onConfigurationChanged(c); if (fit!=null) fit.requestLayout(); }
  void removeShortcuts(){ if (Build.VERSION.SDK_INT<25) return; try { getSystemService(ShortcutManager.class).removeAllDynamicShortcuts(); } catch(Throwable t){} }
  void shortcuts(){ if (Build.VERSION.SDK_INT<25) return; try { ShortcutManager sm=getSystemService(ShortcutManager.class);
    ShortcutInfo s=new ShortcutInfo.Builder(this,"paradox_downloads").setShortLabel("Downloads").setLongLabel("My downloads")
      .setIcon(Icon.createWithResource(this, android.R.drawable.stat_sys_download_done)).setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, DownloadsActivity.class)).build();
    sm.setDynamicShortcuts(Collections.singletonList(s)); } catch(Throwable t){} }
  @Override public void surfaceCreated(SurfaceHolder sh){ if (gone) return; try { if (mp!=null){ mp.setDisplay(sh); return; }
      mp=new MediaPlayer(); AssetFileDescriptor fd=getAssets().openFd("paradox_intro.mp4");
      mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength()); fd.close(); mp.setDisplay(sh);
      mp.setOnVideoSizeChangedListener((m,w,hh) -> { if (fit!=null && w>0 && hh>0) fit.setVideo(w,hh); });
      mp.setOnCompletionListener(m -> next()); mp.setOnErrorListener((m,a,c) -> { next(); return true; }); mp.prepare();
      if (mp.getVideoWidth()>0 && mp.getVideoHeight()>0) fit.setVideo(mp.getVideoWidth(), mp.getVideoHeight());
      if (resumeAt>0) mp.seekTo(resumeAt); mp.start(); } catch(Exception e){ next(); } }
  @Override public void surfaceChanged(SurfaceHolder s,int f,int w,int hh){} @Override public void surfaceDestroyed(SurfaceHolder s){}
  /** Tap the intro to skip it. */
  @Override public void onClick(View v){ next(); }
  void next(){ if (gone) return; gone=true; h.removeCallbacksAndMessages(null);
    try { if (mp!=null){ mp.release(); mp=null; } } catch(Exception e){}
    Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(this, HOME); Intent src=getIntent(); if (src!=null && src.getExtras()!=null) i.putExtras(src.getExtras());
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); finish(); }
  @Override protected void onDestroy(){ super.onDestroy(); try { if (mp!=null) mp.release(); mp=null; } catch(Exception e){} }
  /** Fits its one child inside itself at the video's aspect ratio, centred (letterbox/pillarbox, no crop, no stretch). */
  static final class Fit extends android.widget.FrameLayout { int vw=1080, vh=1920;
    Fit(Context c){ super(c); }
    void setVideo(int w, int hh){ if (w==vw && hh==vh) return; vw=w; vh=hh; requestLayout(); }
    @Override protected void onMeasure(int ws, int hs){ int W=MeasureSpec.getSize(ws), H=MeasureSpec.getSize(hs); setMeasuredDimension(W,H);
      int[] r=fitInside(W,H,vw,vh); for (int k=0;k<getChildCount();k++) getChildAt(k).measure(MeasureSpec.makeMeasureSpec(r[0],MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(r[1],MeasureSpec.EXACTLY)); }
    @Override protected void onLayout(boolean ch, int l, int t, int r, int b){ int W=r-l, H=b-t;
      for (int k=0;k<getChildCount();k++){ View v=getChildAt(k); int cw=v.getMeasuredWidth(), chh=v.getMeasuredHeight(); int x=(W-cw)/2, y=(H-chh)/2; v.layout(x,y,x+cw,y+chh); } } }
  /** {width, height} of a vw x vh video scaled to fit inside W x H. */
  static int[] fitInside(int W, int H, int vw, int vh){ if (W<=0 || H<=0 || vw<=0 || vh<=0) return new int[]{Math.max(0,W),Math.max(0,H)};
    if ((long)W*vh <= (long)H*vw) return new int[]{W, (int)((long)W*vh/vw)}; return new int[]{(int)((long)H*vw/vh), H}; }
}
