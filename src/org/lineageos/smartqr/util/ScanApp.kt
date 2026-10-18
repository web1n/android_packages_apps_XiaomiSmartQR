/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import org.lineageos.smartqr.R

internal enum class ScanApp(
    val title: Int,
    private val packageName: String? = null,
    private val intent: Intent? = null,
) {
    CHOOSER(R.string.scan_app_chooser),
    DEFAULT(R.string.scan_app_system),
    WECHAT(
        R.string.scan_app_wechat,
        "com.tencent.mm",
        Intent()
            .setComponent(ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"))
            .putExtra("LauncherUI.From.Scaner.Shortcut", true),
    ),
    ALIPAY(
        R.string.scan_app_alipay,
        "com.eg.android.AlipayGphone",
        Intent(Intent.ACTION_VIEW, Uri.parse("alipayqr://platformapi/startapp?saId=10000007")),
    ),
    UNIONPAY(
        R.string.scan_app_unionpay,
        "com.unionpay",
        Intent(Intent.ACTION_VIEW, Uri.parse("upwallet://native/scanCode")),
    ),
    QQ(
        R.string.scan_app_qq,
        "com.tencent.mobileqq",
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("shortcut_jump", "mqqapi://qrscan/open?source=1"),
    );

    val value: String = name.lowercase()

    companion object {
        private const val TAG = "ScanApp"
        private const val DEBUG = true

        fun fromValue(value: String?): ScanApp =
            entries.firstOrNull { it.value == value } ?: CHOOSER
    }

    fun unavailableReason(context: Context): Int {
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

        return if (resolveIntent(context) == null) R.string.scan_app_unavailable else 0
    }

    fun titleText(context: Context): String {
        if (this != DEFAULT) return context.getString(title)
        return appLabel(context) ?: context.getString(R.string.scan_app_system)
    }

    private fun appLabel(context: Context): String? {
        val packageName = resolveIntent(context)?.component?.packageName ?: return null
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString().ifBlank { null }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
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
        if (this == CHOOSER) {
            val intents = entries.filter { it != CHOOSER }.mapNotNull { it.resolveIntent(context) }
            val target = intents.firstOrNull() ?: return null
            return Intent.createChooser(target, context.getString(R.string.scan_apps_title))
                .putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
                .putExtra(Intent.EXTRA_ALTERNATE_INTENTS, intents.drop(1).toTypedArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val target: Intent = if (this == DEFAULT) {
            Intent().setComponent(resolveDefaultComponent(context) ?: return null)
        } else {
            Intent(intent ?: return null).setPackage(packageName)
        }
        val info = context.packageManager.resolveActivity(target, 0)?.activityInfo ?: return null
        val permission = info.permission
        if (
            (packageName != null && info.packageName != packageName) ||
                !info.enabled ||
                !info.applicationInfo.enabled ||
                !info.exported ||
                (permission != null &&
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
        )
            return null

        return target
            .setComponent(ComponentName(info.packageName, info.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolveDefaultComponent(context: Context): ComponentName? {
        val value = context.resources.getString(
            com.android.internal.R.string.config_defaultQrCodeComponent
        )
        return value.takeIf { it.isNotBlank() }?.let { ComponentName.unflattenFromString(it) }
    }
}
