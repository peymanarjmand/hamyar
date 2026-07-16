package com.hesabyar.garage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PersonBalanceRow(
    val employeeId: Long,
    val employeeName: String,
    val claimAmount: Long,
    val custodyAmount: Long,
)

data class AccountCustodyRow(
    val accountId: Long,
    val employeeId: Long,
    val accountTitle: String,
    val amount: Long,
)

data class CustodySourceRow(
    val sourceIncomeEventId: Long,
    val amount: Long,
    val sourceDate: Long,
)

data class ClaimSourceRow(
    val sourceIncomeEventId: Long,
    val amount: Long,
    val sourceDate: Long,
)

data class IncomeWithReceiverRow(
    val id: Long,
    val financialYear: Int,
    val dateEpochDay: Long,
    val amount: Long,
    val type: String,
    val receiverName: String,
    val description: String,
    val sharePerPerson: Long,
    val resultingRemainder: Long,
    val status: String,
)

data class PersonHistoryRow(
    val rowId: String,
    val category: String,
    val entryType: String,
    val amountSigned: Long,
    val businessDateEpochDay: Long,
    val referenceId: Long,
    val sourceIncomeEventId: Long?,
    val detail: String,
)

@Dao
interface GarageDao {
    @Insert suspend fun insertEmployee(entity: EmployeeEntity): Long
    @Update suspend fun updateEmployee(entity: EmployeeEntity)
    @Query("SELECT * FROM employees WHERE archivedAt IS NULL ORDER BY name") fun observeEmployees(): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM employees ORDER BY name") suspend fun allEmployees(): List<EmployeeEntity>
    @Query("SELECT * FROM employees WHERE id=:id") suspend fun employee(id: Long): EmployeeEntity?
    @Query("""
        SELECT COUNT(DISTINCT e.id) FROM employees e
        JOIN employment_periods p ON p.employeeId=e.id AND p.voidedAt IS NULL
        WHERE e.archivedAt IS NULL AND :epochDay>=p.startEpochDay AND (p.endEpochDay IS NULL OR :epochDay<=p.endEpochDay)
    """) fun observeActiveEmployeeCount(epochDay: Long): Flow<Int>

    @Insert suspend fun insertEmploymentPeriod(entity: EmploymentPeriodEntity): Long
    @Update suspend fun updateEmploymentPeriod(entity: EmploymentPeriodEntity)
    @Query("SELECT * FROM employment_periods WHERE employeeId=:employeeId AND voidedAt IS NULL ORDER BY startEpochDay") fun observeEmploymentPeriods(employeeId: Long): Flow<List<EmploymentPeriodEntity>>
    @Query("SELECT * FROM employment_periods WHERE employeeId=:employeeId AND voidedAt IS NULL ORDER BY startEpochDay") suspend fun employmentPeriods(employeeId: Long): List<EmploymentPeriodEntity>
    @Query("SELECT * FROM employment_periods WHERE employeeId=:employeeId AND voidedAt IS NULL AND endEpochDay IS NULL ORDER BY startEpochDay DESC LIMIT 1") suspend fun openEmploymentPeriod(employeeId: Long): EmploymentPeriodEntity?
    @Query("SELECT * FROM employment_periods ORDER BY startEpochDay") suspend fun allEmploymentPeriods(): List<EmploymentPeriodEntity>

    @Insert suspend fun insertAbsence(entity: AbsencePeriodEntity): Long
    @Update suspend fun updateAbsence(entity: AbsencePeriodEntity)
    @Query("SELECT * FROM absence_periods WHERE employeeId=:employeeId AND voidedAt IS NULL ORDER BY startEpochDay") fun observeAbsences(employeeId: Long): Flow<List<AbsencePeriodEntity>>
    @Query("SELECT * FROM absence_periods WHERE employeeId=:employeeId AND voidedAt IS NULL ORDER BY startEpochDay") suspend fun absences(employeeId: Long): List<AbsencePeriodEntity>
    @Query("SELECT * FROM absence_periods ORDER BY startEpochDay") suspend fun allAbsences(): List<AbsencePeriodEntity>

