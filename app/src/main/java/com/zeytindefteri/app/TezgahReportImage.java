package com.zeytindefteri.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gün raporu resmi: alım tablosu + TOPLAM + Akşam hesabı (teslim, fire, komisyon, avans, tüccar ödeyecek).
 * Beyaz kâğıt görünümü; uygulamada yakınlaştırılarak bakılır, WhatsApp'tan resim olarak gönderilir.
 * Üreticilerin telefon numaraları paylaşılan resme konmaz.
 */
public final class TezgahReportImage {
    private TezgahReportImage() {}

    private static final String[] HEAD = {"Saat", "Üretici", "Kasa", "Double", "1 Numara", "2 Numara", "3 Numara",
            "4 Numara", "Yağlık", "Net kg", "Ort. ₺/kg", "Tutar", "Ödenen", "Kalan"};
    private static final int[] W = {78, 230, 66, 96, 122, 122, 122, 122, 96, 104, 116, 132, 132, 120};

    private static final int PAD = 40, HEAD_H = 50, ROW_H = 44;
    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int GREEN_DARK = Color.parseColor("#1B5E20");
    private static final int GREEN_SOFT = Color.parseColor("#C8EBB4");
    private static final int ZEBRA = Color.parseColor("#F4F9F5");
    private static final int GRID = Color.parseColor("#C9D6CF");
    private static final int INK = Color.parseColor("#1F2A27");
    private static final int MUTED = Color.parseColor("#6B7B76");
    private static final int AMBER = Color.parseColor("#B26A00");

