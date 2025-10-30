package com.example.medicationreminderapp

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Set initial summary for theme, language, and accent color preferences
        findPreference<ListPreference>("theme")?.let {
            it.summary = it.entry
        }
        findPreference<ListPreference>("language")?.let {
            it.summary = it.entry
        }
        findPreference<ListPreference>("accent_color")?.let {
            it.summary = it.entry
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        view.setBackgroundColor(typedValue.data)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        activity?.title = getString(R.string.title_settings)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        activity?.title = getString(R.string.app_name)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "theme" -> {
                findPreference<ListPreference>(key)?.let { themePreference ->
                    themePreference.summary = themePreference.entry
                    val themeValue = sharedPreferences.getString(key, "system")
                    ThemeUtils.setTheme(requireActivity(), themeValue)
                }
            }
            "language" -> {
                findPreference<ListPreference>(key)?.let { languagePreference ->
                    languagePreference.summary = languagePreference.entry
                    val languageValue = sharedPreferences.getString(key, "system")
                    (activity as? MainActivity)?.setLocale(languageValue)
                }
            }
            "accent_color" -> {
                findPreference<ListPreference>(key)?.let { accentColorPreference ->
                    accentColorPreference.summary = accentColorPreference.entry
                    activity?.recreate()
                }
            }
            "engineering_mode" -> {
                val isEnabled = sharedPreferences.getBoolean(key, false)
                (activity as? MainActivity)?.bluetoothLeManager?.let { bleManager ->
                    if (bleManager.isConnected()) {
                        bleManager.setEngineeringMode(isEnabled)
                        val status = if (isEnabled) "啟用" else "停用"
                        Toast.makeText(requireContext(), "工程模式已$status", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "請先連接藥盒", Toast.LENGTH_SHORT).show()
                        // Revert the switch state if not connected
                        findPreference<SwitchPreferenceCompat>(key)?.isChecked = !isEnabled
                    }
                }
            }
        }
    }
}
