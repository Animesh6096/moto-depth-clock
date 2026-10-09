package com.motorola.deptheffect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Approximates Personalize's Modern clock for the editor preview. Personalize ships three Modern
 * layouts (layout_modern_1/2/3 with a back and a front part). Boxes are fractions of a 1220x2712
 * screen measured from Edge 60 Pro lock screen captures; fonts come from Personalize's assets.
 */
final class ModernClockPreview {
    private static final String TAG = "ClockPreview";
    private static final String PERSONALIZE = "com.motorola.personalize";

    /** Layout 1: "HHMM" on one row, date below on the left. */
    private static final RectF L1_TIME = box(102, 190, 1125, 814);
    private static final RectF L1_DATE = box(170, 860, 700, 920);
    /** Layout 2: hours (behind), minutes (in front), weekday pill on the right. */
    private static final RectF L2_HOURS = box(237, 217, 986, 719);
    private static final RectF L2_MINUTES = box(230, 888, 983, 1397);
    private static final RectF L2_PILL = box(1040, 232, 1130, 556);
    /** Layout 3: date on top, hours (behind), minutes (in front). */
    private static final RectF L3_DATE = box(300, 170, 930, 250);
    private static final RectF L3_HOURS = box(230, 360, 1004, 936);
    private static final RectF L3_MINUTES = box(271, 1071, 1017, 1654);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface lightFont;
    private final Typeface lineStackedFont;
    private final Typeface timeStackedFont;

    ModernClockPreview(Context context) {
        lightFont = loadPersonalizeFont(context, "font/Moto_Light_1.ttf");
        lineStackedFont = loadPersonalizeFont(context, "font/Moto_Line_Stacked.ttf");
        timeStackedFont = loadPersonalizeFont(context, "font/Moto_Time_Stacked.ttf");
    }

    /**
     * Which layout a face uses, mirroring Personalize's createModernLayoutHolderById:
     * 101 is one row, 103 is stacked with the date on top, 102 and 104-106 are stacked with a day pill.
     */
    static int layoutOf(int faceId) {
        if (faceId == 101) {
            return 1;
        }
        return faceId == 103 ? 3 : 2;
    }

    /**
     * Font per face as seen on the lock screen: 101 thin Light 1, 103 Time Stacked, and the stacked
     * layout faces (102, 104-106) the triple-line Line Stacked. Personalize's
     * getModernClockFaceTypeFace suggests Light 1 for 104-106, but face 105 renders triple-line.
     */
    private Typeface fontOf(int faceId) {
        if (faceId == 101) {
            return lightFont;
        }
        return faceId == 103 ? timeStackedFont : lineStackedFont;
    }

    /** Loads a clock font from Personalize's assets, the same files its lock screen clock uses. */
    static Typeface loadPersonalizeFont(Context context, String asset) {
        try {
            return Typeface.createFromAsset(context.createPackageContext(PERSONALIZE, 0).getAssets(), asset);
        } catch (Exception e) {
            Log.w(TAG, "Personalize font unavailable, using fallback: " + asset, e);
            return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        }
    }

    /** The part of the clock Personalize draws behind the subject, in screen pixels. */
    static RectF backLayerBox(int faceId, int width, int height) {
        switch (layoutOf(faceId)) {
            case 1:
                return scaled(L1_TIME, width, height);
            case 2:
                return scaled(L2_HOURS, width, height);
            default:
                return scaled(L3_HOURS, width, height);
        }
    }

