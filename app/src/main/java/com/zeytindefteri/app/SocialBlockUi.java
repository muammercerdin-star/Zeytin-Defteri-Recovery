package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public final class SocialBlockUi {
    private SocialBlockUi(){}
    public static void confirm(Activity activity,SocialBlockRepository blocks,String uid,String name,String region,boolean remove) {
        if(blocks==null||!blocks.isCurrent()||!SocialReportManager.canReport(uid))return;
        new AlertDialog.Builder(activity)
            .setTitle(remove?"Bu kullanıcının engelini kaldırmak istiyor musunuz?":"Bu kullanıcıyı engellemek istiyor musunuz?")
            .setMessage(remove?"Bu kullanıcının içerikleri yeniden görünebilecek.":"Bu kullanıcının gönderi, yorum ve yanıtlarını artık görmeyeceksiniz.")
            .setNegativeButton("Vazgeç",null).setPositiveButton(remove?"Engeli Kaldır":"Engelle",(d,w)->{
                AlertDialog progress=new AlertDialog.Builder(activity).setMessage("İşlem yapılıyor…").setCancelable(false).create();progress.show();
                Handler handler=new Handler(Looper.getMainLooper());boolean[] completed={false};
                Runnable timeout=()->{if(completed[0])return;completed[0]=true;if(!activity.isDestroyed())progress.dismiss();
                    message(activity,"İşlem sonucu doğrulanamadı. Bağlantınızı kontrol edip listeyi yenileyin.");};
                handler.postDelayed(timeout,20000);
                blocks.change(uid,name,region,remove,ok->{handler.removeCallbacks(timeout);if(completed[0])return;completed[0]=true;
                    if(!activity.isDestroyed())progress.dismiss();
                    if(!blocks.isCurrent())return;
                    message(activity,ok?(remove?"Engel kaldırıldı.":"Kullanıcı engellendi."):"İşlem tamamlanamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.");});
            }).show();
    }
    private static void message(Activity a,String text){if(!a.isFinishing()&&!a.isDestroyed())Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
}
