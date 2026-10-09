package com.motorola.deptheffect;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Depth wallpaper editor. Pick one of Personalize's six Modern clock styles, pick a photo, position
 * it, choose what sits in front of the clock and the clock colour, then apply.
 *
 * How Personalize behaves, which shapes the rules here: it lets the depth app choose the Modern
 * style and adopts our choice only when it next starts (a phone restart). In Modern mode it saves
 * a style's photo only if the style has none; in Classic mode it re-saves our current photo into
 * the reported style on every start. So replacing a filled style's photo takes Classic, restart,
 * Modern. Personalize opens this screen via INTERNAL_SCREEN_EDIT.
 */
public class EditActivity extends AppCompatActivity {
    private static final String TAG = "DepthEdit";
    private static final int REQUEST_PICK = 1;
    /** Cap on the decoded photo's long edge; leaves headroom for zooming in. */
    private static final int MAX_SOURCE_EDGE = 4096;
    /** Personalize's Modern faces are ids 101..106 ("Modern clock face 1".."6"). */
    private static final int FIRST_MODERN_FACE_ID = 101;
    private static final int MODERN_STYLE_COUNT = 6;
    private static final String[] LAYOUT_NAMES = {"One row", "Stacked", "Date on top"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SubjectSegmenter segmenter;
    private DepthEditorView editor;
    private TextView hint;
    private MaterialCardView restartBanner;
    private TextView restartText;
    private LinearLayout styleRow;
    private LinearLayout swatchRow;
    private MaterialButtonToggleGroup toolGroup;
    private MaterialSwitch hoursInFront;
    private MaterialButton pickColor;
    private MaterialButton resetButton;
    private MaterialButton applyButton;
    private int screenWidth;
    private int screenHeight;
    private int selectedFaceId;
    private int clockColor;
    private boolean hasNewPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        segmenter = SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
        screenWidth = Math.min(bounds.width(), bounds.height());
        screenHeight = Math.max(bounds.width(), bounds.height());

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.edit_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_slots_reset) {
                confirmResetSlots();
                return true;
            }
            return false;
        });

        editor = findViewById(R.id.editor);
        hint = findViewById(R.id.hint);
        restartBanner = findViewById(R.id.restart_banner);
        restartText = findViewById(R.id.restart_text);
        styleRow = findViewById(R.id.style_row);
        swatchRow = findViewById(R.id.swatch_row);
        toolGroup = findViewById(R.id.tool_group);
        hoursInFront = findViewById(R.id.hours_in_front);
        pickColor = findViewById(R.id.pick_color);
        resetButton = findViewById(R.id.action_reset);
        applyButton = findViewById(R.id.action_apply);

        editor.setScreenSize(screenWidth, screenHeight);
        selectedFaceId = DepthStore.clockFaceId(this);
        editor.setFaceId(selectedFaceId);
        clockColor = DepthStore.clockColor(this);
        editor.setClockColor(clockColor);
        editor.setColorPickListener(color -> {
            setClockColor(color);
            toolGroup.check(R.id.tool_move);
        });

        toolGroup.check(R.id.tool_move);
        toolGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.tool_front) {
                editor.setTool(DepthEditorView.Tool.FRONT_BRUSH);
                showHint("Paint over parts of the photo to bring them in front of the clock.");
            } else if (checkedId == R.id.tool_back) {
                editor.setTool(DepthEditorView.Tool.BACK_BRUSH);
                showHint("Paint over the subject to push those parts behind the clock.");
            } else {
                editor.setTool(DepthEditorView.Tool.MOVE);
                showHint("Pinch to zoom, drag to move.");
            }
        });
        hoursInFront.setOnCheckedChangeListener((view, checked) -> editor.setHoursInFront(checked));
        pickColor.setOnClickListener(v -> {
            editor.setTool(DepthEditorView.Tool.PICK_COLOR);
            showHint("Tap the photo to take the clock colour from that spot.");
        });
        findViewById(R.id.action_photo).setOnClickListener(v -> pickPhoto());
        resetButton.setOnClickListener(v -> {
            editor.reset();
            hoursInFront.setChecked(false);
        });
        applyButton.setOnClickListener(v -> apply());

        showSwatches(new int[0]);
        setPhotoControlsEnabled(false);
        refreshStyles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRestartBanner();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        segmenter.close();
        super.onDestroy();
    }

    // ---- Styles -------------------------------------------------------------------------------

    /** Rebuilds the style cards: thumbnail of the style's stored photo, name, layout and status. */
    private void refreshStyles() {
        styleRow.removeAllViews();
        Set<Integer> filled = SlotTracker.filledSlots(this);
        int current = DepthStore.clockFaceId(this);
        for (int i = 0; i < MODERN_STYLE_COUNT; i++) {
            int faceId = FIRST_MODERN_FACE_ID + i;
            styleRow.addView(styleCard(faceId, i + 1, filled.contains(faceId), faceId == current));
        }
        updateApplyButton();
    }

    private View styleCard(int faceId, int number, boolean filled, boolean inUse) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCheckable(true);
        card.setChecked(faceId == selectedFaceId);
        card.setStrokeWidth(dp(faceId == selectedFaceId ? 3 : 1));
        card.setRadius(dp(14));
        card.setOnClickListener(v -> {
            selectedFaceId = faceId;
            editor.setFaceId(faceId);
            refreshStyles();
        });
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        card.setLayoutParams(cardParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        FrameLayout thumbFrame = new FrameLayout(this);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Color.rgb(28, 28, 34));
        Bitmap thumbnail = thumbnail(faceId);
        if (thumbnail != null) {
            thumb.setImageBitmap(thumbnail);
        }
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(110)));
        TextView numberView = new TextView(this);
        numberView.setText(String.valueOf(number));
        numberView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        numberView.setTextColor(Color.WHITE);
        numberView.setGravity(Gravity.CENTER);
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(Color.argb(150, 0, 0, 0));
        numberView.setBackground(badge);
        thumbFrame.addView(numberView, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        column.addView(thumbFrame);

        TextView layout = new TextView(this);
        layout.setText(LAYOUT_NAMES[ModernClockPreview.layoutOf(faceId) - 1]);
        layout.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        layout.setPadding(dp(8), dp(6), dp(8), 0);
        column.addView(layout);

        TextView status = new TextView(this);
        status.setText(inUse ? "In use" : filled ? "Has photo" : "Empty");
        status.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        status.setAlpha(inUse ? 1f : 0.7f);
        status.setPadding(dp(8), dp(2), dp(8), dp(8));
        column.addView(status);

        card.addView(column);
        return card;
    }

    private Bitmap thumbnail(int faceId) {
        File file = DepthStore.storedBackground(this, faceId);
        if (file == null) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 16;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    /** The apply button says what it will do for the selected style. */
    private void updateApplyButton() {
        boolean filled = SlotTracker.filledSlots(this).contains(selectedFaceId);
        int number = selectedFaceId - FIRST_MODERN_FACE_ID + 1;
        if (hasNewPhoto) {
            applyButton.setText((filled ? "Replace style " : "Apply to style ") + number);
            applyButton.setEnabled(true);
        } else if (filled) {
            applyButton.setText(selectedFaceId == DepthStore.clockFaceId(this) ? "In use" : "Use style " + number);
            applyButton.setEnabled(selectedFaceId != DepthStore.clockFaceId(this));
        } else {
            applyButton.setText("Pick a photo");
            applyButton.setEnabled(false);
        }
    }

    // ---- Photo --------------------------------------------------------------------------------

    private void pickPhoto() {
        startActivityForResult(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        showHint("Cutting out the subject…");
        executor.execute(() -> {
            try {
                Bitmap photo = loadPhoto(uri);
                runOnUiThread(() -> segment(photo));
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                runOnUiThread(() -> showHint("Couldn't open that photo: " + e.getMessage()));
            }
        });
    }

    private void segment(Bitmap photo) {
        segmenter.process(InputImage.fromBitmap(photo, 0))
                .addOnSuccessListener(result -> {
                    Bitmap cutout = result.getForegroundBitmap();
                    if (cutout == null) {
                        showHint("No subject found. Try a photo with a clear person or object.");
                        return;
                    }
                    if (cutout.getWidth() != photo.getWidth() || cutout.getHeight() != photo.getHeight()) {
                        cutout = Bitmap.createScaledBitmap(cutout, photo.getWidth(), photo.getHeight(), true);
                    }
                    editor.setImages(photo, cutout, screenWidth, screenHeight);
                    hasNewPhoto = true;
                    showSwatches(photoColors(photo));
                    toolGroup.check(R.id.tool_move);
                    hoursInFront.setChecked(false);
                    setPhotoControlsEnabled(true);
                    showHint("Pinch to zoom, drag to move. The preview clock is approximate.");
                    updateApplyButton();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "segmentation failed", e);
                    showHint("Cut-out failed. The ML model may still be downloading; try again in a minute.");
                });
    }

    // ---- Apply --------------------------------------------------------------------------------

    private void apply() {
        boolean filled = SlotTracker.filledSlots(this).contains(selectedFaceId);
        int number = selectedFaceId - FIRST_MODERN_FACE_ID + 1;
        if (hasNewPhoto && filled) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Replace style " + number + "'s photo?")
                    .setMessage("Motorola only refreshes a used style while the lock screen is in Classic "
                            + "mode. After saving:\n\n"
                            + "1. In Settings \u203a Personalize \u203a lock screen, switch to Classic and apply.\n"
                            + "2. Restart your phone.\n"
                            + "3. Switch back to Modern and apply.\n\n"
                            + "Switching to Classic may also change your home screen wallpaper.")
                    .setPositiveButton("Save", (dialog, which) -> saveNewPhoto())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        if (!hasNewPhoto) {
            useStyle(number);
            return;
        }
        saveNewPhoto();
    }

    private void saveNewPhoto() {
        Bitmap[] layers = editor.render();
        int faceId = selectedFaceId;
        DepthStore.setClockColor(this, clockColor);
        applyButton.setEnabled(false);
        executor.execute(() -> {
            try {
                DepthStore.save(this, faceId, layers[0], layers[1]);
                SlotTracker.markFilled(this, faceId);
                runOnUiThread(() -> {
                    hasNewPhoto = false;
                    refreshStyles();
                    refreshRestartBanner();
                    finishIfOpenedByPersonalize();
                });
            } catch (Exception e) {
                Log.e(TAG, "save failed", e);
                runOnUiThread(() -> {
                    showHint("Saving failed: " + e.getMessage());
                    updateApplyButton();
                });
            }
        });
    }

    /** Switches to a style that already has a photo on the lock screen. */
    private void useStyle(int number) {
        if (!DepthStore.hasPhoto(this, selectedFaceId)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Photo not stored in this app")
                    .setMessage("Style " + number + " was filled by an older version of Depth Clock, which "
                            + "didn't keep a copy. Its clock cut-out will show over a plain background. "
                            + "Wiping Personalize's data clears this up.")
                    .setPositiveButton("Use anyway", (dialog, which) -> switchStyle())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        switchStyle();
    }

    private void switchStyle() {
        DepthStore.setClockFaceId(this, selectedFaceId);
        refreshStyles();
        refreshRestartBanner();
        finishIfOpenedByPersonalize();
    }

    private void refreshRestartBanner() {
        boolean pending = DepthStore.isRestartPending(this);
        restartBanner.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            restartText.setText(getString(R.string.restart_banner,
                    DepthStore.clockFaceId(this) - FIRST_MODERN_FACE_ID + 1));
        }
    }

    /**
     * When Personalize opened us, return CANCELED: on OK it closes its own editor and expects
     * Motorola's app to apply Modern mode through a call only that app may make.
     */
    private void finishIfOpenedByPersonalize() {
        if ("com.motorola.deptheffect.INTERNAL_SCREEN_EDIT".equals(getIntent().getAction())) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            Snackbar.make(findViewById(R.id.root), "Saved. Restart your phone to see it on the lock screen.",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void confirmResetSlots() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark all styles empty?")
                .setMessage("Only do this right after wiping Personalize's data (adb shell pm clear "
                        + "com.motorola.personalize). Otherwise the labels will be wrong.")
                .setPositiveButton("Mark empty", (dialog, which) -> {
                    SlotTracker.reset(this);
                    refreshStyles();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- Colour -------------------------------------------------------------------------------

    private void setClockColor(int color) {
        clockColor = color;
        editor.setClockColor(color);
        for (int i = 0; i < swatchRow.getChildCount(); i++) {
            View swatch = swatchRow.getChildAt(i);
            styleSwatch(swatch, (int) swatch.getTag(), (int) swatch.getTag() == color);
        }
        showHint("Colour applies when a style gets a new photo.");
    }

    /** Shows white, the current colour, and the given photo colours as tappable circles. */
    private void showSwatches(int[] photoColors) {
        swatchRow.removeAllViews();
        Set<Integer> shown = new LinkedHashSet<>();
        shown.add(Color.WHITE);
        shown.add(clockColor);
        for (int color : photoColors) {
            shown.add(color);
        }
        int size = dp(36);
        for (int color : shown) {
            View swatch = new View(this);
            swatch.setTag(color);
            styleSwatch(swatch, color, color == clockColor);
            swatch.setOnClickListener(v -> setClockColor(color));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatchRow.addView(swatch, params);
        }
    }

    private void styleSwatch(View swatch, int color, boolean selected) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setStroke(dp(selected ? 3 : 1), selected ? Color.rgb(120, 180, 255) : Color.argb(90, 255, 255, 255));
        swatch.setBackground(circle);
    }

    /** Distinct, prominent colours from the photo (vibrant and muted variants, then the dominant). */
    private static int[] photoColors(Bitmap photo) {
        Palette palette = Palette.from(photo).maximumColorCount(16).generate();
        Set<Integer> colors = new LinkedHashSet<>();
        for (Palette.Swatch swatch : new Palette.Swatch[] {palette.getVibrantSwatch(), palette.getLightVibrantSwatch(),
                palette.getDarkVibrantSwatch(), palette.getMutedSwatch(), palette.getLightMutedSwatch(),
                palette.getDarkMutedSwatch(), palette.getDominantSwatch()}) {
            if (swatch != null) {
                colors.add(swatch.getRgb() | 0xFF000000);
            }
        }
        int[] result = new int[colors.size()];
        int i = 0;
        for (int color : colors) {
            result[i++] = color;
        }
        return result;
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private void setPhotoControlsEnabled(boolean enabled) {
        for (int i = 0; i < toolGroup.getChildCount(); i++) {
            toolGroup.getChildAt(i).setEnabled(enabled);
        }
        hoursInFront.setEnabled(enabled);
        pickColor.setEnabled(enabled);
        resetButton.setEnabled(enabled);
    }

    private void showHint(String text) {
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
    }

    /** Decodes the photo as a software bitmap, downsampled only if its long edge exceeds MAX_SOURCE_EDGE. */
    private Bitmap loadPhoto(Uri uri) throws Exception {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int longEdge = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (longEdge > MAX_SOURCE_EDGE) {
                        float ratio = (float) MAX_SOURCE_EDGE / longEdge;
                        decoder.setTargetSize(Math.round(info.getSize().getWidth() * ratio),
                                Math.round(info.getSize().getHeight() * ratio));
                    }
                });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
