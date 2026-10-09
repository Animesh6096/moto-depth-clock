package com.motorola.deptheffect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Lock screen preview and editor. Draws the photo, the Modern clock's back layer, the subject
 * cut-out, then the clock's front layer. Tools: move/zoom the photo, paint parts of the photo into
 * the cut-out (in front of the clock), erase parts of the cut-out (behind the clock), and pick the
 * clock colour from the photo. Positions are kept in screen pixels so render() matches the preview.
 */
public class DepthEditorView extends View {
    public enum Tool { MOVE, FRONT_BRUSH, BACK_BRUSH, PICK_COLOR }

    public interface ColorPickListener {
        void onColorPicked(int color);
    }

    private static final float MAX_ZOOM = 4f;
    /**
     * The lock screen dims the wallpaper layer but not the cut-out layer above it. Measured on the
     * Edge 60 Pro: wallpaper shows at 0.89 of the photo's brightness, the cut-out at 1.01. Dimming
     * the cut-out by the same amount keeps both layers the same brightness.
     */
    private static final float LOCK_SCREEN_WALLPAPER_DIM = 0.89f;
    /** Brush radius in screen pixels. */
    private static final float BRUSH_RADIUS = 45f;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brushOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ModernClockPreview clock;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector dragDetector;

    private Bitmap source;
    private Bitmap originalCutout;
    private Bitmap cutout;
    private Paint frontBrushPaint;
    private int screenWidth = 1220;
    private int screenHeight = 2712;
    private float zoom = 1f;
    private float offsetX;
    private float offsetY;
    private float viewScale = 1f;
    private int faceId = DepthStore.DEFAULT_CLOCK_FACE_ID;
    private int clockColor = Color.WHITE;
    private boolean hoursInFront;
    private Tool tool = Tool.MOVE;
    private ColorPickListener colorPickListener;
    private float brushX = -1f;
    private float brushY = -1f;

    public DepthEditorView(Context context) {
        this(context, null);
    }

