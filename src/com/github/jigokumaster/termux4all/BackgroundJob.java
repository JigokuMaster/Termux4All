package com.github.jigokumaster.termux4all;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
// import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.*;

/**
 * A background job launched by Termux.
 */
public final class BackgroundJob {

    private static final String LOG_TAG = "termux-task";

    final Process mProcess;

    public BackgroundJob(String cwd, String fileToExecute, final String[] args, final TermuxService service) {
        String[] env = buildEnvironment(null, false, cwd);
        if (cwd == null) cwd = TermuxService.HOME_PATH;

        final String[] progArray = setupProcessArgs(fileToExecute, args);
        final String processDescription = Arrays.toString(progArray);

        Process process;
        try {
            process = Runtime.getRuntime().exec(progArray, env, new File(cwd));
        } catch (IOException e) {
            mProcess = null;
            // TODO: Visible error message?
            Log.e(LOG_TAG, "Failed running background job: " + processDescription, e);
            return;
        }

        mProcess = process;
        final int pid = getPid(mProcess);

        new Thread() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "[" + pid + "] starting: " + processDescription);
                InputStream stdout = mProcess.getInputStream();

                BufferedReader reader ;
                try
                {
                    reader = new BufferedReader(new InputStreamReader(stdout, "UTF-8"));
                }
                catch(java.io.UnsupportedEncodingException e)
                {
                    throw new AssertionError(e);
                }

                String line;
                try {
                    // FIXME: Long lines.
                    while ((line = reader.readLine()) != null) {
                        Log.i(LOG_TAG, "[" + pid + "] stdout: " + line);
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error reading output", e);
                }

                try {
                    int exitCode = mProcess.waitFor();
                    service.onBackgroundJobExited(BackgroundJob.this);
                    if (exitCode == 0) {
                        Log.i(LOG_TAG, "[" + pid + "] exited normally");
                    } else {
                        Log.w(LOG_TAG, "[" + pid + "] exited with code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }.start();


        new Thread() {
            @Override
            public void run() {
                InputStream stderr = mProcess.getErrorStream();
                BufferedReader reader ;
                try{

                    reader = new BufferedReader(new InputStreamReader(stderr, "UTF-8"));
                }
                catch(java.io.UnsupportedEncodingException e)
                {
                    throw new AssertionError(e);
                }

                String line;
                try {
                    // FIXME: Long lines.
                    while ((line = reader.readLine()) != null) {
                        Log.i(LOG_TAG, "[" + pid + "] stderr: " + line);
                    }
                } catch (IOException e) {
                    // Ignore.
                }
            }
        };
    }

    private static void addToEnvIfPresent(List<String> environment, String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.add(name + "=" + value);
        }
    }

    static String[] buildEnvironment(String shell, boolean failSafe, String cwd) {
        new File(TermuxService.HOME_PATH).mkdirs();

        if (cwd == null) cwd = TermuxService.HOME_PATH;

        List<String> environment = new ArrayList<>();
		Map<String, String> systemEnv = System.getenv();
		for(String k : systemEnv.keySet())
		{
			if(!k.equals("PATH"))
			{
				environment.add(String.format("%s=%s", k, systemEnv.get(k)));
			}
		}
		
		if(shell != null){
			environment.add("SHELL=" + shell);
		}
		else
		{
			environment.add("SHELL=/system/bin/sh");
		}
		
		environment.add("TERMINFO=" + TermuxService.PREFIX_PATH + "/share/terminfo");
        environment.add("TERM=xterm-256color");
        environment.add("HOME=" + TermuxService.HOME_PATH);
        environment.add("PREFIX=" + TermuxService.PREFIX_PATH);
        environment.add("BOOTCLASSPATH" + System.getenv("BOOTCLASSPATH"));
        environment.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        environment.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        environment.add("EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE"));
        // ANDROID_RUNTIME_ROOT and ANDROID_TZDATA_ROOT are required for `am` to run on Android Q
        addToEnvIfPresent(environment, "ANDROID_RUNTIME_ROOT");
        addToEnvIfPresent(environment, "ANDROID_TZDATA_ROOT");
        if (failSafe) {
            // Keep the default path so that system binaries can be used in the failsafe session.
            environment.add("PATH=" + System.getenv("PATH"));
        } else {
            /*if (shouldAddLdLibraryPath()) {
                environment.add("LD_LIBRARY_PATH=" + TermuxService.PREFIX_PATH + "/lib");
            }*/
		
			environment.add("LD_LIBRARY_PATH=" + System.getenv("LD_LIBRARY_PATH") +":" + TermuxService.PREFIX_PATH + "/lib");
            environment.add("LANG=en_US.UTF-8");
            environment.add("PATH=" + TermuxService.PREFIX_PATH + "/bin:" + TermuxService.PREFIX_PATH + "/bin/applets" + ":" + System.getenv("PATH"));
            environment.add("PWD=" + cwd);
            environment.add("TMPDIR=" + TermuxService.PREFIX_PATH + "/tmp");
			
        }

        return environment.toArray(new String[0]);
    }

    private static boolean shouldAddLdLibraryPath() {
		try{
			
			InputStreamReader fr = new InputStreamReader(new FileInputStream(TermuxService.PREFIX_PATH + "/etc/apt/sources.list"));
        	BufferedReader in = new BufferedReader(fr);
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("#") && line.contains("//termux.net stable")) {
                    return true;
                }
            }
            fr.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error trying to read sources.list", e);
        }
        return false;
    }

    public static int getPid(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            try {
                return f.getInt(p);
            } finally {
                f.setAccessible(false);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    static String[] setupProcessArgs(String fileToExecute, String[] args) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try {
            File file = new File(fileToExecute);
            FileInputStream in = new FileInputStream(file); {
                byte[] buffer = new byte[256];
                int bytesRead = in.read(buffer);
                if (bytesRead > 4) {
                    if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                        // Elf file, do nothing.
                    } else if (buffer[0] == '#' && buffer[1] == '!') {
                        // Try to parse shebang.
                        StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            char c = (char) buffer[i];
                            if (c == ' ' || c == '\n') {
                                if (builder.length() == 0) {
                                    // Skip whitespace after shebang.
                                } else {
                                    // End of shebang.
                                    String executable = builder.toString();
                                    if (executable.startsWith("/usr") || executable.startsWith("/bin")) {
                                        String[] parts = executable.split("/");
                                        String binary = parts[parts.length - 1];
                                        interpreter = TermuxService.PREFIX_PATH + "/bin/" + binary;
                                    }
									else
									{
										interpreter = "/system/bin/sh";
									}
                                    break;
                                }
                            } else {
                                builder.append(c);
                            }
                        }
                    } else {
                        // No shebang and no ELF, use standard shell.
                        interpreter = TermuxService.PREFIX_PATH + "/bin/sh";
                    }
                }
				
            }
			in.close();
        } catch (IOException e) {
            // Ignore.
        }

        List<String> result = new ArrayList<>();
        if (interpreter != null) result.add(interpreter);
        result.add(fileToExecute);
        if (args != null) Collections.addAll(result, args);
        return result.toArray(new String[0]);
    }

}
