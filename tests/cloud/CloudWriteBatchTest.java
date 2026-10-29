package com.zeytindefteri.app;
import java.util.*;
public class CloudWriteBatchTest {
  static int n; static int[] got;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static CloudWriteBatch.Done rec(){ got=null; return (w,f,p)->{ ok(got==null,"bir kez bildirilir"); got=new int[]{w,f,p}; }; }
  public static void main(String[] a){
    CloudWriteBatch b=new CloudWriteBatch(); b.close(rec());
    ok(got!=null&&got[0]==0&&got[2]==0,"yazma yoksa hemen biter");
    b=new CloudWriteBatch(); b.started(); b.started(); b.started(); b.close(rec());
    ok(got==null,"yazmalar bitmeden bitmiş sayılmaz (eski hata)");
    b.finished(true); b.finished(false); ok(got==null,"biri hâlâ bekliyor");
    b.finished(true); ok(Arrays.equals(got,new int[]{2,1,0}),"2 tamam 1 hata");
    b.timeout(); b.finished(true); ok(Arrays.equals(got,new int[]{2,1,0}),"sonra gelen değiştirmez");
    b=new CloudWriteBatch(); b.started(); b.started(); b.finished(true); b.timeout(); ok(got[0]==2,"kapanmadan süre dolması sayılmaz");
    b.close(rec()); ok(got==null,"kapandı, biri bekliyor");
    b.timeout(); ok(Arrays.equals(got,new int[]{1,0,1}),"süre doldu: 1 bekliyor");
    b.finished(true); ok(Arrays.equals(got,new int[]{1,0,1}),"geç sonuç yeni bildirim üretmez");
    b=new CloudWriteBatch(); b.started(); b.finished(true); b.close(rec()); ok(Arrays.equals(got,new int[]{1,0,0}),"kapanmadan önce bitenler sayılır");
    System.out.println("PASS: "+n+" bulut yazma sayacı kontrolü");
  }
}
