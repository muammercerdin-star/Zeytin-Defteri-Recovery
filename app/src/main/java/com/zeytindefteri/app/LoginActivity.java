package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Patterns;
import android.widget.ScrollView;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.concurrent.Executors;


public class LoginActivity extends Activity {


    // ============================================================
    // RENKLER
    // ============================================================

    private static final String COLOR_BG =
            "#0E1C25";

    private static final String COLOR_GREEN =
            "#9CCB78";

    private static final String COLOR_GREEN_BUTTON =
            "#3F6B47";

    private static final String COLOR_TEXT =
            "#F2F6F0";

    private static final String COLOR_MUTED =
            "#A3B4BC";

    private static final String COLOR_BORDER =
            "#2A4957";

    private static final String COLOR_INPUT =
            "#132732";

    private static final String COLOR_SOFT_GREEN =
            "#19392F";


    // ============================================================
    // FORM
    // ============================================================

    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;


    // ============================================================
    // ÜST METİNLER
    // ============================================================

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvEmailDivider;


    // ============================================================
    // TAB
    // ============================================================

    private TextView btnRegisterTab;
    private TextView btnLoginTab;


    // ============================================================
    // SOSYAL
    // ============================================================

    private LinearLayout btnGoogle;


    // ============================================================
    // FORM BUTONLARI
    // ============================================================

    private TextView btnSubmit;
    private TextView tvForgotPassword;
    private TextView googleLabel;
    private View nameGroup, confirmGroup, strengthGroup, legalGroup;
    private ImageButton passwordEye, confirmEye;
    private CheckBox legalAcceptance;
    private ProgressBar authProgress;
    private TextView strengthDescription;
    private final View[] strengthSegments = new View[4];


    // ============================================================
    // DURUM
    // ============================================================

    private boolean registerMode =
            false;

    private boolean passwordVisible =
            false;

    private boolean actionRunning =
            false;

    private boolean fromOnboarding =
            false;


    // ============================================================
    // SERVİSLER
    // ============================================================

