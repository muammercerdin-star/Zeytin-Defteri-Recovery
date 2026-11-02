package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Ana ekrandaki namaz vakitleri şeridi: altı vakit yan yana; içinde bulunulan vakit altın, sıradaki mavi.
 * Alttaki çubuk günün akışını gösterir (içinde bulunulan vakitten sıradakine doğru dolar), ucundaki nokta
 * yavaşça nefes alır. Sağ üstte geri sayım ("İmsak'a 8 sa 23 dk"), dakikada bir güncellenir.
 * Vakitler bahçenin konumundan telefonda hesaplanır (PrayerTimes). Ekranda değilken çizim durur.
 */
public final class PrayerStripView extends View {
    private final float d;
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint right = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Rect visible = new Rect();
    private final int text, muted, gold, blue;
    private final boolean animate;
    private LinearGradient gradient;
    private int gradientW;

    private double lat = Double.NaN, lon = Double.NaN;
    private String place = "";
    private int dayKey = -1;
    private int[] today;
    private int tomorrowImsak, yesterdayIsha;

    public PrayerStripView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        text = GreenTheme.color(R.color.zd_text);
        muted = GreenTheme.color(R.color.zd_muted);
        gold = GreenTheme.color(R.color.zd_warning);
        blue = 0xFF8FC0E0;
        label.setColor(muted);
        label.setTextSize(11 * d);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.08f);
        right.setColor(blue);
        right.setTextSize(12 * d);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setTextAlign(Paint.Align.RIGHT);
        name.setTextSize(11 * d);
        name.setTextAlign(Paint.Align.CENTER);
        time.setTextSize(14 * d);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setTextAlign(Paint.Align.CENTER);
        track.setColor(GreenTheme.color(R.color.zd_border));
        float scale = 1f;
        try { scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f); }
        catch (RuntimeException ignored) { }
        animate = scale > 0f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private static final String PREFS = "zd_home", KEY = "prayer_times";

    /** Ayarlar'daki "Namaz vakitleri" (varsayılan açık). */
    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    /** Bahçenin konumu ve görünen yer adı ("Girelli"). Konum yoksa şerit gizli kalır. */
    public void setLocation(double latitude, double longitude, String placeName) {
        lat = latitude; lon = longitude;
        place = placeName == null ? "" : placeName.trim();
        dayKey = -1;
        setVisibility(Double.isNaN(lat) || Double.isNaN(lon) ? GONE : VISIBLE);
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) (96 * d));
    }

    private boolean refresh(Calendar now) {
        int key = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        if (key == dayKey && today != null) return true;
        TimeZone zone = now.getTimeZone();
        Calendar y = (Calendar) now.clone(); y.add(Calendar.DAY_OF_MONTH, -1);
        Calendar t = (Calendar) now.clone(); t.add(Calendar.DAY_OF_MONTH, 1);
        today = calc(now, zone);
        int[] tm = calc(t, zone), ys = calc(y, zone);
        if (today == null || tm == null || ys == null) { today = null; return false; }
        tomorrowImsak = tm[0];
        yesterdayIsha = ys[5];
        dayKey = key;
        return true;
    }

    private int[] calc(Calendar c, TimeZone zone) {
        int yy = c.get(Calendar.YEAR), mm = c.get(Calendar.MONTH) + 1, dd = c.get(Calendar.DAY_OF_MONTH);
        return PrayerTimes.day(yy, mm, dd, lat, lon, PrayerTimes.tzHours(zone, yy, mm, dd));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        Calendar cal = Calendar.getInstance();
        if (Double.isNaN(lat) || !refresh(cal)) return;
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        PrayerTimes.Now n = PrayerTimes.now(today, tomorrowImsak, yesterdayIsha, nowMin);
        float w = getWidth(), padX = 14 * d;

        String head = "NAMAZ VAKİTLERİ" + (place.isEmpty() ? "" : " · " + place.toUpperCase(new Locale("tr", "TR")));
        float headY = 22 * d;
        String count = PrayerTimes.countdown(n);
        float countW = right.measureText(count);
        float room = w - 2 * padX - countW - 10 * d;
        String shown = head;
        while (shown.length() > 3 && label.measureText(shown) > room) shown = shown.substring(0, shown.length() - 2);
        if (!shown.equals(head)) shown = shown.trim() + "…";
        c.drawText(shown, padX, headY, label);
        c.drawText(count, w - padX, headY, right);

        float colW = (w - 2 * padX) / 6f;
        float nameY = 46 * d, timeY = 66 * d;
        for (int i = 0; i < 6; i++) {
            float cx = padX + colW * (i + 0.5f);
            int color = i == n.current ? gold : i == n.next ? blue : muted;
            name.setColor(color);
            c.drawText(PrayerTimes.NAMES[i], cx, nameY, name);
            time.setColor(i == n.current ? gold : i == n.next ? blue : text);
            int value = i == 0 && nowMin >= today[5] ? tomorrowImsak : today[i];   // yatsıdan sonra yarının imsakı
            c.drawText(PrayerTimes.clock(value), cx, timeY, time);
        }

        // Günün akışı: içinde bulunulan vakit sütunundan sıradakine doğru ilerleyen nokta.
        float barY = 82 * d, h = 4 * d;
        bar.set(padX, barY - h / 2, w - padX, barY + h / 2);
        c.drawRoundRect(bar, h / 2, h / 2, track);
        float from, to;
        if (n.next == 0 && nowMin < today[0]) { from = padX; to = padX + colW * 0.5f; }              // gece yarısından imsaka
        else if (n.next == 0) { from = padX + colW * 5.5f; to = w - padX; }                         // yatsıdan gece yarısına
        else { from = padX + colW * (n.current + 0.5f); to = padX + colW * (n.next + 0.5f); }
        float x = from + (to - from) * n.fraction;
        if (gradient == null || gradientW != (int) w) {
            gradient = new LinearGradient(padX, 0, w - padX, 0, gold, blue, Shader.TileMode.CLAMP);
            gradientW = (int) w;
        }
        fill.setShader(gradient);
        bar.set(padX, barY - h / 2, Math.max(padX + h, x), barY + h / 2);
        c.drawRoundRect(bar, h / 2, h / 2, fill);
        float t = SystemClock.uptimeMillis() / 1000f;
        float pulse = animate ? 0.5f + 0.5f * (float) Math.sin(t * 2.2f) : 0f;
        dot.setColor(blue);
        dot.setAlpha((int) (70 * (1f - pulse)));
        c.drawCircle(x, barY, (6 + 5 * pulse) * d, dot);
        dot.setAlpha(255);
        c.drawCircle(x, barY, 5 * d, dot);

        setContentDescription("Namaz vakitleri. " + PrayerTimes.NAMES[n.next] + " " + PrayerTimes.clock(
                n.next == 0 && nowMin >= today[5] ? tomorrowImsak : today[n.next]) + ". " + count + ".");

        if (getGlobalVisibleRect(visible) && getWindowVisibility() == VISIBLE) postInvalidateDelayed(animate ? 50 : 20000);
        else postInvalidateDelayed(1000);
    }
}
