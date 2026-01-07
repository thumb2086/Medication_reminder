package com.example.medicationreminderapp.model

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class CharacterManager(context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonFile = File(context.filesDir, "characters.json")
    private val imagesDir = File(context.filesDir, "characters")
    private val remoteJsonUrl = "https://raw.githubusercontent.com/thumb2086/Medication_reminder/main/characters.json"

    init {
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
    }

    suspend fun getCharactersWithImages(): List<Character> {
        return withContext(Dispatchers.IO) {
            val characters = getCharacters()
            characters.map { character ->
                val imageFile = File(imagesDir, "${character.id}.png")

                if (!imageFile.exists() && character.imageUrl.isNotBlank()) {
                    try {
                        downloadImage(character.imageUrl, imageFile)
                    } catch (_: IOException) {
                        Log.e("CharacterManager", "Image download failed for ${character.id} from ${character.imageUrl}")
                        if (imageFile.exists()) {
                            imageFile.delete()
                        }
                    }
                }

                if (imageFile.exists()) {
                    character.copy(imagePath = imageFile.absolutePath)
                } else {
                    character.copy(imagePath = null)
                }
            }
        }
    }

    private fun downloadImage(url: String, file: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun getCharacters(): List<Character> {
        return withContext(Dispatchers.IO) {
            if (jsonFile.exists()) {
                try {
                    val json = jsonFile.readText()
                    val type = object : TypeToken<List<Character>>() {}.type
                    return@withContext gson.fromJson(json, type)
                } catch (_: Exception) {
                    Log.w("CharacterManager", "Local characters.json is corrupt, re-downloading.")
                }
            }
            return@withContext downloadCharacters()
        }
    }

    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                downloadCharacters()
            } catch (_: Exception) {
                 Log.e("CharacterManager", "Failed to check for character updates.")
            }
        }
    }

    private fun downloadCharacters(): List<Character> {
        try {
            val request = Request.Builder().url(remoteJsonUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonStr = response.body?.string()
                if (jsonStr != null) {
                    jsonFile.writeText(jsonStr)
                    val type = object : TypeToken<List<Character>>() {}.type
                    return gson.fromJson(jsonStr, type)
                }
                return emptyList()
            }
        } catch (_: Exception) {
            Log.e("CharacterManager", "Could not download characters.json")
            return emptyList()
        }
    }
}