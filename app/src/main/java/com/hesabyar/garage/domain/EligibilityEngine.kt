package com.hesabyar.garage.domain

object EligibilityEngine {
    const val GRACE_DAYS = 14

    fun evaluate(
        eventEpochDay: Long,
        employmentRanges: List<EmploymentRange>,
        absenceRanges: List<AbsenceRange>,
    ): EligibilityResult {
        val employment = employmentRanges.firstOrNull { period ->
            eventEpochDay >= period.startEpochDay &&
                (period.endEpochDay == null || eventEpochDay <= period.endEpochDay)
        } ?: return EligibilityResult(
            status = EligibilityStatus.NOT_ELIGIBLE,
            reasonCode = "OUTSIDE_EMPLOYMENT",
            reasonFa = "خارج از دوره همکاری",
        )

        val absence = absenceRanges.firstOrNull { period ->
            eventEpochDay >= period.startEpochDay &&
                (period.returnEpochDay == null || eventEpochDay < period.returnEpochDay)
        }
        if (absence == null) {
            return EligibilityResult(
                status = EligibilityStatus.ELIGIBLE,
                reasonCode = "PRESENT",
                reasonFa = "حاضر و در حال همکاری",
                employmentPeriodId = employment.id,
            )
        }

        val absenceDay = Math.toIntExact(eventEpochDay - absence.startEpochDay + 1)
        return if (absenceDay <= GRACE_DAYS) {
            EligibilityResult(
                status = EligibilityStatus.ELIGIBLE,
                reasonCode = "ABSENCE_GRACE",
                reasonFa = "روز $absenceDay از غیبت مجاز ۱۴ روزه",
                employmentPeriodId = employment.id,
                absencePeriodId = absence.id,
                absenceDay = absenceDay,
            )
        } else {
            EligibilityResult(
                status = EligibilityStatus.NOT_ELIGIBLE,
                reasonCode = "ABSENCE_OVER_GRACE",
                reasonFa = "روز $absenceDay غیبت؛ بیشتر از ۱۴ روز",
                employmentPeriodId = employment.id,
                absencePeriodId = absence.id,
                absenceDay = absenceDay,
            )
        }
    }

    fun rangesOverlap(startA: Long, endA: Long?, startB: Long, endB: Long?): Boolean {
        val aEnd = endA ?: Long.MAX_VALUE
        val bEnd = endB ?: Long.MAX_VALUE
        return startA <= bEnd && startB <= aEnd
    }
}
