package app.morphe.extension.youtube.shared;
import java.util.*;
/** Test stub with the same shape as the extension's: enum + a static Event with addObserver(Function1). */
public enum VideoState { NEW, PLAYING, PAUSED, ENDED;
  public static class Event { final List<kotlin.jvm.functions.Function1<VideoState,kotlin.Unit>> obs=new ArrayList<>(); public final void addObserver(kotlin.jvm.functions.Function1 f){ obs.add(f); } public final void invoke(VideoState s){ for (kotlin.jvm.functions.Function1<VideoState,kotlin.Unit> f: obs) f.invoke(s); } }
  static final Event onChange=new Event(); public static Event getOnChange(){ return onChange; } }
