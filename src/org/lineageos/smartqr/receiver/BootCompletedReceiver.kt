/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.lineageos.smartqr.service.SmartQrService
import org.lineageos.smartqr.util.SmartQrPreferences

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SmartQrPreferences.isEnabled(context)) return

        SmartQrService.start(context)
    }
}
