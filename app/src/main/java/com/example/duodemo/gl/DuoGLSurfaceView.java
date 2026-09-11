package com.example.duodemo.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

public class DuoGLSurfaceView extends GLSurfaceView {
    private final DuoGLRenderer renderer;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private OnUiToggleListener uiToggleListener;

    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float startTouchX = 0f;
    private float startTouchY = 0f;
    private boolean isDraggingPhoto = false;
    private int touchSlop = 16;
    private boolean isGestureTransformEnabled = false;

    public interface OnUiToggleListener {
        void onToggleUi();
    }

    public DuoGLSurfaceView(Context context) {
        this(context, null);
    }

    public DuoGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Configure OpenGL ES 2.0 / 3.0 context
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        renderer = new DuoGLRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        initGestureDetector(context);
    }

    private void initGestureDetector(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Two-finger pinch to scale photo size on desktop
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!isGestureTransformEnabled) {
                    return false;
                }
                float factor = detector.getScaleFactor();
                renderer.scalePhoto(factor);
                return true;
            }
        });

        // Tap & double-tap detector
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (uiToggleListener != null) {
                    uiToggleListener.onToggleUi();
                    return true;
                }
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isGestureTransformEnabled) {
                    renderer.resetPhotoTransform();
                    return true;
                }
                return super.onDoubleTap(e);
            }
        });
    }

    public void setOnUiToggleListener(OnUiToggleListener listener) {
        this.uiToggleListener = listener;
    }

    public void setBitmap(Bitmap bitmap) {
        renderer.setBitmap(bitmap);
    }

    public void setTilt(float pitchDeg, float rollDeg) {
        renderer.setTilt(pitchDeg, rollDeg);
    }

    public void setAperture(float aperture) {
        renderer.setAperture(aperture);
    }

    public void setCameraDistanceFactor(float factor) {
        renderer.setCameraDistanceFactor(factor);
    }

    public void setFov(float fov) {
        renderer.setFov(fov);
    }

    public float getFov() {
        return renderer.getFov();
    }

    public void setPhotoScale(float scale) {
        renderer.setPhotoScale(scale);
    }

    public float getPhotoScale() {
        return renderer.getPhotoScale();
    }

    public void setPhotoOffset(float x, float y) {
        renderer.setPhotoOffset(x, y);
    }

    public void translatePhoto(float dx, float dy) {
        renderer.translatePhoto(dx, dy);
    }

    public void resetPhotoTransform() {
        renderer.resetPhotoTransform();
    }

    public float getPhotoBaseHalfW() {
        return renderer.getPhotoBaseHalfW();
    }

    public float getPhotoBaseHalfH() {
        return renderer.getPhotoBaseHalfH();
    }

    public void setGestureTransformEnabled(boolean enabled) {
        this.isGestureTransformEnabled = enabled;
    }

    public boolean isGestureTransformEnabled() {
        return isGestureTransformEnabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isGestureTransformEnabled) {
            scaleGestureDetector.onTouchEvent(event);
        }
        gestureDetector.onTouchEvent(event);

        if (!isGestureTransformEnabled) {
            return true;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startTouchX = event.getX();
                startTouchY = event.getY();
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDraggingPhoto = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers down, pinch zoom takes over
                isDraggingPhoto = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleGestureDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    float totalDist = (float) Math.hypot(event.getX() - startTouchX, event.getY() - startTouchY);

                    if (totalDist > touchSlop) {
                        isDraggingPhoto = true;
                    }

                    if (isDraggingPhoto) {
                        float halfW = renderer.getHalfW();
                        float halfH = renderer.getHalfH();
                        int w = Math.max(1, getWidth());
                        int h = Math.max(1, getHeight());

                        // Map screen drag pixels to desktop 3D world coordinates
                        float worldDx = (dx / (float) w) * (2.0f * halfW);
                        float worldDy = -(dy / (float) h) * (2.0f * halfH);

                        renderer.translatePhoto(worldDx, worldDy);
                    }
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDraggingPhoto = false;
                break;
        }

        return true;
    }
}
