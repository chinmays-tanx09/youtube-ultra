package paradox.dl;
import java.io.*; import java.nio.charset.StandardCharsets;
/** Minimal ID3v2.3 tag: title (TIT2), artist (TPE1), album (TALB) and a JPEG/PNG front cover (APIC). Pure Java. */
public final class Id3 {
  public static byte[] tag(String title, String artist, String album, byte[] cover){
    try { ByteArrayOutputStream frames=new ByteArrayOutputStream();
      text(frames,"TIT2",title); text(frames,"TPE1",artist); text(frames,"TALB",album);
      if (cover!=null && cover.length>0 && cover.length<8_000_000){ String mime= isPng(cover)? "image/png": isJpeg(cover)? "image/jpeg": null;
        if (mime!=null){ ByteArrayOutputStream f=new ByteArrayOutputStream(); f.write(0); f.write(mime.getBytes(StandardCharsets.ISO_8859_1)); f.write(0); f.write(3 /*front cover*/); f.write(0 /*empty description*/); f.write(cover); frame(frames,"APIC",f.toByteArray()); } }
      byte[] body=frames.toByteArray(); ByteArrayOutputStream o=new ByteArrayOutputStream(10+body.length);
      o.write(new byte[]{'I','D','3',3,0,0}); int n=body.length; o.write((n>>21)&0x7f); o.write((n>>14)&0x7f); o.write((n>>7)&0x7f); o.write(n&0x7f); o.write(body); return o.toByteArray();
    } catch (IOException e){ return new byte[0]; } }
  static boolean isJpeg(byte[] b){ return b.length>3 && (b[0]&0xff)==0xFF && (b[1]&0xff)==0xD8; }
  static boolean isPng(byte[] b){ return b.length>8 && (b[0]&0xff)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G'; }
  static void text(ByteArrayOutputStream o, String id, String v) throws IOException {
    if (v==null || v.trim().isEmpty()) return; boolean latin=true; for (int i=0;i<v.length();i++) if (v.charAt(i)>0xff) { latin=false; break; }
    ByteArrayOutputStream f=new ByteArrayOutputStream();
    if (latin){ f.write(0); f.write(v.getBytes(StandardCharsets.ISO_8859_1)); }
    else { f.write(1); f.write(0xFF); f.write(0xFE); f.write(v.getBytes(StandardCharsets.UTF_16LE)); }   // UTF-16 with BOM
    frame(o,id,f.toByteArray()); }
  static void frame(ByteArrayOutputStream o, String id, byte[] data) throws IOException {
    o.write(id.getBytes(StandardCharsets.ISO_8859_1)); int n=data.length; o.write(n>>>24); o.write((n>>16)&0xff); o.write((n>>8)&0xff); o.write(n&0xff); o.write(0); o.write(0); o.write(data); }
}
