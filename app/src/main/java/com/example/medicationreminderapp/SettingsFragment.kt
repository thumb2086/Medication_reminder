package com.example.medicationreminderapp

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.medicationreminderapp.util.UpdateManager
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<ListPreference>("theme")?.let { it.summary = it.entry }
        findPreference<ListPreference>("language")?.let { it.summary = it.entry }
        findPreference<ListPreference>("character")?.let { it.summary = it.entry }
        findPreference<ListPreference>("update_channel")?.let { it.summary = it.entry }

        // Dynamically set version info
        findPreference<Preference>("app_version")?.summary = BuildConfig.VERSION_NAME
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        view.setBackgroundColor(typedValue.data)

        // Handle Window Insets to avoid content being obscured by gesture navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // Observe the engineering mode status from the ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEngineeringMode.collect { isEnabled ->
                    findPreference<SwitchPreferenceCompat>("engineering_mode")?.let {
                        // Stop listening to changes to prevent feedback loop
                        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this@SettingsFragment)
                        it.isChecked = isEnabled
                        // Re-register listener
                        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this@SettingsFragment)
                    }
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "wifi_settings" -> {
                parentFragmentManager.commit {
                    replace(R.id.fragment_container, WiFiConfigFragment())
                    addToBackStack(null)
                }
                true
            }
            "check_for_updates" -> {
                checkForUpdates()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun checkForUpdates() {
        val sharedPreferences = preferenceScreen.sharedPreferences ?: return
        val channel = sharedPreferences.getString("update_channel", "official") ?: "official"
        val updateManager = UpdateManager(requireContext())
        
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "正在檢查更新...", Toast.LENGTH_SHORT).show()
            val updateInfo = updateManager.checkForUpdates(channel)
            if (updateInfo != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.update_available_title))
                    .setMessage(getString(R.string.update_available_message, updateInfo.version, updateInfo.releaseNotes))
                    .setPositiveButton(R.string.update_now) { _, _ ->
                        updateManager.downloadAndInstall(updateInfo.downloadUrl, "MedicationReminderApp-${updateInfo.version}.apk")
                    }
                    .setNegativeButton(R.string.update_later, null)
                    .show()
            } else {
                Toast.makeText(requireContext(), R.string.no_updates_available, Toast.LENGTH_SHORT).show()
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
        // Fixed: Do NOT reset UI visibility here. 
        // Resetting it in onPause() causes the main tabs to reappear when the app goes to background 
        // (e.g., when switching apps), which is incorrect behavior while still on the Settings screen.
        // The visibility is correctly managed by the FragmentManager listener in MainActivity.
    }

    override fun onDestroyView() {
        super.onDestroyView()
         activity?.title = getString(R.string.app_name)
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
            "update_channel" -> {
                 findPreference<ListPreference>(key)?.let { pref ->
                    pref.summary = pref.entry
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
