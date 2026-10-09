package com.motorola.deptheffect.service;

import android.app.WallpaperColors;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import com.motorola.deptheffect.DepthStore;

/**
 * Lock screen wallpaper: draws the background photo. SystemUI draws the Modern clock and the
 * subject cut-out (pulled by Personalize from DepthWallpaperProvider) on top of it.
 */
public class DepthWallpaperService extends WallpaperService {
    private static final String TAG = "DepthWallpaper";

    @Override
    public Engine onCreateEngine() {
        return new BackgroundEngine();
    }

    private class BackgroundEngine extends Engine {
        private final Runnable onImagesChanged = this::onImagesChanged;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            DepthStore.addListener(onImagesChanged);
        }

        @Override
        public void onDestroy() {
            DepthStore.removeListener(onImagesChanged);
            super.onDestroy();
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            draw(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                draw(getSurfaceHolder());
            }
        }

        /** Personalize listens for wallpaper color changes and re-pulls the depth layers when they fire. */
        @Override
        public WallpaperColors onComputeColors() {
            Bitmap bitmap = loadBackground(4);
            if (bitmap == null) {
                return null;
            }
            WallpaperColors colors = WallpaperColors.fromBitmap(bitmap);
            bitmap.recycle();
            return colors;
        }

        private void onImagesChanged() {
            draw(getSurfaceHolder());
            notifyColorsChanged();
        }

        private void draw(SurfaceHolder holder) {
            Bitmap bitmap = loadBackground(1);
            if (bitmap == null) {
                return;
            }
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bitmap, null, centerCrop(bitmap, canvas.getWidth(), canvas.getHeight()), null);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "draw failed", e);
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
                bitmap.recycle();
            }
        }

        private Bitmap loadBackground(int sampleSize) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                return BitmapFactory.decodeFile(DepthStore.background(DepthWallpaperService.this).getPath(), options);
            } catch (Exception e) {
                Log.e(TAG, "loadBackground failed", e);
                return null;
            }
        }
    }

    private static Rect centerCrop(Bitmap bitmap, int width, int height) {
        float scale = Math.max((float) width / bitmap.getWidth(), (float) height / bitmap.getHeight());
        int w = Math.round(bitmap.getWidth() * scale);
        int h = Math.round(bitmap.getHeight() * scale);
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        return new Rect(left, top, left + w, top + h);
    }
}
