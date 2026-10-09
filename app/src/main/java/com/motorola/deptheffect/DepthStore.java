package com.motorola.deptheffect;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Stores each Modern style's depth wallpaper as two full-screen layers: the photo (background) and
 * the subject cut-out on a transparent background (foreground). Layers are kept per style because
 * Personalize saves a style's cut-out only once; our background must stay the one that cut-out was
 * made from, or the two layers drift apart on the lock screen.
 *
 * Uses device-protected storage because the lock screen wallpaper is drawn before the user unlocks
 * after a reboot.
 */
public final class DepthStore {
    private static final String BACKGROUND = "bg.jpg";
    private static final String FOREGROUND = "fg.png";
    private static final String LEGACY_BACKGROUND = "depth_bg.jpg";
    private static final String LEGACY_FOREGROUND = "depth_fg.png";
    private static final String PREFS = "depth";
    private static final String KEY_CLOCK_FACE_ID = "clock_face_id";
    private static final String KEY_CLOCK_COLOR = "clock_color";
    private static final String KEY_RESTART_PENDING = "restart_pending";
    /** Modern clock face 3; Personalize's default on this phone. */
    public static final int DEFAULT_CLOCK_FACE_ID = 103;
    private static final int DEFAULT_WIDTH = 1220;
    private static final int DEFAULT_HEIGHT = 2712;

    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DepthStore() {}

    /** Background layer of the current style (a plain gradient if the style has no photo yet). */
    public static synchronized File background(Context context) throws IOException {
        File file = new File(slotDir(context, clockFaceId(context)), BACKGROUND);
        return file.exists() ? file : placeholders(context)[0];
    }

    /** Foreground layer of the current style (fully transparent if the style has no photo yet). */
    public static synchronized File foreground(Context context) throws IOException {
        File file = new File(slotDir(context, clockFaceId(context)), FOREGROUND);
        return file.exists() ? file : placeholders(context)[1];
    }

    /** Background layer of a specific style, for thumbnails; null if the style has no photo. */
    public static File storedBackground(Context context, int faceId) {
        File file = new File(slotDir(context, faceId), BACKGROUND);
        return file.exists() ? file : null;
    }

    public static boolean hasPhoto(Context context, int faceId) {
        return new File(slotDir(context, faceId), FOREGROUND).exists();
    }

    /** Saves both layers for a style, makes it the current style, and redraws the wallpaper. */
    public static void save(Context context, int faceId, Bitmap background, Bitmap foreground) throws IOException {
        synchronized (DepthStore.class) {
            File dir = slotDir(context, faceId);
            write(background, Bitmap.CompressFormat.JPEG, 95, new File(dir, BACKGROUND));
            write(foreground, Bitmap.CompressFormat.PNG, 100, new File(dir, FOREGROUND));
        }
        setClockFaceId(context, faceId);
    }

    public static int clockFaceId(Context context) {
        migrateLegacyLayers(context);
        return prefs(context).getInt(KEY_CLOCK_FACE_ID, DEFAULT_CLOCK_FACE_ID);
    }

    /**
     * Switches the style we report to Personalize. Personalize only adopts it on its next start, so
     * this also raises the "restart needed" flag; the wallpaper switches immediately.
     */
    public static void setClockFaceId(Context context, int id) {
        prefs(context).edit().putInt(KEY_CLOCK_FACE_ID, id).putBoolean(KEY_RESTART_PENDING, true).commit();
        notifyListeners();
    }

    public static boolean isRestartPending(Context context) {
        return prefs(context).getBoolean(KEY_RESTART_PENDING, false);
    }

    /** Called when Personalize starts and pulls our layers, which is when a pending change lands. */
    public static void clearRestartPending(Context context) {
        prefs(context).edit().putBoolean(KEY_RESTART_PENDING, false).apply();
    }

    /** Modern clock colour (ARGB) handed to Personalize when it fills a slot; white by default. */
    public static int clockColor(Context context) {
        return prefs(context).getInt(KEY_CLOCK_COLOR, Color.WHITE);
    }

    public static void setClockColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_CLOCK_COLOR, color).apply();
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            mainHandler.post(listener);
        }
    }


    /**
     * Versions before 2.0 kept one pair of layers for all styles. They belong to the style that was
     * current when they were saved, so move them there once.
     */
    private static synchronized void migrateLegacyLayers(Context context) {
        File root = deviceContext(context).getFilesDir();
        File legacyBackground = new File(root, LEGACY_BACKGROUND);
        File legacyForeground = new File(root, LEGACY_FOREGROUND);
        if (!legacyBackground.exists() || !legacyForeground.exists()) {
            return;
        }
        File dir = slotDir(context, prefs(context).getInt(KEY_CLOCK_FACE_ID, DEFAULT_CLOCK_FACE_ID));
        if (!new File(dir, FOREGROUND).exists()) {
            legacyBackground.renameTo(new File(dir, BACKGROUND));
            legacyForeground.renameTo(new File(dir, FOREGROUND));
        } else {
            legacyBackground.delete();
            legacyForeground.delete();
        }
    }

    /** Gradient background and empty foreground, shared by styles that have no photo yet. */
    private static File[] placeholders(Context context) throws IOException {
        File root = deviceContext(context).getFilesDir();
        File background = new File(root, "placeholder_bg.jpg");
        File foreground = new File(root, "placeholder_fg.png");
        if (!background.exists() || !foreground.exists()) {
            Bitmap bitmap = Bitmap.createBitmap(DEFAULT_WIDTH, DEFAULT_HEIGHT, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint();
            paint.setShader(new LinearGradient(0, 0, 0, DEFAULT_HEIGHT,
                    Color.rgb(20, 24, 40), Color.rgb(60, 50, 90), Shader.TileMode.CLAMP));
            new Canvas(bitmap).drawRect(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT, paint);
            write(bitmap, Bitmap.CompressFormat.JPEG, 95, background);
            bitmap.eraseColor(Color.TRANSPARENT);
            write(bitmap, Bitmap.CompressFormat.PNG, 100, foreground);
            bitmap.recycle();
        }
        return new File[] {background, foreground};
    }

    /** Writes to a temp file first so readers never see a half-written image. */
    private static void write(Bitmap bitmap, Bitmap.CompressFormat format, int quality, File target)
            throws IOException {
        File temp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!bitmap.compress(format, quality, out)) {
                throw new IOException("compress failed for " + target.getName());
            }
        }
        if (!temp.renameTo(target)) {
            throw new IOException("rename failed for " + target.getName());
        }
    }

    private static File slotDir(Context context, int faceId) {
        File dir = new File(deviceContext(context).getFilesDir(), "slot_" + faceId);
        dir.mkdirs();
        return dir;
    }

    private static SharedPreferences prefs(Context context) {
        return deviceContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Context deviceContext(Context context) {
        return context.isDeviceProtectedStorage() ? context : context.createDeviceProtectedStorageContext();
    }
}
