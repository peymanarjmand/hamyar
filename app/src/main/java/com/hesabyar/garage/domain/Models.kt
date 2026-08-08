package com.hesabyar.garage.domain

enum class EmployeeRole { BODYWORK, PAINTER, BOTH, OTHER }
enum class AccountType { CASH, CARD, POS }
enum class FinancialYearStatus { OPEN, CLOSED }
enum class IncomeType { TIP, SCRAP }
enum class MoneyMethod { CASH, CARD, POS }
enum class RecordStatus { POSTED, REVERSED, DELETED }
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
    val totalEntitlement: Long = 0,
    val settledEntitlement: Long = 0,
) {
    val net: Long get() = Math.subtractExact(claim, custody)
    val selfOffsetNeeded: Long get() = minOf(claim, custody)
    val isFullySettled: Boolean get() = claim == 0L && custody == 0L
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
