package com.hesabyar.garage.data

import androidx.room.withTransaction
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.security.BackupCrypto
import com.hesabyar.garage.util.IranianCardValidator
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class AddEmployeeCommand(
    val name: String,
    val phone: String,
    val role: EmployeeRole,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val notes: String = "",
    val sharedIncomeEligible: Boolean = true,
)

data class UpdateEmployeeCommand(
    val employeeId: Long,
    val name: String,
    val phone: String,
    val role: EmployeeRole,
    val notes: String = "",
    val sharedIncomeEligible: Boolean = true,
)

data class AddCardCommand(
    val employeeId: Long,
    val title: String,
    val ownerName: String,
    val bankName: String,
    val cardNumber: String,
)

data class RegisterIncomeCommand(
    val financialYear: Int,
    val dateEpochDay: Long,
    val amount: Long,
    val type: IncomeType,
    val receiverEmployeeId: Long,
    val receiverAccountId: Long?,
    val description: String,
    val method: MoneyMethod? = null,
    val cardNumber: String = "",
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val commandId: String = UUID.randomUUID().toString(),
)

data class TransferCustodyCommand(
    val financialYear: Int,
    val dateEpochDay: Long,
    val fromAccountId: Long,
    val toAccountId: Long?,
    val amount: Long,
    val note: String,
    val toEmployeeId: Long? = null,
    val method: MoneyMethod? = null,
    val cardNumber: String = "",
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val commandId: String = UUID.randomUUID().toString(),
)

data class RecordSettlementCommand(
    val financialYear: Int,
    val dateEpochDay: Long,
    val fromEmployeeId: Long,
    val fromAccountId: Long,
    val toEmployeeId: Long,
    val destinationAccountId: Long?,
    val amount: Long,
    val method: MoneyMethod,
    val type: SettlementType,
    val note: String,
    val cardNumber: String = "",
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val commandId: String = UUID.randomUUID().toString(),
)

data class CorrectIncomeCommand(
    val originalEventId: Long,
    val dateEpochDay: Long,
    val amount: Long,
    val type: IncomeType,
    val receiverEmployeeId: Long,
    val receiverAccountId: Long? = null,
    val description: String,
    val method: MoneyMethod,
    /** Null keeps the previous encrypted card when the method remains CARD; blank removes it. */
    val cardNumber: String? = null,
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val correctionReason: String,
    val commandId: String = UUID.randomUUID().toString(),
)

data class CorrectTransferCommand(
    val originalTransferId: Long,
    val dateEpochDay: Long,
    val fromAccountId: Long,
    val toAccountId: Long? = null,
    val toEmployeeId: Long? = null,
    val amount: Long,
    val method: MoneyMethod,
    val note: String,
    /** Null keeps the previous encrypted card when the method remains CARD; blank removes it. */
    val cardNumber: String? = null,
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val correctionReason: String,
    val commandId: String = UUID.randomUUID().toString(),
)

data class CorrectSettlementCommand(
    val originalPaymentId: Long,
    val dateEpochDay: Long,
    val fromEmployeeId: Long,
    val fromAccountId: Long,
    val toEmployeeId: Long,
    val destinationAccountId: Long? = null,
    val amount: Long,
    val method: MoneyMethod,
    val type: SettlementType,
    val note: String,
    /** Null keeps the previous encrypted card when the method remains CARD; blank removes it. */
    val cardNumber: String? = null,
    val movementDateEpochDay: Long? = null,
    val movementTimeMinutes: Int? = null,
    val correctionReason: String,
    val commandId: String = UUID.randomUUID().toString(),
)

data class EligibilityPreview(val employee: EmployeeEntity, val result: EligibilityResult)
data class SourceAllocationDetail(val event: IncomeEventEntity, val amount: Long)
data class TransferDetailData(
    val transfer: CustodyTransferEntity,
    val from: EmployeeEntity,
    val to: EmployeeEntity,
    val fromAccount: MoneyAccountEntity,
    val toAccount: MoneyAccountEntity,
    val sources: List<SourceAllocationDetail>,
)
data class SettlementDetailData(
    val payment: SettlementPaymentEntity,
    val from: EmployeeEntity,
    val to: EmployeeEntity,
    val fromAccount: MoneyAccountEntity,
    val destinationAccount: MoneyAccountEntity?,
    val custodySources: List<SourceAllocationDetail>,
    val claimSources: List<SourceAllocationDetail>,
)

