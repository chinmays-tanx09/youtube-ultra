package paradox.dl;
import android.app.Activity; import android.content.Context; import android.content.Intent; import android.os.*; import android.view.*; import android.widget.FrameLayout;
/** Called from patched YouTube code. */
public final class Hooks {
  static long last;
  /** PaneDescriptor.j(endpoint): legacy browse route (FEdownloads) opens our Downloads activity. */
  public static void onBrowse(String browseId){
    try { if (!"FEdownloads".equals(browseId)) return; long now=SystemClock.uptimeMillis(); if (now-last<2000) return; last=now;
      final Activity act=(Activity)Class.forName("app.morphe.extension.shared.utils.Utils").getMethod("getActivity").invoke(null);
      if (act==null) return;
      new Handler(Looper.getMainLooper()).postDelayed(() -> {
        try { if (!act.isFinishing()) act.onBackPressed(); } catch (Throwable t) { android.util.Log.w("ParadoxDL","back",t); }
        try { act.startActivity(new Intent(act, DownloadsActivity.class)); act.overridePendingTransition(0,0); } catch (Throwable t) { android.util.Log.e("ParadoxDL","open downloads",t); }
      }, 450);
    } catch (Throwable t) { android.util.Log.e("ParadoxDL","onBrowse",t); }
  }
  /** Host that keeps YouTube's own downloads view (and its "Recommended downloads") alive but invisible and untouchable, with our list on top. */
  static final class Host extends FrameLayout { View hidden; Host(Context c){ super(c); }
    @Override protected boolean drawChild(android.graphics.Canvas cv, View child, long t){ return child==hidden? false: super.drawChild(cv,child,t); } }
  /** downloads_page_fragment (kod.N, every route into YouTube's Downloads page): replace its content with our list. */
  public static View downloadsPage(View yt){
    try { if (yt==null) return null; Context c=yt.getContext(); Host host=new Host(c);
      host.hidden=yt; yt.setVisibility(View.GONE); yt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      host.addView(yt,new FrameLayout.LayoutParams(-1,-1));
      DownloadsPage p=new DownloadsPage(c,true); p.setClickable(true); host.addView(p,new FrameLayout.LayoutParams(-1,-1));
      android.util.Log.i("ParadoxDL","downloads page: ours");
      return host;
    } catch (Throwable t){ android.util.Log.e("ParadoxDL","downloadsPage",t); return yt; }
  }
}
