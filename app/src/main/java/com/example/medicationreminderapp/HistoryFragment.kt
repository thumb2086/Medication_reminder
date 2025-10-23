package com.example.medicationreminderapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.medicationreminderapp.databinding.FragmentHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
    }

    private fun setupObservers() {
        viewModel.dailyStatusMap.observe(viewLifecycleOwner) { statusMap ->
            // Note: Standard CalendarView has limited decoration options.
            // For more advanced markers (like green dots), a custom CalendarView or a third-party library is usually required.
            // Here, we'll log the dates that should be marked.
            statusMap.forEach { (dateStr, status) ->
                if (status == MainViewModel.STATUS_ALL_TAKEN) {
                    // You would typically use a custom decorator to highlight this date on the calendar.
                    // For now, we are just confirming the logic is connected.
                    android.util.Log.d("HistoryFragment", "Date to be marked as taken: $dateStr")
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
