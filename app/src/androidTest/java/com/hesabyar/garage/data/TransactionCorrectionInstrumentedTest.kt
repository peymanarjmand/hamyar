package com.hesabyar.garage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesabyar.garage.data.local.AuditLogEntity
import com.hesabyar.garage.data.local.ClaimPostingEntity
import com.hesabyar.garage.data.local.CustodyPostingEntity
import com.hesabyar.garage.data.local.CustodyTransferAllocationEntity
import com.hesabyar.garage.data.local.CustodyTransferEntity
import com.hesabyar.garage.data.local.EligibilitySnapshotEntity
import com.hesabyar.garage.data.local.FinancialYearEntity
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.data.local.IncomeEventEntity
import com.hesabyar.garage.data.local.MoneyAccountEntity
import com.hesabyar.garage.data.local.SettlementClaimAllocationEntity
import com.hesabyar.garage.data.local.SettlementCustodyAllocationEntity
import com.hesabyar.garage.data.local.SettlementPaymentEntity
import com.hesabyar.garage.domain.AccountType
import com.hesabyar.garage.domain.ClaimPostingType
import com.hesabyar.garage.domain.CustodyPostingType
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.domain.RecordStatus
import com.hesabyar.garage.domain.SettlementType
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionCorrectionInstrumentedTest {
    private lateinit var database: GarageDatabase
    private lateinit var repository: GarageRepository
    private lateinit var deletionHarness: TransactionDeletionTestHarness

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsRepository(context)
        runBlocking { settings.setAbsenceGraceDays(14) }
        repository = GarageRepository(database, CardCrypto(), settings)
        runBlocking { deletionHarness = TransactionDeletionTestHarness.create(context, repository) }
    }

    @After
    fun tearDown() {
        runBlocking { if (::deletionHarness.isInitialized) deletionHarness.close() }
        database.close()
    }

    @Test
    fun posIncomeCreatesPosAccountAndKeepsClaimRemainderEqualToCustody() = runTest {
        val people = addTwoEmployees()

        val eventId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE,
                amount = 101,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "دریافت با دستگاه پوز",
                method = MoneyMethod.POS,
            ),
        )

        val event = database.dao().incomeEvent(eventId)!!
        val account = database.dao().account(event.receiverAccountId)!!
        assertEquals(MoneyMethod.POS, event.method)
        assertEquals(AccountType.POS, account.type)
        assertEquals(people.holderId, account.employeeId)
        assertTrue(account.title.startsWith("دستگاه پوز"))
        assertEquals(
            1,
            database.dao().allAccounts().count {
                it.employeeId == people.holderId && it.type == AccountType.POS
            },
        )
        repository.updateEmployee(
            UpdateEmployeeCommand(
                employeeId = people.holderId,
                name = "دارنده جدید",
                phone = "",
                role = EmployeeRole.PAINTER,
            ),
        )
        val renamedPosAccount = database.dao().account(account.id)!!
        assertEquals("دستگاه پوز دارنده جدید", renamedPosAccount.title)
        assertEquals("دارنده جدید", renamedPosAccount.ownerName)

        assertLedgerBalanced(expectedClaims = 100, expectedRemainder = 1, expectedCustody = 101)
    }

    @Test
    fun safeIncomeCorrectionReversesOriginalAndPostsLinkedReplacement() = runTest {
        val people = addTwoEmployees()
        val originalId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE,
                amount = 101,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "ثبت اولیه",
                method = MoneyMethod.POS,
            ),
        )
        val originalClaims = database.dao().incomeClaimPostings(YEAR, originalId)
        val originalCustody = database.dao().incomeCustodyPostings(YEAR, originalId)

        val replacementId = repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = originalId,
                dateEpochDay = EVENT_DATE + 1,
                amount = 120,
                type = IncomeType.SCRAP,
                receiverEmployeeId = people.beneficiaryId,
                description = "فروش اصلاح‌شده ضایعات",
                method = MoneyMethod.CASH,
                correctionReason = "اصلاح مبلغ و روش دریافت",
            ),
        )

        assertNotEquals(originalId, replacementId)
        val original = database.dao().incomeEvent(originalId)!!
        val replacement = database.dao().incomeEvent(replacementId)!!
        assertEquals(RecordStatus.REVERSED, original.status)
        assertNotNull(original.reversedAt)
        assertEquals(RecordStatus.POSTED, replacement.status)
        assertEquals(originalId, replacement.replacesEventId)
        assertEquals(IncomeType.SCRAP, replacement.type)
        assertEquals(MoneyMethod.CASH, replacement.method)
        assertEquals(120L, replacement.amount)

        val neutralizedClaims = database.dao().incomeClaimPostings(YEAR, originalId)
        val neutralizedCustody = database.dao().incomeCustodyPostings(YEAR, originalId)
        assertEquals(originalClaims.size * 2, neutralizedClaims.size)
        assertEquals(0L, neutralizedClaims.sumOf { it.amountSigned })
        assertEquals(originalCustody.size * 2, neutralizedCustody.size)
        assertEquals(0L, neutralizedCustody.sumOf { it.amountSigned })
        assertEquals(120L, database.dao().incomeClaimPostings(YEAR, replacementId).sumOf { it.amountSigned })
        assertEquals(120L, database.dao().incomeCustodyPostings(YEAR, replacementId).sumOf { it.amountSigned })
        assertTrue(
            database.dao().allAuditLogs().any {
                it.action == "CORRECT" && it.entityType == "INCOME" && it.entityId == originalId
            },
        )

        assertLedgerBalanced(expectedClaims = 120, expectedRemainder = 0, expectedCustody = 120)
    }

    @Test
    fun incomeWithActiveAllocationRejectsCorrectionWithoutPartialDatabaseChange() = runTest {
        val people = addTwoEmployees()
        val originalId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE,
                amount = 200,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "درآمد دارای انتقال وابسته",
                method = MoneyMethod.CASH,
            ),
        )
        val sourceAccountId = database.dao().incomeEvent(originalId)!!.receiverAccountId
        val destinationAccountId = repository.accountsNow(people.beneficiaryId).single().id
        repository.transferCustody(
            TransferCustodyCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE + 1,
                fromAccountId = sourceAccountId,
                toAccountId = destinationAccountId,
                amount = 40,
                note = "انتقال وابسته",
                method = MoneyMethod.CASH,
            ),
        )
        val before = databaseState()

        val failure = expectIllegalArgument {
            repository.correctIncome(
                CorrectIncomeCommand(
                    originalEventId = originalId,
                    dateEpochDay = EVENT_DATE + 2,
                    amount = 220,
                    type = IncomeType.SCRAP,
                    receiverEmployeeId = people.holderId,
                    description = "اصلاحی که نباید ثبت شود",
                    method = MoneyMethod.CASH,
                    correctionReason = "اصلاح مبلغ تراکنش",
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("منشأ انتقال یا تسویه فعال"))
        assertEquals(before, databaseState())
        assertEquals(RecordStatus.POSTED, database.dao().incomeEvent(originalId)!!.status)
        assertLedgerBalanced(expectedClaims = 200, expectedRemainder = 0, expectedCustody = 200)
    }

    @Test
    fun transferCorrectionRollsBackInvalidReplacementThenPostsSafeReplacement() = runTest {
        val people = addTwoEmployees()
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE,
                amount = 200,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "منبع انتقال",
                method = MoneyMethod.CASH,
            ),
        )
        val sourceAccountId = database.dao().incomeEvent(incomeId)!!.receiverAccountId
        val destinationAccountId = repository.accountsNow(people.beneficiaryId).single().id
        val originalId = repository.transferCustody(
            TransferCustodyCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE + 1,
                fromAccountId = sourceAccountId,
                toAccountId = destinationAccountId,
                amount = 80,
                note = "انتقال اولیه",
                method = MoneyMethod.CASH,
            ),
        )
        val beforeInvalidCorrection = databaseState()

        expectIllegalArgument {
            repository.correctTransfer(
                CorrectTransferCommand(
                    originalTransferId = originalId,
                    dateEpochDay = EVENT_DATE + 2,
                    fromAccountId = sourceAccountId,
                    toAccountId = destinationAccountId,
                    amount = 201,
                    method = MoneyMethod.CASH,
                    note = "مبلغ نامعتبر",
                    correctionReason = "آزمون بازگشت کامل",
                ),
            )
        }
        assertEquals(beforeInvalidCorrection, databaseState())
        assertEquals(RecordStatus.POSTED, database.dao().custodyTransfer(originalId)!!.status)

        expectIllegalArgument {
            repository.correctTransfer(
                CorrectTransferCommand(
                    originalTransferId = originalId,
                    dateEpochDay = EVENT_DATE - 1,
                    fromAccountId = sourceAccountId,
                    toAccountId = destinationAccountId,
                    amount = 60,
                    method = MoneyMethod.CASH,
                    note = "تاریخ پیش از منبع",
                    correctionReason = "آزمون ترتیب زمانی",
                ),
            )
        }
        assertEquals(beforeInvalidCorrection, databaseState())
        assertEquals(RecordStatus.POSTED, database.dao().custodyTransfer(originalId)!!.status)

        val replacementId = repository.correctTransfer(
            CorrectTransferCommand(
                originalTransferId = originalId,
                dateEpochDay = EVENT_DATE + 2,
                fromAccountId = sourceAccountId,
                toAccountId = destinationAccountId,
                amount = 60,
                method = MoneyMethod.CASH,
                note = "انتقال اصلاح‌شده",
                correctionReason = "اصلاح مبلغ انتقال",
            ),
        )

        val original = database.dao().custodyTransfer(originalId)!!
        val replacement = database.dao().custodyTransfer(replacementId)!!
        assertEquals(RecordStatus.REVERSED, original.status)
        assertNotNull(original.reversedAt)
        assertEquals(RecordStatus.POSTED, replacement.status)
        assertEquals(originalId, replacement.replacesTransferId)
        assertEquals(60L, replacement.amount)
        assertEquals(60L, database.dao().custodyTransferAllocations(replacementId).sumOf { it.amount })

        val originalLedger = database.dao().allCustodyPostings().filter {
            it.referenceId == originalId &&
                it.type in setOf(CustodyPostingType.TRANSFER_OUT, CustodyPostingType.TRANSFER_IN)
        }
        assertTrue(originalLedger.groupBy { it.accountId }.values.all { rows -> rows.sumOf { it.amountSigned } == 0L })
        assertLedgerBalanced(expectedClaims = 200, expectedRemainder = 0, expectedCustody = 200)
    }

    @Test
    fun settlementCorrectionRollsBackInvalidReplacementThenPostsSafeReplacement() = runTest {
        val people = addTwoEmployees()
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE,
                amount = 200,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "منبع تسویه",
                method = MoneyMethod.CASH,
            ),
        )
        val sourceAccountId = database.dao().incomeEvent(incomeId)!!.receiverAccountId
        val originalId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE + 1,
                fromEmployeeId = people.holderId,
                fromAccountId = sourceAccountId,
                toEmployeeId = people.beneficiaryId,
                destinationAccountId = null,
                amount = 40,
                method = MoneyMethod.CASH,
                type = SettlementType.PAYMENT,
                note = "تسویه اولیه",
            ),
        )
        val beforeInvalidCorrection = databaseState()

        expectIllegalArgument {
            repository.correctSettlement(
                CorrectSettlementCommand(
                    originalPaymentId = originalId,
                    dateEpochDay = EVENT_DATE + 2,
                    fromEmployeeId = people.holderId,
                    fromAccountId = sourceAccountId,
                    toEmployeeId = people.beneficiaryId,
                    amount = 101,
                    method = MoneyMethod.CASH,
                    type = SettlementType.PAYMENT,
                    note = "تسویه نامعتبر",
                    correctionReason = "آزمون بازگشت کامل",
                ),
            )
        }
        assertEquals(beforeInvalidCorrection, databaseState())
        assertEquals(RecordStatus.POSTED, database.dao().settlementPayment(originalId)!!.status)

        val replacementId = repository.correctSettlement(
            CorrectSettlementCommand(
                originalPaymentId = originalId,
                dateEpochDay = EVENT_DATE + 2,
                fromEmployeeId = people.holderId,
                fromAccountId = sourceAccountId,
                toEmployeeId = people.beneficiaryId,
                amount = 60,
                method = MoneyMethod.CASH,
                type = SettlementType.PAYMENT,
                note = "تسویه اصلاح‌شده",
                correctionReason = "اصلاح مبلغ تسویه",
            ),
        )

        val original = database.dao().settlementPayment(originalId)!!
        val replacement = database.dao().settlementPayment(replacementId)!!
        assertEquals(RecordStatus.REVERSED, original.status)
        assertNotNull(original.reversedAt)
        assertEquals(RecordStatus.POSTED, replacement.status)
        assertEquals(originalId, replacement.replacesPaymentId)
        assertEquals(60L, replacement.amount)
        assertEquals(60L, database.dao().settlementCustodyAllocations(replacementId).sumOf { it.amount })
        assertEquals(60L, database.dao().settlementClaimAllocations(replacementId).sumOf { it.amount })

        val neutralizedCustody = database.dao().allCustodyPostings().filter {
            it.referenceId == originalId && it.type == CustodyPostingType.SETTLEMENT_PAID
        }
        val neutralizedClaims = database.dao().allClaimPostings().filter {
            it.referenceId == originalId && it.type == ClaimPostingType.SETTLEMENT_RECEIVED
        }
        assertEquals(0L, neutralizedCustody.sumOf { it.amountSigned })
        assertEquals(0L, neutralizedClaims.sumOf { it.amountSigned })
        assertLedgerBalanced(expectedClaims = 140, expectedRemainder = 0, expectedCustody = 140)
    }

    @Test
    fun sequentialIncomeCorrectionsReflowLaterVersionsWithoutDeadlock() = runTest {
        val people = addTwoEmployees()
        val firstId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE, 101, IncomeType.TIP, people.holderId, null, "درآمد اول", MoneyMethod.CASH),
        )
        val secondId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE + 1, 102, IncomeType.TIP, people.holderId, null, "درآمد دوم", MoneyMethod.CASH),
        )
        val thirdId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE + 2, 103, IncomeType.SCRAP, people.holderId, null, "درآمد سوم", MoneyMethod.CASH),
        )

        val correctedThirdId = repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = thirdId,
                dateEpochDay = EVENT_DATE + 2,
                amount = 105,
                type = IncomeType.SCRAP,
                receiverEmployeeId = people.holderId,
                description = "درآمد سوم اصلاح‌شده",
                method = MoneyMethod.CASH,
                correctionReason = "اصلاح درآمد آخر",
            ),
        )
        repository.updateEmployee(
            UpdateEmployeeCommand(
                employeeId = people.beneficiaryId,
                name = "ذی‌نفع",
                phone = "",
                role = EmployeeRole.BODYWORK,
                sharedIncomeEligible = false,
            ),
        )

        val correctedSecondId = repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = secondId,
                dateEpochDay = EVENT_DATE + 1,
                amount = 104,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                description = "درآمد دوم اصلاح‌شده",
                method = MoneyMethod.CASH,
                correctionReason = "اصلاح درآمد میانی پس از درآمد آخر",
            ),
        )
        val replayedThird = database.dao().allIncomeEvents()
            .single { it.status == RecordStatus.POSTED && it.description == "درآمد سوم اصلاح‌شده" }
        val replayedThirdSnapshots = database.dao().eligibilitySnapshots(replayedThird.id)
        assertEquals(2, replayedThirdSnapshots.count { it.status == com.hesabyar.garage.domain.EligibilityStatus.ELIGIBLE })
        assertEquals(RecordStatus.REVERSED, database.dao().incomeEvent(correctedThirdId)!!.status)
        assertEquals(secondId, database.dao().incomeEvent(correctedSecondId)!!.replacesEventId)

        repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = firstId,
                dateEpochDay = EVENT_DATE,
                amount = 100,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                description = "درآمد اول اصلاح‌شده",
                method = MoneyMethod.CASH,
                correctionReason = "اصلاح درآمد قدیمی پس از دو اصلاح",
            ),
        )

        val allEvents = database.dao().allIncomeEvents()
        val active = allEvents.filter { it.status == RecordStatus.POSTED }
        assertEquals(3, active.size)
        assertTrue(active.groupBy { incomeRootId(it, allEvents) }.values.all { it.size == 1 })
        val finalThird = active.single { it.description == "درآمد سوم اصلاح‌شده" }
        assertEquals(
            2,
            database.dao().eligibilitySnapshots(finalThird.id)
                .count { it.status == com.hesabyar.garage.domain.EligibilityStatus.ELIGIBLE },
        )
        assertLedgerBalanced(expectedClaims = 308, expectedRemainder = 1, expectedCustody = 309)
    }

    @Test
    fun deletingMiddleIncomeKeepsAuditHistoryAndReflowsRemainder() = runTest {
        val people = addTwoEmployees()
        repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE, 101, IncomeType.TIP, people.holderId, null, "درآمد اول", MoneyMethod.CASH),
        )
        val middleId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE + 1, 101, IncomeType.TIP, people.holderId, null, "درآمد قابل حذف", MoneyMethod.CASH),
        )
        val lastId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE + 2, 101, IncomeType.SCRAP, people.holderId, null, "درآمد بعدی", MoneyMethod.CASH),
        )
        val lastBefore = database.dao().incomeEvent(lastId)!!
        assertEquals(50L, lastBefore.sharePerPerson)

        deletionHarness.deleteIncome(middleId, "ثبت این درآمد اشتباه بوده است")

        assertEquals(RecordStatus.DELETED, database.dao().incomeEvent(middleId)!!.status)
        assertEquals(0L, database.dao().incomeClaimPostings(YEAR, middleId).sumOf { it.amountSigned })
        assertEquals(0L, database.dao().incomeCustodyPostings(YEAR, middleId).sumOf { it.amountSigned })
        val replayedLast = database.dao().allIncomeEvents()
            .single { it.status == RecordStatus.POSTED && it.description == "درآمد بعدی" }
        assertEquals(51L, replayedLast.sharePerPerson)
        assertEquals(RecordStatus.REVERSED, database.dao().incomeEvent(lastId)!!.status)
        assertEquals(202L, repository.totalIncome(YEAR).first())
        assertTrue(repository.incomeRows(YEAR).first().none { it.id == middleId })
        assertTrue(repository.incomeHistoryRows(YEAR).first().any { it.id == middleId && it.status == "DELETED" })
        assertTrue(database.dao().deletionAudit("INCOME", middleId)!!.description.contains("ثبت این درآمد اشتباه"))
        assertLedgerBalanced(expectedClaims = 202, expectedRemainder = 0, expectedCustody = 202)
    }

    @Test
    fun deletingSettlementAndTransferLeavesDeletedHistoryAndReleasesIncomeSource() = runTest {
        val people = addTwoEmployees()
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE, 200, IncomeType.TIP, people.holderId, null, "منشأ حذف وابسته‌ها", MoneyMethod.CASH),
        )
        val holderAccount = database.dao().incomeEvent(incomeId)!!.receiverAccountId
        val beneficiaryAccount = repository.accountsNow(people.beneficiaryId).single().id
        val transferId = repository.transferCustody(
            TransferCustodyCommand(YEAR, EVENT_DATE + 1, holderAccount, beneficiaryAccount, 80, "انتقال قابل حذف", method = MoneyMethod.CASH),
        )
        val settlementId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = YEAR,
                dateEpochDay = EVENT_DATE + 2,
                fromEmployeeId = people.beneficiaryId,
                fromAccountId = beneficiaryAccount,
                toEmployeeId = people.beneficiaryId,
                destinationAccountId = beneficiaryAccount,
                amount = 40,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "تسویه قابل حذف",
            ),
        )

        val blockedDelete = expectIllegalArgument {
            deletionHarness.deleteIncome(incomeId, "حذف پیش از وابستگی‌ها")
        }
        assertTrue(blockedDelete.message.orEmpty().contains("منشأ انتقال یا تسویه فعال"))
        deletionHarness.deleteSettlement(settlementId, "پرداخت در دنیای واقعی انجام نشده")
        deletionHarness.deleteTransfer(transferId, "انتقال در دنیای واقعی انجام نشده")

        assertEquals(RecordStatus.DELETED, database.dao().settlementPayment(settlementId)!!.status)
        assertEquals(RecordStatus.DELETED, database.dao().custodyTransfer(transferId)!!.status)
        assertTrue(repository.settlementHistory(YEAR).first().any { it.id == settlementId && it.status == RecordStatus.DELETED })
        assertTrue(repository.transferHistory(YEAR).first().any { it.id == transferId && it.status == RecordStatus.DELETED })
        assertTrue(repository.settlements(YEAR).first().none { it.id == settlementId })
        assertTrue(repository.transfers(YEAR).first().none { it.id == transferId })
        assertLedgerBalanced(expectedClaims = 200, expectedRemainder = 0, expectedCustody = 200)

        deletionHarness.deleteIncome(incomeId, "درآمد نیز اشتباه ثبت شده است")
        assertEquals(RecordStatus.DELETED, database.dao().incomeEvent(incomeId)!!.status)
        assertLedgerBalanced(expectedClaims = 0, expectedRemainder = 0, expectedCustody = 0)
    }

    @Test
    fun closedYearDeletionRollsBackWithoutChangingLedger() = runTest {
        val people = addTwoEmployees()
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(YEAR, EVENT_DATE, 200, IncomeType.TIP, people.holderId, null, "درآمد سال بسته", MoneyMethod.CASH),
        )
        val year = database.dao().financialYear(YEAR)!!
        database.dao().updateFinancialYear(year.copy(status = com.hesabyar.garage.domain.FinancialYearStatus.CLOSED))
        val before = databaseState()

        val failure = expectIllegalArgument {
            deletionHarness.deleteIncome(incomeId, "حذف غیرمجاز در سال بسته")
        }

        assertTrue(failure.message.orEmpty().contains("بسته شده"))
        assertEquals(before, databaseState())
    }

    @Test
    fun annualCloseSnapshotNetsReversedAndReplacementPostings() = runTest {
        val year = 1403
        val eventDate = PersianDate(year, 2, 10).toEpochDay()
        val people = addTwoEmployees(year)
        val originalIncomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = year,
                dateEpochDay = eventDate,
                amount = 200,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                receiverAccountId = null,
                description = "Original income before close",
                method = MoneyMethod.CASH,
            ),
        )
        val replacementIncomeId = repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = originalIncomeId,
                dateEpochDay = eventDate + 1,
                amount = 240,
                type = IncomeType.TIP,
                receiverEmployeeId = people.holderId,
                description = "Corrected income before close",
                method = MoneyMethod.CASH,
                correctionReason = "Correct close snapshot income",
            ),
        )
        val holderAccountId = database.dao().incomeEvent(replacementIncomeId)!!.receiverAccountId
        val beneficiaryAccountId = repository.accountsNow(people.beneficiaryId).single().id

        val originalTransferId = repository.transferCustody(
            TransferCustodyCommand(
                financialYear = year,
                dateEpochDay = eventDate + 2,
                fromAccountId = holderAccountId,
                toAccountId = beneficiaryAccountId,
                amount = 80,
                note = "Original transfer before close",
                method = MoneyMethod.CASH,
            ),
        )
        repository.correctTransfer(
            CorrectTransferCommand(
                originalTransferId = originalTransferId,
                dateEpochDay = eventDate + 3,
                fromAccountId = holderAccountId,
                toAccountId = beneficiaryAccountId,
                amount = 60,
                method = MoneyMethod.CASH,
                note = "Corrected transfer before close",
                correctionReason = "Correct close snapshot transfer",
            ),
        )

        val originalSelfOffsetId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = year,
                dateEpochDay = eventDate + 4,
                fromEmployeeId = people.holderId,
                fromAccountId = holderAccountId,
                toEmployeeId = people.holderId,
                destinationAccountId = null,
                amount = 100,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Original self offset before close",
            ),
        )
        repository.correctSettlement(
            CorrectSettlementCommand(
                originalPaymentId = originalSelfOffsetId,
                dateEpochDay = eventDate + 5,
                fromEmployeeId = people.holderId,
                fromAccountId = holderAccountId,
                toEmployeeId = people.holderId,
                amount = 120,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Corrected self offset before close",
                correctionReason = "Correct close snapshot settlement",
            ),
        )
        repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = year,
                dateEpochDay = eventDate + 6,
                fromEmployeeId = people.holderId,
                fromAccountId = holderAccountId,
                toEmployeeId = people.beneficiaryId,
                destinationAccountId = null,
                amount = 60,
                method = MoneyMethod.CASH,
                type = SettlementType.PAYMENT,
                note = "Final payment before close",
            ),
        )
        repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = year,
                dateEpochDay = eventDate + 7,
                fromEmployeeId = people.beneficiaryId,
                fromAccountId = beneficiaryAccountId,
                toEmployeeId = people.beneficiaryId,
                destinationAccountId = null,
                amount = 60,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Final beneficiary offset before close",
            ),
        )

        val beforeClose = repository.balances(year).first()
        assertTrue(beforeClose.all { it.claim == 0L && it.custody == 0L })
        assertEquals(0L, database.dao().remainder(year))
        val closeId = repository.closeFinancialYear(
            year = year,
            backupChecksum = "close-snapshot-test",
            backupAt = System.currentTimeMillis() + 1_000,
        )
        val snapshots = database.dao().allAnnualCloseSnapshots()
            .filter { it.closeId == closeId }
            .associateBy { it.employeeId }
        val holderSnapshot = snapshots.getValue(people.holderId)
        val beneficiarySnapshot = snapshots.getValue(people.beneficiaryId)

        assertEquals(120L, holderSnapshot.totalClaim)
        assertEquals(240L, holderSnapshot.totalCustodyIn)
        assertEquals(240L, holderSnapshot.totalCustodyOut)
        assertEquals(60L, holderSnapshot.totalSettlementPaid)
        assertEquals(0L, holderSnapshot.totalSettlementReceived)
        assertEquals(0L, holderSnapshot.finalNet)
        assertEquals(120L, beneficiarySnapshot.totalClaim)
        assertEquals(60L, beneficiarySnapshot.totalCustodyIn)
        assertEquals(60L, beneficiarySnapshot.totalCustodyOut)
        assertEquals(0L, beneficiarySnapshot.totalSettlementPaid)
        assertEquals(60L, beneficiarySnapshot.totalSettlementReceived)
        assertEquals(0L, beneficiarySnapshot.finalNet)
    }

    private suspend fun addTwoEmployees(year: Int = YEAR): People {
        val startDate = PersianDate(year, 1, 1).toEpochDay()
        val holderId = repository.addEmployee(
            AddEmployeeCommand("نگهدارنده", "", EmployeeRole.PAINTER, startDate),
        )
        val beneficiaryId = repository.addEmployee(
            AddEmployeeCommand("ذی‌نفع", "", EmployeeRole.BODYWORK, startDate),
        )
        return People(holderId, beneficiaryId)
    }

    private suspend fun assertLedgerBalanced(
        expectedClaims: Long,
        expectedRemainder: Long,
        expectedCustody: Long,
    ) {
        val balances = repository.balances(YEAR).first()
        val claims = balances.sumOf { it.claim }
        val remainder = database.dao().remainder(YEAR)
        val custody = balances.sumOf { it.custody }
        assertEquals(expectedClaims, claims)
        assertEquals(expectedRemainder, remainder)
        assertEquals(expectedCustody, custody)
        assertEquals(custody, Math.addExact(claims, remainder))
    }

    private suspend fun expectIllegalArgument(block: suspend () -> Unit): IllegalArgumentException {
        var failure: IllegalArgumentException? = null
        try {
            block()
        } catch (throwable: IllegalArgumentException) {
            failure = throwable
        }
        assertNotNull("انتظار می‌رفت تراکنش نامعتبر رد شود", failure)
        return failure!!
    }

    private suspend fun databaseState(): DatabaseState {
        val dao = database.dao()
        return DatabaseState(
            accounts = dao.allAccounts(),
            financialYears = dao.allFinancialYears(),
            incomeEvents = dao.allIncomeEvents(),
            eligibility = dao.allEligibilitySnapshots(),
            claims = dao.allClaimPostings(),
            custody = dao.allCustodyPostings(),
            transfers = dao.allCustodyTransfers(),
            transferAllocations = dao.allCustodyTransferAllocations(),
            settlements = dao.allSettlementPayments(),
            settlementClaimAllocations = dao.allSettlementClaimAllocations(),
            settlementCustodyAllocations = dao.allSettlementCustodyAllocations(),
            audit = dao.allAuditLogs(),
        )
    }

    private fun incomeRootId(event: IncomeEventEntity, allEvents: List<IncomeEventEntity>): Long {
        val byId = allEvents.associateBy { it.id }
        var current = event
        while (true) {
            val previousId = current.replacesEventId ?: break
            current = byId.getValue(previousId)
        }
        return current.id
    }

    private data class People(val holderId: Long, val beneficiaryId: Long)

    private data class DatabaseState(
        val accounts: List<MoneyAccountEntity>,
        val financialYears: List<FinancialYearEntity>,
        val incomeEvents: List<IncomeEventEntity>,
        val eligibility: List<EligibilitySnapshotEntity>,
        val claims: List<ClaimPostingEntity>,
        val custody: List<CustodyPostingEntity>,
        val transfers: List<CustodyTransferEntity>,
        val transferAllocations: List<CustodyTransferAllocationEntity>,
        val settlements: List<SettlementPaymentEntity>,
        val settlementClaimAllocations: List<SettlementClaimAllocationEntity>,
        val settlementCustodyAllocations: List<SettlementCustodyAllocationEntity>,
        val audit: List<AuditLogEntity>,
    )

    private companion object {
        const val YEAR = 1405
        val EVENT_DATE = PersianDate(YEAR, 2, 10).toEpochDay()
    }
}
