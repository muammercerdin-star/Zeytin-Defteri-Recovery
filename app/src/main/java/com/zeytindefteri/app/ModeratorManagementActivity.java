package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Super Admin UI only; 06.3C callables independently enforce live authority. */
public final class ModeratorManagementActivity extends Activity {
    private final ModeratorManagementState state=new ModeratorManagementState();
    private FirebaseAuth auth;
    private ModerationBackendClient functions;
    private FirebaseAuth.AuthStateListener authListener;
    private String owner;
    private boolean active,authorized,busy;
    private int generation;
    private LinearLayout content,result,list;
    private EditText search;
    private TextView lookupMessage,listMessage,more;
    private AlertDialog dialog;
    private static final String ERROR="İşlem tamamlanamadı. Tekrar deneyin.";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(getActionBar()!=null)getActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int bg=GreenTheme.color(R.color.zd_bg);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        auth=FirebaseAuth.getInstance();functions=ModerationBackendClient.getInstance();gate();
    }
    @Override protected void onStart(){
        super.onStart();active=true;authorized=false;gate();
        FirebaseUser user=auth.getCurrentUser();
        if(user==null||user.isAnonymous()){deny();return;}
        owner=user.getUid();
        authListener=value->{FirebaseUser current=value.getCurrentUser();if(current==null||!owner.equals(current.getUid()))deny();};
        auth.addAuthStateListener(authListener);
        final int request=++generation;
        user.getIdToken(true).addOnCompleteListener(this,task->{
            if(!current(request))return;
            if(!task.isSuccessful()){Toast.makeText(this,ERROR,Toast.LENGTH_LONG).show();finish();return;}
            if(!ModeratorManagementState.superAllowed(task.getResult().getClaims())){deny();return;}
            authorized=true;state.clear();build();loadList(false);
        });
    }
    @Override protected void onStop(){
        active=false;authorized=false;busy=false;generation++;
        if(authListener!=null){auth.removeAuthStateListener(authListener);authListener=null;}
        if(dialog!=null){dialog.dismiss();dialog=null;}
        state.clear();gate();super.onStop();
    }
    private boolean current(int request){
        FirebaseUser u=auth.getCurrentUser();return active&&!isFinishing()&&!isDestroyed()&&generation==request
                &&u!=null&&owner!=null&&owner.equals(u.getUid());
    }
    private boolean ready(){return authorized&&!busy&&current(generation);}
    private void gate(){TextView v=text("Yetki kontrol ediliyor…",16);v.setGravity(Gravity.CENTER);v.setBackgroundColor(GreenTheme.color(R.color.zd_bg));setContentView(v);}
    private void deny(){authorized=false;generation++;state.clear();if(dialog!=null)dialog.dismiss();gate();Toast.makeText(this,"Bu alan için Süper Admin yetkisi gerekiyor.",Toast.LENGTH_LONG).show();finish();}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(12),dp(18),dp(16));root.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        TextView back=button("←  Yetki Yönetimi",this::finish);back.setGravity(Gravity.CENTER_VERTICAL);back.setTextSize(21);root.addView(back);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=text("Moderatörleri ve yetkilerini yönetin.",14);intro.setPadding(0,dp(16),0,dp(12));content.addView(intro);
        LinearLayout searchCard=card();
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(GreenTheme.color(R.color.zd_text));search.setHintTextColor(GreenTheme.color(R.color.zd_text));
        search.setHint("E-posta veya Firebase UID");search.setContentDescription("E-posta veya Firebase UID");search.setMinHeight(dp(52));
        search.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setSaveEnabled(false);searchCard.addView(search,new LinearLayout.LayoutParams(-1,-2));
        add(searchCard,button("Kullanıcıyı Bul",this::lookup),12);
        lookupMessage=text("Tam e-posta adresi veya UID ile arayın.",13);add(searchCard,lookupMessage,8);add(content,searchCard,0);
        result=new LinearLayout(this);result.setOrientation(1);content.addView(result);
        TextView heading=text("Mevcut Moderatörler",19);add(content,heading,24);
        add(content,button("Listeyi Yenile",()->loadList(false)),8);
        listMessage=text("Moderatörler yükleniyor…",14);add(content,listMessage,10);
        list=new LinearLayout(this);list.setOrientation(1);content.addView(list);
        more=button("Daha fazla yükle",()->loadList(true));add(content,more,12);more.setVisibility(View.GONE);
        setContentView(root);
    }
    private interface Success {void accept(Object value);}
    private void call(String name,Map<String,Object> data,TextView message,String loading,Success success){
        if(!ready())return;busy=true;controls();message.setText(loading);final int request=generation;
        try{
            functions.operation(name).withTimeout(30,TimeUnit.SECONDS).call(data).addOnCompleteListener(this,task->{
                if(!current(request))return;busy=false;
                if(!task.isSuccessful()){failure(task.getException(),message);return;}
                try{success.accept(task.getResult().getData());}catch(RuntimeException e){failure(e,message);return;}
                controls();
            });
        }catch(RuntimeException e){busy=false;failure(e,message);}
    }
    private void lookup(){
        if(!ready())return;
        Map<String,Object> data;
        try{data=ModeratorManagementState.lookup(search.getText().toString());}
        catch(IllegalArgumentException e){lookupMessage.setText("Tam e-posta adresi veya UID girin.");return;}
        state.selected=null;result.removeAllViews();
        call("moderationLookupUser",data,lookupMessage,"Kullanıcı aranıyor…",raw->{state.acceptLookup(raw);lookupMessage.setText("Kullanıcı bulundu.");renderResult();});
    }
    private void loadList(boolean append){
        if(!ready()||(append&&state.cursor==null))return;
        if(!append){state.resetList();renderList();}
        call("moderationListModerators",state.listRequest(append),listMessage,"Moderatörler yükleniyor…",raw->{
            state.acceptList(raw);renderList();listMessage.setText(state.users.isEmpty()?(state.cursor==null?"Henüz moderatör bulunmuyor.":"Bu sayfada aktif rol kaydı bulunmuyor. Devamını yükleyebilirsiniz."):"Moderatörler güncellendi.");
        });
    }
    private void renderResult(){result.removeAllViews();if(state.selected!=null)add(result,userCard(state.selected,false),16);}
    private void renderList(){list.removeAllViews();for(ModeratorManagementState.User u:state.users.values())add(list,userCard(u,true),12);}
    private LinearLayout userCard(ModeratorManagementState.User u,boolean compact){
        LinearLayout card=card();card.addView(text(u.name.isEmpty()?"İsim belirtilmemiş":u.name,17));
        TextView email=text(u.email.isEmpty()?"E-posta belirtilmemiş":u.email,14);email.setTextIsSelectable(true);card.addView(email);
        String shortUid=compact&&u.uid.length()>18?u.uid.substring(0,9)+"…"+u.uid.substring(u.uid.length()-6):u.uid;
        TextView identity=text("UID: "+shortUid,12);identity.setTextIsSelectable(true);card.addView(identity);
        card.addView(text(u.role()+" • "+(u.disabled?"Hesap devre dışı":"Hesap aktif"),14));
        if(u.canChange()){
            if(u.moderator)add(card,button(compact?"Yetkiyi Kaldır":"Moderatör Yetkisini Kaldır",()->confirm(u,false)),12);
            else if(!u.disabled)add(card,button("Moderatör Yap",()->confirm(u,true)),12);
            else add(card,text("Devre dışı hesaba yetki verilemez.",13),8);
        }
        return card;
    }
    private void confirm(ModeratorManagementState.User u,boolean grant){
        if(!ready()||!u.canChange())return;
        dialog=new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(grant?"Moderatör Yap":"Yetkiyi Kaldır")
                .setMessage((grant?"Bu kullanıcıya moderatör yetkisi verilsin mi?":"Bu kullanıcının moderatör yetkisi kaldırılsın mı?")+"\n\n"+(u.name.isEmpty()?u.uid:u.name)+"\n"+u.email+"\nUID: "+u.uid)
                .setNegativeButton("Vazgeç",null).setPositiveButton(grant?"Yetki Ver":"Yetkiyi Kaldır",(d,w)->change(u,grant)).create();
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawableResource(R.color.zd_bg);
    }
    private void change(ModeratorManagementState.User u,boolean grant){
        if(!ready()||!u.canChange())return;
        call(grant?"moderationGrantModerator":"moderationRevokeModerator",ModeratorManagementState.target(u),lookupMessage,"Yetki güncelleniyor…",raw->{
            state.acceptChange(u,grant,raw);renderResult();renderList();
            String message=grant?"Moderatör yetkisi verildi.":"Moderatör yetkisi kaldırıldı.";
            lookupMessage.setText(message);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
            // Refresh ordering/cursor after the authoritative mutation response.
            loadList(false);
        });
    }
    private void failure(Exception e,TextView message){
        if(e instanceof ModerationBackendException){
            ModerationBackendException.Code code=((ModerationBackendException)e).getCode();
            if(code==ModerationBackendException.Code.PERMISSION_DENIED||code==ModerationBackendException.Code.UNAUTHENTICATED){deny();return;}
            if(code==ModerationBackendException.Code.UNAVAILABLE){message.setText(ModerationBackendException.UNAVAILABLE_MESSAGE);controls();return;}
            if(code==ModerationBackendException.Code.NOT_FOUND){message.setText("Kullanıcı bulunamadı.");controls();return;}
            if(code==ModerationBackendException.Code.FAILED_PRECONDITION){
                state.selected=null;result.removeAllViews();state.resetList();renderList();
            }
        }
        message.setText(ERROR);controls();
    }
    private void controls(){enable(content,!busy);more.setVisibility(!busy&&state.cursor!=null?View.VISIBLE:View.GONE);}
    private void enable(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enable(group.getChildAt(i),enabled);}}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setPadding(dp(16),dp(14),dp(16),dp(14));v.setBackground(background());return v;}
    private GradientDrawable background(){GradientDrawable d=new GradientDrawable();d.setColor(GreenTheme.color(R.color.zd_green_soft));d.setCornerRadius(dp(16));d.setStroke(dp(1),GreenTheme.color(R.color.zd_border));return d;}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(GreenTheme.color(R.color.zd_text));v.setPadding(0,dp(4),0,dp(4));return v;}
    private TextView button(String value,Runnable action){TextView v=text(value,15);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(background());v.setOnClickListener(w->action.run());return v;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
