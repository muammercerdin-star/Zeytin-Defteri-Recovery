package com.zeytindefteri.app;


/**
 * Bahçe / sezon analitiği için immutable local snapshot.
 *
 * Bu sınıf:
 *
 * - SQLite sorgusu çalıştırmaz.
 * - Firebase kullanmaz.
 * - UI oluşturmaz.
 * - Yalnız repository tarafından hazırlanmış gerçek yerel değerleri taşır
 *   ve güvenli türetilmiş hesapları yapar.
 *
 * Eksik oranlar null ile temsil edilir.
 * NaN / Infinity hiçbir zaman UI'ya bırakılmaz.
 */
public final class GardenAnalyticsSnapshot {


    // ============================================================
    // ELEK
    // ============================================================

    public static final String[] GRADE_LABELS = {
            "Double",
            "1 No",
            "2 No",
            "3 No",
            "4 No",
            "Yağlık"
    };


    /*
     * Hasat ile Elek toplamını karşılaştırırken küçük floating-point
     * farklarının kullanıcıya sahte uyarı üretmesini engeller.
     *
     * 0.01 kg = 10 gram.
     */
    private static final double HARVEST_ELEK_TOLERANCE_KG =
            0.01d;


    // ============================================================
    // KİMLİK
    // ============================================================

    public final long gardenId;

    public final String name;
    public final String variety;

    public final int season;


    // ============================================================
    // KAYIT SAYILARI
    // ============================================================

    public final int gardenCount;

    public final int harvestRecords;
    public final int expenseRecords;
    public final int saleRecords;
    public final int elekRecords;


    // ============================================================
    // BAHÇE META
    // ============================================================

    public final double trees;
    public final double area;

    public final boolean completeTrees;
    public final boolean completeArea;


    // ============================================================
    // ÜRETİM / MASRAF
    // ============================================================

    public final double harvestKg;
    public final double expense;


    // ============================================================
    // SATIŞ
    // ============================================================

    public final double revenue;
    public final double grossSales;
    public final double soldKg;

    public final double collected;
    public final double receivable;


    // ============================================================
    // SALAMURA
    // ============================================================

    public final double brineCost;
    public final double brineOliveKg;
    public final double readyBrineKg;

    public final int activeBrine;


    // ============================================================
    // BAHÇEYE BAĞLANAMAYAN KAYITLAR
    // ============================================================

    public final double unassignedCost;
    public final double unassignedReceivable;


    // ============================================================
    // ELEK DAĞILIMI
    // ============================================================

    private final double[] grades;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    GardenAnalyticsSnapshot(
            Builder builder,
            int year
    ) {

        gardenId =
                builder.id;


        name =
                builder.name == null
                        ? ""
                        : builder.name;


        variety =
                builder.variety == null
                        ? ""
                        : builder.variety;


        season =
                year;


        gardenCount =
                builder.gardens;


        harvestRecords =
                builder.harvestRecords;


        expenseRecords =
                builder.expenseRecords;


        saleRecords =
                builder.saleRecords;


        elekRecords =
                builder.elekRecords;


        trees =
                finite(
                        builder.trees
                );


        area =
                finite(
                        builder.area
                );


        harvestKg =
                finite(
                        builder.harvest
                );


        expense =
                finite(
                        builder.expense
                );


        revenue =
                finite(
                        builder.revenue
                );


        grossSales =
                finite(
                        builder.gross
                );


        soldKg =
                finite(
                        builder.sold
                );


        collected =
                finite(
                        builder.collected
                );


        receivable =
                finite(
                        builder.receivable
                );


        brineCost =
                finite(
                        builder.brineCost
                );


        brineOliveKg =
                finite(
                        builder.brineOlive
                );


        readyBrineKg =
                finite(
                        builder.ready
                );


        activeBrine =
                builder.activeBrine;


        completeTrees =
                builder.completeTrees;


        completeArea =
                builder.completeArea;


        unassignedCost =
                finite(
                        builder.unassignedCost
                );


        unassignedReceivable =
                finite(
                        builder.unassignedReceivable
                );


        grades =
                builder.grades.clone();


        /*
         * Tek tek Elek değerlerini doğrula.
         */
        for (
                double grade :
                grades
        ) {

            finite(
                    grade
            );
        }


        /*
         * Türetilmiş toplamların da taşma / Infinity üretmediğini
         * snapshot UI'ya verilmeden doğrula.
         */
        finite(
                revenue - expense
        );


        totalGradeKg();
    }


