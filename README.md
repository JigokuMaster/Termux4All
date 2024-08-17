Termux4all is a backport of [termux-app v0.82 ](https://github.com/termux/termux-app) to older Android versions.

Termux4all is just a Terminal Emulator and doesn't include a package manager like pkg in termux.


Termux4all requires (Android 2.1+) and has been tested on the following Android versions/devices:

* Android 2.1 (emulator only)
* Android 2.2 (emulator only)
* Android 2.3.6 (real device)
* Android 4.1 (real device)
* Android 5.1 (real device).

Termux4all was originally developed for personal use. so i have built and included just few tools, 
the bootstrap contains:

* bash - v5.1.16 (built with musl-libc)
* nano - v4.5 (built with musl-libc)
* busybox - v1.34.1
* aria2c - v1.36.0
* curl - v7.68
* [nnn - v3.7] (https://github.com/jarun/nnn)
* [sl] (https://github.com/mtoyoda/sl/) 
* termux-reload-settings, termux-wake-lock, termux-wake-unlock

# known issues:

* aria2c doesn't work on Android 2.2 and 2.1

* no zoom gesture on Android 2.1 

# Downloads:

see [the releases page](https://github.com/JigokuMaster/Termux4All/releases)

* Termux4All-armeabi.apk is for Android 4.0 and earlier

* Termux4All-armv7a-PIE.apk is for Android 4.1 - 5.x
