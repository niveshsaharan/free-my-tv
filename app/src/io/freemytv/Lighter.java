package io.freemytv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

/**
 * The lighter home screen (Speed > Lighter home screen): the drawer, All Apps, background and day/night changes are
 * instant and the focus quicker, the photos of the tabs you're not on go as soon as the home screen is hidden, and the
 * screensaver's photos stand still. "Automatic" turns it on for smaller TVs: little memory, Android 10 or older, or a
 * UI drawn at 720p.
 */
final class Lighter {
    private Lighter() {}

    private static int version = -1;
    private static boolean on;
    private static Boolean small;

    static boolean on() {
        if (version != Prefs.version) {
            version = Prefs.version;
            String v = Prefs.lighter();
            on = v.equals("on") || (v.equals("auto") && small());
            Spring.pace = on ? 0.7f : 1;
        }
        return on;
    }

    /** A smaller TV: low-RAM, under 2.5 GB, Android 10 or older, or a UI 1280 wide. Checked once. */
    static boolean small() {
        if (small == null) {
            Context c = Prefs.context();
            ActivityManager am = c == null ? null : (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mem);
            boolean lowRam = am != null && am.isLowRamDevice();
            small = lowRam || (mem.totalMem > 0 && mem.totalMem < 2_500_000_000L) || Build.VERSION.SDK_INT <= 29
                    || Backdrop.W <= 1280;
            AppLog.w("lighter: automatic = " + (small ? "on" : "off") + " (" + mem.totalMem / 1_000_000 + " MB, Android "
                    + Build.VERSION.RELEASE + ", " + Backdrop.W + " px UI" + (lowRam ? ", low RAM" : "") + ")");
        }
        return small;
    }
}
