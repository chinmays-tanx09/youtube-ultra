package app.morphe.extension.shared.spoof.requests;
import app.morphe.extension.shared.spoof.ClientType; import java.util.*;
/** Stub: returns canned player responses per client and records what it was called with. */
public class StreamOrDetailsDataRequest {
  public static final class StreamData { private final byte[] s; public StreamData(byte[] s){ this.s=s; } public byte[] streamingData(){ return s; } }
  public static final Map<ClientType,byte[]> responses=new HashMap<>();
  public static final List<String> calls=new ArrayList<>();
  public static StreamData fetchDownloadStream(String videoId, ClientType c, Map<String,String> headers){
    calls.add(videoId+"|"+c+"|"+headers.get("Authorization"));
    byte[] b=responses.get(c); return b==null? null: new StreamData(b); }
}
