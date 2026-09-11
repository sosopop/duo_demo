package com.example.duodemo.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BitmapUtils {

    /**
     * Decode a user selected image from URI, safely downsampling if needed and correcting EXIF rotation.
     */
    public static Bitmap loadBitmapFromUri(Context context, Uri uri, int maxDimension) {
        try {
            // First decode bounds
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, boundsOpts);
            }

            int w = boundsOpts.outWidth;
            int h = boundsOpts.outHeight;
            if (w <= 0 || h <= 0) return null;

            int sampleSize = 1;
            while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
                sampleSize *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(is, null, opts);
            }

            if (bitmap == null) return null;

            // Check EXIF orientation
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    int rotationDeg = 0;
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90: rotationDeg = 90; break;
                        case ExifInterface.ORIENTATION_ROTATE_180: rotationDeg = 180; break;
                        case ExifInterface.ORIENTATION_ROTATE_270: rotationDeg = 270; break;
                    }
                    if (rotationDeg != 0) {
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postRotate(rotationDeg);
                        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        if (rotated != bitmap) {
                            bitmap.recycle();
                            bitmap = rotated;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate a default high-resolution simulated iPhone / iPad desktop screenshot.
     * Guarantees that the app looks stunning out of the box with sharp icons, widgets, and dock.
     */
    public static Bitmap createDefaultShowcaseBitmap(Context context, int width, int height) {
        if (width <= 0) width = 1080;
        if (height <= 0) height = 2400;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Beautiful deep mesh gradient background (Apple dynamic wallpaper style)
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient gradient = new LinearGradient(
                0, 0, width, height,
                new int[]{Color.parseColor("#1B1E38"), Color.parseColor("#2E1C4D"), Color.parseColor("#4A1942"), Color.parseColor("#121826")},
                new float[]{0.0f, 0.35f, 0.7f, 1.0f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, bgPaint);

        // Subtle glow spheres
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.parseColor("#350A84FF"));
        canvas.drawCircle(width * 0.3f, height * 0.35f, width * 0.45f, glowPaint);
        glowPaint.setColor(Color.parseColor("#30FF375F"));
        canvas.drawCircle(width * 0.75f, height * 0.65f, width * 0.5f, glowPaint);

        // 2. Top Status / Time & Date Widget
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(height * 0.045f);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(8, 0, 4, Color.parseColor("#60000000"));

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFormat.format(new Date());
        canvas.drawText(timeStr, width * 0.5f, height * 0.11f, textPaint);

        textPaint.setTextSize(height * 0.016f);
        textPaint.setFakeBoldText(false);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.US);
        String dateStr = dateFormat.format(new Date());
        canvas.drawText(dateStr, width * 0.5f, height * 0.145f, textPaint);

        // 3. App Icons Grid (5 rows x 4 cols)
        String[][] apps = {
                {"Photos", "Camera", "Safari", "Maps"},
                {"Settings", "Notes", "Weather", "Music"},
                {"Clock", "Health", "Stocks", "Podcasts"},
                {"Files", "App Store", "Mail", "Fitness"},
                {"Calculator", "Compass", "TV", "Books"}
        };
        int[][] iconColors = {
                {Color.parseColor("#FF9500"), Color.parseColor("#8E8E93"), Color.parseColor("#007AFF"), Color.parseColor("#34C759")},
                {Color.parseColor("#5856D6"), Color.parseColor("#FFCC00"), Color.parseColor("#32ADE6"), Color.parseColor("#FA2D48")},
                {Color.parseColor("#1C1C1E"), Color.parseColor("#FF2D55"), Color.parseColor("#30D158"), Color.parseColor("#AF52DE")},
                {Color.parseColor("#0A84FF"), Color.parseColor("#007AFF"), Color.parseColor("#5E5CE6"), Color.parseColor("#FF453A")},
                {Color.parseColor("#FF9500"), Color.parseColor("#1C1C1E"), Color.parseColor("#1C1C1E"), Color.parseColor("#FF9500")}
        };

        float startY = height * 0.20f;
        float rowSpacing = height * 0.115f;
        float colSpacing = width / 4.0f;
        float iconRadius = width * 0.075f;

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint iconLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconLabelPaint.setColor(Color.WHITE);
        iconLabelPaint.setTextAlign(Paint.Align.CENTER);
        iconLabelPaint.setTextSize(height * 0.014f);
        iconLabelPaint.setShadowLayer(4, 0, 2, Color.parseColor("#80000000"));

        for (int r = 0; r < apps.length; r++) {
            for (int c = 0; c < 4; c++) {
                float cx = colSpacing * c + colSpacing * 0.5f;
                float cy = startY + r * rowSpacing;

                // Squircle / rounded icon
                iconPaint.setColor(iconColors[r][c]);
                RectF iconRect = new RectF(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius);
                canvas.drawRoundRect(iconRect, iconRadius * 0.45f, iconRadius * 0.45f, iconPaint);

                // Subtle inner sheen
                Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
                sheen.setColor(Color.parseColor("#25FFFFFF"));
                canvas.drawCircle(cx, cy - iconRadius * 0.3f, iconRadius * 0.6f, sheen);

                // App label
                canvas.drawText(apps[r][c], cx, cy + iconRadius + height * 0.022f, iconLabelPaint);
            }
        }

        // 4. Bottom Dock Card (Pinned 4 apps)
        float dockY = height * 0.83f;
        float dockHeight = height * 0.11f;
        RectF dockRect = new RectF(width * 0.06f, dockY, width * 0.94f, dockY + dockHeight);

        Paint dockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dockPaint.setColor(Color.parseColor("#55FFFFFF"));
        canvas.drawRoundRect(dockRect, 42, 42, dockPaint);

        String[] dockApps = {"Phone", "Messages", "Safari", "Music"};
        int[] dockColors = {Color.parseColor("#34C759"), Color.parseColor("#30D158"), Color.parseColor("#007AFF"), Color.parseColor("#FA2D48")};
        float dockColSpacing = width * 0.88f / 4.0f;
        float dockIconRadius = width * 0.07f;

        for (int i = 0; i < 4; i++) {
            float cx = width * 0.06f + dockColSpacing * i + dockColSpacing * 0.5f;
            float cy = dockY + dockHeight * 0.5f;
            iconPaint.setColor(dockColors[i]);
            RectF iconRect = new RectF(cx - dockIconRadius, cy - dockIconRadius, cx + dockIconRadius, cy + dockIconRadius);
            canvas.drawRoundRect(iconRect, dockIconRadius * 0.45f, dockIconRadius * 0.45f, iconPaint);
        }

        // 6. Home Indicator bar at very bottom
        Paint homeBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        homeBar.setColor(Color.parseColor("#A0FFFFFF"));
        RectF homeRect = new RectF(width * 0.35f, height * 0.975f, width * 0.65f, height * 0.982f);
        canvas.drawRoundRect(homeRect, 10, 10, homeBar);

        return bitmap;
    }
}
