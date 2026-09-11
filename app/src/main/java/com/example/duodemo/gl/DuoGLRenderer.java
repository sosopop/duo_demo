package com.example.duodemo.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DuoGLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "DuoGLRenderer";

    private final Context context;

    // Shader program and uniforms
    private int programId = 0;
    private int uModelMatrixLoc;
    private int uScreenHalfSizeLoc;
    private int uPhotoHalfSizeLoc;
    private int uCameraDistanceLoc;
    private int uTextureLoc;
    private int uTexelSizeLoc;
    private int uApertureLoc;
    private int uMaxBlurPixelsLoc;

    private int aPositionLoc;

    // Fullscreen Screen-Space Grid Geometry (48x48 quads)
    private static final int GRID_COLS = 48;
    private static final int GRID_ROWS = 48;
    private FloatBuffer vertexBuffer;
    private ShortBuffer indexBuffer;
    private int indexCount = 0;

    // Texture
    private int textureId = 0;
    private Bitmap pendingBitmap = null;
    private boolean hasNewBitmap = false;
    private int imageWidth = 1080;
    private int imageHeight = 2400;

    // Viewport and Matrices
    private int viewWidth = 1080;
    private int viewHeight = 2400;
    private float viewportAspect = 1.0f;
    private final float[] modelMatrix = new float[16];

    // Dynamic Tilt State (degrees)
    // X角度: Negative = bottom edge hinge; Positive = top edge hinge
    // Y角度: Negative = left edge hinge; Positive = right edge hinge
    private volatile float pitchDeg = 0f;
    private volatile float rollDeg = 0f;

    // Settings
    private volatile float fovY = 30.0f; // Camera vertical FOV in degrees (default 30.0°, natural human eye perspective)
    private volatile float apertureMultiplier = 1.0f;
    private volatile float cameraDistanceFactor = 1.0f;
    private static final float MAX_BLUR_PIXELS = 160.0f;

    // Half dimensions of the phone screen and photo
    private float halfH = 1.0f;
    private float halfW = 1.0f;

    public DuoGLRenderer(Context context) {
        this.context = context;
        initMesh((float) viewWidth / (float) viewHeight);
    }

    /**
     * Build a planar subdivided grid for the physical screen viewport.
     * Dimensions: Y in [-halfH, +halfH], X in [-halfW, +halfW].
     * The phone screen is the frosted glass in the user's hand!
     */
    public synchronized void initMesh(float aspect) {
        this.viewportAspect = aspect;
        this.halfH = 1.0f;
        this.halfW = halfH * aspect;

        int totalVertices = (GRID_COLS + 1) * (GRID_ROWS + 1);
        float[] vertices = new float[totalVertices * 2]; // 2D (x, y) coordinates

        int vIndex = 0;
        for (int r = 0; r <= GRID_ROWS; r++) {
            float vRatio = (float) r / GRID_ROWS; // 0.0 to 1.0
            float y = -halfH + vRatio * (2.0f * halfH);

            for (int c = 0; c <= GRID_COLS; c++) {
                float uRatio = (float) c / GRID_COLS; // 0.0 to 1.0
                float x = -halfW + uRatio * (2.0f * halfW);

                vertices[vIndex++] = x;
                vertices[vIndex++] = y;
            }
        }

        // Indices for TRIANGLES
        indexCount = GRID_COLS * GRID_ROWS * 6;
        short[] indices = new short[indexCount];
        int iIndex = 0;

        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                short bl = (short) (r * (GRID_COLS + 1) + c);
                short br = (short) (bl + 1);
                short tl = (short) ((r + 1) * (GRID_COLS + 1) + c);
                short tr = (short) (tl + 1);

                indices[iIndex++] = bl;
                indices[iIndex++] = br;
                indices[iIndex++] = tl;

                indices[iIndex++] = tl;
                indices[iIndex++] = br;
                indices[iIndex++] = tr;
            }
        }

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        indexBuffer = ibb.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);
    }

    public synchronized void setBitmap(Bitmap bitmap) {
        if (bitmap == null) return;
        this.pendingBitmap = bitmap;
        this.hasNewBitmap = true;
    }

    public void setTilt(float pitch, float roll) {
        this.pitchDeg = Math.max(-80.0f, Math.min(pitch, 80.0f));
        this.rollDeg = Math.max(-60.0f, Math.min(roll, 60.0f));
    }

    public void setAperture(float aperture) {
        this.apertureMultiplier = aperture;
    }

    public void setCameraDistanceFactor(float factor) {
        this.cameraDistanceFactor = factor;
    }

    public void setFov(float fov) {
        this.fovY = Math.max(5.0f, Math.min(fov, 120.0f));
    }

    public float getFov() {
        return fovY;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        String vertShader = ShaderUtils.readAssetFile(context, "shaders/dof_vertex.glsl");
        String fragShader = ShaderUtils.readAssetFile(context, "shaders/dof_fragment.glsl");

        programId = ShaderUtils.createProgram(vertShader, fragShader);
        if (programId != 0) {
            uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix");
            uScreenHalfSizeLoc = GLES20.glGetUniformLocation(programId, "uScreenHalfSize");
            uPhotoHalfSizeLoc = GLES20.glGetUniformLocation(programId, "uPhotoHalfSize");
            uCameraDistanceLoc = GLES20.glGetUniformLocation(programId, "uCameraDistance");
            uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture");
            uTexelSizeLoc = GLES20.glGetUniformLocation(programId, "uTexelSize");
            uApertureLoc = GLES20.glGetUniformLocation(programId, "uAperture");
            uMaxBlurPixelsLoc = GLES20.glGetUniformLocation(programId, "uMaxBlurPixels");

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
        GLES20.glViewport(0, 0, width, height);

        float screenAspect = (float) width / (float) height;
        initMesh(screenAspect);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Upload new texture if requested
        synchronized (this) {
            if (hasNewBitmap && pendingBitmap != null && !pendingBitmap.isRecycled()) {
                if (textureId != 0) {
                    GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                }
                textureId = ShaderUtils.loadTexture(pendingBitmap);
                imageWidth = pendingBitmap.getWidth();
                imageHeight = pendingBitmap.getHeight();
                hasNewBitmap = false;
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (programId == 0 || textureId == 0) return;

        // Virtual camera distance directly above desktop photo (Z = 0)
        float halfFovRad = (float) Math.toRadians(fovY * 0.5f);
        float baseDistance = (halfH / (float) Math.tan(halfFovRad)) * cameraDistanceFactor;

        // 3D Tilting of the Viewport Frosted Glass Plane:
        // The phone screen is the frosted glass in the user's hand!
        // As user tilts the phone:
        // - X角度为负: 围绕下边沿 (Y = -halfH) 旋转，上边沿抬起向上 (Z > 0)
        // - X角度为正: 围绕上边沿 (Y = +halfH) 旋转，下边沿抬起向上 (Z > 0)
        // - Y角度为负: 围绕左边沿 (X = -halfW) 旋转，右边沿抬起向上 (Z > 0)
        // - Y角度为正: 围绕右边沿 (X = +halfW) 旋转，左边沿抬起向上 (Z > 0)
        float pivotY = (pitchDeg <= 0f) ? -halfH : +halfH;
        float rotX = -pitchDeg; // Lifted edge tilts UP into Z > 0

        float pivotX = (rollDeg <= 0f) ? -halfW : +halfW;
        float rotY = rollDeg;   // Lifted edge tilts UP into Z > 0

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, pivotX, pivotY, 0f);
        Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f);
        Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f);
        Matrix.translateM(modelMatrix, 0, -pivotX, -pivotY, 0f);

        GLES20.glUseProgram(programId);

        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0);
        GLES20.glUniform2f(uScreenHalfSizeLoc, halfW, halfH);
        GLES20.glUniform2f(uPhotoHalfSizeLoc, halfW, halfH);
        GLES20.glUniform1f(uCameraDistanceLoc, baseDistance);

        // Texture and Texel Size
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTextureLoc, 0);
        GLES20.glUniform2f(uTexelSizeLoc, 1.0f / (float) imageWidth, 1.0f / (float) imageHeight);

        // Frosted glass blur uniforms
        GLES20.glUniform1f(uApertureLoc, apertureMultiplier * 1.0f);
        GLES20.glUniform1f(uMaxBlurPixelsLoc, MAX_BLUR_PIXELS);

        // Render full-screen frosted glass mesh
        GLES20.glEnableVertexAttribArray(aPositionLoc);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        indexBuffer.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(aPositionLoc);
    }
}
