package com.zeytindefteri.app;
public class TezgahPrintFormatTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    TezgahModel m=new TezgahModel(); m.add(0,2,60); m.setPrice(0,130); m.add(5,1,22); m.setPrice(5,15);
    for(int w: new int[]{32,48}){
      String r=TezgahPrintFormat.receipt(m,"Tezgah Şükrü","Ali Özdemir","24.10.2026 20:01",1000,w);
      for(String line: r.split("\n")) ok(TezgahPrintFormat.fold(line).length()<=w,"satır genişliği "+w+": '"+line+"'");
      ok(r.contains("TOPLAM")&&r.contains("BORÇ")&&!r.contains("₺"),"toplam/borç, ₺ yok");
    }
    String paid=TezgahPrintFormat.receipt(m,"","", "",m.total(),32);
    ok(paid.contains("tamami odendi")&&paid.contains("Satan: Münferit satıcı"),"ödendi ve münferit");
    ok(TezgahPrintFormat.fold("Şükrü Işık ığüöçİ ₺").equals("Sukru Isik iguocI TL"),"sadeleştirme");
    byte[] b=TezgahPrintFormat.escpos("BASLIK\nşı\n",false);
    ok(b[0]==0x1B&&b[1]==0x40,"ESC @"); ok(b[2]==0x1B&&b[3]==0x21&&b[4]==0x30,"büyük başlık");
    ok(new String(b,java.nio.charset.StandardCharsets.US_ASCII).contains("si\n"),"ASCII Türkçe sadeleşir");
    ok(b[b.length-4]==0x1D&&b[b.length-3]==0x56,"kes komutu");
    byte[] t=TezgahPrintFormat.escpos("X\nşğı\n",true);
    ok(t[2]==0x1B&&t[3]==0x74&&t[4]==13,"PC857 kod sayfası");
    boolean found=false; for(int i=0;i+2<t.length;i++) if((t[i]&0xff)==0x9F&&(t[i+1]&0xff)==0xA7&&(t[i+2]&0xff)==0x8D) found=true;
    ok(found,"ş ğ ı CP857 baytları");
    ok(TezgahPrintFormat.row("Sol","Sağ",10).equals("Sol    Sağ"),"satır hizası");
    ok(TezgahPrintFormat.row("Çok uzun bir sol metin","123",10).length()==10,"uzun sol kırpılır");
    String full=TezgahPrintFormat.receipt(m,"A","B","t",0,32);
    ok(!TezgahPrintFormat.fold(full).contains("?"),"bölünmez boşluk soru işareti olmaz: "+TezgahPrintFormat.fold(full));
    ok(!new String(TezgahPrintFormat.escpos(full,true),java.nio.charset.StandardCharsets.ISO_8859_1).contains("?"),"CP857'de de soru işareti yok");
    // QR raster: 3x3 modül -> 10 katı 30x30, 64 satırlık şerit, bitler geri okunur
    boolean[] mod={true,false,true, false,true,false, true,true,false};
    int[] size=new int[1]; boolean[] px=TezgahPrintFormat.scale(mod,3,32,size);
    ok(size[0]==30,"tam sayı katı ölçek: "+size[0]);
    ok(px[0]&&!px[10]&&px[20]&&px[11*30+11]&&!px[29*30+29],"büyütülmüş modüller");
    byte[] r=TezgahPrintFormat.raster(px,30,30);
    ok(r[0]==0x1D&&r[1]==0x76&&r[2]==0x30&&r[4]==4&&r[6]==30,"GS v 0 başlığı (4 bayt/satır, 30 satır)");
    ok(r.length==8+4*30,"veri boyu");
    ok((r[8]&0xff)==0xFF&&(r[9]&0x80)==0x80,"ilk satır: ilk 10 nokta siyah");
    boolean[] tall=new boolean[8*130]; byte[] t2=TezgahPrintFormat.raster(tall,8,130);
    ok(t2.length==3*8+130,"130 satır 3 şeride bölünür");
    byte[] withQr=TezgahPrintFormat.escpos("X\n",false,r,"Uretici okutsun");
    String sx=new String(withQr,java.nio.charset.StandardCharsets.ISO_8859_1);
    ok(sx.contains("\u001ba\u0001Uretici okutsun\n\u001dv0"),"ortalanmış başlık ve QR");
    ok(TezgahPrintFormat.escpos("X\n",false,null,"Y").length==TezgahPrintFormat.escpos("X\n",false).length,"QR yoksa eski fiş");
    System.out.println("PASS: "+n+" fiş yazıcısı kontrolü");
  }
}
