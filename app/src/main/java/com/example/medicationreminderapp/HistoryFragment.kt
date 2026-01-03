package com.example.medicationreminderapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medicationreminderapp.databinding.FragmentHistoryBinding
import com.example.medicationreminderapp.util.ReportGenerator
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy MMMM", Locale.getDefault())
    private lateinit var reportGenerator: ReportGenerator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        reportGenerator = ReportGenerator()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
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

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(10)
        val endMonth = currentMonth.plusMonths(10)
        binding.calendarView.setup(startMonth, endMonth, java.time.DayOfWeek.SUNDAY)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.monthTitle.text = monthTitleFormatter.format(currentMonth)

        binding.calendarView.monthScrollListener = { month ->
             binding.monthTitle.text = monthTitleFormatter.format(month.yearMonth)
        }
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
                // More styling might be needed depending on the data
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
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
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
                launch {
                    viewModel.dailyStatusMap.collect { statusMap ->
                        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
                            override fun create(view: View) = DayViewContainer(view)
                            override fun bind(container: DayViewContainer, data: CalendarDay) {
                                val textView = container.view.findViewById<TextView>(R.id.calendarDayText)
                                textView.text = data.date.dayOfMonth.toString()
                                val dotView = container.view.findViewById<ImageView>(R.id.dotView)

                                if (data.position == DayPosition.MonthDate) {
                                    val dateStr = formatter.format(data.date)
                                    val status = statusMap[dateStr]

                                    dotView.isVisible = true // Make dot visible and decide color
                                    when (status) {
                                        AppRepository.STATUS_ALL_TAKEN -> {
                                            dotView.setImageResource(R.drawable.green_dot)
                                        }
                                        AppRepository.STATUS_PARTIALLY_TAKEN -> {
                                            dotView.setImageResource(R.drawable.yellow_dot)
                                        }
                                        AppRepository.STATUS_NONE_TAKEN -> {
                                            dotView.setImageResource(R.drawable.red_dot)
                                        }
                                        else -> { // STATUS_NOT_APPLICABLE or future dates
                                            dotView.isVisible = false
                                        }
                                    }
                                } else {
                                    dotView.isVisible = false
                                }
                            }
                        }
                    }
                }

                // Observe the compliance rate for the text view
                launch {
                    viewModel.complianceRate.collect { rate ->
                        val percentage = (rate * 100).toInt()
                        // This complianceRate is from the daily status map, not the report chart.
                        // We will keep this separate for now as it's a 30-day overview.
                        binding.complianceRateTextView.text = getString(R.string.compliance_rate_format, percentage)
                    }
                }

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

class DayViewContainer(view: View) : ViewContainer(view)
