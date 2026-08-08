package com.hesabyar.garage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.domain.AccountType
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.domain.RecordStatus
import com.hesabyar.garage.domain.SettlementType
import com.hesabyar.garage.security.BackupCrypto
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSchema6InstrumentedTest {
    private lateinit var db: GarageDatabase
    private lateinit var repository: GarageRepository
    private lateinit var settings: SettingsRepository
    private lateinit var deletionHarness: TransactionDeletionTestHarness

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        runBlocking { settings.setAbsenceGraceDays(14) }
        repository = GarageRepository(db, CardCrypto(), settings)
        runBlocking { deletionHarness = TransactionDeletionTestHarness.create(context, repository) }
    }

    @After
    fun close() {
        runBlocking { if (::deletionHarness.isInitialized) deletionHarness.close() }
        db.close()
    }

    @Test
    fun schemaSixBackupRoundTripsDeletionPosAndCorrectionLinks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BackupService(context, db, settings, CardCrypto())
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val date = PersianDate(1405, 2, 10).toEpochDay()
        val holder = repository.addEmployee(AddEmployeeCommand("Holder", "", EmployeeRole.OTHER, start))
        val beneficiary = repository.addEmployee(AddEmployeeCommand("Beneficiary", "", EmployeeRole.PAINTER, start))

        val originalIncomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = 1405,
                dateEpochDay = date,
                amount = 100,
                type = IncomeType.TIP,
                receiverEmployeeId = holder,
                receiverAccountId = null,
                description = "Original POS income",
                method = MoneyMethod.POS,
                movementDateEpochDay = date,
                movementTimeMinutes = 9 * 60,
            ),
        )
        val replacementIncomeId = repository.correctIncome(
            CorrectIncomeCommand(
                originalEventId = originalIncomeId,
                dateEpochDay = date,
                amount = 120,
                type = IncomeType.SCRAP,
                receiverEmployeeId = holder,
                description = "Corrected POS income",
                method = MoneyMethod.POS,
                movementDateEpochDay = date,
                movementTimeMinutes = 9 * 60 + 15,
                correctionReason = "Correct test amount",
            ),
        )
        val accounts = repository.accountsNow(holder)
        val posAccount = accounts.single { it.type == AccountType.POS }
        val beneficiaryCashAccount = repository.accountsNow(beneficiary).single { it.type == AccountType.CASH }

        val originalTransferId = repository.transferCustody(
            TransferCustodyCommand(
                financialYear = 1405,
                dateEpochDay = date + 1,
                fromAccountId = posAccount.id,
                toAccountId = beneficiaryCashAccount.id,
                amount = 20,
                note = "Original transfer",
                method = MoneyMethod.CASH,
            ),
        )
        val replacementTransferId = repository.correctTransfer(
            CorrectTransferCommand(
                originalTransferId = originalTransferId,
                dateEpochDay = date + 1,
                fromAccountId = posAccount.id,
                toAccountId = beneficiaryCashAccount.id,
                amount = 25,
                method = MoneyMethod.CASH,
                note = "Corrected transfer",
                correctionReason = "Correct test amount",
            ),
        )

        val originalPaymentId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = 1405,
                dateEpochDay = date + 2,
                fromEmployeeId = beneficiary,
                fromAccountId = beneficiaryCashAccount.id,
                toEmployeeId = beneficiary,
                destinationAccountId = null,
                amount = 10,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Original self offset",
            ),
        )
        val replacementPaymentId = repository.correctSettlement(
            CorrectSettlementCommand(
                originalPaymentId = originalPaymentId,
                dateEpochDay = date + 2,
                fromEmployeeId = beneficiary,
                fromAccountId = beneficiaryCashAccount.id,
                toEmployeeId = beneficiary,
                amount = 15,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Corrected self offset",
                correctionReason = "Correct test amount",
            ),
        )
        deletionHarness.deleteSettlement(replacementPaymentId, "Delete corrected settlement in backup test")
        deletionHarness.deleteTransfer(replacementTransferId, "Delete corrected transfer in backup test")
        deletionHarness.deleteIncome(replacementIncomeId, "Delete corrected income in backup test")

        val password = "schema-five-password".toCharArray()
        val export = service.export(password.copyOf())
        val outer = JSONObject(
            BackupCrypto.decrypt(export.encryptedBytes.toString(Charsets.UTF_8), password.copyOf())
                .toString(Charsets.UTF_8),
        )
        assertEquals(6, outer.getInt("schemaVersion"))
        val payload = JSONObject(outer.getString("payload"))
        val accountJson = payload.getJSONArray("accounts")
        val posAccountJson = (0 until accountJson.length())
            .map { accountJson.getJSONObject(it) }
            .single { it.getLong("id") == posAccount.id }
        assertEquals("POS", posAccountJson.getString("type"))
        val incomeJson = payload.getJSONArray("incomeEvents")
        val replacementIncomeJson = (0 until incomeJson.length())
            .map { incomeJson.getJSONObject(it) }
            .single { it.getLong("id") == replacementIncomeId }
        assertEquals("POS", replacementIncomeJson.getString("method"))
        assertEquals(originalIncomeId, replacementIncomeJson.getLong("replacesEventId"))
        assertEquals("DELETED", replacementIncomeJson.getString("status"))
        val transferJson = payload.getJSONArray("transfers")
        val replacementTransferJson = (0 until transferJson.length())
            .map { transferJson.getJSONObject(it) }
            .single { it.getLong("id") == replacementTransferId }
        assertEquals(originalTransferId, replacementTransferJson.getLong("replacesTransferId"))
        assertEquals("DELETED", replacementTransferJson.getString("status"))
        val settlementJson = payload.getJSONArray("settlements")
        val replacementPaymentJson = (0 until settlementJson.length())
            .map { settlementJson.getJSONObject(it) }
            .single { it.getLong("id") == replacementPaymentId }
        assertEquals(originalPaymentId, replacementPaymentJson.getLong("replacesPaymentId"))
        assertEquals("DELETED", replacementPaymentJson.getString("status"))

        db.clearAllTables()
        service.restore(export.encryptedBytes, password.copyOf())

        assertEquals(AccountType.POS, db.dao().account(posAccount.id)!!.type)
        assertEquals(RecordStatus.REVERSED, db.dao().incomeEvent(originalIncomeId)!!.status)
        val restoredIncome = db.dao().incomeEvent(replacementIncomeId)!!
        assertEquals(MoneyMethod.POS, restoredIncome.method)
        assertEquals(originalIncomeId, restoredIncome.replacesEventId)
        assertEquals(RecordStatus.DELETED, restoredIncome.status)
        assertEquals(RecordStatus.REVERSED, db.dao().custodyTransfer(originalTransferId)!!.status)
        assertEquals(originalTransferId, db.dao().custodyTransfer(replacementTransferId)!!.replacesTransferId)
        assertEquals(RecordStatus.DELETED, db.dao().custodyTransfer(replacementTransferId)!!.status)
        assertEquals(RecordStatus.REVERSED, db.dao().settlementPayment(originalPaymentId)!!.status)
        assertEquals(originalPaymentId, db.dao().settlementPayment(replacementPaymentId)!!.replacesPaymentId)
        assertEquals(RecordStatus.DELETED, db.dao().settlementPayment(replacementPaymentId)!!.status)
        assertEquals(3, db.dao().allAuditLogs().count { it.action == "DELETE" })
    }

    @Test
    fun schemaFiveBackupFromVersion14StillRestores() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BackupService(context, db, settings, CardCrypto())
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val date = PersianDate(1405, 2, 20).toEpochDay()
        val holder = repository.addEmployee(AddEmployeeCommand("Schema 5 holder", "", EmployeeRole.OTHER, start))
        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = 1405,
                dateEpochDay = date,
                amount = 101,
                type = IncomeType.TIP,
                receiverEmployeeId = holder,
                receiverAccountId = null,
                description = "Schema 5 income",
                method = MoneyMethod.CASH,
            ),
        )
        val passwordText = "schema-five-version-14"
        val export = service.export(passwordText.toCharArray())
        val outer = JSONObject(
            BackupCrypto.decrypt(
                export.encryptedBytes.toString(Charsets.UTF_8),
                passwordText.toCharArray(),
            ).toString(Charsets.UTF_8),
        ).put("schemaVersion", 5)
        val legacyBytes = BackupCrypto.encrypt(
            outer.toString().toByteArray(Charsets.UTF_8),
            passwordText.toCharArray(),
        ).toByteArray(Charsets.UTF_8)

        db.clearAllTables()
        service.restore(legacyBytes, passwordText.toCharArray())

        assertEquals("Schema 5 holder", db.dao().employee(holder)!!.name)
        assertEquals(RecordStatus.POSTED, db.dao().incomeEvent(incomeId)!!.status)
    }

    @Test
    fun schemaFourBackupFromVersion13RestoresWithoutCorrectionLinks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = BackupService(context, db, settings, CardCrypto())
        val start = PersianDate(1405, 1, 1).toEpochDay()
        val date = PersianDate(1405, 3, 12).toEpochDay()
        val holder = repository.addEmployee(AddEmployeeCommand("Version 1.3 holder", "", EmployeeRole.OTHER, start))
        val beneficiary = repository.addEmployee(AddEmployeeCommand("Version 1.3 beneficiary", "", EmployeeRole.PAINTER, start))
        val holderCash = repository.accountsNow(holder).single { it.type == AccountType.CASH }
        val beneficiaryCash = repository.accountsNow(beneficiary).single { it.type == AccountType.CASH }

        val incomeId = repository.registerIncome(
            RegisterIncomeCommand(
                financialYear = 1405,
                dateEpochDay = date,
                amount = 100,
                type = IncomeType.TIP,
                receiverEmployeeId = holder,
                receiverAccountId = holderCash.id,
                description = "Version 1.3 income",
                method = MoneyMethod.CASH,
            ),
        )
        val transferId = repository.transferCustody(
            TransferCustodyCommand(
                financialYear = 1405,
                dateEpochDay = date + 1,
                fromAccountId = holderCash.id,
                toAccountId = beneficiaryCash.id,
                amount = 50,
                note = "Version 1.3 transfer",
                method = MoneyMethod.CASH,
            ),
        )
        val paymentId = repository.recordSettlement(
            RecordSettlementCommand(
                financialYear = 1405,
                dateEpochDay = date + 2,
                fromEmployeeId = beneficiary,
                fromAccountId = beneficiaryCash.id,
                toEmployeeId = beneficiary,
                destinationAccountId = null,
                amount = 50,
                method = MoneyMethod.CASH,
                type = SettlementType.SELF_OFFSET,
                note = "Version 1.3 self offset",
            ),
        )

        val passwordText = "schema-four-version-13"
        val export = service.export(passwordText.toCharArray())
        val outer = JSONObject(
            BackupCrypto.decrypt(
                export.encryptedBytes.toString(Charsets.UTF_8),
                passwordText.toCharArray(),
            ).toString(Charsets.UTF_8),
        )
        val payload = JSONObject(outer.getString("payload"))
        val transfers = payload.getJSONArray("transfers")
        for (index in 0 until transfers.length()) {
            transfers.getJSONObject(index).remove("replacesTransferId")
        }
        val settlements = payload.getJSONArray("settlements")
        for (index in 0 until settlements.length()) {
            settlements.getJSONObject(index).remove("replacesPaymentId")
        }
        val legacyPayload = payload.toString()
        val legacyOuter = outer
            .put("schemaVersion", 4)
            .put("payload", legacyPayload)
            .put("checksum", BackupCrypto.checksum(legacyPayload.toByteArray(Charsets.UTF_8)))
        val legacyBytes = BackupCrypto.encrypt(
            legacyOuter.toString().toByteArray(Charsets.UTF_8),
            passwordText.toCharArray(),
        ).toByteArray(Charsets.UTF_8)

        db.clearAllTables()
        val summary = service.restore(legacyBytes, passwordText.toCharArray())

        assertEquals(2, summary.employeeCount)
        assertEquals(1, summary.incomeCount)
        assertEquals("Version 1.3 holder", db.dao().employee(holder)!!.name)
        assertEquals("Version 1.3 beneficiary", db.dao().employee(beneficiary)!!.name)
        assertEquals(incomeId, db.dao().incomeEvent(incomeId)!!.id)
        assertNull(db.dao().custodyTransfer(transferId)!!.replacesTransferId)
        assertNull(db.dao().settlementPayment(paymentId)!!.replacesPaymentId)
        assertEquals(1, db.dao().custodyTransferAllocations(transferId).size)
        assertEquals(1, db.dao().settlementClaimAllocations(paymentId).size)
        assertEquals(1, db.dao().settlementCustodyAllocations(paymentId).size)
        assertEquals(50L, db.dao().claimBalance(1405, holder))
        assertEquals(0L, db.dao().claimBalance(1405, beneficiary))
        assertEquals(50L, db.dao().custodyBalance(1405, holder))
        assertEquals(0L, db.dao().custodyBalance(1405, beneficiary))
    }
}
