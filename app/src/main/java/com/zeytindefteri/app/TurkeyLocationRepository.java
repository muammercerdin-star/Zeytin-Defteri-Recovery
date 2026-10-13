package com.zeytindefteri.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TurkeyLocationRepository {


    // ============================================================
    // TURKIYE API
    //
    // İl / İlçe / Mahalle / Köy
    // ============================================================

    private static final String BASE_URL =
            "https://api.turkiyeapi.dev/v2";


    // ============================================================
    // CLOUDFLARE STATIC GEO DATA
    // ============================================================

    private static final String GEO_DATA_BASE =
            "https://turkey-geo-data.muammercerdin.workers.dev";


    // ============================================================
    // SOKAK BUCKET SAYISI
    //
    // GitHub/Cloudflare scriptinde de 8 kullanıyoruz.
    // İki taraf aynı olmak zorunda.
    // ============================================================

    private static final int STREET_BUCKET_COUNT =
            8;


    // ============================================================
    // TIMEOUT
    // ============================================================

    private static final int API_CONNECT_TIMEOUT =
            12000;

    private static final int API_READ_TIMEOUT =
            20000;


    private static final int GEO_CONNECT_TIMEOUT =
            12000;

    private static final int GEO_READ_TIMEOUT =
            20000;


    // ============================================================
    // THREAD
    //
    // Birden fazla konum isteği birbirini bloke etmesin.
    // ============================================================

    private final ExecutorService executorService;

    private final Handler mainHandler;


    // ============================================================
    // TÜRKÇE
    // ============================================================

    private final Locale turkishLocale =
            new Locale(
                    "tr",
                    "TR"
            );


    // ============================================================
    // RAM CACHE
    //
    // Aynı ilçe / mahalle / sokak tekrar açıldığında
    // yeniden internet sorgusu yapmayalım.
    // ============================================================

    private static final Map<String, Integer>
            DISTRICT_ID_CACHE =
            new ConcurrentHashMap<>();


    private static final Map<String, Integer>
            NEIGHBORHOOD_ID_CACHE =
            new ConcurrentHashMap<>();


    private static final Map<String, List<LocationItem>>
            STREET_CACHE =
            new ConcurrentHashMap<>();


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public TurkeyLocationRepository() {


        executorService =
                Executors.newFixedThreadPool(
                        3
                );


        mainHandler =
                new Handler(
                        Looper.getMainLooper()
                );
    }


    // ============================================================
    // CALLBACK
    // ============================================================

    public interface LocationCallback {


        void onSuccess(
                List<LocationItem> items
        );


        void onError(
                String message
        );
    }


    // ============================================================
    // LOCATION ITEM
    // ============================================================

    public static class LocationItem {


        public static final String TYPE_PROVINCE =
                "PROVINCE";


        public static final String TYPE_DISTRICT =
                "DISTRICT";


        public static final String TYPE_NEIGHBORHOOD =
                "NEIGHBORHOOD";


        public static final String TYPE_VILLAGE =
                "VILLAGE";


        public static final String TYPE_STREET =
                "STREET";


        private final int id;

        private final String name;

        private final String type;


        public LocationItem(
                int id,
                String name,
                String type
        ) {


            this.id =
                    id;


            this.name =
                    name == null
                            ? ""
                            : name.trim();


            this.type =
                    type == null
                            ? ""
                            : type;
        }


        public int getId() {

            return id;
        }


        public String getName() {

            return name;
        }


        public String getType() {

            return type;
        }


        public boolean isProvince() {

            return TYPE_PROVINCE.equals(
                    type
            );
        }


        public boolean isDistrict() {

            return TYPE_DISTRICT.equals(
                    type
            );
        }


        public boolean isNeighborhood() {

            return TYPE_NEIGHBORHOOD.equals(
                    type
            );
        }


        public boolean isVillage() {

            return TYPE_VILLAGE.equals(
                    type
            );
        }


        public boolean isStreet() {

            return TYPE_STREET.equals(
                    type
            );
        }


        public String getDisplayName() {


            if (isNeighborhood()) {


                return name
                        + "  •  Mahalle";
            }


            if (isVillage()) {


                return name
                        + "  •  Köy";
            }


            return name;
        }


        @Override
        public String toString() {

            return getDisplayName();
        }
    }


    // ============================================================
    // İLLER
    // ============================================================

    public void loadProvinces(
            final LocationCallback callback
    ) {


        executorService.execute(
                new Runnable() {


                    @Override
                    public void run() {


                        try {


                            String url =
                                    BASE_URL
                                            + "/provinces"
                                            + "?fields=id,name"
                                            + "&sort=name"
                                            + "&limit=100";


                            List<LocationItem> items =
                                    requestLocations(
                                            url,
                                            LocationItem.TYPE_PROVINCE
                                    );


                            sortTurkish(
                                    items
                            );


                            postSuccess(
                                    callback,
                                    items
                            );


                        } catch (Exception e) {


                            postError(
                                    callback,
                                    readableError(e)
                            );
                        }
                    }
                }
        );
    }


    // ============================================================
    // İLÇELER
    // ============================================================

    public void loadDistricts(
            final int provinceId,
            final LocationCallback callback
    ) {


        executorService.execute(
                new Runnable() {


                    @Override
                    public void run() {


                        try {


                            if (provinceId <= 0) {


                                throw new Exception(
                                        "İl bilgisi bulunamadı."
                                );
                            }


                            String url =
                                    BASE_URL
                                            + "/districts"
                                            + "?provinceId="
                                            + provinceId
                                            + "&fields=id,name"
                                            + "&sort=name"
                                            + "&limit=1000";


                            List<LocationItem> items =
                                    requestLocations(
                                            url,
                                            LocationItem.TYPE_DISTRICT
                                    );


                            sortTurkish(
                                    items
                            );


                            postSuccess(
                                    callback,
                                    items
                            );


                        } catch (Exception e) {


                            postError(
                                    callback,
                                    readableError(e)
                            );
                        }
                    }
                }
        );
    }


    // ============================================================
    // MAHALLE + KÖY
    // ============================================================

    public void loadSettlements(
            final int districtId,
            final LocationCallback callback
    ) {


        executorService.execute(
                new Runnable() {


                    @Override
                    public void run() {


                        try {


                            if (districtId <= 0) {


                                throw new Exception(
                                        "İlçe bilgisi bulunamadı."
                                );
                            }


                            // ====================================
                            // MAHALLELER
                            // ====================================

                            String neighborhoodsUrl =
                                    BASE_URL
                                            + "/neighborhoods"
                                            + "?districtId="
                                            + districtId
                                            + "&fields=id,name"
                                            + "&sort=name"
                                            + "&limit=1000";


                            List<LocationItem> neighborhoods =
                                    requestLocations(
                                            neighborhoodsUrl,
                                            LocationItem.TYPE_NEIGHBORHOOD
                                    );


                            // ====================================
                            // KÖYLER
                            // ====================================

                            String villagesUrl =
                                    BASE_URL
                                            + "/villages"
                                            + "?districtId="
                                            + districtId
                                            + "&fields=id,name"
                                            + "&sort=name"
                                            + "&limit=1000";


                            List<LocationItem> villages =
                                    requestLocations(
                                            villagesUrl,
                                            LocationItem.TYPE_VILLAGE
                                    );


                            // ====================================
                            // BİRLEŞTİR
                            // ====================================

                            List<LocationItem> settlements =
                                    new ArrayList<>();


                            settlements.addAll(
                                    neighborhoods
                            );


                            settlements.addAll(
                                    villages
                            );


                            sortTurkish(
                                    settlements
                            );


                            postSuccess(
                                    callback,
                                    settlements
                            );


                        } catch (Exception e) {


                            postError(
                                    callback,
                                    readableError(e)
                            );
                        }
                    }
                }
        );
    }


    // ============================================================
    // CADDE / SOKAK
    //
    // YENİ HIZLI AKIŞ:
    //
    // Manisa
    //     ↓
    // districts.jsonl
    //     ↓
    // Alaşehir GEO ID = 1751
    //     ↓
    // district-neighborhoods/1751.jsonl
    //     ↓
    // Girelli GEO mahalle ID
    //     ↓
    // mahalleId % 8
    //     ↓
    // district-street-buckets/1751/X.jsonl
    //     ↓
    // sadece Girelli sokakları
    //
    // BÜTÜN İLİN SOKAK/Mahalle DOSYASI ARTIK ÇEKİLMEZ.
    // ============================================================

    public void loadStreets(
            final int provinceId,
            final String districtName,
            final String neighborhoodName,
            final LocationCallback callback
    ) {


        executorService.execute(
                new Runnable() {


                    @Override
                    public void run() {


                        try {


                            // ====================================
                            // KONTROL
                            // ====================================

                            if (provinceId <= 0) {


                                throw new Exception(
                                        "İl bilgisi bulunamadı."
                                );
                            }


                            if (districtName == null
                                    || districtName.trim().isEmpty()) {


                                throw new Exception(
                                        "İlçe bilgisi bulunamadı."
                                );
                            }


                            if (neighborhoodName == null
                                    || neighborhoodName.trim().isEmpty()) {


                                throw new Exception(
                                        "Mahalle bilgisi bulunamadı."
                                );
                            }


                            // ====================================
                            // İLÇE CACHE KEY
                            // ====================================

                            String districtCacheKey =
                                    provinceId
                                            + "|"
                                            + normalizePlaceName(
                                            districtName
                                    );


                            // ====================================
                            // 1) GEO İLÇE ID BUL
                            // ====================================

                            Integer cachedDistrictId =
                                    DISTRICT_ID_CACHE.get(
                                            districtCacheKey
                                    );


                            int geoDistrictId;


                            if (cachedDistrictId != null
                                    && cachedDistrictId > 0) {


                                geoDistrictId =
                                        cachedDistrictId;


                            } else {


                                String districtsUrl =
                                        buildGeoFileUrl(
                                                provinceId,
                                                "districts.jsonl"
                                        );


                                geoDistrictId =
                                        findGeoDistrictId(
                                                districtsUrl,
                                                districtName
                                        );


                                if (geoDistrictId <= 0) {


                                    throw new Exception(
                                            districtName
                                                    + " ilçesi sokak veri setinde bulunamadı."
                                    );
                                }


                                DISTRICT_ID_CACHE.put(
                                        districtCacheKey,
                                        geoDistrictId
                                );
                            }


                            // ====================================
                            // MAHALLE CACHE KEY
                            // ====================================

                            String neighborhoodCacheKey =
                                    provinceId
                                            + "|"
                                            + geoDistrictId
                                            + "|"
                                            + normalizePlaceName(
                                            neighborhoodName
                                    );


                            // ====================================
                            // 2) GEO MAHALLE ID BUL
                            //
                            // BURADA ARTIK BÜTÜN İLİN
                            // neighborhoods.jsonl DOSYASI YOK.
                            //
                            // SADECE SEÇİLİ İLÇE:
                            //
                            // district-neighborhoods/1751.jsonl
                            // ====================================

                            Integer cachedNeighborhoodId =
                                    NEIGHBORHOOD_ID_CACHE.get(
                                            neighborhoodCacheKey
                                    );


                            int geoNeighborhoodId;


                            if (cachedNeighborhoodId != null
                                    && cachedNeighborhoodId > 0) {


                                geoNeighborhoodId =
                                        cachedNeighborhoodId;


                            } else {


                                String neighborhoodsUrl =
                                        buildGeoFileUrl(
                                                provinceId,
                                                "district-neighborhoods/"
                                                        + geoDistrictId
                                                        + ".jsonl"
                                        );


                                geoNeighborhoodId =
                                        findGeoNeighborhoodId(
                                                neighborhoodsUrl,
                                                geoDistrictId,
                                                districtName,
                                                neighborhoodName
                                        );


                                if (geoNeighborhoodId <= 0) {


                                    throw new Exception(
                                            neighborhoodName
                                                    + " mahallesi sokak veri setinde bulunamadı."
                                    );
                                }


                                NEIGHBORHOOD_ID_CACHE.put(
                                        neighborhoodCacheKey,
                                        geoNeighborhoodId
                                );
                            }


                            // ====================================
                            // 3) SOKAK CACHE
                            // ====================================

                            String streetCacheKey =
                                    provinceId
                                            + "|"
                                            + geoDistrictId
                                            + "|"
                                            + geoNeighborhoodId;


                            List<LocationItem> cachedStreets =
                                    STREET_CACHE.get(
                                            streetCacheKey
                                    );


                            if (cachedStreets != null) {


                                postSuccess(
                                        callback,
                                        new ArrayList<>(
                                                cachedStreets
                                        )
                                );


                                return;
                            }


                            // ====================================
                            // 4) BUCKET HESAPLA
                            //
                            // GitHub scripti ile aynı formül:
                            //
                            // neighborhood_id % 8
                            // ====================================

                            int streetBucket =
                                    Math.abs(
                                            geoNeighborhoodId
                                    )
                                            % STREET_BUCKET_COUNT;


                            // ====================================
                            // 5) SADECE İLGİLİ SOKAK BUCKET'I
                            //
                            // ÖRNEK:
                            //
                            // province-45/
                            // district-street-buckets/
                            // 1751/
                            // 3.jsonl
                            // ====================================

                            String streetsUrl =
                                    buildGeoFileUrl(
                                            provinceId,
                                            "district-street-buckets/"
                                                    + geoDistrictId
                                                    + "/"
                                                    + streetBucket
                                                    + ".jsonl"
                                    );


                            List<LocationItem> streets =
                                    requestStreets(
                                            streetsUrl,
                                            geoNeighborhoodId
                                    );


                            sortTurkish(
                                    streets
                            );


                            // ====================================
                            // CACHE'E AL
                            // ====================================

                            STREET_CACHE.put(
                                    streetCacheKey,
                                    new ArrayList<>(
                                            streets
                                    )
                            );


                            postSuccess(
                                    callback,
                                    streets
                            );


                        } catch (Exception e) {


                            postError(
                                    callback,
                                    readableError(e)
                            );
                        }
                    }
                }
        );
    }


    // ============================================================
    // GEO URL OLUŞTUR
    // ============================================================

    private String buildGeoFileUrl(
            int provinceId,
            String fileName
    ) {


        return GEO_DATA_BASE
                + "/province-"
                + provinceId
                + "/"
                + fileName;
    }


    // ============================================================
    // TURKIYE API LİSTE
    // ============================================================

    private List<LocationItem> requestLocations(
            String urlAddress,
            String type
    ) throws Exception {


        HttpURLConnection connection =
                null;


        try {


            connection =
                    createConnection(
                            urlAddress,
                            API_CONNECT_TIMEOUT,
                            API_READ_TIMEOUT,
                            false,
                            "application/json"
                    );


            int responseCode =
                    connection.getResponseCode();


            if (responseCode < 200
                    || responseCode >= 300) {


                String errorBody =
                        readStream(
                                connection.getErrorStream()
                        );


                throw new Exception(
                        parseApiError(
                                errorBody,
                                responseCode
                        )
                );
            }


            String json =
                    readStream(
                            connection.getInputStream()
                    );


            return parseLocationList(
                    json,
                    type
            );


        } finally {


            if (connection != null) {


                connection.disconnect();
            }
        }
    }


    // ============================================================
    // GEO İLÇE ID BUL
    // ============================================================

    private int findGeoDistrictId(
            String urlAddress,
            String districtName
    ) throws Exception {


        HttpURLConnection connection =
                null;


        BufferedReader reader =
                null;


        try {


            connection =
                    createConnection(
                            urlAddress,
                            GEO_CONNECT_TIMEOUT,
                            GEO_READ_TIMEOUT,
                            true,
                            "text/plain"
                    );


            int responseCode =
                    connection.getResponseCode();


            if (responseCode < 200
                    || responseCode >= 300) {


                throw new Exception(
                        "İlçe eşleştirme verisi alınamadı. Kod: "
                                + responseCode
                );
            }


            reader =
                    createReader(
                            connection.getInputStream()
                    );


            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {


                if (line.trim().isEmpty()) {


                    continue;
                }


                try {


                    JSONObject item =
                            new JSONObject(
                                    line
                            );


                    int id =
                            optIntAny(
                                    item,
                                    "id"
                            );


                    if (id <= 0) {


                        continue;
                    }


                    String name =
                            optStringAny(
                                    item,
                                    "name"
                            );


                    String official =
                            optStringAny(
                                    item,
                                    "full_official_name",
                                    "fullOfficialName"
                            );


                    if (namesEquivalent(
                            name,
                            districtName
                    )
                            || namesEquivalent(
                            official,
                            districtName
                    )) {


                        return id;
                    }


                } catch (Exception ignored) {

                }
            }


            return 0;


        } finally {


            closeReader(
                    reader
            );


            if (connection != null) {


                connection.disconnect();
            }
        }
    }


    // ============================================================
    // GEO MAHALLE ID BUL
    // ============================================================

    private int findGeoNeighborhoodId(
            String urlAddress,
            int geoDistrictId,
            String districtName,
            String neighborhoodName
    ) throws Exception {


        HttpURLConnection connection =
                null;


        BufferedReader reader =
                null;


        int fallbackId =
                0;


        boolean fallbackAmbiguous =
                false;


        try {


            connection =
                    createConnection(
                            urlAddress,
                            GEO_CONNECT_TIMEOUT,
                            GEO_READ_TIMEOUT,
                            true,
                            "text/plain"
                    );


            int responseCode =
                    connection.getResponseCode();


            if (responseCode < 200
                    || responseCode >= 300) {


                throw new Exception(
                        "Mahalle eşleştirme verisi alınamadı. Kod: "
                                + responseCode
                );
            }


            reader =
                    createReader(
                            connection.getInputStream()
                    );


            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {


                if (line.trim().isEmpty()) {


                    continue;
                }


                try {


                    JSONObject item =
                            new JSONObject(
                                    line
                            );


                    int id =
                            optIntAny(
                                    item,
                                    "id"
                            );


                    if (id <= 0) {


                        continue;
                    }


                    // ============================================
                    // İLÇE ID
                    // ============================================

                    int lineDistrictId =
                            optIntAny(
                                    item,
                                    "district_id",
                                    "districtId"
                            );


                    if (lineDistrictId > 0
                            && geoDistrictId > 0
                            && lineDistrictId
                            != geoDistrictId) {


                        continue;
                    }


                    // ============================================
                    // MAHALLE ADI
                    // ============================================

                    String name =
                            optStringAny(
                                    item,
                                    "name"
                            );


                    String official =
                            optStringAny(
                                    item,
                                    "full_official_name",
                                    "fullOfficialName"
                            );


                    boolean neighborhoodMatches =
                            namesEquivalent(
                                    name,
                                    neighborhoodName
                            )
                                    || namesEquivalent(
                                    official,
                                    neighborhoodName
                            );


                    if (!neighborhoodMatches) {


                        continue;
                    }


                    // ============================================
                    // ID TAM EŞLEŞME
                    // ============================================

                    if (lineDistrictId > 0
                            && geoDistrictId > 0
                            && lineDistrictId
                            == geoDistrictId) {


                        return id;
                    }


                    // ============================================
                    // İLÇE ADI YEDEK
                    // ============================================

                    String lineDistrictName =
                            optStringAny(
                                    item,
                                    "district_name",
                                    "districtName"
                            );


                    if (!lineDistrictName.isEmpty()
                            && namesEquivalent(
                            lineDistrictName,
                            districtName
                    )) {


                        return id;
                    }


                    // ============================================
                    // TEK KAYIT YEDEK
                    // ============================================

                    if (fallbackId == 0) {


                        fallbackId =
                                id;


                    } else if (fallbackId
                            != id) {


                        fallbackAmbiguous =
                                true;
                    }


                } catch (Exception ignored) {

                }
            }


            if (fallbackId > 0
                    && !fallbackAmbiguous) {


                return fallbackId;
            }


            return 0;


        } finally {


            closeReader(
                    reader
            );


            if (connection != null) {


                connection.disconnect();
            }
        }
    }


    // ============================================================
    // CADDE / SOKAK DOSYASI OKU
    // ============================================================

    private List<LocationItem> requestStreets(
            String urlAddress,
            int geoNeighborhoodId
    ) throws Exception {


        HttpURLConnection connection =
                null;


        BufferedReader reader =
                null;


        List<LocationItem> result =
                new ArrayList<>();


        Set<Integer> seenIds =
                new HashSet<>();


        Set<String> seenNames =
                new HashSet<>();


        try {


            connection =
                    createConnection(
                            urlAddress,
                            GEO_CONNECT_TIMEOUT,
                            GEO_READ_TIMEOUT,
                            true,
                            "text/plain"
                    );


            int responseCode =
                    connection.getResponseCode();


            if (responseCode < 200
                    || responseCode >= 300) {


                throw new Exception(
                        "Cadde / sokak verisi alınamadı. Kod: "
                                + responseCode
                );
            }


            reader =
                    createReader(
                            connection.getInputStream()
                    );


            String line;


            String searchWithSpace =
                    "\"neighborhood_id\": "
                            + geoNeighborhoodId;


            String searchWithoutSpace =
                    "\"neighborhood_id\":"
                            + geoNeighborhoodId;


            String searchCamel =
                    "\"neighborhoodId\":"
                            + geoNeighborhoodId;


            String searchCamelSpace =
                    "\"neighborhoodId\": "
                            + geoNeighborhoodId;


            while (
                    (line = reader.readLine())
                            != null
            ) {


                // ================================================
                // JSON OLUŞTURMADAN ÖNCE HIZLI ELEME
                // ================================================

                if (!line.contains(
                        searchWithSpace
                )
                        && !line.contains(
                        searchWithoutSpace
                )
                        && !line.contains(
                        searchCamel
                )
                        && !line.contains(
                        searchCamelSpace
                )) {


                    continue;
                }


                try {


                    JSONObject item =
                            new JSONObject(
                                    line
                            );


                    int lineNeighborhoodId =
                            optIntAny(
                                    item,
                                    "neighborhood_id",
                                    "neighborhoodId"
                            );


                    if (lineNeighborhoodId
                            != geoNeighborhoodId) {


                        continue;
                    }


                    int id =
                            optIntAny(
                                    item,
                                    "id"
                            );


                    String name =
                            optStringAny(
                                    item,
                                    "name"
                            );


                    String fullOfficialName =
                            optStringAny(
                                    item,
                                    "full_official_name",
                                    "fullOfficialName"
                            );


                    int typeCode =
                            optIntAny(
                                    item,
                                    "type_code",
                                    "typeCode"
                            );


                    String displayName =
                            buildStreetName(
                                    name,
                                    fullOfficialName,
                                    typeCode
                            );


                    if (displayName.isEmpty()) {


                        continue;
                    }


                    // ============================================
                    // ID TEKRARI
                    // ============================================

                    if (id > 0
                            && seenIds.contains(
                            id
                    )) {


                        continue;
                    }


                    // ============================================
                    // İSİM TEKRARI
                    // ============================================

                    String normalizedDisplay =
                            normalizePlaceName(
                                    displayName
                            );


                    if (seenNames.contains(
                            normalizedDisplay
                    )) {


                        continue;
                    }


                    if (id > 0) {


                        seenIds.add(
                                id
                        );
                    }


                    seenNames.add(
                            normalizedDisplay
                    );


                    result.add(
                            new LocationItem(
                                    id > 0
                                            ? id
                                            : result.size() + 1,
                                    displayName,
                                    LocationItem.TYPE_STREET
                            )
                    );


                } catch (Exception ignored) {

                }
            }


            return result;


        } finally {


            closeReader(
                    reader
            );


            if (connection != null) {


                connection.disconnect();
            }
        }
    }


    // ============================================================
    // CADDE / SOKAK ADI
    // ============================================================

    private String buildStreetName(
            String name,
            String fullOfficialName,
            int typeCode
    ) {


        String cleanName =
                name == null
                        ? ""
                        : name.trim();


        String official =
                fullOfficialName == null
                        ? ""
                        : fullOfficialName.trim();


        // ========================================================
        // RESMİ TAM AD
        // ========================================================

        if (!official.isEmpty()) {


            String cleanedOfficial =
                    official
                            .replace(
                                    " (Cadde)",
                                    " Caddesi"
                            )
                            .replace(
                                    " (CADDE)",
                                    " Caddesi"
                            )
                            .replace(
                                    " (Sokak)",
                                    " Sokağı"
                            )
                            .replace(
                                    " (SOKAK)",
                                    " Sokağı"
                            )
                            .replace(
                                    " (Bulvar)",
                                    " Bulvarı"
                            )
                            .replace(
                                    " (BULVAR)",
                                    " Bulvarı"
                            )
                            .replace(
                                    " (Meydan)",
                                    " Meydanı"
                            )
                            .replace(
                                    " (MEYDAN)",
                                    " Meydanı"
                            )
                            .replace(
                                    " (Küme Evler)",
                                    " Küme Evler"
                            )
                            .replace(
                                    " (KÜME EVLER)",
                                    " Küme Evler"
                            )
                            .trim();


            if (!cleanedOfficial.isEmpty()) {


                return cleanedOfficial;
            }
        }


        if (cleanName.isEmpty()) {


            return "";
        }


        // ========================================================
        // TİP KODUNDAN YEDEK AD
        // ========================================================

        switch (typeCode) {


            case 1:


                return cleanName
                        + " Meydanı";


            case 2:


                return cleanName
                        + " Bulvarı";


            case 3:


                return cleanName
                        + " Caddesi";


            case 4:


                return cleanName
                        + " Sokağı";


            case 5:


                return cleanName
                        + " Küme Evler";


            default:


                return cleanName;
        }
    }


    // ============================================================
    // TURKIYE API JSON LİSTE
    // ============================================================

    private List<LocationItem> parseLocationList(
            String json,
            String type
    ) throws Exception {


        List<LocationItem> result =
                new ArrayList<>();


        if (json == null
                || json.trim().isEmpty()) {


            return result;
        }


        JSONObject root =
                new JSONObject(
                        json
                );


        JSONArray data =
                root.optJSONArray(
                        "data"
                );


        if (data == null) {


            return result;
        }


        for (
                int i = 0;
                i < data.length();
                i++
        ) {


            JSONObject item =
                    data.optJSONObject(
                            i
                    );


            if (item == null) {


                continue;
            }


            int id =
                    optIntAny(
                            item,
                            "id"
                    );


            String name =
                    optStringAny(
                            item,
                            "name"
                    );


            if (id <= 0
                    || name.isEmpty()) {


                continue;
            }


            result.add(
                    new LocationItem(
                            id,
                            name,
                            type
                    )
            );
        }


        return result;
    }


    // ============================================================
    // İSİM EŞLEŞTİR
    // ============================================================

    private boolean namesEquivalent(
            String first,
            String second
    ) {


        String a =
                normalizePlaceName(
                        first
                );


        String b =
                normalizePlaceName(
                        second
                );


        if (a.isEmpty()
                || b.isEmpty()) {


            return false;
        }


        return a.equals(
                b
        );
    }


    // ============================================================
    // YER ADI NORMALLEŞTİR
    // ============================================================

    private String normalizePlaceName(
            String value
    ) {


        if (value == null) {


            return "";
        }


        String result =
                value
                        .trim()
                        .toLowerCase(
                                turkishLocale
                        );


        result =
                result
                        .replace(
                                "’",
                                "'"
                        )
                        .replace(
                                "‘",
                                "'"
                        )
                        .replace(
                                "´",
                                "'"
                        );


        result =
                result
                        .replace(
                                "'",
                                " "
                        )
                        .replace(
                                ".",
                                " "
                        )
                        .replace(
                                ",",
                                " "
                        )
                        .replace(
                                "-",
                                " "
                        )
                        .replace(
                                "/",
                                " "
                        );


        result =
                collapseSpaces(
                        result
                );


        result =
                removeEnding(
                        result,
                        " mahallesi"
                );


        result =
                removeEnding(
                        result,
                        " mahalle"
                );


        result =
                removeEnding(
                        result,
                        " mah"
                );


        result =
                removeEnding(
                        result,
                        " köyü"
                );


        result =
                removeEnding(
                        result,
                        " köy"
                );


        result =
                removeEnding(
                        result,
                        " ilçesi"
                );


        result =
                removeEnding(
                        result,
                        " ilçe"
                );


        return collapseSpaces(
                result
        );
    }


    // ============================================================
    // SON EK KALDIR
    // ============================================================

    private String removeEnding(
            String value,
            String ending
    ) {


        if (value == null
                || ending == null) {


            return "";
        }


        String result =
                value.trim();


        if (result.endsWith(
                ending
        )) {


            result =
                    result.substring(
                            0,
                            result.length()
                                    - ending.length()
                    );
        }


        return result.trim();
    }


    // ============================================================
    // BOŞLUK TEKLE
    // ============================================================

    private String collapseSpaces(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }


    // ============================================================
    // JSON INT
    // ============================================================

    private int optIntAny(
            JSONObject object,
            String... keys
    ) {


        if (object == null
                || keys == null) {


            return 0;
        }


        for (
                String key
                : keys
        ) {


            if (key == null) {


                continue;
            }


            if (!object.has(
                    key
            )) {


                continue;
            }


            try {


                Object value =
                        object.opt(
                                key
                        );


                if (value instanceof Number) {


                    return ((Number) value)
                            .intValue();
                }


                if (value != null) {


                    String text =
                            String.valueOf(
                                    value
                            )
                                    .trim();


                    if (!text.isEmpty()) {


                        return Integer.parseInt(
                                text
                        );
                    }
                }


            } catch (Exception ignored) {

            }
        }


        return 0;
    }


    // ============================================================
    // JSON STRING
    // ============================================================

    private String optStringAny(
            JSONObject object,
            String... keys
    ) {


        if (object == null
                || keys == null) {


            return "";
        }


        for (
                String key
                : keys
        ) {


            if (key == null) {


                continue;
            }


            String value =
                    object.optString(
                            key,
                            ""
                    );


            if (value != null
                    && !value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(
                    value.trim()
            )) {


                return value.trim();
            }
        }


        return "";
    }


    // ============================================================
    // HTTP CONNECTION
    // ============================================================

    private HttpURLConnection createConnection(
            String urlAddress,
            int connectTimeout,
            int readTimeout,
            boolean useCaches,
            String accept
    ) throws Exception {


        URL url =
                new URL(
                        urlAddress
                );


        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();


        connection.setRequestMethod(
                "GET"
        );


        connection.setConnectTimeout(
                connectTimeout
        );


        connection.setReadTimeout(
                readTimeout
        );


        connection.setUseCaches(
                useCaches
        );


        connection.setInstanceFollowRedirects(
                true
        );


        connection.setRequestProperty(
                "Accept",
                accept
        );


        connection.setRequestProperty(
                "User-Agent",
                "ZeytinDefteri-Android"
        );


        return connection;
    }


    // ============================================================
    // READER
    // ============================================================

    private BufferedReader createReader(
            InputStream inputStream
    ) throws Exception {


        return new BufferedReader(
                new InputStreamReader(
                        inputStream,
                        "UTF-8"
                )
        );
    }


    // ============================================================
    // READER KAPAT
    // ============================================================

    private void closeReader(
            BufferedReader reader
    ) {


        if (reader == null) {


            return;
        }


        try {


            reader.close();


        } catch (Exception ignored) {

        }
    }


    // ============================================================
    // STREAM OKU
    // ============================================================

    private String readStream(
            InputStream inputStream
    ) throws Exception {


        if (inputStream == null) {


            return "";
        }


        BufferedReader reader =
                createReader(
                        inputStream
                );


        StringBuilder builder =
                new StringBuilder();


        try {


            String line;


            while (
                    (line = reader.readLine())
                            != null
            ) {


                builder.append(
                        line
                );
            }


        } finally {


            closeReader(
                    reader
            );
        }


        return builder.toString();
    }


    // ============================================================
    // TÜRKÇE A-Z
    // ============================================================

    private void sortTurkish(
            List<LocationItem> items
    ) {


        if (items == null
                || items.size() < 2) {


            return;
        }


        final Collator collator =
                Collator.getInstance(
                        turkishLocale
                );


        collator.setStrength(
                Collator.PRIMARY
        );


        Collections.sort(
                items,
                new Comparator<LocationItem>() {


                    @Override
                    public int compare(
                            LocationItem first,
                            LocationItem second
                    ) {


                        String firstName =
                                first == null
                                        ? ""
                                        : first.getName();


                        String secondName =
                                second == null
                                        ? ""
                                        : second.getName();


                        return collator.compare(
                                firstName,
                                secondName
                        );
                    }
                }
        );
    }


    // ============================================================
    // API HATASI
    // ============================================================

    private String parseApiError(
            String body,
            int responseCode
    ) {


        try {


            if (body != null
                    && !body.trim().isEmpty()) {


                JSONObject root =
                        new JSONObject(
                                body
                        );


                JSONObject error =
                        root.optJSONObject(
                                "error"
                        );


                if (error != null) {


                    String message =
                            error.optString(
                                    "message",
                                    ""
                            );


                    if (!message.trim().isEmpty()) {


                        return message;
                    }
                }


                String simpleError =
                        root.optString(
                                "error",
                                ""
                        );


                if (!simpleError.trim().isEmpty()) {


                    return simpleError;
                }
            }


        } catch (Exception ignored) {

        }


        return "Konum servisi hata verdi. Kod: "
                + responseCode;
    }


    // ============================================================
    // BAŞARI
    // ============================================================

    private void postSuccess(
            final LocationCallback callback,
            final List<LocationItem> items
    ) {


        if (callback == null) {


            return;
        }


        mainHandler.post(
                new Runnable() {


                    @Override
                    public void run() {


                        callback.onSuccess(
                                items
                        );
                    }
                }
        );
    }


    // ============================================================
    // HATA
    // ============================================================

    private void postError(
            final LocationCallback callback,
            final String message
    ) {


        if (callback == null) {


            return;
        }


        mainHandler.post(
                new Runnable() {


                    @Override
                    public void run() {


                        callback.onError(
                                message
                        );
                    }
                }
        );
    }


    // ============================================================
    // OKUNABİLİR HATA
    // ============================================================

    private String readableError(
            Exception e
    ) {


        if (e == null) {


            return "Konum bilgileri alınamadı.";
        }


        String message =
                e.getMessage();


        if (message == null
                || message.trim().isEmpty()) {


            return "Konum bilgileri alınamadı.";
        }


        return message;
    }


    // ============================================================
    // KAPAT
    // ============================================================

    public void shutdown() {


        try {


            executorService.shutdownNow();


        } catch (Exception ignored) {

        }
    }
}