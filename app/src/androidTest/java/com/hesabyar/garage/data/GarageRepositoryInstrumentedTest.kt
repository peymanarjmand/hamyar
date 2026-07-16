package com.hesabyar.garage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.security.CardCrypto
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GarageRepositoryInstrumentedTest {
    private lateinit var db: GarageDatabase
    private lateinit var repository: GarageRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java).allowMainThreadQueries().build()
        repository = GarageRepository(db, CardCrypto())
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
        val settings = SettingsRepository(context)
        val crypto = CardCrypto()
        val service = BackupService(context, db, settings, crypto)
        val employee = repository.addEmployee(AddEmployeeCommand("قاسم", "09120000000", EmployeeRole.PAINTER, PersianDate(1405, 1, 1).toEpochDay()))
        repository.addCard(AddCardCommand(employee, "کارت ملت", "قاسم", "ملت", "5892107047168045"))
        val export = service.export("backup-password".toCharArray())
        assertFalse(export.encryptedBytes.toString(Charsets.UTF_8).contains("589210"))

        db.clearAllTables()
        assertTrue(db.dao().allEmployees().isEmpty())
        val summary = service.restore(export.encryptedBytes, "backup-password".toCharArray())
        assertEquals(1, summary.employeeCount)
        assertEquals("قاسم", db.dao().allEmployees().single().name)
        val restoredCard = db.dao().allAccounts().first { it.cardLast4 == "8045" }
        assertEquals("5892107047168045", crypto.decrypt(restoredCard.cardNumberEncrypted))
    }
}
