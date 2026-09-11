package com.example.duodemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.duodemo.gl.DuoGLSurfaceView;
import com.example.duodemo.sensor.DeviceTiltTracker;
import com.example.duodemo.util.BitmapUtils;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private DuoGLSurfaceView glSurfaceView;
    private View topStatusBadge;
    private View bottomHudPanel;

    private TextView tvAngleInfo;
    private TextView tvBlurValue;
    private TextView tvFovValue;
    private TextView tvDistanceValue;

    private Button btnPickImage;
    private Button btnCalibrate;
    private SeekBar seekBlurIntensity;
    private SeekBar seekFov;
    private SeekBar seekDistance;

    private DeviceTiltTracker tiltTracker;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isUiVisible = true;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    loadCustomImage(uri);
                }
            });

    private final ActivityResultLauncher<String> fallbackGetContentLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    loadCustomImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge fullscreen
        setupFullScreen();

        setContentView(R.layout.activity_main);

        initViews();
        setupSensors();
        setupListeners();
        loadInitialShowcaseImage();
    }

    private void setupFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView);
        topStatusBadge = findViewById(R.id.topStatusBadge);
        bottomHudPanel = findViewById(R.id.bottomHudPanel);

        tvAngleInfo = findViewById(R.id.tvAngleInfo);
        tvBlurValue = findViewById(R.id.tvBlurValue);
        tvFovValue = findViewById(R.id.tvFovValue);
        tvDistanceValue = findViewById(R.id.tvDistanceValue);

        btnPickImage = findViewById(R.id.btnPickImage);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        seekBlurIntensity = findViewById(R.id.seekBlurIntensity);
        seekFov = findViewById(R.id.seekFov);
        seekDistance = findViewById(R.id.seekDistance);
    }

    private long lastAngleTextUpdate = 0L;

    private void setupSensors() {
        tiltTracker = new DeviceTiltTracker(this);
        tiltTracker.setOnTiltListener((pitchDeg, rollDeg, rawPitch, rawRoll) -> {
            // 1. Immediately update GL thread with ZERO latency (bypassing Android UI thread message queue)
            glSurfaceView.setTilt(pitchDeg, rollDeg);

            // 2. Throttle text display update to ~15fps so it doesn't bog down the UI thread
            long now = android.os.SystemClock.uptimeMillis();
            if (isUiVisible && now - lastAngleTextUpdate > 66) {
                lastAngleTextUpdate = now;
                runOnUiThread(() -> {
                    tvAngleInfo.setText(String.format(Locale.getDefault(), "X角度: %.1f° | Y角度: %.1f°", pitchDeg, rollDeg));
                });
            }
        });
    }

    private void setupListeners() {
        // Tap screen to toggle UI visibility
        glSurfaceView.setOnUiToggleListener(this::toggleUiVisibility);

        // Pick Image
        btnPickImage.setOnClickListener(v -> {
            try {
                pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } catch (Exception e) {
                // Fallback for older devices without Google Play services Photo Picker
                fallbackGetContentLauncher.launch("image/*");
            }
        });

        // Calibrate: Align 4 corners of the photo exactly with the screen viewport
        btnCalibrate.setOnClickListener(v -> {
            tiltTracker.calibrateCurrentAsZero();

            // Reset FOV to 30° (5 + 25 = 30)
            seekFov.setProgress(25);
            glSurfaceView.setFov(30.0f);
            tvFovValue.setText("30°");

            // Reset distance to 1.0x (0.1 + 90 * 0.01 = 1.0)
            seekDistance.setProgress(90);
            glSurfaceView.setCameraDistanceFactor(1.0f);
            tvDistanceValue.setText("1.0x");

            // Reset photo pan and scale back to 1:1 viewport alignment
            glSurfaceView.resetPhotoTransform();

            Toast.makeText(this, "已校准水平：X/Y角度归零，图片位置与尺寸恢复默认对齐", Toast.LENGTH_SHORT).show();
        });

        // Blur slider (Aperture)
        seekBlurIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float aperture = progress / 50.0f; // 0.0 to 2.0
                glSurfaceView.setAperture(aperture);
                tvBlurValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Camera FOV slider (5° to 90°, default 30°)
        seekFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float fov = 5.0f + progress; // 5° to 90°
                glSurfaceView.setFov(fov);
                tvFovValue.setText(String.format(Locale.getDefault(), "%.0f°", fov));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Distance slider (Camera distance factor: 0.1x to 2.0x)
        seekDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float factor = 0.1f + progress * 0.01f; // 0.1x to 2.0x
                glSurfaceView.setCameraDistanceFactor(factor);
                tvDistanceValue.setText(String.format(Locale.getDefault(), "%.1fx", factor));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void hideAllUi() {
        isUiVisible = false;
        topStatusBadge.setVisibility(View.GONE);
        bottomHudPanel.setVisibility(View.GONE);
    }

    private void showAllUi() {
        isUiVisible = true;
        topStatusBadge.setVisibility(View.VISIBLE);
        bottomHudPanel.setVisibility(View.VISIBLE);
    }

    private void toggleUiVisibility() {
        if (isUiVisible) {
            hideAllUi();
        } else {
            showAllUi();
        }
    }

    private void loadInitialShowcaseImage() {
        executor.execute(() -> {
            Bitmap showcase = BitmapUtils.createDefaultShowcaseBitmap(this, 1080, 2400);
            glSurfaceView.setBitmap(showcase);
        });
    }

    private void loadCustomImage(Uri uri) {
        Toast.makeText(this, "正在加载并构建3D纹理...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            Bitmap bitmap = BitmapUtils.loadBitmapFromUri(this, uri, 2048);
            if (bitmap != null) {
                glSurfaceView.setBitmap(bitmap);
                runOnUiThread(() -> {
                    Toast.makeText(this, "图片加载完成，3D空间已对齐", Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载图片失败，请重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
        glSurfaceView.onResume();
        tiltTracker.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        tiltTracker.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
