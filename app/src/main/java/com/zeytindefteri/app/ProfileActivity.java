package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Zeytin Defteri - Sosyal Profil Ekranı
 *
 * Amaç:
 * - Facebook benzeri sosyal profil mantığı
 * - Kullanıcının kendi gönderilerini profilde göstermek
 * - Profil düzenleme / yeni gönderi / çıkış
 * - Mevcut SQLite + Firebase + Session altyapısını bozmamak
 *
 * Not:
 * Bu sürüm tamamen programatik View yapısını kullanır.
 * activity_profile.xml gerektirmez.
 */
public class ProfileActivity extends Activity {

    static final String EXTRA_CONFIRM_LOGOUT = "settings_confirm_logout";


    // ============================================================
    // CORE
    // ============================================================

    private DatabaseHelper databaseHelper;
    private SessionManager sessionManager;
    private FirebaseAuth firebaseAuth;

    // ============================================================
    // PROFILE PHOTO
    // ============================================================

    private static final int REQUEST_PICK_PROFILE_PHOTO = 4201;

    private static final String PROFILE_PREFS =
            "zeytin_profile_prefs";

    private static final String KEY_PHOTO_MODE_PREFIX =
            "photo_mode_";

    private static final String KEY_CUSTOM_PHOTO_URI_PREFIX =
            "custom_photo_uri_";

    private static final String PHOTO_MODE_AUTO =
            "AUTO";

    private static final String PHOTO_MODE_CUSTOM =
            "CUSTOM";

    private static final String PHOTO_MODE_GOOGLE =
            "GOOGLE";

    private static final String PHOTO_MODE_DEFAULT =
            "DEFAULT";

    private SharedPreferences profilePreferences;

    private ImageView ivProfilePhoto;
    private TextView tvProfilePhotoFallback;

    private Bitmap cachedGoogleProfileBitmap;
    private boolean googleProfilePhotoLoading = false;

    private final ArrayList<ProfilePhotoTarget> profilePhotoTargets =
            new ArrayList<>();


    // ============================================================
    // SOCIAL
    // ============================================================

    private FeedManager feedManager;
    private ReactionManager reactionManager;
    private CommentManager commentManager;
    private PostMediaManager postMediaManager;


    // ============================================================
    // ROOT / DYNAMIC
    // ============================================================

    private ScrollView scrollView;
    private LinearLayout contentRoot;
    private LinearLayout dynamicTabContainer;

    private TextView tvProfileName;
    private TextView tvProfileMeta;
    private TextView tvGardenCount;
    private TextView tvPostCount;
    private TextView tvHarvestCount;

    private TextView tabPosts;
    private TextView tabGardens;
    private TextView tabAbout;

    private String activeTab = "POSTS";


    // ============================================================
    // COLORS
    // ============================================================

    private static final int COLOR_BG =
            GreenTheme.color(R.color.zd_bg);

    private static final int COLOR_CARD =
            GreenTheme.color(R.color.zd_card);

    private static final int COLOR_WHITE =
            GreenTheme.parse("#FFFFFF");

    private static final int COLOR_DARK =
            GreenTheme.color(R.color.zd_text);

    private static final int COLOR_GREEN =
            GreenTheme.parse("#2F6B3F");

    private static final int COLOR_GREEN_2 =
            GreenTheme.parse("#3C7A4B");

    private static final int COLOR_SOFT_GREEN =
            GreenTheme.color(R.color.zd_green_soft);

