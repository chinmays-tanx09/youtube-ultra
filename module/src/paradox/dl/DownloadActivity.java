package paradox.dl;
import android.app.*; import android.content.*; import android.os.*; import android.view.*; import android.widget.*; import android.graphics.*; import android.graphics.drawable.*;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.playlist.*;
import java.util.*; import java.util.regex.*;
/** YouTube-style download flow: Video/Audio sheet -> quality sheet (tap a row to start) -> our 16-connection engine. */
public class DownloadActivity extends Activity {
  Ui.Pal P; FrameLayout root; Ui.Sheet sheet; String url; volatile StreamInfo info; volatile List<String[]> vids; volatile String err; volatile boolean plainErr; String type; boolean playlist, remember; TextView subtitle;
  final Handler h=new Handler(Looper.getMainLooper()); Runnable waiting;
  static String findUrl(String s){ if (s==null) return null; Matcher m=Pattern.compile("https?://\\S+").matcher(s); return m.find()? m.group(): null; }
  int dp(float v){ return Ui.dp(this,v); }
  @Override protected void onCreate(Bundle b){ super.onCreate(b); P=Ui.pal(this); overridePendingTransition(0,0);
    getWindow().setBackgroundDrawable(new ColorDrawable(0));
    Intent in=getIntent(); String t=in.getStringExtra(Intent.EXTRA_TEXT); if (t==null && in.getData()!=null) t=in.getDataString(); if (t==null) t=in.getStringExtra("url");
    url=findUrl(t); if (url==null){ Toast.makeText(this,"No video link",Toast.LENGTH_SHORT).show(); finish(); return; }
    playlist= url.contains("list=") && !url.contains("v=") && !url.contains("youtu.be/") && !url.contains("/shorts/");
    root=new FrameLayout(this); setContentView(root); Ui.edgeToEdge(this, false, !P.dark);
    sheet=new Ui.Sheet(this, P, root, () -> { finish(); overridePendingTransition(0,0); });
    if (Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
    resolve(); typeSheet(); sheet.show(); }
  @Override public void onBackPressed(){ if (sheet!=null && sheet.isOpen()) sheet.dismiss(null); else super.onBackPressed(); }
  void resolve(){ new Thread(() -> { try { Net.init();
      if (playlist){ PlaylistInfo pi=PlaylistInfo.getInfo(ServiceList.YouTube, url); List<String[]> v=new ArrayList<>(); for (StreamInfoItem s: pi.getRelatedItems()) v.add(new String[]{s.getUrl(), s.getName()});
        Page next=pi.getNextPage(); int guard=0; while (next!=null && guard++<20){ ListExtractor.InfoItemsPage<StreamInfoItem> pg=PlaylistInfo.getMoreItems(ServiceList.YouTube, url, next); for (StreamInfoItem s: pg.getItems()) v.add(new String[]{s.getUrl(), s.getName()}); next=pg.getNextPage(); }
        vids=v; }
      else info=Resolve.info(url);
    } catch (Throwable e){ android.util.Log.e("ParadoxDL","resolve failed",e); plainErr= e instanceof Resolve.Plain; err=String.valueOf(e.getMessage()); }
    h.post(() -> { fillSubtitle(); if (waiting!=null){ Runnable x=waiting; waiting=null; x.run(); } }); }).start(); }
  String what(){ if (info!=null) return info.getName(); if (vids!=null) return vids.size()+" videos in this playlist"; return null; }
  void fillSubtitle(){ if (subtitle==null) return; String w=what(); if (w==null) return; boolean blank=subtitle.getText().toString().trim().isEmpty(); subtitle.setText(w); subtitle.setVisibility(View.VISIBLE); if (blank){ subtitle.setAlpha(0f); subtitle.animate().alpha(1f).setDuration(260).start(); } }
  /** Two-line row with a 24dp icon on a soft rounded tile and a chevron: tap to continue. */
  View iconRow(int icon, String label, String sub, int idx){ LinearLayout r=Ui.row(this,P,72);
    FrameLayout tile=new FrameLayout(this); tile.setBackground(Ui.round(P.chip, dp(14), 0)); ImageView ic=new ImageView(this); ic.setImageDrawable(new Ui.Icon(icon,P.text)); tile.addView(ic,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
    r.addView(tile,new LinearLayout.LayoutParams(dp(44),dp(44)));
    LinearLayout col=new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(16),0,dp(8),0); col.addView(Ui.text(this,label,16,P.text,false));
    if (sub!=null){ TextView s=Ui.text(this,sub,14,P.sub,false); s.setPadding(0,dp(2),0,0); col.addView(s); } r.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
    r.addView(Ui.icon(this,Ui.I_CHEVRON,P.sub,20)); Ui.enter(r,idx); return r; }
  void typeSheet(){ subtitle=sheet.header("Download", what()==null? " ": what());
    View v=iconRow(Ui.I_VIDEO,"Video","MP4 with sound, saved to Gallery",0); v.setOnClickListener(x -> chooseType("video"));
    View a=iconRow(Ui.I_AUDIO,"Audio","MP3, saved to Music",1); a.setOnClickListener(x -> chooseType("audio"));
    sheet.body.addView(v); sheet.body.addView(a);
    if (!playlist) { View q=iconRow(Ui.I_VIDEO,"Add to queue","Play it later in the YouTube Ultra queue",2); q.setOnClickListener(x -> saveTo(QueueStore.QUEUE));
      View w=iconRow(Ui.I_TIMER,"Watch later","Save it here; download it to watch offline",3); w.setOnClickListener(x -> saveTo(QueueStore.LATER)); sheet.body.addView(q); sheet.body.addView(w); } }
  /** v10.13: queue / offline Watch later. */
  void saveTo(String list){ String m; try { m=QueueRun.add(this, list, url, info!=null? info.getName(): null); } catch (Throwable e){ m="Couldn't save"; } Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); sheet.dismiss(null); }
  void chooseType(String t){ type=t; String rem=Prefs.get(this, t); if (rem!=null){ startRemembered(rem); return; } qualitySheet(); }
  String qTitle(){ return type.equals("audio")?"Audio quality":"Download quality"; }
  /** While the video is still loading: skeleton rows with a moving shimmer where qualities and sizes will be. */
  void whenReady(final Runnable r){ if (err!=null){ fail(); return; } if (info!=null || vids!=null) { r.run(); return; }
    sheet.morph(() -> { subtitle=sheet.header(qTitle(), "Getting sizes…"); int n=type.equals("audio")? 3: 5;
      for (int k=0;k<n;k++){ LinearLayout rw=new LinearLayout(this); rw.setGravity(Gravity.CENTER_VERTICAL); rw.setMinimumHeight(dp(56)); rw.setPadding(dp(24),0,dp(24),0);
        Ui.Shimmer a=new Ui.Shimmer(this,P); LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(dp(k%2==0? 132: 104),dp(14)); rw.addView(a,al);
        rw.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f)); Ui.Shimmer s=new Ui.Shimmer(this,P); rw.addView(s,new LinearLayout.LayoutParams(dp(60),dp(12))); sheet.body.addView(rw); } });
    waiting=() -> { if (err!=null) fail(); else r.run(); }; }
  /** The whole reason in a dialog (a toast cut it off after two lines), with Try again. */
  void fail(){ final String msg= plainErr? err: "Couldn't load this video: "+err; if (isFinishing()) return;
    try { android.app.AlertDialog d=new android.app.AlertDialog.Builder(this, P.dark? android.R.style.Theme_DeviceDefault_Dialog_Alert: android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
        .setTitle("Can't download this video").setMessage(msg)
        .setPositiveButton("Try again", (x,w) -> retry())
        .setNegativeButton("Close", (x,w) -> sheet.dismiss(null))
        .setOnCancelListener(x -> sheet.dismiss(null)).create();
      d.show(); }
    catch (Throwable t){ Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); sheet.dismiss(null); } }
  /** Resolve again (every route) and continue where the person was. */
  void retry(){ err=null; plainErr=false; info=null; vids=null; resolve(); if (type!=null) chooseType(type); }
  List<String[]> rows(){ boolean au=type.equals("audio");
    if (playlist){ if (au) return Arrays.asList(new String[]{"a:rank:0","MP3 320 kbps (Best)","0"},new String[]{"a:rank:1","MP3 192 kbps (Standard)","0"},new String[]{"a:rank:2","MP3 128 kbps (Smaller)","0"});
      return Arrays.asList(new String[]{"v:2160:any","2160p (4K)","0"},new String[]{"v:1440:any","1440p (QHD)","0"},new String[]{"v:1080:mp4","1080p (Full HD)","0"},new String[]{"v:720:mp4","720p (HD)","0"},new String[]{"v:480:mp4","480p","0"},new String[]{"v:360:mp4","360p","0"},new String[]{"v:144:mp4","144p","0"}); }
    return au? Formats.audioChoices(info): Formats.videoChoices(info); }
  /** "1080p (Full HD) 60fps" -> main "1080p", rest "Full HD · 60fps". */
  static String[] split(String label){ String main=label, rest=""; int i=label.indexOf(" ("); if (i>0){ main=label.substring(0,i); rest=label.substring(i+2).replace(")", " ·").trim(); if (rest.endsWith("·")) rest=rest.substring(0,rest.length()-1).trim(); }
    else { int j=label.indexOf(" · "); if (j>0){ main=label.substring(0,j); rest=label.substring(j+3); } else { int k=label.indexOf(' '); if (k>0 && label.matches("^\\d+p .*")){ main=label.substring(0,k); rest=label.substring(k+1); } } }
    return new String[]{main, rest}; }
  void qualitySheet(){ whenReady(() -> { final List<String[]> rows=rows(); if (rows.isEmpty()){ err="no downloadable formats"; fail(); return; }
    sheet.morph(() -> { subtitle=sheet.header(qTitle(), playlist? vids.size()+" videos": what());
    LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
    for (int k=0;k<rows.size();k++){ final String[] r=rows.get(k); final int idx=k; LinearLayout rw=Ui.row(this,P,56);
      String[] lab=split(r[1]); TextView main=Ui.text(this,lab[0],16,P.text,false); rw.addView(main);
      TextView extra=Ui.text(this,lab[1].isEmpty()? "": "  "+lab[1],14,P.sub,false); extra.setSingleLine(true); extra.setEllipsize(android.text.TextUtils.TruncateAt.END); rw.addView(extra,new LinearLayout.LayoutParams(0,-2,1f));
      long sz=Long.parseLong(r[2]); if (sz>0){ TextView st=Ui.text(this,Ui.size(sz),14,P.sub,false); st.setGravity(Gravity.END); st.setPadding(dp(12),0,0,0); rw.addView(st); }
      rw.setOnClickListener(v -> { if (remember) Prefs.put(this, type, type.equals("audio")? "rank:"+idx: r[0].split(":")[1]); go(r[0]); }); Ui.enter(rw,k); list.addView(rw); }
    ScrollView sv=new ScrollView(this); sv.setOverScrollMode(View.OVER_SCROLL_NEVER); sv.setVerticalScrollBarEnabled(false); sv.addView(list); sheet.body.addView(sv,new LinearLayout.LayoutParams(-1,-2,1f));
    sheet.body.addView(Ui.divider(this,P));
    final LinearLayout rr=Ui.row(this,P,56);
    final Ui.Tick tick=new Ui.Tick(this,P); tick.set(remember,false); rr.addView(tick,new LinearLayout.LayoutParams(dp(24),dp(24)));
    TextView rl=Ui.text(this,"Remember my settings for 30 days",16,P.text,false); rl.setPadding(dp(16),0,0,0); rr.addView(rl,new LinearLayout.LayoutParams(0,-2,1f));
    rr.setOnClickListener(v -> { remember=!remember; tick.set(remember,true); }); Ui.enter(rr, Math.min(rows.size(),8)); sheet.body.addView(rr); }); }); }
  void startRemembered(final String rem){ whenReady(() -> { String spec;
    if (type.equals("audio")) spec="a:rank:"+rem.replace("rank:","");
    else { int want=Integer.parseInt(rem); spec="v:"+want+":mp4"; if (!playlist){ List<String[]> vc=Formats.videoChoices(info); String best=null; for (String[] c: vc){ int hh=Integer.parseInt(c[0].split(":")[1]); if (hh<=want){ best=c[0]; break; } } if (best==null && !vc.isEmpty()) best=vc.get(vc.size()-1)[0]; if (best!=null) spec=best; } }
    go(spec); }); }
  void go(String spec){
    if (playlist){ for (String[] v: vids){ Store.Item it=Store.add(this, v[0], spec, v[1]); DownloadService.start(this, it.id);} Toast.makeText(this,"Downloading "+vids.size()+" videos",Toast.LENGTH_SHORT).show(); }
    else { Store.Item it=Store.add(this, url, spec, info.getName()); DownloadService.start(this, it.id); Toast.makeText(this,"Downloading…",Toast.LENGTH_SHORT).show(); }
    Store.notifyChanged(); sheet.dismiss(null); }
}
