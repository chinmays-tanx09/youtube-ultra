package paradox.dl;
import android.app.Activity; import android.content.Intent; import android.os.Handler; import android.os.Looper; import android.util.Log;
import android.view.HapticFeedbackConstants; import android.view.MotionEvent; import android.view.View; import android.view.ViewConfiguration; import android.view.Window;
import java.lang.reflect.InvocationHandler; import java.lang.reflect.InvocationTargetException; import java.lang.reflect.Method; import java.lang.reflect.Proxy;
/** v10.14: Shorts long-press = Share. A thin Window.Callback wrapper watches touches only; every call is forwarded to the original unchanged.
 *  Tap = pause/play and double tap = like are YouTube's own Shorts gestures and are left alone. Never throws into the app. */
public final class UltraG {
  static final String TAG="YouTubeUltra", MASTER="ytultra_shorts_gestures", LONG="ytultra_shorts_longpress_share";
  static final long HOLD_MS=380; // below Android's 500 ms long-press, so our share fires before YouTube's own Shorts menu
  static volatile boolean swallow; static volatile Window.Callback origCb; static long downT;
  static final Handler h=new Handler(Looper.getMainLooper());
  static volatile Activity act; static float dx, dy; static boolean pending; static int slop=24;
  /** All the Shorts button-hide keys that the "Hide buttons" menu item flips together. */
  static final String[] HIDE_KEYS={"revanced_hide_shorts_save_button","revanced_hide_shorts_share_button","revanced_hide_shorts_remix_button","revanced_hide_shorts_sound_button","revanced_hide_shorts_subscribe_button","revanced_hide_shorts_channel_bar","revanced_hide_shorts_video_title","revanced_hide_shorts_like_button","revanced_hide_shorts_comments_button","revanced_hide_shorts_search_suggestions_button","revanced_hide_shorts_navigation_bar"};
  static final String[] SEED_KEYS={"revanced_hide_shorts_search_suggestions_button","revanced_hide_shorts_navigation_bar"};
  /** v10.16-19 switched these on by default; one of them is the suspect for the missing Auto scroll menu item, so v10.20 switches them back off. */
  static final String[] UNSEED_KEYS={"revanced_hide_shorts_video_link_label","revanced_hide_shorts_info_panel","revanced_hide_shorts_shopping_button","revanced_hide_shorts_tagged_products","revanced_hide_shorts_sound_metadata_label"};
  /** Set the given hide keys through RVX's own Setting objects (so the change applies right away), falling back to the prefs file. */
  static boolean setKeys(String[] keys, boolean on){ int n=0; try {
      Class<?> sc=Class.forName("app.morphe.extension.shared.settings.Setting");
      java.util.List<?> all=(java.util.List<?>)sc.getMethod("allLoadedSettings").invoke(null);
      java.lang.reflect.Field kf=sc.getField("key"); java.lang.reflect.Method sv=null;
      for (java.lang.reflect.Method m: sc.getMethods()) if (m.getName().equals("save") && m.getParameterTypes().length==1) sv=m;
      java.util.HashSet<String> want=new java.util.HashSet<String>(java.util.Arrays.asList(keys));
      for (Object o: all) { String k=(String)kf.get(o); if (want.contains(k)) { sv.invoke(o, Boolean.valueOf(on)); n++; } }
    } catch (Throwable t){ Log.w(TAG,"setKeys",t); }
    return n>0; }
  static boolean allHidden(){ try {
      Class<?> sc=Class.forName("app.morphe.extension.shared.settings.Setting");
      java.util.List<?> all=(java.util.List<?>)sc.getMethod("allLoadedSettings").invoke(null);
      java.lang.reflect.Field kf=sc.getField("key"); java.lang.reflect.Method gt=sc.getMethod("get");
      java.util.HashSet<String> want=new java.util.HashSet<String>(java.util.Arrays.asList(HIDE_KEYS)); int on=0, seen=0;
      for (Object o: all) { String k=(String)kf.get(o); if (want.contains(k)) { seen++; if (Boolean.TRUE.equals(gt.invoke(o))) on++; } }
      return seen>0 && on==seen;
    } catch (Throwable t){ return false; } }
  /** Menu label (the "Hide buttons" item in the Shorts hold menu). */
  public static String hideLabel(){ return allHidden() ? "Show buttons" : "Hide buttons"; }
  /** Menu action: hide all the Shorts buttons, or show them all again. */
  public static void toggleAll(){ boolean now=!allHidden(); setKeys(HIDE_KEYS, now);
    try { Activity a=act; if (a!=null) android.widget.Toast.makeText(a, now ? "Shorts buttons hidden. Scroll to the next Short to apply." : "Shorts buttons shown. Scroll to the next Short to apply.", android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable t){} }
  static void seed(Activity a){ try { android.content.SharedPreferences p=a.getSharedPreferences("revanced", android.content.Context.MODE_PRIVATE); if (p.getBoolean("ytultra_seed_v20", false)) return;
      setKeys(UNSEED_KEYS, false); setKeys(SEED_KEYS, true); p.edit().putBoolean("ytultra_seed_v20", true).apply(); } catch (Throwable t){ Log.w(TAG,"seed",t); } }

  public static void install(final Activity a){ try { if (a==null) return; seed(a); final Window w=a.getWindow(); if (w==null) return; final Window.Callback orig=w.getCallback(); if (orig==null || Proxy.isProxyClass(orig.getClass())) return;
      act=a; origCb=orig; slop=Math.max(12, ViewConfiguration.get(a).getScaledTouchSlop());
      Object p=Proxy.newProxyInstance(Window.Callback.class.getClassLoader(), new Class[]{Window.Callback.class}, new InvocationHandler(){ public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
          if (args!=null && args.length==1 && args[0] instanceof MotionEvent && "dispatchTouchEvent".equals(m.getName())) { MotionEvent me=(MotionEvent)args[0];
            if (swallow) { int ac=me.getActionMasked(); if (ac==MotionEvent.ACTION_UP || ac==MotionEvent.ACTION_CANCEL) swallow=false; return Boolean.TRUE; }
            try { onTouch(a,me); } catch (Throwable t){ Log.w(TAG,"touch",t); } }
          try { return m.invoke(orig, args); } catch (InvocationTargetException e){ throw e.getCause()!=null? e.getCause(): e; } } });
      w.setCallback((Window.Callback)p); Log.i(TAG,"shorts gestures on"); } catch (Throwable t){ Log.w(TAG,"gesture install",t); } }
  static final Runnable fire=new Runnable(){ public void run(){ pending=false; try { Activity a=act; if (a==null || !Ultra.shortsOpen()) return; String url=UltraGLogic.shareUrl(Ultra.videoId()); if (url==null) return;
      try { a.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch (Throwable t){}
      try { Window.Callback o=origCb; if (o!=null) { long now=android.os.SystemClock.uptimeMillis(); MotionEvent c=MotionEvent.obtain(downT,now,MotionEvent.ACTION_CANCEL,dx,dy,0); try { o.dispatchTouchEvent(c); } finally { c.recycle(); } swallow=true; } } catch (Throwable t){ Log.w(TAG,"cancel",t); }
      Intent s=new Intent(Intent.ACTION_SEND); s.setType("text/plain"); s.putExtra(Intent.EXTRA_TEXT, url); a.startActivity(Intent.createChooser(s, null)); Log.i(TAG,"long-press share"); } catch (Throwable t){ Log.w(TAG,"share",t); } } };
  static void cancel(){ if (pending) { h.removeCallbacks(fire); pending=false; } }
  static void onTouch(Activity a, MotionEvent e){ int act0=e.getActionMasked();
    if (act0==MotionEvent.ACTION_DOWN) { cancel(); if (e.getPointerCount()!=1) return;
      if (!Ultra.flag(a,MASTER,true) || !Ultra.flag(a,LONG,true) || !Ultra.shortsOpen()) return;
      View d=a.getWindow().getDecorView(); if (!UltraGLogic.inZone(e.getRawX(), e.getRawY(), d.getWidth(), d.getHeight())) return;
      dx=e.getRawX(); dy=e.getRawY(); downT=e.getDownTime(); pending=true; h.postDelayed(fire, HOLD_MS); }
    else if (act0==MotionEvent.ACTION_MOVE) { if (pending && UltraGLogic.moved(dx,dy,e.getRawX(),e.getRawY(),slop*2f)) cancel(); }
    else if (act0==MotionEvent.ACTION_UP || act0==MotionEvent.ACTION_CANCEL || act0==MotionEvent.ACTION_POINTER_DOWN) cancel(); }
}
