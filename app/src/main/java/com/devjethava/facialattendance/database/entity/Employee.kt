package com.devjethava.facialattendance.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

// Data Classes and Database
@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey val id: String,
    val name: String,
    val faceEmbedding: String
) {
    // Helper methods to work with embeddings
    fun getEmbeddingArray(): FloatArray? {
        return try {
            faceEmbedding.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun fromEmbedding(id: String, name: String, embedding: FloatArray): Employee {
            return Employee(
                id = id,
                name = name,
                faceEmbedding = embedding.joinToString(",")
            )
        }
    }
}

// Type converter for FloatArray <-> String conversion
class Converters {
    @TypeConverter
    fun fromString(value: String): FloatArray {
        val listType = object : TypeToken<FloatArray>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        return Gson().toJson(array)
    }
}