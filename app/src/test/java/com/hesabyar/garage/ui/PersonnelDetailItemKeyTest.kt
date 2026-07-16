package com.hesabyar.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersonnelDetailItemKeyTest {
    @Test
    fun `same database id in different sections produces unique lazy list keys`() {
        val overlappingId = "1"
        val keys = PersonnelDetailItemSection.entries.map { section ->
            personnelDetailItemKey(section, overlappingId)
        }

        assertEquals(PersonnelDetailItemSection.entries.size, keys.toSet().size)
    }

    @Test
    fun `different rows in the same section retain unique keys`() {
        val first = personnelDetailItemKey(PersonnelDetailItemSection.ACCOUNT, "1")
        val second = personnelDetailItemKey(PersonnelDetailItemSection.ACCOUNT, "2")

        assertNotEquals(first, second)
    }
}
