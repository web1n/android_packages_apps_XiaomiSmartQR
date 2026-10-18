/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.ui

import android.os.Bundle
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.smartqr.R
import org.lineageos.smartqr.util.SmartQrPreferences

class SmartQrSettingsFragment : SettingsBasePreferenceFragment() {
    private val enable by lazy { findPreference<MainSwitchPreference>(SmartQrPreferences.ENABLED)!! }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.smartqr_preferences, rootKey)

        val context = requireContext()
        enable.setOnPreferenceChangeListener { _, value ->
            SmartQrPreferences.setEnabled(context, value as Boolean)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        enable.isChecked = SmartQrPreferences.isEnabled(requireContext())
    }
}