    private static final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);

    private static final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);

    private static final int COLOR_MUTED =
            GreenTheme.color(R.color.zd_muted);

    private static final int COLOR_DIVIDER =
            GreenTheme.color(R.color.zd_border);

    private static final int COLOR_DANGER =
            GreenTheme.parse("#A3423A");

    private static final int COLOR_TAB_BG =
            GreenTheme.color(R.color.zd_green_soft);


    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        hideActionBar();
        setupStatusBar();

        setupCore();
        setupSocialManagers();

        buildScreen();
        refreshProfile();
        if (getIntent().getBooleanExtra(EXTRA_CONFIRM_LOGOUT, false)) {
            getIntent().removeExtra(EXTRA_CONFIRM_LOGOUT);
            confirmLogout();
        }
    }


    @Override
    protected void onResume() {

        super.onResume();

        if (databaseHelper != null
                && dynamicTabContainer != null) {

            refreshProfile();
        }
    }


    @Override
    protected void onDestroy() {

        if (databaseHelper != null) {

            try {
                databaseHelper.close();
            } catch (Exception ignored) {
            }

            databaseHelper = null;
        }

        if (reactionManager != null) reactionManager.release();
        if (commentManager != null) commentManager.release();
        feedManager = null;
        reactionManager = null;
        commentManager = null;
        postMediaManager = null;

        super.onDestroy();
    }


    // ============================================================
    // SETUP
    // ============================================================

    private void hideActionBar() {

        if (getActionBar() != null) {
            getActionBar().hide();
        }
    }


    private void setupStatusBar() {

        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );
    }


    private void setupCore() {

        sessionManager =
                new SessionManager(this);

        profilePreferences =
                getSharedPreferences(
                        PROFILE_PREFS,
                        MODE_PRIVATE
                );

        databaseHelper =
                new DatabaseHelper(this);

        try {

            firebaseAuth =
                    FirebaseAuth.getInstance();

        } catch (Exception e) {

            firebaseAuth = null;
        }

        feedManager =
                new FeedManager(databaseHelper);
    }


    private void setupSocialManagers() {

        reactionManager =
                new ReactionManager(
                        this,
                        databaseHelper,
                        this::loadPostsTab
                );


        commentManager =
                new CommentManager(

                        this,

                        databaseHelper,

                        new CommentManager.UserProvider() {

                            @Override
                            public String getUserKey() {

                                return getCurrentReactionUserKey();
                            }

                            @Override
                            public String getUserName() {

                                return getCurrentDisplayName();
                            }
                        },

                        this::loadPostsTab
                );


        postMediaManager =
                new PostMediaManager(
                        this,
                        databaseHelper
                );
    }


    // ============================================================
    // SCREEN
    // ============================================================

    private void buildScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                COLOR_BG
        );


        // ========================================================
        // TOP BAR
        // ========================================================

        root.addView(
                createTopBar()
        );


        // ========================================================
        // SCROLL
        // ========================================================

        scrollView =
                new ScrollView(this);

        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);


        contentRoot =
                new LinearLayout(this);

        contentRoot.setOrientation(
                LinearLayout.VERTICAL
        );

        contentRoot.setPadding(
                dp(16),
                0,
                dp(16),
                dp(36)
        );


        createProfileHero(
                contentRoot
        );

        createProfileStats(
                contentRoot
        );

        createProfileActions(
                contentRoot
        );

        createShortBio(
                contentRoot
        );

        createTabs(
                contentRoot
        );


        dynamicTabContainer =
                new LinearLayout(this);

        dynamicTabContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams dynamicParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        dynamicParams.topMargin =
                dp(10);


        contentRoot.addView(
                dynamicTabContainer,
                dynamicParams
        );


        scrollView.addView(
                contentRoot
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
    }


    // ============================================================
    // TOP BAR
    // ============================================================

    private View createTopBar() {

        LinearLayout bar =
                new LinearLayout(this);

        bar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bar.setGravity(
                Gravity.CENTER_VERTICAL
        );

        bar.setPadding(
                dp(14),
                dp(10),
                dp(10),
                dp(10)
        );


        TextView back =
                createText(
                        "‹",
                        36,
                        COLOR_DARK,
                        true
                );

        back.setGravity(
                Gravity.CENTER
        );

        back.setOnClickListener(
                v -> finish()
        );


        bar.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );


        LinearLayout titleArea =
                new LinearLayout(this);

        titleArea.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView mini =
                createText(
                        "ZEYTİN DEFTERİ",
                        10,
                        COLOR_GREEN_2,
                        true
                );


        TextView title =
                createText(
                        "Profil",
                        24,
                        COLOR_DARK,
                        true
                );


        titleArea.addView(
                mini
        );

        titleArea.addView(
                title
        );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.leftMargin =
                dp(6);


        bar.addView(
                titleArea,
                titleParams
        );


        TextView menu =
                createText(
                        "•••",
                        20,
                        COLOR_DARK,
                        true
                );

        menu.setGravity(
                Gravity.CENTER
        );

        menu.setBackground(
                rounded(
                        COLOR_CARD,
                        999,
                        COLOR_BORDER,
                        1
                )
        );

        menu.setOnClickListener(
                v -> showAccountMenu()
        );


        bar.addView(
                menu,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );


        return bar;
    }


    // ============================================================
    // PROFILE HERO
    // ============================================================

    private void createProfileHero(
            LinearLayout parent
    ) {

        LinearLayout hero =
                new LinearLayout(this);

        hero.setOrientation(
                LinearLayout.VERTICAL
        );

        hero.setBackground(
                rounded(
                        COLOR_CARD,
                        24,
                        COLOR_BORDER,
                        1
                )
        );

        hero.setElevation(
                dp(2)
        );


        // ========================================================
        // COVER
        // ========================================================

        LinearLayout cover =
                new LinearLayout(this);

        cover.setOrientation(
                LinearLayout.VERTICAL
        );

        cover.setGravity(
                Gravity.CENTER
        );

        cover.setBackground(
                rounded(
                        COLOR_DARK,
                        24,
                        null,
                        0
                )
        );


        TextView coverIcon =
                createText(
                        "🌿  🫒  🌿",
                        30,
                        COLOR_WHITE,
                        false
                );

        coverIcon.setGravity(
                Gravity.CENTER
        );


        TextView coverTitle =
                createText(
                        "Zeytinliğim",
                        14,
                        GreenTheme.color(R.color.zd_green_soft),
                        true
                );

        coverTitle.setGravity(
                Gravity.CENTER
        );


        cover.addView(
                coverIcon
        );

        cover.addView(
                coverTitle
        );


        hero.addView(
                cover,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(145)
                )
        );


        // ========================================================
        // AVATAR
        // Google fotoğrafı -> kullanıcı fotoğrafı -> baş harf
        // ========================================================

        FrameLayout avatarFrame =
                new FrameLayout(this);

        avatarFrame.setBackground(
                rounded(
                        COLOR_CARD,
                        999,
                        COLOR_WHITE,
                        4
                )
        );

        avatarFrame.setClipToOutline(
                false
        );


        ivProfilePhoto =
                new ImageView(this);

        ivProfilePhoto.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        ivProfilePhoto.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        999,
                        COLOR_BORDER,
                        1
                )
        );

        ivProfilePhoto.setClipToOutline(
                true
        );


        tvProfilePhotoFallback =
                createText(
                        sessionManager.isGuest()
                                ? "🫒"
                                : getAvatarLetter(),
                        30,
                        COLOR_DARK,
                        true
                );

        tvProfilePhotoFallback.setGravity(
                Gravity.CENTER
        );

        tvProfilePhotoFallback.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        999,
                        COLOR_BORDER,
                        1
                )
        );


        FrameLayout.LayoutParams photoParams =
                new FrameLayout.LayoutParams(
                        dp(88),
                        dp(88),
                        Gravity.CENTER
                );


        avatarFrame.addView(
                ivProfilePhoto,
                photoParams
        );

        avatarFrame.addView(
                tvProfilePhotoFallback,
                photoParams
        );


        TextView cameraBadge =
                createText(
                        "📷",
                        15,
                        COLOR_DARK,
                        false
                );

        cameraBadge.setGravity(
                Gravity.CENTER
        );

        cameraBadge.setBackground(
                rounded(
                        COLOR_CARD,
                        999,
                        COLOR_BORDER,
                        1
                )
        );


        FrameLayout.LayoutParams cameraParams =
                new FrameLayout.LayoutParams(
                        dp(34),
                        dp(34),
                        Gravity.END | Gravity.BOTTOM
                );

        cameraParams.rightMargin =
                dp(1);

        cameraParams.bottomMargin =
                dp(1);


        avatarFrame.addView(
                cameraBadge,
                cameraParams
        );


        View.OnClickListener photoClickListener =
                v -> showProfilePhotoMenu();


        avatarFrame.setOnClickListener(
                photoClickListener
        );

        ivProfilePhoto.setOnClickListener(
                photoClickListener
        );

        tvProfilePhotoFallback.setOnClickListener(
                photoClickListener
        );

        cameraBadge.setOnClickListener(
                photoClickListener
        );


        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(
                        dp(100),
                        dp(100)
                );

        avatarParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        avatarParams.topMargin =
                dp(-50);


        hero.addView(
                avatarFrame,
                avatarParams
        );


        applyProfilePhoto(
                ivProfilePhoto,
                tvProfilePhotoFallback
        );


        // ========================================================
        // NAME
        // ========================================================

        tvProfileName =
                createText(
                        getCurrentDisplayName(),
                        24,
                        COLOR_DARK,
                        true
                );

        tvProfileName.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        nameParams.topMargin =
                dp(9);


        hero.addView(
                tvProfileName,
                nameParams
        );


        tvProfileMeta =
                createText(
                        buildProfileMeta(),
                        12,
                        COLOR_MUTED,
                        false
                );

        tvProfileMeta.setGravity(
                Gravity.CENTER
        );

        tvProfileMeta.setPadding(
                dp(18),
                dp(4),
                dp(18),
                dp(17)
        );


        hero.addView(
                tvProfileMeta
        );


        parent.addView(
                hero
        );
    }


    // ============================================================
    // STATS
    // ============================================================

    private void createProfileStats(
            LinearLayout parent
    ) {

        LinearLayout stats =
                new LinearLayout(this);

        stats.setOrientation(
                LinearLayout.HORIZONTAL
        );

        stats.setGravity(
                Gravity.CENTER
        );

        stats.setPadding(
                dp(8),
                dp(13),
                dp(8),
                dp(13)
        );

        stats.setBackground(
                rounded(
                        COLOR_CARD,
                        20,
                        COLOR_BORDER,
                        1
                )
        );


        LinearLayout.LayoutParams statsParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        statsParams.topMargin =
                dp(12);


        tvGardenCount =
                createStatBlock(
                        stats,
                        "0",
                        "Bahçe"
                );


        addVerticalDivider(
                stats
        );


        tvPostCount =
                createStatBlock(
                        stats,
                        "0",
                        "Gönderi"
                );


        addVerticalDivider(
                stats
        );


        tvHarvestCount =
                createStatBlock(
                        stats,
                        "0 kg",
                        "Hasat"
                );


        parent.addView(
                stats,
                statsParams
        );
    }


    private TextView createStatBlock(
            LinearLayout parent,
            String value,
            String label
    ) {

        LinearLayout block =
                new LinearLayout(this);

        block.setOrientation(
                LinearLayout.VERTICAL
        );

        block.setGravity(
                Gravity.CENTER
        );


        TextView valueView =
                createText(
                        value,
                        18,
                        COLOR_DARK,
                        true
                );

        valueView.setGravity(
                Gravity.CENTER
        );


        TextView labelView =
                createText(
                        label,
                        10,
                        COLOR_MUTED,
                        false
                );

        labelView.setGravity(
                Gravity.CENTER
        );


        block.addView(
                valueView
        );

        block.addView(
                labelView
        );


        parent.addView(
                block,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        return valueView;
    }


    private void addVerticalDivider(
            LinearLayout parent
    ) {

        View divider =
                new View(this);

        divider.setBackgroundColor(
                COLOR_DIVIDER
        );


        parent.addView(
                divider,
                new LinearLayout.LayoutParams(
                        dp(1),
                        dp(40)
                )
        );
    }


    // ============================================================
    // ACTION BUTTONS
    // ============================================================

    private void createProfileActions(
            LinearLayout parent
    ) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.topMargin =
                dp(12);


        TextView edit =
                createPillButton(
                        "✏️  Profili Düzenle",
                        COLOR_GREEN,
                        COLOR_WHITE
                );


        TextView newPost =
                createPillButton(
                        "＋  Gönderi",
                        COLOR_CARD,
                        COLOR_DARK
                );

        newPost.setBackground(
                rounded(
                        COLOR_CARD,
                        16,
                        COLOR_BORDER,
                        1
                )
        );


        LinearLayout.LayoutParams left =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                );

        left.rightMargin =
                dp(6);


        LinearLayout.LayoutParams right =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                );

        right.leftMargin =
                dp(6);


        row.addView(
                edit,
                left
        );

        row.addView(
                newPost,
                right
        );


        edit.setOnClickListener(
                v -> showEditProfileDialog()
        );


        newPost.setOnClickListener(
                v -> {

                    if (sessionManager.isGuest()) {

                        Toast.makeText(
                                this,
                                "Gönderi paylaşmak için hesapla giriş yapmalısın.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    startActivity(
                            new Intent(
                                    this,
                                    CreatePostActivity.class
                            )
                    );
                }
        );


        parent.addView(
                row,
                rowParams
        );
    }


    // ============================================================
    // SHORT BIO
    // ============================================================

    private void createShortBio(
            LinearLayout parent
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        card.setBackground(
                rounded(
                        COLOR_CARD,
                        18,
                        COLOR_BORDER,
                        1
                )
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin =
                dp(12);


        TextView title =
                createText(
                        "🌿 Üretici bilgileri",
                        14,
                        COLOR_DARK,
                        true
                );


        TextView body =
                createText(
                        buildBioText(),
                        12,
                        COLOR_TEXT,
                        false
                );

        body.setLineSpacing(
                dp(2),
                1f
        );

        body.setPadding(
                0,
                dp(7),
                0,
                0
        );


        card.addView(
                title
        );

        card.addView(
                body
        );


        parent.addView(
                card,
                params
        );
    }


    // ============================================================
    // TABS
    // ============================================================

    private void createTabs(
            LinearLayout parent
    ) {

        LinearLayout tabs =
                new LinearLayout(this);

        tabs.setOrientation(
                LinearLayout.HORIZONTAL
        );

        tabs.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams tabsParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        tabsParams.topMargin =
                dp(16);


        tabPosts =
                createTab(
                        "Gönderiler"
                );

        tabGardens =
                createTab(
                        "Bahçeler"
                );

        tabAbout =
                createTab(
                        "Hakkında"
                );


        tabs.addView(
                tabPosts,
                tabParams()
        );

        tabs.addView(
                tabGardens,
                tabParams()
        );

        tabs.addView(
                tabAbout,
                tabParams()
        );


        tabPosts.setOnClickListener(
                v -> changeTab("POSTS")
        );

        tabGardens.setOnClickListener(
                v -> changeTab("GARDENS")
        );

        tabAbout.setOnClickListener(
                v -> changeTab("ABOUT")
        );


        parent.addView(
                tabs,
                tabsParams
        );
    }


    private TextView createTab(
            String text
    ) {

        TextView tab =
                createText(
                        text,
                        12,
                        COLOR_MUTED,
                        true
                );

        tab.setGravity(
                Gravity.CENTER
        );

        tab.setPadding(
                dp(8),
                dp(11),
                dp(8),
                dp(11)
        );

        return tab;
    }


    private LinearLayout.LayoutParams tabParams() {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        params.leftMargin =
                dp(3);

        params.rightMargin =
                dp(3);

        return params;
    }


    private void changeTab(
            String tab
    ) {

        activeTab =
                tab;

        updateTabStyles();
        loadActiveTab();
    }


    private void updateTabStyles() {

        styleTab(
                tabPosts,
                "POSTS".equals(activeTab)
        );

        styleTab(
                tabGardens,
                "GARDENS".equals(activeTab)
        );

        styleTab(
                tabAbout,
                "ABOUT".equals(activeTab)
        );
    }


    private void styleTab(
            TextView tab,
            boolean active
    ) {

        if (tab == null) {
            return;
        }

        tab.setTextColor(
                active
                        ? COLOR_GREEN
                        : COLOR_MUTED
        );

        tab.setBackground(
                rounded(
                        active
                                ? COLOR_TAB_BG
                                : Color.TRANSPARENT,
                        999,
                        active
                                ? COLOR_BORDER
                                : null,
                        active
                                ? 1
                                : 0
                )
        );
    }


    // ============================================================
    // REFRESH
    // ============================================================

    private void refreshProfile() {

        if (tvProfileName != null) {

            tvProfileName.setText(
                    getCurrentDisplayName()
            );
        }


        if (tvProfilePhotoFallback != null) {

            tvProfilePhotoFallback.setText(
                    sessionManager.isGuest()
                            ? "🫒"
                            : getAvatarLetter()
            );
        }


        if (tvProfileMeta != null) {

            tvProfileMeta.setText(
                    buildProfileMeta()
            );
        }


        int gardenCount =
                0;

        double totalHarvest =
                0;


        try {

            gardenCount =
                    databaseHelper.getGardenCount();

        } catch (Exception ignored) {
        }


        try {

            totalHarvest =
                    databaseHelper.getTotalHarvestKg();

        } catch (Exception ignored) {
        }


        int postCount =
                getOwnPosts().size();


        if (tvGardenCount != null) {

            tvGardenCount.setText(
                    String.valueOf(gardenCount)
            );
        }


        if (tvPostCount != null) {

            tvPostCount.setText(
                    String.valueOf(postCount)
            );
        }


        if (tvHarvestCount != null) {

            tvHarvestCount.setText(
                    formatCompactKg(totalHarvest)
            );
        }


        updateTabStyles();
        loadActiveTab();
    }


    private void loadActiveTab() {

        if (dynamicTabContainer == null) {
            return;
        }


        if ("GARDENS".equals(activeTab)) {

            loadGardensTab();
            return;
        }


        if ("ABOUT".equals(activeTab)) {

            loadAboutTab();
            return;
        }


        loadPostsTab();
    }


    // ============================================================
    // POSTS TAB
    // ============================================================

    private void loadPostsTab() {

        if (dynamicTabContainer == null) {
            return;
        }


        dynamicTabContainer.removeAllViews();


        TextView section =
                createText(
                        "Gönderiler",
                        19,
                        COLOR_DARK,
                        true
                );

        section.setPadding(
                dp(2),
                dp(4),
                0,
                dp(10)
        );


        dynamicTabContainer.addView(
                section
        );


        List<FeedManager.PostItem> posts =
                getOwnPosts();


        if (tvPostCount != null) {

            tvPostCount.setText(
                    String.valueOf(posts.size())
            );
        }


        if (posts.isEmpty()) {

            dynamicTabContainer.addView(
                    createEmptyPostsView()
            );

            return;
        }


        for (FeedManager.PostItem post : posts) {

            if (post == null) {
                continue;
            }


            dynamicTabContainer.addView(
                    createProfilePostCard(post)
            );
        }
    }


    private List<FeedManager.PostItem> getOwnPosts() {

        ArrayList<FeedManager.PostItem> result =
                new ArrayList<>();


        if (feedManager == null) {
            return result;
        }


        List<FeedManager.PostItem> posts =
                feedManager.getLatestPosts(300);


        if (posts == null) {
            return result;
        }


        String rawFirebaseUid =
                getRawFirebaseUid();

        String currentName =
                safe(getCurrentDisplayName());


        for (FeedManager.PostItem post : posts) {

            if (post == null
                    || !post.active) {

                continue;
            }


            String postUserId =
                    safe(post.userId);


            boolean sameUser =
                    false;


            if (!rawFirebaseUid.isEmpty()
                    && rawFirebaseUid.equals(postUserId)) {

                sameUser =
                        true;
            }


            // Eski / lokal kayıtlarda user_id boş olabilir.
            if (!sameUser
                    && postUserId.isEmpty()
                    && !currentName.isEmpty()
                    && currentName.equalsIgnoreCase(
                    safe(post.userName)
            )) {

                sameUser =
                        true;
            }


            if (sameUser) {

                result.add(post);
            }
        }


        return result;
    }


    private View createEmptyPostsView() {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER
        );

        card.setPadding(
                dp(20),
                dp(28),
                dp(20),
                dp(28)
        );

        card.setBackground(
                rounded(
                        COLOR_CARD,
                        20,
                        COLOR_BORDER,
                        1
                )
        );


        TextView icon =
                createText(
                        "🫒",
                        34,
                        COLOR_DARK,
                        false
                );

        icon.setGravity(
                Gravity.CENTER
        );


        TextView title =
                createText(
                        "Henüz gönderin yok",
                        16,
                        COLOR_DARK,
                        true
                );

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                dp(9),
                0,
                0
        );


        TextView text =
                createText(
                        "Bahçenden bir gelişme, hasat sonucu veya üretici deneyimi paylaşabilirsin.",
                        12,
                        COLOR_MUTED,
                        false
                );

        text.setGravity(
                Gravity.CENTER
        );

        text.setPadding(
                dp(15),
                dp(5),
                dp(15),
                dp(14)
        );


        TextView button =
                createPillButton(
                        "＋ İlk gönderini paylaş",
                        COLOR_GREEN,
                        COLOR_WHITE
                );


        button.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                CreatePostActivity.class
                        )
                )
        );


        card.addView(icon);
        card.addView(title);
        card.addView(text);

        card.addView(
                button,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );


        return card;
    }


    // ============================================================
    // POST CARD
    // ============================================================

    private View createProfilePostCard(
            FeedManager.PostItem post
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(12)
        );

        card.setBackground(
                rounded(
                        COLOR_CARD,
                        20,
                        COLOR_BORDER,
                        1
                )
        );

        card.setElevation(
                dp(1)
        );


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin =
                dp(11);

        card.setLayoutParams(
                cardParams
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


        FrameLayout postAvatarFrame =
                createProfilePhotoView(
                        dp(44),
                        14
                );


        header.addView(
                postAvatarFrame,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );


        LinearLayout nameArea =
                new LinearLayout(this);

        nameArea.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView name =
                createText(
                        safeOr(
                                post.userName,
                                getCurrentDisplayName()
                        ),
                        14,
                        COLOR_DARK,
                        true
                );


        TextView meta =
                createText(
                        buildPostMeta(post),
                        10,
                        COLOR_MUTED,
                        false
                );


        nameArea.addView(
                name
        );

        nameArea.addView(
                meta
        );


        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        nameParams.leftMargin =
                dp(10);


        header.addView(
                nameArea,
                nameParams
        );


        TextView menu =
                createText(
                        "•••",
                        18,
                        COLOR_MUTED,
                        true
                );

        menu.setGravity(
                Gravity.CENTER
        );

        menu.setOnClickListener(
                v -> showOwnPostMenu(post)
        );


        header.addView(
                menu,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(42)
                )
        );


        card.addView(
                header
        );


        // ========================================================
        // CONTENT
        // ========================================================

        TextView body =
                createText(
                        safe(post.content),
                        13,
                        COLOR_TEXT,
                        false
                );

        body.setLineSpacing(
                dp(2),
                1f
        );


        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        bodyParams.topMargin =
                dp(11);


        card.addView(
                body,
                bodyParams
        );


        // ========================================================
        // MEDIA
        // ========================================================

        if (postMediaManager != null) {

            postMediaManager.addPostMediaToCard(
                    card,
                    post.id,
                    post.photoUri
            );
        }


        // ========================================================
        // TAG / LOCATION
        // ========================================================

        String tags =
                buildPostTags(post);


        if (!tags.isEmpty()) {

            TextView tag =
                    createText(
                            tags,
                            10,
                            COLOR_GREEN_2,
                            true
                    );


            LinearLayout.LayoutParams tagParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            tagParams.topMargin =
                    dp(8);


            card.addView(
                    tag,
                    tagParams
            );
        }


        // ========================================================
        // COUNTERS
        // ========================================================

        TextView counters =
                createText(
                        buildCounterText(post),
                        10,
                        COLOR_MUTED,
                        false
                );


        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        countParams.topMargin =
                dp(9);


        card.addView(
                counters,
                countParams
        );


        // ========================================================
        // DIVIDER
        // ========================================================

        View divider =
                new View(this);

        divider.setBackgroundColor(
                COLOR_DIVIDER
        );


        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        dividerParams.topMargin =
                dp(10);

        dividerParams.bottomMargin =
                dp(5);


        card.addView(
                divider,
                dividerParams
        );


        // ========================================================
        // ACTIONS
        // ========================================================

        LinearLayout actions =
                new LinearLayout(this);

        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );


        TextView reaction =
                createActionText(
                        "👍 Faydalı"
                );


        TextView comment =
                createActionText(
                        "💬 Yorum"
                );


        TextView share =
                createActionText(
                        "↗ Paylaş"
                );


        if (reactionManager != null) {

            reactionManager.setupReactionButton(
                    reaction,
                    post.id,
                    getCurrentReactionUserKey()
            );
        }


        comment.setOnClickListener(
                v -> {

                    if (commentManager != null) {

                        commentManager.showComments(
                                post.id
                        );
                    }
                }
        );


        share.setOnClickListener(
                v -> sharePost(post)
        );


        actions.addView(
                reaction,
                actionParams()
        );

        actions.addView(
                comment,
                actionParams()
        );

        actions.addView(
                share,
                actionParams()
        );


        card.addView(
                actions
        );


        return card;
    }


    private LinearLayout.LayoutParams actionParams() {

        return new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        );
    }


    private TextView createActionText(
            String text
    ) {

        TextView view =
                createText(
                        text,
                        11,
                        GreenTheme.color(R.color.zd_muted),
                        true
                );

        view.setGravity(
                Gravity.CENTER
        );

        view.setClickable(true);
        view.setFocusable(true);

        return view;
    }


    // ============================================================
    // OWN POST MENU
    // ============================================================

    private void showOwnPostMenu(
            FeedManager.PostItem post
    ) {

        String[] items = {
                "💬 Yorumları aç",
                "↗ Gönderiyi paylaş",
                "🗑 Gönderiyi sil"
        };


        new AlertDialog.Builder(this)
                .setTitle("Gönderi seçenekleri")
                .setItems(
                        items,
                        (dialog, which) -> {

                            if (which == 0) {

                                if (commentManager != null) {

                                    commentManager.showComments(
                                            post.id
                                    );
                                }

                                return;
                            }


                            if (which == 1) {

                                sharePost(post);
                                return;
                            }


                            if (which == 2) {

                                confirmDeletePost(post);
                            }
                        }
                )
                .setNegativeButton(
                        "Vazgeç",
                        null
                )
                .show();
    }


    private void confirmDeletePost(
            FeedManager.PostItem post
    ) {

        new AlertDialog.Builder(this)
                .setTitle("Gönderiyi sil?")
                .setMessage(
                        "Bu gönderi profilinden ve üretici akışından kaldırılacak."
                )
                .setNegativeButton(
                        "Vazgeç",
                        null
                )
                .setPositiveButton(
                        "Sil",
                        (dialog, which) -> deletePost(post)
                )
                .show();
    }


    private void deletePost(
            FeedManager.PostItem post
    ) {

        if (post == null
                || post.id <= 0) {

            return;
        }


        if (!isOwnPost(post)) {

            Toast.makeText(
                    this,
                    "Bu gönderiyi silme yetkin yok.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        try {

            int changed =
                    databaseHelper.deletePost(
                            post.id
                    );


            if (changed > 0) {

                Toast.makeText(
                        this,
                        "Gönderi silindi.",
                        Toast.LENGTH_SHORT
                ).show();

                refreshProfile();

            } else {

                Toast.makeText(
                        this,
                        "Gönderi silinemedi.",
                        Toast.LENGTH_SHORT
                ).show();
            }


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Gönderi silinirken hata oluştu.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    private boolean isOwnPost(
            FeedManager.PostItem post
    ) {

        if (post == null) {
            return false;
        }


        String uid =
                getRawFirebaseUid();


        if (!uid.isEmpty()
                && uid.equals(
                safe(post.userId)
        )) {

            return true;
        }


        return safe(post.userId).isEmpty()
                &&
                getCurrentDisplayName().equalsIgnoreCase(
                        safe(post.userName)
                );
    }


    // ============================================================
    // SHARE
    // ============================================================

    private void sharePost(
            FeedManager.PostItem post
    ) {

        if (post == null) {
            return;
        }


        StringBuilder text =
                new StringBuilder();


        text.append(
                safeOr(
                        post.userName,
                        "Zeytin üreticisi"
                )
        );


        if (!safe(post.content).isEmpty()) {

            text.append("\n\n");
            text.append(post.content.trim());
        }


        if (!safe(post.gardenName).isEmpty()) {

            text.append("\n\n🫒 ");
            text.append(post.gardenName.trim());
        }


        text.append(
                "\n\nZeytin Defteri"
        );


        Intent intent =
                new Intent(
                        Intent.ACTION_SEND
                );

        intent.setType(
                "text/plain"
        );

        intent.putExtra(
                Intent.EXTRA_TEXT,
                text.toString()
        );


        try {

            startActivity(
                    Intent.createChooser(
                            intent,
                            "Gönderiyi paylaş"
                    )
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Paylaşım uygulaması açılamadı.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // GARDENS TAB
    // ============================================================

    private void loadGardensTab() {

        dynamicTabContainer.removeAllViews();


        TextView title =
                createText(
                        "Bahçelerim",
                        19,
                        COLOR_DARK,
                        true
                );

        title.setPadding(
                dp(2),
                dp(4),
                0,
                dp(10)
        );


        dynamicTabContainer.addView(
                title
        );


        int gardenCount =
                0;

        double harvest =
                0;


        try {
            gardenCount =
                    databaseHelper.getGardenCount();
        } catch (Exception ignored) {
        }


        try {
            harvest =
                    databaseHelper.getTotalHarvestKg();
        } catch (Exception ignored) {
        }


        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
        );

        card.setBackground(
                rounded(
                        COLOR_CARD,
                        20,
                        COLOR_BORDER,
                        1
                )
        );


        TextView icon =
                createText(
                        "🌳",
                        32,
                        COLOR_DARK,
                        false
                );


        TextView count =
                createText(
                        gardenCount + " kayıtlı bahçe",
                        16,
                        COLOR_DARK,
                        true
                );

        count.setPadding(
                0,
                dp(7),
                0,
                0
        );


        TextView info =
                createText(
                        "Toplam kayıtlı hasat: "
                                + formatKg(harvest),
                        12,
                        COLOR_MUTED,
                        false
                );

        info.setPadding(
                0,
                dp(4),
                0,
                dp(12)
        );


        TextView goHome =
                createPillButton(
                        "Bahçelerimi görüntüle",
                        COLOR_GREEN,
                        COLOR_WHITE
                );


        goHome.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    MainActivity.class
                            );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );

                    startActivity(intent);
                    finish();
                }
        );


        card.addView(icon);
        card.addView(count);
        card.addView(info);

        card.addView(
                goHome,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );


        dynamicTabContainer.addView(
                card
        );
    }


    // ============================================================
    // ABOUT TAB
    // ============================================================

    private void loadAboutTab() {

        dynamicTabContainer.removeAllViews();


        TextView title =
                createText(
                        "Hakkında",
                        19,
                        COLOR_DARK,
                        true
                );

        title.setPadding(
                dp(2),
                dp(4),
                0,
                dp(10)
        );


        dynamicTabContainer.addView(
                title
        );


        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(16)
        );

        card.setBackground(
                rounded(
                        COLOR_CARD,
                        20,
                        COLOR_BORDER,
                        1
                )
        );


        addAboutRow(
                card,
                "👤",
                "Ad Soyad",
                getCurrentDisplayName()
        );


        addAboutRow(
                card,
                "📍",
                "Konum",
                getLocationText()
        );


        addAboutRow(
                card,
                "✉",
                "E-posta",
                sessionManager.isGuest()
                        ? "Misafir oturumu"
                        : safeOr(
                        getCurrentEmail(),
                        "Belirtilmedi"
                )
        );


        dynamicTabContainer.addView(
                card
        );
    }


    private void addAboutRow(
            LinearLayout parent,
            String icon,
            String title,
            String value
    ) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.setPadding(
                0,
                dp(8),
                0,
                dp(8)
        );


        TextView iconView =
                createText(
                        icon,
                        20,
                        COLOR_DARK,
                        false
                );

        iconView.setGravity(
                Gravity.CENTER
        );


        LinearLayout textArea =
                new LinearLayout(this);

        textArea.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView titleView =
                createText(
                        title,
                        11,
                        COLOR_MUTED,
                        true
                );


        TextView valueView =
                createText(
                        safeOr(value, "Belirtilmedi"),
                        13,
                        COLOR_TEXT,
                        false
                );


        textArea.addView(titleView);
        textArea.addView(valueView);


        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.leftMargin =
                dp(10);


        row.addView(
                iconView,
                new LinearLayout.LayoutParams(
                        dp(36),
                        dp(36)
                )
        );

        row.addView(
                textArea,
                textParams
        );


        parent.addView(
                row
        );
    }


    // ============================================================
    // PROFILE PHOTO
    // ============================================================

    private static class ProfilePhotoTarget {

        final ImageView imageView;
        final TextView fallbackView;

        ProfilePhotoTarget(
                ImageView imageView,
                TextView fallbackView
        ) {

            this.imageView =
                    imageView;

            this.fallbackView =
                    fallbackView;
        }
    }


    private FrameLayout createProfilePhotoView(
            int size,
            int fallbackTextSize
    ) {

        FrameLayout frame =
                new FrameLayout(this);


        ImageView image =
                new ImageView(this);

        image.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        image.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        999,
                        COLOR_BORDER,
                        1
                )
        );

        image.setClipToOutline(
                true
        );


        TextView fallback =
                createText(
                        sessionManager.isGuest()
                                ? "🫒"
                                : getAvatarLetter(),
                        fallbackTextSize,
                        COLOR_DARK,
                        true
                );

        fallback.setGravity(
                Gravity.CENTER
        );

        fallback.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        999,
                        COLOR_BORDER,
                        1
                )
        );


        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        size,
                        size,
                        Gravity.CENTER
                );


        frame.addView(
                image,
                params
        );

        frame.addView(
                fallback,
                params
        );


        applyProfilePhoto(
                image,
                fallback
        );


        return frame;
    }


    private void showProfilePhotoMenu() {

        if (sessionManager.isGuest()) {

            new AlertDialog.Builder(this)
                    .setTitle("Profil fotoğrafı")
                    .setMessage(
                            "Profil fotoğrafını değiştirmek için önce hesabınla giriş yap."
                    )
                    .setNegativeButton(
                            "Vazgeç",
                            null
                    )
                    .setPositiveButton(
                            "Giriş yap",
                            (dialog, which) -> goToLogin()
                    )
                    .show();

            return;
        }


        boolean googlePhotoAvailable =
                getGoogleProfilePhotoUri() != null;


        ArrayList<String> options =
                new ArrayList<>();


        options.add(
                "🖼 Galeriden fotoğraf seç"
        );


        if (googlePhotoAvailable) {

            options.add(
                    "🔄 Google profil fotoğrafını kullan"
            );
        }


        options.add(
                "👤 Varsayılan simgeyi kullan"
        );


        String[] items =
                options.toArray(
                        new String[0]
                );


        new AlertDialog.Builder(this)
                .setTitle("Profil fotoğrafı")
                .setItems(
                        items,
                        (dialog, which) -> {

                            String selected =
                                    items[which];


                            if (selected.startsWith("🖼")) {

                                openProfilePhotoPicker();
                                return;
                            }


                            if (selected.startsWith("🔄")) {

                                setProfilePhotoMode(
                                        PHOTO_MODE_GOOGLE
                                );

                                clearLoadedPhotoTargets();

                                refreshProfilePhotoViews();

                                Toast.makeText(
                                        this,
                                        "Google profil fotoğrafın kullanılacak.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }


                            if (selected.startsWith("👤")) {

                                setProfilePhotoMode(
                                        PHOTO_MODE_DEFAULT
                                );

                                clearLoadedPhotoTargets();

                                refreshProfilePhotoViews();

                                Toast.makeText(
                                        this,
                                        "Varsayılan profil simgesi kullanılıyor.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .setNegativeButton(
                        "Kapat",
                        null
                )
                .show();
    }


    private void openProfilePhotoPicker() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "image/*"
        );


        try {

            startActivityForResult(
                    intent,
                    REQUEST_PICK_PROFILE_PHOTO
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Galeri açılamadı.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (requestCode != REQUEST_PICK_PROFILE_PHOTO
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {

            return;
        }


        Uri uri =
                data.getData();


        try {

            final int takeFlags =
                    data.getFlags()
                            &
                            (
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            |
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );


            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            takeFlags
                    );


        } catch (Exception ignored) {
        }


        saveCustomProfilePhotoUri(
                uri.toString()
        );

        setProfilePhotoMode(
                PHOTO_MODE_CUSTOM
        );

        cachedGoogleProfileBitmap =
                null;

        clearLoadedPhotoTargets();
        refreshProfilePhotoViews();


        Toast.makeText(
                this,
                "Profil fotoğrafın güncellendi ✅",
                Toast.LENGTH_SHORT
        ).show();
    }


    private void refreshProfilePhotoViews() {

        if (ivProfilePhoto != null
                && tvProfilePhotoFallback != null) {

            tvProfilePhotoFallback.setText(
                    sessionManager.isGuest()
                            ? "🫒"
                            : getAvatarLetter()
            );


            applyProfilePhoto(
                    ivProfilePhoto,
                    tvProfilePhotoFallback
            );
        }


        // Gönderi kartlarındaki küçük avatarları da yenilemek için.
        if ("POSTS".equals(activeTab)
                && dynamicTabContainer != null) {

            loadPostsTab();
        }
    }


    private void applyProfilePhoto(
            ImageView imageView,
            TextView fallbackView
    ) {

        if (imageView == null
                || fallbackView == null) {

            return;
        }


        fallbackView.setVisibility(
                View.VISIBLE
        );

        imageView.setVisibility(
                View.INVISIBLE
        );


        String mode =
                getProfilePhotoMode();


        if (PHOTO_MODE_DEFAULT.equals(mode)) {

            return;
        }


        if (PHOTO_MODE_CUSTOM.equals(mode)
                || PHOTO_MODE_AUTO.equals(mode)) {

            String customUri =
                    getCustomProfilePhotoUri();


            if (!customUri.isEmpty()) {

                try {

                    imageView.setImageURI(
                            Uri.parse(customUri)
                    );

                    imageView.setVisibility(
                            View.VISIBLE
                    );

                    fallbackView.setVisibility(
                            View.GONE
                    );

                    return;

                } catch (Exception ignored) {
                }
            }


            if (PHOTO_MODE_CUSTOM.equals(mode)) {

                setProfilePhotoMode(
                        PHOTO_MODE_AUTO
                );
            }
        }


        Uri googleUri =
                getGoogleProfilePhotoUri();


        if (googleUri == null
                || PHOTO_MODE_DEFAULT.equals(mode)) {

            return;
        }


        if (cachedGoogleProfileBitmap != null) {

            imageView.setImageBitmap(
                    cachedGoogleProfileBitmap
            );

            imageView.setVisibility(
                    View.VISIBLE
            );

            fallbackView.setVisibility(
                    View.GONE
            );

            return;
        }


        profilePhotoTargets.add(
                new ProfilePhotoTarget(
                        imageView,
                        fallbackView
                )
        );


        startGoogleProfilePhotoLoad(
                googleUri.toString()
        );
    }


    private void startGoogleProfilePhotoLoad(
            String urlString
    ) {

        if (googleProfilePhotoLoading
                || urlString == null
                || urlString.trim().isEmpty()) {

            return;
        }


        googleProfilePhotoLoading =
                true;


        new Thread(
                () -> {

                    Bitmap bitmap =
                            null;

                    HttpURLConnection connection =
                            null;

                    InputStream inputStream =
                            null;


                    try {

                        URL url =
                                new URL(
                                        urlString
                                );


                        connection =
                                (HttpURLConnection)
                                        url.openConnection();

                        connection.setConnectTimeout(
                                8000
                        );

                        connection.setReadTimeout(
                                8000
                        );

                        connection.setDoInput(
                                true
                        );

                        connection.connect();


                        inputStream =
                                connection.getInputStream();


                        bitmap =
                                BitmapFactory.decodeStream(
                                        inputStream
                                );


                    } catch (Exception ignored) {


                    } finally {

                        try {

                            if (inputStream != null) {

                                inputStream.close();
                            }

                        } catch (Exception ignored) {
                        }


                        if (connection != null) {

                            connection.disconnect();
                        }
                    }


                    Bitmap finalBitmap =
                            bitmap;


                    runOnUiThread(
                            () -> {

                                googleProfilePhotoLoading =
                                        false;


                                if (finalBitmap != null) {

                                    cachedGoogleProfileBitmap =
                                            finalBitmap;


                                    ArrayList<ProfilePhotoTarget> targets =
                                            new ArrayList<>(
                                                    profilePhotoTargets
                                            );


                                    profilePhotoTargets.clear();


                                    for (ProfilePhotoTarget target : targets) {

                                        if (target == null
                                                || target.imageView == null
                                                || target.fallbackView == null) {

                                            continue;
                                        }


                                        target.imageView.setImageBitmap(
                                                finalBitmap
                                        );

                                        target.imageView.setVisibility(
                                                View.VISIBLE
                                        );

                                        target.fallbackView.setVisibility(
                                                View.GONE
                                        );
                                    }

                                } else {

                                    profilePhotoTargets.clear();
                                }
                            }
                    );
                }
        ).start();
    }


    private Uri getGoogleProfilePhotoUri() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null
                        && user.getPhotoUrl() != null) {

                    return user.getPhotoUrl();
                }
            }

        } catch (Exception ignored) {
        }


        return null;
    }


    private String getProfilePhotoMode() {

        if (profilePreferences == null) {

            return PHOTO_MODE_AUTO;
        }


        return profilePreferences.getString(
                KEY_PHOTO_MODE_PREFIX
                        + getProfilePreferenceUserKey(),
                PHOTO_MODE_AUTO
        );
    }


    private void setProfilePhotoMode(
            String mode
    ) {

        if (profilePreferences == null) {
            return;
        }


        profilePreferences
                .edit()
                .putString(
                        KEY_PHOTO_MODE_PREFIX
                                + getProfilePreferenceUserKey(),
                        mode
                )
                .apply();
    }


    private String getCustomProfilePhotoUri() {

        if (profilePreferences == null) {

            return "";
        }


        return safe(
                profilePreferences.getString(
                        KEY_CUSTOM_PHOTO_URI_PREFIX
                                + getProfilePreferenceUserKey(),
                        ""
                )
        );
    }


    private void saveCustomProfilePhotoUri(
            String uri
    ) {

        if (profilePreferences == null) {
            return;
        }


        profilePreferences
                .edit()
                .putString(
                        KEY_CUSTOM_PHOTO_URI_PREFIX
                                + getProfilePreferenceUserKey(),
                        safe(uri)
                )
                .apply();
    }


    private String getProfilePreferenceUserKey() {

        String uid =
                getRawFirebaseUid();


        if (!uid.isEmpty()) {

            return "firebase_"
                    + uid;
        }


        String email =
                safe(
                        sessionManager.getUserEmail()
                );


        if (!email.isEmpty()) {

            return "email_"
                    +
                    Math.abs(
                            email
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                                    .hashCode()
                    );
        }


        return "guest";
    }


    private void clearLoadedPhotoTargets() {

        profilePhotoTargets.clear();
    }


    // ============================================================
    // PROFILE EDIT
    // ============================================================

    private void showEditProfileDialog() {

        if (sessionManager.isGuest()) {

            new AlertDialog.Builder(this)
                    .setTitle("Profil düzenleme")
                    .setMessage(
                            "Profil bilgilerini düzenlemek için önce bir hesapla giriş yap."
                    )
                    .setNegativeButton(
                            "Vazgeç",
                            null
                    )
                    .setPositiveButton(
                            "Giriş yap",
                            (dialog, which) -> goToLogin()
                    )
                    .show();

            return;
        }


        EditText input =
                new EditText(this);

        input.setSingleLine(true);

        input.setText(
                getCurrentDisplayName()
        );

        input.setSelection(
                input.length()
        );

        input.setHint(
                "Ad Soyad"
        );

        input.setTextSize(
                15
        );

        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        input.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        input.setBackground(
                rounded(
                        GreenTheme.color(R.color.zd_surface),
                        16,
                        COLOR_BORDER,
                        1
                )
        );


        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setOrientation(
                LinearLayout.VERTICAL
        );

        wrapper.setPadding(
                dp(20),
                dp(8),
                dp(20),
                0
        );


        TextView photoButton =
                createPillButton(
                        "📷  Profil fotoğrafını değiştir",
                        COLOR_SOFT_GREEN,
                        COLOR_DARK
                );

        photoButton.setBackground(
                rounded(
                        COLOR_SOFT_GREEN,
                        14,
                        COLOR_BORDER,
                        1
                )
        );

        photoButton.setOnClickListener(
                v -> showProfilePhotoMenu()
        );


        LinearLayout.LayoutParams photoButtonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        photoButtonParams.bottomMargin =
                dp(12);


        wrapper.addView(
                photoButton,
                photoButtonParams
        );

        wrapper.addView(
                input,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54)
                )
        );


        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Profili düzenle")
                        .setView(wrapper)
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .setPositiveButton(
                                "Kaydet",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                d -> dialog
                        .getButton(
                                AlertDialog.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                v -> {

                                    String newName =
                                            input
                                                    .getText()
                                                    .toString()
                                                    .trim();


                                    if (newName.length() < 2) {

                                        input.setError(
                                                "Adını ve soyadını gir."
                                        );

                                        return;
                                    }


                                    updateProfileName(
                                            newName,
                                            dialog
                                    );
                                }
                        )
        );


        dialog.show();
    }


    private void updateProfileName(
            String newName,
            AlertDialog dialog
    ) {

        if (firebaseAuth == null) {

            Toast.makeText(
                    this,
                    "Firebase bağlantısı hazır değil.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        FirebaseUser user =
                firebaseAuth.getCurrentUser();


        if (user == null) {

            Toast.makeText(
                    this,
                    "Aktif kullanıcı bulunamadı.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        UserProfileChangeRequest request =
                new UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build();


        user.updateProfile(
                request
        )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                String message =
                                        "Profil adı güncellenemedi.";


                                if (task.getException() != null
                                        && task.getException().getMessage() != null) {

                                    message =
                                            task
                                                    .getException()
                                                    .getMessage();
                                }


                                Toast.makeText(
                                        ProfileActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }


                            String email =
                                    user.getEmail();


                            if (email == null
                                    || email.trim().isEmpty()) {

                                email =
                                        sessionManager.getUserEmail();
                            }


                            sessionManager.startUserSession(
                                    newName,
                                    email
                            );


                            dialog.dismiss();

                            refreshProfile();


                            Toast.makeText(
                                    ProfileActivity.this,
                                    "Profil güncellendi ✅",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }


    // ============================================================
    // ACCOUNT MENU
    // ============================================================

    private void showAccountMenu() {

        String[] items = {
                "✏️ Profili düzenle",
                "📷 Profil fotoğrafı",
                "⚙ Hesap bilgileri",
                "🚪 Çıkış yap"
        };


        new AlertDialog.Builder(this)
                .setTitle("Profil seçenekleri")
                .setItems(
                        items,
                        (dialog, which) -> {

                            if (which == 0) {

                                showEditProfileDialog();
                                return;
                            }


                            if (which == 1) {

                                showProfilePhotoMenu();
                                return;
                            }


                            if (which == 2) {

                                changeTab("ABOUT");
                                return;
                            }


                            if (which == 3) {

                                confirmLogout();
                            }
                        }
                )
                .setNegativeButton(
                        "Kapat",
                        null
                )
                .show();
    }


    private void confirmLogout() {

        new AlertDialog.Builder(this)
                .setTitle("Çıkış yapılsın mı?")
                .setMessage(
                        "Zeytin Defteri hesabından çıkış yapacaksın."
                )
                .setNegativeButton(
                        "Vazgeç",
                        null
                )
                .setPositiveButton(
                        "Çıkış yap",
                        (dialog, which) -> performLogout()
                )
                .show();
    }


    private void performLogout() {

        signOutFirebase();

        sessionManager.logout();

        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }


    private void goToLogin() {

        signOutFirebase();
        sessionManager.logout();

        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }


    // ============================================================
    // TEXT BUILDERS
    // ============================================================

    private String buildProfileMeta() {

        String location =
                getLocationText();


        if (sessionManager.isGuest()) {

            return "Misafir üretici"
                    +
                    (
                            location.isEmpty()
                                    ? ""
                                    : " • " + location
                    );
        }


        String email =
                getCurrentEmail();


        if (!location.isEmpty()) {

            return location;
        }


        if (!email.isEmpty()) {

            return email;
        }


        return "Zeytin üreticisi";
    }


    private String buildBioText() {

        int gardenCount =
                0;


        try {

            gardenCount =
                    databaseHelper.getGardenCount();

        } catch (Exception ignored) {
        }


        String location =
                getLocationText();


        StringBuilder text =
                new StringBuilder();


        text.append(
                gardenCount
        );

        text.append(
                " kayıtlı bahçe"
        );


        if (!location.isEmpty()) {

            text.append(
                    " • "
            );

            text.append(
                    location
            );
        }


        return text.toString();
    }


    private String buildPostMeta(
            FeedManager.PostItem post
    ) {

        StringBuilder text =
                new StringBuilder();


        appendMeta(
                text,
                post.district
        );


        appendMeta(
                text,
                post.province
        );


        String time =
                formatPostTime(
                        post.createdAt
                );


        appendMeta(
                text,
                time
        );


        return text.toString();
    }


    private void appendMeta(
            StringBuilder builder,
            String value
    ) {

        value =
                safe(value);


        if (value.isEmpty()) {
            return;
        }


        if (builder.length() > 0) {

            builder.append(
                    " • "
            );
        }


        builder.append(
                value
        );
    }


    private String buildPostTags(
            FeedManager.PostItem post
    ) {

        ArrayList<String> tags =
                new ArrayList<>();


        if (!safe(post.gardenName).isEmpty()) {

            tags.add(
                    "🫒 " + post.gardenName.trim()
            );
        }


        if (!safe(post.variety).isEmpty()) {

            tags.add(
                    "🌿 " + post.variety.trim()
            );
        }


        if (post.realSale) {

            tags.add(
                    "₺ Gerçek satış"
            );
        }


        StringBuilder text =
                new StringBuilder();


        for (String tag : tags) {

            if (text.length() > 0) {

                text.append(
                        "   "
                );
            }

            text.append(tag);
        }


        return text.toString();
    }


    private String buildCounterText(
            FeedManager.PostItem post
    ) {

        int likes =
                Math.max(
                        0,
                        post.usefulCount
                );

        int comments =
                Math.max(
                        0,
                        post.commentCount
                );


        if (likes <= 0
                && comments <= 0) {

            return "İlk etkileşimi sen başlat";
        }


        StringBuilder text =
                new StringBuilder();


        if (likes > 0) {

            text.append(
                    "👍 "
            );

            text.append(
                    likes
            );

            text.append(
                    " faydalı"
            );
        }


        if (comments > 0) {

            if (text.length() > 0) {

                text.append(
                        "     "
                );
            }


            text.append(
                    "💬 "
            );

            text.append(
                    comments
            );

            text.append(
                    " yorum"
            );
        }


        return text.toString();
    }


    private String getLocationText() {

        if (databaseHelper == null) {
            return "";
        }


        String province =
                safe(
                        databaseHelper.getSetting(
                                "user_province",
                                ""
                        )
                );


        String district =
                safe(
                        databaseHelper.getSetting(
                                "user_district",
                                ""
                        )
                );


        StringBuilder text =
                new StringBuilder();


        if (!district.isEmpty()) {

            text.append(
                    district
            );
        }


        if (!province.isEmpty()) {

            if (text.length() > 0) {

                text.append(
                        " / "
                );
            }


            text.append(
                    province
            );
        }


        return text.toString();
    }


    // ============================================================
    // USER
    // ============================================================

    private String getRawFirebaseUid() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null
                        && user.getUid() != null) {

                    return user
                            .getUid()
                            .trim();
                }
            }


        } catch (Exception ignored) {
        }


        return "";
    }


    private String getCurrentReactionUserKey() {

        String uid =
                getRawFirebaseUid();


        if (!uid.isEmpty()) {

            return "firebase:"
                    + uid;
        }


        String email =
                safe(
                        sessionManager.getUserEmail()
                );


        if (!email.isEmpty()) {

            return "email:"
                    +
                    email.toLowerCase(
                            Locale.ROOT
                    );
        }


        String name =
                safe(
                        sessionManager.getUserName()
                );


        if (!name.isEmpty()) {

            return "local:"
                    +
                    name.toLowerCase(
                            TR
                    );
        }


        return "local_user";
    }


    private String getCurrentDisplayName() {

        if (sessionManager.isGuest()) {

            return safeOr(
                    sessionManager.getDisplayName(),
                    "Misafir Üretici"
            );
        }


        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null) {

                    String name =
                            user.getDisplayName();


                    if (name != null
                            && !name.trim().isEmpty()) {

                        return name.trim();
                    }
                }
            }


        } catch (Exception ignored) {
        }


        return safeOr(
                sessionManager.getDisplayName(),
                "Üretici"
        );
    }


    private String getCurrentEmail() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null) {

                    String email =
                            user.getEmail();


                    if (email != null
                            && !email.trim().isEmpty()) {

                        return email.trim();
                    }
                }
            }


        } catch (Exception ignored) {
        }


        return safe(
                sessionManager.getUserEmail()
        );
    }


    private String getAvatarLetter() {

        String name =
                safe(
                        getCurrentDisplayName()
                );


        if (name.isEmpty()) {

            return "🫒";
        }


        return name
                .substring(
                        0,
                        1
                )
                .toUpperCase(
                        TR
                );
    }


    private void signOutFirebase() {

        try {

            if (firebaseAuth != null) {

                PushRegistration.beforeSignOut(this);
                firebaseAuth.signOut();
            }

        } catch (Exception ignored) {
        }
    }


    // ============================================================
    // FORMAT
    // ============================================================

    private String formatPostTime(
            long createdAt
    ) {

        if (createdAt <= 0) {
            return "";
        }


        long difference =
                Math.max(
                        0,
                        System.currentTimeMillis()
                                - createdAt
                );


        long minute =
                60_000L;

        long hour =
                60L * minute;

        long day =
                24L * hour;


        if (difference < minute) {

            return "Şimdi";
        }


        if (difference < hour) {

            return (
                    difference / minute
            )
                    + " dk";
        }


        if (difference < day) {

            return (
                    difference / hour
            )
                    + " sa";
        }


        if (difference < 7L * day) {

            return (
                    difference / day
            )
                    + " gün";
        }


        long week =
                difference
                        / (7L * day);


        if (week < 5) {

            return week
                    + " hf";
        }


        long month =
                difference
                        / (30L * day);


        if (month < 12) {

            return Math.max(
                    1,
                    month
            )
                    + " ay";
        }


        return (
                difference
                        / (365L * day)
        )
                + " yıl";
    }


    private String formatCompactKg(
            double kg
    ) {

        if (kg >= 1000) {

            double ton =
                    kg / 1000.0;


            return String.format(
                    TR,
                    "%.1f t",
                    ton
            );
        }


        return String.format(
                TR,
                "%.0f kg",
                kg
        );
    }


    private String formatKg(
            double value
    ) {

        NumberFormat format =
                NumberFormat.getNumberInstance(
                        TR
                );

        format.setMaximumFractionDigits(
                1
        );

        return format.format(
                value
        )
                + " kg";
    }


    // ============================================================
    // UI HELPERS
    // ============================================================

    private TextView createText(
            String text,
            int size,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(this);

        view.setText(
                text == null
                        ? ""
                        : text
        );

        view.setTextSize(
                size
        );

        view.setTextColor(
                color
        );

        if (bold) {

            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }


        return view;
    }


    private TextView createPillButton(
            String text,
            int background,
            int textColor
    ) {

        TextView button =
                createText(
                        text,
                        12,
                        textColor,
                        true
                );

        button.setGravity(
                Gravity.CENTER
        );

        button.setClickable(
                true
        );

        button.setFocusable(
                true
        );

        button.setBackground(
                rounded(
                        background,
                        16,
                        null,
                        0
                )
        );


        return button;
    }


    private GradientDrawable rounded(
            int color,
            int radiusDp,
            Integer strokeColor,
            int strokeWidthDp
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                color
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );


        if (strokeColor != null
                && strokeWidthDp > 0) {

            drawable.setStroke(
                    dp(strokeWidthDp),
                    strokeColor
            );
        }


        return drawable;
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }


    private String safeOr(
            String value,
            String fallback
    ) {

        value =
                safe(value);


        if (value.isEmpty()) {

            return fallback == null
                    ? ""
                    : fallback;
        }


        return value;
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
}
