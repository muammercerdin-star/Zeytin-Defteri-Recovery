package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Onboarding-only presentation. Never reads or writes producer/account data. */
public final class OnboardingReferencePage extends ScrollView {
    private static final int BACKGROUND = Color.rgb(3, 30, 29);
    private static final int ACCENT = Color.rgb(170, 236, 91);
    private static final int TEXT = Color.rgb(242, 247, 244);
    private static final int MUTED = Color.rgb(203, 216, 211);

    private int viewportHeight;

    public OnboardingReferencePage(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setBackgroundColor(BACKGROUND);
    }

    public void bind(int page) {
        removeAllViews();
        final int resource;
        final String firstLine;
        final String secondLine;
        final String description;
        final String artworkDescription;
        final String[] benefits;
        final Rect scene;
        final Rect[] icons;
        switch (page) {
            case 1:
                resource = R.drawable.onboarding_reference_story;
                firstLine = "Zeytin";
                secondLine = "Senin Hikâyen";
                description = "Bahçeni kaydet,\nemeğini takip et,\ndaha verimli bir geleceğe\nadım at.";
                artworkDescription = "Gün batımında zeytinlik; ahşap masa üzerinde Zeytin Defteri ve zeytinler.";
                benefits = new String[]{"Bahçelerini kaydet", "Üretimini takip et", "Daha güçlü bir sezon planla"};
                scene = new Rect(0, 455, 864, 1310);
                icons = new Rect[]{new Rect(142, 1324, 208, 1390), new Rect(396, 1324, 462, 1390), new Rect(655, 1324, 725, 1394)};
                break;
            case 3:
                resource = R.drawable.onboarding_reference_market;
                firstLine = "Piyasayı";
                secondLine = "Anlık Takip Et";
                description = "Bölgesel fiyatları gör, Zeytin Ligi'nde üreticileri keşfet, daha doğru kararlar ver.";
                artworkDescription = "Zeytin Ligi ve Piyasa telefon tanıtımı. Örnek sıralama: Muammer Çerdin, Volkan Afşar, Yusuf Soyaslan, Hakan Yıldırım, Emre Karataş.";
                benefits = new String[]{"Bölgesel fiyatlar", "Zeytin Ligi sıralaması", "Piyasa analizleri"};
                scene = new Rect(221, 191, 877, 1334);
                icons = new Rect[]{new Rect(181, 1360, 267, 1445), new Rect(502, 1352, 590, 1445), new Rect(829, 1364, 918, 1445)};
                break;
            case 4:
                resource = R.drawable.onboarding_reference_community;
                firstLine = "Üreticilerle";
                secondLine = "Birlikte";
                description = "Soru sor, tecrübeni paylaş, diğer üreticilerle iletişim kur. Yalnız değilsin, koskoca bir zeytin topluluğu var.";
                artworkDescription = "Sohbet telefon tanıtımı. Kategoriler: #Genel, #Piyasa, #Hastalık, #Hasat. Üreticiler soru soruyor ve tecrübelerini paylaşıyor.";
                benefits = new String[]{"Soru sor cevap al", "Tecrübe paylaş", "Üreticilerle tanış"};
                scene = new Rect(232, 253, 850, 1379);
                icons = new Rect[]{new Rect(194, 1390, 282, 1478), new Rect(493, 1390, 583, 1478), new Rect(786, 1390, 882, 1478)};
                break;
            default:
                throw new IllegalArgumentException("Reference page must be 1, 3 or 4");
        }

        // The supplied reference bytes are unchanged. Only the visual regions
        // are drawn; headings, explanations and benefit labels are native text.
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource);
        final boolean balanced = page == 3 || page == 4;
        LinearLayout content = balanced ? new BalancedContent() : new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), dp(balanced ? 6 : 12), dp(12), dp(balanced ? 8 : 16));
        addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        content.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        if (balanced) {
            copy.setMinimumHeight(dp(110));
            TextView title = text(firstLine + " " + secondLine, 22, TEXT, true);
            title.setGravity(page == 4 ? Gravity.CENTER : Gravity.START);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));
        } else {
            copy.addView(text(firstLine, 30, TEXT, true), new LinearLayout.LayoutParams(-1, -2));
            copy.addView(text(secondLine, 30, ACCENT, true), new LinearLayout.LayoutParams(-1, -2));
        }
        TextView subtitle = text(description, balanced ? 14 : 16, MUTED, false);
        if (page == 4) subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(balanced ? 8 : 12);
        copy.addView(subtitle, subtitleParams);

        ReferenceRegion artwork = new ReferenceRegion(bitmap, scene, page == 1 ? 390 : 460, page == 1);
        artwork.setContentDescription(artworkDescription);
        LinearLayout.LayoutParams artworkParams = new LinearLayout.LayoutParams(-1, -2);
        artworkParams.topMargin = dp(balanced ? 8 : 14);
        content.addView(artwork, artworkParams);
        if (balanced) ((BalancedContent) content).artwork = artwork;

        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.TOP);
        if (balanced) footer.setMinimumHeight(dp(76));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(balanced ? 10 : 16);
        content.addView(footer, footerParams);
        for (int i = 0; i < benefits.length; i++) {
            LinearLayout column = new LinearLayout(getContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setPadding(dp(4), 0, dp(4), 0);
            footer.addView(column, new LinearLayout.LayoutParams(0, -2, 1f));
            ReferenceRegion icon = new ReferenceRegion(bitmap, icons[i], 32, false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            column.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView label = text(benefits[i], 12, TEXT, false);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(8);
            column.addView(label, labelParams);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // ViewFlipper supplies the real available height, excluding the outer CTA.
        viewportHeight = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? 0 : MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
        super.onMeasure(widthSpec, heightSpec);
    }

    /** Used only by pages 3/4. Page 1 retains its original layout and sizing. */
    private final class BalancedContent extends LinearLayout {
        private ReferenceRegion artwork;

        BalancedContent() {
            super(OnboardingReferencePage.this.getContext());
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            if (artwork == null || viewportHeight <= 0) return;
            int fixedHeight = getPaddingTop() + getPaddingBottom();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == View.GONE) continue;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
                fixedHeight += params.topMargin + params.bottomMargin;
                if (child != artwork) fixedHeight += child.getMeasuredHeight();
            }
            int width = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            int naturalHeight = Math.round(width * artwork.source.height() / (float) artwork.source.width());
            int available = Math.max(dp(160), viewportHeight - fixedHeight);
            int target = Math.min(naturalHeight, Math.min(dp(460), available));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) artwork.getLayoutParams();
            if (params.height != target) {
                // Resolve in this measurement pass, not with post()/timers; avoids jumping.
                params.height = target;
                super.onMeasure(widthSpec, heightSpec);
            }
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        // No maxLines or fixed height: large system fonts wrap without clipping.
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Aspect-preserving presentation of a region of the original reference. */
    private final class ReferenceRegion extends View {
        private final Bitmap bitmap;
        private final Rect source;
        private final int maximumHeight;
        private final boolean feather;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF destination = new RectF();
        private final Paint fade = new Paint();

        ReferenceRegion(Bitmap bitmap, Rect source, int maximumHeight, boolean feather) {
            super(OnboardingReferencePage.this.getContext());
            this.bitmap = bitmap;
            this.source = source;
            this.maximumHeight = dp(maximumHeight);
            this.feather = feather;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int naturalHeight = Math.round(width * source.height() / (float) source.width());
            int height = Math.min(naturalHeight, maximumHeight);
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            float scale = Math.min(getWidth() / (float) source.width(), getHeight() / (float) source.height());
            float width = source.width() * scale;
            float height = source.height() * scale;
            float left = (getWidth() - width) / 2f;
            float top = (getHeight() - height) / 2f;
            destination.set(left, top, left + width, top + height);
            canvas.drawBitmap(bitmap, source, destination, paint);
            if (feather) {
                float edge = Math.min(dp(14), height / 8f);
                fade.setShader(new LinearGradient(0, top, 0, top + edge, BACKGROUND, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawRect(left, top, left + width, top + edge, fade);
                fade.setShader(new LinearGradient(0, top + height - edge, 0, top + height, Color.TRANSPARENT, BACKGROUND, Shader.TileMode.CLAMP));
                canvas.drawRect(left, top + height - edge, left + width, top + height, fade);
            }
        }
    }
}
