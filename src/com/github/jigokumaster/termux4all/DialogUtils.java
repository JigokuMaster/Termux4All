package com.github.jigokumaster.termux4all;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView.*;
import android.widget.*;
import com.github.jigokumaster.termux4all.TermuxActivity.*;
import android.graphics.*;

public final class DialogUtils
{


    public interface TextSetListener {
        public void onTextSet(String text);
    }

    public static void textInput(Activity activity, int titleText, String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss) {
        final EditText input = new EditText(activity);
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener(new OnEditorActionListener(){

				@Override
				public boolean onEditorAction(TextView p1, int p2, KeyEvent p3)
				{
					onPositive.onTextSet(input.getText().toString());
					dialogHolder[0].dismiss();
					return true;
				}
				
			
		});
		
		
		

        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
        // https://www.google.com/design/spec/components/dialogs.html#dialogs-specs
        int paddingTopAndSides = Math.round(16 * dipInPixels);
        int paddingBottom = Math.round(24 * dipInPixels);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        layout.setPadding(paddingTopAndSides, paddingTopAndSides, paddingTopAndSides, paddingBottom);
        layout.addView(input);

		
		DialogInterface.OnClickListener positiveButtonClickListener = new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface p1, int p2)
			{

				onNeutral.onTextSet(input.getText().toString());

			}
		};
		
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setTitle(titleText).setView(layout)
            .setPositiveButton(positiveButtonText, new DialogInterface.OnClickListener(){

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					
					onPositive.onTextSet(input.getText().toString());
				}
				
				
			});
		
		DialogInterface.OnClickListener negativeButtonClickListener = new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface p1, int p2)
			{

				onNegative.onTextSet(input.getText().toString());

			}
		};	

		DialogInterface.OnClickListener neutralButtonClickListener = new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface p1, int p2)
			{

				onNeutral.onTextSet(input.getText().toString());

			}
		};
		
        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, neutralButtonClickListener);
			
        }

        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, negativeButtonClickListener);
        }

        //if (onDismiss != null) builder.setOnDismissListener(onDismiss);
        dialogHolder[0] = builder.create();
        dialogHolder[0].setCanceledOnTouchOutside(false);
        dialogHolder[0].show();
    }

}