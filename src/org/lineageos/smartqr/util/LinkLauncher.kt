/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URISyntaxException
import org.lineageos.smartqr.R

internal object LinkLauncher {
    private const val TAG = "LinkLauncher"
    private const val DEBUG = true

    private data class AppLinkRule(
        val packageName: String,
        val intentUri: String,
        val prefixes: Array<String>,
    )

    fun open(context: Context, text: String) {
        if (SmartQrPreferences.preferDefaultApp(context)) {
            if (openAppLink(context, text)) return
        }

        SmartQrPreferences.selectedScanApp(context).open(context)
    }

    private fun openAppLink(context: Context, text: String): Boolean {
        val uri = Uri.parse(text.trim()).normalizeScheme()

        if (tryOpen(context, createDefaultAppIntent(uri))) return true
        return tryOpen(context, resolveAppLink(context, uri))
    }

    private fun tryOpen(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (DEBUG) Log.d(TAG, "Opened app link")
            true
        } catch (_: ActivityNotFoundException) {
            if (DEBUG) Log.d(TAG, "No app for link intent")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot open app link", e)
            false
        }
    }

    private fun createDefaultAppIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(
                Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
                    Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT
            )
    }

    private fun resolveAppLink(context: Context, uri: Uri): Intent? {
        val url = uri.toString()
        for (rule in loadAppLinkRules(context)) {
            if (rule.prefixes.none { url.startsWith(it) }) continue

            val intent = createAppLinkIntent(rule, url) ?: continue
            return intent
        }
        return null
    }

    private fun loadAppLinkRules(context: Context): List<AppLinkRule> {
        val resources = context.resources
        return resources.obtainTypedArray(R.array.app_link_rules).use { rules ->
            List(rules.length()) { index ->
                loadAppLinkRule(context, rules.getResourceId(index, 0))
            }
        }
    }

    private fun loadAppLinkRule(context: Context, resourceId: Int): AppLinkRule {
        val resources = context.resources
        return resources.obtainTypedArray(resourceId).use { rule ->
            val prefixesId = rule.getResourceId(2, 0)
            val prefixes = if (prefixesId != 0) {
                resources.getStringArray(prefixesId)
            } else {
                arrayOf(rule.getString(2).orEmpty())
            }
            AppLinkRule(
                requireNotNull(rule.getString(0)),
                requireNotNull(rule.getString(1)),
                prefixes,
            )
        }
    }

    private fun createAppLinkIntent(rule: AppLinkRule, url: String): Intent? {
        val intentUri = rule.intentUri
            .replace("\${URLENCODED}", Uri.encode(url))
            .replace("\${URL}", url)
        return try {
            Intent.parseUri(intentUri, 0).setPackage(rule.packageName)
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Invalid app link intent", e)
            null
        }
    }
}
