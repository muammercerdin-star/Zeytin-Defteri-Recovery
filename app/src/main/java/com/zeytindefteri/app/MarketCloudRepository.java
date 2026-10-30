package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Çok kullanıcılı anonim bölgesel Piyasa istemci adaptörü.
 *
 * V7 direct-RPC mimarisi:
 * - Mevcut Firebase/Google oturumu aynen korunur.
 * - Firebase ID token Supabase Third-Party Auth tarafından doğrulanır.
 * - Android yalnız Supabase publishable key taşır; secret/service-role taşımaz.
 * - Elek satırları, kullanıcı Firebase ile oturum açtığında otomatik olarak
 *   market_submit RPC ile private sunucu tablosuna gider.
 * - Firebase UID telefondan gönderilmez; sunucuda auth.jwt() üzerinden alınır ve
 *   gizli pepper ile SHA-256 üretici hash'ine çevrilir.
 * - Telefon başka üreticilerin ham kayıtlarını hiçbir zaman okuyamaz.
 * - market_snapshot / market_regions / market_chart yalnız toplulaştırılmış
 *   anonim çıktıları döndürür.
 * - Aynı Elek kaydı tekrar gönderilirse UPSERT olur; silinen/değişen satırlar
 *   market_withdraw ile temizlenir.
 */
public final class MarketCloudRepository {

    private static final String TAG = "MarketCloudRepository";

    public interface ResultCallback {
        void onComplete(Result result);
    }

    public interface RegionsCallback {
        void onComplete(List<RegionRef> regions);
    }

    public interface LeagueRegionsCallback {
        void onComplete(List<LeagueRegionStat> regions, Exception error);
    }

    public interface LeagueRegionReportCallback {
        void onComplete(LeagueRegionReport report, Exception error);
    }

    private interface TokenCallback {
        void onComplete(String firebaseToken, Exception error);
    }

    private interface SyncCallback {
        void onComplete(Exception error);
    }

    public static final class RegionRef {
        public String id = "";
        public String name = "";
        public String district = "";
        public String province = "";
    }

    public static final class RegionStat {
        public String id = "";
        public String name = "";
        public double median;
        public double totalKg;
        public int producerCount;
    }

    /**
     * Zeytin Ligi ana ekranındaki bölge dizini için güvenli, toplu özet.
     * Eşik altındaki bölgeler yalnızca ad + üretici sayısı ile görünür;
     * kg/fiyat/baskın elek ancak sunucu gizlilik eşiği sağlandığında gelir.
     */
    public static final class LeagueRegionStat {
        public String id = "";
        public String name = "";
        public String grade = "";
        public int producerCount;
        /** True when the server allows the selected view's price to be shown. */
        public boolean published;
        /** May intentionally be 0 for a one-producer preview even when price is visible. */
        public double totalKg;
        public double averagePrice;
        public String dominantGrade = "";
    }

    public static final class ChartPoint {
        public String day = "";
        public double price;
        public int producerCount;
    }

    public static final class LeagueGradeReport {
        public String grade = "";
        public int producerCount;
        public int saleCount;
        public double kg;
        public double averagePrice;
        public double medianPrice;
        public boolean kgPublished;

        // V10: aynı eleğin son 7 günü ile önceki 7 gününü karşılaştıran,
        // üretici-dengeli trend. Bir üreticinin çok kayıt girmesi trend
        // ağırlığını artırmaz; sunucu önce üreticiyi tek dönem medyanına indirger.
        public boolean trendAvailable;
        public double trendPct;
        public double trendCurrentMedian;
        public double trendPreviousMedian;
        public int trendCurrentProducers;
        public int trendPreviousProducers;
        public int trendCurrentDays;
        public int trendPreviousDays;
    }

    public static final class LeagueRegionReport {
        public boolean available;
        public String regionId = "";
        public String regionName = "";
        public int producerCount;
        public int saleCount;
        public double totalKg;
        public double averagePrice;
        public double medianPrice;
        public double minPrice;
        public double maxPrice;
        public String dominantGrade = "";
        public double trendPct;
        public boolean trendAvailable;
        public String trendGrade = "";
        public double trendCurrentMedian;
        public double trendPreviousMedian;
        public int trendCurrentProducers;
        public int trendPreviousProducers;
        public int trendCurrentDays;
        public int trendPreviousDays;
        public String trustLabel = "";
        public final List<LeagueGradeReport> grades = new ArrayList<>();
        public final List<ChartPoint> chart = new ArrayList<>();
        public String status = "Bölge raporu yükleniyor";
    }

    public static final class Result {
        public boolean available;
        public String regionId = "";
        public String regionName = "";
        public String district = "";
        public String province = "";
        public String grade = "";
        public String period = "";
        public double median;
        public double rangeLow;
        public double rangeHigh;
        public double totalKg;
        public int recordCount;
        public int outlierCount;
        public int producerCount;
        public int confidenceScore;
        public String confidenceLabel = "";
        public boolean previousAvailable;
        public double previousMedian;
        public int previousRecordCount;
        public double changeAbs;
        public double changePct;
        public long updatedAtMs;
        public final List<ChartPoint> chart = new ArrayList<>();
        public final List<RegionStat> regions = new ArrayList<>();
        public String status = "Yerel önizleme";
    }

    private static final long REGION_CACHE_MS = 5L * 60L * 1000L;
    private static final int MAX_SYNC_ROWS = 2000;

    private static final String[] DB_GRADES = new String[]{
            "DOUBLE", "1", "2", "3", "4", "YAGLIK"
    };

    private final DatabaseHelper databaseHelper;
    private final String uid;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<RegionRef> regionCache = new ArrayList<>();
    private long regionCacheAt;
    private int regionCacheYear;

    public MarketCloudRepository(
            DatabaseHelper databaseHelper,
            String uid
    ) {
        this.databaseHelper = databaseHelper;
        this.uid = safe(uid);
    }

    public boolean isBackendConfigured() {
        return MarketBackendConfig.isConfigured();
    }

