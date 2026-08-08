package com.hesabyar.garage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.hesabyar.garage.HesabYarApplication
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.data.local.GarageDao
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.domain.RecordStatus
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.security.CredentialHasher
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSecurityRepositoryInstrumentedTest {
    private lateinit var application: HesabYarApplication
    private lateinit var repository: AppSecurityRepository
    private lateinit var securityDataStore: DataStore<Preferences>
    private lateinit var financialDao: GarageDao
    private var nowMillis = 1_700_000_000_000L

    @Before
    fun setUp() {
        runBlocking {
            application = ApplicationProvider.getApplicationContext()
            repository = AppSecurityRepository(
                context = application,
                credentialHasher = CredentialHasher(iterations = 1_000),
                currentTimeMillis = { nowMillis },
            )
            securityDataStore = repository.securityDataStoreForTest()
            financialDao = application.container.database.dao()

            // The production DataStore has a fixed name and process-wide singleton. Clearing it
            // through its own API makes this single comprehensive test repeatable without deleting
            // or recreating the independent Room financial database.
            securityDataStore.edit { it.clear() }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            if (::securityDataStore.isInitialized) {
                securityDataStore.edit { it.clear() }
            }
        }
    }

    @Test
    fun optionalLockSupportsPersianCredentialsRecoveryAndIndependentCleanup() = runTest {
        val initialSettings = repository.settings.first()
        assertFalse(initialSettings.lockEnabled)
        assertFalse(initialSettings.deviceAuthEnabled)
        assertEquals(AppSecurityRepository.DEFAULT_LOCK_TIMEOUT_SECONDS, initialSettings.lockTimeoutSeconds)

        val initialPassword = "رمزِفارسی۱۲۳".toCharArray()
        val recoveryQuestion = "نام اولین استاد شما چه بود؟"
        val storedRecoveryAnswer = "كيان\u200C۱۲۳".toCharArray()
        val recoveryCode = repository.enableLock(
            password = initialPassword,
            recoveryQuestion = recoveryQuestion,
            recoveryAnswer = storedRecoveryAnswer,
        ).successValue()

        assertEquals(CredentialHasher.RECOVERY_CODE_LENGTH, recoveryCode.length)
        assertTrue(recoveryCode.all { it in '0'..'9' })
        repository.settings.first().also { enabled ->
            assertTrue(enabled.lockEnabled)
            assertFalse(enabled.deviceAuthEnabled)
            assertEquals(recoveryQuestion, enabled.recoveryQuestion)
            assertTrue(enabled.hasRecoveryCode)
            assertEquals(AppSecurityRepository.DEFAULT_LOCK_TIMEOUT_SECONDS, enabled.lockTimeoutSeconds)
        }

        assertSame(SecurityResult.Incorrect, repository.verifyPassword("رمز نادرست".toCharArray()))
        repository.verifyPassword(initialPassword).successValue()

        val changedPassword = "گذرواژه\u200Cنو۱۲۳".toCharArray()
        repository.changePassword(initialPassword, changedPassword).successValue()
        assertSame(SecurityResult.Incorrect, repository.verifyPassword(initialPassword))
        repository.verifyPassword(changedPassword).successValue()

        // Stored Arabic ي/ك, Persian digits and ZWNJ normalize to this Persian/ASCII form.
        val answerNormalizedOnReset = "  کیان   123  ".toCharArray()
        val answerResetPassword = "رمزبازیابی۴۵۶".toCharArray()
        repository.resetPasswordWithRecoveryAnswer(
            recoveryAnswer = answerNormalizedOnReset,
            newPassword = answerResetPassword,
        ).successValue()
        repository.verifyPassword(answerResetPassword).successValue()

        val localizedRecoveryCode = recoveryCode
            .map(::toPersianDigit)
            .chunked(5)
            .joinToString(" - ") { it.joinToString("") }
        val codeResetPassword = "رمزنهایی۷۸۹".toCharArray()
        val rotatedRecoveryCode = repository.resetPasswordWithRecoveryCode(
            recoveryCode = localizedRecoveryCode,
            newPassword = codeResetPassword,
        ).successValue()

        assertEquals(CredentialHasher.RECOVERY_CODE_LENGTH, rotatedRecoveryCode.length)
        assertTrue(rotatedRecoveryCode.all { it in '0'..'9' })
        assertTrue(rotatedRecoveryCode != recoveryCode)
        assertSame(SecurityResult.Incorrect, repository.verifyRecoveryCode(recoveryCode))
        repository.verifyRecoveryCode(rotatedRecoveryCode).successValue()
        repository.verifyPassword(codeResetPassword).successValue()

        repeat(4) {
            assertSame(SecurityResult.Incorrect, repository.verifyPassword("حدس اشتباه".toCharArray()))
        }
        val blocked = repository.verifyPassword("حدس پنجم".toCharArray()) as SecurityResult.Blocked
        val recreatedRepository = AppSecurityRepository(
            context = application,
            credentialHasher = CredentialHasher(iterations = 1_000),
            currentTimeMillis = { nowMillis },
        )
        assertTrue(recreatedRepository.verifyPassword(codeResetPassword) is SecurityResult.Blocked)
        nowMillis = blocked.retryAtEpochMillis
        recreatedRepository.verifyPassword(codeResetPassword).successValue()
        repository = recreatedRepository

        repository.setDeviceAuthEnabled(true)
        assertTrue(repository.settings.first().deviceAuthEnabled)
        repository.recordDeviceAuthenticationSuccess()
        repository.setDeviceAuthEnabled(false)
        assertFalse(repository.settings.first().deviceAuthEnabled)

        val financialDataBeforeDisable = financialDao.completeSnapshot()
        repository.disableLock(codeResetPassword).successValue()
        val financialDataAfterDisable = financialDao.completeSnapshot()

        assertEquals(financialDataBeforeDisable, financialDataAfterDisable)
        assertTrue(securityDataStore.data.first().asMap().isEmpty())
        repository.settings.first().also { disabled ->
            assertFalse(disabled.lockEnabled)
            assertFalse(disabled.deviceAuthEnabled)
            assertEquals("", disabled.recoveryQuestion)
            assertFalse(disabled.hasRecoveryCode)
            assertEquals(AppSecurityRepository.DEFAULT_LOCK_TIMEOUT_SECONDS, disabled.lockTimeoutSeconds)
        }
    }

    @Test
    fun transactionDeletionRequiresExactAppPasswordBeforeFinancialMutation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settings = SettingsRepository(context)
            settings.setAbsenceGraceDays(14)
            val garage = GarageRepository(database, CardCrypto(), settings)
            val deletionService = TransactionDeletionService(repository, garage)
            val start = PersianDate(1405, 1, 1).toEpochDay()
            val date = PersianDate(1405, 2, 10).toEpochDay()
            val holder = garage.addEmployee(AddEmployeeCommand("دارنده", "", EmployeeRole.OTHER, start))
            val incomeId = garage.registerIncome(
                RegisterIncomeCommand(1405, date, 100, IncomeType.TIP, holder, null, "ثبت نیازمند رمز", MoneyMethod.CASH),
            )

            val noPasswordFailure = runCatching {
                deletionService.deleteIncome(incomeId, "حذف بدون تعریف رمز", "هر رمزی".toCharArray())
            }.exceptionOrNull()
            assertTrue(noPasswordFailure is IllegalArgumentException)
            assertEquals(RecordStatus.POSTED, database.dao().incomeEvent(incomeId)!!.status)

            val exactPassword = "رمزِحذف۱۲۳".toCharArray()
            repository.enableLock(
                password = exactPassword.copyOf(),
                recoveryQuestion = "پرسش بازیابی حذف چیست؟",
                recoveryAnswer = "پاسخ بازیابی".toCharArray(),
            ).successValue()
            val wrongPassword = "رمزِحذف123".toCharArray()
            assertSame(
                SecurityResult.Incorrect,
                deletionService.deleteIncome(incomeId, "حذف با رمز اشتباه", wrongPassword),
            )
            assertTrue(wrongPassword.all { it == '\u0000' })
            assertEquals(RecordStatus.POSTED, database.dao().incomeEvent(incomeId)!!.status)

            val submittedPassword = exactPassword.copyOf()
            deletionService.deleteIncome(incomeId, "درآمد اشتباه ثبت شده", submittedPassword).successValue()
            assertTrue(submittedPassword.all { it == '\u0000' })
            assertEquals(RecordStatus.DELETED, database.dao().incomeEvent(incomeId)!!.status)
            assertEquals(0L, database.dao().claimPostingsForYear(1405).sumOf { it.amountSigned })
            assertEquals(0L, database.dao().custodyPostingsForYear(1405).sumOf { it.amountSigned })
        } finally {
            database.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AppSecurityRepository.securityDataStoreForTest(): DataStore<Preferences> {
        val field = AppSecurityRepository::class.java.getDeclaredField("dataStore")
        field.isAccessible = true
        return field.get(this) as DataStore<Preferences>
    }

    private suspend fun GarageDao.completeSnapshot(): List<List<*>> = listOf(
        allEmployees(),
        allEmploymentPeriods(),
        allAbsences(),
        allAccounts(),
        allFinancialYears(),
        allIncomeEvents(),
        allEligibilitySnapshots(),
        allClaimPostings(),
        allCustodyPostings(),
        allCustodyTransfers(),
        allCustodyTransferAllocations(),
        allSettlementPayments(),
        allSettlementClaimAllocations(),
        allSettlementCustodyAllocations(),
        allAnnualCloses(),
        allAnnualCloseSnapshots(),
        allAuditLogs(),
    )

    private fun <T> SecurityResult<T>.successValue(): T = when (this) {
        is SecurityResult.Success -> value
        SecurityResult.Incorrect -> throw AssertionError("Expected successful security operation, but credentials were rejected")
        is SecurityResult.Blocked -> throw AssertionError("Expected successful security operation, but it was throttled")
    }

    private fun toPersianDigit(value: Char): Char = when (value) {
        '0' -> '۰'
        '1' -> '۱'
        '2' -> '۲'
        '3' -> '۳'
        '4' -> '۴'
        '5' -> '۵'
        '6' -> '۶'
        '7' -> '۷'
        '8' -> '۸'
        '9' -> '۹'
        else -> value
    }
}
