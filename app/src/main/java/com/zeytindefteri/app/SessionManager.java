package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    public static final String SESSION_NONE = "NONE";
    public static final String SESSION_GUEST = "GUEST";
    public static final String SESSION_USER = "USER";

    private static final String PREF_NAME = "zeytin_session";
    private static final String KEY_SESSION_TYPE = "session_type";
    private static final String KEY_USER_UID = "user_uid";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_LOGIN_TIME = "login_time";

    private final SharedPreferences preferences;
    private final Context appContext;

    public SessionManager(Context context) {
        appContext = context.getApplicationContext();
        preferences = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public void startGuestSession() {
        synchronized (LocalReminderScheduler.LOCK) {
            LocalReminderScheduler.beforeSessionChange(appContext);

        PushRegistration.beforeSignOut(appContext);
        preferences.edit()
                .putString(KEY_SESSION_TYPE, SESSION_GUEST)
                .putString(KEY_USER_UID, "")
                .putString(KEY_USER_NAME, "")
                .putString(KEY_USER_EMAIL, "")
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    
        }
        LocalReminderScheduler.requestReschedule(appContext);
    }

    public void startUserSession(
            String name,
            String email
    ) {
        startUserSession(
                name,
                email,
                getUserUid()
        );
    }

    public void startUserSession(
            String name,
            String email,
            String uid
    ) {
        synchronized (LocalReminderScheduler.LOCK) {
            LocalReminderScheduler.beforeSessionChange(appContext);

        preferences.edit()
                .putString(KEY_SESSION_TYPE, SESSION_USER)
                .putString(KEY_USER_UID, safe(uid))
                .putString(KEY_USER_NAME, safe(name))
                .putString(KEY_USER_EMAIL, safe(email))
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    
        }
        LocalReminderScheduler.requestReschedule(appContext);
    }

    public String getSessionType() {
        return preferences.getString(
                KEY_SESSION_TYPE,
                SESSION_NONE
        );
    }

    public boolean hasSession() {
        String type = getSessionType();
        return SESSION_GUEST.equals(type)
                || SESSION_USER.equals(type);
    }

    public boolean isGuest() {
        return SESSION_GUEST.equals(
                getSessionType()
        );
    }

    public boolean isLoggedInUser() {
        return SESSION_USER.equals(
                getSessionType()
        );
    }

    public String getUserUid() {
        return preferences.getString(
                KEY_USER_UID,
                ""
        );
    }

    public String getUserName() {
        return preferences.getString(
                KEY_USER_NAME,
                ""
        );
    }

    public String getUserEmail() {
        return preferences.getString(
                KEY_USER_EMAIL,
                ""
        );
    }

    public long getLoginTime() {
        return preferences.getLong(
                KEY_LOGIN_TIME,
                0L
        );
    }

    public String getDisplayName() {
        if (isGuest()) {
            return "Misafir Üretici";
        }

        String name = getUserName();
        if (name == null || name.trim().isEmpty()) {
            return "Zeytin Üreticisi";
        }

        return name.trim();
    }

    public void logout() {
        synchronized (LocalReminderScheduler.LOCK) {
            LocalReminderScheduler.beforeSessionChange(appContext);

        PushRegistration.beforeSignOut(appContext);
        preferences.edit()
                .clear()
                .apply();
    
        }
        LocalReminderScheduler.requestReschedule(appContext);
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}