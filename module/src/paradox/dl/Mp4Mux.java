package paradox.dl;
import java.io.*; import java.util.*;
/** Our own MP4 muxer (v10.11). Pure Java, no Android, so it works on every Android version and is tested on a desktop JVM.
 *  It copies already-compressed samples (no re-encode) from YouTube's DASH files into one progressive MP4 with the index first:
 *    video: VP9 or AV1 from WebM (vp09 + vpcC / av01 + av1C written here), or AV1 / H.264 from (fragmented) MP4 (sample entry copied as is)
 *    audio: AAC from (fragmented) M4A (sample entry copied as is)
 *  Two passes: 1) read both files' indexes (headers only, sample data is skipped), 2) write ftyp + moov, then one mdat copied in
 *  ~1 s interleaved chunks. Output is purely sequential, so any OutputStream works (a MediaStore descriptor, a file).
 *  Files past 4 GB use co64 and a 64-bit mdat size. */
public final class Mp4Mux {
  public interface Progress { void at(long done, long total); boolean cancelled(); }
  public static final class Failed extends IOException { public Failed(String m){ super(m); } public Failed(String m, Throwable t){ super(m, t); } }

  /** One track's sample index. Times are in the track's own timescale; dts starts at 0 (start offset kept in startTicks). */
  public static final class Track {
    public boolean video; public String codec="";   // vp09, av01, avc1, mp4a, ...
    public int timescale=1000, width, height; public byte[] entry; public long mediaTime=-1;   // mediaTime: source edit list media_time
    public long startTicks; public long lastDur; public int n; public boolean allKey=true, hasCto, negCto;
    long[] off=new long[1024], dts=new long[1024]; int[] size=new int[1024], cto; byte[] key=new byte[128];
    public File file; public int bitDepth=8; public boolean hdr;
    void add(long o, int s, long d, int c, boolean k){
      if (n==off.length){ int m=n*2; off=Arrays.copyOf(off,m); dts=Arrays.copyOf(dts,m); size=Arrays.copyOf(size,m); if (cto!=null) cto=Arrays.copyOf(cto,m); key=Arrays.copyOf(key,(m+7)/8); }
      off[n]=o; size[n]=s; dts[n]=d; if (c!=0){ if (cto==null) cto=new int[off.length]; hasCto=true; if (c<0) negCto=true; } if (cto!=null) cto[n]=c;
      if (k) key[n>>3]|=(byte)(1<<(n&7)); else allKey=false; n++; }
    public boolean isKey(int i){ return (key[i>>3]&(1<<(i&7)))!=0; }
    public long sampleOffset(int i){ return off[i]; } public int sampleSize(int i){ return size[i]; } public long sampleDts(int i){ return dts[i]; }
    int ctoOf(int i){ return cto==null? 0: cto[i]; }
    long dur(int i){ return i+1<n? dts[i+1]-dts[i]: lastDur; }
    /** Duration in this track's timescale. */
    public long duration(){ return n==0? 0: dts[n-1]-dts[0]+lastDur; }
    public double seconds(){ return duration()/(double)timescale; }
    public long bytes(){ long t=0; for (int i=0;i<n;i++) t+=size[i]; return t; }
    void normalize(){ if (n==0) return; long b=dts[0]; if (b!=0){ for (int i=0;i<n;i++) dts[i]-=b; } startTicks+=b; if (lastDur<=0) lastDur= n>1? Math.max(1,(dts[n-1]-dts[0])/(n-1)): timescale/30; }
  }

  // ---------------------------------------------------------------- reading
  /** Buffered random-access reader. */
  static final class Rd implements Closeable {
    final RandomAccessFile f; final long len; final byte[] buf=new byte[1<<16]; long bufPos=-1; int bufLen; long pos;
    Rd(File file) throws IOException { f=new RandomAccessFile(file,"r"); len=f.length(); }
    int u8() throws IOException { if (bufPos<0 || pos<bufPos || pos>=bufPos+bufLen){ if (pos>=len) throw new EOFException("unexpected end of file at "+pos); f.seek(pos); bufPos=pos; bufLen=f.read(buf,0,buf.length); if (bufLen<=0){ bufPos=-1; throw new EOFException("read failed at "+pos); } }
      int v=buf[(int)(pos-bufPos)]&0xff; pos++; return v; }
    long u(int bytes) throws IOException { long v=0; for (int i=0;i<bytes;i++) v=(v<<8)|u8(); return v; }
    int u16() throws IOException { return (int)u(2); } long u32() throws IOException { return u(4); } long u64() throws IOException { return u(8); }
    String four() throws IOException { char[] c=new char[4]; for (int i=0;i<4;i++) c[i]=(char)u8(); return new String(c); }
    byte[] bytes(int n) throws IOException { byte[] b=new byte[n]; for (int i=0;i<n;i++) b[i]=(byte)u8(); return b; }
    public void close() throws IOException { f.close(); }
  }

  /** Reads one file: WebM/Matroska (EBML magic) or MP4 (anything else with boxes). wantVideo picks the track. */
  public static Track read(File file, boolean wantVideo) throws IOException {
    Rd r=new Rd(file); try { if (r.len<8) throw new Failed("the downloaded "+(wantVideo?"video":"audio")+" file is empty");
      long magic=r.u32(); r.pos=0; Track t= magic==0x1A45DFA3L? Webm.read(r, wantVideo): Mp4In.read(r, wantVideo);
      if (t==null || t.n==0) throw new Failed("no "+(wantVideo?"video":"audio")+" samples in the download");
      t.file=file; t.normalize(); return t; }
    finally { r.close(); } }

