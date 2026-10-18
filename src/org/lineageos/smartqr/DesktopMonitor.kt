/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr

import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.app.WindowConfiguration
import android.util.Log

internal class DesktopMonitor : TaskStackListener() {
    companion object {
        private const val TAG = "DesktopMonitor"
        private const val DEBUG = true
    }

    fun interface Listener {
        fun onDesktopChanged(isDesktop: Boolean)
    }

    private val listeners = mutableSetOf<Listener>()
    private var lastDesktop = false
    @Volatile var isAvailable = false
        private set

    override fun onTaskStackChanged() {
        notifyListeners()
    }

    override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
        notifyListeners()
    }

    @Synchronized
    fun addListener(listener: Listener) {
        listeners.add(listener)
        if (isAvailable) return

        try {
            ActivityTaskManager.getService().registerTaskStackListener(this)
            isAvailable = true
            if (DEBUG) Log.d(TAG, "Desktop monitor started")
            lastDesktop = isHomeForeground()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot monitor desktop state", e)
        }
    }

    @Synchronized
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isNotEmpty() || !isAvailable) return

        runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(this) }
            .onFailure { Log.w(TAG, "Cannot remove activity listener", it) }
        isAvailable = false
    }

    fun isHomeForeground(): Boolean {
        if (!isAvailable) return false

        return try {
            val task = ActivityTaskManager.getService().getFocusedRootTaskInfo()
            task?.activityType == WindowConfiguration.ACTIVITY_TYPE_HOME
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read desktop state", e)
            false
        }
    }

    private fun notifyListeners() {
        val desktop: Boolean
        val callbacks = synchronized(this) {
            if (!isAvailable) return

            desktop = isHomeForeground()
            if (desktop == lastDesktop) return

            lastDesktop = desktop
            listeners.toList()
        }

        if (DEBUG) Log.d(TAG, "Desktop state changed: $desktop")
        callbacks.forEach { it.onDesktopChanged(desktop) }
    }
}
