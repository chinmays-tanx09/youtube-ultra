package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.stream.*; import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import java.io.*; import java.util.*;
/** v10.11: up to 4K / 8K / HDR quality rows, and our own MP4 muxer's pure-Java parts (the real-file checks are in muxer.sh). */
public class HighResTest {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static String rows(List<String[]> l){ StringBuilder b=new StringBuilder(); for (String[] r: l) b.append(r[0]).append('=').append(r[1]).append(' '); return b.toString().trim(); }
  static boolean has(String u, int itag){ return u!=null && u.contains("itag="+itag+"&"); }
  static PlayerProto.Fmt f(int itag, String mime, int w, int h, int fps, long clen, int bitrate){ PlayerProto.Fmt x=new PlayerProto.Fmt(); x.itag=itag; x.mimeType=mime; x.width=w; x.height=h; x.fps=fps; x.contentLength=clen; x.bitrate=bitrate; x.adaptive=true;
    x.url="https://rr1---sn-fake.googlevideo.com/videoplayback?itag="+itag+"&id=FAKE"; x.qualityLabel= h>0? Math.min(w,h)+"p"+(fps>30? fps: "")+(mime.contains("vp9.2")||mime.contains(".10\"")? " HDR": ""): ""; return x; }
  public static void main(String[] args) throws Exception {
    // ---- An 8K HDR upload as YouTube's player answer lists it (several itags the extractor's own table doesn't know: 571, 402, 272@4320, 337, 701).
    PlayerProto.Player p=new PlayerProto.Player(); p.status=PlayerProto.OK; p.videoId="FAKE8K"; p.title="8K HDR test"; p.lengthSeconds=60;
    p.formats.add(f(137,"video/mp4; codecs=\"avc1.640028\"",1920,1080,30,40_000_000,0));
    p.formats.add(f(299,"video/mp4; codecs=\"avc1.64002a\"",1920,1080,60,60_000_000,0));
    p.formats.add(f(136,"video/mp4; codecs=\"avc1.4d401f\"",1280,720,30,15_000_000,0));
    p.formats.add(f(308,"video/webm; codecs=\"vp9\"",2560,1440,60,150_000_000,0));
    p.formats.add(f(315,"video/webm; codecs=\"vp9\"",3840,2160,60,400_000_000,0));
    p.formats.add(f(272,"video/webm; codecs=\"vp9\"",7680,4320,60,900_000_000,0));
    p.formats.add(f(400,"video/mp4; codecs=\"av01.0.12M.08\"",2560,1440,60,90_000_000,0));
    p.formats.add(f(401,"video/mp4; codecs=\"av01.0.13M.08\"",3840,2160,60,200_000_000,0));
    p.formats.add(f(571,"video/mp4; codecs=\"av01.0.17M.08\"",7680,4320,60,0,48_000_000));     // no length: size estimated from bitrate x duration
    p.formats.add(f(337,"video/webm; codecs=\"vp9.2\"",3840,2160,60,500_000_000,0));
    p.formats.add(f(336,"video/webm; codecs=\"vp9.2\"",2560,1440,60,200_000_000,0));
    p.formats.add(f(701,"video/mp4; codecs=\"av01.0.13M.10.0.110.09.16.09.0\"",3840,2160,60,250_000_000,0));
    PlayerProto.Fmt aac=f(140,"audio/mp4; codecs=\"mp4a.40.2\"",0,0,0,1_000_000,0); aac.averageBitrate=129_000; p.formats.add(aac);
    PlayerProto.Fmt op=f(251,"audio/webm; codecs=\"opus\"",0,0,0,1_200_000,0); op.averageBitrate=150_000; p.formats.add(op);
    StreamInfo si=Resolve.toInfo(p, "FAKE8K", "https://youtu.be/FAKE8K");
    check(si!=null, "player answer -> StreamInfo");
    Set<Integer> its=new HashSet<>(); for (VideoStream v: si.getVideoOnlyStreams()) its.add(v.getItagItem().id);
    check(its.containsAll(Arrays.asList(571,337,336,701,272)), "itags missing from the extractor's table are kept (571, 337, 336, 701, 272): "+its);
    int h272=0; for (VideoStream v: si.getVideoOnlyStreams()) if (v.getItagItem().id==272) h272=Formats.height(v);
    check(h272==4320, "itag 272 on an 8K video is 4320p, not the table's 2160p: "+h272);

    // No hardware AV1, hardware VP9: VP9 above 1080p, HDR rows after the normal row of the same height.
    Formats.av1MaxHeight=0; Formats.vp9MaxHeight=2160;
    List<String[]> vc=Formats.videoChoices(si);
    check(rows(vc).equals("v:4320:vp9=4320p (8K · VP9) 60fps v:2160:vp9=2160p (4K · VP9) 60fps v:2160:hdr=2160p (4K · HDR · VP9) 60fps v:1440:vp9=1440p (QHD · VP9) 60fps v:1440:hdr=1440p (QHD · HDR · VP9) 60fps v:1080:mp4=1080p (Full HD) 60fps v:720:mp4=720p (HD)"),
      "8K/4K/1440p + HDR rows (no AV1 decoder): "+rows(vc));
    check(vc.get(0)[2].equals(String.valueOf(900_000_000L+1_000_000)), "8K size = VP9 + AAC: "+vc.get(0)[2]);
    Formats.Pick k=Formats.pick(si,"v:2160:hdr"); check(k!=null && has(k.videoUrl,337) && k.hdr && k.codec.equals("vp9") && k.ownMux() && has(k.audioUrl,140) && k.label.equals("2160p60 VP9 HDR"), "4K HDR row -> 337 VP9.2 + AAC: "+(k==null? null: k.label));
    k=Formats.pick(si,"v:2160:vp9"); check(k!=null && has(k.videoUrl,315) && !k.hdr && k.fps==60, "4K row -> 315 (SDR 60fps), not the HDR stream");
    k=Formats.pick(si,"v:4320:vp9"); check(k!=null && has(k.videoUrl,272) && k.height==4320, "8K row -> 272");
    k=Formats.pick(si,"v:2160:any"); check(k!=null && has(k.videoUrl,315), "playlist 4K row -> SDR 4K");
    k=Formats.pick(si,"v:1440:any"); check(k!=null && has(k.videoUrl,308), "playlist 1440p row -> 308");
    k=Formats.pick(si,"v:9999:mp4"); check(k!=null && has(k.videoUrl,272), "remembered 'tallest' -> 8K");
    k=Formats.pick(si,"v:1080:hdr"); check(k!=null && has(k.videoUrl,299) && !k.hdr, "HDR asked but none at/below 1080p -> normal 1080p60 H.264");

    // Hardware AV1 up to 4K: AV1 wins up to 2160, 8K stays VP9 (decoder can't), HDR 4K is AV1 HDR (701).
    Formats.av1MaxHeight=2160;
    vc=Formats.videoChoices(si);
    check(rows(vc).equals("v:4320:vp9=4320p (8K · VP9) 60fps v:2160:av1=2160p (4K · AV1) 60fps v:2160:hdr=2160p (4K · HDR · AV1) 60fps v:1440:av1=1440p (QHD · AV1) 60fps v:1440:hdr=1440p (QHD · HDR · VP9) 60fps v:1080:mp4=1080p (Full HD) 60fps v:720:mp4=720p (HD)"),
      "AV1-to-4K phone rows: "+rows(vc));
    k=Formats.pick(si,"v:2160:hdr"); check(k!=null && has(k.videoUrl,701) && k.codec.equals("av1") && k.hdr, "4K HDR on an AV1 phone -> 701");
    // Hardware AV1 to 8K: the 8K row becomes AV1 (571), size estimated from bitrate x 60 s.
    Formats.av1MaxHeight=4320;
    vc=Formats.videoChoices(si);
    check(vc.get(0)[0].equals("v:4320:av1") && vc.get(0)[2].equals(String.valueOf(48_000_000L*60/8 + 1_000_000)), "8K AV1 row with an estimated size: "+vc.get(0)[0]+" "+vc.get(0)[2]);
    Formats.av1MaxHeight=0;

    // Codec text missing: the itag decides the family; 3GP / unknown codecs are never offered.
    ItagItem it=new ItagItem(ItagItem.getItag(313)); it.setHeight(2160); it.setWidth(3840); it.setContentLength(10);
    VideoStream noCodec=new VideoStream.Builder().setId("313").setContent("https://x/videoplayback?itag=313&", true).setMediaFormat(it.getMediaFormat()).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).setIsVideoOnly(true).setResolution("2160p").setItagItem(it).build();
    check("vp9".equals(Formats.family(noCodec)), "no codec text, itag 313 -> VP9");
    check(Formats.hdr(si.getVideoOnlyStreams().get(si.getVideoOnlyStreams().size()-1)) || true, "hdr() runs");
    int hdrCount=0; for (VideoStream v: si.getVideoOnlyStreams()) if (Formats.hdr(v)) hdrCount++; check(hdrCount==3, "HDR streams detected (337, 336, 701): "+hdrCount);

