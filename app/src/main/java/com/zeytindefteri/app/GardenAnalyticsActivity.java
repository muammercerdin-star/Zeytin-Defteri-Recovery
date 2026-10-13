package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Immutable SQLite snapshot gösterir.
 *
 * Bahçeler arasında geçiş sırasında yeni SQLite sorgusu çalıştırmaz.
 * Temel analiz tamamen local-first'tür.
 */
public final class GardenAnalyticsActivity extends Activity {


    // ============================================================
    // VERİ
    // ============================================================

    private DatabaseHelper helper;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private GardenAnalyticsRepository.Result data;


    // ============================================================
    // SEÇİM
    // ============================================================

    private final ArrayList<Long> gardenIds =
            new ArrayList<>();

    private long selectedGarden =
            -1L;

    private String screenOwner =
            "";

    private int generation =
            0;


    // ============================================================
    // UI
    // ============================================================

    private LinearLayout root;
    private LinearLayout cards;


    // ============================================================
    // FORMAT
    // ============================================================

    private final Locale TR =
            new Locale(
                    "tr",
                    "TR"
            );

    private final NumberFormat numbers =
            NumberFormat.getNumberInstance(
                    TR
            );


    // ============================================================
    // RENKLER
    // ============================================================

    private final int COLOR_TEXT =
            GreenTheme.color(
                    R.color.zd_text
            );

    private final int COLOR_MUTED =
            GreenTheme.color(
                    R.color.zd_muted
            );

    private final int COLOR_GREEN =
            GreenTheme.color(
                    R.color.zd_green
            );

