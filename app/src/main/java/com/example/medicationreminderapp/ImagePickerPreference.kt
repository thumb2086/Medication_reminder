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
import com.example.medicationreminderapp.model.CharacterManager
import com.example.medicationreminderapp.model.CharacterPack

class ImagePickerPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private val characterManager = CharacterManager(context)
    private val characters: List<CharacterPack> = characterManager.getCharacters()

    init {
        entries = characters.map { it.name }.toTypedArray()
        entryValues = characters.map { it.id }.toTypedArray()
    }

    override fun onClick() {
        val adapter = ImageListAdapter(context, R.layout.preference_image_picker_item, entries)

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
    ) : ArrayAdapter<CharSequence>(context, itemLayoutId, items) {

        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(itemLayoutId, parent, false)

            val imageView = view.findViewById<ImageView>(R.id.image)
            val textView = view.findViewById<TextView>(R.id.text)
            val radioButton = view.findViewById<RadioButton>(R.id.radio_button)

            val character = characters[position]

            imageView.setImageResource(character.imageResId)
            textView.text = character.name

            radioButton.isChecked = (value == character.id)

            return view
        }
    }
}
