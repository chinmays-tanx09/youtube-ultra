package paradox.dl;
import java.nio.charset.StandardCharsets;
/** Output file names and MIME types: "<title>.mp4" for video, "<title>.mp3" for audio. Pure Java (unit-tested on a plain JVM). */
public final class Names {
  /** Longest name we write, in UTF-8 bytes, extension included (Android file systems allow 255). */
  static final int MAX_BYTES=200;
  public static final String MP4="mp4", MP3="mp3";
  public static String mime(String ext){ return MP3.equals(ext)? "audio/mpeg": "video/mp4"; }
  /** Characters Android/FAT/Windows refuse in names, control characters and invisible format characters become "_" / are dropped. */
  public static String clean(String title, String fallback){
    String s= title==null? "": title;
    StringBuilder b=new StringBuilder(s.length());
    for (int i=0;i<s.length();){ int cp=s.codePointAt(i); i+=Character.charCount(cp);
      if (cp<0x20 || cp==0x7f || (cp>=0x80 && cp<0xa0)) { b.append(' '); continue; }
      if ("\\/:*?\"<>|".indexOf(cp)>=0) { b.append('_'); continue; }
      int t=Character.getType(cp); if (t==Character.FORMAT && cp!=0x200d) continue; // bidi marks, zero-width spaces (keep ZWJ for emoji)
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { b.append(' '); continue; }
      b.appendCodePoint(cp); }
    String r=b.toString().replaceAll(" {2,}"," ").trim();
    r=r.replaceAll("^[. ]+","").replaceAll("[. ]+$","");   // no hidden (".name") or trailing-dot names
    if (r.isEmpty()) r=fallback;
    return r; }
  /** "<clean title>.<ext>", cut on a character boundary so the whole name stays within MAX_BYTES UTF-8 bytes. */
  public static String file(String title, String ext){
    String base=clean(title, MP3.equals(ext)? "audio": "video"); String suffix="."+ext;
    int budget=MAX_BYTES-suffix.getBytes(StandardCharsets.UTF_8).length;
    if (base.getBytes(StandardCharsets.UTF_8).length>budget){ StringBuilder b=new StringBuilder(); int used=0;
      for (int i=0;i<base.length();){ int cp=base.codePointAt(i); int n=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length; if (used+n>budget) break; b.appendCodePoint(cp); used+=n; i+=Character.charCount(cp); }
      base=b.toString().replaceAll("[. ]+$",""); if (base.isEmpty()) base= MP3.equals(ext)? "audio": "video"; }
    return base+suffix; }
  /** Legacy (Android 9 and older) collision rule: "name (1).mp4", "name (2).mp4"… */
  public static String numbered(String name, int n){ int dot=name.lastIndexOf('.'); return dot>0? name.substring(0,dot)+" ("+n+")"+name.substring(dot): name+" ("+n+")"; }
}
