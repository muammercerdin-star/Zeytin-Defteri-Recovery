package com.zeytindefteri.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

public class AssistantOrbDrawable extends Drawable {

    // ============================================================
    // BOYALAR
    // ============================================================

    private final Paint corePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint innerBorderPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint haloPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint mainLightPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint goldLightPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);


    // ============================================================
    // RENKLER
    // ============================================================

    private static final int DARK_GREEN =
            GreenTheme.parse("#31571E");

    private static final int MAIN_GREEN =
            GreenTheme.parse("#416A25");

    private static final int LIGHT_GREEN =
            GreenTheme.parse("#9CC66B");

    private static final int SOFT_GREEN =
            GreenTheme.parse("#D9E9B5");

    private static final int GOLD =
            GreenTheme.parse("#D6B34C");


    // ============================================================
    // ANİMASYON
    // ============================================================

    private ValueAnimator animator;

    private float rotation =
            0f;

    private float pulse =
            0f;

    private boolean running =
            false;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public AssistantOrbDrawable() {

        // Ana yeşil merkez

        corePaint.setStyle(
                Paint.Style.FILL
        );

        corePaint.setColor(
                MAIN_GREEN
        );


        // İç ince sınır

        innerBorderPaint.setStyle(
                Paint.Style.STROKE
        );

        innerBorderPaint.setStrokeWidth(
                dp(1.3f)
        );

        innerBorderPaint.setColor(
                DARK_GREEN
        );

        innerBorderPaint.setAlpha(
                150
        );


        // Dış yumuşak halo

        haloPaint.setStyle(
                Paint.Style.STROKE
        );

        haloPaint.setStrokeWidth(
                dp(4.5f)
        );

        haloPaint.setColor(
                LIGHT_GREEN
        );

        haloPaint.setAlpha(
                48
        );


        // Ana hareketli ışık halkası

        mainLightPaint.setStyle(
                Paint.Style.STROKE
        );

        mainLightPaint.setStrokeWidth(
                dp(3.2f)
        );

        mainLightPaint.setStrokeCap(
                Paint.Cap.ROUND
        );


        // İnce altın ışık

        goldLightPaint.setStyle(
                Paint.Style.STROKE
        );

        goldLightPaint.setStrokeWidth(
                dp(1.8f)
        );

        goldLightPaint.setStrokeCap(
                Paint.Cap.ROUND
        );
    }


    // ============================================================
    // ÇİZ
    // ============================================================

    @Override
    public void draw(Canvas canvas) {

        int width =
                getBounds().width();

        int height =
                getBounds().height();


        if (width <= 0 || height <= 0) {
            return;
        }


        float cx =
                getBounds().left
                        + width / 2f;

        float cy =
                getBounds().top
                        + height / 2f;


        float size =
                Math.min(
                        width,
                        height
                );


        // ========================================================
        // HAFİF NEFES ALMA
        // ========================================================

        float breathe =
                1f
                        + pulse * 0.015f;


        float coreRadius =
                size
                        * 0.395f
                        * breathe;


        float haloRadius =
                size
                        * (
                        0.455f
                                + pulse * 0.007f
                );


        float lightRadius =
                size * 0.445f;


        float goldRadius =
                size * 0.425f;


        // ========================================================
        // YUMUŞAK DIŞ HALO
        // ========================================================

        haloPaint.setAlpha(
                35
                        + (int) (
                        pulse * 28
                )
        );


        canvas.drawCircle(
                cx,
                cy,
                haloRadius,
                haloPaint
        );


        // ========================================================
        // MERKEZ KÜRE
        // ========================================================

        canvas.drawCircle(
                cx,
                cy,
                coreRadius,
                corePaint
        );


        canvas.drawCircle(
                cx,
                cy,
                coreRadius - dp(1.2f),
                innerBorderPaint
        );


        // ========================================================
        // ANA YEŞİL IŞIK HALKASI
        // Kesintisiz ama bir tarafı daha parlak.
        // ========================================================

        int transparentGreen =
                Color.argb(
                        0,
                        156,
                        198,
                        107
                );


        int softGreen =
                Color.argb(
                        115,
                        217,
                        233,
                        181
                );


        int brightGreen =
                Color.argb(
                        255,
                        156,
                        198,
                        107
                );


        Shader greenShader =
                new SweepGradient(
                        cx,
                        cy,

                        new int[]{

                                transparentGreen,
                                transparentGreen,
                                softGreen,
                                brightGreen,
                                Color.WHITE,
                                brightGreen,
                                softGreen,
                                transparentGreen,
                                transparentGreen

                        },

                        new float[]{

                                0.00f,
                                0.36f,
                                0.48f,
                                0.57f,
                                0.62f,
                                0.68f,
                                0.77f,
                                0.88f,
                                1.00f
                        }
                );


        mainLightPaint.setShader(
                greenShader
        );


        canvas.save();

        canvas.rotate(
                rotation,
                cx,
                cy
        );


        canvas.drawCircle(
                cx,
                cy,
                lightRadius,
                mainLightPaint
        );


        canvas.restore();


        mainLightPaint.setShader(
                null
        );


        // ========================================================
        // ALTIN IŞIK
        // Ters yönde çok daha sakin hareket eder.
        // ========================================================

        int transparentGold =
                Color.argb(
                        0,
                        214,
                        179,
                        76
                );


        int softGold =
                Color.argb(
                        90,
                        214,
                        179,
                        76
                );


        int brightGold =
                Color.argb(
                        215,
                        214,
                        179,
                        76
                );


        Shader goldShader =
                new SweepGradient(
                        cx,
                        cy,

                        new int[]{

                                transparentGold,
                                transparentGold,
                                softGold,
                                brightGold,
                                softGold,
                                transparentGold,
                                transparentGold

                        },

                        new float[]{

                                0.00f,
                                0.52f,
                                0.64f,
                                0.70f,
                                0.77f,
                                0.86f,
                                1.00f
                        }
                );


        goldLightPaint.setShader(
                goldShader
        );


        canvas.save();

        canvas.rotate(
                -rotation * 0.68f,
                cx,
                cy
        );


        canvas.drawCircle(
                cx,
                cy,
                goldRadius,
                goldLightPaint
        );


        canvas.restore();


        goldLightPaint.setShader(
                null
        );
    }


    // ============================================================
    // BAŞLAT
    // ============================================================

    public void start() {

        if (running) {
            return;
        }


        running =
                true;


        animator =
                ValueAnimator.ofFloat(
                        0f,
                        360f
                );


        // Daha ağır ve premium dönme

        animator.setDuration(
                5200
        );


        animator.setRepeatCount(
                ValueAnimator.INFINITE
        );


        animator.setRepeatMode(
                ValueAnimator.RESTART
        );


        animator.setInterpolator(
                new LinearInterpolator()
        );


        animator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {

                    @Override
                    public void onAnimationUpdate(
                            ValueAnimator animation
                    ) {

                        rotation =
                                (float)
                                        animation
                                                .getAnimatedValue();


                        double wave =
                                Math.toRadians(
                                        rotation * 2f
                                );


                        pulse =
                                (float) (
                                        (
                                                Math.sin(wave)
                                                        + 1d
                                        )
                                                / 2d
                                );


                        invalidateSelf();
                    }
                }
        );


        animator.start();
    }


    // ============================================================
    // DURDUR
    // ============================================================

    public void stop() {

        running =
                false;


        if (animator != null) {

            animator.cancel();

            animator =
                    null;
        }
    }


    public boolean isRunning() {

        return running;
    }


    // ============================================================
    // DRAWABLE
    // ============================================================

    @Override
    public void setAlpha(
            int alpha
    ) {

        corePaint.setAlpha(
                alpha
        );

        invalidateSelf();
    }


    @Override
    public void setColorFilter(
            ColorFilter colorFilter
    ) {

        corePaint.setColorFilter(
                colorFilter
        );

        invalidateSelf();
    }


    @Override
    public int getOpacity() {

        return PixelFormat.TRANSLUCENT;
    }


    // ============================================================
    // DP
    // ============================================================

    private float dp(
            float value
    ) {

        return value
                * android.content.res.Resources
                .getSystem()
                .getDisplayMetrics()
                .density;
    }
}