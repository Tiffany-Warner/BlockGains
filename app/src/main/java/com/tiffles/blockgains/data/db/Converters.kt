package com.tiffles.blockgains.data.db

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate

/** Money/quantities as plain decimal text (full precision, never REAL); dates as ISO text. */
class Converters {
    @TypeConverter
    fun fromDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