    public static Bitmap draw(List<TezgahPurchases.Purchase> list, TezgahClosing closing, String title, String trader) {
        int tableW = 0;
        for (int w : W) tableW += w;
        int width = PAD * 2 + tableW;
        boolean showClosing = closing != null && !list.isEmpty();
        // Fire yoksa teslim/fire satırları alınanı tekrar etmesin: yalnız "Teslim · alınanla aynı" satırı.
        boolean same = true;
        if (showClosing) for (int g = 0; g < TezgahClosing.N; g++) same &= Math.abs(closing.fire(g)) < 0.05;
        int height = PAD + 44 + 34 + 16 + HEAD_H + ROW_H * (list.size() + 1)
                + (showClosing ? 30 + 44 + (same ? 0 : ROW_H * 2 + 18) + ROW_H * (same ? 5 : 4) : 0) + 70;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        TextPaint title1 = paint(34, GREEN_DARK, true);
        TextPaint sub = paint(20, MUTED, false);
        TextPaint head = paint(20, Color.WHITE, true);
        TextPaint cell = paint(21, INK, false);
        TextPaint bold = paint(21, INK, true);
        Paint fill = new Paint();
        Paint grid = new Paint();
        grid.setColor(GRID);
        grid.setStrokeWidth(1.5f);

        int y = PAD;
        c.drawText(title, PAD, y + 34, title1);
        y += 44;
        c.drawText("Zeytin Defteri · Tezgah alım defteri" + (trader == null || trader.isEmpty() ? "" : " · " + trader)
                + " · net kilo = kantar − kasa × dara", PAD, y + 24, sub);
        y += 34 + 16;

        // Sütun başlıkları
        fill.setColor(GREEN);
        c.drawRect(PAD, y, PAD + tableW, y + HEAD_H, fill);
        int x = PAD;
        for (int i = 0; i < HEAD.length; i++) {
            text(c, HEAD[i], x, y, W[i], HEAD_H, head, 1);
            x += W[i];
        }
        y += HEAD_H;

        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        double[] gsum = new double[TezgahModel.GRADES.length];
        int top = y;
        for (int r = 0; r < list.size(); r++) {
            TezgahPurchases.Purchase p = list.get(r);
            double[] g = TezgahModel.decode(p.data).netArray();
            for (int k = 0; k < g.length; k++) gsum[k] += g[k];
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;
            if (r % 2 == 1) { fill.setColor(ZEBRA); c.drawRect(PAD, y, PAD + tableW, y + ROW_H, fill); }
            String[] v = {hm.format(new Date(p.createdAt)), p.seller.isEmpty() ? "Adsız" : p.seller, String.valueOf(p.kasa),
                    kgCell(g[0]), kgCell(g[1]), kgCell(g[2]), kgCell(g[3]), kgCell(g[4]), kgCell(g[5]),
                    kgCell(p.kg), p.kg > 0 ? priceCell(p.gross / p.kg) : "—", moneyCell(p.gross), moneyCell(p.paid),
                    p.remaining() > 0.5 ? moneyCell(p.remaining()) : "—"};
            row(c, v, y, cell, p.remaining() > 0.5 ? AMBER : INK);
            y += ROW_H;
        }
        // TOPLAM
        fill.setColor(GREEN_SOFT);
        c.drawRect(PAD, y, PAD + tableW, y + ROW_H, fill);
        String[] t = {"", "", String.valueOf(kasa),
                kgCell(gsum[0]), kgCell(gsum[1]), kgCell(gsum[2]), kgCell(gsum[3]), kgCell(gsum[4]), kgCell(gsum[5]),
                kgCell(kg), kg > 0 ? priceCell(gross / kg) : "—", moneyCell(gross), moneyCell(paid),
                gross - paid > 0.5 ? moneyCell(gross - paid) : "—"};
        row(c, t, y, bold, INK);
        y += ROW_H;
        gridLines(c, grid, top - HEAD_H, y, tableW);
        // TOPLAM etiketi iki sütuna yayılır: aradaki çizgiyi toplam satırında kapat.
        fill.setColor(GREEN_SOFT);
        c.drawRect(PAD + W[0] - 2, y - ROW_H + 1, PAD + W[0] + 2, y - 1, fill);
        text(c, "TOPLAM · " + list.size() + " alım", PAD, y - ROW_H, W[0] + W[1], ROW_H, bold, 0);

        if (showClosing) {
            y += 30;
            c.drawText("AKŞAM HESABI · TÜCCARA TESLİM", PAD, y + 30, paint(28, GREEN_DARK, true));
            y += 44;
            int gx = PAD + W[0] + W[1] + W[2];
            int labelW = W[0] + W[1] + W[2];
            int sTop = y;
            if (!same) {
            // Teslim satırı
            fill.setColor(GREEN_SOFT);
            c.drawRect(PAD, y, PAD + labelW, y + ROW_H, fill);
            text(c, "Teslim (tüccar kantarı)", PAD, y, labelW, ROW_H, bold, 0);
            x = gx;
            for (int k = 0; k < TezgahClosing.N; k++) { text(c, kgCell(closing.delivered[k]), x, y, W[3 + k], ROW_H, cell, 2); x += W[3 + k]; }
            text(c, kgCell(closing.deliveredKg()), x, y, W[9], ROW_H, bold, 2);
            y += ROW_H;
            // Fire satırı
            fill.setColor(GREEN_SOFT);
            c.drawRect(PAD, y, PAD + labelW, y + ROW_H, fill);
            text(c, "Fire (alınan − teslim)", PAD, y, labelW, ROW_H, bold, 0);
            x = gx;
            for (int k = 0; k < TezgahClosing.N; k++) {
                double f = closing.fire(k);
                text(c, Math.abs(f) < 0.05 ? "—" : signed(f), x, y, W[3 + k], ROW_H, f > 0.05 ? paint(21, AMBER, false) : cell, 2);
                x += W[3 + k];
            }
            text(c, signed(closing.fireKg()), x, y, W[9], ROW_H, bold, 2);
            x += W[9];
            text(c, "%" + TezgahClosing.oneDecimal(closing.firePercent()), x, y, W[10], ROW_H, cell, 2);
            y += ROW_H;
            int sw = labelW + W[3] + W[4] + W[5] + W[6] + W[7] + W[8] + W[9] + W[10];
            box(c, grid, PAD, sTop, PAD + sw, y, ROW_H);
            y += 18;
            }

            int vw = 330, lw = 520, kx = PAD;
            int kTop = y;
            String[][] kvAll = {
                    {"Teslim", kgCell(closing.deliveredKg()) + " kg · alınanla aynı"},
                    {"Üreticilere tutar", moneyCell(closing.producerTotal) + " ₺"},
                    {"Komisyon (" + kgCell(closing.deliveredKg()) + " kg × " + priceCell(closing.rate) + " ₺)",
                            moneyCell(closing.commission()) + " ₺"},
                    {"Sabah alınan avans", closing.advance > 0 ? "− " + moneyCell(closing.advance) + " ₺" : "—"}};
            String[][] kv = same ? kvAll : java.util.Arrays.copyOfRange(kvAll, 1, kvAll.length);
            for (String[] row : kv) {
                text(c, row[0], kx, y, lw, ROW_H, cell, 0);
                text(c, row[1], kx + lw, y, vw, ROW_H, bold, 2);
                y += ROW_H;
            }
            double owes = closing.traderOwes();
            fill.setColor(GREEN_SOFT);
            c.drawRect(kx, y, kx + lw + vw, y + ROW_H, fill);
            text(c, owes >= 0 ? "TÜCCAR ÖDEYECEK" : "TEZGAH İADE EDECEK", kx, y, lw, ROW_H, bold, 0);
            text(c, moneyCell(Math.abs(owes)) + " ₺", kx + lw, y, vw, ROW_H, paint(23, owes >= 0 ? GREEN_DARK : AMBER, true), 2);
            y += ROW_H;
            box(c, grid, kx, kTop, kx + lw + vw, y, ROW_H);
        }
        return bmp;
    }

