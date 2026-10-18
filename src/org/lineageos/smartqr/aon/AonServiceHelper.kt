/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.aon

import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import vendor.xiaomi.hardware.aon.IAlwaysOn
import vendor.xiaomi.hardware.aon.IAlwaysOnListener

internal class AonServiceHelper(onResult: (Boolean) -> Unit, onDied: () -> Unit) {
    companion object {
        private const val TAG = "AonServiceHelper"
        private const val DEBUG = true
        private const val SERVICE = "vendor.xiaomi.hardware.aon.IAlwaysOn/miaonservicehal"
        private const val QR_SERVICE_TYPE = 3
        private const val REQUEST_TIMEOUT_MS = 4000
        private const val FRAME_RATE = 7.0f
    }

    private var service: IAlwaysOn? = null
    private val death = IBinder.DeathRecipient { onDied() }
    private val callback = object : IAlwaysOnListener.Stub() {
        override fun getInterfaceVersion() = IAlwaysOnListener.VERSION
        override fun getInterfaceHash() = IAlwaysOnListener.HASH

        override fun onCallbackListener(type: Int, data: IntArray?) {
            if (DEBUG) Log.d(TAG, "Callback: type=$type data=${data?.contentToString()}")
            if (data == null || data.size < 3 || data[0] != QR_SERVICE_TYPE ||
                data[1] < 1 || data[2] and 3 == 0) return

            onResult(data[2] and 1 != 0)
        }
    }

    fun register() {
        val binder = ServiceManager.checkService(SERVICE)
            ?: error("AON service is not registered")
        val connected = IAlwaysOn.Stub.asInterface(binder)
        binder.linkToDeath(death, 0)
        service = connected

        val status = connected.registerListener(QR_SERVICE_TYPE, FRAME_RATE, REQUEST_TIMEOUT_MS, callback)
        if (DEBUG) Log.d(TAG, "Register listener: status=$status")
        check(status >= 0) { "AON registration failed: $status" }
    }

    fun release(): Boolean {
        val connected = service ?: return true
        val binder = connected.asBinder()

        try {
            if (binder.isBinderAlive) {
                val status = connected.unregisterListener(QR_SERVICE_TYPE, callback)
                if (DEBUG) Log.d(TAG, "Unregister listener: status=$status")
                check(status == 0 || status == -1) { "Unexpected unregister status=$status" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot unregister AON listener", e)
            if (binder.isBinderAlive) return false
        }

        try {
            binder.unlinkToDeath(death, 0)
        } catch (_: java.util.NoSuchElementException) {
        }

        service = null
        return true
    }
}
