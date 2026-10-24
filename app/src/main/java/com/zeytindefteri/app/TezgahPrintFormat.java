package com.zeytindefteri.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Termal fiş yazıcısı (ESC/POS, 58 mm = 32 harf, 80 mm = 48 harf) için alım fişi düzeni ve bayt çevirisi.
 * Saf (Android'siz); Bluetooth bağlantısı TezgahPrinter'dadır.
 *
 * Türkçe karakter: varsayılan olarak sadeleştirilir (ş→s, İ→I…) çünkü ucuz yazıcıların çoğu Türkçe kod sayfasını
 * bilmez. Ayardan açılırsa CP857 (PC857 Türkçe, ESC t 13) gönderilir.
 */
public final class TezgahPrintFormat {
    public static final int WIDTH_58 = 32, WIDTH_80 = 48;
    /** ESC t n: Epson uyumlu yazıcılarda PC857 Türkçe kod sayfası. */
    static final int CODEPAGE_PC857 = 13;

    private TezgahPrintFormat() {}

    /** Alım fişi metni (satır sonlu, genişliğe göre hizalı). İlk satır başlıktır (yazıcıda büyük basılır). */
    public static String receipt(TezgahModel m, String trader, String seller, String date, double paid, int width) {
        int w = width >= WIDTH_80 ? WIDTH_80 : WIDTH_58;
        StringBuilder b = new StringBuilder();
        b.append(center("ZEYTIN ALIM FISI", w / 2)).append('\n'); // büyük harf: çift genişlik yarı yer kaplar
        if (trader != null && !trader.trim().isEmpty()) b.append(fit("Tezgah: " + trader.trim(), w)).append('\n');
        if (date != null && !date.isEmpty()) b.append(fit("Tarih: " + date, w)).append('\n');
        String who = seller == null || seller.trim().isEmpty() ? TezgahWizardFlow.WALK_IN : seller.trim();
        b.append(fit("Satan: " + who, w)).append('\n');
        b.append(line(w)).append('\n');
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            double n = m.gradeNet(g);
            if (n <= 0) continue;
            b.append(row(TezgahModel.LABELS[g], m.gradeKasa(g) + " kasa", w)).append('\n');
            String left = "  " + TezgahModel.kg(n) + (m.price(g) > 0 ? " x " + money(m.price(g)).replace(" TL", "") : "");
            b.append(row(left, tl(m.gradeAmount(g)), w)).append('\n');
        }
        b.append(line(w)).append('\n');
        b.append(row("Kasa " + m.totalKasa() + "  Brut " + TezgahModel.kg(m.totalGross()), "Dara " + TezgahModel.kg(m.totalTare()), w)).append('\n');
        b.append(row("NET", TezgahModel.kg(m.totalNet()), w)).append('\n');
        b.append(row("TOPLAM", tl(m.total()), w)).append('\n');
        if (m.averagePrice() > 0) b.append(row("Ortalama", money(m.averagePrice()) + "/kg", w)).append('\n');
        double t = m.total();
        if (paid >= t - 0.009 && t > 0) {
            b.append(row("Odeme", "tamami odendi", w)).append('\n');
        } else {
            b.append(row("Odenen", tl(Math.max(0, paid)), w)).append('\n');
            b.append(row("KALAN", tl(Math.max(0, t - paid)), w)).append('\n');
        }
        b.append(line(w)).append('\n');
        b.append(center("Zeytin Defteri", w)).append('\n');
        return b.toString();
    }

    /** Yazıcıya gidecek baytlar: başlat, (Türkçe kod sayfası), büyük başlık, metin, kâğıt besle, kes. */
    public static byte[] escpos(String receipt, boolean turkish) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1B); out.write(0x40);                       // ESC @ başlat
        if (turkish) { out.write(0x1B); out.write(0x74); out.write(CODEPAGE_PC857); } // ESC t 13
        String[] lines = receipt.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) break;
            if (i == 0) { out.write(0x1B); out.write(0x21); out.write(0x30); } // ESC ! çift en+boy
            byte[] text = turkish ? cp857(lines[i]) : ascii(lines[i]);
            out.write(text, 0, text.length);
            out.write(0x0A);
            if (i == 0) { out.write(0x1B); out.write(0x21); out.write(0x00); }
        }
        out.write(0x1B); out.write(0x64); out.write(4);          // ESC d 4: 4 satır besle
        out.write(0x1D); out.write(0x56); out.write(0x42); out.write(0); // GS V 66 0: kes (kesicisi yoksa yok sayılır)
        return out.toByteArray();
    }

    /** Türkçe harfleri sadeleştirir; ASCII dışı kalan her şey '?' olur. ₺ → TL. */
    public static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ş': b.append('s'); break; case 'Ş': b.append('S'); break;
                case 'ı': b.append('i'); break; case 'İ': b.append('I'); break;
                case 'ğ': b.append('g'); break; case 'Ğ': b.append('G'); break;
                case 'ü': b.append('u'); break; case 'Ü': b.append('U'); break;
                case 'ö': b.append('o'); break; case 'Ö': b.append('O'); break;
                case 'ç': b.append('c'); break; case 'Ç': b.append('C'); break;
                case 'â': b.append('a'); break; case 'Â': b.append('A'); break;
                case '₺': b.append("TL"); break;
                case '×': b.append('x'); break;
                case '·': b.append('-'); break;
                // Sayı biçimlendirmenin bölünmez boşlukları ("44 kg", "5.720 ₺") yazıcıda '?' çıkmasın.
                case '\u00A0': case '\u202F': case '\u2007': case '\u2009': b.append(' '); break;
                default: b.append(c < 128 ? c : '?');
            }
        }
        return b.toString();
    }

    static byte[] ascii(String s) { return fold(s).getBytes(StandardCharsets.US_ASCII); }

    /** CP857 (Türkçe DOS kod sayfası) baytları; tabloda olmayan ASCII dışı karakter sadeleştirilir. */
    static byte[] cp857(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int code;
            switch (c) {
                case 'Ç': code = 0x80; break; case 'ü': code = 0x81; break; case 'ç': code = 0x87; break;
                case 'ı': code = 0x8D; break; case 'ö': code = 0x94; break; case 'İ': code = 0x98; break;
                case 'Ö': code = 0x99; break; case 'Ü': code = 0x9A; break; case 'Ş': code = 0x9E; break;
                case 'ş': code = 0x9F; break; case 'Ğ': code = 0xA6; break; case 'ğ': code = 0xA7; break;
                default: code = -1;
            }
            if (code >= 0) { out.write(code); continue; }
            byte[] f = fold(String.valueOf(c)).getBytes(StandardCharsets.US_ASCII);
            out.write(f, 0, f.length);
        }
        return out.toByteArray();
    }

    /** Fişte ₺ yerine "TL" (yazıcılar ₺ basamaz; genişlik hesabı da doğru kalsın). */
    static String tl(double v) { return TezgahModel.tl(v).replace("₺", "TL"); }

    static String money(double v) { return TezgahModel.money(v).replace("₺", "TL"); }

    /** Solda metin, sağda değer; sığmazsa sol kısaltılır. */
    static String row(String left, String right, int w) {
        String r = right == null ? "" : right;
        int room = w - r.length() - 1;
        String l = left == null ? "" : left;
        if (room < 1) return fit(r, w);
        if (l.length() > room) l = l.substring(0, room);
        StringBuilder b = new StringBuilder(l);
        while (b.length() + r.length() < w) b.append(' ');
        return b.append(r).toString();
    }

    static String center(String s, int w) {
        String t = fit(s, w);
        int pad = Math.max(0, (w - t.length()) / 2);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pad; i++) b.append(' ');
        return b.append(t).toString();
    }

    static String fit(String s, int w) { return s.length() <= w ? s : s.substring(0, w); }

    static String line(int w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w; i++) b.append('-');
        return b.toString();
    }
}
