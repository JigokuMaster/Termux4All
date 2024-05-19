package com.github.jigokumaster.termux4all;
import android.content.*;
import android.os.*;

public class Clipboard
{
	public static boolean  hasText(Context ctx)
	{
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			android.text.ClipboardManager cbm = (android.text.ClipboardManager) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);
		    return cbm.hasText();
			
		}

		else
		{
			android.content.ClipboardManager cbm = (android.content.ClipboardManager ) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);
			return cbm.hasPrimaryClip();
		}
	
	}
	public static String  getText(Context ctx)
	{
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			android.text.ClipboardManager cbm = (android.text.ClipboardManager) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);
		    CharSequence data = cbm.getText();
		
			return (data != null ? data.toString() : "");
		}
		
			
		android.content.ClipboardManager cbm = (android.content.ClipboardManager ) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);
		ClipData clipData = cbm.getPrimaryClip();
        if (clipData == null)
		{
			return "";
		}
		CharSequence data = clipData.getItemAt(0).coerceToText(ctx);
		return (data != null ? data.toString() : "");
		
	}
	

	public static void setText(Context ctx, String text)
	{
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			android.text.ClipboardManager cbm = (android.text.ClipboardManager) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);
		    cbm.setText(text);
			
		}

		else{
			android.content.ClipboardManager cbm = (android.content.ClipboardManager ) ctx.getSystemService(ctx.CLIPBOARD_SERVICE);		
			cbm.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(text)));
		}

	}
	
	
}
