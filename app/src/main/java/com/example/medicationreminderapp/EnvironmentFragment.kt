package com.example.medicationreminderapp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medicationreminderapp.databinding.FragmentEnvironmentBinding
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
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
        val tempColor = ContextCompat.getColor(requireContext(), R.color.temp_color)
        val humColor = ContextCompat.getColor(requireContext(), R.color.humidity_color)
        
        // Pre-fetch format strings
        val tempFormat = getString(R.string.chart_value_temp)
        val humFormat = getString(R.string.chart_value_humidity)

        tempDataSet = createDataSet(getString(R.string.temperature_label), tempColor, YAxis.AxisDependency.LEFT)
        humidityDataSet = createDataSet(getString(R.string.humidity_label), humColor, YAxis.AxisDependency.RIGHT)

        binding.lineChart.apply {
            data = LineData(tempDataSet, humidityDataSet)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false) // Cleaner background

            // Custom Marker View
            val markerView = CustomMarkerView(requireContext(), R.layout.custom_marker_view)
            markerView.chartView = this
            marker = markerView

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.GRAY
                setAvoidFirstLastClipping(true) // Prevent clipping
                
                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        val originalTimestamp = referenceTimestamp + value.toLong()
                        return sdf.format(Date(originalTimestamp * 1000))
                    }
                }
                // Improve granularity
                granularity = 300f // 5 minutes
                labelCount = 5 // Show ~5 labels
            }
            
            // Left Axis (Temperature)
            axisLeft.apply {
                isEnabled = true
                textColor = tempColor
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                gridLineWidth = 0.5f
                // granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format(Locale.getDefault(), tempFormat, value)
                    }
                }
            }

            // Right Axis (Humidity)
            axisRight.apply {
                isEnabled = true
                textColor = humColor
                setDrawGridLines(false) // Avoid grid clutter
                // granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format(Locale.getDefault(), humFormat, value)
                    }
                }
            }
            
            legend.isEnabled = true
            legend.textColor = Color.GRAY
            
            animateX(1000) // Entry animation
        }
    }

    private fun createDataSet(label: String, color: Int, axisDependency: YAxis.AxisDependency): LineDataSet {
        return LineDataSet(null, label).apply {
            this.color = color
            this.axisDependency = axisDependency
            this.valueTextColor = color
            this.lineWidth = 3f // Slightly thicker line for better visibility
            
            // Don't draw circles by default (only for single point)
            this.setDrawCircles(false)
            this.setDrawCircleHole(true) // Hollow circle looks nicer with fill
            this.circleRadius = 4f // Moderate size
            this.circleHoleRadius = 2f
            this.setCircleColor(color)
            this.highLightColor = color 
            
            this.mode = LineDataSet.Mode.CUBIC_BEZIER
            this.setDrawFilled(true)
            this.fillAlpha = 50 // Slightly increased alpha
            this.fillColor = color
            
            setDrawValues(false) 
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

        referenceTimestamp = dataPoints.first().timestamp

        val tempEntries = dataPoints.map { 
            Entry((it.timestamp - referenceTimestamp).toFloat(), it.temperature) 
        }
        val humidityEntries = dataPoints.map { 
            Entry((it.timestamp - referenceTimestamp).toFloat(), it.humidity) 
        }

        tempDataSet.values = tempEntries
        humidityDataSet.values = humidityEntries

        // Only show circles if there is only one data point to make it visible
        val showCircles = dataPoints.size == 1
        tempDataSet.setDrawCircles(showCircles)
        humidityDataSet.setDrawCircles(showCircles)

        binding.lineChart.data.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.fitScreen()
        binding.lineChart.invalidate()
        binding.lineChart.animateX(800) // Re-animate on update
    }

    private fun clearChartData() {
        tempDataSet.clear()
        humidityDataSet.clear()
        binding.lineChart.data?.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Inner class for MarkerView
    inner class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
        private val tvContent: TextView = findViewById(R.id.tvContent)
        private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            e?.let {
                val time = sdf.format(Date((referenceTimestamp + it.x.toLong()) * 1000))
                val value = it.y
                // Determine if this is temp or humidity based on the dataset
                val isTemp = highlight?.dataSetIndex == 0
                val unit = if (isTemp) "°C" else "%"
                val type = if (isTemp) getString(R.string.temperature_label) else getString(R.string.humidity_label)
                
                // Use resource string with placeholders to avoid concatenation warnings
                tvContent.text = context.getString(R.string.marker_view_format, time, type, value, unit)
            }
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF(-(width / 2).toFloat(), -height.toFloat())
        }
    }
}
