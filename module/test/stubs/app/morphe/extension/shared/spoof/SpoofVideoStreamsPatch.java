package app.morphe.extension.shared.spoof;
public class SpoofVideoStreamsPatch { public static volatile java.util.Map<String,String> currentVideoRequestHeader=null;
  public static ClientType preferred=ClientType.VISIONOS_1_02;
  public static ClientType getPreferredClient(){ return preferred; } }
