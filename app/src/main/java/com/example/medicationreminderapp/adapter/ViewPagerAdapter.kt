package com.example.medicationreminderapp.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.medicationreminderapp.EnvironmentFragment
import com.example.medicationreminderapp.HistoryFragment
import com.example.medicationreminderapp.MedicationListFragment
import com.example.medicationreminderapp.ReminderSettingsFragment
import com.example.medicationreminderapp.ReportFragment

class ViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val isEngineeringMode: Boolean
) : FragmentStateAdapter(fragmentActivity) {

    private val baseTabCount = 4

    override fun getItemCount(): Int = if (isEngineeringMode) baseTabCount + 1 else baseTabCount

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReminderSettingsFragment()
            1 -> MedicationListFragment()
            2 -> HistoryFragment()
            3 -> ReportFragment()
            4 -> {
                if (isEngineeringMode) {
                    EnvironmentFragment()
                } else {
                    throw IllegalArgumentException("Invalid position: $position without engineering mode")
                }
            }
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
