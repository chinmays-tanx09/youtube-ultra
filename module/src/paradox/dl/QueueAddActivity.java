package paradox.dl;
import android.app.Activity; import android.content.Intent; import android.os.*; import android.widget.Toast;
/** v10.13: Share -> "Add to queue (YouTube Ultra)". No screen: saves the link and closes. */
public class QueueAddActivity extends Activity {
  String list(){ return QueueStore.QUEUE; }
  @Override protected void onCreate(Bundle b){ super.onCreate(b); Intent in=getIntent(); String t=in.getStringExtra(Intent.EXTRA_TEXT); if (t==null && in.getData()!=null) t=in.getDataString();
    String m; try { m=QueueRun.add(this, list(), t==null? "": t, in.getStringExtra(Intent.EXTRA_SUBJECT)); } catch (Throwable e){ m="Couldn't save"; }
    Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); finish(); overridePendingTransition(0,0); }
}
