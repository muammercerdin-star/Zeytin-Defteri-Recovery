package com.zeytindefteri.app;

import android.database.Cursor;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MarketRepository üzerinden Elek ekranındaki elek_records + elek_grades
 * satış/fiyat kayıtlarını MarketView.MarketUiState biçimine dönüştürür.
 *
 * UI bilmez, View oluşturmaz, Firebase çağrısı yapmaz.
 * Aktif hesap + aktif sezon kapsamındaki yerel Elek kayıtlarıyla çalışır.
 * Ayrı sales/sale_grades tablosuna ihtiyaç duymaz.
 */
public final class MarketUiDataProvider {

    public static final String PERIOD_7_DAYS = "Son 7 Gün";
    public static final String PERIOD_30_DAYS = "Son 30 Gün";
    public static final String PERIOD_90_DAYS = "Son 90 Gün";
    public static final String PERIOD_SEASON = "Bu Sezon";

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private static final List<String> UI_GRADES = Arrays.asList(
            "Double",
            "1 No",
            "2 No",
            "3 No",
            "4 No",
            "Yağlık"
    );

    private final MarketRepository repository;
    private final int seasonYear;
    private final Locale tr = new Locale("tr", "TR");

    public MarketUiDataProvider(
            MarketRepository repository,
            int seasonYear
    ) {
        this.repository = repository;
        this.seasonYear = seasonYear;
    }

    public List<String> getPeriodOptions() {
        return Arrays.asList(
                PERIOD_7_DAYS,
                PERIOD_30_DAYS,
                PERIOD_90_DAYS,
                PERIOD_SEASON
        );
    }

    public List<String> getAvailableRegions(
            String uiGrade,
            String period
    ) {
        String dbGrade = toDatabaseGrade(uiGrade);
        List<SaleRow> rows = loadMarketRows(dbGrade, null);
        rows = filterCurrentPeriod(rows, period);

        Map<String, RegionBucket> buckets = groupRegions(rows);
        List<RegionBucket> ordered = new ArrayList<>(buckets.values());
        Collections.sort(
                ordered,
                (a, b) -> Double.compare(b.totalKg, a.totalKg)
        );

        List<String> result = new ArrayList<>();
        for (RegionBucket bucket : ordered) {
            if (!isBlank(bucket.displayName)) {
                result.add(bucket.displayName);
            }
        }
        return result;
    }

    public MarketView.MarketUiState load(
            String uiGrade,
            String requestedRegion,
            String period
    ) {
        String safeGrade = normalizeUiGrade(uiGrade);
        String safePeriod = normalizePeriod(period);
        String dbGrade = toDatabaseGrade(safeGrade);

        List<SaleRow> allMarketRows = loadMarketRows(dbGrade, null);
        List<SaleRow> periodMarketRows = filterCurrentPeriod(allMarketRows, safePeriod);

        String selectedRegion = trim(requestedRegion);
        if (selectedRegion.isEmpty()) {
            selectedRegion = chooseDefaultRegion(periodMarketRows);
        }
        if (selectedRegion.isEmpty()) {
            selectedRegion = "Bölge";
        }

        List<SaleRow> selectedMarketRows = filterRegion(periodMarketRows, selectedRegion);
        List<SaleRow> acceptedMarketRows = filterOutliers(selectedMarketRows);

        MarketStats marketStats = statsAccepted(acceptedMarketRows);
        marketStats.rawSaleCount = distinctSaleCount(selectedMarketRows);
        marketStats.outlierCount = Math.max(0, marketStats.rawSaleCount - marketStats.saleCount);

        String medianPrice = marketStats.saleCount > 0
                ? formatPrice(marketStats.median)
                : "—";

        String range = marketStats.saleCount > 0
                ? formatPriceCompact(marketStats.min)
                + " — "
                + formatPriceCompact(marketStats.max)
                : "Veri yok";

        String salesCount = marketStats.saleCount + " kayıt";
        String totalKg = formatKg(marketStats.totalKg);

        TrendInfo trend = buildTrendInfo(
                allMarketRows,
                selectedRegion,
                safePeriod,
                marketStats.median,
                marketStats.saleCount
        );

        ConfidenceInfo confidence = buildConfidenceInfo(
                acceptedMarketRows,
                marketStats.rawSaleCount,
                marketStats.outlierCount
        );

        String change = trend.text;
        String badge = confidence.label;

        ChartData chart = buildChart(acceptedMarketRows);

        List<MarketView.RegionUiState> regions = buildRegionStates(
                periodMarketRows,
                selectedRegion
        );

        List<SaleRow> ownRows = loadOwnRows(dbGrade);
        SaleRow ownLatest = latestOwnSale(ownRows, selectedRegion);

        String ownSale = ownLatest == null
                ? "—"
                : formatPricePerKg(ownLatest.price);

        String marketSale = marketStats.saleCount > 0
                ? formatPricePerKg(marketStats.median)
                : "—";

        String comparison = buildComparison(
                ownLatest,
                marketStats
        );

        return new MarketView.MarketUiState(
                new ArrayList<>(UI_GRADES),
                safeGrade,
                selectedRegion,
                safePeriod,
                medianPrice,
                range,
                change,
                salesCount,
                totalKg,
                badge,
                chart.values,
                chart.startLabel,
                chart.endLabel,
                regions,
                ownSale,
                marketSale,
                comparison,
                marketStats.rawSaleCount,
                marketStats.outlierCount,
                marketStats.saleCount,
                trend.previousRecordCount,
                trend.previousMedian > 0
                        ? formatPrice(trend.previousMedian)
                        : "—",
                confidence.score,
                confidence.details
        );
    }

