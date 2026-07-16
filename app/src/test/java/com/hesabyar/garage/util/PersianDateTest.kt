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
}
