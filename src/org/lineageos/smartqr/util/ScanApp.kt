/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import java.net.URISyntaxException
import org.lineageos.smartqr.R

internal data class ScanApp(
    val value: String,
    val title: String,
    private val packageName: String? = null,
    private val intentUri: String? = null,
) {
    companion object {
        const val CHOOSER = "chooser"
        private const val TAG = "ScanApp"
        private const val DEBUG = true

        fun entries(context: Context): List<ScanApp> {
            val resources = context.resources
            val apps = resources.obtainTypedArray(R.array.scan_apps)
            try {
                return List(apps.length()) { index ->
                    val entry = resources.getStringArray(apps.getResourceId(index, 0))
                    ScanApp(entry[0], entry[1], entry.getOrNull(2), entry.getOrNull(3))
                }
            } finally {
                apps.recycle()
            }
        }

        fun fromValue(context: Context, value: String?): ScanApp {
            val apps = entries(context)
            return apps.firstOrNull { it.value == value } ?: apps.first { it.value == CHOOSER }
        }
    }

    fun unavailableReason(context: Context): Int {
        if (resolveIntent(context) != null) return 0

        val name = packageName

        if (name != null) {
            try {
                if (!context.packageManager.getApplicationInfo(name, 0).enabled) {
                    return R.string.scan_app_not_installed
                }
            } catch (_: PackageManager.NameNotFoundException) {
                return R.string.scan_app_not_installed
            }
        }

        return R.string.scan_app_unavailable
    }

    fun open(context: Context) {
        if (DEBUG) Log.d(TAG, "Opening scanner: $value")
        try {
            val intent = resolveIntent(context)
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot launch scanner: $value", e)
        }
        Toast.makeText(context, R.string.scan_app_launch_failed, Toast.LENGTH_SHORT).show()
    }

    private fun resolveIntent(context: Context): Intent? {
        if (value == CHOOSER) {
            val intents = entries(context).filter { it.value != CHOOSER }
                .mapNotNull { it.resolveIntent(context) }
            val target = intents.firstOrNull() ?: return null
            return Intent.createChooser(target, context.getString(R.string.scan_apps_title))
                .putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
                .putExtra(Intent.EXTRA_ALTERNATE_INTENTS, intents.drop(1).toTypedArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val target = try {
            Intent.parseUri(intentUri ?: return null, 0).setPackage(packageName)
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Invalid scanner intent: $value", e)
            return null
        }
        val info = context.packageManager.resolveActivity(target, 0)?.activityInfo ?: return null
        val permission = info.permission
        if (
            info.packageName != packageName ||
                !info.exported ||
                (permission != null &&
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
        )
            return null

        return target
            .setComponent(ComponentName(info.packageName, info.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
