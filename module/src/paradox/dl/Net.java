package paradox.dl;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.localization.*;
import java.io.*; import java.net.*; import java.util.*;
public final class Net extends Downloader {
  public static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
  private static boolean inited;
  /** User-Agent per stream URL for streams fetched with the account (each client's own UA); in memory only. */
  private static final Map<String,String> UAS=Collections.synchronizedMap(new LinkedHashMap<String,String>(64, 0.75f, true){ @Override protected boolean removeEldestEntry(Map.Entry<String,String> e){ return size()>512; } });
  public static void uaFor(String url, String ua){ if (url!=null && ua!=null && !ua.isEmpty()) UAS.put(url, ua); }
  public static String ua(String url){ String u= url==null? null: UAS.get(url); return u!=null? u: UA; }
  public static synchronized void init(){
    if (inited) return; NewPipe.init(new Net(), Localization.DEFAULT, ContentCountry.DEFAULT); inited=true;
  }
  @Override public Response execute(Request r) throws IOException, org.schabi.newpipe.extractor.exceptions.ReCaptchaException {
    HttpURLConnection c=(HttpURLConnection)new URL(r.url()).openConnection();
    c.setRequestMethod(r.httpMethod()); c.setConnectTimeout(15000); c.setReadTimeout(30000);
    c.setRequestProperty("User-Agent", UA);
    for (Map.Entry<String,List<String>> e: r.headers().entrySet()) for (String v: e.getValue()) c.addRequestProperty(e.getKey(), v);
    byte[] d=r.dataToSend(); if (d!=null){ c.setDoOutput(true); OutputStream o=c.getOutputStream(); o.write(d); o.close(); }
    int code=c.getResponseCode();
    if (code==429) throw new org.schabi.newpipe.extractor.exceptions.ReCaptchaException("rate limited", r.url());
    InputStream in= code>=400? c.getErrorStream(): c.getInputStream();
    String body=""; if (in!=null){ ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n; while((n=in.read(b))>0) bo.write(b,0,n); in.close(); body=bo.toString("UTF-8"); }
    Map<String,List<String>> h=new HashMap<>(); for (Map.Entry<String,List<String>> e: c.getHeaderFields().entrySet()) if (e.getKey()!=null) h.put(e.getKey(), e.getValue());
    return new Response(code, c.getResponseMessage(), h, body, c.getURL().toString());
  }
}
