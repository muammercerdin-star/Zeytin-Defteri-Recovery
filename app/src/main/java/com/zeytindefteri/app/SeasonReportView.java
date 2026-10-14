package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * "Sezon Raporum": Zeytin Ligi → "Kişisel raporlar ve işlem geçmişi". Yalnızca görünüm;
 * veri SeasonReportModel.Data olarak activity'den gelir, eylemler Actions ile geri verilir.
 */
public final class SeasonReportView extends ScrollView {
    public interface Actions {
        void back();
        void convertElek();
        void addElek();
        void detailedReport();
        void operationHistory();
        void seasonPicker();
    }

    private final int bg = GreenTheme.color(R.color.zd_bg), card = GreenTheme.color(R.color.zd_card);
    private final int surface = GreenTheme.color(R.color.zd_surface), border = GreenTheme.color(R.color.zd_border);
    private final int green = GreenTheme.color(R.color.zd_green), gold = GreenTheme.color(R.color.zd_warning);
    private final int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
    private final int soft = GreenTheme.color(R.color.zd_text_soft), danger = GreenTheme.color(R.color.zd_danger);
    private final int harvestColor = 0xff81b64d;

    public SeasonReportView(Context context, SeasonReportModel.Data d, Actions actions) {
        super(context);
        setFillViewport(true); setVerticalScrollBarEnabled(false); setBackgroundColor(bg);
        LinearLayout body = column();
        body.setPadding(dp(16), dp(8), dp(16), dp(96));
        addView(body, new ScrollView.LayoutParams(-1, -2));

        // Başlık: geri + "Sezon Raporum" + sezon seçici
        LinearLayout header = row(); header.setPadding(0, dp(8), 0, dp(8));
        android.widget.ImageView back = new android.widget.ImageView(getContext());
        back.setImageResource(R.drawable.ic_elek_back_chevron); back.setColorFilter(text);
        back.setPadding(dp(11), dp(11), dp(11), dp(11)); back.setContentDescription("Zeytin Ligi'ne dön");
        back.setOnClickListener(v -> actions.back());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = column(); titles.setPadding(dp(4), 0, dp(8), 0);
        titles.addView(label("Sezon Raporum", 22, text, true));
        titles.addView(label("Yalnızca senin kayıtların · kimseyle paylaşılmaz", 12, muted, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView season = label(d.season + " ▾", 13, green, true);
        season.setGravity(Gravity.CENTER); season.setPadding(dp(14), 0, dp(14), 0);
        season.setBackground(round(card, border, 20)); season.setContentDescription("Sezon seç: " + d.season);
        season.setOnClickListener(v -> actions.seasonPicker());
        header.addView(season, new LinearLayout.LayoutParams(-2, dp(40)));
        body.addView(header);

        // 2x2 özet
        List<SeasonReportModel.Tile> tiles = SeasonReportModel.tiles(d);
        for (int r = 0; r < 2; r++) {
            LinearLayout line = row(); line.setGravity(Gravity.TOP);
            for (int c = 0; c < 2; c++) {
                SeasonReportModel.Tile t = tiles.get(r * 2 + c);
                LinearLayout box = column(); box.setPadding(dp(14), dp(12), dp(14), dp(12));
                box.setBackground(round(card, border, 16));
                box.addView(label(t.label, 12, muted, false));
                TextView value = label(t.value, 20, color(t.tone, text), true); value.setPadding(0, dp(2), 0, 0);
                box.addView(value);
                TextView note = label(t.note, 11, muted, false); note.setPadding(0, dp(2), 0, 0);
                box.addView(note);
                box.setContentDescription(t.label + ": " + t.value + ", " + t.note);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                if (c == 0) lp.rightMargin = dp(4); else lp.leftMargin = dp(4);
                line.addView(box, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(r == 0 ? 8 : 8);
            body.addView(line, rp);
        }

        // Sezon nerede?
        LinearLayout where = section(body, "SEZON NEREDE?");
        for (SeasonReportModel.Bar b : SeasonReportModel.chain(d)) {
            LinearLayout head = row(); head.setPadding(0, dp(8), 0, dp(4));
            head.addView(label(b.label, 13, text, true), new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(label(b.value, 13, b.tone == SeasonReportModel.Tone.WARNING ? gold : text, true));
            where.addView(head);
            FrameLayout track = new FrameLayout(getContext()); track.setBackground(round(surface, surface, 4));
            View fill = new View(getContext());
            int barColor = b.tone == SeasonReportModel.Tone.WARNING ? gold : b.tone == SeasonReportModel.Tone.POSITIVE ? green : harvestColor;
            fill.setBackground(round(barColor, barColor, 4));
            track.addView(fill, new FrameLayout.LayoutParams(0, -1));
            final int percent = b.percent;
            track.addOnLayoutChangeListener((v, l, t, rr, bb, ol, ot, or, ob) -> {
                int w = Math.round((rr - l) * percent / 100f);
                if (fill.getLayoutParams().width != w) { fill.getLayoutParams().width = w; fill.requestLayout(); }
            });
            track.setContentDescription(b.label + " " + b.value);
            where.addView(track, new LinearLayout.LayoutParams(-1, dp(8)));
        }
        String[] missing = SeasonReportModel.missingLink(d);
        if (missing != null) {
            LinearLayout alert = row(); alert.setGravity(Gravity.TOP);
            alert.setPadding(dp(12), dp(12), dp(12), dp(12)); alert.setBackground(round(surface, border, 14));
            View dot = new View(getContext()); dot.setBackground(round(gold, gold, 4));
            LinearLayout.LayoutParams dp8 = new LinearLayout.LayoutParams(dp(8), dp(8)); dp8.topMargin = dp(6); dp8.rightMargin = dp(10);
            alert.addView(dot, dp8);
            LinearLayout copy = column();
            copy.addView(label(missing[0], 13, text, true));
            TextView msg = label(missing[1], 12, soft, false); msg.setPadding(0, dp(2), 0, 0); copy.addView(msg);
            alert.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2); ap.topMargin = dp(14);
            where.addView(alert, ap);
            TextView action = label(missing[2], 14, green, true);
            action.setGravity(Gravity.CENTER); action.setBackground(round(card, green, 22));
            final boolean sale = d.elekKg > 0;
            action.setOnClickListener(v -> { if (sale) actions.convertElek(); else actions.addElek(); });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(44)); bp.topMargin = dp(10);
            where.addView(action, bp);
        }
        String harvestNote = SeasonReportModel.harvestNote(d);
        if (!harvestNote.isEmpty()) {
            TextView note = label(harvestNote, 11, muted, false); note.setPadding(0, dp(10), 0, 0);
            where.addView(note);
        }

        // Para
        LinearLayout money = section(body, "PARA");
        moneyRow(money, "Masraf", "işlem, hasat ve salamura masrafları", SeasonReportModel.tl(d.expenses), text, true);
        moneyRow(money, "Satış geliri", "gerçek satış kayıtları", SeasonReportModel.tl(d.salesRevenue), text, true);
        moneyRow(money, "Tahsil edilen / açık alacak", d.realSaleCount + " gerçek satış",
                SeasonReportModel.tl(d.received) + " / " + SeasonReportModel.tl(d.openReceivable), text, true);
        SeasonReportModel.Tile net = SeasonReportModel.net(d);
        moneyRow(money, net.label, net.note, net.value, color(net.tone, text), false);

        // Salamura
        LinearLayout brine = section(body, "SALAMURA");
        LinearLayout brineRow = row(); brineRow.setPadding(0, dp(8), 0, 0);
        brineCell(brineRow, SeasonReportModel.kg(d.brineInKg), "Giren");
        brineCell(brineRow, SeasonReportModel.kg(d.brineReadyKg), "Hazır");
        brineCell(brineRow, SeasonReportModel.tl(d.brineCost), "Maliyet");
        brineCell(brineRow, String.valueOf(d.brineBatches), "Aktif parti");
        brine.addView(brineRow);

        // Bahçelere göre
        LinearLayout gardens = section(body, "BAHÇELERE GÖRE");
        if (d.gardens.isEmpty()) gardens.addView(label("Henüz bahçe yok.", 13, muted, false));
        for (int i = 0; i < d.gardens.size(); i++) {
            SeasonReportModel.Garden g = d.gardens.get(i);
            LinearLayout line = row(); line.setMinimumHeight(dp(44));
            TextView name = label(g.name, 13, text, true); name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView meta = label(SeasonReportModel.gardenMeta(g), 12, soft, false); meta.setGravity(Gravity.END);
            line.addView(meta, new LinearLayout.LayoutParams(0, -2, 1.4f));
            if (i > 0) gardens.addView(divider());
            gardens.addView(line);
        }

        // Daha fazla
        LinearLayout links = column(); links.setBackground(round(card, border, 20));
        links.addView(linkRow("Ayrıntılı rapor", "Aylık masraf, hasat ve satış dökümü", actions::detailedReport));
        links.addView(divider());
        links.addView(linkRow("İşlem geçmişi", "Bütün bakım ve işlem kayıtların", actions::operationHistory));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
        body.addView(links, lp);
    }

    private LinearLayout section(LinearLayout body, String title) {
        LinearLayout s = column(); s.setPadding(dp(16), dp(14), dp(16), dp(16));
        s.setBackground(round(card, border, 20));
        TextView t = label(title, 11, muted, true); t.setLetterSpacing(0.08f); s.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
        body.addView(s, lp);
        return s;
    }

    private void moneyRow(LinearLayout parent, String title, String note, String value, int valueColor, boolean line) {
        LinearLayout r = row(); r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout copy = column();
        copy.addView(label(title, 13, text, false));
        copy.addView(label(note, 11, muted, false));
        r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(label(value, 15, valueColor, true));
        r.setContentDescription(title + ": " + value + ", " + note);
        parent.addView(r);
        if (line) parent.addView(divider());
    }

    private void brineCell(LinearLayout parent, String value, String caption) {
        LinearLayout c = column(); c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(label(value, 15, text, true));
        TextView cap = label(caption, 11, muted, false); cap.setPadding(0, dp(2), 0, 0); c.addView(cap);
        c.setContentDescription(caption + ": " + value);
        parent.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private View linkRow(String title, String note, Runnable action) {
        LinearLayout r = row(); r.setPadding(dp(16), 0, dp(12), 0); r.setMinimumHeight(dp(56));
        LinearLayout copy = column();
        copy.addView(label(title, 14, text, true));
        copy.addView(label(note, 11, muted, false));
        r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = label("›", 22, muted, false);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(chevron);
        r.setOnClickListener(v -> action.run());
        return r;
    }

    private int color(SeasonReportModel.Tone tone, int fallback) {
        switch (tone) {
            case WARNING: return gold;
            case MUTED: return muted;
            case POSITIVE: return green;
            case NEGATIVE: return danger;
            default: return fallback;
        }
    }

    private View divider() {
        View v = new View(getContext()); v.setBackgroundColor(border); v.setAlpha(.6f);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext()); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