    // ============================================================
    // VERİM
    // ============================================================

    public Double kgPerTree() {

        if (!completeTrees) {

            return null;
        }


        return ratio(
                harvestKg,
                trees
        );
    }


    public Double kgPerDecare() {

        if (!completeArea) {

            return null;
        }


        return ratio(
                harvestKg,
                area
        );
    }


    // ============================================================
    // MALİYET
    // ============================================================

    public Double costPerKg() {

        return ratio(
                expense,
                harvestKg
        );
    }


    // ============================================================
    // SATIŞ
    // ============================================================

    /**
     * Ağırlıklı ortalama satış fiyatı.
     *
     * Basit fiyat ortalaması değildir.
     *
     * grossSales / soldKg
     */
    public Double averageSalePrice() {

        return ratio(
                grossSales,
                soldKg
        );
    }


    // ============================================================
    // KAYITLI NET SONUÇ
    // ============================================================

    public double netResult() {

        return finite(
                revenue - expense
        );
    }


    // ============================================================
    // ELEK TEK SINIF
    // ============================================================

    public double gradeKg(
            int index
    ) {

        if (
                index < 0
                        || index >= grades.length
        ) {

            throw new IndexOutOfBoundsException(
                    "Geçersiz Elek indeksi: "
                            + index
            );
        }


        return finite(
                grades[index]
        );
    }


    // ============================================================
    // ELEK TOPLAMI
    // ============================================================

    /**
     * Double + 1 + 2 + 3 + 4 + Yağlık toplamı.
     *
     * Örnek:
     *
     * 15 + 37 + 67 + 82 + 61 + 18 = 280 kg
     */
    public double totalGradeKg() {

        double total =
                0d;


        for (
                double grade :
                grades
        ) {

            total +=
                    grade;
        }


        return finite(
                total
        );
    }


    // ============================================================
    // BASKIN ELEK
    // ============================================================

    /**
     * En fazla kilogramı bulunan Elek sınıfını döndürür.
     *
     * Eşitlikte GRADE_LABELS sırasındaki ilk sınıf kazanır.
     * Böylece sonuç deterministiktir.
     */
    public String dominantGrade() {

        int best =
                -1;


        for (
                int i = 0;
                i < grades.length;
                i++
        ) {

            if (
                    grades[i] > 0
                            && (
                            best < 0
                                    || grades[i] > grades[best]
                    )
            ) {

                best =
                        i;
            }
        }


        if (best < 0) {

            return "Elek kaydı yok";
        }


        return GRADE_LABELS[best];
    }


    // ============================================================
    // HASAT <-> ELEK KARŞILAŞTIRMASI
    // ============================================================

    /**
     * Hasat ve Elek tarafında gerçek kayıt varsa iki veri kümesi
     * fiziksel tutarlılık açısından karşılaştırılabilir.
     *
     * Bir tarafta hiç kayıt yoksa bunu "tutarsızlık" olarak
     * etiketlemiyoruz.
     */
    public boolean hasComparableHarvestAndElek() {

        return harvestRecords > 0
                && elekRecords > 0;
    }


    /**
     * Elek toplamı - hasat toplamı.
     *
     * Pozitif:
     * Elek toplamı hasattan fazla.
     *
     * Negatif:
     * Elek toplamı hasattan az.
     *
     * Örnek:
     *
     * 280 - 190 = +90 kg
     */
    public double harvestElekDifferenceKg() {

        return finite(
                totalGradeKg()
                        - harvestKg
        );
    }


    /**
     * Hasat ile Elek arasındaki farkın yönsüz büyüklüğü.
     *
     * Örnek:
     *
     * |280 - 190| = 90 kg
     */
    public double absoluteHarvestElekDifferenceKg() {

        return finite(
                Math.abs(
                        harvestElekDifferenceKg()
                )
        );
    }


