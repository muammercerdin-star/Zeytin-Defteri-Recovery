package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Bundle;

/**
 * Mevcut giriş noktalarını (Ayarlar, karşılama ekranı) korur ve tek kaynaktaki
 * güncel belgeye yönlendirir. Eski activity_privacy.xml metni artık gösterilmez.
 */
public class PrivacyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LegalDocumentActivity.open(this, LegalDocuments.PRIVACY);
        finish();
        overridePendingTransition(0, 0);
    }
}
