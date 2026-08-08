package com.hesabyar.garage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.AccountType
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.security.BackupCrypto
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class GarageRepositoryInstrumentedTest {
    private lateinit var db: GarageDatabase
    private lateinit var repository: GarageRepository
    private lateinit var settings: SettingsRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        runBlocking { settings.setAbsenceGraceDays(14) }
        repository = GarageRepository(db, CardCrypto(), settings)
    }

    @After fun close() = db.close()

    @Test fun incomeTransferAndBalancesRemainConsistent() = runTest {
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val date = PersianDate(1405, 2, 10).toEpochDay()
        val a = repository.addEmployee(AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, start))
        val b = repository.addEmployee(AddEmployeeCommand("شمس‌الله", "", EmployeeRole.BODYWORK, start))
        val c = repository.addEmployee(AddEmployeeCommand("علی", "", EmployeeRole.BODYWORK, start))
        val accountA = repository.accountsNow(a).single().id
        val accountB = repository.accountsNow(b).single().id
        repository.registerIncome(RegisterIncomeCommand(1405, date, 900, IncomeType.TIP, a, accountA, "تست"))

        val initial = repository.balances(1405).first().associateBy { it.employeeId }
        assertEquals(-600, initial.getValue(a).net)
        assertEquals(300, initial.getValue(b).net)
        assertEquals(300, initial.getValue(c).net)

        repository.transferCustody(TransferCustodyCommand(1405, date, accountA, accountB, 100, "انتقال جزئی"))
        val after = repository.balances(1405).first().associateBy { it.employeeId }
        assertEquals(-500, after.getValue(a).net)
        assertEquals(200, after.getValue(b).net)
        assertEquals(300, after.getValue(c).net)
        assertEquals(900, after.values.sumOf { it.custody })
        assertEquals(900, after.values.sumOf { it.claim })
    }

    @Test fun encryptedBackupRestoresCompleteDataAndCard() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CardCrypto()
        val service = BackupService(context, db, settings, crypto)
        settings.setAbsenceGraceDays(21)
        val employee = repository.addEmployee(AddEmployeeCommand("قاسم", "09120000000", EmployeeRole.PAINTER, PersianDate(1405, 1, 1).toEpochDay()))
        repository.addCard(AddCardCommand(employee, "کارت ملت", "قاسم", "ملت", "5892107047168045"))
        val movementDate = PersianDate(1405, 2, 10).toEpochDay()
        repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = 1405,
                dateEpochDay = movementDate,
                amount = 100,
                type = IncomeType.TIP,
                receiverEmployeeId = employee,
                receiverAccountId = null,
                description = "دریافت کارتی",
                method = MoneyMethod.CARD,
                cardNumber = "5892107047168045",
                movementDateEpochDay = movementDate,
                movementTimeMinutes = 14 * 60 + 30,
            ),
        )
        repository.updateEmployee(
            UpdateEmployeeCommand(employee, "قاسم", "09120000000", EmployeeRole.PAINTER, sharedIncomeEligible = false),
        )
        val export = service.export("backup-password".toCharArray())
        assertFalse(export.encryptedBytes.toString(Charsets.UTF_8).contains("589210"))

        db.clearAllTables()
        assertTrue(db.dao().allEmployees().isEmpty())
        val summary = service.restore(export.encryptedBytes, "backup-password".toCharArray())
        assertEquals(1, summary.employeeCount)
        assertEquals("قاسم", db.dao().allEmployees().single().name)
        assertFalse(db.dao().allEmployees().single().sharedIncomeEligible)
        val restoredCard = db.dao().allAccounts().first { it.cardLast4 == "8045" }
        assertEquals("5892107047168045", crypto.decrypt(restoredCard.cardNumberEncrypted))
        val restoredIncome = db.dao().allIncomeEvents().single()
        assertEquals(MoneyMethod.CARD, restoredIncome.method)
        assertEquals("8045", restoredIncome.cardLast4)
        assertEquals(14 * 60 + 30, restoredIncome.movementTimeMinutes)
        assertEquals("5892107047168045", crypto.decrypt(restoredIncome.cardNumberEncrypted))
        assertEquals(21, settings.settings.first().absenceGraceDays)
    }

    @Test fun cardMovementCanBeRecordedWithoutCardNumberOrExactTime() = runTest {
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val employee = repository.addEmployee(AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, start))
        val eventId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = 1405,
                dateEpochDay = PersianDate(1405, 2, 10).toEpochDay(),
                amount = 100,
                type = IncomeType.TIP,
                receiverEmployeeId = employee,
                receiverAccountId = null,
                description = "بدون جزئیات کارت",
                method = MoneyMethod.CARD,
            ),
        )

        val event = repository.incomeEvent(eventId)!!
        assertEquals(MoneyMethod.CARD, event.method)
        assertTrue(event.cardNumberEncrypted.isBlank())
        assertNull(event.movementDateEpochDay)
        assertNull(event.movementTimeMinutes)
    }

    @Test fun explicitMovementMethodsDoNotRequireProfileCardsAndKeepLedgerBalanced() = runTest {
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val date = PersianDate(1405, 2, 10).toEpochDay()
        val payer = repository.addEmployee(AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, start))
        val receiver = repository.addEmployee(AddEmployeeCommand("علی", "", EmployeeRole.BODYWORK, start))
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(1405, date, 100, IncomeType.TIP, payer, null, "کارت بدون شماره", MoneyMethod.CARD),
        )
        val sourceAccount = repository.incomeEvent(incomeId)!!.receiverAccountId
        val transferId = repository.transferCustody(
            TransferCustodyCommand(
                financialYear = 1405,
                dateEpochDay = date,
                fromAccountId = sourceAccount,
                toAccountId = null,
                amount = 20,
                note = "انتقال کارتی",
                toEmployeeId = receiver,
                method = MoneyMethod.CARD,
            ),
        )
        val paymentId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = 1405,
                dateEpochDay = date,
                fromEmployeeId = payer,
                fromAccountId = sourceAccount,
                toEmployeeId = receiver,
                destinationAccountId = null,
                amount = 30,
                method = MoneyMethod.CARD,
                type = com.hesabyar.garage.domain.SettlementType.PAYMENT,
                note = "تسویه بدون شماره کارت",
            ),
        )

        assertEquals(MoneyMethod.CARD, db.dao().custodyTransfer(transferId)!!.method)
        assertTrue(db.dao().custodyTransfer(transferId)!!.cardNumberEncrypted.isBlank())
        assertEquals(MoneyMethod.CARD, db.dao().settlementPayment(paymentId)!!.method)
        assertTrue(db.dao().settlementPayment(paymentId)!!.cardNumberEncrypted.isBlank())
        val balances = repository.balances(1405).first()
        assertTrue(balances.all { it.net == 0L })
        assertTrue(balances.none { it.isFullySettled })
        assertEquals(50, balances.first { it.employeeId == payer }.totalEntitlement)
        assertEquals(0, balances.first { it.employeeId == payer }.settledEntitlement)
        assertEquals(50, balances.first { it.employeeId == receiver }.totalEntitlement)
        assertEquals(30, balances.first { it.employeeId == receiver }.settledEntitlement)
        assertEquals(balances.sumOf { it.claim }, balances.sumOf { it.custody })

        var failure: Throwable? = null
        try {
            repository.registerIncome(
                RegisterIncomeCommand(1405, date, 10, IncomeType.TIP, payer, null, "شماره نامعتبر", MoneyMethod.CARD, "1234"),
            )
        } catch (throwable: Throwable) {
            failure = throwable
        }
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun editingEmployeeAndOpenAbsenceUsesNewPolicyWithoutChangingPostedSnapshot() = runTest {
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val eventDate = start + 9
        val returnDate = start + 10
        val absentEmployee = repository.addEmployee(AddEmployeeCommand("علی", "09120000000", EmployeeRole.BODYWORK, start))
        val presentEmployee = repository.addEmployee(AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, start))
        val absenceId = repository.addAbsence(absentEmployee, start, null, "سفر")
        val account = repository.accountsNow(presentEmployee).single().id

        settings.setAbsenceGraceDays(14)
        val eventId = repository.registerIncome(RegisterIncomeCommand(1405, eventDate, 200, IncomeType.TIP, presentEmployee, account, "قبل از تغییر قانون"))
        val postedSnapshot = repository.eligibility(eventId).first().first { it.employeeId == absentEmployee }
        val postedClaim = repository.balances(1405).first().first { it.employeeId == absentEmployee }.claim
        assertTrue(postedSnapshot.status == com.hesabyar.garage.domain.EligibilityStatus.ELIGIBLE)

        settings.setAbsenceGraceDays(5)
        assertFalse(repository.previewEligibility(eventDate).first { it.employee.id == absentEmployee }.result.isEligible)
        repository.updateAbsence(absenceId, absentEmployee, start, returnDate, "بازگشت ثبت شد")
        repository.updateEmployee(UpdateEmployeeCommand(absentEmployee, "علی رضایی", "09350000000", EmployeeRole.BOTH, "ویرایش‌شده"))

        val updated = repository.employee(absentEmployee)!!
        assertEquals("علی رضایی", updated.name)
        assertEquals(EmployeeRole.BOTH, updated.role)
        assertEquals(returnDate, repository.absence(absenceId)!!.returnEpochDay)
        assertTrue(repository.previewEligibility(returnDate).first { it.employee.id == absentEmployee }.result.isEligible)
        assertEquals(postedSnapshot, repository.eligibility(eventId).first().first { it.employeeId == absentEmployee })
        assertEquals(postedClaim, repository.balances(1405).first().first { it.employeeId == absentEmployee }.claim)
    }

    @Test fun employeeWithoutSharedIncomeShareCanReceiveAndMoveMoneyWithoutChangingFrozenHistory() = runTest {
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val eventDate = start + 5
        val holder = repository.addEmployee(
            AddEmployeeCommand("نگهدارنده", "", EmployeeRole.OTHER, start, sharedIncomeEligible = false),
        )
        val beneficiary = repository.addEmployee(AddEmployeeCommand("صافکار", "", EmployeeRole.BODYWORK, start))

        val firstEvent = repository.registerIncome(
            RegisterIncomeCommand(1405, eventDate, 600, IncomeType.TIP, holder, null, "دریافت توسط فرد بدون سهم", MoneyMethod.CASH),
        )
        val firstSnapshot = repository.eligibility(firstEvent).first().first { it.employeeId == holder }
        assertFalse(firstSnapshot.status == com.hesabyar.garage.domain.EligibilityStatus.ELIGIBLE)
        assertEquals("SHARED_INCOME_DISABLED", firstSnapshot.reasonCode)

        val firstBalances = repository.balances(1405).first().associateBy { it.employeeId }
        assertEquals(0, firstBalances.getValue(holder).claim)
        assertEquals(600, firstBalances.getValue(holder).custody)
        assertEquals(0, firstBalances.getValue(holder).totalEntitlement)
        assertEquals(600, firstBalances.getValue(beneficiary).claim)

        val original = repository.employee(holder)!!
        repository.updateEmployee(
            UpdateEmployeeCommand(original.id, original.name, original.phone, original.role, original.notes, sharedIncomeEligible = true),
        )
        assertEquals(firstSnapshot, repository.eligibility(firstEvent).first().first { it.employeeId == holder })

        repository.registerIncome(
            RegisterIncomeCommand(1405, eventDate + 1, 400, IncomeType.SCRAP, holder, null, "پس از روشن‌کردن سهم", MoneyMethod.CASH),
        )
        val secondBalances = repository.balances(1405).first().associateBy { it.employeeId }
        assertEquals(200, secondBalances.getValue(holder).claim)
        assertEquals(200, secondBalances.getValue(holder).totalEntitlement)
        assertEquals(1_000, secondBalances.getValue(holder).custody)

        val holderAccount = repository.accountsNow(holder).first { it.type == AccountType.CASH }
        repository.transferCustody(
            TransferCustodyCommand(1405, eventDate + 2, holderAccount.id, null, 100, "انتقال مجاز", toEmployeeId = beneficiary, method = MoneyMethod.CASH),
        )
        val finalBalances = repository.balances(1405).first().associateBy { it.employeeId }
        assertEquals(900, finalBalances.getValue(holder).custody)
        assertEquals(100, finalBalances.getValue(beneficiary).custody)
    }

    @Test fun schemaTwoBackupWithoutAbsencePolicyRestoresDefaultFourteenDays() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BackupService(context, db, settings, CardCrypto())
        repository.addEmployee(AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, PersianDate(1405, 1, 1).toEpochDay()))
        settings.setAbsenceGraceDays(30)
        val password = "legacy-password".toCharArray()
        val export = service.export(password.copyOf())
        val outer = JSONObject(
            BackupCrypto.decrypt(export.encryptedBytes.toString(Charsets.UTF_8), password.copyOf()).toString(Charsets.UTF_8),
        )
        val payload = JSONObject(outer.getString("payload"))
        payload.getJSONObject("settings").remove("absenceGraceDays")
        val legacyPayload = payload.toString()
        outer.put("schemaVersion", 2)
        outer.put("payload", legacyPayload)
        outer.put("checksum", BackupCrypto.checksum(legacyPayload.toByteArray(Charsets.UTF_8)))
        val legacyBytes = BackupCrypto.encrypt(outer.toString().toByteArray(Charsets.UTF_8), password.copyOf()).toByteArray(Charsets.UTF_8)

        service.restore(legacyBytes, password.copyOf())
        assertEquals(14, settings.settings.first().absenceGraceDays)
    }

    @Test fun schemaThreeBackupWithoutEmployeeShareSettingRestoresEligibleByDefault() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BackupService(context, db, settings, CardCrypto())
        repository.addEmployee(
            AddEmployeeCommand("قاسم", "", EmployeeRole.PAINTER, PersianDate(1405, 1, 1).toEpochDay(), sharedIncomeEligible = false),
        )
        val password = "legacy-share-password".toCharArray()
        val export = service.export(password.copyOf())
        val outer = JSONObject(
            BackupCrypto.decrypt(export.encryptedBytes.toString(Charsets.UTF_8), password.copyOf()).toString(Charsets.UTF_8),
        )
        val payload = JSONObject(outer.getString("payload"))
        payload.getJSONArray("employees").getJSONObject(0).remove("sharedIncomeEligible")
        val legacyPayload = payload.toString()
        outer.put("schemaVersion", 3)
        outer.put("payload", legacyPayload)
        outer.put("checksum", BackupCrypto.checksum(legacyPayload.toByteArray(Charsets.UTF_8)))
        val legacyBytes = BackupCrypto.encrypt(outer.toString().toByteArray(Charsets.UTF_8), password.copyOf()).toByteArray(Charsets.UTF_8)

        service.restore(legacyBytes, password.copyOf())
        assertTrue(db.dao().allEmployees().single().sharedIncomeEligible)
    }
}