    /** Parts of the clock that sit behind the subject. */
    void drawBehind(Canvas canvas, int faceId, int color, int width, int height) {
        switch (layoutOf(faceId)) {
            case 1:
                // Style 1 interleaves: digits 1 and 3 in the chosen colour behind the subject.
                drawRowDigits(canvas, faceId, color, true, width, height);
                drawText(canvas, time("EEE, MMM d").toUpperCase(Locale.getDefault()), L1_DATE,
                        Typeface.DEFAULT_BOLD, color, width, height);
                break;
            case 2:
                drawText(canvas, time("hh"), L2_HOURS, fontOf(faceId), withAlpha(color, 140), width, height);
                break;
            default:
                drawText(canvas, time("EEE, MMM d").toUpperCase(Locale.getDefault()), L3_DATE,
                        Typeface.create("sans-serif-medium", Typeface.NORMAL), Color.WHITE, width, height);
                drawText(canvas, time("hh"), L3_HOURS, fontOf(faceId), withAlpha(color, 140), width, height);
                break;
        }
    }

    /** Parts of the clock drawn over the subject. */
    void drawInFront(Canvas canvas, int faceId, int color, int width, int height) {
        switch (layoutOf(faceId)) {
            case 1:
                // ...and digits 2 and 4 in white in front of it.
                drawRowDigits(canvas, faceId, Color.WHITE, false, width, height);
                break;
            case 2:
                drawText(canvas, time("mm"), L2_MINUTES, fontOf(faceId), color, width, height);
                drawPill(canvas, width, height);
                break;
            default:
                drawText(canvas, time("mm"), L3_MINUTES, fontOf(faceId), color, width, height);
                break;
        }
    }

    /**
     * Draws style 1's "HHMM" row, laid out as one string but painting only every other digit:
     * the even positions (1st, 3rd) when {@code backDigits}, otherwise the odd ones (2nd, 4th).
     * This matches the lock screen, where Personalize splits the row across its back and front layers.
     */
    private void drawRowDigits(Canvas canvas, int faceId, int color, boolean backDigits, int width, int height) {
        String text = time("hhmm");
        RectF box = scaled(L1_TIME, width, height);
        paint.setTypeface(fontOf(faceId));
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(100f);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        if (bounds.isEmpty()) {
            return;
        }
        paint.setTextSize(100f * Math.min(box.height() / bounds.height(), box.width() / bounds.width()));
        paint.getTextBounds(text, 0, text.length(), bounds);
        float x = box.centerX() - bounds.exactCenterX();
        float y = box.centerY() - bounds.exactCenterY();
        paint.setColor(color);
        for (int i = backDigits ? 0 : 1; i < text.length(); i += 2) {
            canvas.drawText(text, i, i + 1, x + paint.measureText(text, 0, i), y, paint);
        }
    }

    private void drawPill(Canvas canvas, int width, int height) {
        RectF pill = scaled(L2_PILL, width, height);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200, 40, 40, 40));
        canvas.drawRoundRect(pill, pill.width() / 2, pill.width() / 2, paint);
        String day = time("EEE");
        paint.setColor(Color.WHITE);
        paint.setTextSize(pill.width() * 0.45f);
        paint.setTextAlign(Paint.Align.CENTER);
        float step = pill.height() / (day.length() + 1);
        for (int i = 0; i < day.length(); i++) {
            canvas.drawText(String.valueOf(day.charAt(i)), pill.centerX(), pill.top + step * (i + 1) + paint.getTextSize() / 3, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Scales the text so its glyph bounds fill the box (whichever of width/height is tighter). */
    private void drawText(Canvas canvas, String text, RectF fraction, Typeface typeface, int color, int width, int height) {
        RectF box = scaled(fraction, width, height);
        paint.setTypeface(typeface);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(100f);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        if (bounds.isEmpty()) {
            return;
        }
        float scale = Math.min(box.height() / bounds.height(), box.width() / bounds.width());
        paint.setTextSize(100f * scale);
        paint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText(text, box.centerX() - bounds.exactCenterX(), box.centerY() - bounds.exactCenterY(), paint);
    }

    private static RectF box(float left, float top, float right, float bottom) {
        return new RectF(left / 1220, top / 2712, right / 1220, bottom / 2712);
    }

    private static RectF scaled(RectF fraction, int width, int height) {
        return new RectF(fraction.left * width, fraction.top * height, fraction.right * width, fraction.bottom * height);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String time(String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

}
