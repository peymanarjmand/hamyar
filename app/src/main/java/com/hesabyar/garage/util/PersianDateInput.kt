package com.hesabyar.garage.util

/** Pure validation helpers shared by the Jalali date editor and its tests. */
object PersianDateInput {
    const val MIN_YEAR = 1300
    const val MAX_YEAR = 1600

    fun digits(raw: String, maxLength: Int): String = raw
        .toLatinDigits()
        .filter(Char::isDigit)
        .take(maxLength)

    fun parse(yearText: String, monthText: String, dayText: String): PersianDate? {
        val year = yearText.toLatinDigits().toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR } ?: return null
        val month = monthText.toLatinDigits().toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = dayText.toLatinDigits().toIntOrNull()?.takeIf { it in 1..PersianDate.monthLength(year, month) } ?: return null
        return PersianDate(year, month, day)
    }

    fun clampDay(year: Int, month: Int, day: Int): Int =
        day.coerceIn(1, PersianDate.monthLength(year, month))
}