    /**
     * Sunucuda anonim olarak toplulaştırılmış bölgesel sonucu, yerel state'in
     * üzerine uygular. Kişinin kendi son satış/fiyatı cihazdaki Elek kaydından
     * kalır; başka üreticilerin ham satırları telefona hiç gelmez.
     */
    public MarketView.MarketUiState applyPublished(
            MarketView.MarketUiState local,
            MarketCloudRepository.Result cloud
    ) {
        if (local == null || cloud == null || !cloud.available) {
            return local;
        }

        String selectedRegion = isBlank(cloud.district)
                ? local.selectedRegion
                : cloud.district;

        String medianPrice = formatPrice(cloud.median);
        String range = formatPriceCompact(cloud.rangeLow)
                + " — "
                + formatPriceCompact(cloud.rangeHigh);

        String change;
        int days = periodDays(local.selectedPeriod);
        if (days <= 0) {
            change = seasonYear
                    + " sezonu • "
                    + cloud.recordCount
                    + " kayıt • "
                    + cloud.producerCount
                    + " üretici";
        } else if (cloud.previousAvailable && cloud.previousMedian > 0) {
            String diffPrefix = cloud.changeAbs > 0 ? "+" : "";
            String percentPrefix = cloud.changePct > 0 ? "+" : "";
            change = diffPrefix
                    + formatNumber(cloud.changeAbs, 2)
                    + " TL • önceki "
                    + days
                    + " güne göre "
                    + percentPrefix
                    + "%"
                    + formatNumber(cloud.changePct, 2);
        } else {
            change = "Önceki " + days + " günde anonim veri yok";
        }

        float[] chartValues = new float[cloud.chart.size()];
        String chartStart = "";
        String chartEnd = "";
        for (int i = 0; i < cloud.chart.size(); i++) {
            MarketCloudRepository.ChartPoint point = cloud.chart.get(i);
            chartValues[i] = (float) point.price;
            String label = cloudDayLabel(point.day);
            if (i == 0) chartStart = label;
            if (i == cloud.chart.size() - 1) chartEnd = label;
        }
        if (!cloud.chart.isEmpty()) {
            MarketCloudRepository.ChartPoint last = cloud.chart.get(cloud.chart.size() - 1);
            long lastTime = parseSaleTime(last.day, 0L);
            if (isToday(lastTime)) chartEnd = "Bugün";
        }

        double maxMedian = 0d;
        for (MarketCloudRepository.RegionStat item : cloud.regions) {
            maxMedian = Math.max(maxMedian, item.median);
        }
        List<MarketView.RegionUiState> regions = new ArrayList<>();
        for (MarketCloudRepository.RegionStat item : cloud.regions) {
            float ratio = maxMedian <= 0
                    ? 0f
                    : (float) (item.median / maxMedian);
            regions.add(
                    new MarketView.RegionUiState(
                            item.name,
                            formatPrice(item.median),
                            ratio
                    )
            );
        }

        String dbGrade = toDatabaseGrade(local.grade);
        List<SaleRow> ownRows = loadOwnRows(dbGrade);
        SaleRow ownLatest = latestOwnSale(ownRows, selectedRegion);
        String ownSale = ownLatest == null
                ? "—"
                : formatPricePerKg(ownLatest.price);

        MarketStats cloudStats = new MarketStats();
        cloudStats.saleCount = cloud.recordCount;
        cloudStats.median = cloud.median;

        String comparison = buildComparison(ownLatest, cloudStats);
        String marketSale = formatPricePerKg(cloud.median);

        String details = "Anonim bölgesel ağ • "
                + cloud.producerCount
                + " üretici • "
                + cloud.recordCount
                + " toplulaştırılmış kayıt • "
                + "ham üretici kayıtları telefona indirilmez";

        return new MarketView.MarketUiState(
                new ArrayList<>(UI_GRADES),
                local.grade,
                selectedRegion,
                local.selectedPeriod,
                medianPrice,
                range,
                change,
                cloud.recordCount + " kayıt",
                formatKg(cloud.totalKg),
                cloud.confidenceLabel,
                chartValues,
                chartStart,
                chartEnd,
                regions,
                ownSale,
                marketSale,
                comparison,
                cloud.recordCount + cloud.outlierCount,
                cloud.outlierCount,
                cloud.recordCount,
                cloud.previousRecordCount,
                cloud.previousAvailable
                        ? formatPrice(cloud.previousMedian)
                        : "—",
                cloud.confidenceScore,
                details
        );
    }

