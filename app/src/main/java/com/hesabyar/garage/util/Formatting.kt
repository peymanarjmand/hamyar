package com.hesabyar.garage.util

import java.text.NumberFormat
import java.util.Locale

fun formatMoney(amount: Long): String {
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(amount)
    return "${grouped.toPersianDigits()} تومان"
}

object IranianCardValidator {
    fun normalize(input: String): String = input.toLatinDigits().filter(Char::isDigit)

    fun isValid(input: String): Boolean {
        val value = normalize(input)
        if (value.length != 16 || value.all { it == value.first() }) return false
        val sum = value.mapIndexed { index, char ->
            val product = (char - '0') * if (index % 2 == 0) 2 else 1
            if (product > 9) product - 9 else product
        }.sum()
        return sum % 10 == 0
    }

    fun formatFull(input: String): String = normalize(input).chunked(4).joinToString("-").toPersianDigits()

    fun mask(input: String): String {
        val value = normalize(input)
        if (value.length != 16) return "کارت نامعتبر"
        return "${value.take(4)}-${value.substring(4, 6)}**-****-${value.takeLast(4)}".toPersianDigits()
    }
}

object MovementTimeInput {
    fun digits(input: String): String = input.toLatinDigits().filter(Char::isDigit).take(4)

    fun parse(input: String): Int? {
        val value = digits(input)
        if (value.isBlank()) return null
        if (value.length != 4) return null
        val hour = value.take(2).toIntOrNull() ?: return null
        val minute = value.takeLast(2).toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    fun isValidOrBlank(input: String): Boolean = input.isBlank() || parse(input) != null

    fun displayInput(input: String): String {
        val value = digits(input)
        return if (value.length <= 2) value.toPersianDigits() else "${value.take(2)}:${value.drop(2)}".toPersianDigits()
    }

    fun formatMinutes(minutes: Int): String =
        "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60).toPersianDigits()
}
