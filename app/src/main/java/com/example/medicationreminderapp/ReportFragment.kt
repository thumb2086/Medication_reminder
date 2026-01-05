package com.example.medicationreminderapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medicationreminderapp.databinding.FragmentReportBinding
import com.example.medicationreminderapp.util.ReportGenerator
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var reportGenerator: ReportGenerator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        reportGenerator = ReportGenerator()
        Log.d(TAG, "onCreateView: ReportFragment view created")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up chart and observers")
        setupChart()
        setupObservers()

        binding.timeframeSelector.setOnCheckedChangeListener { _, checkedId ->
            val timeframe = when (checkedId) {
                R.id.weekly_button -> Timeframe.WEEKLY
                R.id.monthly_button -> Timeframe.MONTHLY
                R.id.quarterly_button -> Timeframe.QUARTERLY
                else -> null
            }
            timeframe?.let {
                Log.d(TAG, "Timeframe changed to: $it")
                viewModel.calculateComplianceRateForTimeframe(it)
            }
        }

        binding.shareReportButton.setOnClickListener {
            Log.d(TAG, "Share report button clicked")
            shareReport()
        }

        // Set initial calculation
        Log.d(TAG, "Setting initial timeframe to MONTHLY")
        viewModel.calculateComplianceRateForTimeframe(Timeframe.MONTHLY)
    }

    private fun shareReport() {
        val selectedTimeframeId = binding.timeframeSelector.checkedRadioButtonId
        val timeframe = when (selectedTimeframeId) {
            R.id.weekly_button -> Timeframe.WEEKLY
            R.id.monthly_button -> Timeframe.MONTHLY
            R.id.quarterly_button -> Timeframe.QUARTERLY
            else -> Timeframe.MONTHLY // Default
        }
        val data = viewModel.reportComplianceData.value
        Log.d(TAG, "Generating CSV for timeframe: $timeframe with data size: ${data.size}")

        val csvData = reportGenerator.generateCsv(timeframe, data)

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvData)
            type = "text/csv"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun setupChart() {
        binding.complianceChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            isDragEnabled = true
            setScaleEnabled(true)
            setDrawValueAboveBar(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
            }

            axisRight.isEnabled = false
        }
        Log.d(TAG, "Chart setup complete")
    }

    private fun updateChart(data: List<BarEntry>, labels: List<String>) {
        if (data.isEmpty()) {
            Log.d(TAG, "updateChart: Data is empty, clearing chart")
            binding.complianceChart.clear()
            binding.complianceChart.invalidate()
            return
        }
        Log.d(TAG, "updateChart: Updating chart with ${data.size} entries")

        val dataSet = BarDataSet(data, "Compliance Rate").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary_light)
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        val barData = BarData(dataSet)
        binding.complianceChart.data = barData
        binding.complianceChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.complianceChart.invalidate() // Refresh the chart
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe the compliance data for the chart
                launch {
                    viewModel.reportComplianceData.collect { dataPoints ->
                        Log.d(TAG, "Observer: Received ${dataPoints.size} data points for report")
                        val entries = dataPoints.mapIndexed { index, dataPoint ->
                            BarEntry(index.toFloat(), dataPoint.complianceRate)
                        }
                        val labels = dataPoints.map { it.label }
                        updateChart(entries, labels)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView: ReportFragment view destroyed")
    }

    companion object {
        private const val TAG = "ReportFragment"
    }
}
