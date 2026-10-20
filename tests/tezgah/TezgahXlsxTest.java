package com.zeytindefteri.app;
import java.util.*;
public class TezgahXlsxTest {
  public static void main(String[] a) throws Exception {
    List<TezgahPurchases.Purchase> list=new ArrayList<>();
    String[] names={"Yusuf Soyaslan","Muammer Çerdin","Ali Yılmaz","Adsız <&> \"test\""};
    double[][] kg={{5,28,46,37,15,5},{3,15,20,10,6,3},{5,26,15,10,8,3},{5,46,53,46,16,5}};
    for(int i=0;i<4;i++){
      TezgahModel m=new TezgahModel();
      for(int g=0;g<6;g++) m.add(g,1,kg[i][g]);
      double[] pr={130,100,70,40,30,20}; for(int g=0;g<6;g++) m.setPrice(g,pr[g]);
      TezgahPurchases.Purchase p=new TezgahPurchases.Purchase();
      p.seller=names[i]; p.phone=i==0?"05321112233":""; p.date="2026-10-20"; p.data=m.encode();
      p.kg=m.totalNet(); p.kasa=m.totalKasa(); p.gross=m.total(); p.paid=i==1?1000:m.total(); p.createdAt=1760980000000L+i*600000L;
      list.add(p);
    }
    TezgahClosing c=TezgahClosing.fromPurchases(list);
    c.delivered[1]-=2; c.delivered[2]-=1.5; c.rate=1.5; c.advance=20000;
    java.nio.file.Files.write(java.nio.file.Paths.get(a[0]), TezgahXlsx.build(list,"Alım defteri · 20 Ekim 2026",c));
    java.nio.file.Files.write(java.nio.file.Paths.get(a[1]), TezgahXlsx.build(new ArrayList<>(),"Boş gün"));
    System.out.println("xlsx yazıldı");
  }
}
