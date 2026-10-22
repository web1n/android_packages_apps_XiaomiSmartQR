/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.lineageos.smartqr.R

internal class ScanStatusNotification(private val context: Context) {
    private enum class State(val title: Int, val emoji: Int, val timeout: Long) {
        DETECTING(R.string.qr_detecting_title, R.string.qr_detecting_status, 3000L),
        DETECTED(R.string.qr_detected_title, R.string.qr_detected_status, 5000L),
    }

    companion object {
        private const val CHANNEL_ID = "scan_status"
        private const val NOTIFICATION_ID = 1001
    }

    private val manager = context.getSystemService(NotificationManager::class.java)
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
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setTimeoutAfter(state.timeout)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        this.state = state
    }
}
