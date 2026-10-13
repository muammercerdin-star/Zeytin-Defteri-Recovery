package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/** Converts the cloud post identity to this device's SQLite identity. */
public final class PushOpenActivity extends Activity {
    private DatabaseHelper db;
    private SocialCloudRepository cloud;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String post=getIntent().getStringExtra("post_cloud_id");
        String uid=getIntent().getStringExtra("recipient_uid"),session=getIntent().getStringExtra("session_id");
        if(!ZeytinMessagingService.validId(post)||!PushRegistration.accepts(this,uid,session)) {
            Toast.makeText(this,"Bu bildirim önceki oturuma ait. Meydan'dan devam edebilirsin.",Toast.LENGTH_LONG).show();
            startActivity(new Intent(this,MainActivity.class).putExtra("open_section","MEYDAN"));finish();return;
        }
        TextView loading=new TextView(this);loading.setText("Yanıt açılıyor…");loading.setPadding(32,48,32,32);setContentView(loading);
        db=new DatabaseHelper(this);cloud=new SocialCloudRepository(this,db);
        long id=db.getLocalPostIdByCloudId(post);
        if(id>0){open(id);return;}
        cloud.fetchPost(post,success -> {
            if(isFinishing()||isDestroyed())return;
            if(!PushRegistration.accepts(this,uid,session)){finish();return;}
            long local=db.getLocalPostIdByCloudId(post);
            if(success&&local>0)open(local);
            else {Toast.makeText(this,"Gönderi yüklenemedi. Bağlantını kontrol edip bildirime tekrar dokun.",Toast.LENGTH_LONG).show();finish();}
        });
    }
    private void open(long id) {
        Intent home=new Intent(this,MainActivity.class).putExtra("open_section","MEYDAN");
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivities(new Intent[]{home,new Intent(this,PostRepliesActivity.class).putExtra("post_id",id)});finish();
    }
    @Override protected void onDestroy(){if(cloud!=null)cloud.close();if(db!=null)db.close();super.onDestroy();}
}
