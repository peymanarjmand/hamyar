package com.hesabyar.garage.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PersianDateTest {
    @Test fun `known Nowruz dates convert correctly`() {
        assertEquals(LocalDate.of(2026, 3, 21), PersianDate(1405, 1, 1).toLocalDate())
        assertEquals(PersianDate(1405, 1, 1), PersianDate.from(LocalDate.of(2026, 3, 21)))
        assertEquals(LocalDate.of(2025, 3, 21), PersianDate(1404, 1, 1).toLocalDate())
    }

    @Test fun `round trip remains stable across years`() {
        for (year in 1390..1410) {
            for (month in 1..12) {
                val day = PersianDate.monthLength(year, month)
                val original = PersianDate(year, month, day)
                assertEquals(original, PersianDate.from(original.toLocalDate()))
            }
        }
    }

    @Test fun `month lengths and leap Esfand are correct`() {
        assertEquals(31, PersianDate.monthLength(1405, 1))
        assertEquals(30, PersianDate.monthLength(1405, 7))
        assertEquals(29, PersianDate.monthLength(1405, 12))
        assertEquals(30, PersianDate.monthLength(1403, 12))
    }

    @Test fun `date input accepts Persian Arabic and Latin digits`() {
        assertEquals(PersianDate(1405, 3, 7), PersianDateInput.parse("۱۴۰۵", "۳", "۷"))
        assertEquals(PersianDate(1405, 3, 7), PersianDateInput.parse("١٤٠٥", "٣", "٧"))
        assertEquals(PersianDate(1405, 3, 7), PersianDateInput.parse("1405", "3", "7"))
        assertEquals("140512", PersianDateInput.digits("سال ۱۴۰۵/۱۲", 6))
    }

    @Test fun `date input rejects invalid calendar values`() {
        assertNull(PersianDateInput.parse("1299", "1", "1"))
        assertNull(PersianDateInput.parse("1405", "13", "1"))
        assertNull(PersianDateInput.parse("1405", "12", "30"))
        assertEquals(29, PersianDateInput.clampDay(1405, 12, 31))
        assertEquals(30, PersianDateInput.clampDay(1403, 12, 31))
    }
}
