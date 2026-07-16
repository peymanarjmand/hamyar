package com.hesabyar.garage.util

import java.time.LocalDate

data class PersianDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12)
        require(day in 1..monthLength(year, month))
    }

    fun toLocalDate(): LocalDate {
        var jy = year + 1595
        var days = -355668L + 365L * jy + (jy / 33) * 8L + ((jy % 33 + 3) / 4)
        days += day
        days += if (month < 7) (month - 1) * 31L else (month - 7) * 30L + 186L

        var gy = 400 * (days / 146097).toInt()
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524).toInt()
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461).toInt()
        days %= 1461
        if (days > 365) {
            gy += ((days - 1) / 365).toInt()
            days = (days - 1) % 365
        }
        var gd = days.toInt() + 1
        val monthLengths = intArrayOf(0, 31, if (isGregorianLeap(gy)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > monthLengths[gm]) {
            gd -= monthLengths[gm]
            gm++
        }
        return LocalDate.of(gy, gm, gd)
    }

    fun toEpochDay(): Long = toLocalDate().toEpochDay()

    fun format(): String = "${year.toString().toPersianDigits()}/${month.toString().padStart(2, '0').toPersianDigits()}/${day.toString().padStart(2, '0').toPersianDigits()}"

    companion object {
        val monthNames = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        fun today(): PersianDate = from(LocalDate.now())

        fun fromEpochDay(epochDay: Long): PersianDate = from(LocalDate.ofEpochDay(epochDay))

        fun from(date: LocalDate): PersianDate {
            val gy = date.year
            val gm = date.monthValue
            val gd = date.dayOfMonth
            val offsets = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            val gy2 = if (gm > 2) gy + 1 else gy
            var days = 355666L + 365L * gy + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + offsets[gm - 1]
            var jy = -1595 + 33 * (days / 12053).toInt()
            days %= 12053
            jy += 4 * (days / 1461).toInt()
            days %= 1461
            if (days > 365) {
                jy += ((days - 1) / 365).toInt()
                days = (days - 1) % 365
            }
            val jm: Int
            val jd: Int
            if (days < 186) {
                jm = 1 + (days / 31).toInt()
                jd = 1 + (days % 31).toInt()
            } else {
                jm = 7 + ((days - 186) / 30).toInt()
                jd = 1 + ((days - 186) % 30).toInt()
            }
            return PersianDate(jy, jm, jd)
        }

        fun monthLength(year: Int, month: Int): Int = when (month) {
            in 1..6 -> 31
            in 7..11 -> 30
            12 -> if (isLeap(year)) 30 else 29
            else -> error("ماه نامعتبر")
        }

        fun isLeap(year: Int): Boolean {
            val start = PersianDateUnchecked(year, 1, 1).toLocalDate()
            val next = PersianDateUnchecked(year + 1, 1, 1).toLocalDate()
            return next.toEpochDay() - start.toEpochDay() == 366L
        }

        private fun isGregorianLeap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        private data class PersianDateUnchecked(val year: Int, val month: Int, val day: Int) {
            fun toLocalDate(): LocalDate {
                var jy = year + 1595
                var days = -355668L + 365L * jy + (jy / 33) * 8L + ((jy % 33 + 3) / 4) + day
                days += if (month < 7) (month - 1) * 31L else (month - 7) * 30L + 186L
                var gy = 400 * (days / 146097).toInt()
                days %= 146097
                if (days > 36524) {
                    gy += 100 * (--days / 36524).toInt(); days %= 36524; if (days >= 365) days++
                }
                gy += 4 * (days / 1461).toInt(); days %= 1461
                if (days > 365) { gy += ((days - 1) / 365).toInt(); days = (days - 1) % 365 }
                var gd = days.toInt() + 1
                val ml = intArrayOf(0, 31, if (isGregorianLeap(gy)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
                var gm = 1
                while (gd > ml[gm]) { gd -= ml[gm]; gm++ }
                return LocalDate.of(gy, gm, gd)
            }
        }
    }
}

fun String.toPersianDigits(): String = buildString(length) {
    this@toPersianDigits.forEach { append(if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it) }
}

fun String.toLatinDigits(): String = buildString(length) {
    this@toLatinDigits.forEach { char ->
        append(
            when (char) {
                in '۰'..'۹' -> '0' + (char - '۰')
                in '٠'..'٩' -> '0' + (char - '٠')
                else -> char
            },
        )
    }
}
