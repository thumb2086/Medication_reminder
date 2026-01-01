package com.example.medicationreminderapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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

    companion object {
        private var hasShownInvalidChannelWarning = false
    }

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
            val currentChannel: String = viewModel.getCurrentUpdateChannel()
            val savedChannel = preferenceManager.sharedPreferences?.getString("update_channel", null)

            val entries = mutableListOf<CharSequence>(getString(R.string.update_channel_stable))
            val entryValues = mutableListOf<CharSequence>("main")
            
            if (currentChannel.isNotEmpty() && !entryValues.contains(currentChannel)) {
                entries.add(getString(R.string.update_channel_current, currentChannel))
                entryValues.add(currentChannel)
            }
            
            if (!entryValues.contains("dev")) {
                entries.add(getString(R.string.update_channel_dev))
                entryValues.add("dev")
            }

            listPref.entries = entries.toTypedArray()
            listPref.entryValues = entryValues.toTypedArray()

            if (savedChannel != null) {
                listPref.value = savedChannel
            } else {
                listPref.value = currentChannel.ifEmpty { "main" }
            }
            
            listPref.summary = listPref.entry ?: getString(R.string.update_channel_summary, listPref.value)
            
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
                    val regex = Regex(".*-nightly-(.+)-\\d+")
                    
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
        
        if (currentChannel.isNotEmpty() && !entryValues.contains(currentChannel)) {
            entries.add(getString(R.string.update_channel_current, currentChannel))
            entryValues.add(currentChannel)
        }

        if (!entryValues.contains("dev")) {
            entries.add(getString(R.string.update_channel_dev))
            entryValues.add("dev")
        }

        remoteChannels.forEach { channel ->
            if (!entryValues.contains(channel)) {
                entries.add(channel.replaceFirstChar { it.uppercase() })
                entryValues.add(channel)
            }
        }
        
        listPref.entries = entries.toTypedArray()
        listPref.entryValues = entryValues.toTypedArray()
        
        listPref.summary = listPref.entry ?: getString(R.string.update_channel_summary, listPref.value)

        if (currentChannel.isNotEmpty() && currentChannel != "main" && currentChannel != "dev" && !remoteChannels.contains(currentChannel)) {
            if (isAdded && view != null && !hasShownInvalidChannelWarning) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.update_channel_invalid_title))
                    .setMessage(getString(R.string.update_channel_invalid_message, currentChannel))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                hasShownInvalidChannelWarning = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        view.setBackgroundColor(typedValue.data)

        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEngineeringMode.collect { isEnabled ->
                    findPreference<SwitchPreferenceCompat>("engineering_mode")?.let {
                        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this@SettingsFragment)
                        it.isChecked = isEnabled
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
        val updateManager = UpdateManager(requireActivity())
        
        lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
            val updateInfo = updateManager.checkForUpdates(isManualCheck = true)
            
            if (updateInfo != null) {
                val sb = StringBuilder()
                val title: String

                if (updateInfo.isDifferentAppId) {
                    title = getString(R.string.install_different_version_title)
                    // Add a detailed explanation about signatures
                    sb.append(getString(R.string.install_different_version_message))
                    sb.append("\n\n").append(getString(R.string.signature_mismatch_warning))
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
                        updateManager.downloadAndInstall(requireActivity(), updateInfo.downloadUrl, "MedicationReminderApp-${updateInfo.version}.apk")
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

                    val fontSizeValue = sharedPreferences.getString(key, "medium")
                    val scale = when (fontSizeValue) {
                        "small" -> 1.5f
                        "medium" -> 1.6f
                        "large" -> 1.8f
                        else -> 1.5f
                    }

                    val appPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    appPrefs.edit {
                        putFloat("font_scale", scale)
                    }

                    requireActivity().recreate()
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
                    } else {
                        Toast.makeText(requireContext(), R.string.connect_box_first, Toast.LENGTH_SHORT).show()
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
                        findPreference<SwitchPreferenceCompat>(key)?.isChecked = !isEnabled
                        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
                    }
                }
            }
        }
    }
}
