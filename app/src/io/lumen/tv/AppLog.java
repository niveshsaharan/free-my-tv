package io.lumen.tv;

import android.util.Log;

/** The app's log (adb logcat -s Lumen). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("Lumen", message);
    }
}
