package com.zeytindefteri.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Resolves both role flags together, then installs one fully built SettingsView before drawing. */
final class SettingsContentHost extends FrameLayout {
    interface ContentFactory { SettingsView create(ModerationAccess.Access access); }
    interface Fingerprint { String read(); }
    private final Activity activity;
    private final ContentFactory factory;
    private final Fingerprint fingerprint;
    private final SettingsRenderState requests = new SettingsRenderState();
    private final FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;
    private SettingsView content;
    private ModerationAccess.Access access;
    private String owner = "", renderedFingerprint;
    private int restoreScroll = -1, savedScroll;
    private boolean resumed = true;

    SettingsContentHost(Activity activity, Fingerprint fingerprint, ContentFactory factory) {
        super(activity);
        this.activity = activity;
        this.fingerprint = fingerprint;
        this.factory = factory;
        auth = FirebaseAuth.getInstance();
        setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        gate(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (resumed) requests.activate();
        authListener = value -> refresh(true);
        auth.addAuthStateListener(authListener);
        refresh(true); // Coalesced with the initial AuthStateListener event.
    }
    @Override protected void onDetachedFromWindow() {
        requests.invalidate();
        if (authListener != null) auth.removeAuthStateListener(authListener);
        authListener = null;
        content = null;
        access = null;
        renderedFingerprint = null;
        owner = "";
        savedScroll = 0;
        restoreScroll = -1;
        removeAllViews();
        super.onDetachedFromWindow();
    }
    void pause() { resumed = false; requests.invalidate(); }
    void resume() {
        resumed = true;
        if (isAttachedToWindow()) { requests.activate(); refresh(true); }
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) refresh(true);
    }
    void refreshContent() { refresh(false); }

    private String scope() {
        FirebaseUser user = auth.getCurrentUser();
        SessionManager session = new SessionManager(activity);
        String uid = user == null ? "" : user.getUid(), local = session.getUserUid();
        return uid.length() + ":" + uid + local.length() + ":" + local + ":" + session.getSessionType();
    }
    private void refresh(boolean force) {
        if (!requests.active() || !isAttachedToWindow() || activity.isFinishing() || activity.isDestroyed()) return;
        final String currentOwner = scope();
        if (!currentOwner.equals(owner)) {
            owner = currentOwner;
            content = null;
            access = null;
            renderedFingerprint = null;
            savedScroll = 0;
            restoreScroll = -1;
            gate(false); // Never retain another user's content, even while a fresh request is pending.
        }
        if (!force && content != null && fingerprint.read().equals(renderedFingerprint)) return;
        final long request = requests.begin(currentOwner);
        if (request < 0) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            if (requests.accept(request, scope())) render(new ModerationAccess.Access(null));
            return;
        }
        // No Activity-bound listener: completion is handled even across pause; the generation gate rejects it.
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!isAttachedToWindow() || activity.isFinishing() || activity.isDestroyed()
                    || !requests.accept(request, scope())) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                if (content != null) savedScroll = content.getScrollY();
                content = null;
                access = null;
                gate(true); // Unknown role is an error state, not a guessed normal-user layout.
                return;
            }
            render(new ModerationAccess.Access(task.getResult().getClaims()));
        });
    }

    private void render(ModerationAccess.Access nextAccess) {
        String nextFingerprint = fingerprint.read();
        if (content != null && nextAccess.same(access) && nextFingerprint.equals(renderedFingerprint)) return;
        int y = content == null ? savedScroll : content.getScrollY();
        SettingsView next = factory.create(nextAccess); // Includes all authorized rows before attachment.
        content = next;
        access = nextAccess;
        renderedFingerprint = nextFingerprint;
        restoreScroll = y;
        removeAllViews();
        addView(next, new FrameLayout.LayoutParams(-1, -1));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (content != null && restoreScroll >= 0) {
            content.scrollTo(0, restoreScroll); // Layout is complete, drawing has not started. No delayed jump.
            savedScroll = content.getScrollY();
            restoreScroll = -1;
        }
    }
    private void gate(boolean failed) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20), dp(16), dp(20), dp(16));
        if (!failed) panel.addView(new ProgressBar(activity), new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView message = new TextView(activity);
        message.setText(failed ? "Ayarlar yüklenemedi. Tekrar deneyin." : "Ayarlar hazırlanıyor…");
        message.setTextColor(GreenTheme.color(R.color.zd_muted));
        message.setTextSize(14);
        message.setPadding(dp(10), dp(8), 0, dp(8));
        panel.addView(message);
        if (failed) {
            panel.setMinimumHeight(dp(48));
            panel.setFocusable(true);
            panel.setOnClickListener(v -> { gate(false); refresh(true); });
        }
        removeAllViews();
        addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
