package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Five quiet bars driven by real RMS, with finite decay; no random or perpetual animation. */
public final class AssistantVoiceWaveView extends View {
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
        super.onDraw(canvas);paint.setColor(GreenTheme.parse(listening?"#89CE8C":"#53726B"));
        float unit=getResources().getDisplayMetrics().density;
        float width=6*unit,gap=6*unit,start=(getWidth()-(width*5+gap*4))/2f;
        float[] weights={0.40f,0.72f,1f,0.72f,0.40f};
        for(int i=0;i<5;i++){
            float h=5*unit+level*weights[i]*Math.max(0,getHeight()-12*unit);
            float x=start+i*(width+gap),y=(getHeight()-h)/2;
            canvas.drawRoundRect(x,y,x+width,y+h,width/2,width/2,paint);
        }
    }
    @Override protected void onDetachedFromWindow(){listening(false);super.onDetachedFromWindow();}
}
