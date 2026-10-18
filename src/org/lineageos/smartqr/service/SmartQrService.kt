/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.lineageos.smartqr.DesktopMonitor
import org.lineageos.smartqr.QrDetector
import org.lineageos.smartqr.QrGestureSensor
import org.lineageos.smartqr.R
import org.lineageos.smartqr.ScreenStateMonitor
import org.lineageos.smartqr.util.SmartQrPreferences

class SmartQrService : Service() {
    companion object {
        private const val TAG = "SmartQrService"
        private const val DEBUG = true

        private const val DESKTOP_SETTLE_MS = 500L
        private const val DETECTED_COOLDOWN_MS = 2000L
        private const val RETRY_COOLDOWN_MS = 1000L

        internal fun start(context: Context) {
            context.startService(Intent(context, SmartQrService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground by lazy { DesktopMonitor() }
    private val screen by lazy { ScreenStateMonitor(this) }
    private val gesture by lazy { QrGestureSensor(this) }
    private val detector by lazy {
        QrDetector(scope, ::isReady, ::onDetectionFinished)
    }

    private val foregroundListener = DesktopMonitor.Listener { _ ->
        scope.launch { refresh() }
    }
    private val screenListener = ScreenStateMonitor.Listener { refresh() }
    private val gestureListener = QrGestureSensor.Listener { beginDetection() }

    private var destroyed = false
    private var desktopSince = 0L
    private var cooldownUntil = 0L

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "Creating service")
        foreground.addListener(foregroundListener)
        screen.addListener(screenListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "Starting service")
        refresh()
        return if (SmartQrPreferences.isEnabled(this)) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service")
        destroyed = true
        foreground.removeListener(foregroundListener)
        screen.removeListener(screenListener)
        gesture.removeListener(gestureListener)

        detector.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun beginDetection() {
        refresh()
        val now = SystemClock.elapsedRealtime()
        if (
            destroyed ||
                desktopSince == 0L ||
                now < cooldownUntil ||
                now - desktopSince < DESKTOP_SETTLE_MS
        ) {
            return
        }

        detector.start()
    }

    private fun refresh() {
        if (destroyed) return

        val enabled = SmartQrPreferences.isEnabled(this)
        val desktop = enabled && screen.isReady() && foreground.isHomeForeground()
        if (desktop) {
            gesture.addListener(gestureListener)
        } else {
            gesture.removeListener(gestureListener)
        }

        if (desktop && gesture.isListening) {
            if (desktopSince == 0L) desktopSince = SystemClock.elapsedRealtime()
        } else {
            desktopSince = 0
            detector.cancel()
        }
        if (!enabled) stopSelf()
    }

    private fun onDetectionFinished(result: Boolean) {
        val cooldown = if (result) DETECTED_COOLDOWN_MS else RETRY_COOLDOWN_MS
        cooldownUntil = SystemClock.elapsedRealtime() + cooldown
        if (!result || !isReady()) return

        Toast.makeText(this, R.string.qr_detected_toast, Toast.LENGTH_SHORT).show()
    }

    private fun isReady(): Boolean =
        !destroyed && SmartQrPreferences.isEnabled(this) && gesture.isListening &&
            screen.isReady() && foreground.isHomeForeground()
}
