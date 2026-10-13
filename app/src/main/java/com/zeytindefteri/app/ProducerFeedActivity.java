package com.zeytindefteri.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/**
 * Zeytin Defteri - Tek Ekran Uygulama Kabuğu
 *
 * Alt menü mevcut bölümleri gösterir; Elek mevcut ElekActivity ile açılır.
 * Ortadaki asistanın sesli komut ve hızlı kayıt davranışları korunur.
 * Firebase/Firestore ve yerel SQLite altyapısı korunur.
 */
public class ProducerFeedActivity extends Activity {

    private static final int BG = GreenTheme.color(R.color.zd_bg);
    private static final int CARD = GreenTheme.color(R.color.zd_card);
    private static final int GREEN = GreenTheme.color(R.color.zd_green);
    private static final int GREEN_DARK = GreenTheme.color(R.color.zd_text);
    private static final int GREEN_SOFT = GreenTheme.color(R.color.zd_green_soft);
    private static final int TEXT = GreenTheme.color(R.color.zd_text);
    private static final int MUTED = GreenTheme.color(R.color.zd_muted);
    private static final int BORDER = GreenTheme.color(R.color.zd_border);
    private static final int RED = GreenTheme.color(R.color.zd_danger);

    private static final String HOME_ACTIVE_GARDEN_SETTING =
            "home_active_garden_id";

    private DatabaseHelper db;
    private GardenRepository gardenRepository;
    private FeedManager feedManager;
    private SocialCloudRepository cloudRepository;
    private MarketRepository marketRepository;
    private ReactionManager reactionManager;
    private CommentManager commentManager;
    private PrivateCloudSyncManager privateCloudSyncManager;
    private WeatherRepository weatherRepository;

    private TodayState activeTodayState;
    private HomeWeatherState homeWeather;
    private static final long WEATHER_REFRESH_MS = 10 * 60 * 1000L;
    private static final class HomeWeatherState {
        String key, coordinates, fingerprint;
        View hero;
        TodayState today;
        WeatherRepository.WeatherForecast forecast;
        WeatherRepository.WeatherSnapshot current;
        boolean forecastPending, currentPending;
        long forecastAttempt, currentAttempt;
        Runnable refresh;
        Runnable expandForecast;
        View weatherCard;
    }
    private String homeWeatherKey(HomeGardenSnapshot garden) {
        return currentUserKey()+"|"+garden.id+"|"+safe(garden.province,"")+"|"+
                safe(garden.district,"")+"|"+safe(garden.neighborhood,"")+"|"+safe(garden.name,"");
    }
    private String weatherCoordinates(long gardenId) {
        String prefix="weather_geo:"+gardenId+":";
        return db.getSetting(prefix+"lat","")+"|"+db.getSetting(prefix+"lon","");
    }
    private String weatherFingerprint(WeatherRepository.WeatherForecast forecast) {
        StringBuilder out=new StringBuilder();
        for(WeatherRepository.HourlyForecast h:forecast.hourly)
            out.append(h.timeIso).append('|').append(h.hourLabel).append('|').append(h.temperatureC).append('|')
                .append(h.apparentTemperatureC).append('|').append(h.windKmh).append('|').append(h.humidity).append('|')
                .append(h.precipitationProbability).append('|').append(h.weatherCode).append('|').append(h.isDay)
                .append('|').append(h.condition).append('|').append(h.icon).append(';');
        for(WeatherRepository.DailyForecast d:forecast.daily)
            out.append(d.dateIso).append('|').append(d.maxTemperatureC).append('|').append(d.minTemperatureC)
                .append('|').append(d.precipitationProbability).append('|').append(d.weatherCode)
                .append('|').append(d.condition).append('|').append(d.icon).append(';');
        return out.toString();
    }
    private boolean weatherStateCurrent(HomeWeatherState state) {
        return !isFinishing() && !isDestroyed() && homeWeather==state
                && state.key.equals(homeWeatherKey(state.today.garden));
    }

    private ZeytinAssistantController zeytinAssistant;
    private FirebaseAuth firebaseAuth;

    private LinearLayout contentHost;
    private LinearLayout bottomNav;
    private View persistentHeader;
    private SettingsContentHost settingsContent;

    // Zeytin Ligi ekranini alt menude gidip gelince yeniden kurma.
    // Ayni sezon/kullanici icin mevcut View korunur; boylece kartlar tekrar
    // skeleton -> veri gecisi yapip gozle gorunur sekilde "kip kip" etmez.
    private SocialBlockRepository socialBlocks;
    private ZeytinLeagueContainerView reportsSectionCache;
    private int reportsSectionCacheYear = Integer.MIN_VALUE;
    private String reportsSectionCacheUid = "";

    private LinearLayout feedContainer;
    private LinearLayout categoryContainer;
    private LinearLayout meydanPulseHost;

    private TextView marketStrip;
    private TextView seasonButton;
    private TextView chatSectionTitle;
    private EditText messageInput;

    private boolean publishingText;
    private final Map<Long, FeedReplyPreview> feedReplyPreviews = new HashMap<>();
    private final SimpleDateFormat feedThreadDate = new SimpleDateFormat("dd MMM HH:mm",new Locale("tr","TR"));
    private final SimpleDateFormat feedThreadClock = new SimpleDateFormat("HH:mm",new Locale("tr","TR"));
    private static final class FeedReplyPreview {
        String cloudId, uid, name, content, parentId, replyToName;
        long createdAt;
        int cachedCount;
    }
    private CommentCloudRepository replyOutbox;
    private String currentSection = "MEYDAN";
    private String activeType = "ALL";

    private final List<TextView> categoryViews =
            new ArrayList<>();

    private ZeytinAssistantOrbView assistantOrb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        db = new DatabaseHelper(this);
        gardenRepository = new GardenRepository(db);

        try {
            firebaseAuth = FirebaseAuth.getInstance();
        } catch (Exception ignored) {
            firebaseAuth = null;
        }

        socialBlocks = new SocialBlockRepository(this);

        privateCloudSyncManager =
                new PrivateCloudSyncManager(db);

        feedManager =
                new FeedManager(db);

        cloudRepository =
                new SocialCloudRepository(this, db);
        replyOutbox = new CommentCloudRepository(db);

        marketRepository =
                new MarketRepository(db);

        weatherRepository =
                new WeatherRepository(db);

        reactionManager =
                new ReactionManager(
                        this,
                        db,
                        this::loadFeed
                );

        commentManager =
                new CommentManager(
                        this,
                        db,
                        new CommentManager.UserProvider() {
                            @Override
                            public String getUserKey() {
                                return currentUserKey();
                            }

                            @Override
                            public String getUserName() {
                                return currentUserName();
                            }
                        },
                        this::loadFeed
                );

        applyRequestedCategory(getIntent());
        setContentView(buildAppShell());

        String requested =
                getIntent() == null
                        ? null
                        : getIntent().getStringExtra(
                        "open_section"
                );

        showSection(
                TextUtils.isEmpty(requested)
                        ? "HOME"
                        : requested
        );

        if (getIntent() != null && getIntent().getBooleanExtra("open_assistant", false)) {
            getIntent().removeExtra("open_assistant");
            if (contentHost != null) {
                contentHost.post(this::openZeytinAssistant);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        applyRequestedCategory(intent);

        String requested =
                intent == null
                        ? null
                        : intent.getStringExtra(
                        "open_section"
                );

        if (!TextUtils.isEmpty(requested)) {
            showSection(requested);
        }

        if (intent != null && intent.getBooleanExtra("open_assistant", false)) {
            intent.removeExtra("open_assistant");
            if (contentHost != null) {
                contentHost.post(this::openZeytinAssistant);
            }
        }
    }

    private void applyRequestedCategory(Intent intent) {
        String category = intent == null ? null : intent.getStringExtra("open_category");
        if (category != null && java.util.Arrays.asList("ALL","MARKET","HARVEST","PEST","IRRIGATION","SALE").contains(category)) {
            activeType = category;
            intent.removeExtra("open_category");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsContent != null && "PROFILE".equals(currentSection)) settingsContent.resume();
        if (socialBlocks != null) socialBlocks.stop();
        socialBlocks = new SocialBlockRepository(this);
        socialBlocks.start(fresh -> { if (!isFinishing() && !isDestroyed()) loadFeed(); });
        PushRegistration.requestPermission(this);

        if (cloudRepository != null) {
            cloudRepository.syncLatestPosts(
                    success ->
                            runOnUiThread(
                                    this::refreshCurrentSection
                            )
            );
        }

        if (cloudRepository != null) cloudRepository.retryPendingPosts();
        if (replyOutbox != null) replyOutbox.flushPending();
        syncPrivateCloudData();
        refreshCurrentSection();
    }

    private View buildAppShell() {
        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(BG);

        persistentHeader =
                buildPersistentHeader();

        root.addView(
                persistentHeader,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(76)
                )
        );

        contentHost =
                new LinearLayout(this);

        contentHost.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(
                contentHost,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        bottomNav =
                buildBottomNav();

        root.addView(
                bottomNav,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(88)
                )
        );

        return root;
    }

    private View buildPersistentHeader() {
        LinearLayout header = row();

        header.setPadding(
                dp(18),
                dp(8),
                dp(16),
                dp(8)
        );

        header.setBackgroundColor(
                GreenTheme.color(R.color.zd_surface)
        );

        ImageView logo =
                new ImageView(this);

        logo.setImageResource(
                R.drawable.ic_olive_logo
        );

        header.addView(
                logo,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        LinearLayout brand =
                new LinearLayout(this);

        brand.setOrientation(
                LinearLayout.VERTICAL
        );

        brand.setPadding(
                dp(10),
                0,
                0,
                0
        );

        brand.addView(
                text(
                        "Zeytin Defteri",
                        24,
                        GREEN_DARK,
                        true
                )
        );

        brand.addView(
                text(
                        "Üretenin, paylaşanın, yanındayız.",
                        12,
                        MUTED,
                        false
                )
        );

        header.addView(
                brand,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        seasonButton =
                text(
                        db.getActiveSeasonYear()
                                + " ▾",
                        14,
                        GREEN,
                        true
                );

        seasonButton.setGravity(
                Gravity.CENTER
        );

        seasonButton.setBackground(
                round(
                        CARD,
                        22,
                        BORDER
                )
        );

        seasonButton.setContentDescription(
                "Aktif sezonu değiştir"
        );

        seasonButton.setOnClickListener(
                v -> showSeasonPicker()
        );

        header.addView(
                seasonButton,
                new LinearLayout.LayoutParams(
                        dp(78),
                        dp(44)
                )
        );

        TextView profile =
                circle(
                        "♙",
                        21,
                        CARD,
                        TEXT
                );

        profile.setBackground(
                round(
                        CARD,
                        28,
                        BORDER
                )
        );

        LinearLayout.LayoutParams pp =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        pp.setMargins(
                dp(8),
                0,
                0,
                0
        );

        header.addView(
                profile,
                pp
        );

        profile.setOnClickListener(
                v -> showSection("PROFILE")
        );

        return header;
    }

    private void showSection(String section) {
        if ("PROFILE".equals(section) && "PROFILE".equals(currentSection)
                && settingsContent != null && settingsContent.getParent() == contentHost) {
            settingsContent.refreshContent();
            return;
        }
        int previousHomeY = -1;
        HomeWeatherState previousWeather = homeWeather;
        if("HOME".equals(currentSection) && "HOME".equals(section) && contentHost!=null
                && contentHost.getChildCount()>0 && contentHost.getChildAt(0) instanceof ScrollView)
            previousHomeY=contentHost.getChildAt(0).getScrollY();
        currentSection = section;

        if (contentHost == null) {
            return;
        }

        contentHost.removeAllViews();

        feedContainer = null;
        categoryContainer = null;
        meydanPulseHost = null;
        marketStrip = null;
        messageInput = null;
        chatSectionTitle = null;

        categoryViews.clear();

        switch (section) {
            case "HOME":
                contentHost.addView(
                        buildHomeSection(),
                        match()
                );
                break;

            case "GARDENS":
                contentHost.addView(
                        buildGardensSection(),
                        match()
                );
                break;

            case "MARKET":
                contentHost.addView(
                        buildMarketSection(),
                        match()
                );
                break;

            case "REPORTS":
                contentHost.addView(
                        buildReportsSection(),
                        match()
                );
                break;

            case "PROFILE":
                contentHost.addView(
                        (settingsContent = new SettingsContentHost(this, this::settingsFingerprint, this::buildProfileSection)),
                        match()
                );
                break;

            default:
                contentHost.addView(
                        buildMeydanSection(),
                        match()
                );

                currentSection = "MEYDAN";
                break;
        }

        if(previousHomeY>=0 && previousWeather==homeWeather && "HOME".equals(section)
                && contentHost.getChildCount()>0 && contentHost.getChildAt(0) instanceof ScrollView) {
            final ScrollView home=(ScrollView)contentHost.getChildAt(0);
            final int scrollY=previousHomeY;
            home.post(() -> { if(home.getParent()==contentHost)home.scrollTo(0,scrollY); });
        }
        if (persistentHeader != null) {
            persistentHeader.setVisibility(
                    ("HOME".equals(currentSection) || "REPORTS".equals(currentSection) || "PROFILE".equals(currentSection))
                            ? View.GONE
                            : View.VISIBLE
            );
        }

        refreshBottomNav();
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                -1,
                -1
        );
    }

    private void refreshCurrentSection() {
        if (contentHost == null) {
            return;
        }

        if ("MEYDAN".equals(currentSection)
                && feedContainer != null) {

            refreshMarketStrip();
            refreshMeydanPulse();
            loadFeed();

        } else {
            showSection(currentSection);
        }
    }

    // =========================================================
    // MEYDAN
    // =========================================================

    private View buildMeydanSection() {
        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(BG);

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);

        scroll.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        LinearLayout body =
                new LinearLayout(this);

        body.setOrientation(
                LinearLayout.VERTICAL
        );

        body.setPadding(
                0,
                0,
                0,
                dp(10)
        );

        meydanPulseHost = null;

        body.addView(
                buildCategoryRow(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(58)
                )
        );

        marketStrip =
                text(
                        "Piyasa • Veriler hazırlanıyor...",
                        13,
                        GREEN_DARK,
                        true
                );

        marketStrip.setGravity(
                Gravity.CENTER_VERTICAL
        );

        marketStrip.setPadding(
                dp(18),
                0,
                dp(18),
                0
        );

        marketStrip.setBackground(
                round(
                        GREEN_SOFT,
                        20,
                        GreenTheme.color(R.color.zd_border)
                )
        );

        LinearLayout.LayoutParams mp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        mp.setMargins(
                dp(16),
                0,
                dp(16),
                dp(10)
        );

        body.addView(
                marketStrip,
                mp
        );

        marketStrip.setOnClickListener(
                v -> showSection("MARKET")
        );

        body.addView(
                buildChatSectionHeader()
        );

        feedContainer =
                new LinearLayout(this);

        feedContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        feedContainer.setPadding(
                dp(16),
                dp(4),
                dp(16),
                dp(16)
        );

        body.addView(feedContainer);

        scroll.addView(body);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        root.addView(
                buildComposer(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );

        refreshMarketStrip();
        loadFeed();

        return root;
    }



    private void refreshMeydanPulse() {
        if (meydanPulseHost == null) {
            return;
        }

        meydanPulseHost.removeAllViews();

        meydanPulseHost.addView(
                buildMeydanPulse(),
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );
    }

    private View buildMeydanPulse() {
        HorizontalScrollView hsv =
                new HorizontalScrollView(this);

        hsv.setHorizontalScrollBarEnabled(false);

        hsv.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        LinearLayout cards =
                new LinearLayout(this);

        cards.setOrientation(
                LinearLayout.HORIZONTAL
        );

        cards.setPadding(
                dp(16),
                dp(12),
                dp(8),
                dp(8)
        );

        MeydanStats stats =
                calculateMeydanStats();

        View summary =
                buildMeydanSummaryCard(stats);

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(
                        dp(250),
                        dp(132)
                );

        sp.setMargins(
                0,
                0,
                dp(10),
                0
        );

        cards.addView(
                summary,
                sp
        );

        View region =
                buildMeydanRegionCard(stats);

        LinearLayout.LayoutParams rp =
                new LinearLayout.LayoutParams(
                        dp(230),
                        dp(132)
                );

        rp.setMargins(
                0,
                0,
                dp(10),
                0
        );

        cards.addView(
                region,
                rp
        );

        View waiting =
                buildMeydanWaitingCard(stats);

        LinearLayout.LayoutParams wp =
                new LinearLayout.LayoutParams(
                        dp(250),
                        dp(132)
                );

        wp.setMargins(
                0,
                0,
                dp(8),
                0
        );

        cards.addView(
                waiting,
                wp
        );

        hsv.addView(cards);

        return hsv;
    }

    private View buildMeydanSummaryCard(
            MeydanStats stats
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(10)
        );

        card.setBackground(
                round(
                        GreenTheme.parse("#153C2D"),
                        20,
                        GreenTheme.parse("#397158")
                )
        );

        LinearLayout titleLine = row();

        TextView icon =
                text(
                        "▥",
                        16,
                        GREEN,
                        true
                );

        titleLine.addView(icon);

        TextView title =
                text(
                        "  Bugünün Özeti",
                        15,
                        TEXT,
                        true
                );

        titleLine.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView arrow =
                text(
                        "›",
                        22,
                        GREEN,
                        true
                );

        titleLine.addView(arrow);

        card.addView(titleLine);

        TextView main =
                text(
                        stats.topTopicCount > 0
                                ? stats.topTopic
                                + " bugün öne çıkıyor."
                                : "Bugün henüz belirgin bir konu yok.",
                        13,
                        GreenTheme.color(R.color.zd_text_soft),
                        true
                );

        main.setPadding(
                0,
                dp(7),
                0,
                0
        );

        main.setMaxLines(2);

        card.addView(main);

        TextView detail =
                text(
                        stats.todayCount
                                + " yeni mesaj"
                                + "  •  "
                                + stats.todayReplies
                                + " yanıt",
                        11,
                        MUTED,
                        false
                );

        detail.setPadding(
                0,
                dp(7),
                0,
                0
        );

        card.addView(detail);

        card.setOnClickListener(v -> {
            activeType = "ALL";
            styleCategories();
            updateChatSectionTitle();
            loadFeed();
        });

        return card;
    }

    private View buildMeydanRegionCard(
            MeydanStats stats
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(10)
        );

        card.setBackground(
                round(
                        CARD,
                        20,
                        BORDER
                )
        );

        LinearLayout titleLine = row();

        TextView title =
                text(
                        "📍  Bölgemde",
                        15,
                        TEXT,
                        true
                );

        titleLine.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        card.addView(titleLine);

        String userDistrict =
                safe(
                        db.getSetting(
                                "user_district",
                                ""
                        ),
                        "Bölgem"
                );

        TextView region =
                text(
                        userDistrict,
                        14,
                        GREEN,
                        true
                );

        region.setPadding(
                0,
                dp(8),
                0,
                0
        );

        card.addView(region);

        TextView newMessages =
                text(
                        stats.localTodayCount > 0
                                ? stats.localTodayCount
                                + " yeni mesaj"
                                : "Bugün yeni mesaj yok",
                        12,
                        GreenTheme.color(R.color.zd_text_soft),
                        false
                );

        newMessages.setPadding(
                0,
                dp(4),
                0,
                0
        );

        card.addView(newMessages);

