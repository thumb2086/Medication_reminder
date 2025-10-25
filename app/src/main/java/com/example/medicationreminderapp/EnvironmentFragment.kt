package com.example.medicationreminderapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.medicationreminderapp.databinding.FragmentEnvironmentBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class EnvironmentFragment : Fragment() {

    private var _binding: FragmentEnvironmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tempDataSet: LineDataSet
    private lateinit var humidityDataSet: LineDataSet
    private var entryCount = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnvironmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupSwipeToRefresh()
        setupObservers()
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.onRefreshEnvironmentData()
        }
    }

    private fun setupChart() {
        tempDataSet = createDataSet(getString(R.string.temperature_label), ContextCompat.getColor(requireContext(), R.color.temp_color))
        humidityDataSet = createDataSet(getString(R.string.humidity_label), ContextCompat.getColor(requireContext(), R.color.humidity_color))

        binding.lineChart.apply {
            data = LineData(tempDataSet, humidityDataSet)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisRight.isEnabled = false
        }
    }

    private fun createDataSet(label: String, color: Int): LineDataSet {
        return LineDataSet(null, label).apply {
            this.color = color
            this.valueTextColor = Color.BLACK
            this.lineWidth = 2f
            this.setCircleColor(color)
            this.circleRadius = 4f
        }
    }

    private fun setupObservers() {
        viewModel.isBleConnected.observe(viewLifecycleOwner) { isConnected ->
            binding.swipeRefreshLayout.isEnabled = isConnected
            if (isConnected) {
                binding.lineChart.visibility = View.VISIBLE
                binding.notConnectedTextView.visibility = View.GONE
            } else {
                binding.lineChart.visibility = View.GONE
                binding.notConnectedTextView.visibility = View.VISIBLE
                // Clear chart data on disconnect
                clearChartData()
            }
        }

        viewModel.temperature.observe(viewLifecycleOwner) { temp ->
            if (viewModel.isBleConnected.value == true) {
                addChartEntry(temp, tempDataSet)
                if (binding.swipeRefreshLayout.isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        viewModel.humidity.observe(viewLifecycleOwner) { humidity ->
            if (viewModel.isBleConnected.value == true) {
                addChartEntry(humidity, humidityDataSet)
                entryCount++
                if (binding.swipeRefreshLayout.isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun addChartEntry(value: Float, dataSet: LineDataSet) {
        val data = binding.lineChart.data
        if (data != null) {
            dataSet.addEntry(Entry(entryCount, value))
            data.notifyDataChanged()
            binding.lineChart.notifyDataSetChanged()
            binding.lineChart.setVisibleXRangeMaximum(20f)
            binding.lineChart.moveViewToX(data.entryCount.toFloat())
        }
    }
    
    private fun clearChartData() {
        tempDataSet.clear()
        humidityDataSet.clear()
        entryCount = 0f
        binding.lineChart.invalidate() // Refresh the chart view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
