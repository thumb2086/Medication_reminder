package com.example.medicationreminderapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminderapp.R
import com.example.medicationreminderapp.databinding.FragmentLogBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
        observeViewModel()
    }

    private fun setupCalendar() {
        class DayViewContainer(view: View) : ViewContainer(view) {
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            val dotView: View = view.findViewById(R.id.calendarDayDot)
        }

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()
                container.textView.alpha = if (data.position == DayPosition.MonthDate) 1f else 0.3f
                val dateStr = data.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                container.dotView.isVisible = (viewModel.dailyStatusMap.value?.get(dateStr) == 2)
            }
        }

        binding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<ViewContainer> {
            override fun create(view: View) = ViewContainer(view)
            override fun bind(container: ViewContainer, data: CalendarMonth) {
                val textView = container.view.findViewById<TextView>(R.id.calendarMonthText)
                textView.text = DateTimeFormatter.ofPattern("yyyy MMMM").format(data.yearMonth)
            }
        }

        val currentMonth = YearMonth.now()
        binding.calendarView.setup(currentMonth.minusMonths(12), currentMonth.plusMonths(12), DayOfWeek.SUNDAY)
        binding.calendarView.scrollToMonth(currentMonth)
    }

    private fun observeViewModel() {
        viewModel.complianceRate.observe(viewLifecycleOwner) {
            binding.complianceRateTextView.text = getString(R.string.compliance_rate_format, it)
            binding.complianceProgressBar.progress = it
        }
        viewModel.dailyStatusMap.observe(viewLifecycleOwner) {
            binding.calendarView.notifyCalendarChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}