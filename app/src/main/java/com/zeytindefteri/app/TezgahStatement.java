package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Üretici ekstresi (saf, Android'siz): bir üreticinin bütün tezgah alımları; toplam kilo/tutar/ödenen/borç,
 * numara numara kilo, alım alım döküm, WhatsApp metni ve toplu ödemenin en eski borçtan başlayarak dağıtımı.
 * Üretici adla eşleşir (Türkçe harf ve büyük/küçük farkı gözetilmez); "Münferit satıcı" ve adsızın ekstresi olmaz.
 */
public final class TezgahStatement {
    public String name = "", phone = "";
    /** Eskiden yeniye. */
    public final List<TezgahPurchases.Purchase> purchases = new ArrayList<>();
    public final double[] gradeKg = new double[TezgahModel.GRADES.length];
    public double kg, gross, paid;
    public int kasa;

    public double remaining() { return Math.max(0, gross - paid); }

    public double averagePrice() { return kg > 0 ? gross / kg : 0; }

    public int debtCount() {
        int n = 0;
        for (TezgahPurchases.Purchase p : purchases) if (p.remaining() > 0.5) n++;
        return n;
    }

    public static boolean hasStatement(String seller) {
        String k = TezgahSearch.fold(seller);
        return !k.isEmpty() && !k.equals(TezgahSearch.fold(TezgahWizardFlow.WALK_IN));
    }

    /** all: yeniden eskiye bütün alımlar (TezgahPurchases.all). */
    public static TezgahStatement of(List<TezgahPurchases.Purchase> all, String seller) {
        TezgahStatement s = new TezgahStatement();
        String key = TezgahSearch.fold(seller);
        if (!hasStatement(seller) || all == null) return s;
        for (int i = all.size() - 1; i >= 0; i--) {
            TezgahPurchases.Purchase p = all.get(i);
            if (!TezgahSearch.fold(p.seller).equals(key)) continue;
            s.purchases.add(p);
            if (s.name.isEmpty()) s.name = p.seller.trim();
            if (!p.phone.trim().isEmpty()) s.phone = p.phone.trim(); // en yeni telefon kalır
            s.kg += p.kg;
            s.kasa += p.kasa;
            s.gross += p.gross;
            s.paid += p.paid;
            TezgahModel m = TezgahModel.decode(p.data);
            for (int g = 0; g < s.gradeKg.length; g++) s.gradeKg[g] += m.gradeNet(g);
        }
        if (s.name.isEmpty()) s.name = seller.trim();
        return s;
    }

    /** Toplu ödemenin dağıtımı: en eski borçtan başlar. Dönüş {purchaseId, tutar} çiftleri; artan para dağıtılmaz. */
    public List<long[]> allocate(double amount) {
        List<long[]> out = new ArrayList<>();
        double left = amount;
        for (TezgahPurchases.Purchase p : purchases) {
            if (left <= 0.009) break;
            double owe = p.remaining();
            if (owe <= 0.5) continue;
            double take = Math.min(owe, left);
            out.add(new long[]{p.id, Math.round(take * 100)}); // kuruş olarak
            left -= take;
        }
        return out;
    }

    /** WhatsApp'tan gönderilecek ekstre metni. dates: her alımın gösterilecek tarihi (purchases ile aynı sırada). */
    public String text(String trader, List<String> dates, String today) {
        StringBuilder b = new StringBuilder();
        b.append("🫒 Üretici ekstresi\n");
        b.append("Üretici: ").append(name).append('\n');
        if (trader != null && !trader.trim().isEmpty()) b.append("Alan: ").append(trader.trim()).append('\n');
        if (today != null && !today.isEmpty()) b.append("Tarih: ").append(today).append('\n');
        b.append('\n');
        for (int i = 0; i < purchases.size(); i++) {
            TezgahPurchases.Purchase p = purchases.get(i);
            String d = dates != null && i < dates.size() ? dates.get(i) : p.date;
            b.append(d).append(" · ").append(TezgahModel.kg(p.kg)).append(" · ").append(TezgahModel.tl(p.gross));
            if (p.remaining() > 0.5) b.append(" · kalan ").append(TezgahModel.tl(p.remaining()));
            else b.append(" · ödendi");
            b.append('\n');
        }
        b.append('\n');
        for (int g = 0; g < gradeKg.length; g++) {
            if (gradeKg[g] <= 0) continue;
            b.append(TezgahModel.LABELS[g]).append(": ").append(TezgahModel.kg(gradeKg[g])).append('\n');
        }
        b.append('\n');
        b.append(purchases.size()).append(" alım · ").append(kasa).append(" kasa · net ").append(TezgahModel.kg(kg)).append('\n');
        b.append("Toplam: ").append(TezgahModel.tl(gross)).append('\n');
        b.append("Ödenen: ").append(TezgahModel.tl(paid)).append('\n');
        b.append(remaining() > 0.5 ? "KALAN BORÇ: " + TezgahModel.tl(remaining()) : "Borç yok ✓").append('\n');
        b.append("\nZeytin Defteri ile hazırlandı");
        return b.toString();
    }
}
