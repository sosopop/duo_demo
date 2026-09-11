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
    private TextView tvModeBadge;
    private TextView tvBlurValue;
    private TextView tvDistanceValue;

    private Button btnPickImage;
    private Button btnCalibrate;
    private Button btnToggleMode;
    private Button btnHideUi;
    private SeekBar seekBlurIntensity;
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
        tvModeBadge = findViewById(R.id.tvModeBadge);
        tvBlurValue = findViewById(R.id.tvBlurValue);
        tvDistanceValue = findViewById(R.id.tvDistanceValue);

        btnPickImage = findViewById(R.id.btnPickImage);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        btnHideUi = findViewById(R.id.btnHideUi);
        seekBlurIntensity = findViewById(R.id.seekBlurIntensity);
        seekDistance = findViewById(R.id.seekDistance);
    }

    private void setupSensors() {
        tiltTracker = new DeviceTiltTracker(this);
        tiltTracker.setOnTiltListener((pitchDeg, rollDeg, rawPitch, rawRoll) -> {
            runOnUiThread(() -> {
                glSurfaceView.setTilt(pitchDeg, rollDeg);
                tvAngleInfo.setText(String.format(Locale.getDefault(), "X角度: %.1f° | Y角度: %.1f°", pitchDeg, rollDeg));
            });
        });
    }

    private void setupListeners() {
        // Tap screen to toggle UI visibility
        glSurfaceView.setOnUiToggleListener(this::toggleUiVisibility);

        // Manual tilt listener from touch gestures
        glSurfaceView.setOnManualTiltListener((pitchDeg, rollDeg) -> {
            runOnUiThread(() -> {
                tvAngleInfo.setText(String.format(Locale.getDefault(), "触控 X角度: %.1f° | Y角度: %.1f°", pitchDeg, rollDeg));
            });
        });

        // Hide UI button
        btnHideUi.setOnClickListener(v -> hideAllUi());

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
            if (glSurfaceView.isTouchSimulationMode()) {
                glSurfaceView.setManualTilt(0.0f, 0.0f);
            }
            // Reset distance to 1.0x to guarantee pixel-perfect 4-corner alignment
            seekDistance.setProgress(50);
            glSurfaceView.setCameraDistanceFactor(1.0f);
            tvDistanceValue.setText("1.0x");

            Toast.makeText(this, "已校准水平：X角度与Y角度已归零，四角完全对齐视口", Toast.LENGTH_SHORT).show();
        });

        // Toggle Sensor / Touch Mode
        btnToggleMode.setOnClickListener(v -> {
            boolean touchMode = !glSurfaceView.isTouchSimulationMode();
            glSurfaceView.setTouchSimulationMode(touchMode);
            if (touchMode) {
                btnToggleMode.setText("切换传感器");
                tvModeBadge.setText("触控模拟");
                Toast.makeText(this, "已切换为触控模拟：上下滑调节 X角度，左右滑调节 Y角度", Toast.LENGTH_SHORT).show();
            } else {
                btnToggleMode.setText("切换触控");
                tvModeBadge.setText("传感器");
                Toast.makeText(this, "已切换为物理传感器实时追踪", Toast.LENGTH_SHORT).show();
            }
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

        // Distance slider (Camera distance factor)
        seekDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float factor = 0.6f + (progress / 100.0f) * 0.8f; // 0.6x to 1.4x
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
        Toast.makeText(this, "已隐藏UI，轻触屏幕任意位置可恢复显示", Toast.LENGTH_SHORT).show();
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
