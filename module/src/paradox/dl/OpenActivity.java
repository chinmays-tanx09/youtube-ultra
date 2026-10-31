package paradox.dl;
import android.app.Activity; import android.os.Bundle;
/** Download-complete notification target: hands the file to the phone's player, then closes (no UI of its own). */
public class OpenActivity extends Activity {
  @Override protected void onCreate(Bundle b){ super.onCreate(b);
    String p=getIntent().getStringExtra("path"); boolean audio=getIntent().getBooleanExtra("audio", false);
    if (p!=null && !getIntent().hasExtra("audio")) audio= Open.ext(p).matches("m4a|opus|ogg|mp3|aac") || p.contains("/audio/") || p.contains("/Music/");
    Open.play(this, p, audio, false);
    finish(); overridePendingTransition(0,0); }
}
