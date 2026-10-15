package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Moderatöre özel şikâyet ekranı (tuval: "Moderasyon Merkezi · öneri"). Liste ve şikâyet detayı aynı
 * ekranda iki görünüm; kaldırma onayı alttan açılan pencere. Bütün okuma ve değişiklikler
 * moderasyon sunucusu üzerinden yapılır; bu ekran Firestore'a doğrudan erişmez.
 */
public final class ModerationActivity extends Activity {
    private final ModerationPage page = new ModerationPage();
    private FirebaseAuth auth;
    private ModerationBackendClient functions;
    private FirebaseAuth.AuthStateListener authListener;
    private String owner;
    private boolean active, authorized, busy;
    private int generation;
    private LinearLayout list, filters;
    private TextView message, more, retry;
    private ImageView refresh;
    private View listView;
    private AlertDialog dialog;
    private Dialog sheet;
    // Detay görünümü
    private Map<String,Object> detailRow;
    private Object detailBack;
    private TextView detailBadge, detailAuthor, detailWhen, detailBody;
    private TextView removeButton, rejectButton, changeButton;

    private int bg, surface, card, border, text, soft, muted, green, greenSoft, warning, warningSoft, danger;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        bg = GreenTheme.color(R.color.zd_bg); surface = GreenTheme.color(R.color.zd_surface);
        card = GreenTheme.color(R.color.zd_card); border = GreenTheme.color(R.color.zd_border);
        text = GreenTheme.color(R.color.zd_text); soft = GreenTheme.color(R.color.zd_text_soft);
        muted = GreenTheme.color(R.color.zd_muted); green = GreenTheme.color(R.color.zd_green);
        greenSoft = GreenTheme.color(R.color.zd_green_soft); warning = GreenTheme.color(R.color.zd_warning);
        warningSoft = GreenTheme.color(R.color.zd_warning_soft); danger = GreenTheme.color(R.color.zd_danger);
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
        dismissDialogs(); closeDetailBack(); detailRow = null;
        page.reset(page.status); showGate();
        super.onStop();
    }
    /** Android 12 ve altı; 13+ cihazlarda geri tuşu BackCompat ile yakalanır. */
    @Override public void onBackPressed() {
        if (detailRow != null) { showList(); return; }
        super.onBackPressed();
    }
    private boolean current(int request) {
        FirebaseUser user = auth.getCurrentUser();
        return active && !isFinishing() && !isDestroyed() && generation == request
                && user != null && owner != null && owner.equals(user.getUid());
    }
    private void deny() {
        authorized = false; generation++; page.reset(page.status);
        dismissDialogs(); closeDetailBack(); detailRow = null;
        showGate(); Toast.makeText(this,"Bu alan için moderatör yetkisi gerekiyor.",Toast.LENGTH_LONG).show(); finish();
    }
    private void dismissDialogs() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (sheet != null) { sheet.dismiss(); sheet = null; }
    }
    private void showGate() {
        TextView gate = text("Yetki kontrol ediliyor…",16,muted,false);
        gate.setGravity(Gravity.CENTER); gate.setBackgroundColor(bg);
        setContentView(gate);
    }

    // ============================================================ LİSTE

    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(bg);
        root.addView(header("Moderasyon Merkezi","Topluluk şikâyetleri · yalnızca moderatörler",this::finish,true));
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false); horizontal.setPadding(dp(16),dp(4),dp(16),dp(8)); horizontal.setClipToPadding(false);
        filters = row(); horizontal.addView(filters);
        for (int i=0;i<ModerationPage.STATES.length;i++) {
            final String state = ModerationPage.STATES[i];
            TextView filter = text(ModerationPage.LABELS[i],13,soft,true);
            filter.setGravity(Gravity.CENTER); filter.setMinHeight(dp(38)); filter.setPadding(dp(14),0,dp(14),0);
            filter.setOnClickListener(v -> { if (!busy && !state.equals(page.status)) { page.reset(state); reload(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(8);
            filters.addView(filter,lp);
        }
        root.addView(horizontal);
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(); content.setPadding(dp(16),0,dp(16),dp(24)); scroll.addView(content);
        message = text("Şikâyetler yükleniyor…",13,muted,false); message.setPadding(dp(2),dp(4),0,dp(4)); content.addView(message);
        retry = action("Yeniden dene",()->load(page.rows.size()>0));content.addView(retry,margins(-1,dp(48),0,dp(8)));
        list = column();content.addView(list);
        more = text("Daha fazla yükle",13,green,true);more.setGravity(Gravity.CENTER);more.setMinHeight(dp(48));
        more.setOnClickListener(v->load(true));content.addView(more);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        listView = root; setContentView(root);
    }
    private void showList() {
        closeDetailBack(); detailRow = null;
        if (listView != null) setContentView(listView);
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
        if (refresh != null) { refresh.setEnabled(!busy); refresh.setAlpha(busy?0.5f:1f); }
        for(int i=0;filters!=null&&i<filters.getChildCount();i++) {
            TextView filter=(TextView)filters.getChildAt(i);filter.setEnabled(!busy);
            boolean on=ModerationPage.STATES[i].equals(page.status);
            filter.setTextColor(on?green:soft);filter.setBackground(round(on?greenSoft:surface,on?green:border,19));
        }
        if (more != null) more.setVisibility(!busy && page.cursor!=null?View.VISIBLE:View.GONE);
        if (retry != null) retry.setEnabled(!busy);
        for (TextView b : new TextView[]{removeButton,rejectButton,changeButton}) if (b != null) { b.setEnabled(!busy); b.setAlpha(busy?0.5f:1f); }
    }
    private void render() {
        list.removeAllViews();
        for(Map<String,Object> row:page.rows.values()) list.addView(reportCard(row),margins(-1,-2,0,dp(10)));
        if (page.rows.isEmpty()) {
            LinearLayout empty=column();empty.setGravity(Gravity.CENTER_HORIZONTAL);empty.setPadding(dp(16),dp(22),dp(16),dp(22));
            empty.setBackground(dashed());
            ImageView check=icon(R.drawable.ic_op_check,green,26);empty.addView(check,new LinearLayout.LayoutParams(dp(26),dp(26)));
            TextView title=text(label(page.status)+" şikâyet yok",14,text,true);title.setPadding(0,dp(8),0,dp(2));empty.addView(title);
            TextView sub=text("OPEN".equals(page.status)?"Yeni şikâyetler burada en üstte görünür.":"Bu durumda kayıt bulunmuyor.",12,muted,false);
            sub.setGravity(Gravity.CENTER);empty.addView(sub);
            list.addView(empty,margins(-1,-2,0,0));
        }
        message.setText(page.rows.isEmpty()?"":label(page.status).toUpperCase(new Locale("tr","TR"))+" ŞİKÂYETLER · EN YENİ ÖNCE");
        message.setVisibility(page.rows.isEmpty()?View.GONE:View.VISIBLE);
        retry.setVisibility(View.GONE);controls();
    }
    private View reportCard(Map<String,Object> row) {
        LinearLayout card=row();card.setPadding(dp(14),dp(14),dp(10),dp(14));card.setMinimumHeight(dp(72));
        card.setBackground(round(this.card,border,18));
        card.addView(disc(typeIcon(value(row,"target_type")),warning,warningSoft,40));
        LinearLayout texts=column();texts.setPadding(dp(12),0,dp(8),0);
        TextView title=text(reason(value(row,"reason")),14,text,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);texts.addView(title);
        TextView meta=text(type(value(row,"target_type"))+" · "+date(value(row,"created_at")),12,muted,false);meta.setPadding(0,dp(3),0,0);texts.addView(meta);
        card.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(statusBadge(value(row,"status")));
        ImageView chev=icon(R.drawable.ic_op_chevron,muted,16);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(16),dp(16));cp.leftMargin=dp(6);card.addView(chev,cp);
        card.setContentDescription(reason(value(row,"reason"))+", "+type(value(row,"target_type"))+", "+label(value(row,"status")));
        card.setOnClickListener(v->{if(!busy && authorized)details(row);});
        return card;
    }

    // ============================================================ DETAY

    private void details(Map<String,Object> row) {
        if(!authorized || busy || !current(generation))return;
        detailRow=row;
        final boolean hasContent=ModerationPage.hasContent(row);
        LinearLayout root=column();root.setBackgroundColor(bg);
        root.addView(header("Şikâyet detayı",reason(value(row,"reason")),this::showList,false));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=column();body.setPadding(dp(16),dp(4),dp(16),dp(24));scroll.addView(body);

        detailBadge=null;detailAuthor=null;detailWhen=null;detailBody=null;
        if(hasContent){
            LinearLayout content=column();content.setPadding(dp(16),dp(16),dp(16),dp(16));content.setBackground(round(card,border,20));
            LinearLayout top=row();
            top.addView(overline("POST".equals(value(row,"target_type"))?"ŞİKÂYET EDİLEN GÖNDERİ":"ŞİKÂYET EDİLEN YANIT"),new LinearLayout.LayoutParams(0,-2,1));
            detailBadge=badge("YÜKLENİYOR",muted,surface);top.addView(detailBadge);content.addView(top);
            LinearLayout who=row();who.setPadding(0,dp(12),0,dp(10));
            LinearLayout names=column();
            detailAuthor=text("…",14,text,true);names.addView(detailAuthor);
            detailWhen=text("",12,muted,false);names.addView(detailWhen);
            who.addView(names,new LinearLayout.LayoutParams(0,-2,1));content.addView(who);
            detailBody=text("İçerik yükleniyor…",15,text,false);detailBody.setLineSpacing(dp(2),1f);detailBody.setTextIsSelectable(true);
            detailBody.setPadding(dp(14),dp(12),dp(14),dp(12));detailBody.setBackground(quote());content.addView(detailBody);
            body.addView(content,margins(-1,-2,0,dp(12)));
        }

        LinearLayout info=column();info.setPadding(dp(16),dp(16),dp(16),dp(16));info.setBackground(round(card,border,20));
        info.addView(overline("ŞİKÂYET"));
        info.addView(pair(metric("Neden",shortReason(value(row,"reason")),text),metric("Durum",label(value(row,"status")),statusColor(value(row,"status")))),margins(-1,-2,dp(10),0));
        info.addView(pair(metric("Tür",type(value(row,"target_type")),text),metric("Tarih",date(value(row,"created_at")),text)),margins(-1,-2,dp(12),0));
        body.addView(info,margins(-1,-2,0,dp(12)));

        // Kimlikler önde değil: dokununca açılır, kopyalanabilir.
        LinearLayout tech=column();tech.setBackground(round(card,border,18));
        LinearLayout techHead=row();techHead.setPadding(dp(16),dp(14),dp(14),dp(14));
        techHead.addView(icon(R.drawable.ic_op_code,muted,18),new LinearLayout.LayoutParams(dp(18),dp(18)));
        LinearLayout tt=column();tt.setPadding(dp(12),0,dp(8),0);
        tt.addView(text("Teknik bilgiler",14,text,true));tt.addView(text("Şikâyet eden / edilen kimlikleri, gönderi kimliği",12,muted,false));
        techHead.addView(tt,new LinearLayout.LayoutParams(0,-2,1));
        final ImageView arrow=icon(R.drawable.ic_op_down,muted,18);techHead.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(18)));
        final TextView ids=text("Şikâyet eden: "+value(row,"reporter_uid")+"\nŞikâyet edilen: "+value(row,"reported_uid")
                +"\nHedef: "+value(row,"target_id")
                +(value(row,"post_cloud_id").isEmpty()?"":"\nGönderi: "+value(row,"post_cloud_id"))
                +(value(row,"reviewed_by").isEmpty()?"":"\nİnceleyen: "+value(row,"reviewed_by"))
                +(value(row,"reviewed_at").isEmpty()?"":"\nİncelenme: "+date(value(row,"reviewed_at"))),12,soft,false);
        ids.setTypeface(Typeface.MONOSPACE);ids.setTextIsSelectable(true);ids.setPadding(dp(16),0,dp(16),dp(14));ids.setVisibility(View.GONE);
        techHead.setOnClickListener(v->{boolean open=ids.getVisibility()!=View.VISIBLE;ids.setVisibility(open?View.VISIBLE:View.GONE);arrow.setRotation(open?180f:0f);});
        tech.addView(techHead);tech.addView(ids);
        body.addView(tech);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=column();footer.setPadding(dp(16),dp(10),dp(16),dp(16));footer.setBackgroundColor(bg);
        removeButton=null;
        if(hasContent){
            removeButton=text("İçeriği kaldır",16,bg,true);removeButton.setGravity(Gravity.CENTER);
            removeButton.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.ic_op_eye_off,bg),null,null,null);
            removeButton.setCompoundDrawablePadding(dp(8));removeButton.setBackground(round(danger,danger,18));
            removeButton.setOnClickListener(v->confirmRemove(row));
            // İçerik okunana ve hâlâ yayında olduğu görülene kadar kaldırma kapalı: moderatör görmediği metni kaldırmasın.
            removeButton.setEnabled(false);removeButton.setAlpha(0.5f);
            footer.addView(removeButton,margins(-1,dp(54),0,dp(10)));
        }
        LinearLayout second=row();
        rejectButton=secondary("Şikâyeti reddet",()->update(row,"REJECTED"));
        changeButton=secondary("Durumu değiştir",()->choose(row));
        if(!"REJECTED".equals(value(row,"status"))){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.rightMargin=dp(10);second.addView(rejectButton,lp);
        } else rejectButton=null;
        second.addView(changeButton,new LinearLayout.LayoutParams(0,dp(48),1));
        footer.addView(second);
        root.addView(footer);
        setContentView(root);
        closeDetailBack();detailBack=BackCompat.register(this,this::showList);
        if(hasContent)loadContent(row);
    }
    private void loadContent(Map<String,Object> row) {
        final int request=generation;
        try{
            functions.operation("moderationGetReportedContent").withTimeout(30,TimeUnit.SECONDS)
                    .call(ModerationPage.content(row)).addOnCompleteListener(this,task->{
                // Kullanıcı bu arada listeye döndüyse ya da başka şikâyet açtıysa geç yanıt yazılmaz.
                if(!current(request)||detailRow!=row||detailBody==null)return;
                if(!task.isSuccessful()){
                    Exception error=task.getException();
                    if(error instanceof ModerationBackendException){
                        ModerationBackendException.Code code=((ModerationBackendException)error).getCode();
                        if(code==ModerationBackendException.Code.PERMISSION_DENIED||code==ModerationBackendException.Code.UNAUTHENTICATED){deny();return;}
                        if(code==ModerationBackendException.Code.UNAVAILABLE){detailBody.setText(ModerationBackendException.UNAVAILABLE_MESSAGE);setBadge("OKUNAMADI",muted,surface);return;}
                    }
                    detailBody.setText("İçerik okunamadı. Geri dönüp şikâyeti yeniden açın.");setBadge("OKUNAMADI",muted,surface);return;
                }
                ModerationPage.Reported data=ModerationPage.reported(task.getResult().getData());
                detailBody.setText(data.body);
                detailAuthor.setText(data.author.isEmpty()?"—":data.author);
                detailWhen.setText(data.createdAt>0?new SimpleDateFormat("d MMM HH:mm",new Locale("tr","TR")).format(new Date(data.createdAt)):"");
                if(data.state==ModerationPage.Reported.LIVE)setBadge(data.badge(),green,greenSoft);
                else if(data.state==ModerationPage.Reported.MODERATED)setBadge(data.badge(),danger,0xFF3A2228);
                else setBadge(data.badge(),muted,surface);
                if(removeButton!=null){
                    removeButton.setEnabled(data.removable());
                    removeButton.setAlpha(data.removable()?1f:0.5f);
                    removeButton.setVisibility(data.removable()?View.VISIBLE:View.GONE);
                }
            });
        }catch(RuntimeException e){detailBody.setText("İçerik okunamadı. Geri dönüp şikâyeti yeniden açın.");}
    }
    private void setBadge(String label,int fg,int back){
        if(detailBadge==null)return;detailBadge.setText(label);detailBadge.setTextColor(fg);detailBadge.setBackground(round(back,back,10));
    }
    private void closeDetailBack(){BackCompat.unregister(this,detailBack);detailBack=null;}

    // ============================================================ KALDIRMA

    private void confirmRemove(Map<String,Object> row) {
        if(!authorized || busy || !current(generation))return;
        boolean post="POST".equals(value(row,"target_type"));
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column();box.setPadding(dp(20),dp(10),dp(20),dp(24));
        GradientDrawable top=new GradientDrawable();top.setColor(card);float r=dp(26);top.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});box.setBackground(top);
        View handle=new View(this);handle.setBackground(round(border,border,2));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(14);box.addView(handle,hp);
        box.addView(disc(R.drawable.ic_op_eye_off,danger,0xFF3A2228,52),new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView title=text(post?"Gönderi kaldırılsın mı?":"Yanıt kaldırılsın mı?",20,text,true);title.setPadding(0,dp(14),0,dp(12));box.addView(title);
        box.addView(bullet(post?"Sohbet’te herkesten gizlenir; yanıtlarıyla birlikte görünmez olur."
                :"Yalnızca bu yanıt gizlenir; ana gönderi yerinde kalır.",danger),margins(-1,-2,0,dp(8)));
        box.addView(bullet("Yazan kişi geri açamaz ve düzenleyemez.",danger),margins(-1,-2,0,dp(8)));
        box.addView(bullet("Metin silinmez, kanıt olarak saklanır. Kimin, ne zaman kaldırdığı kaydedilir.",green),margins(-1,-2,0,dp(8)));
        box.addView(bullet("Şikâyet “İşlem Yapıldı” olarak kapanır.",green),margins(-1,-2,0,dp(16)));
        TextView ok=text("Kaldır",16,bg,true);ok.setGravity(Gravity.CENTER);ok.setBackground(round(danger,danger,18));
        ok.setOnClickListener(v->{d.dismiss();remove(row);});box.addView(ok,margins(-1,dp(54),0,dp(10)));
        TextView cancel=text("Vazgeç",15,text,true);cancel.setGravity(Gravity.CENTER);cancel.setBackground(round(bg,border,16));
        cancel.setOnClickListener(v->d.dismiss());box.addView(cancel,margins(-1,dp(48),0,0));
        d.setContentView(box);
        Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
        sheet=d;d.show();
    }
    private void remove(Map<String,Object> row) {
        if(!authorized || busy || !current(generation))return;
        busy=true;controls();
        final int request=generation;
        try {
            functions.operation("moderationRemoveContent").withTimeout(30,TimeUnit.SECONDS)
                    .call(ModerationPage.remove(row)).addOnCompleteListener(this,task->{
                if(!current(request))return;
                busy=false;
                if(!task.isSuccessful()){
                    Exception error=task.getException();
                    if(error instanceof ModerationBackendException
                            &&((ModerationBackendException)error).getCode()==ModerationBackendException.Code.NOT_FOUND){
                        controls();Toast.makeText(this,"İçerik bulunamadı; yazan kişi kalıcı olarak silmiş olabilir. Şikâyetin durumunu elle değiştirebilirsiniz.",Toast.LENGTH_LONG).show();return;
                    }
                    failure(error,true);return;
                }
                Object data=task.getResult().getData();
                boolean removed=data instanceof Map&&Boolean.TRUE.equals(((Map<?,?>)data).get("removed"));
                page.rows.remove(ModerationPage.key(row));showList();render();
                Toast.makeText(this,removed?"İçerik kaldırıldı. Şikâyet kapatıldı.":"İçerik zaten yayında değildi. Şikâyet kapatıldı.",Toast.LENGTH_LONG).show();reload();
            });
        }catch(RuntimeException e){busy=false;failure(e,true);}
    }

    // ============================================================ DURUM

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
        busy=true;controls();
        final int request=generation;
        try {
            functions.operation("moderationUpdateReportStatus").withTimeout(30,TimeUnit.SECONDS)
                    .call(ModerationPage.update(row,next)).addOnCompleteListener(this,task->{
                if(!current(request))return;
                busy=false;
                if(!task.isSuccessful()){failure(task.getException(),true);return;}
                // İnceleme bilgisi uydurulmaz: listeyi sunucudan yeniden al.
                page.rows.remove(ModerationPage.key(row));showList();render();
                Toast.makeText(this,"REJECTED".equals(next)?"Şikâyet reddedildi.":"Şikâyet durumu güncellendi.",Toast.LENGTH_SHORT).show();reload();
            });
        }catch(RuntimeException e){busy=false;failure(e,true);}
    }
    private void failure(Exception error,boolean updating) {
        if(error instanceof ModerationBackendException) {
            ModerationBackendException.Code code=((ModerationBackendException)error).getCode();
            if(code==ModerationBackendException.Code.PERMISSION_DENIED || code==ModerationBackendException.Code.UNAUTHENTICATED){deny();return;}
            if(code==ModerationBackendException.Code.UNAVAILABLE){
                controls();
                if(updating||detailRow!=null)Toast.makeText(this,ModerationBackendException.UNAVAILABLE_MESSAGE,Toast.LENGTH_LONG).show();
                else{message.setVisibility(View.VISIBLE);message.setText(ModerationBackendException.UNAVAILABLE_MESSAGE);retry.setVisibility(View.VISIBLE);}
                return;
            }
            if(updating && code==ModerationBackendException.Code.FAILED_PRECONDITION){
                Toast.makeText(this,"Kayıt değişmiş olabilir. Liste yenileniyor.",Toast.LENGTH_LONG).show();showList();reload();return;
            }
        }
        controls();
        if(updating){Toast.makeText(this,"İşlem tamamlanamadı. Listeyi yenileyip tekrar deneyin.",Toast.LENGTH_LONG).show();return;}
        message.setVisibility(View.VISIBLE);message.setText("Şikâyetler yüklenemedi. Tekrar deneyin.");
        retry.setVisibility(View.VISIBLE);
    }
    private void showDialog(){
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawableResource(R.color.zd_bg);
    }

    // ============================================================ METİNLER

    private String value(Map<String,Object> row,String key){return ModerationPage.string(row,key);}
    private String label(String state){for(int i=0;i<ModerationPage.STATES.length;i++)if(ModerationPage.STATES[i].equals(state))return ModerationPage.LABELS[i];return state;}
    private String type(String type){switch(type){case "POST":return "Gönderi";case "COMMENT":return "Yorum";case "REPLY":return "Yanıt";case "USER":return "Kullanıcı";default:return "Diğer";}}
    private int typeIcon(String type){switch(type){case "POST":return R.drawable.ic_op_chat;case "COMMENT":case "REPLY":return R.drawable.ic_op_reply;case "USER":return R.drawable.ic_op_user;default:return R.drawable.ic_op_flag;}}
    private String reason(String code){switch(code){case "SPAM":return "Spam / reklam";case "ABUSE":return "Hakaret veya rahatsız edici içerik";case "MISLEADING":return "Yanlış / yanıltıcı bilgi";case "INAPPROPRIATE":return "Uygunsuz içerik";case "FRAUD":return "Dolandırıcılık şüphesi";default:return "Diğer";}}
    private String shortReason(String code){switch(code){case "SPAM":return "Spam";case "ABUSE":return "Hakaret";case "MISLEADING":return "Yanıltıcı";case "INAPPROPRIATE":return "Uygunsuz";case "FRAUD":return "Dolandırıcılık";default:return "Diğer";}}
    private int statusColor(String state){switch(state){case "OPEN":return warning;case "ACTIONED":return green;default:return muted;}}
    private String date(String iso){
        if(iso.isEmpty())return "Tarih yok";
        try{SimpleDateFormat source=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);source.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new SimpleDateFormat("d MMM HH:mm",new Locale("tr","TR")).format(source.parse(iso));
        }catch(Exception e){return "Tarih okunamadı";}
    }

    // ============================================================ GÖRÜNÜM PARÇALARI

    private View header(String title,String sub,Runnable back,boolean withRefresh){
        LinearLayout h=row();h.setPadding(dp(8),dp(16),dp(16),dp(8));
        ImageView b=icon(R.drawable.ic_elek_back_chevron,text,22);b.setPadding(dp(11),dp(11),dp(11),dp(11));
        b.setContentDescription("Geri");b.setOnClickListener(v->back.run());h.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout t=column();t.setPadding(dp(4),0,dp(8),0);
        TextView tt=text(title,22,text,true);tt.setMaxLines(1);tt.setEllipsize(TextUtils.TruncateAt.END);t.addView(tt);
        TextView ts=text(sub,12,muted,false);ts.setMaxLines(1);ts.setEllipsize(TextUtils.TruncateAt.END);t.addView(ts);
        h.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        if(withRefresh){
            refresh=icon(R.drawable.ic_op_refresh,soft,20);refresh.setPadding(dp(12),dp(12),dp(12),dp(12));
            refresh.setBackground(round(surface,border,22));refresh.setContentDescription("Yenile");refresh.setOnClickListener(v->reload());
            h.addView(refresh,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }
        return h;
    }
    private TextView statusBadge(String state){
        switch(state){
            case "OPEN":return badge("Açık",warning,warningSoft);
            case "ACTIONED":return badge("İşlem yapıldı",green,greenSoft);
            case "REVIEWED":return badge("İncelendi",soft,surface);
            default:return badge("Reddedildi",muted,surface);
        }
    }
    private TextView badge(String s,int fg,int back){
        TextView v=text(s,11,fg,true);v.setPadding(dp(10),dp(3),dp(10),dp(3));v.setBackground(round(back,back,10));v.setSingleLine(true);return v;
    }
    private TextView overline(String s){TextView v=text(s,11,muted,true);v.setLetterSpacing(0.08f);return v;}
    private LinearLayout metric(String labelText,String valueText,int color){
        LinearLayout m=column();m.addView(text(labelText,12,muted,false));
        TextView v=text(valueText,17,color,true);v.setPadding(0,dp(2),0,0);m.addView(v);return m;
    }
    private LinearLayout pair(View a,View b){LinearLayout p=row();p.setGravity(Gravity.TOP);p.addView(a,new LinearLayout.LayoutParams(0,-2,1));p.addView(b,new LinearLayout.LayoutParams(0,-2,1));return p;}
    private LinearLayout bullet(String s,int dot){
        LinearLayout b=row();b.setGravity(Gravity.TOP);b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(round(surface,surface,12));
        View d=new View(this);d.setBackground(round(dot,dot,4));LinearLayout.LayoutParams dp8=new LinearLayout.LayoutParams(dp(8),dp(8));dp8.topMargin=dp(5);dp8.rightMargin=dp(10);b.addView(d,dp8);
        b.addView(text(s,13,soft,false),new LinearLayout.LayoutParams(0,-2,1));return b;
    }
    private TextView secondary(String s,Runnable run){
        TextView v=text(s,14,text,true);v.setGravity(Gravity.CENTER);v.setBackground(round(surface,border,16));v.setOnClickListener(w->run.run());return v;
    }
    private TextView action(String s,Runnable run){
        TextView v=text(s,14,green,true);v.setGravity(Gravity.CENTER);v.setBackground(round(surface,border,16));v.setOnClickListener(w->run.run());return v;
    }
    private LinearLayout disc(int res,int color,int back,int size){
        LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER);d.setBackground(round(back,back,size/2));
        d.addView(icon(res,color,size/2),new LinearLayout.LayoutParams(dp(size/2),dp(size/2)));
        d.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return d;
    }
    private ImageView icon(int res,int color,int size){ImageView v=new ImageView(this);v.setImageResource(res);v.setColorFilter(color);return v;}
    private android.graphics.drawable.Drawable tinted(int res,int color){
        android.graphics.drawable.Drawable d=getDrawable(res);if(d==null)return null;d=d.mutate();d.setTint(color);d.setBounds(0,0,dp(20),dp(20));return d;
    }
    private GradientDrawable round(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private GradientDrawable dashed(){GradientDrawable g=new GradientDrawable();g.setColor(bg);g.setCornerRadius(dp(18));g.setStroke(dp(1),border,dp(6),dp(4));return g;}
    private GradientDrawable quote(){GradientDrawable g=new GradientDrawable();g.setColor(surface);g.setCornerRadius(dp(14));g.setStroke(dp(1),border);return g;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams margins(int w,int h,int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h);lp.topMargin=top;lp.bottomMargin=bottom;return lp;}
    private TextView text(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
