package com.zeytindefteri.app;

import android.app.Activity;
import android.app.Dialog;

import android.content.Intent;

import android.database.Cursor;

import android.graphics.Color;

import android.graphics.drawable.GradientDrawable;

import android.net.Uri;

import android.view.Gravity;
import android.view.View;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;


public class PostMediaManager {


    // ============================================================
    // ACTIVITY
    // ============================================================

    private final Activity activity;


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // RENKLER
    // ============================================================

    private final int COLOR_DARK_GREEN =
            GreenTheme.color(R.color.zd_text);


    private final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);


    private final int COLOR_MEDIA_BACKGROUND =
            GreenTheme.color(R.color.zd_green_soft);


    private final int COLOR_VIDEO_BACKGROUND =
            GreenTheme.color(R.color.zd_green_soft);


    private final int COLOR_VIDEO_BORDER =
            GreenTheme.color(R.color.zd_border);


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public PostMediaManager(
            Activity activity,
            DatabaseHelper databaseHelper
    ) {


        this.activity =
                activity;


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // POST MEDYASINI KARTA EKLE
    // ============================================================

    public void addPostMediaToCard(
            LinearLayout card,
            long postId,
            String legacyPhotoUri
    ) {


        if (card == null) {

            return;
        }


        if (postId <= 0) {


            addLegacyPhotoIfNeeded(
                    card,
                    legacyPhotoUri
            );


            return;
        }


        boolean hasMedia =
                false;


        Cursor cursor =
                null;


        try {


            cursor =
                    databaseHelper.getPostMedia(
                            postId
                    );


            while (cursor.moveToNext()) {


                String mediaType =
                        getStringSafe(
                                cursor,
                                "media_type",
                                ""
                        );


                String mediaUri =
                        getStringSafe(
                                cursor,
                                "media_uri",
                                ""
                        );


                if (mediaUri.isEmpty()) {

                    continue;
                }


                // =================================================
                // FOTOĞRAF
                // =================================================

                if ("IMAGE".equalsIgnoreCase(
                        mediaType
                )) {


                    addImageToCard(
                            card,
                            mediaUri
                    );


                    hasMedia =
                            true;


                    continue;
                }


                // =================================================
                // VİDEO
                // =================================================

                if ("VIDEO".equalsIgnoreCase(
                        mediaType
                )) {


                    addVideoToCard(
                            card,
                            mediaUri
                    );


                    hasMedia =
                            true;
                }
            }


        } catch (Exception e) {


            e.printStackTrace();


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        // ========================================================
        // ESKİ photo_uri DESTEĞİ
        //
        // post_media tablosunda kayıt yoksa eski gönderilerdeki
        // photo_uri alanını kullanmaya devam eder.
        // ========================================================

        if (!hasMedia) {


            addLegacyPhotoIfNeeded(
                    card,
                    legacyPhotoUri
            );
        }
    }


    // ============================================================
    // ESKİ FOTOĞRAF DESTEĞİ
    // ============================================================

    private void addLegacyPhotoIfNeeded(
            LinearLayout card,
            String legacyPhotoUri
    ) {


        String uri =
                safe(
                        legacyPhotoUri
                );


        if (uri.isEmpty()) {

            return;
        }


        addImageToCard(
                card,
                uri
        );
    }


    // ============================================================
    // FOTOĞRAFI KARTA EKLE
    // ============================================================

    private void addImageToCard(
            LinearLayout card,
            String photoUri
    ) {


        photoUri =
                safe(
                        photoUri
                );


        if (photoUri.isEmpty()) {

            return;
        }


        try {


            ImageView imageView =
                    new ImageView(
                            activity
                    );


            imageView.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );


            imageView.setAdjustViewBounds(
                    false
            );


            imageView.setBackground(
                    createRoundedBackground(
                            COLOR_MEDIA_BACKGROUND,
                            COLOR_BORDER,
                            16
                    )
            );


            /*
             * GradientDrawable outline olarak kullanılabildiği için
             * görsel kartın yuvarlak köşelerinden taşmaz.
             */
            imageView.setClipToOutline(
                    true
            );


            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(220)
                    );


            params.topMargin =
                    dp(12);


            imageView.setLayoutParams(
                    params
            );


            // ====================================================
            // URI
            // ====================================================

            Uri uri =
                    Uri.parse(
                            photoUri
                    );


            if (photoUri.startsWith("http://") || photoUri.startsWith("https://")) {
                Glide.with(activity).load(photoUri).into(imageView);
            } else {
                imageView.setImageURI(uri);
            }


            // ====================================================
            // TIKLANINCA TAM EKRAN
            // ====================================================

            final String selectedUri =
                    photoUri;


            imageView.setOnClickListener(
                    view -> showFullScreenImage(
                            selectedUri
                    )
            );


            card.addView(
                    imageView
            );


        } catch (Exception e) {


            e.printStackTrace();


            addMediaErrorView(
                    card,
                    "Fotoğraf açılamadı"
            );
        }
    }


    // ============================================================
    // VİDEOYU KARTA EKLE
    // ============================================================

    private void addVideoToCard(
            LinearLayout card,
            String videoUri
    ) {


        videoUri =
                safe(
                        videoUri
                );


        if (videoUri.isEmpty()) {

            return;
        }


        TextView videoButton =
                new TextView(
                        activity
                );


        videoButton.setText(
                "▶  Videoyu Aç"
        );


        videoButton.setTextSize(
                12
        );


        videoButton.setTextColor(
                COLOR_DARK_GREEN
        );


        videoButton.setGravity(
                Gravity.CENTER
        );


        videoButton.setClickable(
                true
        );


        videoButton.setFocusable(
                true
        );


        videoButton.setBackground(
                createRoundedBackground(
                        COLOR_VIDEO_BACKGROUND,
                        COLOR_VIDEO_BORDER,
                        16
                )
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60)
                );


        params.topMargin =
                dp(12);


        final String selectedUri =
                videoUri;


        videoButton.setOnClickListener(
                view -> openVideo(
                        selectedUri
                )
        );


        card.addView(
                videoButton,
                params
        );
    }


    // ============================================================
    // TAM EKRAN FOTOĞRAF
    // ============================================================

    public void showFullScreenImage(
            String photoUri
    ) {


        photoUri =
                safe(
                        photoUri
                );


        if (photoUri.isEmpty()) {


            Toast.makeText(
                    activity,
                    "Fotoğraf bulunamadı.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        final Dialog dialog =
                new Dialog(
                        activity,
                        android.R.style.Theme_Black_NoTitleBar_Fullscreen
                );


        ImageView imageView =
                new ImageView(
                        activity
                );


        imageView.setBackgroundColor(
                Color.BLACK
        );


        imageView.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );


        imageView.setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(4)
        );


        try {


            imageView.setImageURI(
                    Uri.parse(
                            photoUri
                    )
            );


        } catch (Exception e) {


            e.printStackTrace();


            Toast.makeText(
                    activity,
                    "Fotoğraf açılamadı.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        // ========================================================
        // FOTOĞRAFA DOKUNUNCA KAPAT
        // ========================================================

        imageView.setOnClickListener(
                view -> dialog.dismiss()
        );


        dialog.setContentView(
                imageView
        );


        dialog.show();
    }


    // ============================================================
    // VİDEO AÇ
    // ============================================================

    public void openVideo(
            String videoUri
    ) {


        videoUri =
                safe(
                        videoUri
                );


        if (videoUri.isEmpty()) {


            Toast.makeText(
                    activity,
                    "Video bulunamadı.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        try {


            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW
                    );


            intent.setDataAndType(
                    Uri.parse(
                            videoUri
                    ),
                    "video/*"
            );


            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );


            activity.startActivity(
                    intent
            );


        } catch (Exception e) {


            e.printStackTrace();


            Toast.makeText(
                    activity,
                    "Videoyu açabilecek bir uygulama bulunamadı.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // MEDYA HATA GÖRÜNÜMÜ
    // ============================================================

    private void addMediaErrorView(
            LinearLayout card,
            String message
    ) {


        TextView errorView =
                new TextView(
                        activity
                );


        errorView.setText(
                message
        );


        errorView.setTextSize(
                12
        );


        errorView.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        errorView.setGravity(
                Gravity.CENTER
        );


        errorView.setBackground(
                createRoundedBackground(
                        COLOR_MEDIA_BACKGROUND,
                        COLOR_BORDER,
                        16
                )
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(70)
                );


        params.topMargin =
                dp(12);


        card.addView(
                errorView,
                params
        );
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


        if (index < 0) {

            return defaultValue;
        }


        if (cursor.isNull(
                index
        )) {


            return defaultValue;
        }


        String value =
                cursor.getString(
                        index
                );


        if (value == null) {

            return defaultValue;
        }


        return value.trim();
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
    // SAFE STRING
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
}
