package paradox.dl;
import android.app.Activity; import android.os.Bundle; import android.widget.Toast;
/** v10.13: one-tap actions from Settings -> YouTube Ultra (no screen). */
public class UltraActionActivity extends Activity {
  @Override protected void onCreate(Bundle b){ super.onCreate(b); String d=getIntent().getStringExtra("do");
    if ("clear_seen".equals(d)) { int n=Ultra.seenCount(); Ultra.clearSeen(this); Toast.makeText(this, n==0? "The seen list was already empty": "Cleared "+n+" seen Shorts", Toast.LENGTH_SHORT).show(); }
    finish(); overridePendingTransition(0,0); }
}
