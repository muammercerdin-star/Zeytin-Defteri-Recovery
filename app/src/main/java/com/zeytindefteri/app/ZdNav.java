package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Eski ekranlarda kalmış ortak navigasyon.
 * Üst seviye sayfalar artık ayrı Activity açmak yerine MainActivity'nin
 * tek-ekran kabuğuna ilgili bölüm koduyla döner.
 */
public final class ZdNav {
    private static final int GREEN=GreenTheme.parse("#2F6B3F");
    private static final int CARD=GreenTheme.color(R.color.zd_card);
    private static final int MUTED=GreenTheme.color(R.color.zd_muted);
    private ZdNav(){}

    public static void attach(Activity a, String active){
        LinearLayout nav=new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(CARD);
        nav.setElevation(dp(a,16));

        nav.addView(item(a,"⌂\nAna Sayfa","Ana".equals(active),v->open(a,"HOME")), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(item(a,"♧\nBahçem","Bahçem".equals(active),v->open(a,"GARDENS")), new LinearLayout.LayoutParams(0,-1,1));

        TextView plus=item(a,"+",false,v->a.startActivity(new Intent(a,NewOperationActivity.class)));
        plus.setTextSize(29); plus.setTextColor(Color.WHITE);
        GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(GREEN); plus.setBackground(g);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(a,58),dp(a,58)); pp.setMargins(dp(a,4),0,dp(a,4),dp(a,14)); nav.addView(plus,pp);

        nav.addView(item(a,"▥\nPiyasa","Piyasa".equals(active),v->open(a,"MARKET")), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(item(a,"♙\nProfil","Profil".equals(active),v->open(a,"PROFILE")), new LinearLayout.LayoutParams(0,-1,1));

        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,76),Gravity.BOTTOM);
        a.addContentView(nav,p);
    }

    private static void open(Activity a, String section){
        Intent i=new Intent(a,MainActivity.class);
        i.putExtra("open_section",section);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(i);
        if(!(a instanceof MainActivity)) a.finish();
    }

    private static TextView item(Activity a,String s,boolean active,View.OnClickListener l){
        TextView t=new TextView(a); t.setText(s); t.setTextSize(10); t.setGravity(Gravity.CENTER);
        t.setTextColor(active?GREEN:MUTED); if(active)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setOnClickListener(l); return t;
    }
    private static int dp(Activity a,int v){return (int)(v*a.getResources().getDisplayMetrics().density+.5f);}
}
