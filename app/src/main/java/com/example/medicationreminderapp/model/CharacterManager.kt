package com.example.medicationreminderapp.model

import android.content.Context
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class CharacterManager(private val context: Context) {

    val characters: List<CharacterPack> by lazy {
        loadCharacters()
    }

    private fun getDrawableResourceIdByName(name: String): Int {
        return when (name) {
            "kuromi" -> R.drawable.kuromi
            "chibi_maruko_chan" -> R.drawable.chibi_maruko_chan
            "crayon_shin_chan" -> R.drawable.crayon_shin_chan
            "doraemon" -> R.drawable.doraemon
            else -> 0 // Or some default drawable
        }
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
            val imageResId = getDrawableResourceIdByName(it.imageResName)
            CharacterPack(it.id, it.name, imageResId)
        }
    }

    private data class CharacterData(val id: String, val name: String, val imageResName: String)
}
