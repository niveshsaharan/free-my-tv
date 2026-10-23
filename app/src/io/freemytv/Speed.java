package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Speed screen (Free My TV > Speed): the apps the person turns off, the Assistant and the TV's animations, all part
 * of Free My TV mode (HomeMode applies them; Original undoes them).
 *  - The choices (speed-choices.json) are written only by the settings screen (:home) and read by HomeMode (main
 *    process). A choice is kept even when it's "on": that tells "turned back on" from "never picked".
 *  - The list of apps (speed-apps.json) is made in the main process, which has the TV's ADB, and read by the screen.
 * Only apps you can open (and the promo apps HomeMode turns off anyway) are offered, never the TV's system pieces: a
 * crash loop in one of those can make Android reset the TV. See lock().
 */
final class Speed {
    private Speed() {}

    static final String NORMAL = "normal", FASTER = "faster", ON = "on", OFF = "off";
    /** Why an app can't be picked: not listed at all, or listed with the reason. */
    static final String HIDE = "hide", WIFI = "wifi", KEYBOARD = "keyboard", ACCESSIBILITY = "accessibility", CAST = "cast",
            BEFORE = "before";
    private static final String CHOICES = "speed-choices.json", LIST = "speed-apps.json";
    /** Casting YouTube from a phone opens this app: it stays on (casting must keep working). */
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][\\w.]*");
    private static final Object CHOSEN = new Object(), FILES = new Object(), SCAN = new Object();
    /** When the last list was made (uptime): a second request right after (Speed, then Turn off apps) uses it. */
    private static long scannedAt;
    /** :home's copy of the choices, the newest (only :home changes them); the file catches up on WRITER. */
    private static JSONObject chosen;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    // MARK: - Choices

    static JSONObject choices(Context c) {
        synchronized (CHOSEN) {
            if (chosen != null) return copy(chosen);
        }
        return read(new File(c.getFilesDir(), CHOICES));
    }

    /** on = false: turned off in Free My TV mode; on = true: left on (or turned back on), even if a list turns it off. */
    static void setApp(Context c, String pkg, boolean on) {
        if (!PACKAGE.matcher(pkg).matches()) return;
        change(c, o -> {
            JSONObject apps = o.optJSONObject("apps");
            if (apps == null) o.put("apps", apps = new JSONObject());
            apps.put(pkg, on ? ON : OFF);
        });
    }

    static void setAssistant(Context c, boolean on) {
        change(c, o -> o.put("assistant", on ? ON : OFF));
    }

    /** NORMAL (the TV's own), FASTER (half the time) or OFF. */
    static void setAnimations(Context c, String value) {
        change(c, o -> {
            if (NORMAL.equals(value)) o.remove("animations");
            else o.put("animations", value);
        });
    }

    private interface Edit { void apply(JSONObject o) throws JSONException; }

    /**
     * Saves a change and asks the main process to apply it (soon after the last of several quick changes). The file is
     * written on WRITER, in order: syncing it can take a while on a TV's storage, and the screen shouldn't wait.
     */
    private static void change(Context c, Edit edit) {
        Context app = c.getApplicationContext();
        JSONObject snapshot;
        synchronized (CHOSEN) {
            JSONObject o = chosen != null ? chosen : read(new File(app.getFilesDir(), CHOICES));
            try {
                edit.apply(o);
            } catch (JSONException e) {
                return;
            }
            chosen = o;
            snapshot = copy(o);
        }
        WRITER.execute(() -> {
            write(app, CHOICES, snapshot);
            ModeJob.schedule(app, ModeJob.SPEED, null, 1_500);
        });
    }

    private static JSONObject copy(JSONObject o) {
        try {
            return new JSONObject(o.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** The picked apps with this value (ON or OFF). */
    static Set<String> apps(JSONObject choices, String value) {
        Set<String> out = new HashSet<>();
        JSONObject apps = choices.optJSONObject("apps");
        if (apps == null) return out;
        for (java.util.Iterator<String> it = apps.keys(); it.hasNext(); ) {
            String pkg = it.next();
            if (PACKAGE.matcher(pkg).matches() && value.equals(apps.optString(pkg))) out.add(pkg);
        }
        return out;
    }

    static String app(JSONObject choices, String pkg) {
        JSONObject apps = choices.optJSONObject("apps");
        return apps == null ? "" : apps.optString(pkg);
    }

    /** The Assistant goes off: the person's choice here, or else disable_assistant in config.properties. */
    static boolean assistantOff(JSONObject choices) {
        String v = choices.optString("assistant");
        return v.isEmpty() ? Config.DISABLE_ASSISTANT : OFF.equals(v);
    }

    static String animations(JSONObject choices) {
        String v = choices.optString("animations");
        return FASTER.equals(v) || OFF.equals(v) ? v : NORMAL;
    }

    // MARK: - What can be turned off (main process)

    /** What the TV says about its apps, read once for a list (and again before turning off a picked app). */
    static final class Facts {
        Set<String> all, off, wifi;
        final Set<String> typing = new HashSet<>(), access = new HashSet<>(), homes = new HashSet<>(),
                inputs = new HashSet<>(), mine = new HashSet<>(), keptOff = new HashSet<>();
    }

    static Facts facts(Context c, Adb adb, JSONObject state) throws IOException {
        Facts f = new Facts();
        f.all = HomeMode.packages(adb, "");
        f.off = HomeMode.packages(adb, "-d");
        f.wifi = HomeMode.wifiOwners(adb);
        for (String key : new String[] {"enabled_input_methods", "default_input_method"}) {
            packagesIn(adb.shell("settings get secure " + key), f.typing);
        }
        for (String key : new String[] {"enabled_accessibility_services", "tts_default_synth"}) {
            packagesIn(adb.shell("settings get secure " + key), f.access);
        }
        // Home screens, turned off ones too (the TV's own is, in Free My TV mode).
        packagesIn(adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME"), f.homes);
        for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS)) {
            f.homes.add(ri.activityInfo.packageName);
        }
        f.homes.add(HomeMode.GOOGLE_SETUP);
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        try {
            if (tim != null) for (TvInputInfo info : tim.getTvInputList()) f.inputs.add(info.getServiceInfo().packageName);
        } catch (RuntimeException ignored) {
        }
        f.mine.addAll(HomeMode.core(state));
        f.keptOff.addAll(HomeMode.stringList(state, "keptOff"));
        f.keptOff.removeAll(HomeMode.stringList(state, "disabled"));
        // Off now, and not by us: the person (or the TV) turned it off.
        for (String pkg : f.off) if (!HomeMode.stringList(state, "disabled").contains(pkg)) f.keptOff.add(pkg);
        return f;
    }

    /** "com.x/.Service:com.y/.Ime;12" or "com.z": the package names in a setting's value. */
    private static void packagesIn(String value, Set<String> out) {
        Matcher m = Pattern.compile("([A-Za-z][\\w.]*)(?=/|$|[:,;\\s])").matcher(value == null ? "" : value.trim());
        while (m.find()) if (m.group(1).contains(".")) out.add(m.group(1));
    }

    // The TV's own pieces, by name: hidden even when they have a launcher entry. Hiding too much is the safe direction.
    private static final String[] SYSTEM_PREFIXES = {"android.", "com.android.", "mitv.", "vendor.",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.ext.", "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller", "com.google.android.webview", "com.google.android.tv.",
        "com.google.android.inputmethod.", "com.google.android.leanback.ime", "com.google.android.tts",
        "com.google.android.tungsten.", "com.google.android.syncadapters.", "com.google.android.backuptransport",
        "com.google.android.partnersetup", "com.google.android.configupdater", "com.google.android.apps.mediashell",
        "com.droidlogic.", "com.amlogic.", "com.mstar.", "com.mediatek.", "com.realtek.", "com.rtk.", "com.hisilicon.",
        "com.novatek.", "org.codeaurora.", "com.qualcomm.", "com.dolby."};
    private static final String[] SYSTEM_WORDS = {"factory", "provider", "overlay", "autoinstalls", "setup", "update",
        "upgrade", "ota", "tvinput", "livetv", "tuner", "bluetooth", "autopair", "inputmethod", "webview", "miracast",
        "cec", "hdmi", "settings", "systemui"};

    /** HIDE (never listed), a reason it stays on (listed, can't be changed), or "" (it can be turned off). */
    static String lock(Context c, String pkg, Facts f) {
        if (pkg.equals(HomeMode.PKG) || HomeMode.isProtected(pkg) || Arrays.asList(HomeMode.ASSISTANT).contains(pkg)
                || f.mine.contains(pkg)) return HIDE;
        ApplicationInfo ai;
        try {
            ai = c.getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return HIDE;
        }
        // A crash loop in a system-uid or persistent app makes Android offer to reset the TV (rescue party).
        if (ai.uid < Process.FIRST_APPLICATION_UID || (ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return HIDE;
        if (f.homes.contains(pkg) || f.inputs.contains(pkg) || Arrays.asList(HomeApps.LIVE_TV_APPS).contains(pkg)) return HIDE;
        if (!HomeMode.isDebloat(pkg)) {
            for (String p : SYSTEM_PREFIXES) if (pkg.startsWith(p)) return HIDE;
            String lower = pkg.toLowerCase(Locale.ROOT);
            for (String w : SYSTEM_WORDS) if (lower.contains(w)) return HIDE;
        }
        if (f.wifi.contains(pkg)) return WIFI;
        if (f.typing.contains(pkg)) return KEYBOARD;
        if (f.access.contains(pkg)) return ACCESSIBILITY;
        if (pkg.equals(YOUTUBE)) return CAST;
        if (f.keptOff.contains(pkg)) return BEFORE;
        return "";
    }

    /** The picked apps that can't be turned off now (a new keyboard, a saved Wi-Fi…): HomeMode skips them. */
    static Set<String> blocked(Context c, Adb adb, JSONObject state, Set<String> picks) throws IOException {
        Set<String> out = new HashSet<>();
        if (picks.isEmpty()) return out;
        Facts f = facts(c, adb, state);
        for (String pkg : picks) {
            String lock = lock(c, pkg, f);
            if (!lock.isEmpty() && !lock.equals(BEFORE)) out.add(pkg);
        }
        return out;
    }

    // MARK: - The list (speed-apps.json)

    /** Makes the list in the main process (ModeJob.SCAN): with the TV's ADB, asking to allow it if needed. */
    static void scanNow(Context app) {
        synchronized (SCAN) {
            if (scannedAt != 0 && SystemClock.uptimeMillis() - scannedAt < 10_000) {
                // The same list, marked as new, so the screen stops waiting for one.
                JSONObject list = list(app);
                try {
                    list.put("at", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                write(app, LIST, list);
                return;
            }
            try (Adb adb = Adb.connect(app, 60_000, () -> writeList(app, "waiting", true))) {
                scan(app, adb, HomeMode.state(app));
                scannedAt = SystemClock.uptimeMillis();
            } catch (Exception e) {
                writeList(app, "error", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
    }

    /**
     * Apps that open from a launcher (TV or phone, turned off ones too), the promo apps on HomeMode's list, and whatever
     * was picked before; each with its state, its memory now and why it can't change, if so.
     */
    static void scan(Context c, Adb adb, JSONObject state) throws IOException {
        synchronized (SCAN) {   // one at a time: a later list never gets replaced by an older one
            scanLocked(c, adb, state);
        }
    }

    private static void scanLocked(Context c, Adb adb, JSONObject state) throws IOException {
        Facts f = facts(c, adb, state);
        PackageManager pm = c.getPackageManager();
        Set<String> candidates = new LinkedHashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),
                    PackageManager.MATCH_DISABLED_COMPONENTS)) candidates.add(ri.activityInfo.packageName);
        }
        candidates.addAll(Arrays.asList(HomeMode.DEBLOAT));
        JSONObject choices = choices(c);
        candidates.addAll(apps(choices, OFF));
        candidates.addAll(apps(choices, ON));
        candidates.addAll(HomeMode.stringList(state, "disabled"));
        candidates.retainAll(f.all);
        Map<String, Long> memory = memory(adb);
        List<JSONObject> rows = new ArrayList<>();
        for (String pkg : candidates) {
            String lock = lock(c, pkg, f);
            if (lock.equals(HIDE)) continue;
            String label;
            try {
                label = String.valueOf(pm.getApplicationInfo(pkg, 0).loadLabel(pm)).trim();
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            Long kb = memory.get(pkg);
            try {
                rows.add(new JSONObject().put("pkg", pkg).put("label", label.isEmpty() ? pkg : label)
                        .put("off", f.off.contains(pkg)).put("mb", kb == null ? 0 : kb / 1024)
                        .put("lock", lock).put("suggested", HomeMode.isDebloat(pkg)));
            } catch (JSONException ignored) {
            }
        }
        // The ones Free My TV turns off anyway first, then by name.
        Collections.sort(rows, (a, b) -> a.optBoolean("suggested") != b.optBoolean("suggested")
                ? (a.optBoolean("suggested") ? -1 : 1)
                : a.optString("label").compareToIgnoreCase(b.optString("label")));
        JSONObject out = new JSONObject();
        try {
            out.put("at", System.currentTimeMillis()).put("apps", new JSONArray(rows));
        } catch (JSONException ignored) {
        }
        write(c, LIST, out);
        AppLog.w("speed: " + rows.size() + " apps listed");
    }

    /** Memory each app uses now (resident, all its processes), in KB: a hint, not an exact measure. */
    private static Map<String, Long> memory(Adb adb) {
        Map<String, Long> out = new HashMap<>();
        try {
            for (String line : adb.shell("ps -A -o RSS,NAME").split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2 || !parts[0].matches("\\d+")) continue;
                int colon = parts[1].indexOf(':');
                String pkg = colon > 0 ? parts[1].substring(0, colon) : parts[1];
                Long sum = out.get(pkg);
                out.put(pkg, (sum == null ? 0 : sum) + Long.parseLong(parts[0]));
            }
        } catch (IOException | RuntimeException e) {
            AppLog.w("speed: memory: " + e.getMessage());
        }
        return out;
    }

    /** The list as the settings screen reads it: {at, apps[]} or {waiting} or {error}. */
    static JSONObject list(Context c) {
        return read(new File(c.getFilesDir(), LIST));
    }

    static long listStamp(Context c) {
        File f = new File(c.getFilesDir(), LIST);
        return f.lastModified() * 31 + f.length();
    }

    private static void writeList(Context c, String key, Object value) {
        try {
            write(c, LIST, new JSONObject().put("at", System.currentTimeMillis()).put(key, value));
        } catch (JSONException ignored) {
        }
    }

    // MARK: - Files (each with one writer; a temporary file, synced, then renamed)

    private static JSONObject read(File f) {
        if (!f.exists()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            return new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return new JSONObject();
        }
    }

    private static void write(Context c, String name, JSONObject o) {
        synchronized (FILES) {   // the threads of a process share the temporary file's name
            File dir = c.getFilesDir(), tmp = new File(dir, name + "." + Process.myPid() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                AppLog.w("speed: could not save " + name + ": " + e.getMessage());
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(new File(dir, name))) AppLog.w("speed: could not replace " + name);
        }
    }
}
