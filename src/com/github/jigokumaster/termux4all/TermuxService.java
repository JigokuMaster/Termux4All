package com.github.jigokumaster.termux4all;

import android.annotation.SuppressLint;
import android.app.Notification;
// import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.github.jigokumaster.termux4all.R;
import com.github.jigokumaster.termux4all.BackgroundJob;
import com.termux.terminal.EmulatorDebug;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSession.SessionChangedCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.support.v4.app.*;
import android.widget.*;
import android.app.*;
import android.app.AlertDialog.*;
import android.content.*;
import android.view.*;
//import android.transition.*;
import android.graphics.*;

/**
 * A service holding a list of terminal sessions, {@link #mTerminalSessions}, showing a foreground notification while
 * running so that it is not terminated. The user interacts with the session through {@link TermuxActivity}, but this
 * service may outlive the activity when the user or the system disposes of the activity. In that case the user may
 * restart {@link TermuxActivity} later to yet again access the sessions.
 * <p/>
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, {@link Service#startForeground(int, Notification)}.
 * <p/>
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * {@link #buildNotification()}.
 */
public final class TermuxService extends Service implements SessionChangedCallback {

    private static final String NOTIFICATION_CHANNEL_ID = "termux_notification_channel";

    /** Note that this is a symlink on the Android M preview. */
    // @SuppressLint("SdCardPath")
    public static final String FILES_PATH = "/data/data/com.github.jigokumaster.termux4all/files";
    public static final String PREFIX_PATH = FILES_PATH + "/usr";
    public static final String HOME_PATH = FILES_PATH + "/home";

    private static final int NOTIFICATION_ID = 1337;

    private static final String ACTION_STOP_SERVICE = "com.termux.service_stop";
    private static final String ACTION_LOCK_WAKE = "com.termux.service_wake_lock";
    private static final String ACTION_UNLOCK_WAKE = "com.termux.service_wake_unlock";
	private static final String ACTION_TOGGLE_NOTIFICATION_BUTTONS = "com.termux.service_toggle_notification_buttons";
	
    /** Intent action to launch a new terminal session. Executed from TermuxWidgetProvider. */
    public static final String ACTION_EXECUTE = "com.termux.service_execute";

    public static final String EXTRA_ARGUMENTS = "com.termux.execute.arguments";

    public static final String EXTRA_CURRENT_WORKING_DIRECTORY = "com.termux.execute.cwd";
    private static final String EXTRA_EXECUTE_IN_BACKGROUND = "com.termux.execute.background";

    /** This service is only bound from inside the same process and never uses IPC. */
    class LocalBinder extends Binder {
        public final TermuxService service = TermuxService.this;
    }

    private final IBinder mBinder = new LocalBinder();

    private final Handler mHandler = new Handler();

    /**
     * The terminal sessions which this service manages.
     * <p/>
     * Note that this list is observed by {@link TermuxActivity#mListViewAdapter}, so any changes must be made on the UI
     * thread and followed by a call to {@link ArrayAdapter#notifyDataSetChanged()} }.
     */
    final List<TerminalSession> mTerminalSessions = new ArrayList<>();

    final List<BackgroundJob> mBackgroundTasks = new ArrayList<>();

    /** Note that the service may often outlive the activity, so need to clear this reference. */
    SessionChangedCallback mSessionChangeCallback;

