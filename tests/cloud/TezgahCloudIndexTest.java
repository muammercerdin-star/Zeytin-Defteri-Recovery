package com.zeytindefteri.app;
import java.util.*;
public class TezgahCloudIndexTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static Map<String,Object> row(long created,int parts,String hash,String name){ return TezgahCloudIndex.row(created,parts,100,hash,"12 alım",name,12); }
  public static void main(String[] a){
    // belge adları
    ok(TezgahCloudIndex.partDoc("ab12cd34",0).equals("dab12cd34_p0"),"bölme parçası");
    ok(TezgahCloudIndex.partDoc(TezgahCloudIndex.LEGACY,3).equals("part_3")&&TezgahCloudIndex.partDoc("",1).equals("part_1"),"eski yedek parçası");
    ok(TezgahCloudIndex.cleanId("1B2C-3d4e/../5F6a7b8c9d0e1f2").equals("1b2c3d4e5f6a7b8c"),"kimlik temizlenir, 16 karakter");
    // dizin okuma, sıralama, bu telefon
    Map<String,Object> dev=new LinkedHashMap<>();
    dev.put("aaa11111",row(100,2,"h1","Samsung SM-A515F"));
    dev.put("bbb22222",row(300,1,"h2","Xiaomi Redmi Note 12"));
    dev.put("bozuk","x");
    dev.put("ccc33333",row(200,0,"h3","boş")); // parçasız sayılmaz
    List<TezgahCloudIndex.Entry> l=TezgahCloudIndex.parse(dev,"aaa11111");
    ok(l.size()==2,"geçerli 2 yedek");
    ok(l.get(0).device.equals("bbb22222")&&TezgahCloudIndex.latest(l).created==300,"en yeni başta");
    ok(TezgahCloudIndex.mine(l).device.equals("aaa11111")&&TezgahCloudIndex.label(TezgahCloudIndex.mine(l)).equals("Bu telefon"),"bu telefon");
    ok(TezgahCloudIndex.label(l.get(0)).equals("Xiaomi Redmi Note 12"),"başka telefonun adı");
    ok(l.get(1).rows==12&&l.get(1).summary.equals("12 alım"),"satır bilgisi");
    ok(TezgahCloudIndex.parse(null,"x").isEmpty(),"dizin yok");
    // başka telefonda daha yeni yedek
    ok(TezgahCloudIndex.newerElsewhere(l,100,"h1").device.equals("bbb22222"),"Xiaomi daha yeni");
    ok(TezgahCloudIndex.newerElsewhere(l,300,"h1")==null,"geri yükleyip bildiğimiz hal: uyarı yok");
    ok(TezgahCloudIndex.newerElsewhere(l,100,"h2")==null,"aynı içerik: uyarı yok");
    // eski (bölmesiz) yedek
    Map<String,Object> meta=new HashMap<>(); meta.put("created",50L); meta.put("parts",3L); meta.put("hash","h0"); meta.put("summary","500 alım");
    TezgahCloudIndex.addLegacy(l,meta);
    ok(l.size()==3&&l.get(2).device.equals(TezgahCloudIndex.LEGACY)&&l.get(2).partDoc(0).equals("part_0"),"eski yedek listede, part_N");
    ok(TezgahCloudIndex.label(l.get(2)).equals("Önceki yedek"),"eski yedek adı");
    ok(TezgahCloudIndex.newerElsewhere(l,0,"zz").device.equals("bbb22222"),"eski yedek uyarı üretmez, diğeri üretir");
    TezgahCloudIndex.addLegacy(l,meta); ok(l.size()==3,"iki kez eklenmez");
    Map<String,Object> newMeta=new HashMap<>(meta); newMeta.put("device","aaa11111");
    List<TezgahCloudIndex.Entry> l2=TezgahCloudIndex.parse(dev,"aaa11111"); TezgahCloudIndex.addLegacy(l2,newMeta);
    ok(l2.size()==2,"yeni sürümün meta'sı eski yedek sayılmaz");
    // en fazla 5 bölme
    Map<String,Object> many=new LinkedHashMap<>();
    for(int i=1;i<=7;i++) many.put("d"+i+"xxxxxx",row(i*10,1,"h"+i,"T"+i));
    List<TezgahCloudIndex.Entry> lm=TezgahCloudIndex.parse(many,"d7xxxxxx");
    List<TezgahCloudIndex.Entry> ev=TezgahCloudIndex.evict(lm,"d7xxxxxx");
    ok(ev.size()==2&&ev.get(0).device.equals("d2xxxxxx")&&ev.get(1).device.equals("d1xxxxxx"),"7 bölme: en eski 2 silinir");
    ok(TezgahCloudIndex.evict(lm,"yeni0000").size()==3,"yeni telefon: 4 başka kalır, 3 silinir");
    ok(TezgahCloudIndex.evict(l,"aaa11111").isEmpty(),"sınır altında silinmez");
    boolean keepsMine=true; for(TezgahCloudIndex.Entry e:ev) if(e.mine) keepsMine=false;
    ok(keepsMine,"kendi yedeği hiç silinmez");
    System.out.println("PASS: "+n+" bulut yedeği dizin kontrolü");
  }
}
