/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

internal object LinkLauncher {
    private const val TAG = "LinkLauncher"
    private const val DEBUG = true

    fun open(context: Context, text: String) {
        if (openAppLink(context, text)) return
        SmartQrPreferences.selectedScanApp(context).open(context)
    }

    private fun openAppLink(context: Context, text: String): Boolean {
        val uri = Uri.parse(text.trim()).normalizeScheme()
        if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrEmpty()) return false

        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
                    Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
            )

        return try {
            context.startActivity(intent)
            if (DEBUG) Log.d(TAG, "Opened app link")
            true
        } catch (_: ActivityNotFoundException) {
            if (DEBUG) Log.d(TAG, "No default app for link")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot open app link", e)
            false
        }
    }
}
