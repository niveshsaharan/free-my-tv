package io.lumen.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.SparseArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The app's look (brand/, built in by build.sh as BrandConfig): its name, a tagline, an accent color and a logo. The
 * texts say "Lumen"; another name in brand/ takes its place everywhere. Without brand/: the name alone, no accent or
 * logo. The package (io.lumen.tv), the records on the TV and the log tag never change with it.
 */
final class Brand {
    private Brand() {}

    static final String STOCK = "Lumen";
    static final String NAME = BrandConfig.NAME.isEmpty() ? STOCK : BrandConfig.NAME;
    private static final SparseArray<Bitmap> LOGOS = new SparseArray<>(), MASKS = new SparseArray<>();

    /** The line under the name in the settings: the tagline, or a plain description. */
    static String tagline() {
        return BrandConfig.TAGLINE.isEmpty() ? Ui.tr("Your TV's home screen and screensaver") : BrandConfig.TAGLINE;
    }

    /** "Lumen 1.2". */
    static String version(String versionName) {
        return versionName == null ? NAME : NAME + " " + versionName;
    }

    /**
     * The accent on this background: darkened (or, on a dark one, lightened) just enough to stand out (3:1, the least
     * for large text and controls). The usual color without an accent. Worked out once, when a screen is made.
     */
    static int accent(int usual, int background) {
        int a = BrandConfig.ACCENT;
        if (a == 0) return usual;
        int toward = luminance(background) > 0.18 ? Color.BLACK : Color.WHITE;
        for (int step = 0; step <= 10; step++) {
            int c = mix(a, toward, step * 0.06f);
            if (contrast(c, background) >= 3) return c;
        }
        return usual;
    }

    private static int mix(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * With another name in brand/: the catalogue with it in place of "Lumen", and the English texts that hold it
     * (English has no catalogue of its own). Done once per language change, so a lookup costs nothing more.
     */
    static Map<String, String> rename(Map<String, String> catalogue, Map<String, String> english) {
        if (NAME.equals(STOCK)) return catalogue;
        HashMap<String, String> out = new HashMap<>(catalogue.size() * 2 + 32);
        for (Map.Entry<String, String> e : catalogue.entrySet()) out.put(e.getKey(), e.getValue().replace(STOCK, NAME));
        if (english != null) {
            for (Map.Entry<String, String> e : english.entrySet()) {
                if (!out.containsKey(e.getKey()) && e.getValue().contains(STOCK)) {
                    out.put(e.getKey(), e.getValue().replace(STOCK, NAME));
                }
            }
        }
        return out;
    }

    static boolean renamed() {
        return !NAME.equals(STOCK);
    }

    /**
     * The logo's shape alone (ALPHA_8), fitted into size × size px, or null without one: drawn with a Paint, it takes the
     * Paint's color, so a tint that follows day and night costs no new filter.
     */
    static Bitmap logoMask(Context c, int size) {
        synchronized (LOGOS) {
            Bitmap m = MASKS.get(size);
            if (m != null) return m;
        }
        Bitmap logo = logo(c, size);
        if (logo == null) return null;
        Bitmap m = logo.extractAlpha();
        synchronized (LOGOS) {
            MASKS.put(size, m);
        }
        return m;
    }

    /** The logo fitted into size × size px, in its own colors, or null without one. Decoded once per size. */
    static Bitmap logo(Context c, int size) {
        if (!BrandConfig.LOGO || size <= 0) return null;
        synchronized (LOGOS) {
            Bitmap b = LOGOS.get(size);
            if (b != null) return b;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = c.getAssets().open("brand/logo.png")) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= size * 2) opts.inSampleSize *= 2;
                Bitmap src;
                try (InputStream in = c.getAssets().open("brand/logo.png")) {
                    src = BitmapFactory.decodeStream(in, null, opts);
                }
                if (src == null) return null;
                float scale = (float) size / Math.max(src.getWidth(), src.getHeight());
                b = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * scale)),
                        Math.max(1, Math.round(src.getHeight() * scale)), true);
                if (b != src) src.recycle();
                LOGOS.put(size, b);
                return b;
            } catch (IOException | RuntimeException e) {
                AppLog.w("brand logo: " + e.getMessage());
                return null;
            }
        }
    }
}
