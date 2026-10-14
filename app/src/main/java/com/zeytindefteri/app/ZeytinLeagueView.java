package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** REPORTS content only. Navigation and assistant remain owned by the activity. */
public final class ZeytinLeagueView extends ScrollView {
    private final int bg = GreenTheme.color(R.color.zd_bg), card = GreenTheme.color(R.color.zd_card);
    private final int green = GreenTheme.color(R.color.zd_green), gold = GreenTheme.color(R.color.zd_warning);
    private final int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
    private final int border = GreenTheme.color(R.color.zd_border);
    private final int year;
    private final String uid;
    private final Runnable seasonPicker;
    private final Runnable personalReports;
    private final LinearLayout body;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ZeytinLeagueRepository repository = new ZeytinLeagueRepository();
    private ExecutorService worker;
    private int generation;
    private String selected = "";
    private ZeytinLeagueRepository.Result data;
    private boolean paging, loading;
    private boolean expanded; // Presentation only: reveal the already loaded Top 20.
    private int loadSerial;
    private ZeytinLeagueRepository.Result localData;
    private com.google.firebase.firestore.ListenerRegistration pointerListener;
    private Runnable expiry;
    private String observedPointer;

    private MarketCloudRepository leagueCloudRepository;
    private final List<MarketCloudRepository.LeagueRegionStat> cloudLeagueRegions = new ArrayList<>();
    private boolean cloudDirectoryLoaded;
    private int cloudDirectorySerial;

    // UI stability: async cloud / Firestore callbacks used to rebuild the entire
    // screen several times and reset scroll positions. Keep the user's viewport
    // stable while fresh data is painted into the same screen.
    private int regionCarouselScrollX;
    private int gradeFilterScrollX;
    private static final String GRADE_ALL = "Genel";
    private static final String[] LEAGUE_GRADES = {
            GRADE_ALL, "Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"
    };
    private String selectedLeagueGrade = GRADE_ALL;

    private final NumberFormat numbers = NumberFormat.getNumberInstance(new Locale("tr", "TR"));

