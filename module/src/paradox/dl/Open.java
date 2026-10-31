package paradox.dl;
import android.app.Activity; import android.content.*; import android.database.Cursor; import android.net.Uri; import android.os.*; import android.provider.MediaStore; import android.widget.Toast; import java.io.File; import java.util.Locale;
/** Hands a finished download to the phone's own player/music app (plain ACTION_VIEW, so Android shows its "Just once / Always" picker the first time). */
public final class Open {
  /** content:// URI for a saved download: the MediaStore item, else a MediaStore lookup of a legacy file path, else our own read-only provider. */
  public static Uri uri(Context c, String path){
    if (path==null || path.isEmpty()) return null;
    if (path.startsWith("content:")) return Uri.parse(path);
    if (path.startsWith("file:")) path=Uri.parse(path).getPath();
    File f=new File(path);
    try { Uri coll=MediaStore.Files.getContentUri("external");
      Cursor cu=c.getContentResolver().query(coll, new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DATA+"=?", new String[]{f.getAbsolutePath()}, null);
      if (cu!=null) try { if (cu.moveToFirst()) { long id=cu.getLong(0); String ext=ext(path);
        boolean audio= ext.equals("m4a")||ext.equals("opus")||ext.equals("ogg")||ext.equals("mp3")||ext.equals("aac")||(ext.equals("webm") && path.contains("/Music/"));
        return ContentUris.withAppendedId(audio? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI: MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id); } } finally { cu.close(); }
    } catch (Throwable t) { android.util.Log.w("ParadoxDL","mediastore lookup",t); }
    return FileShare.uri(c, f);
  }
  static String ext(String p){ String s=p==null? "": p; int q=s.lastIndexOf('.'); return q<0? "": s.substring(q+1).toLowerCase(Locale.US); }
  /** MIME by file extension (what MediaStore stored for our items). */
  public static String mimeFor(String name, boolean audio){
    switch (ext(name)){ case "mp4": return audio? "audio/mp4": "video/mp4"; case "m4a": return "audio/mp4"; case "webm": return audio? "audio/webm": "video/webm";
      case "opus": case "ogg": return "audio/ogg"; case "mp3": return "audio/mpeg"; case "aac": return "audio/aac"; case "mkv": return "video/x-matroska"; case "3gp": return "video/3gpp"; default: return audio? "audio/*": "video/*"; }
  }
  static String mime(Context c, Uri u, String path, boolean audio){
    String m=null;
    try { m=c.getContentResolver().getType(u); } catch (Throwable t) {}
    if (m==null || m.isEmpty() || m.equals("application/octet-stream") || m.endsWith("/*")) {
      String name=path;
      if (path!=null && path.startsWith("content:")) try { Cursor cu=c.getContentResolver().query(u, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
        if (cu!=null) try { if (cu.moveToFirst()) name=cu.getString(0); } finally { cu.close(); } } catch (Throwable t) {}
      m=mimeFor(name, audio);
    }
    return m;
  }
  static boolean exists(Context c, Uri u){
    try { android.os.ParcelFileDescriptor pfd=c.getContentResolver().openFileDescriptor(u,"r"); if (pfd==null) return false; pfd.close(); return true; } catch (Throwable t) { return false; }
  }
  static Intent view(Context c, Uri u, String type){
    Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(u, type);
    i.setClipData(ClipData.newRawUri("", u)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return i;
  }
  /** Tap on a finished download. chooser=false: the system default / "Just once · Always" picker. chooser=true: "Open with…" every time. */
  public static void play(Context c, String path, boolean audio, boolean chooser){
    try {
      Uri u=uri(c, path);
      if (u==null || !exists(c, u)) { Toast.makeText(c, "This file is no longer on your phone", Toast.LENGTH_SHORT).show(); return; }
      Intent i=view(c, u, mime(c, u, path, audio));
      if (chooser) { Intent ch=Intent.createChooser(i, "Open with"); if (!(c instanceof Activity)) ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(ch); }
      else c.startActivity(i);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(c, audio? "No music app on this phone can play this file": "No video player on this phone can play this file", Toast.LENGTH_LONG).show();
    } catch (Throwable t) { android.util.Log.e("ParadoxDL","open",t); Toast.makeText(c, "Couldn't open this file", Toast.LENGTH_SHORT).show(); }
  }
  public static void play(Context c, Store.Item it, boolean chooser){ play(c, it.path, it.audio, chooser); }
  /** Share sheet with a readable content:// URI (file:// URIs crash on Android 7+). */
  public static void share(Context c, Store.Item it){
    try { Uri u=uri(c, it.path); if (u==null || !exists(c,u)) { Toast.makeText(c, "This file is no longer on your phone", Toast.LENGTH_SHORT).show(); return; }
      Intent s=new Intent(Intent.ACTION_SEND).setType(mime(c,u,it.path,it.audio)).putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      s.setClipData(ClipData.newRawUri("", u));
      Intent ch=Intent.createChooser(s,"Share"); if (!(c instanceof Activity)) ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(ch);
    } catch (Throwable t) { android.util.Log.e("ParadoxDL","share",t); }
  }
}