  /** WebM / Matroska demuxer (index only). */
  static final class Webm {
    static final long EBML=0x1A45DFA3L, SEGMENT=0x18538067L, INFO=0x1549A966L, TRACKS=0x1654AE6BL, CLUSTER=0x1F43B675L, CUES=0x1C53BB6BL, TAGS=0x1254C367L, SEEKHEAD=0x114D9B74L, CHAPTERS=0x1043A770L, ATTACH=0x1941A469L;
    static long id(Rd r) throws IOException { int b=r.u8(); int len= b>=0x80?1: b>=0x40?2: b>=0x20?3: b>=0x10?4: 0; if (len==0) throw new Failed("bad WebM element id at "+(r.pos-1)); long v=b; for (int i=1;i<len;i++) v=(v<<8)|r.u8(); return v; }
    /** Element size; -1 = unknown. */
    static long size(Rd r) throws IOException { int b=r.u8(); int len=1; int mask=0x80; while (len<=8 && (b&mask)==0){ len++; mask>>=1; } if (len>8) throw new Failed("bad WebM size at "+(r.pos-1));
      long v=b&(mask-1); boolean ones= v==mask-1; for (int i=1;i<len;i++){ int c=r.u8(); v=(v<<8)|c; if (c!=0xff) ones=false; } return ones? -1: v; }
    static long uint(Rd r, long n) throws IOException { return r.u((int)n); }
    static double flt(Rd r, long n) throws IOException { if (n==4) return Float.intBitsToFloat((int)r.u32()); if (n==8) return Double.longBitsToDouble(r.u64()); r.pos+=n; return 0; }
    static boolean topLevel(long id){ return id==CLUSTER || id==CUES || id==TAGS || id==INFO || id==TRACKS || id==SEEKHEAD || id==CHAPTERS || id==ATTACH; }
    static final class T { long num=-1, type; String codecId=""; byte[] priv; long defDur; int w, h; Color col=new Color(); }
    static Track read(Rd r, boolean wantVideo) throws IOException {
      long scale=1_000_000; List<T> tracks=new ArrayList<>(); T sel=null; Track out=new Track(); out.video=wantVideo;
      long[] ns=new long[1024]; int[] cnt={0};
      // EBML header
      if (id(r)!=EBML) throw new Failed("not a WebM file"); long hs=size(r); r.pos+=hs;
      while (r.pos<r.len) { long sid=id(r); long ss=size(r); if (sid!=SEGMENT){ if (ss<0) break; r.pos+=ss; continue; }
        long end= ss<0? r.len: Math.min(r.len, r.pos+ss);
        while (r.pos<end) { long eid; long es; long start=r.pos;
          try { eid=id(r); es=size(r); } catch (EOFException e){ break; }
          long body=r.pos; long eend= es<0? -1: body+es;
          if (eid==INFO){ while (r.pos<eend){ long c=id(r), cs=size(r); if (c==0x2AD7B1) scale=uint(r,cs); else r.pos+=cs; } }
          else if (eid==TRACKS){ while (r.pos<eend){ long c=id(r), cs=size(r); if (c==0xAE) tracks.add(track(r, r.pos+cs)); else r.pos+=cs; }
            for (T t: tracks) if ((wantVideo && t.type==1) || (!wantVideo && t.type==2)) { sel=t; break; }
            if (sel==null) throw new Failed("no "+(wantVideo?"video":"audio")+" track in the WebM file");
            setup(out, sel); }
          else if (eid==CLUSTER){ if (sel==null) throw new Failed("WebM cluster before track info");
            ns=cluster(r, eend<0? end: eend, eend<0, sel.num, out, ns, cnt, scale); }
          else { if (es<0) throw new Failed("WebM element of unknown size"); r.pos=eend; }
          if (eend>=0 && eid!=CLUSTER) r.pos=eend; }
        break; }
      if (sel==null) throw new Failed("no tracks in the WebM file");
      times(out, ns, out.n, sel.defDur);
      if (out.video && out.codec.equals("vp09")) out.entry=vp09Entry(r, out, sel);
      return out; }
    static T track(Rd r, long end) throws IOException { T t=new T();
      while (r.pos<end){ long c=id(r), cs=size(r); long b=r.pos;
        if (c==0xD7) t.num=uint(r,cs); else if (c==0x83) t.type=uint(r,cs); else if (c==0x86) t.codecId=new String(r.bytes((int)cs),"ISO-8859-1").trim();
        else if (c==0x63A2) t.priv=r.bytes((int)cs); else if (c==0x23E383) t.defDur=uint(r,cs);
        else if (c==0xE0){ while (r.pos<b+cs){ long v=id(r), vs=size(r); long vb=r.pos;
            if (v==0xB0) t.w=(int)uint(r,vs); else if (v==0xBA) t.h=(int)uint(r,vs); else if (v==0x55B0) colour(r, vb+vs, t.col); r.pos=vb+vs; } }
        r.pos=b+cs; }
      return t; }
    static void colour(Rd r, long end, Color c) throws IOException { while (r.pos<end){ long v=id(r), s=size(r); long b=r.pos;
        switch ((int)v){ case 0x55B1: c.matrix=(int)uint(r,s); break; case 0x55B2: c.bits=(int)uint(r,s); break; case 0x55B3: c.subH=(int)uint(r,s); break; case 0x55B4: c.subV=(int)uint(r,s); break;
          case 0x55B9: c.range=(int)uint(r,s); break; case 0x55BA: c.transfer=(int)uint(r,s); break; case 0x55BB: c.primaries=(int)uint(r,s); break; case 0x55BC: c.maxCll=(int)uint(r,s); break; case 0x55BD: c.maxFall=(int)uint(r,s); break;
          case 0x55D0: { c.mastering=true; while (r.pos<b+s){ long m=id(r), ms=size(r); long mb=r.pos; double d=flt(r,ms); int k=(int)(m-0x55D1); if (k>=0 && k<10) c.md[k]=d; r.pos=mb+ms; } break; }
          default: }
        r.pos=b+s; } }
    static void setup(Track out, T t) throws IOException {
      String c=t.codecId; out.width=t.w; out.height=t.h;
      if (c.equals("V_VP9")) out.codec="vp09";
      else if (c.equals("V_AV1")) { out.codec="av01"; if (t.priv==null || t.priv.length<4) throw new Failed("AV1 in WebM without its codec header"); out.entry=visualEntry("av01", t.w, t.h, box("av1C", t.priv), t.col.nclx()); }
      else if (out.video) throw new Failed("this video codec ("+c+") can't go into the MP4");
      else throw new Failed("this audio codec ("+c+") can't go into the MP4");
      out.timescale=1000; }
    static long[] cluster(Rd r, long end, boolean unknown, long trackNum, Track out, long[] ns, int[] cnt, long scale) throws IOException {
      long tc=0;
      while (r.pos<end){ long start=r.pos; long c; long cs;
        try { c=id(r); } catch (EOFException e){ break; }
        if (unknown && topLevel(c)){ r.pos=start; break; }
        cs=size(r); long b=r.pos; if (cs<0) throw new Failed("WebM block of unknown size");
        if (c==0xE7) tc=uint(r,cs);
        else if (c==0xA3) ns=block(r, b, cs, true, false, trackNum, tc, out, ns, cnt, scale);
        else if (c==0xA0){ long bOff=-1, bSize=0; boolean ref=false; while (r.pos<b+cs){ long g=id(r), gs=size(r); long gb=r.pos; if (g==0xA1){ bOff=gb; bSize=gs; } else if (g==0xFB) ref=true; r.pos=gb+gs; }
          if (bOff>=0){ long save=b+cs; r.pos=bOff; ns=block(r, bOff, bSize, false, !ref, trackNum, tc, out, ns, cnt, scale); r.pos=save; } }
        r.pos=b+cs; }
      return ns; }
    static long[] block(Rd r, long b, long size, boolean simple, boolean keyFromGroup, long trackNum, long tc, Track out, long[] ns, int[] cnt, long scale) throws IOException {
      r.pos=b; long tn=size(r); int rel=(short)r.u16(); int flags=r.u8(); long data=r.pos; long dlen=b+size-data;
      if (tn!=trackNum) return ns;
      if (((flags>>1)&3)!=0) throw new Failed("laced WebM blocks aren't supported for video");
      if (dlen<=0 || dlen>Integer.MAX_VALUE) throw new Failed("bad WebM block size");
      boolean key= simple? (flags&0x80)!=0: keyFromGroup;
      long t=(tc+rel)*scale; if (cnt[0]==ns.length) ns=Arrays.copyOf(ns, ns.length*2); ns[cnt[0]++]=t;
      out.add(data, (int)dlen, 0, 0, key); return ns; }
  }

