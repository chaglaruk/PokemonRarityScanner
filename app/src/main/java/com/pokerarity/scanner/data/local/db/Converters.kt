package com.pokerarity.scanner.data.local.db

import androidx.room.TypeConverter
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
}
