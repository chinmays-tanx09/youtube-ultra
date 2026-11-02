package app.morphe.extension.shared.spoof.requests;
import app.morphe.extension.shared.spoof.ClientType; import java.util.*;
/** Stub: canned player responses per client, a fake player cache, and a record of what was called. */
public class StreamOrDetailsDataRequest {
  public static final class StreamData { private final byte[] s; public StreamData(byte[] s){ this.s=s; } public byte[] streamingData(){ return s; } }
  public static final Map<ClientType,byte[]> responses=new HashMap<>();
  public static final List<String> calls=new ArrayList<>();
  /** The app's player cache: video id -> player answer the app received while playing it. */
  public static final Map<String,StreamOrDetailsDataRequest> streamCache=new HashMap<>();
  final byte[] data; final boolean done;
  public StreamOrDetailsDataRequest(byte[] d, boolean done){ data=d; this.done=done; }
  public static StreamData fetchDownloadStream(String videoId, ClientType c, Map<String,String> headers){
    calls.add(videoId+"|"+c+"|"+headers.get("Authorization"));
    byte[] b=responses.get(c); return b==null? null: new StreamData(b); }
  public static StreamOrDetailsDataRequest getStreamRequestForVideoId(String id){ return streamCache.get(id); }
  public boolean fetchIsDone(){ return done; }
  public StreamData getStream(){ return data==null? null: new StreamData(data); }
}
