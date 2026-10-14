package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public final class RemindersActivity extends Activity {
    private LinearLayout list;
    private String owner;
    private int generation;
    private long openId=-1;
    private String openOwner;
    private final ArrayList<Integer> gardenIds=new ArrayList<>();
    private final ArrayList<String> gardenNames=new ArrayList<>();
    private boolean loaded;
    private static final int GREEN=GreenTheme.color(R.color.zd_green),MUTED=GreenTheme.color(R.color.zd_muted);
    private static final String[] RULES={"NONE","DAILY","WEEKLY","MONTHLY"};
    static final class Row {
        long id,due;Integer garden;boolean done;String title,note,type,repeat,gardenName;
        Row(Cursor c){id=LocalReminderScheduler.number(c,"id");due=LocalReminderScheduler.number(c,"due_at");
            long g=LocalReminderScheduler.number(c,"garden_id");garden=g>0?(int)g:null;done=LocalReminderScheduler.number(c,"is_done")!=0;
            title=LocalReminderScheduler.text(c,"title");note=LocalReminderScheduler.text(c,"note");type=LocalReminderScheduler.text(c,"reminder_type");
            repeat=LocalReminderScheduler.text(c,"repeat_rule");gardenName=LocalReminderScheduler.text(c,"garden_name");}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);owner=new SessionManager(this).getUserUid();
        if(!new SessionManager(this).hasSession()){finish();return;}
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        LinearLayout root=column();root.setBackgroundColor(GreenTheme.color(R.color.zd_bg));root.setPadding(dp(18),dp(16),dp(18),0);setContentView(root);
        TextView back=label("‹  Hatırlatmalar",24,true);root.addView(back);back.setOnClickListener(v->finish());
        TextView hint=label("İnternet gerekmez. Android güç tasarrufu bildirimi geciktirebilir. Hatırlatmalar seçili rapor sezonundan bağımsızdır.",13,false);root.addView(hint);
        root.addView(button("+ Yeni hatırlatma",()->{if(loaded)edit(null);}));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));list=column();scroll.addView(list);
        readIntent(getIntent());
    }
    private void readIntent(Intent i){openId=i.getLongExtra("reminder_id",-1);openOwner=i.getStringExtra("reminder_owner");}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);readIntent(i);load();}
    @Override protected void onResume(){super.onResume();load();LocalReminderScheduler.requestReschedule(this);}
    private boolean current(){SessionManager s=new SessionManager(this);return !isFinishing()&&!isDestroyed()&&s.hasSession()&&owner.equals(s.getUserUid());}
    private void load() {
        if(!current()){finish();return;}loaded=false;final int token=++generation;list.removeAllViews();list.addView(label("Yükleniyor…",16,false));
        LocalReminderScheduler.WORKER.execute(()->{
            ArrayList<Row> rows=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
            boolean success=false;
            synchronized(LocalReminderScheduler.LOCK){try(DatabaseHelper db=new DatabaseHelper(getApplicationContext())) {
                if(owner.equals(db.getActiveOwnerUid())) {
                    ReminderRepository r=new ReminderRepository(db);ids.add(null);names.add("Genel hatırlatma");
                    try(Cursor c=r.getOwnedGardens()){while(c.moveToNext()){ids.add(c.getInt(0));names.add(c.getString(1));}}
                    try(Cursor c=r.getAllReminders()){while(c.moveToNext())rows.add(new Row(c));}success=true;
                }
            }catch(RuntimeException e){android.util.Log.w("Reminders","Liste okunamadı",e);}}
            final boolean ok=success;runOnUiThread(()->{if(!current()||token!=generation)return;
                loaded=ok;gardenIds.clear();gardenIds.addAll(ids);gardenNames.clear();gardenNames.addAll(names);list.removeAllViews();
                LocalReminderScheduler s=new LocalReminderScheduler(this);s.createChannel();if(!s.enabled())list.addView(button("Bildirimler kapalı • İzni kontrol et",this::permission));
                if(!ok){list.addView(label("Hatırlatmalar okunamadı. Tekrar açmayı deneyin.",16,false));return;}
                if(rows.isEmpty())list.addView(label("Henüz hatırlatma yok. İlk hatırlatmanı ekleyebilirsin.",16,false));
                String[] groups={"Geçmiş • Bekleyen","Bugün","Yaklaşan","Tamamlanan"};
                for(int group=0;group<4;group++){boolean heading=false;for(Row row:rows)if(group(row)==group){if(!heading){list.addView(label(groups[group],19,true));heading=true;}list.addView(card(row));}}
                if(openId>0){long id=openId;openId=-1;if(owner.equals(openOwner)){for(Row row:rows)if(row.id==id){detail(row);return;}}toast("Hatırlatma bu oturumda bulunamadı.");}
            });
        });
    }
    private int group(Row r){if(r.done)return 3;Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        long today=start.getTimeInMillis();start.add(Calendar.DATE,1);return r.due<today?0:r.due<start.getTimeInMillis()?1:2;}
    private View card(Row r){LinearLayout box=column();box.setPadding(dp(14),dp(12),dp(14),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(GreenTheme.color(R.color.zd_card));bg.setCornerRadius(dp(14));box.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));box.setLayoutParams(p);
        box.addView(label(r.title,18,true));box.addView(label((r.gardenName.isEmpty()?"Genel":r.gardenName)+" · "+date(r.due),14,false));
        box.addView(label(r.done?"Tamamlandı":"Ayrıntı ve işlemler →",13,false));box.setOnClickListener(v->detail(r));return box;}
    private void detail(Row r){if(!current())return;ArrayList<String> options=new ArrayList<>();options.add("Düzenle");if(!r.done)options.add("Tamamlandı işaretle");if(r.garden!=null)options.add("Bahçeyi aç");options.add("Sil");
        new AlertDialog.Builder(this).setTitle(r.title).setItems(options.toArray(new String[0]),(d,which)->{String a=options.get(which);
            if(a.equals("Düzenle"))edit(r);
            else if(a.equals("Bahçeyi aç")){if(current())startActivity(new Intent(this,GardenDetailActivity.class).putExtra("garden_id",(long)r.garden));}
            else new AlertDialog.Builder(this).setTitle(a.equals("Sil")?"Hatırlatma silinsin mi?":"Tamamlandı işaretlensin mi?")
                .setMessage(a.equals("Sil")?"Bu hatırlatma ve yerel alarmı kaldırılacak.":"Bu hatırlatmanın tekrarları da duracak.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Onayla",(x,y)->change(true,repo->a.equals("Sil")?(repo.deleteReminder(r.id)?r.id:-1):(repo.markReminderDone(r.id)>0?r.id:-1))).show();
        }).setCustomTitle(label(r.title+"\n"+(r.note.isEmpty()?date(r.due):r.note+"\n"+date(r.due)),17,true)).setNegativeButton("Kapat",null).show();}
    private void edit(Row r){if(!current())return;
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(12));form.setBackgroundColor(GreenTheme.color(R.color.zd_card));
        EditText title=input("Başlık",r==null?"":r.title);form.addView(title);
        Spinner garden=new Spinner(this);garden.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,gardenNames));
        if(r!=null)garden.setSelection(Math.max(0,gardenIds.indexOf(r.garden)));form.addView(label("Bahçe",14,false));form.addView(garden);
        Calendar selected=Calendar.getInstance();selected.setTimeInMillis(r!=null&&ReminderTime.valid(r.due)?r.due:System.currentTimeMillis()+5*60000);selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);
        TextView when=label(date(selected.getTimeInMillis()),18,true);form.addView(when);
        form.addView(button("Tarih seç",()->new DatePickerDialog(this,(v,y,m,d)->{selected.set(Calendar.DAY_OF_MONTH,1);selected.set(Calendar.YEAR,y);selected.set(Calendar.MONTH,m);selected.set(Calendar.DAY_OF_MONTH,d);when.setText(date(selected.getTimeInMillis()));},selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show()));
        form.addView(button("Saat seç",()->new TimePickerDialog(this,(v,h,m)->{selected.set(Calendar.HOUR_OF_DAY,h);selected.set(Calendar.MINUTE,m);when.setText(date(selected.getTimeInMillis()));},selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),true).show()));
        EditText note=input("Not",r==null?"":r.note);note.setMinLines(2);form.addView(note);
        Spinner repeat=new Spinner(this);repeat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tek sefer","Her gün","Her hafta","Her ay"}));
        if(r!=null)for(int n=0;n<RULES.length;n++)if(RULES[n].equals(ReminderTime.rule(r.repeat)))repeat.setSelection(n);
        form.addView(label("Tekrar • Aylık: kısa ayda son geçerli gün",14,false));form.addView(repeat);
        if(r!=null&&r.done)form.addView(label("Düzenleme tamamlanma durumunu değiştirmez.",13,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(r==null?"Yeni hatırlatma":"Hatırlatmayı düzenle").setView(scroll).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!current()){dialog.dismiss();return;}
            String t=title.getText().toString().trim();long due=selected.getTimeInMillis();
            if(t.isEmpty()){title.setError("Başlık gerekli");return;}if(!ReminderTime.valid(due)||due<=System.currentTimeMillis()){toast("Gelecekte bir tarih ve saat seç.");return;}
            Integer g=gardenIds.get(garden.getSelectedItemPosition());String rule=RULES[repeat.getSelectedItemPosition()],n=note.getText().toString();
            dialog.dismiss();change(false,repo->r==null?repo.addReminder(g,t,"GENERAL",due,rule,n):(repo.updateReminder(r.id,g,t,r.type,due,rule,n)>0?r.id:-1));
        }));dialog.show();
    }
    private void change(boolean cancel,ReminderActions.Change action){ReminderActions.run(this,owner,cancel,action,(saved,scheduled)->{
        if(!current())return;toast(saved?(scheduled?"Kaydedildi.":"Kaydedildi; yerel alarm kurulamadı. Ekranı yeniden açmayı deneyin."):"İşlem kaydedilemedi.");load();
        if(saved&&!cancel&&!new LocalReminderScheduler(this).enabled())permission();
    });}
    private void permission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED
                &&!getPreferences(MODE_PRIVATE).getBoolean("permission_asked",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_asked",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},303);return;
        }
        if(!new LocalReminderScheduler(this).enabled())new AlertDialog.Builder(this).setTitle("Bildirim izni kapalı")
            .setMessage("Hatırlatman telefonda kayıtlı kalır. Bildirim almak için uygulama bildirimlerine izin verebilirsin.")
            .setNegativeButton("Şimdi değil",null).setPositiveButton("Ayarları aç",(d,w)->{
                Intent i=Build.VERSION.SDK_INT>=26?new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));startActivity(i);
            }).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==303){toast(new LocalReminderScheduler(this).enabled()?"Bildirimler açık.":"Bildirim izni kapalı; hatırlatmaların kayıtlı kalıyor.");load();}}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(bold?Color.WHITE:MUTED);t.setPadding(0,dp(7),0,dp(7));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView button(String value,Runnable action){TextView t=label(value,16,true);t.setTextColor(GREEN);t.setMinHeight(dp(48));t.setOnClickListener(v->action.run());return t;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(Color.WHITE);e.setHintTextColor(MUTED);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);return e;}
    private String date(long due){return ReminderTime.valid(due)?DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(due)):"Geçersiz tarih";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
