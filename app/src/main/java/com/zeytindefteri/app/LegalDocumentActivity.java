package com.zeytindefteri.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** KVKK Aydınlatma Metni, Gizlilik Politikası ve Kullanım Koşulları için ortak okuma ekranı. */
public class LegalDocumentActivity extends Activity {
    static final String EXTRA_DOCUMENT = "legal_document";

    static void open(Context context, String document) {
        context.startActivity(new Intent(context, LegalDocumentActivity.class)
                .putExtra(EXTRA_DOCUMENT, document));
    }

    /** Hesap/veri silme talebini e-posta uygulamasıyla başlatır; uygulama yoksa adresi kopyalar. */
    static void requestDeletion(Context context) {
        sendMail(context, LegalDocuments.deletionSubject(), LegalDocuments.deletionBody());
    }

    static void sendMail(Context context, String subject, String body) {
        Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, new String[]{LegalDocuments.CONTACT_EMAIL})
                .putExtra(Intent.EXTRA_SUBJECT, subject);
        if (body != null) mail.putExtra(Intent.EXTRA_TEXT, body);
        try {
            context.startActivity(mail);
        } catch (ActivityNotFoundException e) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("E-posta", LegalDocuments.CONTACT_EMAIL));
            }
            Toast.makeText(context, "E-posta uygulaması bulunamadı. Adres kopyalandı: "
                    + LegalDocuments.CONTACT_EMAIL, Toast.LENGTH_LONG).show();
        }
    }

    private String document;
    private int colorTitle, colorBody, colorAccent, colorField, colorBorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        document = getIntent().getStringExtra(EXTRA_DOCUMENT);
        if (!LegalDocuments.isKnown(document)) document = LegalDocuments.PRIVACY;
        colorTitle = getResources().getColor(R.color.auth_title);
        colorBody = getResources().getColor(R.color.auth_body);
        colorAccent = getResources().getColor(R.color.auth_accent);
        colorField = getResources().getColor(R.color.auth_field);
        colorBorder = getResources().getColor(R.color.auth_border);
        setTitle(LegalDocuments.title(document));
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(getResources().getColor(R.color.auth_bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(16), dp(4));
        TextView back = text("‹", 28, colorTitle, false);
        back.setGravity(Gravity.CENTER);
        back.setMinWidth(dp(48));
        back.setMinHeight(dp(48));
        back.setContentDescription("Geri");
        back.setBackgroundResource(android.R.drawable.list_selector_background);
        back.setOnClickListener(v -> finish());
        TextView barTitle = text(LegalDocuments.title(document), 17, colorTitle, true);
        bar.addView(back);
        bar.addView(barTitle);
        screen.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(4), dp(20), dp(28));
        scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (LegalDocuments.isDraft()) {
            TextView draft = text("TASLAK · Bu metin yayın öncesinde hukuki incelemeden geçirilecektir.",
                    12, colorAccent, true);
            draft.setPadding(dp(12), dp(10), dp(12), dp(10));
            draft.setBackground(rounded(colorField, colorBorder));
            body.addView(draft, margins(0, 4, 0, 12));
        }

        renderDocument(body, LegalDocuments.fill(readDocument()));

        if (!LegalDocuments.TERMS.equals(document)) {
            body.addView(action("Hesap ve veri silme talebi gönder", true,
                    v -> requestDeletion(this)), margins(0, 20, 0, 0));
        }
        body.addView(action("E-posta ile iletişime geç", false,
                v -> sendMail(this, "Zeytin Defteri - " + LegalDocuments.title(document), null)),
                margins(0, 10, 0, 0));

        TextView related = text("İlgili belgeler", 12, colorBody, true);
        body.addView(related, margins(0, 22, 0, 2));
        for (String other : new String[]{LegalDocuments.KVKK, LegalDocuments.PRIVACY, LegalDocuments.TERMS}) {
            if (other.equals(document)) continue;
            TextView link = text(LegalDocuments.title(other) + "  ›", 14, colorAccent, false);
            link.setMinHeight(dp(44));
            link.setGravity(Gravity.CENTER_VERTICAL);
            link.setBackgroundResource(android.R.drawable.list_selector_background);
            link.setOnClickListener(v -> open(this, other));
            body.addView(link);
        }
        return screen;
    }

    private void renderDocument(LinearLayout body, String content) {
        String[] lines = content.split("\n");
        boolean first = true;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (first) {
                body.addView(text(line, 22, colorTitle, true), margins(0, 4, 0, 4));
                first = false;
            } else if (line.startsWith("# ")) {
                body.addView(text(line.substring(2), 16, colorTitle, true), margins(0, 18, 0, 6));
            } else if (line.startsWith("- ")) {
                LinearLayout row = new LinearLayout(this);
                TextView dot = text("•", 14, colorAccent, true);
                dot.setMinWidth(dp(16));
                TextView item = paragraph(line.substring(2));
                row.addView(dot);
                row.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                body.addView(row, margins(0, 0, 0, 6));
            } else if (line.startsWith("Sürüm ")) {
                body.addView(text(line, 12, colorBody, false), margins(0, 0, 0, 8));
            } else {
                body.addView(paragraph(line), margins(0, 0, 0, 8));
            }
        }
    }

    private TextView paragraph(String value) {
        TextView view = text(value, 14, colorBody, false);
        view.setLineSpacing(dp(3), 1f);
        view.setLinkTextColor(colorAccent);
        Linkify.addLinks(view, Linkify.EMAIL_ADDRESSES);
        return view;
    }

    private String readDocument() {
        int resource = LegalDocuments.KVKK.equals(document) ? R.raw.legal_kvkk
                : LegalDocuments.TERMS.equals(document) ? R.raw.legal_terms : R.raw.legal_privacy;
        try (InputStream in = getResources().openRawResource(resource);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return LegalDocuments.title(document) + "\n\nBelge açılamadı. Lütfen {{EPOSTA}} adresine yaz.";
        }
    }

    private TextView action(String label, boolean primary, View.OnClickListener listener) {
        TextView view = text(label, 14, primary ? 0xFFFFFFFF : colorAccent, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(primary ? rounded(getResources().getColor(R.color.auth_green), 0)
                : rounded(colorField, colorBorder));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(listener);
        return view;
    }

    private TextView text(String value, float sp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(12));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
