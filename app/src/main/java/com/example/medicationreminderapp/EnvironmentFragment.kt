package com.example.medicationreminderapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medicationreminderapp.databinding.FragmentEnvironmentBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnvironmentFragment : Fragment() {

    private var _binding: FragmentEnvironmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tempDataSet: LineDataSet
    private lateinit var humidityDataSet: LineDataSet
    
    // Reference timestamp to offset X-axis values for better float precision
    private var referenceTimestamp: Long = 0

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

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        // Reconstruct the original timestamp: reference + offset
                        val originalTimestamp = referenceTimestamp + value.toLong()
                        return sdf.format(Date(originalTimestamp * 1000))
                    }
                }
                // Prevent labels from bunching up
                granularity = 60f // Minimum 1 minute interval
                setDrawGridLines(false)
            }
            
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawLabels(true) // Explicitly enable Y-axis labels
                setDrawGridLines(true)
                granularity = 1f
            }
            
            legend.isEnabled = true
        }
    }

    private fun createDataSet(label: String, color: Int): LineDataSet {
        return LineDataSet(null, label).apply {
            this.color = color
            this.valueTextColor = Color.BLACK
            this.lineWidth = 2.5f
            this.setCircleColor(color)
            this.circleRadius = 3.5f
            this.setDrawCircleHole(false)
            
            // Enhance style
            this.mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curves
            this.setDrawFilled(true)
            this.fillAlpha = 50
            this.fillColor = color
            
            // Optimize drawing for performance
            setDrawValues(false) 
            setDrawCircles(true) // Keep circles for data points visibility
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isBleConnected.collect { isConnected ->
                        binding.swipeRefreshLayout.isEnabled = isConnected
                        if (isConnected) {
                            binding.lineChart.visibility = View.VISIBLE
                            binding.notConnectedTextView.visibility = View.GONE
                        } else {
                            binding.lineChart.visibility = View.GONE
                            binding.notConnectedTextView.visibility = View.VISIBLE
                            clearChartData()
                        }
                    }
                }

                launch {
                    viewModel.historicSensorData.collect { dataPoints ->
                        if (dataPoints.isNotEmpty()) {
                            updateChart(dataPoints)
                        } else {
                            clearChartData()
                        }
                        if (binding.swipeRefreshLayout.isRefreshing) {
                            binding.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }

                launch {
                    viewModel.bleStatus.collect {
                        if (it == "Historic data sync complete" && binding.swipeRefreshLayout.isRefreshing) {
                            binding.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun updateChart(dataPoints: List<SensorDataPoint>) {
        if (dataPoints.isEmpty()) {
            clearChartData()
            return
        }

        // Update reference timestamp to the first point's timestamp
        referenceTimestamp = dataPoints.first().timestamp

        // Create entries relative to the reference timestamp
        val tempEntries = dataPoints.map { 
            Entry((it.timestamp - referenceTimestamp).toFloat(), it.temperature) 
        }
        val humidityEntries = dataPoints.map { 
            Entry((it.timestamp - referenceTimestamp).toFloat(), it.humidity) 
        }

        tempDataSet.values = tempEntries
        humidityDataSet.values = humidityEntries

        binding.lineChart.data.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.fitScreen() // Reset zoom to fit new data
        binding.lineChart.invalidate()
    }

    private fun clearChartData() {
        tempDataSet.clear()
        humidityDataSet.clear()
        binding.lineChart.data.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
