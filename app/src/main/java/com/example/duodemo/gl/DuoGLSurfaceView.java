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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }
}
