package paradox.dl;
/** Pure helpers for the Shorts long-press share (unit-tested on the box). */
public final class UltraGLogic {
  /** The finger must start on the video itself: not the right button column, the top bar or the bottom info/progress area. */
  public static boolean inZone(float x, float y, int w, int hh){ if (w<=0 || hh<=0) return false; return x<w*0.78f && y>hh*0.12f && y<hh*0.80f; }
  public static boolean moved(float x0, float y0, float x1, float y1, float limit){ float a=x1-x0, b=y1-y0; return a*a+b*b>limit*limit; }
  public static String shareUrl(String id){ if (id==null) return null; String s=id.trim(); if (!s.matches("[A-Za-z0-9_-]{6,20}")) return null; return "https://youtube.com/shorts/"+s; }
}
