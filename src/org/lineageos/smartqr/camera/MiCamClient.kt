/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.IServiceCallback
import android.os.IServiceManager
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.android.internal.os.BinderInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import vendor.xiaomi.sensor.camera.Ashmem
import vendor.xiaomi.sensor.camera.IMiCam
import vendor.xiaomi.sensor.camera.IMiCamCallback

internal class MiCamClient(
    private val context: Context,
    private val onFrame: (Frame) -> Unit,
    private val onError: (Exception) -> Unit,
) {
    companion object {
        private const val TAG = "MiCamClient"
        private const val DEBUG = true
        private const val SERVICE = "vendor.xiaomi.sensor.camera.IMiCam/default"
        private const val CONNECT_TIMEOUT_MS = 1000L
    }

    data class Frame(val data: ByteArray, val width: Int, val height: Int)

    private var service: IMiCam? = null
    private var memory: MemoryFile? = null
    private var opened = false
    private val death = IBinder.DeathRecipient { onError(DeadObjectException()) }
    private val callback = object : IMiCamCallback.Stub() {
        override fun getInterfaceVersion() = IMiCamCallback.VERSION
        override fun getInterfaceHash() = IMiCamCallback.HASH

        override fun onFrameEvent(event: Int, width: Int, height: Int, format: Int) {
            try {
                check(event == 0) { "Camera event=$event" }
                val frame = readFrame(width, height) ?: return
                onFrame(frame)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    suspend fun open() = withContext(Dispatchers.IO) {
        val connected = connect()
        service = connected
        connected.asBinder().linkToDeath(death, 0)

        val size = previewSize()
        val buffer = MemoryFile("SmartQrFrame", size.width * size.height * 3 / 2)
        synchronized(this@MiCamClient) { memory = buffer }
        currentCoroutineContext().ensureActive()
        openCamera(connected, buffer, size)
        opened = true
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        val connected = service ?: return@withContext
        try {
            if (opened && connected.asBinder().isBinderAlive) {
                val status = connected.closeCam(1, 1)
                if (DEBUG) Log.d(TAG, "Close camera: status=$status")
                check(status == 0) { "Cannot close MiCam: $status" }
            }
        } finally {
            opened = false
            service = null
            synchronized(this@MiCamClient) {
                memory?.close()
                memory = null
            }
            try {
                connected.asBinder().unlinkToDeath(death, 0)
            } catch (_: java.util.NoSuchElementException) {
            }
        }
    }

    private suspend fun connect(): IMiCam {
        val manager = IServiceManager.Stub.asInterface(
            Binder.allowBlocking(BinderInternal.getContextObject())
        )
        val result = CompletableDeferred<IBinder>()
        val callback = object : IServiceCallback.Stub() {
            override fun onRegistration(name: String, binder: IBinder) {
                result.complete(Binder.allowBlocking(binder))
            }
        }
        manager.registerForNotifications(SERVICE, callback)
        try {
            val binder = withTimeout(CONNECT_TIMEOUT_MS) {
                manager.getService(SERVICE)?.let { result.complete(Binder.allowBlocking(it)) }
                result.await()
            }
            return IMiCam.Stub.asInterface(binder)
        } finally {
            withContext(NonCancellable) {
                manager.unregisterForNotifications(SERVICE, callback)
            }
        }
    }

    private fun openCamera(
        connected: IMiCam,
        buffer: MemoryFile,
        size: Size,
    ) {
        ParcelFileDescriptor.dup(buffer.fileDescriptor).use { descriptor ->
            val ashmem = Ashmem().apply {
                fd = descriptor
                this.size = buffer.length().toLong()
            }
            val name = "scanner"
            val config = intArrayOf(size.width, size.height, name.length) +
                name.map { it.code - '0'.code }
            val status = connected.openCam(1, 1, callback, ashmem, config)
            if (DEBUG) Log.d(TAG, "Open camera: status=$status size=$size")
            check(status == 0) { "Cannot open MiCam: $status" }
        }
    }

    private fun readFrame(width: Int, height: Int): Frame? {
        synchronized(this) {
            val buffer = memory ?: return null
            val data = ByteArray(buffer.length())
            buffer.readBytes(data, 0, 0, data.size)
            return Frame(data, width, height)
        }
    }

    private fun previewSize(): Size {
        val cameras = context.getSystemService(CameraManager::class.java)
        val id = cameras.cameraIdList.first { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        val map = cameras.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: error("Camera stream configurations are unavailable")
        val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            ?: error("Camera does not support YUV frames")
        return sizes.filter { it.width <= 1280 && it.height <= 1280 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: error("Camera has no supported preview size")
    }
}
