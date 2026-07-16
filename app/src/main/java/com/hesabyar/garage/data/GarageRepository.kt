package com.hesabyar.garage.data

import androidx.room.withTransaction
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.security.BackupCrypto
import com.hesabyar.garage.util.IranianCardValidator
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.Flow
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
    val receiverAccountId: Long,
    val description: String,
    val commandId: String = UUID.randomUUID().toString(),
)

data class TransferCustodyCommand(
    val financialYear: Int,
    val dateEpochDay: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
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
) {
    val dao: GarageDao = database.dao()

    fun employees(): Flow<List<EmployeeEntity>> = dao.observeEmployees()
    fun financialYears(): Flow<List<FinancialYearEntity>> = dao.observeFinancialYears()
    fun incomeRows(year: Int): Flow<List<IncomeWithReceiverRow>> = dao.observeIncomeRows(year)
    fun transfers(year: Int): Flow<List<CustodyTransferEntity>> = dao.observeCustodyTransfers(year)
    fun settlements(year: Int): Flow<List<SettlementPaymentEntity>> = dao.observeSettlements(year)
    fun totalIncome(year: Int): Flow<Long> = dao.observeTotalIncome(year)
    fun remainder(year: Int): Flow<Long> = dao.observeRemainder(year)
    fun accountCustody(year: Int): Flow<List<AccountCustodyRow>> = dao.observeAccountCustody(year)
    fun activeEmployeeCount(epochDay: Long): Flow<Int> = dao.observeActiveEmployeeCount(epochDay)
    fun balances(year: Int): Flow<List<PersonBalance>> = dao.observePersonBalances(year).map { rows ->
        rows.map { PersonBalance(it.employeeId, it.employeeName, it.claimAmount, it.custodyAmount) }
    }

    fun accounts(employeeId: Long): Flow<List<MoneyAccountEntity>> = dao.observeAccounts(employeeId)
    fun employmentPeriods(employeeId: Long): Flow<List<EmploymentPeriodEntity>> = dao.observeEmploymentPeriods(employeeId)
    fun absences(employeeId: Long): Flow<List<AbsencePeriodEntity>> = dao.observeAbsences(employeeId)
    fun eligibility(eventId: Long): Flow<List<EligibilitySnapshotEntity>> = dao.observeEligibilitySnapshots(eventId)
    fun personHistory(employeeId: Long, year: Int): Flow<List<PersonHistoryRow>> = dao.observePersonHistory(employeeId, year)

    suspend fun employee(id: Long): EmployeeEntity? = dao.employee(id)
    suspend fun accountsNow(employeeId: Long): List<MoneyAccountEntity> = dao.activeAccounts(employeeId)
    suspend fun financialYearNow(year: Int): FinancialYearEntity? = dao.financialYear(year)
    suspend fun incomeEvent(id: Long): IncomeEventEntity? = dao.incomeEvent(id)
    suspend fun account(id: Long): MoneyAccountEntity? = dao.account(id)
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

    suspend fun previewEligibility(dateEpochDay: Long): List<EligibilityPreview> = dao.allEmployees().map { employee ->
        EligibilityPreview(
            employee,
            EligibilityEngine.evaluate(
                dateEpochDay,
                dao.employmentPeriods(employee.id).map { EmploymentRange(it.id, it.startEpochDay, it.endEpochDay) },
                dao.absences(employee.id).map { AbsenceRange(it.id, it.startEpochDay, it.returnEpochDay) },
            ),
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

    suspend fun registerIncome(command: RegisterIncomeCommand): Long = database.withTransaction {
        dao.incomeByCommand(command.commandId)?.let { return@withTransaction it.id }
        require(command.amount > 0) { "مبلغ باید بیشتر از صفر باشد" }
        require(command.dateEpochDay <= LocalDate.now().toEpochDay()) { "ثبت درآمد برای آینده مجاز نیست" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val receiver = dao.employee(command.receiverEmployeeId) ?: error("دریافت‌کننده پیدا نشد")
        val receiverAccount = dao.account(command.receiverAccountId) ?: error("حساب دریافت‌کننده پیدا نشد")
        require(receiverAccount.employeeId == receiver.id) { "حساب انتخابی متعلق به دریافت‌کننده نیست" }

        val evaluations = dao.allEmployees().associateWith { employee ->
            EligibilityEngine.evaluate(
                command.dateEpochDay,
                dao.employmentPeriods(employee.id).map { EmploymentRange(it.id, it.startEpochDay, it.endEpochDay) },
                dao.absences(employee.id).map { AbsenceRange(it.id, it.startEpochDay, it.returnEpochDay) },
            )
        }
        val eligible = evaluations.filterValues { it.isEligible }
        require(eligible.isNotEmpty()) { "در این تاریخ هیچ فردی واجد سهم نیست" }
        val carried = dao.remainder(year.year)
        val distribution = AccountingEngine.distribute(command.amount, carried, eligible.size)
        val method = if (receiverAccount.type == AccountType.CARD) MoneyMethod.CARD else MoneyMethod.CASH
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
        eventId
    }

    suspend fun transferCustody(command: TransferCustodyCommand): Long = database.withTransaction {
        dao.transferByCommand(command.commandId)?.let { return@withTransaction it.id }
        require(command.amount > 0) { "مبلغ انتقال باید بیشتر از صفر باشد" }
        require(command.fromAccountId != command.toAccountId) { "حساب مبدأ و مقصد یکسان است" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val from = dao.account(command.fromAccountId) ?: error("حساب مبدأ پیدا نشد")
        val to = dao.account(command.toAccountId) ?: error("حساب مقصد پیدا نشد")
        require(from.archivedAt == null && to.archivedAt == null) { "حساب غیرفعال قابل استفاده نیست" }
        val available = dao.accountCustody(year.year, from.id)
        require(command.amount <= available) { "مبلغ انتقال از امانت موجود بیشتر است" }
        val sources = allocateCustodySources(year.year, from.id, command.amount)
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
                method = if (to.type == AccountType.CARD) MoneyMethod.CARD else MoneyMethod.CASH,
                note = command.note.trim(),
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
        dao.insertAudit(AuditLogEntity(financialYear = year.year, action = "POST", entityType = "CUSTODY_TRANSFER", entityId = transferId, description = "انتقال جزئی امانت"))
        transferId
    }

    suspend fun recordSettlement(command: RecordSettlementCommand): Long = database.withTransaction {
        dao.settlementByCommand(command.commandId)?.let { return@withTransaction it.id }
        require(command.amount > 0) { "مبلغ پرداخت باید بیشتر از صفر باشد" }
        val year = requireOpenYear(command.financialYear, command.dateEpochDay)
        val fromAccount = dao.account(command.fromAccountId) ?: error("حساب پرداخت‌کننده پیدا نشد")
        require(fromAccount.employeeId == command.fromEmployeeId) { "حساب مبدأ متعلق به پرداخت‌کننده نیست" }
        command.destinationAccountId?.let { destination ->
            require(dao.account(destination)?.employeeId == command.toEmployeeId) { "حساب مقصد متعلق به دریافت‌کننده نیست" }
        }
        val fromCustody = dao.custodyBalance(year.year, command.fromEmployeeId)
        val fromClaim = dao.claimBalance(year.year, command.fromEmployeeId)
        val toClaim = dao.claimBalance(year.year, command.toEmployeeId)
        val toCustody = dao.custodyBalance(year.year, command.toEmployeeId)
        val accountAvailable = dao.accountCustody(year.year, fromAccount.id)
        require(command.amount <= accountAvailable) { "این حساب امانت کافی ندارد" }
        if (command.type == SettlementType.SELF_OFFSET) {
            require(command.fromEmployeeId == command.toEmployeeId) { "تهاتر داخلی فقط برای یک شخص است" }
            require(command.amount <= minOf(fromClaim, fromCustody)) { "مبلغ تهاتر بیشتر از سهم یا امانت شخص است" }
        } else {
            require(command.fromEmployeeId != command.toEmployeeId) { "برای یک شخص از تهاتر داخلی استفاده کنید" }
            val payerDebt = Math.subtractExact(fromCustody, fromClaim).coerceAtLeast(0)
            val receiverCredit = Math.subtractExact(toClaim, toCustody).coerceAtLeast(0)
            require(command.amount <= minOf(payerDebt, receiverCredit)) { "مبلغ از بدهی یا طلب فعلی بیشتر است" }
        }

        val custodySources = allocateCustodySources(year.year, fromAccount.id, command.amount)
        val claimSources = allocateClaimSources(year.year, command.toEmployeeId, command.amount)
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
        dao.insertAudit(AuditLogEntity(financialYear = year.year, action = "POST", entityType = "SETTLEMENT", entityId = paymentId, description = if (command.type == SettlementType.SELF_OFFSET) "ثبت تهاتر داخلی" else "ثبت پرداخت واقعی"))
        paymentId
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
        require(totalCustody == remainder) { "هنوز امانت یا پرداخت تسویه‌نشده وجود دارد" }

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
                AnnualCloseEmployeeSnapshotEntity(
                    closeId = closeId,
                    employeeId = employee.id,
                    totalClaim = employeeClaims.filter { it.amountSigned > 0 }.sumOf { it.amountSigned },
                    totalCustodyIn = employeeCustody.filter { it.amountSigned > 0 }.sumOf { it.amountSigned },
                    totalCustodyOut = -employeeCustody.filter { it.amountSigned < 0 }.sumOf { it.amountSigned },
                    totalSettlementPaid = -employeeCustody.filter { it.type == CustodyPostingType.SETTLEMENT_PAID && it.amountSigned < 0 }.sumOf { it.amountSigned },
                    totalSettlementReceived = -employeeClaims.filter { it.type == ClaimPostingType.SETTLEMENT_RECEIVED && it.amountSigned < 0 }.sumOf { it.amountSigned },
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
        require(remaining == 0L) { "منشأ کافی برای امانت پیدا نشد" }
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
