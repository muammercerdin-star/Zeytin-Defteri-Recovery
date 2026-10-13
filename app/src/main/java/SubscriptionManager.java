package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;

public class SubscriptionManager {

    // ============================================================
    // PAKETLER
    // ============================================================

    public static final String PLAN_FREE = "FREE";
    public static final String PLAN_PRO = "PRO";
    public static final String PLAN_PREMIUM = "PREMIUM";


    // ============================================================
    // ÖDEME DÖNEMLERİ
    // ============================================================

    public static final String BILLING_NONE = "NONE";
    public static final String BILLING_MONTHLY = "MONTHLY";
    public static final String BILLING_YEARLY = "YEARLY";


    // ============================================================
    // SHARED PREFERENCES
    // ============================================================

    private static final String PREF_NAME =
            "zeytin_subscription";

    private static final String KEY_PLAN =
            "subscription_plan";

    private static final String KEY_BILLING_PERIOD =
            "subscription_billing_period";

    private static final String KEY_START_DATE =
            "subscription_start_date";

    private static final String KEY_END_DATE =
            "subscription_end_date";


    private final SharedPreferences preferences;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public SubscriptionManager(Context context) {

        preferences =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );
    }


    // ============================================================
    // MEVCUT PAKET
    // ============================================================

    public String getCurrentPlan() {

        return preferences.getString(
                KEY_PLAN,
                PLAN_FREE
        );
    }


    // ============================================================
    // ÖDEME DÖNEMİ
    // ============================================================

    public String getBillingPeriod() {

        return preferences.getString(
                KEY_BILLING_PERIOD,
                BILLING_NONE
        );
    }


    public String getBillingDisplayName() {

        String billing =
                getBillingPeriod();


        if (BILLING_MONTHLY.equals(billing)) {

            return "Aylık";
        }


        if (BILLING_YEARLY.equals(billing)) {

            return "Yıllık";
        }


        return "";
    }


    // ============================================================
    // PAKET ADI
    // ============================================================

    public String getPlanDisplayName() {

        String plan =
                getCurrentPlan();


        if (PLAN_PRO.equals(plan)) {

            return "Zeytin Defteri Pro";
        }


        if (PLAN_PREMIUM.equals(plan)) {

            return "Zeytin Defteri Premium";
        }


        return "Ücretsiz Paket";
    }


    // ============================================================
    // FREE
    // ============================================================

    public boolean isFree() {

        return PLAN_FREE.equals(
                getCurrentPlan()
        );
    }


    // ============================================================
    // PRO
    // ============================================================

    public boolean isPro() {

        return PLAN_PRO.equals(
                getCurrentPlan()
        );
    }


    // ============================================================
    // PREMIUM
    // ============================================================

    public boolean isPremium() {

        return PLAN_PREMIUM.equals(
                getCurrentPlan()
        );
    }


    // ============================================================
    // ÜCRETLİ PAKET VAR MI?
    // ============================================================

    public boolean hasPaidPlan() {

        return isPro()
                || isPremium();
    }


    // ============================================================
    // NORMAL ABONELİK AKTİFLEŞTİR
    // ============================================================

    public void activatePlan(
            String plan,
            String billingPeriod,
            long durationMillis
    ) {

        long now =
                System.currentTimeMillis();

        long endDate =
                now + durationMillis;


        preferences
                .edit()
                .putString(
                        KEY_PLAN,
                        plan
                )
                .putString(
                        KEY_BILLING_PERIOD,
                        billingPeriod
                )
                .putLong(
                        KEY_START_DATE,
                        now
                )
                .putLong(
                        KEY_END_DATE,
                        endDate
                )
                .apply();
    }


    // ============================================================
    // TEST PAKETİ
    // ============================================================

    public void activateTestPlan(
            String plan,
            String billingPeriod
    ) {

        preferences
                .edit()
                .putString(
                        KEY_PLAN,
                        plan
                )
                .putString(
                        KEY_BILLING_PERIOD,
                        billingPeriod
                )
                .putLong(
                        KEY_START_DATE,
                        System.currentTimeMillis()
                )
                .putLong(
                        KEY_END_DATE,
                        0L
                )
                .apply();
    }


    // ============================================================
    // ESKİ KOD UYUMLULUĞU
    // ============================================================

    public void activateTestPlan(
            String plan
    ) {

        activateTestPlan(
                plan,
                BILLING_MONTHLY
        );
    }


    // ============================================================
    // BAŞLANGIÇ TARİHİ
    // ============================================================

    public long getStartDate() {

        return preferences.getLong(
                KEY_START_DATE,
                0L
        );
    }


    // ============================================================
    // BİTİŞ TARİHİ
    // ============================================================

    public long getEndDate() {

        return preferences.getLong(
                KEY_END_DATE,
                0L
        );
    }


    // ============================================================
    // ABONELİK AKTİF Mİ?
    // ============================================================

    public boolean isSubscriptionActive() {

        if (isFree()) {

            return false;
        }


        long endDate =
                getEndDate();


        // Test üyeliği
        if (endDate == 0L) {

            return true;
        }


        return System.currentTimeMillis()
                < endDate;
    }


    // ============================================================
    // SÜRESİ BİTMİŞ Mİ?
    // ============================================================

    public boolean isExpired() {

        if (isFree()) {

            return false;
        }


        long endDate =
                getEndDate();


        if (endDate == 0L) {

            return false;
        }


        return System.currentTimeMillis()
                >= endDate;
    }


    // ============================================================
    // KALAN GÜN
    // ============================================================

    public long getRemainingDays() {

        long endDate =
                getEndDate();


        if (endDate == 0L) {

            return -1;
        }


        long remaining =
                endDate
                        - System.currentTimeMillis();


        if (remaining <= 0L) {

            return 0L;
        }


        long oneDay =
                24L
                        * 60L
                        * 60L
                        * 1000L;


        return (
                remaining
                        + oneDay
                        - 1L
        ) / oneDay;
    }


    // ============================================================
    // FREE'YE DÖN
    // ============================================================

    public void resetToFree() {

        preferences
                .edit()
                .putString(
                        KEY_PLAN,
                        PLAN_FREE
                )
                .putString(
                        KEY_BILLING_PERIOD,
                        BILLING_NONE
                )
                .putLong(
                        KEY_START_DATE,
                        0L
                )
                .putLong(
                        KEY_END_DATE,
                        0L
                )
                .apply();
    }


    // ============================================================
    // ÖZELLİK YETKİLERİ
    // ============================================================

    public boolean canUseAdvancedReports() {

        return isPro()
                || isPremium();
    }


    public boolean canUseAdvancedMarket() {

        return isPro()
                || isPremium();
    }


    public boolean canUseAiAssistant() {

        return isPremium();
    }


    public boolean canUseUnlimitedGardens() {

        return isPro()
                || isPremium();
    }


    // ============================================================
    // BAHÇE SINIRI
    // ============================================================

    public int getGardenLimit() {

        if (isPro()
                || isPremium()) {

            return Integer.MAX_VALUE;
        }


        return 2;
    }
}