    private String cloudDayLabel(String day) {
        if (isBlank(day)) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            input.setLenient(false);
            Date date = input.parse(day);
            if (date == null) return day;
            return new SimpleDateFormat("d MMM", tr).format(date);
        } catch (Exception ignored) {
            return day;
        }
    }

    private List<SaleRow> loadMarketRows(
            String dbGrade,
            String region
    ) {
        Cursor cursor = null;
        List<SaleRow> result = new ArrayList<>();

        try {
            if (isBlank(region)) {
                cursor = repository.getScopedMarketSalesByGrade(dbGrade);
            } else {
                cursor = repository.getScopedMarketSalesByGradeAndRegion(
                        dbGrade,
                        region
                );
            }

            readRows(cursor, result);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    private List<SaleRow> loadOwnRows(
            String dbGrade
    ) {
        Cursor cursor = null;
        List<SaleRow> result = new ArrayList<>();

        try {
            cursor = repository.getScopedOwnSalesByGrade(dbGrade);
            readRows(cursor, result);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    private void readRows(
            Cursor cursor,
            List<SaleRow> target
    ) {
        if (cursor == null) {
            return;
        }

        while (cursor.moveToNext()) {
            double price = getDouble(cursor, "price_per_kg");
            double kg = getDouble(cursor, "kg");

            if (price <= 0 || kg <= 0) {
                continue;
            }

            SaleRow row = new SaleRow();
            row.saleId = getLong(cursor, "sale_id");
            row.saleDate = getString(cursor, "sale_date");
            row.region = getString(cursor, "region");
            row.createdAt = getLong(cursor, "created_at");
            row.price = price;
            row.kg = kg;
            row.timestamp = parseSaleTime(row.saleDate, row.createdAt);

            target.add(row);
        }
    }

    private List<SaleRow> filterCurrentPeriod(
            List<SaleRow> source,
            String period
    ) {
        int days = periodDays(period);
        if (days <= 0) {
            return new ArrayList<>(source);
        }

        long now = System.currentTimeMillis();
        long cutoff = now - days * DAY_MS;
        List<SaleRow> result = new ArrayList<>();

        for (SaleRow row : source) {
            if (row.timestamp >= cutoff && row.timestamp <= now + DAY_MS) {
                result.add(row);
            }
        }
        return result;
    }

    private List<SaleRow> filterPreviousPeriod(
            List<SaleRow> source,
            String period
    ) {
        int days = periodDays(period);
        if (days <= 0) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        long currentStart = now - days * DAY_MS;
        long previousStart = now - (2L * days * DAY_MS);
        List<SaleRow> result = new ArrayList<>();

        for (SaleRow row : source) {
            if (row.timestamp >= previousStart && row.timestamp < currentStart) {
                result.add(row);
            }
        }
        return result;
    }

    private List<SaleRow> filterRegion(
            List<SaleRow> source,
            String region
    ) {
        if (isBlank(region) || "Bölge".equalsIgnoreCase(region)) {
            return new ArrayList<>(source);
        }

        List<SaleRow> result = new ArrayList<>();
        for (SaleRow row : source) {
            if (sameRegion(row.region, region)) {
                result.add(row);
            }
        }
        return result;
    }

    private String chooseDefaultRegion(
            List<SaleRow> rows
    ) {
        Map<String, RegionBucket> grouped = groupRegions(rows);
        RegionBucket best = null;

        for (RegionBucket bucket : grouped.values()) {
            if (best == null || bucket.totalKg > best.totalKg) {
                best = bucket;
            }
        }

        return best == null ? "" : best.displayName;
    }

    private Map<String, RegionBucket> groupRegions(
            List<SaleRow> rows
    ) {
        Map<String, RegionBucket> grouped = new LinkedHashMap<>();

        for (SaleRow row : rows) {
            String display = trim(row.region);
            if (display.isEmpty()) {
                continue;
            }

            String key = display.toLowerCase(tr);
            RegionBucket bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new RegionBucket();
                bucket.displayName = display;
                grouped.put(key, bucket);
            }

            bucket.rows.add(row);
            bucket.totalKg += row.kg;
        }

        return grouped;
    }

    /**
     * Aykırı değer filtresinden geçmiş kayıtların istatistiğini hesaplar.
     * Bu metot kendi başına filtre uygulamaz; böylece grafik, medyan, min/max
     * ve bölge kartları aynı kabul edilmiş veri kümesini kullanır.
     */
    private MarketStats statsAccepted(
            List<SaleRow> rows
    ) {
        MarketStats out = new MarketStats();
        if (rows == null || rows.isEmpty()) {
            return out;
        }

        List<Double> prices = new ArrayList<>();
        Set<Long> saleIds = new LinkedHashSet<>();
        double totalKg = 0;

        for (SaleRow row : rows) {
            if (row.price <= 0 || row.kg <= 0) {
                continue;
            }
            prices.add(row.price);
            totalKg += row.kg;
            if (row.saleId > 0) {
                saleIds.add(row.saleId);
            }
        }

        if (prices.isEmpty()) {
            return out;
        }

        Collections.sort(prices);
        out.median = median(prices);
        out.min = prices.get(0);
        out.max = prices.get(prices.size() - 1);
        out.totalKg = totalKg;
        out.saleCount = saleIds.isEmpty() ? prices.size() : saleIds.size();
        return out;
    }

    /**
     * Piyasa güven filtresi.
     *
     * 6 kayıttan az veri olduğunda küçük örneklemi bozmamak için filtre yoktur.
     * 6+ kayıtta IQR (çeyrekler arası aralık) kullanılır. IQR sıfırsa, aynı
     * fiyatta yoğunlaşmış veri setinde yalnızca çok uzak fiyatları elemek için
     * korumacı bir medyan bandı kullanılır.
     *
     * Kullanıcının kendi son satışı bu metotla silinmez; yalnızca bölgesel
     * piyasa medyanı, aralık, grafik ve bölge karşılaştırmaları korunur.
     */
    private List<SaleRow> filterOutliers(
            List<SaleRow> source
    ) {
        if (source == null || source.size() < 6) {
            return source == null
                    ? new ArrayList<>()
                    : new ArrayList<>(source);
        }

        List<Double> prices = new ArrayList<>();
        for (SaleRow row : source) {
            if (row != null && row.price > 0 && row.kg > 0) {
                prices.add(row.price);
            }
        }

        if (prices.size() < 6) {
            return new ArrayList<>(source);
        }

        Collections.sort(prices);
        double median = median(prices);
        double q1 = percentileMedian(prices, true);
        double q3 = percentileMedian(prices, false);
        double iqr = q3 - q1;

        double lower;
        double upper;

        if (iqr > 0.005d) {
            lower = q1 - (1.5d * iqr);
            upper = q3 + (1.5d * iqr);
        } else {
            // Örnek: 85,85,85,85,85,85,85,85,850.
            // IQR=0 olsa bile bariz veri giriş hatasını dışarıda bırak.
            double guard = Math.max(10d, Math.abs(median) * 0.20d);
            lower = median - guard;
            upper = median + guard;
        }

        List<SaleRow> accepted = new ArrayList<>();
        for (SaleRow row : source) {
            if (row == null || row.price <= 0 || row.kg <= 0) {
                continue;
            }
            if (row.price >= lower && row.price <= upper) {
                accepted.add(row);
            }
        }

        // Güvenlik: filtre veri setinin çoğunu yanlışlıkla silerse ham veriye dön.
        // Amaç piyasayı korumak, küçük/veri çeşitliliği yüksek kümeyi yok etmek değil.
        int minimumAccepted = Math.max(3, (int) Math.ceil(prices.size() * 0.60d));
        if (accepted.size() < minimumAccepted) {
            return new ArrayList<>(source);
        }

        return accepted;
    }

    private double percentileMedian(
            List<Double> sorted,
            boolean lowerHalf
    ) {
        if (sorted == null || sorted.isEmpty()) {
            return 0d;
        }

        int n = sorted.size();
        int mid = n / 2;
        List<Double> half;

        if (lowerHalf) {
            half = sorted.subList(0, mid);
        } else {
            half = sorted.subList((n & 1) == 0 ? mid : mid + 1, n);
        }

        if (half.isEmpty()) {
            return sorted.get(mid);
        }

        return median(half);
    }

    private int distinctSaleCount(
            List<SaleRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        Set<Long> ids = new LinkedHashSet<>();
        int anonymous = 0;
        for (SaleRow row : rows) {
            if (row == null || row.price <= 0 || row.kg <= 0) {
                continue;
            }
            if (row.saleId > 0) {
                ids.add(row.saleId);
            } else {
                anonymous++;
            }
        }
        return ids.size() + anonymous;
    }

    private TrendInfo buildTrendInfo(
            List<SaleRow> allMarketRows,
            String region,
            String period,
            double currentMedian,
            int currentCount
    ) {
        TrendInfo out = new TrendInfo();

        if (currentCount <= 0) {
            out.text = "Bu dönemde veri yok";
            return out;
        }

        int days = periodDays(period);
        if (days <= 0) {
            out.text = seasonYear + " sezonu • " + currentCount + " kayıt";
            return out;
        }

        List<SaleRow> previous = filterRegion(
                filterPreviousPeriod(allMarketRows, period),
                region
        );
        List<SaleRow> acceptedPrevious = filterOutliers(previous);
        MarketStats previousStats = statsAccepted(acceptedPrevious);

        out.previousRecordCount = previousStats.saleCount;
        out.previousMedian = previousStats.median;

        if (previousStats.saleCount <= 0 || previousStats.median <= 0) {
            out.text = "Önceki " + days + " günde veri yok";
            return out;
        }

        double diff = currentMedian - previousStats.median;
        double percent = (diff / previousStats.median) * 100d;

        String diffPrefix = diff > 0 ? "+" : "";
        String percentPrefix = percent > 0 ? "+" : "";

        out.text = diffPrefix
                + formatNumber(diff, 2)
                + " TL • önceki "
                + days
                + " güne göre "
                + percentPrefix
                + "%"
                + formatNumber(percent, 2);

        return out;
    }

    /**
     * Güven puanı yalnızca kayıt sayısına bakmaz.
     * 100 puanın bileşenleri:
     * - kayıt sayısı: 45
     * - güncellik: 20
     * - fiyatların birbirine yakınlığı (IQR/medyan): 25
     * - aykırı kayıt oranı: 10
     *
     * Kayıt sayısı yine üst sınırdır: örneğin 3 kayıt ne kadar temiz olursa
     * olsun "Güvenilir Veri" seviyesine çıkamaz. Kalite ölçütleri gerekirse
     * güven seviyesini aşağı indirir.
     */
    private ConfidenceInfo buildConfidenceInfo(
            List<SaleRow> acceptedRows,
            int rawCount,
            int outlierCount
    ) {
        ConfidenceInfo out = new ConfidenceInfo();

        int acceptedCount = distinctSaleCount(acceptedRows);
        if (acceptedCount <= 0) {
            out.label = "Veri Yok";
            out.score = 0;
            out.details = "Puan 0/100 • hesaplanabilir kayıt yok";
            return out;
        }

        int countScore;
        if (acceptedCount >= 30) countScore = 45;
        else if (acceptedCount >= 15) countScore = 36;
        else if (acceptedCount >= 10) countScore = 30;
        else if (acceptedCount >= 6) countScore = 23;
        else if (acceptedCount >= 3) countScore = 16;
        else if (acceptedCount == 2) countScore = 10;
        else countScore = 5;

        long newest = 0L;
        List<Double> prices = new ArrayList<>();
        if (acceptedRows != null) {
            for (SaleRow row : acceptedRows) {
                if (row == null || row.price <= 0 || row.kg <= 0) continue;
                prices.add(row.price);
                newest = Math.max(newest, row.timestamp);
            }
        }

        long ageDays = newest <= 0
                ? -1L
                : Math.max(0L, (System.currentTimeMillis() - newest) / DAY_MS);

        int recencyScore;
        if (ageDays < 0) recencyScore = 2;
        else if (ageDays <= 3) recencyScore = 20;
        else if (ageDays <= 7) recencyScore = 18;
        else if (ageDays <= 14) recencyScore = 15;
        else if (ageDays <= 30) recencyScore = 12;
        else if (ageDays <= 60) recencyScore = 7;
        else recencyScore = 3;

        Collections.sort(prices);
        double median = median(prices);
        double q1 = prices.size() >= 2 ? percentileMedian(prices, true) : median;
        double q3 = prices.size() >= 2 ? percentileMedian(prices, false) : median;
        double spreadPercent = median <= 0
                ? 100d
                : Math.max(0d, ((q3 - q1) / median) * 100d);

        int dispersionScore;
        if (acceptedCount < 3) {
            dispersionScore = 8;
        } else {
            if (spreadPercent <= 5d) dispersionScore = 25;
            else if (spreadPercent <= 10d) dispersionScore = 22;
            else if (spreadPercent <= 15d) dispersionScore = 18;
            else if (spreadPercent <= 25d) dispersionScore = 13;
            else if (spreadPercent <= 40d) dispersionScore = 8;
            else dispersionScore = 4;

            if (acceptedCount < 6) {
                dispersionScore = Math.min(dispersionScore, 15);
            }
        }

        int safeRaw = Math.max(rawCount, acceptedCount + Math.max(0, outlierCount));
        double outlierRatio = safeRaw <= 0
                ? 0d
                : (Math.max(0, outlierCount) * 100d) / safeRaw;

        int outlierScore;
        if (outlierRatio <= 0.01d) outlierScore = 10;
        else if (outlierRatio <= 10d) outlierScore = 8;
        else if (outlierRatio <= 20d) outlierScore = 6;
        else if (outlierRatio <= 30d) outlierScore = 3;
        else outlierScore = 0;

        int score = Math.max(0, Math.min(100,
                countScore + recencyScore + dispersionScore + outlierScore));

        int countRank;
        if (acceptedCount >= 30) countRank = 4;
        else if (acceptedCount >= 15) countRank = 3;
        else if (acceptedCount >= 6) countRank = 2;
        else if (acceptedCount >= 3) countRank = 1;
        else countRank = 0;

        int qualityRank;
        if (score >= 85) qualityRank = 4;
        else if (score >= 70) qualityRank = 3;
        else if (score >= 50) qualityRank = 2;
        else if (score >= 35) qualityRank = 1;
        else qualityRank = 0;

        int finalRank = Math.min(countRank, qualityRank);
        String[] labels = {
                "Çok Az Veri",
                "Sınırlı Veri",
                "Orta Güven",
                "Güvenilir Veri",
                "Yüksek Güven"
        };

        out.label = labels[finalRank];
        out.score = score;

        String recencyText = ageDays < 0
                ? "tarih belirsiz"
                : ageDays == 0
                ? "son kayıt bugün"
                : "son kayıt " + ageDays + " gün önce";

        out.details = "Puan "
                + score
                + "/100 • "
                + recencyText
                + " • fiyat yayılımı %"
                + formatNumber(spreadPercent, 1)
                + " • aykırı oranı %"
                + formatNumber(outlierRatio, 1);

        return out;
    }

    private List<MarketView.RegionUiState> buildRegionStates(
            List<SaleRow> rows,
            String selectedRegion
    ) {
        Map<String, RegionBucket> grouped = groupRegions(rows);
        List<RegionBucket> buckets = new ArrayList<>(grouped.values());

        for (RegionBucket bucket : buckets) {
            List<SaleRow> accepted = filterOutliers(bucket.rows);
            MarketStats stats = statsAccepted(accepted);
            bucket.median = stats.median;
            bucket.saleCount = stats.saleCount;
        }

        Collections.sort(
                buckets,
                (a, b) -> Double.compare(b.totalKg, a.totalKg)
        );

        if (buckets.isEmpty()) {
            return new ArrayList<>();
        }

        List<RegionBucket> visible = new ArrayList<>();
        for (int i = 0; i < buckets.size() && i < 3; i++) {
            visible.add(buckets.get(i));
        }

        if (!isBlank(selectedRegion)) {
            RegionBucket selected = null;
            boolean alreadyVisible = false;

            for (RegionBucket bucket : buckets) {
                if (sameRegion(bucket.displayName, selectedRegion)) {
                    selected = bucket;
                    break;
                }
            }

            if (selected != null) {
                for (RegionBucket bucket : visible) {
                    if (sameRegion(bucket.displayName, selectedRegion)) {
                        alreadyVisible = true;
                        break;
                    }
                }

                if (!alreadyVisible) {
                    if (visible.size() >= 3) {
                        visible.set(visible.size() - 1, selected);
                    } else {
                        visible.add(selected);
                    }
                }
            }
        }

        double maxMedian = 0;
        for (RegionBucket bucket : buckets) {
            maxMedian = Math.max(maxMedian, bucket.median);
        }

        List<MarketView.RegionUiState> result = new ArrayList<>();
        for (RegionBucket bucket : visible) {
            float ratio = maxMedian <= 0
                    ? 0f
                    : (float) (bucket.median / maxMedian);

            result.add(
                    new MarketView.RegionUiState(
                            bucket.displayName,
                            bucket.median > 0
                                    ? formatPrice(bucket.median)
                                    : "—",
                            ratio
                    )
            );
        }

        return result;
    }

    /**
     * Grafik her ham kaydı ayrı nokta yapmak yerine aynı günün fiyatlarını
     * tek günlük medyanda birleştirir. Böylece bir günde çok kayıt olması
     * grafiği gereksiz zikzaklı veya yoğun göstermez.
     */
    private ChartData buildChart(
            List<SaleRow> rows
    ) {
        ChartData out = new ChartData();
        if (rows == null || rows.isEmpty()) {
            return out;
        }

        List<SaleRow> ordered = new ArrayList<>(rows);
        Collections.sort(ordered, Comparator.comparingLong(a -> a.timestamp));

        Map<String, DailyBucket> days = new LinkedHashMap<>();
        for (SaleRow row : ordered) {
            String key = dayKey(row);
            DailyBucket bucket = days.get(key);
            if (bucket == null) {
                bucket = new DailyBucket();
                bucket.timestamp = row.timestamp;
                bucket.label = dateLabel(row);
                days.put(key, bucket);
            }
            bucket.prices.add(row.price);
            if (row.timestamp > 0) {
                bucket.timestamp = row.timestamp;
            }
        }

        List<DailyBucket> daily = new ArrayList<>(days.values());
        if (daily.size() > 30) {
            daily = new ArrayList<>(daily.subList(daily.size() - 30, daily.size()));
        }

        out.values = new float[daily.size()];
        for (int i = 0; i < daily.size(); i++) {
            DailyBucket bucket = daily.get(i);
            Collections.sort(bucket.prices);
            out.values[i] = (float) median(bucket.prices);
        }

        if (!daily.isEmpty()) {
            DailyBucket first = daily.get(0);
            DailyBucket last = daily.get(daily.size() - 1);
            out.startLabel = first.label;
            out.endLabel = isToday(last.timestamp) ? "Bugün" : last.label;
        }

        return out;
    }

    private String dayKey(
            SaleRow row
    ) {
        if (row != null && row.timestamp > 0) {
            return new SimpleDateFormat("yyyy-MM-dd", tr)
                    .format(new Date(row.timestamp));
        }
        return row == null ? "" : trim(row.saleDate);
    }

    private SaleRow latestOwnSale(
            List<SaleRow> rows,
            String region
    ) {
        SaleRow bestRegion = null;
        SaleRow bestAny = null;

        for (SaleRow row : rows) {
            if (bestAny == null || row.timestamp > bestAny.timestamp) {
                bestAny = row;
            }

            if (sameRegion(row.region, region)) {
                if (bestRegion == null || row.timestamp > bestRegion.timestamp) {
                    bestRegion = row;
                }
            }
        }

        return bestRegion != null ? bestRegion : bestAny;
    }

    private String buildComparison(
            SaleRow ownLatest,
            MarketStats marketStats
    ) {
        if (ownLatest == null) {
            return "Bu elekte henüz satış kaydın yok.";
        }

        if (marketStats.saleCount <= 0 || marketStats.median <= 0) {
            return "Karşılaştırmak için piyasa verisi yok.";
        }

        double diff = ownLatest.price - marketStats.median;
        if (Math.abs(diff) < 0.005d) {
            return "Piyasa medyanıyla aynı fiyata satmışsın.";
        }

        if (diff > 0) {
            return "Piyasanın "
                    + formatNumber(diff, 2)
                    + " TL üzerinde satmışsın.";
        }

        return "Piyasanın "
                + formatNumber(Math.abs(diff), 2)
                + " TL altında satmışsın.";
    }

    private int periodDays(
            String period
    ) {
        if (PERIOD_7_DAYS.equals(period)) {
            return 7;
        }
        if (PERIOD_30_DAYS.equals(period)) {
            return 30;
        }
        if (PERIOD_90_DAYS.equals(period)) {
            return 90;
        }
        return 0;
    }

    private String normalizePeriod(
            String period
    ) {
        if (PERIOD_7_DAYS.equals(period)
                || PERIOD_30_DAYS.equals(period)
                || PERIOD_90_DAYS.equals(period)
                || PERIOD_SEASON.equals(period)) {
            return period;
        }
        return PERIOD_30_DAYS;
    }

    private String normalizeUiGrade(
            String value
    ) {
        String grade = trim(value);
        for (String candidate : UI_GRADES) {
            if (candidate.equalsIgnoreCase(grade)) {
                return candidate;
            }
        }

        // Veritabanında kullanılan eski/kanonik kodları da UI adına çevir.
        if ("DOUBLE".equalsIgnoreCase(grade)) return "Double";
        if ("YAGLIK".equalsIgnoreCase(grade)
                || "YAĞLIK".equalsIgnoreCase(grade)) return "Yağlık";
        if ("1".equals(grade)) return "1 No";
        if ("2".equals(grade)) return "2 No";
        if ("3".equals(grade)) return "3 No";
        if ("4".equals(grade)) return "4 No";
        return "2 No";
    }

    /**
     * MarketView kullanıcı dostu adları gösterir; elek_grades tablosu ise
     * mevcut Elek kayıtlarının kanonik kodlarını saklar. Bu dönüşüm tek noktada
     * tutulur ki Double/Yağlık kayıtları yanlış isimle sorgulanmasın.
     */
    private String toDatabaseGrade(
            String uiGrade
    ) {
        String grade = normalizeUiGrade(uiGrade);

        if ("Double".equalsIgnoreCase(grade)) {
            return "DOUBLE";
        }

        if ("Yağlık".equalsIgnoreCase(grade)) {
            return "YAGLIK";
        }

        if (grade.endsWith(" No")) {
            return grade.substring(0, grade.length() - 3).trim();
        }

        return grade;
    }

    private double median(
            List<Double> sorted
    ) {
        if (sorted == null || sorted.isEmpty()) {
            return 0;
        }

        int size = sorted.size();
        if ((size & 1) == 1) {
            return sorted.get(size / 2);
        }
        return (
                sorted.get(size / 2 - 1)
                        + sorted.get(size / 2)
        ) / 2.0d;
    }

    private long parseSaleTime(
            String raw,
            long fallback
    ) {
        String date = trim(raw);
        String[] formats = {
                "yyyy-MM-dd",
                "dd.MM.yyyy",
                "dd/MM/yyyy"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, tr);
                sdf.setLenient(false);
                Date parsed = sdf.parse(date);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
            }
        }

        return fallback > 0 ? fallback : 0;
    }

    private String dateLabel(
            SaleRow row
    ) {
        if (row == null) {
            return "";
        }

        long time = row.timestamp;
        if (time <= 0) {
            return trim(row.saleDate);
        }

        return new SimpleDateFormat(
                "d MMM",
                tr
        ).format(new Date(time));
    }

    private boolean isToday(
            long millis
    ) {
        if (millis <= 0) {
            return false;
        }

        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(millis);

        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean sameRegion(
            String a,
            String b
    ) {
        return !isBlank(a)
                && !isBlank(b)
                && trim(a).equalsIgnoreCase(trim(b));
    }

    private String formatPrice(
            double value
    ) {
        return formatNumber(value, 2) + " TL";
    }

    private String formatPriceCompact(
            double value
    ) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.005d) {
            return formatNumber(value, 0) + " TL";
        }
        return formatNumber(value, 2) + " TL";
    }

    private String formatPricePerKg(
            double value
    ) {
        return formatNumber(value, 2) + " TL/kg";
    }

    private String formatKg(
            double value
    ) {
        NumberFormat nf = NumberFormat.getNumberInstance(tr);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(
                Math.abs(value - Math.rint(value)) < 0.005d ? 0 : 1
        );
        return nf.format(value) + " kg";
    }

    private String formatNumber(
            double value,
            int fractionDigits
    ) {
        NumberFormat nf = NumberFormat.getNumberInstance(tr);
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        return nf.format(value);
    }

    private static String trim(
            String value
    ) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(
            String value
    ) {
        return trim(value).isEmpty();
    }

    private static String getString(
            Cursor cursor,
            String column
    ) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        String value = cursor.getString(index);
        return value == null ? "" : value;
    }

    private static long getLong(
            Cursor cursor,
            String column
    ) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0L
                : cursor.getLong(index);
    }

    private static double getDouble(
            Cursor cursor,
            String column
    ) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0d
                : cursor.getDouble(index);
    }

    private static final class SaleRow {
        long saleId;
        long createdAt;
        long timestamp;
        String saleDate = "";
        String region = "";
        double kg;
        double price;
    }

    private static final class MarketStats {
        int saleCount;
        int rawSaleCount;
        int outlierCount;
        double totalKg;
        double median;
        double min;
        double max;
    }

    private static final class RegionBucket {
        String displayName = "";
        int saleCount;
        double totalKg;
        double median;
        final List<SaleRow> rows = new ArrayList<>();
    }

    private static final class DailyBucket {
        long timestamp;
        String label = "";
        final List<Double> prices = new ArrayList<>();
    }

    private static final class TrendInfo {
        String text = "";
        int previousRecordCount;
        double previousMedian;
    }

    private static final class ConfidenceInfo {
        String label = "Veri Yok";
        int score;
        String details = "";
    }

    private static final class ChartData {
        float[] values = new float[0];
        String startLabel = "";
        String endLabel = "";
    }
}
