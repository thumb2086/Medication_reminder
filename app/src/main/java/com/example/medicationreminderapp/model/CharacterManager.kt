package com.example.medicationreminderapp.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class CharacterManager(private val context: Context) {

    private val characters: List<CharacterPack> by lazy {
        loadCharacters()
    }

    private fun loadCharacters(): List<CharacterPack> {
        val jsonString = try {
            context.assets.open("characters.json").bufferedReader().use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return emptyList()
        }

        val type = object : TypeToken<List<CharacterData>>() {}.type
        val characterDataList: List<CharacterData> = Gson().fromJson(jsonString, type)

        return characterDataList.map {
            val imageResId = context.resources.getIdentifier(it.imageResName, "drawable", context.packageName)
            CharacterPack(it.id, it.name, imageResId)
        }
    }

    fun getCharacters(): List<CharacterPack> {
        return characters
    }

    private data class CharacterData(val id: String, val name: String, val imageResName: String)
}
