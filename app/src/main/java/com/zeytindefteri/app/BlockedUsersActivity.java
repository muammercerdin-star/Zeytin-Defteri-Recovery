package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** Small owner-only list; identity labels are captured when the block is confirmed. */
public final class BlockedUsersActivity extends Activity {
    private SocialBlockRepository blocks;
    private LinearLayout list;
    private TextView status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(getActionBar()!=null)getActionBar().hide();
        blocks=new SocialBlockRepository(this);
        if(!blocks.canManage()){Toast.makeText(this,"Engel listesi için giriş yapın.",Toast.LENGTH_SHORT).show();finish();return;}
        int bg=GreenTheme.color(R.color.zd_bg);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(12),dp(16),dp(12));
        TextView back=text("←  Engellenen Kullanıcılar",20);back.setMinHeight(dp(52));back.setGravity(Gravity.CENTER_VERTICAL);back.setOnClickListener(v->finish());root.addView(back);
        status=text("Telefonda saklanan engel listesi",12);root.addView(status);
        Button refresh=new Button(this);refresh.setText("Yenile");refresh.setOnClickListener(v->startList());root.addView(refresh);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);render();
    }
    @Override protected void onStart(){super.onStart();if(blocks!=null&&!isFinishing())startList();}
    private void startList(){blocks.start(fresh->{if(isFinishing()||isDestroyed())return;if(!blocks.canManage()){finish();return;}
        status.setText(fresh?"Engel listesi güncel":"Liste yenilenemedi. Saklanan kayıtlar korunuyor.");render();});}
    private void render(){list.removeAllViews();
        for(SocialBlockRepository.Entry entry:blocks.list()){
            LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(0,dp(14),0,dp(14));
            String name=entry.name.isEmpty()?"Üretici • "+entry.uid:entry.name;
            row.addView(text("●  "+name,17));if(!entry.region.isEmpty())row.addView(text(entry.region,13));
            Button remove=new Button(this);remove.setText("Engeli Kaldır");remove.setOnClickListener(v->SocialBlockUi.confirm(this,blocks,entry.uid,entry.name,entry.region,true));row.addView(remove);list.addView(row);
        }
        if(list.getChildCount()==0)list.addView(text("Engellenen kullanıcı bulunmuyor.",16));
    }
    @Override protected void onStop(){if(blocks!=null)blocks.stop();super.onStop();}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(GreenTheme.color(R.color.zd_text));return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
