package com.confidant.ai.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Room type converters for complex data types
 */
class Converters {
    companion object {
        private val gson = Gson() // Singleton instance
    }
    
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
    
    @TypeConverter
    fun toInstant(millis: Long?): Instant? {
        return millis?.let { Instant.ofEpochMilli(it) }
    }
    
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): Long? {
        return dateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }
    
    @TypeConverter
    fun toLocalDateTime(millis: Long?): LocalDateTime? {
        return millis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
    }
    
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String? {
        return array?.joinToString(",")
    }
    
    @TypeConverter
    fun toFloatArray(str: String?): FloatArray? {
        return str?.split(",")?.map { it.toFloat() }?.toFloatArray()
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return Companion.gson.toJson(list)
    }
    
    @TypeConverter
    fun toStringList(str: String?): List<String>? {
        return str?.let {
            val type = object : TypeToken<List<String>>() {}.type
            Companion.gson.fromJson(it, type)
        }
    }
    
    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        return Companion.gson.toJson(map)
    }
    
    @TypeConverter
    fun toStringMap(str: String?): Map<String, String>? {
        return str?.let {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Companion.gson.fromJson(it, type)
        }
    }
}