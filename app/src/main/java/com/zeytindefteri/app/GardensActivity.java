package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class GardensActivity extends Activity {

    private DatabaseHelper databaseHelper;
    private GardenRepository gardenRepository;
    private LinearLayout list;

    private static final int BG = GreenTheme.color(R.color.zd_bg);
    private static final int CARD = GreenTheme.color(R.color.zd_card);
    private static final int GREEN = GreenTheme.parse("#2F6B3F");
    private static final int TEXT = GreenTheme.color(R.color.zd_text);
    private static final int MUTED = GreenTheme.color(R.color.zd_muted);
    private static final int BORDER = GreenTheme.color(R.color.zd_border);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        databaseHelper = new DatabaseHelper(this);
        gardenRepository = new GardenRepository(databaseHelper);
        setContentView(buildScreen());
    }

    @Override protected void onResume() {
        super.onResume();
        loadGardens();
    }

    private View buildScreen() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(100));
        scroll.addView(root);

        LinearLayout header = row();
        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        back.setOnClickListener(v -> finish());
        TextView title = text("Bahçelerim", 22, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView search = circleText("⌕", 22, CARD, TEXT);
        header.addView(search, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView add = circleText("+", 24, GREEN, Color.WHITE);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(42), dp(42));
        alp.setMargins(dp(8),0,0,0);
        header.addView(add, alp);
        add.setOnClickListener(v -> startActivity(new Intent(this, AddGardenActivity.class)));
        root.addView(header);
        TextView seasonSummary = text("Sezon Özeti →", 16, GreenTheme.color(R.color.zd_green), true);
        seasonSummary.setMinHeight(dp(48));
        seasonSummary.setGravity(Gravity.CENTER_VERTICAL);
        seasonSummary.setOnClickListener(v -> startActivity(new Intent(this, GardenAnalyticsActivity.class)));
        TextView reminders = text("Hatırlatmalar →", 16, GreenTheme.color(R.color.zd_green), true);
        reminders.setPadding(0,dp(10),0,dp(14));
        reminders.setMinHeight(dp(48));
        root.addView(reminders);
        reminders.setOnClickListener(v -> startActivity(new Intent(this, RemindersActivity.class)));
        root.addView(seasonSummary);


        LinearLayout chips = row();
        chips.setPadding(0, dp(10), 0, dp(12));
        chips.addView(chip("Tümü", true));
        chips.addView(chip("Aktif", false));
        chips.addView(chip("Pasif", false));
        root.addView(chips);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));

        frame.addView(scroll, new FrameLayout.LayoutParams(-1,-1));
        frame.addView(bottomNav("Bahçem"), new FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM));
        return frame;
    }

    private void loadGardens() {
        if (list == null) return;
        list.removeAllViews();
        Cursor c = null;
        try {
            c = gardenRepository.getGardenSummaries();
            if (c == null || !c.moveToFirst()) {
                TextView empty = text("Henüz bahçe yok.\nİlk bahçeni + ile ekleyebilirsin. 🌿", 14, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(70), 0, dp(70));
                list.addView(empty);
                return;
            }
            do {
                long id = getLong(c,"id");
                String name = getString(c,"name","Bahçe");
                int tree = getInt(c,"tree_count");
                int age = getInt(c,"age");
                String variety = getString(c,"variety","");
                String photo = getString(c,"photo_uri","");
                String last = getString(c,"last_operation","");
                list.addView(gardenCard(id,name,tree,age,variety,photo,last));
            } while (c.moveToNext());
        } finally { if (c != null) c.close(); }
    }

    private View gardenCard(long id, String name, int tree, int age, String variety, String photo, String last) {
        LinearLayout card = row();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(CARD,18,BORDER));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(136));
        cp.setMargins(0,0,0,dp(12));
        card.setLayoutParams(cp);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { if (!photo.trim().isEmpty()) image.setImageURI(Uri.parse(photo)); else image.setImageResource(com.zeytindefteri.app.R.drawable.welcome_hero_scene); }
        catch (Exception e) { image.setImageResource(com.zeytindefteri.app.R.drawable.welcome_hero_scene); }
        card.addView(image,new LinearLayout.LayoutParams(dp(112),-1));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13),dp(3),0,0);
        TextView n = text(name,16,TEXT,true); info.addView(n);
        TextView loc = text(variety.isEmpty()?"Zeytin bahçesi":variety,11,MUTED,false); loc.setPadding(0,dp(4),0,0); info.addView(loc);
        TextView meta = text("🌳  "+tree+" ağaç     📅  "+age+" yaş",11,GreenTheme.color(R.color.zd_muted),false); meta.setPadding(0,dp(12),0,0); info.addView(meta);
        TextView op = text(last.isEmpty()?"🌱 Son işlem: —":"🌱 Son işlem: "+last,10,MUTED,false); op.setPadding(0,dp(12),0,0); info.addView(op);
        card.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        TextView arrow = text("›",26,MUTED,false); arrow.setGravity(Gravity.CENTER); card.addView(arrow,new LinearLayout.LayoutParams(dp(26),-1));
        card.setOnClickListener(v -> { Intent i=new Intent(this,GardenDetailActivity.class); i.putExtra("garden_id",id); startActivity(i); });
        return card;
    }

    private View bottomNav(String active) {
        LinearLayout nav=row(); nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(CARD); nav.setElevation(dp(14));
        nav.addView(navItem("⌂\nAna Sayfa", active.equals("Ana"), v->{startActivity(new Intent(this,MainActivity.class)); finish();}), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("♧\nBahçem", true, v->{}), new LinearLayout.LayoutParams(0,-1,1));
        TextView plus=circleText("+",28,GREEN,Color.WHITE); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(58),dp(58)); pp.setMargins(4,0,4,dp(14)); nav.addView(plus,pp); plus.setOnClickListener(v->startActivity(new Intent(this,NewOperationActivity.class)));
        nav.addView(navItem("▥\nPiyasa", false, v->startActivity(new Intent(this,MarketActivity.class))), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("♙\nProfil", false, v->startActivity(new Intent(this,ProfileActivity.class))), new LinearLayout.LayoutParams(0,-1,1));
        return nav;
    }

    private TextView navItem(String s, boolean active, View.OnClickListener l){TextView t=text(s,10,active?GREEN:MUTED,active);t.setGravity(Gravity.CENTER);t.setOnClickListener(l);return t;}
    private TextView chip(String s,boolean active){TextView t=text(s,11,active?Color.WHITE:MUTED,active);t.setGravity(Gravity.CENTER);t.setBackground(round(active?GREEN:CARD,22,BORDER));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(80),dp(36));p.setMargins(0,0,dp(8),0);t.setLayoutParams(p);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView circleText(String s,int sp,int bg,int fg){TextView t=text(s,sp,fg,true);t.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(bg);g.setStroke(dp(1),BORDER);t.setBackground(g);return t;}
    private GradientDrawable round(int color,int r,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private String getString(Cursor c,String col,String def){int i=c.getColumnIndex(col);if(i<0||c.isNull(i))return def;String s=c.getString(i);return s==null?def:s;}
    private int getInt(Cursor c,String col){int i=c.getColumnIndex(col);return i<0||c.isNull(i)?0:c.getInt(i);}
    private long getLong(Cursor c,String col){int i=c.getColumnIndex(col);return i<0||c.isNull(i)?0:c.getLong(i);}
}