    // Heights follow YouTube's quality label: a 1080x1920 Short is "1080p", itag 780 (1080x608, labelled 480p) is 480p; HDR rows only from 1080p up.
    PlayerProto.Player q=new PlayerProto.Player(); q.status=PlayerProto.OK; q.videoId="SHORT"; q.lengthSeconds=30;
    PlayerProto.Fmt sh=f(137,"video/mp4; codecs=\"avc1.640028\"",1080,1920,30,9_000_000,0); sh.qualityLabel="1080p"; q.formats.add(sh);
    PlayerProto.Fmt s4=f(313,"video/webm; codecs=\"vp9\"",2160,3840,30,40_000_000,0); s4.qualityLabel="2160p"; q.formats.add(s4);
    PlayerProto.Fmt odd=f(780,"video/webm; codecs=\"vp9\"",1080,608,30,1_000_000,0); odd.qualityLabel="480p"; q.formats.add(odd);
    PlayerProto.Fmt h7=f(334,"video/webm; codecs=\"vp9.2\"",1280,720,60,5_000_000,0); q.formats.add(h7);
    PlayerProto.Fmt h10=f(335,"video/webm; codecs=\"vp9.2\"",1920,1080,60,9_000_000,0); q.formats.add(h10);
    q.formats.add(aac);
    StreamInfo qs=Resolve.toInfo(q, "SHORT", "https://youtube.com/shorts/SHORT"); Formats.av1MaxHeight=0;
    check(rows(Formats.videoChoices(qs)).equals("v:2160:vp9=2160p (4K · VP9) v:1080:mp4=1080p (Full HD) v:1080:hdr=1080p (Full HD · HDR · VP9) 60fps v:480:vp9=480p (VP9)"),
      "portrait 4K Short + odd itag + HDR rows from 1080p: "+rows(Formats.videoChoices(qs)));
    PlayerProto.Player lo=new PlayerProto.Player(); lo.status=PlayerProto.OK; lo.videoId="LOHDR"; lo.lengthSeconds=30; lo.formats.add(f(136,"video/mp4; codecs=\"avc1.4d401f\"",1280,720,30,5_000_000,0)); lo.formats.add(h7); lo.formats.add(aac);
    check(rows(Formats.videoChoices(Resolve.toInfo(lo,"LOHDR","https://youtu.be/LOHDR"))).equals("v:720:mp4=720p (HD) v:720:hdr=720p (HD · HDR · VP9) 60fps"), "HDR video that tops out at 720p still gets its HDR row");

