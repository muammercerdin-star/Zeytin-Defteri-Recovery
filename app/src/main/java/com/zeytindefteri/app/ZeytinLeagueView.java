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
    private boolean scoreInfoOpen; // "Puan nasıl hesaplanır?" açıklaması
    private View ownCardView;
    private int loadSerial;
    private ZeytinLeagueRepository.Result localData;
    private com.google.firebase.firestore.ListenerRegistration pointerListener;
    private Runnable expiry;
    private String observedPointer;

    private MarketCloudRepository leagueCloudRepository;
    private DatabaseHelper settingsDb;
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
        settingsDb = databaseHelper;
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
            boolean anyTop = false;
            for (ZeytinLeagueRepository.Region r : target.regions) if (r != null && !r.top.isEmpty()) anyTop = true;
            target.status = LeagueBoardModel.statusLine(target.regions.size(), anyTop);
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
        // Fotoğrafsız başlık: koyu yeşil geçiş + soluk kupa + altta yeşil→altın çizgi.
        FrameLayout hero = new FrameLayout(getContext());
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{0xff24503a, 0xff163428, GreenTheme.color(R.color.zd_surface)});
        fill.setCornerRadius(dp(20)); fill.setStroke(dp(1), border);
        hero.setBackground(fill); hero.setClipToOutline(true);
        ImageView watermark = icon(R.drawable.ic_league_nav, green);
        watermark.setAlpha(.13f);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.END | Gravity.TOP);
        wp.rightMargin = -dp(6); wp.topMargin = -dp(8);
        hero.addView(watermark, wp);
        View line = new View(getContext());
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{green, gold}));
        hero.addView(line, new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM));
        LinearLayout copy = column(); copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout heading = row();
        ImageView trophy = icon(R.drawable.ic_league_nav, gold);
        heading.addView(trophy, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = label("Zeytin Ligi", 26, text, true); title.setPadding(dp(10), 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); copy.addView(heading);
        TextView motto = label("Gerçek satış verileriyle bölgenin en iyi üreticileri", 13,
                GreenTheme.color(R.color.zd_text_soft), false);
        motto.setPadding(0, dp(8), 0, 0); copy.addView(motto);
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        float scale = Math.max(1, getResources().getConfiguration().fontScale);
        body.addView(hero, new LinearLayout.LayoutParams(-1, dp(Math.min(150, 112 * scale))));
    }

    /**
     * Varsayılan açık anonim katkıyı ilk açılışta bir kez bildirir: ne paylaşıldığı ve
     * nasıl kapatılacağı. "Tamam" ya da ayar değişince bir daha gösterilmez.
     */
    private void contributionNotice() {
        if (leagueCloudRepository == null || !leagueCloudRepository.canUseCloud()
                || leagueCloudRepository.contributionNoticeSeen()
                || !(getContext() instanceof android.app.Activity)) return;
        LinearLayout box = column(); box.setPadding(dp(14), dp(12), dp(14), dp(10));
        box.setBackground(round(GreenTheme.color(R.color.zd_surface), green));
        box.addView(label("Anonim katkın açık", 14, text, true));
        TextView copy = label("Elek kayıtlarındaki sınıf, kg, kg fiyatı, tarih ve il/ilçe; adın olmadan bölge "
                + "istatistiklerine katılır. Bir bölgede en az " + LeagueBoardModel.MIN_PRODUCERS
                + " üretici olmadan hiçbir rakam yayınlanmaz. İstediğin zaman Ayarlar'dan kapatabilirsin.",
                12, GreenTheme.color(R.color.zd_text_soft), false);
        copy.setPadding(0, dp(4), 0, dp(6)); box.addView(copy);
        LinearLayout actions = row(); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView change = label("Ayarı değiştir", 13, muted, true);
        change.setMinHeight(dp(44)); change.setGravity(Gravity.CENTER); change.setPadding(dp(12), 0, dp(12), 0);
        change.setOnClickListener(v -> {
            leagueCloudRepository.markContributionNoticeSeen();
            MarketContributionPrompt.show((android.app.Activity) getContext(), this::render);
            render();
        });
        TextView ok = label("Tamam", 13, green, true);
        ok.setMinHeight(dp(44)); ok.setGravity(Gravity.CENTER); ok.setPadding(dp(12), 0, dp(4), 0);
        ok.setOnClickListener(v -> { leagueCloudRepository.markContributionNoticeSeen(); render(); });
        actions.addView(change); actions.addView(ok); box.addView(actions);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10);
        body.addView(box, lp);
    }

    /** Başlığın altındaki "Senin sıran" şeridi; dokununca alttaki ayrıntılı karta kayar. */
    private void ownStrip() {
        if (data == null) return;
        ZeytinLeagueScore.Producer published = data.mine;
        boolean matches = data.published && published != null;
        ZeytinLeagueScore.Producer p = matches ? published : data.local;
        String regionId = matches ? published.region : (data.local == null ? "" : data.local.region);
        String district = "";
        for (ZeytinLeagueRepository.Region r : data.regions) {
            if (r != null && r.id.equals(regionId)) district = LeagueBoardModel.splitRegion(r.name)[0];
        }
        LinearLayout strip = row();
        strip.setPadding(dp(14), dp(10), dp(12), dp(10));
        strip.setBackground(round(GreenTheme.color(R.color.zd_green_soft), green));
        strip.setMinimumHeight(dp(60));

        FrameLayout cup = new FrameLayout(getContext());
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL); ring.setColor(bg);
        if (matches) ring.setStroke(dp(1), gold); else ring.setStroke(dp(1), muted, dp(3), dp(3));
        cup.setBackground(ring);
        ImageView cupIcon = icon(R.drawable.ic_league_nav, matches ? gold : muted);
        if (!matches) cupIcon.setAlpha(.7f);
        cup.addView(cupIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        strip.addView(cup, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout middle = column(); middle.setPadding(dp(12), 0, dp(8), 0);
        String over = district.isEmpty() ? "SENİN SIRAN"
                : "SENİN SIRAN · " + district.toUpperCase(new Locale("tr", "TR"));
        middle.addView(label(over, 11, green, true));
        middle.addView(label(matches ? "#" + published.rank + " · Doğrulanmış" : "Sıra henüz yok", 15, text, true));
        strip.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = column(); right.setGravity(Gravity.END);
        right.addView(label(matches ? "Puan" : "Ön puan", 11, muted, false));
        boolean scored = p != null && p.kg > 0 && p.amount > 0;
        right.addView(label(scored ? n(p.score) : "—", 15, green, true));
        strip.addView(right, new LinearLayout.LayoutParams(-2, -2));
        TextView chevron = label("›", 22, muted, false); chevron.setPadding(dp(8), 0, 0, 0);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        strip.addView(chevron);

        strip.setContentDescription((matches ? "Senin sıran " + published.rank : "Sıra henüz yok")
                + (scored ? ", " + (matches ? "puan " : "ön puan ") + n(p.score) : "") + ". Ayrıntılar için dokun.");
        strip.setOnClickListener(v -> {
            if (ownCardView != null) smoothScrollTo(0, Math.max(0, ownCardView.getTop() - dp(8)));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10);
        body.addView(strip, lp);
    }

    /** Görselsiz bölge kartı: rozet + durum şeridi, eşik altında yalnızca ilerleme. */
    private View regionTile(ZeytinLeagueRepository.Region r, boolean active, String badge) {
        int greenSoft = GreenTheme.color(R.color.zd_green_soft);
        LinearLayout tile = column();
        tile.setBackground(round(active ? greenSoft : card, active ? green : border));
        tile.setClipToOutline(true); tile.setSelected(active); tile.setElevation(active ? dp(3) : 0);

        MarketCloudRepository.LeagueRegionStat cloud = cloudRegion(r.id);
        int producers = cloud != null ? cloud.producerCount : r.count;
        boolean open = cloud != null && LeagueBoardModel.publishable(cloud.published, cloud.producerCount);
        boolean hasTop = !r.top.isEmpty();
        String[] parts = LeagueBoardModel.splitRegion(r.name);

        LinearLayout band = row(); band.setPadding(dp(10), 0, dp(10), 0);
        band.setBackgroundColor(active ? 0xff21503a : GreenTheme.color(R.color.zd_surface));
        TextView badgeView = label(badge, 12, active ? green : GreenTheme.color(R.color.zd_text_soft), true);
        badgeView.setGravity(Gravity.CENTER); badgeView.setMinWidth(dp(28)); badgeView.setPadding(dp(4), 0, dp(4), 0);
        badgeView.setBackground(round(bg, active ? green : border));
        badgeView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        band.addView(badgeView, new LinearLayout.LayoutParams(-2, dp(28)));
        View spacer = new View(getContext()); band.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView tag = label(LeagueBoardModel.tag(hasTop, open), 12, hasTop ? gold : open ? green : muted, true);
        tag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        band.addView(tag);
        tile.addView(band, new LinearLayout.LayoutParams(-1, dp(44)));
        View divider = new View(getContext()); divider.setBackgroundColor(active ? green : border);
        tile.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout details = column(); details.setPadding(dp(10), dp(10), dp(10), dp(12));
        // Sabit min. yükseklik yok: kartlar satırdaki en uzun karta eşitlenir (MATCH_PARENT),
        // eşik altındaki kısa kartların altında boşluk kalmaz.
        TextView district = label(parts[0], 15, text, true); district.setMaxLines(1);
        district.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(district);
        if (!parts[1].isEmpty()) details.addView(label(parts[1], 12, muted, false));
        TextView count = label(hasTop && !open ? producers + " üretici"
                : r.shared || cloud != null ? LeagueBoardModel.countText(producers, open) : "Yerel önizleme",
                13, text, true);
        count.setPadding(0, dp(6), 0, dp(6)); details.addView(count);
        String spoken;
        if (open) {
            double price = cloud.averagePrice, kg = cloud.totalKg;
            details.addView(label(price > 0d ? n(price) + " TL/kg" : "— TL/kg", 16, text, true));
            details.addView(label(kg > 0d ? n(kg) + " kg toplam" : "— kg", 12, GreenTheme.color(R.color.zd_text_soft), false));
            String dominant = cloud.dominantGrade == null ? "" : cloud.dominantGrade.trim();
            details.addView(label(isGradeSpecific() ? "Seçili: " + selectedLeagueGrade
                    : "Baskın: " + (dominant.isEmpty() ? "—" : dominant), 12, muted, false));
            spoken = (price > 0d ? ", ortalama " + n(price) + " TL/kg" : "") + (kg > 0d ? ", " + n(kg) + " kg" : "");
        } else if (hasTop) {
            // Firestore Top 20 yayında ama Supabase özeti yok/eşik altı: eşik çubuğu
            // göstermek "3 üretici daha" gibi çelişkili bir mesaj verirdi.
            details.addView(label("Top 20 yayında", 13, gold, true));
            details.addView(label("Bölge rakamları gizli", 12, muted, false));
            spoken = ", Top 20 yayında, bölge rakamları gizli";
        } else if (r.shared || cloud != null) {
            FrameLayout track = new FrameLayout(getContext());
            track.setBackground(round(bg, bg));
            View progress = new View(getContext()); progress.setBackground(round(muted, muted));
            track.addView(progress, new FrameLayout.LayoutParams(0, -1));
            final int percent = LeagueBoardModel.progressPercent(producers);
            track.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, orr, ob) -> {
                int w = Math.round((rr - l) * percent / 100f);
                if (progress.getLayoutParams().width != w) { progress.getLayoutParams().width = w; progress.requestLayout(); }
            });
            details.addView(track, new LinearLayout.LayoutParams(-1, dp(6)));
            TextView need = label(LeagueBoardModel.needText(producers), 12, muted, false);
            need.setPadding(0, dp(6), 0, 0); details.addView(need);
            spoken = ", " + LeagueBoardModel.needText(producers) + " (gizlilik eşiği)";
        } else {
            details.addView(label("Yalnızca bu cihazdaki kayıtlar", 12, muted, false));
            spoken = ", yalnızca bu cihazdaki kayıtlar";
        }
        tile.addView(details);
        tile.setContentDescription(r.name + ", " + (r.shared || cloud != null ? producers + " üretici" : "yerel önizleme")
                + spoken + (hasTop ? ", Top 20 yayında" : "") + (active ? ", seçili" : ""));
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
        return tile;
    }
    private void render() {
        final int restoreScrollY = getScrollY();
        body.removeAllViews();
        ownCardView = null;
        header();
        contributionNotice();
        ownStrip();
        filters();
        gradeFilters();
        LinearLayout statusRow = row();
        String status = loading ? "Doğrulanmış sonuçlar yükleniyor…" : data.status
                .replace("Bölgesel bağlantı kullanılamıyor", "Sonuçlara şu anda ulaşılamıyor")
                .replace("Sunucuda yayımlanan sonuçlar", "Bölgesel lig sonuçları");
        TextView state = label(status, 12, muted, false);
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
        cards.setGravity(Gravity.TOP);
        int selectedIndex = 0;
        List<String> districts = new ArrayList<>();
        for (ZeytinLeagueRepository.Region r : data.regions) districts.add(LeagueBoardModel.splitRegion(r.name)[0]);
        List<String> badges = LeagueBoardModel.badges(districts);
        int tileWidth = cardWidth(getResources().getDisplayMetrics().widthPixels - dp(16));
        for (int i = 0; i < data.regions.size(); i++) {
            ZeytinLeagueRepository.Region r = data.regions.get(i);
            boolean active = r.id.equals(selected); if (active) selectedIndex = i;
            View tile = regionTile(r, active, badges.get(i));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(tileWidth, -1);
            cp.rightMargin = dp(8); cards.addView(tile, cp);
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
        controls.setPadding(dp(2), dp(14), 0, dp(8));
        TextView caption = label("Elek fiyatı", 13, GreenTheme.color(R.color.zd_text_soft), false);
        controls.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));

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
        TextView how = label((scoreInfoOpen ? "ⓘ Açıklamayı gizle" : "ⓘ Puan nasıl hesaplanır?"), 12, green, true);
        how.setMinHeight(dp(36)); how.setGravity(Gravity.CENTER_VERTICAL);
        how.setOnClickListener(v -> { scoreInfoOpen = !scoreInfoOpen; render(); });
        body.addView(how);
        if (scoreInfoOpen) {
            LinearLayout info = column(); info.setPadding(dp(14), dp(12), dp(14), dp(12));
            info.setBackground(round(GreenTheme.color(R.color.zd_surface), border));
            int soft = GreenTheme.color(R.color.zd_text_soft);
            info.addView(label("%50 Kalite — kilonun hangi elek sınıflarına düştüğü", 12, soft, false));
            info.addView(label("%35 Fiyat — bölgedeki aynı sınıfların ortanca fiyatına göre", 12, soft, false));
            info.addView(label("%15 Hacim — toplam kilo", 12, soft, false));
            TextView rule = label("Sıralamaya girmek için sezonda en az 100 kg fiyatlı, geçerli elek kaydı gerekir. "
                    + "Bölge fiyatından çok sapan kayıtlar sıralamaya alınmaz.", 12, muted, false);
            rule.setPadding(0, dp(6), 0, 0); info.addView(rule);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2); ip.bottomMargin = dp(8);
            body.addView(info, ip);
        }
        LinearLayout table = column(); table.setBackground(round(card, border)); table.setClipToOutline(true);
        LinearLayout columns = row(); columns.setPadding(dp(5), dp(8), dp(5), dp(8));
        String[] titles = {"#", "Üretici", "Baskın\nElek", "Ort. Fiyat\nTL/kg", "Toplam\nkg", "Puan"};
        for (int i = 0; i < titles.length; i++) cell(columns, titles[i], i, muted, false, 9);
        table.addView(columns);
        if (region == null || region.top.isEmpty()) {
            LinearLayout empty = column(); empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(16), dp(20), dp(16), dp(20));
            if (loading) {
                empty.addView(label("Top 20 yükleniyor…", 14, text, true));
            } else {
                FrameLayout cup = new FrameLayout(getContext());
                GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL);
                ring.setColor(card); ring.setStroke(dp(1), border, dp(3), dp(3)); cup.setBackground(ring);
                ImageView cupIcon = icon(R.drawable.ic_league_nav, muted); cupIcon.setAlpha(.7f);
                cup.addView(cupIcon, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
                LinearLayout.LayoutParams cupLp = new LinearLayout.LayoutParams(dp(60), dp(60)); cupLp.bottomMargin = dp(8);
                empty.addView(cup, cupLp);
                String district = region == null ? "" : LeagueBoardModel.splitRegion(region.name)[0];
                TextView emptyTitle = label(LeagueBoardModel.emptyTitle(district), 14, text, true); emptyTitle.setGravity(Gravity.CENTER);
                empty.addView(emptyTitle);
                TextView note = label(LeagueBoardModel.emptyBody(), 12, muted, false); note.setGravity(Gravity.CENTER);
                note.setPadding(0, dp(4), 0, 0); empty.addView(note);
            }
            table.addView(empty);
        } else {
            podium(table, region.top);
            int first = Math.min(3, region.top.size());
            int visible = expanded ? region.top.size() : Math.min(7, region.top.size());
            for (int i = first; i < visible; i++) producerRow(table, region.top.get(i));
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
    /** İlk üç üretici için kürsü: ortada 1. (altın kupa), solda 2., sağda 3. */
    private void podium(LinearLayout table, List<ZeytinLeagueScore.Producer> top) {
        LinearLayout stage = row(); stage.setGravity(Gravity.BOTTOM);
        stage.setPadding(dp(12), dp(14), dp(12), 0);
        stage.setBackgroundColor(GreenTheme.color(R.color.zd_surface));
        for (int rank : LeagueBoardModel.podiumOrder(Math.min(3, top.size()))) {
            ZeytinLeagueScore.Producer p = top.get(rank - 1);
            int medal = rank == 1 ? gold : rank == 2 ? 0xffc4d0d8 : 0xffc89470;
            LinearLayout column = column(); column.setGravity(Gravity.CENTER_HORIZONTAL);
            if (rank == 1) column.addView(icon(R.drawable.ic_league_nav, gold), new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView name = label(isMine(p) ? "Sen" : p.name, 12, text, true);
            name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setGravity(Gravity.CENTER);
            column.addView(name, new LinearLayout.LayoutParams(-1, -2));
            column.addView(label(n(p.score), 12, green, true));
            TextView block = label(String.valueOf(rank), 17, medal, true);
            block.setGravity(Gravity.CENTER_HORIZONTAL); block.setPadding(0, dp(6), 0, 0);
            GradientDrawable step = new GradientDrawable();
            step.setColor(isMine(p) ? GreenTheme.color(R.color.zd_green_soft) : card);
            step.setStroke(dp(1), medal);
            float rr = dp(12); step.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
            block.setBackground(step);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(rank == 1 ? 64 : rank == 2 ? 48 : 38));
            bp.topMargin = dp(4); column.addView(block, bp);
            column.setContentDescription(rank + ". sıra, " + (isMine(p) ? "sen" : p.name) + ", puan " + n(p.score));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            cp.leftMargin = dp(4); cp.rightMargin = dp(4);
            stage.addView(column, cp);
        }
        table.addView(stage);
    }
    private boolean isMine(ZeytinLeagueScore.Producer p) {
        // page() zaten Top 20 satırının id/puan/kg değerini kişisel belgeyle doğruluyor.
        return p != null && data != null && data.published && data.mine != null
                && p.id != null && !p.id.isEmpty() && p.id.equals(data.mine.id);
    }
    private void producerRow(LinearLayout table, ZeytinLeagueScore.Producer p) {
        int medal = p.rank == 1 ? gold : p.rank == 2 ? 0xffc4d0d8 : p.rank == 3 ? 0xffc89470 : muted;
        View divider = new View(getContext()); divider.setBackgroundColor(border); divider.setAlpha(.35f);
        table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout line = row(); line.setPadding(dp(5), dp(4), dp(5), dp(4)); line.setMinimumHeight(dp(40));
        if (isMine(p)) line.setBackgroundColor(GreenTheme.color(R.color.zd_green_soft));
        TextView rank = cell(line, String.valueOf(p.rank), 0, medal, true, 12);
        if (p.rank >= 1 && p.rank <= 3) rank.setBackground(round(bg, medal));
        cell(line, isMine(p) ? "Sen" : p.name, 1, text, true, 12);
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
        ownCardView = box;
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
        if (leagueCloudRepository != null && leagueCloudRepository.canUseCloud()
                && !leagueCloudRepository.isContributionOptedIn() && getContext() instanceof android.app.Activity) {
            TextView consent = label("Bölge istatistiklerine anonim katkın kapalı · Aç", 13, green, true);
            consent.setMinHeight(dp(44)); consent.setGravity(Gravity.CENTER_VERTICAL);
            consent.setContentDescription("Piyasaya anonim katkı kapalı. Açmak için dokun.");
            consent.setOnClickListener(v -> MarketContributionPrompt.show((android.app.Activity) getContext(), () -> {
                render();
                if (leagueCloudRepository.isContributionOptedIn()) refreshCloudDirectory();
            }));
            body.addView(consent);
        }
        final String homeKey = "home_league_card_hidden_" + uid;
        if (settingsDb != null && !uid.isEmpty() && "1".equals(settingsDb.getSetting(homeKey, "0"))) {
            TextView restore = label("Ana Sayfa'daki Zeytin Ligi kartını yeniden göster", 13, green, true);
            restore.setMinHeight(dp(44)); restore.setGravity(Gravity.CENTER_VERTICAL);
            restore.setOnClickListener(v -> {
                settingsDb.setSetting(homeKey, "0");
                android.widget.Toast.makeText(getContext(), "Kart Ana Sayfa'da yeniden görünecek.", android.widget.Toast.LENGTH_SHORT).show();
                render();
            });
            body.addView(restore);
        }
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
        // ~2,5 kart görünür; yarım kart yana kaydırılabildiğini gösterir.
        return Math.round(Math.max(dp(118), Math.min(dp(136), available / 2.6f))
                * Math.min(1.3f, Math.max(1, getResources().getConfiguration().fontScale)));
    }
    private ImageView icon(int resource, int tint) {
        ImageView v = new ImageView(getContext()); v.setImageResource(resource); v.setColorFilter(tint);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
    private TextView pill(String title, int foreground, int fill, int stroke) {
        TextView t = label(title, 12, foreground, true); t.setGravity(Gravity.CENTER);
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
