
import org.objectweb.asm.*; import org.objectweb.asm.tree.*; import java.util.*; import java.util.zip.*; import java.io.*;
public class Check {
  static Map<String,ClassNode> lib=new HashMap<>();
  static ClassNode read(InputStream in) throws IOException { ClassNode n=new ClassNode(); new ClassReader(in.readAllBytes()).accept(n, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG); return n; }
  static boolean has(String owner, String name, String desc, boolean field){ if (owner.startsWith("[")) return true; ClassNode c=lib.get(owner); if (c==null) return false;
    if (field){ for (FieldNode f: c.fields) if (f.name.equals(name)) return true; } else for (MethodNode m: c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
    if (c.superName!=null && has(c.superName,name,desc,field)) return true; for (String i: c.interfaces) if (has(i,name,desc,field)) return true; return false; }
  public static void main(String[] a) throws Exception {
    try (ZipFile z=new ZipFile(a[0])) { for (Enumeration<? extends ZipEntry> e=z.entries(); e.hasMoreElements();){ ZipEntry x=e.nextElement(); if (x.getName().endsWith(".class")) { ClassNode n=read(z.getInputStream(x)); lib.put(n.name,n);} } }
    TreeMap<String,Set<String>> miss=new TreeMap<>();
    try (ZipFile z=new ZipFile(a[1])) { for (Enumeration<? extends ZipEntry> e=z.entries(); e.hasMoreElements();){ ZipEntry x=e.nextElement(); if (!x.getName().endsWith(".class")) continue;
      ClassNode n=new ClassNode(); new ClassReader(z.getInputStream(x).readAllBytes()).accept(n,0);
      for (MethodNode m: n.methods) for (AbstractInsnNode in: m.instructions) {
        String owner=null,name=null,desc=null; boolean f=false;
        if (in instanceof MethodInsnNode){ MethodInsnNode mi=(MethodInsnNode)in; owner=mi.owner; name=mi.name; desc=mi.desc; }
        else if (in instanceof FieldInsnNode){ FieldInsnNode fi=(FieldInsnNode)in; owner=fi.owner; name=fi.name; desc=fi.desc; f=true; }
        else if (in instanceof TypeInsnNode){ String t=((TypeInsnNode)in).desc; if ((t.startsWith("java/")||t.startsWith("javax/")) && !lib.containsKey(t)) miss.computeIfAbsent("CLASS "+t,k->new TreeSet<>()).add(n.name); continue; }
        if (owner==null) continue;
        if (!(owner.startsWith("java/")||owner.startsWith("javax/")||owner.startsWith("android/")||owner.startsWith("org/json/"))) continue;
        if (!has(owner,name,desc,f)) miss.computeIfAbsent(owner+"."+name+desc,k->new TreeSet<>()).add(n.name);
      } } }
    for (Map.Entry<String,Set<String>> e: miss.entrySet()) System.out.println(e.getKey()+"  <- "+e.getValue().size()+" e.g. "+e.getValue().iterator().next());
  }
}