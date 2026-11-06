package paradox.dl;
import android.app.*; import android.content.*; import android.graphics.*; import android.media.MediaPlayer; import android.net.Uri; import android.os.*; import android.view.*; import android.widget.*;
import java.util.*;
/** v10.13: plays a downloaded video from the Queue / Watch later page, fully offline, with previous / next and automatic advance.
 *  Monochrome, tap the video to show or hide the controls. If the next entry is not downloaded it opens in YouTube instead (needs network). */
public class OfflinePlayerActivity extends Activity {
  VideoView vv; TextView title, time; SeekBar seek; ImageButton play; View ui; String list, id; final Handler h=new Handler(Looper.getMainLooper()); boolean seeking; int resumeAt;
  int dp(float v){ return Ui.dp(this,v); }
  @Override protected void onCreate(Bundle b){ super.onCreate(b); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    list=getIntent().getStringExtra("list"); id=getIntent().getStringExtra("id"); if (b!=null) resumeAt=b.getInt("pos",0);
    FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0xFF000000); vv=new VideoView(this); root.addView(vv,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
    ui=new FrameLayout(this); FrameLayout f=(FrameLayout)ui; root.addView(ui,new FrameLayout.LayoutParams(-1,-1));
    title=Ui.medium(this,"",15,0xFFFFFFFF); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(dp(16),dp(14),dp(16),dp(14)); title.setBackgroundColor(0x99000000);
    f.addView(title,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
    LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setBackgroundColor(0x99000000); bottom.setPadding(dp(12),dp(6),dp(12),dp(10));
    LinearLayout sr=new LinearLayout(this); sr.setGravity(Gravity.CENTER_VERTICAL); time=Ui.text(this,"00:00 / 00:00",12,0xFFFFFFFF,false); seek=new SeekBar(this);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean u){ if (u) vv.seekTo(p); } public void onStartTrackingTouch(SeekBar s){ seeking=true; } public void onStopTrackingTouch(SeekBar s){ seeking=false; } });
    sr.addView(seek,new LinearLayout.LayoutParams(0,-2,1f)); sr.addView(time); bottom.addView(sr);
    LinearLayout br=new LinearLayout(this); br.setGravity(Gravity.CENTER); br.addView(btn("\u23EE",v -> go(-1))); br.addView(btn("\u23EA",v -> vv.seekTo(Math.max(0,vv.getCurrentPosition()-10000))));
    play=btn("\u23F8",v -> { if (vv.isPlaying()) { vv.pause(); play.setImageDrawable(glyph("\u25B6")); } else { vv.start(); play.setImageDrawable(glyph("\u23F8")); } }); br.addView(play);
    br.addView(btn("\u23E9",v -> vv.seekTo(Math.min(vv.getDuration(),vv.getCurrentPosition()+10000)))); br.addView(btn("\u23ED",v -> go(+1))); bottom.addView(br);
    f.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    vv.setOnClickListener(v -> toggle()); root.setOnClickListener(v -> toggle());
    vv.setOnCompletionListener(mp -> { if (!go(+1)) { Toast.makeText(this,"End of the list",Toast.LENGTH_SHORT).show(); finish(); } });
    vv.setOnErrorListener((mp,w,x) -> { Toast.makeText(this,"Can't play this file",Toast.LENGTH_SHORT).show(); if (!go(+1)) finish(); return true; });
    setContentView(root); hideBars(); load(getIntent().getStringExtra("path")); tick.run(); }
  android.graphics.drawable.Drawable glyph(String s){ return new Drawable0(s); }
  /** A text glyph as an image (keeps the controls dependency-free and monochrome). */
  final class Drawable0 extends android.graphics.drawable.Drawable { final String s; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); Drawable0(String s){ this.s=s; p.setColor(0xFFFFFFFF); p.setTextAlign(Paint.Align.CENTER); }
    @Override public void draw(Canvas c){ Rect b=getBounds(); p.setTextSize(b.height()*0.62f); c.drawText(s,b.exactCenterX(),b.exactCenterY()-(p.ascent()+p.descent())/2,p); }
    @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; } }
  ImageButton btn(String g, View.OnClickListener l){ ImageButton b=new ImageButton(this); b.setImageDrawable(glyph(g)); b.setBackgroundColor(0); b.setOnClickListener(l); b.setLayoutParams(new LinearLayout.LayoutParams(dp(56),dp(48))); return b; }
  void toggle(){ ui.setVisibility(ui.getVisibility()==View.VISIBLE? View.GONE: View.VISIBLE); }
  void hideBars(){ getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
  void load(String path){ try { QueueStore.Entry cur=null; for (QueueStore.Entry e: QueueRun.store(this).all(list)) if (e.id.equals(id)) cur=e;
      title.setText(cur==null||cur.title.isEmpty()? "Offline video": cur.title); final int at=resumeAt; resumeAt=0;
      vv.setVideoURI(Open.uri(this,path)); vv.setOnPreparedListener(mp -> { if (at>0) mp.seekTo(at); vv.start(); play.setImageDrawable(glyph("\u23F8")); seek.setMax(Math.max(1,vv.getDuration())); });
    } catch (Throwable t){ Toast.makeText(this,"Can't open the file",Toast.LENGTH_SHORT).show(); } }
  /** Next (+1) or previous (-1) entry: a downloaded one plays here; one that is not downloaded opens in YouTube. */
  boolean go(int dir){ QueueStore s=QueueRun.store(this); int idx= dir>0? s.nextIndex(list,id): s.prevIndex(list,id); if (idx<0) return false;
    QueueStore.Entry e=s.all(list).get(idx); Store.Item d=QueueRun.downloaded(this,e.id); QueueRun.list=list;
    if (d!=null) { id=e.id; QueueRun.id=e.id; QueueRun.online=false; load(d.path); return true; }
    QueueRun.play(this,list,e); finish(); return true; }
  final Runnable tick=new Runnable(){ public void run(){ try { if (vv.getDuration()>0 && !seeking) { seek.setProgress(vv.getCurrentPosition()); time.setText(Ui.time(vv.getCurrentPosition())+" / "+Ui.time(vv.getDuration())); } } catch (Throwable t){} h.postDelayed(this,500); } };
  @Override protected void onSaveInstanceState(Bundle o){ super.onSaveInstanceState(o); try { o.putInt("pos",vv.getCurrentPosition()); } catch (Throwable t){} }
  @Override protected void onPause(){ super.onPause(); try { vv.pause(); } catch (Throwable t){} }
  @Override protected void onDestroy(){ super.onDestroy(); h.removeCallbacksAndMessages(null); }
  @Override public void onWindowFocusChanged(boolean f){ super.onWindowFocusChanged(f); if (f) hideBars(); }
}