    /** The wake lock and wifi lock are always acquired and released together. */
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    /** If the user has executed the {@link #ACTION_STOP_SERVICE} intent. */
    boolean mWantsToStop = false;
	boolean mShowActionsButtons = false;
    // @SuppressLint("Wakelock")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		
		if(ACTION_TOGGLE_NOTIFICATION_BUTTONS.equals(action))
		{
			mShowActionsButtons = !mShowActionsButtons;
			updateNotification();
		}
        else if (ACTION_STOP_SERVICE.equals(action)) {
            mWantsToStop = true;
            for (int i = 0; i < mTerminalSessions.size(); i++)
			{
                try
				{
					mTerminalSessions.get(i).finishIfRunning();
				}
				catch (Exception e)
				{}
			}
            stopSelf();
        } else if (ACTION_LOCK_WAKE.equals(action)) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, EmulatorDebug.LOG_TAG);
                mWakeLock.acquire();

                // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, EmulatorDebug.LOG_TAG);
                mWifiLock.acquire();

				mShowActionsButtons = false;
                updateNotification();
            }
        } else if (ACTION_UNLOCK_WAKE.equals(action)) {
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;

                mWifiLock.release();
                mWifiLock = null;
				mShowActionsButtons = false;
                updateNotification();
            }
        } else if (ACTION_EXECUTE.equals(action)) {
            Uri executableUri = intent.getData();
            String executablePath = (executableUri == null ? null : executableUri.getPath());

            String[] arguments = (executableUri == null ? null : intent.getStringArrayExtra(EXTRA_ARGUMENTS));
            String cwd = intent.getStringExtra(EXTRA_CURRENT_WORKING_DIRECTORY);

            if (intent.getBooleanExtra(EXTRA_EXECUTE_IN_BACKGROUND, false)) {
                BackgroundJob task = new BackgroundJob(cwd, executablePath, arguments, this);
                mBackgroundTasks.add(task);
                updateNotification();
            } else {
                boolean failsafe = intent.getBooleanExtra(TermuxActivity.TERMUX_FAILSAFE_SESSION_ACTION, false);
                TerminalSession newSession = createTermSession(executablePath, arguments, cwd, failsafe);

                // Transform executable path to session name, e.g. "/bin/do-something.sh" => "do something.sh".
                if (executablePath != null) {
                    int lastSlash = executablePath.lastIndexOf('/');
                    String name = (lastSlash == -1) ? executablePath : executablePath.substring(lastSlash + 1);
                    name = name.replace('-', ' ');
                    newSession.mSessionName = name;
                }

                // Make the newly created session the current one to be displayed:
                // TermuxPreferences.storeCurrentSession(this, newSession);

                // Launch the main Termux app, which will now show the current session:
                startActivity(new Intent(this, TermuxActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else if (action != null) {
            Log.e(EmulatorDebug.LOG_TAG, "Unknown TermuxService action: '" + action + "'");
        }
		
		
		// If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
		return Service.START_NOT_STICKY;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        // setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    /** Update the shown foreground service notification after making any changes that affect it. */
    void updateNotification() {
        if (mWakeLock == null && mTerminalSessions.isEmpty() && mBackgroundTasks.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            stopSelf();
        } else {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification());
        }
    }
	

    private Notification buildNotification() {
		Resources res = getResources();
		
		Intent notifyIntent = new Intent(this, TermuxActivity.class);
        // PendingIntent#getActivity(): "Note that the activity will be started outside of the context of an existing
        // activity, so you must use the Intent.FLAG_ACTIVITY_NEW_TASK launch flag in the Intent":
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);

        int sessionCount = mTerminalSessions.size();
        int taskCount = mBackgroundTasks.size();
        String contentText = sessionCount + " session" + (sessionCount == 1 ? "" : "s");
        if (taskCount > 0) {
            contentText += ", " + taskCount + " task" + (taskCount == 1 ? "" : "s");
        }

        final boolean wakeLockHeld = mWakeLock != null;
        if (wakeLockHeld) contentText += " (wake lock held)";
		
		Intent exitIntent = new Intent(this, TermuxService.class).setAction(ACTION_STOP_SERVICE);
        String newWakeAction = wakeLockHeld ? ACTION_UNLOCK_WAKE : ACTION_LOCK_WAKE;
        Intent toggleWakeLockIntent = new Intent(this, TermuxService.class).setAction(newWakeAction);

        
		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
		{
			
			Notification.Builder builder = new Notification.Builder(this);

			builder.setContentTitle(getText(R.string.application_name));
			builder.setContentText(contentText);
			builder.setSmallIcon(R.drawable.ic_launcher);
			builder.setContentIntent(pendingIntent);
			builder.setOngoing(true);

			// If holding a wake or wifi lock consider the notification of high priority since it's using power,
			// otherwise use a low priority
			builder.setPriority((wakeLockHeld) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW);

			// No need to show a timestamp:
			//builder.setShowWhen(false);
			// Background color for small notification icon:
        	// builder.setColor(0xFF607D8B);
        	/*
		 		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		 		builder.setChannelId(NOTIFICATION_CHANNEL_ID);
		 	}*/
		 
			
			
			builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit), PendingIntent.getService(this, 0, exitIntent, 0));
			String actionTitle = res.getString(wakeLockHeld ?
										   R.string.notification_action_wake_unlock :
										   R.string.notification_action_wake_lock);
        	int actionIcon = wakeLockHeld ? android.R.drawable.ic_lock_idle_lock : android.R.drawable.ic_lock_lock;
        	builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, 0));

        	return builder.build();
		
		}
		
 
		NotificationCompat.Builder  builder =  new NotificationCompat.Builder(this);
		builder.setAutoCancel(false);
		builder.setSmallIcon(R.drawable.ic_service_notification);
		builder.setContentIntent(pendingIntent);
		builder.setOngoing(true);
		// If holding a wake or wifi lock consider the notification of high priority since it's using power,
		// otherwise use a low priority
		builder.setPriority((wakeLockHeld) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW);
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_layout);
			contentView.setTextViewText(R.id.notification_title, getText(R.string.application_name));
			contentView.setTextViewText(R.id.notification_text, contentText);
			contentView.setImageViewResource(R.id.image, R.drawable.ic_service_notification);
			
			Intent toggleBtnsIntent = new Intent(this, TermuxService.class).setAction(ACTION_TOGGLE_NOTIFICATION_BUTTONS);
			int btnsVisibility = (mShowActionsButtons ? View.VISIBLE : View.GONE);
			int textsVisibility = (mShowActionsButtons ? View.GONE : View.VISIBLE);
			contentView.setViewVisibility(R.id.notification_sub_layout, btnsVisibility);
			contentView.setViewVisibility(R.id.notification_title, textsVisibility);
			contentView.setViewVisibility(R.id.notification_text, textsVisibility);
			contentView.setOnClickPendingIntent(R.id.notification_toggle_button, PendingIntent.getService(this,0, toggleBtnsIntent, 0));
		   	contentView.setOnClickPendingIntent(R.id.notification_exit_btn, PendingIntent.getService(this,0, exitIntent, 0));

			contentView.setInt(R.id.notification_wakelock_btn, "setBackgroundColor", Color.TRANSPARENT);
			contentView.setInt(R.id.notification_exit_btn, "setBackgroundColor", Color.TRANSPARENT);
		
			contentView.setCharSequence(R.id.notification_exit_btn, "setText", res.getString(R.string.notification_action_exit));	
			contentView.setCharSequence(R.id.notification_wakelock_btn, "setText", res.getString(wakeLockHeld ?
																								 R.string.notification_action_wake_unlock :
																								 R.string.notification_action_wake_lock));																										
			contentView.setOnClickPendingIntent(R.id.notification_wakelock_btn, PendingIntent.getService(this,0, toggleWakeLockIntent, 0));
			builder.setContent(contentView);
		
		}
		
		else
		{
						
	        builder.setContentTitle(getText(R.string.application_name));
			builder.setContentText(contentText);
		    
		}	
		
		return builder.build();
    }


    @Override
    public void onDestroy() {
       
		/*
        File termuxTmpDir = new File(TermuxService.PREFIX_PATH + "/tmp");

        if (termuxTmpDir.exists()) {
            

            termuxTmpDir.mkdirs();
        }
		*/

        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();

        stopForeground(true);

        for (int i = 0; i < mTerminalSessions.size(); i++)
		{
            try
			{
				mTerminalSessions.get(i).finishIfRunning();
			}
			catch (Exception e)
			{}
		}
    }

    public List<TerminalSession> getSessions() {
        return mTerminalSessions;
    }

    TerminalSession createTermSession(String executablePath, String[] arguments, String cwd, boolean failSafe) {
        new File(HOME_PATH).mkdirs();
		
        if (cwd == null)
		{
			cwd = "/sdcard";
			if(!new File(cwd).canWrite())
			{
				cwd = HOME_PATH;
			}
		}

        
        boolean isLoginShell = false;

        if (executablePath == null) {
            if (!failSafe) {
                for (String shellBinary : new String[]{"login", "bash", "zsh"}) {
                    File shellFile = new File(PREFIX_PATH + "/bin/" + shellBinary);
                    //if (shellFile.canExecute()) 
                    if (shellFile.exists()){
                        executablePath = shellFile.getAbsolutePath();
                        break;
                    }
                }
            }

            // for now, only system shell is supported  on FROYO and FROYO
            if (executablePath == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                // Fall back to system shell as last resort:
                executablePath = "/system/bin/sh";
            }
            isLoginShell = true;
        }

        String[] env = BackgroundJob.buildEnvironment(executablePath, failSafe, cwd);
       
        String[] processArgs = BackgroundJob.setupProcessArgs(executablePath, arguments);
        executablePath = processArgs[0];
        int lastSlashIndex = executablePath.lastIndexOf('/');
        String processName = (isLoginShell ? "-" : "") +
            (lastSlashIndex == -1 ? executablePath : executablePath.substring(lastSlashIndex + 1));

        String[] args = new String[processArgs.length];
        args[0] = processName;
        if (processArgs.length > 1) System.arraycopy(processArgs, 1, args, 1, processArgs.length - 1);

        TerminalSession session = new TerminalSession(executablePath, cwd, args, env, this);
        mTerminalSessions.add(session);
        updateNotification();

        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent("com.termux.app.reload_style");
        stylingIntent.putExtra("com.termux.app.reload_style", "styling");
        sendBroadcast(stylingIntent);

        return session;
    }

    public int removeTermSession(TerminalSession sessionToRemove) {
        int indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove);
		if(indexOfRemoved >= 0 )
		{
        	mTerminalSessions.remove(indexOfRemoved);
		}
        if (mTerminalSessions.isEmpty() && mWakeLock == null) {
            // Finish if there are no sessions left and the wake lock is not held, otherwise keep the service alive if
            // holding wake lock since there may be daemon processes (e.g. sshd) running.
            stopSelf();
        } else {
            updateNotification();
        }
        return indexOfRemoved;
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTitleChanged(changedSession);
    }

    @Override
    public void onSessionFinished(final TerminalSession finishedSession) {
        if (mSessionChangeCallback != null)
            mSessionChangeCallback.onSessionFinished(finishedSession);
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTextChanged(changedSession);
    }

    @Override
    public void onClipboardText(TerminalSession session, String text) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onClipboardText(session, text);
    }

    @Override
    public void onBell(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onBell(session);
    }

    @Override
    public void onColorsChanged(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onColorsChanged(session);
    }

    public void onBackgroundJobExited(final BackgroundJob task) {
        Runnable r = new Runnable()
        {

                @Override
                public void run()
                {
                    mBackgroundTasks.remove(task);
                    updateNotification();
                }
        };

        mHandler.post(r);

    }

    /*
    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String channelName = "Termux";
        String channelDescription = "Notifications from Termux";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName,importance);
        channel.setDescription(channelDescription);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
    */
}
