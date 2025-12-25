package xyz.mordorx.flacblaster.fs

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromMap(value: Map<String, List<String>>): String = Json.encodeToString(value)

    @TypeConverter
    fun toMap(value: String): Map<String, List<String>> = Json.decodeFromString(value)
}