    // ---- Mp4Mux pure parts
    // VP9 key-frame header: profile 0 8-bit 4:2:0 BT.709; profile 2 10-bit BT.2020.
    int[] h0=Mp4Mux.vp9Header(new byte[]{(byte)0x82,0x49,(byte)0x83,0x42,0x20}); check(h0!=null && h0[0]==0 && h0[1]==8 && h0[2]==1, "VP9 header profile 0: "+Arrays.toString(h0));
    // profile 2: frame_marker 10, low 0, high 1 -> 1001 ...; show_existing 0, key 0, show 1, err 0 -> 0010 ; sync ; ten_or_twelve 0, cs=5 (101), range 0
    int[] h2=Mp4Mux.vp9Header(new byte[]{(byte)0x92,0x49,(byte)0x83,0x42,(byte)0b01010000}); check(h2!=null && h2[0]==2 && h2[1]==10 && h2[2]==5 && h2[3]==0, "VP9 header profile 2 / 10-bit / BT.2020: "+Arrays.toString(h2));
    check(Mp4Mux.vp9Header(new byte[]{(byte)0x86,0,0,0,0})==null, "inter frame -> no header");
    check(Mp4Mux.vp9Level(3840,2160,60)==51 && Mp4Mux.vp9Level(3840,2160,30)==50 && Mp4Mux.vp9Level(1920,1080,30)==40 && Mp4Mux.vp9Level(7680,4320,60)==61, "VP9 levels 4K60=5.1, 4K30=5.0, 1080p30=4.0, 8K60=6.1");
    // WebM millisecond timestamps snap to the real frame rate (29.97 -> timescale 30000, 1001 per frame).
    Mp4Mux.Track t=new Mp4Mux.Track(); t.video=true; long[] ns=new long[300]; for (int i=0;i<300;i++){ ns[i]=Math.round(i*1001.0/30)*1_000_000L; t.add(0,1,0,0,i%60==0); }
    Mp4Mux.times(t, ns, 300, 0); boolean uni=true; for (int i=0;i<299;i++) if (t.sampleDts(i+1)-t.sampleDts(i)!=1001) uni=false;
    check(t.timescale==30000 && uni && t.lastDur==1001, "29.97 fps from ms timestamps -> uniform 1001/30000: ts="+t.timescale);
    Mp4Mux.Track t60=new Mp4Mux.Track(); t60.video=true; long[] n60=new long[120]; for (int i=0;i<120;i++){ n60[i]=Math.round(i*1000.0/60)*1_000_000L; t60.add(0,1,0,0,true); }
    Mp4Mux.times(t60, n60, 120, 0); check(t60.timescale==60000 && Math.abs(t60.seconds()-2.0)<1e-9, "60 fps -> 60000 timescale, 120 frames = 2.000 s: "+t60.seconds());
    Mp4Mux.Track tv=new Mp4Mux.Track(); tv.video=true; long[] nv={0, 40_000_000L, 70_000_000L, 130_000_000L}; for (int i=0;i<4;i++) tv.add(0,1,0,0,true);
    Mp4Mux.times(tv, nv, 4, 0); check(tv.timescale==90000 && tv.sampleDts(3)==11700, "variable frame rate keeps real times (90 kHz)");

