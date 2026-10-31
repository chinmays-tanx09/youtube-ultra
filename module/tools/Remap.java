
import org.objectweb.asm.*; import java.util.*; import java.util.zip.*; import java.io.*;
public class Remap {
  static final String C="paradox/dl/Compat"; static int n=0;
  static String[] map(int op, String owner, String name, String desc){
    if (owner.equals("java/net/URLDecoder")&&name.equals("decode")&&desc.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;")) return new String[]{"urlDecode",desc};
    if (owner.equals("java/net/URLEncoder")&&name.equals("encode")&&desc.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;")) return new String[]{"urlEncode",desc};
    if (owner.equals("java/util/stream/Collectors")&&name.equals("toUnmodifiableList")) return new String[]{"toUnmodifiableList",desc};
    if (owner.equals("java/util/Optional")&&name.equals("or")) return new String[]{"optOr","(Ljava/util/Optional;Ljava/util/function/Supplier;)Ljava/util/Optional;"};
    if (owner.equals("java/util/Optional")&&name.equals("stream")) return new String[]{"optStream","(Ljava/util/Optional;)Ljava/util/stream/Stream;"};
    if (owner.equals("java/math/BigInteger")&&name.equals("intValueExact")) return new String[]{"intValueExact","(Ljava/math/BigInteger;)I"};
    if (owner.equals("java/lang/Math")&&name.equals("multiplyHigh")) return new String[]{"multiplyHigh",desc};
    if (owner.equals("java/lang/String")&&name.equals("isBlank")) return new String[]{"isBlank","(Ljava/lang/String;)Z"};
    if (owner.equals("java/util/Objects")&&name.equals("requireNonNullElse")) return new String[]{"requireNonNullElse",desc};
    return null; }
  public static void main(String[] a) throws Exception {
    try (ZipFile z=new ZipFile(a[0]); ZipOutputStream out=new ZipOutputStream(new FileOutputStream(a[1]))) {
      for (Enumeration<? extends ZipEntry> e=z.entries(); e.hasMoreElements();){ ZipEntry x=e.nextElement(); byte[] b=z.getInputStream(x).readAllBytes();
        if (x.getName().endsWith(".class") && !x.getName().equals(C+".class")) { ClassReader cr=new ClassReader(b); ClassWriter cw=new ClassWriter(0);
          cr.accept(new ClassVisitor(Opcodes.ASM9, cw){ public MethodVisitor visitMethod(int acc,String nm,String d,String s,String[] ex){ MethodVisitor mv=super.visitMethod(acc,nm,d,s,ex);
            return new MethodVisitor(Opcodes.ASM9, mv){ public void visitMethodInsn(int op,String ow,String name,String desc,boolean itf){ String[] m=map(op,ow,name,desc); if (m!=null){ n++; super.visitMethodInsn(Opcodes.INVOKESTATIC,C,m[0],m[1],false);} else super.visitMethodInsn(op,ow,name,desc,itf); } }; } }, 0);
          b=cw.toByteArray(); }
        ZipEntry y=new ZipEntry(x.getName()); out.putNextEntry(y); out.write(b); out.closeEntry(); } }
    System.out.println("remapped "+n+" call sites"); } }