    public boolean canUseCloud() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return MarketBackendConfig.isConfigured()
                && user != null
                && !uid.isEmpty()
                && uid.equals(user.getUid());
    }

    /**
     * Anonim katkı varsayılan olarak AÇIK (yayıncı kararı, 14.10.2026); kullanıcı
     * Ayarlar → Piyasaya Anonim Katkı'dan kapatabilir. Lig ekranı ilk açılışta bunu bir kez
     * bildirir. KVKK/Gizlilik metinleri bu davranışı anlatır. Kapatınca geri çekme çalışır.
     */
    public boolean localContributionEnabled() {
        return canUseCloud() && isContributionOptedIn();
    }

    public static final String CONTRIBUTION_OPT_IN_PREFIX = "market_contribution_opt_in_";

    public boolean isContributionOptedIn() {
        return !uid.isEmpty() && !"0".equals(databaseHelper.getSetting(CONTRIBUTION_OPT_IN_PREFIX + uid, "1"));
    }

    public static final String CONTRIBUTION_NOTICE_PREFIX = "market_contribution_notice_seen_";

    /** Varsayılan açık katkının bir kez bildirilip bildirilmediği (kullanıcıya özel). */
    public boolean contributionNoticeSeen() {
        return uid.isEmpty() || "1".equals(databaseHelper.getSetting(CONTRIBUTION_NOTICE_PREFIX + uid, "0"));
    }

    public void markContributionNoticeSeen() {
        if (!uid.isEmpty()) databaseHelper.setSetting(CONTRIBUTION_NOTICE_PREFIX + uid, "1");
    }

    /** Katkı anahtarı sonucu: hata yoksa null. Ana iş parçacığında çağrılır. */
    public interface OptInCallback {
        void onComplete(Exception error);
    }

    /**
     * İzni açar/kapatır. Kapatınca bu cihazdan gönderilmiş tüm katkılar sunucudan geri
     * çekilir ve senkron izi sıfırlanır; tekrar açılınca kayıtlar baştan gönderilir.
     */
    public void setContributionOptIn(boolean enabled, OptInCallback callback) {
        if (uid.isEmpty()) {
            if (callback != null) callback.onComplete(new IllegalStateException("Oturum yok"));
            return;
        }
        databaseHelper.setSetting(CONTRIBUTION_OPT_IN_PREFIX + uid, enabled ? "1" : "0");
        bumpSyncEpoch();
        if (enabled || !canUseCloud()) {
            if (callback != null) callback.onComplete(enabled || loadUploadedRefs().isEmpty()
                    ? null : new IllegalStateException("Bulut bağlantısı yok; katkılar daha sonra geri çekilecek"));
            return;
        }
        withdrawAllTracked(error -> {
            if (callback != null) callback.onComplete(error);
        });
    }

    private void bumpSyncEpoch() {
        String key = "market_supabase_rpc_sync_epoch_" + uid;
        int epoch = 0;
        try { epoch = Integer.parseInt(databaseHelper.getSetting(key, "0")); } catch (NumberFormatException ignored) { }
        databaseHelper.setSetting(key, String.valueOf(epoch + 1));
    }

    /**
     * Bölge seçicide uzak ağda yayınlanmış bölgeleri de gösterebilmek için
     * elek sınıflarının 180 günlük yayınlanmış bölge listesini birleştirir.
     */
    public void loadRegions(
            int year,
            RegionsCallback callback
    ) {
        if (!canUseCloud()) {
            if (callback != null) callback.onComplete(Collections.emptyList());
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (regionCache) {
            if (regionCacheYear == year
                    && !regionCache.isEmpty()
                    && now - regionCacheAt <= REGION_CACHE_MS) {
                if (callback != null) callback.onComplete(copyRegions(regionCache));
                return;
            }
        }

        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(copyRegions(regionCache));
                return;
            }

            networkExecutor.execute(() -> {
                Map<String, RegionRef> merged = new LinkedHashMap<>();
                Exception failure = null;

                try {
                    for (String dbGrade : DB_GRADES) {
                        JSONObject body = new JSONObject();
                        put(body, "p_grade", toApiGrade(dbGrade));
                        put(body, "p_days", 180);

                        JSONArray array = requestRpcArray(
                                "market_regions",
                                body,
                                token
                        );

                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item == null) continue;
                            RegionRef region = parseRegionRef(item);
                            if (!region.id.isEmpty() && !region.district.isEmpty()) {
                                merged.put(region.id, region);
                            }
                        }
                    }
                } catch (Exception error) {
                    failure = error;
                }

                List<RegionRef> parsed = new ArrayList<>(merged.values());
                Collections.sort(parsed, (a, b) -> a.name.compareToIgnoreCase(b.name));

                if (failure == null) {
                    synchronized (regionCache) {
                        regionCache.clear();
                        regionCache.addAll(parsed);
                        regionCacheAt = System.currentTimeMillis();
                        regionCacheYear = year;
                    }
                }

                List<RegionRef> result = failure == null
                        ? parsed
                        : copyRegions(regionCache);

                postToMain(() -> {
                    if (callback != null) callback.onComplete(copyRegions(result));
                });
            });
        });
    }

    /**
     * Zeytin Ligi için çok kullanıcılı bölge dizinini Supabase'ten getirir.
     * Bu çağrı ham üretici satırlarını dönmez. Sunucu eşiğinin altındaki bir
     * bölge yalnızca "aktif bölge" olarak görünür; özel kg/fiyat değerleri
     * null bırakılır. Böylece Alaşehir cihazı Salihli'nin varlığını görebilir
     * fakat tek üreticinin satış değerleri açığa çıkmaz.
     */
    public void loadLeagueRegions(
            int year,
            LeagueRegionsCallback callback
    ) {
        loadLeagueRegions(year, "Genel", callback);
    }

    /**
     * Zeytin Ligi için çok kullanıcılı bölge dizinini Supabase'ten getirir.
     * "Genel" görünüm eski market_league_regions RPC'sini kullanır. Belirli
     * bir elek seçildiğinde market_league_grade_regions ile yalnız o eleğin
     * bölgesel fiyat özeti okunur.
     */
    public void loadLeagueRegions(
            int year,
            String uiGrade,
            LeagueRegionsCallback callback
    ) {
        if (!canUseCloud()) {
            if (callback != null) {
                callback.onComplete(
                        Collections.emptyList(),
                        new IllegalStateException(
                                MarketBackendConfig.isConfigured()
                                        ? "Oturum gerekli"
                                        : "Piyasa backend ayarı yapılmamış"
                        )
                );
            }
            return;
        }

        final String requestedGrade = safe(uiGrade);
        final boolean gradeSpecific = !requestedGrade.isEmpty()
                && !"Genel".equalsIgnoreCase(requestedGrade);
        final String apiGrade = gradeSpecific ? toApiGrade(requestedGrade) : "";

        if (gradeSpecific && apiGrade.isEmpty()) {
            if (callback != null) {
                callback.onComplete(
                        Collections.emptyList(),
                        new IllegalArgumentException("Geçersiz elek seçimi")
                );
            }
            return;
        }

        SyncCallback proceed = ignored -> withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) {
                    callback.onComplete(
                            Collections.emptyList(),
                            tokenError != null
                                    ? tokenError
                                    : new IllegalStateException("Firebase token alınamadı")
                    );
                }
                return;
            }

            networkExecutor.execute(() -> {
                List<LeagueRegionStat> parsed = new ArrayList<>();
                Exception failure = null;

                try {
                    JSONObject body = new JSONObject();
                    put(body, "p_season_year", year);
                    String rpcName = "market_league_regions";
                    if (gradeSpecific) {
                        put(body, "p_grade", apiGrade);
                        rpcName = "market_league_grade_regions";
                    }

                    JSONArray rows = requestRpcArray(
                            rpcName,
                            body,
                            token
                    );

                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.optJSONObject(i);
                        if (item == null) continue;

                        LeagueRegionStat stat = new LeagueRegionStat();
                        stat.id = safe(item.optString("region_key", ""));
                        stat.name = safe(item.optString("region_label", ""));
                        stat.grade = gradeSpecific ? apiGrade : "";
                        stat.producerCount = Math.max(0, item.optInt("producer_count", 0));
                        // İstemci de gizlilik eşiğini uygular: sunucu test eşiğiyle
                        // (ör. 2 üretici) yayımlasa bile 5'in altı gösterilmez.
                        stat.published = item.optBoolean("published", false)
                                && stat.producerCount >= MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;

                        if (!item.isNull("total_kg")) {
                            stat.totalKg = Math.max(0d, item.optDouble("total_kg", 0d));
                        }
                        if (!item.isNull("average_price")) {
                            stat.averagePrice = Math.max(0d, item.optDouble("average_price", 0d));
                        }
                        if (!item.isNull("dominant_grade")) {
                            stat.dominantGrade = safe(item.optString("dominant_grade", ""));
                        }

                        // Eşik altı bölgede kg/fiyat/baskın elek hiç tutulmaz.
                        // (Eski "tek üreticili fiyat önizlemesi" o üreticinin
                        // fiyatını herkese açtığı için kaldırıldı.)
                        if (!stat.published) {
                            stat.totalKg = 0d;
                            stat.averagePrice = 0d;
                            stat.dominantGrade = "";
                        }

                        if (!stat.id.isEmpty() && !stat.name.isEmpty() && stat.producerCount > 0) {
                            parsed.add(stat);
                        }
                    }

                    Collections.sort(
                            parsed,
                            (a, b) -> a.name.compareToIgnoreCase(b.name)
                    );
                } catch (Exception error) {
                    failure = error;
                }

                final Exception finalFailure = failure;
                final List<LeagueRegionStat> result = parsed;
                postToMain(() -> {
                    if (callback != null) {
                        callback.onComplete(
                                finalFailure == null
                                        ? new ArrayList<>(result)
                                        : Collections.emptyList(),
                                finalFailure
                        );
                    }
                });
            });
        });

        // Bölge dizinini okumadan önce bu cihazın en güncel Elek kayıtlarını
        // otomatik anonim havuza yetiştir.
        syncContributionsIfNeeded(year, proceed);
    }


    /**
     * Zeytin Ligi bölge kartı ayrıntı penceresi için anonim bölge raporu.
     * Önce tek RPC ile sezon özeti + elek dağılımı + 30 günlük trend okunur.
     * Sunucuda yeni rapor RPC'si henüz kurulmadıysa eski güvenli RPC'lerden
     * sınırlı bir fallback özet oluşturulur; uygulama bu yüzden çökmez.
     */
    public void loadLeagueRegionReport(
            int year,
            String regionId,
            String regionName,
            LeagueRegionReportCallback callback
    ) {
        LeagueRegionReport empty = new LeagueRegionReport();
        empty.regionId = safe(regionId);
        empty.regionName = safe(regionName);
        empty.status = "Anonim bölge raporu alınamadı";

        if (!canUseCloud() || empty.regionId.isEmpty()) {
            if (callback != null) callback.onComplete(empty, new IllegalStateException("Bölgesel bağlantı hazır değil"));
            return;
        }

        SyncCallback proceed = ignored -> withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(empty, tokenError);
                return;
            }

            networkExecutor.execute(() -> {
                LeagueRegionReport report = null;
                Exception failure = null;
                try {
                    JSONObject body = new JSONObject();
                    put(body, "p_season_year", year);
                    put(body, "p_region_key", empty.regionId);
                    put(body, "p_days", 30);
                    JSONArray rows = requestRpcArray("market_league_region_report", body, token);
                    if (rows.length() > 0) {
                        report = parseLeagueRegionReport(rows.optJSONObject(0), empty.regionId, empty.regionName);
                    }
                } catch (Exception error) {
                    failure = error;
                    Log.w(TAG, "market_league_region_report RPC failed; using safe fallback", error);
                }

                if (report == null) {
                    try {
                        report = loadLeagueRegionReportFallback(year, empty.regionId, empty.regionName, token);
                    } catch (Exception fallbackError) {
                        Log.w(TAG, "League region report fallback also failed", fallbackError);
                        if (failure == null) failure = fallbackError;
                    }
                }

                final LeagueRegionReport finalReport = report == null ? empty : report;
                final Exception finalFailure = failure;
                postToMain(() -> {
                    if (callback != null) callback.onComplete(finalReport, finalFailure);
                });
            });
        });

        syncContributionsIfNeeded(year, proceed);
    }

    private LeagueRegionReport parseLeagueRegionReport(
            JSONObject item,
            String fallbackRegionId,
            String fallbackRegionName
    ) {
        LeagueRegionReport out = new LeagueRegionReport();
        out.regionId = safe(fallbackRegionId);
        out.regionName = safe(fallbackRegionName);
        if (item == null) return out;

        out.regionId = safe(item.optString("region_key", out.regionId));
        out.regionName = safe(item.optString("region_label", out.regionName));
        out.available = item.optBoolean("published", false);
        out.producerCount = Math.max(0, item.optInt("producer_count", 0));
        out.saleCount = Math.max(0, item.optInt("sale_count", 0));
        if (!item.isNull("total_kg")) out.totalKg = Math.max(0d, item.optDouble("total_kg", 0d));
        if (!item.isNull("average_price")) out.averagePrice = Math.max(0d, item.optDouble("average_price", 0d));
        if (!item.isNull("median_price")) out.medianPrice = Math.max(0d, item.optDouble("median_price", 0d));
        if (!item.isNull("min_price")) out.minPrice = Math.max(0d, item.optDouble("min_price", 0d));
        if (!item.isNull("max_price")) out.maxPrice = Math.max(0d, item.optDouble("max_price", 0d));
        out.dominantGrade = safe(item.optString("dominant_grade", ""));
        if (!item.isNull("trend_pct")) out.trendPct = item.optDouble("trend_pct", 0d);
        out.trustLabel = safe(item.optString("trust_label", ""));

        JSONArray grades = item.optJSONArray("grade_breakdown");
        if (grades != null) {
            for (int i = 0; i < grades.length(); i++) {
                JSONObject row = grades.optJSONObject(i);
                if (row == null) continue;
                LeagueGradeReport grade = new LeagueGradeReport();
                grade.grade = safe(row.optString("grade", ""));
                grade.producerCount = Math.max(0, row.optInt("producer_count", 0));
                grade.saleCount = Math.max(0, row.optInt("sale_count", 0));
                grade.kgPublished = !row.isNull("kg");
                if (grade.kgPublished) grade.kg = Math.max(0d, row.optDouble("kg", 0d));
                // Gizlilik: 5 farklı üreticiden az olan elekte fiyat tutulmaz (sunucu gönderse bile).
                if (RegionGradePrice.visible(grade.producerCount)) {
                    if (!row.isNull("average_price")) grade.averagePrice = Math.max(0d, row.optDouble("average_price", 0d));
                    if (!row.isNull("median_price")) grade.medianPrice = Math.max(0d, row.optDouble("median_price", 0d));
                }
                grade.trendAvailable = row.optBoolean("trend_available", false);
                if (!row.isNull("trend_pct")) grade.trendPct = row.optDouble("trend_pct", 0d);
                if (!row.isNull("trend_current_median")) grade.trendCurrentMedian = Math.max(0d, row.optDouble("trend_current_median", 0d));
                if (!row.isNull("trend_previous_median")) grade.trendPreviousMedian = Math.max(0d, row.optDouble("trend_previous_median", 0d));
                grade.trendCurrentProducers = Math.max(0, row.optInt("trend_current_producers", 0));
                grade.trendPreviousProducers = Math.max(0, row.optInt("trend_previous_producers", 0));
                grade.trendCurrentDays = Math.max(0, row.optInt("trend_current_days", 0));
                grade.trendPreviousDays = Math.max(0, row.optInt("trend_previous_days", 0));
                if (!grade.grade.isEmpty()) out.grades.add(grade);
            }
        }

        JSONArray chart = item.optJSONArray("chart");
        if (chart != null) {
            for (int i = 0; i < chart.length(); i++) {
                JSONObject row = chart.optJSONObject(i);
                if (row == null) continue;
                ChartPoint point = new ChartPoint();
                point.day = safe(row.optString("market_date", ""));
                point.price = Math.max(0d, row.optDouble("price", 0d));
                point.producerCount = Math.max(0, row.optInt("producer_count", 0));
                if (!point.day.isEmpty() && point.price > 0d) out.chart.add(point);
            }
        }

        enforceReportPrivacy(out);
        applyDominantGradeTrend(out);

        if (out.trustLabel.isEmpty()) out.trustLabel = trustForProducerCount(out.producerCount);
        out.status = out.available
                ? "Bölgedeki anonim satış verilerine göre hazırlanmıştır."
                : "Ayrıntılı rapor için üretici eşiği bekleniyor.";
        return out;
    }

    private void applyDominantGradeTrend(LeagueRegionReport report) {
        if (report == null) return;
        LeagueGradeReport selected = null;
        for (LeagueGradeReport grade : report.grades) {
            if (grade == null) continue;
            if (!report.dominantGrade.isEmpty() && report.dominantGrade.equalsIgnoreCase(grade.grade)) {
                selected = grade;
                break;
            }
        }
        if (selected == null) return;
        report.trendGrade = selected.grade;
        report.trendAvailable = selected.trendAvailable;
        report.trendPct = selected.trendAvailable ? selected.trendPct : report.trendPct;
        report.trendCurrentMedian = selected.trendCurrentMedian;
        report.trendPreviousMedian = selected.trendPreviousMedian;
        report.trendCurrentProducers = selected.trendCurrentProducers;
        report.trendPreviousProducers = selected.trendPreviousProducers;
        report.trendCurrentDays = selected.trendCurrentDays;
        report.trendPreviousDays = selected.trendPreviousDays;
    }

    private LeagueRegionReport loadLeagueRegionReportFallback(
            int year,
            String regionId,
            String regionName,
            String token
    ) throws Exception {
        LeagueRegionReport out = new LeagueRegionReport();
        out.regionId = safe(regionId);
        out.regionName = safe(regionName);
        out.status = "Bölgesel özet • ayrıntılı rapor verisi hazırlanıyor";

        JSONObject generalBody = new JSONObject();
        put(generalBody, "p_season_year", year);
        JSONArray generalRows = requestRpcArray("market_league_regions", generalBody, token);
        for (int i = 0; i < generalRows.length(); i++) {
            JSONObject item = generalRows.optJSONObject(i);
            if (item == null || !out.regionId.equals(safe(item.optString("region_key", "")))) continue;
            out.regionName = safe(item.optString("region_label", out.regionName));
            out.producerCount = Math.max(0, item.optInt("producer_count", 0));
            out.available = item.optBoolean("published", false);
            if (!item.isNull("total_kg")) out.totalKg = Math.max(0d, item.optDouble("total_kg", 0d));
            if (!item.isNull("average_price")) out.averagePrice = Math.max(0d, item.optDouble("average_price", 0d));
            if (!item.isNull("dominant_grade")) out.dominantGrade = safe(item.optString("dominant_grade", ""));
            break;
        }

        String[] grades = new String[]{"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};
        for (String uiGrade : grades) {
            JSONObject body = new JSONObject();
            put(body, "p_season_year", year);
            put(body, "p_grade", uiGrade);
            JSONArray rows = requestRpcArray("market_league_grade_regions", body, token);
            LeagueGradeReport grade = new LeagueGradeReport();
            grade.grade = uiGrade;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null || !out.regionId.equals(safe(item.optString("region_key", "")))) continue;
                grade.producerCount = Math.max(0, item.optInt("producer_count", 0));
                grade.kgPublished = !item.isNull("total_kg");
                if (grade.kgPublished) grade.kg = Math.max(0d, item.optDouble("total_kg", 0d));
                if (!item.isNull("average_price")) grade.averagePrice = Math.max(0d, item.optDouble("average_price", 0d));
                break;
            }
            out.grades.add(grade);
        }

        out.trustLabel = out.producerCount > 0 ? "Düşük" : "Veri yok";
        enforceReportPrivacy(out);
        return out;
    }

    /**
     * Bölge raporunda gizlilik eşiği (PUBLIC_MARKET_MIN_PRODUCERS farklı üretici).
     * Eşik altı bölgede hiçbir rakam tutulmaz; eşik üstü bölgede bile az üreticili
     * elek satırları, az üreticili trend pencereleri ve grafik günleri temizlenir.
     * Aksi halde örn. tek üreticinin olduğu bir elek satırı o kişinin fiyatını açar.
     */
    static void enforceReportPrivacy(LeagueRegionReport out) {
        if (out == null) return;
        final int min = MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
        if (out.producerCount < min) out.available = false;
        if (!out.available) {
            out.saleCount = 0;
            out.totalKg = 0d;
            out.averagePrice = 0d;
            out.medianPrice = 0d;
            out.minPrice = 0d;
            out.maxPrice = 0d;
            out.dominantGrade = "";
            out.trendPct = 0d;
            out.chart.clear();
        }
        for (LeagueGradeReport grade : out.grades) {
            if (grade == null) continue;
            if (!out.available || grade.producerCount < min) {
                grade.saleCount = 0;
                grade.kg = 0d;
                grade.kgPublished = false;
                grade.averagePrice = 0d;
                grade.medianPrice = 0d;
            }
            if (!out.available || grade.producerCount < min
                    || grade.trendCurrentProducers < min || grade.trendPreviousProducers < min) {
                grade.trendAvailable = false;
                grade.trendPct = 0d;
                grade.trendCurrentMedian = 0d;
                grade.trendPreviousMedian = 0d;
            }
        }
        for (int i = out.chart.size() - 1; i >= 0; i--) {
            ChartPoint point = out.chart.get(i);
            if (point == null || point.producerCount < min) out.chart.remove(i);
        }
    }

    private String trustForProducerCount(int count) {
        if (count >= 5) return "Yüksek";
        if (count >= 2) return "Orta";
        if (count == 1) return "Düşük";
        return "Veri yok";
    }

    public void load(
            int year,
            String selectedRegion,
            String provinceHint,
            String uiGrade,
            String uiPeriod,
            ResultCallback callback
    ) {
        Result empty = new Result();
        empty.status = MarketBackendConfig.isConfigured()
                ? "Yerel önizleme • Anonim bölgesel veri alınamadı"
                : "Yerel önizleme • Bölgesel bağlantı yapılandırılmadı";

        if (!canUseCloud()) {
            if (callback != null) callback.onComplete(empty);
            return;
        }

        SyncCallback proceed = error -> loadPublished(
                year,
                selectedRegion,
                provinceHint,
                uiGrade,
                uiPeriod,
                empty,
                callback
        );

        syncContributionsIfNeeded(year, proceed);
    }

    private void loadPublished(
            int year,
            String selectedRegion,
            String provinceHint,
            String uiGrade,
            String uiPeriod,
            Result empty,
            ResultCallback callback
    ) {
        String district = safe(selectedRegion);
        String province = safe(provinceHint);

        if (province.isEmpty() && !district.isEmpty()) {
            province = findLocalProvince(district);
        }

        if (district.isEmpty() || province.isEmpty()) {
            if (callback != null) callback.onComplete(empty);
            return;
        }

        final String finalDistrict = district;
        final String finalProvince = province;
        final String regionKey = regionKey(finalDistrict, finalProvince);
        final String apiGrade = toApiGrade(uiGrade);
        final int days = periodDays(uiPeriod);

        if (regionKey.isEmpty() || apiGrade.isEmpty()) {
            if (callback != null) callback.onComplete(empty);
            return;
        }

        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(empty);
                return;
            }

            networkExecutor.execute(() -> {
                try {
                    JSONObject snapshotBody = new JSONObject();
                    put(snapshotBody, "p_region_key", regionKey);
                    put(snapshotBody, "p_grade", apiGrade);
                    put(snapshotBody, "p_days", days);

                    JSONArray snapshotRows = requestRpcArray(
                            "market_snapshot",
                            snapshotBody,
                            token
                    );

                    Result result = new Result();
                    result.regionId = regionKey;
                    result.regionName = regionLabel(finalDistrict, finalProvince);
                    result.district = finalDistrict;
                    result.province = finalProvince;
                    result.grade = apiGrade;
                    result.period = uiPeriod;
                    result.updatedAtMs = System.currentTimeMillis();

                    if (snapshotRows.length() > 0) {
                        parseSnapshot(snapshotRows.optJSONObject(0), result);
                    }

                    JSONObject chartBody = new JSONObject();
                    put(chartBody, "p_region_key", regionKey);
                    put(chartBody, "p_grade", apiGrade);
                    put(chartBody, "p_days", days);
                    parseChart(
                            requestRpcArray("market_chart", chartBody, token),
                            result
                    );

                    JSONObject regionsBody = new JSONObject();
                    put(regionsBody, "p_grade", apiGrade);
                    put(regionsBody, "p_days", days);
                    parseRegionStats(
                            requestRpcArray("market_regions", regionsBody, token),
                            result
                    );

                    result.status = result.available
                            ? "Anonim bölge verisi"
                            : "Anonim ağ • "
                            + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS
                            + " farklı üretici eşiği bekleniyor";

                    postToMain(() -> {
                        if (callback != null) callback.onComplete(result);
                    });
                } catch (Exception error) {
                    postToMain(() -> {
                        if (callback != null) callback.onComplete(empty);
                    });
                }
            });
        });
    }

    private void parseSnapshot(JSONObject item, Result out) {
        if (item == null || out == null) return;

        out.producerCount = item.optInt("producer_count", 0);
        out.available = item.optBoolean("published", false)
                && out.producerCount >= MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
        if (!out.available) return; // Do not accept prices from a server still running the old test threshold.
        out.recordCount = item.optInt("record_count", 0);
        out.totalKg = item.optDouble("total_kg", 0d);
        out.median = item.optDouble("median_price", 0d);
        out.rangeLow = item.optDouble("min_price", 0d);
        out.rangeHigh = item.optDouble("max_price", 0d);
        out.confidenceLabel = safe(item.optString("trust_label", ""));
        out.confidenceScore = confidenceScore(out.confidenceLabel);
        out.outlierCount = item.optInt("outlier_count", 0);

        out.previousMedian = item.optDouble("previous_median", 0d);
        out.previousAvailable = out.previousMedian > 0d;
        out.previousRecordCount = 0;
        out.changeAbs = item.optDouble("delta_tl", 0d);
        out.changePct = item.optDouble("delta_pct", 0d);
    }

    private void parseChart(JSONArray rows, Result out) {
        if (rows == null || out == null || !out.available) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null) continue;

            ChartPoint point = new ChartPoint();
            point.day = safe(item.optString("market_date", ""));
            point.price = item.optDouble("median_price", 0d);

            if (!point.day.isEmpty() && point.price > 0d) {
                out.chart.add(point);
            }
        }
    }

    private void parseRegionStats(JSONArray rows, Result out) {
        if (rows == null || out == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null) continue;

            RegionStat stat = new RegionStat();
            stat.id = safe(item.optString("region_key", ""));
            stat.name = safe(item.optString("region_label", ""));
            stat.median = item.optDouble("median_price", 0d);
            stat.totalKg = item.optDouble("total_kg", 0d);
            stat.producerCount = item.optInt("producer_count", 0);

            if (!stat.id.isEmpty() && stat.median > 0d && stat.producerCount >= MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS) {
                out.regions.add(stat);
            }
        }
    }

    private RegionRef parseRegionRef(JSONObject item) {
        RegionRef region = new RegionRef();
        if (item == null) return region;

        region.id = safe(item.optString("region_key", ""));
        region.name = safe(item.optString("region_label", ""));

        String[] parts = splitRegionLabel(region.name);
        region.district = parts[0];
        region.province = parts[1];

        if (region.name.isEmpty() && !region.district.isEmpty()) {
            region.name = region.province.isEmpty()
                    ? region.district
                    : region.district + " / " + region.province;
        }
        return region;
    }

    /**
     * Cihazdaki aktif sezon Elek kayıtlarını market_submit ile upsert eder;
     * daha önce yüklenmiş olup artık cihazda bulunmayan/değişmiş satırları
     * market_withdraw ile kaldırır.
     */
    private void syncContributionsIfNeeded(
            int year,
            SyncCallback callback
    ) {
        if (!canUseCloud()) {
            if (callback != null) callback.onComplete(null);
            return;
        }
        if (!isContributionOptedIn()) {
            // Kullanıcı kapattı: bu cihazın izlediği katkıları geri çek (önceki denemesi
            // başarısız olduysa burada tamamlanır). Liste boşsa hemen döner; ekranı bekletmez.
            withdrawAllTracked(error -> {
                if (callback != null) callback.onComplete(null);
            });
            return;
        }

        List<LocalContribution> rows = loadLocalContributions(year);
        if (rows.size() > MAX_SYNC_ROWS) {
            rows = new ArrayList<>(rows.subList(0, MAX_SYNC_ROWS));
        }

        String fingerprint = fingerprint(rows);
        String previous = databaseHelper.getSetting(syncHashSettingKey(year), "");
        // "|v1@<12 saatlik dilim>": cinssiz gönderim; aynı 12 saat içinde tekrar gönderilmez.
        String v1Mark = fingerprint + "|v1@" + (System.currentTimeMillis() / (12L * 3600_000L));
        if (!fingerprint.isEmpty() && (fingerprint.equals(previous) || v1Mark.equals(previous))) {
            if (callback != null) callback.onComplete(null);
            return;
        }

        final List<LocalContribution> syncRows = rows;
        final String finalFingerprint = fingerprint;

        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(tokenError != null
                        ? tokenError
                        : new IllegalStateException("Firebase token alınamadı"));
                return;
            }

            networkExecutor.execute(() -> {
                Exception failure = null;
                Map<String, UploadedRef> uploaded = loadUploadedRefs();
                Set<String> currentKeys = new LinkedHashSet<>();
                boolean submitV2 = true;

                try {
                    for (LocalContribution row : syncRows) {
                        String composite = compositeKey(row.sourceRecordId, row.apiGrade, year);
                        currentKeys.add(composite);

                        JSONObject body = new JSONObject();
                        put(body, "p_source_record_id", row.sourceRecordId);
                        put(body, "p_region_key", row.regionKey);
                        put(body, "p_region_label", row.regionLabel);
                        put(body, "p_grade", row.apiGrade);
                        put(body, "p_kg", row.kg);
                        put(body, "p_price_per_kg", row.price);
                        put(body, "p_sale_date", row.recordDate);
                        put(body, "p_season_year", year);

                        // Cinsli gönderim (V2); sunucuda henüz kurulmadıysa eski fonksiyona dön.
                        if (submitV2) {
                            JSONObject v2 = new JSONObject(body.toString());
                            put(v2, "p_variety", row.variety);
                            try {
                                requestRpcRaw("market_submit_v2", v2, token);
                            } catch (IllegalStateException missing) {
                                String m = String.valueOf(missing.getMessage());
                                if (!(m.contains("PGRST202") || m.contains("Could not find the function") || m.contains("HTTP 404"))) throw missing;
                                submitV2 = false;
                                requestRpcRaw("market_submit", body, token);
                            }
                        } else {
                            requestRpcRaw("market_submit", body, token);
                        }

                        UploadedRef ref = new UploadedRef();
                        ref.sourceRecordId = row.sourceRecordId;
                        ref.apiGrade = row.apiGrade;
                        ref.seasonYear = year;
                        uploaded.put(composite, ref);
                    }

                    List<String> staleKeys = new ArrayList<>();
                    for (Map.Entry<String, UploadedRef> entry : uploaded.entrySet()) {
                        UploadedRef ref = entry.getValue();
                        if (ref.seasonYear == year && !currentKeys.contains(entry.getKey())) {
                            staleKeys.add(entry.getKey());
                        }
                    }

                    for (String key : staleKeys) {
                        UploadedRef ref = uploaded.get(key);
                        if (ref == null) continue;

                        JSONObject body = new JSONObject();
                        put(body, "p_source_record_id", ref.sourceRecordId);
                        put(body, "p_grade", ref.apiGrade);
                        requestRpcRaw("market_withdraw", body, token);
                        uploaded.remove(key);
                    }
                } catch (Exception error) {
                    failure = error;
                }

                saveUploadedRefs(uploaded);
                if (failure == null) {
                    // Eski fonksiyonla (cinssiz) gönderildiyse işaretle: V2 kurulunca en geç 12 saatte bir
                    // yeniden denenir ve kayıtlar cinsiyle gider.
                    databaseHelper.setSetting(syncHashSettingKey(year), submitV2 ? finalFingerprint
                            : finalFingerprint + "|v1@" + (System.currentTimeMillis() / (12L * 3600_000L)));
                }

                final Exception finalFailure = failure;
                postToMain(() -> {
                    if (callback != null) callback.onComplete(finalFailure);
                });
            });
        });
    }

    private void withdrawAllTracked(SyncCallback callback) {
        Map<String, UploadedRef> uploaded = loadUploadedRefs();
        if (uploaded.isEmpty()) {
            if (callback != null) callback.onComplete(null);
            return;
        }

        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(tokenError != null
                        ? tokenError
                        : new IllegalStateException("Firebase token alınamadı"));
                return;
            }

            networkExecutor.execute(() -> {
                Exception failure = null;
                Map<String, UploadedRef> remaining = new LinkedHashMap<>(uploaded);

                for (Map.Entry<String, UploadedRef> entry : uploaded.entrySet()) {
                    UploadedRef ref = entry.getValue();
                    try {
                        JSONObject body = new JSONObject();
                        put(body, "p_source_record_id", ref.sourceRecordId);
                        put(body, "p_grade", ref.apiGrade);
                        requestRpcRaw("market_withdraw", body, token);
                        remaining.remove(entry.getKey());
                    } catch (Exception error) {
                        failure = error;
                        break;
                    }
                }

                saveUploadedRefs(remaining);
                final Exception finalFailure = failure;
                postToMain(() -> {
                    if (callback != null) callback.onComplete(finalFailure);
                });
            });
        });
    }

    private List<LocalContribution> loadLocalContributions(int year) {
        List<LocalContribution> rows = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            String sql =
                    "SELECT " +
                            "er.id AS record_id, " +
                            "COALESCE(er.cloud_id,'') AS cloud_id, " +
                            "er.record_date AS record_date, " +
                            "TRIM(COALESCE(g.district,'')) AS district, " +
                            "TRIM(COALESCE(g.province,'')) AS province, " +
                            "COALESCE(g.variety,'') AS variety, " +
                            "eg.grade AS grade, " +
                            "eg.kg AS kg, " +
                            "eg.price_per_kg AS price_per_kg " +
                            "FROM elek_grades eg " +
                            "INNER JOIN elek_records er ON er.id = eg.elek_id " +
                            "INNER JOIN gardens g ON g.id = er.garden_id " +
                            "WHERE COALESCE(g.owner_uid,'') = ? " +
                            "AND (COALESCE(er.owner_uid,'') = ? OR COALESCE(er.owner_uid,'') = '') " +
                            "AND (er.season_year = ? OR (COALESCE(er.season_year,0) = 0 AND " +
                            "(substr(TRIM(er.record_date),1,4) = ? OR substr(TRIM(er.record_date),-4) = ?))) " +
                            "AND eg.kg > 0 " +
                            "AND eg.price_per_kg > 0 " +
                            "AND TRIM(COALESCE(g.district,'')) <> '' " +
                            "AND TRIM(COALESCE(g.province,'')) <> '' " +
                            "ORDER BY er.id, eg.id";

            String y = String.valueOf(year);
            cursor = db.rawQuery(sql, new String[]{uid, uid, y, y, y});

            while (cursor.moveToNext()) {
                LocalContribution row = new LocalContribution();
                long recordId = cursor.getLong(cursor.getColumnIndexOrThrow("record_id"));
                String cloudId = safe(cursor.getString(cursor.getColumnIndexOrThrow("cloud_id")));

                row.sourceRecordId = cloudId.isEmpty()
                        ? "local-" + recordId
                        : cloudId;
                row.recordDate = safe(cursor.getString(cursor.getColumnIndexOrThrow("record_date")));
                row.district = safe(cursor.getString(cursor.getColumnIndexOrThrow("district")));
                row.province = safe(cursor.getString(cursor.getColumnIndexOrThrow("province")));
                row.regionKey = regionKey(row.district, row.province);
                row.regionLabel = regionLabel(row.district, row.province);
                row.apiGrade = toApiGrade(cursor.getString(cursor.getColumnIndexOrThrow("grade")));
                row.variety = OliveVariety.apiKey(cursor.getString(cursor.getColumnIndexOrThrow("variety")));
                if (row.variety.length() > 40) row.variety = row.variety.substring(0, 40);
                row.kg = cursor.getDouble(cursor.getColumnIndexOrThrow("kg"));
                row.price = cursor.getDouble(cursor.getColumnIndexOrThrow("price_per_kg"));

                if (!row.sourceRecordId.isEmpty()
                        && row.recordDate.matches("\\d{4}-\\d{2}-\\d{2}")
                        && !row.regionKey.isEmpty()
                        && !row.regionLabel.isEmpty()
                        && !row.apiGrade.isEmpty()
                        && row.kg > 0d
                        && row.price > 0d) {
                    rows.add(row);
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return rows;
    }

    private String findLocalProvince(String district) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT district, province FROM gardens " +
                            "WHERE COALESCE(owner_uid,'') = ? " +
                            "AND TRIM(COALESCE(province,'')) <> ''",
                    new String[]{uid}
            );

            while (cursor.moveToNext()) {
                String d = safe(cursor.getString(0));
                String p = safe(cursor.getString(1));
                if (d.equalsIgnoreCase(safe(district))) return p;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return "";
    }

    private void withFirebaseToken(TokenCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !uid.equals(user.getUid()) || !MarketBackendConfig.isConfigured()) {
            if (callback != null) {
                callback.onComplete("", new IllegalStateException("Piyasa bulut bağlantısı hazır değil"));
            }
            return;
        }

        user.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String firebaseToken = tokenResult == null
                            ? ""
                            : safe(tokenResult.getToken());
                    if (callback != null) {
                        callback.onComplete(
                                firebaseToken,
                                firebaseToken.isEmpty()
                                        ? new IllegalStateException("Firebase oturum belirteci alınamadı")
                                        : null
                        );
                    }
                })
                .addOnFailureListener(error -> {
                    if (callback != null) callback.onComplete("", error);
                });
    }

    // ================================================================== ADLI ÖN SIRALAMA (Zeytin Ligi)
    // Supabase: supabase/ZEYTIN-LIGI-ADLI-SIRALAMA-V1.sql. Ad yalnız izin verenlerde döner; kg/fiyat dönmez.

    public static final String LEAGUE_NAME_SHOW_PREFIX = "league_name_show_";
    public static final String LEAGUE_NAME_PREFIX = "league_name_";

    /** Bölge ön sıralamasının bir satırı. name boşsa "Anonim üretici". */
    public static final class RankRow {
        public int rank, producerCount, rankedCount;
        public String name = "", dominant = "";
        public boolean me;
        public double score;
    }

    public interface RankingCallback { void onComplete(List<RankRow> rows, Exception error); }

    public interface LeagueProfileCallback { void onComplete(String name, boolean show, Exception error); }

    /** Bu cihazda son bilinen ayar (çevrimdışıyken Ayarlar'da göstermek için). */
    public boolean leagueNameShownLocally() {
        return !uid.isEmpty() && "1".equals(databaseHelper.getSetting(LEAGUE_NAME_SHOW_PREFIX + uid, "0"));
    }

    public String leagueNameLocally() {
        return uid.isEmpty() ? "" : databaseHelper.getSetting(LEAGUE_NAME_PREFIX + uid, "");
    }

    private void rememberLeagueProfile(String name, boolean show) {
        if (uid.isEmpty()) return;
        databaseHelper.setSetting(LEAGUE_NAME_SHOW_PREFIX + uid, show ? "1" : "0");
        databaseHelper.setSetting(LEAGUE_NAME_PREFIX + uid, show && name != null ? name : "");
    }

    public void loadLeagueProfile(LeagueProfileCallback callback) {
        if (!canUseCloud()) {
            if (callback != null) callback.onComplete(leagueNameLocally(), leagueNameShownLocally(),
                    new IllegalStateException("Bulut bağlantısı hazır değil"));
            return;
        }
        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(leagueNameLocally(), leagueNameShownLocally(), tokenError);
                return;
            }
            networkExecutor.execute(() -> {
                String name = "";
                boolean show = false;
                Exception failure = null;
                try {
                    JSONArray rows = requestRpcArray("league_get_profile", new JSONObject(), token);
                    JSONObject row = rows.length() > 0 ? rows.optJSONObject(0) : null;
                    if (row != null) {
                        name = safe(row.optString("display_name", ""));
                        show = row.optBoolean("show_name", false);
                    }
                    rememberLeagueProfile(name, show);
                } catch (Exception error) {
                    failure = error;
                    name = leagueNameLocally();
                    show = leagueNameShownLocally();
                }
                final String n = name;
                final boolean sh = show;
                final Exception f = failure;
                postToMain(() -> { if (callback != null) callback.onComplete(n, sh, f); });
            });
        });
    }

    /** Adı ligde göster / gizle. Gizleyince ad sunucudan silinir. */
    public void setLeagueProfile(String name, boolean show, LeagueProfileCallback callback) {
        if (!canUseCloud()) {
            if (callback != null) callback.onComplete(name, !show, new IllegalStateException("İnternet ya da oturum yok"));
            return;
        }
        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(name, !show, tokenError);
                return;
            }
            networkExecutor.execute(() -> {
                Exception failure = null;
                String saved = show ? safe(name) : "";
                try {
                    JSONObject body = new JSONObject();
                    put(body, "p_display_name", show ? safe(name) : JSONObject.NULL);
                    put(body, "p_show_name", show);
                    String raw = requestRpcRaw("league_set_profile", body, token);
                    try { saved = show ? safe(new JSONObject(raw).optString("display_name", saved)) : ""; }
                    catch (Exception ignored) { }
                    rememberLeagueProfile(saved, show);
                } catch (Exception error) {
                    failure = error;
                }
                final String s = saved;
                final Exception f = failure;
                postToMain(() -> { if (callback != null) callback.onComplete(s, f == null ? show : !show, f); });
            });
        });
    }

    /** Filtre sayısı: kind "variety" (value: "gemlik") ya da "month" (value: "10"). */
    public static final class FilterCount {
        public String kind = "", value = "";
        public int producers;
    }

    public interface FiltersCallback { void onComplete(List<FilterCount> rows, Exception error); }

    private static boolean missingFunction(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage());
        return m.contains("PGRST202") || m.contains("Could not find the function") || m.contains("HTTP 404");
    }

    /** Bölgede cins ve ay başına farklı üretici sayısı (V2 kurulu değilse boş liste). */
    public void loadLeagueFilters(int year, String regionKey, FiltersCallback callback) {
        String key = safe(regionKey);
        if (!canUseCloud() || !key.matches("[a-z0-9_]{2,80}")) {
            if (callback != null) callback.onComplete(Collections.emptyList(), null);
            return;
        }
        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(Collections.emptyList(), tokenError);
                return;
            }
            networkExecutor.execute(() -> {
                List<FilterCount> out = new ArrayList<>();
                Exception failure = null;
                try {
                    JSONObject body = new JSONObject();
                    put(body, "p_season_year", year);
                    put(body, "p_region_key", key);
                    JSONArray rows = requestRpcArray("league_region_filters", body, token);
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject o = rows.optJSONObject(i);
                        if (o == null) continue;
                        FilterCount f = new FilterCount();
                        f.kind = safe(o.optString("kind", ""));
                        f.value = safe(o.optString("value", ""));
                        f.producers = o.optInt("producer_count", 0);
                        out.add(f);
                    }
                } catch (Exception error) {
                    if (!missingFunction(error)) failure = error;
                }
                final Exception f = failure;
                postToMain(() -> { if (callback != null) callback.onComplete(out, f); });
            });
        });
    }

    /** Bölgenin ön sıralaması (5+ üretici). Boş liste: eşik altı ya da kayıt yok. */
    public void loadLeagueRanking(int year, String regionKey, RankingCallback callback) {
        loadLeagueRanking(year, regionKey, "", 0, callback);
    }

    /** variety: cins anahtarı ("" = tümü); month: 0 = sezon, 1–12 = ay. V2 yoksa yalnız sezon/tümü döner. */
    public void loadLeagueRanking(int year, String regionKey, String variety, int month, RankingCallback callback) {
        final String va = safe(variety);
        final int mo = Math.max(0, Math.min(12, month));
        String key = safe(regionKey);
        if (!canUseCloud() || !key.matches("[a-z0-9_]{2,80}")) {
            if (callback != null) callback.onComplete(Collections.emptyList(), new IllegalStateException("Bölgesel bağlantı hazır değil"));
            return;
        }
        withFirebaseToken((token, tokenError) -> {
            if (tokenError != null || token.isEmpty()) {
                if (callback != null) callback.onComplete(Collections.emptyList(), tokenError);
                return;
            }
            networkExecutor.execute(() -> {
                List<RankRow> out = new ArrayList<>();
                Exception failure = null;
                try {
                    JSONObject body = new JSONObject();
                    put(body, "p_season_year", year);
                    put(body, "p_region_key", key);
                    JSONArray rows;
                    try {
                        JSONObject v2 = new JSONObject(body.toString());
                        put(v2, "p_variety", va);
                        put(v2, "p_month", mo);
                        rows = requestRpcArray("league_region_ranking_v2", v2, token);
                    } catch (Exception missing) {
                        if (!missingFunction(missing) || !va.isEmpty() || mo != 0) throw missing;
                        rows = requestRpcArray("league_region_ranking", body, token);
                    }
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject o = rows.optJSONObject(i);
                        if (o == null) continue;
                        RankRow r = new RankRow();
                        r.rank = o.optInt("rank", i + 1);
                        r.name = o.isNull("display_name") ? "" : safe(o.optString("display_name", ""));
                        r.me = o.optBoolean("is_me", false);
                        r.score = o.optDouble("score", 0);
                        r.dominant = o.isNull("dominant_grade") ? "" : safe(o.optString("dominant_grade", ""));
                        r.producerCount = o.optInt("producer_count", 0);
                        r.rankedCount = o.optInt("ranked_count", 0);
                        out.add(r);
                    }
                } catch (Exception error) {
                    failure = error;
                    Log.w(TAG, "league_region_ranking RPC failed", error);
                }
                final Exception f = failure;
                postToMain(() -> { if (callback != null) callback.onComplete(out, f); });
            });
        });
    }

    private JSONArray requestRpcArray(
            String functionName,
            JSONObject body,
            String firebaseToken
    ) throws Exception {
        String response = requestRpcRaw(functionName, body, firebaseToken);
        if (response.isEmpty()) return new JSONArray();
        return new JSONArray(response);
    }

    private String requestRpcRaw(
            String functionName,
            JSONObject body,
            String firebaseToken
    ) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(MarketBackendConfig.rpcUrl(functionName));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("apikey", MarketBackendConfig.SUPABASE_PUBLISHABLE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + safe(firebaseToken));

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = readFully(stream);

            if (code < 200 || code >= 300) {
                String message = "HTTP " + code;
                try {
                    JSONObject error = new JSONObject(response);
                    String candidate = safe(error.optString("message", ""));
                    if (candidate.isEmpty()) candidate = safe(error.optString("details", ""));
                    if (candidate.isEmpty()) candidate = safe(error.optString("hint", ""));
                    if (!candidate.isEmpty()) message = candidate;
                } catch (Exception ignored) {
                }
                throw new IllegalStateException(message);
            }

            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }

    private Map<String, UploadedRef> loadUploadedRefs() {
        Map<String, UploadedRef> out = new LinkedHashMap<>();
        String raw = databaseHelper.getSetting(uploadedRefsSettingKey(), "[]");

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                UploadedRef ref = new UploadedRef();
                ref.sourceRecordId = safe(item.optString("source_record_id", ""));
                ref.apiGrade = safe(item.optString("grade", ""));
                ref.seasonYear = item.optInt("season_year", 0);

                if (!ref.sourceRecordId.isEmpty()
                        && !ref.apiGrade.isEmpty()
                        && ref.seasonYear > 0) {
                    out.put(
                            compositeKey(ref.sourceRecordId, ref.apiGrade, ref.seasonYear),
                            ref
                    );
                }
            }
        } catch (Exception ignored) {
        }

        return out;
    }

    private void saveUploadedRefs(Map<String, UploadedRef> refs) {
        JSONArray array = new JSONArray();
        if (refs != null) {
            for (UploadedRef ref : refs.values()) {
                JSONObject item = new JSONObject();
                put(item, "source_record_id", ref.sourceRecordId);
                put(item, "grade", ref.apiGrade);
                put(item, "season_year", ref.seasonYear);
                array.put(item);
            }
        }
        databaseHelper.setSetting(uploadedRefsSettingKey(), array.toString());
    }

    private String fingerprint(List<LocalContribution> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LocalContribution row : rows) {
                String value = row.sourceRecordId + "|"
                        + row.recordDate + "|"
                        + row.regionKey + "|"
                        + row.apiGrade + "|"
                        + String.format(Locale.US, "%.3f", row.kg) + "|"
                        + String.format(Locale.US, "%.2f", row.price) + "|"
                        + row.variety + "\n";
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String toApiGrade(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) return "";

        String upper = value.toUpperCase(new Locale("tr", "TR"));
        if ("DOUBLE".equals(upper)) return "Double";
        if ("1".equals(upper) || "1 NO".equals(upper)) return "1 No";
        if ("2".equals(upper) || "2 NO".equals(upper)) return "2 No";
        if ("3".equals(upper) || "3 NO".equals(upper)) return "3 No";
        if ("4".equals(upper) || "4 NO".equals(upper)) return "4 No";
        if ("YAGLIK".equals(upper) || "YAĞLIK".equals(upper)) return "Yağlık";
        return "";
    }

    private int periodDays(String uiPeriod) {
        String period = safe(uiPeriod);
        if (MarketUiDataProvider.PERIOD_7_DAYS.equals(period)) return 7;
        if (MarketUiDataProvider.PERIOD_90_DAYS.equals(period)) return 90;
        if (MarketUiDataProvider.PERIOD_SEASON.equals(period)) return 180;
        return 30;
    }

    private int confidenceScore(String label) {
        String value = safe(label);
        if ("Yüksek Güven".equalsIgnoreCase(value)) return 95;
        if ("Güvenilir Veri".equalsIgnoreCase(value)) return 82;
        if ("Orta Güven".equalsIgnoreCase(value)) return 65;
        if ("Sınırlı Veri".equalsIgnoreCase(value)) return 45;
        return 20;
    }

    private String regionKey(String district, String province) {
        String d = foldRegionPart(district);
        String p = foldRegionPart(province);
        if (d.isEmpty() || p.isEmpty()) return "";
        String key = p + "_" + d;
        return key.length() <= 80 ? key : "";
    }

    private String regionLabel(String district, String province) {
        String d = safe(district);
        String p = safe(province);
        if (d.isEmpty() || p.isEmpty()) return "";
        return d + " / " + p;
    }

    private String foldRegionPart(String value) {
        String text = safe(value)
                .toLowerCase(new Locale("tr", "TR"))
                .replace('ı', 'i');
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        text = text.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return text;
    }

    private String[] splitRegionLabel(String label) {
        String value = safe(label);
        String[] out = new String[]{"", ""};
        if (value.isEmpty()) return out;

        String[] parts = value.split("\\s*/\\s*", 2);
        out[0] = safe(parts[0]);
        if (parts.length > 1) out[1] = safe(parts[1]);
        return out;
    }

    private List<RegionRef> copyRegions(List<RegionRef> source) {
        List<RegionRef> out = new ArrayList<>();
        if (source == null) return out;
        for (RegionRef item : source) {
            RegionRef copy = new RegionRef();
            copy.id = item.id;
            copy.name = item.name;
            copy.district = item.district;
            copy.province = item.province;
            out.add(copy);
        }
        return out;
    }

    private String compositeKey(String sourceRecordId, String apiGrade, int year) {
        return year + "|" + safe(sourceRecordId) + "|" + safe(apiGrade);
    }

    private String syncHashSettingKey(int year) {
        // Epoch, izin kapatılıp açıldığında eski "zaten gönderildi" izini geçersiz kılar.
        String epoch = databaseHelper.getSetting("market_supabase_rpc_sync_epoch_" + uid, "0");
        return "market_supabase_rpc_sync_hash_" + uid + "_" + year + ("0".equals(epoch) ? "" : "_e" + epoch);
    }

    private String uploadedRefsSettingKey() {
        return "market_supabase_rpc_uploaded_" + uid;
    }

    private void postToMain(Runnable runnable) {
        if (runnable == null) return;
        mainHandler.post(runnable);
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (Exception ignored) {
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class LocalContribution {
        String sourceRecordId = "";
        String recordDate = "";
        String district = "";
        String province = "";
        String regionKey = "";
        String regionLabel = "";
        String apiGrade = "";
        /** Zeytin cinsi anahtarı ("gemlik"); boş = belirtilmemiş. */
        String variety = "";
        double kg;
        double price;
    }

    private static final class UploadedRef {
        String sourceRecordId = "";
        String apiGrade = "";
        int seasonYear;
    }
}
