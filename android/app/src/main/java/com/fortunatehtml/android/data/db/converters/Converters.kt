package com.fortunatehtml.android.data.db.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Room type converters for non-primitive fields. */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String =
        gson.toJson(value ?: emptyMap<String, String>())

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, type) ?: emptyMap()
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
