package com.zeytindefteri.app;
import java.util.*;
public class ChatTypingTest {
  static int n; static void eq(Object a,Object b,String m){ if(!Objects.equals(a,b)) throw new AssertionError(m+": "+a+" != "+b); n++; }
  static ChatTypingRepository.Typer t(String uid,String name,String type){ ChatTypingRepository.Typer x=new ChatTypingRepository.Typer(); x.uid=uid; x.name=name; x.postType=type; return x; }
  public static void main(String[] a){
    List<ChatTypingRepository.Typer> one=Arrays.asList(t("u1","Yıldırım Yıldırım","GENERAL"));
    eq(ChatTypingRepository.label(one,"ALL"),"Yıldırım yazıyor","tek kişi, ad kısaltılır");
    eq(ChatTypingRepository.label(Arrays.asList(t("u1","Yusuf Soyaslan","MARKET")),"GENERAL"),"Yusuf (#Piyasa) yazıyor","başka kanal belirtilir");
    eq(ChatTypingRepository.label(Arrays.asList(t("u1","Ali","GENERAL"),t("u2","Veli","GENERAL")),"ALL"),"Ali ve Veli yazıyor","iki kişi");
    eq(ChatTypingRepository.label(Arrays.asList(t("a","A",""),t("b","B",""),t("c","C","")),"ALL"),"3 kişi yazıyor","çok kişi");
    eq(ChatTypingRepository.label(new ArrayList<>(),"ALL"),"","kimse yok");
    eq(ChatTypingRepository.firstName("  "),"Bir üretici","boş ad");
    Map<String,Long> until=new HashMap<>(); until.put("u1",2000L); until.put("u2",500L); until.put("u3",3000L);
    List<ChatTypingRepository.Typer> all=Arrays.asList(t("u1","Zeki",""),t("u2","Eski",""),t("u3","Ahmet",""));
    Set<String> blocked=new HashSet<>(Collections.singletonList("u3"));
    List<ChatTypingRepository.Typer> v=ChatTypingRepository.visible(all,until,blocked,1000L);
    eq(v.size(),1,"süresi geçen ve engellenen gizlenir"); eq(v.get(0).uid,"u1","kalan");
    eq(ChatTypingRepository.visible(all,until,null,1000L).get(0).name,"Ahmet","ada göre sıralı");
    eq(ChatTypingRepository.PING_MS,5000L,"5 sn seyreltme"); eq(ChatTypingRepository.TTL_MS,10000L,"10 sn süre");
    System.out.println("PASS: "+n+" yazıyor göstergesi kontrolü");
  }
}
