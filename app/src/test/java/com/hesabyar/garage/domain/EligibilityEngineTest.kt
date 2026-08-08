package com.hesabyar.garage.domain

import org.junit.Assert.*
import org.junit.Test

class EligibilityEngineTest {
    private val employment = listOf(EmploymentRange(1, 100, 300))

    @Test fun `present person is eligible and boundaries are inclusive`() {
        assertTrue(EligibilityEngine.evaluate(100, employment, emptyList()).isEligible)
        assertTrue(EligibilityEngine.evaluate(300, employment, emptyList()).isEligible)
        assertFalse(EligibilityEngine.evaluate(99, employment, emptyList()).isEligible)
        assertFalse(EligibilityEngine.evaluate(301, employment, emptyList()).isEligible)
    }

    @Test fun `absence days one through fourteen are eligible and fifteen is not`() {
        val absence = listOf(AbsenceRange(2, 120, 150))
        val day14 = EligibilityEngine.evaluate(133, employment, absence)
        val day15 = EligibilityEngine.evaluate(134, employment, absence)
        assertTrue(day14.isEligible)
        assertEquals(14, day14.absenceDay)
        assertFalse(day15.isEligible)
        assertEquals(15, day15.absenceDay)
    }

    @Test fun `return day is present`() {
        val result = EligibilityEngine.evaluate(150, employment, listOf(AbsenceRange(2, 120, 150)))
        assertTrue(result.isEligible)
        assertEquals("PRESENT", result.reasonCode)
    }

    @Test fun `open absence keeps counting`() {
        val result = EligibilityEngine.evaluate(200, employment, listOf(AbsenceRange(2, 120, null)))
        assertFalse(result.isEligible)
        assertEquals(81, result.absenceDay)
    }

    @Test fun `custom grace days control only the requested boundary`() {
        val absence = listOf(AbsenceRange(2, 120, null))
        assertTrue(EligibilityEngine.evaluate(124, employment, absence, graceDays = 5).isEligible)
        assertFalse(EligibilityEngine.evaluate(125, employment, absence, graceDays = 5).isEligible)
        assertFalse(EligibilityEngine.evaluate(120, employment, absence, graceDays = 0).isEligible)
    }

    @Test fun `absence policy reports open and completed status with return day excluded`() {
        val open = AbsencePolicy.evaluate(startEpochDay = 100, returnEpochDay = null, referenceEpochDay = 105, graceDays = 7)
        assertEquals(6, open.absentDays)
        assertEquals(6, open.eligibleDays)
        assertTrue(open.currentlyEligible == true)

        val completed = AbsencePolicy.evaluate(startEpochDay = 100, returnEpochDay = 110, referenceEpochDay = 200, graceDays = 7)
        assertEquals(10, completed.absentDays)
        assertEquals(7, completed.eligibleDays)
        assertEquals(3, completed.ineligibleDays)
        assertNull(completed.currentlyEligible)
    }
}
