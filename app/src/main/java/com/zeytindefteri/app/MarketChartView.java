package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class MarketChartView extends View {

    // =============================================================
    // VERİ
    // =============================================================

    private final float[] values = new float[] {
            92.0f,
            93.8f,
            93.1f,
            95.6f,
            96.8f,
            97.2f,
            98.5f
    };


    // =============================================================
    // ANİMASYON İLERLEMESİ
    //
    // 0f = grafik görünmez
    // 1f = grafik tamamen çizilmiş
    // =============================================================

    private float progress = 1f;


    // =============================================================
    // PAINTLER
    // =============================================================

    private final Paint linePaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint fillPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint gridPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint pointPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint pointInnerPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint textPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

    private final Paint todayLabelPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );


    // =============================================================
    // CONSTRUCTOR
    // =============================================================

    public MarketChartView(Context context) {

        super(context);

        init();

    }


    public MarketChartView(
            Context context,
            AttributeSet attrs
    ) {

        super(
                context,
                attrs
        );

        init();

    }


    public MarketChartView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {

        super(
                context,
                attrs,
                defStyleAttr
        );

        init();

    }


    // =============================================================
    // BAŞLANGIÇ
    // =============================================================

    private void init() {

        setLayerType(
                View.LAYER_TYPE_SOFTWARE,
                null
        );


        // =========================================================
        // ÇİZGİ
        // =========================================================

        linePaint.setColor(
                GreenTheme.color(R.color.zd_green)
        );

        linePaint.setStyle(
                Paint.Style.STROKE
        );

        linePaint.setStrokeWidth(
                dp(2.2f)
        );

        linePaint.setStrokeCap(
                Paint.Cap.ROUND
        );

        linePaint.setStrokeJoin(
                Paint.Join.ROUND
        );


        // =========================================================
        // DOLGU
        // =========================================================

        fillPaint.setColor(
                ((GreenTheme.color(R.color.zd_green) & 0x00FFFFFF) | 0x1A000000)
        );

        fillPaint.setStyle(
                Paint.Style.FILL
        );


        // =========================================================
        // IZGARA
        // =========================================================

        gridPaint.setColor(
                GreenTheme.color(R.color.zd_border)
        );

        gridPaint.setStyle(
                Paint.Style.STROKE
        );

        gridPaint.setStrokeWidth(
                dp(0.7f)
        );


        // =========================================================
        // NOKTA
        // =========================================================

        pointPaint.setColor(
                GreenTheme.color(R.color.zd_green)
        );

        pointPaint.setStyle(
                Paint.Style.FILL
        );


        pointInnerPaint.setColor(
                GreenTheme.color(R.color.zd_bg)
        );

        pointInnerPaint.setStyle(
                Paint.Style.FILL
        );


        // =========================================================
        // METİN
        // =========================================================

        textPaint.setColor(
                GreenTheme.color(R.color.zd_muted)
        );

        textPaint.setTextSize(
                sp(8.5f)
        );

        textPaint.setTextAlign(
                Paint.Align.CENTER
        );


        // =========================================================
        // BUGÜN ETİKETİ
        // =========================================================

        todayLabelPaint.setColor(
                GreenTheme.color(R.color.zd_text)
        );

        todayLabelPaint.setTextSize(
                sp(9f)
        );

        todayLabelPaint.setFakeBoldText(
                true
        );

        todayLabelPaint.setTextAlign(
                Paint.Align.CENTER
        );

    }


    // =============================================================
    // PROGRESS
    // =============================================================

    public void setProgress(float progress) {

        if (progress < 0f) {
            progress = 0f;
        }

        if (progress > 1f) {
            progress = 1f;
        }


        this.progress =
                progress;


        invalidate();

    }


    public float getProgress() {

        return progress;

    }


    // =============================================================
    // ÇİZ
    // =============================================================

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(
                canvas
        );


        if (getWidth() <= 0 ||
                getHeight() <= 0) {

            return;
        }


        // =========================================================
        // ALAN
        // =========================================================

        float left =
                dp(20);

        float right =
                getWidth()
                        - dp(12);

        float top =
                dp(8);

        float bottom =
                getHeight()
                        - dp(18);


        if (right <= left ||
                bottom <= top) {

            return;
        }


        // =========================================================
        // DEĞER ARALIĞI
        // =========================================================

        float minValue =
                90f;

        float maxValue =
                100f;


        // =========================================================
        // IZGARA
        // =========================================================

        drawGrid(
                canvas,
                left,
                top,
                right,
                bottom
        );


        // =========================================================
        // NOKTALARI HESAPLA
        // =========================================================

        float[] xs =
                new float[
                        values.length
                        ];

        float[] ys =
                new float[
                        values.length
                        ];


        float step =
                (right - left)
                        /
                        (values.length - 1);


        for (
                int i = 0;
                i < values.length;
                i++
        ) {

            xs[i] =
                    left
                            + step
                            * i;


            float normalized =
                    (values[i] - minValue)
                            /
                            (maxValue - minValue);


            ys[i] =
                    bottom
                            - normalized
                            * (bottom - top);

        }


        // =========================================================
        // PROGRESS'E GÖRE KAÇ PARÇA ÇİZİLECEK
        // =========================================================

        float segmentProgress =
                progress
                        * (values.length - 1);


        int fullSegments =
                (int) Math.floor(
                        segmentProgress
                );


        float partial =
                segmentProgress
                        - fullSegments;


        if (fullSegments >= values.length - 1) {

            fullSegments =
                    values.length - 1;

            partial =
                    0f;

        }


        // =========================================================
        // DOLGU PATH
        // =========================================================

        Path fillPath =
                new Path();


        fillPath.moveTo(
                xs[0],
                bottom
        );

        fillPath.lineTo(
                xs[0],
                ys[0]
        );


        // =========================================================
        // ÇİZGİ PATH
        // =========================================================

        Path linePath =
                new Path();


        linePath.moveTo(
                xs[0],
                ys[0]
        );


        for (
                int i = 0;
                i < fullSegments;
                i++
        ) {

            linePath.lineTo(
                    xs[i + 1],
                    ys[i + 1]
            );


            fillPath.lineTo(
                    xs[i + 1],
                    ys[i + 1]
            );

        }


        // =========================================================
        // KISMİ SON PARÇA
        // =========================================================

        float currentX =
                xs[Math.min(
                        fullSegments,
                        values.length - 1
                )];

        float currentY =
                ys[Math.min(
                        fullSegments,
                        values.length - 1
                )];


        if (
                fullSegments < values.length - 1 &&
                        partial > 0f
        ) {

            float startX =
                    xs[fullSegments];

            float startY =
                    ys[fullSegments];


            float endX =
                    xs[fullSegments + 1];

            float endY =
                    ys[fullSegments + 1];


            currentX =
                    startX
                            + (endX - startX)
                            * partial;


            currentY =
                    startY
                            + (endY - startY)
                            * partial;


            linePath.lineTo(
                    currentX,
                    currentY
            );


            fillPath.lineTo(
                    currentX,
                    currentY
            );

        }


        // =========================================================
        // DOLGUYU KAPAT
        // =========================================================

        fillPath.lineTo(
                currentX,
                bottom
        );

        fillPath.close();


        // =========================================================
        // DOLGU
        // =========================================================

        if (progress > 0f) {

            canvas.drawPath(
                    fillPath,
                    fillPaint
            );

        }


        // =========================================================
        // ÇİZGİ
        // =========================================================

        if (progress > 0f) {

            canvas.drawPath(
                    linePath,
                    linePaint
            );

        }


        // =========================================================
        // NOKTALAR
        // =========================================================

        int visiblePoints =
                fullSegments + 1;


        if (
                progress >= 1f
        ) {

            visiblePoints =
                    values.length;

        }


        for (
                int i = 0;
                i < visiblePoints;
                i++
        ) {

            drawPoint(
                    canvas,
                    xs[i],
                    ys[i],
                    i == values.length - 1
            );

        }


        // =========================================================
        // KISMİ NOKTA
        // =========================================================

        if (
                partial > 0f &&
                        fullSegments < values.length - 1
        ) {

            canvas.drawCircle(
                    currentX,
                    currentY,
                    dp(3.5f),
                    pointPaint
            );

        }


        // =========================================================
        // ALT ETİKETLER
        // =========================================================

        drawLabels(
                canvas,
                xs
        );

    }


    // =============================================================
    // IZGARA
    // =============================================================

    private void drawGrid(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom
    ) {

        float height =
                bottom - top;


        float y1 =
                top;

        float y2 =
                top
                        + height / 2f;

        float y3 =
                bottom;


        canvas.drawLine(
                left,
                y1,
                right,
                y1,
                gridPaint
        );


        canvas.drawLine(
                left,
                y2,
                right,
                y2,
                gridPaint
        );


        canvas.drawLine(
                left,
                y3,
                right,
                y3,
                gridPaint
        );

    }


    // =============================================================
    // NOKTA
    // =============================================================

    private void drawPoint(
            Canvas canvas,
            float x,
            float y,
            boolean isToday
    ) {

        float outerRadius =
                isToday
                        ? dp(5.8f)
                        : dp(4.2f);


        canvas.drawCircle(
                x,
                y,
                outerRadius,
                pointPaint
        );


        canvas.drawCircle(
                x,
                y,
                outerRadius * 0.46f,
                pointInnerPaint
        );

    }


    // =============================================================
    // ETİKETLER
    // =============================================================

    private void drawLabels(
            Canvas canvas,
            float[] xs
    ) {

        String[] labels =
                new String[] {
                        "7g",
                        "6g",
                        "5g",
                        "4g",
                        "3g",
                        "2g",
                        "Bugün"
                };


        float y =
                getHeight()
                        - dp(4);


        for (
                int i = 0;
                i < labels.length;
                i++
        ) {

            Paint paint =
                    i == labels.length - 1
                            ? todayLabelPaint
                            : textPaint;


            canvas.drawText(
                    labels[i],
                    xs[i],
                    y,
                    paint
            );

        }

    }


    // =============================================================
    // DP
    // =============================================================

    private float dp(float value) {

        return value
                * getResources()
                .getDisplayMetrics()
                .density;

    }


    // =============================================================
    // SP
    // =============================================================

    private float sp(float value) {

        return value
                * getResources()
                .getDisplayMetrics()
                .scaledDensity;

    }

}
