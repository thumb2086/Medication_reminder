package com.example.medicationreminderapp

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import com.example.medicationreminderapp.model.Character
import com.example.medicationreminderapp.model.CharacterManager
import kotlinx.coroutines.launch

class ImagePickerPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private val characterManager = CharacterManager(context)

    override fun onClick() {
        (context as? LifecycleOwner)?.lifecycleScope?.launch {
            val characters = characterManager.getCharactersWithImages()

            if (characters.isEmpty()) {
                // Handle case where no characters are available
                return@launch
            }

            entries = characters.map { it.name }.toTypedArray()
            entryValues = characters.map { it.id }.toTypedArray()

            val adapter = ImageListAdapter(context, R.layout.preference_image_picker_item, characters.toTypedArray())

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
    }

    private inner class ImageListAdapter(
        context: Context,
        private val itemLayoutId: Int,
        items: Array<Character>,
    ) : ArrayAdapter<Character>(context, itemLayoutId, items) {

        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(itemLayoutId, parent, false)

            val imageView = view.findViewById<ImageView>(R.id.image)
            val textView = view.findViewById<TextView>(R.id.text)
            val radioButton = view.findViewById<RadioButton>(R.id.radio_button)

            val character = getItem(position)!!

            character.imagePath?.let {
                val bitmap = BitmapFactory.decodeFile(it)
                imageView.setImageBitmap(bitmap)
            }

            textView.text = character.name
            radioButton.isChecked = (value == character.id)

            return view
        }
    }
}
