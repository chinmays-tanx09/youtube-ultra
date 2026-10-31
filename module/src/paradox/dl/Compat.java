package paradox.dl;
import java.util.*; import java.util.function.*; import java.util.stream.*;
/** Shims for Java APIs newer than Android 8 (rewired into the extractor at build time). */
public final class Compat {
  public static String urlDecode(String s, java.nio.charset.Charset c){ try { return java.net.URLDecoder.decode(s, c.name()); } catch (java.io.UnsupportedEncodingException e){ throw new RuntimeException(e); } }
  public static String urlEncode(String s, java.nio.charset.Charset c){ try { return java.net.URLEncoder.encode(s, c.name()); } catch (java.io.UnsupportedEncodingException e){ throw new RuntimeException(e); } }
  public static <T> Collector<T,?,List<T>> toUnmodifiableList(){ return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList); }
  @SuppressWarnings("unchecked") public static <T> Optional<T> optOr(Optional<T> o, Supplier<? extends Optional<? extends T>> s){ return o.isPresent()? o: (Optional<T>) Objects.requireNonNull(s.get()); }
  public static <T> Stream<T> optStream(Optional<T> o){ return o.isPresent()? Stream.of(o.get()): Stream.empty(); }
  public static int intValueExact(java.math.BigInteger b){ if (b.bitLength()<=31) return b.intValue(); throw new ArithmeticException("BigInteger out of int range"); }
  public static long multiplyHigh(long x, long y){
    if (x<0 || y<0) { long x1=x>>32, x2=x&0xFFFFFFFFL, y1=y>>32, y2=y&0xFFFFFFFFL; long z2=x2*y2, t=x1*y2+(z2>>>32), z1=t&0xFFFFFFFFL, z0=t>>32; z1+=x2*y1; return x1*y1+z0+(z1>>32); }
    long x1=x>>>32, y1=y>>>32, x2=x&0xFFFFFFFFL, y2=y&0xFFFFFFFFL; long A=x1*y1, B=x2*y2, C=(x1+x2)*(y1+y2), K=C-A-B; return (((B>>>32)+K)>>>32)+A; }
  public static boolean isBlank(String s){ for (int i=0;i<s.length();i++) if (!Character.isWhitespace(s.charAt(i))) return false; return true; }
  public static <T> T requireNonNullElse(T a, T b){ return a!=null? a: Objects.requireNonNull(b, "defaultObj"); }
}
