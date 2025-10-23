package com.example.medicationreminderapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.medicationreminderapp.databinding.FragmentHistoryBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
        setupObservers()
    }

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(10)
        val endMonth = currentMonth.plusMonths(10)
        binding.calendarView.setup(startMonth, endMonth, java.time.DayOfWeek.SUNDAY)
        binding.calendarView.scrollToMonth(currentMonth)
    }

    private fun setupObservers() {
        viewModel.dailyStatusMap.observe(viewLifecycleOwner) { statusMap ->
            binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) = DayViewContainer(view)
                override fun bind(container: DayViewContainer, data: CalendarDay) {
                    container.day = data
                    val textView = container.view.findViewById<TextView>(R.id.calendarDayText)
                    textView.text = data.date.dayOfMonth.toString()
                    val dotView = container.view.findViewById<View>(R.id.dotView)

                    if (data.position == DayPosition.MonthDate) {
                        val dateStr = formatter.format(data.date)
                        dotView.isVisible = statusMap[dateStr] == MainViewModel.STATUS_ALL_TAKEN
                    } else {
                        dotView.isVisible = false
                    }
                }
            }
        }

        viewModel.complianceRate.observe(viewLifecycleOwner) { rate ->
            val percentage = (rate * 100).toInt()
            binding.complianceRateTextView.text = getString(R.string.compliance_rate_format, percentage)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DayViewContainer(view: View) : ViewContainer(view) {
    lateinit var day: CalendarDay // Will be set when the view is bound.
}