  /** Nanosecond timestamps -> dts in a timescale that fits the frame rate (snapped, so 33/34 ms WebM rounding disappears). */
  static final int[][] RATES={{24000,1001},{24,1},{25,1},{30000,1001},{30,1},{48,1},{50,1},{60000,1001},{60,1},{90,1},{100,1},{120,1},{144,1},{240,1}};
  static void times(Track t, long[] ns, int n, long defDurNs){
    if (n==0) return; for (int i=1;i<n;i++) if (ns[i]<=ns[i-1]) ns[i]=ns[i-1]+1;   // keep strictly increasing
    long base=ns[0]; double fps=0;
    if (defDurNs>0) fps=1e9/defDurNs; else if (n>2) fps=(n-1)*1e9/(ns[n-1]-base);
    int[] rate=null; double bestErr=1;
    if (t.video && fps>0) for (int[] c: RATES){ double f=c[0]/(double)c[1]; double e=Math.abs(f-fps)/f; if (e<0.004 && e<bestErr){ bestErr=e; rate=c; } }
    if (rate!=null){ int ts= rate[1]==1001? rate[0]: rate[0]*1000, tpf= rate[1]==1001? 1001: 1000; t.timescale=ts; long prev=-1; double tol=ts*0.0015;
      for (int i=0;i<n;i++){ double raw=(ns[i]-base)*(double)ts/1e9; long sn=Math.round(raw/tpf)*tpf; long v= Math.abs(sn-raw)<=tol? sn: Math.round(raw); if (v<=prev) v=prev+1; t.dts[i]=v; prev=v; }
      t.lastDur=tpf; }
    else { int ts= t.video? 90000: 48000; t.timescale=ts; long prev=-1;
      for (int i=0;i<n;i++){ long v=Math.round((ns[i]-base)*(double)ts/1e9); if (v<=prev) v=prev+1; t.dts[i]=v; prev=v; }
      long[] d=new long[Math.max(1,n-1)]; for (int i=0;i+1<n;i++) d[i]=t.dts[i+1]-t.dts[i]; Arrays.sort(d); t.lastDur= n>1? Math.max(1,d[d.length/2]): (defDurNs>0? Math.max(1,Math.round(defDurNs*(double)ts/1e9)): ts/30); }
    t.startTicks=Math.round(base*(double)t.timescale/1e9); }

  /** Colour description (Matroska Colour element / ISO 23091-2 code points). */
  public static final class Color { public int matrix=2, bits, subH, subV, range, transfer=2, primaries=2, maxCll, maxFall; public boolean mastering; public final double[] md=new double[10];
    boolean known(){ return matrix!=2 || transfer!=2 || primaries!=2; }
    /** colr box (nclx) or null when nothing is known. */
    byte[] nclx(){ if (!known()) return null; B b=new B(); int at=b.open("colr"); b.four("nclx"); b.u16(primaries); b.u16(transfer); b.u16(matrix); b.u8(range==2? 0x80: 0); b.close(at); return b.toArray(); }
    public boolean hdr(){ return transfer==16 || transfer==18; } }

