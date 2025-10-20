package com.example.medicationreminderapp.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.R
import com.example.medicationreminderapp.databinding.FragmentEnvironmentBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class EnvironmentFragment : Fragment() {

    private var _binding: FragmentEnvironmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnvironmentBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        observeViewModel()
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
            xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
            axisLeft.textColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
            axisRight.textColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
        }
    }

    private fun observeViewModel() {
        viewModel.temperature.observe(viewLifecycleOwner) { temp ->
            val text = if (temp != null) getString(R.string.temperature_format, temp) else getString(R.string.temperature_empty)
            binding.tempTextView.text = text
            temp?.let {
                val history = viewModel.tempHistory.value ?: mutableListOf()
                history.add(Entry(history.size.toFloat(), it))
                viewModel.tempHistory.value = history
            }
        }
        viewModel.humidity.observe(viewLifecycleOwner) { humidity ->
            val text = if (humidity != null) getString(R.string.humidity_format, humidity) else getString(R.string.humidity_empty)
            binding.humidityTextView.text = text
            humidity?.let {
                val history = viewModel.humidityHistory.value ?: mutableListOf()
                history.add(Entry(history.size.toFloat(), it))
                viewModel.humidityHistory.value = history
            }
        }

        viewModel.tempHistory.observe(viewLifecycleOwner) { updateChart() }
        viewModel.humidityHistory.observe(viewLifecycleOwner) { updateChart() }
    }

    private fun updateChart() {
        val tempHistory = viewModel.tempHistory.value ?: emptyList()
        val humidityHistory = viewModel.humidityHistory.value ?: emptyList()

        val tempDataSet = LineDataSet(tempHistory, getString(R.string.temperature_label)).apply {
            color = Color.RED
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
            setCircleColor(Color.RED)
            lineWidth = 2f
            circleRadius = 4f
        }

        val humidityDataSet = LineDataSet(humidityHistory, getString(R.string.humidity_label)).apply {
            color = Color.BLUE
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.onSurface)
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
        }

        binding.lineChart.data = LineData(tempDataSet, humidityDataSet)
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}