    // ------------------------------------------------------------------ çizim yardımcıları

    private static void row(Canvas c, String[] v, int y, TextPaint p, int lastColor) {
        int x = PAD;
        for (int i = 0; i < v.length; i++) {
            TextPaint use = p;
            if (i == v.length - 1 && lastColor != INK) { use = new TextPaint(p); use.setColor(lastColor); }
            text(c, v[i], x, y, W[i], ROW_H, use, i <= 1 ? 0 : 2);
            x += W[i];
        }
    }

    private static void gridLines(Canvas c, Paint grid, int top, int bottom, int tableW) {
        int x = PAD;
        for (int w : W) { c.drawLine(x, top, x, bottom, grid); x += w; }
        c.drawLine(PAD + tableW, top, PAD + tableW, bottom, grid);
        for (int y = top + HEAD_H; y <= bottom; y += ROW_H) c.drawLine(PAD, y, PAD + tableW, y, grid);
        c.drawLine(PAD, top, PAD + tableW, top, grid);
    }

    private static void box(Canvas c, Paint grid, int l, int t, int r, int b, int rowH) {
        c.drawLine(l, t, r, t, grid);
        for (int y = t + rowH; y <= b; y += rowH) c.drawLine(l, y, r, y, grid);
        c.drawLine(l, t, l, b, grid);
        c.drawLine(r, t, r, b, grid);
    }

    /** align: 0 sol, 1 orta, 2 sağ. Sığmayan metin "…" ile kısaltılır. */
    private static void text(Canvas c, String s, int x, int y, int w, int h, TextPaint p, int align) {
        int padX = 10;
        CharSequence t = TextUtils.ellipsize(s == null ? "" : s, p, w - padX * 2, TextUtils.TruncateAt.END);
        float tw = p.measureText(t, 0, t.length());
        float tx = align == 0 ? x + padX : align == 1 ? x + (w - tw) / 2f : x + w - padX - tw;
        Paint.FontMetrics fm = p.getFontMetrics();
        float ty = y + h / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(t, 0, t.length(), tx, ty, p);
    }

    private static TextPaint paint(int size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    private static String kgCell(double v) {
        if (Math.abs(v) < 0.05) return "—";
        String s = TezgahModel.kg(v);
        return s.substring(0, s.length() - 3);
    }

    private static String signed(double fire) {
        if (Math.abs(fire) < 0.05) return "—";
        return (fire > 0 ? "−" : "+") + kgCell(Math.abs(fire));
    }

    private static String moneyCell(double v) {
        String s = TezgahModel.tl(v);
        return s.substring(0, s.length() - 2);
    }

    private static String priceCell(double v) {
        String s = TezgahModel.money(v);
        return s.substring(0, s.length() - 2);
    }
}
