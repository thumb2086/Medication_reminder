package com.example.medicationreminderapp

import android.content.Intent
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupObservers()

        binding.timeframeSelector.setOnCheckedChangeListener { _, checkedId ->
            val timeframe = when (checkedId) {
                R.id.weekly_button -> Timeframe.WEEKLY
                R.id.monthly_button -> Timeframe.MONTHLY
                R.id.quarterly_button -> Timeframe.QUARTERLY
                else -> null
            }
            timeframe?.let { viewModel.calculateComplianceRateForTimeframe(it) }
        }

        binding.shareReportButton.setOnClickListener {
            shareReport()
        }

        // Set initial calculation
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
    }

    private fun updateChart(data: List<BarEntry>, labels: List<String>) {
        if (data.isEmpty()) {
            binding.complianceChart.clear()
            binding.complianceChart.invalidate()
            return
        }

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
    }
}
