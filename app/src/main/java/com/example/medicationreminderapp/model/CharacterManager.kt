package com.example.medicationreminderapp.model

import android.content.Context
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

object CharacterManager {

    private var characterCache: List<Character>? = null

    fun getCharacters(context: Context): List<Character> {
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
        characterCache = characters
        return characters
    }
}