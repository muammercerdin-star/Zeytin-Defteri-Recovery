package com.zeytindefteri.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/** Fixed dashboard when it fits; native focus scrolling when IME/accessibility needs space. */
public final class ElekDashboardScrollView extends ScrollView {
    public ElekDashboardScrollView(Context context) { super(context); init(); }
    public ElekDashboardScrollView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ElekDashboardScrollView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style); init();
    }
    private void init() {
        setFillViewport(true);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setSmoothScrollingEnabled(false);
    }
    private boolean needsScrolling() {
        if (getChildCount() == 0) return false;
        View content = getChildAt(0);
        int available = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        return content.getHeight() > available;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return needsScrolling() && super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return needsScrolling() && super.onTouchEvent(event);
    }
    @Override public void fling(int velocityY) {
        if (needsScrolling()) super.fling(velocityY);
    }
    @Override public void scrollTo(int x, int y) {
        if (needsScrolling()) super.scrollTo(0, y);
        else super.scrollTo(0, 0);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!needsScrolling()) super.scrollTo(0, 0);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // A resized window grows again when the keyboard closes. Drop its temporary offset.
        if (h > oldh && oldh > 0) super.scrollTo(0, 0);
    }
}
