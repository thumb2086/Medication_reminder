package com.example.medicationreminderapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
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
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<ListPreference>("theme")?.let { it.summary = it.entry }
        findPreference<ListPreference>("language")?.let { it.summary = it.entry }
        findPreference<ListPreference>("font_size")?.let { it.summary = it.entry }
        findPreference<ListPreference>("character")?.let { it.summary = it.entry }
        
        setupUpdateChannelPreference()

        // Dynamically set version info
        findPreference<Preference>("app_version")?.summary = BuildConfig.VERSION_NAME
    }

    private fun setupUpdateChannelPreference() {
        findPreference<ListPreference>("update_channel")?.let { listPref ->
            // Use ViewModel helper to avoid Lint constant expression warning
            val currentChannel: String = viewModel.getCurrentUpdateChannel()
            val savedChannel = preferenceManager.sharedPreferences?.getString("update_channel", null)

            // Initial simple setup with local current channel
            val entries = mutableListOf<CharSequence>(getString(R.string.update_channel_stable))
            val entryValues = mutableListOf<CharSequence>("main")
            
            // Add current channel if it's not already in the list (e.g. not "main")
            if (currentChannel.isNotEmpty() && !entryValues.contains(currentChannel)) {
                entries.add(getString(R.string.update_channel_current, currentChannel))
                entryValues.add(currentChannel)
            }
            
            // Add Dev by default if not present
            if (!entryValues.contains("dev")) {
                entries.add(getString(R.string.update_channel_dev))
                entryValues.add("dev")
            }

            listPref.entries = entries.toTypedArray()
            listPref.entryValues = entryValues.toTypedArray()

            // Logic to set default value:
            // 1. If user has manually selected a channel (savedChannel != null), use it.
            // 2. If no selection (first install or clear data):
            //    - If the installed version is from "main" (Stable), default to "main".
            //    - If the installed version is from a specific branch (e.g., "dev" or "feat-x"), default to that branch ("Current").
            //    - This ensures that if a user installs a Nightly build, they stay on that channel by default.
            //    - But if they are on Stable, it stays Stable.
            
            if (listPref.value == null) {
                if (savedChannel != null) {
                    listPref.value = savedChannel
                } else {
                    // No saved preference, use build config
                    if (currentChannel == "main" || currentChannel.isEmpty()) {
                        listPref.value = "main"
                    } else {
                        // User installed a non-stable build, so default to that channel to keep receiving updates for it
                         listPref.value = currentChannel
                    }
                }
            }
            
            listPref.summary = listPref.entry ?: getString(R.string.update_channel_summary, listPref.value)
            
            // Fetch available channels from GitHub Releases
            fetchAvailableChannels(listPref)
        }
    }

    private fun fetchAvailableChannels(listPref: ListPreference) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/thumb2086/Medication_reminder/releases?per_page=100")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body.string()
                    val gson = Gson()
                    val releases = gson.fromJson(jsonStr, JsonArray::class.java)
                    
                    val remoteChannels = mutableSetOf<String>()
                    val regex = Regex(".*-nightly-(.+)-\\\\d+")
                    
                    releases.forEach { element ->
                        val release = element.asJsonObject
                        val tagName = release.get("tag_name").asString
                        if (tagName.startsWith("nightly-")) {
                            val channelName = tagName.removePrefix("nightly-")
                            remoteChannels.add(channelName)
                        } else {
                            val match = regex.find(tagName)
                            if (match != null) {
                                remoteChannels.add(match.groupValues[1])
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        updateChannelList(listPref, remoteChannels)
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to fetch channels", e)
            }
        }
    }

    private fun updateChannelList(listPref: ListPreference, remoteChannels: Set<String>) {
        val currentChannel = viewModel.getCurrentUpdateChannel()
        val entries = mutableListOf<CharSequence>(getString(R.string.update_channel_stable))
        val entryValues = mutableListOf<CharSequence>("main")
        
        // Add current if valid
        if (currentChannel.isNotEmpty() && !entryValues.contains(currentChannel)) {
            entries.add(getString(R.string.update_channel_current, currentChannel))
            entryValues.add(currentChannel)
        }

        // Always add Dev option if not present (as it is a permanent channel)
        if (!entryValues.contains("dev")) {
            entries.add(getString(R.string.update_channel_dev))
            entryValues.add("dev")
        }

        // Add remote channels (filtering out duplicates)
        remoteChannels.forEach { channel ->
            if (!entryValues.contains(channel)) {
                entries.add(channel.replaceFirstChar { it.uppercase() }) // Capitalize for display
                entryValues.add(channel)
            }
        }
        
        listPref.entries = entries.toTypedArray()
        listPref.entryValues = entryValues.toTypedArray()
        
        // Refresh summary
        listPref.summary = listPref.entry ?: getString(R.string.update_channel_summary, listPref.value)

        // Check for dead branch
        // If the current channel is not a permanent one (main/dev) and is not found in the remote list, warn the user.
        if (currentChannel.isNotEmpty() && currentChannel != "main" && currentChannel != "dev" && !remoteChannels.contains(currentChannel)) {
            // Only show if the fragment is currently added and visible to avoid crashes or leaks
            if (isAdded && view != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.update_channel_invalid_title))
                    .setMessage(getString(R.string.update_channel_invalid_message, currentChannel))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
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
            "app_author" -> {
                openUrl("https://github.com/thumb2086")
                true
            }
            "app_project" -> {
                openUrl("https://github.com/thumb2086/Medication_reminder")
                true
            }
            "app_version" -> {
                openUrl("https://github.com/thumb2086/Medication_reminder/releases")
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), getString(R.string.cannot_open_link), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForUpdates() {
        // Automatically checks based on the channel defined in BuildConfig
        val updateManager = UpdateManager(requireContext())
        
        lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
            // Pass isManualCheck = true since this is a manual click
            val updateInfo = updateManager.checkForUpdates(isManualCheck = true)
            
            if (updateInfo != null) {
                val sb = StringBuilder()
                val title: String // Removed redundant initializer
                
                if (updateInfo.isDifferentAppId) {
                    title = getString(R.string.install_different_version_title)
                    sb.append(getString(R.string.install_different_version_message))
                } else if (updateInfo.isNewer) {
                    title = getString(R.string.update_available_title)
                } else {
                    title = getString(R.string.reinstall_title)
                    sb.append(getString(R.string.reinstall_message))
                }
                
                sb.append("\n\n${getString(R.string.version_label)}: ${updateInfo.version}")
                if (updateInfo.releaseNotes.isNotEmpty()) {
                    sb.append("\n\n${getString(R.string.release_notes_label)}:\n${updateInfo.releaseNotes}")
                }
                
                AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(sb.toString())
                    .setPositiveButton(if (updateInfo.isNewer || updateInfo.isDifferentAppId) R.string.update_now else R.string.ok) { _, _ ->
                        updateManager.downloadAndInstall(updateInfo.downloadUrl, "MedicationReminderApp-${updateInfo.version}.apk")
                    }
                    .setNegativeButton(R.string.cancel, null)
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
            "font_size" -> {
                findPreference<ListPreference>(key)?.let { fontSizePreference ->
                    fontSizePreference.summary = fontSizePreference.entry
                    activity?.recreate()
                }
            }
            "character" -> {
                findPreference<ListPreference>(key)?.let { characterPreference ->
                    characterPreference.summary = characterPreference.entry
                    activity?.recreate()
                }
            }
            "update_channel" -> {
                findPreference<ListPreference>(key)?.let { 
                    it.summary = it.entry 
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
                        Toast.makeText(requireContext(), R.string.connect_box_first, Toast.LENGTH_SHORT).show()
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
