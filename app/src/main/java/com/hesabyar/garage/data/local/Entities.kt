package com.hesabyar.garage.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hesabyar.garage.domain.*

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val role: EmployeeRole,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "employment_periods",
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("employeeId")],
)
data class EmploymentPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val voidedAt: Long? = null,
)

@Entity(
    tableName = "absence_periods",
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("employeeId")],
)
data class AbsencePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val startEpochDay: Long,
    val returnEpochDay: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val voidedAt: Long? = null,
)

@Entity(
    tableName = "money_accounts",
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("employeeId"), Index(value = ["cardFingerprint"], unique = true)],
)
data class MoneyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val type: AccountType,
    val title: String,
    val ownerName: String,
    val bankName: String = "",
    val cardNumberEncrypted: String = "",
    val cardLast4: String = "",
    val cardFingerprint: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
)

@Entity(tableName = "financial_years")
data class FinancialYearEntity(
    @PrimaryKey val year: Int,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val status: FinancialYearStatus = FinancialYearStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
)

@Entity(
    tableName = "income_events",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["receiverEmployeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["receiverAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("financialYear"), Index("receiverEmployeeId"), Index("receiverAccountId"), Index(value = ["commandId"], unique = true)],
)
data class IncomeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val financialYear: Int,
    val dateEpochDay: Long,
    val amount: Long,
    val type: IncomeType,
    val receiverEmployeeId: Long,
    val receiverAccountId: Long,
    val method: MoneyMethod,
    val description: String = "",
    val carriedRemainder: Long,
    val sharePerPerson: Long,
    val resultingRemainder: Long,
    val status: RecordStatus = RecordStatus.POSTED,
    val createdAt: Long = System.currentTimeMillis(),
    val reversedAt: Long? = null,
    val replacesEventId: Long? = null,
)

@Entity(
    tableName = "eligibility_snapshots",
    foreignKeys = [
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("eventId"), Index("employeeId"), Index(value = ["eventId", "employeeId"], unique = true)],
)
data class EligibilitySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val employeeId: Long,
    val status: EligibilityStatus,
    val reasonCode: String,
    val reasonFa: String,
    val employmentPeriodId: Long? = null,
    val absencePeriodId: Long? = null,
    val absenceDay: Int? = null,
    val shareAmount: Long = 0,
    val evaluatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "claim_postings",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["sourceIncomeEventId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("financialYear"), Index("employeeId"), Index("sourceIncomeEventId"), Index("referenceId")],
)
data class ClaimPostingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val financialYear: Int,
    val sourceIncomeEventId: Long? = null,
    val employeeId: Long? = null,
    val amountSigned: Long,
    val type: ClaimPostingType,
    val referenceId: Long,
    val businessDateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "custody_postings",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["sourceIncomeEventId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("financialYear"), Index("employeeId"), Index("accountId"), Index("sourceIncomeEventId"), Index("referenceId")],
)
data class CustodyPostingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val financialYear: Int,
    val sourceIncomeEventId: Long? = null,
    val employeeId: Long,
    val accountId: Long,
    val amountSigned: Long,
    val type: CustodyPostingType,
    val referenceId: Long,
    val businessDateEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "custody_transfers",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["fromEmployeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["toEmployeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("financialYear"), Index("fromEmployeeId"), Index("toEmployeeId"), Index("fromAccountId"), Index("toAccountId"), Index(value = ["commandId"], unique = true)],
)
data class CustodyTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val financialYear: Int,
    val dateEpochDay: Long,
    val fromEmployeeId: Long,
    val fromAccountId: Long,
    val toEmployeeId: Long,
    val toAccountId: Long,
    val amount: Long,
    val method: MoneyMethod,
    val note: String = "",
    val status: RecordStatus = RecordStatus.POSTED,
    val createdAt: Long = System.currentTimeMillis(),
    val reversedAt: Long? = null,
)

@Entity(
    tableName = "custody_transfer_allocations",
    primaryKeys = ["transferId", "sourceIncomeEventId"],
    foreignKeys = [
        ForeignKey(entity = CustodyTransferEntity::class, parentColumns = ["id"], childColumns = ["transferId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["sourceIncomeEventId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sourceIncomeEventId")],
)
data class CustodyTransferAllocationEntity(
    val transferId: Long,
    val sourceIncomeEventId: Long,
    val amount: Long,
)

@Entity(
    tableName = "settlement_payments",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["fromEmployeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["toEmployeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MoneyAccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("financialYear"), Index("fromEmployeeId"), Index("toEmployeeId"), Index("fromAccountId"), Index("destinationAccountId"), Index(value = ["commandId"], unique = true)],
)
data class SettlementPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val financialYear: Int,
    val dateEpochDay: Long,
    val fromEmployeeId: Long,
    val fromAccountId: Long,
    val toEmployeeId: Long,
    val destinationAccountId: Long?,
    val amount: Long,
    val method: MoneyMethod,
    val type: SettlementType,
    val note: String = "",
    val status: RecordStatus = RecordStatus.POSTED,
    val createdAt: Long = System.currentTimeMillis(),
    val reversedAt: Long? = null,
)

@Entity(
    tableName = "settlement_claim_allocations",
    primaryKeys = ["paymentId", "sourceIncomeEventId"],
    foreignKeys = [
        ForeignKey(entity = SettlementPaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["sourceIncomeEventId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sourceIncomeEventId")],
)
data class SettlementClaimAllocationEntity(val paymentId: Long, val sourceIncomeEventId: Long, val amount: Long)

@Entity(
    tableName = "settlement_custody_allocations",
    primaryKeys = ["paymentId", "sourceIncomeEventId"],
    foreignKeys = [
        ForeignKey(entity = SettlementPaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = IncomeEventEntity::class, parentColumns = ["id"], childColumns = ["sourceIncomeEventId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sourceIncomeEventId")],
)
data class SettlementCustodyAllocationEntity(val paymentId: Long, val sourceIncomeEventId: Long, val amount: Long)

@Entity(
    tableName = "annual_closes",
    foreignKeys = [ForeignKey(entity = FinancialYearEntity::class, parentColumns = ["year"], childColumns = ["financialYear"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("financialYear")],
)
data class AnnualCloseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val financialYear: Int,
    val revision: Int,
    val closedAt: Long,
    val ledgerHash: String,
    val backupChecksum: String,
    val summaryJson: String,
    val status: AnnualCloseStatus = AnnualCloseStatus.FINAL,
    val reversedAt: Long? = null,
    val reverseReason: String? = null,
)

@Entity(
    tableName = "annual_close_employee_snapshots",
    primaryKeys = ["closeId", "employeeId"],
    foreignKeys = [
        ForeignKey(entity = AnnualCloseEntity::class, parentColumns = ["id"], childColumns = ["closeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("employeeId")],
)
data class AnnualCloseEmployeeSnapshotEntity(
    val closeId: Long,
    val employeeId: Long,
    val totalClaim: Long,
    val totalCustodyIn: Long,
    val totalCustodyOut: Long,
    val totalSettlementPaid: Long,
    val totalSettlementReceived: Long,
    val finalNet: Long,
)

@Entity(tableName = "audit_logs", indices = [Index("financialYear"), Index("entityType"), Index("entityId")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val financialYear: Int? = null,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
)
