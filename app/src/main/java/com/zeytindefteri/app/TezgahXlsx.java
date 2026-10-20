package com.zeytindefteri.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Alım defterini hazır biçimli Excel (.xlsx) dosyası olarak üretir; ek kütüphane gerekmez (xlsx bir zip + XML'dir).
 *
 * Şablon: 1. satır başlık (tarih), 2. satır yeşil sütun başlıkları, altında her alım bir satır, en altta TOPLAM.
 * - Sayılar gerçek sayı: kg "1.234,5", para "27.220 ₺", ortalama "64,35 ₺" biçiminde (Excel/WPS/E-Tablolar dil ayarına göre gösterir).
 * - Ortalama ve Kalan satırda formül (=Tutar/Net, =Tutar−Ödenen); TOPLAM satırı =TOPLA(…) formülleri.
 *   Hücre değerleri de yazılır, formül hesaplamayan görüntüleyicilerde de rakamlar görünür.
 * - Başlık satırı ve ilk iki sütun dondurulur, sütunlara filtre konur.
 */
public final class TezgahXlsx {
    private TezgahXlsx() {}

    static final String[] HEAD = {"Saat", "Üretici", "Telefon", "Kasa", "Double kg", "1 Numara kg", "2 Numara kg",
            "3 Numara kg", "4 Numara kg", "Yağlık kg", "Net kg", "Ort. ₺/kg", "Tutar ₺", "Ödenen ₺", "Kalan ₺"};
    private static final double[] WIDTH = {8, 22, 15, 7, 11, 12, 12, 12, 12, 11, 11, 11, 13, 13, 12};

    // Stil numaraları (styles.xml cellXfs sırası)
    private static final int S_TITLE = 1, S_HEAD = 2, S_TEXT = 3, S_INT = 4, S_KG = 5, S_MONEY = 6, S_PRICE = 7,
            S_T_TEXT = 8, S_T_INT = 9, S_T_KG = 10, S_T_MONEY = 11, S_T_PRICE = 12, S_SUB = 13;

    public static byte[] build(List<TezgahPurchases.Purchase> list, String title) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", RELS);
            put(zip, "xl/workbook.xml", WORKBOOK);
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(list, title));
        }
        return bytes.toByteArray();
    }

    static String sheet(List<TezgahPurchases.Purchase> list, String title) {
        int first = 3, last = first + list.size() - 1, total = last + 1;
        StringBuilder x = new StringBuilder(4096);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
                .append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane xSplit=\"2\" ySplit=\"2\" topLeftCell=\"C3\" activePane=\"bottomRight\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"18\"/><cols>");
        for (int c = 0; c < WIDTH.length; c++) {
            x.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                    .append(WIDTH[c]).append("\" customWidth=\"1\"/>");
        }
        x.append("</cols><sheetData>");

        // 1: başlık
        x.append("<row r=\"1\" ht=\"26\" customHeight=\"1\">").append(str("A1", title, S_TITLE)).append("</row>");
        // 2: sütun başlıkları
        x.append("<row r=\"2\" ht=\"22\" customHeight=\"1\">");
        for (int c = 0; c < HEAD.length; c++) x.append(str(col(c) + 2, HEAD[c], S_HEAD));
        x.append("</row>");

        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        double[] gsum = new double[TezgahModel.GRADES.length];
        int r = first;
        for (TezgahPurchases.Purchase p : list) {
            double[] g = TezgahModel.decode(p.data).netArray();
            x.append("<row r=\"").append(r).append("\">")
                    .append(str("A" + r, hm.format(new Date(p.createdAt)), S_TEXT))
                    .append(str("B" + r, p.seller.isEmpty() ? "Adsız" : p.seller, S_TEXT))
                    .append(str("C" + r, p.phone, S_TEXT))
                    .append(num("D" + r, p.kasa, S_INT));
            for (int k = 0; k < g.length; k++) {
                x.append(num(col(4 + k) + r, round1(g[k]), S_KG));
                gsum[k] += g[k];
            }
            x.append(num("K" + r, round1(p.kg), S_KG))
                    .append(formula("L" + r, "IF(K" + r + ">0,M" + r + "/K" + r + ",0)", p.kg > 0 ? p.gross / p.kg : 0, S_PRICE))
                    .append(num("M" + r, round2(p.gross), S_MONEY))
                    .append(num("N" + r, round2(p.paid), S_MONEY))
                    .append(formula("O" + r, "M" + r + "-N" + r, round2(p.remaining()), S_MONEY))
                    .append("</row>");
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;
            r++;
        }

        // TOPLAM satırı
        x.append("<row r=\"").append(total).append("\" ht=\"22\" customHeight=\"1\">")
                .append(str("A" + total, "TOPLAM", S_T_TEXT))
                .append(str("B" + total, list.size() + " alım", S_T_TEXT))
                .append(str("C" + total, "", S_T_TEXT))
                .append(sum("D", first, last, total, kasa, S_T_INT));
        for (int k = 0; k < gsum.length; k++) x.append(sum(col(4 + k), first, last, total, round1(gsum[k]), S_T_KG));
        x.append(sum("K", first, last, total, round1(kg), S_T_KG))
                .append(formula("L" + total, "IF(K" + total + ">0,M" + total + "/K" + total + ",0)", kg > 0 ? gross / kg : 0, S_T_PRICE))
                .append(sum("M", first, last, total, round2(gross), S_T_MONEY))
                .append(sum("N", first, last, total, round2(paid), S_T_MONEY))
                .append(formula("O" + total, "M" + total + "-N" + total, round2(Math.max(0, gross - paid)), S_T_MONEY))
                .append("</row>");
        int note = total + 2;
        x.append("<row r=\"").append(note).append("\">")
                .append(str("A" + note, "Zeytin Defteri · Tezgah alım defteri · net kilo = kantar − kasa × dara", S_SUB))
                .append("</row>");
        x.append("</sheetData>")
                .append("<autoFilter ref=\"A2:O").append(Math.max(2, last)).append("\"/>")
                .append("<mergeCells count=\"1\"><mergeCell ref=\"A1:O1\"/></mergeCells>")
                .append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>")
                .append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
                .append("</worksheet>");
        return x.toString();
    }

    // ------------------------------------------------------------------ hücreler

    static String col(int index) {
        StringBuilder s = new StringBuilder();
        int n = index + 1;
        while (n > 0) { int m = (n - 1) % 26; s.insert(0, (char) ('A' + m)); n = (n - 1) / 26; }
        return s.toString();
    }

    private static String str(String ref, String value, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + esc(value) + "</t></is></c>";
    }

    private static String num(String ref, double value, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + plain(value) + "</v></c>";
    }

    private static String formula(String ref, String f, double cached, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><f>" + f + "</f><v>" + plain(round2(cached)) + "</v></c>";
    }

    private static String sum(String c, int first, int last, int row, double cached, int style) {
        if (last < first) return num(c + row, 0, style);
        return formula(c + row, "SUM(" + c + first + ":" + c + last + ")", cached, style);
    }

    static String plain(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    // XML'de geçersiz denetim karakterleri atılır.
                    if (ch >= 0x20 || ch == '\t') b.append(ch);
            }
        }
        return b.toString();
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ------------------------------------------------------------------ sabit parçalar

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Alım defteri\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
            + "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">'Alım defteri'!$A$2:$O$2</definedName></definedNames>"
            + "<calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/>"
            + "</workbook>";

    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    private static final String BORDER = "<border><left style=\"thin\"><color rgb=\"FFC9D6CF\"/></left>"
            + "<right style=\"thin\"><color rgb=\"FFC9D6CF\"/></right><top style=\"thin\"><color rgb=\"FFC9D6CF\"/></top>"
            + "<bottom style=\"thin\"><color rgb=\"FFC9D6CF\"/></bottom><diagonal/></border>";

    private static String xf(int numFmt, int font, int fill, int border, String align) {
        return "<xf numFmtId=\"" + numFmt + "\" fontId=\"" + font + "\" fillId=\"" + fill + "\" borderId=\"" + border
                + "\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment " + align + " vertical=\"center\"/></xf>";
    }

    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<numFmts count=\"3\">"
            + "<numFmt numFmtId=\"164\" formatCode=\"#,##0&quot; ₺&quot;;-#,##0&quot; ₺&quot;;&quot;—&quot;\"/>"
            + "<numFmt numFmtId=\"165\" formatCode=\"#,##0.00&quot; ₺&quot;\"/>"
            + "<numFmt numFmtId=\"166\" formatCode=\"#,##0.0;-#,##0.0;&quot;—&quot;\"/>"
            + "</numFmts>"
            + "<fonts count=\"5\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"15\"/><color rgb=\"FF1B5E20\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FF1B3A1F\"/><name val=\"Calibri\"/></font>"
            + "<font><i/><sz val=\"9\"/><color rgb=\"FF6B7B76\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"4\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2E7D32\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFC8EBB4\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" + BORDER + "</borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"14\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"          // 0
            + xf(0, 1, 0, 0, "horizontal=\"left\"")                                               // 1 başlık
            + xf(0, 2, 2, 1, "horizontal=\"center\" wrapText=\"1\"")                              // 2 sütun başlığı
            + xf(0, 0, 0, 1, "horizontal=\"left\"")                                               // 3 metin
            + xf(3, 0, 0, 1, "horizontal=\"right\"")                                              // 4 tam sayı
            + xf(166, 0, 0, 1, "horizontal=\"right\"")                                            // 5 kg
            + xf(164, 0, 0, 1, "horizontal=\"right\"")                                            // 6 para
            + xf(165, 0, 0, 1, "horizontal=\"right\"")                                            // 7 kg fiyatı
            + xf(0, 3, 3, 1, "horizontal=\"left\"")                                               // 8 toplam metin
            + xf(3, 3, 3, 1, "horizontal=\"right\"")                                              // 9 toplam tam sayı
            + xf(166, 3, 3, 1, "horizontal=\"right\"")                                            // 10 toplam kg
            + xf(164, 3, 3, 1, "horizontal=\"right\"")                                            // 11 toplam para
            + xf(165, 3, 3, 1, "horizontal=\"right\"")                                            // 12 toplam kg fiyatı
            + xf(0, 4, 0, 0, "horizontal=\"left\"")                                               // 13 dipnot
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
