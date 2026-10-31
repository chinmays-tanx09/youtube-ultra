package paradox.dl;
import android.app.*; import android.content.*; import android.os.*; import android.view.*; import android.widget.*;
/** Downloads page styled after YouTube's You -> Downloads (standalone: notification, legacy route). */
public class DownloadsActivity extends Activity {
  Ui.Pal P; DownloadsPage page;
  @Override protected void onCreate(Bundle b){ P=Ui.pal(this); setTheme(P.dark? android.R.style.Theme_Material_NoActionBar: android.R.style.Theme_Material_Light_NoActionBar); super.onCreate(b); int TXT=P.text;
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(P.bg);
    LinearLayout bar=DownloadSettingsActivity.toolbar(this,P,"Downloads");
    FrameLayout more=new FrameLayout(this); android.graphics.drawable.GradientDrawable m=new android.graphics.drawable.GradientDrawable(); m.setShape(android.graphics.drawable.GradientDrawable.OVAL); m.setColor(0xFFFFFFFF);
    more.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(P.ripple), null, m)); more.setClickable(true); more.setContentDescription("More");
    ImageView mi0=new ImageView(this); mi0.setImageDrawable(new Ui.Icon(Ui.I_MORE,TXT)); more.addView(mi0,new FrameLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24),Gravity.CENTER));
    more.setOnClickListener(v -> { PopupMenu pm=new PopupMenu(this, v); pm.getMenu().add(0,1,0,"Download quality settings");
      pm.setOnMenuItemClickListener(mi -> { startActivity(new Intent(this, DownloadSettingsActivity.class)); return true; }); pm.show(); });
    bar.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48))); root.addView(bar);
    page=new DownloadsPage(this,false); page.padInsets();
    root.addView(page,new LinearLayout.LayoutParams(-1,0,1f)); setContentView(root); Ui.themed(this); }
  @Override protected void onResume(){ super.onResume(); if (Ui.dark(this)!=P.dark){ recreate(); return; } page.render(); }
}