  /** VP9: profile / bit depth / subsampling / colour come from the first key frame's uncompressed header; the Matroska Colour element wins where present. */
  static byte[] vp09Entry(Rd r, Track t, Webm.T src) throws IOException {
    int[] h=null; for (int i=0;i<t.n && i<64 && h==null;i++){ if (!t.isKey(i)) continue; r.pos=t.off[i]; h=vp9Header(r.bytes(Math.min(t.size[i], 32))); }
    double fps= t.lastDur>0? t.timescale/(double)t.lastDur: 30;
    return vp09(t, h, src.col, fps); }
  static int[] vp9Header(byte[] d){   // {profile, bitDepth, colorSpace, colorRange, ssx, ssy} or null
    try { Bits b=new Bits(d); if (b.r(2)!=2) return null; int lo=b.r(1), hi=b.r(1); int profile=(hi<<1)|lo; if (profile==3) b.r(1);
      if (b.r(1)==1) return null;                       // show_existing_frame
      int frameType=b.r(1); b.r(1); b.r(1); if (frameType!=0) return null;   // not a key frame
      if (b.r(8)!=0x49 || b.r(8)!=0x83 || b.r(8)!=0x42) return null;
      int bitDepth=8; if (profile>=2) bitDepth= b.r(1)==1? 12: 10;
      int cs=b.r(3), range, ssx=1, ssy=1;
      if (cs!=7){ range=b.r(1); if (profile==1 || profile==3){ ssx=b.r(1); ssy=b.r(1); b.r(1); } }
      else { range=1; if (profile==1 || profile==3){ ssx=0; ssy=0; b.r(1); } }
      return new int[]{profile, bitDepth, cs, range, ssx, ssy}; } catch (RuntimeException e){ return null; } }
  static final class Bits { final byte[] d; int p; Bits(byte[] d){ this.d=d; } int r(int n){ int v=0; for (int i=0;i<n;i++){ int byt=d[p>>3]&0xff; v=(v<<1)|((byt>>(7-(p&7)))&1); p++; } return v; } }
  static final long[][] VP9_LEVELS={{10,829440L,36864},{11,2764800L,73728},{20,4608000L,122880},{21,9216000L,245760},{30,20736000L,552960},{31,36864000L,983040},{40,83558400L,2228224},{41,160432128L,2228224},{50,311951360L,8912896},{51,588251136L,8912896},{52,1176502272L,8912896},{60,1176502272L,35651584},{61,2353004544L,35651584},{62,4706009088L,35651584}};
  static int vp9Level(int w, int h, double fps){ long pic=(long)w*h; long rate=(long)(pic*Math.max(1,fps)); for (long[] l: VP9_LEVELS) if (pic<=l[2] && rate<=l[1]) return (int)l[0]; return 62; }