class GarageRepository(
    private val database: GarageDatabase,
    private val cardCrypto: CardCrypto,
    private val settingsRepository: SettingsRepository,
) {
    val dao: GarageDao = database.dao()
    private var transactionDeletionAuthority: Any? = null

    @Synchronized
    internal fun bindTransactionDeletionAuthority(authority: Any) {
        check(transactionDeletionAuthority == null) { "مجوز حذف تراکنش قبلاً متصل شده است" }
        transactionDeletionAuthority = authority
    }

    private fun requireTransactionDeletionAuthority(authority: Any) {
        check(authority === transactionDeletionAuthority) { "حذف تراکنش فقط پس از تأیید رمز برنامه مجاز است" }
    }

    fun employees(): Flow<List<EmployeeEntity>> = dao.observeEmployees()
    fun financialYears(): Flow<List<FinancialYearEntity>> = dao.observeFinancialYears()
    fun incomeRows(year: Int): Flow<List<IncomeWithReceiverRow>> = dao.observeIncomeRows(year)
    fun incomeHistoryRows(year: Int): Flow<List<IncomeWithReceiverRow>> = dao.observeIncomeHistoryRows(year)
    fun transfers(year: Int): Flow<List<CustodyTransferEntity>> = dao.observeCustodyTransfers(year)
    fun transferHistory(year: Int): Flow<List<CustodyTransferEntity>> = dao.observeCustodyTransferHistory(year)
    fun settlements(year: Int): Flow<List<SettlementPaymentEntity>> = dao.observeSettlements(year)
    fun settlementHistory(year: Int): Flow<List<SettlementPaymentEntity>> = dao.observeSettlementHistory(year)
    fun totalIncome(year: Int): Flow<Long> = dao.observeTotalIncome(year)
    fun remainder(year: Int): Flow<Long> = dao.observeRemainder(year)
    fun accountCustody(year: Int): Flow<List<AccountCustodyRow>> = dao.observeAccountCustody(year)
    fun activeEmployeeCount(epochDay: Long): Flow<Int> = dao.observeActiveEmployeeCount(epochDay)
    fun balances(year: Int): Flow<List<PersonBalance>> = dao.observePersonBalances(year).map { rows ->
        rows.map {
            PersonBalance(
                it.employeeId,
                it.employeeName,
                it.claimAmount,
                it.custodyAmount,
                it.totalEntitlementAmount,
                it.settledEntitlementAmount,
            )
        }
    }

    fun accounts(employeeId: Long): Flow<List<MoneyAccountEntity>> = dao.observeAccounts(employeeId)
    fun employeeFlow(id: Long): Flow<EmployeeEntity?> = dao.observeEmployee(id)
    fun employmentPeriods(employeeId: Long): Flow<List<EmploymentPeriodEntity>> = dao.observeEmploymentPeriods(employeeId)
    fun absences(employeeId: Long): Flow<List<AbsencePeriodEntity>> = dao.observeAbsences(employeeId)
    fun eligibility(eventId: Long): Flow<List<EligibilitySnapshotEntity>> = dao.observeEligibilitySnapshots(eventId)
    fun personHistory(employeeId: Long, year: Int): Flow<List<PersonHistoryRow>> = dao.observePersonHistory(employeeId, year)

    suspend fun employee(id: Long): EmployeeEntity? = dao.employee(id)
    suspend fun employmentPeriod(id: Long): EmploymentPeriodEntity? = dao.employmentPeriod(id)
    suspend fun absence(id: Long): AbsencePeriodEntity? = dao.absence(id)
    suspend fun accountsNow(employeeId: Long): List<MoneyAccountEntity> = dao.activeAccounts(employeeId)
    suspend fun financialYearNow(year: Int): FinancialYearEntity? = dao.financialYear(year)
    suspend fun incomeEvent(id: Long): IncomeEventEntity? = dao.incomeEvent(id)
    suspend fun account(id: Long): MoneyAccountEntity? = dao.account(id)
    suspend fun correctionAudit(entityType: String, originalId: Long): AuditLogEntity? =
        dao.correctionAudit(entityType, originalId)
    suspend fun deletionAudit(entityType: String, entityId: Long): AuditLogEntity? =
        dao.deletionAudit(entityType, entityId)
    suspend fun transferDetail(id: Long): TransferDetailData? {
        val transfer = dao.custodyTransfer(id) ?: return null
        return TransferDetailData(
            transfer,
            dao.employee(transfer.fromEmployeeId) ?: return null,
            dao.employee(transfer.toEmployeeId) ?: return null,
            dao.account(transfer.fromAccountId) ?: return null,
            dao.account(transfer.toAccountId) ?: return null,
            dao.custodyTransferAllocations(id).mapNotNull { allocation -> dao.incomeEvent(allocation.sourceIncomeEventId)?.let { SourceAllocationDetail(it, allocation.amount) } },
        )
    }
    suspend fun settlementDetail(id: Long): SettlementDetailData? {
        val payment = dao.settlementPayment(id) ?: return null
        return SettlementDetailData(
            payment,
            dao.employee(payment.fromEmployeeId) ?: return null,
            dao.employee(payment.toEmployeeId) ?: return null,
            dao.account(payment.fromAccountId) ?: return null,
            payment.destinationAccountId?.let { dao.account(it) },
            dao.settlementCustodyAllocations(id).mapNotNull { allocation -> dao.incomeEvent(allocation.sourceIncomeEventId)?.let { SourceAllocationDetail(it, allocation.amount) } },
            dao.settlementClaimAllocations(id).mapNotNull { allocation -> dao.incomeEvent(allocation.sourceIncomeEventId)?.let { SourceAllocationDetail(it, allocation.amount) } },
        )
    }

    suspend fun previewEligibility(dateEpochDay: Long): List<EligibilityPreview> {
        val graceDays = settingsRepository.settings.first().absenceGraceDays
        return dao.allEmployees().map { employee ->
            EligibilityPreview(
                employee,
                evaluateSharedIncomeEligibility(employee, dateEpochDay, graceDays),
            )
        }
    }

    private suspend fun evaluateSharedIncomeEligibility(
        employee: EmployeeEntity,
        dateEpochDay: Long,
        graceDays: Int,
    ): EligibilityResult {
        if (!employee.sharedIncomeEligible) {
            return EligibilityResult(
                status = EligibilityStatus.NOT_ELIGIBLE,
                reasonCode = "SHARED_INCOME_DISABLED",
                reasonFa = "سهم‌گیری برای ثبت‌های جدید خاموش است",
            )
        }
        return EligibilityEngine.evaluate(
            dateEpochDay,
            dao.employmentPeriods(employee.id).map { EmploymentRange(it.id, it.startEpochDay, it.endEpochDay) },
            dao.absences(employee.id).map { AbsenceRange(it.id, it.startEpochDay, it.returnEpochDay) },
            graceDays,
        )
    }

    suspend fun ensureYear(year: Int): FinancialYearEntity {
        require(year in 1300..1600) { "سال مالی معتبر نیست" }
        val existing = dao.financialYear(year)
        if (existing != null) return existing
        val entity = FinancialYearEntity(
            year = year,
            startEpochDay = PersianDate(year, 1, 1).toEpochDay(),
            endEpochDay = PersianDate(year, 12, PersianDate.monthLength(year, 12)).toEpochDay(),
        )
        dao.insertFinancialYear(entity)
        return dao.financialYear(year) ?: entity
    }

    suspend fun addEmployee(command: AddEmployeeCommand): Long = database.withTransaction {
        require(command.name.trim().length >= 2) { "نام پرسنل را کامل وارد کنید" }
        require(command.endEpochDay == null || command.endEpochDay >= command.startEpochDay) { "پایان همکاری قبل از شروع است" }
        val employeeId = dao.insertEmployee(
            EmployeeEntity(
                name = command.name.trim(),
                phone = command.phone.trim(),
                role = command.role,
                notes = command.notes.trim(),
                sharedIncomeEligible = command.sharedIncomeEligible,
            ),
        )
        dao.insertEmploymentPeriod(
            EmploymentPeriodEntity(employeeId = employeeId, startEpochDay = command.startEpochDay, endEpochDay = command.endEpochDay),
        )
        dao.insertAccount(
            MoneyAccountEntity(
                employeeId = employeeId,
                type = AccountType.CASH,
                title = "پول نقد ${command.name.trim()}",
                ownerName = command.name.trim(),
            ),
        )
        dao.insertAudit(AuditLogEntity(action = "CREATE", entityType = "EMPLOYEE", entityId = employeeId, description = "افزودن ${command.name.trim()}"))
        employeeId
    }

    suspend fun updateEmployee(command: UpdateEmployeeCommand) = database.withTransaction {
        val employee = dao.employee(command.employeeId) ?: error("پرسنل پیدا نشد")
        val name = command.name.trim()
        require(name.length >= 2) { "نام پرسنل را کامل وارد کنید" }
        dao.updateEmployee(
            employee.copy(
                name = name,
                phone = command.phone.trim(),
                role = command.role,
                notes = command.notes.trim(),
                sharedIncomeEligible = command.sharedIncomeEligible,
            ),
        )
        dao.activeAccounts(employee.id)
            .forEach { account ->
                val renamedGeneratedTitle = when {
                    account.type == AccountType.CASH && account.title == "پول نقد ${employee.name}" ->
                        "پول نقد $name"
                    account.type == AccountType.CARD && account.cardNumberEncrypted.isBlank() &&
                        account.title == "کارت‌به‌کارت ${employee.name}" ->
                        "کارت‌به‌کارت $name"
                    account.type == AccountType.POS && account.title == "دستگاه پوز ${employee.name}" ->
                        "دستگاه پوز $name"
                    else -> null
                } ?: return@forEach
                dao.updateAccount(
                    account.copy(
                        title = renamedGeneratedTitle,
                        ownerName = if (account.ownerName == employee.name) name else account.ownerName,
                    ),
                )
            }
        val eligibilityChange = if (employee.sharedIncomeEligible == command.sharedIncomeEligible) "" else if (command.sharedIncomeEligible) " و فعال‌کردن سهم درآمد مشترک" else " و غیرفعال‌کردن سهم درآمد مشترک"
        dao.insertAudit(AuditLogEntity(action = "UPDATE", entityType = "EMPLOYEE", entityId = employee.id, description = "ویرایش مشخصات $name$eligibilityChange"))
    }

    suspend fun addEmploymentPeriod(employeeId: Long, startEpochDay: Long, endEpochDay: Long?): Long = database.withTransaction {
        require(dao.employee(employeeId) != null) { "پرسنل پیدا نشد" }
        require(endEpochDay == null || endEpochDay >= startEpochDay) { "پایان همکاری قبل از شروع است" }
        val existing = dao.employmentPeriods(employeeId)
        require(existing.none { EligibilityEngine.rangesOverlap(startEpochDay, endEpochDay, it.startEpochDay, it.endEpochDay) }) {
            "این دوره با یکی از دوره‌های همکاری قبلی هم‌پوشانی دارد"
        }
        val id = dao.insertEmploymentPeriod(EmploymentPeriodEntity(employeeId = employeeId, startEpochDay = startEpochDay, endEpochDay = endEpochDay))
        dao.insertAudit(AuditLogEntity(action = "CREATE", entityType = "EMPLOYMENT", entityId = id, description = "ثبت دوره همکاری"))
        id
    }

    suspend fun endEmployment(employeeId: Long, lastWorkingDay: Long) = database.withTransaction {
        val period = dao.openEmploymentPeriod(employeeId) ?: error("دوره همکاری بازی پیدا نشد")
        require(lastWorkingDay >= period.startEpochDay) { "آخرین روز کاری قبل از شروع همکاری است" }
        dao.updateEmploymentPeriod(period.copy(endEpochDay = lastWorkingDay))
        dao.insertAudit(AuditLogEntity(action = "END", entityType = "EMPLOYMENT", entityId = period.id, description = "ثبت پایان همکاری"))
    }

    suspend fun updateEmploymentPeriod(periodId: Long, employeeId: Long, startEpochDay: Long, endEpochDay: Long?) = database.withTransaction {
        val period = dao.employmentPeriod(periodId) ?: error("دوره همکاری پیدا نشد")
        require(period.employeeId == employeeId) { "دوره همکاری متعلق به این پرسنل نیست" }
        require(endEpochDay == null || endEpochDay >= startEpochDay) { "پایان همکاری قبل از شروع است" }
        val updatedPeriod = period.copy(startEpochDay = startEpochDay, endEpochDay = endEpochDay)
        val resultingPeriods = dao.employmentPeriods(employeeId).map { existing ->
            if (existing.id == periodId) updatedPeriod else existing
        }
        require(
            resultingPeriods.none { existing ->
                existing.id != periodId && EligibilityEngine.rangesOverlap(startEpochDay, endEpochDay, existing.startEpochDay, existing.endEpochDay)
            },
        ) { "این دوره با یکی از دوره‌های همکاری دیگر هم‌پوشانی دارد" }
        require(
            dao.absences(employeeId).all { absence ->
                resultingPeriods.any { employment ->
                    absence.startEpochDay >= employment.startEpochDay &&
                        (employment.endEpochDay == null || absence.startEpochDay <= employment.endEpochDay) &&
                        when (val returned = absence.returnEpochDay) {
                            null -> employment.endEpochDay == null
                            else -> employment.endEpochDay == null || returned - 1 <= employment.endEpochDay
                        }
                }
            },
        ) { "با این تغییر، یکی از غیبت‌ها بیرون دوره همکاری قرار می‌گیرد" }
        dao.updateEmploymentPeriod(updatedPeriod)
        dao.insertAudit(AuditLogEntity(action = "UPDATE", entityType = "EMPLOYMENT", entityId = period.id, description = "ویرایش دوره همکاری"))
    }

    suspend fun addAbsence(employeeId: Long, startEpochDay: Long, returnEpochDay: Long?, note: String): Long = database.withTransaction {
        require(returnEpochDay == null || returnEpochDay > startEpochDay) { "روز بازگشت باید بعد از اولین روز غیبت باشد" }
        val employment = dao.employmentPeriods(employeeId).firstOrNull { period ->
            startEpochDay >= period.startEpochDay &&
                (period.endEpochDay == null || startEpochDay <= period.endEpochDay) &&
                (returnEpochDay == null || period.endEpochDay == null || returnEpochDay - 1 <= period.endEpochDay)
        }
        require(employment != null) { "غیبت باید داخل یک دوره همکاری ثبت شود" }

        val overlapping = dao.absences(employeeId).filter { existing ->
            val existingLastAbsent = existing.returnEpochDay?.minus(1)
            val newLastAbsent = returnEpochDay?.minus(1)
            EligibilityEngine.rangesOverlap(startEpochDay, newLastAbsent, existing.startEpochDay, existingLastAbsent)
        }
        if (overlapping.isNotEmpty()) {
            val first = overlapping.minBy { it.startEpochDay }
            val mergedStart = minOf(startEpochDay, overlapping.minOf { it.startEpochDay })
            val allReturns = overlapping.map { it.returnEpochDay } + returnEpochDay
            val mergedReturn = if (allReturns.any { it == null }) null else allReturns.filterNotNull().max()
            dao.updateAbsence(first.copy(startEpochDay = mergedStart, returnEpochDay = mergedReturn, note = note.trim()))
            overlapping.filter { it.id != first.id }.forEach { dao.updateAbsence(it.copy(voidedAt = System.currentTimeMillis())) }
            dao.insertAudit(AuditLogEntity(action = "MERGE", entityType = "ABSENCE", entityId = first.id, description = "ادغام بازه‌های غیبت"))
            first.id
        } else {
            val id = dao.insertAbsence(AbsencePeriodEntity(employeeId = employeeId, startEpochDay = startEpochDay, returnEpochDay = returnEpochDay, note = note.trim()))
            dao.insertAudit(AuditLogEntity(action = "CREATE", entityType = "ABSENCE", entityId = id, description = "ثبت غیبت موقت"))
            id
        }
    }

    suspend fun updateAbsence(absenceId: Long, employeeId: Long, startEpochDay: Long, returnEpochDay: Long?, note: String) = database.withTransaction {
        val absence = dao.absence(absenceId) ?: error("غیبت موقت پیدا نشد")
        require(absence.employeeId == employeeId) { "غیبت متعلق به این پرسنل نیست" }
        require(returnEpochDay == null || returnEpochDay > startEpochDay) { "روز بازگشت باید بعد از اولین روز غیبت باشد" }
        val employment = dao.employmentPeriods(employeeId).firstOrNull { period ->
            startEpochDay >= period.startEpochDay &&
                (period.endEpochDay == null || startEpochDay <= period.endEpochDay) &&
                (returnEpochDay == null || period.endEpochDay == null || returnEpochDay - 1 <= period.endEpochDay)
        }
        require(employment != null) { "غیبت باید داخل یک دوره همکاری ثبت شود" }
        val newLastAbsent = returnEpochDay?.minus(1)
        require(
            dao.absences(employeeId).none { existing ->
                existing.id != absenceId && EligibilityEngine.rangesOverlap(
                    startEpochDay,
                    newLastAbsent,
                    existing.startEpochDay,
                    existing.returnEpochDay?.minus(1),
                )
            },
        ) { "این بازه با یکی از غیبت‌های دیگر هم‌پوشانی دارد" }
        dao.updateAbsence(absence.copy(startEpochDay = startEpochDay, returnEpochDay = returnEpochDay, note = note.trim()))
        dao.insertAudit(AuditLogEntity(action = "UPDATE", entityType = "ABSENCE", entityId = absence.id, description = if (returnEpochDay == null) "ویرایش غیبت باز" else "ثبت یا ویرایش بازگشت از غیبت"))
    }

    suspend fun addCard(command: AddCardCommand): Long = database.withTransaction {
        val normalized = IranianCardValidator.normalize(command.cardNumber)
        require(IranianCardValidator.isValid(normalized)) { "شماره کارت معتبر نیست" }
        require(command.ownerName.trim().isNotEmpty()) { "نام صاحب کارت لازم است" }
        val fingerprint = cardCrypto.fingerprint(normalized)
        require(dao.accountByFingerprint(fingerprint) == null) { "این شماره کارت قبلاً ثبت شده است" }
        val employee = dao.employee(command.employeeId) ?: error("پرسنل پیدا نشد")
        val id = dao.insertAccount(
            MoneyAccountEntity(
                employeeId = command.employeeId,
                type = AccountType.CARD,
                title = command.title.trim().ifEmpty { "کارت ${employee.name}" },
                ownerName = command.ownerName.trim(),
                bankName = command.bankName.trim(),
                cardNumberEncrypted = cardCrypto.encrypt(normalized),
                cardLast4 = normalized.takeLast(4),
                cardFingerprint = fingerprint,
            ),
        )
        dao.insertAudit(AuditLogEntity(action = "CREATE", entityType = "CARD", entityId = id, description = "افزودن کارت با پایان ${normalized.takeLast(4)}"))
        id
    }

    suspend fun revealCard(accountId: Long): String {
        val account = dao.account(accountId) ?: error("حساب پیدا نشد")
        require(account.type == AccountType.CARD) { "این حساب کارت بانکی نیست" }
        return cardCrypto.decrypt(account.cardNumberEncrypted)
    }

    suspend fun revealMovementCard(encryptedValue: String): String {
        require(encryptedValue.isNotBlank()) { "شماره کارتی برای این تراکنش ثبت نشده است" }
        return cardCrypto.decrypt(encryptedValue)
    }

    suspend fun registerIncome(command: RegisterIncomeCommand): Long = database.withTransaction {
        registerIncomeInTransaction(command)
    }

    private suspend fun registerIncomeInTransaction(
        command: RegisterIncomeCommand,
        replacesEventId: Long? = null,
        movementOverride: EncodedMovementDetails? = null,
    ): Long {
        dao.incomeByCommand(command.commandId)?.let { return it.id }
        require(command.amount > 0) { "مبلغ باید بیشتر از صفر باشد" }
        require(command.dateEpochDay <= LocalDate.now().toEpochDay()) { "ثبت درآمد برای آینده مجاز نیست" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val receiver = dao.employee(command.receiverEmployeeId) ?: error("دریافت‌کننده پیدا نشد")
        val requestedAccount = command.receiverAccountId?.let { dao.account(it) ?: error("حساب دریافت‌کننده پیدا نشد") }
        requestedAccount?.let { require(it.employeeId == receiver.id) { "حساب انتخابی متعلق به دریافت‌کننده نیست" } }
        val method = command.method ?: requestedAccount?.type.toMoneyMethod()
        requestedAccount?.let { require(it.type.toMoneyMethod() == method) { "نوع حساب دریافت با روش جابه‌جایی هماهنگ نیست" } }
        val receiverAccount = requestedAccount ?: movementAccount(receiver, method)
        val movement = movementOverride ?: encodeMovementDetails(
            method,
            command.cardNumber,
            command.movementDateEpochDay,
            command.movementTimeMinutes,
        )

        val graceDays = settingsRepository.settings.first().absenceGraceDays
        val evaluations = dao.allEmployees().associateWith { employee ->
            evaluateSharedIncomeEligibility(employee, command.dateEpochDay, graceDays)
        }
        val eligible = evaluations.filterValues { it.isEligible }
        require(eligible.isNotEmpty()) { "برای این ثبت هیچ فردی سهم ندارد؛ سهم حداقل یک پرسنل را روشن یا وضعیت حضور او را اصلاح کنید" }
        val carried = dao.remainder(year.year)
        val distribution = AccountingEngine.distribute(command.amount, carried, eligible.size)
        val eventId = dao.insertIncomeEvent(
            IncomeEventEntity(
                commandId = command.commandId,
                financialYear = year.year,
                dateEpochDay = command.dateEpochDay,
                amount = command.amount,
                type = command.type,
                receiverEmployeeId = receiver.id,
                receiverAccountId = receiverAccount.id,
                method = method,
                description = command.description.trim(),
                carriedRemainder = carried,
                sharePerPerson = distribution.sharePerPerson,
                resultingRemainder = distribution.resultingRemainder,
                cardNumberEncrypted = movement.cardNumberEncrypted,
                cardLast4 = movement.cardLast4,
                movementDateEpochDay = movement.dateEpochDay,
                movementTimeMinutes = movement.timeMinutes,
                replacesEventId = replacesEventId,
            ),
        )
        dao.insertEligibilitySnapshots(
            evaluations.map { (employee, result) ->
                EligibilitySnapshotEntity(
                    eventId = eventId,
                    employeeId = employee.id,
                    status = result.status,
                    reasonCode = result.reasonCode,
                    reasonFa = result.reasonFa,
                    employmentPeriodId = result.employmentPeriodId,
                    absencePeriodId = result.absencePeriodId,
                    absenceDay = result.absenceDay,
                    shareAmount = if (result.isEligible) distribution.sharePerPerson else 0,
                )
            },
        )
        val claimPostings = buildList {
            if (carried > 0) add(
                ClaimPostingEntity(financialYear = year.year, sourceIncomeEventId = eventId, employeeId = null, amountSigned = -carried, type = ClaimPostingType.REMAINDER_RELEASE, referenceId = eventId, businessDateEpochDay = command.dateEpochDay),
            )
            eligible.keys.forEach { employee ->
                if (distribution.sharePerPerson > 0) add(
                    ClaimPostingEntity(financialYear = year.year, sourceIncomeEventId = eventId, employeeId = employee.id, amountSigned = distribution.sharePerPerson, type = ClaimPostingType.ENTITLEMENT, referenceId = eventId, businessDateEpochDay = command.dateEpochDay),
                )
            }
            if (distribution.resultingRemainder > 0) add(
                ClaimPostingEntity(financialYear = year.year, sourceIncomeEventId = eventId, employeeId = null, amountSigned = distribution.resultingRemainder, type = ClaimPostingType.REMAINDER_ADD, referenceId = eventId, businessDateEpochDay = command.dateEpochDay),
            )
        }
        if (claimPostings.isNotEmpty()) dao.insertClaimPostings(claimPostings)
        dao.insertCustodyPostings(
            listOf(
                CustodyPostingEntity(
                    financialYear = year.year,
                    sourceIncomeEventId = eventId,
                    employeeId = receiver.id,
                    accountId = receiverAccount.id,
                    amountSigned = command.amount,
                    type = CustodyPostingType.INCOME_RECEIPT,
                    referenceId = eventId,
                    businessDateEpochDay = command.dateEpochDay,
                ),
            ),
        )
        dao.insertAudit(AuditLogEntity(financialYear = year.year, action = "POST", entityType = "INCOME", entityId = eventId, description = "ثبت درآمد و سهم ${eligible.size} نفر"))
        return eventId
    }

    suspend fun correctIncome(command: CorrectIncomeCommand): Long = database.withTransaction {
        dao.incomeByCommand(command.commandId)?.let { return@withTransaction it.id }
        val original = dao.incomeEvent(command.originalEventId) ?: error("درآمد اصلی پیدا نشد")
        require(original.status == RecordStatus.POSTED) { "این نسخه قبلاً اصلاح شده است" }
        require(command.correctionReason.trim().length >= 3) { "دلیل اصلاح را کوتاه و روشن بنویسید" }
        requireOpenYear(original.financialYear, command.dateEpochDay)
        val activeSequence = activeIncomeSequence(original.financialYear)
        val originalIndex = activeSequence.indexOfFirst { it.id == original.id }
        require(originalIndex >= 0) { "درآمد فعال در زنجیره باقیمانده پیدا نشد" }
        val affected = activeSequence.drop(originalIndex)
        require(affected.all { dao.activeAllocationCountForIncome(it.id) == 0 }) {
            "این درآمد یا یکی از درآمدهای بعدی منشأ انتقال یا تسویه فعال است؛ ابتدا تراکنش وابسته را حذف کنید یا طوری اصلاح کنید که دیگر از این درآمد مصرف نکند"
        }
        val frozenSuccessors = affected.drop(1).map { it to dao.eligibilitySnapshots(it.id) }
        val reversedAt = System.currentTimeMillis()
        affected.asReversed().forEach { event ->
            reverseIncomeEvent(event, RecordStatus.REVERSED, reversedAt)
        }

        val movement = correctedMovementDetails(
            previousMethod = original.method,
            previousEncryptedCard = original.cardNumberEncrypted,
            previousCardLast4 = original.cardLast4,
            method = command.method,
            cardNumberUpdate = command.cardNumber,
            dateEpochDay = command.movementDateEpochDay,
            timeMinutes = command.movementTimeMinutes,
        )
        val replacementId = registerIncomeInTransaction(
            RegisterIncomeCommand(
                financialYear = original.financialYear,
                dateEpochDay = command.dateEpochDay,
                amount = command.amount,
                type = command.type,
                receiverEmployeeId = command.receiverEmployeeId,
                receiverAccountId = command.receiverAccountId,
                description = command.description,
                method = command.method,
                movementDateEpochDay = command.movementDateEpochDay,
                movementTimeMinutes = command.movementTimeMinutes,
                commandId = command.commandId,
            ),
            replacesEventId = original.id,
            movementOverride = movement,
        )
        frozenSuccessors.forEach { (successor, snapshots) ->
            val replayedId = replayIncomeEvent(successor, snapshots)
            dao.insertAudit(
                AuditLogEntity(
                    financialYear = successor.financialYear,
                    action = "CORRECT",
                    entityType = "INCOME",
                    entityId = successor.id,
                    description = "بازمحاسبه خودکار درآمد با نسخه $replayedId: هماهنگ‌سازی سهم و باقیمانده پس از اصلاح درآمد قبلی",
                ),
            )
        }
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "CORRECT",
                entityType = "INCOME",
                entityId = original.id,
                description = "اصلاح درآمد با نسخه $replacementId: ${command.correctionReason.trim()}",
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
        replacementId
    }

    internal suspend fun deleteIncomeAfterAuthorization(
        eventId: Long,
        reason: String,
        authority: Any,
    ) = database.withTransaction {
        requireTransactionDeletionAuthority(authority)
        val original = dao.incomeEvent(eventId) ?: error("درآمد پیدا نشد")
        if (original.status == RecordStatus.DELETED) return@withTransaction
        require(original.status == RecordStatus.POSTED) { "فقط نسخه فعال تراکنش قابل حذف است" }
        val normalizedReason = requireDeletionReason(reason)
        requireOpenYear(original.financialYear, original.dateEpochDay)
        val activeSequence = activeIncomeSequence(original.financialYear)
        val originalIndex = activeSequence.indexOfFirst { it.id == original.id }
        require(originalIndex >= 0) { "درآمد فعال در زنجیره باقیمانده پیدا نشد" }
        val affected = activeSequence.drop(originalIndex)
        require(affected.all { dao.activeAllocationCountForIncome(it.id) == 0 }) {
            "این درآمد یا یکی از درآمدهای بعدی منشأ انتقال یا تسویه فعال است؛ ابتدا تراکنش وابسته را حذف کنید یا طوری اصلاح کنید که دیگر از این درآمد مصرف نکند"
        }
        val frozenSuccessors = affected.drop(1).map { it to dao.eligibilitySnapshots(it.id) }
        val deletedAt = System.currentTimeMillis()
        affected.asReversed().forEach { event ->
            reverseIncomeEvent(
                event = event,
                status = if (event.id == original.id) RecordStatus.DELETED else RecordStatus.REVERSED,
                reversedAt = deletedAt,
            )
        }
        frozenSuccessors.forEach { (successor, snapshots) ->
            val replayedId = replayIncomeEvent(successor, snapshots)
            dao.insertAudit(
                AuditLogEntity(
                    financialYear = successor.financialYear,
                    action = "CORRECT",
                    entityType = "INCOME",
                    entityId = successor.id,
                    description = "بازمحاسبه خودکار درآمد با نسخه $replayedId: هماهنگ‌سازی سهم و باقیمانده پس از حذف درآمد قبلی",
                ),
            )
        }
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "DELETE",
                entityType = "INCOME",
                entityId = original.id,
                description = "حذف درآمد از محاسبات: $normalizedReason",
                createdAt = deletedAt,
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
    }

    private suspend fun activeIncomeSequence(year: Int): List<IncomeEventEntity> {
        val events = dao.incomeEventsForYear(year)
        val byId = events.associateBy(IncomeEventEntity::id)
        fun rootId(event: IncomeEventEntity): Long {
            var current = event
            val visited = mutableSetOf<Long>()
            while (true) {
                require(visited.add(current.id)) { "زنجیره نسخه‌های درآمد نامعتبر است" }
                val previousId = current.replacesEventId ?: return current.id
                val previous = byId[previousId] ?: error("نسخه قبلی درآمد پیدا نشد")
                require(previous.financialYear == year) { "نسخه‌های یک درآمد باید در یک سال مالی باشند" }
                current = previous
            }
        }
        return events
            .filter { it.status == RecordStatus.POSTED }
            .sortedWith(compareBy<IncomeEventEntity>({ rootId(it) }, { it.id }))
    }

    private suspend fun reverseIncomeEvent(
        event: IncomeEventEntity,
        status: RecordStatus,
        reversedAt: Long,
    ) {
        require(event.status == RecordStatus.POSTED) { "فقط درآمد فعال قابل خنثی‌سازی است" }
        require(status != RecordStatus.POSTED) { "وضعیت خنثی‌سازی درآمد معتبر نیست" }
        val claims = dao.incomeClaimPostings(event.financialYear, event.id)
        val custody = dao.incomeCustodyPostings(event.financialYear, event.id)
        require(claims.fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) } == event.amount) {
            "دفتر سهم درآمد اصلی کامل نیست؛ عملیات لغو شد"
        }
        require(custody.fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) } == event.amount) {
            "دفتر نگهداری درآمد اصلی کامل نیست؛ عملیات لغو شد"
        }
        dao.updateIncomeEvent(event.copy(status = status, reversedAt = reversedAt))
        if (claims.isNotEmpty()) {
            dao.insertClaimPostings(
                claims.map { posting ->
                    posting.copy(id = 0, amountSigned = Math.negateExact(posting.amountSigned), createdAt = reversedAt)
                },
            )
        }
        if (custody.isNotEmpty()) {
            dao.insertCustodyPostings(
                custody.map { posting ->
                    posting.copy(id = 0, amountSigned = Math.negateExact(posting.amountSigned), createdAt = reversedAt)
                },
            )
        }
    }

    private suspend fun replayIncomeEvent(
        previous: IncomeEventEntity,
        frozenSnapshots: List<EligibilitySnapshotEntity>,
    ): Long {
        require(previous.status == RecordStatus.POSTED) { "نسخه درآمد برای بازپخش معتبر نیست" }
        val eligibleCount = frozenSnapshots.count { it.status == EligibilityStatus.ELIGIBLE }
        require(eligibleCount > 0) { "نسخه درآمد برای بازپخش، واجد سهم معتبری ندارد" }
        val carried = dao.remainder(previous.financialYear)
        val distribution = AccountingEngine.distribute(previous.amount, carried, eligibleCount)
        val createdAt = System.currentTimeMillis()
        val eventId = dao.insertIncomeEvent(
            previous.copy(
                id = 0,
                commandId = UUID.randomUUID().toString(),
                carriedRemainder = carried,
                sharePerPerson = distribution.sharePerPerson,
                resultingRemainder = distribution.resultingRemainder,
                status = RecordStatus.POSTED,
                createdAt = createdAt,
                reversedAt = null,
                replacesEventId = previous.id,
            ),
        )
        dao.insertEligibilitySnapshots(
            frozenSnapshots.map { snapshot ->
                snapshot.copy(
                    id = 0,
                    eventId = eventId,
                    shareAmount = if (snapshot.status == EligibilityStatus.ELIGIBLE) distribution.sharePerPerson else 0L,
                )
            },
        )
        val claimPostings = buildList {
            if (carried > 0L) {
                add(
                    ClaimPostingEntity(
                        financialYear = previous.financialYear,
                        sourceIncomeEventId = eventId,
                        employeeId = null,
                        amountSigned = -carried,
                        type = ClaimPostingType.REMAINDER_RELEASE,
                        referenceId = eventId,
                        businessDateEpochDay = previous.dateEpochDay,
                        createdAt = createdAt,
                    ),
                )
            }
            frozenSnapshots
                .filter { it.status == EligibilityStatus.ELIGIBLE }
                .forEach { snapshot ->
                    if (distribution.sharePerPerson > 0L) {
                        add(
                            ClaimPostingEntity(
                                financialYear = previous.financialYear,
                                sourceIncomeEventId = eventId,
                                employeeId = snapshot.employeeId,
                                amountSigned = distribution.sharePerPerson,
                                type = ClaimPostingType.ENTITLEMENT,
                                referenceId = eventId,
                                businessDateEpochDay = previous.dateEpochDay,
                                createdAt = createdAt,
                            ),
                        )
                    }
                }
            if (distribution.resultingRemainder > 0L) {
                add(
                    ClaimPostingEntity(
                        financialYear = previous.financialYear,
                        sourceIncomeEventId = eventId,
                        employeeId = null,
                        amountSigned = distribution.resultingRemainder,
                        type = ClaimPostingType.REMAINDER_ADD,
                        referenceId = eventId,
                        businessDateEpochDay = previous.dateEpochDay,
                        createdAt = createdAt,
                    ),
                )
            }
        }
        if (claimPostings.isNotEmpty()) dao.insertClaimPostings(claimPostings)
        dao.insertCustodyPostings(
            listOf(
                CustodyPostingEntity(
                    financialYear = previous.financialYear,
                    sourceIncomeEventId = eventId,
                    employeeId = previous.receiverEmployeeId,
                    accountId = previous.receiverAccountId,
                    amountSigned = previous.amount,
                    type = CustodyPostingType.INCOME_RECEIPT,
                    referenceId = eventId,
                    businessDateEpochDay = previous.dateEpochDay,
                    createdAt = createdAt,
                ),
            ),
        )
        dao.insertAudit(
            AuditLogEntity(
                financialYear = previous.financialYear,
                action = "POST",
                entityType = "INCOME",
                entityId = eventId,
                description = "بازثبت خودکار درآمد با حفظ وضعیت واجدان سهم",
                createdAt = createdAt,
            ),
        )
        return eventId
    }

    private fun requireDeletionReason(reason: String): String = reason.trim().also {
        require(it.length >= 3) { "دلیل حذف را کوتاه و روشن بنویسید" }
    }

    suspend fun transferCustody(command: TransferCustodyCommand): Long = database.withTransaction {
        transferCustodyInTransaction(command)
    }

    private suspend fun transferCustodyInTransaction(
        command: TransferCustodyCommand,
        replacesTransferId: Long? = null,
        movementOverride: EncodedMovementDetails? = null,
    ): Long {
        dao.transferByCommand(command.commandId)?.let { return it.id }
        require(command.amount > 0) { "مبلغ انتقال باید بیشتر از صفر باشد" }
        require(command.dateEpochDay <= LocalDate.now().toEpochDay()) { "ثبت انتقال برای آینده مجاز نیست" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val from = dao.account(command.fromAccountId) ?: error("حساب مبدأ پیدا نشد")
        val requestedDestination = command.toAccountId?.let { dao.account(it) ?: error("حساب مقصد پیدا نشد") }
        val destinationEmployee = requestedDestination?.let { dao.employee(it.employeeId) }
            ?: command.toEmployeeId?.let { dao.employee(it) }
            ?: error("گیرنده پول مشترک پیدا نشد")
        val method = command.method ?: requestedDestination?.type.toMoneyMethod()
        require(method != MoneyMethod.POS) { "روش پوز فقط برای دریافت انعام یا فروش ضایعات است" }
        requestedDestination?.let { require(it.type.toMoneyMethod() == method) { "نوع حساب مقصد با روش انتقال هماهنگ نیست" } }
        val to = requestedDestination ?: movementAccount(destinationEmployee, method)
        require(from.employeeId != destinationEmployee.id) { "انتقال پول مشترک باید بین دو شخص متفاوت باشد" }
        require(from.id != to.id) { "حساب مبدأ و مقصد یکسان است" }
        require(from.archivedAt == null && to.archivedAt == null) { "حساب غیرفعال قابل استفاده نیست" }
        val movement = movementOverride ?: encodeMovementDetails(
            method,
            command.cardNumber,
            command.movementDateEpochDay,
            command.movementTimeMinutes,
        )
        val available = dao.accountCustody(year.year, from.id)
        require(command.amount <= available) { "مبلغ انتقال از پول مشترک موجود بیشتر است" }
        val sources = allocateCustodySources(year.year, from.id, command.amount)
        require(sources.all { (sourceId, _) ->
            (dao.incomeEvent(sourceId)?.dateEpochDay ?: Long.MAX_VALUE) <= command.dateEpochDay
        }) { "تاریخ انتقال نمی‌تواند پیش از تاریخ درآمدِ منبع پول باشد" }
        val transferId = dao.insertCustodyTransfer(
            CustodyTransferEntity(
                commandId = command.commandId,
                financialYear = year.year,
                dateEpochDay = command.dateEpochDay,
                fromEmployeeId = from.employeeId,
                fromAccountId = from.id,
                toEmployeeId = to.employeeId,
                toAccountId = to.id,
                amount = command.amount,
                method = method,
                note = command.note.trim(),
                cardNumberEncrypted = movement.cardNumberEncrypted,
                cardLast4 = movement.cardLast4,
                movementDateEpochDay = movement.dateEpochDay,
                movementTimeMinutes = movement.timeMinutes,
                replacesTransferId = replacesTransferId,
            ),
        )
        dao.insertCustodyTransferAllocations(sources.map { CustodyTransferAllocationEntity(transferId, it.first, it.second) })
        dao.insertCustodyPostings(
            sources.flatMap { (sourceId, amount) ->
                listOf(
                    CustodyPostingEntity(financialYear = year.year, sourceIncomeEventId = sourceId, employeeId = from.employeeId, accountId = from.id, amountSigned = -amount, type = CustodyPostingType.TRANSFER_OUT, referenceId = transferId, businessDateEpochDay = command.dateEpochDay),
                    CustodyPostingEntity(financialYear = year.year, sourceIncomeEventId = sourceId, employeeId = to.employeeId, accountId = to.id, amountSigned = amount, type = CustodyPostingType.TRANSFER_IN, referenceId = transferId, businessDateEpochDay = command.dateEpochDay),
                )
            },
        )
        dao.insertAudit(AuditLogEntity(financialYear = year.year, action = "POST", entityType = "CUSTODY_TRANSFER", entityId = transferId, description = "انتقال جزئی پول مشترک"))
        return transferId
    }

    suspend fun correctTransfer(command: CorrectTransferCommand): Long = database.withTransaction {
        dao.transferByCommand(command.commandId)?.let { return@withTransaction it.id }
        val original = dao.custodyTransfer(command.originalTransferId) ?: error("انتقال اصلی پیدا نشد")
        require(original.status == RecordStatus.POSTED) { "این نسخه قبلاً اصلاح شده است" }
        require(command.correctionReason.trim().length >= 3) { "دلیل اصلاح را کوتاه و روشن بنویسید" }
        requireOpenYear(original.financialYear, command.dateEpochDay)
        val reversedAt = System.currentTimeMillis()
        reverseTransfer(original, RecordStatus.REVERSED, reversedAt)
        val movement = correctedMovementDetails(
            previousMethod = original.method,
            previousEncryptedCard = original.cardNumberEncrypted,
            previousCardLast4 = original.cardLast4,
            method = command.method,
            cardNumberUpdate = command.cardNumber,
            dateEpochDay = command.movementDateEpochDay,
            timeMinutes = command.movementTimeMinutes,
        )
        val replacementId = transferCustodyInTransaction(
            TransferCustodyCommand(
                financialYear = original.financialYear,
                dateEpochDay = command.dateEpochDay,
                fromAccountId = command.fromAccountId,
                toAccountId = command.toAccountId,
                amount = command.amount,
                note = command.note,
                toEmployeeId = command.toEmployeeId,
                method = command.method,
                movementDateEpochDay = command.movementDateEpochDay,
                movementTimeMinutes = command.movementTimeMinutes,
                commandId = command.commandId,
            ),
            replacesTransferId = original.id,
            movementOverride = movement,
        )
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "CORRECT",
                entityType = "CUSTODY_TRANSFER",
                entityId = original.id,
                description = "اصلاح انتقال با نسخه $replacementId: ${command.correctionReason.trim()}",
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
        replacementId
    }

    internal suspend fun deleteTransferAfterAuthorization(
        transferId: Long,
        reason: String,
        authority: Any,
    ) = database.withTransaction {
        requireTransactionDeletionAuthority(authority)
        val original = dao.custodyTransfer(transferId) ?: error("انتقال پیدا نشد")
        if (original.status == RecordStatus.DELETED) return@withTransaction
        require(original.status == RecordStatus.POSTED) { "فقط نسخه فعال تراکنش قابل حذف است" }
        val normalizedReason = requireDeletionReason(reason)
        requireOpenYear(original.financialYear, original.dateEpochDay)
        val deletedAt = System.currentTimeMillis()
        reverseTransfer(original, RecordStatus.DELETED, deletedAt)
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "DELETE",
                entityType = "CUSTODY_TRANSFER",
                entityId = original.id,
                description = "حذف انتقال از محاسبات: $normalizedReason",
                createdAt = deletedAt,
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
    }

    private suspend fun reverseTransfer(
        original: CustodyTransferEntity,
        status: RecordStatus,
        reversedAt: Long,
    ) {
        require(original.status == RecordStatus.POSTED) { "فقط انتقال فعال قابل خنثی‌سازی است" }
        require(status != RecordStatus.POSTED) { "وضعیت خنثی‌سازی انتقال معتبر نیست" }
        val allocations = dao.custodyTransferAllocations(original.id)
        require(allocations.isNotEmpty() && allocations.fold(0L) { total, item -> Math.addExact(total, item.amount) } == original.amount) {
            "منشأ انتقال اصلی کامل نیست؛ عملیات لغو شد"
        }
        require(allocations.all { allocation ->
            dao.custodySourceBalance(original.financialYear, original.toAccountId, allocation.sourceIncomeEventId) >= allocation.amount
        }) {
            "بخشی از این پول بعداً جابه‌جا یا تسویه شده است؛ ابتدا تراکنش وابسته جدیدتر را اصلاح یا حذف کنید"
        }
        dao.updateCustodyTransfer(original.copy(status = status, reversedAt = reversedAt))
        dao.insertCustodyPostings(
            allocations.flatMap { allocation ->
                listOf(
                    CustodyPostingEntity(
                        financialYear = original.financialYear,
                        sourceIncomeEventId = allocation.sourceIncomeEventId,
                        employeeId = original.fromEmployeeId,
                        accountId = original.fromAccountId,
                        amountSigned = allocation.amount,
                        type = CustodyPostingType.TRANSFER_OUT,
                        referenceId = original.id,
                        businessDateEpochDay = original.dateEpochDay,
                        createdAt = reversedAt,
                    ),
                    CustodyPostingEntity(
                        financialYear = original.financialYear,
                        sourceIncomeEventId = allocation.sourceIncomeEventId,
                        employeeId = original.toEmployeeId,
                        accountId = original.toAccountId,
                        amountSigned = Math.negateExact(allocation.amount),
                        type = CustodyPostingType.TRANSFER_IN,
                        referenceId = original.id,
                        businessDateEpochDay = original.dateEpochDay,
                        createdAt = reversedAt,
                    ),
                )
            },
        )
    }

    suspend fun recordSettlement(command: RecordSettlementCommand): Long = database.withTransaction {
        recordSettlementInTransaction(command)
    }

    private suspend fun recordSettlementInTransaction(
        command: RecordSettlementCommand,
        replacesPaymentId: Long? = null,
        movementOverride: EncodedMovementDetails? = null,
    ): Long {
        dao.settlementByCommand(command.commandId)?.let { return it.id }
        require(command.amount > 0) { "مبلغ پرداخت باید بیشتر از صفر باشد" }
        require(command.dateEpochDay <= LocalDate.now().toEpochDay()) { "ثبت تسویه برای آینده مجاز نیست" }
        require(command.method != MoneyMethod.POS) { "روش پوز فقط برای دریافت انعام یا فروش ضایعات است" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val fromAccount = dao.account(command.fromAccountId) ?: error("حساب پرداخت‌کننده پیدا نشد")
        require(fromAccount.employeeId == command.fromEmployeeId) { "حساب مبدأ متعلق به پرداخت‌کننده نیست" }
        command.destinationAccountId?.let { destination ->
            val destinationAccount = dao.account(destination) ?: error("حساب مقصد پیدا نشد")
            require(destinationAccount.employeeId == command.toEmployeeId) { "حساب مقصد متعلق به دریافت‌کننده نیست" }
            require(destinationAccount.archivedAt == null) { "حساب مقصد غیرفعال است" }
            if (command.type == SettlementType.PAYMENT) {
                require(destinationAccount.type.toMoneyMethod() == command.method) {
                    "نوع حساب مقصد با روش پرداخت هماهنگ نیست"
                }
            }
        }
        val movement = movementOverride ?: encodeMovementDetails(
            command.method,
            command.cardNumber,
            command.movementDateEpochDay,
            command.movementTimeMinutes,
        )
        val fromCustody = dao.custodyBalance(year.year, command.fromEmployeeId)
        val fromClaim = dao.claimBalance(year.year, command.fromEmployeeId)
        val toClaim = dao.claimBalance(year.year, command.toEmployeeId)
        val toCustody = dao.custodyBalance(year.year, command.toEmployeeId)
        val accountAvailable = dao.accountCustody(year.year, fromAccount.id)
        require(command.amount <= accountAvailable) { "این محل نگهداری پول مشترک موجودی کافی ندارد" }
        if (command.type == SettlementType.SELF_OFFSET) {
            require(command.fromEmployeeId == command.toEmployeeId) { "برداشت سهم از پول نزد خودش فقط برای همان شخص است" }
            require(command.amount <= minOf(fromClaim, fromCustody)) { "مبلغ برداشت بیشتر از سهم یا پول مشترک نزد شخص است" }
        } else {
            require(command.fromEmployeeId != command.toEmployeeId) { "برای یک شخص، برداشت سهم از پول نزد خودش را ثبت کنید" }
            val payerDebt = Math.subtractExact(fromCustody, fromClaim).coerceAtLeast(0)
            val receiverCredit = Math.subtractExact(toClaim, toCustody).coerceAtLeast(0)
            require(command.amount <= minOf(payerDebt, receiverCredit)) { "مبلغ از بدهی یا طلب فعلی بیشتر است" }
        }

        val custodySources = allocateCustodySources(year.year, fromAccount.id, command.amount)
        val claimSources = allocateClaimSources(year.year, command.toEmployeeId, command.amount)
        require((custodySources + claimSources).all { (sourceId, _) ->
            (dao.incomeEvent(sourceId)?.dateEpochDay ?: Long.MAX_VALUE) <= command.dateEpochDay
        }) { "تاریخ تسویه نمی‌تواند پیش از تاریخ درآمدِ منبع سهم یا پول باشد" }
        val paymentId = dao.insertSettlementPayment(
            SettlementPaymentEntity(
                commandId = command.commandId,
                financialYear = year.year,
                dateEpochDay = command.dateEpochDay,
                fromEmployeeId = command.fromEmployeeId,
                fromAccountId = fromAccount.id,
                toEmployeeId = command.toEmployeeId,
                destinationAccountId = command.destinationAccountId,
                amount = command.amount,
                method = command.method,
                type = command.type,
                note = command.note.trim(),
                cardNumberEncrypted = movement.cardNumberEncrypted,
                cardLast4 = movement.cardLast4,
                movementDateEpochDay = movement.dateEpochDay,
                movementTimeMinutes = movement.timeMinutes,
                replacesPaymentId = replacesPaymentId,
            ),
        )
        dao.insertSettlementCustodyAllocations(custodySources.map { SettlementCustodyAllocationEntity(paymentId, it.first, it.second) })
        dao.insertSettlementClaimAllocations(claimSources.map { SettlementClaimAllocationEntity(paymentId, it.first, it.second) })
        dao.insertCustodyPostings(
            custodySources.map { (sourceId, amount) ->
                CustodyPostingEntity(financialYear = year.year, sourceIncomeEventId = sourceId, employeeId = command.fromEmployeeId, accountId = fromAccount.id, amountSigned = -amount,
                    type = if (command.type == SettlementType.SELF_OFFSET) CustodyPostingType.SELF_OFFSET else CustodyPostingType.SETTLEMENT_PAID,
                    referenceId = paymentId, businessDateEpochDay = command.dateEpochDay)
            },
        )
        dao.insertClaimPostings(
            claimSources.map { (sourceId, amount) ->
                ClaimPostingEntity(financialYear = year.year, sourceIncomeEventId = sourceId, employeeId = command.toEmployeeId, amountSigned = -amount,
                    type = if (command.type == SettlementType.SELF_OFFSET) ClaimPostingType.SELF_OFFSET else ClaimPostingType.SETTLEMENT_RECEIVED,
                    referenceId = paymentId, businessDateEpochDay = command.dateEpochDay)
            },
        )
        dao.insertAudit(AuditLogEntity(financialYear = year.year, action = "POST", entityType = "SETTLEMENT", entityId = paymentId, description = if (command.type == SettlementType.SELF_OFFSET) "ثبت برداشت سهم از پول نزد خودش" else "ثبت پرداخت واقعی"))
        return paymentId
    }

    suspend fun correctSettlement(command: CorrectSettlementCommand): Long = database.withTransaction {
        dao.settlementByCommand(command.commandId)?.let { return@withTransaction it.id }
        val original = dao.settlementPayment(command.originalPaymentId) ?: error("تسویه اصلی پیدا نشد")
        require(original.status == RecordStatus.POSTED) { "این نسخه قبلاً اصلاح شده است" }
        require(command.correctionReason.trim().length >= 3) { "دلیل اصلاح را کوتاه و روشن بنویسید" }
        requireOpenYear(original.financialYear, command.dateEpochDay)
        val reversedAt = System.currentTimeMillis()
        reverseSettlement(original, RecordStatus.REVERSED, reversedAt)
        val movement = correctedMovementDetails(
            previousMethod = original.method,
            previousEncryptedCard = original.cardNumberEncrypted,
            previousCardLast4 = original.cardLast4,
            method = command.method,
            cardNumberUpdate = command.cardNumber,
            dateEpochDay = command.movementDateEpochDay,
            timeMinutes = command.movementTimeMinutes,
        )
        val replacementId = recordSettlementInTransaction(
            RecordSettlementCommand(
                financialYear = original.financialYear,
                dateEpochDay = command.dateEpochDay,
                fromEmployeeId = command.fromEmployeeId,
                fromAccountId = command.fromAccountId,
                toEmployeeId = command.toEmployeeId,
                destinationAccountId = command.destinationAccountId,
                amount = command.amount,
                method = command.method,
                type = command.type,
                note = command.note,
                movementDateEpochDay = command.movementDateEpochDay,
                movementTimeMinutes = command.movementTimeMinutes,
                commandId = command.commandId,
            ),
            replacesPaymentId = original.id,
            movementOverride = movement,
        )
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "CORRECT",
                entityType = "SETTLEMENT",
                entityId = original.id,
                description = "اصلاح تسویه با نسخه $replacementId: ${command.correctionReason.trim()}",
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
        replacementId
    }

    internal suspend fun deleteSettlementAfterAuthorization(
        paymentId: Long,
        reason: String,
        authority: Any,
    ) = database.withTransaction {
        requireTransactionDeletionAuthority(authority)
        val original = dao.settlementPayment(paymentId) ?: error("تسویه پیدا نشد")
        if (original.status == RecordStatus.DELETED) return@withTransaction
        require(original.status == RecordStatus.POSTED) { "فقط نسخه فعال تراکنش قابل حذف است" }
        val normalizedReason = requireDeletionReason(reason)
        requireOpenYear(original.financialYear, original.dateEpochDay)
        val deletedAt = System.currentTimeMillis()
        reverseSettlement(original, RecordStatus.DELETED, deletedAt)
        dao.insertAudit(
            AuditLogEntity(
                financialYear = original.financialYear,
                action = "DELETE",
                entityType = "SETTLEMENT",
                entityId = original.id,
                description = "حذف تسویه از محاسبات: $normalizedReason",
                createdAt = deletedAt,
            ),
        )
        assertOpenYearLedgerInvariant(original.financialYear)
    }

    private suspend fun reverseSettlement(
        original: SettlementPaymentEntity,
        status: RecordStatus,
        reversedAt: Long,
    ) {
        require(original.status == RecordStatus.POSTED) { "فقط تسویه فعال قابل خنثی‌سازی است" }
        require(status != RecordStatus.POSTED) { "وضعیت خنثی‌سازی تسویه معتبر نیست" }
        val custodyAllocations = dao.settlementCustodyAllocations(original.id)
        val claimAllocations = dao.settlementClaimAllocations(original.id)
        require(custodyAllocations.fold(0L) { total, item -> Math.addExact(total, item.amount) } == original.amount) {
            "منشأ پول تسویه اصلی کامل نیست؛ عملیات لغو شد"
        }
        require(claimAllocations.fold(0L) { total, item -> Math.addExact(total, item.amount) } == original.amount) {
            "منشأ سهم تسویه اصلی کامل نیست؛ عملیات لغو شد"
        }
        dao.updateSettlementPayment(original.copy(status = status, reversedAt = reversedAt))
        dao.insertCustodyPostings(
            custodyAllocations.map { allocation ->
                CustodyPostingEntity(
                    financialYear = original.financialYear,
                    sourceIncomeEventId = allocation.sourceIncomeEventId,
                    employeeId = original.fromEmployeeId,
                    accountId = original.fromAccountId,
                    amountSigned = allocation.amount,
                    type = if (original.type == SettlementType.SELF_OFFSET) CustodyPostingType.SELF_OFFSET else CustodyPostingType.SETTLEMENT_PAID,
                    referenceId = original.id,
                    businessDateEpochDay = original.dateEpochDay,
                    createdAt = reversedAt,
                )
            },
        )
        dao.insertClaimPostings(
            claimAllocations.map { allocation ->
                ClaimPostingEntity(
                    financialYear = original.financialYear,
                    sourceIncomeEventId = allocation.sourceIncomeEventId,
                    employeeId = original.toEmployeeId,
                    amountSigned = allocation.amount,
                    type = if (original.type == SettlementType.SELF_OFFSET) ClaimPostingType.SELF_OFFSET else ClaimPostingType.SETTLEMENT_RECEIVED,
                    referenceId = original.id,
                    businessDateEpochDay = original.dateEpochDay,
                    createdAt = reversedAt,
                )
            },
        )
    }

    suspend fun settlementSuggestions(year: Int): List<SettlementSuggestion> = AccountingEngine.suggestSettlement(
        dao.personBalances(year).map { PersonBalance(it.employeeId, it.employeeName, it.claimAmount, it.custodyAmount) },
    )

    suspend fun closeFinancialYear(year: Int, backupChecksum: String, backupAt: Long): Long = database.withTransaction {
        val entity = dao.financialYear(year) ?: error("سال مالی پیدا نشد")
        require(entity.status == FinancialYearStatus.OPEN) { "این سال قبلاً بسته شده است" }
        require(LocalDate.now().toEpochDay() > entity.endEpochDay) { "سال مالی فقط پس از پایان اسفند بسته می‌شود" }
        require(backupChecksum.isNotBlank()) { "قبل از بستن سال، پشتیبان نهایی بسازید" }
        require(backupAt >= dao.latestAuditAt(year)) { "بعد از آخرین تغییر مالی، یک پشتیبان نهایی جدید بسازید" }
        val beforeBalances = dao.personBalances(year)
        val remainder = dao.remainder(year)
        require(beforeBalances.all { it.claimAmount == 0L }) { "هنوز سهم تسویه‌نشده وجود دارد" }
        val totalCustody = beforeBalances.fold(0L) { acc, row -> Math.addExact(acc, row.custodyAmount) }
        require(totalCustody == remainder) { "هنوز پول مشترک یا پرداخت تسویه‌نشده وجود دارد" }

        if (remainder > 0) {
            ensureYear(year + 1)
            val accountRows = dao.accountCustodyRows(year)
            val oldCustody = mutableListOf<CustodyPostingEntity>()
            val newCustody = mutableListOf<CustodyPostingEntity>()
            accountRows.forEach { row ->
                val account = dao.account(row.accountId) ?: error("حساب انتقال سال پیدا نشد")
                allocateCustodySources(year, row.accountId, row.amount).forEach { (sourceId, amount) ->
                    oldCustody += CustodyPostingEntity(financialYear = year, sourceIncomeEventId = sourceId, employeeId = account.employeeId, accountId = account.id, amountSigned = -amount, type = CustodyPostingType.ANNUAL_ROLLOVER_OUT, referenceId = year.toLong(), businessDateEpochDay = entity.endEpochDay)
                    newCustody += CustodyPostingEntity(financialYear = year + 1, sourceIncomeEventId = sourceId, employeeId = account.employeeId, accountId = account.id, amountSigned = amount, type = CustodyPostingType.ANNUAL_ROLLOVER_IN, referenceId = year.toLong(), businessDateEpochDay = entity.endEpochDay + 1)
                }
            }
            dao.insertCustodyPostings(oldCustody + newCustody)
            dao.insertClaimPostings(
                listOf(
                    ClaimPostingEntity(financialYear = year, sourceIncomeEventId = null, employeeId = null, amountSigned = -remainder, type = ClaimPostingType.ANNUAL_ROLLOVER_OUT, referenceId = year.toLong(), businessDateEpochDay = entity.endEpochDay),
                    ClaimPostingEntity(financialYear = year + 1, sourceIncomeEventId = null, employeeId = null, amountSigned = remainder, type = ClaimPostingType.ANNUAL_ROLLOVER_IN, referenceId = year.toLong(), businessDateEpochDay = entity.endEpochDay + 1),
                ),
            )
        }
        require(dao.personBalances(year).all { it.claimAmount == 0L && it.custodyAmount == 0L } && dao.remainder(year) == 0L) {
            "تراز سال صفر نشد؛ بستن لغو شد"
        }
        val claims = dao.claimPostingsForYear(year)
        val custody = dao.custodyPostingsForYear(year)
        val ledgerBytes = buildString {
            claims.forEach { append("C|${it.id}|${it.employeeId}|${it.amountSigned}|${it.type}|${it.referenceId}\n") }
            custody.forEach { append("P|${it.id}|${it.employeeId}|${it.accountId}|${it.amountSigned}|${it.type}|${it.referenceId}\n") }
        }.toByteArray()
        val summary = JSONObject()
            .put("year", year)
            .put("income", dao.allIncomeEvents().filter { it.financialYear == year && it.status == RecordStatus.POSTED }.sumOf { it.amount })
            .put("remainderRolled", remainder)
            .put("closedAt", System.currentTimeMillis())
            .toString()
        val closeId = dao.insertAnnualClose(
            AnnualCloseEntity(
                financialYear = year,
                revision = (dao.latestAnnualClose(year)?.revision ?: 0) + 1,
                closedAt = System.currentTimeMillis(),
                ledgerHash = BackupCrypto.checksum(ledgerBytes),
                backupChecksum = backupChecksum,
                summaryJson = summary,
            ),
        )
        val employees = dao.allEmployees()
        dao.insertAnnualCloseSnapshots(
            employees.map { employee ->
                val employeeClaims = claims.filter { it.employeeId == employee.id }
                val employeeCustody = custody.filter { it.employeeId == employee.id }
                val totalClaim = employeeClaims
                    .filter { it.type == ClaimPostingType.ENTITLEMENT }
                    .fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) }
                val totalCustodyIn = employeeCustody
                    .filter {
                        it.type in setOf(
                            CustodyPostingType.INCOME_RECEIPT,
                            CustodyPostingType.TRANSFER_IN,
                            CustodyPostingType.ANNUAL_ROLLOVER_IN,
                        )
                    }
                    .fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) }
                val totalCustodyOut = Math.negateExact(
                    employeeCustody
                        .filter {
                            it.type in setOf(
                                CustodyPostingType.TRANSFER_OUT,
                                CustodyPostingType.SETTLEMENT_PAID,
                                CustodyPostingType.SELF_OFFSET,
                                CustodyPostingType.ANNUAL_ROLLOVER_OUT,
                            )
                        }
                        .fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) },
                )
                val totalSettlementPaid = Math.negateExact(
                    employeeCustody
                        .filter { it.type == CustodyPostingType.SETTLEMENT_PAID }
                        .fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) },
                )
                val totalSettlementReceived = Math.negateExact(
                    employeeClaims
                        .filter { it.type == ClaimPostingType.SETTLEMENT_RECEIVED }
                        .fold(0L) { total, posting -> Math.addExact(total, posting.amountSigned) },
                )
                AnnualCloseEmployeeSnapshotEntity(
                    closeId = closeId,
                    employeeId = employee.id,
                    totalClaim = totalClaim,
                    totalCustodyIn = totalCustodyIn,
                    totalCustodyOut = totalCustodyOut,
                    totalSettlementPaid = totalSettlementPaid,
                    totalSettlementReceived = totalSettlementReceived,
                    finalNet = 0,
                )
            },
        )
        dao.updateFinancialYear(entity.copy(status = FinancialYearStatus.CLOSED, closedAt = System.currentTimeMillis()))
        dao.insertAudit(AuditLogEntity(financialYear = year, action = "CLOSE", entityType = "FINANCIAL_YEAR", entityId = year.toLong(), description = "بستن نهایی سال $year"))
        closeId
    }

    private suspend fun requireOpenYear(year: Int, dateEpochDay: Long): FinancialYearEntity {
        val entity = ensureYear(year)
        require(entity.status == FinancialYearStatus.OPEN) { "سال $year بسته شده و فقط قابل مشاهده است" }
        require(dateEpochDay in entity.startEpochDay..entity.endEpochDay) { "تاریخ خارج از سال مالی $year است" }
        return entity
    }

    private suspend fun movementAccount(employee: EmployeeEntity, method: MoneyMethod): MoneyAccountEntity {
        val accounts = dao.activeAccounts(employee.id)
        val existing = when (method) {
            MoneyMethod.CASH -> accounts.firstOrNull { it.type == AccountType.CASH }
            MoneyMethod.CARD -> accounts.firstOrNull {
                it.type == AccountType.CARD && it.cardNumberEncrypted.isBlank() && it.title.startsWith("کارت‌به‌کارت")
            }
            MoneyMethod.POS -> accounts.firstOrNull { it.type == AccountType.POS }
        }
        if (existing != null) return existing
        val id = dao.insertAccount(
            MoneyAccountEntity(
                employeeId = employee.id,
                type = when (method) {
                    MoneyMethod.CASH -> AccountType.CASH
                    MoneyMethod.CARD -> AccountType.CARD
                    MoneyMethod.POS -> AccountType.POS
                },
                title = when (method) {
                    MoneyMethod.CASH -> "پول نقد ${employee.name}"
                    MoneyMethod.CARD -> "کارت‌به‌کارت ${employee.name}"
                    MoneyMethod.POS -> "دستگاه پوز ${employee.name}"
                },
                ownerName = employee.name,
            ),
        )
        return dao.account(id) ?: error("حساب داخلی تراکنش ساخته نشد")
    }

    private data class EncodedMovementDetails(
        val cardNumberEncrypted: String,
        val cardLast4: String,
        val dateEpochDay: Long?,
        val timeMinutes: Int?,
    )

    private fun encodeMovementDetails(
        method: MoneyMethod,
        cardNumber: String,
        dateEpochDay: Long?,
        timeMinutes: Int?,
    ): EncodedMovementDetails {
        require(timeMinutes == null || timeMinutes in 0..1_439) { "زمان تراکنش معتبر نیست" }
        require(timeMinutes == null || dateEpochDay != null) { "برای ثبت زمان، تاریخ تراکنش را هم وارد کنید" }
        val normalized = if (method == MoneyMethod.CARD) IranianCardValidator.normalize(cardNumber) else ""
        require(normalized.isBlank() || IranianCardValidator.isValid(normalized)) {
            "شماره کارت را کامل و معتبر وارد کنید یا خالی بگذارید"
        }
        return EncodedMovementDetails(
            cardNumberEncrypted = normalized.takeIf { it.isNotBlank() }?.let(cardCrypto::encrypt).orEmpty(),
            cardLast4 = normalized.takeLast(4),
            dateEpochDay = dateEpochDay,
            timeMinutes = timeMinutes,
        )
    }

    private fun correctedMovementDetails(
        previousMethod: MoneyMethod,
        previousEncryptedCard: String,
        previousCardLast4: String,
        method: MoneyMethod,
        cardNumberUpdate: String?,
        dateEpochDay: Long?,
        timeMinutes: Int?,
    ): EncodedMovementDetails {
        require(timeMinutes == null || timeMinutes in 0..1_439) { "زمان تراکنش معتبر نیست" }
        require(timeMinutes == null || dateEpochDay != null) { "برای ثبت زمان، تاریخ تراکنش را هم وارد کنید" }
        return if (method == MoneyMethod.CARD && previousMethod == MoneyMethod.CARD && cardNumberUpdate == null) {
            EncodedMovementDetails(previousEncryptedCard, previousCardLast4, dateEpochDay, timeMinutes)
        } else {
            encodeMovementDetails(method, cardNumberUpdate.orEmpty(), dateEpochDay, timeMinutes)
        }
    }

    private suspend fun assertOpenYearLedgerInvariant(year: Int) {
        val balances = dao.personBalances(year)
        require(balances.all { it.claimAmount >= 0 && it.custodyAmount >= 0 }) {
            "اصلاح باعث مانده منفی شد؛ عملیات کامل لغو شد"
        }
        val claimTotal = balances.fold(dao.remainder(year)) { total, row -> Math.addExact(total, row.claimAmount) }
        val custodyTotal = balances.fold(0L) { total, row -> Math.addExact(total, row.custodyAmount) }
        require(claimTotal == custodyTotal) { "اصلاح تراز سهم و پول مشترک را ناسازگار کرد؛ عملیات لغو شد" }
        require(dao.remainder(year) >= 0L) { "اصلاح باقیمانده تقسیم را منفی کرد؛ عملیات لغو شد" }
        require(dao.negativeAccountBalanceCount(year) == 0) { "اصلاح موجودی یک حساب را منفی کرد؛ عملیات لغو شد" }
        require(dao.negativeCustodySourceCount(year) == 0) { "اصلاح منشأ پول مشترک را منفی کرد؛ عملیات لغو شد" }
        require(dao.negativeClaimSourceCount(year) == 0) { "اصلاح منشأ سهم را منفی کرد؛ عملیات لغو شد" }
    }

    private suspend fun allocateCustodySources(year: Int, accountId: Long, amount: Long): List<Pair<Long, Long>> {
        var remaining = amount
        val allocations = mutableListOf<Pair<Long, Long>>()
        dao.custodySources(year, accountId).forEach { source ->
            if (remaining > 0) {
                val take = minOf(source.amount, remaining)
                if (take > 0) allocations += source.sourceIncomeEventId to take
                remaining = Math.subtractExact(remaining, take)
            }
        }
        require(remaining == 0L) { "منشأ کافی برای پول مشترک پیدا نشد" }
        return allocations
    }

    private suspend fun allocateClaimSources(year: Int, employeeId: Long, amount: Long): List<Pair<Long, Long>> {
        var remaining = amount
        val allocations = mutableListOf<Pair<Long, Long>>()
        dao.claimSources(year, employeeId).forEach { source ->
            if (remaining > 0) {
                val take = minOf(source.amount, remaining)
                if (take > 0) allocations += source.sourceIncomeEventId to take
                remaining = Math.subtractExact(remaining, take)
            }
        }
        require(remaining == 0L) { "منشأ کافی برای طلب پیدا نشد" }
        return allocations
    }
}

private fun AccountType?.toMoneyMethod(): MoneyMethod = when (this) {
    AccountType.CARD -> MoneyMethod.CARD
    AccountType.POS -> MoneyMethod.POS
    AccountType.CASH, null -> MoneyMethod.CASH
}
