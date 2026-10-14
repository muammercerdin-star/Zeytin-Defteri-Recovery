package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import com.bumptech.glide.Glide;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Presentation only: all actions use the existing screens and season/assistant callbacks. */
final class SettingsView extends ScrollView {
    // Uygulama paleti (önceden ekranın kendi tonları vardı: 0A202B / 102B38 / 203D48 / nane yeşili)
    private static final int BG = GreenTheme.color(R.color.zd_bg);
    private static final int CARD = GreenTheme.color(R.color.zd_card);
    private static final int BORDER = GreenTheme.color(R.color.zd_border);
    private static final int TEXT = GreenTheme.color(R.color.zd_text);
    private static final int MUTED = GreenTheme.color(R.color.zd_muted);
    private static final int ACCENT = GreenTheme.color(R.color.zd_green);
    private static final int OLIVE = GreenTheme.color(R.color.zd_green);
    private static final int DANGER = GreenTheme.color(R.color.zd_danger);

    SettingsView(Activity activity, View avatar, String name, String phone, String email,
                 String region, int season, Runnable seasonPicker, Runnable assistant, ModerationAccess.Access access) {
        super(activity);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setBackgroundColor(BG);
        LinearLayout body = column(activity);
        body.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 24));
        addView(body, new ScrollView.LayoutParams(-1, -2));

        FrameLayout masthead = new FrameLayout(activity);
        ImageView olive = new ImageView(activity);
        olive.setScaleType(ImageView.ScaleType.CENTER_CROP);
        olive.setScaleX(-1f);
        olive.setAlpha(0.18f);
        olive.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        masthead.addView(olive, new FrameLayout.LayoutParams(dp(activity, 156), dp(activity, 112), Gravity.RIGHT | Gravity.TOP));
        Glide.with(activity).load(R.drawable.home_hero_olive).centerCrop().into(olive);
        View fade = new View(activity);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BG, (BG & 0x00FFFFFF) | 0x99000000, BG & 0x00FFFFFF}));
        masthead.addView(fade, new FrameLayout.LayoutParams(dp(activity, 156), dp(activity, 112), Gravity.RIGHT | Gravity.TOP));
        LinearLayout heading = column(activity);
        masthead.addView(heading, new FrameLayout.LayoutParams(-1, -2));
        body.addView(masthead);
        LinearLayout brand = new LinearLayout(activity);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_olive_logo);
        logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));
        LinearLayout brandText = column(activity);
        brandText.setPadding(dp(activity, 10), 0, 0, 0);
        brandText.addView(label(activity, "Zeytin Defteri", 21, TEXT, true));
        brandText.addView(label(activity, "Üretenin, paylaşanın, yanındayız.", 11, MUTED, false));
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(brand);

        TextView title = label(activity, "Ayarlar", 27, TEXT, true);
        title.setPadding(0, dp(activity, 14), 0, dp(activity, 2));
        heading.addView(title);
        TextView subtitle = label(activity, "Hesabınızı ve uygulama tercihlerinizi yönetin.", 13, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(activity, 12));
        heading.addView(subtitle);
        body.addView(profile(activity, avatar, name, phone, email, region));

        addSection(body, activity, "HESAP",
                row(activity, R.drawable.ic_settings_person, "Profili Yönet", "Kişisel bilgilerinizi düzenleyin", null,
                        () -> open(activity, ProfileActivity.class)),
                row(activity, R.drawable.ic_settings_users, "Engellenen Kullanıcılar", "Engellediğiniz kullanıcıları yönetin", null,
                        () -> open(activity, BlockedUsersActivity.class)));
        addSection(body, activity, "ÜRETİM",
                row(activity, R.drawable.ic_archive_calendar, "Aktif Sezon", "Üretim sezonunuzu seçin", String.valueOf(season), seasonPicker),
                row(activity, R.drawable.ic_bell_line, "Bildirimler ve Hatırlatmalar", "Üretim dönemine özel hatırlatmalar", null,
                        () -> open(activity, RemindersActivity.class)),
                contributionRow(activity));
        addSection(body, activity, "UYGULAMA",
                row(activity, R.drawable.ic_assistant_nav_archive, "Sesli Asistan", "Asistanınız her an yanınızda", null, assistant));
        View management = ModerationAccess.settingsSection(activity, access);
        if (management != null) body.addView(management);
        addSection(body, activity, "DİĞER",
                row(activity, R.drawable.ic_settings_document, "Gizlilik Politikası", "Kişisel verilerinizin kullanımı", null,
                        () -> open(activity, PrivacyActivity.class)),
                row(activity, R.drawable.ic_settings_document, "KVKK Aydınlatma Metni", "Kişisel verilerin nasıl işlendiği", null,
                        () -> LegalDocumentActivity.open(activity, LegalDocuments.KVKK)),
                row(activity, R.drawable.ic_archive_document, "Kullanım Koşulları", "Uygulamayı kullanma şartları", null,
                        () -> open(activity, TermsActivity.class)),
                row(activity, R.drawable.ic_settings_document, "Hesap ve Veri Silme Talebi", "Hesabının ve verilerinin silinmesini iste", null,
                        () -> LegalDocumentActivity.requestDeletion(activity)),
                row(activity, R.drawable.ic_settings_logout, "Çıkış Yap", "Hesabınızdan güvenli şekilde çıkış yapın", null,
                        () -> activity.startActivity(new Intent(activity, ProfileActivity.class)
                                .putExtra(ProfileActivity.EXTRA_CONFIRM_LOGOUT, true))));
        // Only the Management → Other gap is reduced, by 3 dp.
        if (management != null) {
            LinearLayout other = (LinearLayout) body.getChildAt(body.getChildCount() - 1);
            View headingView = other.getChildAt(0);
            headingView.setPadding(headingView.getPaddingLeft(), dp(activity, 7),
                    headingView.getPaddingRight(), headingView.getPaddingBottom());
        }
        String version = "";
        try {
            String current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            if (current != null && !current.trim().isEmpty()) version = " v" + current;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        TextView footer = label(activity, "Zeytin Defteri" + version + "\nDoğadan ilhamla, geleceğe 🌿", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(activity, 10), 0, 0);
        body.addView(footer);
    }

    /** Piyasaya anonim katkı izni; değer pencere kapanınca yerinde güncellenir. */
    private static View contributionRow(Activity activity) {
        final TextView[] value = new TextView[1];
        View row = row(activity, R.drawable.ic_settings_shield, "Piyasaya Anonim Katkı",
                "Elek kayıtların adın olmadan bölge istatistiklerine katılır",
                MarketContributionPrompt.stateLabel(activity),
                () -> MarketContributionPrompt.show(activity, () -> {
                    if (value[0] != null) value[0].setText(MarketContributionPrompt.stateLabel(activity));
                }));
        LinearLayout line = (LinearLayout) row;
        value[0] = (TextView) line.getChildAt(2);
        return row;
    }

    private View profile(Activity activity, View avatar, String name, String phone, String email, String region) {
        LinearLayout profile = new LinearLayout(activity);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 6), dp(activity, 12));
        profile.setBackground(background(activity, CARD));
        avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)));
        LinearLayout info = column(activity);
        info.setPadding(dp(activity, 12), 0, dp(activity, 2), 0);
        TextView userName = label(activity, name, 16, TEXT, true);
        userName.setMaxLines(2);
        userName.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(userName);
        detail(info, activity, R.drawable.ic_settings_phone, phone);
        detail(info, activity, R.drawable.ic_settings_mail, email);
        detail(info, activity, R.drawable.ic_settings_location, region);
        profile.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView edit = new ImageView(activity);
        edit.setImageResource(R.drawable.ic_settings_edit);
        edit.setImageTintList(ColorStateList.valueOf(ACCENT));
        edit.setPadding(dp(activity, 13), dp(activity, 13), dp(activity, 13), dp(activity, 13));
        GradientDrawable editDisc = new GradientDrawable();
        editDisc.setShape(GradientDrawable.OVAL);
        editDisc.setColor(GreenTheme.color(R.color.zd_green_soft));
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(editDisc, dp(activity, 7));
        edit.setBackground(new RippleDrawable(ColorStateList.valueOf((ACCENT & 0x00FFFFFF) | 0x33000000), inset, inset));
        edit.setContentDescription("Profili düzenle");
        edit.setOnClickListener(v -> open(activity, ProfileActivity.class));
        edit.setFocusable(true);
        profile.addView(edit, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        return profile;
    }

    private static void detail(LinearLayout info, Activity a, int icon, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout line = new LinearLayout(a);
        line.setGravity(Gravity.TOP);
        line.setPadding(0, dp(a, 3), 0, 0);
        ImageView glyph = new ImageView(a);
        glyph.setImageResource(icon);
        glyph.setImageTintList(ColorStateList.valueOf(OLIVE));
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(a, 13), dp(a, 13));
        glyphParams.topMargin = dp(a, 2);
        line.addView(glyph, glyphParams);
        TextView text = label(a, value.trim(), 12, GreenTheme.color(R.color.zd_text_soft), false);
        text.setPadding(dp(a, 5), 0, 0, 0);
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(line);
    }

    static View row(Activity a, int icon, String title, String description, String value, Runnable action) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(a, description == null ? 48 : 56));
        row.setPadding(dp(a, 14), dp(a, 8), dp(a, 10), dp(a, 8));
        row.setBackground(ripple(a));
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        ImageView glyph = new ImageView(a);
        glyph.setImageResource(icon);
        // Accounts/application: mint; production/management: olive; sign out: muted terracotta.
        int iconColor = icon == R.drawable.ic_settings_logout ? DANGER
                : icon == R.drawable.ic_archive_calendar || icon == R.drawable.ic_bell_line
                || icon == R.drawable.ic_settings_shield || "Yetki Yönetimi".equals(title) ? OLIVE : ACCENT;
        glyph.setImageTintList(ColorStateList.valueOf(iconColor));
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22)));
        LinearLayout copy = column(a);
        copy.setPadding(dp(a, 12), 0, dp(a, 8), 0);
        copy.addView(label(a, title, 14, icon == R.drawable.ic_settings_logout ? DANGER : TEXT, false));
        if (description != null) {
            TextView sub = label(a, description, 11, MUTED, false);
            sub.setPadding(0, dp(a, 2), 0, 0);
            copy.addView(sub);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null) row.addView(label(a, value, 14, OLIVE, true));
        TextView chevron = label(a, "›", 23, MUTED, false);
        chevron.setGravity(Gravity.CENTER);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(a, 24), -2));
        return row;
    }

    static LinearLayout section(Activity a, String title, View... children) {
        LinearLayout section = column(a);
        TextView heading = label(a, title, 11, ACCENT, true);
        heading.setLetterSpacing(0.04f);
        heading.setPadding(dp(a, 8), dp(a, 10), 0, dp(a, 5));
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        section.addView(heading);
        LinearLayout card = column(a);
        card.setBackground(background(a, CARD));
        card.setClipToOutline(true);
        for (View child : children) card.addView(child);
        section.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return section;
    }

    static View separator(Activity a) {
        View line = new View(a);
        line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(a, 1));
        params.setMargins(dp(a, 48), 0, dp(a, 14), 0);
        line.setLayoutParams(params);
        line.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return line;
    }

    private static void addSection(LinearLayout body, Activity a, String title, View... rows) {
        View[] children = new View[rows.length * 2 - 1];
        for (int i = 0; i < rows.length; i++) {
            children[i * 2] = rows[i];
            if (i > 0) children[i * 2 - 1] = separator(a);
        }
        body.addView(section(a, title, children));
    }

    private static void open(Activity a, Class<? extends Activity> screen) {
        a.startActivity(new Intent(a, screen));
    }
    private static LinearLayout column(Activity a) {
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }
    private static TextView label(Activity a, String value, int size, int color, boolean bold) {
        TextView text = new TextView(a);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setFontFeatureSettings("kern");
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }
    private static GradientDrawable background(Activity a, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(a, 12));
        shape.setStroke(dp(a, 1), BORDER);
        return shape;
    }
    private static RippleDrawable ripple(Activity a) {
        return new RippleDrawable(ColorStateList.valueOf((ACCENT & 0x00FFFFFF) | 0x22000000), null, background(a, 0xFFFFFFFF));
    }
    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
