package paradox.dl;
import java.lang.reflect.*;
/** v10.13: the few calls into RVX's own extension classes (Shorts player state, current video id, video-ended event), by reflection so the module
 *  compiles without them and a changed extension can never crash the app. Pure java.lang.reflect: unit-tested on the JVM against stub classes
 *  that carry the signatures read from the extension's smali. */
public final class Probe {
  private static Method mCur, mOpen, mVid;
  /** ShortsPlayerState.getCurrent().isOpen() — false when unknown. */
  public static boolean shortsOpen(){ try { if (mCur==null) { Class<?> k=Class.forName("app.morphe.extension.youtube.shared.ShortsPlayerState"); mCur=k.getMethod("getCurrent"); mOpen=k.getMethod("isOpen"); } Object s=mCur.invoke(null); return s!=null && (Boolean)mOpen.invoke(s); } catch (Throwable t){ return false; } }
  /** VideoInformation.getVideoId() — null when unknown. */
  public static String videoId(){ try { if (mVid==null) mVid=Class.forName("app.morphe.extension.youtube.shared.VideoInformation").getMethod("getVideoId"); Object o=mVid.invoke(null); return o==null? null: o.toString(); } catch (Throwable t){ return null; } }
  /** Registers a Kotlin Function1 observer on VideoState.getOnChange() that runs onEnded when the new state is ENDED. Throws if the extension differs. */
  public static void observeVideoEnded(ClassLoader cl, final Runnable onEnded) throws Exception {
    Class<?> vs=Class.forName("app.morphe.extension.youtube.shared.VideoState"); Object ev=vs.getMethod("getOnChange").invoke(null);
    final Class<?> fn=Class.forName("kotlin.jvm.functions.Function1"); final Object unit=Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
    Object proxy=Proxy.newProxyInstance(cl, new Class<?>[]{fn}, new InvocationHandler(){ public Object invoke(Object p, Method m, Object[] args){
      try { if ("invoke".equals(m.getName()) && args!=null && args.length==1 && args[0] instanceof Enum && "ENDED".equals(((Enum<?>)args[0]).name())) onEnded.run(); } catch (Throwable t){}
      if (m.getDeclaringClass()==Object.class) { String n=m.getName(); if (n.equals("hashCode")) return System.identityHashCode(p); if (n.equals("equals")) return p==args[0]; if (n.equals("toString")) return "UltraEndObserver"; }
      return m.getReturnType()==void.class? null: unit; } });
    ev.getClass().getMethod("addObserver", fn).invoke(ev, proxy); }
}
