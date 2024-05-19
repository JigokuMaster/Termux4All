package com.termux.terminal;

// import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
public abstract class TerminalOutput {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    public final void write(String data) {
        try{

            byte[] bytes = data.getBytes("UTF-8");
            write(bytes, 0, bytes.length);
        }
       catch(java.io.UnsupportedEncodingException e)
       {
                    throw new AssertionError(e);
       }

    }

    /** Write bytes to the terminal client. */
    public abstract void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void clipboardText(String text);

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    public abstract void onBell();

    public abstract void onColorsChanged();

}
