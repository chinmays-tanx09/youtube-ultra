package paradox.dl;
import android.animation.*; import android.app.Activity; import android.content.Context; import android.graphics.*; import android.graphics.drawable.*;
import android.view.*; import android.view.animation.*; import android.widget.*; import android.os.Build; import android.os.SystemClock;
public final class Ui {
  /** YouTube palette following the system light/dark setting. */
  public static final class Pal { public final boolean dark; public final int bg, sheet, text, sub, ripple, track, chip, line, shimmerBase, shimmerHi, scrim;
    Pal(boolean d){ dark=d; bg=d?0xFF0F0F0F:0xFFFFFFFF; sheet=d?0xFF212121:0xFFFFFFFF; text=d?0xFFF1F1F1:0xFF0F0F0F; sub=d?0xFFAAAAAA:0xFF606060; ripple=d?0x33FFFFFF:0x1F000000; track=d?0x4DFFFFFF:0x33000000; chip=d?0x1FFFFFFF:0x0D000000;
      line=d?0x1AFFFFFF:0x1A000000; shimmerBase=d?0x24FFFFFF:0x12000000; shimmerHi=d?0x2EFFFFFF:0xB3FFFFFF; scrim=0x99000000; } }
  /** Decided fresh on every show, never cached: YouTube's own theme as RVX tracks it (explicit Light/Dark, or the device theme it follows), else the phone's system setting.
   *  Deliberately NOT the Context's configuration: inside YouTube's process that can carry YouTube's/AppCompat's override and flip between opens. */
  public static Pal pal(Context c){ return new Pal(dark(c)); }
  public static boolean dark(Context c){ Boolean yt=ytDark(); boolean sys=systemDark(c); boolean d= yt!=null? yt: sys;
    String why="theme: youtube="+yt+" system="+sys+" -> "+(d?"dark":"light"); if (!why.equals(lastTheme)){ lastTheme=why; android.util.Log.i("ParadoxDL",why); } return d; }
  static volatile String lastTheme;
  static Boolean ytDark(){ try { java.lang.reflect.Field f=Class.forName("app.morphe.extension.shared.utils.BaseThemeUtils").getDeclaredField("isDarkModeEnabled"); f.setAccessible(true);
      Object v=f.get(null); return v instanceof Boolean? (Boolean)v: null; } catch (Throwable t) { return null; } }
  static boolean systemDark(Context c){ int NM=android.content.res.Configuration.UI_MODE_NIGHT_MASK, YES=android.content.res.Configuration.UI_MODE_NIGHT_YES;
    try { android.app.UiModeManager um=(android.app.UiModeManager)c.getApplicationContext().getSystemService(Context.UI_MODE_SERVICE);
      if (um!=null){ int m=um.getNightMode(); if (m==android.app.UiModeManager.MODE_NIGHT_YES) return true; if (m==android.app.UiModeManager.MODE_NIGHT_NO) return (android.content.res.Resources.getSystem().getConfiguration().uiMode & NM)==YES; } } catch (Throwable t) {}
    return (android.content.res.Resources.getSystem().getConfiguration().uiMode & NM)==YES; }
  public static int dp(Context c, float v){ return Math.round(v*c.getResources().getDisplayMetrics().density); }
  static final int SDK=Build.VERSION.SDK_INT;

