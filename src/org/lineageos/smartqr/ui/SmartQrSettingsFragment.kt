/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.ui

import android.os.Bundle
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.smartqr.QrGestureSensor
import org.lineageos.smartqr.R
import org.lineageos.smartqr.service.SmartQrService
import org.lineageos.smartqr.util.ScanApp
import org.lineageos.smartqr.util.SmartQrPreferences

class SmartQrSettingsFragment : SettingsBasePreferenceFragment() {
    private val enable by lazy { findPreference<MainSwitchPreference>(SmartQrPreferences.ENABLED)!! }
    private val scanApps = mutableMapOf<ScanApp, SelectorWithWidgetPreference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.smartqr_preferences, rootKey)

        val context = requireContext()
        val supported = QrGestureSensor.isSupported(context)
        enable.isEnabled = supported
        enable.summary = if (supported) null else getString(R.string.status_sensor_missing)

        val category = findPreference<PreferenceCategory>("scan_apps")!!
        for (app in ScanApp.entries) {
            val preference = SelectorWithWidgetPreference(context).apply {
                key = "scan_app_${app.value}"
                setTitle(app.titleText(context))
                isPersistent = false
                setOnClickListener {
                    SmartQrPreferences.setScanApp(context, app)
                    updateScanApps()
                }
            }
            scanApps[app] = preference
            category.addPreference(preference)
        }
        enable.setOnPreferenceChangeListener { _, value ->
            SmartQrPreferences.setEnabled(context, value as Boolean)
            SmartQrService.start(context)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateScanApps()
        enable.isChecked = SmartQrPreferences.isEnabled(requireContext())
    }

    private fun updateScanApps() {
        val context = requireContext()
        val selected = SmartQrPreferences.selectedScanApp(context)
        for ((app, preference) in scanApps) {
            val reason = app.unavailableReason(context)
            preference.isChecked = app == selected
            preference.isEnabled = reason == 0
            preference.summary = if (reason == 0) null else getString(reason)
        }
    }
}
