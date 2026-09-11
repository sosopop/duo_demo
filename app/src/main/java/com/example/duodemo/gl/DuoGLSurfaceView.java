package com.example.duodemo.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class DuoGLSurfaceView extends GLSurfaceView {
    private final DuoGLRenderer renderer;
    private GestureDetector gestureDetector;
    private OnUiToggleListener uiToggleListener;
    private OnManualTiltListener manualTiltListener;

    private boolean touchSimulationMode = false;
    // Default open angle in touch mode: -25 degrees (negative = bottom edge hinge)
    private float manualPitch = -25.0f;
    private float manualRoll = 0.0f;
    private float startTouchX = 0f;
    private float startTouchY = 0f;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;

    public interface OnUiToggleListener {
        void onToggleUi();
    }

    public interface OnManualTiltListener {
        void onManualTilt(float pitchDeg, float rollDeg);
    }

    public DuoGLSurfaceView(Context context) {
        this(context, null);
    }

    public DuoGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Configure OpenGL ES 2.0 / 3.0 context
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        renderer = new DuoGLRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        initGestureDetector(context);
    }

    private void initGestureDetector(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // Return true so Android dispatches subsequent MOVE and UP events
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (uiToggleListener != null) {
                    uiToggleListener.onToggleUi();
                    return true;
                }
                return super.onSingleTapUp(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (uiToggleListener != null) {
                    uiToggleListener.onToggleUi();
                    return true;
                }
                return super.onDoubleTap(e);
            }
        });
    }

    public void setOnUiToggleListener(OnUiToggleListener listener) {
        this.uiToggleListener = listener;
    }

    public void setOnManualTiltListener(OnManualTiltListener listener) {
        this.manualTiltListener = listener;
    }

    public void setTouchSimulationMode(boolean enabled) {
        this.touchSimulationMode = enabled;
        if (enabled) {
            renderer.setTilt(manualPitch, manualRoll);
            if (manualTiltListener != null) {
                manualTiltListener.onManualTilt(manualPitch, manualRoll);
            }
        }
    }

    public boolean isTouchSimulationMode() {
        return touchSimulationMode;
    }

    public void setBitmap(Bitmap bitmap) {
        renderer.setBitmap(bitmap);
    }

    public void setTilt(float pitchDeg, float rollDeg) {
        if (!touchSimulationMode) {
            renderer.setTilt(pitchDeg, rollDeg);
        }
    }

    public void setManualTilt(float pitchDeg, float rollDeg) {
        this.manualPitch = pitchDeg;
        this.manualRoll = rollDeg;
        renderer.setTilt(pitchDeg, rollDeg);
        if (manualTiltListener != null) {
            manualTiltListener.onManualTilt(pitchDeg, rollDeg);
        }
    }

    public void setAperture(float aperture) {
        renderer.setAperture(aperture);
    }

    public void setCameraDistanceFactor(float factor) {
        renderer.setCameraDistanceFactor(factor);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!touchSimulationMode) {
            return gestureDetector.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTouchX = event.getX();
                startTouchY = event.getY();
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float totalDx = event.getX() - startTouchX;
                float totalDy = event.getY() - startTouchY;
                if (Math.abs(totalDx) > 8 || Math.abs(totalDy) > 8) {
                    isDragging = true;
                }

                if (isDragging) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();

                    // Dragging vertically:
                    // dy < 0 (drag up): pitch becomes more negative (bottom edge hinge, top opens)
                    // dy > 0 (drag down): pitch becomes positive (top edge hinge, bottom opens)
                    manualPitch += dy * 0.18f;
                    manualPitch = Math.max(-70.0f, Math.min(manualPitch, 70.0f));

                    // Dragging horizontally:
                    // dx < 0 (drag left): roll becomes negative (left edge hinge, right opens)
                    // dx > 0 (drag right): roll becomes positive (right edge hinge, left opens)
                    manualRoll += dx * 0.15f;
                    manualRoll = Math.max(-50.0f, Math.min(manualRoll, 50.0f));

                    renderer.setTilt(manualPitch, manualRoll);
                    if (manualTiltListener != null) {
                        manualTiltListener.onManualTilt(manualPitch, manualRoll);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                // If finger was released without significant drag, treat as tap
                if (!isDragging) {
                    if (uiToggleListener != null) {
                        uiToggleListener.onToggleUi();
                    }
                }
                return true;
        }

        return true;
    }
}
