/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

internal class QrGestureSensor(context: Context) : SensorEventListener {
    companion object {
        private const val SENSOR_TYPE = "xiaomi.sensor.smart_qr_gesture"

        fun isSupported(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL)
                .any { it.stringType == SENSOR_TYPE }
    }

    fun interface Listener {
        fun onGesture()
    }

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors.getSensorList(Sensor.TYPE_ALL).firstOrNull {
        it.stringType == SENSOR_TYPE
    }
    private val listeners = mutableSetOf<Listener>()
    private var registeredAt = 0L
    var isListening = false
        private set

    fun addListener(listener: Listener) {
        listeners.add(listener)
        if (isListening || sensor == null) return

        registeredAt = SystemClock.elapsedRealtimeNanos()
        isListening = sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isNotEmpty() || !isListening) return

        sensors.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.timestamp < registeredAt || event.values.firstOrNull() != 1.0f) return

        listeners.toList().forEach { it.onGesture() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
