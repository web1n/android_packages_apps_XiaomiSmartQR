/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.app.INotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.ParceledListSlice
import android.os.ServiceManager
import android.util.Log

internal class NotificationProxy(
    private val context: Context,
    private val packageName: String,
) {
    companion object {
        private const val TAG = "NotificationProxy"
        private const val DEBUG = true
    }

    private val service = INotificationManager.Stub.asInterface(
        ServiceManager.getService(Context.NOTIFICATION_SERVICE)
    )
    private val userId = context.userId
    private val tag = context.packageName

    fun createNotificationChannel(channel: NotificationChannel) {
        try {
            val uid = context.packageManager.getPackageUid(packageName, 0)
            service.createNotificationChannelsForPackage(
                packageName,
                uid,
                ParceledListSlice(listOf(channel)),
            )
            if (DEBUG) Log.d(TAG, "Created channel for $packageName, uid=$uid")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create notification channel for $packageName", e)
        }
    }

    fun notify(id: Int, notification: Notification) {
        try {
            service.enqueueNotificationWithTag(packageName, "android", tag, id, notification, userId)
            if (DEBUG) Log.d(TAG, "Requested notification for $packageName, id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot post notification for $packageName", e)
        }
    }

    fun cancel(id: Int) {
        try {
            service.cancelNotificationWithTag(packageName, "android", tag, id, userId)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot cancel notification for $packageName", e)
        }
    }
}
