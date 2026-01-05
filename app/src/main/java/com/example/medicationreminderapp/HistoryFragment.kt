package com.example.medicationreminderapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medicationreminderapp.databinding.FragmentHistoryBinding
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView: HistoryFragment view created")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up calendar and observers")
        setupCalendar()
        setupObservers()
    }

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(10)
        val endMonth = currentMonth.plusMonths(10)
        binding.calendarView.setup(startMonth, endMonth, java.time.DayOfWeek.SUNDAY)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.monthTitle.text = monthTitleFormatter.format(currentMonth)

        binding.calendarView.monthScrollListener = { month ->
            Log.d(TAG, "Calendar scrolled to month: ${month.yearMonth}")
            binding.monthTitle.text = monthTitleFormatter.format(month.yearMonth)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyStatusMap.collect { statusMap ->
                        Log.d(TAG, "Observer: Received ${statusMap.size} daily status updates")
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
                    viewModel.complianceRateText.collect { text ->
                        Log.d(TAG, "Observer: Compliance rate text updated to $text")
                        binding.complianceRateTextView.text = text
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView: HistoryFragment view destroyed")
    }

    companion object {
        private const val TAG = "HistoryFragment"
    }
}

class DayViewContainer(view: View) : ViewContainer(view)
