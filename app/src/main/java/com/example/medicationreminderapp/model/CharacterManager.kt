package com.example.medicationreminderapp.model

import android.annotation.SuppressLint
import android.content.Context
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

object CharacterManager {

    private var characterCache: List<Character>? = null
    private val lock = Any()

    @SuppressLint("DiscouragedApi")
    fun getCharacters(context: Context): List<Character> {
        synchronized(lock) {
            if (characterCache != null) {
                return characterCache!!
            }

            val jsonString: String
            try {
                val inputStream = context.resources.openRawResource(R.raw.characters)
                jsonString = inputStream.bufferedReader().use { it.readText() }
            } catch (ioException: IOException) {
                ioException.printStackTrace()
                return emptyList()
            }

            val listType = object : TypeToken<List<Character>>() {}.type
            val characters: List<Character> = Gson().fromJson(jsonString, listType)

            characters.forEach { character ->
                var resId = context.resources.getIdentifier(character.imageResName, "drawable", context.packageName)
                if (resId == 0) {
                    resId = context.resources.getIdentifier(character.imageResName, "drawable-nodpi", context.packageName)
                }
                if (resId == 0) {
                    resId = R.drawable.kuromi // Fallback
                }
                character.imageResId = resId
            }

            characterCache = characters
            return characters
        }
    }

    fun findCharacterById(context: Context, id: String): Character? {
        return getCharacters(context).find { it.id == id }
    }
}
