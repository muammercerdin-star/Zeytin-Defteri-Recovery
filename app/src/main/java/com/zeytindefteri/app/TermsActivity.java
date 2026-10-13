package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class TermsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_terms);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

    }

}