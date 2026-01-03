package com.example.medicationreminderapp.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TypeConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String): Map<Int, Long> {
        val mapType = object : TypeToken<Map<Int, Long>>() {}.type
        return gson.fromJson(value, mapType)
    }

    @TypeConverter
    fun fromMap(map: Map<Int, Long>): String {
        return gson.toJson(map)
    }
}