  // ---------------------------------------------------------------- edge-to-edge + insets
  /** Draw behind the status and navigation bars on every Android version (Android 15 forces this anyway); views pad themselves with onInsets. */
  public static void edgeToEdge(Activity a, boolean lightStatusIcons, boolean lightNavIcons){ Window w=a.getWindow();
    w.setStatusBarColor(0); w.setNavigationBarColor(0);
    if (SDK>=29){ w.setStatusBarContrastEnforced(false); w.setNavigationBarContrastEnforced(false); }
    if (SDK>=28){ WindowManager.LayoutParams lp=w.getAttributes(); lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; w.setAttributes(lp); }
    w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION); w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    int f=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    if (lightStatusIcons) f|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if (lightNavIcons && SDK>=26) f|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    w.getDecorView().setSystemUiVisibility(f);
    if (SDK>=30){ w.setDecorFitsSystemWindows(false); try { WindowInsetsController c=w.getInsetsController(); if (c!=null){ int L=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, N=WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        c.setSystemBarsAppearance((lightStatusIcons?L:0)|(lightNavIcons?N:0), L|N); } } catch (Throwable t){} } }
  public interface InsetFn { void apply(int top, int bottom, int left, int right); }
  /** Calls fn with the status-bar/cutout top inset and the nav-bar/gesture-area bottom inset whenever they change. */
  public static void onInsets(final View v, final InsetFn fn){
    v.setOnApplyWindowInsetsListener((view, ins) -> { int t, b, l, r;
      if (SDK>=30){ android.graphics.Insets s=ins.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()); android.graphics.Insets g=ins.getInsets(WindowInsets.Type.mandatorySystemGestures());
        t=s.top; b=Math.max(s.bottom, g.bottom); l=s.left; r=s.right; }
      else { t=ins.getSystemWindowInsetTop(); b=ins.getSystemWindowInsetBottom(); l=ins.getSystemWindowInsetLeft(); r=ins.getSystemWindowInsetRight(); }
      fn.apply(t,b,l,r); return ins; });
    if (v.isAttachedToWindow()) v.requestApplyInsets();
    else v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){ public void onViewAttachedToWindow(View x){ x.requestApplyInsets(); } public void onViewDetachedFromWindow(View x){} }); }

  /** Old entry point: page activities now go edge-to-edge and pad their own bars. */
  public static void themed(Activity a){ Pal p=pal(a); a.getWindow().setBackgroundDrawable(new ColorDrawable(p.bg)); edgeToEdge(a, !p.dark, !p.dark); }

  // ---------------------------------------------------------------- motion
  /** Damped spring (≈2% overshoot) as an interpolator: Magic-UI style slide-up. */
  public static final class Spring implements TimeInterpolator { final double z, w; public Spring(double damping, double stiff){ z=damping; w=stiff; }
    public float getInterpolation(float t){ if (t>=1f) return 1f; double x=t*w, wd=Math.sqrt(1-z*z); return (float)(1-Math.exp(-z*x)*(Math.cos(wd*x*1.0)+z/wd*Math.sin(wd*x))); } }
  public static TimeInterpolator spring(){ return new Spring(0.80, 8.5); }
  /** Subtle scale on press for tappable rows. */
  public static StateListAnimator pressAnim(float scale){ StateListAnimator s=new StateListAnimator();
    ObjectAnimator down=ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("scaleX",scale), PropertyValuesHolder.ofFloat("scaleY",scale)); down.setDuration(110); down.setInterpolator(new DecelerateInterpolator());
    ObjectAnimator up=ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("scaleX",1f), PropertyValuesHolder.ofFloat("scaleY",1f)); up.setDuration(260); up.setInterpolator(new OvershootInterpolator(2.2f));
    s.addState(new int[]{android.R.attr.state_pressed}, down); s.addState(new int[0], up); return s; }
  /** Blur-fade style entrance: fade in and rise a little, staggered. */
  public static void enter(View v, int index){ v.setAlpha(0f); v.setTranslationY(dp(v.getContext(),14)); v.animate().alpha(1f).translationY(0).setStartDelay(40+index*32L).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start(); }

  // ---------------------------------------------------------------- rows
  /** Full-width rounded ripple for tappable rows. */
  public static Drawable rowBg(Context c, Pal p){ GradientDrawable mask=new GradientDrawable(); mask.setColor(0xFFFFFFFF); mask.setCornerRadius(dp(c,16));
    return new RippleDrawable(android.content.res.ColorStateList.valueOf(p.ripple), null, new InsetDrawable(mask, dp(c,8), 0, dp(c,8), 0)); }
  public static void setRow(View v, Pal p){ int l=v.getPaddingLeft(), t=v.getPaddingTop(), r=v.getPaddingRight(), b=v.getPaddingBottom(); v.setBackground(rowBg(v.getContext(),p)); v.setPadding(l,t,r,b); }
  /** A YouTube-sized tap row: min height, 24dp side padding, ripple + press scale. */
  public static LinearLayout row(Context c, Pal p, int minDp){ LinearLayout r=new LinearLayout(c); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
    r.setMinimumHeight(dp(c,minDp)); r.setPadding(dp(c,24),dp(c,8),dp(c,24),dp(c,8)); setRow(r,p); r.setClickable(true); r.setFocusable(true); r.setStateListAnimator(pressAnim(0.975f)); return r; }
  public static TextView text(Context c, String s, float sp, int color, boolean bold){
    TextView t=new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true);
    if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
  public static TextView medium(Context c, String s, float sp, int color){ TextView t=text(c,s,sp,color,false); t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t; }
  public static View divider(Context c, Pal p){ View d=new View(c); d.setBackgroundColor(p.line); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1, Math.max(1,dp(c,1)/2+0)); lp.setMargins(dp(c,24),dp(c,6),dp(c,24),dp(c,6)); d.setLayoutParams(lp); return d; }
  public static ImageView icon(Context c, int kind, int color, int sizeDp){ ImageView iv=new ImageView(c); iv.setImageDrawable(new Icon(kind,color)); iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c,sizeDp),dp(c,sizeDp))); return iv; }

  // ---------------------------------------------------------------- icons (24-unit grid, drawn in the given colour)
  public static final int I_BACK=1, I_MORE=2, I_VIDEO=3, I_AUDIO=4, I_CHEVRON=5, I_CHECK=6, I_DOWNLOAD=7, I_TIMER=8;
  public static final class Icon extends Drawable { final int kind; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); final Path path=new Path();
    public Icon(int kind, int color){ this.kind=kind; p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
    public void setColor(int c){ p.setColor(c); invalidateSelf(); }
    @Override public void draw(Canvas c){ Rect b=getBounds(); float s=Math.min(b.width(),b.height())/24f; c.save(); c.translate(b.left+(b.width()-24*s)/2f, b.top+(b.height()-24*s)/2f); c.scale(s,s);
      p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); path.reset();
      switch (kind){
        case I_BACK: path.moveTo(20,12); path.lineTo(4.5f,12); path.moveTo(11,5); path.lineTo(4,12); path.lineTo(11,19); c.drawPath(path,p); break;
        case I_MORE: p.setStyle(Paint.Style.FILL); c.drawCircle(12,5.5f,1.9f,p); c.drawCircle(12,12,1.9f,p); c.drawCircle(12,18.5f,1.9f,p); break;
        case I_VIDEO: p.setStrokeWidth(1.8f); c.drawRoundRect(new RectF(2.5f,5f,21.5f,19f),3.2f,3.2f,p); p.setStyle(Paint.Style.FILL); path.moveTo(10f,8.8f); path.lineTo(10f,15.2f); path.lineTo(15.4f,12f); path.close(); c.drawPath(path,p); break;
        case I_AUDIO: p.setStyle(Paint.Style.FILL); c.drawCircle(9.2f,16.8f,3.4f,p); c.drawRect(11.0f,4f,12.9f,16.8f,p); path.moveTo(11.0f,4f); path.lineTo(18.5f,4f); path.lineTo(18.5f,7.8f); path.lineTo(11.0f,7.8f); path.close(); c.drawPath(path,p); break;
        case I_CHEVRON: path.moveTo(9.5f,6); path.lineTo(15.5f,12); path.lineTo(9.5f,18); c.drawPath(path,p); break;
        case I_CHECK: p.setStrokeWidth(2.4f); path.moveTo(5,12.5f); path.lineTo(9.8f,17.2f); path.lineTo(19,7.6f); c.drawPath(path,p); break;
        case I_DOWNLOAD: path.moveTo(12,4); path.lineTo(12,15); path.moveTo(7,10.2f); path.lineTo(12,15.2f); path.lineTo(17,10.2f); path.moveTo(5,20); path.lineTo(19,20); c.drawPath(path,p); break;
        case I_TIMER: p.setStrokeWidth(1.8f); c.drawCircle(12,13.5f,7.8f,p); path.moveTo(12,13.5f); path.lineTo(12,9.3f); path.moveTo(9.5f,2.8f); path.lineTo(14.5f,2.8f); c.drawPath(path,p); break; }
      c.restore(); }
    @Override public void setAlpha(int a){ p.setAlpha(a); } @Override public void setColorFilter(ColorFilter cf){ p.setColorFilter(cf); } @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth(){ return -1; } @Override public int getIntrinsicHeight(){ return -1; } }

  /** Round tick that pops when toggled (text colour, never an accent colour). */
  public static final class Tick extends View { final Pal p; boolean on; float k=0f; final Paint pa=new Paint(Paint.ANTI_ALIAS_FLAG); final Icon check;
    public Tick(Context c, Pal p){ super(c); this.p=p; check=new Icon(I_CHECK, p.sheet); }
    public void set(boolean v, boolean anim){ on=v; if (!anim){ k=v?1f:0f; invalidate(); return; } ValueAnimator va=ValueAnimator.ofFloat(k, v?1f:0f); va.setDuration(v?320:180); va.setInterpolator(v? new OvershootInterpolator(2.5f): new AccelerateInterpolator()); va.addUpdateListener(x -> { k=(float)x.getAnimatedValue(); invalidate(); }); va.start(); }
    @Override protected void onDraw(Canvas c){ float w=getWidth(), h=getHeight(), r=Math.min(w,h)/2f-dp(getContext(),1.5f), cx=w/2f, cy=h/2f;
      pa.setStyle(Paint.Style.STROKE); pa.setStrokeWidth(dp(getContext(),2)); pa.setColor(p.sub); c.drawCircle(cx,cy,r,pa);
      if (k>0.001f){ float kr=Math.max(0f,k); pa.setStyle(Paint.Style.FILL); pa.setColor(p.text); c.drawCircle(cx,cy,(r+dp(getContext(),1))*Math.min(kr,1.15f),pa);
        int s=(int)(r*1.25f*Math.min(1f,kr)); check.setBounds((int)cx-s,(int)cy-s,(int)cx+s,(int)cy+s); check.setAlpha((int)(255*Math.min(1f,kr))); check.draw(c); } } }

  /** Skeleton bar with a moving shine (sizes / qualities while the video loads). */
  public static final class Shimmer extends View { final Pal p; final Paint base=new Paint(Paint.ANTI_ALIAS_FLAG), hi=new Paint(Paint.ANTI_ALIAS_FLAG); final RectF rf=new RectF();
    public Shimmer(Context c, Pal p){ super(c); this.p=p; base.setColor(p.shimmerBase); }
    @Override protected void onDraw(Canvas c){ float w=getWidth(), h=getHeight(), r=h/2f; rf.set(0,0,w,h); c.drawRoundRect(rf,r,r,base);
      int[] loc=new int[2]; getLocationOnScreen(loc); float sw=getResources().getDisplayMetrics().widthPixels, band=sw*0.45f;
      float ph=(SystemClock.uptimeMillis()%1400)/1400f; float x=-band+ph*(sw+2*band)-loc[0];
      hi.setShader(new LinearGradient(x,0,x+band,0,new int[]{0x00FFFFFF & p.shimmerHi, p.shimmerHi, 0x00FFFFFF & p.shimmerHi},null,Shader.TileMode.CLAMP)); c.drawRoundRect(rf,r,r,hi);
      postInvalidateOnAnimation(); } }

  /** Rounded progress bar with an animated sheen sweeping across the filled part while downloading. */
  public static final class SheenBar extends View { int fill, track, glow; float prog; boolean active=true; final Paint pa=new Paint(Paint.ANTI_ALIAS_FLAG); final RectF rf=new RectF();
    public SheenBar(Context c, int fill, int track){ super(c); this.fill=fill; this.track=track; glow=0xB3FFFFFF; }
    public void setProgress(int pct, boolean running){ prog=Math.max(0,Math.min(100,pct))/100f; active=running; invalidate(); }
    @Override protected void onDraw(Canvas c){ float w=getWidth(), h=getHeight(), r=h/2f; pa.setShader(null); pa.setColor(track); rf.set(0,0,w,h); c.drawRoundRect(rf,r,r,pa);
      float fw=Math.max(prog>0? h: 0, w*prog); if (fw<=0) return; rf.set(0,0,fw,h); pa.setColor(fill); c.drawRoundRect(rf,r,r,pa);
      if (!active || prog>=1f) return;
      float band=Math.max(h*14, w*0.3f), ph=(SystemClock.uptimeMillis()%1600)/1600f, x=-band+ph*(fw+band);
      pa.setShader(new LinearGradient(x,0,x+band,0,new int[]{0x00FFFFFF, glow, 0x00FFFFFF},null,Shader.TileMode.CLAMP)); c.save(); c.clipRect(rf); c.drawRoundRect(rf,r,r,pa); c.restore(); pa.setShader(null);
      postInvalidateDelayed(32); } }

  // ---------------------------------------------------------------- bottom sheet
  /** YouTube-style rounded bottom sheet hosted in an activity's root FrameLayout: scrim, drag handle, spring slide-up, nav-bar aware. */
  public static final class Sheet { final Context c; final Pal p; public final FrameLayout scrim; final View shade; public final LinearLayout body; final FrameLayout host; Runnable onGone; boolean closing, shown;
    int insTop, insBottom; final int under;
    public Sheet(Context c, Pal p, FrameLayout host, Runnable onGone){ this.c=c; this.p=p; this.host=host; this.onGone=onGone; under=dp(c,72);
      scrim=new FrameLayout(c); shade=new View(c); shade.setBackgroundColor(p.scrim); shade.setAlpha(0f); shade.setOnClickListener(v -> dismiss(null)); scrim.addView(shade,new FrameLayout.LayoutParams(-1,-1));
      body=new LinearLayout(c){ @Override protected void onMeasure(int wms, int hms){ int avail=MeasureSpec.getSize(hms); int max=avail-insTop-dp(c,48); super.onMeasure(wms, MeasureSpec.makeMeasureSpec(Math.max(dp(c,200),max), MeasureSpec.AT_MOST)); } };
      body.setOrientation(LinearLayout.VERTICAL); body.setClickable(true);
      GradientDrawable g=new GradientDrawable(); g.setColor(p.sheet); float r=dp(c,24); g.setCornerRadii(new float[]{r,r,r,r,0,0,0,0}); body.setBackground(g);
      if (SDK>=21) body.setElevation(dp(c,8));
      FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); lp.bottomMargin=-under; scrim.addView(body,lp); pad();
      body.setTranslationY(dp(c,2000));
      host.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
      onInsets(scrim,(t,b,l,rr) -> { insTop=t; insBottom=b; pad(); body.requestLayout(); }); }
    void pad(){ body.setPadding(0,0,0,dp(c,16)+insBottom+under); }
    /** Rebuilds the content: handle + title (+ subtitle) then whatever add() puts in; morphs height upward smoothly. */
    public TextView header(String title, String subtitle){ body.removeAllViews();
      View h=new View(c); h.setBackground(round(p.track, dp(c,2), 0)); LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(dp(c,36),dp(c,4)); hl.gravity=Gravity.CENTER_HORIZONTAL; hl.topMargin=dp(c,12); hl.bottomMargin=dp(c,8); body.addView(h,hl);
      TextView t=medium(c,title,20,p.text); t.setPadding(dp(c,24),dp(c,10),dp(c,24),subtitle==null? dp(c,12): dp(c,2)); body.addView(t);
      TextView s=text(c, subtitle==null? "": subtitle, 14, p.sub, false); s.setMaxLines(2); s.setEllipsize(android.text.TextUtils.TruncateAt.END); s.setPadding(dp(c,24),0,dp(c,24),dp(c,12)); s.setVisibility(subtitle==null? View.GONE: View.VISIBLE); body.addView(s);
      return s; }
    float topNow(){ return body.getTop()+body.getTranslationY(); }
    /** Call around a content rebuild: keeps the top edge where it was and springs to the new height. */
    public void morph(Runnable rebuild){ if (!shown){ rebuild.run(); return; } final float oldTop=topNow(); body.animate().cancel(); rebuild.run();
      body.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){ public boolean onPreDraw(){ body.getViewTreeObserver().removeOnPreDrawListener(this);
        float d=oldTop-body.getTop(); if (d>0){ body.setTranslationY(d); body.animate().translationY(0).setDuration(460).setInterpolator(spring()).start(); } else body.setTranslationY(0); return true; } }); }
    public void show(){ body.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){ public boolean onPreDraw(){ body.getViewTreeObserver().removeOnPreDrawListener(this);
        body.setTranslationY(body.getHeight()); body.animate().translationY(0).setDuration(560).setInterpolator(spring()).withEndAction(() -> shown=true).start();
        shade.animate().alpha(1f).setDuration(240).start(); return true; } }); scrim.requestLayout(); }
    public boolean isOpen(){ return !closing && scrim.getParent()!=null; }
    public void dismiss(final Runnable then){ if (closing) return; closing=true; body.animate().cancel();
      body.animate().translationY(body.getHeight()).setDuration(220).setInterpolator(new AccelerateInterpolator(1.6f)).start();
      shade.animate().alpha(0f).setDuration(220).withEndAction(() -> { host.removeView(scrim); if (then!=null) then.run(); if (onGone!=null) onGone.run(); }).start(); } }

  // ---------------------------------------------------------------- misc (unchanged API)
  public static Drawable progress(Context c, int fill, int track){ float r=dp(c,2);
    GradientDrawable t=new GradientDrawable(); t.setColor(track); t.setCornerRadius(r); GradientDrawable f=new GradientDrawable(); f.setColor(fill); f.setCornerRadius(r);
    LayerDrawable ld=new LayerDrawable(new Drawable[]{t, new ClipDrawable(f, Gravity.START, ClipDrawable.HORIZONTAL)}); ld.setId(0, android.R.id.background); ld.setId(1, android.R.id.progress); return ld; }
  public static final int BG=0xFF000000, CARD=0xFF121214, SILVER=0xFFD6D6DC, DIM=0xFF8A8A92, ACCENT=0xFFFFFFFF, LINE=0xFF26262A;
  public static void black(Activity a){
    Window w=a.getWindow(); w.setBackgroundDrawable(new ColorDrawable(BG));
    w.setStatusBarColor(BG); w.setNavigationBarColor(BG);
    if (Build.VERSION.SDK_INT>=29) { w.setStatusBarContrastEnforced(false); w.setNavigationBarContrastEnforced(false);} }
  public static GradientDrawable round(int color, float r, int stroke){
    GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(r);
    if (stroke!=0) g.setStroke(2, stroke); return g; }
  public static Button button(Context c, String s){
    Button b=new Button(c); b.setText(s); b.setAllCaps(false); b.setTextColor(ACCENT); b.setTextSize(15);
    b.setBackground(round(0xFF1C1C20, dp(c,12), 0xFF3A3A40)); b.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10)); return b; }
  public static String size(long b){
    if (b<=0) return "?";
    if (b<1024*1024) return String.format(java.util.Locale.US,"%.0f KB", b/1024.0);
    if (b<1024L*1024*1024) return String.format(java.util.Locale.US,"%.1f MB", b/1048576.0);
    return String.format(java.util.Locale.US,"%.2f GB", b/1073741824.0); }
  public static String time(long ms){
    long s=Math.max(0,ms/1000); long h=s/3600, m=(s%3600)/60, ss=s%60;
    return h>0? String.format(java.util.Locale.US,"%d:%02d:%02d",h,m,ss): String.format(java.util.Locale.US,"%02d:%02d",m,ss); }
}
