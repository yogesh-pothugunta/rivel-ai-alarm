package com.example.mission.movement

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class MovementMissionTracker(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()

    private val _isSensorAvailable = MutableStateFlow(true)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable.asStateFlow()

    private var targetSteps = 20
    private var lastAccelMagnitude = 0.0
    private var lastStepTime = 0L

    fun isSupported(): Boolean {
        return stepDetectorSensor != null || accelerometerSensor != null
    }

    fun startTracking(target: Int) {
        targetSteps = target
        _currentSteps.value = 0

        if (stepDetectorSensor != null) {
            _isSensorAvailable.value = true
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelerometerSensor != null) {
            _isSensorAvailable.value = true
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            _isSensorAvailable.value = false
        }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    fun simulateStepManual() {
        // Fallback for user or testing
        _currentSteps.value = (_currentSteps.value + 1).coerceAtMost(targetSteps)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            _currentSteps.value = (_currentSteps.value + 1).coerceAtMost(targetSteps)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Heuristic peak detector for walking motion
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble())
            val delta = magnitude - 9.81 // gravity offset

            val now = System.currentTimeMillis()
            if (delta > 3.5 && (now - lastStepTime) > 350) {
                lastStepTime = now
                _currentSteps.value = (_currentSteps.value + 1).coerceAtMost(targetSteps)
            }
            lastAccelMagnitude = magnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