    public ZeytinLeagueView(Context context, int year, String uid, Runnable seasonPicker, Runnable personalReports) {
        super(context); this.year = year; this.uid = uid; this.seasonPicker = seasonPicker; this.personalReports = personalReports;
        numbers.setMaximumFractionDigits(2);
        setFillViewport(true); setVerticalScrollBarEnabled(false); setBackgroundColor(bg);
        body = column(); body.setPadding(dp(8), dp(4), dp(8), dp(12)); addView(body);
        renderLoading();
    }
    /**
     * Zeytin Ligi bölge kartlarını ortak Supabase ağıyla besler.
     * Top 20 sıralama altyapısı Firestore'da kalır; Supabase burada yalnız
     * güvenli bölge dizini ve eşik üstü anonim özet metriklerini sağlar.
     */
    public void configureCloud(DatabaseHelper databaseHelper) {
        if (databaseHelper == null) return;
        leagueCloudRepository = new MarketCloudRepository(databaseHelper, uid);
        if (isAttachedToWindow() && localData != null) {
            refreshCloudDirectory();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); final int token = ++generation;

        // Alt navigasyondan geri gelindiginde ayni View yeniden baglaniyorsa,
        // daha once cizilmis kartlari silip skeleton gostermeyelim. Eski goruntu
        // ekranda sabit kalir; taze veri arkada okunup geldigi anda guncellenir.
        // Ilk acilista ise normal loading skeletonu kullanilir.
        final boolean hasCachedLeagueUi =
                data != null
                        && !data.regions.isEmpty()
                        && body.getChildCount() > 0;

        loading = !hasCachedLeagueUi;
        if (!hasCachedLeagueUi) {
            renderLoading();
        }

        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            DatabaseHelper db = new DatabaseHelper(getContext().getApplicationContext());
            try {
                ZeytinLeagueRepository.Result local = repository.local(db, year, uid);
                main.post(() -> { if (current(token)) begin(local); });
            } catch (RuntimeException e) {
                main.post(() -> {
                    if (!current(token)) return;
                    ZeytinLeagueRepository.Result local = new ZeytinLeagueRepository.Result();
                    local.status = "Yerel kayıtlar okunamadı"; begin(local);
                });
            } finally { db.close(); }
        });
    }
    private void begin(ZeytinLeagueRepository.Result local) {
        if (pointerListener != null) pointerListener.remove();
        observedPointer = null;
        localData = local; data = local;
        mergeCloudRegions(data);
        refreshCloudDirectory();
        refresh();
        final int token = generation;
        pointerListener = repository.watch(year, version -> {
            if (!current(token)) return;
            if (version == null) { observedPointer = null; useLocal("Bölgesel bağlantı kullanılamıyor • Yerel önizleme"); return; }
            if (!version.equals(observedPointer)) {
                observedPointer = version;
                if (!version.equals(data.snapshot) && data.published) useLocal("Yeni bölgesel sonuçlar alınıyor • Yerel önizleme");
                if (!loading || !version.equals(data.snapshot)) refresh();
            }
        });
    }
    private void refresh() {
        if (localData == null || !current(generation)) return;
        final int token = generation, request = ++loadSerial;
        loading = true;
        paging = false;
        // Do not tear down and rebuild an already visible league screen just to
        // show a transient loading state. The callback below renders once with
        // the actual result. This removes the visible "kıp kıp" flash.
        if (data == null || data.regions.isEmpty()) {
            render();
        }
        Runnable timeout = () -> {
            if (current(token) && request == loadSerial) useLocal("Bağlantı zaman aşımına uğradı • Yerel önizleme");
        };
        main.postDelayed(timeout, 15000);
        repository.published(year, uid, localData, result -> {
            main.removeCallbacks(timeout);
            if (!current(token) || request != loadSerial) return;
            loading = false; data = result;
            mergeCloudRegions(data);
            applyCloudStatus(data);
            if (data.published && !ZeytinLeagueRepository.fresh(data.publishedAtMs, System.currentTimeMillis())) {
                useLocal("Güncel bölgesel sonuç yok • Yerel önizleme"); return;
            }
            render(); scheduleExpiry();
        });
    }
    private void useLocal(String message) {
        ++loadSerial; loading = false; paging = false;
        if (localData == null) return;
        localData.status = message;
        data = localData;
        mergeCloudRegions(data);
        applyCloudStatus(data);
        render();
        scheduleExpiry();
    }
    private void scheduleExpiry() {
        if (expiry != null) main.removeCallbacks(expiry);
        if (!data.published) return;
        final int token = generation;
        expiry = () -> {
            if (!current(token)) return;
            useLocal("Güncel bölgesel sonuç yok • Yerel önizleme"); refresh();
        };
        main.postDelayed(expiry, Math.max(1, data.publishedAtMs + 86400001L - System.currentTimeMillis()));
    }
    private boolean current(int token) {
        return token == generation && isAttachedToWindow()
                && uid.equals(new SessionManager(getContext()).getUserUid());
    }
    @Override protected void onDetachedFromWindow() {
        ++generation; ++loadSerial; ++cloudDirectorySerial; main.removeCallbacksAndMessages(null);
        if (pointerListener != null) { pointerListener.remove(); pointerListener = null; }
        observedPointer = null;
        if (worker != null) worker.shutdownNow();
        super.onDetachedFromWindow();
    }
    private void refreshCloudDirectory() {
        if (leagueCloudRepository == null || !leagueCloudRepository.canUseCloud()) return;

        final int token = generation;
        final int request = ++cloudDirectorySerial;
        leagueCloudRepository.loadLeagueRegions(year, selectedLeagueGrade, (regions, error) -> {
            if (!current(token) || request != cloudDirectorySerial) return;
            if (error != null) {
                cloudDirectoryLoaded = false;
                return;
            }

            cloudDirectoryLoaded = true;
            cloudLeagueRegions.clear();
            if (regions != null) cloudLeagueRegions.addAll(regions);

            if (localData != null) {
                mergeCloudRegions(localData);
                applyCloudStatus(localData);
            }
            if (data != null) {
                mergeCloudRegions(data);
                applyCloudStatus(data);
            }
            render();
        });
    }

    private void mergeCloudRegions(ZeytinLeagueRepository.Result target) {
        if (target == null) return;

        java.util.LinkedHashMap<String, ZeytinLeagueRepository.Region> merged =
                new java.util.LinkedHashMap<>();
        for (ZeytinLeagueRepository.Region region : target.regions) {
            if (region != null && region.id != null && !region.id.trim().isEmpty()) {
                // aggregatePublished is an overlay for the CURRENT selected elek.
                // Reset it so switching filters can never leave stale values.
                region.aggregatePublished = false;
                merged.put(region.id, region);
            }
        }

        for (MarketCloudRepository.LeagueRegionStat cloud : cloudLeagueRegions) {
            if (cloud == null || cloud.id == null || cloud.id.trim().isEmpty()) continue;

            ZeytinLeagueRepository.Region region = merged.get(cloud.id);
            if (region == null) {
                region = new ZeytinLeagueRepository.Region();
                region.id = cloud.id;
                region.name = cloud.name;
                merged.put(region.id, region);
            }

            region.shared = true;
            region.aggregatePublished = cloud.published;
            if (region.name == null || region.name.trim().isEmpty()) region.name = cloud.name;
        }

        target.regions.clear();
        target.regions.addAll(merged.values());
        java.util.Collections.sort(
                target.regions,
                (a, b) -> safeRegionName(a).compareToIgnoreCase(safeRegionName(b))
        );
    }

    private void applyCloudStatus(ZeytinLeagueRepository.Result target) {
        if (target == null || target.published || !cloudDirectoryLoaded) return;
        if (isGradeSpecific()) {
            target.status = selectedLeagueGrade + " • " + cloudLeagueRegions.size()
                    + " bölgede anonim fiyat verisi"
                    + (cloudLeagueRegions.isEmpty() ? " bulunamadı"
                    : " • rakamlar " + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS + " üreticiyle açılır");
        } else if (!cloudLeagueRegions.isEmpty()) {
            target.status = "Bölgesel ağ • " + cloudLeagueRegions.size()
                    + " bölge bulundu • Top 20 için lig verisi bekleniyor";
        }
    }

    private String safeRegionName(ZeytinLeagueRepository.Region region) {
        return region == null || region.name == null ? "" : region.name.trim();
    }

    private boolean isGradeSpecific() {
        return !GRADE_ALL.equalsIgnoreCase(selectedLeagueGrade);
    }

    private MarketCloudRepository.LeagueRegionStat cloudRegion(String regionId) {
        if (regionId == null || regionId.trim().isEmpty()) return null;
        for (MarketCloudRepository.LeagueRegionStat item : cloudLeagueRegions) {
            if (item != null && regionId.equals(item.id)) return item;
        }
        return null;
    }

    private void gradeFilters() {
        TextView caption = label("Elek fiyatı", 10, muted, false);
        caption.setPadding(dp(1), dp(7), 0, dp(4));
        body.addView(caption);

        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                gradeFilterScrollX = scrollX);
        LinearLayout options = row();
        for (String grade : LEAGUE_GRADES) {
            final String value = grade;
            boolean active = value.equals(selectedLeagueGrade);
            TextView chip = pill(
                    value,
                    active ? bg : muted,
                    active ? green : card,
                    active ? green : border
            );
            chip.setSelected(active);
            chip.setContentDescription("Elek fiyatı: " + value + (active ? ", seçili" : ""));
            chip.setOnClickListener(v -> {
                if (value.equals(selectedLeagueGrade)) return;
                selectedLeagueGrade = value;
                cloudDirectoryLoaded = false;
                cloudLeagueRegions.clear();
                ++cloudDirectorySerial;
                expanded = false;
                render();
                refreshCloudDirectory();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.rightMargin = dp(5);
            options.addView(chip, cp);
        }
        scroll.addView(options);
        body.addView(scroll);
        final int restoreGradeX = gradeFilterScrollX;
        restoreHorizontalBeforeDraw(scroll, restoreGradeX);
    }

    private void header() {
        FrameLayout hero = new FrameLayout(getContext());
        hero.setBackground(round(card, gold)); hero.setClipToOutline(true);
        ImageView photo = new ImageView(getContext()); photo.setImageResource(R.drawable.home_hero_olive);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP); photo.setScaleX(-1f);
        photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(getContext());
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xee061b20, 0xb0061b20, 0x30061b20}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout copy = column(); copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout heading = row(); ImageView trophy = new ImageView(getContext());
        trophy.setImageResource(R.drawable.ic_league_nav); trophy.setColorFilter(gold);
        trophy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        heading.addView(trophy, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = label("Zeytin Ligi", 26, text, true); title.setPadding(dp(10), 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); copy.addView(heading);
        TextView motto = label("Kaliteli üretim, güçlü üreticiler", 14, green, true);
        motto.setPadding(0, dp(8), 0, dp(5)); copy.addView(motto);
        copy.addView(label("Gerçek satış verileriyle, bölgemizin en iyi zeytin üreticileri.", 12, text, false));
        // Fixed outer bounds prevent the bitmap's intrinsic height sizing the banner.
        // Text alone can scroll at very large accessibility fonts / short landscape windows.
        ScrollView heroText = new ScrollView(getContext()); heroText.setFillViewport(true);
        heroText.setVerticalScrollBarEnabled(false); heroText.addView(copy);
        hero.addView(heroText, new FrameLayout.LayoutParams(-1, -1));
        float bannerDp = Math.min(170, 150 * Math.max(1, getResources().getConfiguration().fontScale));
        bannerDp = Math.min(bannerDp, Math.max(140, getResources().getConfiguration().screenHeightDp * .28f));
        body.addView(hero, new LinearLayout.LayoutParams(-1, dp(bannerDp)));
    }
    private void render() {
        final int restoreScrollY = getScrollY();
        body.removeAllViews();
        header();
        filters();
        gradeFilters();
        LinearLayout statusRow = row();
        String status = loading ? "Doğrulanmış sonuçlar yükleniyor…" : data.status
                .replace("Bölgesel bağlantı kullanılamıyor", "Sonuçlara şu anda ulaşılamıyor")
                .replace("Sunucuda yayımlanan sonuçlar", "Bölgesel lig sonuçları");
        TextView state = label(status, 11, muted, false);
        state.setMinHeight(dp(36));
        state.setMaxLines(2);
        state.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(state, new LinearLayout.LayoutParams(0, dp(36), 1));
        ImageView retry = icon(R.drawable.ic_league_refresh, green);
        retry.setPadding(dp(8), dp(8), dp(8), dp(8));
        retry.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        retry.setContentDescription(loading ? "Yükleniyor" : "Sonuçları yenile");
        retry.setEnabled(!loading); retry.setAlpha(loading ? .4f : 1f);
        retry.setOnClickListener(v -> begin(localData));
        statusRow.addView(retry, new LinearLayout.LayoutParams(dp(36), dp(36))); body.addView(statusRow);
        if (data.regions.isEmpty()) {
            LinearLayout region = box();
            region.addView(label("BÖLGELER", 12, gold, true));
            region.addView(label(loading ? "Bölge sonuçları yükleniyor…" : "Bu sezon için yayımlanmış bölge bulunmuyor.", 14, muted, false));
            body.addView(region);
            emptyRanking();
            ownCard(null);
            policy();
            restoreVerticalBeforeDraw(restoreScrollY);
            return;
        }
        ZeytinLeagueRepository.Region chosen = null;
        for (ZeytinLeagueRepository.Region r : data.regions) if (r.id.equals(selected)) chosen = r;
        if (chosen == null) {
            chosen = data.regions.get(0);
            String ownRegion = data.mine == null ? (data.local == null ? "" : data.local.region) : data.mine.region;
            for (ZeytinLeagueRepository.Region r : data.regions) if (r.id.equals(ownRegion)) chosen = r;
            selected = chosen.id;
        }
        HorizontalScrollView carousel = new HorizontalScrollView(getContext());
        carousel.setHorizontalScrollBarEnabled(false); LinearLayout cards = row();
        int selectedIndex = 0;
        for (int i = 0; i < data.regions.size(); i++) {
            ZeytinLeagueRepository.Region r = data.regions.get(i);
            boolean active = r.id.equals(selected); if (active) selectedIndex = i;
            LinearLayout tile = column(); tile.setPadding(dp(1), dp(1), dp(1), dp(1));
            tile.setMinimumHeight(dp(238));
            tile.setBackground(round(active ? GreenTheme.color(R.color.zd_green_soft) : card, active ? green : border));
            tile.setClipToOutline(true); tile.setSelected(active); tile.setElevation(active ? dp(3) : 0);
            MarketCloudRepository.LeagueRegionStat cloud = cloudRegion(r.id);
            int displayProducerCount = cloud != null ? cloud.producerCount : r.count;
            tile.setContentDescription(r.name + ", " + displayProducerCount
                    + (r.shared ? " üretici" : " yerel üretici"));
            ImageView landscape = new ImageView(getContext());
            landscape.setImageResource(R.drawable.home_hero_olive);
            landscape.setScaleType(ImageView.ScaleType.CENTER_CROP);
            landscape.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(landscape, new LinearLayout.LayoutParams(-1, dp(54)));
            LinearLayout details = column(); details.setPadding(dp(6), dp(5), dp(6), dp(6));
            details.setMinimumHeight(dp(180));
            TextView name = label(r.name, 11, text, true); name.setMinHeight(dp(28)); details.addView(name);
            details.addView(label("Bölgesi", 10, muted, false));
            regionLine(details, R.drawable.ic_profile_nav, displayProducerCount + (r.shared ? " üretici" : " yerel üretici"));

            boolean cloudPriceVisible = cloud != null && cloud.published && cloud.averagePrice > 0d;
            boolean cloudKgVisible = cloud != null && cloud.published && cloud.totalKg > 0d;
            boolean localGeneralPreview = !isGradeSpecific() && r.localPreview && cloud == null;

            double displayKg = cloudKgVisible ? cloud.totalKg : (localGeneralPreview ? r.kg : 0d);
            double displayPrice = cloudPriceVisible
                    ? cloud.averagePrice
                    : (localGeneralPreview && r.kg > 0d ? r.amount / r.kg : 0d);

            regionLine(details, R.drawable.ic_garden_nav, displayKg > 0d ? n(displayKg) + " kg" : "— kg");
            regionLine(details, R.drawable.ic_league_nav, displayPrice > 0d ? n(displayPrice) + " TL/kg" : "— TL/kg");

            if (isGradeSpecific()) {
                details.addView(label("Seçili elek: " + selectedLeagueGrade, 9, muted, false));
            } else {
                String dominant = cloud != null && cloud.published && cloud.dominantGrade != null
                        && !cloud.dominantGrade.trim().isEmpty()
                        ? cloud.dominantGrade
                        : r.dominant;
                details.addView(label("Baskın elek: "
                        + (dominant != null && !dominant.trim().isEmpty() ? dominant : "—"),
                        9, muted, false));
            }

            String confidenceText;
            if (!r.top.isEmpty()) confidenceText = "Doğrulanmış lig sonucu";
            else if (isGradeSpecific() && cloudPriceVisible && displayProducerCount == 1)
                confidenceText = selectedLeagueGrade + " • 1 üretici • fiyat ön izlemesi";
            else if (isGradeSpecific() && cloudPriceVisible)
                confidenceText = selectedLeagueGrade + " • anonim bölgesel fiyat";
            else if (isGradeSpecific() && r.shared) confidenceText = selectedLeagueGrade + " • veri yok";
            else if (cloud != null && cloud.published) confidenceText = "Anonim bölgesel veri";
            else if (r.shared) confidenceText = "Bölge aktif • eşik bekleniyor";
            else confidenceText = "Geçici / yetersiz veri";
            TextView confidence = label(confidenceText, 9, active ? green : muted, false);
            confidence.setMinHeight(dp(42));
            confidence.setGravity(Gravity.CENTER_VERTICAL);
            confidence.setMaxLines(2);
            confidence.setPadding(dp(5), dp(5), dp(5), dp(5)); confidence.setBackground(round(bg, active ? green : border));
            LinearLayout.LayoutParams trust = new LinearLayout.LayoutParams(-1, -2); trust.topMargin = dp(4);
            details.addView(confidence, trust); tile.addView(details);
            tile.setOnClickListener(v -> {
                boolean changed = !r.id.equals(selected);
                selected = r.id;
                expanded = false;
                if (changed) render();
                post(() -> ZeytinLeagueRegionReportDialog.show(
                        getContext(),
                        leagueCloudRepository,
                        year,
                        r,
                        cloudRegion(r.id)
                ));
            });
            int initialWidth = cardWidth(getResources().getDisplayMetrics().widthPixels - dp(16));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(initialWidth, -2);
            cp.rightMargin = dp(5); cards.addView(tile, cp);
        }
        carousel.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                regionCarouselScrollX = scrollX);
        carousel.addView(cards);
        body.addView(carousel);
        final int activeIndex = selectedIndex;
        final int restoreRegionX = regionCarouselScrollX;
        restoreHorizontalBeforeDraw(carousel, restoreRegionX);
        HorizontalScrollView indicatorScroll = new HorizontalScrollView(getContext());
        indicatorScroll.setHorizontalScrollBarEnabled(false); indicatorScroll.setFillViewport(true);
        LinearLayout dots = row(); dots.setGravity(Gravity.CENTER); dots.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < data.regions.size(); i++) {
            final ZeytinLeagueRepository.Region r = data.regions.get(i);
            FrameLayout target = new FrameLayout(getContext());
            View dot = new View(getContext()); dot.setBackground(round(r.id.equals(selected) ? green : border, border));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(6), dp(6), Gravity.CENTER);
            target.addView(dot, dotParams); target.setContentDescription(r.name + (r.id.equals(selected) ? ", seçili" : ", bölgeyi seç"));
            target.setOnClickListener(v -> { selected = r.id; expanded = false; render(); });
            dots.addView(target, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        indicatorScroll.addView(dots);
        body.addView(indicatorScroll);
        indicatorScroll.post(() -> {
            if (dots.getChildCount() == 0 || activeIndex >= dots.getChildCount()) return;
            int targetX = Math.max(0, dots.getChildAt(activeIndex).getLeft() - indicatorScroll.getWidth() / 2);
            indicatorScroll.scrollTo(targetX, 0);
        });
        if (data.more) {
            TextView more = label(paging ? "Bölgeler yükleniyor…" : "Diğer bölgeleri yükle", 14, green, true);
            more.setPadding(0, dp(14), 0, dp(14)); more.setEnabled(!paging); body.addView(more);
            more.setOnClickListener(v -> {
                if (paging) return; paging = true; final int token = generation; render();
                repository.page(year, data, data.last, result -> {
                    if (!current(token) || loading || data != result) return;
                    paging = false;
                    if (!result.published || !ZeytinLeagueRepository.fresh(result.publishedAtMs, System.currentTimeMillis())) {
                        useLocal("Güncel bölgesel sonuç yok • Yerel önizleme"); return;
                    }
                    data = result; render();
                });
            });
        }
        ranking(chosen);
        ownCard(chosen);
        policy();

        // Rebuilding is still used for simple state rendering, but the user's
        // vertical position must not jump when an async source finishes.
        restoreVerticalBeforeDraw(restoreScrollY);
    }
    private void filters() {
        // Bölgelere Göre / Tüm Türkiye / Harita satırı kaldırıldı.
        // Sezon seçici tek başına sağda kalır; mevcut sezon davranışı korunur.
        LinearLayout controls = row();
        controls.setPadding(0, dp(6), 0, 0);
        controls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView season = pill(year + " Sezonu", gold, card, gold);
        android.graphics.drawable.Drawable arrow = getContext().getDrawable(R.drawable.ic_dropdown_arrow).mutate();
        arrow.setTint(gold);
        arrow.setBounds(0, 0, dp(12), dp(12));
        season.setCompoundDrawables(null, null, arrow, null);
        season.setCompoundDrawablePadding(dp(3));
        season.setContentDescription("Sezon seç: " + year);
        season.setOnClickListener(v -> seasonPicker.run());

        controls.addView(season, new LinearLayout.LayoutParams(-2, -2));
        body.addView(controls);
    }
    private void emptyRanking() { ranking(null); }
    private void ranking(ZeytinLeagueRepository.Region region) {
        LinearLayout heading = row(); heading.setPadding(0, dp(6), 0, dp(8));
        TextView title = label(region == null ? "Bölgesel Top 20" : region.name + " Bölgesi – En İyi 20 Üretici", 13, text, true);
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription(title.getText());
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView order = pill("Puan Sıralaması", muted, card, border);
        android.graphics.drawable.Drawable orderArrow = getContext().getDrawable(R.drawable.ic_dropdown_arrow).mutate();
        orderArrow.setTint(muted); orderArrow.setBounds(0, 0, dp(12), dp(12));
        order.setCompoundDrawables(null, null, orderArrow, null);
        order.setEnabled(false); order.setContentDescription("Backend puan sıralaması; başka sıralama seçeneği yok");
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2); op.leftMargin = dp(6); heading.addView(order, op); body.addView(heading);
        LinearLayout table = column(); table.setBackground(round(card, border)); table.setClipToOutline(true);
        LinearLayout columns = row(); columns.setPadding(dp(5), dp(8), dp(5), dp(8));
        String[] titles = {"#", "Üretici", "Baskın\nElek", "Ort. Fiyat\nTL/kg", "Toplam\nkg", "Puan"};
        for (int i = 0; i < titles.length; i++) cell(columns, titles[i], i, muted, false, 9);
        table.addView(columns);
        if (region == null || region.top.isEmpty()) {
            LinearLayout empty = column(); empty.setPadding(dp(10), dp(9), dp(10), dp(9));
            empty.addView(label(loading ? "Top 20 yükleniyor…" : "Bölgesel sıralama henüz oluşmadı.", 13, text, true));
            empty.addView(label("Yeterli doğrulanmış üretici verisi oluştuğunda Top 20 burada görünecek.", 11, muted, false)); table.addView(empty);
        } else {
            int visible = expanded ? region.top.size() : Math.min(7, region.top.size());
            for (int i = 0; i < visible; i++) producerRow(table, region.top.get(i));
            if (region.top.size() > 7) {
                TextView all = pill(expanded ? "İlk 7 Satırı Göster" : "Tüm Listeyi Gör (İlk 20)", text, bg, border);
                all.setOnClickListener(v -> { expanded = !expanded; render(); });
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2); ap.gravity = Gravity.CENTER; ap.topMargin = dp(4); ap.bottomMargin = dp(4); table.addView(all, ap);
            }
        }
        body.addView(table);
    }
    private void renderLoading() {
        body.removeAllViews(); header(); filters(); gradeFilters();
        body.addView(label("Lig sonuçları yükleniyor…", 14, muted, false));
        for (String title : new String[]{"BÖLGELER", "TOP 20", "SENİN SIRAN"}) {
            LinearLayout placeholder = box(); placeholder.addView(label(title, 12, gold, true));
            View bar = new View(getContext()); bar.setBackground(round(border, border));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(12)); bp.topMargin = dp(12);
            placeholder.addView(bar, bp); placeholder.setContentDescription(title + ", yükleniyor"); body.addView(placeholder);
        }
    }
    private void producerRow(LinearLayout table, ZeytinLeagueScore.Producer p) {
        int medal = p.rank == 1 ? gold : p.rank == 2 ? 0xffc4d0d8 : p.rank == 3 ? 0xffc89470 : muted;
        View divider = new View(getContext()); divider.setBackgroundColor(border); divider.setAlpha(.35f);
        table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout line = row(); line.setPadding(dp(5), dp(4), dp(5), dp(4)); line.setMinimumHeight(dp(34));
        TextView rank = cell(line, String.valueOf(p.rank), 0, medal, true, 12);
        if (p.rank >= 1 && p.rank <= 3) rank.setBackground(round(bg, medal));
        cell(line, p.name, 1, text, true, 11);
        cell(line, p.dominant(), 2, text, false, 10);
        cell(line, n(p.average()), 3, text, false, 10);
        cell(line, n(p.kg), 4, text, false, 10);
        TextView score = cell(line, n(p.score), 5, green, true, 11); score.setBackground(round(bg, border));
        table.addView(line);
    }
    private TextView cell(LinearLayout line, String value, int index, int color, boolean bold, float size) {
        float[] weights = {.42f, 1.45f, .88f, 1.08f, 1.02f, .72f};
        TextView t = label(value, size, color, bold); t.setPadding(dp(2), dp(3), dp(2), dp(3));
        t.setGravity(index == 1 ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        line.addView(t, new LinearLayout.LayoutParams(0, -2, weights[index])); return t;
    }
    private void ownCard(ZeytinLeagueRepository.Region chosen) {
        LinearLayout box = box(); box.setBackground(round(GreenTheme.color(R.color.zd_green_soft), green));
        LinearLayout band = row(); LinearLayout identity = column();
        identity.addView(label("Senin Sıran", 11, green, true));
        band.addView(identity, new LinearLayout.LayoutParams(0, -2, 1.2f)); box.addView(band);
        ZeytinLeagueScore.Producer published = data.mine;
        boolean matches = data.published && published != null;
        ZeytinLeagueScore.Producer p = matches ? published : data.local;
        if (matches) {
            identity.addView(label("#" + published.rank + "  Sen", 15, text, true));
            box.addView(label(chosen != null && chosen.id.equals(published.region)
                    ? "Doğrulanmış bölgesel sıran" : "Doğrulanmış sıran başka bölgede.", 10, muted, false));
        }
        else identity.addView(label("Sıra henüz yok", 12, text, true));
        if (!matches) box.addView(label(published != null ? "Yerel önizleme • Kesin bölgesel sıra değildir."
                : data.published ? "Sıran henüz oluşmadı • Yerel önizleme"
                : "Yerel önizleme • Bölgesel sıra değildir.", 10, muted, false));
        if (p == null || p.kg <= 0) {
            box.addView(label(p == null ? "Yerel arşiv bilgisi okunamadı." : "Bu sezon için kişisel Elek kaydı bulunmuyor.", 13, muted, false));
            body.addView(box); return;
        }
        LinearLayout values = row(); values.setPadding(0, dp(2), 0, dp(2));
        primaryMetric(values, "TL/kg", n(p.average()), text);
        primaryMetric(values, "kg", n(p.kg), text);
        primaryMetric(values, matches ? "Puan" : "Ön Puan", p.kg > 0 && p.amount > 0 ? n(p.score) : "—", green);
        band.addView(values, new LinearLayout.LayoutParams(0, -2, 3));
        // Secondary values remain accessible without enlarging the personal rank bar.
        TextView detail = label("Ayrıntılar", 10, muted, false);
        detail.setPadding(0, dp(3), 0, dp(3));
        LinearLayout extra = column(); extra.setVisibility(View.GONE);
        extra.addView(label("Toplam tutar: " + n(p.amount) + " TL", 11, muted, false));
        if (!matches) {
            extra.addView(label("Elek kalitesi " + n(p.quality) + " • Hacim " + n(p.volume), 11, muted, false));
            extra.addView(label(p.eligible ? "Fiyat puanı: geçici " + n(p.price)
                    : "Yetersiz veri: en az 100 kg ve fiyatlı, geçerli elek kaydı gerekir.", 11, gold, false));
        }
        detail.setOnClickListener(v -> {
            boolean open = extra.getVisibility() == View.VISIBLE;
            extra.setVisibility(open ? View.GONE : View.VISIBLE);
            detail.setText(open ? "Ayrıntılar" : "Ayrıntıları gizle");
        });
        box.addView(detail); box.addView(extra);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(12); body.addView(box, bp);
    }
    private void policy() {
        TextView info = label("%50 elek kalitesi • %35 fiyat • %15 hacim\n"
                + "Elek tutarları tahmini olabilir; doğrulanmış satış değildir.", 11, muted, false);
        info.setPadding(0, dp(6), 0, 0); body.addView(info);
        TextView reports = label("Kişisel raporlar ve işlem geçmişi", 14, green, true);
        reports.setPadding(0, dp(8), 0, dp(8)); reports.setOnClickListener(v -> personalReports.run());
        body.addView(reports);
    }
    private void primaryMetric(LinearLayout parent, String unit, String value, int color) {
        LinearLayout c = column(); c.setPadding(dp(3), dp(4), dp(3), dp(4));
        c.addView(label(value, 16, color, true)); c.addView(label(unit, 11, muted, false));
        parent.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }
    /**
     * Scroll konumunu ilk cizimden ONCE geri yukler. post() kullanildiginda
     * bir frame 0 konumunda gorunup sonra eski konuma donuyor ve kartlar
     * "kip kip" ediyor gibi algilaniyordu.
     */
    private void restoreHorizontalBeforeDraw(HorizontalScrollView scroll, int wantedX) {
        if (scroll == null) return;
        final ViewTreeObserver observer = scroll.getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver current = scroll.getViewTreeObserver();
                if (current.isAlive()) current.removeOnPreDrawListener(this);
                int childWidth = scroll.getChildCount() > 0 ? scroll.getChildAt(0).getWidth() : 0;
                int maxScroll = Math.max(0, childWidth - scroll.getWidth());
                scroll.scrollTo(Math.min(Math.max(0, wantedX), maxScroll), 0);
                return true;
            }
        });
    }

    private void restoreVerticalBeforeDraw(int wantedY) {
        final ViewTreeObserver observer = getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver current = getViewTreeObserver();
                if (current.isAlive()) current.removeOnPreDrawListener(this);
                int maxY = Math.max(0, body.getHeight() - getHeight());
                scrollTo(0, Math.min(Math.max(0, wantedY), maxY));
                return true;
            }
        });
    }

    private int cardWidth(int available) {
        return Math.round(Math.max(dp(88), Math.min(dp(110), available / 4.3f))
                * Math.max(1, getResources().getConfiguration().fontScale));
    }
    private ImageView icon(int resource, int tint) {
        ImageView v = new ImageView(getContext()); v.setImageResource(resource); v.setColorFilter(tint);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
    private void regionLine(LinearLayout parent, int resource, String value) {
        LinearLayout line = row(); line.setPadding(0, dp(2), 0, dp(2));
        line.addView(icon(resource, gold), new LinearLayout.LayoutParams(dp(11), dp(11)));
        TextView t = label(value, 10, text, false); t.setPadding(dp(4), 0, 0, 0);
        line.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); parent.addView(line);
    }
    private TextView pill(String title, int foreground, int fill, int stroke) {
        TextView t = label(title, 10, foreground, true); t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(36)); t.setPadding(dp(8), dp(6), dp(8), dp(6));
        t.setBackground(round(fill, stroke)); return t;
    }
    private LinearLayout box() {
        LinearLayout l = column(); l.setPadding(dp(12), dp(10), dp(12), dp(10)); l.setBackground(round(card, border));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(6); l.setLayoutParams(p); return l;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext()); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable round(int fill, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(18)); d.setStroke(dp(1), stroke); return d; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String n(double v) { return ZeytinLeagueScore.finite(v) ? numbers.format(v) : "—"; }
}
