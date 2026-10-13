package com.zeytindefteri.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * Tek merkezden profil fotoğrafı yönetimi.
 *
 * Öncelik:
 * 1) Kullanıcının uygulama içinden seçtiği özel fotoğraf
 * 2) Firebase/Google profil fotoğrafı
 * 3) Baş harf / varsayılan avatar
 *
 * ProfileActivity ile aynı SharedPreferences anahtarlarını kullanır.
 * Böylece profilde seçilen fotoğraf ana akışta da otomatik görünür.
 */
public class ProfilePhotoManager {


    private static final String PROFILE_PREFS =
            "zeytin_profile_prefs";

    private static final String KEY_PHOTO_MODE_PREFIX =
            "photo_mode_";

    private static final String KEY_CUSTOM_PHOTO_URI_PREFIX =
            "custom_photo_uri_";

    private static final String PHOTO_MODE_AUTO =
            "AUTO";

    private static final String PHOTO_MODE_CUSTOM =
            "CUSTOM";

    private static final String PHOTO_MODE_GOOGLE =
            "GOOGLE";

    private static final String PHOTO_MODE_DEFAULT =
            "DEFAULT";


    private static final Map<String, Bitmap> MEMORY_CACHE =
            new HashMap<>();


    private final Activity activity;
    private final FirebaseAuth firebaseAuth;
    private final SessionManager sessionManager;
    private final SharedPreferences preferences;

    private final Locale TR =
            new Locale("tr", "TR");


    public ProfilePhotoManager(
            Activity activity,
            FirebaseAuth firebaseAuth,
            SessionManager sessionManager
    ) {

        this.activity =
                activity;

        this.firebaseAuth =
                firebaseAuth;

        this.sessionManager =
                sessionManager;

        this.preferences =
                activity.getSharedPreferences(
                        PROFILE_PREFS,
                        Activity.MODE_PRIVATE
                );
    }


    // ============================================================
    // PUBLIC AVATAR FACTORY
    // ============================================================

    public View createAvatarView(
            int sizeDp,
            int textSizeSp,
            String postUserId,
            String postUserName,
            int backgroundColor,
            int borderColor,
            int textColor
    ) {

        FrameLayout frame =
                new FrameLayout(activity);


        ImageView image =
                new ImageView(activity);

        image.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        image.setBackground(
                rounded(
                        backgroundColor,
                        999,
                        borderColor,
                        1
                )
        );

        image.setClipToOutline(
                true
        );


        TextView fallback =
                new TextView(activity);

        fallback.setGravity(
                Gravity.CENTER
        );

        fallback.setTextSize(
                textSizeSp
        );

        fallback.setTextColor(
                textColor
        );

        fallback.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        fallback.setBackground(
                rounded(
                        backgroundColor,
                        999,
                        borderColor,
                        1
                )
        );


        int size =
                dp(sizeDp);


        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        size,
                        size,
                        Gravity.CENTER
                );


        frame.addView(
                image,
                params
        );

        frame.addView(
                fallback,
                params
        );


        boolean ownPost =
                isCurrentUser(
                        postUserId,
                        postUserName
                );


        if (ownPost) {

            fallback.setText(
                    getCurrentAvatarLetter()
            );

            bindCurrentUserPhoto(
                    image,
                    fallback
            );

        } else {

            fallback.setText(
                    buildOtherUserFallback(
                            postUserName
                    )
            );

            image.setVisibility(
                    View.INVISIBLE
            );

            fallback.setVisibility(
                    View.VISIBLE
            );
        }


