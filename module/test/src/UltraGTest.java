import paradox.dl.UltraGLogic;
public class UltraGTest { static int f=0; static void ok(boolean c,String n){ if(!c){f++;System.out.println("FAIL "+n);} else System.out.println("ok "+n);}
 public static void main(String[] a){
  ok(UltraGLogic.inZone(300,800,1080,2400),"centre in zone"); ok(!UltraGLogic.inZone(950,800,1080,2400),"right column out"); ok(!UltraGLogic.inZone(300,100,1080,2400),"top bar out"); ok(!UltraGLogic.inZone(300,2200,1080,2400),"bottom out"); ok(!UltraGLogic.inZone(300,800,0,0),"no size");
  ok(!UltraGLogic.moved(100,100,110,105,48),"small wobble ok"); ok(UltraGLogic.moved(100,100,100,200,48),"swipe cancels");
  ok("https://youtube.com/shorts/abcDEF12345".equals(UltraGLogic.shareUrl("abcDEF12345")),"url"); ok(UltraGLogic.shareUrl(null)==null,"null id"); ok(UltraGLogic.shareUrl("a b;rm")==null,"bad id");
  System.exit(f); } }
