package com.hesabyar.garage.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTimeInputTest {
    @Test fun `accepts optional Persian or Latin 24 hour time`() {
        assertNull(MovementTimeInput.parse(""))
        assertEquals(14 * 60 + 30, MovementTimeInput.parse("۱۴:۳۰"))
        assertEquals(9 * 60 + 5, MovementTimeInput.parse("09:05"))
        assertTrue(MovementTimeInput.isValidOrBlank(""))
    }

    @Test fun `rejects incomplete and out of range time`() {
        assertFalse(MovementTimeInput.isValidOrBlank("930"))
        assertFalse(MovementTimeInput.isValidOrBlank("24:00"))
        assertFalse(MovementTimeInput.isValidOrBlank("12:60"))
    }
}
