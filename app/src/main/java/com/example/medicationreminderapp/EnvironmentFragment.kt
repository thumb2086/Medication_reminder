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
                    override fun getFormattedValue(value: Float):
                            String {
                        return sdf.format(Date(value.toLong() * 1000))
                    }
                }
            }
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
        val tempEntries = dataPoints.map { Entry(it.timestamp.toFloat(), it.temperature) }
        val humidityEntries = dataPoints.map { Entry(it.timestamp.toFloat(), it.humidity) }

        tempDataSet.values = tempEntries
        humidityDataSet.values = humidityEntries

        binding.lineChart.data.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
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