        if (!TextUtils.isEmpty(
                stats.secondaryRegion
        )) {
            TextView second =
                    text(
                            stats.secondaryRegion
                                    + "  •  "
                                    + stats.secondaryRegionCount
                                    + " mesaj",
                            11,
                            MUTED,
                            false
                    );

            second.setPadding(
                    0,
                    dp(6),
                    0,
                    0
            );

            card.addView(second);
        }

        return card;
    }

    private View buildMeydanWaitingCard(
            MeydanStats stats
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(10)
        );

        card.setBackground(
                round(
                        GreenTheme.color(R.color.zd_surface),
                        20,
                        BORDER
                )
        );

        LinearLayout titleLine = row();

        TextView title =
                text(
                        "❓  Cevap Bekleyenler",
                        15,
                        TEXT,
                        true
                );

        titleLine.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        card.addView(titleLine);

        if (stats.waitingQuestion == null) {

            TextView empty =
                    text(
                            "Şimdilik cevapsız soru görünmüyor.",
                            12,
                            MUTED,
                            false
                    );

            empty.setPadding(
                    0,
                    dp(12),
                    0,
                    0
            );

            card.addView(empty);

        } else {

            TextView question =
                    text(
                            safe(
                                    stats.waitingQuestion.content,
                                    "Soru"
                            ),
                            12,
                            GreenTheme.color(R.color.zd_text_soft),
                            true
                    );

            question.setMaxLines(2);

            question.setEllipsize(
                    TextUtils.TruncateAt.END
            );

            question.setPadding(
                    0,
                    dp(9),
                    0,
                    0
            );

            card.addView(question);

            TextView time =
                    text(
                            relativeTime(
                                    stats.waitingQuestion.createdAt
                            )
                                    + "  •  cevap bekliyor",
                            11,
                            MUTED,
                            false
                    );

            time.setPadding(
                    0,
                    dp(5),
                    0,
                    0
            );

            card.addView(time);

            card.setOnClickListener(v -> {
                if (commentManager != null) {
                    commentManager.showComments(
                            stats.waitingQuestion.id
                    );
                }
            });
        }

        return card;
    }

    private View buildCategoryRow() {
        HorizontalScrollView hsv =
                new HorizontalScrollView(this);

        hsv.setHorizontalScrollBarEnabled(false);

        hsv.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        categoryContainer = row();

        categoryContainer.setPadding(
                dp(16),
                dp(6),
                dp(8),
                dp(6)
        );

        addCategory(
                "👥  #Genel",
                "ALL"
        );

        addCategory(
                "▥  #Piyasa",
                "MARKET"
        );

        addCategory(
                "🧺  #Hasat",
                "HARVEST"
        );

        addCategory(
                "🌿  #Hastalık",
                "PEST"
        );

        addCategory(
                "💧  #Sulama",
                "IRRIGATION"
        );

        addCategory(
                "🤝  #Alım-Satım",
                "SALE"
        );

        hsv.addView(categoryContainer);

        return hsv;
    }

    private void addCategory(
            String label,
            String type
    ) {
        TextView chip =
                text(
                        label,
                        12,
                        GREEN_DARK,
                        true
                );

        chip.setGravity(
                Gravity.CENTER
        );

        chip.setTag(type);

        chip.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(42)
                );

        p.setMargins(
                0,
                0,
                dp(8),
                0
        );

        categoryContainer.addView(
                chip,
                p
        );

        chip.setOnClickListener(v -> {
            activeType =
                    String.valueOf(
                            v.getTag()
                    );

            styleCategories();
            updateChatSectionTitle();
            loadFeed();
        });

        categoryViews.add(chip);

        styleCategories();
    }

    private void styleCategories() {
        for (TextView v : categoryViews) {

            boolean active =
                    activeType.equals(
                            String.valueOf(
                                    v.getTag()
                            )
                    );

            v.setTextColor(
                    active
                            ? Color.WHITE
                            : GreenTheme.parse(
                            "#D9E3E0"
                    )
            );

            v.setBackground(
                    round(
                            active
                                    ? GreenTheme.parse(
                                    "#28583D"
                            )
                                    : GreenTheme.color(R.color.zd_surface),
                            22,
                            active
                                    ? GREEN
                                    : BORDER
                    )
            );
        }
    }

    private View buildChatSectionHeader() {
        LinearLayout wrap = row();

        wrap.setPadding(
                dp(18),
                dp(3),
                dp(18),
                dp(7)
        );

        chatSectionTitle =
                text(
                        chatTitleForActiveType(),
                        17,
                        TEXT,
                        true
                );

        wrap.addView(
                chatSectionTitle,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView newest =
                text(
                        "En yeni",
                        11,
                        MUTED,
                        true
                );

        newest.setGravity(
                Gravity.CENTER_VERTICAL
                        | Gravity.RIGHT
        );

        wrap.addView(newest);

        return wrap;
    }

    private void updateChatSectionTitle() {
        if (chatSectionTitle != null) {
            chatSectionTitle.setText(
                    chatTitleForActiveType()
            );
        }
    }

    private String chatTitleForActiveType() {
        switch (activeType) {
            case "MARKET":
                return "#Piyasa Sohbeti";

            case "PEST":
                return "#Hastalık Sohbeti";

            case "HARVEST":
                return "#Hasat Sohbeti";

            case "IRRIGATION":
                return "#Sulama Sohbeti";

            case "SALE":
                return "#Alım-Satım Sohbeti";

            default:
                return "#Genel Sohbet";
        }
    }

    private View buildComposer() {
        LinearLayout holder = row();

        holder.setPadding(
                dp(14),
                dp(7),
                dp(14),
                dp(7)
        );

        holder.setBackgroundColor(
                GreenTheme.color(R.color.zd_surface)
        );

        TextView topic =
                circle(
                        "☰",
                        17,
                        GreenTheme.color(R.color.zd_input),
                        GreenTheme.color(R.color.zd_text_soft)
                );

        topic.setContentDescription(
                "Mesaj konusunu seç"
        );

        holder.addView(
                topic,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        topic.setOnClickListener(
                v -> showMeydanTopicPicker()
        );

        messageInput =
                new EditText(this);
        messageInput.setBackgroundTintList(null);

        messageInput.setSingleLine(false);
        messageInput.setMaxLines(3);

        messageInput.setHint(
                "Meydan’a bir mesaj yaz..."
        );

        messageInput.setTextSize(14);
        messageInput.setTextColor(TEXT);

        messageInput.setHintTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        messageInput.setPadding(
                dp(16),
                dp(4),
                dp(12),
                dp(4)
        );

        messageInput.setBackground(
                round(
                        GreenTheme.color(R.color.zd_input),
                        24,
                        GreenTheme.color(R.color.zd_border)
                )
        );

        LinearLayout.LayoutParams ip =
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                );

        ip.setMargins(
                dp(8),
                0,
                dp(8),
                0
        );

        holder.addView(
                messageInput,
                ip
        );

        TextView send =
                circle(
                        "➤",
                        20,
                        GREEN,
                        BG
                );

        send.setContentDescription(
                "Mesajı gönder"
        );

        holder.addView(
                send,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        send.setOnClickListener(v -> {
            String draft =
                    messageInput == null
                            ? ""
                            : messageInput
                            .getText()
                            .toString()
                            .trim();

            if (draft.isEmpty()) {
                Toast.makeText(
                        this,
                        "Önce bir mesaj yaz.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            sendMeydanText(send);
        });

        return holder;
    }

    private void sendMeydanText(TextView send) {
        if (publishingText || messageInput == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.length() < 3 || text.length() > 2000) {
            messageInput.setError("Mesaj 3–2000 karakter olmalı."); return;
        }
        if (firebaseAuth == null || firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Paylaşmak için giriş yapmalısın.", Toast.LENGTH_SHORT).show(); return;
        }
        publishingText = true; send.setEnabled(false);
        long id;
        try { id = cloudRepository.createTextPost(text, activeType); }
        catch (RuntimeException error) { id = -1; }
        if (id > 0) {
            messageInput.setText(""); loadFeed();
            Toast.makeText(this, "Mesaj kaydedildi. Buluta gönderim bekleniyor.", Toast.LENGTH_SHORT).show();
        } else messageInput.setError("Mesaj kaydedilemedi.");
        send.postDelayed(() -> { publishingText = false; send.setEnabled(true); }, 600);
    }

    private void showMeydanTopicPicker() {
        final String[] labels = {
                "Genel",
                "Piyasa",
                "Hasat",
                "Hastalık",
                "Sulama",
                "Alım-Satım"
        };

        final String[] values = {
                "ALL",
                "MARKET",
                "HARVEST",
                "PEST",
                "IRRIGATION",
                "SALE"
        };

        int selected = 0;

        for (int i = 0;
             i < values.length;
             i++) {

            if (values[i].equals(
                    activeType
            )) {
                selected = i;
                break;
            }
        }

        final int checked =
                selected;

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Mesaj konusu")
                        .setSingleChoiceItems(
                                labels,
                                checked,
                                (d, which) -> {
                                    activeType =
                                            values[which];

                                    styleCategories();
                                    updateChatSectionTitle();

                                    d.dismiss();

                                    loadFeed();
                                }
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .create();

        showDarkDialog(dialog);
    }

    private void refreshMarketStrip() {
        if (marketStrip == null
                || marketRepository == null) {
            return;
        }

        String district =
                db.getSetting(
                        "user_district",
                        "Bölgem"
                );

        double price =
                marketRepository
                        .getMarketAveragePrice("1");

        NumberFormat nf =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "tr",
                                "TR"
                        )
                );

        nf.setMaximumFractionDigits(0);

        if (price > 0) {

            marketStrip.setText(
                    "▥  Piyasa  •  "
                            + safe(
                            district,
                            "Bölgem"
                    )
                            + "     1 No   "
                            + nf.format(price)
                            + " TL/kg     ›"
            );

        } else {

            marketStrip.setText(
                    "▥  Piyasa  •  "
                            + safe(
                            district,
                            "Bölgem"
                    )
                            + "     Bölgesel fiyatları gör  ›"
            );
        }
    }

    private void loadFeed() {
        if (feedContainer == null
                || feedManager == null) {
            return;
        }

        feedContainer.removeAllViews();
        feedReplyPreviews.clear();
        if (firebaseAuth == null || firebaseAuth.getCurrentUser() == null) {
            feedContainer.addView(emptyCard("Meydan'a katıl", "Mesajları görmek ve yanıtlamak için giriş yapmalısın."));
            return;
        }


        if (socialBlocks == null || !socialBlocks.isCurrent()) return;

        List<FeedManager.PostItem> source =
                feedManager.getLatestVisiblePosts(100, socialBlocks.blockedIds());

        List<FeedManager.PostItem> filtered =
                new ArrayList<>();

        if (source != null) {
            for (FeedManager.PostItem p : source) {

                if (p != null && !socialBlocks.blocked(p.userId)
                        && matchesType(
                        p,
                        activeType
                )) {
                    filtered.add(p);
                }
            }
        }

        if (filtered.isEmpty()) {
            LinearLayout empty =
                    verticalCard();

            empty.setGravity(
                    Gravity.CENTER
            );

            empty.setPadding(
                    dp(18),
                    dp(28),
                    dp(18),
                    dp(28)
            );

            TextView title =
                    text(
                            "Burada henüz konuşma yok",
                            17,
                            TEXT,
                            true
                    );

            title.setGravity(
                    Gravity.CENTER
            );

            empty.addView(title);

            TextView sub =
                    text(
                            "İlk mesajı sen yaz, Meydan hareketlensin.",
                            13,
                            MUTED,
                            false
                    );

            sub.setGravity(
                    Gravity.CENTER
            );

            sub.setPadding(
                    0,
                    dp(6),
                    0,
                    dp(12)
            );

            empty.addView(sub);

            TextView add =
                    text(
                            "Mesaj Yaz",
                            13,
                            Color.WHITE,
                            true
                    );

            add.setGravity(
                    Gravity.CENTER
            );

            add.setBackground(
                    round(
                            GREEN,
                            20,
                            Color.TRANSPARENT
                    )
            );

            add.setOnClickListener(v -> {
                if (messageInput != null) {
                    messageInput.requestFocus();
                }
            });

            empty.addView(
                    add,
                    new LinearLayout.LayoutParams(
                            dp(140),
                            dp(40)
                    )
            );

            feedContainer.addView(empty);

            return;
        }

        int count =
                Math.min(
                        filtered.size(),
                        50
                );

        loadFeedReplyPreviews(filtered.subList(0,count));
        for (int i = 0;
             i < count;
             i++) {

            feedContainer.addView(
                    buildPostCard(
                            filtered.get(i)
                    )
            );
        }
    }

    private boolean matchesType(
            FeedManager.PostItem p,
            String type
    ) {
        if ("ALL".equals(type)) {
            return true;
        }

        String pt =
                p.postType == null
                        ? "GENERAL"
                        : p.postType.toUpperCase(
                        Locale.ROOT
                );

        if ("MARKET".equals(type)) {
            return "MARKET".equals(pt)
                    || (
                    p.realSale
                            && !"SALE".equals(pt)
            );
        }

        if ("PEST".equals(type)) {
            return "PEST".equals(pt)
                    || "SPRAY".equals(pt);
        }

        if ("SALE".equals(type)) {
            return "SALE".equals(pt)
                    || p.realSale;
        }

        return type.equals(pt);
    }

    /** One local aggregate for the visible posts; no per-post Firestore reads/listeners. */
    private void loadFeedReplyPreviews(List<FeedManager.PostItem> posts) {
        if(posts.isEmpty())return;
        StringBuilder marks=new StringBuilder(); String[] ids=new String[posts.size()];
        for(int i=0;i<posts.size();i++) { if(i>0)marks.append(',');marks.append('?');ids[i]=String.valueOf(posts.get(i).id); }
        String sql="SELECT * FROM post_comments WHERE is_active = 1 AND post_id IN ("+marks+") ORDER BY created_at DESC, cloud_id DESC";
        try(Cursor c=db.getReadableDatabase().rawQuery(sql,ids)) {
            while(c.moveToNext()) {
                String uid=CommentCloudRepository.string(c,"user_key");
                if(socialBlocks.blocked(uid))continue;
                long id=CommentCloudRepository.number(c,"post_id");
                FeedReplyPreview previous=feedReplyPreviews.get(id);
                if(previous!=null){previous.cachedCount++;continue;}
                FeedReplyPreview r=new FeedReplyPreview();
                r.cloudId=CommentCloudRepository.string(c,"cloud_id");
                r.uid=uid;r.name=CommentCloudRepository.string(c,"user_name");
                r.content=CommentCloudRepository.string(c,"content");r.parentId=CommentCloudRepository.string(c,"parent_comment_id");
                r.replyToName=socialBlocks.blocked(CommentCloudRepository.string(c,"reply_to_uid"))?"Önceki mesaj":CommentCloudRepository.string(c,"reply_to_name");
                r.createdAt=CommentCloudRepository.number(c,"created_at");r.cachedCount=1;
                feedReplyPreviews.put(id,r);
            }
        }
    }


    private View buildPostCard(final FeedManager.PostItem p) {
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.bottomMargin=dp(20);group.setLayoutParams(gp);
        FeedReplyPreview preview=feedReplyPreviews.get(p.id);
        int count=socialBlocks.hasBlocks()?(preview==null?0:preview.cachedCount):Math.max(Math.max(0,p.commentCount),preview==null?0:preview.cachedCount);
        group.addView(buildFeedThreadRow(p,null,count));
        if(preview!=null) {
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.leftMargin=dp(24);pp.topMargin=dp(8);
            group.addView(buildFeedThreadRow(p,preview,count),pp);
            TextView all=text("Tüm yanıtları gör ("+count+")",12,GREEN,true);
            all.setPadding(dp(48),dp(6),0,dp(6));all.setMinHeight(dp(40));
            all.setOnClickListener(v -> openFeedThread(p.id,"",false));group.addView(all);
        }
        return group;
    }

    private View buildFeedThreadRow(FeedManager.PostItem p,FeedReplyPreview preview,int count) {
        boolean post=preview==null;
        String uid=post?safe(p.userId,""):preview.uid;
        String name=post?safe(p.userName,"Üretici"):safe(preview.name,"Üretici");
        String message=post?safe(p.content,""):preview.content;
        String target=post?"":preview.cloudId;
        long at=post?p.createdAt:preview.createdAt;
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.addView(feedThreadAvatar(uid,name),new LinearLayout.LayoutParams(dp(36),dp(36)));
        View line=new View(this);line.setBackgroundColor(GreenTheme.color(R.color.reply_border));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(1),dp(24));lp.topMargin=dp(8);rail.addView(line,lp);
        row.addView(rail,new LinearLayout.LayoutParams(dp(42),-2));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(6),0,0,0);
        row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout header=row();LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);
        TextView author=text(name+(post&&p.realSale?" ✓":""),14,TEXT,true);
        author.setSingleLine(true);author.setEllipsize(TextUtils.TruncateAt.END);identity.addView(author);
        String meta=(at>0?feedThreadDate.format(new Date(at))+" • ":"")+feedThreadCategory(p.postType);
        if(post&&!place(p).isEmpty())meta+=" • "+place(p);
        TextView metadata=text(meta,10,MUTED,false);metadata.setMaxLines(2);identity.addView(metadata);
        header.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        // Existing feed dots did not expose a post edit/delete handler. Keep that behavior.
        TextView menu=text("•••",14,MUTED,true);menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Mesaj");header.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(40)));
        if (SocialReportManager.canReport(uid)) {
            menu.setContentDescription("Şikâyet seçenekleri");
            menu.setOnClickListener(v -> {
                String postCloudId = db.getPostCloudId(p.id);
                String type = post ? "POST" : (preview.parentId.isEmpty() ? "COMMENT" : "REPLY");
                new android.app.AlertDialog.Builder(this).setItems(new String[]{"Şikâyet Et","Kullanıcıyı Şikâyet Et","Kullanıcıyı Engelle"},(dialog,index) -> {
                    if(index==2)SocialBlockUi.confirm(this,socialBlocks,uid,name,post?place(p):"",false);
                    else SocialReportManager.showReasons(this,type,post?postCloudId:preview.cloudId,postCloudId,uid,index==1);
                }).setNegativeButton("Vazgeç",null).show();
            });
        }
        content.addView(header);
        if(!post&&!preview.parentId.isEmpty()) {
            TextView context=text("↪ "+safe(preview.replyToName,"Önceki mesaj")+" adlı kullanıcıya yanıt",10,MUTED,false);
            context.setMaxLines(1);context.setEllipsize(TextUtils.TruncateAt.END);content.addView(context);
        }
        TextView bubble=text(message,15,TEXT,false);bubble.setLineSpacing(dp(2),1f);
        bubble.setPadding(dp(12),dp(10),dp(12),dp(10));bubble.setMaxLines(post?5:3);bubble.setEllipsize(TextUtils.TruncateAt.END);
        bubble.setBackground(round(GreenTheme.color(R.color.reply_surface),16,GreenTheme.color(R.color.reply_border)));
        bubble.setOnClickListener(v -> openFeedThread(p.id,"",false));content.addView(bubble,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=row();
        if(post) {
            TextView reaction=text("",12,GreenTheme.color(R.color.zd_text_soft),false);
            if(reactionManager!=null)reactionManager.setupReactionButton(reaction,p.id,currentUserKey());
            actions.addView(reaction,new LinearLayout.LayoutParams(-2,dp(40)));
        }
        TextView reply=text(post&&count>0?"YANITLA ("+count+")":"YANITLA",11,GreenTheme.color(R.color.zd_text_soft),true);
        reply.setPadding(post?dp(8):0,0,dp(4),0);reply.setGravity(Gravity.CENTER_VERTICAL);reply.setMaxLines(2);
        reply.setOnClickListener(v -> openFeedThread(p.id,target,true));
        actions.addView(reply,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView clock=text(at>0?feedThreadClock.format(new Date(at)):"",10,MUTED,false);actions.addView(clock);
        content.addView(actions);row.setOnClickListener(v -> openFeedThread(p.id,"",false));return row;
    }

    private View feedThreadAvatar(String uid,String name) {
        FrameLayout frame=new FrameLayout(this);
        frame.addView(circle(initials(name),12,GREEN_SOFT,TEXT),new FrameLayout.LayoutParams(-1,-1));
        FirebaseUser user=firebaseAuth==null?null:firebaseAuth.getCurrentUser();
        if(user!=null&&user.getUid().equals(uid)) {
            android.content.SharedPreferences prefs=getSharedPreferences("zeytin_profile_prefs",MODE_PRIVATE);
            String mode=prefs.getString("photo_mode_"+uid,"AUTO"),uri="";
            if(!"DEFAULT".equals(mode)) {
                if("AUTO".equals(mode)||"CUSTOM".equals(mode))uri=prefs.getString("custom_photo_uri_"+uid,"");
                if(TextUtils.isEmpty(uri)&&user.getPhotoUrl()!=null)uri=user.getPhotoUrl().toString();
                if(!TextUtils.isEmpty(uri)) {
                    ImageView photo=new ImageView(this);frame.addView(photo,new FrameLayout.LayoutParams(-1,-1));
                    Glide.with(this).load(uri).circleCrop().into(photo);
                }
            }
        }
        return frame;
    }
    private String feedThreadCategory(String type) {
        switch(safe(type,"GENERAL").toUpperCase(Locale.ROOT)) {
            case "MARKET":return "#PİYASA";
            case "HARVEST":return "#HASAT";
            case "PEST":case "SPRAY":return "#HASTALIK";
            case "IRRIGATION":return "#SULAMA";
            case "SALE":return "#ALIM-SATIM";
            case "QUESTION":return "#SORU";
            case "FERTILIZER":return "#GÜBRELEME";
            case "GARDEN":return "#BAHÇEM";
            default:return "#GENEL";
        }
    }
    private void openFeedThread(long postId,String parentId,boolean focus) {
        Intent intent=new Intent(this,PostRepliesActivity.class).putExtra("post_id",postId)
            .putExtra("focus_composer",focus);
        if(!TextUtils.isEmpty(parentId))intent.putExtra("reply_parent_cloud_id",parentId);
        startActivity(intent);
    }

    private MeydanStats calculateMeydanStats() {
        MeydanStats stats =
                new MeydanStats();

        if (feedManager == null) {
            return stats;
        }

        List<FeedManager.PostItem> source =
                feedManager.getLatestVisiblePosts(100, socialBlocks.blockedIds());

        if (source == null) {
            return stats;
        }

        long cutoff =
                System.currentTimeMillis()
                        - (
                        24L
                                * 60L
                                * 60L
                                * 1000L
                );

        String userDistrict =
                safe(
                        db.getSetting(
                                "user_district",
                                ""
                        ),
                        ""
                );

        int market = 0;
        int harvest = 0;
        int pest = 0;
        int irrigation = 0;
        int sale = 0;
        int general = 0;

        String secondaryRegion = "";
        int secondaryRegionCount = 0;

        for (FeedManager.PostItem p : source) {

            if (p == null) {
                continue;
            }

            boolean today =
                    p.createdAt >= cutoff;

            if (today) {
                stats.todayCount++;

                stats.todayReplies +=
                        Math.max(
                                0,
                                p.commentCount
                        );

                String pt =
                        p.postType == null
                                ? "GENERAL"
                                : p.postType
                                .toUpperCase(
                                        Locale.ROOT
                                );

                if ("MARKET".equals(pt)
                        || p.realSale) {

                    market++;

                } else if ("HARVEST".equals(pt)) {

                    harvest++;

                } else if (
                        "PEST".equals(pt)
                                || "SPRAY".equals(pt)
                ) {

                    pest++;

                } else if (
                        "IRRIGATION".equals(pt)
                ) {

                    irrigation++;

                } else if (
                        "SALE".equals(pt)
                ) {

                    sale++;

                } else {

                    general++;
                }

                String pDistrict =
                        safe(
                                p.district,
                                ""
                        );

                if (
                        !userDistrict.isEmpty()
                                && userDistrict
                                .equalsIgnoreCase(
                                        pDistrict
                                )
                ) {
                    stats.localTodayCount++;
                }

                if (
                        !pDistrict.isEmpty()
                                && !pDistrict
                                .equalsIgnoreCase(
                                        userDistrict
                                )
                ) {
                    int count =
                            countRegionToday(
                                    source,
                                    pDistrict,
                                    cutoff
                            );

                    if (count
                            > secondaryRegionCount) {

                        secondaryRegion =
                                pDistrict;

                        secondaryRegionCount =
                                count;
                    }
                }
            }

            if (
                    stats.waitingQuestion == null
                            && p.commentCount <= 0
                            && isQuestionLike(p)
            ) {
                stats.waitingQuestion = p;
            }
        }

        stats.secondaryRegion =
                secondaryRegion;

        stats.secondaryRegionCount =
                secondaryRegionCount;

        stats.topTopic = "#Genel";
        stats.topTopicCount = general;

        if (market
                > stats.topTopicCount) {

            stats.topTopic =
                    "#Piyasa";

            stats.topTopicCount =
                    market;
        }

        if (harvest
                > stats.topTopicCount) {

            stats.topTopic =
                    "#Hasat";

            stats.topTopicCount =
                    harvest;
        }

        if (pest
                > stats.topTopicCount) {

            stats.topTopic =
                    "#Hastalık";

            stats.topTopicCount =
                    pest;
        }

        if (irrigation
                > stats.topTopicCount) {

            stats.topTopic =
                    "#Sulama";

            stats.topTopicCount =
                    irrigation;
        }

        if (sale
                > stats.topTopicCount) {

            stats.topTopic =
                    "#Alım-Satım";

            stats.topTopicCount =
                    sale;
        }

        return stats;
    }

    private int countRegionToday(
            List<FeedManager.PostItem> source,
            String district,
            long cutoff
    ) {
        int count = 0;

        if (source == null
                || TextUtils.isEmpty(
                district
        )) {
            return 0;
        }

        for (FeedManager.PostItem item : source) {

            if (item == null) {
                continue;
            }

            if (item.createdAt < cutoff) {
                continue;
            }

            if (
                    district.equalsIgnoreCase(
                            safe(
                                    item.district,
                                    ""
                            )
                    )
            ) {
                count++;
            }
        }

        return count;
    }

    private boolean isQuestionLike(
            FeedManager.PostItem p
    ) {
        if (p == null) {
            return false;
        }

        String type =
                p.postType == null
                        ? ""
                        : p.postType
                        .toUpperCase(
                                Locale.ROOT
                        );

        if ("QUESTION".equals(type)) {
            return true;
        }

        String content =
                safe(
                        p.content,
                        ""
                ).toLowerCase(
                        new Locale(
                                "tr",
                                "TR"
                        )
                );

        return content.contains("?")
                || content.contains("var mı")
                || content.contains("bilen var")
                || content.contains("ne durumda")
                || content.contains("kaç para")
                || content.contains("ne oldu")
                || content.contains("ne öner")
                || content.contains("başladı mı")
                || content.contains("gören var");
    }

    private static final class MeydanStats {
        int todayCount;
        int todayReplies;
        int localTodayCount;

        String topTopic = "#Genel";
        int topTopicCount;

        String secondaryRegion = "";
        int secondaryRegionCount;

        FeedManager.PostItem waitingQuestion;
    }

    // =========================================================
    // ANA SAYFA
    // =========================================================

    private View buildHomeSection() {
        ScrollView scroll = scroll();

        LinearLayout body = pageBody();

        body.setPadding(
                0,
                0,
                0,
                dp(96)
        );

        scroll.addView(body);

        HomeGardenSnapshot garden =
                loadHomeGardenSnapshot();

        final TodayState today =
                loadTodayState(garden);

        activeTodayState = today;

        final View hero =
                buildHomeHero(
                        garden,
                        today
                );

        body.addView(hero);

        final HomeWeatherState weatherState =
                homeWeather;

        today.openWeather =
                () -> {
                    int offset = 0;

                    if (weatherState != null
                            && weatherState.weatherCard != null) {
                        if (weatherState.expandForecast != null) {
                            weatherState.expandForecast.run();
                        }

                        offset = weatherState.weatherCard.getTop();
                    }

                    scroll.smoothScrollTo(
                            0,
                            hero.getTop() + offset
                    );
                };

        LinearLayout titleRow = row();

        TextView todayTitle =
                text(
                        "Bugün yapılacaklar",
                        20,
                        TEXT,
                        true
                );

        titleRow.addView(
                todayTitle,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView all =
                text(
                        today.scope.garden > 0
                                ? "Bahçeyi Aç  ›"
                                : "Bahçe Ekle  ›",
                        13,
                        MUTED,
                        true
                );

        all.setGravity(
                Gravity.CENTER_VERTICAL
                        | Gravity.RIGHT
        );

        all.setOnClickListener(v -> {
            if (!isTodayCurrent(today)) {
                return;
            }

            Intent i =
                    new Intent(
                            this,
                            today.scope.garden > 0
                                    ? GardenDetailActivity.class
                                    : AddGardenActivity.class
                    );

            if (today.scope.garden > 0) {
                i.putExtra(
                        "garden_id",
                        today.scope.garden
                );
            }

            startActivity(i);
        });

        titleRow.addView(
                all,
                new LinearLayout.LayoutParams(
                        -2,
                        dp(40)
                )
        );

        LinearLayout.LayoutParams trp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        trp.setMargins(
                dp(16),
                dp(14),
                dp(16),
                dp(6)
        );

        body.addView(
                titleRow,
                trp
        );

        View todayCard =
                today.container;

        renderTodayRecommendations(today);

        LinearLayout.LayoutParams tcp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        tcp.setMargins(
                dp(16),
                0,
                dp(16),
                0
        );

        body.addView(
                todayCard,
                tcp
        );

        body.addView(
                buildHomeAssistantChips()
        );

        LinearLayout quick = row();

        quick.addView(
                quickButton(
                        "💧",
                        "Sulama",
                        v ->
                                openOperationForGarden(
                                        "Sulama",
                                        garden.id
                                )
                ),
                homeQuickCard()
        );

        quick.addView(
                quickButton(
                        "🌿",
                        "Gübreleme",
                        v ->
                                openOperationForGarden(
                                        "Gübreleme",
                                        garden.id
                                )
                ),
                homeQuickCardWithLeft()
        );

        quick.addView(
                quickButton(
                        "🧪",
                        "İlaçlama",
                        v ->
                                openOperationForGarden(
                                        "İlaçlama",
                                        garden.id
                                )
                ),
                homeQuickCardWithLeft()
        );

        quick.addView(
                quickButton(
                        "🧺",
                        "Hasat",
                        v ->
                                openHarvestForGarden(
                                        garden.id
                                )
                ),
                homeQuickCardWithLeft()
        );

        LinearLayout.LayoutParams qp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        qp.setMargins(
                dp(16),
                dp(8),
                dp(16),
                0
        );

        body.addView(
                quick,
                qp
        );

        LinearLayout marketTitleRow = row();

        marketTitleRow.addView(
                text(
                        "Piyasa Özeti",
                        22,
                        TEXT,
                        true
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView marketAll =
                text(
                        "Tümünü Gör  ›",
                        13,
                        MUTED,
                        true
                );

        marketAll.setGravity(
                Gravity.CENTER_VERTICAL
                        | Gravity.RIGHT
        );

        marketAll.setOnClickListener(
                v -> showSection("MARKET")
        );

        marketTitleRow.addView(
                marketAll,
                new LinearLayout.LayoutParams(
                        -2,
                        dp(40)
                )
        );

        LinearLayout.LayoutParams mtp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        mtp.setMargins(
                dp(16),
                dp(14),
                dp(16),
                dp(6)
        );

        body.addView(
                marketTitleRow,
                mtp
        );

        List<HomeMarketSnapshot> market =
                loadHomeMarketSnapshots();

        LinearLayout marketRow = row();

        if (market.isEmpty()) {

            LinearLayout empty =
                    verticalCard();

            empty.setPadding(
                    dp(16),
                    dp(15),
                    dp(16),
                    dp(15)
            );

            empty.addView(
                    text(
                            "Henüz piyasa verisi yok",
                            16,
                            TEXT,
                            true
                    )
            );

            TextView sub =
                    text(
                            "Piyasa izni verilen gerçek satışlar geldikçe burada 2 No bölgesel özet görünecek.",
                            12,
                            MUTED,
                            false
                    );

            sub.setPadding(
                    0,
                    dp(5),
                    0,
                    0
            );

            empty.addView(sub);

            empty.setOnClickListener(
                    v -> showSection("MARKET")
            );

            LinearLayout.LayoutParams ep =
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(104)
                    );

            ep.setMargins(
                    dp(16),
                    0,
                    dp(16),
                    0
            );

            body.addView(
                    empty,
                    ep
            );

        } else {

            marketRow.addView(
                    buildHomeMarketCard(
                            market.get(0)
                    ),
                    new LinearLayout.LayoutParams(
                            0,
                            dp(116),
                            1
                    )
            );

            if (market.size() > 1) {

                LinearLayout.LayoutParams second =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(116),
                                1
                        );

                second.setMargins(
                        dp(8),
                        0,
                        0,
                        0
                );

                marketRow.addView(
                        buildHomeMarketCard(
                                market.get(1)
                        ),
                        second
                );

            } else {

                LinearLayout.LayoutParams second =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(116),
                                1
                        );

                second.setMargins(
                        dp(8),
                        0,
                        0,
                        0
                );

                LinearLayout more =
                        verticalCard();

                more.setGravity(
                        Gravity.CENTER
                );

                more.addView(
                        text(
                                "Piyasayı Gör",
                                15,
                                TEXT,
                                true
                        )
                );

                more.setOnClickListener(
                        v -> showSection("MARKET")
                );

                marketRow.addView(
                        more,
                        second
                );
            }

            LinearLayout.LayoutParams mrp =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            mrp.setMargins(
                    dp(16),
                    0,
                    dp(16),
                    0
            );

            body.addView(
                    marketRow,
                    mrp
            );
        }

        double harvest =
                db.getTotalHarvestKg();

        double expenses =
                db.getTotalExpenses();

        double revenue =
                db.getTotalSalesRevenue();

        LinearLayout season =
                verticalCard();

        season.setPadding(
                dp(16),
                dp(15),
                dp(16),
                dp(15)
        );

        season.addView(
                text(
                        "Bu Sezon",
                        19,
                        TEXT,
                        true
                )
        );

        LinearLayout seasonRow = row();

        seasonRow.addView(
                homeMiniMetric(
                        "Hasat",
                        fmtKg(harvest),
                        TEXT
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        seasonRow.addView(
                homeMiniMetric(
                        "Masraf",
                        fmtTl(expenses),
                        expenses > 0
                                ? RED
                                : TEXT
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        seasonRow.addView(
                homeMiniMetric(
                        "Satış",
                        fmtTl(revenue),
                        GREEN
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        season.addView(seasonRow);

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        sp.setMargins(
                dp(16),
                dp(14),
                dp(16),
                dp(18)
        );

        body.addView(
                season,
                sp
        );

        return scroll;
    }

    // =========================================================
    // ANA SAYFA ÜST BÖLÜM (Saha Defteri)
    // Selamlama, bahçe seçici, zeytin takvimi ve hava kartı.
    // Hava önbelleği/yenileme mantığı önceki hero ile aynıdır.
    // =========================================================

    private View buildHomeHero(
            final HomeGardenSnapshot garden,
            final TodayState today
    ) {
        final String key=homeWeatherKey(garden);
        if(homeWeather!=null && key.equals(homeWeather.key) && homeWeather.hero!=null
                && (homeWeather.forecastPending || homeWeather.currentPending
                || weatherCoordinates(garden.id).equals(homeWeather.coordinates))) {
            homeWeather.today=today;
            today.forecast=homeWeather.forecast;today.current=homeWeather.current;
            View cached=homeWeather.hero;
            if(cached.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup)cached.getParent()).removeView(cached);
            if(homeWeather.refresh!=null)homeWeather.refresh.run();
            return cached;
        }
        final HomeWeatherState state=new HomeWeatherState();state.key=key;state.today=today;
        homeWeather=state;state.coordinates=weatherCoordinates(garden.id);

        final int gold=GreenTheme.color(R.color.zd_warning);

        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16),dp(18),dp(16),0);

        // 1) Selamlama + hatırlatmalar + profil
        LinearLayout top=row();
        top.setGravity(Gravity.TOP);
        LinearLayout greetingBox=new LinearLayout(this);
        greetingBox.setOrientation(LinearLayout.VERTICAL);
        greetingBox.addView(text(HomeDashboardModel.dateLine(Calendar.getInstance()),13,MUTED,false));
        final String userName=currentUserName();
        TextView greeting=text(dayGreeting()+", "+HomeDashboardModel.displayFirstName(userName),24,TEXT,true);
        greeting.setPadding(0,dp(3),0,0);
        greeting.setMaxLines(2);
        greeting.setEllipsize(TextUtils.TruncateAt.END);
        greetingBox.addView(greeting);
        top.addView(greetingBox,new LinearLayout.LayoutParams(0,-2,1));

        ImageView bell=new ImageView(this);
        bell.setImageResource(R.drawable.ic_bell_line);
        bell.setColorFilter(TEXT);
        bell.setPadding(dp(11),dp(11),dp(11),dp(11));
        bell.setBackground(round(CARD,22,BORDER));
        bell.setContentDescription("Hatırlatmaları aç");
        bell.setClickable(true);
        bell.setFocusable(true);
        bell.setOnClickListener(v -> startActivity(new Intent(ProducerFeedActivity.this,RemindersActivity.class)));
        LinearLayout.LayoutParams bellParams=new LinearLayout.LayoutParams(dp(44),dp(44));
        bellParams.setMargins(dp(8),0,0,0);
        top.addView(bell,bellParams);

        TextView avatar=circle(HomeDashboardModel.initials(userName),14,GreenTheme.color(R.color.zd_green_chip),TEXT);
        avatar.setContentDescription("Profil");
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> showSection("PROFILE"));
        LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(dp(44),dp(44));
        avatarParams.setMargins(dp(8),0,0,0);
        top.addView(avatar,avatarParams);
        hero.addView(top);

        // 2) Bahçe seçici
        TextView place=text(
                garden.id>0
                        ? garden.name+"  ·  "+db.getActiveSeasonYear()+"   ▾"
                        : "Bahçe seç   ▾",
                14,TEXT,true);
        place.setSingleLine(true);
        place.setEllipsize(TextUtils.TruncateAt.END);
        place.setGravity(Gravity.CENTER_VERTICAL);
        place.setMinHeight(dp(44));
        place.setPadding(dp(16),0,dp(16),0);
        place.setBackground(round(CARD,22,BORDER));
        place.setClickable(true);
        place.setFocusable(true);
        place.setContentDescription(garden.id>0
                ? "Aktif bahçe: "+garden.name+". Değiştirmek için dokun."
                : "Aktif bahçe seç");
        place.setOnClickListener(v -> showHomeGardenPicker(garden.id));
        LinearLayout.LayoutParams placeParams=new LinearLayout.LayoutParams(-2,-2);
        placeParams.setMargins(0,dp(14),0,0);
        hero.addView(place,placeParams);

        // 3) Zeytin takvimi
        hero.addView(buildHomeSeasonCard(today,gold),homeCardParams(14));

        // 4) Hava kartı
        LinearLayout weatherCard=new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(16),dp(14),dp(16),dp(16));
        weatherCard.setBackground(round(CARD,20,BORDER));

        LinearLayout weatherTop=row();
        LinearLayout nowBox=new LinearLayout(this);
        nowBox.setOrientation(LinearLayout.VERTICAL);
        final TextView temp=text("☀ —°",30,TEXT,true);
        temp.setIncludeFontPadding(false);
        nowBox.addView(temp);
        final TextView weatherStatus=text(
                garden.id>0 ? "Bahçe havası yükleniyor" : "Önce bahçe kaydet",
                13,MUTED,false);
        weatherStatus.setSingleLine(true);
        weatherStatus.setEllipsize(TextUtils.TruncateAt.END);
        weatherStatus.setPadding(0,dp(4),0,0);
        nowBox.addView(weatherStatus);
        weatherTop.addView(nowBox,new LinearLayout.LayoutParams(0,-2,1));

        final TextView toggle=text("Saatlik  ›",13,GREEN,true);
        toggle.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        toggle.setMinHeight(dp(44));
        toggle.setPadding(dp(10),0,0,0);
        toggle.setContentDescription("Saatlik ve 5 günlük tahmini göster");
        toggle.setVisibility(garden.id>0 ? View.VISIBLE : View.GONE);
        weatherTop.addView(toggle,new LinearLayout.LayoutParams(-2,-2));
        weatherCard.addView(weatherTop);

        final LinearLayout conditions=new LinearLayout(this);
        conditions.setOrientation(LinearLayout.VERTICAL);
        TextView loading=text(
                garden.id>0 && weatherRepository!=null
                        ? "Bahçenin hava tahmini hazırlanıyor…"
                        : "Hava tahmini için önce bir bahçe seç",
                13,MUTED,false);
        loading.setPadding(0,dp(6),0,dp(4));
        conditions.addView(loading);
        LinearLayout.LayoutParams conditionParams=new LinearLayout.LayoutParams(-1,-2);
        conditionParams.setMargins(0,dp(12),0,0);
        weatherCard.addView(conditions,conditionParams);

        final LinearLayout rainBars=row();
        rainBars.setGravity(Gravity.BOTTOM);
        rainBars.setVisibility(View.GONE);
        LinearLayout.LayoutParams rainParams=new LinearLayout.LayoutParams(-1,-2);
        rainParams.setMargins(0,dp(14),0,0);
        weatherCard.addView(rainBars,rainParams);

        final LinearLayout forecastPanel=new LinearLayout(this);
        forecastPanel.setOrientation(LinearLayout.VERTICAL);
        forecastPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,-2);
        panelParams.setMargins(0,dp(14),0,0);
        weatherCard.addView(forecastPanel,panelParams);

        final Runnable showPanel=() -> {
            forecastPanel.setVisibility(View.VISIBLE);
            toggle.setText("Gizle  ˄");
            toggle.setContentDescription("Saatlik ve 5 günlük tahmini gizle");
        };
        toggle.setOnClickListener(v -> {
            if(forecastPanel.getVisibility()==View.VISIBLE){
                forecastPanel.setVisibility(View.GONE);
                toggle.setText("Saatlik  ›");
                toggle.setContentDescription("Saatlik ve 5 günlük tahmini göster");
            } else showPanel.run();
        });
        state.expandForecast=showPanel;
        state.weatherCard=weatherCard;
        hero.addView(weatherCard,homeCardParams(12));

        state.refresh=() -> {
            if(!weatherStateCurrent(state) || garden.id<=0 || weatherRepository==null)return;
            long now=android.os.SystemClock.elapsedRealtime();
            if(!state.forecastPending && (state.forecastAttempt==0 || now-state.forecastAttempt>=WEATHER_REFRESH_MS)) {
                state.forecastPending=true;state.forecastAttempt=now;
                weatherRepository.loadForecastForGarden(garden.id,garden.province,garden.district,garden.neighborhood,
                        new WeatherRepository.ForecastCallback() {
                            @Override public void onSuccess(WeatherRepository.WeatherForecast forecast) {
                                runOnUiThread(() -> {
                                    state.forecastPending=false;
                                    if(!weatherStateCurrent(state))return;
                                    state.forecast=forecast;state.today.forecast=forecast;
                                    state.coordinates=weatherCoordinates(garden.id);
                                    String fingerprint=weatherFingerprint(forecast);
                                    boolean changed=!fingerprint.equals(state.fingerprint);
                                    if(changed) {
                                        state.fingerprint=fingerprint;
                                        boolean daily=forecastPanel.getTag() instanceof Boolean && (Boolean)forecastPanel.getTag();
                                        renderWeatherForecastPanel(forecastPanel,forecast,daily);
                                        renderHomeRainBars(rainBars,forecast);
                                    }
                                    renderHomeConditions(conditions,state);
                                    if(changed && isTodayCurrent(state.today))renderTodayRecommendations(state.today);
                                });
                            }
                            @Override public void onError(String message) {
                                runOnUiThread(() -> {
                                    state.forecastPending=false;
                                    if(!weatherStateCurrent(state) || state.forecast!=null)return;
                                    conditions.removeAllViews();
                                    TextView error=text("☁ "+message,13,MUTED,false);
                                    error.setPadding(0,dp(6),0,dp(4));
                                    conditions.addView(error);
                                });
                            }
                        });
            }
            if(!state.currentPending && (state.currentAttempt==0 || now-state.currentAttempt>=WEATHER_REFRESH_MS)) {
                state.currentPending=true;state.currentAttempt=now;
                weatherRepository.loadForGarden(garden.id,garden.province,garden.district,garden.neighborhood,
                        new WeatherRepository.Callback() {
                            @Override public void onSuccess(WeatherRepository.WeatherSnapshot w) {
                                runOnUiThread(() -> {
                                    state.currentPending=false;
                                    if(!weatherStateCurrent(state))return;
                                    state.current=w;state.today.current=w;
                                    state.coordinates=weatherCoordinates(garden.id);
                                    temp.setText(w.icon+" "+Math.round(w.temperatureC)+"°");
                                    String detail=w.condition;
                                    if(!Double.isNaN(w.windKmh)&&w.windKmh>=1)detail+=" · rüzgâr "+Math.round(w.windKmh)+" km/s";
                                    weatherStatus.setText(detail);
                                    if(state.forecast!=null)renderHomeConditions(conditions,state);
                                    if(isTodayCurrent(state.today))renderTodayRecommendations(state.today);
                                });
                            }
                            @Override public void onError(String message) {
                                runOnUiThread(() -> {
                                    state.currentPending=false;
                                    if(!weatherStateCurrent(state) || state.current!=null)return;
                                    temp.setText("☁ —°");weatherStatus.setText(message);
                                });
                            }
                        });
            }
        };
        state.refresh.run();
        hero.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        state.hero=hero;
        return hero;
    }

    private LinearLayout.LayoutParams homeCardParams(int topMargin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(topMargin),0,0);
        return p;
    }

    private View buildHomeSeasonCard(TodayState today,int gold) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackground(round(CARD,20,BORDER));
        int phase=HomeDashboardModel.seasonPhase(Calendar.getInstance().get(Calendar.MONTH));

        LinearLayout head=row();
        head.setGravity(Gravity.BOTTOM);
        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView label=text("ZEYTİN TAKVİMİ",11,MUTED,true);
        label.setLetterSpacing(0.08f);
        titles.addView(label);
        TextView title=text(HomeDashboardModel.phaseTitle(phase),19,gold,true);
        title.setPadding(0,dp(2),0,0);
        titles.addView(title);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        long lastHarvest=-1;
        for(GardenTodayAdvisor.Record r:today.records){
            if(!"Hasat".equalsIgnoreCase(r.type))continue;
            long d=daysSince(r.date);
            if(d>=0 && (lastHarvest<0 || d<lastHarvest))lastHarvest=d;
        }
        TextView harvest=text(today.scope.garden>0 ? HomeDashboardModel.lastHarvestText(lastHarvest) : "",12,MUTED,false);
        harvest.setGravity(Gravity.RIGHT);
        head.addView(harvest,new LinearLayout.LayoutParams(-2,-2));
        card.addView(head);

        LinearLayout track=row();
        track.setGravity(Gravity.TOP);
        int line=BORDER;
        StringBuilder description=new StringBuilder("Genel zeytin takvimi. Şu an: ")
                .append(HomeDashboardModel.phaseTitle(phase)).append('.');
        for(int i=0;i<HomeDashboardModel.PHASE_NAMES.length;i++){
            LinearLayout column=new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout marker=row();
            View dot=new View(this);
            GradientDrawable dotShape=new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(i==phase ? gold : i<phase ? GREEN : line);
            int size=dp(i==phase ? 12 : 8);
            if(i==phase)dotShape.setStroke(dp(3),(gold & 0x00FFFFFF)|0x40000000);
            dot.setBackground(dotShape);
            marker.addView(dot,new LinearLayout.LayoutParams(size,size));
            View segment=new View(this);
            segment.setBackgroundColor(i<phase ? GREEN : i==HomeDashboardModel.PHASE_NAMES.length-1 ? Color.TRANSPARENT : line);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(2),1);
            sp.setMargins(dp(3),0,dp(3),0);
            marker.addView(segment,sp);
            marker.setMinimumHeight(dp(16));
            column.addView(marker,new LinearLayout.LayoutParams(-1,dp(16)));
            TextView name=text(HomeDashboardModel.PHASE_NAMES[i]+"\n"+HomeDashboardModel.PHASE_MONTHS[i],10,
                    i==phase ? TEXT : MUTED,i==phase);
            name.setLineSpacing(0,1.05f);
            name.setPadding(0,dp(4),0,0);
            column.addView(name);
            track.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }
        track.setContentDescription(description.toString());
        track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.setMargins(0,dp(14),0,0);
        card.addView(track,tp);

        TextView note=text("Genel takvim; bölgene ve yıla göre değişebilir.",11,MUTED,false);
        note.setPadding(0,dp(10),0,0);
        card.addView(note);
        return card;
    }

    private void renderHomeConditions(LinearLayout container,HomeWeatherState state) {
        if(state.forecast==null)return;
        Calendar now=Calendar.getInstance();
        List<HomeDashboardModel.Hour> hours=new ArrayList<>();
        for(WeatherRepository.HourlyForecast h:state.forecast.hourly)
            hours.add(new HomeDashboardModel.Hour(h.timeIso,h.precipitationProbability,h.windKmh,h.temperatureC));
        List<HomeDashboardModel.Day> days=new ArrayList<>();
        for(WeatherRepository.DailyForecast d:state.forecast.daily)
            days.add(new HomeDashboardModel.Day(d.dateIso,d.precipitationProbability,d.maxTemperatureC,d.minTemperatureC));
        WeatherRepository.WeatherSnapshot current=state.current!=null ? state.current : state.forecast.current;
        double wind=current==null ? Double.NaN : current.windKmh;
        List<HomeDashboardModel.Condition> items=HomeDashboardModel.conditions(wind,hours,days,now);
        container.removeAllViews();
        int gold=GreenTheme.color(R.color.zd_warning);
        int inner=GreenTheme.color(R.color.zd_surface);
        for(int i=0;i<items.size();i++){
            HomeDashboardModel.Condition c=items.get(i);
            int tone=c.tone==HomeDashboardModel.Tone.CRITICAL ? RED
                    : c.tone==HomeDashboardModel.Tone.ATTENTION ? gold
                    : c.tone==HomeDashboardModel.Tone.INFO ? GreenTheme.parse("#8FC0E0") : GREEN;
            LinearLayout item=row();
            item.setPadding(dp(12),dp(10),dp(12),dp(10));
            item.setBackground(round(inner,14,Color.TRANSPARENT));
            View dot=new View(this);
            GradientDrawable shape=new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(tone);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(10),dp(10));
            dotParams.setMargins(0,0,dp(12),0);
            item.addView(dot,dotParams);
            LinearLayout texts=new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            android.text.SpannableStringBuilder line=new android.text.SpannableStringBuilder(c.title+"  ");
            int start=line.length();
            line.append(c.value);
            line.setSpan(new android.text.style.ForegroundColorSpan(tone),start,line.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView head=text("",14,TEXT,true);
            head.setText(line);
            texts.addView(head);
            TextView note=text(c.note,12,MUTED,false);
            note.setPadding(0,dp(2),0,0);
            texts.addView(note);
            item.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
            item.setContentDescription(c.title+": "+c.value+". "+c.note);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
            if(i>0)ip.setMargins(0,dp(8),0,0);
            container.addView(item,ip);
        }
    }

    private String homeTemp(double value) {
        return Double.isNaN(value) ? "—" : Math.round(value)+"°";
    }

    private void renderHomeRainBars(LinearLayout container,WeatherRepository.WeatherForecast forecast) {
        container.removeAllViews();
        if(forecast==null || forecast.daily.isEmpty()){container.setVisibility(View.GONE);return;}
        container.setVisibility(View.VISIBLE);
        Calendar now=Calendar.getInstance();
        int inner=GreenTheme.color(R.color.zd_surface);
        int rain=GreenTheme.parse("#6FA7CF");
        StringBuilder description=new StringBuilder("Günlük yağış ihtimali: ");
        int count=Math.min(5,forecast.daily.size());
        for(int i=0;i<count;i++){
            WeatherRepository.DailyForecast d=forecast.daily.get(i);
            String day=HomeDashboardModel.shortDay(d.dateIso,now);
            boolean hasRain=!Double.isNaN(d.precipitationProbability);
            String percent=hasRain ? "%"+Math.round(d.precipitationProbability) : "—";
            description.append(day).append(' ').append(percent).append(i<count-1 ? ", " : ".");
            LinearLayout column=new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.addView(text(percent,10,MUTED,true));
            FrameLayout well=new FrameLayout(this);
            well.setBackground(round(inner,8,Color.TRANSPARENT));
            View bar=new View(this);
            bar.setBackground(round(rain,8,Color.TRANSPARENT));
            well.addView(bar,new FrameLayout.LayoutParams(-1,
                    dp(HomeDashboardModel.rainBar(d.precipitationProbability,40)),Gravity.BOTTOM));
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(40));
            wp.setMargins(0,dp(4),0,dp(4));
            column.addView(well,wp);
            column.addView(text(day,11,i==0 ? TEXT : MUTED,true));
            column.addView(text(homeTemp(d.maxTemperatureC)+"/"+homeTemp(d.minTemperatureC),10,MUTED,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
            if(i>0)cp.setMargins(dp(6),0,0,0);
            container.addView(column,cp);
        }
        container.setContentDescription(description.toString());
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for(int i=0;i<container.getChildCount();i++)
            container.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void animateHeroHeight(
            final View hero,
            int from,
            int to
    ) {
        ValueAnimator animator =
                ValueAnimator.ofInt(
                        from,
                        to
                );

        animator.setDuration(260);

        animator.addUpdateListener(
                animation -> {
                    if (hero.getLayoutParams()
                            == null) {
                        return;
                    }

                    hero.getLayoutParams().height =
                            (Integer)
                                    animation
                                            .getAnimatedValue();

                    hero.requestLayout();
                }
        );

        animator.start();
    }

    private void renderWeatherForecastPanel(
            final LinearLayout panel,
            final WeatherRepository.WeatherForecast forecast,
            final boolean dailyMode
    ) {
        panel.setTag(dailyMode);
        panel.removeAllViews();

        LinearLayout tabs = row();

        tabs.setGravity(
                Gravity.RIGHT
        );

        TextView hourlyTab =
                weatherTab(
                        "24 Saat",
                        !dailyMode
                );

        TextView dailyTab =
                weatherTab(
                        "5 Günlük",
                        dailyMode
                );

        hourlyTab.setOnClickListener(
                v ->
                        renderWeatherForecastPanel(
                                panel,
                                forecast,
                                false
                        )
        );

        dailyTab.setOnClickListener(
                v ->
                        renderWeatherForecastPanel(
                                panel,
                                forecast,
                                true
                        )
        );

        tabs.addView(
                hourlyTab,
                new LinearLayout.LayoutParams(
                        dp(70),
                        dp(24)
                )
        );

        LinearLayout.LayoutParams dtp =
                new LinearLayout.LayoutParams(
                        dp(70),
                        dp(24)
                );

        dtp.setMargins(
                dp(4),
                0,
                0,
                0
        );

        tabs.addView(
                dailyTab,
                dtp
        );

        panel.addView(tabs);

        final LinearLayout detail = row();

        detail.setPadding(
                dp(2),
                0,
                dp(2),
                0
        );

        detail.setBackground(
                round(
                        GreenTheme.parse("#800E2732"),
                        10,
                        GreenTheme.parse("#404B6268")
                )
        );

        HorizontalScrollView scroller =
                new WeatherForecastScrollView(
                        this
                );

        scroller.setHorizontalScrollBarEnabled(
                false
        );

        scroller.setFillViewport(false);

        LinearLayout strip = row();

        strip.setPadding(
                0,
                dp(4),
                0,
                0
        );

        scroller.addView(
                strip,
                new HorizontalScrollView.LayoutParams(
                        -2,
                        -1
                )
        );

        panel.addView(
                scroller,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(92)
                )
        );

        if (!dailyMode) {

            int index = 0;

            for (
                    WeatherRepository.HourlyForecast h :
                    forecast.hourly
            ) {
                final int animationIndex =
                        index++;

                TextView card =
                        weatherHourCard(
                                h,
                                animationIndex == 0
                        );

                card.setOnClickListener(v -> {
                    clearWeatherCardSelection(
                            strip
                    );

                    v.setBackground(
                            round(
                                    GreenTheme.parse("#702B503D"),
                                    10,
                                    GreenTheme.parse("#8CCF91")
                            )
                    );

                    updateHourlyWeatherDetail(
                            detail,
                            h
                    );
                });

                LinearLayout.LayoutParams cp =
                        new LinearLayout.LayoutParams(
                                dp(60),
                                dp(84)
                        );

                if (animationIndex > 0) {
                    cp.setMargins(
                            dp(4),
                            0,
                            0,
                            0
                    );
                }

                strip.addView(
                        card,
                        cp
                );

                card.setAlpha(0f);
                card.setTranslationX(0);

                card.animate()
                        .alpha(1f)
                        .translationX(0)
                        .setDuration(220)
                        .setStartDelay(
                                Math.min(
                                        280,
                                        animationIndex
                                                * 28L
                                )
                        )
                        .start();
            }

            if (!forecast.hourly.isEmpty()) {
                updateHourlyWeatherDetail(
                        detail,
                        forecast.hourly.get(0)
                );
            }

        } else {

            int index = 0;

            for (
                    WeatherRepository.DailyForecast d :
                    forecast.daily
            ) {
                final int animationIndex =
                        index++;

                TextView card =
                        weatherDayCard(
                                d,
                                animationIndex,
                                animationIndex == 0
                        );

                card.setOnClickListener(v -> {
                    clearWeatherCardSelection(
                            strip
                    );

                    v.setBackground(
                            round(
                                    GreenTheme.parse("#702B503D"),
                                    10,
                                    GreenTheme.parse("#8CCF91")
                            )
                    );

                    updateDailyWeatherDetail(
                            detail,
                            d
                    );
                });

                LinearLayout.LayoutParams cp =
                        new LinearLayout.LayoutParams(
                                dp(60),
                                dp(84)
                        );

                if (animationIndex > 0) {
                    cp.setMargins(
                            dp(4),
                            0,
                            0,
                            0
                    );
                }

                strip.addView(
                        card,
                        cp
                );

                card.setAlpha(0f);
                card.setTranslationX(0);

                card.animate()
                        .alpha(1f)
                        .translationX(0)
                        .setDuration(220)
                        .setStartDelay(
                                animationIndex
                                        * 45L
                        )
                        .start();
            }

            if (!forecast.daily.isEmpty()) {
                updateDailyWeatherDetail(
                        detail,
                        forecast.daily.get(0)
                );
            }
        }

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(38)
                );

        detailParams.setMargins(
                0,
                dp(4),
                0,
                0
        );

        panel.addView(
                detail,
                detailParams
        );
    }

    private TextView weatherTab(
            String label,
            boolean active
    ) {
        TextView tab =
                text(
                        label,
                        10,
                        active
                                ? Color.WHITE
                                : MUTED,
                        true
                );

        tab.setGravity(
                Gravity.CENTER
        );

        tab.setBackground(
                round(
                        active
                                ? GreenTheme.parse("#802B503D")
                                : GreenTheme.parse("#7015303C"),
                        12,
                        active
                                ? GREEN
                                : GreenTheme.color(R.color.zd_border)
                )
        );

        return tab;
    }

    private TextView weatherHourCard(
            WeatherRepository.HourlyForecast h,
            boolean selected
    ) {
        String rain =
                Double.isNaN(
                        h.precipitationProbability
                )
                        ? ""
                        : "\n💧%"
                        + Math.round(
                        h.precipitationProbability
                );

        String temp =
                Double.isNaN(
                        h.temperatureC
                )
                        ? "—"
                        : Math.round(
                        h.temperatureC
                )
                        + "°";

        TextView card =
                text(
                        h.hourLabel
                                + "\n"
                                + h.icon
                                + "\n"
                                + temp
                                + rain,
                        11,
                        Color.WHITE,
                        true
                );

        card.setGravity(
                Gravity.CENTER
        );

        card.setIncludeFontPadding(
                false
        );

        card.setLineSpacing(
                dp(1),
                1f
        );

        card.setPadding(
                0,
                dp(3),
                0,
                dp(3)
        );

        card.setBackground(
                round(
                        selected
                                ? GreenTheme.parse("#702B503D")
                                : GreenTheme.parse("#7815303C"),
                        10,
                        selected
                                ? GreenTheme.parse("#8CCF91")
                                : GreenTheme.parse("#5048636B")
                )
        );

        return card;
    }

    private TextView weatherDayCard(
            WeatherRepository.DailyForecast d,
            int index,
            boolean selected
    ) {
        String max =
                Double.isNaN(
                        d.maxTemperatureC
                )
                        ? "—"
                        : Math.round(
                        d.maxTemperatureC
                )
                        + "°";

        String min =
                Double.isNaN(
                        d.minTemperatureC
                )
                        ? "—"
                        : Math.round(
                        d.minTemperatureC
                )
                        + "°";

        String rain =
                Double.isNaN(
                        d.precipitationProbability
                )
                        ? ""
                        : "\n💧%"
                        + Math.round(
                        d.precipitationProbability
                );

        TextView card =
                text(
                        weatherDayLabel(
                                d.dateIso,
                                index
                        )
                                + "\n"
                                + d.icon
                                + "\n"
                                + max
                                + "/"
                                + min
                                + rain,
                        11,
                        Color.WHITE,
                        true
                );

        card.setGravity(
                Gravity.CENTER
        );

        card.setIncludeFontPadding(
                false
        );

        card.setLineSpacing(
                dp(1),
                1f
        );

        card.setPadding(
                0,
                dp(3),
                0,
                dp(3)
        );

        card.setBackground(
                round(
                        selected
                                ? GreenTheme.parse("#702B503D")
                                : GreenTheme.parse("#7815303C"),
                        10,
                        selected
                                ? GreenTheme.parse("#8CCF91")
                                : GreenTheme.parse("#5048636B")
                )
        );

        return card;
    }

    private void clearWeatherCardSelection(
            LinearLayout strip
    ) {
        for (
                int i = 0;
                i < strip.getChildCount();
                i++
        ) {
            View child =
                    strip.getChildAt(i);

            child.setBackground(
                    round(
                            GreenTheme.parse("#7815303C"),
                            10,
                            GreenTheme.parse("#5048636B")
                    )
            );
        }
    }

    private void updateHourlyWeatherDetail(
            LinearLayout detail,
            WeatherRepository.HourlyForecast h
    ) {
        detail.removeAllViews();

        detail.addView(
                weatherDetailMetric(
                        "Hissedilen",
                        formatWeatherTemp(
                                h.apparentTemperatureC
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "Rüzgâr",
                        formatWeatherWind(
                                h.windKmh
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "Nem",
                        formatWeatherPercent(
                                h.humidity
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "Yağış",
                        formatWeatherPercent(
                                h.precipitationProbability
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );
    }

    private void updateDailyWeatherDetail(
            LinearLayout detail,
            WeatherRepository.DailyForecast d
    ) {
        detail.removeAllViews();

        detail.addView(
                weatherDetailMetric(
                        "En yüksek",
                        formatWeatherTemp(
                                d.maxTemperatureC
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "En düşük",
                        formatWeatherTemp(
                                d.minTemperatureC
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "Yağış",
                        formatWeatherPercent(
                                d.precipitationProbability
                        )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        detail.addView(
                weatherDetailMetric(
                        "Durum",
                        d.condition
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );
    }

    private View weatherDetailMetric(
            String label,
            String value
    ) {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setGravity(
                Gravity.CENTER
        );

        box.setPadding(
                dp(2),
                0,
                dp(2),
                0
        );

        TextView l =
                text(
                        label,
                        9,
                        MUTED,
                        false
                );

        l.setGravity(
                Gravity.CENTER
        );

        l.setIncludeFontPadding(false);
        l.setSingleLine(true);

        l.setEllipsize(
                TextUtils.TruncateAt.END
        );

        TextView v =
                text(
                        value,
                        10,
                        Color.WHITE,
                        true
                );

        v.setGravity(
                Gravity.CENTER
        );

        v.setIncludeFontPadding(false);
        v.setSingleLine(true);

        v.setEllipsize(
                TextUtils.TruncateAt.END
        );

        box.addView(l);
        box.addView(v);

        return box;
    }

    private String formatWeatherTemp(
            double value
    ) {
        return Double.isNaN(value)
                ? "—"
                : Math.round(value)
                + "°C";
    }

    private String formatWeatherWind(
            double value
    ) {
        return Double.isNaN(value)
                ? "—"
                : Math.round(value)
                + " km/s";
    }

    private String formatWeatherPercent(
            double value
    ) {
        return Double.isNaN(value)
                ? "—"
                : "%"
                + Math.round(value);
    }

    private String weatherDayLabel(
            String iso,
            int index
    ) {
        if (index == 0) {
            return "Bugün";
        }

        if (TextUtils.isEmpty(iso)) {
            return "Gün "
                    + (index + 1);
        }

        try {
            SimpleDateFormat input =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            Date date =
                    input.parse(iso);

            if (date == null) {
                return "Gün "
                        + (index + 1);
            }

            SimpleDateFormat output =
                    new SimpleDateFormat(
                            "EEE",
                            new Locale(
                                    "tr",
                                    "TR"
                            )
                    );

            String day =
                    output.format(date);

            if (day.length() > 0) {
                day =
                        day.substring(
                                0,
                                1
                        ).toUpperCase(
                                new Locale(
                                        "tr",
                                        "TR"
                                )
                        )
                                + day.substring(1);
            }

            return day;

        } catch (Exception ignored) {
            return "Gün "
                    + (index + 1);
        }
    }

    private static final class TodayState {
        GardenTodayAdvisor.Scope scope;
        HomeGardenSnapshot garden;
        LinearLayout container;

        final List<GardenTodayAdvisor.Record> records =
                new ArrayList<>();

        boolean historyAvailable = true;

        WeatherRepository.WeatherSnapshot current;
        WeatherRepository.WeatherForecast forecast;

        Runnable openWeather;
    }

    private TodayState loadTodayState(
            HomeGardenSnapshot garden
    ) {
        TodayState state =
                new TodayState();

        state.garden =
                garden;

        state.scope =
                new GardenTodayAdvisor.Scope(
                        db.getActiveOwnerUid(),
                        db.getActiveSeasonYear(),
                        garden.id
                );

        state.container =
                new LinearLayout(this);

        state.container.setOrientation(
                LinearLayout.VERTICAL
        );

        if (garden.id <= 0) {
            return state;
        }

        boolean owned = false;

        try (
                Cursor gardens =
                        db.getGardens()
        ) {
            while (
                    gardens != null
                            && gardens.moveToNext()
            ) {
                if (
                        getLong(
                                gardens,
                                "id"
                        )
                                == garden.id
                ) {
                    owned = true;
                    break;
                }
            }

        } catch (Exception ignored) {
        }

        if (!owned) {
            state.scope =
                    new GardenTodayAdvisor.Scope(
                            state.scope.owner,
                            state.scope.season,
                            0
                    );

            return state;
        }

        try (
                Cursor operations =
                        db.getGardenOperations(
                                garden.id
                        )
        ) {
            if (operations == null) {
                state.historyAvailable =
                        false;
            }

            while (
                    operations != null
                            && operations.moveToNext()
            ) {
                if (
                        getLong(
                                operations,
                                "garden_id"
                        )
                                != garden.id
                ) {
                    continue;
                }

                state.records.add(
                        new GardenTodayAdvisor.Record(
                                state.scope,
                                getString(
                                        operations,
                                        "type",
                                        ""
                                ).trim(),
                                getString(
                                        operations,
                                        "operation_date",
                                        ""
                                )
                        )
                );
            }

        } catch (Exception e) {
            state.historyAvailable =
                    false;
        }

        try (
                Cursor harvests =
                        db.getGardenHarvests(
                                garden.id
                        )
        ) {
            if (harvests == null) {
                state.historyAvailable =
                        false;
            }

            while (
                    harvests != null
                            && harvests.moveToNext()
            ) {
                if (
                        getLong(
                                harvests,
                                "garden_id"
                        )
                                != garden.id
                ) {
                    continue;
                }

                state.records.add(
                        new GardenTodayAdvisor.Record(
                                state.scope,
                                "Hasat",
                                getString(
                                        harvests,
                                        "harvest_date",
                                        ""
                                )
                        )
                );
            }

        } catch (Exception e) {
            state.historyAvailable =
                    false;
        }

        return state;
    }

    private boolean isTodayCurrent(
            TodayState state
    ) {
        if (isFinishing()
                || activeTodayState != state) {
            return false;
        }

        long selected = 0;

        try {
            selected =
                    Long.parseLong(
                            db.getSetting(
                                    homeActiveGardenSettingKey(),
                                    "0"
                            )
                    );

        } catch (Exception ignored) {
        }

        return state.scope.matches(
                new GardenTodayAdvisor.Scope(
                        db.getActiveOwnerUid(),
                        db.getActiveSeasonYear(),
                        state.scope.garden <= 0
                                ? 0
                                : (selected > 0 ? selected : state.scope.garden)
                )
        );
    }

    private List<GardenTodayAdvisor.Weather>
    todayWeather(
            TodayState state
    ) {
        List<GardenTodayAdvisor.Weather> values =
                new ArrayList<>();

        WeatherRepository.WeatherSnapshot current =
                state.current != null
                        ? state.current
                        : state.forecast == null
                        ? null
                        : state.forecast.current;

        if (current != null) {
            GardenTodayAdvisor.Weather w =
                    new GardenTodayAdvisor.Weather(
                            state.scope,
                            "Şimdi"
                    );

            w.max =
                    current.temperatureC;

            w.min =
                    current.temperatureC;

            w.wind =
                    current.windKmh;

            w.humidity =
                    current.humidity;

            w.rain =
                    current.precipitationProbability;

            values.add(w);
        }

        if (state.forecast != null) {

            for (
                    WeatherRepository.HourlyForecast h :
                    state.forecast.hourly
            ) {
                GardenTodayAdvisor.Weather w =
                        new GardenTodayAdvisor.Weather(
                                state.scope,
                                HomeDashboardModel.whenLabel(
                                        h.timeIso,
                                        Calendar.getInstance()
                                )
                        );

                w.max =
                        h.temperatureC;

                w.min =
                        h.temperatureC;

                w.wind =
                        h.windKmh;

                w.rain =
                        h.precipitationProbability;

                w.humidity =
                        h.humidity;

                values.add(w);
            }

            for (
                    WeatherRepository.DailyForecast d :
                    state.forecast.daily
            ) {
                GardenTodayAdvisor.Weather w =
                        new GardenTodayAdvisor.Weather(
                                state.scope,
                                HomeDashboardModel.whenLabel(
                                        d.dateIso,
                                        Calendar.getInstance()
                                )
                        );

                w.max =
                        d.maxTemperatureC;

                w.min =
                        d.minTemperatureC;

                w.rain =
                        d.precipitationProbability;

                values.add(w);
            }
        }

        return values;
    }

    private void renderTodayRecommendations(
            TodayState state
    ) {
        if (!isTodayCurrent(state)) {
            return;
        }

        String date =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(
                        new Date()
                );

        List<GardenTodayAdvisor.Recommendation>
                recommendations =
                new GardenTodayAdvisor()
                        .evaluate(
                                state.scope,
                                date,
                                state.records,
                                state.historyAvailable,
                                todayWeather(state)
                        );

        state.container.removeAllViews();

        int gold =
                GreenTheme.color(R.color.zd_warning);

        for (
                GardenTodayAdvisor.Recommendation item :
                recommendations
        ) {
            // Anlık hava zaten hava kartında gösteriliyor.
            if ("current".equals(item.key)) {
                continue;
            }

            LinearLayout card =
                    new LinearLayout(this);

            card.setOrientation(
                    LinearLayout.VERTICAL
            );

            card.setPadding(
                    dp(16),
                    dp(12),
                    dp(16),
                    dp(4)
            );

            card.setBackground(
                    round(
                            CARD,
                            18,
                            BORDER
                    )
            );

            int accent =
                    item.level
                            == GardenTodayAdvisor.Level.CRITICAL
                            ? RED
                            : item.level
                            == GardenTodayAdvisor.Level.WARNING
                            ? gold
                            : "empty".equals(item.key)
                            ? GREEN
                            : MUTED;

            TextView tag =
                    text(
                            HomeDashboardModel.tagFor(item.key),
                            11,
                            accent,
                            true
                    );

            tag.setLetterSpacing(0.08f);

            card.addView(tag);

            TextView title =
                    text(
                            HomeDashboardModel.plainTitle(item.title),
                            16,
                            TEXT,
                            true
                    );

            title.setPadding(
                    0,
                    dp(4),
                    0,
                    0
            );

            card.addView(title);

            TextView description =
                    text(
                            item.message,
                            13,
                            GreenTheme.color(R.color.zd_text_soft),
                            false
                    );

            description.setLineSpacing(
                    dp(2),
                    1f
            );

            description.setPadding(
                    0,
                    dp(4),
                    0,
                    0
            );

            card.addView(description);

            String label =
                    item.action
                            == GardenTodayAdvisor.Action.WEATHER
                            ? "Saatlik tahmin"
                            : item.action
                            == GardenTodayAdvisor.Action.ADD_GARDEN
                            ? "Bahçe ekle"
                            : "Bahçeyi aç";

            TextView action =
                    text(
                            label + "  ›",
                            13,
                            GREEN,
                            true
                    );

            action.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            action.setMinHeight(
                    dp(44)
            );

            action.setOnClickListener(
                    v -> {
                        if (!isTodayCurrent(
                                state
                        )) {
                            return;
                        }

                        if (
                                item.action
                                        == GardenTodayAdvisor.Action.WEATHER
                        ) {
                            if (
                                    state.openWeather
                                            != null
                            ) {
                                state.openWeather.run();
                            }
                        } else if (
                                item.action
                                        == GardenTodayAdvisor.Action.ADD_GARDEN
                        ) {
                            startActivity(
                                    new Intent(
                                            this,
                                            AddGardenActivity.class
                                    )
                            );
                        } else {
                            Intent intent =
                                    new Intent(
                                            this,
                                            GardenDetailActivity.class
                                    );

                            intent.putExtra(
                                    "garden_id",
                                    state.scope.garden
                            );

                            startActivity(
                                    intent
                            );
                        }
                    }
            );

            card.addView(
                    action,
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    )
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            params.setMargins(
                    0,
                    0,
                    0,
                    dp(8)
            );

            state.container.addView(
                    card,
                    params
            );
        }
    }

    private static final String[] HOME_ASSISTANT_QUESTIONS = {
            "Bugün bahçede ne var?",
            "Yarın yağmur yağacak mı?",
            "Son sulama ne zaman?"
    };

    private View buildHomeAssistantChips() {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                dp(16),
                dp(10),
                dp(16),
                0
        );

        TextView label =
                text(
                        "ASİSTANA SOR",
                        11,
                        MUTED,
                        true
                );

        label.setLetterSpacing(0.08f);

        box.addView(label);

        HorizontalScrollView scroller =
                new HorizontalScrollView(this);

        scroller.setHorizontalScrollBarEnabled(false);

        LinearLayout chips = row();

        for (int i = 0; i < HOME_ASSISTANT_QUESTIONS.length; i++) {
            final String question =
                    HOME_ASSISTANT_QUESTIONS[i];

            TextView chip =
                    text(
                            question,
                            13,
                            TEXT,
                            true
                    );

            chip.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            chip.setMinHeight(
                    dp(44)
            );

            chip.setPadding(
                    dp(14),
                    0,
                    dp(14),
                    0
            );

            chip.setBackground(
                    round(
                            Color.TRANSPARENT,
                            22,
                            BORDER
                    )
            );

            chip.setContentDescription(
                    "Asistana sor: " + question
            );

            chip.setOnClickListener(
                    v -> askZeytinAssistant(question)
            );

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    );

            if (i > 0) {
                cp.setMargins(
                        dp(8),
                        0,
                        0,
                        0
                );
            }

            chips.addView(
                    chip,
                    cp
            );
        }

        scroller.addView(chips);

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        sp.setMargins(
                0,
                dp(8),
                0,
                0
        );

        box.addView(
                scroller,
                sp
        );

        return box;
    }

    private LinearLayout homeMiniMetric(
            String label,
            String value,
            int valueColor
    ) {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                dp(4),
                dp(10),
                dp(4),
                0
        );

        TextView l =
                text(
                        label,
                        11,
                        MUTED,
                        false
                );

        l.setGravity(
                Gravity.CENTER
        );

        box.addView(l);

        TextView v =
                text(
                        value,
                        15,
                        valueColor,
                        true
                );

        v.setGravity(
                Gravity.CENTER
        );

        v.setPadding(
                0,
                dp(4),
                0,
                0
        );

        box.addView(v);

        return box;
    }

    private View buildHomeMarketCard(
            HomeMarketSnapshot item
    ) {
        LinearLayout card =
                verticalCard();

        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );

        card.addView(
                text(
                        item.region,
                        16,
                        TEXT,
                        true
                )
        );

        TextView grade =
                text(
                        "2 No",
                        12,
                        MUTED,
                        false
                );

        grade.setPadding(
                0,
                dp(2),
                0,
                0
        );

        card.addView(grade);

        TextView price =
                text(
                        item.median > 0
                                ? fmtPrice(
                                item.median
                        )
                                : "—",
                        23,
                        GREEN,
                        true
                );

        price.setPadding(
                0,
                dp(4),
                0,
                0
        );

        card.addView(price);

        TextView detail =
                text(
                        item.saleCount
                                + " satış  •  "
                                + fmtKg(
                                item.totalKg
                        ),
                        11,
                        MUTED,
                        false
                );

        detail.setPadding(
                0,
                dp(5),
                0,
                0
        );

        card.addView(detail);

        card.setOnClickListener(
                v -> showSection("MARKET")
        );

        return card;
    }

    private HomeGardenSnapshot loadHomeGardenSnapshot() {
        long preferredId = 0;

        try {
            preferredId =
                    Long.parseLong(
                            db.getSetting(
                                    homeActiveGardenSettingKey(),
                                    "0"
                            )
                    );

        } catch (Exception ignored) {
            preferredId = 0;
        }

        if (preferredId > 0) {
            HomeGardenSnapshot preferred =
                    loadHomeGardenSnapshotById(
                            preferredId
                    );

            if (preferred.id > 0) {
                return preferred;
            }
        }

        if(preferredId<=0 && homeWeather!=null && homeWeather.today!=null
                && homeWeather.key.startsWith(currentUserKey()+"|")) {
            HomeGardenSnapshot previous=loadHomeGardenSnapshotById(homeWeather.today.garden.id);
            if(previous.id>0)return previous;
        }

        Cursor c = null;

        try {
            c =
                    gardenRepository
                            .getGardenSummaries();

            if (c != null
                    && c.moveToFirst()) {

                long firstId =
                        getLong(
                                c,
                                "id"
                        );

                HomeGardenSnapshot first =
                        loadHomeGardenSnapshotById(
                                firstId
                        );

                if (first.id > 0) {
                    db.setSetting(
                            homeActiveGardenSettingKey(),
                            String.valueOf(
                                    first.id
                            )
                    );

                    return first;
                }
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }

        return new HomeGardenSnapshot();
    }

    private HomeGardenSnapshot
    loadHomeGardenSnapshotById(
            long gardenId
    ) {
        HomeGardenSnapshot out =
                new HomeGardenSnapshot();

        if (gardenId <= 0) {
            return out;
        }

        Cursor gardenCursor = null;

        try {
            gardenCursor =
                    gardenRepository
                            .getGardenById(
                                    gardenId
                            );

            if (gardenCursor == null
                    || !gardenCursor.moveToFirst()) {
                return out;
            }

            out.id =
                    getLong(
                            gardenCursor,
                            "id"
                    );

            out.name =
                    getString(
                            gardenCursor,
                            "name",
                            "Bahçe"
                    );

            out.photoUri =
                    getString(
                            gardenCursor,
                            "photo_uri",
                            ""
                    );

            out.province =
                    getString(
                            gardenCursor,
                            "province",
                            ""
                    );

            out.district =
                    getString(
                            gardenCursor,
                            "district",
                            ""
                    );

            out.neighborhood =
                    getString(
                            gardenCursor,
                            "neighborhood",
                            ""
                    );

        } finally {
            if (gardenCursor != null) {
                gardenCursor.close();
            }
        }

        return out;
    }

    private String homeActiveGardenSettingKey() {
        String userKey =
                currentUserKey();

        if (TextUtils.isEmpty(
                userKey
        )) {
            userKey = "local";
        }

        return HOME_ACTIVE_GARDEN_SETTING
                + ":"
                + userKey;
    }

    private void showHomeGardenPicker(
            long currentGardenId
    ) {
        final ArrayList<Long> ids =
                new ArrayList<>();

        final ArrayList<String> labels =
                new ArrayList<>();

        int checked = -1;

        Cursor c = null;

        try {
            c =
                    gardenRepository
                            .getGardenSummaries();

            while (
                    c != null
                            && c.moveToNext()
            ) {
                long id =
                        getLong(
                                c,
                                "id"
                        );

                String name =
                        getString(
                                c,
                                "name",
                                "Bahçe"
                        );

                ids.add(id);
                labels.add(name);

                if (id == currentGardenId) {
                    checked =
                            ids.size() - 1;
                }
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (ids.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Aktif bahçe")
                    .setMessage(
                            "Hava ve Bugün Yapılacaklar için önce bir bahçe kaydet."
                    )
                    .setPositiveButton(
                            "Bahçe Ekle",
                            (d, w) ->
                                    startActivity(
                                            new Intent(
                                                    this,
                                                    AddGardenActivity.class
                                            )
                                    )
                    )
                    .setNegativeButton(
                            "Kapat",
                            null
                    )
                    .show();

            return;
        }

        final int selected =
                checked;

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Aktif bahçeyi seç"
                        )
                        .setSingleChoiceItems(
                                labels.toArray(
                                        new String[0]
                                ),
                                selected,
                                null
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored ->
                        dialog.getListView()
                                .setOnItemClickListener(
                                        (
                                                parent,
                                                view,
                                                position,
                                                id
                                        ) -> {
                                            long gardenId =
                                                    ids.get(
                                                            position
                                                    );

                                            db.setSetting(
                                                    homeActiveGardenSettingKey(),
                                                    String.valueOf(
                                                            gardenId
                                                    )
                                            );

                                            dialog.dismiss();

                                            refreshCurrentSection();
                                        }
                                )
        );

        dialog.show();
    }

    private List<HomeMarketSnapshot>
    loadHomeMarketSnapshots() {
        List<HomeMarketSnapshot> result =
                new ArrayList<>();

        Cursor regions = null;

        try {
            regions =
                    marketRepository
                            .getLocalMarketByGrade(
                                    "2"
                            );

            while (
                    regions != null
                            && regions.moveToNext()
                            && result.size() < 2
            ) {
                String region =
                        getString(
                                regions,
                                "region",
                                ""
                        ).trim();

                if (region.isEmpty()) {
                    continue;
                }

                HomeMarketSnapshot item =
                        new HomeMarketSnapshot();

                item.region =
                        region;

                item.saleCount =
                        getInt(
                                regions,
                                "sale_count"
                        );

                item.totalKg =
                        getDouble(
                                regions,
                                "total_kg"
                        );

                Cursor raw = null;

                List<Double> prices =
                        new ArrayList<>();

                try {
                    raw =
                            marketRepository
                                    .getMarketSalesByGradeAndRegion(
                                            "2",
                                            region
                                    );

                    while (
                            raw != null
                                    && raw.moveToNext()
                    ) {
                        double p =
                                getDouble(
                                        raw,
                                        "price_per_kg"
                                );

                        if (p > 0) {
                            prices.add(p);
                        }
                    }

                } finally {
                    if (raw != null) {
                        raw.close();
                    }
                }

                Collections.sort(
                        prices
                );

                item.median =
                        median(prices);

                result.add(item);
            }

        } finally {
            if (regions != null) {
                regions.close();
            }
        }

        return result;
    }

    private long daysSince(
            String rawDate
    ) {
        if (TextUtils.isEmpty(
                rawDate
        )) {
            return -1;
        }

        String[] formats = {
                "yyyy-MM-dd",
                "dd.MM.yyyy",
                "dd/MM/yyyy"
        };

        for (String f : formats) {
            try {
                SimpleDateFormat sdf =
                        new SimpleDateFormat(
                                f,
                                new Locale(
                                        "tr",
                                        "TR"
                                )
                        );

                sdf.setLenient(false);

                Date d =
                        sdf.parse(
                                rawDate.trim()
                        );

                if (d != null) {
                    return Math.max(
                            0,
                            (
                                    System.currentTimeMillis()
                                            - d.getTime()
                            )
                                    / 86400000L
                    );
                }

            } catch (Exception ignored) {
            }
        }

        return -1;
    }

    private String dayGreeting() {
        int hour =
                Calendar.getInstance()
                        .get(
                                Calendar.HOUR_OF_DAY
                        );

        if (hour < 11) {
            return "Günaydın";
        }

        if (hour < 18) {
            return "İyi günler";
        }

        return "İyi akşamlar";
    }

    private String firstName(
            String fullName
    ) {
        if (TextUtils.isEmpty(
                fullName
        )) {
            return "Üretici";
        }

        String[] p =
                fullName
                        .trim()
                        .split("\\s+");

        return p.length == 0
                ? "Üretici"
                : p[0];
    }

    private void openOperationForGarden(
            String type,
            long gardenId
    ) {
        Intent i =
                new Intent(
                        this,
                        NewOperationActivity.class
                );

        i.putExtra(
                "operation_type",
                type
        );

        if (gardenId > 0) {
            i.putExtra(
                    "garden_id",
                    gardenId
            );
        }

        startActivity(i);
    }

    private void openHarvestForGarden(
            long gardenId
    ) {
        Intent i =
                new Intent(
                        this,
                        HarvestActivity.class
                );

        if (gardenId > 0) {
            i.putExtra(
                    "garden_id",
                    gardenId
            );
        }

        startActivity(i);
    }

    private String homeGardenPlace(
            HomeGardenSnapshot garden
    ) {
        if (garden == null
                || garden.id <= 0) {
            return "Bahçe havası";
        }

        if (!TextUtils.isEmpty(
                garden.neighborhood
        )) {
            if (!TextUtils.isEmpty(
                    garden.district
            )) {
                return garden.neighborhood
                        + " • "
                        + garden.district;
            }

            return garden.neighborhood;
        }

        if (!TextUtils.isEmpty(
                garden.district
        )) {
            return garden.district;
        }

        if (!TextUtils.isEmpty(
                garden.province
        )) {
            return garden.province;
        }

        return "Bahçe konumu";
    }

    private static class HomeGardenSnapshot {
        long id;
        String name = "Bahçe";
        String photoUri = "";
        String province = "";
        String district = "";
        String neighborhood = "";
    }

    private static class HomeMarketSnapshot {
        String region = "Bölge";
        int saleCount;
        double totalKg;
        double median;
    }

    // =========================================================
    // BAHÇELER
    // =========================================================

    // =========================================================
    // BAHÇELERİM (Ana Sayfa "Saha Defteri" kart diliyle)
    // =========================================================

    private static final class GardenListItem {
        long id;
        String name, variety, last, photoUri;
        int tree, age;
        double harvestKg, cost;
    }

    private View buildGardensSection() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);

        List<GardenListItem> gardens = new ArrayList<>();
        Cursor c = null;
        try {
            c = gardenRepository.getGardenSummaries();
            while (c != null && c.moveToNext()) {
                GardenListItem g = new GardenListItem();
                g.id = getLong(c, "id");
                g.name = getString(c, "name", "Bahçe");
                g.variety = getString(c, "variety", "");
                g.tree = getInt(c, "tree_count");
                g.age = getInt(c, "age");
                g.last = getString(c, "last_operation", "");
                g.photoUri = getString(c, "photo_uri", "");
                g.harvestKg = getDouble(c, "total_harvest");
                g.cost = getDouble(c, "total_cost");
                gardens.add(g);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        // Ana Sayfa'daki aktif bahçe; ayar boşsa Ana Sayfa ilk bahçeyi seçer.
        long activeId = 0;
        try {
            activeId = Long.parseLong(db.getSetting(homeActiveGardenSettingKey(), "0"));
        } catch (Exception ignored) {
        }
        if (activeId <= 0 && !gardens.isEmpty()) {
            activeId = gardens.get(0).id;
        }

        // Başlık
        LinearLayout header = row();
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView overline = text(
                HomeDashboardModel.gardensOverline(db.getActiveSeasonYear(), gardens.size()),
                11, MUTED, true);
        overline.setLetterSpacing(0.08f);
        titles.addView(overline);
        TextView title = text("Bahçelerim", 24, TEXT, true);
        title.setPadding(0, dp(3), 0, 0);
        titles.addView(title);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        TextView add = text("+ Bahçe ekle", 14, GREEN, true);
        add.setGravity(Gravity.CENTER);
        add.setMinHeight(dp(44));
        add.setPadding(dp(16), 0, dp(16), 0);
        add.setBackground(round(CARD, 22, BORDER));
        add.setContentDescription("Yeni bahçe ekle");
        add.setOnClickListener(v -> startActivity(new Intent(this, AddGardenActivity.class)));
        header.addView(add, new LinearLayout.LayoutParams(-2, -2));
        body.addView(header);

        // Hatırlatmalar
        LinearLayout reminders = row();
        reminders.setPadding(dp(14), dp(12), dp(14), dp(12));
        reminders.setMinimumHeight(dp(64));
        reminders.setBackground(round(CARD, 20, BORDER));
        reminders.setClickable(true);
        reminders.setFocusable(true);
        reminders.setContentDescription(
                "Hatırlatmalar. Sulama, gübreleme ve diğer işlerini zamanında hatırla. Açmak için dokun.");
        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_bell_line);
        bell.setColorFilter(GREEN);
        bell.setPadding(dp(9), dp(9), dp(9), dp(9));
        bell.setBackground(round(GreenTheme.color(R.color.zd_surface), 12, Color.TRANSPARENT));
        bell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        reminders.addView(bell, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout reminderText = new LinearLayout(this);
        reminderText.setOrientation(LinearLayout.VERTICAL);
        reminderText.setPadding(dp(12), 0, dp(8), 0);
        reminderText.addView(text("Hatırlatmalar", 15, TEXT, true));
        TextView reminderSubtitle = text("Sulama, gübreleme ve diğer işlerini zamanında hatırla", 12, MUTED, false);
        reminderSubtitle.setPadding(0, dp(2), 0, 0);
        reminderText.addView(reminderSubtitle);
        reminders.addView(reminderText, new LinearLayout.LayoutParams(0, -2, 1f));
        reminders.addView(text("›", 24, GREEN, true), new LinearLayout.LayoutParams(-2, -2));
        reminders.setOnClickListener(v -> startActivity(
                new Intent(ProducerFeedActivity.this, RemindersActivity.class)));
        LinearLayout.LayoutParams reminderParams = new LinearLayout.LayoutParams(-1, -2);
        reminderParams.setMargins(0, dp(16), 0, dp(12));
        body.addView(reminders, reminderParams);

        for (GardenListItem g : gardens) {
            body.addView(gardenCard(g, g.id == activeId));
        }
        if (gardens.isEmpty()) {
            body.addView(emptyCard(
                    "Henüz bahçe yok",
                    "İlk bahçeni ekleyerek Zeytin Defteri'ni kullanmaya başla."));
        }
        return scroll;
    }

    private View gardenCard(GardenListItem g, boolean active) {
        int gold = GreenTheme.color(R.color.zd_warning);
        int inner = GreenTheme.color(R.color.zd_surface);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(CARD, 20, active ? GreenTheme.color(R.color.zd_green_chip) : BORDER));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout top = row();

        if (!TextUtils.isEmpty(g.photoUri)) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(true);
            image.setBackground(round(inner, 14, Color.TRANSPARENT));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            try {
                Glide.with(this).load(g.photoUri)
                        .placeholder(R.drawable.ic_olive_logo)
                        .error(R.drawable.ic_olive_logo)
                        .centerCrop().into(image);
            } catch (Exception e) {
                image.setImageResource(R.drawable.ic_olive_logo);
            }
            top.addView(image, new LinearLayout.LayoutParams(dp(56), dp(56)));
        } else {
            // Fotoğraf yoksa her bahçeye aynı stok görsel yerine sade bir zeytin karosu.
            ImageView tile = new ImageView(this);
            tile.setImageResource(R.drawable.ic_olive_logo);
            tile.setPadding(dp(12), dp(12), dp(12), dp(12));
            tile.setBackground(round(inner, 14, Color.TRANSPARENT));
            tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            top.addView(tile, new LinearLayout.LayoutParams(dp(56), dp(56)));
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(6), 0);
        TextView name = text(g.name, 17, TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);
        TextView meta = text(HomeDashboardModel.gardenMeta(g.variety, g.tree, g.age), 13, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        info.addView(meta);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        if (active) {
            TextView badge = text("AKTİF", 10, gold, true);
            badge.setLetterSpacing(0.08f);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(round(inner, 10, Color.TRANSPARENT));
            badge.setContentDescription("Ana Sayfa'da gösterilen bahçe");
            top.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        } else {
            top.addView(text("›", 24, MUTED, false), new LinearLayout.LayoutParams(-2, -2));
        }
        card.addView(top);

        LinearLayout stats = row();
        stats.setGravity(Gravity.TOP);
        stats.addView(gardenStat("Hasat", g.harvestKg > 0 ? fmtKg(g.harvestKg) : "—"),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Masraf", g.cost > 0 ? fmtTl(g.cost) : "—"), second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, -2, 1);
        third.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Son işlem", g.last.isEmpty() ? "Kayıt yok" : g.last), third);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.setMargins(0, dp(12), 0, 0);
        card.addView(stats, statsParams);

        card.setContentDescription(g.name + ". " + HomeDashboardModel.gardenMeta(g.variety, g.tree, g.age)
                + ". Bu sezon hasat " + (g.harvestKg > 0 ? fmtKg(g.harvestKg) : "yok")
                + ", masraf " + (g.cost > 0 ? fmtTl(g.cost) : "yok")
                + ", son işlem " + (g.last.isEmpty() ? "kayıt yok" : g.last)
                + (active ? ". Ana Sayfa'da gösterilen bahçe." : ".") + " Açmak için dokun.");
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, GardenDetailActivity.class);
            i.putExtra("garden_id", g.id);
            startActivity(i);
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cp);
        return card;
    }

    private View gardenStat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(round(GreenTheme.color(R.color.zd_surface), 12, Color.TRANSPARENT));
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        box.addView(text(label, 11, MUTED, false));
        TextView v = text(value, 13, TEXT, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        return box;
    }


    // =========================================================
    // PİYASA
    // =========================================================

    private View buildMarketSection() {
        ScrollView scroll = scroll();

        LinearLayout body =
                pageBody();

        scroll.addView(body);

        body.addView(
                sectionTitleWithAction(
                        "Zeytin Piyasası",
                        "İzin verilmiş gerçek satış kayıtlarından oluşan bölgesel piyasa",
                        "+ Satış Ekle",
                        v ->
                                startActivity(
                                        new Intent(
                                                this,
                                                SaleActivity.class
                                        )
                                )
                )
        );

        String[] grades = {
                "Double",
                "1",
                "2",
                "3",
                "4",
                "Yağlık"
        };

        for (String grade : grades) {
            body.addView(
                    marketGradeCard(
                            grade
                    )
            );
        }

        LinearLayout filterInfo =
                verticalCard();

        filterInfo.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        filterInfo.addView(
                text(
                        "🔎  Piyasa görünümü",
                        16,
                        TEXT,
                        true
                )
        );

        TextView filterSub =
                text(
                        "Elekler aynı ekranda gösterilir. Bölgesel karşılaştırma için Meydan’daki piyasa bandını kullanabilirsin.",
                        13,
                        MUTED,
                        false
                );

        filterSub.setPadding(
                0,
                dp(5),
                0,
                0
        );

        filterInfo.addView(
                filterSub
        );

        TextView detail =
                button(
                        "Ayrıntılı piyasa ve filtreler",
                        v ->
                                startActivity(
                                        new Intent(
                                                this,
                                                MarketActivity.class
                                        )
                                )
                );

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        detailParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        filterInfo.addView(
                detail,
                detailParams
        );

        LinearLayout.LayoutParams fp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        fp.setMargins(
                0,
                dp(2),
                0,
                dp(18)
        );

        body.addView(
                filterInfo,
                fp
        );

        return scroll;
    }

    private View marketGradeCard(
            String grade
    ) {
        Cursor c = null;

        List<Double> prices =
                new ArrayList<>();

        double kg = 0;
        int count = 0;

        try {
            c =
                    marketRepository
                            .getMarketSalesByGrade(
                                    grade
                            );

            while (
                    c != null
                            && c.moveToNext()
            ) {
                double p =
                        getDouble(
                                c,
                                "price_per_kg"
                        );

                double k =
                        getDouble(
                                c,
                                "kg"
                        );

                if (p > 0) {
                    prices.add(p);
                }

                if (k > 0) {
                    kg += k;
                }

                count++;
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }

        Collections.sort(
                prices
        );

        double median =
                median(prices);

        double min =
                prices.isEmpty()
                        ? 0
                        : prices.get(0);

        double max =
                prices.isEmpty()
                        ? 0
                        : prices.get(
                        prices.size() - 1
                );

        LinearLayout card =
                verticalCard();

        card.setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
        );

        card.addView(
                text(
                        grade
                                + " No Piyasası"
                                .replace(
                                        "Double No",
                                        "Double"
                                )
                                .replace(
                                        "Yağlık No",
                                        "Yağlık"
                                ),
                        20,
                        GREEN_DARK,
                        true
                )
        );

        LinearLayout line = row();

        LinearLayout a =
                new LinearLayout(this);

        a.setOrientation(
                LinearLayout.VERTICAL
        );

        a.addView(
                text(
                        "Medyan Fiyat",
                        13,
                        MUTED,
                        false
                )
        );

        a.addView(
                text(
                        median > 0
                                ? fmtPrice(median)
                                : "—",
                        25,
                        GREEN_DARK,
                        true
                )
        );

        line.addView(
                a,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        LinearLayout b =
                new LinearLayout(this);

        b.setOrientation(
                LinearLayout.VERTICAL
        );

        b.addView(
                text(
                        "Fiyat Aralığı",
                        13,
                        MUTED,
                        false
                )
        );

        b.addView(
                text(
                        min > 0
                                ? fmtPrice(min)
                                + " – "
                                + fmtPrice(max)
                                : "—",
                        18,
                        TEXT,
                        true
                )
        );

        line.addView(
                b,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        lp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        card.addView(
                line,
                lp
        );

        card.addView(
                padded(
                        text(
                                "Satış hacmi: "
                                        + fmtKg(kg)
                                        + "   •   Gerçek satış: "
                                        + count
                                        + " kayıt",
                                13,
                                MUTED,
                                false
                        ),
                        12
                )
        );

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        card.setLayoutParams(cp);

        return card;
    }

    // =========================================================
    // RAPORLAR
    // =========================================================

    private View buildReportsSection() {
        final int activeYear = db.getActiveSeasonYear();
        final String activeUid = db.getActiveOwnerUid() == null ? "" : db.getActiveOwnerUid();

        if (reportsSectionCache != null
                && reportsSectionCacheYear == activeYear
                && activeUid.equals(reportsSectionCacheUid)) {
            return reportsSectionCache;
        }

        ZeytinLeagueContainerView container =
                new ZeytinLeagueContainerView(
                        this,
                        activeYear,
                        activeUid,
                        this::showSeasonPicker,
                        () -> {
                            contentHost.removeAllViews();
                            contentHost.addView(
                                    buildLegacyReportsSection(),
                                    match()
                            );
                        }
                );

        String marketInitialRegion =
                db.getSetting(
                        "user_district",
                        ""
                );

        String marketInitialProvince =
                db.getSetting(
                        "user_province",
                        ""
                );

        HomeGardenSnapshot marketGarden =
                loadHomeGardenSnapshot();

        if (marketGarden != null) {
            if (TextUtils.isEmpty(marketInitialRegion)) {
                if (!TextUtils.isEmpty(marketGarden.district)) {
                    marketInitialRegion = marketGarden.district;
                } else if (!TextUtils.isEmpty(marketGarden.province)) {
                    marketInitialRegion = marketGarden.province;
                }
            }

            if (TextUtils.isEmpty(marketInitialProvince)
                    && !TextUtils.isEmpty(marketGarden.province)) {
                marketInitialProvince = marketGarden.province;
            }
        }

        container.configureMarket(
                marketRepository,
                db,
                marketInitialRegion,
                marketInitialProvince
        );

        reportsSectionCache = container;
        reportsSectionCacheYear = activeYear;
        reportsSectionCacheUid = activeUid;

        return container;
    }

    private View buildLegacyReportsSection() {
        ScrollView scroll = scroll();

        LinearLayout body =
                pageBody();

        scroll.addView(body);

        body.addView(
                sectionTitle(
                        "Zeytin Ligi",
                        db.getActiveSeasonYear()
                                + " sezonu • yalnızca bu hesaba ait kayıtlar"
                )
        );

        LinearLayout reportLinks =
                row();

        reportLinks.addView(
                button(
                        "Ayrıntılı rapor",
                        v ->
                                startActivity(
                                        new Intent(
                                                this,
                                                ReportActivity.class
                                        )
                                )
                ),
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                )
        );

        TextView historyLink =
                button(
                        "İşlem geçmişi",
                        v ->
                                startActivity(
                                        new Intent(
                                                this,
                                                OperationHistoryActivity.class
                                        )
                                )
                );

        LinearLayout.LayoutParams historyParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1
                );

        historyParams.setMargins(
                dp(8),
                0,
                0,
                dp(14)
        );

        reportLinks.addView(
                historyLink,
                historyParams
        );

        body.addView(reportLinks);

        double expenses =
                db.getTotalExpenses();

        double harvest =
                db.getTotalHarvestKg();

        double revenue =
                db.getTotalSalesRevenue();

        body.addView(
                reportBlock(
                        "📊  Genel Durum",
                        new String[][]{
                                {
                                        "Toplam Masraf",
                                        fmtTl(expenses)
                                },
                                {
                                        "Toplam Hasat",
                                        fmtKg(harvest)
                                },
                                {
                                        "Satış Geliri",
                                        fmtTl(revenue)
                                },
                                {
                                        "Net Sonuç",
                                        fmtTl(
                                                revenue
                                                        - expenses
                                        )
                                }
                        },
                        revenue - expenses < 0
                                ? 3
                                : -1
                )
        );

        body.addView(
                reportBlock(
                        "💰  Satış & Tahsilat",
                        new String[][]{
                                {
                                        "Satılan",
                                        fmtKg(
                                                db.getTotalSoldKg()
                                        )
                                },
                                {
                                        "Gerçek Satış",
                                        db.getRealSaleCount()
                                                + " kayıt"
                                },
                                {
                                        "Tahsil Edilen",
                                        fmtTl(
                                                db.getTotalAmountReceived()
                                        )
                                },
                                {
                                        "Açık Alacak",
                                        fmtTl(
                                                db.getTotalOpenReceivableBalance()
                                        )
                                }
                        },
                        -1
                )
        );

        body.addView(
                reportBlock(
                        "🫙  Salamura",
                        new String[][]{
                                {
                                        "Salamuraya Giren",
                                        fmtKg(
                                                db.getTotalBrineOliveKg()
                                        )
                                },
                                {
                                        "Hazır Ürün",
                                        fmtKg(
                                                db.getTotalReadyBrineKg()
                                        )
                                },
                                {
                                        "Salamura Maliyeti",
                                        fmtTl(
                                                db.getTotalBrineCost()
                                        )
                                },
                                {
                                        "Aktif Parti",
                                        db.getActiveBrineBatchCount()
                                                + ""
                                }
                        },
                        -1
                )
        );

        body.addView(
                buildGardenPerformanceBlock()
        );

        return scroll;
    }

    private View reportBlock(
            String title,
            String[][] values,
            int redIndex
    ) {
        LinearLayout card =
                verticalCard();

        card.setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
        );

        card.addView(
                text(
                        title,
                        20,
                        GREEN_DARK,
                        true
                )
        );

        for (int r = 0;
             r < 2;
             r++) {

            LinearLayout row = row();

            for (int col = 0;
                 col < 2;
                 col++) {

                int i =
                        r * 2
                                + col;

                LinearLayout cell =
                        new LinearLayout(this);

                cell.setOrientation(
                        LinearLayout.VERTICAL
                );

                cell.addView(
                        text(
                                values[i][0],
                                13,
                                MUTED,
                                false
                        )
                );

                TextView value =
                        text(
                                values[i][1],
                                22,
                                i == redIndex
                                        ? RED
                                        : GREEN_DARK,
                                true
                        );

                value.setPadding(
                        0,
                        dp(3),
                        0,
                        0
                );

                cell.addView(value);

                row.addView(
                        cell,
                        new LinearLayout.LayoutParams(
                                0,
                                dp(76),
                                1
                        )
                );
            }

            card.addView(row);
        }

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        card.setLayoutParams(cp);

        return card;
    }

    private View buildGardenPerformanceBlock() {
        LinearLayout wrap =
                verticalCard();

        wrap.setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
        );

        wrap.addView(
                text(
                        "🌳  Bahçe Performansı",
                        20,
                        TEXT,
                        true
                )
        );

        TextView sub =
                text(
                        "Her bahçenin hasat ve masraf özeti",
                        13,
                        MUTED,
                        false
                );

        sub.setPadding(
                0,
                dp(4),
                0,
                dp(10)
        );

        wrap.addView(sub);

        Cursor c = null;
        boolean any = false;

        try {
            c =
                    gardenRepository
                            .getGardenSummaries();

            while (
                    c != null
                            && c.moveToNext()
            ) {
                any = true;

                String name =
                        getString(
                                c,
                                "name",
                                "Bahçe"
                        );

                String variety =
                        getString(
                                c,
                                "variety",
                                ""
                        );

                int tree =
                        getInt(
                                c,
                                "tree_count"
                        );

                double totalCost =
                        getDouble(
                                c,
                                "total_cost"
                        );

                double totalHarvest =
                        getDouble(
                                c,
                                "total_harvest"
                        );

                LinearLayout item =
                        new LinearLayout(this);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );

                item.setPadding(
                        dp(14),
                        dp(12),
                        dp(14),
                        dp(12)
                );

                item.setBackground(
                        round(
                                GreenTheme.color(R.color.zd_surface),
                                16,
                                BORDER
                        )
                );

                item.addView(
                        text(
                                name,
                                16,
                                TEXT,
                                true
                        )
                );

                item.addView(
                        padded(
                                text(
                                        tree
                                                + " ağaç"
                                                + (
                                                TextUtils.isEmpty(
                                                        variety
                                                )
                                                        ? ""
                                                        : "  •  "
                                                        + variety
                                        ),
                                        12,
                                        MUTED,
                                        false
                                ),
                                3
                        )
                );

                item.addView(
                        padded(
                                text(
                                        "Hasat: "
                                                + fmtKg(
                                                totalHarvest
                                        )
                                                + "   •   Masraf: "
                                                + fmtTl(
                                                totalCost
                                        ),
                                        13,
                                        GREEN_DARK,
                                        true
                                ),
                                8
                        )
                );

                LinearLayout.LayoutParams ip =
                        new LinearLayout.LayoutParams(
                                -1,
                                -2
                        );

                ip.setMargins(
                        0,
                        0,
                        0,
                        dp(8)
                );

                wrap.addView(
                        item,
                        ip
                );
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (!any) {
            wrap.addView(
                    text(
                            "Henüz bahçe verisi yok.",
                            13,
                            MUTED,
                            false
                    )
            );
        }

        LinearLayout.LayoutParams wp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        wp.setMargins(
                0,
                0,
                0,
                dp(18)
        );

        wrap.setLayoutParams(wp);

        return wrap;
    }

    // =========================================================
    // PROFİL
    // =========================================================

    // Content identity only, never an authorization cache. Read the same profile sources as the view.
    private String settingsFingerprint() {
        SessionManager session = new SessionManager(this);
        FirebaseUser user = firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
        Map<String, ?> photo = getSharedPreferences("zeytin_profile_prefs", MODE_PRIVATE).getAll();
        return java.util.Arrays.asList(session.getSessionType(), session.getUserUid(), session.getUserName(),
                session.getUserEmail(), currentUserName(), user == null ? null : user.getEmail(),
                user == null ? null : user.getPhoneNumber(), user == null ? null : user.getPhotoUrl(),
                db.getSetting("user_province", ""), db.getSetting("user_district", ""),
                db.getActiveSeasonYear(), new java.util.TreeMap<>(photo)).toString();
    }

    private SettingsView buildProfileSection(ModerationAccess.Access access) {
        SessionManager session = new SessionManager(this);
        FirebaseUser user = firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
        boolean guest = session.isGuest() || (user != null && user.isAnonymous());
        String name = guest ? session.getDisplayName() : currentUserName();
        if (user == null || TextUtils.isEmpty(user.getDisplayName())) {
            name = safe(session.getUserName(), name);
        }
        String email = guest ? "" : user != null && !TextUtils.isEmpty(user.getEmail())
                ? user.getEmail() : session.getUserEmail();
        String phone = guest || user == null ? "" : user.getPhoneNumber();
        String province = db.getSetting("user_province", "");
        String district = db.getSetting("user_district", "");
        String region = guest ? "" : TextUtils.isEmpty(district) ? safe(province, "")
                : district + (TextUtils.isEmpty(province) ? "" : " / " + province);
        View avatar = new ProfilePhotoManager(this, firebaseAuth, session).createAvatarView(
                52, 22, user == null ? "" : user.getUid(), name, GREEN_SOFT, GREEN, TEXT);
        return new SettingsView(this, avatar, name, phone, email, region,
                db.getActiveSeasonYear(), this::showSeasonPicker, this::openZeytinAssistant, access);
    }

    // =========================================================
    // ALT MENÜ
    // =========================================================

    private LinearLayout buildBottomNav() {
        LinearLayout nav = row();

        nav.setGravity(
                Gravity.CENTER
        );

        nav.setBackgroundColor(
                GreenTheme.color(R.color.zd_surface)
        );

        nav.setElevation(
                dp(16)
        );

        return nav;
    }

    private void refreshBottomNav() {
        if (bottomNav == null) {
            return;
        }

        bottomNav.removeAllViews();
        bottomNav.addView(navItem(R.drawable.ic_home_nav, "Ana Sayfa", "HOME"), navWeight());
        bottomNav.addView(navItem(R.drawable.ic_garden_nav, "Bahçelerim", "GARDENS"), navWeight());
        bottomNav.addView(navItem(R.drawable.ic_elek_nav, "Elek", "ELEK"), navWeight());

        LinearLayout assistantItem = new LinearLayout(this);
        assistantItem.setOrientation(LinearLayout.VERTICAL);
        assistantItem.setGravity(Gravity.CENTER);
        ZeytinAssistantOrbView plus = new ZeytinAssistantOrbView(this);
        assistantOrb = plus;
        plus.setContentDescription("Zeytin Asistanını aç. İşlem eklemek için uzun bas.");
        plus.setElevation(dp(8));
        assistantItem.addView(plus, new LinearLayout.LayoutParams(dp(60), dp(60)));
        TextView assistantLabel = text("ASİSTAN", 10, TEXT, true);
        assistantLabel.setGravity(Gravity.CENTER);
        assistantLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        assistantItem.addView(assistantLabel, new LinearLayout.LayoutParams(-1, -2));
        bottomNav.addView(assistantItem, new LinearLayout.LayoutParams(dp(64), -1));

        plus.setOnClickListener(v -> {
            if (
                    zeytinAssistant != null
                            && zeytinAssistant.isOpen()
            ) {
                zeytinAssistant.orbTap();

            } else {
                openZeytinAssistant();
            }
        });

        plus.setOnLongClickListener(v -> {
            if (zeytinAssistant != null) {
                zeytinAssistant.stop();
            }

            showQuickCreateMenu();

            return true;
        });

        bottomNav.addView(navItem(R.drawable.ic_league_nav, "Zeytin Ligi", "REPORTS"), navWeight());
        bottomNav.addView(navItem(R.drawable.ic_chat_nav, "Sohbet", "MEYDAN"), navWeight());
        bottomNav.addView(navItem(R.drawable.ic_settings_nav, "Ayarlar", "PROFILE"), navWeight());
    }

    private LinearLayout navItem(int icon, String label, String section) {
        boolean active = section.equals(currentSection);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setContentDescription(label);
        item.setSelected(active);
        item.setPadding(dp(1), dp(6), dp(1), dp(4));

        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(active ? GREEN : MUTED);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = text(label, 10, active ? GREEN : MUTED, active);
        title.setGravity(Gravity.CENTER);
        title.setMinLines(2);
        title.setMaxLines(2);
        title.setIncludeFontPadding(false);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(5);
        item.addView(title, labelParams);
        item.setOnClickListener(v -> {
            if ("ELEK".equals(section)) {
                startActivity(new Intent(this, ElekActivity.class));
            } else {
                showSection(section);
            }
        });
        return item;
    }

    private LinearLayout.LayoutParams navWeight() {
        return new LinearLayout.LayoutParams(
                0,
                -1,
                1
        );
    }

    private long assistantActiveGardenId() {
        try {
            return Long.parseLong(
                    db.getSetting(
                            homeActiveGardenSettingKey(),
                            "0"
                    )
            );

        } catch (Exception ignored) {
            return 0;
        }
    }

    private void openZeytinAssistant() {
        ensureZeytinAssistant();
        zeytinAssistant.show();
    }

    private void ensureZeytinAssistant() {
        if (zeytinAssistant == null) {

            zeytinAssistant =
                    new ZeytinAssistantController(
                            this,
                            new ZeytinAssistantController.Host() {

                                public void visual(
                                        ZeytinAssistantConversation.State state
                                ) {
                                    if (assistantOrb != null) {
                                        assistantOrb.state(
                                                state
                                        );
                                    }
                                }

                                public void rms(
                                        float level
                                ) {
                                    if (assistantOrb != null) {
                                        assistantOrb.rms(
                                                level
                                        );
                                    }
                                }

                                public String owner() {
                                    return db.getActiveOwnerUid();
                                }

                                public int season() {
                                    return db.getActiveSeasonYear();
                                }

                                public long activeGarden() {
                                    return assistantActiveGardenId();
                                }

                                public void navigate(
                                        String target,
                                        long gardenId
                                ) {
                                    switch (target) {

                                        case "HOME":
                                        case "GARDENS":
                                        case "MARKET":
                                        case "MEYDAN":
                                        case "REPORTS":
                                            showSection(target);
                                            return;

                                        case "Sulama":
                                        case "Gübre":
                                        case "İlaçlama":
                                            openOperationForGarden(
                                                    target,
                                                    gardenId
                                            );
                                            return;

                                        case "HARVEST":
                                            openHarvestForGarden(
                                                    gardenId
                                            );
                                            return;

                                        default:
                                            break;
                                    }

                                    Intent intent;

                                    if ("ELEK".equals(target)) {

                                        intent =
                                                new Intent(
                                                        ProducerFeedActivity.this,
                                                        ElekActivity.class
                                                );

                                    } else if ("SALE".equals(target)) {

                                        intent =
                                                new Intent(
                                                        ProducerFeedActivity.this,
                                                        SaleActivity.class
                                                );

                                    } else if ("BRINE".equals(target)) {

                                        intent =
                                                new Intent(
                                                        ProducerFeedActivity.this,
                                                        BrineActivity.class
                                                );

                                    } else {
                                        return;
                                    }

                                    if (gardenId > 0) {
                                        intent.putExtra(
                                                "garden_id",
                                                gardenId
                                        );
                                    }

                                    startActivity(intent);
                                }

                                public void weather(
                                        ZeytinAssistantCommand.Garden garden,
                                        WeatherRepository.ForecastCallback callback
                                ) {
                                    weatherRepository
                                            .loadForecastForGarden(
                                                    garden.id,
                                                    garden.province,
                                                    garden.district,
                                                    garden.neighborhood,
                                                    callback
                                            );
                                }
                            }
                    );
        }

    }

    private void askZeytinAssistant(
            String question
    ) {
        // Paneli mikrofonu başlatmadan açar; soru yazılı olarak gönderilir.
        ensureZeytinAssistant();
        zeytinAssistant.ask(question);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (zeytinAssistant != null) {
            zeytinAssistant.permissionResult(
                    requestCode,
                    grantResults
            );
        }
    }

    @Override
    protected void onPause() {
        if (settingsContent != null) settingsContent.pause();
        if (zeytinAssistant != null) {
            zeytinAssistant.pause();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        if (socialBlocks != null) socialBlocks.stop();
        if (zeytinAssistant != null) {
            zeytinAssistant.stop();
        }

        super.onStop();
    }

    private void showQuickCreateMenu() {
        String[] actions = {
                "Yeni işlem",
                "Bahçe ekle",
                "Hasat kaydı",
                "Elek kaydı",
                "Satış / fiyat teklifi",
                "Salamura",
                "Sohbet gönderisi"
        };

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Ne kaydetmek istersin?"
                        )
                        .setItems(
                                actions,
                                (d, which) -> {
                                    Intent intent;

                                    switch (which) {

                                        case 1:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            AddGardenActivity.class
                                                    );
                                            break;

                                        case 2:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            HarvestActivity.class
                                                    );
                                            break;

                                        case 3:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            ElekActivity.class
                                                    );
                                            break;

                                        case 4:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            SaleActivity.class
                                                    );
                                            break;

                                        case 5:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            BrineActivity.class
                                                    );
                                            break;

                                        case 6:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            CreatePostActivity.class
                                                    );
                                            break;

                                        default:
                                            intent =
                                                    new Intent(
                                                            this,
                                                            NewOperationActivity.class
                                                    );
                                            break;
                                    }

                                    startActivity(
                                            intent
                                    );
                                }
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .create();

        showDarkDialog(dialog);
    }

    private void showSeasonPicker() {
        final int firstLeagueSeasonYear = 2026;

        int currentYear =
                Calendar.getInstance()
                        .get(
                                Calendar.YEAR
                        );

        int activeYear =
                db.getActiveSeasonYear();

        /*
         * Zeytin Ligi 2026 sezonunda basladi.
         * 2026 oncesi yillari secicide gostermiyoruz; eski kayitlara dokunmuyoruz.
         * Her yeni yil geldiginde gelecek sezon otomatik eklenir:
         * 2026 -> 2027, 2026
         * 2027 -> 2028, 2027, 2026
         */
        int newestSeasonYear =
                Math.max(
                        firstLeagueSeasonYear,
                        currentYear + 1
                );

        int seasonCount =
                newestSeasonYear
                        - firstLeagueSeasonYear
                        + 1;

        String[] seasons =
                new String[seasonCount];

        int checked = -1;

        for (
                int i = 0;
                i < seasons.length;
                i++
        ) {
            int year =
                    newestSeasonYear
                            - i;

            seasons[i] =
                    year
                            + " sezonu";

            if (year == activeYear) {
                checked = i;
            }
        }

        // Eski bir kurulumda aktif sezon 2026 oncesindeyse secicide
        // 2026'yi varsayilan isaretle; kullanici secmeden veriyi degistirme.
        if (checked < 0 && activeYear < firstLeagueSeasonYear) {
            checked =
                    seasons.length - 1;
        }

        final AlertDialog[] holder =
                new AlertDialog[1];

        holder[0] =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Aktif sezon"
                        )
                        .setSingleChoiceItems(
                                seasons,
                                checked,
                                (dialog, which) -> {
                                    int selected =
                                            newestSeasonYear
                                                    - which;

                                    db.setActiveSeasonYear(
                                            selected
                                    );

                                    if (seasonButton != null) {
                                        seasonButton.setText(
                                                selected
                                                        + " ▾"
                                        );
                                    }

                                    dialog.dismiss();

                                    refreshCurrentSection();
                                }
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .create();

        showDarkDialog(
                holder[0]
        );
    }

    private void showDarkDialog(
            AlertDialog dialog
    ) {
        dialog.setOnShowListener(
                ignored -> {
                    if (dialog.getWindow()
                            != null) {

                        dialog.getWindow()
                                .setBackgroundDrawable(
                                        round(
                                                CARD,
                                                20,
                                                BORDER
                                        )
                                );
                    }

                    TextView negative =
                            dialog.getButton(
                                    AlertDialog.BUTTON_NEGATIVE
                            );

                    if (negative != null) {
                        negative.setTextColor(
                                GREEN
                        );
                    }
                }
        );

        dialog.show();
    }

    // =========================================================
    // FIREBASE / SOSYAL
    // =========================================================

    private String typeBadge(
            FeedManager.PostItem p
    ) {
        if (p.realSale) {
            String grade =
                    safe(
                            p.saleGrade,
                            "Satış"
                    );

            return "▥  "
                    + grade
                    + (
                    p.district == null
                            || p.district.isEmpty()
                            ? ""
                            : " • "
                            + p.district
            );
        }

        String t =
                p.postType == null
                        ? "GENERAL"
                        : p.postType.toUpperCase(
                        Locale.ROOT
                );

        switch (t) {
            case "MARKET":
                return "▥  Piyasa";

            case "PEST":
            case "SPRAY":
                return "🌿  Hastalık";

            case "HARVEST":
                return "🧺  Hasat";

            case "IRRIGATION":
                return "💧  Sulama";

            case "FERTILIZER":
                return "🌱  Gübreleme";

            case "SALE":
                return "🤝  Alım-Satım";

            case "GARDEN":
                return "🌳  Bahçem";

            case "QUESTION":
                return "?  Soru";

            default:
                return "👥  Genel";
        }
    }

    private void openComposerWithDraft() {
        String draft =
                messageInput == null
                        ? ""
                        : messageInput
                        .getText()
                        .toString()
                        .trim();

        Intent i =
                new Intent(
                        this,
                        CreatePostActivity.class
                );

        if (!draft.isEmpty()) {
            i.putExtra(
                    "draft_content",
                    draft
            );
        }

        i.putExtra(
                "draft_type",
                activeType
        );

        startActivity(i);
    }

    private void syncPrivateCloudData() {
        if (firebaseAuth == null
                || privateCloudSyncManager == null) {
            return;
        }

        FirebaseUser user =
                firebaseAuth
                        .getCurrentUser();

        if (user == null
                || TextUtils.isEmpty(
                user.getUid()
        )) {
            return;
        }

        privateCloudSyncManager.sync(
                user.getUid(),
                new PrivateCloudSyncManager.Callback() {

                    @Override
                    public void onComplete() {
                        if (!isFinishing()) {
                            runOnUiThread(
                                    ProducerFeedActivity.this
                                            ::refreshCurrentSection
                            );
                        }
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                    }
                }
        );
    }

    private String currentUserKey() {
        try {
            FirebaseUser u =
                    FirebaseAuth
                            .getInstance()
                            .getCurrentUser();

            if (u != null
                    && u.getUid() != null) {

                return u.getUid();
            }

        } catch (Exception ignored) {
        }

        return db == null
                ? "local_user"
                : db.getSetting(
                "user_id",
                "local_user"
        );
    }

    private String currentUserName() {
        try {
            FirebaseUser u =
                    FirebaseAuth
                            .getInstance()
                            .getCurrentUser();

            if (
                    u != null
                            && !TextUtils.isEmpty(
                            u.getDisplayName()
                    )
            ) {
                return u.getDisplayName()
                        .trim();
            }

        } catch (Exception ignored) {
        }

        return db == null
                ? "Üretici"
                : db.getSetting(
                "user_name",
                "Üretici"
        );
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private ScrollView scroll() {
        ScrollView s =
                new ScrollView(this);

        s.setFillViewport(true);

        s.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        s.setBackgroundColor(BG);

        return s;
    }

    private LinearLayout pageBody() {
        LinearLayout b =
                new LinearLayout(this);

        b.setOrientation(
                LinearLayout.VERTICAL
        );

        b.setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(110)
        );

        return b;
    }

    private View sectionTitle(
            String title,
            String subtitle
    ) {
        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                0,
                dp(2),
                0,
                dp(18)
        );

        box.addView(
                text(
                        title,
                        30,
                        GREEN_DARK,
                        true
                )
        );

        TextView sub =
                text(
                        subtitle,
                        15,
                        MUTED,
                        false
                );

        sub.setPadding(
                0,
                dp(4),
                0,
                0
        );

        box.addView(sub);

        return box;
    }

    private View sectionTitleWithAction(
            String title,
            String subtitle,
            String action,
            View.OnClickListener listener
    ) {
        LinearLayout outer =
                new LinearLayout(this);

        outer.setOrientation(
                LinearLayout.VERTICAL
        );

        outer.setPadding(
                0,
                dp(2),
                0,
                dp(18)
        );

        LinearLayout top = row();

        top.addView(
                text(
                        title,
                        30,
                        GREEN_DARK,
                        true
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView a =
                text(
                        action,
                        14,
                        BG,
                        true
                );

        a.setGravity(
                Gravity.CENTER
        );

        a.setBackground(
                round(
                        GREEN,
                        22,
                        Color.TRANSPARENT
                )
        );

        a.setOnClickListener(
                listener
        );

        top.addView(
                a,
                new LinearLayout.LayoutParams(
                        dp(122),
                        dp(44)
                )
        );

        outer.addView(top);

        TextView sub =
                text(
                        subtitle,
                        15,
                        MUTED,
                        false
                );

        sub.setPadding(
                0,
                dp(5),
                0,
                0
        );

        outer.addView(sub);

        return outer;
    }

    private TextView actionTitle(
            String title
    ) {
        TextView t =
                text(
                        title,
                        21,
                        TEXT,
                        true
                );

        t.setPadding(
                0,
                dp(18),
                0,
                dp(12)
        );

        return t;
    }

    private LinearLayout metricCard(
            String label,
            String value,
            String sub
    ) {
        LinearLayout c =
                verticalCard();

        c.setPadding(
                dp(16),
                dp(15),
                dp(16),
                dp(15)
        );

        c.addView(
                text(
                        label,
                        14,
                        MUTED,
                        false
                )
        );

        c.addView(
                padded(
                        text(
                                value,
                                23,
                                GREEN_DARK,
                                true
                        ),
                        5
                )
        );

        c.addView(
                padded(
                        text(
                                sub,
                                12,
                                MUTED,
                                false
                        ),
                        3
                )
        );

        return c;
    }

    private LinearLayout quickButton(
            String icon,
            String label,
            View.OnClickListener l
    ) {
        LinearLayout c =
                verticalCard();

        c.setGravity(
                Gravity.CENTER
        );

        c.setPadding(
                dp(5),
                dp(10),
                dp(5),
                dp(9)
        );

        TextView i =
                text(
                        icon,
                        23,
                        GREEN,
                        false
                );

        i.setGravity(
                Gravity.CENTER
        );

        c.addView(i);

        TextView txt =
                text(
                        label,
                        11,
                        TEXT,
                        true
                );

        txt.setGravity(
                Gravity.CENTER
        );

        txt.setPadding(
                0,
                dp(5),
                0,
                0
        );

        c.addView(txt);

        c.setOnClickListener(l);

        return c;
    }

    private LinearLayout.LayoutParams homeQuickCard() {
        return new LinearLayout.LayoutParams(
                0,
                dp(96),
                1
        );
    }

    private LinearLayout.LayoutParams homeQuickCardWithLeft() {
        LinearLayout.LayoutParams p =
                homeQuickCard();

        p.setMargins(
                dp(7),
                0,
                0,
                0
        );

        return p;
    }

    private LinearLayout.LayoutParams weightCard() {
        return new LinearLayout.LayoutParams(
                0,
                dp(112),
                1
        );
    }

    private LinearLayout.LayoutParams weightCardWithLeft() {
        LinearLayout.LayoutParams p =
                weightCard();

        p.setMargins(
                dp(8),
                0,
                0,
                0
        );

        return p;
    }

    private LinearLayout verticalCard() {
        LinearLayout c =
                new LinearLayout(this);

        c.setOrientation(
                LinearLayout.VERTICAL
        );

        c.setBackground(
                round(
                        CARD,
                        20,
                        BORDER
                )
        );

        c.setElevation(
                dp(1)
        );

        return c;
    }

    private LinearLayout rowCard() {
        LinearLayout c = row();

        c.setBackground(
                round(
                        CARD,
                        20,
                        BORDER
                )
        );

        c.setElevation(
                dp(1)
        );

        return c;
    }

    private View emptyCard(
            String title,
            String sub
    ) {
        LinearLayout c =
                verticalCard();

        c.setGravity(
                Gravity.CENTER
        );

        c.setPadding(
                dp(18),
                dp(34),
                dp(18),
                dp(34)
        );

        TextView t =
                text(
                        title,
                        18,
                        TEXT,
                        true
                );

        t.setGravity(
                Gravity.CENTER
        );

        c.addView(t);

        TextView s =
                text(
                        sub,
                        14,
                        MUTED,
                        false
                );

        s.setGravity(
                Gravity.CENTER
        );

        s.setPadding(
                0,
                dp(6),
                0,
                0
        );

        c.addView(s);

        return c;
    }

    private TextView button(
            String label,
            View.OnClickListener l
    ) {
        TextView b =
                text(
                        label,
                        15,
                        BG,
                        true
                );

        b.setGravity(
                Gravity.CENTER
        );

        b.setBackground(
                round(
                        GREEN,
                        24,
                        Color.TRANSPARENT
                )
        );

        b.setOnClickListener(l);

        return b;
    }

    private View padded(
            View v,
            int top
    ) {
        v.setPadding(
                v.getPaddingLeft(),
                dp(top),
                v.getPaddingRight(),
                v.getPaddingBottom()
        );

        return v;
    }

    private void openOperation(
            String type
    ) {
        Intent i =
                new Intent(
                        this,
                        NewOperationActivity.class
                );

        i.putExtra(
                "operation_type",
                type
        );

        startActivity(i);
    }

    private LinearLayout row() {
        LinearLayout l =
                new LinearLayout(this);

        l.setOrientation(
                LinearLayout.HORIZONTAL
        );

        l.setGravity(
                Gravity.CENTER_VERTICAL
        );

        return l;
    }

    private TextView text(
            String s,
            int sp,
            int color,
            boolean bold
    ) {
        TextView t =
                new TextView(this);

        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return t;
    }

    private TextView circle(
            String s,
            int sp,
            int bg,
            int fg
    ) {
        TextView t =
                text(
                        s,
                        sp,
                        fg,
                        true
                );

        t.setGravity(
                Gravity.CENTER
        );

        GradientDrawable g =
                new GradientDrawable();

        g.setShape(
                GradientDrawable.OVAL
        );

        g.setColor(bg);

        t.setBackground(g);

        return t;
    }

    private GradientDrawable round(
            int color,
            int radius,
            int stroke
    ) {
        GradientDrawable g =
                new GradientDrawable();

        g.setColor(color);

        g.setCornerRadius(
                dp(radius)
        );

        if (stroke
                != Color.TRANSPARENT) {
            g.setStroke(
                    dp(1),
                    stroke
            );
        }

        return g;
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + .5f
        );
    }

    private String place(FeedManager.PostItem p) {
        String district = safe(p.district, "");
        return district.isEmpty() ? safe(p.province, "") : district;
    }

    private String relativeTime(
            long millis
    ) {
        if (millis <= 0) {
            return "şimdi";
        }

        long diff =
                Math.max(
                        0,
                        System.currentTimeMillis()
                                - millis
                );

        long min =
                diff
                        / 60000L;

        if (min < 1) {
            return "şimdi";
        }

        if (min < 60) {
            return min
                    + " dk önce";
        }

        long h =
                min
                        / 60;

        if (h < 24) {
            return h
                    + " sa önce";
        }

        if (h < 48) {
            return "dün";
        }

        return new SimpleDateFormat(
                "dd MMM",
                new Locale(
                        "tr",
                        "TR"
                )
        ).format(
                new Date(millis)
        );
    }

    private String initials(
            String name
    ) {
        if (TextUtils.isEmpty(
                name
        )) {
            return "Ü";
        }

        String[] parts =
                name.trim()
                        .split("\\s+");

        String a =
                parts[0]
                        .substring(
                                0,
                                1
                        )
                        .toUpperCase(
                                new Locale(
                                        "tr",
                                        "TR"
                                )
                        );

        if (parts.length > 1) {
            a +=
                    parts[
                            parts.length - 1
                            ]
                            .substring(
                                    0,
                                    1
                            )
                            .toUpperCase(
                                    new Locale(
                                            "tr",
                                            "TR"
                                    )
                            );
        }

        return a;
    }

    private static String safe(
            String s,
            String fallback
    ) {
        return s == null
                || s.trim().isEmpty()
                ? fallback
                : s.trim();
    }

    private String fmtKg(
            double v
    ) {
        NumberFormat nf =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "tr",
                                "TR"
                        )
                );

        nf.setMaximumFractionDigits(1);

        return nf.format(v)
                + " kg";
    }

    private String fmtTl(
            double v
    ) {
        NumberFormat nf =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "tr",
                                "TR"
                        )
                );

        nf.setMaximumFractionDigits(0);

        return nf.format(v)
                + " TL";
    }

    private String fmtPrice(
            double v
    ) {
        NumberFormat nf =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "tr",
                                "TR"
                        )
                );

        nf.setMaximumFractionDigits(2);

        return nf.format(v)
                + " TL/kg";
    }

    private double median(
            List<Double> list
    ) {
        if (list == null
                || list.isEmpty()) {
            return 0;
        }

        int n =
                list.size();

        return n % 2 == 1
                ? list.get(
                n / 2
        )
                : (
                list.get(
                        n / 2 - 1
                )
                        + list.get(
                        n / 2
                )
        )
                / 2.0;
    }

    private String getString(
            Cursor c,
            String col,
            String def
    ) {
        int i =
                c.getColumnIndex(
                        col
                );

        if (i < 0
                || c.isNull(i)) {
            return def;
        }

        String s =
                c.getString(i);

        return s == null
                ? def
                : s;
    }

    private int getInt(
            Cursor c,
            String col
    ) {
        int i =
                c.getColumnIndex(
                        col
                );

        return i < 0
                || c.isNull(i)
                ? 0
                : c.getInt(i);
    }

    private long getLong(
            Cursor c,
            String col
    ) {
        int i =
                c.getColumnIndex(
                        col
                );

        return i < 0
                || c.isNull(i)
                ? 0
                : c.getLong(i);
    }

    private double getDouble(
            Cursor c,
            String col
    ) {
        int i =
                c.getColumnIndex(
                        col
                );

        return i < 0
                || c.isNull(i)
                ? 0
                : c.getDouble(i);
    }

    @Override
    protected void onDestroy() {
        homeWeather=null;
        if (zeytinAssistant != null) {
            zeytinAssistant.destroy();
        }

        super.onDestroy();

        if (reactionManager != null) {
            reactionManager.release();
        }

        if (commentManager != null) {
            commentManager.release();
        }

        if (replyOutbox != null) replyOutbox.close();
        if (cloudRepository != null) cloudRepository.close();
        if (db != null) {
            db.close();
        }
    }
}