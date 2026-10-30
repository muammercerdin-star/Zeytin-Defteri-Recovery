package com.zeytindefteri.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zeytin Ligi ve Piyasa içeriklerini aynı host içinde tutan
 * premium sekmeli container.
 *
 * Yeni Activity açmaz.
 * Intent kullanmaz.
 *
 * ZeytinLeagueView ve MarketView yalnızca bir kez oluşturulur.
 * Sekme değişiminde Activity yeniden oluşturulmaz;
 * sadece View visibility değiştirilir.
 */
public final class ZeytinLeagueContainerView extends LinearLayout {

    private final int bg = GreenTheme.color(R.color.zd_bg);
    private final int surface = GreenTheme.color(R.color.zd_surface);
    private final int text = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);
    private final int border = GreenTheme.color(R.color.zd_border);
    private final int green = GreenTheme.color(R.color.zd_green);
    private final int greenMid = GreenTheme.color(R.color.zd_green_mid);
    private final int greenSoft = GreenTheme.color(R.color.zd_green_soft);

    private final int year;
    private final String uid;
    private final Runnable seasonPicker;
    private final Runnable personalReports;

    private final TextView leagueTab;
    private final TextView marketTab;
    private final FrameLayout contentHost;
    private final ZeytinLeagueView leagueView;
    private final MarketView marketView;

    private MarketUiDataProvider marketDataProvider;
    private MarketCloudRepository marketCloudRepository;
    private String selectedMarketGrade = "2 No";
    private String selectedMarketRegion = "";
    private String selectedMarketProvince = "";
    private String selectedMarketPeriod = MarketUiDataProvider.PERIOD_30_DAYS;
    private MarketView.MarketUiState lastMarketState;
    private MarketCloudRepository.Result lastCloudMarket;
    private final List<MarketCloudRepository.RegionRef> cloudMarketRegions = new ArrayList<>();
    private int marketRequestSerial;

    private boolean leagueSelected = true;

    public ZeytinLeagueContainerView(
            Context context,
            int year,
            String uid,
            Runnable seasonPicker,
            Runnable personalReports
    ) {
        super(context);

        this.year = year;
        this.uid = uid == null ? "" : uid;
        this.seasonPicker = seasonPicker == null ? () -> { } : seasonPicker;
        this.personalReports = personalReports == null ? () -> { } : personalReports;

        setOrientation(VERTICAL);
        setBackgroundColor(bg);
        setClipToPadding(false);

        LinearLayout segmented = new LinearLayout(context);
        segmented.setOrientation(HORIZONTAL);
        segmented.setGravity(Gravity.CENTER_VERTICAL);
        segmented.setPadding(dp(3), dp(3), dp(3), dp(3));
        segmented.setBackground(round(surface, 24, border));

        leagueTab = tab("Zeytin Ligi");
        marketTab = tab("Piyasa");

        segmented.addView(
                leagueTab,
                new LinearLayout.LayoutParams(0, dp(44), 1f)
        );
        segmented.addView(
                marketTab,
                new LinearLayout.LayoutParams(0, dp(44), 1f)
        );

        LinearLayout.LayoutParams segmentedLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(50)
        );
        segmentedLp.leftMargin = dp(14);
        segmentedLp.rightMargin = dp(14);
        segmentedLp.topMargin = dp(10);
        segmentedLp.bottomMargin = dp(6);
        addView(segmented, segmentedLp);

        contentHost = new FrameLayout(context);
        addView(
                contentHost,
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        leagueView = new ZeytinLeagueView(
                context,
                this.year,
                this.uid,
                this.seasonPicker,
                this.personalReports
        );

        marketView = new MarketView(
                context,
                this.year,
                this.uid
        );

        contentHost.addView(
                leagueView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );
        contentHost.addView(
                marketView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        leagueTab.setOnClickListener(v -> showLeague());
        marketTab.setOnClickListener(v -> showMarket());

        applySelection(true);
    }


    /**
     * Piyasa sekmesini MarketRepository üzerinden Elek kayıtlarına bağlar.
     * Constructor geriye dönük uyumlu kalır; bağlantı Activity tarafından yapılır.
     */
    public void configureMarket(
            MarketRepository repository,
            String initialRegion
    ) {
        configureMarket(repository, null, initialRegion, "");
    }

    /**
     * Yerel Elek Piyasa motorunu, anonim çok-kullanıcılı Supabase
     * toplulaştırmasıyla birleştirir. Bulut hazır değilse yerel görünüm aynen
     * çalışmaya devam eder.
     */
    public void configureMarket(
            MarketRepository repository,
            DatabaseHelper databaseHelper,
            String initialRegion,
            String initialProvince
    ) {
        if (repository == null) {
            return;
        }

        marketDataProvider = new MarketUiDataProvider(
                repository,
                year
        );

        selectedMarketRegion = initialRegion == null
                ? ""
                : initialRegion.trim();

        selectedMarketProvince = initialProvince == null
                ? ""
                : initialProvince.trim();

        if (databaseHelper != null) {
            leagueView.configureCloud(databaseHelper);

            marketCloudRepository = new MarketCloudRepository(
                    databaseHelper,
                    uid
            );

            marketCloudRepository.loadRegions(
                    year,
                    regions -> {
                        cloudMarketRegions.clear();
                        if (regions != null) cloudMarketRegions.addAll(regions);
                        MarketCloudRepository.RegionRef matched =
                                findCloudRegion(selectedMarketRegion, selectedMarketProvince);
                        if (matched != null) {
                            selectedMarketProvince = matched.province;
                        }
                    }
            );

        }

        marketView.setMarketEventListener(
                new MarketView.MarketEventListener() {
                    @Override
                    public void onGradeSelected(String grade) {
                        selectedMarketGrade = grade == null
                                ? "2 No"
                                : grade;
                        ensureRegionAvailable();
                        refreshMarketData();
                    }

                    @Override
                    public void onRegionFilterClicked() {
                        showRegionPicker();
                    }

                    @Override
                    public void onPeriodFilterClicked() {
                        showPeriodPicker();
                    }

                    @Override
                    public void onPeriodSelected(String period) {
                        if (period == null || period.equals(selectedMarketPeriod)) return;
                        selectedMarketPeriod = period;
                        ensureRegionAvailable();
                        refreshMarketData();
                    }

                    @Override
                    public void onCalculationInfoClicked() {
                        showCalculationInfo();
                    }
                }
        );

        ensureRegionAvailable();
        refreshMarketData();
    }

    public void refreshMarketData() {
        if (marketDataProvider == null) {
            return;
        }

        final int request = ++marketRequestSerial;

        MarketView.MarketUiState local =
                marketDataProvider.load(
                        selectedMarketGrade,
                        selectedMarketRegion,
                        selectedMarketPeriod
                );

        selectedMarketGrade = local.grade;
        selectedMarketRegion = local.selectedRegion;
        selectedMarketPeriod = local.selectedPeriod;
        lastMarketState = local;
        lastCloudMarket = null;

        marketView.bindState(local);
        marketView.setSourceCaption(
                "Senin elek kayıtların · bölge verisi yükleniyor"
        );

        if (marketCloudRepository == null
                || !marketCloudRepository.canUseCloud()) {
            return;
        }

        marketCloudRepository.load(
                year,
                selectedMarketRegion,
                selectedMarketProvince,
                selectedMarketGrade,
                selectedMarketPeriod,
                cloud -> {
                    if (request != marketRequestSerial || cloud == null) {
                        return;
                    }

                    lastCloudMarket = cloud;
                    marketView.setSourceCaption(cloud.available
                            ? cloud.status + " · " + safeRegionName(cloud.district) + "güncellendi "
                            + new java.text.SimpleDateFormat("HH:mm", new java.util.Locale("tr", "TR")).format(new java.util.Date())
                            : cloud.status);

                    if (!cloud.available) {
                        return;
                    }

                    MarketView.MarketUiState published =
                            marketDataProvider.applyPublished(
                                    local,
                                    cloud
                            );

                    if (published == null) {
                        return;
                    }

                    selectedMarketRegion = published.selectedRegion;
                    if (cloud.province != null && !cloud.province.trim().isEmpty()) {
                        selectedMarketProvince = cloud.province.trim();
                    }
                    lastMarketState = published;
                    marketView.bindState(published);
                }
        );
    }

    private void ensureRegionAvailable() {
        if (marketDataProvider == null) {
            return;
        }

        List<String> regions =
                marketDataProvider.getAvailableRegions(
                        selectedMarketGrade,
                        selectedMarketPeriod
                );

        if (regions.isEmpty()) {
            return;
        }

        for (String region : regions) {
            if (region.equalsIgnoreCase(selectedMarketRegion)) {
                return;
            }
        }

        selectedMarketRegion = regions.get(0);
    }

    private void showRegionPicker() {
        if (marketDataProvider == null) {
            return;
        }

        List<RegionChoice> choices = marketRegionChoices();

        if (choices.isEmpty()) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Bölge")
                    .setMessage("Bu elek ve dönem için yerel veya anonim bölgesel piyasa kaydı bulunmuyor.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }

        int checked = -1;
        String[] items = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            RegionChoice choice = choices.get(i);
            items[i] = choice.label;
            if (choice.region.equalsIgnoreCase(selectedMarketRegion)
                    && (selectedMarketProvince.isEmpty()
                    || choice.province.isEmpty()
                    || choice.province.equalsIgnoreCase(selectedMarketProvince))) {
                checked = i;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Bölge seç")
                .setSingleChoiceItems(
                        items,
                        checked,
                        (dialog, which) -> {
                            RegionChoice choice = choices.get(which);
                            selectedMarketRegion = choice.region;
                            if (!choice.province.isEmpty()) {
                                selectedMarketProvince = choice.province;
                            }
                            dialog.dismiss();
                            refreshMarketData();
                        }
                )
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private List<RegionChoice> marketRegionChoices() {
        Map<String, RegionChoice> values = new LinkedHashMap<>();

        List<String> local = marketDataProvider.getAvailableRegions(
                selectedMarketGrade,
                selectedMarketPeriod
        );
        for (String region : local) {
            RegionChoice choice = new RegionChoice();
            choice.region = region;
            choice.province = selectedMarketProvince;
            choice.label = region;
            values.put((region + "|" + choice.province).toLowerCase(), choice);
        }

        for (MarketCloudRepository.RegionRef region : cloudMarketRegions) {
            RegionChoice choice = new RegionChoice();
            choice.region = region.district;
            choice.province = region.province;
            choice.label = region.district + " / " + region.province;
            values.put((choice.region + "|" + choice.province).toLowerCase(), choice);
        }

        return new ArrayList<>(values.values());
    }

    private MarketCloudRepository.RegionRef findCloudRegion(
            String district,
            String province
    ) {
        for (MarketCloudRepository.RegionRef region : cloudMarketRegions) {
            if (region.district.equalsIgnoreCase(district == null ? "" : district.trim())
                    && (province == null || province.trim().isEmpty()
                    || region.province.equalsIgnoreCase(province.trim()))) {
                return region;
            }
        }
        return null;
    }

    private static final class RegionChoice {
        String region = "";
        String province = "";
        String label = "";
    }

    private static String safeRegionName(String district) {
        String d = district == null ? "" : district.trim();
        return d.isEmpty() ? "" : d + " · ";
    }

    private void showPeriodPicker() {
        if (marketDataProvider == null) {
            return;
        }

        List<String> periods =
                marketDataProvider.getPeriodOptions();

        int checked = -1;
        for (int i = 0; i < periods.size(); i++) {
            if (periods.get(i).equals(selectedMarketPeriod)) {
                checked = i;
                break;
            }
        }

        String[] items = periods.toArray(new String[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Dönem seç")
                .setSingleChoiceItems(
                        items,
                        checked,
                        (dialog, which) -> {
                            selectedMarketPeriod = items[which];
                            dialog.dismiss();
                            ensureRegionAvailable();
                            refreshMarketData();
                        }
                )
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showCalculationInfo() {
        if (lastMarketState == null) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Piyasa hesabı")
                    .setMessage("Henüz hesaplanmış piyasa verisi yok.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }

        int outlierCount = Math.max(0, lastMarketState.outlierCount);
        int acceptedCount = Math.max(0, lastMarketState.acceptedRecordCount);
        boolean cloud = lastCloudMarket != null && lastCloudMarket.available;

        String previousLine = lastMarketState.previousRecordCount > 0
                ? lastMarketState.previousMedianPrice
                        + " ("
                        + lastMarketState.previousRecordCount
                        + " kayıt)"
                : "Veri yok";

        String source = cloud
                ? "Anonim bölgesel ağ • " + lastCloudMarket.producerCount + " üretici"
                : "Bu cihazdaki Elek kayıtları • yerel önizleme";

        String summary =
                "Kaynak: " + source + "\n" +
                        "Kullanılan kayıt: " + acceptedCount + "\n" +
                        "Aykırı çıkarılan: " + outlierCount + "\n" +
                        "Medyan: " + lastMarketState.medianPrice + "\n" +
                        "Önceki dönem: " + previousLine + "\n" +
                        "Güven: " + lastMarketState.dataBadge
                        + " • " + lastMarketState.confidenceScore + "/100";

        String explanation =
                summary + "\n\n" +
                        "Piyasa fiyatı\n" +
                        (cloud
                                ? "En az " + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS
                                + " farklı üreticinin anonimleştirilmiş bölgesel kayıtlarından hesaplanır."
                                : "Seçtiğin elek, bölge ve dönemdeki kendi Elek fiyat kayıtlarının medyanıdır.") + "\n\n" +
                        "Gizlilik\n" +
                        "Firebase ile giriş yapan kullanıcıların Elek fiyat/kg kayıtları anonim bölgesel istatistiklere otomatik katılır. Ad, kullanıcı kimliği, bahçe adı ve tekil ham satış satırı yayımlanmaz. Küçük gruplar yayımlanmaz.\n\n" +
                        "Aykırı fiyatlar\n" +
                        "Yeterli veri olduğunda çok yüksek veya çok düşük fiyatlar piyasa hesabından çıkarılır.\n\n" +
                        "Grafik\n" +
                        "Anonim bulutta günlük nokta da ancak yeterli farklı üretici olduğunda yayımlanır.";

        new AlertDialog.Builder(getContext())
                .setTitle("Piyasa hesabı")
                .setMessage(explanation)
                .setNeutralButton(
                        "Teknik Ayrıntı",
                        (dialog, which) -> showTechnicalCalculationInfo()
                )
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void showTechnicalCalculationInfo() {
        if (lastMarketState == null) {
            return;
        }

        boolean cloud = lastCloudMarket != null && lastCloudMarket.available;
        String technical =
                "• Yerel kaynak: aktif hesap ve aktif sezondaki Elek kayıtlarıdır. Kg ve fiyatı sıfırdan büyük satırlar kullanılır.\n\n" +
                        "• Anonim ağ: Firebase ile giriş yapan hesapların Elek fiyat/kg satırları Supabase güvenli RPC katmanında sunucuda anonim üretici kimliğine dönüştürülür. Ham Firebase UID Piyasa tablosuna yazılmaz ve istemci başka üreticilerin ham satırlarını okuyamaz.\n\n" +
                        "• Gizlilik eşiği: bölgesel fiyat ancak en az "
                        + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS
                        + " farklı üretici olduğunda yayımlanır. Günlük grafik noktası için de en az "
                        + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS
                        + " farklı üretici gerekir.\n\n" +
                        "• Üretici dengesi: aynı hesap çok sayıda kayıt girse bile bölgesel fiyat medyanında bir üretici bir oy taşır. Önce her üreticinin kendi medyanı, sonra bölgesel medyan hesaplanır.\n\n" +
                        "• Medyan: üretici medyanları küçükten büyüğe sıralanır; ortadaki değer piyasa fiyatı kabul edilir.\n\n" +
                        "• Aykırı filtre: yeterli üretici olduğunda IQR yöntemi üretici medyanlarına uygulanır.\n\n" +
                        "• Anonim fiyat aralığı: yayınlanan üretici medyanlarının güvenli min–max aralığı gösterilir; tekil ham üretici satırları istemciye verilmez.\n\n" +
                        "• Önceki dönem: Son 7/30/90 Gün seçiminde aynı uzunluktaki bir önceki anonim dönem medyanıyla karşılaştırılır.\n\n" +
                        "• Bulut sonucu mevcut değilse ekran güvenli biçimde yerel önizlemeye düşer.\n\n" +
                        lastMarketState.confidenceDetails;

        new AlertDialog.Builder(getContext())
                .setTitle(cloud
                        ? "Anonim bölgesel hesaplama"
                        : "Yerel hesaplama ayrıntıları")
                .setMessage(technical)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private TextView tab(String title) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextSize(14);
        view.setTextColor(muted);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        view.setBackground(transparentRound(21));
        return view;
    }

    private void showLeague() {
        if (leagueSelected) {
            return;
        }
        applySelection(true);
    }

    /** Dışarıdan (ör. Sohbet'teki bölge piyasası şeridi) Piyasa sekmesini açar. */
    public void openMarketTab() {
        showMarket();
    }

    private void showMarket() {
        if (!leagueSelected) {
            return;
        }
        refreshMarketData();
        applySelection(false);
    }

    private void applySelection(boolean showLeague) {
        leagueSelected = showLeague;

        leagueView.setVisibility(showLeague ? View.VISIBLE : View.GONE);
        marketView.setVisibility(showLeague ? View.GONE : View.VISIBLE);

        leagueTab.setTextColor(showLeague ? text : muted);
        marketTab.setTextColor(showLeague ? muted : text);

        leagueTab.setTypeface(
                Typeface.DEFAULT,
                showLeague ? Typeface.BOLD : Typeface.NORMAL
        );
        marketTab.setTypeface(
                Typeface.DEFAULT,
                showLeague ? Typeface.NORMAL : Typeface.BOLD
        );

        leagueTab.setBackground(
                showLeague ? activeTabBackground() : transparentRound(21)
        );
        marketTab.setBackground(
                showLeague ? transparentRound(21) : activeTabBackground()
        );

        leagueTab.setSelected(showLeague);
        marketTab.setSelected(!showLeague);
    }

    private GradientDrawable activeTabBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{greenSoft, greenMid}
        );
        drawable.setCornerRadius(dp(21));
        drawable.setStroke(dp(1), green);
        return drawable;
    }

    private GradientDrawable transparentRound(float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00000000);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), 0x00000000);
        return drawable;
    }

    private GradientDrawable round(int fill, float radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
