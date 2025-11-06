package com.example.medicationreminderapp

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<ListPreference>("theme")?.let { it.summary = it.entry }
        findPreference<ListPreference>("language")?.let { it.summary = it.entry }
        findPreference<ListPreference>("character")?.let { it.summary = it.entry }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        view.setBackgroundColor(typedValue.data)

        // Observe the engineering mode status from the ViewModel
        viewModel.isEngineeringMode.observe(viewLifecycleOwner) { isEnabled ->
            findPreference<SwitchPreferenceCompat>("engineering_mode")?.let {
                // Stop listening to changes to prevent feedback loop
                preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
                it.isChecked = isEnabled
                // Re-register listener
                preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            }
        }
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
        (activity as? MainActivity)?.updateUiForFragment(false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "theme" -> {
                findPreference<ListPreference>(key)?.let { themePreference ->
                    themePreference.summary = themePreference.entry
                    val themeValue = sharedPreferences.getString(key, "system")
                    when (themeValue) {
                        "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                }
            }
            "language" -> {
                findPreference<ListPreference>(key)?.let { languagePreference ->
                    languagePreference.summary = languagePreference.entry
                    val languageValue = sharedPreferences.getString(key, "system")
                    (activity as? MainActivity)?.setLocale(languageValue)
                }
            }
            "character" -> {
                findPreference<ListPreference>(key)?.let { characterPreference ->
                    characterPreference.summary = characterPreference.entry
                    activity?.recreate()
                }
            }
            "engineering_mode" -> {
                val isEnabled = sharedPreferences.getBoolean(key, false)
                val mainActivity = (activity as? MainActivity)

                mainActivity?.bluetoothLeManager?.let { bleManager ->
                    if (bleManager.isConnected()) {
                        bleManager.setEngineeringMode(isEnabled)
                        // Toast message is now sent from BleManager for better feedback
                    } else {
                        Toast.makeText(requireContext(), "請先連接藥盒", Toast.LENGTH_SHORT).show()
                        // Revert the switch state immediately if not connected
                        // Unregister to prevent loop, change value, then re-register
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
                        findPreference<SwitchPreferenceCompat>(key)?.isChecked = !isEnabled
                        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
                    }
                }
            }
        }
    }
}
