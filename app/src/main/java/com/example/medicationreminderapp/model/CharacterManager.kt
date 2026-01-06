package com.example.medicationreminderapp.model

import android.content.Context
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
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
        val allCharacters = mutableListOf<CharacterPack>()

        // Load from assets
        try {
            val jsonString = context.assets.open("characters.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<CharacterData>>() {}.type
            val characterDataList: List<CharacterData> = Gson().fromJson(jsonString, type)
            val assetCharacters = characterDataList.map {
                val imageResId = getDrawableResourceIdByName(it.imageResName)
                CharacterPack(it.id, it.name, imageResId)
            }
            allCharacters.addAll(assetCharacters)
        } catch (ioException: IOException) {
            ioException.printStackTrace()
        }

        // Load from internal storage
        val configDir = File(context.filesDir, "characters_config")
        if (configDir.exists() && configDir.isDirectory) {
            configDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val type = object : TypeToken<List<CharacterData>>() {}.type
                    val characterDataList: List<CharacterData> = Gson().fromJson(jsonString, type)
                    val customCharacters = characterDataList.map {
                        val imageResId = getDrawableResourceIdByName(it.imageResName)
                        CharacterPack(it.id, it.name, imageResId)
                    }
                    allCharacters.addAll(customCharacters)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return allCharacters.distinctBy { it.id } // Ensure unique characters by ID
    }

    private data class CharacterData(val id: String, val name: String, val imageResName: String)
}
