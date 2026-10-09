package com.motorola.deptheffect;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Home screen clock in the style of Motorola's Modern clock 1: one row of digits in Personalize's
 * Moto_Light_1 font with the date below. Widgets can't load another app's fonts, so the clock is
 * drawn into a bitmap and redrawn at the start of every minute by a non-wakeup alarm (it catches up
 * as soon as the screen turns on).
 */
public class ModernClockWidget extends AppWidgetProvider {
    private static final String ACTION_TICK = "com.motorola.deptheffect.action.WIDGET_TICK";
    private static final int CLOCK_COLOR = Color.WHITE;
    private static Typeface clockFont;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        updateAll(context, manager, widgetIds);
        scheduleNextTick(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int widgetId, Bundle options) {
        updateAll(context, manager, new int[] {widgetId});
    }

    @Override
    public void onDisabled(Context context) {
        context.getSystemService(AlarmManager.class).cancel(tickIntent(context));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, ModernClockWidget.class));
            if (ids.length > 0) {
                updateAll(context, manager, ids);
                scheduleNextTick(context);
            }
            return;
        }
        super.onReceive(context, intent);
    }

    private static void updateAll(Context context, AppWidgetManager manager, int[] widgetIds) {
        Intent openClock = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(context, 0, openClock, PendingIntent.FLAG_IMMUTABLE);
        for (int id : widgetIds) {
            Bundle options = manager.getAppWidgetOptions(id);
            int width = dpToPx(context, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250));
            int height = dpToPx(context, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_modern_clock);
            views.setImageViewBitmap(R.id.widget_image, render(context, Math.max(width, 1), Math.max(height, 1)));
            views.setOnClickPendingIntent(R.id.widget_root, tap);
            manager.updateAppWidget(id, views);
        }
    }

    /** Draws "hhmm" filling the width (top ~75% of the height) and the date underneath. */
    private static Bitmap render(Context context, int width, int height) {
        if (clockFont == null) {
            clockFont = ModernClockPreview.loadPersonalizeFont(context, "font/Moto_Light_1.ttf");
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Date now = new Date();
        String time = DateFormat.format(DateFormat.is24HourFormat(context) ? "HHmm" : "hhmm", now).toString();
        String date = DateFormat.format("EEE, MMM d", now).toString().toUpperCase(Locale.getDefault());

        float timeAreaHeight = height * 0.75f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(clockFont);
        paint.setColor(CLOCK_COLOR);
        paint.setTextSize(100f);
        Rect bounds = new Rect();
        paint.getTextBounds(time, 0, time.length(), bounds);
        float scale = Math.min(width * 0.94f / bounds.width(), timeAreaHeight * 0.92f / bounds.height());
        paint.setTextSize(100f * scale);
        paint.getTextBounds(time, 0, time.length(), bounds);
        float timeLeft = (width - bounds.width()) / 2f - bounds.left;
        canvas.drawText(time, timeLeft, timeAreaHeight / 2f - bounds.exactCenterY(), paint);

        Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        datePaint.setColor(Color.WHITE);
        datePaint.setShadowLayer(4f, 0f, 1f, Color.argb(128, 0, 0, 0));
        datePaint.setTextSize(Math.min(height * 0.14f, dpToPx(context, 18)));
        float dateLeft = (width - bounds.width()) / 2f + width * 0.03f;
        canvas.drawText(date, dateLeft, timeAreaHeight + (height - timeAreaHeight) * 0.65f, datePaint);
        return bitmap;
    }

    /** Non-wakeup exact alarm at the next minute boundary; delivered on screen-on if the phone was asleep. */
    private static void scheduleNextTick(Context context) {
        Calendar next = Calendar.getInstance();
        next.add(Calendar.MINUTE, 1);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.RTC, next.getTimeInMillis(), tickIntent(context));
        } else {
            alarms.setWindow(AlarmManager.RTC, next.getTimeInMillis(), 1000, tickIntent(context));
        }
    }

    private static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, ModernClockWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }
}
