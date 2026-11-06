package paradox.dl;
import java.io.*; import java.nio.file.*; import java.util.*;
/** v10.13: Skip-seen-Shorts bookkeeping, queue / Watch later persistence and ordering, video-id parsing for the share targets. */
public class V1013Test {
  static int pass, fail;
  static void check(boolean ok, String what){ if (ok) pass++; else { fail++; System.out.println("FAIL: "+what); } }
  static final String A="aaaaaaaaaaa", B="bbbbbbbbbbb", C="ccccccccccc", D="ddddddddddd", E="eeeeeeeeeee";
  public static void main(String[] args) throws Exception {
    // ---- 1. seen Shorts ----
    SeenShorts s=new SeenShorts(); long t=1000;
    check(s.tick(A,true,true,t)==SeenShorts.Act.NONE, "first Short of a session plays");
    check(s.tick(A,true,true,t+1000)==SeenShorts.Act.NONE && !s.contains(A), "not seen before the dwell time");
    s.tick(A,true,true,t+3100); check(s.contains(A), "seen after 3 s on screen");
    check(s.tick(B,true,true,t+4000)==SeenShorts.Act.NONE, "new unseen Short plays");
    s.tick(B,true,true,t+4000+3100); check(s.contains(B), "B seen");
    check(s.tick(A,true,true,t+9000)==SeenShorts.Act.SKIP, "A (seen) is skipped when scrolled to");
    check(s.tick(A,true,true,t+9300)==SeenShorts.Act.NONE, "no second swipe within the settle time");
    check(s.tick(A,true,true,t+9000+SeenShorts.SETTLE_MS+10)==SeenShorts.Act.SKIP, "swipe retried once if the Short did not change");
    check(s.tick(A,true,true,t+9000+2*SeenShorts.SETTLE_MS+50)==SeenShorts.Act.NONE, "gives up after two tries (no loop)");
    check(s.tick(C,true,true,t+20000)==SeenShorts.Act.NONE, "after giving up, the next unseen Short plays");
    // first Short of a session is never skipped even if seen; reset on close
    s.reset(); check(s.tick(A,true,true,t+30000)==SeenShorts.Act.NONE, "a seen Short opened first (session start) plays");
    check(s.tick(null,false,true,t+31000)==SeenShorts.Act.NONE, "closing Shorts resets the session");
    // chain of seen Shorts: bounded
    SeenShorts z=new SeenShorts(); for (int i=0;i<12;i++) z.add(String.format("x%010d",i));
    z.tick("y0000000000",true,true,0); int skips=0; SeenShorts.Act last=null; for (int i=0;i<=SeenShorts.MAX_STREAK;i++) { last=z.tick(String.format("x%010d",i),true,true,100L*(i+1)); if (last==SeenShorts.Act.SKIP) skips++; }
    check(skips==SeenShorts.MAX_STREAK && last==SeenShorts.Act.NONE, "a run of seen Shorts: "+SeenShorts.MAX_STREAK+" skipped, then one is allowed to play (got "+skips+")");
    check(new SeenShorts().tick(A,true,false,0)==SeenShorts.Act.NONE, "disabled setting: nothing");
    SeenShorts d2=new SeenShorts(); d2.tick("q0000000000",true,true,0); d2.tick(A,true,false,10); check(d2.size()==0, "disabled: nothing marked seen");
    // cap
    SeenShorts cp=new SeenShorts(); for (int i=0;i<SeenShorts.CAP+50;i++) cp.add("id"+String.format("%09d",i)); check(cp.size()==SeenShorts.CAP && !cp.contains("id000000000") && cp.contains("id"+String.format("%09d",SeenShorts.CAP+49)), "list capped, oldest dropped");
    // persistence + corruption
    Path dir=Files.createTempDirectory("ytu"); File f=new File(dir.toFile(),"s.txt"), bk=new File(dir.toFile(),"s.bak"), tm=new File(dir.toFile(),"s.tmp");
    SeenShorts p=new SeenShorts(); p.add(A); p.add(B); check(p.save(f,bk,tm) && !p.isDirty(), "save"); p.add(C); p.save(f,bk,tm);
    SeenShorts q=new SeenShorts(); q.load(f,bk); check(q.size()==3 && q.contains(A) && q.contains(C), "reload round-trip");
    Files.write(f.toPath(), new byte[]{(byte)0xff,0,1,2,3}); SeenShorts r=new SeenShorts(); r.load(f,bk); check(r.size()==0 || r.size()==2 || r.size()==3, "garbage file never throws");
    Files.write(f.toPath(), "aaaaaaaaaaa\nnot an id !!\nbbbbbbbbbbb\n".getBytes()); r=new SeenShorts(); r.load(f,bk); check(r.size()==2, "bad lines skipped, good kept");
    f.delete(); r=new SeenShorts(); r.load(f,bk); check(r.size()==2, "missing file -> .bak used (had A,B)");
    // ---- 2. queue store ----
    File qd=Files.createTempDirectory("ytq").toFile(); QueueStore st=new QueueStore(qd);
    check(st.add(QueueStore.QUEUE,A,"Alpha","Chan"), "add"); st.add(QueueStore.QUEUE,B,"Beta",null); st.add(QueueStore.QUEUE,C,"Gamma",null); st.add(QueueStore.LATER,A,"Alpha","Chan");
    check(!st.add(QueueStore.QUEUE,A,"dup",null), "duplicate refused"); check(!st.add(QueueStore.QUEUE,"short","x",null) && !st.add(QueueStore.QUEUE,null,"x",null), "invalid ids refused");
    check(st.size(QueueStore.QUEUE)==3 && st.size(QueueStore.LATER)==1, "sizes (lists are independent)");
    check(st.nextIndex(QueueStore.QUEUE,A)==1 && st.nextIndex(QueueStore.QUEUE,C)==-1 && st.prevIndex(QueueStore.QUEUE,A)==-1 && st.prevIndex(QueueStore.QUEUE,C)==1, "next / previous, none at the ends");
    st.move(QueueStore.QUEUE,C,-1); check(ids(st.all(QueueStore.QUEUE)).equals("a,c,b"), "move up");
    st.move(QueueStore.QUEUE,A,+1); check(ids(st.all(QueueStore.QUEUE)).equals("c,a,b"), "move down");
    check(!st.move(QueueStore.QUEUE,C,-1), "move up at top is a no-op"); st.moveToFront(QueueStore.QUEUE,B); check(ids(st.all(QueueStore.QUEUE)).equals("b,c,a"), "play next = to the front");
    st.setTitle(QueueStore.QUEUE,B,"Beta 2","Ch"); check(st.all(QueueStore.QUEUE).get(0).title.equals("Beta 2") && st.all(QueueStore.QUEUE).get(0).channel.equals("Ch"), "title set");
    st.remove(QueueStore.QUEUE,C); check(ids(st.all(QueueStore.QUEUE)).equals("b,a"), "remove");
    QueueStore st2=new QueueStore(qd); check(ids(st2.all(QueueStore.QUEUE)).equals("b,a") && st2.size(QueueStore.LATER)==1 && st2.all(QueueStore.QUEUE).get(0).thumb.contains("ytimg"), "persisted across a restart");
    // corruption: file garbage -> .bak (previous state), file kept aside
    File main=new File(qd,"ytultra_queue.json"); Files.write(main.toPath(), "{{{ not json".getBytes()); QueueStore st3=new QueueStore(qd);
    check(st3.size(QueueStore.QUEUE)>=2 && new File(qd,"ytultra_queue.corrupt").exists(), "corrupt file: restored from .bak, bad file kept aside ("+st3.size(QueueStore.QUEUE)+")");
    st3.add(QueueStore.QUEUE,D,"Delta",null); check(new QueueStore(qd).size(QueueStore.QUEUE)>=3, "still saves after recovery");
    // one bad entry does not drop the others
    Files.write(main.toPath(), ("{\"queue\":[{\"id\":\"aaaaaaaaaaa\",\"title\":\"ok\"},42,{\"id\":\"bad\"},{\"id\":\"bbbbbbbbbbb\"},{\"id\":\"aaaaaaaaaaa\"}],\"later\":\"oops\"}").getBytes());
    QueueStore st4=new QueueStore(qd); check(ids(st4.all(QueueStore.QUEUE)).equals("a,b") && st4.size(QueueStore.LATER)==0, "per-entry parse: bad entries and duplicates skipped");
    st4.clear(QueueStore.QUEUE); check(st4.size(QueueStore.QUEUE)==0, "clear");
    // ---- 3. video id forms accepted by the share targets / download sheet ----
    String[] urls={"https://youtu.be/dQw4w9WgXcQ","https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=5","https://www.youtube.com/shorts/dQw4w9WgXcQ","https://m.youtube.com/watch?v=dQw4w9WgXcQ"};
    for (String u: urls) check("dQw4w9WgXcQ".equals(videoIdQuick(u)), "id from "+u);
    // ---- 4. reflection into the extension (stub classes with the signatures read from the extension's smali) ----
    check(!Probe.shortsOpen() && Probe.videoId()==null, "probe: closed / no id by default");
    app.morphe.extension.youtube.shared.ShortsPlayerState.cur=app.morphe.extension.youtube.shared.ShortsPlayerState.OPEN; app.morphe.extension.youtube.shared.VideoInformation.id="abcdefghijk";
    check(Probe.shortsOpen() && "abcdefghijk".equals(Probe.videoId()), "probe: reads Shorts state and the current video id");
    final int[] ended={0}; Probe.observeVideoEnded(V1013Test.class.getClassLoader(), new Runnable(){ public void run(){ ended[0]++; } });
    app.morphe.extension.youtube.shared.VideoState.getOnChange().invoke(app.morphe.extension.youtube.shared.VideoState.PLAYING);
    check(ended[0]==0, "observer: PLAYING does not fire");
    app.morphe.extension.youtube.shared.VideoState.getOnChange().invoke(app.morphe.extension.youtube.shared.VideoState.ENDED);
    check(ended[0]==1, "observer: ENDED fires once");
    // a SeenShorts + Probe loop end to end: a Short seen earlier is skipped when scrolled back to
    SeenShorts loop=new SeenShorts(); long now=0; String[] seq={"aaaaaaaaaaa","bbbbbbbbbbb"}; int sk=0;
    for (int round=0; round<2; round++) for (String id: seq) { app.morphe.extension.youtube.shared.VideoInformation.id=id; now+=500; if (loop.tick(Probe.videoId(), Probe.shortsOpen(), true, now)==SeenShorts.Act.SKIP) sk++; now+=3500; loop.tick(Probe.videoId(), Probe.shortsOpen(), true, now); }
    check(sk>=1, "end to end: second pass over seen Shorts triggers a skip ("+sk+")");
    System.out.println("V1013Test: "+pass+" passed, "+fail+" failed"); if (fail>0) System.exit(1);
  }
  static String ids(List<QueueStore.Entry> l){ StringBuilder b=new StringBuilder(); for (QueueStore.Entry e: l) { if (b.length()>0) b.append(','); b.append(e.id.charAt(0)); } return b.toString(); }
  static String videoIdQuick(String u){ try { return Resolve.videoId(u); } catch (Throwable t){ return null; } }
}
