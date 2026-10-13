package com.zeytindefteri.app;

import android.app.Activity;

import android.database.Cursor;

import android.graphics.Color;
import android.graphics.Typeface;

import android.graphics.drawable.GradientDrawable;

import android.net.Uri;

import android.view.Gravity;
import android.view.View;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;


public class GardenHomeManager {


    // ============================================================
    // ACTIVITY
    // ============================================================

    private final Activity activity;


    // ============================================================
    // REPOSITORY
    // ============================================================

    private final GardenRepository gardenRepository;


    // ============================================================
    // CALLBACK
    // ============================================================

    private final GardenActionListener gardenActionListener;


    // ============================================================
    // LOCALE
    // ============================================================

    private final Locale TR =
            new Locale(
                    "tr",
                    "TR"
            );


    // ============================================================
    // COLORS
    // ============================================================

    private static final int COLOR_DARK_GREEN =
            GreenTheme.color(R.color.zd_text);


    private static final int COLOR_GREEN =
            GreenTheme.parse(
                    "#2F6B3F"
            );


    private static final int COLOR_SOFT_GREEN =
            GreenTheme.color(R.color.zd_muted);


    private static final int COLOR_GRAY =
            GreenTheme.color(R.color.zd_muted);


    private static final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);


    private static final int COLOR_WHITE =
            GreenTheme.color(R.color.zd_card);


    private static final int COLOR_LIGHT_GREEN =
            GreenTheme.color(R.color.zd_green_soft);


    // ============================================================
    // CALLBACK INTERFACE
    // ============================================================

    public interface GardenActionListener {


        void onGardenSelected(
                long gardenId
        );
    }


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public GardenHomeManager(
            Activity activity,
            GardenRepository gardenRepository,
            GardenActionListener gardenActionListener
    ) {


        if (activity == null) {


            throw new IllegalArgumentException(
                    "Activity null olamaz."
            );
        }


        if (gardenRepository == null) {


            throw new IllegalArgumentException(
                    "GardenRepository null olamaz."
            );
        }


        this.activity =
                activity;


        this.gardenRepository =
                gardenRepository;


        this.gardenActionListener =
                gardenActionListener;
    }


    // ============================================================
    // BAHÇELERİ YÜKLE
    // ============================================================

    public void loadGardens(
            LinearLayout container,
            String searchText
    ) {


        /*
         * Ana ekrandaki büyük hero kart
         * her yüklemede gerçek ilk bahçeden güncellenir.
         */
        loadHeroFromFirstGarden();


        if (container == null) {


            return;
        }


        container.removeAllViews();


        String normalizedSearch =
                safe(
                        searchText
                )
                        .toLowerCase(
                                TR
                        );


        Cursor cursor =
                null;


        int added =
                0;


        try {


            cursor =
                    gardenRepository.getGardenSummaries();


            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {


                long gardenId =
                        getLongSafe(
                                cursor,
                                "id",
                                0
                        );


                String name =
                        getStringSafe(
                                cursor,
                                "name",
                                "Bahçe"
                        );


                int treeCount =
                        getIntSafe(
                                cursor,
                                "tree_count",
                                0
                        );


                int age =
                        getIntSafe(
                                cursor,
                                "age",
                                0
                        );


                String variety =
                        getStringSafe(
                                cursor,
                                "variety",
                                ""
                        );


                String lastOperation =
                        getStringSafe(
                                cursor,
                                "last_operation",
                                ""
                        );


                double totalCost =
                        getDoubleSafe(
                                cursor,
                                "total_cost",
                                0
                        );


                double totalHarvest =
                        getDoubleSafe(
                                cursor,
                                "total_harvest",
                                0
                        );


                // =================================================
                // SEARCH
                // =================================================

                if (!normalizedSearch.isEmpty()) {


                    String searchableText =
                            (
                                    safe(name)
                                            +
                                            " "
                                            +
                                            safe(variety)
                                            +
                                            " "
                                            +
                                            safe(lastOperation)
                            )
                                    .toLowerCase(
                                            TR
                                    );


                    if (!searchableText.contains(
                            normalizedSearch
                    )) {


                        continue;
                    }
                }


                // =================================================
                // CARD
                // =================================================

                View card =
                        createGardenCard(
                                gardenId,
                                name,
                                treeCount,
                                age,
                                variety,
                                lastOperation,
                                totalCost,
                                totalHarvest
                        );


                container.addView(
                        card
                );


                added++;
            }


        } catch (Exception e) {


            e.printStackTrace();


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        // ========================================================
        // EMPTY
        // ========================================================

        if (added == 0) {


            String message;


            if (normalizedSearch.isEmpty()) {


                message =
                        "Henüz bahçe eklenmedi 🌳";


            } else {


                message =
                        "Aramana uygun bahçe bulunamadı 🔍";
            }


            container.addView(
                    createEmptyView(
                            message
                    )
            );
        }
    }


    // ============================================================
    // ANA EKRAN HERO BAHÇE
    // ============================================================

    private void loadHeroFromFirstGarden() {


        Cursor cursor =
                null;


        try {


            cursor =
                    gardenRepository.getGardenSummaries();


            if (cursor != null
                    &&
                    cursor.moveToFirst()) {


                String photoUri =
                        getStringSafe(
                                cursor,
                                "photo_uri",
                                ""
                        );


                updateHero(
                        getStringSafe(
                                cursor,
                                "name",
                                "Bahçe"
                        ),
                        getIntSafe(
                                cursor,
                                "tree_count",
                                0
                        ),
                        getIntSafe(
                                cursor,
                                "age",
                                0
                        ),
                        getStringSafe(
                                cursor,
                                "variety",
                                ""
                        ),
                        getStringSafe(
                                cursor,
                                "last_operation",
                                ""
                        ),
                        photoUri
                );


            } else {


                showEmptyHero();
            }


        } catch (Exception e) {


            e.printStackTrace();


            showEmptyHero();


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    // ============================================================
    // HERO UPDATE
    // ============================================================

    private void updateHero(
            String name,
            int treeCount,
            int age,
            String variety,
            String lastOperation,
            String photoUri
    ) {


        TextView title =
                activity.findViewById(
                        R.id.btnViewGarden
                );


        TextView meta =
                activity.findViewById(
                        R.id.tvHeroGardenMeta
                );


        TextView last =
                activity.findViewById(
                        R.id.tvHeroLastOperation
                );


        ImageView heroImage =
                activity.findViewById(
                        R.id.imgSelectedGarden
                );


        // ========================================================
        // HERO PHOTO
        // ========================================================

        loadHeroPhoto(
                heroImage,
                photoUri
        );


        // ========================================================
        // TITLE
        // ========================================================

        if (title != null) {


            title.setText(
                    safeOr(
                            name,
                            "Bahçe"
                    )
                            +
                            "  ›"
            );
        }


        // ========================================================
        // META
        // ========================================================

        StringBuilder info =
                new StringBuilder();


        if (treeCount > 0) {


            info.append(
                    treeCount
            )
                    .append(
                            " ağaç"
                    );
        }


        if (age > 0) {


            appendMetaSeparator(
                    info
            );


            info.append(
                    age
            )
                    .append(
                            " yaş"
                    );
        }


        if (!safe(
                variety
        ).isEmpty()) {


            appendMetaSeparator(
                    info
            );


            info.append(
                    variety
            );
        }


        if (meta != null) {


            if (info.length() > 0) {


                meta.setText(
                        info.toString()
                );


            } else {


                meta.setText(
                        "Bahçe bilgileri henüz eklenmedi"
                );
            }
        }


        // ========================================================
        // LAST OPERATION
        // ========================================================

        if (last != null) {


            String operation =
                    safe(
                            lastOperation
                    );


            if (operation.isEmpty()) {


                last.setText(
                        "Son işlem: Henüz kayıt yok"
                );


            } else {


                last.setText(
                        "Son işlem: "
                                +
                                operation
                );
            }
        }
    }


    // ============================================================
    // HERO PHOTO
    // ============================================================

    private void loadHeroPhoto(
            ImageView imageView,
            String photoUri
    ) {


        if (imageView == null) {


            return;
        }


        /*
         * Önce her zaman güvenli varsayılan fotoğrafı koyuyoruz.
         *
         * URI okuma başarısız olursa ana ekran boş veya kırık
         * görsel göstermeyecek.
         */
        imageView.setImageResource(
                R.drawable.onboarding_garden_1
        );


        String uriText =
                safe(
                        photoUri
                );


        if (uriText.isEmpty()) {


            return;
        }


        try {


            Uri uri =
                    Uri.parse(
                            uriText
                    );


            if (uri == null) {


                return;
            }


            imageView.setImageURI(
                    uri
            );


            /*
             * Bazı eski / geçersiz URI'lerde setImageURI
             * hata vermeden drawable null bırakabilir.
             */
            if (imageView.getDrawable() == null) {


                imageView.setImageResource(
                        R.drawable.onboarding_garden_1
                );
            }


        } catch (Exception e) {


            /*
             * Fotoğraf yolu artık mevcut değilse uygulama
             * çökmemeli. Varsayılan zeytinlik resmi kullanılır.
             */
            imageView.setImageResource(
                    R.drawable.onboarding_garden_1
            );
        }
    }


    // ============================================================
    // META SEPARATOR
    // ============================================================

    private void appendMetaSeparator(
            StringBuilder builder
    ) {


        if (builder == null) {


            return;
        }


        if (builder.length() > 0) {


            builder.append(
                    "   •   "
            );
        }
    }


    // ============================================================
    // EMPTY HERO
    // ============================================================

    private void showEmptyHero() {


        TextView title =
                activity.findViewById(
                        R.id.btnViewGarden
                );


        TextView meta =
                activity.findViewById(
                        R.id.tvHeroGardenMeta
                );


        TextView last =
                activity.findViewById(
                        R.id.tvHeroLastOperation
                );


        ImageView heroImage =
                activity.findViewById(
                        R.id.imgSelectedGarden
                );


        if (heroImage != null) {


            heroImage.setImageResource(
                    R.drawable.onboarding_garden_1
            );
        }


        if (title != null) {


            title.setText(
                    "İlk bahçeni oluştur  ›"
            );
        }


        if (meta != null) {


            meta.setText(
                    "Bahçe bilgilerin burada görünecek"
            );
        }


        if (last != null) {


            last.setText(
                    "Henüz işlem kaydı yok"
            );
        }
    }


    // ============================================================
    // GARDEN CARD
    // ============================================================

    private View createGardenCard(
            long gardenId,
            String name,
            int treeCount,
            int age,
            String variety,
            String lastOperation,
            double totalCost,
            double totalHarvest
    ) {


        // ========================================================
        // MAIN CARD
        // ========================================================

        LinearLayout card =
                new LinearLayout(
                        activity
                );


        card.setOrientation(
                LinearLayout.HORIZONTAL
        );


        card.setGravity(
                Gravity.CENTER_VERTICAL
        );


        card.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );


        card.setClickable(
                true
        );


        card.setFocusable(
                true
        );


        card.setBackground(
                createRoundedBackground(
                        COLOR_WHITE,
                        COLOR_BORDER,
                        18
                )
        );


        card.setElevation(
                dp(1)
        );


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        cardParams.topMargin =
                dp(10);


        card.setLayoutParams(
                cardParams
        );


        // ========================================================
        // ICON
        // ========================================================

        TextView icon =
                createText(
                        "🌳",
                        27,
                        COLOR_GREEN,
                        false
                );


        icon.setGravity(
                Gravity.CENTER
        );


        icon.setBackground(
                createRoundedBackground(
                        COLOR_LIGHT_GREEN,
                        GreenTheme.color(R.color.zd_border),
                        100
                )
        );


        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );


        // ========================================================
        // CONTENT
        // ========================================================

        LinearLayout content =
                new LinearLayout(
                        activity
                );


        content.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );


        contentParams.leftMargin =
                dp(12);


        contentParams.rightMargin =
                dp(8);


        // ========================================================
        // NAME
        // ========================================================

        TextView title =
                createText(
                        safeOr(
                                name,
                                "Bahçe"
                        ),
                        16,
                        COLOR_DARK_GREEN,
                        true
                );


        content.addView(
                title
        );


        // ========================================================
        // TREE / AGE / VARIETY
        // ========================================================

        StringBuilder gardenInfo =
                new StringBuilder();


        if (treeCount > 0) {


            gardenInfo.append(
                    treeCount
            )
                    .append(
                            " ağaç"
                    );
        }


        if (age > 0) {


            appendMetaSeparator(
                    gardenInfo
            );


            gardenInfo.append(
                    age
            )
                    .append(
                            " yaş"
                    );
        }


        if (!safe(
                variety
        ).isEmpty()) {


            appendMetaSeparator(
                    gardenInfo
            );


            gardenInfo.append(
                    variety
            );
        }


        TextView infoView =
                createText(
                        gardenInfo.length() > 0
                                ?
                                gardenInfo.toString()
                                :
                                "Bahçe bilgileri eksik",
                        12,
                        COLOR_SOFT_GREEN,
                        false
                );


        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        infoParams.topMargin =
                dp(2);


        content.addView(
                infoView,
                infoParams
        );


        // ========================================================
        // LAST OPERATION
        // ========================================================

        String lastText;


        if (safe(
                lastOperation
        ).isEmpty()) {


            lastText =
                    "Henüz işlem yok";


        } else {


            lastText =
                    "Son işlem: "
                            +
                            lastOperation;
        }


        TextView lastView =
                createText(
                        lastText,
                        11,
                        COLOR_SOFT_GREEN,
                        false
                );


        LinearLayout.LayoutParams lastParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        lastParams.topMargin =
                dp(2);


        content.addView(
                lastView,
                lastParams
        );


        // ========================================================
        // COST + HARVEST
        // ========================================================

        String financialText =
                "Masraf "
                        +
                        formatMoney(
                                totalCost
                        )
                        +
                        " • Hasat "
                        +
                        formatKg(
                                totalHarvest
                        );


        TextView financialView =
                createText(
                        financialText,
                        10,
                        COLOR_GRAY,
                        false
                );


        LinearLayout.LayoutParams financialParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        financialParams.topMargin =
                dp(3);


        content.addView(
                financialView,
                financialParams
        );


        card.addView(
                content,
                contentParams
        );


        // ========================================================
        // DETAIL BUTTON
        // ========================================================

        TextView detailButton =
                createText(
                        "Detay",
                        11,
                        Color.WHITE,
                        true
                );


        detailButton.setGravity(
                Gravity.CENTER
        );


        detailButton.setClickable(
                true
        );


        detailButton.setFocusable(
                true
        );


        detailButton.setBackground(
                createRoundedBackground(
                        COLOR_GREEN,
                        COLOR_GREEN,
                        100
                )
        );


        card.addView(
                detailButton,
                new LinearLayout.LayoutParams(
                        dp(68),
                        dp(38)
                )
        );


        // ========================================================
        // CLICK
        // ========================================================

        View.OnClickListener openGardenListener =
                view -> openGarden(
                        gardenId
                );


        detailButton.setOnClickListener(
                openGardenListener
        );


        card.setOnClickListener(
                openGardenListener
        );


        return card;
    }


    // ============================================================
    // İLK BAHÇEYİ AÇ
    // ============================================================

    public void openFirstGarden() {


        Cursor cursor =
                null;


        try {


            /*
             * Hero hangi sıralamadan bahçe okuyorsa
             * tıklamada da aynı kaynağı kullanıyoruz.
             *
             * Böylece kullanıcı hero'da gördüğü bahçeye gider.
             */
            cursor =
                    gardenRepository.getGardenSummaries();


            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {


                long gardenId =
                        getLongSafe(
                                cursor,
                                "id",
                                0
                        );


                if (gardenId > 0) {


                    openGarden(
                            gardenId
                    );


                    return;
                }
            }


        } catch (Exception e) {


            e.printStackTrace();


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        Toast.makeText(
                activity,
                "Önce bir bahçe eklemelisin 🌳",
                Toast.LENGTH_SHORT
        ).show();
    }


    // ============================================================
    // GARDEN COUNT
    // ============================================================

    public int getGardenCount() {


        try {


            return gardenRepository.getGardenCount();


        } catch (Exception e) {


            e.printStackTrace();


            return 0;
        }
    }


    // ============================================================
    // OPEN GARDEN
    // ============================================================

    private void openGarden(
            long gardenId
    ) {


        if (gardenId <= 0) {


            Toast.makeText(
                    activity,
                    "Bahçe bulunamadı.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        if (gardenActionListener != null) {


            gardenActionListener.onGardenSelected(
                    gardenId
            );
        }
    }


    // ============================================================
    // EMPTY VIEW
    // ============================================================

    private View createEmptyView(
            String message
    ) {


        LinearLayout root =
                new LinearLayout(
                        activity
                );


        root.setOrientation(
                LinearLayout.VERTICAL
        );


        root.setGravity(
                Gravity.CENTER
        );


        root.setPadding(
                dp(16),
                dp(20),
                dp(16),
                dp(20)
        );


        TextView icon =
                createText(
                        "🌿",
                        26,
                        COLOR_GREEN,
                        false
                );


        icon.setGravity(
                Gravity.CENTER
        );


        root.addView(
                icon
        );


        TextView text =
                createText(
                        safeOr(
                                message,
                                "Bahçe bulunamadı"
                        ),
                        12,
                        COLOR_GRAY,
                        false
                );


        text.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        textParams.topMargin =
                dp(6);


        root.addView(
                text,
                textParams
        );


        return root;
    }


    // ============================================================
    // CREATE TEXT
    // ============================================================

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {


        TextView view =
                new TextView(
                        activity
                );


        view.setText(
                text
        );


        view.setTextSize(
                size
        );


        view.setTextColor(
                color
        );


        if (bold) {


            view.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }


        return view;
    }


    // ============================================================
    // BACKGROUND
    // ============================================================

    private GradientDrawable createRoundedBackground(
            int fillColor,
            int strokeColor,
            int radiusDp
    ) {


        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                fillColor
        );


        drawable.setCornerRadius(
                dp(
                        radiusDp
                )
        );


        drawable.setStroke(
                dp(1),
                strokeColor
        );


        return drawable;
    }


    // ============================================================
    // MONEY FORMAT
    // ============================================================

    private String formatMoney(
            double value
    ) {


        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        TR
                );


        numberFormat.setMaximumFractionDigits(
                2
        );


        numberFormat.setMinimumFractionDigits(
                0
        );


        return numberFormat.format(
                value
        )
                +
                " TL";
    }


    // ============================================================
    // KG FORMAT
    // ============================================================

    private String formatKg(
            double value
    ) {


        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        TR
                );


        numberFormat.setMaximumFractionDigits(
                2
        );


        numberFormat.setMinimumFractionDigits(
                0
        );


        return numberFormat.format(
                value
        )
                +
                " kg";
    }


    // ============================================================
    // CURSOR STRING
    // ============================================================

    private String getStringSafe(
            Cursor cursor,
            String column,
            String defaultValue
    ) {


        if (cursor == null) {


            return defaultValue;
        }


        int index =
                cursor.getColumnIndex(
                        column
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {


            return defaultValue;
        }


        String value =
                cursor.getString(
                        index
                );


        return value == null
                ?
                defaultValue
                :
                value;
    }


    // ============================================================
    // CURSOR INT
    // ============================================================

    private int getIntSafe(
            Cursor cursor,
            String column,
            int defaultValue
    ) {


        if (cursor == null) {


            return defaultValue;
        }


        int index =
                cursor.getColumnIndex(
                        column
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {


            return defaultValue;
        }


        return cursor.getInt(
                index
        );
    }


    // ============================================================
    // CURSOR LONG
    // ============================================================

    private long getLongSafe(
            Cursor cursor,
            String column,
            long defaultValue
    ) {


        if (cursor == null) {


            return defaultValue;
        }


        int index =
                cursor.getColumnIndex(
                        column
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {


            return defaultValue;
        }


        return cursor.getLong(
                index
        );
    }


    // ============================================================
    // CURSOR DOUBLE
    // ============================================================

    private double getDoubleSafe(
            Cursor cursor,
            String column,
            double defaultValue
    ) {


        if (cursor == null) {


            return defaultValue;
        }


        int index =
                cursor.getColumnIndex(
                        column
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {


            return defaultValue;
        }


        return cursor.getDouble(
                index
        );
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safe(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }


    // ============================================================
    // SAFE OR
    // ============================================================

    private String safeOr(
            String value,
            String defaultValue
    ) {


        String result =
                safe(
                        value
                );


        if (result.isEmpty()) {


            return defaultValue;
        }


        return result;
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {


        float density =
                activity
                        .getResources()
                        .getDisplayMetrics()
                        .density;


        return Math.round(
                value
                        *
                        density
        );
    }


    // ============================================================
    // RELEASE
    // ============================================================

    public void release() {


        /*
         * Şimdilik açık tutulması gereken Android kaynağı yok.
         *
         * İleride:
         *
         * - görsel yükleyici
         * - observer
         * - executor
         * - network listener
         *
         * eklenirse burada temizlenebilir.
         */
    }
}
