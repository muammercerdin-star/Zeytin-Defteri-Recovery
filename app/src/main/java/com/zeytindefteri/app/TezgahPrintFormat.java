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
    public static byte[] escpos(String receipt, boolean turkish) { return escpos(receipt, turkish, null, null); }

    /**
     * qrRaster null değilse metnin altına ortalanmış başlık satırı (caption) ve QR resmi basılır
     * (üretici fişteki QR'ı Elek kaydında "QR okut" ile okutur).
     */
    public static byte[] escpos(String receipt, boolean turkish, byte[] qrRaster, String caption) {
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
        if (qrRaster != null && qrRaster.length > 0) {
            out.write(0x1B); out.write(0x61); out.write(1);       // ESC a 1: ortala
            if (caption != null && !caption.isEmpty()) {
                byte[] c = turkish ? cp857(caption) : ascii(caption);
                out.write(c, 0, c.length);
                out.write(0x0A);
            }
            out.write(qrRaster, 0, qrRaster.length);
            out.write(0x0A);
            out.write(0x1B); out.write(0x61); out.write(0);       // ESC a 0: sola
        }
        out.write(0x1B); out.write(0x64); out.write(4);          // ESC d 4: 4 satır besle
        out.write(0x1D); out.write(0x56); out.write(0x42); out.write(0); // GS V 66 0: kes (kesicisi yoksa yok sayılır)
        return out.toByteArray();
    }

    /**
     * Siyah-beyaz resmi ESC/POS raster komutlarına çevirir (GS v 0), 64 satırlık şeritler halinde
     * (bazı yazıcıların tamponu küçük). px[y*w+x] true = siyah.
     */
    public static byte[] raster(boolean[] px, int w, int h) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bytesPerRow = (w + 7) / 8;
        for (int top = 0; top < h; top += 64) {
            int rows = Math.min(64, h - top);
            out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0);
            out.write(bytesPerRow & 0xFF); out.write((bytesPerRow >> 8) & 0xFF);
            out.write(rows & 0xFF); out.write((rows >> 8) & 0xFF);
            for (int y = top; y < top + rows; y++) {
                for (int bx = 0; bx < bytesPerRow; bx++) {
                    int v = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        if (x < w && px[y * w + x]) v |= 0x80 >> bit;
                    }
                    out.write(v);
                }
            }
        }
        return out.toByteArray();
    }

    /** QR modül matrisini (n×n) tam sayı katıyla en çok maxDots genişliğe büyütür; sonuç kare resim. */
    public static boolean[] scale(boolean[] modules, int n, int maxDots, int[] outSize) {
        int k = Math.max(1, maxDots / Math.max(1, n));
        int w = n * k;
        boolean[] px = new boolean[w * w];
        for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) px[y * w + x] = modules[(y / k) * n + (x / k)];
        outSize[0] = w;
        return px;
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
