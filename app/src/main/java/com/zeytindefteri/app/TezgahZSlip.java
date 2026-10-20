package com.zeytindefteri.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Z raporu fişi: POS gün sonu fişi görünümünde dar, uzun beyaz kâğıt (alt kenarı yırtık).
 * Bölümler: başlık · dönem · numara dökümü · toplamlar · tüccara teslim · üretici listesi · kilit notu.
 * Üretici telefon numaraları fişe konmaz.
 */
public final class TezgahZSlip {
    private TezgahZSlip() {}

    private static final int W = 640, PAD = 36, LINE_H = 34;
    private static final int INK = Color.parseColor("#1D1D1D");
    private static final int MUTED = Color.parseColor("#6E6E6E");
    private static final Locale TR = new Locale("tr", "TR");

    /** Fiş satırı: sol metin, sağ metin, stil. */
    private static final class L {
        final String left, right;
        final int kind; // 0 normal, 1 kalın, 2 büyük kalın ortalı, 3 ortalı, 4 kesik çizgi, 5 boşluk, 6 küçük soluk ortalı, 7 çok büyük toplam
        L(String l, String r, int k) { left = l; right = r; kind = k; }
    }

    public static Bitmap draw(TezgahZ.Record z, List<TezgahPurchases.Purchase> list, TezgahClosing c, String trader) {
        SimpleDateFormat dt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        List<L> lines = new ArrayList<>();
        lines.add(new L("ZEYTİN DEFTERİ", "", 2));
        lines.add(new L("TEZGAH · ALIM", "", 3));
        if (trader != null && !trader.isEmpty()) lines.add(new L(trader, "", 3));
        lines.add(new L("", "", 4));
        lines.add(new L("Z RAPORU", "No: " + TezgahZ.pad(z.number), 1));
        lines.add(new L("Tarih", dt.format(new Date(z.closedAt)), 0));
        lines.add(new L("Dönem", hm.format(new Date(z.firstAt)) + " – " + hm.format(new Date(z.lastAt))
                + (sameDay(z.firstAt, z.closedAt) ? "" : " (" + new SimpleDateFormat("dd.MM", Locale.US).format(new Date(z.firstAt)) + "'den)"), 0));
        lines.add(new L("", "", 4));
        lines.add(new L("NUMARA", "KG    ·    TUTAR", 1));
        for (int g = 0; g < TezgahClosing.N; g++) {
            if (c.bought[g] <= 0) continue;
            lines.add(new L(TezgahModel.LABELS[g], kg(c.bought[g]) + "  ·  " + TezgahModel.tl(c.boughtValue[g]), 0));
        }
        lines.add(new L("", "", 4));
        lines.add(new L("Alım sayısı", String.valueOf(z.count), 0));
        lines.add(new L("Kasa", String.valueOf(z.kasa), 0));
        lines.add(new L("Net kilo", kg(z.kg), 1));
        lines.add(new L("Ortalama", z.kg > 0 ? TezgahModel.money(z.gross / z.kg) + "/kg" : "—", 0));
        lines.add(new L("TUTAR", TezgahModel.tl(z.gross), 1));
        double paidNow = 0;
        for (TezgahPurchases.Purchase p : list) paidNow += p.paid;
        lines.add(new L("Ödenen", TezgahModel.tl(paidNow), 0));
        double due = Math.max(0, z.gross - paidNow);
        lines.add(new L("Üreticilere borç", due > 0.5 ? TezgahModel.tl(due) : "—", 0));
        lines.add(new L("", "", 4));
        lines.add(new L("TÜCCARA TESLİM", "", 1));
        lines.add(new L("Teslim kilo", kg(c.deliveredKg()), 0));
        if (Math.abs(c.fireKg()) >= 0.05) {
            lines.add(new L("Fire", kg(c.fireKg()) + "  (%" + TezgahClosing.oneDecimal(c.firePercent()) + ")", 0));
        } else {
            lines.add(new L("Fire", "yok", 0));
        }
        lines.add(new L("Komisyon", c.rate > 0 ? TezgahModel.money(c.rate) + "/kg = " + TezgahModel.tl(c.commission()) : "—", 0));
        lines.add(new L("Avans", c.advance > 0 ? "− " + TezgahModel.tl(c.advance) : "—", 0));
        double owes = c.traderOwes();
        lines.add(new L(owes >= 0 ? "TÜCCAR ÖDEYECEK" : "TEZGAH İADE EDER", TezgahModel.tl(Math.abs(owes)), 7));
        lines.add(new L("", "", 4));
        lines.add(new L("ÜRETİCİLER", "", 1));
        List<TezgahPurchases.Purchase> chrono = new ArrayList<>(list);
        java.util.Collections.reverse(chrono);
        for (TezgahPurchases.Purchase p : chrono) {
            String name = hm.format(new Date(p.createdAt)) + " " + (p.seller.isEmpty() ? "Adsız" : p.seller);
            String right = kg(p.kg) + " · " + TezgahModel.tl(p.gross) + (p.remaining() > 0.5 ? " *" : "");
            lines.add(new L(name, right, 0));
        }
        if (due > 0.5) lines.add(new L("* ödemesi tamamlanmamış", "", 6));
        lines.add(new L("", "", 4));
        lines.add(new L("Gün sonu alındı. Bu dönemin kayıtları kilitlidir.", "", 6));
        lines.add(new L("Zeytin Defteri ile hazırlandı", "", 6));

        int h = PAD + 10;
        for (L l : lines) h += height(l);
        h += PAD + 24;
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.TRANSPARENT);
        // Kâğıt: alt kenar testere dişi (yırtık fiş)
        Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);
        paper.setColor(Color.WHITE);
        Path path = new Path();
        path.moveTo(0, 0);
        path.lineTo(W, 0);
        path.lineTo(W, h - 16);
        int tooth = 20;
        for (int x = W; x > 0; x -= tooth) {
            path.lineTo(x - tooth / 2f, h);
            path.lineTo(Math.max(0, x - tooth), h - 16);
        }
        path.close();
        cv.drawPath(path, paper);

        TextPaint normal = paint(24, INK, false);
        TextPaint bold = paint(24, INK, true);
        TextPaint big = paint(34, INK, true);
        TextPaint huge = paint(30, INK, true);
        TextPaint small = paint(19, MUTED, false);
        Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        dash.setColor(MUTED);
        dash.setStrokeWidth(2);
        dash.setPathEffect(new DashPathEffect(new float[]{10, 8}, 0));

        int y = PAD + 10;
        for (L l : lines) {
            int lh = height(l);
            switch (l.kind) {
                case 4:
                    cv.drawLine(PAD, y + lh / 2f, W - PAD, y + lh / 2f, dash);
                    break;
                case 5:
                    break;
                case 2:
                    center(cv, l.left, y, lh, big);
                    break;
                case 3:
                    center(cv, l.left, y, lh, normal);
                    break;
                case 6:
                    center(cv, l.left, y, lh, small);
                    break;
                case 7: {
                    Paint box = new Paint();
                    box.setColor(Color.parseColor("#EDEDED"));
                    cv.drawRect(PAD - 8, y + 4, W - PAD + 8, y + lh - 4, box);
                    pair(cv, l.left, l.right, y, lh, bold, huge);
                    break;
                }
                default:
                    pair(cv, l.left, l.right, y, lh, l.kind == 1 ? bold : normal, l.kind == 1 ? bold : normal);
            }
            y += lh;
        }
        return bmp;
    }

    private static int height(L l) {
        switch (l.kind) {
            case 2: return 50;
            case 4: return 26;
            case 6: return 30;
            case 7: return 58;
            default: return LINE_H;
        }
    }

    private static void pair(Canvas cv, String left, String right, int y, int h, TextPaint lp, TextPaint rp) {
        float rw = right == null || right.isEmpty() ? 0 : rp.measureText(right);
        float maxLeft = W - PAD * 2 - rw - 16;
        CharSequence lt = TextUtils.ellipsize(left == null ? "" : left, lp, maxLeft, TextUtils.TruncateAt.END);
        cv.drawText(lt, 0, lt.length(), PAD, base(y, h, lp), lp);
        if (rw > 0) cv.drawText(right, W - PAD - rw, base(y, h, rp), rp);
    }

    private static void center(Canvas cv, String s, int y, int h, TextPaint p) {
        CharSequence t = TextUtils.ellipsize(s, p, W - PAD * 2, TextUtils.TruncateAt.END);
        float w = p.measureText(t, 0, t.length());
        cv.drawText(t, 0, t.length(), (W - w) / 2f, base(y, h, p), p);
    }

    private static float base(int y, int h, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return y + h / 2f - (fm.ascent + fm.descent) / 2f;
    }

    private static TextPaint paint(int size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    private static String kg(double v) { return TezgahModel.kg(v).replace(' ', ' '); }

    private static boolean sameDay(long a, long b) {
        SimpleDateFormat d = new SimpleDateFormat("yyyyMMdd", Locale.US);
        return d.format(new Date(a)).equals(d.format(new Date(b)));
    }
}
