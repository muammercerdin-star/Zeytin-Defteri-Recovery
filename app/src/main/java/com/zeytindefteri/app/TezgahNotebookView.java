package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Alım defterinin "Defter görünümü"nü çizen tek Canvas'lı View.
 *
 * Fotoğraf gibi davranır: iki parmakla büyüt/küçült, tek parmakla kaydır, çift dokunuşla yakınlaş/sığdır.
 * Yerleşim ve değerler {@link TezgahNotebookPage}'den gelir; burada yalnız çizim ve dokunuş vardır.
 * Çizim sayfa koordinatlarında yapılır, ölçek/kaydırma {@link Matrix} ile uygulanır (20 iç içe layout yok).
 *
 * Not: Hedeflenen el yazısı fontu Caveat indirilemediği için sistemin "cursive" ailesi yedek olarak kullanılır
 * (bkz. TEZGAH-DEFTER-GORUNUMU-RAPORU.md). Gerçek Caveat eklenince yalnız {@link #handwriting} değişir.
 */
public final class TezgahNotebookView extends View {
    // Renkler
    private static final int PAPER = 0xFFFBFAF5;
    private static final int INK_BLUE = 0xFF1F3A8A;
    private static final int INK_RED = 0xFFC62828;
    private static final int PRINT = 0xFF2B2B2B;
    private static final int LINE = 0xFF3A3A3A;
    private static final int MATCH = 0xFFE53935;

    // Sayfa koordinatları (sabit; ölçek Matrix ile)
    private static final float MARGIN = 28f;
    private static final float HEADER_H = 150f;
    private static final float SECTION_W = 300f;
    private static final float SECTION_H = 300f;
    private static final float PAGE_W = 2 * MARGIN + TezgahNotebookPage.COLS * SECTION_W;
    private static final float PAGE_H = 2 * MARGIN + HEADER_H + TezgahNotebookPage.ROWS * SECTION_H;
    private static final float MAX_ZOOM_FACTOR = 3f;

    private final Paint thin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint matchBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint print = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint printSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tiny = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blueName = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headVal = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix matrix = new Matrix();
    private final float[] tmp = new float[2];
    private float scale = 1f;
    private float tx, ty;
    private float fitScale = 1f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private List<TezgahNotebookPage> pages = new ArrayList<>();
    private int current;

    /** Bir bölüme (alıma) dokunulunca çağrılır. */
    public interface OnSectionTap {
        void onTap(long purchaseId);
    }

    /** Sayfa değişince (yana kaydırma) "Sayfa 1/2" göstergesini tazelemek için. */
    public interface OnPageChange {
        void onPage(int index, int total);
    }

    private OnSectionTap sectionTap;
    private OnPageChange pageChange;

    public TezgahNotebookView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(PAPER);
        Typeface hand = handwriting();
        Typeface handBold = Typeface.create(hand, Typeface.BOLD);
        thin.setColor(LINE);
        thin.setStyle(Paint.Style.STROKE);
        thin.setStrokeWidth(1.2f);
        thick.setColor(PRINT);
        thick.setStyle(Paint.Style.STROKE);
        thick.setStrokeWidth(3.2f);
        tLine.setColor(PRINT);
        tLine.setStyle(Paint.Style.STROKE);
        tLine.setStrokeWidth(2.2f);
        matchBorder.setColor(MATCH);
        matchBorder.setStyle(Paint.Style.STROKE);
        matchBorder.setStrokeWidth(4f);
        print.setColor(PRINT);
        print.setTypeface(Typeface.DEFAULT_BOLD);
        print.setTextSize(15f);
        printSmall.setColor(PRINT);
        printSmall.setTypeface(Typeface.DEFAULT);
        printSmall.setTextSize(11f);
        tiny.setColor(0xFF777777);
        tiny.setTypeface(Typeface.DEFAULT);
        tiny.setTextSize(9f);
        blue.setColor(INK_BLUE);
        blue.setTypeface(hand);
        blue.setTextSize(21f);
        blueName.setColor(INK_BLUE);
        blueName.setTypeface(hand);
        blueName.setTextSize(20f);
        red.setColor(INK_RED);
        red.setTypeface(handBold);
        red.setTextSize(22f);
        headVal.setColor(INK_BLUE);
        headVal.setTypeface(hand);
        headVal.setTextSize(22f);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    /** Caveat yoksa sistemin el yazısı ailesi; o da yoksa normal yazı tipi. Türkçe karakterleri gösterir. */
    private static Typeface handwriting() {
        Typeface t = Typeface.create("cursive", Typeface.NORMAL);
        return t == null ? Typeface.DEFAULT : t;
    }

    public void setListeners(OnSectionTap tap, OnPageChange page) {
        this.sectionTap = tap;
        this.pageChange = page;
    }

    public void setPages(List<TezgahNotebookPage> list, int startPage) {
        this.pages = list == null ? new ArrayList<>() : list;
        this.current = Math.max(0, Math.min(startPage, this.pages.size() - 1));
        requestFit();
        notifyPage();
    }

    public int currentPage() {
        return current;
    }

    public int pageCount() {
        return pages.size();
    }

    private void notifyPage() {
        if (pageChange != null) {
            pageChange.onPage(current, pages.size());
        }
    }

    // ------------------------------------------------------------------ ölçek / kaydırma

    private boolean needFit = true;

    private void requestFit() {
        needFit = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        needFit = true;
    }

    private void computeFit() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        fitScale = Math.min(w / PAGE_W, h / PAGE_H);
        scale = fitScale;
        tx = (w - PAGE_W * scale) / 2f;
        ty = (h - PAGE_H * scale) / 2f;
        if (ty < 0) {
            ty = 0;
        }
        needFit = false;
    }

    /** Sayfayı ekrana sığdır. */
    public void fit() {
        needFit = true;
        invalidate();
    }

    public void zoomIn() {
        zoomBy(1.4f, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomOut() {
        zoomBy(1 / 1.4f, getWidth() / 2f, getHeight() / 2f);
    }

    private void zoomBy(float factor, float px, float py) {
        float max = fitScale * MAX_ZOOM_FACTOR;
        float target = Math.max(fitScale, Math.min(max, scale * factor));
        float real = target / scale;
        scale = target;
        tx = px - (px - tx) * real;
        ty = py - (py - ty) * real;
        clamp();
        invalidate();
    }

    private void clamp() {
        float pageW = PAGE_W * scale;
        float pageH = PAGE_H * scale;
        int w = getWidth();
        int h = getHeight();
        if (pageW <= w) {
            tx = (w - pageW) / 2f;
        } else {
            tx = Math.min(0, Math.max(w - pageW, tx));
        }
        if (pageH <= h) {
            ty = (h - pageH) / 2f;
        } else {
            ty = Math.min(0, Math.max(h - pageH, ty));
        }
    }

    private boolean atFit() {
        return scale <= fitScale * 1.03f;
    }

    public void setPage(int index) {
        if (index < 0 || index >= pages.size() || index == current) {
            return;
        }
        current = index;
        needFit = true;
        invalidate();
        notifyPage();
    }

    public void nextPage() {
        setPage(current + 1);
    }

    public void prevPage() {
        setPage(current - 1);
    }

    /** Arama: eşleşen bölüme git (sayfa değiştir, yakınlaş ve ortala). */
    public void focus(int pageIndex, int sectionIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return;
        }
        current = pageIndex;
        notifyPage();
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            needFit = true;
            invalidate();
            return;
        }
        int col = sectionIndex % TezgahNotebookPage.COLS;
        int row = sectionIndex / TezgahNotebookPage.COLS;
        float cx = MARGIN + (col + 0.5f) * SECTION_W;
        float cy = MARGIN + HEADER_H + (row + 0.5f) * SECTION_H;
        scale = Math.min(fitScale * MAX_ZOOM_FACTOR, Math.max(fitScale * 1.8f, fitScale));
        tx = w / 2f - cx * scale;
        ty = h / 2f - cy * scale;
        clamp();
        needFit = false;
        invalidate();
    }

    // ------------------------------------------------------------------ dokunuş

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        return true;
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector d) {
            zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
            return true;
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
            tx -= dx;
            ty -= dy;
            clamp();
            invalidate();
            return true;
        }

        @Override public boolean onSingleTapUp(MotionEvent e) {
            long id = sectionAt(e.getX(), e.getY());
            if (id >= 0 && sectionTap != null) {
                sectionTap.onTap(id);
            }
            return true;
        }

        @Override public boolean onDoubleTap(MotionEvent e) {
            if (atFit()) {
                zoomBy(fitScale * 2f / scale, e.getX(), e.getY());
            } else {
                fit();
            }
            return true;
        }

        @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
            if (atFit() && Math.abs(vx) > 600 && Math.abs(vx) > Math.abs(vy)) {
                if (vx < 0) {
                    nextPage();
                } else {
                    prevPage();
                }
                return true;
            }
            return false;
        }
    }

    /** Ekran noktasını sayfa koordinatına çevirip hangi bölüm olduğunu bulur; alım yoksa -1. */
    private long sectionAt(float screenX, float screenY) {
        if (pages.isEmpty()) {
            return -1;
        }
        float px = (screenX - tx) / scale;
        float py = (screenY - ty) / scale;
        float gx = px - MARGIN;
        float gy = py - MARGIN - HEADER_H;
        if (gx < 0 || gy < 0) {
            return -1;
        }
        int col = (int) (gx / SECTION_W);
        int row = (int) (gy / SECTION_H);
        if (col < 0 || col >= TezgahNotebookPage.COLS || row < 0 || row >= TezgahNotebookPage.ROWS) {
            return -1;
        }
        int index = row * TezgahNotebookPage.COLS + col;
        List<TezgahNotebookPage.Section> sections = pages.get(current).sections;
        if (index >= sections.size()) {
            return -1;
        }
        return sections.get(index).entry.id;
    }

    // ------------------------------------------------------------------ çizim

    @Override protected void onDraw(Canvas canvas) {
        if (needFit) {
            computeFit();
        }
        if (pages.isEmpty()) {
            return;
        }
        canvas.save();
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(tx, ty);
        canvas.concat(matrix);
        drawPage(canvas, pages.get(current));
        canvas.restore();
    }

    private void drawPage(Canvas canvas, TezgahNotebookPage page) {
        canvas.drawRect(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN, thick);
        drawHeader(canvas, page);
        float top = MARGIN + HEADER_H;
        for (int row = 0; row < TezgahNotebookPage.ROWS; row++) {
            for (int col = 0; col < TezgahNotebookPage.COLS; col++) {
                float left = MARGIN + col * SECTION_W;
                float t = top + row * SECTION_H;
                int index = row * TezgahNotebookPage.COLS + col;
                TezgahNotebookPage.Section sec = index < page.sections.size() ? page.sections.get(index) : null;
                drawSection(canvas, left, t, sec);
            }
        }
    }

    private void drawHeader(Canvas canvas, TezgahNotebookPage page) {
        float left = MARGIN + 14;
        float right = PAGE_W - MARGIN - 14;
        float y1 = MARGIN + 54;
        float y2 = MARGIN + 110;
        canvas.drawText("GELEN KASA :", left, y1, print);
        canvas.drawText(String.valueOf(page.gelenKasa), left + print.measureText("GELEN KASA : "), y1, headVal);
        canvas.drawText("GİDEN KASA :", left, y2, print);
        if (page.gidenKasa != null) {
            canvas.drawText(String.valueOf(page.gidenKasa), left + print.measureText("GİDEN KASA : "), y2, headVal);
        }
        String tarih = "TARİH :";
        String tarihVal = page.dateText;
        float tw = print.measureText(tarih + " ");
        float vw = headVal.measureText(tarihVal);
        canvas.drawText(tarih, right - tw - vw, y1, print);
        canvas.drawText(tarihVal, right - vw, y1, headVal);
        String para = "GELEN PARA :";
        float pw = print.measureText(para + " ");
        String paraVal = page.gelenPara == null ? "" : TezgahNotebookPage.cellTl(page.gelenPara) + " ₺";
        float pvw = red.measureText(paraVal);
        canvas.drawText(para, right - pw - pvw, y2, print);
        if (page.gelenPara != null) {
            canvas.drawText(paraVal, right - pvw, y2, red);
        }
        canvas.drawLine(MARGIN, MARGIN + HEADER_H, PAGE_W - MARGIN, MARGIN + HEADER_H, thick);
    }

    private void drawSection(Canvas canvas, float left, float top, TezgahNotebookPage.Section sec) {
        float rightEdge = left + SECTION_W;
        float bottom = top + SECTION_H;
        canvas.drawRect(left, top, rightEdge, bottom, thin);
        float nameH = 40f;
        canvas.drawText("Müstahsil", left + 6, top + 14, tiny);
        if (sec != null) {
            canvas.drawText(sec.entry.seller, left + 6 + tiny.measureText("Müstahsil "), top + 26, blueName);
        }
        float gridTop = top + nameH;
        canvas.drawLine(left, gridTop, rightEdge, gridTop, thin);
        float labelCol = left + 44;
        float midCol = left + 44 + (SECTION_W - 44) * 0.46f;
        canvas.drawLine(labelCol, gridTop, labelCol, bottom, thin);
        canvas.drawLine(midCol, gridTop, midCol, bottom, thin);
        int rows = TezgahNotebookPage.GRADES + 2;
        float rowH = (SECTION_H - nameH) / rows;
        // Başlık satırı: boş | KG. | TUTARI
        float hy = gridTop + rowH - 7;
        canvas.drawText("KG.", labelCol + 8, hy, printSmall);
        canvas.drawText("TUTARI", midCol + 8, hy, printSmall);
        // D / 1 / 2 / 3 / 4 / Y satırları
        for (int g = 0; g < TezgahNotebookPage.GRADES; g++) {
            float ry = gridTop + (g + 1) * rowH;
            canvas.drawLine(left, ry, rightEdge, ry, thin);
            float baseline = ry + rowH - 9;
            canvas.drawText(TezgahNotebookPage.ROW_LABELS[g], left + 14, baseline, print);
            if (sec != null && sec.filled(g)) {
                drawRight(canvas, TezgahNotebookPage.cellKg(sec.rowKg(g)), midCol - 8, baseline, blue);
                drawRight(canvas, TezgahNotebookPage.cellTl(sec.rowAmount(g)), rightEdge - 8, baseline, blue);
            }
        }
        // T satırı (üstünde kalın çizgi, değerler kırmızı)
        float ty0 = gridTop + (TezgahNotebookPage.GRADES + 1) * rowH;
        canvas.drawLine(left, ty0, rightEdge, ty0, tLine);
        float tBase = ty0 + rowH - 9;
        canvas.drawText("T", left + 14, tBase, print);
        if (sec != null) {
            drawRight(canvas, TezgahNotebookPage.cellKg(sec.totalKg()), midCol - 8, tBase, red);
            drawRight(canvas, TezgahNotebookPage.cellTl(sec.totalAmount()), rightEdge - 8, tBase, red);
        }
        if (sec != null && sec.highlighted) {
            canvas.drawRect(left + 2, top + 2, rightEdge - 2, bottom - 2, matchBorder);
        }
    }

    private void drawRight(Canvas canvas, String text, float rightX, float baseline, Paint paint) {
        float w = paint.measureText(text);
        canvas.drawText(text, rightX - w, baseline, paint);
    }
}