  static int indexOf(byte[] d, String four){ outer: for (int i=0;i+4<=d.length;i++){ for (int k=0;k<4;k++) if (d[i+k]!=(byte)four.charAt(k)) continue outer; return i; } return -1; }
  // ---------------------------------------------------------------- MP4 input
  static final class Mp4In {
    static final class Tk { long id; String handler=""; int timescale; byte[] entry; int w, h; long mediaTime=-1; long trexDur, trexSize, trexFlags;
      long[] stts, ctts, stsz, stsc, stco, stss; long stszFixed; boolean cttsV1; }
    static Track read(Rd r, boolean wantVideo) throws IOException {
      Map<Long,Tk> tks=new LinkedHashMap<>(); Tk sel=null; Track out=new Track(); out.video=wantVideo; long dtsNext=0; boolean frag=false;
      while (r.pos+8<=r.len){ long start=r.pos; long size=r.u32(); String type=r.four(); if (size==1) size=r.u64(); else if (size==0) size=r.len-start; long end=start+size;
        if (size<8 || end>r.len+0) { if (type.equals("mdat")) break; throw new Failed("broken MP4 box "+type); }
        if (type.equals("moov")) { moov(r, end, tks); for (Tk t: tks.values()) if (t.handler.equals(wantVideo?"vide":"soun")) { sel=t; break; }
          if (sel==null) throw new Failed("no "+(wantVideo?"video":"audio")+" track in the MP4 file");
          out.timescale=sel.timescale; out.entry=sel.entry; out.width=sel.w; out.height=sel.h; out.mediaTime=sel.mediaTime; out.codec=sel.entry.length>=8? new String(sel.entry,4,4,"ISO-8859-1"): "";
          if (out.codec.equals("av01")) { int i=indexOf(sel.entry, "av1C"); if (i>0 && i+7<sel.entry.length){ int b=sel.entry[i+6]&0xff; out.bitDepth= (b&0x40)!=0? ((b&0x20)!=0? 12: 10): 8; } }
          if (sel.stsz!=null && (sel.stsz.length>0 || sel.stszFixed>0)) table(sel, out); }
        else if (type.equals("moof") && sel!=null) { frag=true; dtsNext=moof(r, start, end, sel, out, dtsNext); }
        r.pos=end; }
      if (sel==null) throw new Failed("no movie header in the MP4 file");
      if (out.n>0 && out.lastDur<=0) out.lastDur= out.n>1? Math.max(1,(out.dts[out.n-1]-out.dts[0])/(out.n-1)): 1024;
      return out; }
    static void moov(Rd r, long end, Map<Long,Tk> tks) throws IOException {
      while (r.pos+8<=end){ long s=r.pos, sz=r.u32(); String t=r.four(); if (sz==1) sz=r.u64(); long e=s+sz;
        if (t.equals("trak")) { Tk k=new Tk(); trak(r, e, k); tks.put(k.id, k); }
        else if (t.equals("mvex")) { while (r.pos+8<=e){ long s2=r.pos, z2=r.u32(); String t2=r.four(); if (t2.equals("trex")){ r.u32(); long id=r.u32(); r.u32(); long d=r.u32(), z=r.u32(), f=r.u32(); Tk k=tks.get(id); if (k!=null){ k.trexDur=d; k.trexSize=z; k.trexFlags=f; } } r.pos=s2+z2; } }
        r.pos=e; } }
    static void trak(Rd r, long end, Tk k) throws IOException {
      while (r.pos+8<=end){ long s=r.pos, sz=r.u32(); String t=r.four(); if (sz==1) sz=r.u64(); long e=s+sz;
        switch (t){
          case "mdia": case "minf": case "stbl": case "edts": trak(r, e, k); break;
          case "tkhd": { int v=r.u8(); r.u(3); r.pos+= v==1? 16: 8; k.id=r.u32(); break; }
          case "mdhd": { int v=r.u8(); r.u(3); r.pos+= v==1? 16: 8; k.timescale=(int)r.u32(); break; }
          case "hdlr": { r.u32(); r.u32(); k.handler=r.four(); break; }
          case "elst": { int v=r.u8(); r.u(3); long n=r.u32(); for (long i=0;i<n;i++){ long dur= v==1? r.u64(): r.u32(); long mt= v==1? r.u64(): (long)(int)r.u32(); r.u32(); if (mt>=0 && k.mediaTime<0) k.mediaTime=mt; } break; }
          case "stsd": { r.u32(); r.u32(); long es=r.pos; long esz=r.u32(); r.pos=es; k.entry=r.bytes((int)esz);
            if (k.entry.length>=36 && k.handler.equals("vide")) { k.w=((k.entry[32]&0xff)<<8)|(k.entry[33]&0xff); k.h=((k.entry[34]&0xff)<<8)|(k.entry[35]&0xff); } break; }
          case "stts": { r.u32(); int n=(int)r.u32(); k.stts=new long[n*2]; for (int i=0;i<n*2;i++) k.stts[i]=r.u32(); break; }
          case "ctts": { int v=r.u8(); r.u(3); k.cttsV1= v==1; int n=(int)r.u32(); k.ctts=new long[n*2]; for (int i=0;i<n;i++){ k.ctts[2*i]=r.u32(); long o=r.u32(); k.ctts[2*i+1]= (long)(int)o; } break; }
          case "stsz": { r.u32(); k.stszFixed=r.u32(); int n=(int)r.u32(); k.stsz=new long[k.stszFixed==0? n: 0]; if (k.stszFixed!=0) k.stsz=new long[]{-n}; else for (int i=0;i<n;i++) k.stsz[i]=r.u32(); break; }
          case "stsc": { r.u32(); int n=(int)r.u32(); k.stsc=new long[n*3]; for (int i=0;i<n*3;i++) k.stsc[i]=r.u32(); break; }
          case "stco": { r.u32(); int n=(int)r.u32(); k.stco=new long[n]; for (int i=0;i<n;i++) k.stco[i]=r.u32(); break; }
          case "co64": { r.u32(); int n=(int)r.u32(); k.stco=new long[n]; for (int i=0;i<n;i++) k.stco[i]=r.u64(); break; }
          case "stss": { r.u32(); int n=(int)r.u32(); k.stss=new long[n]; for (int i=0;i<n;i++) k.stss[i]=r.u32(); break; }
          default: }
        r.pos=e; } }
    /** Non-fragmented file: rebuild the sample list from stts / ctts / stsz / stsc / stco / stss. */
    static void table(Tk k, Track out) throws Failed {
      int n; long fixed=k.stszFixed; if (fixed!=0) n=(int)-k.stsz[0]; else n=k.stsz.length; if (n==0) return;
      if (k.stco==null || k.stsc==null || k.stts==null) throw new Failed("MP4 sample tables are incomplete");
      long[] dts=new long[n]; int si=0; long d=0; for (int e=0;e+1<k.stts.length;e+=2) for (long c=0;c<k.stts[e] && si<n;c++){ dts[si++]=d; d+=k.stts[e+1]; }
      long lastDelta= k.stts.length>=2? k.stts[k.stts.length-1]: 1; while (si<n){ dts[si++]=d; d+=lastDelta; }
      int[] cto=new int[n]; if (k.ctts!=null){ si=0; for (int e=0;e+1<k.ctts.length;e+=2) for (long c=0;c<k.ctts[e] && si<n;c++) cto[si++]=(int)k.ctts[e+1]; }
      boolean[] key=new boolean[n]; if (k.stss==null) Arrays.fill(key,true); else for (long x: k.stss) if (x>=1 && x<=n) key[(int)x-1]=true;
      si=0; int entries=k.stsc.length/3;
      for (int e=0;e<entries && si<n;e++){ long first=k.stsc[e*3], spc=k.stsc[e*3+1]; long nextFirst= e+1<entries? k.stsc[(e+1)*3]: k.stco.length+1;
        for (long ch=first; ch<nextFirst && si<n; ch++){ if (ch-1>=k.stco.length) break; long o=k.stco[(int)(ch-1)];
          for (long s=0;s<spc && si<n;s++){ int sz=(int)(fixed!=0? fixed: k.stsz[si]); out.add(o, sz, dts[si], cto[si], key[si]); o+=sz; si++; } } }
      out.lastDur=lastDelta; }
    static long moof(Rd r, long moofStart, long end, Tk k, Track out, long dtsNext) throws IOException {
      while (r.pos+8<=end){ long s=r.pos, sz=r.u32(); String t=r.four(); long e=s+sz;
        if (t.equals("traf")) {
          long base=moofStart, defDur=k.trexDur, defSize=k.trexSize, defFlags=k.trexFlags; boolean mine=false; long dts=dtsNext; long nextData=-1;
          while (r.pos+8<=e){ long s2=r.pos, z2=r.u32(); String t2=r.four(); long e2=s2+z2;
            if (t2.equals("tfhd")) { int fl=(int)(r.u32()&0xffffff); long id=r.u32(); mine= id==k.id;
              if ((fl&1)!=0) base=r.u64(); if ((fl&2)!=0) r.u32(); if ((fl&8)!=0) defDur=r.u32(); if ((fl&0x10)!=0) defSize=r.u32(); if ((fl&0x20)!=0) defFlags=r.u32(); nextData=base; }
            else if (t2.equals("tfdt") && mine) { int v=r.u8(); r.u(3); dts= v==1? r.u64(): r.u32(); }
            else if (t2.equals("trun") && mine) { int v=r.u8(); int fl=(int)r.u(3); long cnt=r.u32(); long data= nextData<0? base: nextData;
              if ((fl&1)!=0) data=base+(long)(int)r.u32(); long firstFlags=-1; if ((fl&4)!=0) firstFlags=r.u32();
              for (long i=0;i<cnt;i++){ long du= (fl&0x100)!=0? r.u32(): defDur; long si= (fl&0x200)!=0? r.u32(): defSize; long sf= (fl&0x400)!=0? r.u32(): (i==0 && firstFlags>=0? firstFlags: defFlags);
                int co=0; if ((fl&0x800)!=0){ long x=r.u32(); co= v==0? (int)Math.min(x, Integer.MAX_VALUE): (int)x; }
                boolean key=((sf>>16)&1)==0; out.add(data, (int)si, dts, co, key); data+=si; dts+=du; if (du>0) out.lastDur=du; }
              nextData=data; }
            r.pos=e2; }
          if (mine) dtsNext=dts; }
        r.pos=e; }
      return dtsNext; }
  }

