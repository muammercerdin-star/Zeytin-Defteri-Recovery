package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;


public class OnboardingActivity extends Activity {

    // =============================================================
    // ANA VIEW'LAR
    // =============================================================

    private ViewFlipper viewFlipper;
    private TextView tvSkip;

    private TextView dot1;
    private TextView dot2;
    private TextView dot3;
    private TextView dot4;

    private Button btnNext;

    private View onboardingRoot;


    // =============================================================
    // SAYFA 2 - KARTLAR
    // =============================================================

    private View cardWater;
    private View cardFertilizer;
    private View cardHarvest;
    private View cardElek;


    // =============================================================
    // SAYFA 2 - İKONLAR
    // =============================================================

    private View iconWater;
    private View iconFertilizer;
    private View iconHarvest;
    private View iconElek;


    // =============================================================
    // SAYFA 2 - METİNLER
    // =============================================================

    private View tvPageTwoIndicator;
    private View tvPageTwoTitle;
    private View tvPageTwoDescription;
    private View tvPageTwoMemory;


    // =============================================================
    // MARKA GEÇİŞİ
    // =============================================================

    private FrameLayout brandTransitionOverlay;
    private TextView brandTransitionIcon;
    private TextView brandTransitionTitle;
    private TextView brandTransitionSubtitle;

    private boolean brandTransitionRunning = false;


    // =============================================================
    // DURUM
    // =============================================================

    private int currentPage = 0;


    // =============================================================
    // SAYFA 2 DÖNGÜ DURUMU
    // =============================================================

    private boolean pageTwoLoopRunning = false;
    private int pageTwoHighlightIndex = 0;


    // =============================================================
    // HANDLER
    // =============================================================

    private final Handler animationHandler =
            new Handler(Looper.getMainLooper());


    // =============================================================
    // SAYFA 2 VURGU DÖNGÜSÜ
    // =============================================================

