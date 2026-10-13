package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Bundle;

/**
 * Mevcut giriş noktalarını (Ayarlar, karşılama ekranı) korur ve tek kaynaktaki
 * güncel belgeye yönlendirir. Eski activity_terms.xml metni artık gösterilmez.
 */
public class TermsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LegalDocumentActivity.open(this, LegalDocuments.TERMS);
        finish();
        overridePendingTransition(0, 0);
    }
}
