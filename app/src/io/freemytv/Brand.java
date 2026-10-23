package io.freemytv;

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
 * Your brand (brand/, built in by build.sh as BrandConfig): the name wherever the app says "Free My TV", a tagline, an
 * accent color and a logo. Without brand/ these are Free My TV's own and nothing changes. The package (io.freemytv),
 * the records on the TV and the log tag stay, so a branded build installs over the stock one as an update.
 */
final class Brand {
    private Brand() {}

    static final String STOCK = "Free My TV";
    static final String NAME = BrandConfig.NAME.isEmpty() ? STOCK : BrandConfig.NAME;
    private static final SparseArray<Bitmap> LOGOS = new SparseArray<>();

    /** The line under the name in the settings: the tagline, or Free My TV's own. */
    static String tagline() {
        return BrandConfig.TAGLINE.isEmpty() ? Ui.tr("Your TV's home screen and screensaver") : BrandConfig.TAGLINE;
    }

    /** "Free My TV 1.2", or "Lumen 1.2 · based on Free My TV". */
    static String version(String versionName) {
        String v = versionName == null ? NAME : NAME + " " + versionName;
        return NAME.equals(STOCK) ? v : v + " · " + Ui.trf("based on {app}", "app", STOCK);
    }

    /** The accent where it stands out from the background (3:1, the least for controls), else the usual color. */
    static int accent(int usual, int background) {
        int a = BrandConfig.ACCENT;
        return a != 0 && contrast(a, background) >= 3 ? a : usual;
    }

    static boolean hasAccent() {
        return BrandConfig.ACCENT != 0;
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
     * The catalogue with the name in place of "Free My TV", and the English texts that hold it (English has no
     * catalogue of its own): done once per language change, so a lookup costs nothing more.
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

    /** The logo fitted into size × size px (its shape: drawn tinted), or null without one. Decoded once per size. */
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
