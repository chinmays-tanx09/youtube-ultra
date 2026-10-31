package paradox.dl;
import android.content.*; import android.database.*; import android.net.Uri; import android.os.ParcelFileDescriptor; import android.provider.OpenableColumns; import java.io.*;
/** Minimal read-only FileProvider (not exported; access only through a FLAG_GRANT_READ_URI_PERMISSION grant) for downloads that have no MediaStore entry. */
public class FileShare extends ContentProvider {
  static String auth(Context c){ return c.getPackageName()+".ytpm.files"; }
  public static Uri uri(Context c, File f){ return new Uri.Builder().scheme("content").authority(auth(c)).path("f"+f.getAbsolutePath()).build(); }
  static File file(Uri u){ String p=u.getPath(); if (p==null || !p.startsWith("/f/")) return null; File f=new File(p.substring(2)); return f.isFile()? f: null; }
  @Override public boolean onCreate(){ return true; }
  @Override public String getType(Uri u){ File f=file(u); return f==null? null: Open.mimeFor(f.getName(), f.getPath().contains("/Music/")); }
  @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    File f=file(u); if (f==null) throw new FileNotFoundException(String.valueOf(u)); return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); }
  @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort){
    File f=file(u); if (f==null) return null;
    String[] cols= proj==null? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}: proj;
    MatrixCursor mc=new MatrixCursor(cols); Object[] row=new Object[cols.length];
    for (int i=0;i<cols.length;i++) row[i]= OpenableColumns.DISPLAY_NAME.equals(cols[i])? f.getName(): OpenableColumns.SIZE.equals(cols[i])? (Object)f.length(): null;
    mc.addRow(row); return mc; }
  @Override public Uri insert(Uri u, ContentValues v){ throw new UnsupportedOperationException(); }
  @Override public int delete(Uri u, String s, String[] a){ return 0; }
  @Override public int update(Uri u, ContentValues v, String s, String[] a){ return 0; }
}
