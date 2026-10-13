package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SubscriptionActivity extends Activity {

    private SubscriptionManager subscriptionManager;

    private LinearLayout containerPlans;

    private TextView tvCurrentPlan;
    private TextView tvPlanInfo;

    private TextView btnMonthly;
    private TextView btnYearly;

    private String selectedBillingPeriod =
            SubscriptionManager.BILLING_MONTHLY;


    // ============================================================
    // TASLAK FİYATLAR
    // Daha sonra buradan kolayca değiştirebiliriz.
    // ============================================================

    private static final String PRICE_PRO_MONTHLY =
            "149,90 TL / ay";

    private static final String PRICE_PRO_YEARLY =
            "1.499 TL / yıl";

    private static final String PRICE_PREMIUM_MONTHLY =
            "249,90 TL / ay";

    private static final String PRICE_PREMIUM_YEARLY =
            "2.499 TL / yıl";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Varsayılan ActionBar'ı gizle
        if (getActionBar() != null) {
            getActionBar().hide();
        }


        subscriptionManager =
                new SubscriptionManager(this);


        // Eğer daha önce aylık/yıllık seçim yapılmışsa hatırla
        String savedBilling =
                subscriptionManager.getBillingPeriod();


        if (SubscriptionManager.BILLING_YEARLY.equals(savedBilling)) {

            selectedBillingPeriod =
                    SubscriptionManager.BILLING_YEARLY;

        } else {

            selectedBillingPeriod =
                    SubscriptionManager.BILLING_MONTHLY;
        }


        getWindow().setStatusBarColor(
                GreenTheme.color(R.color.zd_bg)
        );
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));


        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );


        buildScreen();
    }


    // ============================================================
    // EKRAN
    // ============================================================

    private void buildScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                GreenTheme.color(R.color.zd_bg)
        );


        // ========================================================
        // HEADER
        // ========================================================

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(14)
        );


        TextView btnBack =
                new TextView(this);

        btnBack.setText("‹");

        btnBack.setTextSize(35);

        btnBack.setTextColor(
                GreenTheme.color(R.color.zd_text)
        );

        btnBack.setGravity(
                Gravity.CENTER
        );

        btnBack.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        btnBack.setOnClickListener(
                view -> finish()
        );


        header.addView(
                btnBack,
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(42)
                )
        );


        LinearLayout titleArea =
                new LinearLayout(this);

        titleArea.setOrientation(
                LinearLayout.VERTICAL
        );

        titleArea.setPadding(
                dp(8),
                0,
                0,
                0
        );


        TextView smallTitle =
                new TextView(this);

        smallTitle.setText(
                "ZEYTİN DEFTERİ"
        );

        smallTitle.setTextSize(11);

        smallTitle.setTextColor(
                GreenTheme.parse("#688153")
        );

        smallTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        TextView mainTitle =
                new TextView(this);

        mainTitle.setText(
                "Üyelik Paketleri"
        );

        mainTitle.setTextSize(25);

        mainTitle.setTextColor(
                GreenTheme.color(R.color.zd_text)
        );

        mainTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        titleArea.addView(
                smallTitle
        );

        titleArea.addView(
                mainTitle
        );


        header.addView(
                titleArea,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        root.addView(
                header
        );


        // ========================================================
        // SCROLL
        // ========================================================

        ScrollView scrollView =
                new ScrollView(this);

        scrollView.setFillViewport(
                true
        );

        scrollView.setClipToPadding(
                false
        );


        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(35)
        );


        // ========================================================
        // HERO
        // ========================================================

        LinearLayout hero =
                new LinearLayout(this);

        hero.setOrientation(
                LinearLayout.VERTICAL
        );

        hero.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(20)
        );

        hero.setBackground(
                createRoundedBackground(
                        "#315D24",
                        26,
                        null,
                        0
                )
        );

        hero.setElevation(
                dp(4)
        );


        TextView heroIcon =
                new TextView(this);

        heroIcon.setText("🫒");

        heroIcon.setTextSize(35);


        TextView heroTitle =
                new TextView(this);

        heroTitle.setText(
                "Bahçenden daha fazlasını çıkar"
        );

        heroTitle.setTextSize(23);

        heroTitle.setTextColor(
                Color.WHITE
        );

        heroTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        heroTitle.setPadding(
                0,
                dp(8),
                0,
                0
        );


        TextView heroText =
                new TextView(this);

        heroText.setText(
                "Daha güçlü raporlar, piyasa analizleri ve Zeytin Asistanı ile üretimini tek yerden yönet."
        );

        heroText.setTextSize(14);

        heroText.setTextColor(
                GreenTheme.color(R.color.zd_green_soft)
        );

        heroText.setLineSpacing(
                dp(2),
                1f
        );

        heroText.setPadding(
                0,
                dp(8),
                0,
                0
        );


        hero.addView(
                heroIcon
        );

        hero.addView(
                heroTitle
        );

        hero.addView(
                heroText
        );


        content.addView(
                hero
        );


        // ========================================================
        // MEVCUT PAKET
        // ========================================================

        LinearLayout currentCard =
                new LinearLayout(this);

        currentCard.setOrientation(
                LinearLayout.VERTICAL
        );

        currentCard.setPadding(
                dp(17),
                dp(15),
                dp(17),
                dp(15)
        );

        currentCard.setBackground(
                createRoundedBackground(
                        "#132A38",
                        20,
                        "#29434D",
                        1
                )
        );

        currentCard.setElevation(
                dp(2)
        );


        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        currentParams.topMargin =
                dp(14);


        TextView currentLabel =
                new TextView(this);

        currentLabel.setText(
                "ŞU ANKİ PAKETİN"
        );

        currentLabel.setTextSize(10);

        currentLabel.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        currentLabel.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        tvCurrentPlan =
                new TextView(this);

        tvCurrentPlan.setTextSize(20);

        tvCurrentPlan.setTextColor(
                GreenTheme.color(R.color.zd_text)
        );

        tvCurrentPlan.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        tvCurrentPlan.setPadding(
                0,
                dp(3),
                0,
                0
        );


        tvPlanInfo =
                new TextView(this);

        tvPlanInfo.setTextSize(12);

        tvPlanInfo.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        tvPlanInfo.setPadding(
                0,
                dp(4),
                0,
                0
        );


        currentCard.addView(
                currentLabel
        );

        currentCard.addView(
                tvCurrentPlan
        );

        currentCard.addView(
                tvPlanInfo
        );


        content.addView(
                currentCard,
                currentParams
        );


        // ========================================================
        // PAKET BAŞLIĞI
        // ========================================================

        TextView plansTitle =
                new TextView(this);

        plansTitle.setText(
                "Sana uygun paketi seç"
        );

        plansTitle.setTextSize(20);

        plansTitle.setTextColor(
                GreenTheme.color(R.color.zd_text)
        );

        plansTitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        plansTitle.setPadding(
                0,
                dp(22),
                0,
                dp(4)
        );


        TextView plansSubtitle =
                new TextView(this);

        plansSubtitle.setText(
                "Aylık veya yıllık üyelik seçebilirsin."
        );

        plansSubtitle.setTextSize(12);

        plansSubtitle.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        plansSubtitle.setPadding(
                0,
                0,
                0,
                dp(13)
        );


        content.addView(
                plansTitle
        );

        content.addView(
                plansSubtitle
        );


        // ========================================================
        // AYLIK - YILLIK SEÇİCİ
        // ========================================================

        LinearLayout billingContainer =
                new LinearLayout(this);

        billingContainer.setOrientation(
                LinearLayout.HORIZONTAL
        );

        billingContainer.setGravity(
                Gravity.CENTER
        );

        billingContainer.setPadding(
                dp(5),
                dp(5),
                dp(5),
                dp(5)
        );

        billingContainer.setBackground(
                createRoundedBackground(
                        "#132A38",
                        22,
                        "#29434D",
                        1
                )
        );


        btnMonthly =
                createBillingButton(
                        "Aylık"
                );


        btnYearly =
                createBillingButton(
                        "Yıllık  •  Avantajlı"
                );


        billingContainer.addView(
                btnMonthly,
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                )
        );


        billingContainer.addView(
                btnYearly,
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                )
        );


        btnMonthly.setOnClickListener(
                view -> {

                    selectedBillingPeriod =
                            SubscriptionManager.BILLING_MONTHLY;

                    refreshBillingButtons();

                    refreshPlans();
                }
        );


        btnYearly.setOnClickListener(
                view -> {

                    selectedBillingPeriod =
                            SubscriptionManager.BILLING_YEARLY;

                    refreshBillingButtons();

                    refreshPlans();
                }
        );


        content.addView(
                billingContainer
        );


        // ========================================================
        // PAKETLER
        // ========================================================

        containerPlans =
                new LinearLayout(this);

        containerPlans.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams plansParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        plansParams.topMargin =
                dp(14);


        content.addView(
                containerPlans,
                plansParams
        );


        // ========================================================
        // TEST BİLGİSİ
        // ========================================================

        TextView testInfo =
                new TextView(this);

        testInfo.setText(
                "ℹ️  Şimdilik test modu kullanılıyor. Gerçek ödeme alınmaz. Google Play ödeme sistemi daha sonra bağlanacak."
        );

        testInfo.setTextSize(11);

        testInfo.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        testInfo.setLineSpacing(
                dp(2),
                1f
        );

        testInfo.setPadding(
                dp(13),
                dp(13),
                dp(13),
                dp(13)
        );

        testInfo.setBackground(
                createRoundedBackground(
                        "#132A38",
                        16,
                        "#29434D",
                        1
                )
        );


        LinearLayout.LayoutParams testParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        testParams.topMargin =
                dp(6);


        content.addView(
                testInfo,
                testParams
        );


        scrollView.addView(
                content
        );


        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );


        setContentView(
                root
        );


        refreshBillingButtons();

        refreshPlans();
    }


    // ============================================================
    // AYLIK - YILLIK BUTONU
    // ============================================================

    private TextView createBillingButton(
            String text
    ) {

        TextView button =
                new TextView(this);

        button.setText(
                text
        );

        button.setGravity(
                Gravity.CENTER
        );

        button.setTextSize(
                13
        );

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setClickable(
                true
        );

        button.setFocusable(
                true
        );


        return button;
    }


    // ============================================================
    // AYLIK - YILLIK GÖRÜNÜMÜ
    // ============================================================

    private void refreshBillingButtons() {

        boolean monthly =
                SubscriptionManager.BILLING_MONTHLY.equals(
                        selectedBillingPeriod
                );


        if (monthly) {

            btnMonthly.setTextColor(
                    Color.WHITE
            );

            btnMonthly.setBackground(
                    createRoundedBackground(
                            "#416A25",
                            18,
                            null,
                            0
                    )
            );


            btnYearly.setTextColor(
                    GreenTheme.color(R.color.zd_muted)
            );

            btnYearly.setBackgroundColor(
                    Color.TRANSPARENT
            );

        } else {

            btnYearly.setTextColor(
                    GreenTheme.color(R.color.zd_text)
            );

            btnYearly.setBackground(
                    createRoundedBackground(
                            "#E5C44B",
                            18,
                            null,
                            0
                    )
            );


            btnMonthly.setTextColor(
                    GreenTheme.color(R.color.zd_muted)
            );

            btnMonthly.setBackgroundColor(
                    Color.TRANSPARENT
            );
        }
    }


    // ============================================================
    // PAKETLERİ YENİLE
    // ============================================================

    private void refreshPlans() {

        containerPlans.removeAllViews();


        String currentPlan =
                subscriptionManager.getCurrentPlan();


        tvCurrentPlan.setText(
                subscriptionManager.getPlanDisplayName()
        );


        if (subscriptionManager.isFree()) {

            tvPlanInfo.setText(
                    "Temel özelliklerle Zeytin Defteri'ni kullanıyorsun."
            );

        } else if (subscriptionManager.getEndDate() == 0L) {

            String billingName =
                    subscriptionManager.getBillingDisplayName();


            if (billingName.isEmpty()) {

                tvPlanInfo.setText(
                        "Test üyeliği aktif."
                );

            } else {

                tvPlanInfo.setText(
                        billingName
                                + " test üyeliği aktif."
                );
            }

        } else {

            long days =
                    subscriptionManager.getRemainingDays();


            tvPlanInfo.setText(
                    subscriptionManager.getBillingDisplayName()
                            + " • "
                            + days
                            + " gün kaldı"
            );
        }


        // ========================================================
        // ÜCRETSİZ
        // ========================================================

        containerPlans.addView(
                createPlanCard(
                        "Ücretsiz",
                        "Başlangıç",
                        "0 TL",
                        "Temel kayıt ve takip",
                        new String[]{
                                "✓  2 bahçeye kadar kayıt",
                                "✓  Sulama, gübre ve ilaç kayıtları",
                                "✓  Hasat ve satış kaydı",
                                "✓  Temel sezon özeti",
                                "—  Gelişmiş piyasa analizi",
                                "—  AI Zeytin Asistanı"
                        },
                        SubscriptionManager.PLAN_FREE,
                        "#132A38",
                        "#F4F7F4",
                        false,
                        currentPlan
                )
        );


        // ========================================================
        // PRO
        // ========================================================

        String proPrice =
                SubscriptionManager.BILLING_YEARLY.equals(
                        selectedBillingPeriod
                )
                        ? PRICE_PRO_YEARLY
                        : PRICE_PRO_MONTHLY;


        containerPlans.addView(
                createPlanCard(
                        "Pro",
                        "Üretici",
                        proPrice,
                        "Ciddi üretim takibi",
                        new String[]{
                                "✓  Sınırsız bahçe",
                                "✓  Tüm temel kayıtlar",
                                "✓  Gelişmiş raporlar",
                                "✓  Gelişmiş piyasa analizi",
                                "✓  Salamura yönetimi",
                                "—  AI Zeytin Asistanı"
                        },
                        SubscriptionManager.PLAN_PRO,
                        "#19392F",
                        "#F4F7F4",
                        false,
                        currentPlan
                )
        );


        // ========================================================
        // PREMIUM
        // ========================================================

        String premiumPrice =
                SubscriptionManager.BILLING_YEARLY.equals(
                        selectedBillingPeriod
                )
                        ? PRICE_PREMIUM_YEARLY
                        : PRICE_PREMIUM_MONTHLY;


        containerPlans.addView(
                createPlanCard(
                        "Premium",
                        "En güçlü paket",
                        premiumPrice,
                        "Zeytin Defteri'nin tamamı",
                        new String[]{
                                "✓  Pro paketindeki her şey",
                                "✓  AI Zeytin Asistanı",
                                "✓  Akıllı üretim önerileri",
                                "✓  Gelişmiş analizler",
                                "✓  Öncelikli yeni özellikler",
                                "✓  Premium üretici deneyimi"
                        },
                        SubscriptionManager.PLAN_PREMIUM,
                        "#315D24",
                        "#FFFFFF",
                        true,
                        currentPlan
                )
        );
    }


    // ============================================================
    // PAKET KARTI
    // ============================================================

    private View createPlanCard(
            String name,
            String badge,
            String price,
            String subtitle,
            String[] features,
            String planCode,
            String backgroundColor,
            String textColor,
            boolean premium,
            String currentPlan
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );


        card.setBackground(
                createRoundedBackground(
                        backgroundColor,
                        24,
                        premium
                                ? "#D4B347"
                                : "#29434D",
                        premium
                                ? 2
                                : 1
                )
        );


        card.setElevation(
                dp(
                        premium
                                ? 5
                                : 2
                )
        );


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin =
                dp(13);


        // ========================================================
        // ÜST KISIM
        // ========================================================

        LinearLayout top =
                new LinearLayout(this);

        top.setOrientation(
                LinearLayout.HORIZONTAL
        );

        top.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout names =
                new LinearLayout(this);

        names.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView tvName =
                new TextView(this);

        tvName.setText(
                premium
                        ? "✦ " + name
                        : name
        );

        tvName.setTextSize(
                23
        );

        tvName.setTextColor(
                GreenTheme.parse(textColor)
        );

        tvName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        TextView tvSubtitle =
                new TextView(this);

        tvSubtitle.setText(
                subtitle
        );

        tvSubtitle.setTextSize(
                12
        );

        tvSubtitle.setTextColor(
                GreenTheme.parse(
                        premium
                                ? "#19392F"
                                : "#9AABA8"
                )
        );

        tvSubtitle.setPadding(
                0,
                dp(2),
                0,
                0
        );


        names.addView(
                tvName
        );

        names.addView(
                tvSubtitle
        );


        top.addView(
                names,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        TextView tvBadge =
                new TextView(this);

        tvBadge.setText(
                badge
        );

        tvBadge.setTextSize(
                10
        );

        tvBadge.setGravity(
                Gravity.CENTER
        );

        tvBadge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        tvBadge.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );


        if (premium) {

            tvBadge.setTextColor(
                    GreenTheme.color(R.color.zd_text)
            );

            tvBadge.setBackground(
                    createRoundedBackground(
                            "#E5C44B",
                            30,
                            null,
                            0
                    )
            );

        } else {

            tvBadge.setTextColor(
                    GreenTheme.color(R.color.zd_muted)
            );

            tvBadge.setBackground(
                    createRoundedBackground(
                            "#19392F",
                            30,
                            null,
                            0
                    )
            );
        }


        top.addView(
                tvBadge
        );


        card.addView(
                top
        );


        // ========================================================
        // FİYAT
        // ========================================================

        TextView tvPrice =
                new TextView(this);

        tvPrice.setText(
                price
        );

        tvPrice.setTextSize(
                "Ücretsiz".equals(name)
                        ? 28
                        : 21
        );

        tvPrice.setTextColor(
                GreenTheme.parse(textColor)
        );

        tvPrice.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        tvPrice.setPadding(
                0,
                dp(15),
                0,
                dp(11)
        );


        card.addView(
                tvPrice
        );


        // ========================================================
        // ÖZELLİKLER
        // ========================================================

        for (String feature : features) {

            TextView tvFeature =
                    new TextView(this);

            tvFeature.setText(
                    feature
            );

            tvFeature.setTextSize(
                    13
            );

            tvFeature.setTextColor(
                    GreenTheme.parse(
                            premium
                                    ? "#19392F"
                                    : "#9AABA8"
                    )
            );

            tvFeature.setPadding(
                    0,
                    dp(5),
                    0,
                    dp(5)
            );


            card.addView(
                    tvFeature
            );
        }


        // ========================================================
        // BUTON
        // ========================================================

        TextView button =
                new TextView(this);

        button.setGravity(
                Gravity.CENTER
        );

        button.setTextSize(
                14
        );

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        buttonParams.topMargin =
                dp(16);


        boolean samePlan =
                planCode.equals(
                        currentPlan
                );


        boolean sameBilling =
                subscriptionManager.isFree()
                        || selectedBillingPeriod.equals(
                        subscriptionManager.getBillingPeriod()
                );


        boolean isCurrent =
                samePlan
                        && sameBilling;


        if (isCurrent) {

            button.setText(
                    "✓ Mevcut Paket"
            );

            button.setEnabled(
                    false
            );


            button.setTextColor(
                    GreenTheme.parse(
                            premium
                                    ? "#315D24"
                                    : "#9AABA8"
                    )
            );


            button.setBackground(
                    createRoundedBackground(
                            premium
                                    ? "#E8D46E"
                                    : "#19392F",
                            17,
                            null,
                            0
                    )
            );

        } else {

            if (SubscriptionManager.PLAN_FREE.equals(planCode)) {

                button.setText(
                        "Ücretsiz Pakete Geç"
                );

            } else {

                String billingText =
                        SubscriptionManager.BILLING_YEARLY.equals(
                                selectedBillingPeriod
                        )
                                ? "Yıllık"
                                : "Aylık";


                button.setText(
                        name
                                + " • "
                                + billingText
                );
            }


            if (premium) {

                button.setTextColor(
                        GreenTheme.color(R.color.zd_text)
                );

                button.setBackground(
                        createRoundedBackground(
                                "#E6C64F",
                                17,
                                null,
                                0
                        )
                );

            } else {

                button.setTextColor(
                        Color.WHITE
                );

                button.setBackground(
                        createRoundedBackground(
                                "#416A25",
                                17,
                                null,
                                0
                        )
                );
            }


            button.setOnClickListener(
                    view -> selectPlan(
                            planCode
                    )
            );
        }


        card.addView(
                button,
                buttonParams
        );


        card.setLayoutParams(
                cardParams
        );


        return card;
    }


    // ============================================================
    // TEST PAKET SEÇİMİ
    // ============================================================

    private void selectPlan(
            String plan
    ) {

        if (SubscriptionManager.PLAN_FREE.equals(plan)) {

            subscriptionManager.resetToFree();


            Toast.makeText(
                    this,
                    "Ücretsiz paket aktif 🌿",
                    Toast.LENGTH_SHORT
            ).show();

        } else {

            subscriptionManager.activateTestPlan(
                    plan,
                    selectedBillingPeriod
            );


            String period =
                    SubscriptionManager.BILLING_YEARLY.equals(
                            selectedBillingPeriod
                    )
                            ? "yıllık"
                            : "aylık";


            String planName =
                    SubscriptionManager.PLAN_PREMIUM.equals(plan)
                            ? "Premium"
                            : "Pro";


            Toast.makeText(
                    this,
                    planName
                            + " "
                            + period
                            + " test paketi aktif ✨",
                    Toast.LENGTH_SHORT
            ).show();
        }


        refreshPlans();
    }


    // ============================================================
    // ARKA PLAN
    // ============================================================

    private GradientDrawable createRoundedBackground(
            String fillColor,
            int radiusDp,
            String strokeColor,
            int strokeDp
    ) {

        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                GreenTheme.parse(
                        fillColor
                )
        );


        drawable.setCornerRadius(
                dp(radiusDp)
        );


        if (strokeColor != null
                && strokeDp > 0) {

            drawable.setStroke(
                    dp(strokeDp),
                    GreenTheme.parse(
                            strokeColor
                    )
            );
        }


        return drawable;
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
