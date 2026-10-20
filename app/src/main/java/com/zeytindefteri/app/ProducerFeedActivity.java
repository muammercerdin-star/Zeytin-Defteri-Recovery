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

    private LinearLayout marketStrip;
    private TextView marketStripTitle;
    private TextView marketStripSubtitle;
    private int marketStripSerial;
    private TextView composerTopic;
    private TextView typingLine;
    private ChatTypingRepository chatTyping;
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
    private boolean liveFeedFirstEvent;
    private String activeType = "ALL";

    private final List<LinearLayout> categoryViews =
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

        // Maliyet: canlı dinleme yalnız Sohbet sekmesi açıkken (diğer sekmelerde bulut okunmaz).
        if (cloudRepository != null && "MEYDAN".equals(currentSection)) {
            // Tek seferlik senkron yerine canlı dinleme: ekran açıkken gelen yeni mesajlar, silinen
            // gönderiler ve başka telefondan yapılan değişiklikler yenileme beklemeden görünür.
            liveFeedFirstEvent = true;
            cloudRepository.startLiveSync(success -> runOnUiThread(this::onLiveFeedChanged));
        }

        if (cloudRepository != null) cloudRepository.retryPendingPosts();
        if ("MEYDAN".equals(currentSection) && typingLine != null) startTypingWatch();
        if (replyOutbox != null) replyOutbox.flushPending();
        syncPrivateCloudData();
        // Paylaş penceresinden dönüşte Ana Sayfa'yı baştan kurmaya gerek yok: veri değişmedi.
        // (Senkron geri çağrıları gerekirse yine yeniler; kaydırma konumu korunur.)
        boolean returningFromShare = skipNextHomeRefresh && "HOME".equals(currentSection);
        skipNextHomeRefresh = false;
        if (!returningFromShare) refreshCurrentSection();
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
        boolean wasChat = "MEYDAN".equals(currentSection);
        currentSection = section;
        setSeasonReportOpen(false);
        stopTypingWatch();

        if (contentHost == null) {
            return;
        }

        contentHost.removeAllViews();

        feedContainer = null;
        categoryContainer = null;
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

            case "ELEK":
                contentHost.addView(
                        buildElekSection(),
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

        // Sohbet sekmesine girince canlı akış başlar, çıkınca durur (okuma maliyeti).
        boolean isChat = "MEYDAN".equals(currentSection);
        if (cloudRepository != null && isChat && !wasChat) {
            liveFeedFirstEvent = false;
            cloudRepository.startLiveSync(success -> runOnUiThread(this::onLiveFeedChanged));
        } else if (cloudRepository != null && !isChat && wasChat) {
            cloudRepository.stopLiveSync();
        }

        if(previousHomeY>=0 && previousWeather==homeWeather && "HOME".equals(section)
                && contentHost.getChildCount()>0 && contentHost.getChildAt(0) instanceof ScrollView) {
            final ScrollView home=(ScrollView)contentHost.getChildAt(0);
            final int scrollY=previousHomeY;
            // post() bir kare en üstte çizip sonra geri kaydırıyordu ("atlama"). Konumu ilk
            // çizimden ÖNCE geri yükle; içerik kısa kaldıysa en alta sınırla.
            final android.view.ViewTreeObserver observer=home.getViewTreeObserver();
            if(observer.isAlive()) observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    android.view.ViewTreeObserver current=home.getViewTreeObserver();
                    if(current.isAlive())current.removeOnPreDrawListener(this);
                    if(home.getParent()!=contentHost)return true;
                    int content=home.getChildCount()>0?home.getChildAt(0).getHeight():0;
                    home.scrollTo(0,Math.min(scrollY,Math.max(0,content-home.getHeight())));
                    return true;
                }
            });
        }
        if (persistentHeader != null) {
            persistentHeader.setVisibility(
                    ("HOME".equals(currentSection) || "REPORTS".equals(currentSection) || "PROFILE".equals(currentSection)
                            || "MEYDAN".equals(currentSection))
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

    /** İlk canlı görüntü eski senkron gibi bölümü yeniler; sonrakiler yalnızca Sohbet açıksa akışı. */
    private void onLiveFeedChanged() {
        if (isFinishing() || isDestroyed()) return;
        if (liveFeedFirstEvent) {
            liveFeedFirstEvent = false;
            refreshCurrentSection();
        } else if ("MEYDAN".equals(currentSection) && feedContainer != null) {
            loadFeed();
        }
    }

    private void refreshCurrentSection() {
        if (contentHost == null) {
            return;
        }

        if ("MEYDAN".equals(currentSection)
                && feedContainer != null) {

            refreshMarketStrip();
            loadFeed();

        } else if (seasonReportOpen && "REPORTS".equals(currentSection)) {
            openSeasonReport(); // satış ekleyip dönünce rapor açık ve güncel kalsın
        } else {
            showSection(currentSection);
        }
    }

    @Override
    public void onBackPressed() {
        // Sezon Raporum açıkken geri tuşu uygulamadan çıkmasın, Zeytin Ligi'ne dönsün.
        if (seasonReportOpen && "REPORTS".equals(currentSection)) {
            showSection("REPORTS");
            return;
        }
        super.onBackPressed();
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


        body.addView(buildMeydanHeader());

        body.addView(
                buildCategoryRow(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(56)
                )
        );

        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(dp(16), dp(2), dp(16), dp(12));
        body.addView(buildMarketStrip(), mp);

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

        // "… yazıyor" satırı: mesaj kutusunun hemen üstünde, kimse yazmıyorsa gizli.
        typingLine = text("", 12, MUTED, false);
        typingLine.setPadding(dp(18), dp(4), dp(18), dp(4));
        typingLine.setSingleLine(true);
        typingLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        typingLine.setVisibility(View.GONE);
        typingLine.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(typingLine, new LinearLayout.LayoutParams(-1, -2));

        root.addView(
                buildComposer(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );
        startTypingWatch();

        refreshMarketStrip();
        loadFeed();

        return root;
    }



    /** Sohbet başlığı: büyük uygulama başlığı ve sezon düğmesi yerine sade başlık + profil. */
    private View buildMeydanHeader() {
        LinearLayout header = row();
        header.setPadding(dp(20), dp(16), dp(16), dp(6));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Sohbet", 24, TEXT, true));
        titles.addView(text("Bölgendeki üreticilerle konuş", 12, MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        FirebaseUser user = firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
        String uid = user == null ? "" : user.getUid();
        String name = user == null ? "Üretici" : safe(user.getDisplayName(), "Üretici");
        View avatar = feedThreadAvatar(uid, name);
        avatar.setContentDescription("Profil ve ayarlar");
        avatar.setOnClickListener(v -> showSection("PROFILE"));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return header;
    }

    private View buildCategoryRow() {
        HorizontalScrollView hsv =
                new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );
        categoryContainer = row();
        categoryContainer.setPadding(dp(16), dp(6), dp(8), dp(6));
        categoryViews.clear();
        addCategory("Genel", R.drawable.ic_op_chat, "ALL");
        addCategory("Piyasa", R.drawable.ic_op_tag, "MARKET");
        addCategory("Hasat", R.drawable.ic_op_basket, "HARVEST");
        addCategory("Hastalık", R.drawable.ic_op_trap, "PEST");
        addCategory("Sulama", R.drawable.ic_op_water, "IRRIGATION");
        addCategory("Alım-satım", R.drawable.ic_op_truck, "SALE");
        hsv.addView(categoryContainer);
        return hsv;
    }

    private void addCategory(String label, int icon, String type) {
        LinearLayout chip = row();
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setTag(type);
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(icon);
        chip.addView(glyph, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView name = text(label, 13, TEXT, true);
        name.setPadding(dp(6), 0, 0, 0);
        chip.addView(name);
        chip.setContentDescription(label + " sohbeti");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
        p.setMargins(0, 0, dp(8), 0);
        categoryContainer.addView(chip, p);
        chip.setOnClickListener(v -> {
            activeType = String.valueOf(v.getTag());
            styleCategories();
            updateChatSectionTitle();
            loadFeed();
        });
        categoryViews.add(chip);
        styleCategories();
    }

    private void styleCategories() {
        int soft = GreenTheme.color(R.color.zd_text_soft);
        for (LinearLayout chip : categoryViews) {
            boolean active = activeType.equals(String.valueOf(chip.getTag()));
            chip.setBackground(round(active ? GREEN_SOFT : GreenTheme.color(R.color.zd_surface), 20, active ? GREEN : BORDER));
            chip.setSelected(active);
            ((ImageView) chip.getChildAt(0)).setColorFilter(active ? GREEN : soft);
            ((TextView) chip.getChildAt(1)).setTextColor(active ? GREEN : soft);
        }
        if (composerTopic != null) composerTopic.setText(chatTitleForActiveType());
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
        if (composerTopic != null) composerTopic.setText(chatTitleForActiveType());
    }

    private String chatTitleForActiveType() {
        switch (activeType) {
            case "MARKET":
                return "#Piyasa";

            case "PEST":
                return "#Hastalık";

            case "HARVEST":
                return "#Hasat";

            case "IRRIGATION":
                return "#Sulama";

            case "SALE":
                return "#Alım-satım";

            default:
                return "#Genel";
        }
    }

    private View buildComposer() {
        LinearLayout holder = row();
        holder.setPadding(dp(12), dp(8), dp(12), dp(8));
        holder.setBackgroundColor(GreenTheme.color(R.color.zd_surface));

        // Seçili konu görünür: mesaj bu konuya gider. Dokununca konu seçici açılır.
        composerTopic = text(chatTitleForActiveType(), 13, GreenTheme.color(R.color.zd_text_soft), true);
        composerTopic.setGravity(Gravity.CENTER_VERTICAL);
        composerTopic.setPadding(dp(12), 0, dp(8), 0);
        composerTopic.setSingleLine(true);
        android.graphics.drawable.Drawable caret = getDrawable(R.drawable.ic_op_down);
        if (caret != null) {
            caret = caret.mutate();
            caret.setTint(MUTED);
            caret.setBounds(0, 0, dp(14), dp(14));
            composerTopic.setCompoundDrawablesRelative(null, null, caret, null);
            composerTopic.setCompoundDrawablePadding(dp(4));
        }
        composerTopic.setBackground(round(CARD, 23, BORDER));
        composerTopic.setContentDescription("Mesaj konusunu seç");
        composerTopic.setOnClickListener(v -> showMeydanTopicPicker());
        holder.addView(composerTopic, new LinearLayout.LayoutParams(-2, dp(46)));

        messageInput = new EditText(this);
        messageInput.setBackgroundTintList(null);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(3);
        messageInput.setHint("Mesaj yaz…");
        messageInput.setTextSize(14);
        messageInput.setTextColor(TEXT);
        messageInput.setHintTextColor(MUTED);
        messageInput.setPadding(dp(16), dp(4), dp(12), dp(4));
        messageInput.setBackground(round(BG, 23, BORDER));
        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (chatTyping == null) return;
                if (s.toString().trim().isEmpty()) chatTyping.clear(); else chatTyping.typing(activeType);
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, dp(46), 1);
        ip.setMargins(dp(8), 0, dp(8), 0);
        holder.addView(messageInput, ip);

        FrameLayout send = new FrameLayout(this);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(GREEN);
        send.setBackground(sendBg);
        ImageView sendIcon = new ImageView(this);
        sendIcon.setImageResource(R.drawable.ic_op_send);
        sendIcon.setColorFilter(BG);
        send.addView(sendIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        send.setContentDescription("Mesajı gönder");
        holder.addView(send, new LinearLayout.LayoutParams(dp(46), dp(46)));
        send.setOnClickListener(v -> {
            String draft = messageInput == null ? "" : messageInput.getText().toString().trim();
            if (draft.isEmpty()) {
                Toast.makeText(this, "Önce bir mesaj yaz.", Toast.LENGTH_SHORT).show();
                return;
            }
            sendMeydanText(send);
        });
        return holder;
    }

    private final Runnable typingDots = new Runnable() {
        private int step;
        @Override public void run() {
            if (typingLine == null || typingLine.getVisibility() != View.VISIBLE) return;
            String base = String.valueOf(typingLine.getTag() == null ? "" : typingLine.getTag());
            step = (step + 1) % 4;
            typingLine.setText(base + " " + "•••".substring(0, Math.max(1, step)));
            typingLine.postDelayed(this, 450);
        }
    };

    /** Sohbet ekranı açıkken başkalarının "yazıyor" bilgisini dinler. */
    private void startTypingWatch() {
        if (chatTyping == null) chatTyping = new ChatTypingRepository(this);
        java.util.Set<String> hidden = socialBlocks == null ? new java.util.HashSet<>() : socialBlocks.blockedIds();
        chatTyping.start(hidden, typers -> runOnUiThread(() -> {
            if (typingLine == null) return;
            String label = ChatTypingRepository.label(typers, activeType);
            if (label.isEmpty()) {
                typingLine.setVisibility(View.GONE);
                typingLine.removeCallbacks(typingDots);
                return;
            }
            boolean wasHidden = typingLine.getVisibility() != View.VISIBLE;
            typingLine.setTag(label);
            typingLine.setContentDescription(label);
            if (wasHidden) {
                typingLine.setText(label + " •");
                typingLine.setVisibility(View.VISIBLE);
                typingLine.postDelayed(typingDots, 450);
            }
        }));
    }

    private void stopTypingWatch() {
        if (chatTyping != null) { chatTyping.clear(); chatTyping.stop(); }
        if (typingLine != null) { typingLine.removeCallbacks(typingDots); typingLine.setVisibility(View.GONE); }
    }

    private void sendMeydanText(View send) {
        if (publishingText || messageInput == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.length() < 3 || text.length() > 2000) {
            messageInput.setError("Mesaj 3–2000 karakter olmalı."); return;
        }
        if (firebaseAuth == null || firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Paylaşmak için giriş yapmalısın.", Toast.LENGTH_SHORT).show(); return;
        }
        publishingText = true; send.setEnabled(false);
        if (chatTyping != null) chatTyping.clear();
        long id;
        try {
            id = cloudRepository.createTextPost(text, activeType, sent -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                // Kart "Gönderiliyor…" etiketini bırakır; başarısızsa etiket kalır ve sonraki açılışta yeniden denenir.
                if ("MEYDAN".equals(currentSection)) loadFeed();
                if (!sent) Toast.makeText(this, "Mesaj henüz gönderilemedi. Bağlantı gelince yeniden denenecek.",
                        Toast.LENGTH_SHORT).show();
            }));
        }
        catch (RuntimeException error) { id = -1; }
        if (id > 0) {
            messageInput.setText(""); loadFeed();
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

    /** Sohbet'in üstündeki bölge piyasası şeridi; dokununca Zeytin Ligi → Piyasa açılır. */
    private View buildMarketStrip() {
        marketStrip = row();
        marketStrip.setPadding(dp(14), dp(12), dp(10), dp(12));
        marketStrip.setBackground(round(GreenTheme.color(R.color.zd_surface), 16, BORDER));
        ImageView chart = new ImageView(this);
        chart.setImageResource(R.drawable.ic_op_chart);
        chart.setColorFilter(GREEN);
        marketStrip.addView(chart, new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        marketStripTitle = text("", 13, TEXT, true);
        marketStripTitle.setSingleLine(true);
        marketStripTitle.setEllipsize(TextUtils.TruncateAt.END);
        marketStripSubtitle = text("", 12, MUTED, false);
        marketStripSubtitle.setMaxLines(2);
        texts.addView(marketStripTitle);
        texts.addView(marketStripSubtitle);
        marketStrip.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_op_chevron);
        chevron.setColorFilter(GREEN);
        marketStrip.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        marketStrip.setOnClickListener(v -> openMarketFromChat());
        applyMarketStrip(MeydanMarketStrip.loading(""));
        return marketStrip;
    }

    private void applyMarketStrip(MeydanMarketStrip state) {
        if (marketStrip == null) return;
        marketStripTitle.setText(state.title);
        marketStripSubtitle.setText(state.subtitle);
        marketStrip.setBackground(round(GreenTheme.color(R.color.zd_surface), 16,
                state.showsPrice ? GreenTheme.color(R.color.zd_green_mid_dim) : BORDER));
        marketStrip.setContentDescription(state.title + ". " + state.subtitle + ". Piyasayı aç");
    }

    private void openMarketFromChat() {
        showSection("REPORTS");
        if (reportsSectionCache != null) reportsSectionCache.openMarketTab();
    }

    /**
     * Kullanıcının bölgesi kendi elek kayıtlarından (Zeytin Ligi ile aynı kural) bulunur; fiyat ise
     * Supabase'teki bölge istatistiğinden gelir. Bölgede 5 üretici yoksa fiyat yazılmaz.
     */
    private void refreshMarketStrip() {
        if (marketStrip == null) return;
        final int serial = ++marketStripSerial;
        FirebaseUser user = firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
        final String uid = user == null ? "" : user.getUid();
        final int year = db.getActiveSeasonYear();
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String regionId = "", regionLabel = "";
            DatabaseHelper helper = new DatabaseHelper(app);
            try {
                ZeytinLeagueRepository.Result local = new ZeytinLeagueRepository().local(helper, year, uid);
                if (local.local != null) regionId = safe(local.local.region, "");
                for (ZeytinLeagueRepository.Region r : local.regions) {
                    if (r != null && regionId.equals(r.id) && r.name != null) regionLabel = r.name;
                }
            } catch (RuntimeException ignored) {
                // Yerel kayıt okunamazsa bölge bilinmiyor sayılır.
            } finally {
                helper.close();
            }
            final String ownRegion = regionId;
            final String label = "Bölge belirtilmemiş".equals(regionLabel) ? "" : regionLabel;
            runOnUiThread(() -> {
                if (serial != marketStripSerial || isFinishing() || marketStrip == null) return;
                if (label.isEmpty() || uid.isEmpty()) { applyMarketStrip(MeydanMarketStrip.of(label, true, 0, false, 0)); return; }
                applyMarketStrip(MeydanMarketStrip.loading(label));
                MarketCloudRepository cloud = new MarketCloudRepository(db, uid);
                if (!cloud.canUseCloud()) { applyMarketStrip(MeydanMarketStrip.of(label, false, 0, false, 0)); return; }
                cloud.loadLeagueRegions(year, "1 No", (regions, error) -> {
                    if (serial != marketStripSerial || isFinishing() || marketStrip == null) return;
                    if (error != null || regions == null) { applyMarketStrip(MeydanMarketStrip.of(label, false, 0, false, 0)); return; }
                    MarketCloudRepository.LeagueRegionStat own = null;
                    for (MarketCloudRepository.LeagueRegionStat r : regions) if (r != null && r.id.equals(ownRegion)) own = r;
                    applyMarketStrip(own == null
                            ? MeydanMarketStrip.of(label, true, 0, false, 0)
                            : MeydanMarketStrip.of(label, true, own.producerCount, own.published, own.averagePrice));
                });
            });
        }, "meydan-market").start();
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


    /** Sohbet mesaj kartı: yazar, metin, son yanıt önizlemesi, Faydalı ve Yanıtla. */
    private View buildPostCard(final FeedManager.PostItem p) {
        FeedReplyPreview preview = feedReplyPreviews.get(p.id);
        int count = socialBlocks.hasBlocks() ? (preview == null ? 0 : preview.cachedCount)
                : Math.max(Math.max(0, p.commentCount), preview == null ? 0 : preview.cachedCount);
        final String uid = safe(p.userId, "");
        final String name = safe(p.userName, "Üretici");
        int soft = GreenTheme.color(R.color.zd_text_soft);
        int surface = GreenTheme.color(R.color.zd_surface);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(12), dp(4));
        card.setBackground(round(CARD, 20, BORDER));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(12);
        card.setLayoutParams(cp);

        LinearLayout header = row();
        header.addView(feedThreadAvatar(uid, name), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(10), 0, dp(6), 0);
        // Eski "gerçek satış" onay işareti sunucuda doğrulanmadığı için gösterilmez.
        TextView author = text(name, 14, TEXT, true);
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(author);
        String when = p.createdAt > 0 ? feedThreadDate.format(new Date(p.createdAt)) : "";
        String meta = place(p).isEmpty() ? when : (when.isEmpty() ? place(p) : place(p) + " · " + when);
        boolean own = firebaseAuth != null && firebaseAuth.getCurrentUser() != null
                && uid.equals(firebaseAuth.getCurrentUser().getUid());
        // Kendi mesajı buluta henüz ulaşmadıysa (sync_state=1) diğer üreticiler onu görmüyor demektir.
        if (own && p.syncState == MeydanPostSync.PENDING) meta = meta.isEmpty() ? "Gönderiliyor…" : meta + " · Gönderiliyor…";
        TextView metadata = text(meta, 12, MUTED, false);
        metadata.setSingleLine(true);
        metadata.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(metadata);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text(feedThreadCategory(p.postType), 11, soft, true);
        tag.setPadding(dp(9), dp(3), dp(9), dp(3));
        tag.setBackground(round(surface, 10, BORDER));
        header.addView(tag);
        if (SocialReportManager.canReport(uid)) {
            ImageView menu = new ImageView(this);
            menu.setImageResource(R.drawable.ic_op_more);
            menu.setColorFilter(MUTED);
            menu.setPadding(dp(10), dp(10), dp(10), dp(10));
            menu.setContentDescription("Şikâyet seçenekleri");
            menu.setOnClickListener(v -> {
                String postCloudId = db.getPostCloudId(p.id);
                new android.app.AlertDialog.Builder(this).setItems(new String[]{"Şikâyet Et","Kullanıcıyı Şikâyet Et","Kullanıcıyı Engelle"},(dialog,index) -> {
                    if(index==2)SocialBlockUi.confirm(this,socialBlocks,uid,name,place(p),false);
                    else SocialReportManager.showReasons(this,"POST",postCloudId,postCloudId,uid,index==1);
                }).setNegativeButton("Vazgeç",null).show();
            });
            header.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        card.addView(header);

        TextView body = text(safe(p.content, ""), 15, TEXT, false);
        body.setLineSpacing(dp(2), 1f);
        body.setMaxLines(5);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(10), dp(4), 0);
        body.setOnClickListener(v -> openFeedThread(p.id, "", false));
        card.addView(body);

        if (preview != null) {
            LinearLayout reply = row();
            reply.setGravity(Gravity.TOP);
            reply.setPadding(dp(10), dp(8), dp(10), dp(8));
            reply.setBackground(round(surface, 12, surface));
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_op_reply);
            arrow.setColorFilter(MUTED);
            reply.addView(arrow, new LinearLayout.LayoutParams(dp(14), dp(14)));
            android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder();
            String who = safe(preview.name, "Üretici");
            line.append(who);
            line.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, who.length(), 0);
            line.append(" ").append(safe(preview.content, ""));
            if (preview.createdAt > 0) line.append(" · ").append(feedThreadClock.format(new Date(preview.createdAt)));
            TextView replyText = text("", 12, soft, false);
            replyText.setText(line);
            replyText.setMaxLines(2);
            replyText.setEllipsize(TextUtils.TruncateAt.END);
            replyText.setPadding(dp(8), 0, 0, 0);
            reply.addView(replyText, new LinearLayout.LayoutParams(0, -2, 1));
            reply.setOnClickListener(v -> openFeedThread(p.id, "", false));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(10);
            rp.rightMargin = dp(4);
            card.addView(reply, rp);
        }

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-1, dp(1));
        dp1.topMargin = dp(12);
        dp1.rightMargin = dp(4);
        card.addView(divider, dp1);

        LinearLayout actions = row();
        TextView useful = meydanAction("Faydalı", R.drawable.ic_op_heart);
        if (reactionManager != null) reactionManager.setupReactionButton(useful, p.id, currentUserKey());
        actions.addView(useful, new LinearLayout.LayoutParams(-2, dp(44)));
        TextView replyAction = meydanAction(count > 0 ? "Yanıtla · " + count : "Yanıtla", R.drawable.ic_op_reply);
        replyAction.setContentDescription(count > 0 ? "Yanıtla, " + count + " yanıt" : "Yanıtla");
        replyAction.setOnClickListener(v -> openFeedThread(p.id, "", true));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, dp(44));
        ap.leftMargin = dp(18);
        actions.addView(replyAction, ap);
        card.addView(actions);
        card.setOnClickListener(v -> openFeedThread(p.id, "", false));
        return card;
    }

    private TextView meydanAction(String label, int icon) {
        int soft = GreenTheme.color(R.color.zd_text_soft);
        TextView t = text(label, 13, soft, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.Drawable d = getDrawable(icon);
        if (d != null) {
            d = d.mutate();
            d.setTint(soft);
            d.setBounds(0, 0, dp(16), dp(16));
            t.setCompoundDrawablesRelative(d, null, null, null);
            t.setCompoundDrawablePadding(dp(6));
        }
        return t;
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

        View leagueCard = buildHomeLeagueCard();
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(-1, -2);
        lcp.setMargins(dp(16), dp(12), dp(16), 0);
        body.addView(leagueCard, lcp);

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

        // Alt bölüm: hızlı işlemler, Bu Sezon, Senin elek fiyatların (HomeLowerSections)
        new HomeLowerSections(this).addTo(
                body,
                HomeSummaryModel.season(db.getTotalHarvestKg(), homeSeasonElekKg(),
                        db.getTotalExpenses(), db.getTotalSalesRevenue()),
                HomeSummaryModel.top(loadOwnGradePrices(), 2),
                new HomeLowerSections.Actions() {
                    @Override public void operation(String type) { openOperationForGarden(type, garden.id); }
                    @Override public void harvest() { openHarvestForGarden(garden.id); }
                    @Override public void elek() {
                        Intent i = new Intent(ProducerFeedActivity.this, ElekActivity.class);
                        if (garden.id > 0) i.putExtra("garden_id", garden.id);
                        startActivity(i);
                    }
                    @Override public void sale() {
                        Intent i = new Intent(ProducerFeedActivity.this, SaleActivity.class);
                        if (garden.id > 0) i.putExtra("garden_id", (long) garden.id);
                        startActivity(i);
                    }
                    @Override public void seasonReport() { showSection("REPORTS"); openSeasonReport(); }
                    @Override public void market() { showSection("MARKET"); }
                });

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
        final boolean weatherLoading=garden.id>0 && weatherRepository!=null;
        if(weatherLoading){
            // Yer tutucu satırlar gerçek satırlarla aynı yükseklikte: tahmin gelince kart
            // büyümez, altındaki içerik aşağı kaymaz ("atlama" olmaz).
            renderHomeConditionPlaceholders(conditions);
        } else {
            TextView loading=text("Hava tahmini için önce bir bahçe seç",13,MUTED,false);
            loading.setPadding(0,dp(6),0,dp(4));
            conditions.addView(loading);
        }
        LinearLayout.LayoutParams conditionParams=new LinearLayout.LayoutParams(-1,-2);
        conditionParams.setMargins(0,dp(12),0,0);
        weatherCard.addView(conditions,conditionParams);

        final LinearLayout rainBars=row();
        rainBars.setGravity(Gravity.BOTTOM);
        if(weatherLoading) renderHomeRainBars(rainBars,null,true); else rainBars.setVisibility(View.GONE);
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
                                    rainBars.removeAllViews();rainBars.setVisibility(View.GONE);
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

    // ------------------------------------------------------------------
    // Ana Sayfa · Zeytin Ligi kartı (HomeLeagueModel kuralları)
    // ------------------------------------------------------------------
    private static final long HOME_LEAGUE_CACHE_MS = 10 * 60 * 1000L;
    private HomeLeagueModel.Input homeLeagueCache;
    private String homeLeagueCacheKey = "";
    private long homeLeagueCacheAt;
    private int homeLeagueSerial;

    private String homeLeagueHiddenKey(String uid) { return "home_league_card_hidden_" + uid; }

    /** Kart yüklenene kadar görünmez; bölge bilinmiyorsa ya da gizlendiyse hiç görünmez. */
    private FrameLayout homeLeagueHost;
    private String homeLeagueHostUid = "";
    private boolean homeLeagueLoading;
    /** Davet paylaşım penceresi açıldı; dönüşteki onResume Ana Sayfa'yı yeniden kurmasın. */
    private boolean skipNextHomeRefresh;
    private String homeLeagueLoadingKey = "";
    private String homeLeagueShownSignature = "";

    private String homeLeagueLastKey(String key) { return "home_league_last_" + key; }

    /**
     * Atlamasız kart: yükseklik, sayfa çizilmeden ÖNCE belli olur. Önce bellekteki/kayıtlı son
     * durum, o da yoksa yerel arşiv (eşzamanlı, küçük bir SQLite okuması) gösterilir. Ağ sonucu
     * gelince kart yalnızca içeriği gerçekten değiştiyse yerinde güncellenir. Ana Sayfa birkaç kez
     * yeniden kurulsa da (onResume, gönderi/özel veri senkronu) tek bir ağ isteği yürür.
     */
    private View buildHomeLeagueCard() {
        final FrameLayout host = new FrameLayout(this);
        host.setVisibility(View.GONE);
        homeLeagueHost = null;
        final int year = db.getActiveSeasonYear();
        final String uid = db.getActiveOwnerUid() == null ? "" : db.getActiveOwnerUid();
        if (uid.isEmpty() || "1".equals(db.getSetting(homeLeagueHiddenKey(uid), "0"))) return host;
        final String key = uid + "_" + year;
        homeLeagueHost = host; homeLeagueHostUid = uid;

        HomeLeagueModel.Input initial = null;
        if (homeLeagueCache != null && key.equals(homeLeagueCacheKey)) initial = homeLeagueCache;
        if (initial == null) initial = HomeLeagueModel.decode(db.getSetting(homeLeagueLastKey(key), ""));
        ZeytinLeagueRepository.Result local = null;
        if (initial == null) {
            try {
                local = new ZeytinLeagueRepository().local(db, year, uid);
                initial = homeLeagueInput(null, local);
            } catch (RuntimeException e) {
                return host; // Yerel arşiv okunamadı: kart gösterilmez.
            }
        }
        homeLeagueShownSignature = "";
        renderHomeLeagueCard(host, initial, uid);

        boolean fresh = homeLeagueCache != null && key.equals(homeLeagueCacheKey)
                && System.currentTimeMillis() - homeLeagueCacheAt < HOME_LEAGUE_CACHE_MS;
        if (fresh || (homeLeagueLoading && key.equals(homeLeagueLoadingKey))) return host;
        startHomeLeagueLoad(year, uid, key, local);
        return host;
    }

    private void startHomeLeagueLoad(int year, String uid, String key, ZeytinLeagueRepository.Result knownLocal) {
        final int serial = ++homeLeagueSerial;
        homeLeagueLoading = true; homeLeagueLoadingKey = key;
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            ZeytinLeagueRepository.Result local = knownLocal;
            if (local == null) {
                DatabaseHelper helper = new DatabaseHelper(app);
                try {
                    local = new ZeytinLeagueRepository().local(helper, year, uid);
                } catch (RuntimeException e) {
                    runOnUiThread(() -> { if (serial == homeLeagueSerial) homeLeagueLoading = false; });
                    return;
                } finally {
                    helper.close();
                }
            }
            final ZeytinLeagueRepository.Result finalLocal = local;
            runOnUiThread(() -> {
                if (serial != homeLeagueSerial || isFinishing()) return;
                new ZeytinLeagueRepository().published(year, uid, finalLocal, result -> {
                    if (serial != homeLeagueSerial || isFinishing()) return;
                    final HomeLeagueModel.Input in = homeLeagueInput(result, finalLocal);
                    if (in.published && in.myRank > 0 || in.raceStarted) { finishHomeLeague(in, key, uid, serial); return; }
                    MarketCloudRepository cloud = new MarketCloudRepository(db, uid);
                    if (!cloud.canUseCloud()) { finishHomeLeague(in, key, uid, serial); return; }
                    final String ownRegion = finalLocal.local == null ? "" : finalLocal.local.region;
                    cloud.loadLeagueRegions(year, (regions, error) -> {
                        if (error == null && regions != null) {
                            for (MarketCloudRepository.LeagueRegionStat r : regions) {
                                if (r != null && r.id.equals(ownRegion)) in.regionProducers = r.producerCount;
                            }
                        }
                        finishHomeLeague(in, key, uid, serial);
                    });
                });
            });
        }, "home-league").start();
    }

    private void finishHomeLeague(HomeLeagueModel.Input in, String key, String uid, int serial) {
        if (serial != homeLeagueSerial || isFinishing()) return;
        homeLeagueLoading = false;
        homeLeagueCache = in; homeLeagueCacheKey = key; homeLeagueCacheAt = System.currentTimeMillis();
        db.setSetting(homeLeagueLastKey(key), HomeLeagueModel.encode(in));
        // Ana Sayfa bu sırada yeniden kurulduysa en güncel karta yaz; içerik aynıysa dokunma.
        FrameLayout host = homeLeagueHost;
        if (host != null && uid.equals(homeLeagueHostUid)) renderHomeLeagueCard(host, in, uid);
    }

    /** Yayın sonucu + yerel önizlemeden yalnızca kullanıcının kendi durumunu çıkarır. */
    private HomeLeagueModel.Input homeLeagueInput(ZeytinLeagueRepository.Result result, ZeytinLeagueRepository.Result local) {
        HomeLeagueModel.Input in = new HomeLeagueModel.Input();
        ZeytinLeagueScore.Producer me = local.local;
        in.localKg = me == null ? 0 : me.kg;
        String regionId = me == null ? "" : me.region;
        boolean fresh = result != null && result.published
                && ZeytinLeagueRepository.fresh(result.publishedAtMs, System.currentTimeMillis());
        in.published = fresh;
        if (fresh && result.mine != null) { regionId = result.mine.region; in.myRank = result.mine.rank; in.myScore = result.mine.score; }
        ZeytinLeagueRepository.Result source = fresh ? result : local;
        for (ZeytinLeagueRepository.Region r : source.regions) {
            if (r == null || r.id == null || !r.id.equals(regionId)) continue;
            if (r.name != null) in.regionName = r.name;
            if (fresh && !r.top.isEmpty()) {
                in.raceStarted = true;
                in.topScores = new double[r.top.size()];
                for (int i = 0; i < r.top.size(); i++) in.topScores[i] = r.top.get(i).score;
            }
        }
        if (in.regionName.isEmpty()) {
            for (ZeytinLeagueRepository.Region r : local.regions) if (r != null && r.name != null) in.regionName = r.name;
        }
        return in;
    }

    private void renderHomeLeagueCard(FrameLayout host, HomeLeagueModel.Input in, String uid) {
        final HomeLeagueModel.Card c = HomeLeagueModel.build(in);
        String signature = HomeLeagueModel.signature(c);
        if (host.getChildCount() > 0 && signature.equals(homeLeagueShownSignature)) return; // değişiklik yok
        homeLeagueShownSignature = signature;
        host.removeAllViews();
        if (c == null) { host.setVisibility(View.GONE); return; }
        int gold = GreenTheme.color(R.color.zd_warning);
        int bronze = 0xffc89470;
        int cupColor = c.state == HomeLeagueModel.State.LEADER ? gold
                : c.state == HomeLeagueModel.State.RANKED ? (in.myRank <= 3 ? bronze : GREEN) : 0xff5e767c;
        boolean strong = c.state == HomeLeagueModel.State.LEADER || c.state == HomeLeagueModel.State.RANKED;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(strong ? GREEN_SOFT : CARD);
        bg.setStroke(dp(1), c.state == HomeLeagueModel.State.LEADER ? gold : strong ? GREEN : BORDER);
        card.setBackground(bg);
        card.setMinimumHeight(dp(64));

        LinearLayout line = row();
        FrameLayout cup = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(GreenTheme.color(R.color.zd_bg));
        if (strong) ring.setStroke(dp(1), cupColor); else ring.setStroke(dp(1), cupColor, dp(3), dp(3));
        cup.setBackground(ring);
        ImageView cupIcon = new ImageView(this);
        cupIcon.setImageResource(R.drawable.ic_league_nav);
        cupIcon.setColorFilter(cupColor);
        cupIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cup.addView(cupIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        line.addView(cup, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(c.overline, 11, c.state == HomeLeagueModel.State.LEADER ? gold : strong ? GREEN : MUTED, true));
        TextView title = text(c.title, 15, TEXT, true);
        title.setPadding(0, dp(2), 0, 0);
        copy.addView(title);
        TextView sub = text(c.sub, 12, GreenTheme.color(R.color.zd_text_soft), false);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        if (c.invite) {
            TextView invite = text("Davet et", 13, GreenTheme.color(R.color.zd_bg), true);
            invite.setGravity(Gravity.CENTER);
            invite.setPadding(dp(14), 0, dp(14), 0);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(20)); pill.setColor(GREEN);
            invite.setBackground(pill);
            invite.setContentDescription("Bölgendeki üreticileri davet et");
            invite.setOnClickListener(v -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, HomeLeagueModel.inviteText(in.regionName));
                skipNextHomeRefresh = true;
                startActivity(Intent.createChooser(share, "Davet et"));
            });
            line.addView(invite, new LinearLayout.LayoutParams(-2, dp(40)));
        } else {
            TextView chevron = text("›", 22, MUTED, false);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            line.addView(chevron);
        }
        card.addView(line);

        if (c.progressText != null && !c.progressText.isEmpty()) {
            if (c.progressPercent >= 0) {
                FrameLayout track = new FrameLayout(this);
                GradientDrawable trackBg = new GradientDrawable();
                trackBg.setCornerRadius(dp(3)); trackBg.setColor(GreenTheme.color(R.color.zd_bg));
                track.setBackground(trackBg);
                View fill = new View(this);
                GradientDrawable fillBg = new GradientDrawable();
                fillBg.setCornerRadius(dp(3)); fillBg.setColor(MUTED);
                fill.setBackground(fillBg);
                track.addView(fill, new FrameLayout.LayoutParams(0, -1));
                final int percent = c.progressPercent;
                track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    int w = Math.round((r - l) * percent / 100f);
                    if (fill.getLayoutParams().width != w) { fill.getLayoutParams().width = w; fill.requestLayout(); }
                });
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(6));
                tp.topMargin = dp(10);
                card.addView(track, tp);
            }
            TextView progress = text(c.progressText, 11, MUTED, false);
            progress.setPadding(0, dp(4), 0, 0);
            card.addView(progress);
        }
        if (c.checklist) {
            LinearLayout chips = row();
            chips.setPadding(0, dp(10), 0, 0);
            chips.addView(homeLeagueChip(c.kgOk ? "✓ En az 100 kg" : "· En az 100 kg", c.kgOk));
            View gap = new View(this);
            chips.addView(gap, new LinearLayout.LayoutParams(dp(6), 1));
            chips.addView(homeLeagueChip("· Paylaşılan satış", false));
            card.addView(chips);
        }

        card.setContentDescription(c.accessibility + " Zeytin Ligi'ni açmak için dokun. Gizlemek için basılı tut.");
        card.setOnClickListener(v -> showSection("REPORTS"));
        card.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Zeytin Ligi kartı")
                    .setMessage("Bu kartı Ana Sayfa'dan gizlemek ister misin? Zeytin Ligi ekranından yeniden açabilirsin.")
                    .setPositiveButton("Gizle", (d, w) -> {
                        db.setSetting(homeLeagueHiddenKey(uid), "1");
                        host.removeAllViews();
                        host.setVisibility(View.GONE);
                    })
                    .setNegativeButton("Vazgeç", null)
                    .show();
            return true;
        });
        host.addView(card, new FrameLayout.LayoutParams(-1, -2));
        host.setVisibility(View.VISIBLE);
    }

    private TextView homeLeagueChip(String label, boolean ok) {
        TextView chip = text(label, 11, ok ? GREEN : GreenTheme.color(R.color.zd_text_soft), true);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        if (ok) d.setStroke(dp(1), GREEN); else d.setStroke(dp(1), BORDER, dp(3), dp(3));
        chip.setBackground(d);
        return chip;
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
        for(int i=0;i<items.size();i++){
            HomeDashboardModel.Condition c=items.get(i);
            int tone=c.tone==HomeDashboardModel.Tone.CRITICAL ? RED
                    : c.tone==HomeDashboardModel.Tone.ATTENTION ? gold
                    : c.tone==HomeDashboardModel.Tone.INFO ? GreenTheme.parse("#8FC0E0") : GREEN;
            addHomeConditionRow(container,c,tone,i);
        }
    }

    /** Tahmin gelene kadar üç yer tutucu satır (Yağış · Rüzgâr · üçüncü satır). */
    private void renderHomeConditionPlaceholders(LinearLayout container) {
        container.removeAllViews();
        String[] titles={"Yağış","Rüzgâr","En kuru gün"};
        for(int i=0;i<titles.length;i++){
            HomeDashboardModel.Condition c=new HomeDashboardModel.Condition(titles[i],"…",
                    "Bahçenin hava tahmini hazırlanıyor",HomeDashboardModel.Tone.NORMAL);
            addHomeConditionRow(container,c,BORDER,i);
        }
    }

    private void addHomeConditionRow(LinearLayout container,HomeDashboardModel.Condition c,int tone,int i) {
            int inner=GreenTheme.color(R.color.zd_surface);
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

    private String homeTemp(double value) {
        return Double.isNaN(value) ? "—" : Math.round(value)+"°";
    }

    private void renderHomeRainBars(LinearLayout container,WeatherRepository.WeatherForecast forecast) {
        renderHomeRainBars(container,forecast,false);
    }

    /** placeholder=true: aynı yükseklikte boş 5 sütun (tahmin gelince kart büyümez). */
    private void renderHomeRainBars(LinearLayout container,WeatherRepository.WeatherForecast forecast,boolean placeholder) {
        container.removeAllViews();
        if(placeholder){
            container.setVisibility(View.VISIBLE);
            int inner=GreenTheme.color(R.color.zd_surface);
            for(int i=0;i<5;i++){
                LinearLayout column=new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setGravity(Gravity.CENTER_HORIZONTAL);
                column.addView(text("—",10,MUTED,true));
                FrameLayout well=new FrameLayout(this);
                well.setBackground(round(inner,8,Color.TRANSPARENT));
                LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(40));
                wp.setMargins(0,dp(4),0,dp(4));
                column.addView(well,wp);
                column.addView(text("—",11,MUTED,true));
                column.addView(text("—/—",10,MUTED,false));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
                if(i>0)cp.setMargins(dp(6),0,0,0);
                container.addView(column,cp);
            }
            container.setContentDescription("Günlük yağış ihtimali yükleniyor");
            return;
        }
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


    private double homeSeasonElekKg() {
        String uid = db.getActiveOwnerUid() == null ? "" : db.getActiveOwnerUid();
        Cursor c = null;
        try {
            c = new ElekRepository(db).getArchiveSummary(db.getActiveSeasonYear(), uid);
            return c != null && c.moveToFirst() ? getDouble(c, "total_kg") : 0;
        } catch (RuntimeException e) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Kullanıcının kendi elek kayıtlarından sınıf sınıf kg, tutar ve kayıt sayısı (bu sezon, bu hesap). */
    private List<HomeSummaryModel.GradePrice> loadOwnGradePrices() {
        List<HomeSummaryModel.GradePrice> out = new ArrayList<>();
        for (int g = 0; g < HomeSummaryModel.GRADE_CODES.length; g++) {
            HomeSummaryModel.GradePrice item = new HomeSummaryModel.GradePrice();
            item.label = HomeSummaryModel.GRADE_LABELS[g];
            java.util.Set<Long> records = new java.util.HashSet<>();
            Cursor c = null;
            try {
                c = marketRepository.getScopedOwnSalesByGrade(HomeSummaryModel.GRADE_CODES[g]);
                while (c != null && c.moveToNext()) {
                    double kg = getDouble(c, "kg");
                    item.kg += kg;
                    item.revenue += kg * getDouble(c, "price_per_kg");
                    records.add((long) getInt(c, "sale_id"));
                }
            } catch (RuntimeException ignored) {
            } finally {
                if (c != null) c.close();
            }
            item.records = records.size();
            out.add(item);
        }
        return out;
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


    private static class HomeGardenSnapshot {
        long id;
        String name = "Bahçe";
        String photoUri = "";
        String province = "";
        String district = "";
        String neighborhood = "";
    }


    // =========================================================
    // BAHÇELER
    // =========================================================

    // =========================================================
    // BAHÇELERİM (kabul edilen tasarım, 13.10.2026)
    // Sezon özeti, kayıt uyarısı, sıralama ve bahçe kartları.
    // Hesaplamalar GardenListModel'dedir (host testli).
    // =========================================================

    private static final String GARDENS_SORT_SETTING = "gardens_sort";

    private View buildGardensSection() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);

        final Calendar now = Calendar.getInstance();
        List<GardenListModel.Item> gardens = new ArrayList<>();
        Cursor c = null;
        try {
            c = gardenRepository.getGardenSummaries();
            while (c != null && c.moveToNext()) {
                GardenListModel.Item g = new GardenListModel.Item();
                g.id = getLong(c, "id");
                g.name = getString(c, "name", "Bahçe");
                g.variety = getString(c, "variety", "");
                g.trees = getInt(c, "tree_count");
                g.age = getInt(c, "age");
                g.lastOperation = getString(c, "last_operation", "");
                g.lastOperationDate = getString(c, "last_operation_date", "");
                g.lastHarvestDate = getString(c, "last_harvest_date", "");
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
        boolean activeExists = false;
        for (GardenListModel.Item g : gardens) activeExists |= g.id == activeId;
        if (!activeExists) {
            activeId = 0;
            List<GardenListModel.Item> byName = GardenListModel.sort(gardens, GardenListModel.Sort.NAME, 0, now);
            if (!byName.isEmpty()) activeId = byName.get(0).id;
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

        if (gardens.isEmpty()) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
            ep.setMargins(0, dp(16), 0, 0);
            body.addView(emptyCard(
                    "Henüz bahçe yok",
                    "İlk bahçeni ekleyerek Zeytin Defteri'ni kullanmaya başla."), ep);
            body.addView(gardensRemindersRow(), gardensBlockParams(12));
            return scroll;
        }

        body.addView(gardensSummaryCard(gardens), gardensBlockParams(16));

        int stale = GardenListModel.staleCount(gardens, now);
        if (stale > 0) {
            body.addView(gardensStaleRow(stale), gardensBlockParams(12));
        }

        body.addView(gardensRemindersRow(), gardensBlockParams(12));

        GardenListModel.Sort sort = GardenListModel.parseSort(db.getSetting(GARDENS_SORT_SETTING, "ACTIVE"));
        body.addView(gardensSortChips(sort), gardensBlockParams(16));

        List<GardenListModel.Item> ordered = GardenListModel.sort(gardens, sort, activeId, now);
        for (int i = 0; i < ordered.size(); i++) {
            GardenListModel.Item g = ordered.get(i);
            View card = gardenCard(g, g.id == activeId, now);
            body.addView(card, gardensBlockParams(i == 0 ? 12 : 10));
        }
        return scroll;
    }

    private LinearLayout.LayoutParams gardensBlockParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private void refreshGardensKeepingScroll() {
        int y = 0;
        if (contentHost != null && contentHost.getChildCount() > 0
                && contentHost.getChildAt(0) instanceof ScrollView) {
            y = contentHost.getChildAt(0).getScrollY();
        }
        refreshCurrentSection();
        final int target = y;
        if (contentHost != null && contentHost.getChildCount() > 0
                && contentHost.getChildAt(0) instanceof ScrollView) {
            final ScrollView s = (ScrollView) contentHost.getChildAt(0);
            s.post(() -> s.scrollTo(0, target));
        }
    }

    private View gardensSummaryCard(List<GardenListModel.Item> gardens) {
        int gold = GreenTheme.color(R.color.zd_warning);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(16));
        card.setBackground(round(CARD, 20, BORDER));

        LinearLayout head = row();
        TextView label = text("SEZON ÖZETİ", 11, MUTED, true);
        label.setLetterSpacing(0.08f);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView report = text("Rapor  ›", 13, GREEN, true);
        report.setGravity(Gravity.CENTER_VERTICAL);
        report.setMinHeight(dp(44));
        report.setPadding(dp(8), 0, 0, 0);
        report.setContentDescription("Sezon raporunu aç");
        report.setOnClickListener(v -> startActivity(new Intent(this, GardenAnalyticsActivity.class)));
        head.addView(report, new LinearLayout.LayoutParams(-2, -2));
        card.addView(head);

        double harvest = GardenListModel.totalHarvest(gardens);
        double cost = GardenListModel.totalCost(gardens);
        double perKg = GardenListModel.costPerKg(cost, harvest);
        card.addView(text("Kilogram başına maliyet", 12, MUTED, false));
        TextView value = text(GardenListModel.formatCostPerKg(perKg), 28, Double.isNaN(perKg) ? TEXT : gold, true);
        value.setPadding(0, dp(2), 0, 0);
        card.addView(value);
        TextView note = text(Double.isNaN(perKg)
                        ? "Hasat ve masraf kaydı girildikçe hesaplanır."
                        : "Bu sezonun masrafı ÷ hasadı",
                12, MUTED, false);
        note.setPadding(0, dp(2), 0, 0);
        card.addView(note);

        LinearLayout stats = row();
        stats.setGravity(Gravity.TOP);
        int trees = GardenListModel.totalTrees(gardens);
        stats.addView(gardenStat("Ağaç", trees > 0 ? String.valueOf(trees) : "—", TEXT),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Hasat", harvest > 0 ? fmtKg(harvest) : "—", TEXT), second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, -2, 1);
        third.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Masraf", cost > 0 ? fmtTl(cost) : "—", TEXT), third);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(12), 0, 0);
        card.addView(stats, sp);

        card.setContentDescription("Sezon özeti. Kilogram başına maliyet "
                + GardenListModel.formatCostPerKg(perKg) + ". Ağaç " + (trees > 0 ? trees : "yok")
                + ", hasat " + (harvest > 0 ? fmtKg(harvest) : "yok")
                + ", masraf " + (cost > 0 ? fmtTl(cost) : "yok") + ".");
        return card;
    }

    private View gardensStaleRow(int count) {
        int gold = GreenTheme.color(R.color.zd_warning);
        LinearLayout row = row();
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setMinimumHeight(dp(52));
        row.setBackground(round(GreenTheme.color(R.color.zd_surface), 16, (gold & 0x00FFFFFF) | 0x66000000));
        row.setClickable(true);
        row.setFocusable(true);
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(gold);
        dot.setBackground(shape);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMargins(0, 0, dp(12), 0);
        row.addView(dot, dotParams);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(GardenListModel.staleText(count), 14, TEXT, true));
        TextView sub = text(GardenTodayAdvisor.RECORD_REVIEW_DAYS + " günden uzun süredir kayıt girilmemiş", 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("›", 22, gold, true), new LinearLayout.LayoutParams(-2, -2));
        row.setContentDescription(GardenListModel.staleText(count) + ". Bu bahçeleri öne almak için dokun.");
        row.setOnClickListener(v -> {
            db.setSetting(GARDENS_SORT_SETTING, GardenListModel.Sort.STALE.name());
            refreshGardensKeepingScroll();
        });
        return row;
    }

    private View gardensRemindersRow() {
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
        return reminders;
    }

    private View gardensSortChips(GardenListModel.Sort selected) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        GardenListModel.Sort[] sorts = {
                GardenListModel.Sort.ACTIVE, GardenListModel.Sort.NAME,
                GardenListModel.Sort.YIELD, GardenListModel.Sort.STALE};
        String[] labels = {"Aktif önce", "Ada göre", "Verime göre", "Kayıt bekleyen"};
        for (int i = 0; i < sorts.length; i++) {
            final GardenListModel.Sort sort = sorts[i];
            boolean on = sort == selected;
            TextView chip = text(labels[i], 12, on ? GREEN : MUTED, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackground(round(on ? GREEN_SOFT : Color.TRANSPARENT, 20, on ? GREEN : BORDER));
            chip.setSelected(on);
            chip.setContentDescription("Sırala: " + labels[i] + (on ? ", seçili" : ""));
            chip.setOnClickListener(v -> {
                if (sort == selected) return;
                db.setSetting(GARDENS_SORT_SETTING, sort.name());
                refreshGardensKeepingScroll();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) cp.setMargins(dp(8), 0, 0, 0);
            chips.addView(chip, cp);
        }
        scroller.addView(chips);
        return scroller;
    }

    private View gardenCard(final GardenListModel.Item g, boolean active, Calendar now) {
        int gold = GreenTheme.color(R.color.zd_warning);
        int inner = GreenTheme.color(R.color.zd_surface);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        card.setBackground(round(CARD, 20, active ? GreenTheme.color(R.color.zd_green_chip) : BORDER));
        card.setClickable(true);
        card.setFocusable(true);

        // Üst satır: görsel, ad + meta, (aktif bahçede) hava
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
        LinearLayout nameRow = row();
        TextView name = text(g.name, 17, TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (active) {
            TextView badge = text("AKTİF", 10, gold, true);
            badge.setLetterSpacing(0.08f);
            badge.setPadding(dp(7), dp(3), dp(7), dp(3));
            badge.setBackground(round(inner, 8, Color.TRANSPARENT));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.setMargins(dp(6), 0, 0, 0);
            nameRow.addView(badge, bp);
        }
        info.addView(nameRow);
        TextView meta = text(HomeDashboardModel.gardenMeta(g.variety, g.trees, g.age), 13, MUTED, false);
        meta.setPadding(0, dp(3), 0, 0);
        info.addView(meta);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        String weatherText = "";
        if (active && homeWeather != null && homeWeather.today != null
                && homeWeather.today.garden != null && homeWeather.today.garden.id == g.id
                && homeWeather.current != null) {
            WeatherRepository.WeatherSnapshot w = homeWeather.current;
            LinearLayout weather = new LinearLayout(this);
            weather.setOrientation(LinearLayout.VERTICAL);
            weather.setGravity(Gravity.RIGHT);
            weather.addView(text(Math.round(w.temperatureC) + "°", 15, TEXT, true));
            double rain = w.precipitationProbability;
            if (Double.isNaN(rain) && homeWeather.forecast != null && !homeWeather.forecast.daily.isEmpty()) {
                rain = homeWeather.forecast.daily.get(0).precipitationProbability;
            }
            if (!Double.isNaN(rain)) {
                weather.addView(text("Yağış %" + Math.round(rain), 11, GreenTheme.parse("#8FC0E0"), true));
            }
            weatherText = " Şimdi " + Math.round(w.temperatureC) + " derece"
                    + (Double.isNaN(rain) ? "." : ", yağış ihtimali yüzde " + Math.round(rain) + ".");
            top.addView(weather, new LinearLayout.LayoutParams(-2, -2));
        }
        card.addView(top);

        // Göstergeler
        double yield = GardenListModel.yieldPerTree(g.harvestKg, g.trees);
        LinearLayout stats = row();
        stats.setGravity(Gravity.TOP);
        stats.addView(gardenStat("Hasat", g.harvestKg > 0 ? fmtKg(g.harvestKg) : "—", TEXT),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Verim", GardenListModel.formatYield(yield), Double.isNaN(yield) ? TEXT : GREEN), second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, -2, 1);
        third.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Masraf", g.cost > 0 ? fmtTl(g.cost) : "—", TEXT), third);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.setMargins(0, dp(12), 0, 0);
        card.addView(stats, statsParams);

        // Alt satır: son kayıt, Ana Sayfa'da göster, + İşlem
        GardenListModel.Activity activity = GardenListModel.lastActivity(g, now);
        boolean stale = GardenListModel.isStale(g, now);
        LinearLayout bottom = row();
        LinearLayout last = new LinearLayout(this);
        last.setOrientation(LinearLayout.VERTICAL);
        last.addView(text("Son kayıt", 11, MUTED, false));
        LinearLayout lastRow = row();
        TextView lastValue = text(activity.label, 13, TEXT, true);
        lastValue.setSingleLine(true);
        lastValue.setEllipsize(TextUtils.TruncateAt.END);
        lastRow.addView(lastValue, new LinearLayout.LayoutParams(-2, -2));
        if (stale) {
            TextView waiting = text("Kayıt bekliyor", 10, BG, true);
            waiting.setPadding(dp(6), dp(2), dp(6), dp(2));
            waiting.setBackground(round(gold, 6, Color.TRANSPARENT));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.setMargins(dp(6), 0, 0, 0);
            lastRow.addView(waiting, wp);
        }
        last.addView(lastRow);
        bottom.addView(last, new LinearLayout.LayoutParams(0, -2, 1));

        if (!active) {
            TextView show = text("Ana Sayfa'da göster", 12, MUTED, true);
            show.setGravity(Gravity.CENTER);
            show.setMinHeight(dp(44));
            show.setPadding(dp(8), 0, dp(8), 0);
            show.setContentDescription(g.name + " bahçesini Ana Sayfa'da göster");
            show.setOnClickListener(v -> {
                db.setSetting(homeActiveGardenSettingKey(), String.valueOf(g.id));
                Toast.makeText(this, "Ana Sayfa'da artık " + g.name + " gösteriliyor.", Toast.LENGTH_SHORT).show();
                refreshGardensKeepingScroll();
            });
            bottom.addView(show, new LinearLayout.LayoutParams(-2, -2));
        }

        TextView addOperation = text("+ İşlem", 13, GREEN, true);
        addOperation.setGravity(Gravity.CENTER);
        addOperation.setMinHeight(dp(40));
        addOperation.setPadding(dp(14), 0, dp(14), 0);
        addOperation.setBackground(round(GREEN_SOFT, 20, GreenTheme.color(R.color.zd_green_chip)));
        addOperation.setContentDescription(g.name + " için işlem ekle");
        addOperation.setOnClickListener(v -> showGardenOperationMenu(g));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.setMargins(dp(4), 0, 0, 0);
        bottom.addView(addOperation, ap);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
        bottomParams.setMargins(0, dp(10), 0, 0);
        card.addView(bottom, bottomParams);

        card.setContentDescription(g.name + ". " + HomeDashboardModel.gardenMeta(g.variety, g.trees, g.age)
                + ". Bu sezon hasat " + (g.harvestKg > 0 ? fmtKg(g.harvestKg) : "yok")
                + ", verim " + GardenListModel.formatYield(yield)
                + ", masraf " + (g.cost > 0 ? fmtTl(g.cost) : "yok")
                + ". Son kayıt " + activity.label + (stale ? ", kayıt bekliyor" : "")
                + (active ? ". Ana Sayfa'da gösterilen bahçe." : ".") + weatherText + " Açmak için dokun.");
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, GardenDetailActivity.class);
            i.putExtra("garden_id", g.id);
            startActivity(i);
        });
        return card;
    }

    private void showGardenOperationMenu(final GardenListModel.Item g) {
        final String[] types = {"Sulama", "Gübreleme", "İlaçlama", "Hasat"};
        new AlertDialog.Builder(this)
                .setTitle(g.name + " · İşlem ekle")
                .setItems(types, (dialog, which) -> {
                    if ("Hasat".equals(types[which])) {
                        openHarvestForGarden(g.id);
                    } else {
                        openOperationForGarden(types[which], g.id);
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private View gardenStat(String label, String value, int valueColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(round(GreenTheme.color(R.color.zd_surface), 12, Color.TRANSPARENT));
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        box.addView(text(label, 11, MUTED, false));
        TextView v = text(value, 13, valueColor, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        return box;
    }
    // =========================================================
    // ELEK SEKMESİ (özet sayfası, alt menü görünür)
    // Rakamlar Geçmiş ekranıyla aynı sorgudan gelir (ElekRepository archive*).
    // Kayıt formu ElekActivity'de tam ekran açılır.
    // =========================================================

    private static final String ELEK_FILTER_SETTING = "elek_overview_garden";
    private static final String[] ELEK_GRADE_COLUMNS =
            {"double_kg", "grade1_kg", "grade2_kg", "grade3_kg", "grade4_kg", "oil_kg"};

    private static final class ElekRow {
        long id;
        String date = "", garden = "";
        double kg, revenue;
        final double[] grades = new double[6];
    }

    private int[] elekGradeColors() {
        return new int[]{
                GreenTheme.color(R.color.elek_badge_double),
                GreenTheme.color(R.color.elek_badge_one),
                GreenTheme.color(R.color.elek_badge_two),
                GreenTheme.color(R.color.elek_badge_three),
                GreenTheme.color(R.color.elek_badge_four),
                GreenTheme.color(R.color.elek_badge_oil)
        };
    }

    private View buildElekSection() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);

        final int season = db.getActiveSeasonYear();
        final String owner = db.getActiveOwnerUid();
        ElekRepository elekRepository = new ElekRepository(db);

        // Bahçeler (filtre çipleri için)
        List<long[]> gardenIds = new ArrayList<>();
        List<String> gardenNames = new ArrayList<>();
        Cursor gc = null;
        try {
            gc = gardenRepository.getGardenSummaries();
            while (gc != null && gc.moveToNext()) {
                gardenIds.add(new long[]{getLong(gc, "id")});
                gardenNames.add(getString(gc, "name", "Bahçe"));
            }
        } finally {
            if (gc != null) gc.close();
        }

        long filter = -1;
        try {
            filter = Long.parseLong(db.getSetting(ELEK_FILTER_SETTING, "-1"));
        } catch (Exception ignored) {
        }
        boolean filterExists = filter == -1;
        for (long[] id : gardenIds) filterExists |= id[0] == filter;
        if (!filterExists) filter = -1;

        // Kayıtlar (aynı arşiv sorgusu; tarih sırasına göre)
        List<ElekRow> rows = new ArrayList<>();
        Cursor rc = null;
        try {
            rc = elekRepository.getArchiveRecords(season, filter, owner);
            while (rc != null && rc.moveToNext()) {
                ElekRow r = new ElekRow();
                r.id = getLong(rc, "record_id");
                r.date = getString(rc, "record_date", "");
                r.garden = getString(rc, "garden_name", "-");
                r.kg = getDouble(rc, "total_kg");
                r.revenue = getDouble(rc, "total_revenue");
                for (int i = 0; i < ELEK_GRADE_COLUMNS.length; i++) r.grades[i] = getDouble(rc, ELEK_GRADE_COLUMNS[i]);
                rows.add(r);
            }
        } catch (Exception ignored) {
        } finally {
            if (rc != null) rc.close();
        }

        final long archiveFilter = filter;
        double totalKg = 0, totalRevenue = 0;
        double[] seasonGrades = new double[6];
        for (ElekRow r : rows) {
            totalKg += r.kg;
            totalRevenue += r.revenue;
            for (int i = 0; i < 6; i++) seasonGrades[i] += r.grades[i];
        }

        // Başlık
        LinearLayout header = row();
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView overline = text(season + " SEZONU · " + ElekInsights.recordCountOverline(rows.size()), 11, MUTED, true);
        overline.setLetterSpacing(0.08f);
        titles.addView(overline);
        TextView title = text("Elek", 24, TEXT, true);
        title.setPadding(0, dp(3), 0, 0);
        titles.addView(title);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView history = text("Geçmiş", 14, GREEN, true);
        history.setGravity(Gravity.CENTER);
        history.setMinHeight(dp(44));
        history.setPadding(dp(16), 0, dp(16), 0);
        history.setBackground(round(CARD, 22, BORDER));
        history.setContentDescription("Elek geçmişini aç");
        history.setOnClickListener(v -> startActivity(new Intent(this, ElekArchiveActivity.class)
                .putExtra(ElekArchiveActivity.EXTRA_GARDEN_ID, archiveFilter)));
        header.addView(history, new LinearLayout.LayoutParams(-2, -2));
        body.addView(header);

        // Yeni kayıt
        final long newRecordGarden = filter > 0 ? filter : homeActiveGardenIdOrZero();
        TextView create = text("+  Yeni elek kaydı", 16, BG, true);
        create.setGravity(Gravity.CENTER);
        create.setMinHeight(dp(56));
        create.setBackground(round(GREEN, 28, Color.TRANSPARENT));
        create.setContentDescription("Yeni elek kaydı oluştur");
        create.setOnClickListener(v -> startActivity(
                new Intent(this, ElekActivity.class).putExtra("garden_id", newRecordGarden)));
        body.addView(create, gardensBlockParams(16));

        // Tezgah Modu: tüccarın kantarında kasa kasa tartım (satış ya da tüccar alımı).
        TextView tezgah = text("Tezgah Modu  ·  kantarda kasa kasa", 15, GREEN, true);
        tezgah.setGravity(Gravity.CENTER);
        tezgah.setMinHeight(dp(50));
        tezgah.setBackground(round(Color.TRANSPARENT, 25, GREEN));
        tezgah.setContentDescription("Tezgah Modu'nu aç");
        tezgah.setOnClickListener(v -> startActivity(
                new Intent(this, TezgahActivity.class).putExtra(TezgahActivity.EXTRA_GARDEN_ID, newRecordGarden)));
        body.addView(tezgah, gardensBlockParams(10));

        // Bahçe filtresi
        if (gardenIds.size() > 1) {
            HorizontalScrollView scroller = new HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = row();
            for (int i = -1; i < gardenIds.size(); i++) {
                final long id = i < 0 ? -1 : gardenIds.get(i)[0];
                String label = i < 0 ? "Tüm bahçeler" : gardenNames.get(i);
                boolean on = id == filter;
                TextView chip = text(label, 12, on ? GREEN : MUTED, true);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(40));
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackground(round(on ? GREEN_SOFT : Color.TRANSPARENT, 20, on ? GREEN : BORDER));
                chip.setContentDescription("Filtre: " + label + (on ? ", seçili" : ""));
                final long current = filter;
                chip.setOnClickListener(v -> {
                    if (id == current) return;
                    db.setSetting(ELEK_FILTER_SETTING, String.valueOf(id));
                    refreshGardensKeepingScroll();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                if (i > -1) cp.setMargins(dp(8), 0, 0, 0);
                chips.addView(chip, cp);
            }
            scroller.addView(chips);
            body.addView(scroller, gardensBlockParams(16));
        }

        if (rows.isEmpty()) {
            body.addView(emptyCard(
                    filter > 0 ? "Bu bahçede henüz elek kaydı yok" : "Bu sezon henüz elek kaydı yok",
                    "İlk elek kaydını eklediğinde sezon toplamı ve sınıf dağılımı burada görünür."),
                    gardensBlockParams(12));
            return scroll;
        }

        // Sezon toplamı
        int gold = GreenTheme.color(R.color.zd_warning);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        card.setBackground(round(CARD, 20, BORDER));
        LinearLayout top = row();
        top.setGravity(Gravity.BOTTOM);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("SEZON TOPLAMI", 11, MUTED, true);
        label.setLetterSpacing(0.08f);
        left.addView(label);
        TextView revenue = text(fmtTl(totalRevenue), 28, gold, true);
        revenue.setPadding(0, dp(2), 0, 0);
        left.addView(revenue);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        right.addView(text(fmtKg(totalKg), 15, TEXT, true));
        TextView avg = text("ort. " + ElekInsights.formatPerKg(totalRevenue, totalKg), 12, MUTED, false);
        avg.setPadding(0, dp(2), 0, 0);
        right.addView(avg);
        top.addView(right, new LinearLayout.LayoutParams(-2, -2));
        card.addView(top);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(10));
        barParams.setMargins(0, dp(12), 0, 0);
        card.addView(elekDistributionBar(seasonGrades, 5), barParams);
        TextView legend = text(ElekInsights.legend(seasonGrades), 11, MUTED, false);
        legend.setPadding(0, dp(8), 0, 0);
        card.addView(legend);

        LinearLayout stats = row();
        stats.setGravity(Gravity.TOP);
        stats.addView(gardenStat("Kayıt", String.valueOf(rows.size()), TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("En çok çıkan", ElekInsights.topGrade(seasonGrades), TEXT), second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, -2, 1);
        third.setMargins(dp(8), 0, 0, 0);
        stats.addView(gardenStat("Son kayıt", ElekInsights.formatDate(rows.get(0).date, true), TEXT), third);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.setMargins(0, dp(12), 0, 0);
        card.addView(stats, statsParams);
        card.setContentDescription("Sezon toplamı " + fmtTl(totalRevenue) + ", " + fmtKg(totalKg)
                + ", ortalama " + ElekInsights.formatPerKg(totalRevenue, totalKg) + ". "
                + ElekInsights.accessibleLegend(seasonGrades) + " " + rows.size() + " kayıt.");
        body.addView(card, gardensBlockParams(12));

        // Son kayıtlar
        LinearLayout listHeader = row();
        TextView listTitle = text("Son kayıtlar", 18, TEXT, true);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = text("Tümünü gör  ›", 13, GREEN, true);
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setMinHeight(dp(44));
        all.setContentDescription("Tüm elek kayıtlarını aç");
        all.setOnClickListener(v -> startActivity(new Intent(this, ElekArchiveActivity.class)
                .putExtra(ElekArchiveActivity.EXTRA_GARDEN_ID, archiveFilter)));
        listHeader.addView(all, new LinearLayout.LayoutParams(-2, -2));
        body.addView(listHeader, gardensBlockParams(16));

        int shown = Math.min(5, rows.size());
        for (int i = 0; i < shown; i++) {
            body.addView(elekRecordRow(rows.get(i)), gardensBlockParams(i == 0 ? 4 : 10));
        }
        return scroll;
    }

    private long homeActiveGardenIdOrZero() {
        try {
            return Long.parseLong(db.getSetting(homeActiveGardenSettingKey(), "0"));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private View elekDistributionBar(double[] grades, int radiusDp) {
        LinearLayout bar = row();
        bar.setBackground(round(GreenTheme.color(R.color.zd_surface), radiusDp, Color.TRANSPARENT));
        bar.setClipToOutline(true);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int[] colors = elekGradeColors();
        boolean first = true;
        for (int i = 0; i < grades.length && i < colors.length; i++) {
            if (grades[i] <= 0) continue;
            View segment = new View(this);
            segment.setBackgroundColor(colors[i]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, (float) grades[i]);
            if (!first) p.setMargins(dp(2), 0, 0, 0);
            first = false;
            bar.addView(segment, p);
        }
        return bar;
    }

    private View elekRecordRow(ElekRow r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(round(CARD, 18, BORDER));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout top = row();
        top.setGravity(Gravity.TOP);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(ElekInsights.formatDate(r.date, false), 15, TEXT, true));
        TextView meta = text(r.garden + "  ·  " + fmtKg(r.kg), 12, MUTED, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(2), 0, 0);
        left.addView(meta);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        right.addView(text(fmtTl(r.revenue), 15, TEXT, true));
        TextView perKg = text(ElekInsights.formatPerKg(r.revenue, r.kg), 12, MUTED, false);
        perKg.setPadding(0, dp(2), 0, 0);
        right.addView(perKg);
        top.addView(right, new LinearLayout.LayoutParams(-2, -2));
        card.addView(top);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(6));
        barParams.setMargins(0, dp(10), 0, 0);
        card.addView(elekDistributionBar(r.grades, 3), barParams);
        card.setContentDescription(ElekInsights.formatDate(r.date, false) + ", " + r.garden + ", "
                + fmtKg(r.kg) + ", " + fmtTl(r.revenue) + ", ortalama " + ElekInsights.formatPerKg(r.revenue, r.kg)
                + ". " + ElekInsights.accessibleLegend(r.grades) + " Makbuzu açmak için dokun.");
        card.setOnClickListener(v -> startActivity(new Intent(this, ElekReceiptActivity.class)
                .putExtra(ElekReceiptActivity.EXTRA_RECORD_ID, r.id)));
        return card;
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
                        this::openSeasonReport
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

    // =========================================================
    // SEZON RAPORUM (Zeytin Ligi → "Kişisel raporlar ve işlem geçmişi")
    // Görünüm SeasonReportView'da, kurallar SeasonReportModel'de; burada yalnızca
    // mevcut sezon/sahip filtreli DatabaseHelper toplamları okunur.
    // =========================================================
    private boolean seasonReportOpen;
    private Object seasonReportBack;

    /**
     * Sezon Raporum açıkken geri tuşu uygulamadan çıkmasın, Zeytin Ligi'ne dönsün.
     * Android 13+ için geri dinleyicisi yalnızca rapor açıkken kayıtlıdır (BackCompat);
     * Android 12 ve altında onBackPressed aynı işi yapar.
     */
    private void setSeasonReportOpen(boolean open) {
        seasonReportOpen = open;
        if (open && seasonReportBack == null) {
            seasonReportBack = BackCompat.register(this, () -> showSection("REPORTS"));
        } else if (!open && seasonReportBack != null) {
            BackCompat.unregister(this, seasonReportBack);
            seasonReportBack = null;
        }
    }

    private void openSeasonReport() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        contentHost.addView(new SeasonReportView(this, loadSeasonReportData(), new SeasonReportView.Actions() {
            @Override public void back() { showSection("REPORTS"); }
            @Override public void addElek() { showSection("ELEK"); }
            @Override public void convertElek() { showElekToSaleDialog(); }
            @Override public void detailedReport() { startActivity(new Intent(ProducerFeedActivity.this, ReportActivity.class)); }
            @Override public void operationHistory() { startActivity(new Intent(ProducerFeedActivity.this, OperationHistoryActivity.class)); }
            @Override public void seasonPicker() { showSeasonPicker(); }
        }), match());
        setSeasonReportOpen(true);
    }

    /** Sezon Raporum → satışı olmayan elekleri seçip toplu satışa dönüştür (tahsilat: henüz değil). */
    private void showElekToSaleDialog() {
        final ElekSaleBridge bridge = new ElekSaleBridge(db);
        final java.util.List<ElekSaleBridge.Pending> pending = bridge.pending(db.getActiveSeasonYear());
        if (pending.isEmpty()) {
            Toast.makeText(this, "Satışa dönüştürülecek elek kaydı yok.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[pending.size()];
        final boolean[] checked = new boolean[pending.size()];
        for (int i = 0; i < labels.length; i++) {
            ElekSaleBridge.Pending p = pending.get(i);
            labels[i] = p.garden + " · " + ElekInsights.formatDate(p.date, true) + " · "
                    + SeasonReportModel.kg(p.kg) + " · " + SeasonReportModel.tl(p.total);
        }
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Sattığın elekleri işaretle")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    checked[which] = isChecked;
                    int count = 0; for (boolean c : checked) if (c) count++;
                    android.widget.Button b = ((android.app.AlertDialog) d).getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                    if (b != null) { b.setText(ElekSaleModel.bulkButton(count)); b.setEnabled(count > 0); }
                })
                .setPositiveButton(ElekSaleModel.bulkButton(0), (d, w) -> {
                    int created = 0, failed = 0;
                    String uid = db.getActiveOwnerUid() == null ? "" : db.getActiveOwnerUid();
                    for (int i = 0; i < checked.length; i++) {
                        if (!checked[i]) continue;
                        long saleId = bridge.createSale(pending.get(i).elekId, ElekSaleModel.PAY_NONE, 0, "");
                        if (saleId > 0) {
                            created++;
                            try {
                                com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                                if (user != null && user.getUid().equals(uid)) new ProductionCloudRepository(db).syncSaleByLocalId(uid, saleId);
                            } catch (Exception ignored) { }
                        } else if (saleId != ElekSaleBridge.ALREADY_CONVERTED) failed++;
                    }
                    Toast.makeText(this, created + " satış oluşturuldu" + (failed > 0 ? ", " + failed + " elek dönüştürülemedi" : "")
                            + ". Tahsilatı satış kayıtlarından güncelleyebilirsin.", Toast.LENGTH_LONG).show();
                    openSeasonReport();
                })
                .setNegativeButton("Vazgeç", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false));
        dialog.show();
    }

    private SeasonReportModel.Data loadSeasonReportData() {
        SeasonReportModel.Data d = new SeasonReportModel.Data();
        d.season = db.getActiveSeasonYear();
        d.expenses = db.getTotalExpenses();
        d.harvestKg = db.getTotalHarvestKg();
        d.salesRevenue = db.getTotalSalesRevenue();
        d.soldKg = db.getTotalSoldKg();
        d.realSaleCount = db.getRealSaleCount();
        d.received = db.getTotalAmountReceived();
        d.openReceivable = db.getTotalOpenReceivableBalance();
        d.brineInKg = db.getTotalBrineOliveKg();
        d.brineReadyKg = db.getTotalReadyBrineKg();
        d.brineCost = db.getTotalBrineCost();
        d.brineBatches = db.getActiveBrineBatchCount();
        String uid = db.getActiveOwnerUid() == null ? "" : db.getActiveOwnerUid();
        java.util.Map<String, Double> elekByGarden = new java.util.HashMap<>();
        Cursor elek = null;
        try {
            elek = new ElekRepository(db).getArchiveRecords(d.season, -1, uid);
            while (elek != null && elek.moveToNext()) {
                double kg = getDouble(elek, "total_kg");
                d.elekKg += kg;
                d.elekRevenue += getDouble(elek, "total_revenue");
                String name = getString(elek, "garden_name", "");
                elekByGarden.put(name, (elekByGarden.containsKey(name) ? elekByGarden.get(name) : 0d) + kg);
            }
        } catch (RuntimeException ignored) {
            // Elek arşivi okunamazsa özet elek olmadan gösterilir.
        } finally {
            if (elek != null) elek.close();
        }
        Cursor c = null;
        try {
            c = gardenRepository.getGardenSummaries();
            while (c != null && c.moveToNext()) {
                SeasonReportModel.Garden g = new SeasonReportModel.Garden();
                g.name = getString(c, "name", "Bahçe");
                g.operationCost = getDouble(c, "total_cost");
                g.harvestKg = getDouble(c, "total_harvest");
                Double kg = elekByGarden.get(g.name);
                g.elekKg = kg == null ? 0 : kg;
                d.gardens.add(g);
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) c.close();
        }
        return d;
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
        // Tek satır: dar ekranda "Bahçelerim" kelimenin ortasından bölünmesin; sığmazsa yazı biraz küçülür.
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            title.setAutoSizeTextTypeUniformWithConfiguration(7, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        title.setIncludeFontPadding(false);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(5);
        item.addView(title, labelParams);
        item.setOnClickListener(v -> {
            // Elek artık alt menülü bir sekme; kayıt formu sekmeden açılır.
            showSection(section);
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
        // Arka planda dinleme sürmesin (pil ve okuma maliyeti); onResume yeniden başlatır.
        if (cloudRepository != null) cloudRepository.stopLiveSync();
        stopTypingWatch();
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