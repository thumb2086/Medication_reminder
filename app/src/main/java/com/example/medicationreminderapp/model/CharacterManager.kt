package com.example.medicationreminderapp.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class CharacterManager(private val context: Context) {

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
                if (!imageFile.exists()) {
                    try {
                        downloadImage(character.imageUrl, imageFile)
                    } catch (e: IOException) {
                        // Handle image download error, maybe return character without image path
                    }
                }
                character.copy(imagePath = imageFile.absolutePath)
            }
        }
    }

    private fun downloadImage(url: String, file: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun getCharacters(): List<Character> {
        return withContext(Dispatchers.IO) {
            // First, try to load from local cache
            if (jsonFile.exists()) {
                try {
                    val json = jsonFile.readText()
                    val type = object : TypeToken<List<Character>>() {}.type
                    return@withContext gson.fromJson(json, type)
                } catch (e: Exception) {
                    // If local cache is corrupt, proceed to download
                }
            }
            // If no local cache, download from remote
            return@withContext downloadCharacters()
        }
    }

    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            downloadCharacters()
        }
    }

    private fun downloadCharacters(): List<Character> {
        try {
            val request = Request.Builder().url(remoteJsonUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonStr = response.body!!.string()
                jsonFile.writeText(jsonStr) // Cache the new json

                val type = object : TypeToken<List<Character>>() {}.type
                return gson.fromJson(jsonStr, type)
            }
        } catch (e: Exception) {
            // In case of network error, return an empty list or handle error appropriately
            return emptyList()
        }
    }
}