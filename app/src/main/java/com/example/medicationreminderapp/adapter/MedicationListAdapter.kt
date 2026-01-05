package com.example.medicationreminderapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medicationreminderapp.Medication
import com.example.medicationreminderapp.R
import com.example.medicationreminderapp.databinding.MedicationListItemBinding
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class MedicationListAdapter : ListAdapter<Medication, MedicationListAdapter.MedicationViewHolder>(MedicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            MedicationViewHolder {
        val binding = MedicationListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MedicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MedicationViewHolder(private val binding: MedicationListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(medication: Medication) {
            binding.medicationNameTextView.text = medication.name
            val times = medication.times.values.sorted().joinToString { timeFormat.format(Date(it)) }
            val context = binding.root.context
            binding.medicationDetailsTextView.text = context.getString(R.string.medication_list_item_details, medication.slotNumber, times)
            binding.colorIndicator.setBackgroundColor(medication.color.toColorInt())
        }
    }
}

class MedicationDiffCallback : DiffUtil.ItemCallback<Medication>() {
    override fun areItemsTheSame(oldItem: Medication, newItem: Medication):
            Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Medication, newItem: Medication):
            Boolean {
        return oldItem == newItem
    }
}
