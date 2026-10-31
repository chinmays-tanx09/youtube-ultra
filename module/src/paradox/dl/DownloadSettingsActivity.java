package paradox.dl;
import android.app.*; import android.content.*; import android.os.*; import android.view.*; import android.widget.*; import android.graphics.*; import android.graphics.drawable.*;
/** Settings -> Downloads: "Video download quality" / "Audio download quality" (shares storage with "Remember my settings"). */
public class DownloadSettingsActivity extends Activity {
  Ui.Pal P; LinearLayout list; FrameLayout host; Ui.Sheet picker;
  static final String[][] VIDEO={{null,"Ask each time"},{"2160","2160p (4K)"},{"1440","1440p (QHD)"},{"1080","1080p (Full HD)"},{"720","720p (HD)"},{"480","480p"},{"360","360p"},{"240","240p"},{"144","144p"}};
  static final String[][] AUDIO={{null,"Ask each time"},{"rank:0","High"},{"rank:1","Medium"},{"rank:2","Low"}};
  static String label(Context c, String type){ String v=Prefs.get(c,type); for (String[] o: type.equals("audio")? AUDIO: VIDEO) if (v==null? o[0]==null: v.equals(o[0])) return o[1]; return v==null? "Ask each time": v; }
  int dp(float v){ return Ui.dp(this,v); }
  /** YouTube-style toolbar: 56dp tall below the status bar, 24dp back arrow in a 48dp round touch target, 20sp title. */
  static LinearLayout toolbar(final Activity a, Ui.Pal P, String title){ LinearLayout bar=new LinearLayout(a); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setBackgroundColor(P.bg);
    final int h=Ui.dp(a,56); bar.setPadding(Ui.dp(a,4),0,Ui.dp(a,4),0); bar.setMinimumHeight(h);
    FrameLayout back=new FrameLayout(a); GradientDrawable m=new GradientDrawable(); m.setShape(GradientDrawable.OVAL); m.setColor(0xFFFFFFFF);
    back.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(P.ripple), null, m)); back.setClickable(true); back.setContentDescription("Back");
    ImageView ic=new ImageView(a); ic.setImageDrawable(new Ui.Icon(Ui.I_BACK,P.text)); back.addView(ic,new FrameLayout.LayoutParams(Ui.dp(a,24),Ui.dp(a,24),Gravity.CENTER)); back.setOnClickListener(v -> a.onBackPressed());
    bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));
    TextView t=Ui.medium(a,title,20,P.text); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setPadding(Ui.dp(a,20),0,Ui.dp(a,8),0); bar.addView(t,new LinearLayout.LayoutParams(0,-2,1f));
    final View topPad=bar; Ui.onInsets(bar,(tp,b,l,r) -> { topPad.setPadding(Ui.dp(a,4)+l, tp, Ui.dp(a,4)+r, 0); topPad.setMinimumHeight(h+tp); });
    return bar; }
  @Override protected void onCreate(Bundle b){ P=Ui.pal(this); setTheme(P.dark? android.R.style.Theme_Material_NoActionBar: android.R.style.Theme_Material_Light_NoActionBar); super.onCreate(b);
    String which=getIntent().getStringExtra("which");
    host=new FrameLayout(this); host.setBackgroundColor(P.bg);
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); host.addView(root,new FrameLayout.LayoutParams(-1,-1));
    root.addView(toolbar(this,P,"Download quality"));
    list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); final ScrollView sv=new ScrollView(this); sv.setClipToPadding(false); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1f));
    Ui.onInsets(sv,(t,bt,l,r) -> sv.setPadding(l,0,r,bt));
    setContentView(host); Ui.themed(this);
    render(); if ("video".equals(which)||"audio".equals(which)) pick(which); }
  @Override public void onBackPressed(){ if (picker!=null && picker.isOpen()){ picker.dismiss(null); return; } super.onBackPressed(); }
  View item(String title, String sub){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setMinimumHeight(dp(72)); r.setPadding(dp(24),dp(12),dp(24),dp(12)); Ui.setRow(r,P); r.setClickable(true); r.setStateListAnimator(Ui.pressAnim(0.98f));
    r.addView(Ui.text(this,title,16,P.text,false)); if (sub!=null){ TextView s=Ui.text(this,sub,14,P.sub,false); s.setPadding(0,dp(2),0,0); r.addView(s); } return r; }
  void render(){ list.removeAllViews();
    TextView hdr=Ui.medium(this,"Quality",14,P.sub); hdr.setPadding(dp(24),dp(16),dp(24),dp(4)); list.addView(hdr);
    View v=item("Video download quality", label(this,"video")); v.setOnClickListener(x -> pick("video")); list.addView(v);
    View a=item("Audio download quality", label(this,"audio")); a.setOnClickListener(x -> pick("audio")); list.addView(a);
    TextView n=Ui.text(this,"Choosing a quality here (or turning on “Remember my settings” when downloading) skips the quality sheet. Video or Audio is always asked.",14,P.sub,false); n.setLineSpacing(dp(2),1f); n.setPadding(dp(24),dp(16),dp(24),dp(16)); list.addView(n); }
  void pick(final String type){ if (picker!=null && picker.isOpen()) return; final String[][] opts= type.equals("audio")? AUDIO: VIDEO; String cur=Prefs.get(this,type);
    picker=new Ui.Sheet(this,P,host,null); picker.header(type.equals("audio")?"Audio download quality":"Video download quality", "Used for every download. “Ask each time” shows the quality list.");
    LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int k=0; final java.util.List<View> checks=new java.util.ArrayList<>();
    for (final String[] o: opts){ boolean sel= cur==null? o[0]==null: cur.equals(o[0]); LinearLayout r=Ui.row(this,P,56);
      String[] lab=DownloadActivity.split(o[1]); TextView main=Ui.text(this,lab[0],16,P.text,false); if (sel) main.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); r.addView(main);
      r.addView(Ui.text(this,lab[1].isEmpty()? "": "  "+lab[1],14,P.sub,false),new LinearLayout.LayoutParams(0,-2,1f));
      final ImageView chk=Ui.icon(this,Ui.I_CHECK,P.text,24); chk.setVisibility(sel? View.VISIBLE: View.INVISIBLE); checks.add(chk); r.addView(chk);
      r.setOnClickListener(v -> { if (o[0]==null) Prefs.clear(this,type); else Prefs.putForever(this,type,o[0]); render();
        for (View c: checks) if (c!=chk) c.animate().alpha(0f).setDuration(120).start(); chk.setAlpha(1f); chk.setVisibility(View.VISIBLE); chk.setScaleX(0.4f); chk.setScaleY(0.4f); chk.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        v.postDelayed(() -> picker.dismiss(null), 260); });
      Ui.enter(r,k++); box.addView(r); }
    ScrollView sv=new ScrollView(this); sv.setOverScrollMode(View.OVER_SCROLL_NEVER); sv.setVerticalScrollBarEnabled(false); sv.addView(box); picker.body.addView(sv,new LinearLayout.LayoutParams(-1,-2,1f));
    picker.show(); }
  @Override protected void onResume(){ super.onResume(); if (Ui.dark(this)!=P.dark) recreate(); }
}