        return frame;
    }


    public void bindCurrentUserPhoto(
            ImageView imageView,
            TextView fallbackView
    ) {

        if (imageView == null
                || fallbackView == null) {

            return;
        }


        fallbackView.setText(
                getCurrentAvatarLetter()
        );

        fallbackView.setVisibility(
                View.VISIBLE
        );

        imageView.setVisibility(
                View.INVISIBLE
        );


        String mode =
                getPhotoMode();


        if (PHOTO_MODE_DEFAULT.equals(mode)) {

            return;
        }


        // 1) Kullanıcının uygulama içinden seçtiği fotoğraf
        if (PHOTO_MODE_CUSTOM.equals(mode)
                || PHOTO_MODE_AUTO.equals(mode)) {

            String customUri =
                    getCustomPhotoUri();


            if (!customUri.isEmpty()) {

                try {

                    imageView.setImageURI(
                            Uri.parse(customUri)
                    );

                    imageView.setVisibility(
                            View.VISIBLE
                    );

                    fallbackView.setVisibility(
                            View.GONE
                    );

                    return;

                } catch (Exception ignored) {
                }
            }
        }


        // 2) Google / Firebase fotoğrafı
        Uri googleUri =
                getGooglePhotoUri();


        if (googleUri == null) {

            return;
        }


        String url =
                googleUri.toString();


        Bitmap cached;


        synchronized (MEMORY_CACHE) {

            cached =
                    MEMORY_CACHE.get(url);
        }


        if (cached != null) {

            showBitmap(
                    imageView,
                    fallbackView,
                    cached
            );

            return;
        }


        loadRemoteBitmap(
                url,
                imageView,
                fallbackView
        );
    }


    public boolean isCurrentUser(
            String postUserId,
            String postUserName
    ) {

        String uid =
                getFirebaseUid();


        String safePostId =
                safe(postUserId);


        if (!uid.isEmpty()
                && uid.equals(safePostId)) {

            return true;
        }


        String currentName =
                safe(
                        getCurrentDisplayName()
                );


        return safePostId.isEmpty()
                &&
                !currentName.isEmpty()
                &&
                currentName.equalsIgnoreCase(
                        safe(postUserName)
                );
    }


    // ============================================================
    // REMOTE IMAGE
    // ============================================================

    private void loadRemoteBitmap(
            String urlString,
            ImageView imageView,
            TextView fallbackView
    ) {

        new Thread(
                () -> {

                    Bitmap bitmap =
                            null;

                    HttpURLConnection connection =
                            null;

                    InputStream inputStream =
                            null;


                    try {

                        URL url =
                                new URL(
                                        urlString
                                );


                        connection =
                                (HttpURLConnection)
                                        url.openConnection();

                        connection.setConnectTimeout(
                                8000
                        );

                        connection.setReadTimeout(
                                8000
                        );

                        connection.setDoInput(
                                true
                        );

                        connection.connect();


                        inputStream =
                                connection.getInputStream();


                        bitmap =
                                BitmapFactory.decodeStream(
                                        inputStream
                                );


                        if (bitmap != null) {

                            synchronized (MEMORY_CACHE) {

                                MEMORY_CACHE.put(
                                        urlString,
                                        bitmap
                                );
                            }
                        }


                    } catch (Exception ignored) {


                    } finally {

                        try {

                            if (inputStream != null) {

                                inputStream.close();
                            }

                        } catch (Exception ignored) {
                        }


                        if (connection != null) {

                            connection.disconnect();
                        }
                    }


                    Bitmap finalBitmap =
                            bitmap;


                    if (finalBitmap == null) {

                        return;
                    }


                    activity.runOnUiThread(
                            () -> {

                                if (activity.isFinishing()
                                        || activity.isDestroyed()) {

                                    return;
                                }


                                showBitmap(
                                        imageView,
                                        fallbackView,
                                        finalBitmap
                                );
                            }
                    );
                }
        ).start();
    }


    private void showBitmap(
            ImageView imageView,
            TextView fallbackView,
            Bitmap bitmap
    ) {

        if (bitmap == null
                || imageView == null
                || fallbackView == null) {

            return;
        }


        imageView.setImageBitmap(
                bitmap
        );

        imageView.setVisibility(
                View.VISIBLE
        );

        fallbackView.setVisibility(
                View.GONE
        );
    }


    // ============================================================
    // USER DATA
    // ============================================================

    private Uri getGooglePhotoUri() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null
                        && user.getPhotoUrl() != null) {

                    return user.getPhotoUrl();
                }
            }

        } catch (Exception ignored) {
        }


        return null;
    }


    private String getFirebaseUid() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null
                        && user.getUid() != null) {

                    return user
                            .getUid()
                            .trim();
                }
            }

        } catch (Exception ignored) {
        }


        return "";
    }


    private String getCurrentDisplayName() {

        try {

            if (firebaseAuth != null) {

                FirebaseUser user =
                        firebaseAuth.getCurrentUser();


                if (user != null
                        && user.getDisplayName() != null
                        && !user.getDisplayName().trim().isEmpty()) {

                    return user
                            .getDisplayName()
                            .trim();
                }
            }

        } catch (Exception ignored) {
        }


        if (sessionManager != null) {

            String name =
                    safe(
                            sessionManager.getDisplayName()
                    );


            if (!name.isEmpty()) {

                return name;
            }


            name =
                    safe(
                            sessionManager.getUserName()
                    );


            if (!name.isEmpty()) {

                return name;
            }
        }


        return "Üretici";
    }


    private String getCurrentAvatarLetter() {

        if (sessionManager != null
                && sessionManager.isGuest()) {

            return "🫒";
        }


        String name =
                safe(
                        getCurrentDisplayName()
                );


        if (name.isEmpty()) {

            return "🫒";
        }


        return name
                .substring(
                        0,
                        1
                )
                .toUpperCase(
                        TR
                );
    }


    private String buildOtherUserFallback(
            String userName
    ) {

        String name =
                safe(userName);


        if (name.isEmpty()) {

            return "👨‍🌾";
        }


        return name
                .substring(
                        0,
                        1
                )
                .toUpperCase(
                        TR
                );
    }


    // ============================================================
    // PROFILE PHOTO PREFS
    // ============================================================

    private String getPhotoMode() {

        return preferences.getString(
                KEY_PHOTO_MODE_PREFIX
                        + getPreferenceUserKey(),
                PHOTO_MODE_AUTO
        );
    }


    private String getCustomPhotoUri() {

        return safe(
                preferences.getString(
                        KEY_CUSTOM_PHOTO_URI_PREFIX
                                + getPreferenceUserKey(),
                        ""
                )
        );
    }


    private String getPreferenceUserKey() {

        String uid =
                getFirebaseUid();


        if (!uid.isEmpty()) {

            return "firebase_"
                    + uid;
        }


        String email =
                "";


        if (sessionManager != null) {

            email =
                    safe(
                            sessionManager.getUserEmail()
                    );
        }


        if (!email.isEmpty()) {

            return "email_"
                    +
                    Math.abs(
                            email
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                                    .hashCode()
                    );
        }


        return "guest";
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private GradientDrawable rounded(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeWidthDp
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                color
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );


        if (strokeWidthDp > 0) {

            drawable.setStroke(
                    dp(strokeWidthDp),
                    strokeColor
            );
        }


        return drawable;
    }


    private int dp(
            int value
    ) {

        return Math.round(
                value
                        *
                        activity
                                .getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }
}