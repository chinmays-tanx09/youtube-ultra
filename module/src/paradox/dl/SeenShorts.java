package paradox.dl;
import java.io.*; import java.util.*;
/** v10.13: "Skip Shorts you have seen" — the pure bookkeeping (no Android types, unit-tested on the JVM).
 *  seen = ids of Shorts the person watched for a few seconds; tick() is fed the current Short id once every ~500 ms while the Shorts player is open
 *  and answers whether to scroll past it. A Short the person opens first in a session is never skipped; a skip is tried at most twice per id and at most
 *  {@link #MAX_STREAK} times in a row, so a swipe that does not take effect can never loop. */
public final class SeenShorts {
  public static final long DWELL_MS=3000, SETTLE_MS=1400; public static final int MAX_STREAK=8, CAP=4000;
  public enum Act { NONE, SKIP }
  private final LinkedHashSet<String> seen=new LinkedHashSet<>();
  private String cur; private long curSince; private boolean marked; private boolean first=true; private int streak; private long lastSkipAt; private final Map<String,Integer> tries=new HashMap<>();
  private boolean dirty;
  public synchronized boolean contains(String id){ return id!=null && seen.contains(id); }
  public synchronized int size(){ return seen.size(); }
  public synchronized boolean isDirty(){ return dirty; }
  public synchronized void clear(){ seen.clear(); dirty=true; }
  public synchronized void add(String id){ if (id==null || id.isEmpty()) return; seen.remove(id); seen.add(id); while (seen.size()>CAP) { Iterator<String> it=seen.iterator(); it.next(); it.remove(); } dirty=true; }
  /** The Shorts player closed or the app left Shorts: the next Short is the first of a new session. */
  public synchronized void reset(){ cur=null; first=true; streak=0; marked=false; tries.clear(); }
  /** @param id current video id (null/empty = unknown) @param open Shorts player is open @param enabled the setting @param now ms clock */
  public synchronized Act tick(String id, boolean open, boolean enabled, long now){
    if (!open || !enabled) { if (!open) reset(); return Act.NONE; }
    if (id==null || id.isEmpty()) return Act.NONE;
    if (!id.equals(cur)) {
      // the previous Short leaves: it counts as seen when it was on screen long enough
      cur=id; curSince=now; marked=false;
      boolean skip=!first && seen.contains(id) && streak<MAX_STREAK && tries(id)<2;
      first=false;
      if (skip) { streak++; tries.put(id, tries(id)+1); lastSkipAt=now; return Act.SKIP; }
      streak=0; return Act.NONE; }
    if (!marked && now-curSince>=DWELL_MS) { marked=true; if (!seen.contains(id)) add(id); streak=0; return Act.NONE; }
    // still on a Short we asked to skip: the swipe did not take effect; try once more, then give up on it
    if (seen.contains(id) && !marked && streak>0 && now-lastSkipAt>=SETTLE_MS && streak<MAX_STREAK && tries(id)<2) { streak++; tries.put(id, tries(id)+1); lastSkipAt=now; return Act.SKIP; }
    return Act.NONE; }
  private int tries(String id){ Integer n=tries.get(id); return n==null? 0: n; }
  // ---- persistence: one id per line, newest last; atomic write with a .bak ----
  public synchronized void load(File f, File bak){ for (File x: new File[]{f,bak}) { try { if (!x.exists()) continue; BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(x),"UTF-8")); try { String l; List<String> got=new ArrayList<>(); while ((l=r.readLine())!=null) { l=l.trim(); if (l.matches("[A-Za-z0-9_-]{6,20}")) got.add(l); } seen.clear(); seen.addAll(got); dirty=false; return; } finally { r.close(); } } catch (Exception e){} } }
  public synchronized boolean save(File f, File bak, File tmp){ try { StringBuilder b=new StringBuilder(); for (String s: seen) b.append(s).append('\n');
      FileOutputStream o=new FileOutputStream(tmp); try { o.write(b.toString().getBytes("UTF-8")); o.flush(); o.getFD().sync(); } finally { o.close(); }
      if (f.exists() && f.length()>0) { try { QueueStore.copy(f,bak); } catch (Exception e){} }
      if (!tmp.renameTo(f)) { QueueStore.copy(tmp,f); tmp.delete(); } dirty=false; return true; } catch (Exception e){ return false; } }
}
