/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.camera

import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.AvailabilityCallback
import android.hardware.camera2.CameraManager.TorchCallback
import android.util.Log

internal class CameraAvailabilityMonitor(private val context: Context) {
    companion object {
        private const val TAG = "CameraAvailabilityMonitor"
        private const val DEBUG = true
    }

    private val cameras = context.getSystemService(CameraManager::class.java)
    private val privacy = context.getSystemService(SensorPrivacyManager::class.java)

    private val cameraStates = mutableMapOf<String, Boolean>()
    private val torchStates = mutableMapOf<String, Boolean>()
    private var registered = false

    private val isCameraAvailable: Boolean
        get() = cameraStates.isNotEmpty() && cameraStates.values.all { it }
    private val isTorchAvailable: Boolean
        get() = torchStates.values.all { it }
    private val isPrivacyEnabled: Boolean
        get() = privacy.areAnySensorPrivacyTogglesEnabled(CAMERA)
    val isAvailable: Boolean
        get() = isCameraAvailable && isTorchAvailable && !isPrivacyEnabled

    private val cameraCallback = object : AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) = updateCamera(cameraId, true)
        override fun onCameraUnavailable(cameraId: String) = updateCamera(cameraId, false)
    }
    private val torchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) =
            updateTorch(cameraId, !enabled)
        override fun onTorchModeUnavailable(cameraId: String) = updateTorch(cameraId, false)
    }

    fun start() {
        if (registered) return
        registered = true

        try {
            initializeStates()

            cameras.registerAvailabilityCallback(context.mainExecutor, cameraCallback)
            cameras.registerTorchCallback(context.mainExecutor, torchCallback)
        } catch (e: Exception) {
            stop()
            Log.e(TAG, "Cannot monitor camera availability", e)
        }
    }

    fun stop() {
        if (registered) {
            cameras.unregisterAvailabilityCallback(cameraCallback)
            cameras.unregisterTorchCallback(torchCallback)
        }
        registered = false

        cameraStates.clear()
        torchStates.clear()
    }

    private fun initializeStates() {
        cameras.cameraIdList.forEach { id ->
            cameraStates[id] = false
            val hasFlash = cameras.getCameraCharacteristics(id).get(FLASH_INFO_AVAILABLE) == true
            if (hasFlash) torchStates[id] = false
        }
    }

    private fun updateCamera(cameraId: String, available: Boolean) {
        cameraStates[cameraId] = available
        if (DEBUG) Log.d(TAG, "Camera $cameraId available=$available, all=$isCameraAvailable")
    }

    private fun updateTorch(cameraId: String, available: Boolean) {
        torchStates[cameraId] = available
        if (DEBUG) Log.d(TAG, "Torch $cameraId available=$available, all=$isTorchAvailable")
    }
}
