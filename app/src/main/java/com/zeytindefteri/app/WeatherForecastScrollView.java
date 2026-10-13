package com.zeytindefteri.app;

import android.content.Context;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Set all five widths before measuring children, including the first frame and window resize. */
public final class WeatherForecastScrollView extends HorizontalScrollView {
    public WeatherForecastScrollView(Context context){super(context);}
    public static int cardWidth(int viewport,int gap){return Math.max(1,(viewport-4*gap)/5);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        // The spec may only be an upper bound. Resolve the actual viewport first.
        super.onMeasure(widthSpec,heightSpec);
        if(getMeasuredWidth()<=0 || getChildCount()==0 || !(getChildAt(0) instanceof LinearLayout))return;
        LinearLayout strip=(LinearLayout)getChildAt(0);
        int width=getMeasuredWidth()-getPaddingLeft()-getPaddingRight()-strip.getPaddingLeft()-strip.getPaddingRight();
        int gap=Math.round(4*getResources().getDisplayMetrics().density);
        int base=cardWidth(width,gap), remainder=Math.max(0,width-4*gap-5*base);
        boolean changed=false;
        for(int i=0;i<strip.getChildCount();i++){
            View card=strip.getChildAt(i);
            LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)card.getLayoutParams();
            int wanted=base+(i<remainder?1:0), margin=i==0?0:gap;
            if(params.width!=wanted || params.leftMargin!=margin || params.rightMargin!=0){
                params.width=wanted;params.leftMargin=margin;params.rightMargin=0;
                // Invalidate the child's measure cache; mutating LayoutParams alone does not.
                card.forceLayout();changed=true;
            }
        }
        if(changed){strip.forceLayout();super.onMeasure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),heightSpec);}
    }
}
