package com.github.jigokumaster.termux4all;


import android.support.v4.view.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.util.*;

/**
* Custom ViewPager with wrap_content support: https://stackoverflow.com/questions/8394681/android-i-am-unable-to-have-viewpager-wrap-content
**/

public class HeightWrappingViewPager extends ViewPager {

  public HeightWrappingViewPager(Context context) {
    super(context);
  }

  public HeightWrappingViewPager(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    	
        int height = 0;
        int childWidthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight()), MeasureSpec.getMode(widthMeasureSpec));
        for (int i = 0; i < getChildCount(); i++)
		{
			View child = getChildAt(i);
            child.measure(childWidthSpec, MeasureSpec.UNSPECIFIED);
            int h = child.getMeasuredHeight();
		    
			if (h > height)
			{
			   height = h;
			   if(child instanceof ExtraKeysView2x)
			   {
				  int rows = ((ExtraKeysView2x)child).getNumRows();
				  height = height * rows;
			   }
		     }
        }
                                                                                        
        if (height != 0)
		{
        	heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
		                                                                                 
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      	
  }
}
