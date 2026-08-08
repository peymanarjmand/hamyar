package com.hesabyar.garage.domain

data class AbsencePolicyStatus(
    val absentDays: Int,
    val eligibleDays: Int,
    val ineligibleDays: Int,
    val isOpen: Boolean,
    val hasStarted: Boolean,
    val currentlyEligible: Boolean?,
)

object AbsencePolicy {
    fun evaluate(startEpochDay: Long, returnEpochDay: Long?, referenceEpochDay: Long, graceDays: Int): AbsencePolicyStatus {
        require(returnEpochDay == null || returnEpochDay > startEpochDay) { "روز بازگشت باید بعد از شروع غیبت باشد" }
        require(graceDays in EligibilityEngine.MIN_GRACE_DAYS..EligibilityEngine.MAX_GRACE_DAYS) { "قانون غیبت معتبر نیست" }

        val hasStarted = referenceEpochDay >= startEpochDay
        val isOpen = returnEpochDay == null
        val lastCountedDay = when {
            !hasStarted -> startEpochDay - 1
            returnEpochDay != null -> returnEpochDay - 1
            else -> referenceEpochDay
        }
        val absentDays = Math.toIntExact((lastCountedDay - startEpochDay + 1).coerceAtLeast(0))
        val eligibleDays = minOf(absentDays, graceDays)
        return AbsencePolicyStatus(
            absentDays = absentDays,
            eligibleDays = eligibleDays,
            ineligibleDays = absentDays - eligibleDays,
            isOpen = isOpen,
            hasStarted = hasStarted,
            currentlyEligible = if (isOpen && hasStarted) absentDays <= graceDays else null,
        )
    }
}
