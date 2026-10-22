/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import org.lineageos.smartqr.R
import org.lineageos.smartqr.util.NotificationProxy

internal class ScanStatusNotification(private val context: Context) {
    private enum class State(val title: Int, val emoji: Int, val timeout: Long) {
        DETECTING(R.string.qr_detecting_title, R.string.qr_detecting_status, 3000L),
        DETECTED(R.string.qr_detected_title, R.string.qr_detected_status, 5000L),
    }

    companion object {
        private const val NOTIFICATION_PACKAGE = "com.android.egg"
        private const val CHANNEL_ID = "smartqr_scan_status"
        private const val NOTIFICATION_ID = 1001
    }

    // SystemUI treats our persistent system UID as foreground and hides its status chip.
    // Post through a package with an independent UID so the chip can remain visible.
    private val manager = NotificationProxy(context, NOTIFICATION_PACKAGE)
    private var state: State? = null

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scan_status_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    fun showDetecting() {
        show(State.DETECTING)
    }

    fun showDetected(text: String) {
        show(State.DETECTED, text)
    }

    fun cancelDetecting() {
        if (state == State.DETECTING) cancel()
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
        state = null
    }

    private fun show(state: State, text: String? = null) {
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_smartqr)
            .setContentTitle(context.getString(state.title))
            .setContentText(text)
            .setShortCriticalText(context.getString(state.emoji))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setTimeoutAfter(state.timeout)
            .addExtras(Bundle().apply {
                putBoolean(Notification.EXTRA_PREFER_SMALL_ICON, true)
                putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, context.getString(R.string.app_name))
            })
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        this.state = state
    }
}
