package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import com.zeytindefteri.app.ZeytinAssistantConversation.State;

/** Olive-leaf light surfaces. No bitmap, random motion or per-frame object allocation. */
public final class ZeytinAssistantOrbView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf=new Path();
    private final RectF oval=new RectF();
    private final Shader shell=new RadialGradient(0,0,1,new int[]{0xff537d52,0xff173e37,0xff102c30},null,Shader.TileMode.CLAMP);
    private final Shader light=new RadialGradient(0,0,1,new int[]{0xffd6dda0,0x605fc5ae,0x0037564d},null,Shader.TileMode.CLAMP);
    private State state=State.IDLE;
    private float morph,from,target,level,rms;
    private long changed,lastRms;
    public ZeytinAssistantOrbView(Context context){super(context);setFocusable(true);setClickable(true);describe();}
    public void state(State next){
        if(state==next)return;
        state=next;from=morph;target=next==State.IDLE?0:1;changed=SystemClock.uptimeMillis();
        if(next!=State.LISTENING){rms=0;level=0;}describe();invalidate();
    }
    public void rms(float value){rms=ZeytinAssistantConversation.level(value);lastRms=SystemClock.uptimeMillis();invalidate();}
    private void describe(){setContentDescription(state==State.IDLE?"Zeytin Asistanını aç. İşlem eklemek için uzun bas.":state==State.LISTENING?"Zeytin Asistanı dinliyor. Dinlemeyi bitirmek için dokun.":state==State.PROCESSING?"Zeytin Asistanı düşünüyor":state==State.ANSWERING?"Zeytin Asistanı cevap veriyor. Tekrar sormak için dokun.":"Zeytin Asistanı. Tekrar denemek için dokun.");}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);long now=SystemClock.uptimeMillis();
        float fraction=Math.min(1,(now-changed)/240f);fraction=fraction*fraction*(3-2*fraction);morph=from+(target-from)*fraction;
        float desired=state==State.LISTENING && now-lastRms<180?rms:0;level+=(desired-level)*.18f;
        float phase=now/2200f;
        float breath=state==State.LISTENING?(float)Math.sin(phase)*.012f:0;
        float radius=Math.min(getWidth(),getHeight())*(.415f+(.018f+breath+level*.025f)*morph);
        c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(radius,radius);
        paint.setShader(null);paint.setColor(0xff42a04d);paint.setAlpha(255);c.drawCircle(0,0,1,paint);
        if(morph>0){
            paint.setShader(shell);paint.setAlpha((int)(255*morph));c.drawCircle(0,0,1,paint);paint.setShader(null);
            c.save();c.rotate(state==State.ERROR?12:(float)Math.sin(phase*.4f)*15+12);
            for(int i=0;i<3;i++){
                c.save();c.rotate(i*112);float drift=state==State.ERROR?0:(float)Math.sin(phase+i)*.08f;
                leaf.reset();leaf.moveTo(-.7f,-.1f);leaf.cubicTo(-.25f,-.85f+drift,.72f,-.58f,.7f,.22f);leaf.cubicTo(.1f,.56f+level*.12f,-.35f,.35f,-.7f,-.1f);
                paint.setColor(i==0?0xff739b46:i==1?0xff38aaa0:0xffc8a356);paint.setAlpha((int)((state==State.ERROR?70:130+level*35)*morph));c.drawPath(leaf,paint);c.restore();
            }c.restore();
            c.save();c.scale(.55f+level*.1f,.55f+level*.1f);paint.setShader(light);paint.setAlpha((int)((state==State.ERROR?65:170+level*50)*morph));c.drawCircle(0,0,1,paint);c.restore();paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.025f);paint.setColor(0xff98bc87);paint.setAlpha((int)(85*morph));c.drawCircle(0,0,.98f,paint);paint.setStyle(Paint.Style.FILL);
        }
        if(morph<1){paint.setColor(Color.WHITE);paint.setAlpha((int)(255*(1-morph)));oval.set(-.15f,-.43f,.15f,.12f);c.drawRoundRect(oval,.15f,.15f,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.07f);oval.set(-.28f,-.2f,.28f,.28f);c.drawArc(oval,0,180,false,paint);c.drawLine(0,.28f,0,.48f,paint);c.drawLine(-.18f,.48f,.18f,.48f,paint);paint.setStyle(Paint.Style.FILL);}
        c.restore();
        if(isAttachedToWindow() && getWindowVisibility()==VISIBLE && (fraction<1 || (state!=State.IDLE && state!=State.ERROR)))postInvalidateDelayed(32);
    }
    public void suspend(){state(State.IDLE);morph=from=target=0;invalidate();}
    @Override protected void onDetachedFromWindow(){suspend();super.onDetachedFromWindow();}
}
