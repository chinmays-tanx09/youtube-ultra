package paradox.dl;
import android.app.Activity; import android.content.Intent; import android.os.Handler; import android.os.Looper; import android.util.Log;
import android.view.HapticFeedbackConstants; import android.view.MotionEvent; import android.view.View; import android.view.ViewConfiguration; import android.view.Window;
import java.lang.reflect.InvocationHandler; import java.lang.reflect.InvocationTargetException; import java.lang.reflect.Method; import java.lang.reflect.Proxy;
/** v10.14: Shorts long-press = Share. A thin Window.Callback wrapper watches touches only; every call is forwarded to the original unchanged.
 *  Tap = pause/play and double tap = like are YouTube's own Shorts gestures and are left alone. Never throws into the app. */
public final class UltraG {
  static final String TAG="YouTubeUltra", MASTER="ytultra_shorts_gestures", LONG="ytultra_shorts_longpress_share";
  static final long HOLD_MS=650;
  static final Handler h=new Handler(Looper.getMainLooper());
  static volatile Activity act; static float dx, dy; static boolean pending; static int slop=24;
  public static void install(final Activity a){ try { if (a==null) return; final Window w=a.getWindow(); if (w==null) return; final Window.Callback orig=w.getCallback(); if (orig==null || Proxy.isProxyClass(orig.getClass())) return;
      act=a; slop=Math.max(12, ViewConfiguration.get(a).getScaledTouchSlop());
      Object p=Proxy.newProxyInstance(Window.Callback.class.getClassLoader(), new Class[]{Window.Callback.class}, new InvocationHandler(){ public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
          if (args!=null && args.length==1 && args[0] instanceof MotionEvent && "dispatchTouchEvent".equals(m.getName())) { try { onTouch(a,(MotionEvent)args[0]); } catch (Throwable t){ Log.w(TAG,"touch",t); } }
          try { return m.invoke(orig, args); } catch (InvocationTargetException e){ throw e.getCause()!=null? e.getCause(): e; } } });
      w.setCallback((Window.Callback)p); Log.i(TAG,"shorts gestures on"); } catch (Throwable t){ Log.w(TAG,"gesture install",t); } }
  static final Runnable fire=new Runnable(){ public void run(){ pending=false; try { Activity a=act; if (a==null || !Ultra.shortsOpen()) return; String url=UltraGLogic.shareUrl(Ultra.videoId()); if (url==null) return;
      try { a.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch (Throwable t){}
      Intent s=new Intent(Intent.ACTION_SEND); s.setType("text/plain"); s.putExtra(Intent.EXTRA_TEXT, url); a.startActivity(Intent.createChooser(s, null)); Log.i(TAG,"long-press share"); } catch (Throwable t){ Log.w(TAG,"share",t); } } };
  static void cancel(){ if (pending) { h.removeCallbacks(fire); pending=false; } }
  static void onTouch(Activity a, MotionEvent e){ int act0=e.getActionMasked();
    if (act0==MotionEvent.ACTION_DOWN) { cancel(); if (e.getPointerCount()!=1) return;
      if (!Ultra.flag(a,MASTER,true) || !Ultra.flag(a,LONG,true) || !Ultra.shortsOpen()) return;
      View d=a.getWindow().getDecorView(); if (!UltraGLogic.inZone(e.getRawX(), e.getRawY(), d.getWidth(), d.getHeight())) return;
      dx=e.getRawX(); dy=e.getRawY(); pending=true; h.postDelayed(fire, HOLD_MS); }
    else if (act0==MotionEvent.ACTION_MOVE) { if (pending && UltraGLogic.moved(dx,dy,e.getRawX(),e.getRawY(),slop*2f)) cancel(); }
    else if (act0==MotionEvent.ACTION_UP || act0==MotionEvent.ACTION_CANCEL || act0==MotionEvent.ACTION_POINTER_DOWN) cancel(); }
}