  // ---------------------------------------------------------------- writing
  static final class B { byte[] a=new byte[4096]; int n;
    void need(int k){ if (n+k>a.length) a=Arrays.copyOf(a, Math.max(a.length*2, n+k)); }
    void u8(int v){ need(1); a[n++]=(byte)v; } void u16(int v){ u8(v>>8); u8(v); } void u24(int v){ u8(v>>16); u16(v); }
    void u32(long v){ need(4); a[n++]=(byte)(v>>24); a[n++]=(byte)(v>>16); a[n++]=(byte)(v>>8); a[n++]=(byte)v; }
    void u64(long v){ u32(v>>>32); u32(v); } void four(String s){ for (int i=0;i<4;i++) u8(s.charAt(i)); }
    void bytes(byte[] b){ if (b==null) return; need(b.length); System.arraycopy(b,0,a,n,b.length); n+=b.length; }
    void zeros(int k){ need(k); n+=k; }
    int open(String type){ int at=n; u32(0); four(type); return at; } int full(String type, int v, int flags){ int at=open(type); u8(v); u24(flags); return at; }
    void close(int at){ int s=n-at; a[at]=(byte)(s>>24); a[at+1]=(byte)(s>>16); a[at+2]=(byte)(s>>8); a[at+3]=(byte)s; }
    byte[] toArray(){ return Arrays.copyOf(a,n); } }
  static byte[] box(String type, byte[] payload){ B b=new B(); int at=b.open(type); b.bytes(payload); b.close(at); return b.toArray(); }
  static byte[] visualEntry(String type, int w, int h, byte[]... children){ B b=new B(); int at=b.open(type);
    b.zeros(6); b.u16(1); b.u16(0); b.u16(0); b.zeros(12); b.u16(w); b.u16(h); b.u32(0x00480000); b.u32(0x00480000); b.u32(0); b.u16(1); b.zeros(32); b.u16(0x18); b.u16(0xffff);
    for (byte[] c: children) b.bytes(c); b.close(at); return b.toArray(); }
  static final int[] CS_TO_MATRIX={2,6,1,6,7,9,2,0};
  /** vp09 sample entry: vpcC (v1) + colr + mdcv/clli for HDR. */
  static byte[] vp09(Track t, int[] hdr, Color col, double fps){
    int profile= hdr!=null? hdr[0]: 0, depth= hdr!=null? hdr[1]: (col.bits>0? col.bits: 8);
    int matrix= col.matrix!=2? col.matrix: (hdr!=null? CS_TO_MATRIX[hdr[2]&7]: 2);
    int fullRange= col.range==2? 1: col.range==1? 0: (hdr!=null? hdr[3]: 0);
    int ss= hdr==null || (hdr[4]==1 && hdr[5]==1)? 1: (hdr[4]==1 && hdr[5]==0)? 2: 3;   // 1 = 4:2:0 (colocated), 2 = 4:2:2, 3 = 4:4:4
    int prim= col.primaries!=2? col.primaries: (matrix==9? 9: matrix==1? 1: 2), trc= col.transfer!=2? col.transfer: (matrix==1? 1: 2);
    B v=new B(); int at=v.full("vpcC",1,0); v.u8(profile); v.u8(vp9Level(t.width,t.height,fps)); v.u8((depth<<4)|(ss<<1)|fullRange); v.u8(prim); v.u8(trc); v.u8(matrix); v.u16(0); v.close(at);
    Color c2=new Color(); c2.primaries=prim; c2.transfer=trc; c2.matrix=matrix; c2.range= fullRange==1? 2: 1;
    List<byte[]> kids=new ArrayList<>(); kids.add(v.toArray()); byte[] colr=c2.nclx(); if (colr!=null) kids.add(colr);
    if (col.mastering){ B m=new B(); int a=m.open("mdcv"); double[] d=col.md; int[][] order={{2,3},{4,5},{0,1}};   // G, B, R
      for (int[] o: order){ m.u16((int)Math.round(d[o[0]]*50000)); m.u16((int)Math.round(d[o[1]]*50000)); } m.u16((int)Math.round(d[6]*50000)); m.u16((int)Math.round(d[7]*50000));
      m.u32(Math.round(d[8]*10000)); m.u32(Math.round(d[9]*10000)); m.close(a); kids.add(m.toArray()); }
    if (col.maxCll>0 || col.maxFall>0){ B l=new B(); int a=l.open("clli"); l.u16(col.maxCll); l.u16(col.maxFall); l.close(a); kids.add(l.toArray()); }
    t.bitDepth=depth; t.hdr= trc==16 || trc==18;
    return visualEntry("vp09", t.width, t.height, kids.toArray(new byte[0][])); }