    @Insert suspend fun insertAccount(entity: MoneyAccountEntity): Long
    @Update suspend fun updateAccount(entity: MoneyAccountEntity)
    @Query("SELECT * FROM money_accounts WHERE employeeId=:employeeId AND archivedAt IS NULL ORDER BY type, title") fun observeAccounts(employeeId: Long): Flow<List<MoneyAccountEntity>>
    @Query("SELECT * FROM money_accounts WHERE employeeId=:employeeId AND archivedAt IS NULL ORDER BY type, title") suspend fun activeAccounts(employeeId: Long): List<MoneyAccountEntity>
    @Query("SELECT * FROM money_accounts WHERE id=:id") suspend fun account(id: Long): MoneyAccountEntity?
    @Query("SELECT * FROM money_accounts ORDER BY employeeId, type, title") suspend fun allAccounts(): List<MoneyAccountEntity>
    @Query("SELECT * FROM money_accounts WHERE cardFingerprint=:fingerprint LIMIT 1") suspend fun accountByFingerprint(fingerprint: String): MoneyAccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertFinancialYear(entity: FinancialYearEntity): Long
    @Update suspend fun updateFinancialYear(entity: FinancialYearEntity)
    @Query("SELECT * FROM financial_years ORDER BY year DESC") fun observeFinancialYears(): Flow<List<FinancialYearEntity>>
    @Query("SELECT * FROM financial_years WHERE year=:year") suspend fun financialYear(year: Int): FinancialYearEntity?
    @Query("SELECT * FROM financial_years ORDER BY year") suspend fun allFinancialYears(): List<FinancialYearEntity>

    @Insert suspend fun insertIncomeEvent(entity: IncomeEventEntity): Long
    @Update suspend fun updateIncomeEvent(entity: IncomeEventEntity)
    @Query("SELECT * FROM income_events WHERE id=:id") suspend fun incomeEvent(id: Long): IncomeEventEntity?
    @Query("SELECT * FROM income_events WHERE commandId=:commandId LIMIT 1") suspend fun incomeByCommand(commandId: String): IncomeEventEntity?
    @Query("SELECT * FROM income_events ORDER BY dateEpochDay, id") suspend fun allIncomeEvents(): List<IncomeEventEntity>
    @Query("SELECT * FROM income_events WHERE financialYear=:year AND status='POSTED' ORDER BY dateEpochDay DESC, id DESC") fun observeIncomeEvents(year: Int): Flow<List<IncomeEventEntity>>
    @Query("""
        SELECT i.id, i.financialYear, i.dateEpochDay, i.amount, i.type, e.name AS receiverName,
               i.description, i.sharePerPerson, i.resultingRemainder, i.status
        FROM income_events i JOIN employees e ON e.id=i.receiverEmployeeId
        WHERE i.financialYear=:year
        ORDER BY i.dateEpochDay DESC, i.id DESC
    """) fun observeIncomeRows(year: Int): Flow<List<IncomeWithReceiverRow>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM income_events WHERE financialYear=:year AND status='POSTED'") fun observeTotalIncome(year: Int): Flow<Long>
    @Query("SELECT COALESCE(SUM(amount),0) FROM income_events WHERE financialYear=:year AND type=:type AND status='POSTED'") suspend fun totalIncomeByType(year: Int, type: String): Long

    @Insert suspend fun insertEligibilitySnapshots(entities: List<EligibilitySnapshotEntity>)
    @Query("SELECT * FROM eligibility_snapshots WHERE eventId=:eventId ORDER BY status DESC, employeeId") fun observeEligibilitySnapshots(eventId: Long): Flow<List<EligibilitySnapshotEntity>>
    @Query("SELECT * FROM eligibility_snapshots ORDER BY eventId, employeeId") suspend fun allEligibilitySnapshots(): List<EligibilitySnapshotEntity>

