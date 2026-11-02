package com.zeytindefteri.app;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Selamlamanın altındaki tek satırlık namaz bilgisi:
 *   "☾ Yatsı 19:30 · Sonraki İmsak 06:03 · 8 sa 23 dk"
 * İçinde bulunulan vakit altın, sıradaki kalın beyaz, geri sayım mavi. Dakikada bir güncellenir (ekrandayken).
 * Dokununca günün altı vakti açılır. Vakitler bahçenin konumundan telefonda hesaplanır (PrayerTimes).
 */
public final class PrayerLineView extends TextView {
    private static final String PREFS = "zd_home", KEY = "prayer_times";
    private final int muted, gold, blue, text;
    private double lat = Double.NaN, lon = Double.NaN;
    private String place = "";
    private int dayKey = -1;
    private int[] today;
    private int tomorrowImsak, yesterdayIsha;
    private final Runnable tick = this::render;

    /** Ayarlar'daki "Namaz vakitleri" (varsayılan açık). */
    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    public PrayerLineView(Context context) {
        super(context);
        muted = GreenTheme.color(R.color.zd_muted);
        gold = GreenTheme.color(R.color.zd_warning);
        text = GreenTheme.color(R.color.zd_text);
        blue = 0xFF8FC0E0;
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        setTextColor(muted);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> showDay());
    }

    /** Bahçenin konumu ve yer adı ("Girelli"). Konum yoksa satır gizli kalır. */
    public void setLocation(double latitude, double longitude, String placeName) {
        lat = latitude; lon = longitude;
        place = placeName == null ? "" : placeName.trim();
        dayKey = -1;
        render();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); render(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    private boolean refresh(Calendar now) {
        int key = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        if (key == dayKey && today != null) return true;
        Calendar y = (Calendar) now.clone(); y.add(Calendar.DAY_OF_MONTH, -1);
        Calendar t = (Calendar) now.clone(); t.add(Calendar.DAY_OF_MONTH, 1);
        int[] td = calc(now), tm = calc(t), ys = calc(y);
        if (td == null || tm == null || ys == null) { today = null; return false; }
        today = td; tomorrowImsak = tm[0]; yesterdayIsha = ys[5]; dayKey = key;
        return true;
    }

    private int[] calc(Calendar c) {
        TimeZone zone = c.getTimeZone();
        int yy = c.get(Calendar.YEAR), mm = c.get(Calendar.MONTH) + 1, dd = c.get(Calendar.DAY_OF_MONTH);
        return PrayerTimes.day(yy, mm, dd, lat, lon, PrayerTimes.tzHours(zone, yy, mm, dd));
    }

    private void render() {
        removeCallbacks(tick);
        Calendar cal = Calendar.getInstance();
        if (Double.isNaN(lat) || Double.isNaN(lon) || !refresh(cal)) { setVisibility(GONE); return; }
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        PrayerTimes.Now n = PrayerTimes.now(today, tomorrowImsak, yesterdayIsha, nowMin);
        int nextTime = n.next == 0 && nowMin >= today[5] ? tomorrowImsak : today[n.next];
        int currentTime = n.current == 5 && nowMin < today[0] ? yesterdayIsha : today[n.current];   // gece yarısından sonra dünün yatsısı

        SpannableStringBuilder sb = new SpannableStringBuilder();
        boolean night = n.current == 0 || n.current >= 4;
        int a = sb.length();
        sb.append(night ? "☾ " : "☀ ").append(PrayerTimes.NAMES[n.current]).append(' ').append(PrayerTimes.clock(currentTime));
        sb.setSpan(new ForegroundColorSpan(gold), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ·  Sonraki ");
        a = sb.length();
        sb.append(PrayerTimes.NAMES[n.next]).append(' ').append(PrayerTimes.clock(nextTime));
        sb.setSpan(new ForegroundColorSpan(text), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ·  ");
        a = sb.length();
        String count = PrayerTimes.countdown(n);
        sb.append(count.substring(count.indexOf(' ') + 1));   // "8 sa 23 dk" (vakit adı zaten yazıyor)
        sb.setSpan(new ForegroundColorSpan(blue), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(sb);
        setContentDescription("Namaz vakti: " + PrayerTimes.NAMES[n.current] + " " + PrayerTimes.clock(currentTime)
                + ". Sonraki " + PrayerTimes.NAMES[n.next] + " " + PrayerTimes.clock(nextTime) + ", " + count
                + ". Bütün vakitler için dokun.");
        setVisibility(VISIBLE);
        // Bir sonraki dakika başında yenile.
        long delay = 60000 - (cal.get(Calendar.SECOND) * 1000L + cal.get(Calendar.MILLISECOND)) + 200;
        if (isAttachedToWindow()) postDelayed(tick, delay);
    }

    /** Günün altı vakti (dokununca). */
    private void showDay() {
        if (today == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(PrayerTimes.NAMES[i]).append("   ").append(PrayerTimes.clock(today[i]));
        }
        sb.append("\n\nBahçenin konumundan Diyanet yöntemiyle hesaplanır; birkaç dakika fark olabilir. ")
                .append("Ayarlar'dan kapatabilirsin.");
        new AlertDialog.Builder(getContext())
                .setTitle("Namaz vakitleri" + (place.isEmpty() ? "" : " · " + place))
                .setMessage(sb.toString())
                .setPositiveButton("Tamam", null)
                .show();
    }
}
