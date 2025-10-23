package com.example.medicationreminderapp.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.medicationreminderapp.EnvironmentFragment
import com.example.medicationreminderapp.HistoryFragment
import com.example.medicationreminderapp.ReminderSettingsFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReminderSettingsFragment()
            1 -> HistoryFragment()
            2 -> EnvironmentFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
