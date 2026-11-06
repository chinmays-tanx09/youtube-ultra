package paradox.dl;
import android.app.Activity; import android.content.*; import android.os.*; import android.preference.PreferenceFragment; import android.util.Log; import android.view.*;
import java.io.File;
/** v10.13 "YouTube Ultra" settings section + Shorts tracking. Everything here is reached from the patched app through Microg.missing (start),
 *  AbstractPreferenceFragment.initialize (which settings XML to load) and BaseActivityHook.createToolbar (the screen's title). */
public final class Ultra {
  static final String TAG="YouTubeUltra", SEEN_KEY="ytultra_skip_seen_shorts", AUTODL_KEY="ytultra_queue_autodownload", AUTOPLAY_KEY="ytultra_queue_autoplay", PREFS="revanced";
  static final SeenShorts seen=new SeenShorts(); static volatile Context app; static boolean started;
  static final Handler h=new Handler(Looper.getMainLooper());
  /** Which preference XML the extension's settings fragment inflates: ours when the screen was opened from Settings -> YouTube Ultra. */
  public static String prefsXml(PreferenceFragment f){ try { Activity a=f.getActivity(); if (a!=null && a.getIntent()!=null && a.getIntent().hasExtra("ytultra")) return "ytultra_prefs"; } catch (Throwable t){ Log.w(TAG,"prefsXml",t); } return "revanced_prefs"; }
  public static String toolbarTitle(Activity a){ try { if (a!=null && a.getIntent()!=null && a.getIntent().hasExtra("ytultra")) return "YouTube Ultra"; } catch (Throwable t){}
    try { return String.valueOf(Class.forName("app.morphe.extension.shared.patches.SettingsNamePatch").getMethod("getSettingsName").invoke(null)); } catch (Throwable t){ return "RVX"; } }
  // ---- settings storage: the extension's own preference file, so the Settings screen and this code agree ----
  public static boolean flag(Context c, String key, boolean def){ try { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, def); } catch (Throwable t){ return def; } }
  static File dir(Context c){ return c.getFilesDir(); }
  /** Called once per start from MainActivity.onCreate (through Microg.missing, after the anti-tamper check). Never throws. */
  public static synchronized void init(Activity a){ try { if (a==null) return; app=a.getApplicationContext(); if (started) return; started=true;
      File d=dir(app); seen.load(new File(d,"ytultra_seen_shorts.txt"), new File(d,"ytultra_seen_shorts.bak"));
      h.postDelayed(tick, 2000); observeVideoEnd(); Log.i(TAG,"init, seen="+seen.size()); } catch (Throwable t){ Log.e(TAG,"init",t); } }
  static void saveSeen(){ Context c=app; if (c==null) return; File d=dir(c); seen.save(new File(d,"ytultra_seen_shorts.txt"), new File(d,"ytultra_seen_shorts.bak"), new File(d,"ytultra_seen_shorts.tmp")); }
  public static void clearSeen(Context c){ Context x= app!=null? app: c; if (x==null) return; seen.clear(); File d=dir(x); seen.save(new File(d,"ytultra_seen_shorts.txt"), new File(d,"ytultra_seen_shorts.bak"), new File(d,"ytultra_seen_shorts.tmp")); }
  public static int seenCount(){ return seen.size(); }
  // ---- Shorts: seen tracking + skipping ----
  static boolean shortsOpen(){ return Probe.shortsOpen(); }
  static String videoId(){ return Probe.videoId(); }
  static final Runnable tick=new Runnable(){ public void run(){ long next=2500; try { Context c=app; if (c!=null && flag(c,SEEN_KEY,false)) { next=500; boolean open=shortsOpen();
          SeenShorts.Act act=seen.tick(open? videoId(): null, open, true, SystemClock.uptimeMillis());
          if (act==SeenShorts.Act.SKIP) { Log.i(TAG,"skip seen Short"); swipeUp(); }
          if (seen.isDirty()) saveSeen();
        } else seen.reset(); } catch (Throwable t){ Log.w(TAG,"tick",t); } h.postDelayed(this, next); } };
  static Activity activity(){ try { return (Activity)Class.forName("app.morphe.extension.shared.utils.Utils").getMethod("getActivity").invoke(null); } catch (Throwable t){ return null; } }
  /** A swipe from the lower to the upper part of the screen, as a finger would: YouTube's own Shorts pager then moves to the next Short. */
  static void swipeUp(){ final Activity a=activity(); if (a==null) return; final View v=a.getWindow().getDecorView(); final int w=v.getWidth(), hh=v.getHeight(); if (w<=0 || hh<=0) return;
    final float x=w*0.5f, y0=hh*0.74f, y1=hh*0.26f; final long t0=SystemClock.uptimeMillis(); final int steps=8;
    send(a, MotionEvent.obtain(t0,t0,MotionEvent.ACTION_DOWN,x,y0,0));
    for (int i=1;i<=steps;i++) { final int k=i; h.postDelayed(() -> { long t=SystemClock.uptimeMillis(); float y=y0+(y1-y0)*k/steps; send(a, MotionEvent.obtain(t0,t,k==steps? MotionEvent.ACTION_UP: MotionEvent.ACTION_MOVE,x,y,0)); }, 18L*i); } }
  static void send(Activity a, MotionEvent e){ try { a.dispatchTouchEvent(e); } catch (Throwable t){ Log.w(TAG,"touch",t); } finally { e.recycle(); } }
  // ---- queue: advance when the queued video ends ----
  static void observeVideoEnd(){ try { Probe.observeVideoEnded(Ultra.class.getClassLoader(), () -> onVideoEnded()); Log.i(TAG,"video-end observer on"); } catch (Throwable t){ Log.w(TAG,"no video-end observer (queue still works from the queue page)",t); } }
  static void onVideoEnded(){ final Context c=app; if (c==null || shortsOpen() || !QueueRun.onlineActive() || !flag(c,AUTOPLAY_KEY,true)) return;
    final String playing=videoId(); if (playing==null || !playing.equals(QueueRun.activeId())) return;
    h.postDelayed(() -> { try { QueueRun.advance(c, +1, true); } catch (Throwable t){ Log.w(TAG,"advance",t); } }, 900); }
}
