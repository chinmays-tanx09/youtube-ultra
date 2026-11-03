package paradox.dl;
import android.media.*; import android.os.Build;
/** What this phone's video decoders can play smoothly (hardware), used to choose AV1 or VP9 for 1440p / 4K / 8K. */
public final class Codecs {
  public static final String AV1="video/av01", VP9="video/x-vnd.on2.vp9";
  private static volatile int av1=-1, vp9=-1;
  /** Tallest video (0 = none) of this type that a hardware decoder on this phone plays (any Android version). */
  public static int hwMaxHeight(String mime){
    if (AV1.equals(mime) && av1>=0) return av1; if (VP9.equals(mime) && vp9>=0) return vp9; int best=0;
    try { int[][] sizes={{7680,4320},{3840,2160},{2560,1440},{1920,1080},{1280,720}};
      for (MediaCodecInfo ci: new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
        if (ci.isEncoder() || !hardware(ci)) continue;
        for (String t: ci.getSupportedTypes()){ if (!t.equalsIgnoreCase(mime)) continue;
          MediaCodecInfo.VideoCapabilities vc=ci.getCapabilitiesForType(t).getVideoCapabilities(); if (vc==null) continue;
          for (int[] s: sizes) if (vc.isSizeSupported(s[0],s[1]) || vc.isSizeSupported(s[1],s[0])) { best=Math.max(best,s[1]); break; } } }
    } catch (Throwable t){ best=0; }
    if (AV1.equals(mime)) av1=best; else if (VP9.equals(mime)) vp9=best; return best; }
  static boolean hardware(MediaCodecInfo ci){
    if (Build.VERSION.SDK_INT>=29) return ci.isHardwareAccelerated() && !ci.isSoftwareOnly();
    String n=ci.getName().toLowerCase(java.util.Locale.ROOT);
    return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.") || n.contains(".sw.") || n.endsWith(".sw")); }
  /** Kept for older callers: AV1 hardware max height. */
  public static int av1Mp4MaxHeight(){ return hwMaxHeight(AV1); }
}
