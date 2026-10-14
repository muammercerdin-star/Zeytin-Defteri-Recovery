package com.zeytindefteri.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;

import android.app.Activity;
import android.app.Dialog;

import android.graphics.Color;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;

import android.media.AudioManager;
import android.media.ToneGenerator;

import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.view.animation.OvershootInterpolator;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


public class ReactionManager {


    // ============================================================
    // ACTIVITY
    // ============================================================

    private final Activity activity;


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CALLBACK
    // ============================================================

    private final ReactionCallback reactionCallback;


    // ============================================================
    // SES
    // ============================================================

    private ToneGenerator reactionTone;


    // ============================================================
    // RENKLER
    // ============================================================

    private final int COLOR_REACTION_ACTIVE =
            GreenTheme.color(R.color.zd_green);


    private final int COLOR_REACTION_INACTIVE =
            GreenTheme.color(R.color.zd_muted);


    private final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);


    // ============================================================
    // CALLBACK INTERFACE
    // ============================================================

    public interface ReactionCallback {


        void onReactionChanged();
    }


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ReactionManager(
            Activity activity,
            DatabaseHelper databaseHelper,
            ReactionCallback reactionCallback
    ) {


        this.activity =
                activity;


        this.databaseHelper =
                databaseHelper;


        this.reactionCallback =
                reactionCallback;


        // ========================================================
        // SES
        // ========================================================

        try {


            reactionTone =
                    new ToneGenerator(
                            AudioManager.STREAM_SYSTEM,
                            35
                    );


        } catch (Exception e) {


            reactionTone =
                    null;
        }
    }


    // ============================================================
    // TEPKİ BUTONU HAZIRLA
    // ============================================================

    public void setupReactionButton(
            TextView reactionButton,
            long postId,
            String userKey
    ) {


        if (reactionButton == null) {


            return;
        }


        // ========================================================
        // MEVCUT TEPKİ
        // ========================================================

        String currentReaction =
                databaseHelper.getPostReactionByUser(
                        postId,
                        userKey
                );


        // Eski USEFUL kayıtları LIKE kabul edilir.
        if ("USEFUL".equalsIgnoreCase(
                currentReaction
        )) {


            currentReaction =
                    "LIKE";
        }


        // ========================================================
        // TOPLAM
        // ========================================================

        int totalReactionCount =
                databaseHelper.getPostTotalReactionCount(
                        postId
                );


        // ========================================================
        // BUTON YAZISI
        // ========================================================

        reactionButton.setText(
                getReactionButtonText(
                        currentReaction,
                        totalReactionCount
                )
        );


        // ========================================================
        // BUTON STİLİ
        // ========================================================

        styleReactionButton(
                reactionButton,
                currentReaction
        );


        // ========================================================
        // KISA BASMA
        // ========================================================

        reactionButton.setOnClickListener(
                view -> {


                    DatabaseHelper.ReactionResult result =
                            databaseHelper.togglePostReaction(
                                    postId,
                                    userKey,
                                    "LIKE"
                            );


                    if (!result.success) {


                        Toast.makeText(
                                activity,
                                "Tepki kaydedilemedi.",
                                Toast.LENGTH_SHORT
                        ).show();


                        return;
                    }


                    reactionButton.setText(
                            getReactionButtonText(
                                    result.reactionType,
                                    result.totalCount
                            )
                    );


                    styleReactionButton(
                            reactionButton,
                            result.reactionType
                    );


                    animateReaction(
                            reactionButton
                    );


                    if (!"NONE".equals(
                            result.reactionType
                    )) {


                        playReactionSound();
                    }


                    notifyReactionChanged();
                }
        );


        // ========================================================
        // UZUN BASMA
        // ========================================================

        reactionButton.setOnLongClickListener(
                view -> {


                    view.animate()
                            .scaleX(
                                    0.94f
                            )
                            .scaleY(
                                    0.94f
                            )
                            .setDuration(
                                    70
                            )
                            .withEndAction(
                                    () -> view.animate()
                                            .scaleX(
                                                    1f
                                            )
                                            .scaleY(
                                                    1f
                                            )
                                            .setDuration(
                                                    90
                                            )
                                            .start()
                            )
                            .start();


                    showReactionPicker(
                            postId,
                            userKey,
                            reactionButton
                    );


                    return true;
                }
        );
    }


    // ============================================================
    // FACEBOOK BENZERİ TEPKİ MENÜSÜ
    // ============================================================

    private void showReactionPicker(
            long postId,
            String userKey,
            TextView reactionButton
    ) {


        final Dialog dialog =
                new Dialog(
                        activity
                );


        dialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );


        dialog.setCanceledOnTouchOutside(
                true
        );


        // ========================================================
        // ROOT
        // ========================================================

        LinearLayout root =
                new LinearLayout(
                        activity
                );


        root.setOrientation(
                LinearLayout.VERTICAL
        );


        root.setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(6)
        );


        root.setBackground(
                createRoundedBackground(
                        GreenTheme.color(R.color.zd_card),
                        GreenTheme.color(R.color.zd_border),
                        100
                )
        );


        root.setElevation(
                dp(8)
        );


        // ========================================================
        // REACTION ROW
        // ========================================================

        LinearLayout reactionRow =
                new LinearLayout(
                        activity
                );


        reactionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        reactionRow.setGravity(
                Gravity.CENTER
        );


        final String[] reactionTypes = {

                "LIKE",
                "LOVE",
                "CARE",
                "HAHA",
                "WOW",
                "SAD",
                "ANGRY"
        };


        final String[] reactionEmojis = {

                "👍",
                "❤️",
                "🥰",
                "😄",
                "😮",
                "😢",
                "😡"
        };


        // ========================================================
        // EMOJİLER
        // ========================================================

        for (
                int i = 0;
                i < reactionTypes.length;
                i++
        ) {


            final String selectedReaction =
                    reactionTypes[i];


            final String selectedEmoji =
                    reactionEmojis[i];


            final int index =
                    i;


            TextView reactionView =
                    createReactionEmoji(
                            selectedEmoji
                    );


            // ====================================================
            // CLICK
            // ====================================================

            reactionView.setOnClickListener(
                    view -> {


                        DatabaseHelper.ReactionResult result =
                                databaseHelper.togglePostReaction(
                                        postId,
                                        userKey,
                                        selectedReaction
                                );


                        if (!result.success) {


                            Toast.makeText(
                                    activity,
                                    "Tepki kaydedilemedi.",
                                    Toast.LENGTH_SHORT
                            ).show();


                            dialog.dismiss();


                            return;
                        }


                        reactionButton.setText(
                                getReactionButtonText(
                                        result.reactionType,
                                        result.totalCount
                                )
                        );


                        styleReactionButton(
                                reactionButton,
                                result.reactionType
                        );


                        animateReaction(
                                reactionButton
                        );


                        if (!"NONE".equals(
                                result.reactionType
                        )) {


                            playReactionSound();
                        }


                        dialog.dismiss();


                        notifyReactionChanged();
                    }
            );


            // ====================================================
            // EMOJİYE DOKUNMA HAREKETİ
            // ====================================================

            reactionView.setOnTouchListener(
                    (view, event) -> {


                        switch (event.getAction()) {


                            case android.view.MotionEvent.ACTION_DOWN:


                                view.animate()
                                        .scaleX(
                                                1.45f
                                        )
                                        .scaleY(
                                                1.45f
                                        )
                                        .translationY(
                                                -dp(8)
                                        )
                                        .setDuration(
                                                110
                                        )
                                        .setInterpolator(
                                                new OvershootInterpolator()
                                        )
                                        .start();


                                break;


                            case android.view.MotionEvent.ACTION_UP:

                            case android.view.MotionEvent.ACTION_CANCEL:


                                view.animate()
                                        .scaleX(
                                                1f
                                        )
                                        .scaleY(
                                                1f
                                        )
                                        .translationY(
                                                0f
                                        )
                                        .setDuration(
                                                120
                                        )
                                        .start();


                                break;
                        }


                        return false;
                    }
            );


            reactionRow.addView(
                    reactionView
            );


            // ====================================================
            // AÇILIŞ ANİMASYONU
            // ====================================================

            reactionView.post(
                    () -> animateReactionEmojiEntrance(
                            reactionView,
                            index
                                    * 45L
                    )
            );
        }


        root.addView(
                reactionRow
        );


        // ========================================================
        // DIALOG
        // ========================================================

        dialog.setContentView(
                root
        );


        dialog.show();


        Window window =
                dialog.getWindow();


        if (window != null) {


            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.TRANSPARENT
                    )
            );


            window.setDimAmount(
                    0f
            );


            window.setGravity(
                    Gravity.CENTER
            );


            window.setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }


    // ============================================================
    // REACTION EMOJI
    // ============================================================

    private TextView createReactionEmoji(
            String emoji
    ) {


        TextView view =
                new TextView(
                        activity
                );


        view.setText(
                emoji
        );


        view.setTextSize(
                24
        );


        view.setGravity(
                Gravity.CENTER
        );


        view.setClickable(
                true
        );


        view.setFocusable(
                true
        );


        view.setPadding(
                dp(3),
                dp(3),
                dp(3),
                dp(3)
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(40),
                        dp(48)
                );


        view.setLayoutParams(
                params
        );


        return view;
    }


    // ============================================================
    // REACTION BUTTON TEXT
    // ============================================================

    public String getReactionButtonText(
            String reaction,
            int totalCount
    ) {


        String emoji =
                getReactionEmoji(
                        reaction
                );


        String label =
                getReactionLabel(
                        reaction
                );


        if (totalCount > 0) {


            return emoji
                    + " "
                    + totalCount;
        }


        return emoji
                + " "
                + label;
    }


    // ============================================================
    // REACTION EMOJI TEXT
    // ============================================================

    public String getReactionEmoji(
            String reaction
    ) {


        reaction =
                safe(
                        reaction
                )
                        .toUpperCase();


        switch (reaction) {


            case "LOVE":

                return "❤️";


            case "CARE":

                return "🥰";


            case "HAHA":

                return "😄";


            case "WOW":

                return "😮";


            case "SAD":

                return "😢";


            case "ANGRY":

                return "😡";


            case "LIKE":

            case "USEFUL":

                return "👍";


            case "NONE":

            default:

                return "👍";
        }
    }


    // ============================================================
    // REACTION LABEL
    // ============================================================

    public String getReactionLabel(
            String reaction
    ) {


        reaction =
                safe(
                        reaction
                )
                        .toUpperCase();


        switch (reaction) {


            case "LOVE":

                return "Sevdim";


            case "CARE":

                return "Destek";


            case "HAHA":

                return "Güldüm";


            case "WOW":

                return "Vay";


            case "SAD":

                return "Üzüldüm";


            case "ANGRY":

                return "Kızdım";


            case "LIKE":

            case "USEFUL":

                return "Faydalı";


            case "NONE":

            default:

                return "Faydalı";
        }
    }


    // ============================================================
    // REACTION BUTTON STYLE
    // ============================================================

    public void styleReactionButton(TextView button, String reaction) {
        if (button == null) return;
        String type = safe(reaction).toUpperCase(java.util.Locale.ROOT);
        boolean selected = !type.isEmpty() && !"NONE".equals(type);
        button.setBackgroundTintList(null);
        button.setBackground(null);
        button.setSingleLine(true);
        button.setTextSize(13);
        button.setIncludeFontPadding(true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setTextColor(selected ? GreenTheme.color(R.color.zd_green) : COLOR_REACTION_INACTIVE);
        button.setSelected(selected);
        button.setContentDescription(getReactionLabel(type) + ", " + button.getText());
        button.setScaleX(1f);
        button.setScaleY(1f);
    }



    // ============================================================
    // BUTON TEPKİ ANİMASYONU
    // ============================================================

    public void animateReaction(View view) {
        // Keep the compact action row stable; scaling emoji can clip its glyph.
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
    }



    // ============================================================
    // EMOJİ AÇILIŞ ANİMASYONU
    // ============================================================

    private void animateReactionEmojiEntrance(
            View view,
            long delay
    ) {


        view.setScaleX(
                0.45f
        );


        view.setScaleY(
                0.45f
        );


        view.setTranslationY(
                dp(14)
        );


        view.setAlpha(
                0f
        );


        view.animate()
                .alpha(
                        1f
                )
                .scaleX(
                        1f
                )
                .scaleY(
                        1f
                )
                .translationY(
                        0f
                )
                .setStartDelay(
                        delay
                )
                .setDuration(
                        250
                )
                .setInterpolator(
                        new OvershootInterpolator(
                                1.6f
                        )
                )
                .start();
    }


    // ============================================================
    // SES
    // ============================================================

    public void playReactionSound() {


        try {


            if (reactionTone != null) {


                reactionTone.startTone(
                        ToneGenerator.TONE_PROP_ACK,
                        80
                );
            }


        } catch (Exception ignored) {


        }
    }


    // ============================================================
    // CALLBACK
    // ============================================================

    private void notifyReactionChanged() {


        if (reactionCallback != null) {


            reactionCallback.onReactionChanged();
        }
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


    // ============================================================
    // DESTROY
    // ============================================================

    public void release() {


        if (reactionTone != null) {


            try {


                reactionTone.release();


            } catch (Exception ignored) {


            }


            reactionTone =
                    null;
        }
    }
}