    /**
     * Hasat ve Elek kayıtları mevcutsa ve iki toplam arasındaki
     * fark toleranstan büyükse gerçek veri tutarsızlığı vardır.
     *
     * Bu metot yalnız "uyarı gösterilmeli mi?" kararını verir.
     * Kullanıcı mesajını UI katmanı oluşturur.
     */
    public boolean hasHarvestElekMismatch() {

        if (!hasComparableHarvestAndElek()) {

            return false;
        }


        return absoluteHarvestElekDifferenceKg()
                > HARVEST_ELEK_TOLERANCE_KG;
    }


    /**
     * Elek toplamı hasattan ne kadar fazla?
     *
     * Fazla değilse 0 döner.
     */
    public double elekExcessKg() {

        if (!hasComparableHarvestAndElek()) {

            return 0d;
        }


        return Math.max(
                0d,
                harvestElekDifferenceKg()
        );
    }


    /**
     * Elek toplamı hasattan ne kadar eksik?
     *
     * Eksik değilse 0 döner.
     */
    public double elekMissingKg() {

        if (!hasComparableHarvestAndElek()) {

            return 0d;
        }


        return Math.max(
                0d,
                -harvestElekDifferenceKg()
        );
    }


    // ============================================================
    // GÜVENLİ ORAN
    // ============================================================

    public static Double ratio(
            double numerator,
            double denominator
    ) {

        if (
                !Double.isFinite(
                        numerator
                )
                        || !Double.isFinite(
                        denominator
                )
                        || denominator <= 0d
        ) {

            return null;
        }


        double result =
                numerator
                        / denominator;


        if (!Double.isFinite(result)) {

            return null;
        }


        return result;
    }


    // ============================================================
    // YÜZDE DEĞİŞİM
    // ============================================================

    public static Double percent(
            Double current,
            Double previous,
            boolean currentData,
            boolean previousData
    ) {

        if (
                !currentData
                        || !previousData
                        || current == null
                        || previous == null
                        || previous == 0d
                        || !Double.isFinite(
                        current
                )
                        || !Double.isFinite(
                        previous
                )
        ) {

            return null;
        }


        double value =
                (
                        current
                                - previous
                )
                        / previous
                        * 100d;


        if (!Double.isFinite(value)) {

            return null;
        }


        return value;
    }


    // ============================================================
    // FINITE KORUMASI
    // ============================================================

    private static double finite(
            double value
    ) {

        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    "Geçersiz sayısal kayıt"
            );
        }


        return value;
    }


    // ============================================================
    // BUILDER
    // ============================================================

    static final class Builder {

        long id;

        String name =
                "";

        String variety =
                "";


        int gardens;

        int harvestRecords;
        int expenseRecords;
        int saleRecords;
        int elekRecords;

        int activeBrine;


        double trees;
        double area;

        double harvest;
        double expense;

        double revenue;
        double gross;
        double sold;

        double collected;
        double receivable;

        double brineCost;
        double brineOlive;
        double ready;

        double unassignedCost;
        double unassignedReceivable;


        boolean completeTrees =
                true;

        boolean completeArea =
                true;


        final double[] grades =
                new double[6];


        // ========================================================
        // TOPLAM SNAPSHOT İÇİN BİRLEŞTİR
        // ========================================================

        void add(
                Builder other
        ) {

            if (other == null) {
                return;
            }


            gardens +=
                    other.gardens;


            trees +=
                    other.trees;


            area +=
                    other.area;


            completeTrees &=
                    other.completeTrees;


            completeArea &=
                    other.completeArea;


            harvestRecords +=
                    other.harvestRecords;


            expenseRecords +=
                    other.expenseRecords;


            saleRecords +=
                    other.saleRecords;


            elekRecords +=
                    other.elekRecords;


            harvest +=
                    other.harvest;


            expense +=
                    other.expense;


            revenue +=
                    other.revenue;


            gross +=
                    other.gross;


            sold +=
                    other.sold;


            collected +=
                    other.collected;


            receivable +=
                    other.receivable;


            brineCost +=
                    other.brineCost;


            brineOlive +=
                    other.brineOlive;


            ready +=
                    other.ready;


            activeBrine +=
                    other.activeBrine;


            for (
                    int i = 0;
                    i < grades.length;
                    i++
            ) {

                grades[i] +=
                        other.grades[i];
            }
        }
    }
}