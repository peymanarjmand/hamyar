package com.hesabyar.garage.util

import org.junit.Assert.*
import org.junit.Test

class IranianCardValidatorTest {
    @Test fun `accepts valid Persian and Latin card input`() {
        assertTrue(IranianCardValidator.isValid("5892107047168045"))
        assertTrue(IranianCardValidator.isValid("۵۸۹۲-۱۰۷۰-۴۷۱۶-۸۰۴۵"))
    }

    @Test fun `rejects invalid length checksum and repeated digits`() {
        assertFalse(IranianCardValidator.isValid("5892107047168046"))
        assertFalse(IranianCardValidator.isValid("123"))
        assertFalse(IranianCardValidator.isValid("1111111111111111"))
    }

    @Test fun `masks middle digits`() {
        assertEquals("۵۸۹۲-۱۰**-****-۸۰۴۵", IranianCardValidator.mask("5892107047168045"))
    }
}
