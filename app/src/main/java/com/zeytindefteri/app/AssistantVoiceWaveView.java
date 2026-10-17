package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Asistan panelindeki ses dalgası (tuval: "Sesli asistan · dinleme"). Gerçek mikrofon düzeyiyle (RMS)
 * hareket eder ve sonlu biçimde söner; rastgele ya da sürekli animasyon yok. Sessizken alçak, sabit çubuklar.
 */
public final class AssistantVoiceWaveView extends View {
    private static final float[] WEIGHTS = {0.20f,0.32f,0.48f,0.66f,0.42f,0.82f,0.58f,0.92f,0.70f,1f,0.70f,0.92f,0.58f,0.82f,0.42f,0.66f,0.48f,0.32f,0.20f};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    private boolean listening;
    private final Runnable decay=new Runnable(){public void run(){
        if(!listening)return;
        level*=0.72f;
        if(level<0.015f)level=0;
        invalidate();
        if(level>0)postDelayed(this,50);
    }};
    public AssistantVoiceWaveView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void listening(boolean value){listening=value;removeCallbacks(decay);level=0;invalidate();}
    public void rms(float value){
        if(!listening)return;
        level=ZeytinAssistantConversation.level(value);removeCallbacks(decay);invalidate();
        if(level>0)postDelayed(decay,100);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float unit=getResources().getDisplayMetrics().density;
        float width=5*unit,gap=4*unit,start=(getWidth()-(width*WEIGHTS.length+gap*(WEIGHTS.length-1)))/2f;
        float span=Math.max(0,getHeight()-8*unit);
        for(int i=0;i<WEIGHTS.length;i++){
            float h=6*unit+level*WEIGHTS[i]*span;
            float x=start+i*(width+gap),y=(getHeight()-h)/2;
            paint.setColor(GreenTheme.color(listening?R.color.zd_green:R.color.zd_border));
            paint.setAlpha(listening?(int)(255*(0.45f+0.55f*WEIGHTS[i])):255);
            canvas.drawRoundRect(x,y,x+width,y+h,width/2,width/2,paint);
        }
    }
    @Override protected void onDetachedFromWindow(){listening(false);super.onDetachedFromWindow();}
}
