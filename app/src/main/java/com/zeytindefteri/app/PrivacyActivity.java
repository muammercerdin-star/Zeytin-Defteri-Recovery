package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class PrivacyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // =========================================================
        // LAYOUT
        // =========================================================

        setContentView(
                R.layout.activity_privacy
        );


        // =========================================================
        // GERİ BUTONU
        // =========================================================

        TextView btnBack =
                findViewById(
                        R.id.btnBack
                );


        btnBack.setOnClickListener(v -> {

            finish();

        });

    }

}