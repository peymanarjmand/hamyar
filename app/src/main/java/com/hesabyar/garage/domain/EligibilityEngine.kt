package com.hesabyar.garage.domain

object EligibilityEngine {
    const val DEFAULT_GRACE_DAYS = 14
    const val MIN_GRACE_DAYS = 0
    const val MAX_GRACE_DAYS = 365

    fun evaluate(
        eventEpochDay: Long,
        employmentRanges: List<EmploymentRange>,
        absenceRanges: List<AbsenceRange>,
        graceDays: Int = DEFAULT_GRACE_DAYS,
    ): EligibilityResult {
        require(graceDays in MIN_GRACE_DAYS..MAX_GRACE_DAYS) { "تعداد روزهای غیبت مجاز معتبر نیست" }
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
        return if (absenceDay <= graceDays) {
            EligibilityResult(
                status = EligibilityStatus.ELIGIBLE,
                reasonCode = "ABSENCE_GRACE",
                reasonFa = "روز $absenceDay از $graceDays روز غیبت مشمول سهم",
                employmentPeriodId = employment.id,
                absencePeriodId = absence.id,
                absenceDay = absenceDay,
            )
        } else {
            EligibilityResult(
                status = EligibilityStatus.NOT_ELIGIBLE,
                reasonCode = "ABSENCE_OVER_GRACE",
                reasonFa = if (graceDays == 0) "غیبت از روز اول مشمول سهم نیست" else "روز $absenceDay غیبت؛ بیشتر از $graceDays روز",
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
