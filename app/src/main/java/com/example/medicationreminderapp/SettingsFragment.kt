package com.example.medicationreminderapp

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Set initial summary for theme and language preferences
        findPreference<ListPreference>("theme")?.let {
            it.summary = it.entry
        }
        findPreference<ListPreference>("language")?.let {
            it.summary = it.entry
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        view.setBackgroundColor(typedValue.data)

        // Show the back arrow
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hide the back arrow
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
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
        }
    }
}
