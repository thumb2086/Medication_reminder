package com.example.medicationreminderapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.medicationreminderapp.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentFontSize = sharedPreferences.getString("font_size", "medium")

        when (currentFontSize) {
            "small" -> binding.smallFontRadioButton.isChecked = true
            "medium" -> binding.mediumFontRadioButton.isChecked = true
            "large" -> binding.largeFontRadioButton.isChecked = true
        }

        binding.fontSizeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newFontSize = when (checkedId) {
                R.id.smallFontRadioButton -> "small"
                R.id.mediumFontRadioButton -> "medium"
                R.id.largeFontRadioButton -> "large"
                else -> "medium"
            }

            sharedPreferences.edit {
                putString("font_size", newFontSize)
            }
            requireActivity().recreate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}