  static final class Chunk { final int track, first, count; Chunk(int t, int f, int c){ track=t; first=f; count=c; } }

  /** Result facts, for logs and tests. */
  public static final class Result { public long bytes; public double videoSeconds, audioSeconds; public String videoCodec="", audioCodec=""; public int width, height, bitDepth; public boolean hdr; }

  /** Mux video (+ optional audio) into out. Closes nothing; the caller owns out. */
  public static Result mux(String videoPath, String audioPath, OutputStream out, Progress p) throws IOException {
    Track v= videoPath==null? null: read(new File(videoPath), true); Track a= audioPath==null? null: read(new File(audioPath), false);
    List<Track> ts=new ArrayList<>(); if (v!=null) ts.add(v); if (a!=null) ts.add(a); if (ts.isEmpty()) throw new Failed("nothing to save");
    if (a!=null && !a.codec.equals("mp4a")) throw new Failed("only AAC audio goes into this MP4 (got "+a.codec+")");
    return write(ts.toArray(new Track[0]), out, p); }

  public static Result write(Track[] ts, OutputStream out, Progress p) throws IOException {
    for (Track t: ts) if (t.entry==null) throw new Failed("missing codec header for "+t.codec);
    // interleave: ~1 s chunks, earliest first
    List<Chunk> chunks=new ArrayList<>(); int[] next=new int[ts.length];
    while (true){ int pick=-1; double best=Double.MAX_VALUE;
      for (int k=0;k<ts.length;k++) if (next[k]<ts[k].n){ double s=(ts[k].dts[next[k]]+ts[k].startTicks)/(double)ts[k].timescale; if (s<best){ best=s; pick=k; } }
      if (pick<0) break; Track t=ts[pick]; int f=next[pick]; double lim=best+1.0; int c=0;
      while (f+c<t.n && c<4096 && (t.dts[f+c]+t.startTicks)/(double)t.timescale<lim) c++; if (c==0) c=1;
      chunks.add(new Chunk(pick, f, c)); next[pick]+=c; }
    long payload=0; for (Track t: ts) payload+=t.bytes();
    byte[] ftyp=ftyp(ts);
    boolean big= payload+ftyp.length+16+ (long)ts.length*1024*1024 > 0xFFFFFFFFL - 1024;   // conservative: co64 + 64-bit mdat when near 4 GB
    int mdatHdr= payload+8>0xFFFFFFFFL? 16: 8;
    byte[] moov=moov(ts, chunks, 0, big);
    long dataStart=ftyp.length+moov.length+mdatHdr;
    moov=moov(ts, chunks, dataStart, big);
    if (ftyp.length+moov.length+mdatHdr!=dataStart) throw new Failed("internal: index size changed");
    long total=dataStart+payload, done=0;
    out.write(ftyp); out.write(moov);
    B h=new B(); if (mdatHdr==16){ h.u32(1); h.four("mdat"); h.u64(16+payload); } else { h.u32(8+payload); h.four("mdat"); } out.write(h.toArray()); done=dataStart;
    RandomAccessFile[] src=new RandomAccessFile[ts.length]; byte[] buf=new byte[1<<20];
    try { for (int k=0;k<ts.length;k++) src[k]=new RandomAccessFile(ts[k].file,"r");
      long lastReport=0;
      for (Chunk c: chunks){ Track t=ts[c.track]; RandomAccessFile f=src[c.track]; int i=c.first, end=c.first+c.count;
        while (i<end){ long o=t.off[i]; long len=t.size[i]; int j=i+1; while (j<end && t.off[j]==o+len && len<64L<<20){ len+=t.size[j]; j++; }
          f.seek(o); long left=len; while (left>0){ int r=f.read(buf,0,(int)Math.min(buf.length,left)); if (r<0) throw new Failed("the downloaded "+(t.video?"video":"audio")+" file is shorter than its index (download incomplete?)"); out.write(buf,0,r); left-=r; done+=r; }
          i=j; }
        if (p!=null){ if (p.cancelled()) throw new InterruptedIOException("cancelled"); if (done-lastReport>(4<<20)){ p.at(done,total); lastReport=done; } } }
    } finally { for (RandomAccessFile f: src) if (f!=null) try { f.close(); } catch (IOException e){} }
    out.flush(); if (p!=null) p.at(total,total);
    Result res=new Result(); res.bytes=total;
    for (Track t: ts){ if (t.video){ res.videoSeconds=t.seconds(); res.videoCodec=t.codec; res.width=t.width; res.height=t.height; res.bitDepth=t.bitDepth; res.hdr=t.hdr; } else { res.audioSeconds=t.seconds(); res.audioCodec=t.codec; } }
    return res; }

  static byte[] ftyp(Track[] ts){ B b=new B(); int at=b.open("ftyp"); b.four("isom"); b.u32(512); b.four("isom"); b.four("iso2");
    for (Track t: ts) if (t.video){ if (t.codec.equals("avc1")) b.four("avc1"); else if (t.codec.equals("av01")) b.four("av01"); }
    b.four("mp41"); b.close(at); return b.toArray(); }

