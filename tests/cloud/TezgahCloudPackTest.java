package com.zeytindefteri.app;
import java.util.List;
public class TezgahCloudPackTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    StringBuilder big=new StringBuilder("ZEYTIN-DEFTERI-YEDEK 1\n@created 123\n#TABLE tezgah_purchases\n#COLS id\tseller\n");
    for(int i=0;i<40000;i++) big.append("I").append(i).append("\tSŞükrü Işık Çağlar ").append(i%97).append('\n');
    String text=big.toString();
    byte[] z=TezgahCloudPack.gzip(text);
    ok(z.length<text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length/3,"sıkıştırma küçültür: "+z.length);
    ok(TezgahCloudPack.gunzip(z).equals(text),"açınca aynı metin (Türkçe harfler dahil)");
    List<byte[]> parts=TezgahCloudPack.split(z,1000);
    ok(parts.size()==(z.length+999)/1000,"parça sayısı");
    for(byte[] p:parts) ok(p.length<=1000,"parça sınırı");
    ok(TezgahCloudPack.gunzip(TezgahCloudPack.join(parts)).equals(text),"parçalar birleşince aynı");
    ok(TezgahCloudPack.split(new byte[0],10).size()==1,"boş yedek tek parça");
    String other=text.replace("@created 123","@created 999");
    ok(TezgahCloudPack.hash(text).equals(TezgahCloudPack.hash(other)),"yalnız tarih değişince özet aynı");
    ok(!TezgahCloudPack.hash(text).equals(TezgahCloudPack.hash(text+"I9\tX\n")),"kayıt değişince özet değişir");
    try{ TezgahCloudPack.gunzip(new byte[]{1,2,3}); ok(false,"bozuk kabul edildi"); }catch(IllegalArgumentException e){ n++; }
    TezgahBackup.Data empty=TezgahBackup.parse("ZEYTIN-DEFTERI-YEDEK 1\n@created 1\n#TABLE tezgah_purchases\n#COLS id\n");
    ok(!TezgahCloudPack.hasRows(empty),"boş yedek buluta gitmez");
    ok(TezgahCloudPack.hasRows(TezgahBackup.parse("ZEYTIN-DEFTERI-YEDEK 1\n#TABLE tezgah_crates\n#COLS id\nI1\n")),"kayıt varsa gider");
    ok(TezgahCloudPack.PART_BYTES<1024*1024,"parça belge sınırının altında");
    System.out.println("PASS: "+n+" bulut yedeği paketleme kontrolü");
  }
}
