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

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    // Raw attitudes
    private float lastRawPitchDeg = 0f;
    private float lastRawRollDeg = 0f;

    // Calibration offsets
    private float pitchOffsetDeg = 0f;
    private float rollOffsetDeg = 0f;

    // Smoothed output (low pass filter)
    private float filteredPitchDeg = 0f;
    private float filteredRollDeg = 0f;
    private static final float LPF_ALPHA = 0.18f;

    private boolean isTracking = false;

    public DeviceTiltTracker(Context context) {
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
        // Record current raw orientation as zero baseline
        pitchOffsetDeg = lastRawPitchDeg;
        rollOffsetDeg = lastRawRollDeg;
        filteredPitchDeg = 0f;
        filteredRollDeg = 0f;
    }

    public void resetCalibrationToAbsolute() {
        pitchOffsetDeg = 0f;
        rollOffsetDeg = 0f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR ||
            event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // orientationAngles[0] is Yaw/Azimuth (ignored per user request)
            // orientationAngles[1] is Pitch (negative when tilting top up / away from table)
            // orientationAngles[2] is Roll (left/right roll)
            float rawPitch = (float) Math.toDegrees(orientationAngles[1]);
            float rawRoll = (float) Math.toDegrees(orientationAngles[2]);

            lastRawPitchDeg = rawPitch;
            lastRawRollDeg = rawRoll;

            // Compute relative angles from calibrated zero
            float relativePitch = rawPitch - pitchOffsetDeg;
            float relativeRoll = rawRoll - rollOffsetDeg;

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
