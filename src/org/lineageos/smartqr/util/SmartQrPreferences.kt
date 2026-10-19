/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.Context
import android.content.SharedPreferences

internal object SmartQrPreferences {
    const val ENABLED = "smartqr_enabled"
    private const val SCAN_APP = "scan_app"

    private fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("smartqr", Context.MODE_PRIVATE)

    fun selectedScanApp(context: Context): ScanApp =
        ScanApp.fromValue(context, get(context).getString(SCAN_APP, ScanApp.CHOOSER))

    fun setScanApp(context: Context, app: ScanApp) {
        get(context).edit().putString(SCAN_APP, app.value).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        get(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean = get(context).getBoolean(ENABLED, false)
}
