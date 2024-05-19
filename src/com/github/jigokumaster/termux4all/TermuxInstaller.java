package com.github.jigokumaster.termux4all;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
//import android.util.Pair;
import android.view.WindowManager;
import com.termux.terminal.EmulatorDebug;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.*;
import android.content.*;


/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX folder below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging folder, $STAGING_PREFIX, is {@link #deleteFolder(File)} if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {
	private static boolean updateNeeded = false;
	
    /** Performs setup if necessary. */
    static void setupIfNeeded(final Activity activity, final Runnable whenDone) {
        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        /*
		UserManager um = (UserManager) activity.getSystemService(Context.USER_SERVICE);
        boolean isPrimaryUser = um.getSerialNumberForUser(android.os.Process.myUserHandle()) == 0;
        if (!isPrimaryUser) {
            new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_not_primary_user_message)
                .setOnDismissListener(dialog -> System.exit(0)).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
		*/
		
			
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    	final String pref_key = "termux_bootstrap_version";
        try
        {
            InputStream fis = activity.getAssets().open("bootstrap-version.txt");
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            fis.close();
            String versionInfo0 = new String(buf);
            
            if(!prefs.contains(pref_key))
            {
                prefs.edit().putString(pref_key, versionInfo0).commit();

            }

            else{
                    String versionInfo1 = prefs.getString(pref_key, null);
                    updateNeeded = !versionInfo0.equals(versionInfo1);
                   // Log.i(EmulatorDebug.LOG_TAG, String.format("versionInfo0=%s, versionInfo1=%s update=%s", versionInfo0, versionInfo1, updateNeeded) );
                    
            }
        }
        catch(IOException e)
        {
            
            //updateNeeded = true;
            Log.e(EmulatorDebug.LOG_TAG, "Bootstrap error", e);
            handleInstallError(activity, whenDone);
            return;
        }

        final File PREFIX_FILE = new File(TermuxService.PREFIX_PATH);
        if (PREFIX_FILE.isDirectory() && !updateNeeded) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread(){
            @Override
            public void run()
			{
				final String STAGING_PREFIX_PATH = TermuxService.FILES_PATH + "/usr-staging";
                final File STAGING_PREFIX_FILE = new File(STAGING_PREFIX_PATH);
				try
				{
					if(updateNeeded)
					{
						unpack(TermuxService.PREFIX_PATH, true);
					}
					else
					{
						if (STAGING_PREFIX_FILE.exists())
						{
							deleteFolder(STAGING_PREFIX_FILE);
						}
				   
						unpack(STAGING_PREFIX_PATH, false);
						if (!STAGING_PREFIX_FILE.renameTo(PREFIX_FILE)) {
							throw new RuntimeException("Unable to rename staging folder");
						}
					}
                    activity.runOnUiThread(whenDone);
                }
				catch(Exception e)
				{
			
                    Log.e(EmulatorDebug.LOG_TAG, "Bootstrap error", e);
					handleInstallError(activity, whenDone);
					
                } finally {
                    activity.runOnUiThread(new Runnable(){

							@Override
							public void run()
							{
								try {
									progress.dismiss();
								} catch (RuntimeException e) {
									// Activity already dismissed - ignore.
								}
							}
                    });
                }
            }
        }.start();
    }

	/*hackish method to install/setup  cli tools , busybox applets ...  */
	public static boolean doCliSetupIfNeeded(TermuxActivity act)
	{
		File cliSetup = new File(TermuxService.PREFIX_PATH, "/tmp/cli_setup.sh");
		String shell = "/system/bin/sh";
		File applets = new File(TermuxService.PREFIX_PATH , "/bin/applets");
		
		if(cliSetup.exists() && !applets.exists())
		{
			String[] arguments = new String[]{cliSetup.getAbsolutePath()};
			act.startSetupSession(shell, arguments);
			return true;
			
		}
		
		return false;
		
	}
	private static void handleInstallError(final Activity activity, final Runnable whenDone)
	{

		activity.runOnUiThread(new Runnable(){

				@Override
				public void run()
				{
					AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
					dialog.setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body);
					dialog.setNegativeButton(R.string.bootstrap_error_abort, new DialogInterface.OnClickListener(){

								@Override
								public void onClick(DialogInterface p1, int p2)
								{
									
									try{
										p1.dismiss();
									}
									catch (WindowManager.BadTokenException e1)
									{
										// Activity already dismissed - ignore.
									}
									activity.finish();
								}
								
							
						});
						
					dialog.setPositiveButton(R.string.bootstrap_error_try_again, new DialogInterface.OnClickListener()
					{

							@Override
							public void onClick(DialogInterface p1, int p2)
							{
								TermuxInstaller.setupIfNeeded(activity, whenDone);
								
							}
								
							
						});
										
					dialog.create().show();
						
				}


			});
		 
	}
	private static void unpack(String dist, boolean update) throws IOException
	{
		final byte[] buffer = new byte[8096];

		final byte[] zipBytes = loadZipBytes();
		ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes));
		ZipEntry zipEntry;

		while ((zipEntry = zipInput.getNextEntry()) != null)
		{

			String zipEntryName = zipEntry.getName();
			File targetFile = new File(dist, zipEntryName);

			boolean isDirectory = zipEntry.isDirectory();

			if(update  && targetFile.exists())
			{
				if(isDirectory)
				{
					deleteFolder(targetFile);
				}
				else{
					//Log.i(EmulatorDebug.LOG_TAG,"bootstrap update, removing old file "+zipEntryName);
					targetFile.delete();
				}	
			}
			ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());

			if (!isDirectory)
			{
				FileOutputStream outStream = new FileOutputStream(targetFile);
				int readBytes;
				while ((readBytes = zipInput.read(buffer)) != -1)
				{
					outStream.write(buffer, 0, readBytes);
					
				}
				
				if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith("lib/apt/methods"))
				{
					//noinspection OctalInteger
					chmod(targetFile.getAbsolutePath(), 0700);
				}
				outStream.close();
			}
		}
		zipInput.close();
	}
	
    private static void ensureDirectoryExists(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + directory.getAbsolutePath());
        }
    }

    public static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();
    public static native int chmod(String fp, int mode);

    /** Delete a folder and all its content or throw. Don't follow symlinks. */
    static void deleteFolder(File fileOrDirectory) throws IOException {
        if (fileOrDirectory.getCanonicalPath().equals(fileOrDirectory.getAbsolutePath()) && fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteFolder(child);
                }
            }
        }

        if (!fileOrDirectory.delete()) {
            throw new RuntimeException("Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
        }
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        new Thread() {
            public void run() {
                try {
                    File storageDir = new File(TermuxService.HOME_PATH, "storage");

                    if (storageDir.exists()) {
                        try {
                            deleteFolder(storageDir);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "Could not delete old $HOME/storage, " + e.getMessage());
                            return;
                        }
                    }

                    if (!storageDir.mkdirs()) {
                        Log.e(LOG_TAG, "Unable to mkdirs() for $HOME/storage");
                        return;
                    }

                    File sharedDir = Environment.getExternalStorageDirectory();
                    //Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    //Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    //Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    //Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    //Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    //Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    final File[] dirs = context.getExternalFilesDir(null).listFiles();
                    if (dirs != null && dirs.length > 1) {
                        for (int i = 1; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            //Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error setting up link", e);
                }
            }
        }.start();
    }

}