    private final Runnable pageTwoHighlightRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!pageTwoLoopRunning) {
                        return;
                    }

                    if (currentPage != 1) {

                        stopPageTwoHighlightLoop();

                        return;
                    }

                    highlightPageTwoCard(
                            pageTwoHighlightIndex
                    );

                    pageTwoHighlightIndex++;

                    if (pageTwoHighlightIndex > 3) {
                        pageTwoHighlightIndex = 0;
                    }

                    animationHandler.postDelayed(
                            this,
                            1350
                    );
                }
            };


    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);

        setContentView(
                R.layout.activity_onboarding
        );


        // =========================================================
        // ROOT
        // =========================================================

        ViewGroup activityContent =
                findViewById(
                        android.R.id.content
                );

        if (activityContent != null &&
                activityContent.getChildCount() > 0) {

            onboardingRoot =
                    activityContent.getChildAt(0);
        }


        // =========================================================
        // ANA VIEW'LAR
        // =========================================================

        viewFlipper =
                findViewById(
                        R.id.viewFlipper
                );

        tvSkip =
                findViewById(
                        R.id.tvSkip
                );

        dot1 =
                findViewById(
                        R.id.dot1
                );

        dot2 =
                findViewById(
                        R.id.dot2
                );

        dot3 =
                findViewById(
                        R.id.dot3
                );

        dot4 =
                findViewById(
                        R.id.dot4
                );

        btnNext =
                findViewById(
                        R.id.btnNext
                );


        // =========================================================
        // SAYFA 2
        // =========================================================

        cardWater =
                findViewById(
                        R.id.cardWater
                );

        cardFertilizer =
                findViewById(
                        R.id.cardFertilizer
                );

        cardHarvest =
                findViewById(
                        R.id.cardHarvest
                );

        cardElek =
                findViewById(
                        R.id.cardElek
                );


        iconWater =
                findViewById(
                        R.id.iconWater
                );

        iconFertilizer =
                findViewById(
                        R.id.iconFertilizer
                );

        iconHarvest =
                findViewById(
                        R.id.iconHarvest
                );

        iconElek =
                findViewById(
                        R.id.iconElek
                );


        tvPageTwoIndicator =
                findViewById(
                        R.id.tvPageTwoIndicator
                );

        tvPageTwoTitle =
                findViewById(
                        R.id.tvPageTwoTitle
                );

        tvPageTwoDescription =
                findViewById(
                        R.id.tvPageTwoDescription
                );

        tvPageTwoMemory =
                findViewById(
                        R.id.tvPageTwoMemory
                );


        ((OnboardingReferencePage) findViewById(R.id.onboardingStory)).bind(1);
        ((OnboardingReferencePage) findViewById(R.id.onboardingMarket)).bind(3);
        ((OnboardingReferencePage) findViewById(R.id.onboardingCommunity)).bind(4);

        // =========================================================
        // SAYFA UI
        // =========================================================

        updatePageUi();


        // =========================================================
        // ATLA
        // =========================================================

        tvSkip.setOnClickListener(v -> {

            if (brandTransitionRunning) {
                return;
            }

            stopAllAnimations();

            openLoginSetup();

        });


        // =========================================================
        // DEVAM ET
        // =========================================================

        btnNext.setOnClickListener(v -> {

            if (brandTransitionRunning) {
                return;
            }


            // =====================================================
            // SAYFA 1 - 2 - 3
            // =====================================================

            if (currentPage < 3) {

                if (currentPage == 1) {

                    stopPageTwoHighlightLoop();

                    resetPageTwoHighlight();
                }



                currentPage++;


                viewFlipper.showNext();


                updatePageUi();


                if (currentPage == 1) {

                    startPageTwoEntranceAnimation();
                }



                return;
            }


            // =====================================================
            // SAYFA 4 - Zeytin Defteri'ne Başla
            // =====================================================

            startBrandTransition();

        });

    }


    // =============================================================
    // MARKA GEÇİŞİ
    // =============================================================

    private void startBrandTransition() {

        if (brandTransitionRunning) {
            return;
        }

        brandTransitionRunning = true;


        btnNext.setClickable(false);
        btnNext.setEnabled(false);

        tvSkip.setClickable(false);
        tvSkip.setEnabled(false);


        // =========================================================
        // BUTON DOKUNMA TEPKİSİ
        // =========================================================

        btnNext.animate().cancel();

        btnNext
                .animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(90)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .withEndAction(
                        () -> btnNext
                                .animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(110)
                                .setInterpolator(
                                        new AccelerateDecelerateInterpolator()
                                )
                                .withEndAction(
                                        this::beginOnboardingExit
                                )
                                .start()
                )
                .start();

    }


    // =============================================================
    // ONBOARDING ÇIKIŞ ANİMASYONU
    // =============================================================

    private void beginOnboardingExit() {

        if (!brandTransitionRunning) {
            return;
        }

        createBrandTransitionOverlay();


        if (brandTransitionOverlay != null) {

            brandTransitionOverlay.setAlpha(
                    0f
            );

            brandTransitionOverlay.setVisibility(
                    View.VISIBLE
            );
        }


        if (onboardingRoot != null) {

            onboardingRoot.animate().cancel();

            onboardingRoot
                    .animate()
                    .alpha(0f)
                    .translationY(-dp(18))
                    .setDuration(320)
                    .setInterpolator(
                            new AccelerateDecelerateInterpolator()
                    )
                    .start();
        }


        if (brandTransitionOverlay != null) {

            brandTransitionOverlay
                    .animate()
                    .alpha(1f)
                    .setStartDelay(130)
                    .setDuration(330)
                    .setInterpolator(
                            new AccelerateDecelerateInterpolator()
                    )
                    .withEndAction(
                            this::animateBrandIdentity
                    )
                    .start();
        } else {

            animationHandler.postDelayed(
                    this::openLoginAfterBrandTransition,
                    450
            );
        }

    }


    // =============================================================
    // MARKA OVERLAY OLUŞTUR
    // =============================================================

    private void createBrandTransitionOverlay() {

        if (brandTransitionOverlay != null) {
            return;
        }


        ViewGroup activityContent =
                findViewById(
                        android.R.id.content
                );

        if (activityContent == null) {
            return;
        }


        brandTransitionOverlay =
                new FrameLayout(
                        this
                );


        brandTransitionOverlay.setBackgroundColor(
                GreenTheme.color(R.color.zd_bg)
        );


        brandTransitionOverlay.setClickable(
                true
        );


        brandTransitionOverlay.setFocusable(
                true
        );


        FrameLayout.LayoutParams overlayParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );


        brandTransitionOverlay.setLayoutParams(
                overlayParams
        );


        // =========================================================
        // ORTA İÇERİK
        // =========================================================

        LinearLayout brandContent =
                new LinearLayout(
                        this
                );


        brandContent.setOrientation(
                LinearLayout.VERTICAL
        );


        brandContent.setGravity(
                Gravity.CENTER
        );


        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        contentParams.gravity =
                Gravity.CENTER;


        brandContent.setLayoutParams(
                contentParams
        );


        // =========================================================
        // ZEYTİN İKONU
        // =========================================================

        brandTransitionIcon =
                new TextView(
                        this
                );


        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(78),
                        dp(78)
                );


        brandTransitionIcon.setLayoutParams(
                iconParams
        );


        brandTransitionIcon.setGravity(
                Gravity.CENTER
        );


        brandTransitionIcon.setText(
                "🫒"
        );


        brandTransitionIcon.setTextSize(
                42f
        );


        brandTransitionIcon.setBackgroundResource(
                R.drawable.bg_welcome_step
        );


        brandContent.addView(
                brandTransitionIcon
        );


        // =========================================================
        // BAŞLIK
        // =========================================================

        brandTransitionTitle =
                new TextView(
                        this
                );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        titleParams.topMargin =
                dp(18);


        brandTransitionTitle.setLayoutParams(
                titleParams
        );


        brandTransitionTitle.setText(
                "Zeytin Defteri"
        );


        brandTransitionTitle.setTextColor(
                GreenTheme.color(R.color.zd_text)
        );


        brandTransitionTitle.setTextSize(
                28f
        );


        brandTransitionTitle.setGravity(
                Gravity.CENTER
        );


        brandTransitionTitle.setTypeface(
                brandTransitionTitle.getTypeface(),
                android.graphics.Typeface.BOLD
        );


        brandContent.addView(
                brandTransitionTitle
        );


        // =========================================================
        // ALT MESAJ
        // =========================================================

        brandTransitionSubtitle =
                new TextView(
                        this
                );


        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        subtitleParams.topMargin =
                dp(7);


        brandTransitionSubtitle.setLayoutParams(
                subtitleParams
        );


        brandTransitionSubtitle.setText(
                "Bahçenin hafızasını oluşturalım."
        );


        brandTransitionSubtitle.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        brandTransitionSubtitle.setTextSize(
                14f
        );


        brandTransitionSubtitle.setGravity(
                Gravity.CENTER
        );


        brandContent.addView(
                brandTransitionSubtitle
        );


        brandTransitionOverlay.addView(
                brandContent
        );


        activityContent.addView(
                brandTransitionOverlay
        );

    }


    // =============================================================
    // MARKA KİMLİĞİ ANİMASYONU
    // =============================================================

    private void animateBrandIdentity() {

        if (!brandTransitionRunning) {
            return;
        }


        if (brandTransitionIcon != null) {

            brandTransitionIcon.animate().cancel();

            brandTransitionIcon.setAlpha(
                    0f
            );

            brandTransitionIcon.setScaleX(
                    0.84f
            );

            brandTransitionIcon.setScaleY(
                    0.84f
            );

            brandTransitionIcon.setTranslationY(
                    dp(12)
            );


            brandTransitionIcon
                    .animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(430)
                    .setInterpolator(
                            new AccelerateDecelerateInterpolator()
                    )
                    .start();
        }


        if (brandTransitionTitle != null) {

            brandTransitionTitle.animate().cancel();

            brandTransitionTitle.setAlpha(
                    0f
            );

            brandTransitionTitle.setTranslationY(
                    dp(10)
            );


            brandTransitionTitle
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(110)
                    .setDuration(380)
                    .setInterpolator(
                            new AccelerateDecelerateInterpolator()
                    )
                    .start();
        }


        if (brandTransitionSubtitle != null) {

            brandTransitionSubtitle.animate().cancel();

            brandTransitionSubtitle.setAlpha(
                    0f
            );

            brandTransitionSubtitle.setTranslationY(
                    dp(8)
            );


            brandTransitionSubtitle
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(210)
                    .setDuration(360)
                    .setInterpolator(
                            new AccelerateDecelerateInterpolator()
                    )
                    .start();
        }


        // Bir an okunmasına izin veriyoruz.
        animationHandler.postDelayed(
                this::finishBrandTransition,
                950
        );

    }


    // =============================================================
    // MARKA SAHNESİNİ BİTİR
    // =============================================================

    private void finishBrandTransition() {

        if (!brandTransitionRunning) {
            return;
        }


        if (brandTransitionOverlay == null) {

            openLoginAfterBrandTransition();

            return;
        }


        brandTransitionOverlay.animate().cancel();


        brandTransitionOverlay
                .animate()
                .alpha(0f)
                .setDuration(260)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .withEndAction(
                        this::openLoginAfterBrandTransition
                )
                .start();

    }


    // =============================================================
    // LOGIN'E GEÇ
    // =============================================================

    private void openLoginAfterBrandTransition() {

        if (!brandTransitionRunning) {
            return;
        }


        Intent intent =
                new Intent(
                        OnboardingActivity.this,
                        LoginActivity.class
                );


        intent.putExtra(
                "from_onboarding",
                true
        );


        startActivity(
                intent
        );


        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );


        finish();

    }


    // =============================================================
    // DP
    // =============================================================

    private int dp(float value) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    // =============================================================
    // SAYFA 2 - GİRİŞ ANİMASYONU
    // =============================================================

    private void startPageTwoEntranceAnimation() {

        stopPageTwoHighlightLoop();

        stopPageTwoAnimations();

        resetPageTwoHighlight();


        AccelerateDecelerateInterpolator interpolator =
                new AccelerateDecelerateInterpolator();


        prepareCard(cardWater);
        prepareCard(cardFertilizer);
        prepareCard(cardHarvest);
        prepareCard(cardElek);


        prepareText(
                tvPageTwoIndicator,
                10f
        );

        prepareText(
                tvPageTwoTitle,
                16f
        );

        prepareText(
                tvPageTwoDescription,
                12f
        );

        prepareText(
                tvPageTwoMemory,
                10f
        );


        animateCardEntrance(
                cardWater,
                iconWater,
                0,
                interpolator
        );

        animateCardEntrance(
                cardFertilizer,
                iconFertilizer,
                110,
                interpolator
        );

        animateCardEntrance(
                cardHarvest,
                iconHarvest,
                220,
                interpolator
        );

        animateCardEntrance(
                cardElek,
                iconElek,
                330,
                interpolator
        );


        animateTextEntrance(
                tvPageTwoIndicator,
                450,
                360,
                interpolator
        );

        animateTextEntrance(
                tvPageTwoTitle,
                520,
                430,
                interpolator
        );

        animateTextEntrance(
                tvPageTwoDescription,
                610,
                420,
                interpolator
        );

        animateTextEntrance(
                tvPageTwoMemory,
                700,
                420,
                interpolator
        );


        animationHandler.postDelayed(
                () -> {

                    if (currentPage == 1 &&
                            !brandTransitionRunning) {

                        startPageTwoHighlightLoop();
                    }
                },
                1300
        );

    }


    // =============================================================
    // SAYFA 2 KART HAZIRLA
    // =============================================================

    private void prepareCard(View card) {

        if (card == null) {
            return;
        }


        card.animate().cancel();

        card.setAlpha(
                0f
        );

        card.setTranslationY(
                24f
        );

        card.setScaleX(
                0.96f
        );

        card.setScaleY(
                0.96f
        );

    }


    // =============================================================
    // SAYFA 2 METİN HAZIRLA
    // =============================================================

    private void prepareText(
            View view,
            float translationY
    ) {

        if (view == null) {
            return;
        }


        view.animate().cancel();

        view.setAlpha(
                0f
        );

        view.setTranslationY(
                translationY
        );

    }


    // =============================================================
    // SAYFA 2 KART GİRİŞ
    // =============================================================

    private void animateCardEntrance(
            View card,
            View icon,
            long delay,
            AccelerateDecelerateInterpolator interpolator
    ) {

        if (card == null) {
            return;
        }


        card
                .animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(470)
                .setStartDelay(delay)
                .setInterpolator(interpolator)
                .start();


        if (icon != null) {

            icon.animate().cancel();

            icon.setScaleX(
                    0.86f
            );

            icon.setScaleY(
                    0.86f
            );


            icon
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420)
                    .setStartDelay(delay + 80)
                    .setInterpolator(interpolator)
                    .start();
        }

    }


    // =============================================================
    // SAYFA 2 METİN GİRİŞ
    // =============================================================

    private void animateTextEntrance(
            View view,
            long delay,
            long duration,
            AccelerateDecelerateInterpolator interpolator
    ) {

        if (view == null) {
            return;
        }


        view
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(interpolator)
                .start();

    }


    // =============================================================
    // SAYFA 2 VURGU DÖNGÜSÜ
    // =============================================================

    private void startPageTwoHighlightLoop() {

        stopPageTwoHighlightLoop();


        if (brandTransitionRunning) {
            return;
        }


        pageTwoLoopRunning = true;

        pageTwoHighlightIndex = 0;


        animationHandler.post(
                pageTwoHighlightRunnable
        );

    }


    // =============================================================
    // SAYFA 2 DÖNGÜ DURDUR
    // =============================================================

    private void stopPageTwoHighlightLoop() {

        pageTwoLoopRunning = false;


        animationHandler.removeCallbacks(
                pageTwoHighlightRunnable
        );

    }


    // =============================================================
    // SAYFA 2 KART VURGUSU
    // =============================================================

    private void highlightPageTwoCard(int index) {

        resetPageTwoHighlight();


        switch (index) {

            case 0:

                pulseCard(
                        cardWater,
                        iconWater
                );

                break;


            case 1:

                pulseCard(
                        cardFertilizer,
                        iconFertilizer
                );

                break;


            case 2:

                pulseCard(
                        cardHarvest,
                        iconHarvest
                );

                break;


            case 3:

                pulseCard(
                        cardElek,
                        iconElek
                );

                break;
        }

    }


    // =============================================================
    // SAYFA 2 PULSE
    // =============================================================

    private void pulseCard(
            View card,
            View icon
    ) {

        if (card == null ||
                brandTransitionRunning) {

            return;
        }


        AccelerateDecelerateInterpolator interpolator =
                new AccelerateDecelerateInterpolator();


        card
                .animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .translationZ(10f)
                .setDuration(260)
                .setInterpolator(interpolator)
                .start();


        if (icon != null) {

            icon
                    .animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(260)
                    .setInterpolator(interpolator)
                    .start();
        }


        animationHandler.postDelayed(
                () -> {

                    if (brandTransitionRunning) {
                        return;
                    }


                    if (card != null) {

                        card
                                .animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .translationZ(0f)
                                .setDuration(300)
                                .setInterpolator(interpolator)
                                .start();
                    }


                    if (icon != null) {

                        icon
                                .animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .setInterpolator(interpolator)
                                .start();
                    }
                },
                650
        );

    }


    // =============================================================
    // SAYFA 2 RESET
    // =============================================================

    private void resetPageTwoHighlight() {

        resetCard(
                cardWater,
                iconWater
        );

        resetCard(
                cardFertilizer,
                iconFertilizer
        );

        resetCard(
                cardHarvest,
                iconHarvest
        );

        resetCard(
                cardElek,
                iconElek
        );

    }


    // =============================================================
    // TEK KART RESET
    // =============================================================

    private void resetCard(
            View card,
            View icon
    ) {

        if (card != null) {

            card.animate().cancel();

            card.setScaleX(
                    1f
            );

            card.setScaleY(
                    1f
            );

            card.setTranslationZ(
                    0f
            );
        }


        if (icon != null) {

            icon.animate().cancel();

            icon.setScaleX(
                    1f
            );

            icon.setScaleY(
                    1f
            );
        }

    }


    // =============================================================
    // SAYFA 2 DURDUR
    // =============================================================

    private void stopPageTwoAnimations() {

        stopAnimation(cardWater);
        stopAnimation(cardFertilizer);
        stopAnimation(cardHarvest);
        stopAnimation(cardElek);

        stopAnimation(iconWater);
        stopAnimation(iconFertilizer);
        stopAnimation(iconHarvest);
        stopAnimation(iconElek);

        stopAnimation(tvPageTwoIndicator);
        stopAnimation(tvPageTwoTitle);
        stopAnimation(tvPageTwoDescription);
        stopAnimation(tvPageTwoMemory);

    }


    // =============================================================
    // TÜM NORMAL ANİMASYONLARI DURDUR
    // =============================================================

    private void stopAllAnimations() {

        stopPageTwoHighlightLoop();

        stopPageTwoAnimations();

        resetPageTwoHighlight();




        if (!brandTransitionRunning) {

            animationHandler.removeCallbacksAndMessages(
                    null
            );
        }

    }


    // =============================================================
    // TEK VIEW DURDUR
    // =============================================================

    private void stopAnimation(View view) {

        if (view == null) {
            return;
        }


        view.animate().cancel();

    }


    // =============================================================
    // SAYFA UI
    // =============================================================

    private void updatePageUi() {

        int pageBackground = currentPage == 1
                ? GreenTheme.color(R.color.zd_bg) : Color.rgb(3, 30, 29);
        if (onboardingRoot != null) onboardingRoot.setBackgroundColor(pageBackground);
        getWindow().setStatusBarColor(pageBackground);
        getWindow().setNavigationBarColor(pageBackground);

        if (dot1 != null) {

            dot1.setAlpha(
                    currentPage == 0 ? 1f : 0.25f
            );
        }


        if (dot2 != null) {

            dot2.setAlpha(
                    currentPage == 1 ? 1f : 0.25f
            );
        }


        if (dot3 != null) {

            dot3.setAlpha(
                    currentPage == 2 ? 1f : 0.25f
            );
        }


        if (dot4 != null) {

            dot4.setAlpha(
                    currentPage == 3 ? 1f : 0.25f
            );
        }


        if (currentPage == 3) {

            btnNext.setText(
                    "Zeytin Defteri'ne Başla   →"
            );


            tvSkip.setAlpha(
                    0f
            );

            tvSkip.setClickable(
                    false
            );

            tvSkip.setEnabled(
                    false
            );

        } else {

            btnNext.setText(
                    "Devam Et   →"
            );


            tvSkip.setAlpha(
                    1f
            );

            tvSkip.setClickable(
                    true
            );

            tvSkip.setEnabled(
                    true
            );
        }

    }


    // =============================================================
    // NORMAL LOGIN AÇILIŞI
    // =============================================================

    private void openLoginSetup() {

        Intent intent =
                new Intent(
                        OnboardingActivity.this,
                        LoginActivity.class
                );


        intent.putExtra(
                "from_onboarding",
                true
        );


        startActivity(
                intent
        );


        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );


        // "Atla" ile Login'e geçildiğinde onboarding geri
        // yığında kalmamalı. Final butonundaki davranışla aynı.
        finish();

    }


    // =============================================================
    // ON RESUME
    // =============================================================

    @Override
    protected void onResume() {

        super.onResume();


        if (brandTransitionRunning) {
            return;
        }


        if (currentPage == 1) {

            animationHandler.postDelayed(
                    () -> {

                        if (currentPage == 1 &&
                                !brandTransitionRunning) {

                            startPageTwoHighlightLoop();
                        }
                    },
                    350
            );
        }



    }


    // =============================================================
    // ON STOP
    // =============================================================

    @Override
    protected void onStop() {

        if (!brandTransitionRunning) {

            stopAllAnimations();
        }


        super.onStop();

    }


    // =============================================================
    // ON DESTROY
    // =============================================================

    @Override
    protected void onDestroy() {

        pageTwoLoopRunning = false;


        animationHandler.removeCallbacksAndMessages(
                null
        );






        super.onDestroy();

    }

}
