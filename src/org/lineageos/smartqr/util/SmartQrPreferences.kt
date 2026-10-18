/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.Context
import android.content.SharedPreferences

internal object SmartQrPreferences {
    const val ENABLED = "smartqr_enabled"

    private fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("smartqr", Context.MODE_PRIVATE)

    fun setEnabled(context: Context, enabled: Boolean) {
        get(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean = get(context).getBoolean(ENABLED, false)
}