  static long movieDur(Track t){ return Math.round((t.duration()+t.startTicks)*1000.0/t.timescale); }
  static byte[] moov(Track[] ts, List<Chunk> chunks, long dataStart, boolean co64){
    // chunk offsets per track
    List<List<long[]>> per=new ArrayList<>(); for (int k=0;k<ts.length;k++) per.add(new ArrayList<long[]>());
    long pos=dataStart; for (Chunk c: chunks){ Track t=ts[c.track]; long sz=0; for (int i=c.first;i<c.first+c.count;i++) sz+=t.size[i]; per.get(c.track).add(new long[]{pos, c.count}); pos+=sz; }
    long mdur=0; for (Track t: ts) mdur=Math.max(mdur, movieDur(t));
    B b=new B(); int moov=b.open("moov");
    boolean v1= mdur>0xFFFFFFFFL; int mvhd=b.full("mvhd", v1?1:0, 0); if (v1){ b.u64(0); b.u64(0); b.u32(1000); b.u64(mdur); } else { b.u32(0); b.u32(0); b.u32(1000); b.u32(mdur); }
    b.u32(0x00010000); b.u16(0x0100); b.zeros(10); matrix(b); b.zeros(24); b.u32(ts.length+1); b.close(mvhd);
    for (int k=0;k<ts.length;k++) trak(b, ts[k], k+1, per.get(k), co64);
    b.close(moov); return b.toArray(); }
  static void matrix(B b){ b.u32(0x10000); b.u32(0); b.u32(0); b.u32(0); b.u32(0x10000); b.u32(0); b.u32(0); b.u32(0); b.u32(0x40000000); }
  static void trak(B b, Track t, int id, List<long[]> chunks, boolean co64){
    long md=movieDur(t), dur=t.duration(); int trak=b.open("trak");
    boolean v1= md>0xFFFFFFFFL; int tkhd=b.full("tkhd", v1?1:0, 3);
    if (v1){ b.u64(0); b.u64(0); b.u32(id); b.u32(0); b.u64(md); } else { b.u32(0); b.u32(0); b.u32(id); b.u32(0); b.u32(md); }
    b.zeros(8); b.u16(0); b.u16(t.video? 0: 1); b.u16(t.video? 0: 0x0100); b.u16(0); matrix(b); b.u32(t.video? (long)t.width<<16: 0); b.u32(t.video? (long)t.height<<16: 0); b.close(tkhd);
    long emptyMs=Math.round(t.startTicks*1000.0/t.timescale); boolean shift= t.mediaTime>0;
    if (emptyMs>0 || shift){ int edts=b.open("edts"); int elst=b.full("elst",1,0); b.u32(emptyMs>0? 2: 1);
      if (emptyMs>0){ b.u64(emptyMs); b.u64(-1L); b.u32(0x00010000); }
      long mt= shift? t.mediaTime: 0; long segTicks=Math.max(0, dur-mt); b.u64(Math.round(segTicks*1000.0/t.timescale)); b.u64(mt); b.u32(0x00010000); b.close(elst); b.close(edts); }
    int mdia=b.open("mdia");
    boolean mv1= dur>0xFFFFFFFFL; int mdhd=b.full("mdhd", mv1?1:0, 0); if (mv1){ b.u64(0); b.u64(0); b.u32(t.timescale); b.u64(dur); } else { b.u32(0); b.u32(0); b.u32(t.timescale); b.u32(dur); } b.u16(0x55C4); b.u16(0); b.close(mdhd);
    int hdlr=b.full("hdlr",0,0); b.u32(0); b.four(t.video? "vide": "soun"); b.zeros(12); byte[] nm=(t.video? "VideoHandler": "SoundHandler").getBytes(); b.bytes(nm); b.u8(0); b.close(hdlr);
    int minf=b.open("minf");
    if (t.video){ int vm=b.full("vmhd",0,1); b.zeros(8); b.close(vm); } else { int sm=b.full("smhd",0,0); b.u32(0); b.close(sm); }
    int dinf=b.open("dinf"); int dref=b.full("dref",0,0); b.u32(1); int url=b.full("url ",0,1); b.close(url); b.close(dref); b.close(dinf);
    int stbl=b.open("stbl");
    int stsd=b.full("stsd",0,0); b.u32(1); b.bytes(t.entry); b.close(stsd);
    // stts (run-length of sample durations)
    { B r=new B(); int runs=0; long cur=-1, cnt=0; for (int i=0;i<t.n;i++){ long d=t.dur(i); if (d==cur) cnt++; else { if (cnt>0){ r.u32(cnt); r.u32(cur); runs++; } cur=d; cnt=1; } } if (cnt>0){ r.u32(cnt); r.u32(cur); runs++; }
      int a=b.full("stts",0,0); b.u32(runs); b.bytes(r.toArray()); b.close(a); }
    if (t.hasCto){ B r=new B(); int runs=0; long cur=Long.MIN_VALUE, cnt=0; for (int i=0;i<t.n;i++){ long c=t.ctoOf(i); if (c==cur) cnt++; else { if (cnt>0){ r.u32(cnt); r.u32(cur); runs++; } cur=c; cnt=1; } } if (cnt>0){ r.u32(cnt); r.u32(cur); runs++; }
      int a=b.full("ctts", t.negCto?1:0, 0); b.u32(runs); b.bytes(r.toArray()); b.close(a); }
    if (!t.allKey){ int cnt=0; for (int i=0;i<t.n;i++) if (t.isKey(i)) cnt++; int a=b.full("stss",0,0); b.u32(cnt); for (int i=0;i<t.n;i++) if (t.isKey(i)) b.u32(i+1); b.close(a); }
    { B r=new B(); int runs=0; long last=-1; for (int c=0;c<chunks.size();c++){ long spc=chunks.get(c)[1]; if (spc!=last){ r.u32(c+1); r.u32(spc); r.u32(1); runs++; last=spc; } }
      int a=b.full("stsc",0,0); b.u32(runs); b.bytes(r.toArray()); b.close(a); }
    { boolean same=t.n>0; for (int i=1;i<t.n;i++) if (t.size[i]!=t.size[0]){ same=false; break; }
      int a=b.full("stsz",0,0); if (same){ b.u32(t.size[0]); b.u32(t.n); } else { b.u32(0); b.u32(t.n); for (int i=0;i<t.n;i++) b.u32(t.size[i]); } b.close(a); }
    { int a=b.full(co64? "co64": "stco",0,0); b.u32(chunks.size()); for (long[] c: chunks){ if (co64) b.u64(c[0]); else b.u32(c[0]); } b.close(a); }
    b.close(stbl); b.close(minf); b.close(mdia); b.close(trak); }
}
