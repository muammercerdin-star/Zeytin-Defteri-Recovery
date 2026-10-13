package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Small, read-only trend chart used by the Zeytin Ligi region report popup. */
public final class LeagueRegionReportChartView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MarketCloudRepository.ChartPoint> points = new ArrayList<>();

    public LeagueRegionReportChartView(Context context) {
        super(context);
        gridPaint.setColor(GreenTheme.color(R.color.zd_border));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.7f));

        linePaint.setColor(GreenTheme.color(R.color.zd_green));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(0x2638A758);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint.setColor(GreenTheme.color(R.color.zd_green));
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(GreenTheme.color(R.color.zd_muted));
        textPaint.setTextSize(sp(8.5f));
    }

    public void setPoints(List<MarketCloudRepository.ChartPoint> source) {
        points.clear();
        if (source != null) points.addAll(source);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float left = dp(31);
        float right = w - dp(10);
        float top = dp(12);
        float bottom = h - dp(23);
        if (right <= left || bottom <= top) return;

        for (int i = 0; i < 4; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (points.size() < 2) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(10));
            canvas.drawText("Trend için yeterli anonim veri yok", w / 2f, h / 2f, textPaint);
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (MarketCloudRepository.ChartPoint p : points) {
            if (p == null || p.price <= 0d) continue;
            min = Math.min(min, p.price);
            max = Math.max(max, p.price);
        }
        if (min == Double.MAX_VALUE || max <= 0d) return;
        if (Math.abs(max - min) < 0.01d) {
            min -= 1d;
            max += 1d;
        } else {
            double pad = Math.max(1d, (max - min) * 0.15d);
            min = Math.max(0d, min - pad);
            max += pad;
        }

        int count = points.size();
        long[] times = new long[count];
        boolean allDatesValid = true;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            times[i] = parseDayMs(points.get(i).day);
            if (times[i] <= 0L) {
                allDatesValid = false;
                break;
            }
            minTime = Math.min(minTime, times[i]);
            maxTime = Math.max(maxTime, times[i]);
        }
        if (!allDatesValid || maxTime <= minTime) {
            minTime = 0L;
            maxTime = Math.max(1, count - 1);
            for (int i = 0; i < count; i++) times[i] = i;
        }

        float[] xs = new float[count];
        float[] ys = new float[count];
        boolean hasLargeGap = false;
        for (int i = 0; i < count; i++) {
            xs[i] = left + (right - left) * (float) ((times[i] - minTime) / (double) (maxTime - minTime));
            ys[i] = bottom - (float) ((points.get(i).price - min) / (max - min)) * (bottom - top);
            if (i > 0 && allDatesValid) {
                long gapDays = Math.abs(times[i] - times[i - 1]) / (24L * 60L * 60L * 1000L);
                if (gapDays > 10L) hasLargeGap = true;
            }
        }

        Path line = new Path();
        for (int i = 0; i < count; i++) {
            boolean breakLine = i == 0;
            if (i > 0 && allDatesValid) {
                long gapDays = Math.abs(times[i] - times[i - 1]) / (24L * 60L * 60L * 1000L);
                if (gapDays > 10L) breakLine = true;
            }
            if (breakLine) line.moveTo(xs[i], ys[i]);
            else line.lineTo(xs[i], ys[i]);
        }

        // Veri boşluğu büyükse alan dolgusu çizilmez; böylece aradaki günlerde
        // gerçek fiyat varmış gibi görsel bir izlenim oluşmaz.
        if (!hasLargeGap) {
            Path fill = new Path();
            fill.moveTo(xs[0], bottom);
            fill.lineTo(xs[0], ys[0]);
            for (int i = 1; i < count; i++) fill.lineTo(xs[i], ys[i]);
            fill.lineTo(xs[count - 1], bottom);
            fill.close();
            canvas.drawPath(fill, fillPaint);
        }
        canvas.drawPath(line, linePaint);

        for (int i = 0; i < count; i++) {
            canvas.drawCircle(xs[i], ys[i], dp(i == count - 1 ? 3.2f : 2.2f), pointPaint);
        }

        float firstX = xs[0];
        float lastX = xs[count - 1];
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(sp(8));
        canvas.drawText(formatNumber(max), left - dp(4), top + dp(3), textPaint);
        canvas.drawText(formatNumber((max + min) / 2d), left - dp(4), (top + bottom) / 2f + dp(3), textPaint);
        canvas.drawText(formatNumber(min), left - dp(4), bottom + dp(3), textPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        String first = shortDate(points.get(0).day);
        String mid = shortDate(points.get(count / 2).day);
        String last = shortDate(points.get(count - 1).day);
        canvas.drawText(first, firstX, h - dp(5), textPaint);
        canvas.drawText(mid, left + (right - left) / 2f, h - dp(5), textPaint);
        canvas.drawText(last, lastX, h - dp(5), textPaint);
    }

    private long parseDayMs(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
            return d == null ? 0L : d.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String shortDate(String raw) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
            if (d == null) return raw;
            return new SimpleDateFormat("d MMM", new Locale("tr", "TR")).format(d);
        } catch (Exception ignored) {
            return raw == null ? "" : raw;
        }
    }

    private String formatNumber(double value) {
        return String.format(new Locale("tr", "TR"), "%.0f", value);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
