package com.pokerarity.scanner.data.local.db

import androidx.room.TypeConverter
import com.pokerarity.scanner.data.model.RecognitionTruth
import java.util.Date

/**
 * Room TypeConverters for non-primitive types.
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromRecognitionTruth(value: RecognitionTruth): String = value.name

    @TypeConverter
    fun toRecognitionTruth(value: String): RecognitionTruth =
        RecognitionTruth.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: RecognitionTruth.UNKNOWN
}