    /*
     * Yeni resource eklememek için warning rengi burada kullanılıyor.
     * Böylece XML/resource bağımlılığı oluşmaz.
     */
    private final int COLOR_WARNING =
            Color.rgb(
                    244,
                    192,
                    84
            );


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle state
    ) {

        super.onCreate(
                state
        );


        if (getActionBar() != null) {

            getActionBar()
                    .hide();
        }


        getWindow()
                .setStatusBarColor(
                        GreenTheme.color(
                                R.color.zd_bg
                        )
                );


        getWindow()
                .setNavigationBarColor(
                        GreenTheme.color(
                                R.color.zd_bg
                        )
                );


        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );


        helper =
                new DatabaseHelper(
                        this
                );


        if (state == null) {

            screenOwner =
                    helper
                            .getActiveOwnerUid();


            selectedGarden =
                    getIntent()
                            .getLongExtra(
                                    "garden_id",
                                    -1L
                            );

        } else {

            screenOwner =
                    state.getString(
                            "owner",
                            ""
                    );


            selectedGarden =
                    state.getLong(
                            "garden",
                            -1L
                    );
        }


        numbers.setMaximumFractionDigits(
                2
        );

        numbers.setMinimumFractionDigits(
                0
        );


        buildBaseLayout();
    }


    // ============================================================
    // BASE LAYOUT
    // ============================================================

    private void buildBaseLayout() {

        ScrollView scroll =
                new ScrollView(
                        this
                );


        scroll.setFillViewport(
                true
        );


        scroll.setBackgroundColor(
                GreenTheme.color(
                        R.color.zd_bg
                )
        );


        root =
                new LinearLayout(
                        this
                );


        root.setOrientation(
                LinearLayout.VERTICAL
        );


        root.setPadding(
                dp(18),
                dp(12),
                dp(18),
                dp(28)
        );


        scroll.addView(
                root
        );


        setContentView(
                scroll
        );
    }


    // ============================================================
    // ON RESUME
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();


        if (!isSameOwner()) {

            finish();

            return;
        }


        final int ticket =
                ++generation;


        final int season =
                helper
                        .getActiveSeasonYear();


        root.removeAllViews();


        header();


        root.addView(
                label(
                        "Kayıtların hesaplanıyor…",
                        16,
                        COLOR_MUTED
                )
        );


        worker.execute(
                () -> {

                    try {

                        GardenAnalyticsRepository.Result loaded =
                                new GardenAnalyticsRepository(
                                        helper
                                )
                                        .load(
                                                screenOwner,
                                                season,
                                                true
                                        );


                        runOnUiThread(
                                () -> {

                                    if (
                                            isFinishing()
                                                    || isDestroyed()
                                                    || ticket != generation
                                    ) {

                                        return;
                                    }


                                    if (!isSameOwner()) {

                                        finish();

                                        return;
                                    }


                                    if (
                                            season
                                                    != helper
                                                    .getActiveSeasonYear()
                                    ) {

                                        return;
                                    }


                                    if (
                                            loaded == null
                                                    || loaded.current == null
                                    ) {

                                        showLoadError();

                                        return;
                                    }


                                    /*
                                     * -1 = Tüm Bahçeler sezon özeti.
                                     *
                                     * Gerçek garden_id ise varlığını kontrol et.
                                     */
                                    if (
                                            selectedGarden != -1L
                                                    && loaded.current
                                                    .garden(
                                                            selectedGarden
                                                    )
                                                    == null
                                    ) {

                                        Toast.makeText(
                                                this,
                                                "Bahçe bulunamadı",
                                                Toast.LENGTH_SHORT
                                        ).show();


                                        finish();

                                        return;
                                    }


                                    data =
                                            loaded;


                                    showScreen();
                                }
                        );


                    } catch (
                            RuntimeException error
                    ) {

                        runOnUiThread(
                                () -> {

                                    if (
                                            isFinishing()
                                                    || isDestroyed()
                                                    || ticket != generation
                                    ) {

                                        return;
                                    }


                                    showLoadError();
                                }
                        );
                    }
                }
        );
    }


    // ============================================================
    // LOAD ERROR
    // ============================================================

    private void showLoadError() {

        root.removeAllViews();


        header();


        root.addView(
                label(
                        "Analiz okunamadı. Kayıtlarını kontrol edip ekranı yeniden aç.",
                        16,
                        COLOR_MUTED
                )
        );
    }


    // ============================================================
    // HEADER
    // ============================================================

    private void header() {

        TextView back =
                label(
                        "‹  Geri",
                        16,
                        COLOR_TEXT
                );


        back.setMinHeight(
                dp(48)
        );


        back.setGravity(
                Gravity.CENTER_VERTICAL
        );


        back.setContentDescription(
                "Önceki ekrana dön"
        );


        back.setOnClickListener(
                view -> finish()
        );


        root.addView(
                back
        );


        TextView title =
                label(
                        "Bahçe Karnesi",
                        27,
                        COLOR_TEXT
                );


        title.setTypeface(
                Typeface.SERIF,
                Typeface.BOLD
        );


        root.addView(
                title
        );
    }


    // ============================================================
    // ANA EKRAN
    // ============================================================

    private void showScreen() {

        root.removeAllViews();


        header();


        root.addView(
                label(
                        data.current.total.season
                                + " • Sezon Zekâsı",
                        14,
                        COLOR_MUTED
                )
        );


        Spinner selector =
                new Spinner(
                        this
                );


        ArrayList<String> names =
                new ArrayList<>();


        gardenIds.clear();


        gardenIds.add(
                -1L
        );


        names.add(
                "Tüm Bahçeler · Sezon Özeti"
        );


        for (
                GardenAnalyticsSnapshot garden :
                data.current.gardens.values()
        ) {

            gardenIds.add(
                    garden.gardenId
            );


            names.add(
                    garden.name
            );
        }


        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        names
                ) {

                    @Override
                    public View getView(
                            int position,
                            View recycled,
                            android.view.ViewGroup parent
                    ) {

                        TextView view =
                                (TextView) super.getView(
                                        position,
                                        recycled,
                                        parent
                                );


                        view.setTextColor(
                                COLOR_TEXT
                        );


                        view.setTextSize(
                                16
                        );


                        return view;
                    }


                    @Override
                    public View getDropDownView(
                            int position,
                            View recycled,
                            android.view.ViewGroup parent
                    ) {

                        TextView view =
                                (TextView) super.getDropDownView(
                                        position,
                                        recycled,
                                        parent
                                );


                        view.setTextColor(
                                COLOR_TEXT
                        );


                        view.setBackgroundColor(
                                GreenTheme.color(
                                        R.color.zd_card
                                )
                        );


                        view.setPadding(
                                dp(12),
                                dp(14),
                                dp(12),
                                dp(14)
                        );


                        return view;
                    }
                };


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        selector.setAdapter(
                adapter
        );


        selector.setContentDescription(
                "Bahçe veya tüm sezon özeti seç"
        );


        selector.setMinimumHeight(
                dp(52)
        );


        root.addView(
                selector
        );


        cards =
                new LinearLayout(
                        this
                );


        cards.setOrientation(
                LinearLayout.VERTICAL
        );


        root.addView(
                cards
        );


        int selectionIndex =
                gardenIds.indexOf(
                        selectedGarden
                );


        if (selectionIndex < 0) {

            selectedGarden =
                    -1L;

            selectionIndex =
                    0;
        }


        selector.setSelection(
                selectionIndex
        );


        selector.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        selectedGarden =
                                gardenIds.get(
                                        position
                                );


                        render();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {

                    }
                }
        );


        render();
    }


    // ============================================================
    // RENDER
    // ============================================================

    private void render() {

        if (!isSameOwner()) {

            finish();

            return;
        }


        if (
                data == null
                        || data.current == null
        ) {

            return;
        }


        GardenAnalyticsSnapshot current =
                data.current
                        .garden(
                                selectedGarden
                        );


        GardenAnalyticsSnapshot previous =
                data.previous == null
                        ? null
                        : data.previous
                        .garden(
                                selectedGarden
                        );


        if (current == null) {

            Toast.makeText(
                    this,
                    "Analiz verisi bulunamadı",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        cards.removeAllViews();


        renderSummary(
                current
        );


        renderYieldAndCost(
                current
        );


        renderSales(
                current
        );


        renderUnassigned(
                current
        );


        renderGrades(
                current
        );


        renderComparison(
                current,
                previous
        );
    }


    // ============================================================
    // ÖZET
    // ============================================================

    private void renderSummary(
            GardenAnalyticsSnapshot snapshot
    ) {

        LinearLayout summary =
                card(
                        selectedGarden == -1L
                                ? snapshot.season
                                + " Sezon Özeti"
                                : snapshot.name
                );


        if (selectedGarden == -1L) {

            summary.addView(
                    label(
                            snapshot.gardenCount
                                    + " bahçe",
                            14,
                            COLOR_MUTED
                    )
            );
        }


        metric(
                summary,
                "Toplam hasat",
                snapshot.harvestKg,
                "kg"
        );


        metric(
                summary,
                "Kayıtlı masraf",
                snapshot.expense,
                "TL"
        );


        metric(
                summary,
                "Satış geliri",
                snapshot.revenue,
                "TL"
        );


        metric(
                summary,
                "Kayıtlı Net Sonuç",
                snapshot.netResult(),
                "TL"
        );


        summary.addView(
                smallInfo(
                        "Gelir, satış nakliyesi ve kesintiler sonrası tutardır. "
                                + "Net sonuç = bu gelir − kayıtlı masraflar."
                )
        );


        summary.addView(
                smallInfo(
                        "Kaydetmediğin emek, kira, amortisman veya vergi hesapta yoktur."
                )
        );
    }


    // ============================================================
    // VERİM / MALİYET
    // ============================================================

    private void renderYieldAndCost(
            GardenAnalyticsSnapshot snapshot
    ) {

        LinearLayout yield =
                card(
                        "Verim ve maliyet"
                );


        metric(
                yield,
                "Kayıtlı ağaç",
                snapshot.trees,
                ""
        );


        metric(
                yield,
                "Ağaç başı verim",
                snapshot.kgPerTree(),
                "kg/ağaç"
        );


        metric(
                yield,
                "Dekar başı verim",
                snapshot.kgPerDecare(),
                "kg/dekar"
        );


        metric(
                yield,
                "Kg başı kayıtlı maliyet",
                snapshot.costPerKg(),
                "TL/kg"
        );


        if (
                !snapshot.completeTrees
                        || !snapshot.completeArea
        ) {

            yield.addView(
                    smallInfo(
                            "Eksik ağaç/dekar bilgisi olan kapsamda ilgili verim hesaplanmaz."
                    )
            );
        }
    }


    // ============================================================
    // SATIŞ / TAHSİLAT
    // ============================================================

    private void renderSales(
            GardenAnalyticsSnapshot snapshot
    ) {

        LinearLayout sales =
                card(
                        "Satış ve tahsilat"
                );


        metric(
                sales,
                "Satılan",
                snapshot.soldKg,
                "kg"
        );


        metric(
                sales,
                "Ağırlıklı satış fiyatı",
                snapshot.averageSalePrice(),
                "TL/kg"
        );


        metric(
                sales,
                "Satış tahsilatı",
                snapshot.collected,
                "TL"
        );


        metric(
                sales,
                "Kalan alacak",
                snapshot.receivable,
                "TL"
        );


        sales.addView(
                smallInfo(
                        "Ortalama fiyat: kesintiler öncesi satış tutarı / satılan kg. "
                                + "Tahsilat, satışın alacak kaydıyla bir kez sayılır."
                )
        );
    }


    // ============================================================
    // BAHÇEYE BAĞLANMAYAN KAYITLAR
    // ============================================================

    private void renderUnassigned(
            GardenAnalyticsSnapshot snapshot
    ) {

        if (
                snapshot.unassignedCost == 0d
                        && snapshot.unassignedReceivable == 0d
        ) {

            return;
        }


        LinearLayout other =
                card(
                        "Bahçeye bağlanmamış sezon kayıtları"
                );


        metric(
                other,
                "Özete dahil salamura masrafı",
                snapshot.unassignedCost,
                "TL"
        );


        metric(
                other,
                "Özete dahil diğer alacak",
                snapshot.unassignedReceivable,
                "TL"
        );
    }


    // ============================================================
    // ELEK
    // ============================================================

    private void renderGrades(
            GardenAnalyticsSnapshot snapshot
    ) {

        LinearLayout grades =
                card(
                        "Elek dağılımı"
                );


        /*
         * Eskiden yalnız:
         *
         * 3 No
         *
         * yazıyordu.
         *
         * Artık ne anlama geldiği açık.
         */
        TextView dominant =
                label(
                        "Baskın Elek: "
                                + snapshot.dominantGrade(),
                        18,
                        COLOR_GREEN
                );


        dominant.setTypeface(
                null,
                Typeface.BOLD
        );


        dominant.setPadding(
                0,
                dp(8),
                0,
                dp(6)
        );


        grades.addView(
                dominant
        );


        if (snapshot.elekRecords > 0) {

            for (
                    int i = 0;
                    i < GardenAnalyticsSnapshot.GRADE_LABELS.length;
                    i++
            ) {

                metric(
                        grades,
                        GardenAnalyticsSnapshot.GRADE_LABELS[i],
                        snapshot.gradeKg(
                                i
                        ),
                        "kg"
                );
            }


            /*
             * Yeni:
             * Kullanıcı artık Elek kayıtlarının toplamını da açıkça görür.
             */
            metric(
                    grades,
                    "Elek kayıtları toplamı",
                    snapshot.totalGradeKg(),
                    "kg"
            );


            renderHarvestElekConsistency(
                    grades,
                    snapshot
            );


        } else {

            grades.addView(
                    smallInfo(
                            "Bu sezon için Elek kaydı bulunmuyor."
                    )
            );
        }
    }


    // ============================================================
    // HASAT <-> ELEK TUTARLILIK ZEKÂSI
    // ============================================================

    private void renderHarvestElekConsistency(
            LinearLayout card,
            GardenAnalyticsSnapshot snapshot
    ) {

        /*
         * Bir tarafta gerçek kayıt yoksa kullanıcıya
         * "hata var" demiyoruz.
         */
        if (!snapshot.hasComparableHarvestAndElek()) {

            card.addView(
                    smallInfo(
                            "Hasat ve Elek toplamlarını karşılaştırmak için iki tarafta da kayıt bulunmalıdır."
                    )
            );

            return;
        }


        double harvest =
                snapshot.harvestKg;


        double elek =
                snapshot.totalGradeKg();


        double difference =
                snapshot.harvestElekDifferenceKg();


        /*
         * UYUMLU
         */
        if (!snapshot.hasHarvestElekMismatch()) {

            TextView ok =
                    label(
                            "✓ Hasat ve Elek kayıtları toplam olarak uyumlu.",
                            13,
                            COLOR_GREEN
                    );


            ok.setPadding(
                    dp(12),
                    dp(11),
                    dp(12),
                    dp(11)
            );


            ok.setBackground(
                    consistencyBackground(
                            COLOR_GREEN
                    )
            );


            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );


            params.topMargin =
                    dp(10);


            ok.setLayoutParams(
                    params
            );


            card.addView(
                    ok
            );


            return;
        }


        /*
         * TUTARSIZ
         */
        String message;


        if (difference > 0d) {

            message =
                    "⚠ Hasat toplamı "
                            + format(
                            harvest,
                            "kg"
                    )
                            + ", Elek kayıtları toplamı "
                            + format(
                            elek,
                            "kg"
                    )
                            + ". Elek kayıtları hasattan "
                            + format(
                            snapshot.elekExcessKg(),
                            "kg"
                    )
                            + " fazla. Kayıtlarını kontrol et.";

        } else {

            message =
                    "⚠ Hasat toplamı "
                            + format(
                            harvest,
                            "kg"
                    )
                            + ", Elek kayıtları toplamı "
                            + format(
                            elek,
                            "kg"
                    )
                            + ". Elek kayıtları hasattan "
                            + format(
                            snapshot.elekMissingKg(),
                            "kg"
                    )
                            + " eksik. Kayıtlarını kontrol et.";
        }


        TextView warning =
                label(
                        message,
                        13,
                        COLOR_WARNING
                );


        warning.setPadding(
                dp(12),
                dp(11),
                dp(12),
                dp(11)
        );


        warning.setBackground(
                consistencyBackground(
                        COLOR_WARNING
                )
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        params.topMargin =
                dp(10);


        warning.setLayoutParams(
                params
        );


        card.addView(
                warning
        );


        /*
         * Kullanıcıya bunun neden olabileceğini de anlatıyoruz.
         *
         * Burada "kod hatası" demiyoruz çünkü Hasat ve Elek
         * kayıtları mevcut veri modelinde bağımsızdır.
         */
        card.addView(
                smallInfo(
                        "Hasat ve Elek kayıtları ayrı kayıt akışlarıdır. "
                                + "Bu uyarı veri toplamlarının birbirini karşılamadığını gösterir."
                )
        );
    }


    // ============================================================
    // TUTARLILIK KUTUSU
    // ============================================================

    private GradientDrawable consistencyBackground(
            int strokeColor
    ) {

        GradientDrawable background =
                new GradientDrawable();


        background.setColor(
                GreenTheme.color(
                        R.color.zd_card
                )
        );


        background.setCornerRadius(
                dp(12)
        );


        background.setStroke(
                dp(1),
                strokeColor
        );


        return background;
    }


    // ============================================================
    // SEZON KARŞILAŞTIRMASI
    // ============================================================

    private void renderComparison(
            GardenAnalyticsSnapshot current,
            GardenAnalyticsSnapshot previous
    ) {

        int previousSeason =
                previous == null
                        ? current.season - 1
                        : previous.season;


        LinearLayout comparison =
                card(
                        previousSeason
                                + " → "
                                + current.season
                                + " karşılaştırması"
                );


        comparison.addView(
                smallInfo(
                        "Tam sezon toplamlarıdır; aynı tarihe kadar olan dönemle sınırlı değildir."
                )
        );


        if (
                previous == null
                        || (
                        previous.harvestRecords
                                + previous.expenseRecords
                                + previous.saleRecords
                                + previous.elekRecords
                        == 0
                )
        ) {

            comparison.addView(
                    label(
                            "Önceki sezonda karşılaştırılabilir kayıt yok.",
                            15,
                            COLOR_MUTED
                    )
            );


        } else {

            compare(
                    comparison,
                    "Hasat",
                    current.harvestKg,
                    previous.harvestKg,
                    current.harvestRecords > 0,
                    previous.harvestRecords > 0,
                    "kg"
            );


            compare(
                    comparison,
                    "Masraf",
                    current.expense,
                    previous.expense,
                    current.expenseRecords > 0,
                    previous.expenseRecords > 0,
                    "TL"
            );


            compare(
                    comparison,
                    "Satış geliri",
                    current.revenue,
                    previous.revenue,
                    current.saleRecords > 0,
                    previous.saleRecords > 0,
                    "TL"
            );


            compare(
                    comparison,
                    "Ağaç başı verim",
                    current.kgPerTree(),
                    previous.kgPerTree(),
                    current.harvestRecords > 0,
                    previous.harvestRecords > 0,
                    "kg/ağaç"
            );


            compare(
                    comparison,
                    "Kg başı maliyet",
                    current.costPerKg(),
                    previous.costPerKg(),
                    current.harvestRecords > 0,
                    previous.harvestRecords > 0,
                    "TL/kg"
            );


            compare(
                    comparison,
                    "Satılan",
                    current.soldKg,
                    previous.soldKg,
                    current.saleRecords > 0,
                    previous.saleRecords > 0,
                    "kg"
            );


            compare(
                    comparison,
                    "Ortalama fiyat",
                    current.averageSalePrice(),
                    previous.averageSalePrice(),
                    current.saleRecords > 0,
                    previous.saleRecords > 0,
                    "TL/kg"
            );


            comparison.addView(
                    label(
                            "Baskın Elek: "
                                    + previous.dominantGrade()
                                    + " → "
                                    + current.dominantGrade(),
                            14,
                            COLOR_TEXT
                    )
            );


            for (
                    int i = 0;
                    i < GardenAnalyticsSnapshot.GRADE_LABELS.length;
                    i++
            ) {

                compare(
                        comparison,
                        GardenAnalyticsSnapshot.GRADE_LABELS[i],
                        current.gradeKg(
                                i
                        ),
                        previous.gradeKg(
                                i
                        ),
                        current.elekRecords > 0,
                        previous.elekRecords > 0,
                        "kg"
                );
            }
        }


        comparison.addView(
                smallInfo(
                        "Geçmiş ağaç sayısı saklanmadığı için iki sezonun kg/ağaç hesabında güncel ağaç sayısı kullanılır."
                )
        );
    }


    // ============================================================
    // KARŞILAŞTIRMA SATIRI
    // ============================================================

    private void compare(
            LinearLayout card,
            String name,
            Double current,
            Double previous,
            boolean hasCurrent,
            boolean hasPrevious,
            String unit
    ) {

        Double percent =
                GardenAnalyticsSnapshot
                        .percent(
                                current,
                                previous,
                                hasCurrent,
                                hasPrevious
                        );


        String change;


        if (percent == null) {

            change =
                    "Karşılaştırma için yeterli veri yok.";

        } else if (percent > 0d) {

            change =
                    "↑ %"
                            + numbers.format(
                            percent
                    );

        } else if (percent < 0d) {

            change =
                    "↓ %"
                            + numbers.format(
                            -percent
                    );

        } else {

            change =
                    "Değişim yok · %0";
        }


        String previousText =
                hasPrevious
                        ? format(
                        previous,
                        unit
                )
                        : "—";


        String currentText =
                hasCurrent
                        ? format(
                        current,
                        unit
                )
                        : "—";


        TextView value =
                label(
                        name
                                + "\n"
                                + previousText
                                + " → "
                                + currentText
                                + "\n"
                                + change,
                        14,
                        COLOR_TEXT
                );


        value.setPadding(
                0,
                dp(9),
                0,
                dp(9)
        );


        card.addView(
                value
        );
    }


    // ============================================================
    // METRIC
    // ============================================================

    private void metric(
            LinearLayout card,
            String name,
            double value,
            String unit
    ) {

        metric(
                card,
                name,
                Double.valueOf(
                        value
                ),
                unit
        );
    }


    private void metric(
            LinearLayout card,
            String name,
            Double value,
            String unit
    ) {

        LinearLayout row =
                new LinearLayout(
                        this
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


        TextView caption =
                label(
                        name,
                        14,
                        COLOR_MUTED
                );


        TextView amount =
                label(
                        format(
                                value,
                                unit
                        ),
                        18,
                        COLOR_TEXT
                );


        amount.setGravity(
                Gravity.END
        );


        amount.setTypeface(
                null,
                Typeface.BOLD
        );


        row.addView(
                caption,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        row.addView(
                amount,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        card.addView(
                row
        );
    }


    // ============================================================
    // FORMAT
    // ============================================================

    private String format(
            Double value,
            String unit
    ) {

        if (
                value == null
                        || !Double.isFinite(
                        value
                )
        ) {

            return "—";
        }


        return numbers.format(
                value
        )
                + (
                unit == null
                        || unit.isEmpty()
                        ? ""
                        : " " + unit
        );
    }


    // ============================================================
    // CARD
    // ============================================================

    private LinearLayout card(
            String title
    ) {

        LinearLayout card =
                new LinearLayout(
                        this
                );


        card.setOrientation(
                LinearLayout.VERTICAL
        );


        card.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );


        GradientDrawable background =
                new GradientDrawable();


        background.setColor(
                GreenTheme.color(
                        R.color.zd_card
                )
        );


        background.setCornerRadius(
                dp(18)
        );


        background.setStroke(
                dp(1),
                GreenTheme.color(
                        R.color.zd_border
                )
        );


        card.setBackground(
                background
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        params.topMargin =
                dp(14);


        cards.addView(
                card,
                params
        );


        TextView heading =
                label(
                        title,
                        18,
                        COLOR_TEXT
                );


        heading.setTypeface(
                null,
                Typeface.BOLD
        );


        card.addView(
                heading
        );


        return card;
    }


    // ============================================================
    // SMALL INFO
    // ============================================================

    private TextView smallInfo(
            String value
    ) {

        TextView view =
                label(
                        value,
                        12,
                        COLOR_MUTED
                );


        view.setPadding(
                0,
                dp(5),
                0,
                dp(3)
        );


        return view;
    }


    // ============================================================
    // LABEL
    // ============================================================

    private TextView label(
            String value,
            int size,
            int color
    ) {

        TextView view =
                new TextView(
                        this
                );


        view.setText(
                value
        );


        view.setTextSize(
                size
        );


        view.setTextColor(
                color
        );


        return view;
    }


    // ============================================================
    // OWNER
    // ============================================================

    private boolean isSameOwner() {

        if (helper == null) {
            return false;
        }


        String activeOwner =
                helper
                        .getActiveOwnerUid();


        if (activeOwner == null) {

            activeOwner =
                    "";
        }


        String expectedOwner =
                screenOwner == null
                        ? ""
                        : screenOwner;


        return expectedOwner.equals(
                activeOwner
        );
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }


    // ============================================================
    // STATE
    // ============================================================

    @Override
    protected void onSaveInstanceState(
            Bundle state
    ) {

        super.onSaveInstanceState(
                state
        );


        state.putString(
                "owner",
                screenOwner
        );


        state.putLong(
                "garden",
                selectedGarden
        );
    }


    // ============================================================
    // DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        generation++;


        worker.shutdownNow();


        if (helper != null) {

            helper.close();

            helper =
                    null;
        }


        super.onDestroy();
    }
}