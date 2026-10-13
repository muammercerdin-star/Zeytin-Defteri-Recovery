package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import android.database.Cursor;

import android.graphics.Color;
import android.graphics.Typeface;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;

import android.text.InputType;
import android.text.TextUtils;

import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;


/**
 * ================================================================
 * COMMENT MANAGER
 * ================================================================
 *
 * Meydan gönderilerinin yanıt / sohbet bölümünü yönetir.
 *
 * Mevcut DatabaseHelper altyapısını değiştirmez.
 *
 * Kullanılan mevcut metotlar:
 *
 * - addPostComment()
 * - getPostComments()
 * - deletePostComment()
 *
 *
 * Bu sınıfın görevleri:
 *
 * - Bir Meydan gönderisinin yanıtlarını açmak
 * - Yanıtları listelemek
 * - Yeni yanıt göndermek
 * - Kullanıcının kendi yanıtını silmek
 * - Yanıt sayısını ekranda göstermek
 * - Yanıt değiştiğinde ana Meydan akışını yenilemek
 *
 * ================================================================
 */
public class CommentManager {


    // ============================================================
    // ACTIVITY
    // ============================================================

    private final Activity activity;


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CURRENT USER
    // ============================================================

    private final UserProvider userProvider;


    // ============================================================
    // CALLBACK
    // ============================================================

    private final OnCommentsChangedListener commentsChangedListener;


    // ============================================================
    // PROFILE PHOTO
    // ============================================================

    private final ProfilePhotoManager profilePhotoManager;


    // ============================================================
    // CURRENT DIALOG
    // ============================================================

    private Dialog activeDialog;


    // ============================================================
    // RELEASE STATE
    // ============================================================

    private boolean released = false;


    // ============================================================
    // COLORS
    // ============================================================

    private static final int COLOR_BG =
            GreenTheme.color(R.color.zd_bg);


    private static final int COLOR_SURFACE =
            GreenTheme.color(R.color.zd_card);


    private static final int COLOR_SURFACE_SOFT =
            GreenTheme.color(R.color.zd_surface);


    private static final int COLOR_INPUT =
            GreenTheme.color(R.color.zd_input);


    private static final int COLOR_GREEN =
            GreenTheme.color(R.color.zd_green);


    private static final int COLOR_GREEN_SOFT =
            GreenTheme.color(R.color.zd_green_soft);


