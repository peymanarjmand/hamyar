package com.hesabyar.garage.domain

enum class EmployeeRole { BODYWORK, PAINTER, BOTH, OTHER }
enum class AccountType { CASH, CARD }
enum class FinancialYearStatus { OPEN, CLOSED }
enum class IncomeType { TIP, SCRAP }
enum class MoneyMethod { CASH, CARD }
enum class RecordStatus { POSTED, REVERSED }
enum class EligibilityStatus { ELIGIBLE, NOT_ELIGIBLE }
enum class SettlementType { PAYMENT, SELF_OFFSET }
enum class AnnualCloseStatus { FINAL, REVERSED }

enum class ClaimPostingType {
    ENTITLEMENT,
    REMAINDER_ADD,
    REMAINDER_RELEASE,
    SETTLEMENT_RECEIVED,
    SELF_OFFSET,
    ANNUAL_ROLLOVER_OUT,
    ANNUAL_ROLLOVER_IN,
}

enum class CustodyPostingType {
    INCOME_RECEIPT,
    TRANSFER_OUT,
    TRANSFER_IN,
    SETTLEMENT_PAID,
    SELF_OFFSET,
    ANNUAL_ROLLOVER_OUT,
    ANNUAL_ROLLOVER_IN,
}

data class EmploymentRange(val id: Long, val startEpochDay: Long, val endEpochDay: Long?)
data class AbsenceRange(val id: Long, val startEpochDay: Long, val returnEpochDay: Long?)

data class EligibilityResult(
    val status: EligibilityStatus,
    val reasonCode: String,
    val reasonFa: String,
    val employmentPeriodId: Long? = null,
    val absencePeriodId: Long? = null,
    val absenceDay: Int? = null,
) {
    val isEligible: Boolean get() = status == EligibilityStatus.ELIGIBLE
}

data class PersonBalance(
    val employeeId: Long,
    val employeeName: String,
    val claim: Long,
    val custody: Long,
) {
    val net: Long get() = Math.subtractExact(claim, custody)
}

data class SettlementSuggestion(
    val fromEmployeeId: Long,
    val fromName: String,
    val toEmployeeId: Long,
    val toName: String,
    val amount: Long,
    val type: SettlementType,
)

data class IncomeDistribution(
    val totalAvailable: Long,
    val sharePerPerson: Long,
    val resultingRemainder: Long,
    val eligibleCount: Int,
)
