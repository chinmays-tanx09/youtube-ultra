package paradox.dl;
import android.app.*; import android.content.*; import android.graphics.*; import android.graphics.drawable.*; import android.os.*; import android.view.*; import android.widget.*;
import java.util.*;
/** v10.13: Queue + offline Watch later page (Settings -> YouTube Ultra -> Queue & Watch later; also the two buttons on the Downloads page). */
public class QueueActivity extends Activity implements Store.Listener {
  Ui.Pal P; String cur=QueueStore.QUEUE; LinearLayout list, tabs; TextView tQ, tL; final Handler h=new Handler(Looper.getMainLooper());
  static final android.util.LruCache<String,Bitmap> THUMBS=new android.util.LruCache<>(60);
  int dp(float v){ return Ui.dp(this,v); }
  QueueStore store(){ return QueueRun.store(this); }
  @Override protected void onCreate(Bundle b){ P=Ui.pal(this); setTheme(P.dark? android.R.style.Theme_Material_NoActionBar: android.R.style.Theme_Material_Light_NoActionBar); super.onCreate(b);
    if ("later".equals(getIntent().getStringExtra("list"))) cur=QueueStore.LATER;
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(P.bg);
    LinearLayout bar=DownloadSettingsActivity.toolbar(this,P,"Queue & Watch later");
    FrameLayout more=new FrameLayout(this); GradientDrawable m=new GradientDrawable(); m.setShape(GradientDrawable.OVAL); m.setColor(0xFFFFFFFF);
    more.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(P.ripple), null, m)); more.setClickable(true); more.setContentDescription("More");
    ImageView mi=new ImageView(this); mi.setImageDrawable(new Ui.Icon(Ui.I_MORE,P.text)); more.addView(mi,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
    more.setOnClickListener(v -> menu(v)); bar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48))); root.addView(bar);
    tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(dp(12),dp(4),dp(12),dp(4));
    tQ=tab(QueueStore.QUEUE); tL=tab(QueueStore.LATER); tabs.addView(tQ,new LinearLayout.LayoutParams(0,dp(44),1f)); tabs.addView(tL,new LinearLayout.LayoutParams(0,dp(44),1f)); root.addView(tabs);
    root.addView(Ui.divider(this,P));
    list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this); sv.setClipToPadding(false); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1f));
    setContentView(root); Ui.themed(this); Ui.onInsets(sv,(tp,bt,l,r) -> sv.setPadding(l,0,r,bt)); }
  TextView tab(final String name){ TextView t=Ui.medium(this,"",15,P.sub); t.setGravity(Gravity.CENTER); t.setOnClickListener(v -> { cur=name; render(); }); return t; }
  @Override protected void onResume(){ super.onResume(); Store.listen(this); store().reload(); render(); }
  @Override protected void onPause(){ super.onPause(); Store.unlisten(this); }
  @Override public void changed(){ h.post(this::render); }
  void menu(View v){ PopupMenu pm=new PopupMenu(this,v); pm.getMenu().add(0,1,0,"Play all"); pm.getMenu().add(0,2,0,"Clear this list"); if (QueueStore.LATER.equals(cur)) pm.getMenu().add(0,3,0,"Download all"); pm.getMenu().add(0,4,0,"Open Downloads");
    pm.setOnMenuItemClickListener(it -> { switch (it.getItemId()) {
      case 1: QueueRun.play(this,cur,0); break;
      case 2: new AlertDialog.Builder(this, P.dark? android.R.style.Theme_DeviceDefault_Dialog_Alert: android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("Clear "+(QueueStore.LATER.equals(cur)? "Watch later": "Queue")+"?").setMessage("Downloaded files stay on the phone.").setPositiveButton("Clear",(d,w) -> { store().clear(cur); render(); }).setNegativeButton("Cancel",null).show(); break;
      case 3: int n=0; for (QueueStore.Entry e: store().all(cur)) if (QueueRun.downloaded(this,e.id)==null && QueueRun.startDownload(this,e.id)) n++; Toast.makeText(this, n+" download"+(n==1?"":"s")+" started", Toast.LENGTH_SHORT).show(); break;
      case 4: startActivity(new Intent(this, DownloadsActivity.class)); break; }
      return true; }); pm.show(); }
  void render(){ QueueStore s=store(); int nq=s.size(QueueStore.QUEUE), nl=s.size(QueueStore.LATER);
    tQ.setText("Queue ("+nq+")"); tL.setText("Watch later ("+nl+")"); boolean q=QueueStore.QUEUE.equals(cur); tQ.setTextColor(q? P.text: P.sub); tL.setTextColor(q? P.sub: P.text);
    tQ.setBackground(q? Ui.round(P.chip, dp(22), 0): null); tL.setBackground(q? null: Ui.round(P.chip, dp(22), 0));
    list.removeAllViews(); final List<QueueStore.Entry> es=s.all(cur);
    if (es.isEmpty()) { TextView e=Ui.text(this, q? "Your queue is empty.\n\nOn any video: Download -> Add to queue, or Share -> “Add to queue (YouTube Ultra)”.": "Nothing saved for later.\n\nOn any video: Download -> Watch later, or Share -> “Watch later (YouTube Ultra)”. Saved videos can be downloaded and then play offline.", 15, P.sub, false); e.setPadding(dp(24),dp(32),dp(24),dp(24)); e.setLineSpacing(0,1.2f); list.addView(e); return; }
    for (int i=0;i<es.size();i++) list.addView(row(es, i)); }
  View row(final List<QueueStore.Entry> es, final int i){ final QueueStore.Entry e=es.get(i); final boolean dl=QueueRun.downloaded(this,e.id)!=null;
    LinearLayout r=Ui.row(this,P,76); r.setPadding(dp(16),dp(8),dp(4),dp(8));
    TextView num=Ui.text(this,String.valueOf(i+1),13,P.sub,false); num.setGravity(Gravity.CENTER); r.addView(num,new LinearLayout.LayoutParams(dp(22),-2));
    ImageView th=new ImageView(this); th.setScaleType(ImageView.ScaleType.CENTER_CROP); th.setBackground(Ui.round(P.chip, dp(8), 0)); th.setClipToOutline(true); th.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(dp(112),dp(63)); tl.leftMargin=dp(8); r.addView(th,tl); thumb(th,e);
    LinearLayout col=new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(12),0,dp(4),0);
    TextView ti=Ui.text(this, e.title.isEmpty()? "YouTube video ("+e.id+")": e.title, 15, P.text, false); ti.setMaxLines(2); ti.setEllipsize(android.text.TextUtils.TruncateAt.END); col.addView(ti);
    String sub=(e.channel.isEmpty()? "": e.channel+" · ")+(dl? "Downloaded · plays offline": "Not downloaded"); TextView su=Ui.text(this,sub,12,dl? P.text: P.sub,false); su.setPadding(0,dp(2),0,0); su.setSingleLine(true); su.setEllipsize(android.text.TextUtils.TruncateAt.END); col.addView(su);
    r.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
    FrameLayout mb=new FrameLayout(this); mb.setClickable(true); mb.setContentDescription("Options"); ImageView ic=new ImageView(this); ic.setImageDrawable(new Ui.Icon(Ui.I_MORE,P.sub)); mb.addView(ic,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER)); r.addView(mb,new LinearLayout.LayoutParams(dp(44),dp(44)));
    r.setOnClickListener(v -> QueueRun.play(this,cur,i));
    mb.setOnClickListener(v -> { PopupMenu pm=new PopupMenu(this,v); pm.getMenu().add(0,1,0,"Play"); pm.getMenu().add(0,2,0,"Play next (move to top)"); if (i>0) pm.getMenu().add(0,3,0,"Move up"); if (i<es.size()-1) pm.getMenu().add(0,4,0,"Move down");
      if (!dl) pm.getMenu().add(0,5,0,"Download"); pm.getMenu().add(0,6,0,"Remove");
      pm.setOnMenuItemClickListener(it -> { QueueStore s=store(); switch (it.getItemId()) {
        case 1: QueueRun.play(this,cur,i); return true; case 2: s.moveToFront(cur,e.id); break; case 3: s.move(cur,e.id,-1); break; case 4: s.move(cur,e.id,+1); break;
        case 5: Toast.makeText(this, QueueRun.startDownload(this,e.id)? "Downloading…": "Couldn't start the download", Toast.LENGTH_SHORT).show(); break; case 6: s.remove(cur,e.id); break; }
        render(); return true; }); pm.show(); });
    return r; }
  void thumb(final ImageView iv, final QueueStore.Entry e){ Bitmap b=THUMBS.get(e.id); if (b!=null) { iv.setImageBitmap(b); return; } iv.setTag(e.id);
    new Thread(() -> { try { java.net.HttpURLConnection u=(java.net.HttpURLConnection)new java.net.URL(e.thumb).openConnection(); u.setConnectTimeout(8000); u.setReadTimeout(8000); if (u.getResponseCode()!=200) return;
        final Bitmap bm=BitmapFactory.decodeStream(u.getInputStream()); if (bm==null) return; THUMBS.put(e.id,bm); h.post(() -> { if (e.id.equals(iv.getTag())) iv.setImageBitmap(bm); }); } catch (Throwable t){} }).start(); }
}