    public DepthEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        clock = new ModernClockPreview(context);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(Color.GRAY);
        framePaint.setStrokeWidth(4f);
        erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        brushOutline.setStyle(Paint.Style.STROKE);
        brushOutline.setColor(Color.WHITE);
        brushOutline.setStrokeWidth(3f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.max(1f, Math.min(MAX_ZOOM, zoom * detector.getScaleFactor()));
                invalidate();
                return true;
            }
        });
        dragDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                offsetX -= distanceX / viewScale;
                offsetY -= distanceY / viewScale;
                invalidate();
                return true;
            }
        });
    }

    public void setImages(Bitmap source, Bitmap cutout, int screenWidth, int screenHeight) {
        this.source = source;
        this.originalCutout = cutout;
        this.cutout = cutout.copy(Bitmap.Config.ARGB_8888, true);
        frontBrushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        frontBrushPaint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        reset();
    }

    /** Sets the screen size used before a photo is chosen, so the clock preview is to scale. */
    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        invalidate();
    }

    public boolean hasImages() {
        return source != null && cutout != null;
    }

    /** Which Modern face to preview (101-106). */
    public void setFaceId(int faceId) {
        this.faceId = faceId;
        invalidate();
    }

    public void setClockColor(int color) {
        clockColor = color;
        invalidate();
    }

    public void setHoursInFront(boolean hoursInFront) {
        this.hoursInFront = hoursInFront;
        invalidate();
    }

    public void setTool(Tool tool) {
        this.tool = tool;
        brushX = -1f;
        invalidate();
    }

    public void setColorPickListener(ColorPickListener listener) {
        colorPickListener = listener;
    }

    /** Resets position, zoom and brush edits. */
    public void reset() {
        zoom = 1f;
        offsetX = 0f;
        offsetY = 0f;
        if (originalCutout != null) {
            cutout = originalCutout.copy(Bitmap.Config.ARGB_8888, true);
        }
        invalidate();
    }

    /** Renders the background and foreground layers at screen size using the current edits. */
    public Bitmap[] render() {
        Matrix matrix = screenMatrix();
        Bitmap background = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        new Canvas(background).drawBitmap(source, matrix, bitmapPaint);
        Bitmap foreground = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(foreground);
        Paint dimmed = new Paint(bitmapPaint);
        ColorMatrix dim = new ColorMatrix();
        dim.setScale(LOCK_SCREEN_WALLPAPER_DIM, LOCK_SCREEN_WALLPAPER_DIM, LOCK_SCREEN_WALLPAPER_DIM, 1f);
        dimmed.setColorFilter(new ColorMatrixColorFilter(dim));
        canvas.drawBitmap(cutout, matrix, dimmed);
        if (hoursInFront) {
            canvas.drawRect(ModernClockPreview.backLayerBox(faceId, screenWidth, screenHeight), erasePaint);
        }
        return new Bitmap[] {background, foreground};
    }

    /** Maps the source photo into screen space: center-crop, user zoom, user offset, clamped to cover. */
    private Matrix screenMatrix() {
        float scale = Math.max((float) screenWidth / source.getWidth(), (float) screenHeight / source.getHeight()) * zoom;
        float scaledWidth = source.getWidth() * scale;
        float scaledHeight = source.getHeight() * scale;
        float centerX = (screenWidth - scaledWidth) / 2f;
        float centerY = (screenHeight - scaledHeight) / 2f;
        float dx = clamp(centerX + offsetX, screenWidth - scaledWidth, 0f);
        float dy = clamp(centerY + offsetY, screenHeight - scaledHeight, 0f);
        offsetX = dx - centerX;
        offsetY = dy - centerY;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        return matrix;
    }

    /** Converts a touch position to screen-pixel coordinates of the previewed lock screen. */
    private float[] toScreen(MotionEvent event) {
        float left = (getWidth() - screenWidth * viewScale) / 2f;
        float top = (getHeight() - screenHeight * viewScale) / 2f;
        return new float[] {(event.getX() - left) / viewScale, (event.getY() - top) / viewScale};
    }

    /** Converts screen-pixel coordinates to photo (source bitmap) coordinates. */
    private float[] toSource(float[] screenPoint) {
        Matrix inverse = new Matrix();
        screenMatrix().invert(inverse);
        float[] point = screenPoint.clone();
        inverse.mapPoints(point);
        return point;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasImages()) {
            return false;
        }
        switch (tool) {
            case MOVE:
                scaleDetector.onTouchEvent(event);
                dragDetector.onTouchEvent(event);
                return true;
            case PICK_COLOR:
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float[] point = toSource(toScreen(event));
                    int x = (int) clamp(point[0], 0, source.getWidth() - 1);
                    int y = (int) clamp(point[1], 0, source.getHeight() - 1);
                    if (colorPickListener != null) {
                        colorPickListener.onColorPicked(source.getPixel(x, y) | 0xFF000000);
                    }
                }
                return true;
            default:
                paintBrush(event);
                return true;
        }
    }

    /** Front brush copies photo pixels into the cut-out; back brush clears the cut-out. */
    private void paintBrush(MotionEvent event) {
        float[] screenPoint = toScreen(event);
        brushX = screenPoint[0];
        brushY = screenPoint[1];
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            brushX = -1f;
        } else {
            float[] point = toSource(screenPoint);
            float radius = BRUSH_RADIUS / currentScale();
            Canvas canvas = new Canvas(cutout);
            canvas.drawCircle(point[0], point[1], radius, tool == Tool.FRONT_BRUSH ? frontBrushPaint : erasePaint);
        }
        invalidate();
    }

    private float currentScale() {
        return Math.max((float) screenWidth / source.getWidth(), (float) screenHeight / source.getHeight()) * zoom;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        viewScale = Math.min((float) getWidth() / screenWidth, (float) getHeight() / screenHeight);
        canvas.save();
        canvas.translate((getWidth() - screenWidth * viewScale) / 2f, (getHeight() - screenHeight * viewScale) / 2f);
        canvas.scale(viewScale, viewScale);
        canvas.clipRect(0, 0, screenWidth, screenHeight);

        if (hasImages()) {
            Matrix matrix = screenMatrix();
            canvas.drawBitmap(source, matrix, bitmapPaint);
            clock.drawBehind(canvas, faceId, clockColor, screenWidth, screenHeight);
            canvas.save();
            if (hoursInFront) {
                canvas.clipOutRect(ModernClockPreview.backLayerBox(faceId, screenWidth, screenHeight));
            }
            canvas.drawBitmap(cutout, matrix, bitmapPaint);
            canvas.restore();
        } else {
            canvas.drawColor(Color.BLACK);
            clock.drawBehind(canvas, faceId, clockColor, screenWidth, screenHeight);
        }
        clock.drawInFront(canvas, faceId, clockColor, screenWidth, screenHeight);
        if (brushX >= 0) {
            canvas.drawCircle(brushX, brushY, BRUSH_RADIUS, brushOutline);
        }
        canvas.restore();

        float left = (getWidth() - screenWidth * viewScale) / 2f;
        float top = (getHeight() - screenHeight * viewScale) / 2f;
        canvas.drawRect(new RectF(left, top, left + screenWidth * viewScale, top + screenHeight * viewScale), framePaint);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
