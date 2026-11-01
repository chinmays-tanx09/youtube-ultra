package paradox.dl;
import android.media.*; import android.os.Build;
/** What this phone can put into an MP4 that it can also play back smoothly. */
public final class Codecs {
  private static volatile int av1=-1;
  /** Tallest AV1 video (0 = none) that MediaMuxer can write into MP4 (Android 14+) AND a hardware decoder plays at that size. */
  public static int av1Mp4MaxHeight(){
    if (av1>=0) return av1; int best=0;
    try { if (Build.VERSION.SDK_INT>=34) {
      int[][] sizes={{7680,4320},{3840,2160},{2560,1440},{1920,1080}};
      for (MediaCodecInfo ci: new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
        if (ci.isEncoder() || !ci.isHardwareAccelerated() || ci.isSoftwareOnly()) continue;
        for (String t: ci.getSupportedTypes()){ if (!t.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AV1)) continue;
          MediaCodecInfo.VideoCapabilities vc=ci.getCapabilitiesForType(t).getVideoCapabilities(); if (vc==null) continue;
          for (int[] s: sizes) if (vc.isSizeSupported(s[0],s[1]) || vc.isSizeSupported(s[1],s[0])) { best=Math.max(best,s[1]); break; } } } }
    } catch (Throwable t){ best=0; }
    av1=best; return best; }
}
