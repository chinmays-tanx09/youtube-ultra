package paradox.dl;
import java.io.*;
/** Tiny protobuf writer used only to (re)generate the test fixtures. */
final class Pb { final ByteArrayOutputStream o=new ByteArrayOutputStream();
  Pb v(int f, long x){ tag(f,0); var(x); return this; }
  Pb s(int f, String x){ return b(f, x.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  Pb m(int f, Pb x){ return b(f, x.o.toByteArray()); }
  Pb b(int f, byte[] x){ tag(f,2); var(x.length); o.write(x,0,x.length); return this; }
  Pb f32(int f, int x){ tag(f,5); for (int i=0;i<4;i++) o.write((x>>>(8*i))&0xff); return this; }
  void tag(int f, int w){ var(((long)f<<3)|w); }
  void var(long x){ while ((x & ~0x7FL)!=0) { o.write((int)((x&0x7f)|0x80)); x>>>=7; } o.write((int)x); }
  byte[] bytes(){ return o.toByteArray(); }
}
