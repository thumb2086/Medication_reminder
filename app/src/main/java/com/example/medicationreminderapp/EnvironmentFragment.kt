package com.example.medicationreminderapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.medicationreminderapp.databinding.FragmentEnvironmentBinding

class EnvironmentFragment : Fragment() {

    private var _binding: FragmentEnvironmentBinding? = null
    private val binding get() = _binding!!

    // Get the shared ViewModel
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnvironmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
    }

    private fun setupObservers() {
        // Observe temperature changes and update the UI
        viewModel.temperature.observe(viewLifecycleOwner) { temp ->
            binding.temperatureTextView.text = getString(R.string.temperature_format, temp)
        }

        // Observe humidity changes and update the UI
        viewModel.humidity.observe(viewLifecycleOwner) { humidity ->
            binding.humidityTextView.text = getString(R.string.humidity_format, humidity)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
