package paradox.dl;
import android.app.*; import android.content.*; import android.content.pm.*; import android.content.res.AssetFileDescriptor; import android.graphics.drawable.Icon;
import android.media.MediaPlayer; import android.os.*; import android.view.*; import java.util.*;
/** Launcher: plays the 2 s intro, then opens the YouTube home screen. */
public class IntroActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {
  MediaPlayer mp; boolean gone; final Handler h=new Handler(Looper.getMainLooper());
  public static final String HOME="com.google.android.youtube.app.honeycomb.Shell$HomeActivity";
  @Override protected void onCreate(Bundle b){ super.onCreate(b); Ui.black(this);
    if (!Guard.check(this)) return; // anti-tamper: modified/re-signed copies stop here
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    if (b!=null){ next(); return; }
    SurfaceView sv=new SurfaceView(this); sv.setOnClickListener(this); FrameLayoutHelper.set(this, sv); sv.getHolder().addCallback(this);
    h.postDelayed(this::next, 3500); removeShortcuts(); }
  void removeShortcuts(){ if (Build.VERSION.SDK_INT<25) return; try { getSystemService(ShortcutManager.class).removeAllDynamicShortcuts(); } catch(Throwable t){} }
  void shortcuts(){ if (Build.VERSION.SDK_INT<25) return; try { ShortcutManager sm=getSystemService(ShortcutManager.class);
    ShortcutInfo s=new ShortcutInfo.Builder(this,"paradox_downloads").setShortLabel("Downloads").setLongLabel("My downloads")
      .setIcon(Icon.createWithResource(this, android.R.drawable.stat_sys_download_done)).setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, DownloadsActivity.class)).build();
    sm.setDynamicShortcuts(Collections.singletonList(s)); } catch(Throwable t){} }
  @Override public void surfaceCreated(SurfaceHolder sh){ try { mp=new MediaPlayer(); AssetFileDescriptor fd=getAssets().openFd("paradox_intro.mp4");
      mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength()); fd.close(); mp.setDisplay(sh);
      mp.setOnCompletionListener(m -> next()); mp.setOnErrorListener((m,a,c) -> { next(); return true; }); mp.prepare(); mp.start(); } catch(Exception e){ next(); } }
  @Override public void surfaceChanged(SurfaceHolder s,int f,int w,int hh){} @Override public void surfaceDestroyed(SurfaceHolder s){}
  /** Tap the intro to skip it. */
  @Override public void onClick(View v){ next(); }
  void next(){ if (gone) return; gone=true; h.removeCallbacksAndMessages(null);
    try { if (mp!=null){ mp.release(); mp=null; } } catch(Exception e){}
    Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(this, HOME); Intent src=getIntent(); if (src!=null && src.getExtras()!=null) i.putExtras(src.getExtras());
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); finish(); }
  @Override protected void onDestroy(){ super.onDestroy(); try { if (mp!=null) mp.release(); } catch(Exception e){} }
}
class FrameLayoutHelper { static void set(Activity a, View v){ android.widget.FrameLayout f=new android.widget.FrameLayout(a); f.setBackgroundColor(0xFF000000);
  f.addView(v, new android.widget.FrameLayout.LayoutParams(-1, a.getResources().getDisplayMetrics().widthPixels*16/9, Gravity.CENTER)); a.setContentView(f); } }
