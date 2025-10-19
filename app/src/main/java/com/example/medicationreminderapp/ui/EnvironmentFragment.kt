package com.example.medicationreminderapp.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            legend.textColor = Color.WHITE
            xAxis.textColor = Color.WHITE
            axisLeft.textColor = Color.WHITE
            axisRight.textColor = Color.WHITE
        }
    }

    private fun observeViewModel() {
        viewModel.temperature.observe(viewLifecycleOwner) { updateChart() }
        viewModel.humidity.observe(viewLifecycleOwner) { updateChart() }
    }

    private fun updateChart() {
        val tempData = viewModel.temperature.value?.let { Entry(1f, it) } ?: Entry(1f, 0f)
        val humidityData = viewModel.humidity.value?.let { Entry(1f, it) } ?: Entry(1f, 0f)

        val tempDataSet = LineDataSet(listOf(tempData), getString(R.string.temperature_label))
        tempDataSet.color = Color.RED
        tempDataSet.valueTextColor = Color.WHITE

        val humidityDataSet = LineDataSet(listOf(humidityData), getString(R.string.humidity_label))
        humidityDataSet.color = Color.BLUE
        humidityDataSet.valueTextColor = Color.WHITE

        val lineData = LineData(tempDataSet, humidityDataSet)
        binding.lineChart.data = lineData
        binding.lineChart.invalidate()

        binding.tempTextView.text = getString(R.string.temperature_format, viewModel.temperature.value ?: "--")
        binding.humidityTextView.text = getString(R.string.humidity_format, viewModel.humidity.value ?: "--")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}