    private SessionManager sessionManager;
    private FirebaseAuth firebaseAuth;
    private DatabaseHelper databaseHelper;
    private GardenRepository gardenRepository;
    private GardenCloudRepository gardenCloudRepository;
    private OperationCloudRepository operationCloudRepository;
    private ProductionCloudRepository productionCloudRepository;
    private SupportCloudRepository supportCloudRepository;
    private PrivateCloudSyncManager privateCloudSyncManager;
    private boolean routingAfterAuth = false;
    private CredentialManager credentialManager;


    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );


    // ============================================================
    // ROOT
    // ============================================================

    private LinearLayout root;


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        fromOnboarding =
                getIntent().getBooleanExtra(
                        "from_onboarding",
                        false
                );


        sessionManager =
                new SessionManager(
                        this
                );


        databaseHelper =
                new DatabaseHelper(
                        this
                );

        gardenRepository =
                new GardenRepository(
                        databaseHelper
                );

        gardenCloudRepository =
                new GardenCloudRepository(
                        databaseHelper
                );

        operationCloudRepository =
                new OperationCloudRepository(
                        databaseHelper
                );

        productionCloudRepository =
                new ProductionCloudRepository(
                        databaseHelper
                );

        supportCloudRepository =
                new SupportCloudRepository(
                        databaseHelper
                );

        privateCloudSyncManager =
                new PrivateCloudSyncManager(
                        databaseHelper
                );


        // ========================================================
        // ACTION BAR
        // ========================================================

        if (getActionBar() != null) {

            getActionBar().hide();
        }


        // ========================================================
        // STATUS BAR
        // ========================================================

        getWindow().setStatusBarColor(
                GreenTheme.parse(
                        COLOR_BG
                )
        );
        getWindow().setNavigationBarColor(GreenTheme.parse(COLOR_BG));


        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );


        // ========================================================
        // FIREBASE
        // ========================================================

        setupFirebase();


        // ========================================================
        // MEVCUT FIREBASE OTURUMU
        // ========================================================

        if (!fromOnboarding
                &&
                firebaseAuth != null) {

            FirebaseUser currentUser =
                    firebaseAuth.getCurrentUser();


            if (currentUser != null) {

                String userName =
                        currentUser.getDisplayName();


                if (userName == null
                        ||
                        userName.trim().isEmpty()) {

                    userName =
                            sessionManager.getUserName();
                }


                if (userName == null
                        ||
                        userName.trim().isEmpty()) {

                    userName =
                            "Üretici";
                }


                String userEmail =
                        currentUser.getEmail();


                if (userEmail == null
                        ||
                        userEmail.trim().isEmpty()) {

                    userEmail =
                            sessionManager.getUserEmail();
                }


                if (userEmail == null) {

                    userEmail =
                            "";
                }


                sessionManager.startUserSession(
                        userName,
                        userEmail,
                        currentUser.getUid()
                );

                routeAuthenticatedUser(
                        currentUser
                );

                return;
            }
        }


        // ========================================================
        // MİSAFİR
        // ========================================================

        if (!fromOnboarding
                &&
                sessionManager.isGuest()) {

            Intent intent =
                    new Intent(
                            LoginActivity.this,
                            MainActivity.class
                    );


            intent.putExtra(
                    "from_login",
                    true
            );


            startActivity(
                    intent
            );


            finish();

            return;
        }


        // ========================================================
        // LOCAL SESSION TEMİZLİĞİ
        // ========================================================

        if (sessionManager.isLoggedInUser()
                &&
                firebaseAuth != null
                &&
                firebaseAuth.getCurrentUser() == null) {

            sessionManager.logout();
        }


        // ========================================================
        // GOOGLE CREDENTIAL
        // ========================================================

        credentialManager =
                CredentialManager.create(
                        this
                );


        // ========================================================
        // UI
        // ========================================================

        buildScreen();
        if (savedInstanceState != null) {
            setRegisterMode(savedInstanceState.getBoolean("auth_register_mode", fromOnboarding));
        }
    }


    // ============================================================
    // FIREBASE
    // ============================================================

    private void setupFirebase() {

        boolean firebaseReady =
                false;


        try {

            if (FirebaseApp
                    .getApps(this)
                    .isEmpty()) {

                FirebaseApp app =
                        FirebaseApp.initializeApp(
                                this
                        );


                firebaseReady =
                        app != null;

            } else {

                firebaseReady =
                        true;
            }


        } catch (Exception ignored) {

            firebaseReady =
                    false;
        }


        if (firebaseReady) {

            firebaseAuth =
                    FirebaseAuth.getInstance();

        } else {

            firebaseAuth =
                    null;
        }
    }


    // ============================================================
    // EKRANI OLUŞTUR
    // ============================================================

    private void buildScreen() {
        setContentView(R.layout.activity_auth_premium);
        root = findViewById(R.id.auth_content);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
        params.width = dp(Math.min(360, Math.max(1,
                getResources().getConfiguration().screenWidthDp - 48)));
        root.setLayoutParams(params);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        tvTitle = findViewById(R.id.auth_title);
        tvSubtitle = findViewById(R.id.auth_subtitle);
        tvEmailDivider = findViewById(R.id.auth_divider_label);
        btnRegisterTab = findViewById(R.id.auth_register_tab);
        btnLoginTab = findViewById(R.id.auth_login_tab);
        btnGoogle = findViewById(R.id.auth_google);
        googleLabel = findViewById(R.id.auth_google_label);
        btnSubmit = findViewById(R.id.auth_submit);
        tvForgotPassword = findViewById(R.id.auth_forgot);
        etName = findViewById(R.id.auth_name);
        etEmail = findViewById(R.id.auth_email);
        etPassword = findViewById(R.id.auth_password);
        etConfirmPassword = findViewById(R.id.auth_confirm);
        nameGroup = findViewById(R.id.auth_name_group);
        confirmGroup = findViewById(R.id.auth_confirm_group);
        strengthGroup = findViewById(R.id.auth_strength);
        legalGroup = findViewById(R.id.auth_legal);
        legalAcceptance = findViewById(R.id.auth_accept);
        authProgress = findViewById(R.id.auth_progress);
        passwordEye = findViewById(R.id.auth_password_eye);
        confirmEye = findViewById(R.id.auth_confirm_eye);
        strengthDescription = findViewById(R.id.auth_strength_description);
        int[] segments = {R.id.auth_strength_0, R.id.auth_strength_1,
                R.id.auth_strength_2, R.id.auth_strength_3};
        for (int i = 0; i < 4; i++) strengthSegments[i] = findViewById(segments[i]);
        findViewById(R.id.auth_logo).setBackground(rounded("#1C3528", 12, null, 0));
        findViewById(R.id.auth_tabs).setBackground(rounded(COLOR_INPUT, 14, COLOR_BORDER, 1));
        btnGoogle.setBackground(rounded("#F2F6F0", 14, null, 0));
        btnSubmit.setBackground(rounded(COLOR_GREEN_BUTTON, 14, null, 0));
        for (EditText field : new EditText[]{etName, etEmail, etPassword, etConfirmPassword}) {
            field.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        passwordEye.setOnClickListener(v -> togglePasswordVisibility());
        confirmEye.setOnClickListener(v -> togglePasswordVisibility());
        applyPasswordVisibility();
        btnRegisterTab.setOnClickListener(v -> { if (!actionRunning) setRegisterMode(true); });
        btnLoginTab.setOnClickListener(v -> { if (!actionRunning) setRegisterMode(false); });
        btnGoogle.setOnClickListener(v -> {
            if (actionRunning || (registerMode && !canStartRegistration())) return;
            openGoogleAccountChooser();
        });
        btnSubmit.setOnClickListener(v -> handleSubmit());
        tvForgotPassword.setOnClickListener(v -> { if (!actionRunning) showForgotPasswordDialog(); });
        legalAcceptance.setChecked(false);
        legalAcceptance.setEnabled(false);
        legalAcceptance.setOnCheckedChangeListener((button, checked) -> refreshAuthControls());
        TextView terms = findViewById(R.id.auth_terms);
        TextView privacy = findViewById(R.id.auth_privacy);
        terms.setPaintFlags(terms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        privacy.setPaintFlags(privacy.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        terms.setOnClickListener(v -> LegalDocumentActivity.open(this, LegalDocuments.TERMS));
        privacy.setOnClickListener(v -> LegalDocumentActivity.open(this, LegalDocuments.KVKK));
        View acceptRow = findViewById(R.id.auth_accept_row);
        acceptRow.setOnClickListener(v -> {
            if (actionRunning) return;
            if (!AuthRegistrationPolicy.documentsReady()) {
                showMessage(LegalDocuments.registrationBlockReason());
                return;
            }
            legalAcceptance.setChecked(!legalAcceptance.isChecked());
        });
        TextView legalStatus = findViewById(R.id.auth_legal_status);
        String blockReason = LegalDocuments.registrationBlockReason();
        legalStatus.setText(blockReason);
        legalStatus.setVisibility(blockReason.isEmpty() ? View.GONE : View.VISIBLE);
        etName.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT) { etEmail.requestFocus(); return true; }
            return false;
        });
        etEmail.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT) { etPassword.requestFocus(); return true; }
            return false;
        });
        etPassword.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT && registerMode) {
                etConfirmPassword.requestFocus(); return true;
            }
            if (action == EditorInfo.IME_ACTION_DONE && !registerMode) {
                handleSubmit(); return true;
            }
            return false;
        });
        etConfirmPassword.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etConfirmPassword.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { handleSubmit(); return true; }
            return false;
        });
        etPassword.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updatePasswordStrength();
            }
            public void afterTextChanged(Editable text) {}
        });
        setRegisterMode(fromOnboarding);
        updatePasswordStrength();
    }

    private void updatePasswordStrength() {
        int strength = AuthRegistrationPolicy.strength(etPassword.getText().toString());
        for (int i = 0; i < 4; i++) {
            strengthSegments[i].setBackground(rounded(i < strength ? "#7FB35C" : COLOR_BORDER, 2, null, 0));
        }
        String[] labels = {"Şifre girilmedi", "Zayıf", "Geliştirilebilir", "Kayıt koşulları sağlandı", "Daha güçlü"};
        strengthDescription.setContentDescription(labels[strength] + ". " + getString(R.string.auth_password_rule));
    }

    private boolean canStartRegistration() {
        if (!AuthRegistrationPolicy.documentsReady()) {
            showMessage(LegalDocuments.registrationBlockReason());
            return false;
        }
        if (!legalAcceptance.isChecked()) {
            showMessage(getString(R.string.auth_accept_required));
            return false;
        }
        return true;
    }

    private void refreshAuthControls() {
        // Kayıt modunda buton açık kalır; onay eksikse canStartRegistration() nedenini gösterir.
        boolean allowed = !registerMode || AuthRegistrationPolicy.documentsReady();
        btnSubmit.setEnabled(!actionRunning && allowed);
        btnSubmit.setAlpha(1f);
        btnGoogle.setEnabled(!actionRunning && allowed);
        btnGoogle.setAlpha(actionRunning ? 0.7f : 1f);
        btnRegisterTab.setEnabled(!actionRunning);
        btnLoginTab.setEnabled(!actionRunning);
        tvForgotPassword.setEnabled(!actionRunning);
        for (EditText field : new EditText[]{etName, etEmail, etPassword, etConfirmPassword}) {
            field.setEnabled(!actionRunning);
        }
        passwordEye.setEnabled(!actionRunning);
        confirmEye.setEnabled(!actionRunning);
        legalAcceptance.setEnabled(!actionRunning && AuthRegistrationPolicy.documentsReady());
        View acceptRow = findViewById(R.id.auth_accept_row);
        acceptRow.setEnabled(!actionRunning);
        acceptRow.setContentDescription(getString(R.string.auth_legal_checkbox)
                + (legalAcceptance.isChecked() ? ", işaretli" : ", işaretli değil"));
        findViewById(R.id.auth_terms).setEnabled(!actionRunning);
        findViewById(R.id.auth_privacy).setEnabled(!actionRunning);
        authProgress.setVisibility(actionRunning ? View.VISIBLE : View.GONE);
        btnSubmit.setPadding(dp(actionRunning ? 40 : 12), dp(12), dp(actionRunning ? 40 : 12), dp(12));
    }

    private void showForgotPasswordDialog() {

        if (firebaseAuth == null) {

            showMessage(
                    "Firebase bağlantısı hazır değil."
            );

            return;
        }


        Dialog dialog =
                new Dialog(
                        this
                );


        dialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );


        // ========================================================
        // ROOT
        // ========================================================

        LinearLayout card =
                new LinearLayout(
                        this
                );


        card.setOrientation(
                LinearLayout.VERTICAL
        );


        card.setPadding(
                dp(22),
                dp(22),
                dp(22),
                dp(20)
        );


        card.setBackground(
                rounded(
                        "#132A38",
                        24,
                        "#2A4957",
                        1
                )
        );


        // ========================================================
        // ICON
        // ========================================================

        TextView icon =
                new TextView(
                        this
                );


        icon.setText(
                "🔐"
        );


        icon.setTextSize(
                30
        );


        icon.setGravity(
                Gravity.CENTER
        );


        icon.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        30,
                        null,
                        0
                )
        );


        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(64),
                        dp(64)
                );


        iconParams.gravity =
                Gravity.CENTER_HORIZONTAL;


        card.addView(
                icon,
                iconParams
        );


        // ========================================================
        // TITLE
        // ========================================================

        TextView title =
                new TextView(
                        this
                );


        title.setText(
                "Şifreni yenile"
        );


        title.setTextSize(
                22
        );


        title.setTextColor(
                GreenTheme.parse(
                        COLOR_GREEN
                )
        );


        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        title.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        titleParams.topMargin =
                dp(13);


        card.addView(
                title,
                titleParams
        );


        // ========================================================
        // DESCRIPTION
        // ========================================================

        TextView description =
                new TextView(
                        this
                );


        description.setText(
                "Hesabına kayıtlı e-posta adresini yaz.\n"
                        + "Şifre yenileme bağlantısını sana gönderelim."
        );


        description.setTextSize(
                12
        );


        description.setTextColor(
                GreenTheme.parse(
                        COLOR_MUTED
                )
        );


        description.setGravity(
                Gravity.CENTER
        );


        description.setLineSpacing(
                dp(3),
                1f
        );


        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        descriptionParams.topMargin =
                dp(7);


        card.addView(
                description,
                descriptionParams
        );


        // ========================================================
        // EMAIL
        // ========================================================

        EditText resetEmail =
                createInput(
                        "E-posta adresi"
                );


        resetEmail.setInputType(
                InputType.TYPE_CLASS_TEXT
                        |
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );


        String currentEmail =
                etEmail == null
                        ? ""
                        : etEmail.getText()
                        .toString()
                        .trim();


        if (!currentEmail.isEmpty()) {

            resetEmail.setText(
                    currentEmail
            );


            resetEmail.setSelection(
                    resetEmail.length()
            );
        }


        LinearLayout.LayoutParams emailParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54)
                );


        emailParams.topMargin =
                dp(18);


        card.addView(
                resetEmail,
                emailParams
        );


        // ========================================================
        // BUTTON ROW
        // ========================================================

        LinearLayout buttonRow =
                new LinearLayout(
                        this
                );


        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout.LayoutParams buttonRowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );


        buttonRowParams.topMargin =
                dp(13);


        // ========================================================
        // CANCEL
        // ========================================================

        TextView btnCancel =
                new TextView(
                        this
                );


        btnCancel.setText(
                "Vazgeç"
        );


        btnCancel.setTextSize(
                13
        );


        btnCancel.setTextColor(
                GreenTheme.parse(
                        COLOR_GREEN
                )
        );


        btnCancel.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        btnCancel.setGravity(
                Gravity.CENTER
        );


        btnCancel.setBackground(
                rounded(
                        "#132732",
                        15,
                        COLOR_BORDER,
                        1
                )
        );


        buttonRow.addView(
                btnCancel,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        0.37f
                )
        );


        // ========================================================
        // SEND
        // ========================================================

        TextView btnSend =
                new TextView(
                        this
                );


        btnSend.setText(
                "Bağlantı Gönder  →"
        );


        btnSend.setTextSize(
                13
        );


        btnSend.setTextColor(
                Color.WHITE
        );


        btnSend.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        btnSend.setGravity(
                Gravity.CENTER
        );


        btnSend.setBackground(
                rounded(
                        COLOR_GREEN_BUTTON,
                        15,
                        null,
                        0
                )
        );


        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        0.63f
                );


        sendParams.leftMargin =
                dp(8);


        buttonRow.addView(
                btnSend,
                sendParams
        );


        card.addView(
                buttonRow,
                buttonRowParams
        );


        // ========================================================
        // CLICK CANCEL
        // ========================================================

        btnCancel.setOnClickListener(
                view -> dialog.dismiss()
        );


        // ========================================================
        // CLICK SEND
        // ========================================================

        btnSend.setOnClickListener(
                view -> {

                    String email =
                            resetEmail
                                    .getText()
                                    .toString()
                                    .trim();


                    if (email.isEmpty()) {

                        resetEmail.setError(
                                "E-posta adresini yaz"
                        );

                        resetEmail.requestFocus();

                        return;
                    }


                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                        resetEmail.setError(
                                "Geçerli bir e-posta adresi yaz"
                        );

                        resetEmail.requestFocus();

                        return;
                    }


                    btnSend.setEnabled(
                            false
                    );


                    btnCancel.setEnabled(
                            false
                    );


                    btnSend.setText(
                            "Gönderiliyor..."
                    );


                    firebaseAuth.setLanguageCode(
                            "tr"
                    );


                    firebaseAuth
                            .sendPasswordResetEmail(
                                    email
                            )
                            .addOnCompleteListener(
                                    LoginActivity.this,
                                    task -> {

                                        if (!task.isSuccessful()) {

                                            btnSend.setEnabled(
                                                    true
                                            );


                                            btnCancel.setEnabled(
                                                    true
                                            );


                                            btnSend.setText(
                                                    "Bağlantı Gönder  →"
                                            );


                                            showMessage(
                                                    authErrorMessage(task.getException(), "reset")
                                            );


                                            return;
                                        }


                                        showPasswordResetSuccess(
                                                card,
                                                dialog,
                                                email
                                        );
                                    }
                            );
                }
        );


        // ========================================================
        // DIALOG
        // ========================================================

        ScrollView resetScroll = new ScrollView(this);
        resetScroll.addView(card);
        dialog.setContentView(resetScroll);


        dialog.setCancelable(
                true
        );


        Window window =
                dialog.getWindow();


        if (window != null) {

            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.TRANSPARENT
                    )
            );


            window.addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
            );


            WindowManager.LayoutParams params =
                    window.getAttributes();


            params.width =
                    WindowManager.LayoutParams.MATCH_PARENT;


            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;


            params.dimAmount =
                    0.46f;


            params.gravity =
                    Gravity.CENTER;


            window.setAttributes(
                    params
            );
        }


        dialog.show();


        Window shownWindow =
                dialog.getWindow();


        if (shownWindow != null) {
            shownWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            WindowManager.LayoutParams params =
                    shownWindow.getAttributes();


            params.width =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels
                            -
                            dp(38);


            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;


            shownWindow.setAttributes(
                    params
            );
        }


        card.setScaleX(
                0.94f
        );


        card.setScaleY(
                0.94f
        );


        card.setAlpha(
                0f
        );


        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();
    }


    // ============================================================
    // RESET SUCCESS
    // ============================================================

    private void showPasswordResetSuccess(
            LinearLayout card,
            Dialog dialog,
            String email
    ) {

        card.removeAllViews();


        card.setGravity(
                Gravity.CENTER_HORIZONTAL
        );


        // ========================================================
        // CHECK
        // ========================================================

        TextView check =
                new TextView(
                        this
                );


        check.setText(
                "✓"
        );


        check.setTextSize(
                30
        );


        check.setTextColor(
                Color.WHITE
        );


        check.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        check.setGravity(
                Gravity.CENTER
        );


        check.setBackground(
                rounded(
                        "#659646",
                        32,
                        null,
                        0
                )
        );


        card.addView(
                check,
                new LinearLayout.LayoutParams(
                        dp(70),
                        dp(70)
                )
        );


        // ========================================================
        // TITLE
        // ========================================================

        TextView title =
                new TextView(
                        this
                );


        title.setText(
                "İstek alındı"
        );


        title.setTextSize(
                21
        );


        title.setTextColor(
                GreenTheme.parse(
                        COLOR_GREEN
                )
        );


        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        title.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        titleParams.topMargin =
                dp(14);


        card.addView(
                title,
                titleParams
        );


        // ========================================================
        // TEXT
        // ========================================================

        TextView message =
                new TextView(
                        this
                );


        message.setText(
                "Bu adres için şifre sıfırlama mümkünse bağlantı gönderilecektir.\n\n"
                        + "E-posta kutunu ve spam klasörünü kontrol et."
        );


        message.setTextSize(
                12
        );


        message.setTextColor(
                GreenTheme.parse(
                        COLOR_MUTED
                )
        );


        message.setGravity(
                Gravity.CENTER
        );


        message.setLineSpacing(
                dp(3),
                1f
        );


        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        messageParams.topMargin =
                dp(8);


        card.addView(
                message,
                messageParams
        );


        // ========================================================
        // TAMAM
        // ========================================================

        TextView btnDone =
                new TextView(
                        this
                );


        btnDone.setText(
                "Tamam"
        );


        btnDone.setTextSize(
                14
        );


        btnDone.setTextColor(
                Color.WHITE
        );


        btnDone.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        btnDone.setGravity(
                Gravity.CENTER
        );


        btnDone.setBackground(
                rounded(
                        COLOR_GREEN_BUTTON,
                        15,
                        null,
                        0
                )
        );


        LinearLayout.LayoutParams doneParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );


        doneParams.topMargin =
                dp(18);


        card.addView(
                btnDone,
                doneParams
        );


        btnDone.setOnClickListener(
                view -> dialog.dismiss()
        );


        check.setScaleX(
                0.6f
        );


        check.setScaleY(
                0.6f
        );


        check.setAlpha(
                0f
        );


        check.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();
    }


    // ============================================================
    // MOD
    // ============================================================

    private void setRegisterMode(boolean register) {
        if (actionRunning) return;
        registerMode = register;
        tvTitle.setText(register ? R.string.auth_register_title : R.string.auth_login_title);
        tvSubtitle.setText(register ? R.string.auth_register_description : R.string.auth_login_description);
        googleLabel.setText(register ? R.string.auth_google_register : R.string.auth_google_login);
        btnGoogle.setContentDescription(googleLabel.getText());
        tvEmailDivider.setText(R.string.auth_email_divider);
        nameGroup.setVisibility(register ? View.VISIBLE : View.GONE);
        confirmGroup.setVisibility(register ? View.VISIBLE : View.GONE);
        strengthGroup.setVisibility(register ? View.VISIBLE : View.GONE);
        legalGroup.setVisibility(register ? View.VISIBLE : View.GONE);
        tvForgotPassword.setVisibility(register ? View.GONE : View.VISIBLE);
        etPassword.setHint(register ? R.string.auth_password_hint : R.string.auth_login_password_hint);
        etPassword.setImeOptions(register ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        btnSubmit.setText(register ? R.string.auth_register_submit : R.string.auth_login_submit);
        selectTab(btnRegisterTab, register);
        selectTab(btnLoginTab, !register);
        btnRegisterTab.setSelected(register);
        btnLoginTab.setSelected(!register);
        if (!register && (etName.hasFocus() || etConfirmPassword.hasFocus())) etEmail.requestFocus();
        refreshAuthControls();
    }

    private void selectTab(
            TextView tab,
            boolean selected
    ) {

        if (tab == null) {

            return;
        }


        if (selected) {

            tab.setTextColor(
                    Color.WHITE
            );


            tab.setBackground(
                    rounded(
                            COLOR_GREEN_BUTTON,
                            10,
                            null,
                            0
                    )
            );


        } else {

            tab.setTextColor(
                    GreenTheme.parse(
                            COLOR_MUTED
                    )
            );


            tab.setBackgroundColor(
                    Color.TRANSPARENT
            );
        }
    }


    // ============================================================
    // SUBMIT
    // ============================================================

    private void handleSubmit() {
        // pressEffect posts a delayed callback; recheck to prevent duplicate requests.
        if (actionRunning) return;

        String email =
                etEmail
                        .getText()
                        .toString()
                        .trim();


        String password =
                etPassword
                        .getText()
                        .toString();


        if (email.isEmpty()) {

            showMessage(
                    "E-posta adresini gir"
            );

            etEmail.requestFocus();

            return;
        }


        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            showMessage(
                    "Geçerli bir e-posta adresi gir."
            );

            etEmail.requestFocus();

            return;
        }


        if (password.isEmpty()) {
            showMessage("Şifreni gir.");
            etPassword.requestFocus();
            return;
        }

        if (registerMode && !AuthRegistrationPolicy.validPassword(password)) {

            showMessage(
                    getString(R.string.auth_password_rejected)
            );

            etPassword.requestFocus();

            return;
        }


        if (firebaseAuth == null) {

            showMessage(
                    "Firebase bağlantısı hazır değil."
            );

            return;
        }


        if (registerMode) {

            String name =
                    etName
                            .getText()
                            .toString()
                            .trim();


            String confirm =
                    etConfirmPassword
                            .getText()
                            .toString();


            if (name.length() < 2) {

                showMessage(
                        "Adını ve soyadını gir."
                );

                etName.requestFocus();

                return;
            }


            if (!password.equals(
                    confirm
            )) {

                showMessage(
                        "Şifreler birbiriyle aynı değil."
                );

                etConfirmPassword.requestFocus();

                return;
            }


            if (!canStartRegistration()) return;

            createFirebaseAccount(
                    name,
                    email,
                    password
            );


            return;
        }


        signInWithEmail(
                email,
                password
        );
    }


    // ============================================================
    // HESAP OLUŞTUR
    // ============================================================

    private void createFirebaseAccount(
            String name,
            String email,
            String password
    ) {

        setActionRunning(
                true,
                "Hesap oluşturuluyor..."
        );


        firebaseAuth
                .createUserWithEmailAndPassword(
                        email,
                        password
                )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                setActionRunning(
                                        false,
                                        null
                                );


                                showMessage(authErrorMessage(task.getException(), "register"));

                                return;
                            }


                            FirebaseUser user =
                                    firebaseAuth.getCurrentUser();


                            if (user == null) {

                                setActionRunning(
                                        false,
                                        null
                                );


                                showMessage(
                                        "Kullanıcı bilgisi alınamadı."
                                );

                                return;
                            }


                            UserProfileChangeRequest updates =
                                    new UserProfileChangeRequest
                                            .Builder()
                                            .setDisplayName(
                                                    name
                                            )
                                            .build();


                            user.updateProfile(
                                    updates
                            )
                                    .addOnCompleteListener(
                                            this,
                                            profileTask -> {
                                                if (!profileTask.isSuccessful()) {
                                                    showMessage("Hesabın oluşturuldu; adın kaydedilemedi. Profilinden güncelleyebilirsin.");
                                                } else {
                                                    // Meydan gönderisindeki ad oturum belgesinden doğrulanır;
                                                    // kayıt anındaki belge adı içermediği için yenilenir.
                                                    user.getIdToken(true);
                                                }

                                                sessionManager
                                                        .startUserSession(
                                                                name,
                                                                email,
                                                                user.getUid()
                                                        );


                                                showSuccessAndContinue(
                                                        "✓   Hesabın hazır"
                                                );
                                            }
                                    );
                        }
                );
    }


    // ============================================================
    // E-POSTA GİRİŞ
    // ============================================================

    private void signInWithEmail(
            String email,
            String password
    ) {

        setActionRunning(
                true,
                "Giriş yapılıyor..."
        );


        firebaseAuth
                .signInWithEmailAndPassword(
                        email,
                        password
                )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                setActionRunning(
                                        false,
                                        null
                                );


                                showMessage(
                                        authErrorMessage(task.getException(), "login")
                                );

                                return;
                            }


                            FirebaseUser user =
                                    firebaseAuth.getCurrentUser();


                            if (user == null) {

                                setActionRunning(
                                        false,
                                        null
                                );

                                showMessage("Kullanıcı bilgisi alınamadı. Lütfen tekrar dene.");
                                return;
                            }


                            String userName =
                                    user.getDisplayName();


                            if (userName == null
                                    ||
                                    userName.trim().isEmpty()) {

                                userName =
                                        "Üretici";
                            }


                            String userEmail =
                                    user.getEmail();


                            if (userEmail == null) {

                                userEmail =
                                        email;
                            }


                            sessionManager.startUserSession(
                                    userName,
                                    userEmail,
                                    user.getUid()
                            );


                            showSuccessAndContinue(
                                    "✓   Giriş başarılı"
                            );
                        }
                );
    }


    // ============================================================
    // SUCCESS
    // ============================================================

    private void showSuccessAndContinue(
            String message
    ) {

        actionRunning = true;
        refreshAuthControls();
        authProgress.setVisibility(View.GONE);

        btnSubmit.setEnabled(
                false
        );


        btnSubmit.setText(
                message
        );


        btnSubmit.setBackground(
                rounded(
                        "#66883E",
                        15,
                        null,
                        0
                )
        );


        handler.postDelayed(
                this::openMainScreen,
                700
        );
    }


    // ============================================================
    // GOOGLE
    // ============================================================

    private void openGoogleAccountChooser() {

        if (credentialManager == null) {

            showMessage(
                    "Google giriş sistemi hazır değil."
            );

            return;
        }


        if (firebaseAuth == null) {

            showMessage(
                    "Firebase bağlantısı hazır değil."
            );

            return;
        }


        try {

            setSocialButtonsEnabled(
                    false
            );


            String webClientId =
                    getString(
                            R.string.default_web_client_id
                    );


            GetSignInWithGoogleOption googleOption =
                    new GetSignInWithGoogleOption
                            .Builder(
                                    webClientId
                            )
                            .build();


            GetCredentialRequest request =
                    new GetCredentialRequest
                            .Builder()
                            .addCredentialOption(
                                    googleOption
                            )
                            .build();


            credentialManager
                    .getCredentialAsync(
                            LoginActivity.this,
                            request,
                            new CancellationSignal(),
                            Executors.newSingleThreadExecutor(),
                            new CredentialManagerCallback
                                    <
                                            GetCredentialResponse,
                                            GetCredentialException
                                            >() {


                                @Override
                                public void onResult(
                                        GetCredentialResponse result
                                ) {

                                    runOnUiThread(
                                            () -> {

                                                setSocialButtonsEnabled(
                                                        true
                                                );


                                                handleGoogleCredential(
                                                        result.getCredential()
                                                );
                                            }
                                    );
                                }


                                @Override
                                public void onError(
                                        GetCredentialException e
                                ) {

                                    runOnUiThread(
                                            () -> {

                                                setSocialButtonsEnabled(
                                                        true
                                                );


                                                showMessage(
                                                        "Google girişi tamamlanamadı: "
                                                                + safeMessage(e)
                                                );
                                            }
                                    );
                                }
                            }
                    );


        } catch (Exception e) {

            setSocialButtonsEnabled(
                    true
            );


            showMessage(
                    "Google giriş hatası: "
                            + safeMessage(e)
            );
        }
    }


    // ============================================================
    // GOOGLE CREDENTIAL
    // ============================================================

    private void handleGoogleCredential(
            Credential credential
    ) {

        try {

            if (!(credential instanceof CustomCredential)) {

                showMessage(
                        "Google kimliği alınamadı."
                );

                return;
            }


            CustomCredential customCredential =
                    (CustomCredential) credential;


            if (!GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(
                            customCredential.getType()
                    )) {

                showMessage(
                        "Beklenmeyen Google kimlik türü."
                );

                return;
            }


            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential
                            .createFrom(
                                    customCredential.getData()
                            );


            String idToken =
                    googleCredential.getIdToken();


            if (idToken == null
                    ||
                    idToken.trim().isEmpty()) {

                showMessage(
                        "Google kimlik anahtarı alınamadı."
                );

                return;
            }


            firebaseAuthWithGoogle(
                    idToken
            );


        } catch (Exception e) {

            showMessage(
                    "Google hesabı okunamadı: "
                            + safeMessage(e)
            );
        }
    }


    // ============================================================
    // GOOGLE FIREBASE
    // ============================================================

    private void firebaseAuthWithGoogle(
            String idToken
    ) {

        setActionRunning(
                true,
                "Google hesabı bağlanıyor..."
        );


        AuthCredential credential =
                GoogleAuthProvider.getCredential(
                        idToken,
                        null
                );


        firebaseAuth
                .signInWithCredential(
                        credential
                )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                setActionRunning(
                                        false,
                                        null
                                );


                                showMessage(
                                        "Google ile giriş başarısız."
                                );

                                return;
                            }


                            FirebaseUser user =
                                    firebaseAuth.getCurrentUser();


                            if (user == null) {

                                setActionRunning(
                                        false,
                                        null
                                );

                                return;
                            }


                            String userName =
                                    user.getDisplayName();


                            if (userName == null
                                    ||
                                    userName.trim().isEmpty()) {

                                userName =
                                        "Üretici";
                            }


                            String userEmail =
                                    user.getEmail();


                            if (userEmail == null) {

                                userEmail =
                                        "";
                            }


                            sessionManager.startUserSession(
                                    userName,
                                    userEmail
                            );


                            showSuccessAndContinue(
                                    "✓   Hesabın hazır"
                            );
                        }
                );
    }


    // ============================================================
    // SONRAKİ EKRAN
    // ============================================================

    private void openMainScreen() {

        if (isFinishing()) {
            return;
        }

        FirebaseUser currentUser =
                firebaseAuth == null
                        ? null
                        : firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            routeAuthenticatedUser(currentUser);
            return;
        }

        openMainForGuest();
    }


    private void routeAuthenticatedUser(
            FirebaseUser user
    ) {

        if (user == null || isFinishing() || routingAfterAuth) {
            return;
        }

        routingAfterAuth = true;

        String uid =
                user.getUid() == null
                        ? ""
                        : user.getUid().trim();

        if (uid.isEmpty()) {
            routingAfterAuth = false;
            Toast.makeText(
                    this,
                    "Hesap kimliği alınamadı.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String userName =
                user.getDisplayName();

        if (userName == null || userName.trim().isEmpty()) {
            userName = sessionManager.getUserName();
        }

        if (userName == null || userName.trim().isEmpty()) {
            userName = "Üretici";
        }

        String userEmail =
                user.getEmail();

        if (userEmail == null) {
            userEmail = "";
        }

        sessionManager.startUserSession(
                userName,
                userEmail,
                uid
        );

        int ownedCount =
                gardenRepository.getGardenCountForOwner(uid);

        int legacyCount =
                gardenRepository.getUnownedGardenCount();

        if (ownedCount == 0 && legacyCount > 0) {
            gardenRepository.claimUnownedGardens(uid);
        }

        // FINAL AKIŞ: Bahçe var/yok ayrımı yapmadan her girişte iki yönlü birleştirme.
        // Böylece yeni telefondaki eksik bahçeler de iner, mevcut telefondaki yeni kayıtlar da korunur.
        if (privateCloudSyncManager == null) {
            privateCloudSyncManager = new PrivateCloudSyncManager(databaseHelper);
        }

        privateCloudSyncManager.sync(uid, new PrivateCloudSyncManager.Callback() {
            @Override
            public void onComplete() {
                if (isFinishing()) return;
                if (gardenRepository.getGardenCountForOwner(uid) > 0) {
                    openMainForUser(true);
                } else {
                    openFirstGarden();
                }
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing()) return;

                // Offline-first: telefonda veri varsa kullanıcı uygulamaya girebilsin.
                if (gardenRepository.getGardenCountForOwner(uid) > 0) {
                    openMainForUser(true);
                    return;
                }

                routingAfterAuth = false;
                Toast.makeText(
                        LoginActivity.this,
                        "Bulut verileri alınamadı. İnternet bağlantını kontrol edip tekrar dene.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }


    private void syncOperationsThenOpenMain(
            String uid
    ) {

        if (operationCloudRepository == null) {
            syncProductionThenOpenMain(uid);
            return;
        }

        operationCloudRepository.restoreAllOperations(
                uid,
                new OperationCloudRepository.RestoreCallback() {
                    @Override
                    public void onComplete(int restoredCount) {

                        if (isFinishing()) {
                            return;
                        }

                        operationCloudRepository.syncAllLocalOperations(
                                uid
                        );

                        syncProductionThenOpenMain(
                                uid
                        );
                    }

                    @Override
                    public void onError(Exception error) {

                        if (isFinishing()) {
                            return;
                        }

                        operationCloudRepository.syncAllLocalOperations(
                                uid
                        );

                        syncProductionThenOpenMain(
                                uid
                        );
                    }
                }
        );
    }




    private void syncProductionThenOpenMain(
            String uid
    ) {

        if (productionCloudRepository == null) {
            syncSupportThenOpenMain(uid);
            return;
        }

        productionCloudRepository.restoreAll(
                uid,
                new ProductionCloudRepository.RestoreCallback() {
                    @Override
                    public void onComplete(int restoredCount) {

                        if (isFinishing()) {
                            return;
                        }

                        productionCloudRepository.syncAllLocal(uid);
                        syncSupportThenOpenMain(uid);
                    }

                    @Override
                    public void onError(Exception error) {

                        if (isFinishing()) {
                            return;
                        }

                        productionCloudRepository.syncAllLocal(uid);
                        syncSupportThenOpenMain(uid);
                    }
                }
        );
    }


    private void syncSupportThenOpenMain(
            String uid
    ) {

        if (supportCloudRepository == null) {
            openMainForUser(true);
            return;
        }

        supportCloudRepository.restoreAll(
                uid,
                new SupportCloudRepository.RestoreCallback() {
                    @Override
                    public void onComplete(int restoredCount) {
                        if (isFinishing()) return;
                        supportCloudRepository.syncAllLocal(uid);
                        openMainForUser(true);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing()) return;
                        supportCloudRepository.syncAllLocal(uid);
                        openMainForUser(true);
                    }
                }
        );
    }

    private void openFirstGarden() {
        // Kullanım şekli: önce sorulur; tezgâhçı bahçe eklemeden Tezgâh'a geçer.
        String role = RoleActivity.role(this);
        if (!UserRole.chosen(role)) {
            startActivity(new Intent(this, RoleActivity.class).putExtra(RoleActivity.EXTRA_FIRST_RUN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        if (!UserRole.needsFirstGarden(role)) {
            openMainForUser(true);
            return;
        }

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        AddGardenActivity.class
                );

        intent.putExtra(
                "first_garden",
                true
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }


    private void openMainForUser(
            boolean welcomeBack
    ) {

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        MainActivity.class
                );

        intent.putExtra(
                "from_login",
                true
        );

        intent.putExtra(
                "welcome_back",
                welcomeBack
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }


    private void openMainForGuest() {

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        MainActivity.class
                );

        intent.putExtra(
                "from_login",
                true
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }


    // ============================================================
    // AKSİYON
    // ============================================================

    private void setActionRunning(
            boolean running,
            String text
    ) {

        actionRunning =
                running;


        if (btnSubmit != null) {

            btnSubmit.setEnabled(
                    !running
            );


            btnSubmit.setAlpha(
                    running
                            ? 0.86f
                            : 1f
            );


            if (text != null) {

                btnSubmit.setText(
                        text
                );


            } else {

                btnSubmit.setText(
                        registerMode
                                ? "Hesabımı Oluştur   →"
                                : "Giriş Yap   →"
                );
            }
        }


        setSocialButtonsEnabled(!running);
        refreshAuthControls();
    }


    // ============================================================
    // SOCIAL BUTTON STATE
    // ============================================================

    private String authErrorMessage(Exception error, String operation) {
        String code = error instanceof FirebaseAuthException
                ? ((FirebaseAuthException) error).getErrorCode() : "";
        return AuthErrorMessages.message(code,
                error instanceof FirebaseNetworkException,
                error instanceof FirebaseTooManyRequestsException, operation);
    }

    private void setSocialButtonsEnabled(
            boolean enabled
    ) {

        LinearLayout[] buttons = {
                btnGoogle
        };


        for (LinearLayout button : buttons) {

            if (button != null) {

                button.setEnabled(
                        enabled
                );


                button.setAlpha(
                        enabled
                                ? 1f
                                : 0.6f
                );
            }
        }
    }


    // ============================================================
    // PASSWORD
    // ============================================================

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        applyPasswordVisibility();
    }

    private void applyPasswordVisibility() {
        for (EditText field : new EditText[]{etPassword, etConfirmPassword}) {
            int start = field.getSelectionStart();
            int end = field.getSelectionEnd();
            field.setTransformationMethod(passwordVisible ? null : PasswordTransformationMethod.getInstance());
            field.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (start >= 0 && end >= 0) {
                field.setSelection(Math.min(start, field.length()), Math.min(end, field.length()));
            }
        }
        for (ImageButton eye : new ImageButton[]{passwordEye, confirmEye}) {
            eye.setImageResource(passwordVisible ? R.drawable.auth_eye_off : R.drawable.auth_eye);
            eye.setContentDescription(getString(passwordVisible
                    ? R.string.auth_hide_passwords : R.string.auth_show_passwords));
        }
    }

    private void pressEffect(
            View view,
            Runnable action
    ) {

        if (view == null) {

            if (action != null) {

                action.run();
            }

            return;
        }


        view.animate()
                .cancel();


        view.animate()
                .scaleX(0.975f)
                .scaleY(0.975f)
                .setDuration(80)
                .withEndAction(
                        () -> view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .withEndAction(
                                        () -> {

                                            if (action != null) {

                                                action.run();
                                            }
                                        }
                                )
                                .start()
                )
                .start();
    }


    // ============================================================
    // TAB
    // ============================================================

    private TextView createTab(
            String text
    ) {

        TextView tab =
                new TextView(
                        this
                );


        tab.setText(
                text
        );


        tab.setTextSize(
                13
        );


        tab.setGravity(
                Gravity.CENTER
        );


        tab.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        tab.setClickable(
                true
        );


        tab.setFocusable(
                true
        );


        return tab;
    }


    // ============================================================
    // SOCIAL BUTTON
    // ============================================================

    private LinearLayout createSocialButton(
            int iconResource,
            String label
    ) {

        LinearLayout button =
                new LinearLayout(
                        this
                );


        button.setOrientation(
                LinearLayout.HORIZONTAL
        );


        button.setGravity(
                Gravity.CENTER_VERTICAL
        );


        button.setPadding(
                dp(18),
                0,
                dp(18),
                0
        );


        button.setBackground(
                rounded(
                        "#132A38",
                        15,
                        COLOR_BORDER,
                        1
                )
        );


        button.setClickable(
                true
        );


        button.setFocusable(
                true
        );


        // ========================================================
        // ICON
        // ========================================================

        ImageView icon =
                new ImageView(
                        this
                );


        icon.setImageResource(
                iconResource
        );


        icon.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );


        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(28),
                        dp(28)
                );


        button.addView(
                icon,
                iconParams
        );


        // ========================================================
        // LABEL
        // ========================================================

        TextView text =
                new TextView(
                        this
                );


        text.setText(
                label
        );


        text.setTextSize(
                13
        );


        text.setTextColor(
                GreenTheme.parse(
                        COLOR_TEXT
                )
        );


        text.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        text.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                );


        textParams.leftMargin =
                dp(14);


        button.addView(
                text,
                textParams
        );


        return button;
    }


    private LinearLayout.LayoutParams socialButtonParams() {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(46)
                );


        params.topMargin =
                dp(6);


        return params;
    }


    // ============================================================
    // INPUT
    // ============================================================

    private EditText createInput(
            String hint
    ) {

        EditText input =
                new EditText(
                        this
                );


        input.setHint(
                hint
        );


        input.setTextSize(
                13
        );


        input.setSingleLine(
                true
        );


        input.setTextColor(
                GreenTheme.parse(
                        COLOR_TEXT
                )
        );


        input.setHintTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        input.setPadding(
                dp(13),
                0,
                dp(13),
                0
        );


        input.setBackground(
                rounded(
                        COLOR_INPUT,
                        15,
                        COLOR_BORDER,
                        1
                )
        );


        return input;
    }


    private LinearLayout.LayoutParams inputParams(
            boolean topMargin
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );


        if (topMargin) {

            params.topMargin =
                    dp(7);
        }


        return params;
    }


    // ============================================================
    // DIVIDER
    // ============================================================

    private TextView createSectionDivider(
            String text
    ) {

        TextView divider =
                new TextView(
                        this
                );


        divider.setText(
                text
        );


        divider.setTextSize(
                10
        );


        divider.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        divider.setGravity(
                Gravity.CENTER
        );


        return divider;
    }


    // ============================================================
    // MESSAGE
    // ============================================================

    private void showMessage(
            String message
    ) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safeMessage(
            Exception e
    ) {

        if (e == null) {

            return "Bilinmeyen hata";
        }


        String message =
                e.getMessage();


        if (message == null
                ||
                message.trim().isEmpty()) {

            return e
                    .getClass()
                    .getSimpleName();
        }


        return message;
    }


    // ============================================================
    // DRAWABLE
    // ============================================================

    private GradientDrawable rounded(
            String color,
            int radius,
            String strokeColor,
            int strokeWidth
    ) {

        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                GreenTheme.parse(
                        color
                )
        );


        drawable.setCornerRadius(
                dp(radius)
        );


        if (strokeColor != null
                &&
                strokeWidth > 0) {

            drawable.setStroke(
                    dp(strokeWidth),
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
                        *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("auth_register_mode", registerMode);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );


        super.onDestroy();
    }
}