    // Files past 4 GB: co64 + 64-bit mdat size. Sparse source file (no disk used), output counted, not stored.
    File big=File.createTempFile("ytu_sparse", ".bin"); big.deleteOnExit();
    long chunk=4L<<20; int count=1100; RandomAccessFile rf=new RandomAccessFile(big,"rw"); rf.setLength(chunk*count); rf.close();
    Mp4Mux.Track bv=new Mp4Mux.Track(); bv.video=true; bv.codec="vp09"; bv.width=3840; bv.height=2160; bv.timescale=60000; bv.file=big;
    for (int i=0;i<count;i++) bv.add(i*chunk, (int)chunk, i*1000L, 0, i%120==0); bv.lastDur=1000;
    bv.entry=Mp4Mux.vp09(bv, new int[]{0,8,2,0,1,1}, new Mp4Mux.Color(), 60);
    final ByteArrayOutputStream head=new ByteArrayOutputStream(); final long[] n={0};
    OutputStream sink=new OutputStream(){ public void write(int b){ if (n[0]<1<<20) head.write(b); n[0]++; } public void write(byte[] b,int o,int l){ int keep=(int)Math.max(0,Math.min(l,(1<<20)-n[0])); if (keep>0) head.write(b,o,keep); n[0]+=l; } };
    Mp4Mux.Result r=Mp4Mux.write(new Mp4Mux.Track[]{bv}, sink, null);
    byte[] hd=head.toByteArray(); String hs=new String(hd,0,Math.min(hd.length,1<<20),"ISO-8859-1");
    check(r.bytes==n[0] && n[0]>(1L<<32), "4.3 GB output, every byte written: "+n[0]);
    check(hs.contains("co64") && !hs.contains("stco"), "over 4 GB -> co64 chunk offsets");
    int mi=hs.indexOf("mdat"); long be=((hd[mi-4]&0xffL)<<24)|((hd[mi-3]&0xffL)<<16)|((hd[mi-2]&0xffL)<<8)|(hd[mi-1]&0xffL); long large=0; for (int i=0;i<8;i++) large=(large<<8)|(hd[mi+4+i]&0xff);
    check(be==1 && large==chunk*count+16 && mi-4+large==n[0], "64-bit mdat size: "+large);
    check(r.videoSeconds>18.33 && r.videoSeconds<18.34, "duration 1100 frames at 60 fps = 18.333 s: "+r.videoSeconds);
    big.delete();

    // Truncated download -> clear message, not a broken file.
    File cut=File.createTempFile("ytu_cut", ".bin"); cut.deleteOnExit(); RandomAccessFile cf=new RandomAccessFile(cut,"rw"); cf.setLength(1000); cf.close();
    Mp4Mux.Track ct=new Mp4Mux.Track(); ct.video=true; ct.codec="vp09"; ct.width=640; ct.height=360; ct.file=cut; ct.add(0,600,0,0,true); ct.add(600,600,1,0,false); ct.lastDur=1; ct.entry=Mp4Mux.vp09(ct,null,new Mp4Mux.Color(),30);
    String msg=""; try { Mp4Mux.write(new Mp4Mux.Track[]{ct}, new ByteArrayOutputStream(), null); } catch (IOException e){ msg=String.valueOf(e.getMessage()); }
    check(msg.contains("shorter than its index"), "truncated part -> '"+msg+"'");
    // Cancel while copying.
    ct.size[1]=400; boolean cancelled=false; try { Mp4Mux.write(new Mp4Mux.Track[]{ct}, new ByteArrayOutputStream(), new Mp4Mux.Progress(){ public void at(long d,long tt){} public boolean cancelled(){ return true; } }); } catch (InterruptedIOException e){ cancelled=true; }
    check(cancelled, "cancel stops the copy"); cut.delete();
    // Not a media file.
    File junk=File.createTempFile("ytu_junk", ".bin"); junk.deleteOnExit(); FileOutputStream jo=new FileOutputStream(junk); jo.write(new byte[64]); jo.close();
    msg=""; try { Mp4Mux.read(junk, true); } catch (IOException e){ msg=String.valueOf(e.getMessage()); } check(!msg.isEmpty(), "junk file -> error: "+msg); junk.delete();

    System.out.println("HighResTest: "+pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1);
  }
}
