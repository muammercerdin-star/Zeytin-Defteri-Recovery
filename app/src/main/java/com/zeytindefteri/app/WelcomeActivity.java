package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


public class WelcomeActivity extends Activity {


    // =============================================================
    // PREFS
    // =============================================================

    private static final String PREFS_NAME =
            "ZeytinDefteriPrefs";

    private static final String KEY_WELCOME_COMPLETED =
            "welcome_completed";


    // =============================================================
    // FIREBASE
    // =============================================================

    private FirebaseAuth firebaseAuth;


    // =============================================================
    // VIEW'LAR
    // =============================================================

    private ImageView imgHeroScene;

    private TextView tvAppName;
    private TextView tvSlogan;
    private TextView tvLogin;
    private TextView tvSecurity;
    private TextView tvTerms;

    private Button btnStart;


    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);


        // =========================================================
        // FIREBASE AUTH
        // =========================================================

        firebaseAuth =
                FirebaseAuth.getInstance();


        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();


        // =========================================================
        // 1. KULLANICI ZATEN GİRİŞ YAPMIŞSA
        //
        // Welcome veya Login gösterme.
        // Doğrudan uygulamaya gir.
        // =========================================================

        if (currentUser != null) {

            openMain();

            return;
        }


        // =========================================================
        // PREFS
        // =========================================================

        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        boolean welcomeCompleted =
                preferences.getBoolean(
                        KEY_WELCOME_COMPLETED,
                        false
                );


        // =========================================================
        // 2. WELCOME ÖNCEDEN TAMAMLANDI AMA OTURUM YOKSA
        //
        // Örneğin kullanıcı:
        // - çıkış yaptıysa
        // - Firebase oturumu kaldırıldıysa
        //
        // Welcome'ı tekrar göstermiyoruz.
        // Login'e gidiyoruz.
        // =========================================================

        if (welcomeCompleted) {

            openLoginAndFinish();

            return;
        }


        // =========================================================
        // 3. İLK KURULUM
        // =========================================================

        setContentView(
                R.layout.activity_welcome
        );


        // =========================================================
        // VIEW BAĞLANTILARI
        // =========================================================

        imgHeroScene =
                findViewById(
                        R.id.imgHeroScene
                );
        // Kısa ekranlı telefonlarda karşılama tek sayfaya sığsın: taşan kadar görsel küçülür.
        ScreenFit.shrinkToFit(findViewById(R.id.welcomeScroll), imgHeroScene, 170);


        tvAppName =
                findViewById(
                        R.id.tvAppName
                );


        tvSlogan =
                findViewById(
                        R.id.tvSlogan
                );


        tvLogin =
                findViewById(
                        R.id.tvLogin
                );


        tvSecurity =
                findViewById(
                        R.id.tvSecurity
                );


        tvTerms =
                findViewById(
                        R.id.tvTerms
                );


        btnStart =
                findViewById(
                        R.id.btnStart
                );


        // =========================================================
        // GİRİŞ METNİNİ HAZIRLA
        // =========================================================

        setupLoginText();


        // =========================================================
        // KOŞULLAR / GİZLİLİK
        // =========================================================

        setupTermsText();


        // =========================================================
        // ANİMASYON HAZIRLIĞI
        // =========================================================

        prepareViews();


        // =========================================================
        // ANİMASYONLARI BAŞLAT
        // =========================================================

        startWelcomeAnimations();


        // =========================================================
        // GİRİŞ YAP
        // =========================================================

        tvLogin.setOnClickListener(v -> {

            stopAnimations();

            openLogin();

        });


        // =========================================================
        // ÜCRETSİZ BAŞLA
        // =========================================================

        btnStart.setOnClickListener(v -> {

            stopAnimations();

            openOnboarding();

        });

    }


    // =============================================================
    // GİRİŞ METNİNİ RENKLENDİR
    // =============================================================

    private void setupLoginText() {

        String fullText =
                "Zaten hesabın var mı?  Giriş yap ›";


        SpannableString spannable =
                new SpannableString(
                        fullText
                );


        String normalText =
                "Zaten hesabın var mı?";


        int normalStart =
                fullText.indexOf(
                        normalText
                );


        int normalEnd =
                normalStart
                        + normalText.length();


        spannable.setSpan(
                new ForegroundColorSpan(
                        GreenTheme.color(R.color.zd_muted)
                ),
                normalStart,
                normalEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        spannable.setSpan(
                new StyleSpan(
                        Typeface.NORMAL
                ),
                normalStart,
                normalEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        String loginText =
                "Giriş yap ›";


        int loginStart =
                fullText.indexOf(
                        loginText
                );


        int loginEnd =
                loginStart
                        + loginText.length();


        spannable.setSpan(
                new ForegroundColorSpan(
                        GreenTheme.color(R.color.zd_green)
                ),
                loginStart,
                loginEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        spannable.setSpan(
                new StyleSpan(
                        Typeface.BOLD
                ),
                loginStart,
                loginEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        tvLogin.setText(
                spannable
        );

    }


    // =============================================================
    // KULLANIM KOŞULLARI / GİZLİLİK POLİTİKASI
    // =============================================================

    private void setupTermsText() {

        String fullText =
                "Devam ederek Kullanım Koşulları ve Gizlilik Politikası’nı kabul etmiş olursun.";


        SpannableString spannable =
                new SpannableString(
                        fullText
                );


        // =========================================================
        // KULLANIM KOŞULLARI
        // =========================================================

        String termsText =
                "Kullanım Koşulları";


        int termsStart =
                fullText.indexOf(
                        termsText
                );


        int termsEnd =
                termsStart
                        + termsText.length();


        ClickableSpan termsSpan =
                new ClickableSpan() {

                    @Override
                    public void onClick(View widget) {

                        openTerms();

                    }


                    @Override
                    public void updateDrawState(TextPaint ds) {

                        super.updateDrawState(ds);

                        ds.setColor(
                                GreenTheme.color(R.color.zd_green)
                        );

                        ds.setUnderlineText(false);

                        ds.setFakeBoldText(true);

                    }

                };


        spannable.setSpan(
                termsSpan,
                termsStart,
                termsEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        // =========================================================
        // GİZLİLİK POLİTİKASI
        // =========================================================

        String privacyText =
                "Gizlilik Politikası";


        int privacyStart =
                fullText.indexOf(
                        privacyText
                );


        int privacyEnd =
                privacyStart
                        + privacyText.length();


        ClickableSpan privacySpan =
                new ClickableSpan() {

                    @Override
                    public void onClick(View widget) {

                        openPrivacyPolicy();

                    }


                    @Override
                    public void updateDrawState(TextPaint ds) {

                        super.updateDrawState(ds);

                        ds.setColor(
                                GreenTheme.color(R.color.zd_green)
                        );

                        ds.setUnderlineText(false);

                        ds.setFakeBoldText(true);

                    }

                };


        spannable.setSpan(
                privacySpan,
                privacyStart,
                privacyEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );


        tvTerms.setText(
                spannable
        );


        tvTerms.setMovementMethod(
                LinkMovementMethod.getInstance()
        );


        tvTerms.setLinksClickable(
                true
        );


        tvTerms.setClickable(
                true
        );


        tvTerms.setFocusable(
                true
        );


        tvTerms.setHighlightColor(
                Color.TRANSPARENT
        );

    }


    // =============================================================
    // VIEW'LARI ANİMASYONA HAZIRLA
    // =============================================================

    private void prepareViews() {

        imgHeroScene.setAlpha(
                0f
        );

        imgHeroScene.setScaleX(
                0.94f
        );

        imgHeroScene.setScaleY(
                0.94f
        );

        imgHeroScene.setTranslationY(
                20f
        );


        tvAppName.setAlpha(
                0f
        );

        tvAppName.setTranslationY(
                24f
        );


        tvSlogan.setAlpha(
                0f
        );

        tvSlogan.setTranslationY(
                18f
        );


        tvLogin.setAlpha(
                0f
        );

        tvLogin.setTranslationY(
                18f
        );


        btnStart.setAlpha(
                0f
        );

        btnStart.setTranslationY(
                40f
        );


        tvSecurity.setAlpha(
                0f
        );

        tvSecurity.setTranslationY(
                12f
        );


        tvTerms.setAlpha(
                0f
        );

        tvTerms.setTranslationY(
                12f
        );

    }


    // =============================================================
    // KARŞILAMA ANİMASYONLARI
    // =============================================================

    private void startWelcomeAnimations() {

        AccelerateDecelerateInterpolator interpolator =
                new AccelerateDecelerateInterpolator();


        imgHeroScene
                .animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(900)
                .setStartDelay(100)
                .setInterpolator(interpolator)
                .start();


        tvAppName
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(750)
                .setInterpolator(interpolator)
                .start();


        tvSlogan
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(1000)
                .setInterpolator(interpolator)
                .start();


        tvLogin
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(550)
                .setStartDelay(1250)
                .setInterpolator(interpolator)
                .start();


        btnStart
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(650)
                .setStartDelay(1450)
                .setInterpolator(interpolator)
                .start();


        tvSecurity
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(1550)
                .setInterpolator(interpolator)
                .start();


        tvTerms
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(1650)
                .setInterpolator(interpolator)
                .start();

    }


    // =============================================================
    // ANA EKRANA GEÇ
    // =============================================================

    private void openMain() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        MainActivity.class
                );


        // Eski Welcome / Login ekranlarının geri tuşuyla
        // yeniden açılmasını engeller.
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();

    }


    // =============================================================
    // ONBOARDING EKRANINA GEÇ
    // =============================================================

    private void openOnboarding() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        OnboardingActivity.class
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
    // KULLANIM KOŞULLARINI AÇ
    // =============================================================

    private void openTerms() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        TermsActivity.class
                );


        startActivity(
                intent
        );

    }


    // =============================================================
    // GİZLİLİK POLİTİKASINI AÇ
    // =============================================================

    private void openPrivacyPolicy() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        PrivacyActivity.class
                );


        startActivity(
                intent
        );

    }


    // =============================================================
    // LOGIN EKRANINA GEÇ
    // =============================================================

    private void openLogin() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        LoginActivity.class
                );


        startActivity(
                intent
        );

    }


    // =============================================================
    // LOGIN EKRANINA GEÇ VE WELCOME'I KAPAT
    // =============================================================

    private void openLoginAndFinish() {

        Intent intent =
                new Intent(
                        WelcomeActivity.this,
                        LoginActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();

    }


    // =============================================================
    // ANİMASYONLARI DURDUR
    // =============================================================

    private void stopAnimations() {

        cancelViewAnimation(
                imgHeroScene
        );


        cancelViewAnimation(
                tvAppName
        );


        cancelViewAnimation(
                tvSlogan
        );


        cancelViewAnimation(
                tvLogin
        );


        cancelViewAnimation(
                btnStart
        );


        cancelViewAnimation(
                tvSecurity
        );


        cancelViewAnimation(
                tvTerms
        );

    }


    // =============================================================
    // TEK VIEW ANİMASYONUNU DURDUR
    // =============================================================

    private void cancelViewAnimation(View view) {

        if (view != null) {

            view
                    .animate()
                    .cancel();

        }

    }


    // =============================================================
    // ACTIVITY ARKA PLANA GEÇİNCE
    // =============================================================

    @Override
    protected void onStop() {

        super.onStop();


        stopAnimations();

    }

}
