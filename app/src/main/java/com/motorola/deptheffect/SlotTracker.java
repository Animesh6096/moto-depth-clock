package com.motorola.deptheffect;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Remembers which Modern clock slots (faces 101-106) already hold a photo. Personalize saves our
 * photo into a slot once, the next time it starts after we report that slot, and offers no way to
 * ask which slots are filled. So a slot is marked when the user applies a photo to it, and all
 * marks are cleared after the user wipes Personalize's data.
 */
public final class SlotTracker {
    private static final String PREFS = "depth";
    private static final String KEY_FILLED = "filled_slots";

    private SlotTracker() {}

    public static synchronized Set<Integer> filledSlots(Context context) {
        Set<Integer> slots = new HashSet<>();
        for (String id : prefs(context).getStringSet(KEY_FILLED, new HashSet<>())) {
            slots.add(Integer.parseInt(id));
        }
        return slots;
    }

    public static synchronized void markFilled(Context context, int faceId) {
        Set<String> filled = new HashSet<>(prefs(context).getStringSet(KEY_FILLED, new HashSet<>()));
        filled.add(String.valueOf(faceId));
        prefs(context).edit().putStringSet(KEY_FILLED, filled).apply();
    }

    /** Call after wiping Personalize's data (pm clear): every slot is empty again. */
    public static synchronized void reset(Context context) {
        prefs(context).edit().remove(KEY_FILLED).apply();
    }

    private static SharedPreferences prefs(Context context) {
        Context device = context.isDeviceProtectedStorage() ? context : context.createDeviceProtectedStorageContext();
        return device.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
