package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import java.util.Map;

/** Visibility only. The existing backend independently verifies the live moderator role. */
final class ModerationAccess {
    static boolean allowed(Map<String, Object> claims) {
        return claims != null && Boolean.TRUE.equals(claims.get("moderation_admin"));
    }
    /** One immutable UI snapshot; backend permissions remain independently enforced. */
    static final class Access {
        final boolean moderator, superAdmin;
        Access(Map<String, Object> claims) {
            moderator = allowed(claims);
            superAdmin = ModeratorManagementState.superAllowed(claims);
        }
        boolean same(Access other) {
            return other != null && moderator == other.moderator && superAdmin == other.superAdmin;
        }
        boolean hasManagement() { return moderator || superAdmin; }
    }

    /** Build only after the complete role snapshot is known. No asynchronous row mutations. */
    static View settingsSection(Activity activity, Access access) {
        if (!access.hasManagement()) return null;
        java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        if (access.moderator) rows.add(SettingsView.row(activity, R.drawable.ic_settings_shield,
                "Moderasyon Merkezi", "Topluluk kuralları ve içerik yönetimi", null,
                () -> activity.startActivity(new Intent(activity, ModerationActivity.class))));
        if (access.superAdmin) {
            if (!rows.isEmpty()) rows.add(SettingsView.separator(activity));
            rows.add(SettingsView.row(activity, R.drawable.ic_settings_users,
                    "Yetki Yönetimi", "Ekip arkadaşlarınızın yetkilerini yönetin", null,
                    () -> activity.startActivity(new Intent(activity, ModeratorManagementActivity.class))));
        }
        return SettingsView.section(activity, "YÖNETİM", rows.toArray(new View[0]));
    }
}