    private static final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);


    private static final int COLOR_TEXT_STRONG =
            GreenTheme.color(R.color.zd_text);


    private static final int COLOR_MUTED =
            GreenTheme.color(R.color.zd_muted);


    private static final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);


    private static final int COLOR_DELETE =
            GreenTheme.color(R.color.zd_danger);


    // ============================================================
    // LIMITS
    // ============================================================

    private static final int MAX_REPLY_LENGTH = 500;


    // ============================================================
    // USER PROVIDER
    // ============================================================

    public interface UserProvider {

        String getUserKey();

        String getUserName();
    }


    // ============================================================
    // COMMENTS CHANGED
    // ============================================================

    public interface OnCommentsChangedListener {

        void onCommentsChanged();
    }


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public CommentManager(
            Activity activity,
            DatabaseHelper databaseHelper,
            UserProvider userProvider,
            OnCommentsChangedListener commentsChangedListener
    ) {

        if (activity == null) {

            throw new IllegalArgumentException(
                    "Activity null olamaz."
            );
        }


        if (databaseHelper == null) {

            throw new IllegalArgumentException(
                    "DatabaseHelper null olamaz."
            );
        }


        this.activity =
                activity;


        this.databaseHelper =
                databaseHelper;


        this.userProvider =
                userProvider;


        this.commentsChangedListener =
                commentsChangedListener;


        FirebaseAuth auth = null;


        try {

            auth =
                    FirebaseAuth.getInstance();

        } catch (Exception ignored) {
        }


        this.profilePhotoManager =
                new ProfilePhotoManager(
                        activity,
                        auth,
                        new SessionManager(activity)
                );
    }


    // ============================================================
    // YANITLARI AÇ
    // ============================================================

    public void showComments(long postId) {
        if (released || postId <= 0) return;
        android.content.Intent intent = new android.content.Intent(activity, PostRepliesActivity.class);
        intent.putExtra("post_id", postId);
        activity.startActivity(intent);
    }



    // ============================================================
    // COMMENTS LOAD
    // ============================================================

    private int loadComments(
            long postId,
            LinearLayout container
    ) {

        if (container == null) {

            return 0;
        }


        container.removeAllViews();


        Cursor cursor = null;


        int count = 0;


        try {

            cursor =
                    databaseHelper.getPostComments(
                            postId
                    );


            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                long commentId =
                        getLongSafe(
                                cursor,
                                "id",
                                0
                        );


                String userKey =
                        getStringSafe(
                                cursor,
                                "user_key",
                                ""
                        );


                String userName =
                        getStringSafe(
                                cursor,
                                "user_name",
                                "Üretici"
                        );


                String content =
                        getStringSafe(
                                cursor,
                                "content",
                                ""
                        );


                long createdAt =
                        getLongSafe(
                                cursor,
                                "created_at",
                                0
                        );


                View commentView =
                        createCommentView(
                                postId,
                                commentId,
                                userKey,
                                userName,
                                content,
                                createdAt,
                                container
                        );


                container.addView(
                        commentView
                );


                count++;
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            closeCursor(cursor);
        }


        // ========================================================
        // EMPTY
        // ========================================================

        if (count == 0) {

            LinearLayout emptyBox =
                    new LinearLayout(activity);


            emptyBox.setOrientation(
                    LinearLayout.VERTICAL
            );


            emptyBox.setGravity(
                    Gravity.CENTER
            );


            emptyBox.setPadding(
                    dp(16),
                    dp(44),
                    dp(16),
                    dp(44)
            );


            TextView icon =
                    createText(
                            "💬",
                            28,
                            COLOR_GREEN,
                            false
                    );


            icon.setGravity(
                    Gravity.CENTER
            );


            emptyBox.addView(
                    icon
            );


            TextView emptyTitle =
                    createText(
                            "Henüz yanıt yok",
                            15,
                            COLOR_TEXT_STRONG,
                            true
                    );


            emptyTitle.setGravity(
                    Gravity.CENTER
            );


            emptyTitle.setPadding(
                    0,
                    dp(8),
                    0,
                    0
            );


            emptyBox.addView(
                    emptyTitle
            );


            TextView empty =
                    createText(
                            "Bu üreticiye ilk yanıtı sen verebilirsin.",
                            12,
                            COLOR_MUTED,
                            false
                    );


            empty.setGravity(
                    Gravity.CENTER
            );


            empty.setPadding(
                    dp(10),
                    dp(5),
                    dp(10),
                    0
            );


            emptyBox.addView(
                    empty
            );


            container.addView(
                    emptyBox
            );
        }


        return count;
    }


    // ============================================================
    // COMMENT VIEW
    // ============================================================

    private View createCommentView(
            long postId,
            long commentId,
            String ownerUserKey,
            String userName,
            String content,
            long createdAt,
            LinearLayout commentContainer
    ) {

        LinearLayout outer =
                new LinearLayout(activity);


        outer.setOrientation(
                LinearLayout.HORIZONTAL
        );


        outer.setGravity(
                Gravity.TOP
        );


        LinearLayout.LayoutParams outerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        outerParams.bottomMargin =
                dp(10);


        outer.setLayoutParams(
                outerParams
        );


        // ========================================================
        // AVATAR
        // ========================================================

        View avatarView =
                profilePhotoManager.createAvatarView(
                        38,
                        12,
                        normalizeCommentUserIdForPhoto(
                                ownerUserKey
                        ),
                        userName,
                        COLOR_GREEN_SOFT,
                        COLOR_BORDER,
                        COLOR_TEXT_STRONG
                );


        outer.addView(
                avatarView,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                )
        );


        // ========================================================
        // MESSAGE AREA
        // ========================================================

        LinearLayout messageArea =
                new LinearLayout(activity);


        messageArea.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams messageAreaParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );


        messageAreaParams.leftMargin =
                dp(9);


        outer.addView(
                messageArea,
                messageAreaParams
        );


        // ========================================================
        // BUBBLE
        // ========================================================

        LinearLayout bubble =
                new LinearLayout(activity);


        bubble.setOrientation(
                LinearLayout.VERTICAL
        );


        bubble.setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
        );


        bubble.setBackground(
                createRoundedBackground(
                        COLOR_SURFACE_SOFT,
                        COLOR_BORDER,
                        16
                )
        );


        LinearLayout top =
                new LinearLayout(activity);


        top.setOrientation(
                LinearLayout.HORIZONTAL
        );


        top.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView nameView =
                createText(
                        safeOr(
                                userName,
                                "Üretici"
                        ),
                        12,
                        COLOR_TEXT_STRONG,
                        true
                );


        top.addView(
                nameView,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        TextView miniTime =
                createText(
                        formatTime(
                                createdAt
                        ),
                        9,
                        COLOR_MUTED,
                        false
                );


        top.addView(
                miniTime
        );


        bubble.addView(
                top
        );


        // ========================================================
        // CONTENT
        // ========================================================

        TextView commentView =
                createText(
                        safe(content),
                        13,
                        COLOR_TEXT,
                        false
                );


        commentView.setLineSpacing(
                dp(1),
                1.08f
        );


        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        contentParams.topMargin =
                dp(5);


        bubble.addView(
                commentView,
                contentParams
        );


        messageArea.addView(
                bubble
        );


        // ========================================================
        // META / DELETE
        // ========================================================

        String currentUserKey =
                getCurrentUserKey();


        if (
                !safe(ownerUserKey).isEmpty()
                        &&
                        ownerUserKey.equals(
                                currentUserKey
                        )
        ) {

            TextView deleteButton =
                    createText(
                            "Yanıtı sil",
                            10,
                            COLOR_DELETE,
                            true
                    );


            deleteButton.setGravity(
                    Gravity.RIGHT
            );


            deleteButton.setPadding(
                    dp(4),
                    dp(5),
                    dp(4),
                    dp(3)
            );


            deleteButton.setClickable(
                    true
            );


            deleteButton.setFocusable(
                    true
            );


            deleteButton.setOnClickListener(
                    view ->
                            confirmDeleteComment(
                                    postId,
                                    commentId,
                                    currentUserKey,
                                    commentContainer
                            )
            );


            messageArea.addView(
                    deleteButton,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
        }


        return outer;
    }


    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    private void confirmDeleteComment(
            long postId,
            long commentId,
            String currentUserKey,
            LinearLayout commentContainer
    ) {

        if (released) {

            return;
        }


        final AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Yanıt silinsin mi?")
                        .setMessage(
                                "Bu yanıt Meydan'dan kaldırılacak."
                        )
                        .setPositiveButton(
                                "Sil",
                                (d, which) -> {

                                    boolean deleted;


                                    try {

                                        deleted =
                                                databaseHelper
                                                        .deletePostComment(
                                                                commentId,
                                                                currentUserKey
                                                        );

                                    } catch (Exception e) {

                                        e.printStackTrace();

                                        deleted =
                                                false;
                                    }


                                    if (!deleted) {

                                        Toast.makeText(
                                                activity,
                                                "Yanıt silinemedi.",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        return;
                                    }


                                    loadComments(
                                            postId,
                                            commentContainer
                                    );


                                    notifyCommentsChanged();
                                }
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                ignored -> {

                    Window window =
                            dialog.getWindow();


                    if (window != null) {

                        window.setBackgroundDrawable(
                                createRoundedBackground(
                                        COLOR_SURFACE,
                                        COLOR_BORDER,
                                        20
                                )
                        );
                    }


                    TextView positive =
                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            );


                    if (positive != null) {

                        positive.setTextColor(
                                COLOR_DELETE
                        );
                    }


                    TextView negative =
                            dialog.getButton(
                                    AlertDialog.BUTTON_NEGATIVE
                            );


                    if (negative != null) {

                        negative.setTextColor(
                                COLOR_GREEN
                        );
                    }
                }
        );


        dialog.show();
    }


    // ============================================================
    // PUBLIC ADD COMMENT
    // ============================================================

    public long addComment(
            long postId,
            String content
    ) {

        if (released) {

            return -1;
        }


        if (postId <= 0) {

            return -1;
        }


        content =
                safe(content);


        if (content.isEmpty()) {

            return -1;
        }


        if (content.length() > MAX_REPLY_LENGTH) {

            return -1;
        }


        long result;


        try {

            result =
                    databaseHelper.addPostComment(
                            postId,
                            getCurrentUserKey(),
                            getCurrentUserName(),
                            content
                    );

        } catch (Exception e) {

            e.printStackTrace();

            result =
                    -1;
        }


        if (result != -1) {

            notifyCommentsChanged();
        }


        return result;
    }


    // ============================================================
    // DELETE COMMENT
    // ============================================================

    public boolean deleteComment(
            long commentId
    ) {

        if (released) {

            return false;
        }


        if (commentId <= 0) {

            return false;
        }


        boolean result;


        try {

            result =
                    databaseHelper.deletePostComment(
                            commentId,
                            getCurrentUserKey()
                    );

        } catch (Exception e) {

            e.printStackTrace();

            result =
                    false;
        }


        if (result) {

            notifyCommentsChanged();
        }


        return result;
    }


    // ============================================================
    // GET COMMENTS
    // ============================================================

    public Cursor getComments(
            long postId
    ) {

        if (released) {

            return null;
        }


        if (postId <= 0) {

            return null;
        }


        return databaseHelper.getPostComments(
                postId
        );
    }


    // ============================================================
    // GET COMMENT COUNT
    // ============================================================

    public int getCommentCount(
            long postId
    ) {

        if (released || postId <= 0) {

            return 0;
        }


        Cursor cursor = null;


        int count = 0;


        try {

            cursor =
                    databaseHelper.getPostComments(
                            postId
                    );


            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                count++;
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            closeCursor(cursor);
        }


        return count;
    }


    // ============================================================
    // COUNT TEXT
    // ============================================================

    private void updateCountText(
            TextView view,
            int count
    ) {

        if (view == null) {

            return;
        }


        if (count <= 0) {

            view.setText(
                    "Henüz yanıt yok"
            );

        } else if (count == 1) {

            view.setText(
                    "1 yanıt"
            );

        } else {

            view.setText(
                    count
                            + " yanıt"
            );
        }
    }


    // ============================================================
    // CALLBACK
    // ============================================================

    private void notifyCommentsChanged() {

        if (released) {

            return;
        }


        if (commentsChangedListener != null) {

            commentsChangedListener
                    .onCommentsChanged();
        }
    }


    // ============================================================
    // USER KEY
    // ============================================================

    private String getCurrentUserKey() {

        if (userProvider == null) {

            return "local_user";
        }


        String result;


        try {

            result =
                    safe(
                            userProvider
                                    .getUserKey()
                    );

        } catch (Exception ignored) {

            result =
                    "";
        }


        if (result.isEmpty()) {

            return "local_user";
        }


        return result;
    }


    // ============================================================
    // USER NAME
    // ============================================================

    private String getCurrentUserName() {

        if (userProvider == null) {

            return "Üretici";
        }


        String result;


        try {

            result =
                    safe(
                            userProvider
                                    .getUserName()
                    );

        } catch (Exception ignored) {

            result =
                    "";
        }


        if (result.isEmpty()) {

            return "Üretici";
        }


        return result;
    }


    // ============================================================
    // COMMENT USER -> PROFILE PHOTO ID
    // ============================================================

    private String normalizeCommentUserIdForPhoto(
            String ownerUserKey
    ) {

        String key =
                safe(ownerUserKey);


        if (
                key.startsWith(
                        "firebase:"
                )
        ) {

            return key.substring(
                    "firebase:".length()
            );
        }


        if (
                !key.isEmpty()
                        &&
                        key.equals(
                                getCurrentUserKey()
                        )
        ) {

            return "";
        }


        return key;
    }


    // ============================================================
    // FORMAT TIME
    // ============================================================

    private String formatTime(
            long createdAt
    ) {

        if (createdAt <= 0) {

            return "şimdi";
        }


        long difference =
                System.currentTimeMillis()
                        - createdAt;


        if (difference < 0) {

            difference =
                    0;
        }


        long minute =
                TimeUnit.MINUTES.toMillis(1);


        long hour =
                TimeUnit.HOURS.toMillis(1);


        long day =
                TimeUnit.DAYS.toMillis(1);


        if (difference < minute) {

            return "şimdi";
        }


        if (difference < hour) {

            return (
                    difference
                            / minute
            )
                    + " dk önce";
        }


        if (difference < day) {

            return (
                    difference
                            / hour
            )
                    + " sa önce";
        }


        if (difference < 2L * day) {

            return "dün";
        }


        if (difference < 7L * day) {

            return (
                    difference
                            / day
            )
                    + " gün önce";
        }


        return "daha önce";
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
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }


        String result =
                cursor.getString(
                        index
                );


        return result == null
                ? defaultValue
                : result;
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
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }


        return cursor.getLong(
                index
        );
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
                new TextView(activity);


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
                    Typeface.DEFAULT,
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
                dp(radiusDp)
        );


        if (strokeColor != Color.TRANSPARENT) {

            drawable.setStroke(
                    dp(1),
                    strokeColor
            );
        }


        return drawable;
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
                value * density
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
                safe(value);


        return result.isEmpty()
                ? defaultValue
                : result;
    }


    // ============================================================
    // CURSOR CLOSE
    // ============================================================

    private void closeCursor(
            Cursor cursor
    ) {

        if (cursor == null) {

            return;
        }


        try {

            cursor.close();

        } catch (Exception ignored) {
        }
    }


    // ============================================================
    // ACTIVE DIALOG CLOSE
    // ============================================================

    private void dismissActiveDialog() {

        if (activeDialog == null) {

            return;
        }


        try {

            if (activeDialog.isShowing()) {

                activeDialog.dismiss();
            }

        } catch (Exception ignored) {
        }


        activeDialog =
                null;
    }


    // ============================================================
    // RELEASE
    // ============================================================

    public void release() {

        released =
                true;


        dismissActiveDialog();
    }
}