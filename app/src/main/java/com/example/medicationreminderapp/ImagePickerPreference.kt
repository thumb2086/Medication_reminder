package com.example.medicationreminderapp

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.preference.ListPreference

class ImagePickerPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private val imageResources: Array<Int> by lazy {
        entries?.map {
            when (it) {
                "酷洛米" -> R.drawable.kuromi
                "櫻桃小丸子" -> R.drawable.chibi_maruko_chan
                else -> R.drawable.ic_face // A default icon
            }
        }?.toTypedArray() ?: emptyArray()
    }

    override fun onClick() {
        if (entries == null || entryValues == null) {
            return
        }

        val adapter = ImageListAdapter(context, R.layout.preference_image_picker_item, entries, entryValues)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setAdapter(adapter) { dialog, which ->
                if (callChangeListener(entryValues[which])) {
                    value = entryValues[which].toString()
                }
                dialog.dismiss()
            }
            .setNegativeButton(negativeButtonText, null)
            .show()
    }

    private inner class ImageListAdapter(
        context: Context,
        private val itemLayoutId: Int,
        items: Array<CharSequence>,
        private val preferenceEntryValues: Array<CharSequence>
    ) : ArrayAdapter<CharSequence>(context, itemLayoutId, items) {

        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(itemLayoutId, parent, false)

            val imageView = view.findViewById<ImageView>(R.id.image)
            val textView = view.findViewById<TextView>(R.id.text)
            val radioButton = view.findViewById<RadioButton>(R.id.radio_button)

            if (position < imageResources.size) {
                imageView.setImageResource(imageResources[position])
            }
            textView.text = getItem(position)

            // Safely access entryValues and check if the current item is the selected one.
            radioButton.isChecked = (value == preferenceEntryValues[position].toString())

            return view
        }
    }
}
