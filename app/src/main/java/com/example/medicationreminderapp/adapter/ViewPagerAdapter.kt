package com.example.medicationreminderapp.adapter

import android.util.Log
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

    override fun getItemCount(): Int {
        val count = if (isEngineeringMode) baseTabCount + 1 else baseTabCount
        Log.d(TAG, "getItemCount: $count")
        return count
    }

    override fun createFragment(position: Int): Fragment {
        Log.d(TAG, "createFragment for position: $position")
        return when (position) {
            0 -> ReminderSettingsFragment()
            1 -> MedicationListFragment()
            2 -> HistoryFragment()
            3 -> ReportFragment()
            4 -> {
                if (isEngineeringMode) {
                    Log.d(TAG, "Creating EnvironmentFragment for engineering mode")
                    EnvironmentFragment()
                } else {
                    Log.e(TAG, "Invalid position: $position without engineering mode")
                    throw IllegalArgumentException("Invalid position: $position without engineering mode")
                }
            }
            else -> {
                Log.e(TAG, "Invalid position: $position")
                throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }

    companion object {
        private const val TAG = "ViewPagerAdapter"
    }
}
