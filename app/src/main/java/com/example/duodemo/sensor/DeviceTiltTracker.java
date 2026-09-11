package com.example.duodemo.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class DeviceTiltTracker implements SensorEventListener {
    private static final String TAG = "DeviceTiltTracker";

    public interface OnTiltListener {
        void onTilt(float pitchDeg, float rollDeg, float rawPitchDeg, float rawRollDeg);
    }

    private final SensorManager sensorManager;
    private Sensor rotationSensor;
    private OnTiltListener listener;

    private final float[] currentRotationMatrix = new float[9];
    private final float[] calibRotationMatrix = new float[9];
    private final float[] relativeRotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final float[] rawOrientationAngles = new float[3];

    // Raw attitudes (Earth reference)
    private float lastRawPitchDeg = 0f;
    private float lastRawRollDeg = 0f;

    // Calibration state
    private boolean isCalibrated = false;

    // Smoothed output (low pass filter)
    private float filteredPitchDeg = 0f;
    private float filteredRollDeg = 0f;

    private boolean isTracking = false;

    public DeviceTiltTracker(Context context) {
        // Initialize matrices to 3x3 identity
        calibRotationMatrix[0] = 1f; calibRotationMatrix[4] = 1f; calibRotationMatrix[8] = 1f;
        currentRotationMatrix[0] = 1f; currentRotationMatrix[4] = 1f; currentRotationMatrix[8] = 1f;

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                // Fallback to game rotation vector or gravity
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            }
            if (rotationSensor == null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            }
        }
    }

    public void setOnTiltListener(OnTiltListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (isTracking || sensorManager == null || rotationSensor == null) return;
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        isTracking = true;
    }

    public void stop() {
        if (!isTracking || sensorManager == null) return;
        sensorManager.unregisterListener(this);
        isTracking = false;
    }

    public void calibrateCurrentAsZero() {
        // Snapshot the current 3D world orientation as the calibrated baseline pose
        System.arraycopy(currentRotationMatrix, 0, calibRotationMatrix, 0, 9);
        isCalibrated = true;
        filteredPitchDeg = 0f;
        filteredRollDeg = 0f;
    }

    public void resetCalibrationToAbsolute() {
        isCalibrated = false;
        calibRotationMatrix[0] = 1f; calibRotationMatrix[1] = 0f; calibRotationMatrix[2] = 0f;
        calibRotationMatrix[3] = 0f; calibRotationMatrix[4] = 1f; calibRotationMatrix[5] = 0f;
        calibRotationMatrix[6] = 0f; calibRotationMatrix[7] = 0f; calibRotationMatrix[8] = 1f;
    }

    /**
     * Multiplies transpose of 3x3 matrix A with matrix B: C = A^T * B
     * Computes relative rotation from calibrated frame A to current frame B.
     */
    private static void multiplyTransposeA(float[] a, float[] b, float[] c) {
        c[0] = a[0] * b[0] + a[3] * b[3] + a[6] * b[6];
        c[1] = a[0] * b[1] + a[3] * b[4] + a[6] * b[7];
        c[2] = a[0] * b[2] + a[3] * b[5] + a[6] * b[8];

        c[3] = a[1] * b[0] + a[4] * b[3] + a[7] * b[6];
        c[4] = a[1] * b[1] + a[4] * b[4] + a[7] * b[7];
        c[5] = a[1] * b[2] + a[4] * b[5] + a[7] * b[8];

        c[6] = a[2] * b[0] + a[5] * b[3] + a[8] * b[6];
        c[7] = a[2] * b[1] + a[5] * b[4] + a[8] * b[7];
        c[8] = a[2] * b[2] + a[5] * b[5] + a[8] * b[8];
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR ||
            event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(currentRotationMatrix, event.values);

            // Raw absolute orientation (relative to Earth horizontal)
            SensorManager.getOrientation(currentRotationMatrix, rawOrientationAngles);
            float rawPitch = (float) Math.toDegrees(rawOrientationAngles[1]);
            float rawRoll = (float) Math.toDegrees(rawOrientationAngles[2]);
            lastRawPitchDeg = rawPitch;
            lastRawRollDeg = rawRoll;

            float relativePitch;
            float relativeRoll;

            if (isCalibrated) {
                // 3D Relative Matrix: R_rel = R_calib^T * R_current
                // Mathematically transforms the rotation axes to follow the calibrated orientation!
                // Rotating about the phone's bottom/top edge produces pure relative Pitch.
                // Rotating about the phone's left/right edge produces pure relative Roll.
                multiplyTransposeA(calibRotationMatrix, currentRotationMatrix, relativeRotationMatrix);
                SensorManager.getOrientation(relativeRotationMatrix, orientationAngles);

                relativePitch = (float) Math.toDegrees(orientationAngles[1]);
                relativeRoll = (float) Math.toDegrees(orientationAngles[2]);
            } else {
                relativePitch = rawPitch;
                relativeRoll = rawRoll;
            }

            // Adaptive instant-response filter:
            // When moving fast (large delta), alpha scales up to 0.92 for immediate real-time response (< 10ms)
            // When stationary, alpha drops to 0.35 to completely eliminate sensor micro-jitter
            float deltaPitch = relativePitch - filteredPitchDeg;
            float deltaRoll = relativeRoll - filteredRollDeg;
            float maxDelta = Math.max(Math.abs(deltaPitch), Math.abs(deltaRoll));
            float dynamicAlpha = Math.min(0.92f, Math.max(0.35f, maxDelta * 0.30f));

            filteredPitchDeg += dynamicAlpha * deltaPitch;
            filteredRollDeg += dynamicAlpha * deltaRoll;

            if (listener != null) {
                listener.onTilt(filteredPitchDeg, filteredRollDeg, rawPitch, rawRoll);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
