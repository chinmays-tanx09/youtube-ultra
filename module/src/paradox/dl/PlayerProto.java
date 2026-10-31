package paradox.dl;
import java.util.*;
/** Reads the parts of a YouTube player response (protobuf, as returned by the app's own innertube
 *  /player call) that a download needs: playability, video details and the stream formats.
 *  Field numbers follow the app's PlayerResponse message. Pure Java, no Android, so it is unit-tested. */
public final class PlayerProto {
  public static final int OK=0, ERROR=1, UNPLAYABLE=2, LOGIN_REQUIRED=3;
  public static final class Fmt {
    public int itag, bitrate, width, height, fps, averageBitrate, channels; public long contentLength, approxDurationMs, sampleRate;
    public String url="", mimeType="", qualityLabel="", signatureCipher=""; public boolean adaptive;
    /** "video/mp4; codecs=\"avc1.640028\"" -> "avc1.640028" */
    public String codec(){ int i=mimeType.indexOf("codecs=\""); if (i<0) return null; int j=mimeType.indexOf('"', i+8); return j<0? null: mimeType.substring(i+8, j); }
    public boolean hasUrl(){ return url.startsWith("https://"); }
  }
  public static final class Thumb { public String url=""; public int width, height; }
  public static final class Player {
    public int status=-1; public String reason="", videoId="", title="", author="", channelId=""; public long lengthSeconds;
    public final List<Fmt> formats=new ArrayList<>(); public final List<Thumb> thumbs=new ArrayList<>();
    /** OK, or no status at all but formats present (the app only hands over playable responses). */
    public boolean playable(){ return status==OK || (status==-1 && !formats.isEmpty()); }
  }

  public static Player parse(byte[] b){ if (b==null) return null; try { Player p=new Player(); R r=new R(b,0,b.length);
      while (r.more()) { int tag=r.varint32(); int f=tag>>>3, w=tag&7;
        if (w==2 && f==2) playability(r.sub(), p); else if (w==2 && f==4) streaming(r.sub(), p); else if (w==2 && f==11) details(r.sub(), p); else r.skip(w); }
      return p; } catch (RuntimeException e){ return null; } }
  static void playability(R r, Player p){ p.status=OK; while (r.more()) { int tag=r.varint32(); int f=tag>>>3, w=tag&7;
      if (f==1 && w==0) p.status=(int)r.varint(); else if (f==2 && w==2) p.reason=r.str(); else r.skip(w); } }
  static void streaming(R r, Player p){ while (r.more()) { int tag=r.varint32(); int f=tag>>>3, w=tag&7;
      if (w==2 && (f==2 || f==3)) { Fmt x=format(r.sub()); x.adaptive= f==3; p.formats.add(x); } else r.skip(w); } }
  static Fmt format(R r){ Fmt x=new Fmt(); while (r.more()) { int tag=r.varint32(); int f=tag>>>3, w=tag&7;
      if (w==0) { long v=r.varint(); switch (f) { case 1: x.itag=(int)v; break; case 6: x.bitrate=(int)v; break; case 7: x.width=(int)v; break; case 8: x.height=(int)v; break;
          case 12: x.contentLength=v; break; case 25: x.fps=(int)v; break; case 31: x.averageBitrate=(int)v; break; case 44: x.approxDurationMs=v; break; case 45: x.sampleRate=v; break; case 46: x.channels=(int)v; break; default: } }
      else if (w==2) { switch (f) { case 2: x.url=r.str(); break; case 5: x.mimeType=r.str(); break; case 26: x.qualityLabel=r.str(); break; case 48: x.signatureCipher=r.str(); break; default: r.skip(w); } }
      else r.skip(w); }
    return x; }
  static void details(R r, Player p){ while (r.more()) { int tag=r.varint32(); int f=tag>>>3, w=tag&7;
      if (w==2) { switch (f) { case 1: p.videoId=r.str(); break; case 15: p.title=r.str(); break; case 19: p.channelId=r.str(); break; case 33: p.author=r.str(); break; case 25: thumbs(r.sub(), p); break; default: r.skip(w); } }
      else if (w==0 && f==16) p.lengthSeconds=r.varint(); else r.skip(w); } }
  static void thumbs(R r, Player p){ while (r.more()) { int tag=r.varint32(); if ((tag>>>3)==1 && (tag&7)==2) { R t=r.sub(); Thumb th=new Thumb();
        while (t.more()) { int g=t.varint32(); int f=g>>>3, w=g&7; if (f==1 && w==2) th.url=t.str(); else if (f==2 && w==0) th.width=(int)t.varint(); else if (f==3 && w==0) th.height=(int)t.varint(); else t.skip(w); }
        if (!th.url.isEmpty()) p.thumbs.add(th); } else r.skip(tag&7); } }

  /** Minimal protobuf wire-format reader over a byte range. */
  static final class R { final byte[] b; int pos; final int end;
    R(byte[] b, int pos, int end){ this.b=b; this.pos=pos; this.end=end; }
    boolean more(){ return pos<end; }
    long varint(){ long v=0; for (int s=0; s<64; s+=7) { if (pos>=end) throw new IllegalStateException("truncated"); int c=b[pos++]&0xff; v|=(long)(c&0x7f)<<s; if (c<0x80) return v; } throw new IllegalStateException("bad varint"); }
    int varint32(){ return (int)varint(); }
    int len(){ long n=varint(); if (n<0 || n>end-pos) throw new IllegalStateException("bad length"); return (int)n; }
    R sub(){ int n=len(); R r=new R(b,pos,pos+n); pos+=n; return r; }
    String str(){ int n=len(); String s=new String(b,pos,n,java.nio.charset.StandardCharsets.UTF_8); pos+=n; return s; }
    void skip(int w){ switch (w) { case 0: varint(); break; case 1: adv(8); break; case 2: adv(len()); break; case 5: adv(4); break; default: throw new IllegalStateException("wire type "+w); } }
    void adv(int n){ if (n>end-pos) throw new IllegalStateException("truncated"); pos+=n; }
  }
}
