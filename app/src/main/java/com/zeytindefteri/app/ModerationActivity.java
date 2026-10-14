package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Moderator-only viewer. All reads and mutations use authenticated callable Functions. */
public final class ModerationActivity extends Activity {
    private final ModerationPage page = new ModerationPage();
    private FirebaseAuth auth;
    private ModerationBackendClient functions;
    private FirebaseAuth.AuthStateListener authListener;
    private String owner;
    private boolean active, authorized, busy;
    private int generation;
    private LinearLayout root, list, filters;
    private TextView message, more, retry, refresh;
    private AlertDialog dialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int bg = GreenTheme.color(R.color.zd_bg);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        auth = FirebaseAuth.getInstance();
        functions = ModerationBackendClient.getInstance();
        showGate();
    }
    @Override protected void onStart() {
        super.onStart(); active = true;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous()) { deny(); return; }
        owner = user.getUid();
        authListener = value -> {
            FirebaseUser current = value.getCurrentUser();
            if (current == null || !owner.equals(current.getUid())) deny();
        };
        auth.addAuthStateListener(authListener);
        final int request = ++generation;
        user.getIdToken(true).addOnCompleteListener(this, task -> {
            if (!current(request)) return;
            if (!task.isSuccessful()) {
                Toast.makeText(this,"Yetki doğrulanamadı. Bağlantınızı kontrol edip tekrar deneyin.",Toast.LENGTH_LONG).show();
                finish(); return;
            }
            if (!ModerationAccess.allowed(task.getResult().getClaims())) { deny(); return; }
            authorized = true; build(); reload();
        });
    }
    @Override protected void onStop() {
        active = false; authorized = false; busy = false; generation++;
        if (authListener != null) auth.removeAuthStateListener(authListener);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        page.reset(page.status); showGate();
        super.onStop();
    }
    private boolean current(int request) {
        FirebaseUser user = auth.getCurrentUser();
        return active && !isFinishing() && !isDestroyed() && generation == request
                && user != null && owner != null && owner.equals(user.getUid());
    }
    private void deny() {
        authorized = false; generation++; page.reset(page.status);
        if (dialog != null) dialog.dismiss();
        showGate(); Toast.makeText(this,"Bu alan için moderatör yetkisi gerekiyor.",Toast.LENGTH_LONG).show(); finish();
    }
    private void showGate() {
        TextView gate = text("Yetki kontrol ediliyor…",16);
        gate.setGravity(Gravity.CENTER); gate.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        setContentView(gate);
    }
    private void build() {
        root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(16),dp(12),dp(16),dp(16));
        root.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        TextView back = action("←  Moderasyon Merkezi",this::finish);
        back.setTextSize(20); root.addView(back);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        filters = new LinearLayout(this); horizontal.addView(filters);
        for (int i=0;i<ModerationPage.STATES.length;i++) {
            final String state = ModerationPage.STATES[i];
            TextView filter = action(ModerationPage.LABELS[i],()-> {
                if (!busy && !state.equals(page.status)) { page.reset(state); reload(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,dp(8),dp(8),dp(12));
            filters.addView(filter,lp);
        }
        root.addView(horizontal);
        refresh = action("Yenile",this::reload); root.addView(refresh);
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(1); scroll.addView(content);
        message = text("Şikâyetler yükleniyor…",14); message.setPadding(0,dp(16),0,dp(12)); content.addView(message);
        retry = action("Yeniden Dene",()->load(page.rows.size()>0));content.addView(retry);
        list = new LinearLayout(this);list.setOrientation(1);content.addView(list);
        more = action("Daha fazla yükle",()->load(true));content.addView(more);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void reload() {
        if (!authorized || busy) return;
        generation++; page.reset(page.status); list.removeAllViews();load(false);
    }
    private void load(boolean append) {
        if (!authorized || busy || !current(generation)) return;
        if (append && page.cursor == null) return;
        busy = true; controls(); message.setText("Şikâyetler yükleniyor…");retry.setVisibility(View.GONE);
        final int request = generation;
        try {
            functions.operation("moderationListReports").withTimeout(30,TimeUnit.SECONDS)
                    .call(page.request(append)).addOnCompleteListener(this, task -> {
                if (!current(request)) return;
                busy = false;
                if (!task.isSuccessful()) { failure(task.getException(),false);return; }
                try { page.accept(task.getResult().getData());render(); }
                catch (RuntimeException e) { failure(e,false); }
            });
        } catch (RuntimeException e) { busy = false;failure(e,false); }
    }
    private void controls() {
        refresh.setEnabled(!busy);
        for(int i=0;i<filters.getChildCount();i++) {
            View filter=filters.getChildAt(i);filter.setEnabled(!busy);
            filter.setAlpha(ModerationPage.STATES[i].equals(page.status)?1f:0.6f);
        }
        more.setVisibility(!busy && page.cursor!=null?View.VISIBLE:View.GONE);
        retry.setEnabled(!busy);
    }
    private void render() {
        list.removeAllViews();
        for(Map<String,Object> row:page.rows.values()) {
            LinearLayout card = new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(14),dp(16),dp(14));
            card.setBackground(background());
            card.addView(text(reason(value(row,"reason")),17));
            card.addView(text(type(value(row,"target_type"))+" • "+label(value(row,"status")),14));
            card.addView(text(date(value(row,"created_at")),13));
            card.setOnClickListener(v->{if(!busy && authorized)details(row);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(4));list.addView(card,lp);
        }
        message.setText(page.rows.isEmpty()?label(page.status)+" durumunda şikâyet bulunmuyor.":"En yeni şikâyetler önce gösteriliyor.");
        retry.setVisibility(View.GONE);controls();
    }
    private void details(Map<String,Object> row) {
        String detail="Neden: "+reason(value(row,"reason"))+"\nHedef türü: "+type(value(row,"target_type"))
                +"\nŞikâyet eden UID: "+value(row,"reporter_uid")+"\nŞikâyet edilen UID: "+value(row,"reported_uid")
                +"\nHedef ID: "+value(row,"target_id")
                +(value(row,"post_cloud_id").isEmpty()?"":"\nGönderi ID: "+value(row,"post_cloud_id"))
                +"\nOluşturulma: "+date(value(row,"created_at"))+"\nDurum: "+label(value(row,"status"))
                +(value(row,"reviewed_by").isEmpty()?"":"\nİnceleyen UID: "+value(row,"reviewed_by"))
                +(value(row,"reviewed_at").isEmpty()?"":"\nİncelenme: "+date(value(row,"reviewed_at")));
        TextView body=text(detail,15);body.setPadding(dp(20),dp(12),dp(20),dp(12));body.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        dialog=new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Şikâyet detayı").setView(scroll)
                .setNegativeButton("Kapat",null).setPositiveButton("Durumu değiştir",(d,w)->choose(row)).create();showDialog();
    }
    private void choose(Map<String,Object> row) {
        if(!authorized || busy || !current(generation))return;
        dialog=new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Yeni durum")
                .setItems(ModerationPage.LABELS,(d,index)-> {
                    String next=ModerationPage.STATES[index];
                    if(next.equals(value(row,"status")))return;
                    dialog=new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Durumu değiştir")
                            .setMessage("Şikâyet durumu “"+label(next)+"” olarak güncellensin mi?")
                            .setNegativeButton("Vazgeç",null).setPositiveButton("Güncelle",(x,w)->update(row,next)).create();showDialog();
                }).setNegativeButton("Vazgeç",null).create();showDialog();
    }
    private void update(Map<String,Object> row,String next) {
        if(!authorized || busy || !current(generation))return;
        busy=true;controls();message.setText("Şikâyet durumu güncelleniyor…");
        final int request=generation;
        try {
            functions.operation("moderationUpdateReportStatus").withTimeout(30,TimeUnit.SECONDS)
                    .call(ModerationPage.update(row,next)).addOnCompleteListener(this,task->{
                if(!current(request))return;
                busy=false;
                if(!task.isSuccessful()){failure(task.getException(),true);return;}
                // Never invent review metadata: refresh it from the backend.
                page.rows.remove(ModerationPage.key(row));render();
                Toast.makeText(this,"Şikâyet durumu güncellendi.",Toast.LENGTH_SHORT).show();reload();
            });
        }catch(RuntimeException e){busy=false;failure(e,true);}
    }
    private void failure(Exception error,boolean updating) {
        if(error instanceof ModerationBackendException) {
            ModerationBackendException.Code code=((ModerationBackendException)error).getCode();
            if(code==ModerationBackendException.Code.PERMISSION_DENIED || code==ModerationBackendException.Code.UNAUTHENTICATED){deny();return;}
            if(code==ModerationBackendException.Code.UNAVAILABLE){
                controls();message.setText(ModerationBackendException.UNAVAILABLE_MESSAGE);retry.setVisibility(updating?View.GONE:View.VISIBLE);return;
            }
            if(updating && code==ModerationBackendException.Code.FAILED_PRECONDITION){
                Toast.makeText(this,"Kayıt değişmiş olabilir. Liste yenileniyor.",Toast.LENGTH_LONG).show();reload();return;
            }
        }
        controls();message.setText(updating?"Durum güncellenemedi. Listeyi yenileyip tekrar deneyin.":"Şikâyetler yüklenemedi. Tekrar deneyin.");
        retry.setVisibility(updating?View.GONE:View.VISIBLE);
    }
    private void showDialog(){
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawableResource(R.color.zd_bg);
    }
    private String value(Map<String,Object> row,String key){return ModerationPage.string(row,key);}
    private String label(String state){for(int i=0;i<ModerationPage.STATES.length;i++)if(ModerationPage.STATES[i].equals(state))return ModerationPage.LABELS[i];return state;}
    private String type(String type){switch(type){case "POST":return "Gönderi";case "COMMENT":return "Yorum";case "REPLY":return "Yanıt";case "USER":return "Kullanıcı";default:return "Diğer";}}
    private String reason(String code){switch(code){case "SPAM":return "Spam / reklam";case "ABUSE":return "Hakaret veya rahatsız edici içerik";case "MISLEADING":return "Yanlış / yanıltıcı bilgi";case "INAPPROPRIATE":return "Uygunsuz içerik";case "FRAUD":return "Dolandırıcılık şüphesi";default:return "Diğer";}}
    private String date(String iso){
        if(iso.isEmpty())return "Tarih belirtilmemiş";
        try{SimpleDateFormat source=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);source.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("tr","TR")).format(source.parse(iso));
        }catch(Exception e){return "Tarih okunamadı";}
    }
    private GradientDrawable background(){GradientDrawable bg=new GradientDrawable();bg.setColor(GreenTheme.color(R.color.zd_green_soft));bg.setCornerRadius(dp(16));bg.setStroke(dp(1),GreenTheme.color(R.color.zd_border));return bg;}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(GreenTheme.color(R.color.zd_text));v.setPadding(0,dp(4),0,dp(4));return v;}
    private TextView action(String s,Runnable run){TextView v=text(s,15);v.setMinHeight(dp(48));v.setGravity(Gravity.CENTER);v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(background());v.setOnClickListener(w->run.run());return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
