package com.example.medicationreminderapp.model

import android.content.Context
import android.util.Log
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

class CharacterManager(private val context: Context) {

    private val gson = Gson()
    private val characterCacheDir by lazy { File(context.filesDir, "character_packs") }
    private val remoteCharactersUrl = "https://raw.githubusercontent.com/thumb2086/Medication_reminder/main/app/src/main/assets/characters.json"

    val characters: List<CharacterPack> by lazy {
        loadCharacters()
    }

    private fun getDrawableResourceIdByName(name: String): Int {
        return when (name) {
            "kuromi" -> R.drawable.kuromi
            "chibi_maruko_chan" -> R.drawable.chibi_maruko_chan
            "crayon_shin_chan" -> R.drawable.crayon_shin_chan
            "doraemon" -> R.drawable.doraemon
            else -> 0
        }
    }

    private fun loadCharacters(): List<CharacterPack> {
        val cachedCharacters = loadCharactersFromCache()
        if (cachedCharacters.isNotEmpty()) {
            return cachedCharacters
        }
        return loadCharactersFromAssets()
    }

    private fun loadCharactersFromAssets(): List<CharacterPack> {
        return try {
            val jsonString = context.assets.open("characters.json").bufferedReader().use { it.readText() }
            val characterDataList = parseCharacterData(jsonString)
            characterDataList.map { 
                CharacterPack(
                    id = it.id, 
                    name = it.name, 
                    imageResId = getDrawableResourceIdByName(it.imageResName)
                )
            }
        } catch (ioException: IOException) {
            Log.e("CharacterManager", "Error loading characters from assets", ioException)
            emptyList()
        }
    }

    private fun loadCharactersFromCache(): List<CharacterPack> {
        val configFile = File(characterCacheDir, "characters.json")
        if (!configFile.exists()) return emptyList()

        return try {
            val jsonString = configFile.readText()
            val characterDataList = parseCharacterData(jsonString)
            characterDataList.map { 
                val imageFile = File(characterCacheDir, it.imageResName)
                CharacterPack(
                    id = it.id, 
                    name = it.name, 
                    imagePath = if (imageFile.exists()) imageFile.absolutePath else null,
                    imageResId = if (!imageFile.exists()) getDrawableResourceIdByName(it.imageResName) else null
                )
            }
        } catch (e: Exception) {
            Log.e("CharacterManager", "Error loading characters from cache", e)
            emptyList()
        }
    }

    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = URL(remoteCharactersUrl).readText()
                val remoteData = parseCharacterData(jsonString)

                if (!characterCacheDir.exists()) {
                    characterCacheDir.mkdirs()
                }

                File(characterCacheDir, "characters.json").writeText(jsonString)

                remoteData.forEach { character ->
                    character.imageUrl?.let { url ->
                        try {
                            val imageFile = File(characterCacheDir, character.imageResName)
                            URL(url).openStream().use { input ->
                                FileOutputStream(imageFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (e: IOException) {
                            Log.e("CharacterManager", "Failed to download image for ${character.name}", e)
                        }
                    }
                }
                Log.d("CharacterManager", "Successfully updated character packs.")

            } catch (e: Exception) {
                Log.e("CharacterManager", "Failed to check for character updates", e)
            }
        }
    }

    private fun parseCharacterData(jsonString: String): List<CharacterData> {
        val type = object : TypeToken<List<CharacterData>>() {}.type
        return gson.fromJson(jsonString, type)
    }

    private data class CharacterData(val id: String, val name: String, val imageResName: String, val imageUrl: String? = null)
}
