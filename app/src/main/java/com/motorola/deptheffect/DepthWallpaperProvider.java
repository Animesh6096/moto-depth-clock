package com.motorola.deptheffect;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.IOException;

/**
 * Answers Personalize's call("request_current_wallpaper") the way Motorola's DepthEffect app does:
 * a Bundle with the Modern clock face id plus file descriptors for the background and foreground.
 */
public class DepthWallpaperProvider extends ContentProvider {
    private static final String TAG = "DepthProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Log.i(TAG, "call: method=" + method + " arg=" + arg + " extras=" + describe(extras));
        if (!"request_current_wallpaper".equals(method)) {
            return null;
        }
        // Personalize calls without extras only while starting up, which is when it adopts the style
        // we report and saves our layers into it. Later calls carry a random request "id".
        if (extras == null) {
            DepthStore.clearRestartPending(getContext());
        }
        try {
            File background = DepthStore.background(getContext());
            Bundle result = new Bundle();
            result.putInt("current_modern_clock_face_id", DepthStore.clockFaceId(getContext()));
            // Personalize reads this as the Modern clock's ARGB colour when it fills a slot.
            result.putInt("current_modern_color_id", DepthStore.clockColor(getContext()));
            result.putParcelable("current_modern_wallpaper_pfd_bg", open(background));
            result.putParcelable("current_modern_wallpaper_pfd_fg", open(DepthStore.foreground(getContext())));
            result.putParcelable("current_animated_wallpaper_pfd_thumb", open(background));
            return result;
        } catch (IOException e) {
            Log.e(TAG, "call: failed to open images", e);
            return null;
        }
    }

    private static String describe(Bundle extras) {
        if (extras == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        for (String key : extras.keySet()) {
            sb.append(key).append('=').append(extras.get(key)).append(' ');
        }
        return sb.append('}').toString();
    }

    private static ParcelFileDescriptor open(File file) throws IOException {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
