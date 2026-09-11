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
    private int uMVPMatrixLoc;
    private int uModelMatrixLoc;
    private int uTextureLoc;
    private int uTexelSizeLoc;
    private int uApertureLoc;
    private int uMaxBlurPixelsLoc;

    private int aPositionLoc;
    private int aTexCoordLoc;

    // 3D Grid Geometry (48x48 vertices for smooth frosted glass tilt interpolation)
    private static final int GRID_COLS = 48;
    private static final int GRID_ROWS = 48;
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
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
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // Dynamic Tilt State (degrees)
    // X角度: Negative = bottom edge hinge; Positive = top edge hinge
    // Y角度: Negative = left edge hinge; Positive = right edge hinge
    private volatile float pitchDeg = 0f;
    private volatile float rollDeg = 0f;

    // Settings
    private volatile float apertureMultiplier = 1.0f;
    private volatile float cameraDistanceFactor = 1.0f;
    private static final float MAX_BLUR_PIXELS = 64.0f;

    // Half dimensions of the photo on the table
    private float halfH = 1.0f;
    private float halfW = 1.0f;

    // Padding ratio for continuous dark surround so frosted glass diffusion melts into blackness
    private static final float PAD_RATIO = 0.35f;

    public DuoGLRenderer(Context context) {
        this.context = context;
        initMesh((float) viewWidth / (float) viewHeight);
    }

    /**
     * Build a planar subdivided grid for the viewport frosted glass centered at (0, 0, 0) on Z = 0.
     * Dimensions of the core image: Y in [-halfH, +halfH], X in [-halfW, +halfW].
     * Extended by PAD_RATIO so frosted glass diffusion smoothly melts into background.
     * At calibration (pitch = 0, roll = 0), the screen viewport frames the 4 corners of the picture exactly.
     */
    public synchronized void initMesh(float aspect) {
        this.viewportAspect = aspect;
        this.halfH = 1.0f;
        this.halfW = halfH * aspect;

        float extendedHalfW = halfW * (1.0f + PAD_RATIO);
        float extendedHalfH = halfH * (1.0f + PAD_RATIO);

        int totalVertices = (GRID_COLS + 1) * (GRID_ROWS + 1);
        float[] vertices = new float[totalVertices * 3];
        float[] texCoords = new float[totalVertices * 2];

        int vIndex = 0;
        int tIndex = 0;

        for (int r = 0; r <= GRID_ROWS; r++) {
            float vRatio = (float) r / GRID_ROWS; // 0.0 to 1.0
            float y = -extendedHalfH + vRatio * (2.0f * extendedHalfH);

            // Normalized texture coordinate: y = -halfH is bottom (1.0), y = +halfH is top (0.0)
            float vTex = 1.0f - ((y + halfH) / (2.0f * halfH));

            for (int c = 0; c <= GRID_COLS; c++) {
                float uRatio = (float) c / GRID_COLS; // 0.0 to 1.0
                float x = -extendedHalfW + uRatio * (2.0f * extendedHalfW);

                // Normalized texture coordinate: x = -halfW is left (0.0), x = +halfW is right (1.0)
                float uTex = (x + halfW) / (2.0f * halfW);

                // Position on the frosted glass plane (starts flat on table at Z = 0)
                vertices[vIndex++] = x;
                vertices[vIndex++] = y;
                vertices[vIndex++] = 0.0f;

                texCoords[tIndex++] = uTex;
                texCoords[tIndex++] = vTex;
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

        ByteBuffer tbb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tbb.asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

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

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        String vertShader = ShaderUtils.readAssetFile(context, "shaders/dof_vertex.glsl");
        String fragShader = ShaderUtils.readAssetFile(context, "shaders/dof_fragment.glsl");

        programId = ShaderUtils.createProgram(vertShader, fragShader);
        if (programId != 0) {
            uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
            uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix");
            uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture");
            uTexelSizeLoc = GLES20.glGetUniformLocation(programId, "uTexelSize");
            uApertureLoc = GLES20.glGetUniformLocation(programId, "uAperture");
            uMaxBlurPixelsLoc = GLES20.glGetUniformLocation(programId, "uMaxBlurPixels");

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition");
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord");
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

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (programId == 0 || textureId == 0) return;

        // 1. Perspective Projection Matrix
        float fovY = 45.0f;
        float aspect = (float) viewWidth / (float) viewHeight;
        float near = 0.1f;
        float far = 50.0f;
        Matrix.perspectiveM(projectionMatrix, 0, fovY, aspect, near, far);

        // 2. Fixed Camera: Perpendicular directly above the picture at baseDistance, looking down at (0, 0, 0)
        // Never changes position or viewing angle!
        float halfFovRad = (float) Math.toRadians(fovY * 0.5f);
        float baseDistance = (halfH / (float) Math.tan(halfFovRad)) * cameraDistanceFactor;

        Matrix.setLookAtM(viewMatrix, 0,
                0f, 0f, baseDistance,
                0f, 0f, 0f,
                0f, 1f, 0f);

        // 3. Dynamic 4-Edge Hinge Tilting of the Viewport Frosted Glass Plane:
        // The viewport frosted glass starts at Z = 0 (touching the picture plane).
        // Per user requirements:
        // - Camera is perpendicular above the picture and fixed at baseDistance looking down.
        // - Tilting tilts the viewport frosted glass away into depth (Z < 0).
        // - The blurred direction must SHRINK (缩小) in perspective, NOT magnify (放大).
        //
        // 4-Edge Hinge Rules:
        // - X角度为负: 围绕下边沿 (Y = -halfH) 旋转，上边沿向远方倾斜 (Z < 0) 并缩小模糊
        // - X角度为正: 围绕上边沿 (Y = +halfH) 旋转，下边沿向远方倾斜 (Z < 0) 并缩小模糊
        // - Y角度为负: 围绕左边沿 (X = -halfW) 旋转，右边沿向远方倾斜 (Z < 0) 并缩小模糊
        // - Y角度为正: 围绕右边沿 (X = +halfW) 旋转，左边沿向远方倾斜 (Z < 0) 并缩小模糊
        float pivotY = (pitchDeg <= 0f) ? -halfH : +halfH;
        float rotX = pitchDeg;  // Negative pitch rotates Y > pivotY into Z < 0 (shrinks into distance)

        float pivotX = (rollDeg <= 0f) ? -halfW : +halfW;
        float rotY = -rollDeg; // Negative roll rotates X > pivotX into Z < 0 (shrinks into distance)

        Matrix.setIdentityM(modelMatrix, 0);
        // Translate to active hinge pivot on the photo boundary, rotate, translate back
        Matrix.translateM(modelMatrix, 0, pivotX, pivotY, 0f);
        Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f);
        Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f);
        Matrix.translateM(modelMatrix, 0, -pivotX, -pivotY, 0f);

        // Combine MVP matrix
        float[] mvMatrix = new float[16];
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);

        // 4. Set Up Shader and Uniforms
        GLES20.glUseProgram(programId);

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0);

        // Texture and Texel Size
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTextureLoc, 0);
        GLES20.glUniform2f(uTexelSizeLoc, 1.0f / (float) imageWidth, 1.0f / (float) imageHeight);

        // Frosted glass blur uniforms
        GLES20.glUniform1f(uApertureLoc, apertureMultiplier * 1.5f);
        GLES20.glUniform1f(uMaxBlurPixelsLoc, MAX_BLUR_PIXELS);

        // 5. Render Frosted Glass Mesh
        GLES20.glEnableVertexAttribArray(aPositionLoc);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(aTexCoordLoc);
        texCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        indexBuffer.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(aPositionLoc);
        GLES20.glDisableVertexAttribArray(aTexCoordLoc);
    }
}
