/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.UserHandle

internal class ScreenStateMonitor(private val context: Context) : BroadcastReceiver() {
    fun interface Listener {
        fun onScreenStateChanged()
    }

    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val listeners = mutableSetOf<Listener>()

    override fun onReceive(context: Context, intent: Intent) {
        listeners.toList().forEach { it.onScreenStateChanged() }
    }

    fun isReady(): Boolean =
        power.isInteractive && !keyguard.isKeyguardLocked &&
            ActivityManager.getCurrentUser() == UserHandle.myUserId()

    fun addListener(listener: Listener) {
        if (!listeners.add(listener) || listeners.size != 1) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_SWITCHED)
            addAction(Intent.ACTION_USER_FOREGROUND)
            addAction(Intent.ACTION_USER_BACKGROUND)
        }
        context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
    }

    fun removeListener(listener: Listener) {
        if (listeners.remove(listener) && listeners.isEmpty()) context.unregisterReceiver(this)
    }
}