    @Insert suspend fun insertClaimPostings(entities: List<ClaimPostingEntity>)
    @Insert suspend fun insertCustodyPostings(entities: List<CustodyPostingEntity>)
    @Query("SELECT * FROM claim_postings ORDER BY id") suspend fun allClaimPostings(): List<ClaimPostingEntity>
    @Query("SELECT * FROM custody_postings ORDER BY id") suspend fun allCustodyPostings(): List<CustodyPostingEntity>
    @Query("SELECT COALESCE(SUM(amountSigned),0) FROM claim_postings WHERE financialYear=:year AND employeeId IS NULL") fun observeRemainder(year: Int): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountSigned),0) FROM claim_postings WHERE financialYear=:year AND employeeId IS NULL") suspend fun remainder(year: Int): Long
    @Query("SELECT COALESCE(SUM(amountSigned),0) FROM claim_postings WHERE financialYear=:year AND employeeId=:employeeId") suspend fun claimBalance(year: Int, employeeId: Long): Long
    @Query("SELECT COALESCE(SUM(amountSigned),0) FROM custody_postings WHERE financialYear=:year AND employeeId=:employeeId") suspend fun custodyBalance(year: Int, employeeId: Long): Long
    @Query("SELECT COALESCE(SUM(amountSigned),0) FROM custody_postings WHERE financialYear=:year AND accountId=:accountId") suspend fun accountCustody(year: Int, accountId: Long): Long
    @Query("""
        SELECT e.id AS employeeId, e.name AS employeeName,
          COALESCE((SELECT SUM(c.amountSigned) FROM claim_postings c WHERE c.financialYear=:year AND c.employeeId=e.id),0) AS claimAmount,
          COALESCE((SELECT SUM(p.amountSigned) FROM custody_postings p WHERE p.financialYear=:year AND p.employeeId=e.id),0) AS custodyAmount
        FROM employees e ORDER BY e.name
    """) fun observePersonBalances(year: Int): Flow<List<PersonBalanceRow>>
    @Query("""
        SELECT e.id AS employeeId, e.name AS employeeName,
          COALESCE((SELECT SUM(c.amountSigned) FROM claim_postings c WHERE c.financialYear=:year AND c.employeeId=e.id),0) AS claimAmount,
          COALESCE((SELECT SUM(p.amountSigned) FROM custody_postings p WHERE p.financialYear=:year AND p.employeeId=e.id),0) AS custodyAmount
        FROM employees e ORDER BY e.name
    """) suspend fun personBalances(year: Int): List<PersonBalanceRow>
    @Query("""
        SELECT a.id AS accountId, a.employeeId AS employeeId, a.title AS accountTitle,
               COALESCE(SUM(p.amountSigned),0) AS amount
        FROM money_accounts a LEFT JOIN custody_postings p ON p.accountId=a.id AND p.financialYear=:year
        WHERE a.archivedAt IS NULL
        GROUP BY a.id HAVING amount > 0 ORDER BY amount DESC
    """) fun observeAccountCustody(year: Int): Flow<List<AccountCustodyRow>>
    @Query("""
        SELECT a.id AS accountId, a.employeeId AS employeeId, a.title AS accountTitle,
               COALESCE(SUM(p.amountSigned),0) AS amount
        FROM money_accounts a LEFT JOIN custody_postings p ON p.accountId=a.id AND p.financialYear=:year
        GROUP BY a.id HAVING amount > 0 ORDER BY amount DESC
    """) suspend fun accountCustodyRows(year: Int): List<AccountCustodyRow>
    @Query("SELECT * FROM claim_postings WHERE financialYear=:year ORDER BY id") suspend fun claimPostingsForYear(year: Int): List<ClaimPostingEntity>
    @Query("SELECT * FROM custody_postings WHERE financialYear=:year ORDER BY id") suspend fun custodyPostingsForYear(year: Int): List<CustodyPostingEntity>
    @Query("""
        SELECT p.sourceIncomeEventId AS sourceIncomeEventId, SUM(p.amountSigned) AS amount,
               MIN(i.dateEpochDay) AS sourceDate
        FROM custody_postings p JOIN income_events i ON i.id=p.sourceIncomeEventId
        WHERE p.financialYear=:year AND p.accountId=:accountId
        GROUP BY p.sourceIncomeEventId HAVING amount > 0 ORDER BY sourceDate, sourceIncomeEventId
    """) suspend fun custodySources(year: Int, accountId: Long): List<CustodySourceRow>
    @Query("""
        SELECT p.sourceIncomeEventId AS sourceIncomeEventId, SUM(p.amountSigned) AS amount,
               MIN(i.dateEpochDay) AS sourceDate
        FROM claim_postings p JOIN income_events i ON i.id=p.sourceIncomeEventId
        WHERE p.financialYear=:year AND p.employeeId=:employeeId
        GROUP BY p.sourceIncomeEventId HAVING amount > 0 ORDER BY sourceDate, sourceIncomeEventId
    """) suspend fun claimSources(year: Int, employeeId: Long): List<ClaimSourceRow>

    @Insert suspend fun insertCustodyTransfer(entity: CustodyTransferEntity): Long
    @Update suspend fun updateCustodyTransfer(entity: CustodyTransferEntity)
    @Insert suspend fun insertCustodyTransferAllocations(entities: List<CustodyTransferAllocationEntity>)
    @Query("SELECT * FROM custody_transfers WHERE commandId=:commandId LIMIT 1") suspend fun transferByCommand(commandId: String): CustodyTransferEntity?
    @Query("SELECT * FROM custody_transfers WHERE id=:id") suspend fun custodyTransfer(id: Long): CustodyTransferEntity?
    @Query("SELECT * FROM custody_transfer_allocations WHERE transferId=:id ORDER BY sourceIncomeEventId") suspend fun custodyTransferAllocations(id: Long): List<CustodyTransferAllocationEntity>
    @Query("SELECT * FROM custody_transfers WHERE financialYear=:year AND status='POSTED' ORDER BY dateEpochDay DESC, id DESC") fun observeCustodyTransfers(year: Int): Flow<List<CustodyTransferEntity>>
    @Query("SELECT * FROM custody_transfers ORDER BY id") suspend fun allCustodyTransfers(): List<CustodyTransferEntity>
    @Query("SELECT * FROM custody_transfer_allocations ORDER BY transferId, sourceIncomeEventId") suspend fun allCustodyTransferAllocations(): List<CustodyTransferAllocationEntity>

    @Insert suspend fun insertSettlementPayment(entity: SettlementPaymentEntity): Long
    @Update suspend fun updateSettlementPayment(entity: SettlementPaymentEntity)
    @Insert suspend fun insertSettlementClaimAllocations(entities: List<SettlementClaimAllocationEntity>)
    @Insert suspend fun insertSettlementCustodyAllocations(entities: List<SettlementCustodyAllocationEntity>)
    @Query("SELECT * FROM settlement_payments WHERE commandId=:commandId LIMIT 1") suspend fun settlementByCommand(commandId: String): SettlementPaymentEntity?
    @Query("SELECT * FROM settlement_payments WHERE id=:id") suspend fun settlementPayment(id: Long): SettlementPaymentEntity?
    @Query("SELECT * FROM settlement_claim_allocations WHERE paymentId=:id ORDER BY sourceIncomeEventId") suspend fun settlementClaimAllocations(id: Long): List<SettlementClaimAllocationEntity>
    @Query("SELECT * FROM settlement_custody_allocations WHERE paymentId=:id ORDER BY sourceIncomeEventId") suspend fun settlementCustodyAllocations(id: Long): List<SettlementCustodyAllocationEntity>
    @Query("SELECT * FROM settlement_payments WHERE financialYear=:year AND status='POSTED' ORDER BY dateEpochDay DESC, id DESC") fun observeSettlements(year: Int): Flow<List<SettlementPaymentEntity>>
    @Query("SELECT * FROM settlement_payments ORDER BY id") suspend fun allSettlementPayments(): List<SettlementPaymentEntity>
    @Query("SELECT * FROM settlement_claim_allocations ORDER BY paymentId, sourceIncomeEventId") suspend fun allSettlementClaimAllocations(): List<SettlementClaimAllocationEntity>
    @Query("SELECT * FROM settlement_custody_allocations ORDER BY paymentId, sourceIncomeEventId") suspend fun allSettlementCustodyAllocations(): List<SettlementCustodyAllocationEntity>

    @Insert suspend fun insertAnnualClose(entity: AnnualCloseEntity): Long
    @Update suspend fun updateAnnualClose(entity: AnnualCloseEntity)
    @Insert suspend fun insertAnnualCloseSnapshots(entities: List<AnnualCloseEmployeeSnapshotEntity>)
    @Query("SELECT * FROM annual_closes WHERE financialYear=:year AND status='FINAL' ORDER BY revision DESC LIMIT 1") suspend fun latestAnnualClose(year: Int): AnnualCloseEntity?
    @Query("SELECT * FROM annual_closes ORDER BY financialYear, revision") suspend fun allAnnualCloses(): List<AnnualCloseEntity>
    @Query("SELECT * FROM annual_close_employee_snapshots ORDER BY closeId, employeeId") suspend fun allAnnualCloseSnapshots(): List<AnnualCloseEmployeeSnapshotEntity>

    @Insert suspend fun insertAudit(entity: AuditLogEntity): Long
    @Query("SELECT * FROM audit_logs WHERE financialYear=:year OR :year IS NULL ORDER BY createdAt DESC, id DESC") fun observeAudit(year: Int?): Flow<List<AuditLogEntity>>
    @Query("SELECT * FROM audit_logs ORDER BY id") suspend fun allAuditLogs(): List<AuditLogEntity>
    @Query("SELECT COALESCE(MAX(createdAt),0) FROM audit_logs WHERE financialYear=:year") suspend fun latestAuditAt(year: Int): Long
    @Query("""
        SELECT 'C-' || id AS rowId, 'CLAIM' AS category, type AS entryType, amountSigned,
               businessDateEpochDay, referenceId, sourceIncomeEventId, '' AS detail
        FROM claim_postings WHERE employeeId=:employeeId AND financialYear=:year AND type!='ENTITLEMENT'
        UNION ALL
        SELECT 'P-' || id AS rowId, 'CUSTODY' AS category, type AS entryType, amountSigned,
               businessDateEpochDay, referenceId, sourceIncomeEventId, '' AS detail
        FROM custody_postings WHERE employeeId=:employeeId AND financialYear=:year
        UNION ALL
        SELECT 'E-' || s.id AS rowId, 'ELIGIBILITY' AS category, s.status AS entryType, s.shareAmount AS amountSigned,
               i.dateEpochDay AS businessDateEpochDay, i.id AS referenceId, i.id AS sourceIncomeEventId,
               i.type || '|' || s.reasonFa AS detail
        FROM eligibility_snapshots s JOIN income_events i ON i.id=s.eventId
        WHERE s.employeeId=:employeeId AND i.financialYear=:year AND i.status='POSTED'
        ORDER BY businessDateEpochDay DESC, rowId DESC
    """) fun observePersonHistory(employeeId: Long, year: Int): Flow<List<PersonHistoryRow>>

    @Query("DELETE FROM annual_close_employee_snapshots") suspend fun clearAnnualCloseSnapshots()
    @Query("DELETE FROM annual_closes") suspend fun clearAnnualCloses()
    @Query("DELETE FROM settlement_custody_allocations") suspend fun clearSettlementCustodyAllocations()
    @Query("DELETE FROM settlement_claim_allocations") suspend fun clearSettlementClaimAllocations()
    @Query("DELETE FROM settlement_payments") suspend fun clearSettlementPayments()
    @Query("DELETE FROM custody_transfer_allocations") suspend fun clearCustodyTransferAllocations()
    @Query("DELETE FROM custody_transfers") suspend fun clearCustodyTransfers()
    @Query("DELETE FROM custody_postings") suspend fun clearCustodyPostings()
    @Query("DELETE FROM claim_postings") suspend fun clearClaimPostings()
    @Query("DELETE FROM eligibility_snapshots") suspend fun clearEligibilitySnapshots()
    @Query("DELETE FROM income_events") suspend fun clearIncomeEvents()
    @Query("DELETE FROM absence_periods") suspend fun clearAbsences()
    @Query("DELETE FROM employment_periods") suspend fun clearEmploymentPeriods()
    @Query("DELETE FROM money_accounts") suspend fun clearAccounts()
    @Query("DELETE FROM financial_years") suspend fun clearFinancialYears()
    @Query("DELETE FROM audit_logs") suspend fun clearAuditLogs()
    @Query("DELETE FROM employees") suspend fun clearEmployees()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreEmployees(items: List<EmployeeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreEmploymentPeriods(items: List<EmploymentPeriodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAbsences(items: List<AbsencePeriodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAccounts(items: List<MoneyAccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreFinancialYears(items: List<FinancialYearEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreIncomeEvents(items: List<IncomeEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreEligibility(items: List<EligibilitySnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreClaims(items: List<ClaimPostingEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCustody(items: List<CustodyPostingEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTransfers(items: List<CustodyTransferEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTransferAllocations(items: List<CustodyTransferAllocationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSettlements(items: List<SettlementPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSettlementClaims(items: List<SettlementClaimAllocationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSettlementCustody(items: List<SettlementCustodyAllocationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAnnualCloses(items: List<AnnualCloseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAnnualCloseSnapshots(items: List<AnnualCloseEmployeeSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreAuditLogs(items: List<AuditLogEntity>)
}
