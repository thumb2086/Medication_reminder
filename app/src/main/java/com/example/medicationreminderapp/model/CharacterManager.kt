package com.example.medicationreminderapp.model

import android.content.Context
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
            jsonString = context.assets.open("characters.json").bufferedReader().use { it.readText() }
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