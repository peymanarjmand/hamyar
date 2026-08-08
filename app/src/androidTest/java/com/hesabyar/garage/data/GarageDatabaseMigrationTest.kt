package com.hesabyar.garage.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hesabyar.garage.data.local.GarageDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GarageDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun deleteOldDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration1To4PreservesRowsAndAddsMovementMetadataShareSettingAndRevisionLinks() {
        createDatabaseFromExportedSchema(1)

        val migrated = Room.databaseBuilder(context, GarageDatabase::class.java, TEST_DB)
            .addMigrations(GarageDatabase.MIGRATION_1_2, GarageDatabase.MIGRATION_2_3, GarageDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val database = migrated.openHelper.writableDatabase

        listOf("income_events", "custody_transfers", "settlement_payments").forEach { table ->
            database.query(
                "SELECT cardNumberEncrypted, cardLast4, movementDateEpochDay, movementTimeMinutes FROM `$table` WHERE id=1",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertNull(if (cursor.isNull(2)) null else cursor.getLong(2))
                assertNull(if (cursor.isNull(3)) null else cursor.getInt(3))
            }
        }
        assertCommandId(database, "income_events", "income-v1")
        assertCommandId(database, "custody_transfers", "transfer-v1")
        assertCommandId(database, "settlement_payments", "settlement-v1")
        assertEmployeeShareSetting(database, expected = true)
        assertRevisionLinksNull(database)
        assertRevisionIndexes(database)
        migrated.close()
    }

    @Test
    fun migration2To4DefaultsExistingEmployeesToEligibleAndPersistsChanges() {
        createDatabaseFromExportedSchema(2)

        val migrated = Room.databaseBuilder(context, GarageDatabase::class.java, TEST_DB)
            .addMigrations(GarageDatabase.MIGRATION_2_3, GarageDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val database = migrated.openHelper.writableDatabase

        assertEmployeeShareSetting(database, expected = true)
        database.execSQL("UPDATE employees SET sharedIncomeEligible=0 WHERE id=1")
        assertEmployeeShareSetting(database, expected = false)
        assertCommandId(database, "income_events", "income-v1")
        assertRevisionLinksNull(database)
        migrated.close()
    }

    @Test
    fun migration3To4PreservesFinancialGraphAndAddsNullRevisionLinksAndUniqueIndexes() {
        createDatabaseFromExportedSchema(3)

        val migrated = Room.databaseBuilder(context, GarageDatabase::class.java, TEST_DB)
            .addMigrations(GarageDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val database = migrated.openHelper.writableDatabase

        assertVersionThreeFinancialGraph(database)
        assertRevisionLinksNull(database)
        assertRevisionIndexes(database)
        database.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse("migration introduced a foreign-key violation", cursor.moveToFirst())
        }
        migrated.close()
    }

    private fun assertCommandId(database: SupportSQLiteDatabase, table: String, expected: String) {
        database.query("SELECT commandId FROM `$table` WHERE id=1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private fun assertEmployeeShareSetting(database: SupportSQLiteDatabase, expected: Boolean) {
        database.query("SELECT sharedIncomeEligible FROM employees WHERE id=1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0) == 1)
        }
    }

    private fun assertRevisionLinksNull(database: SupportSQLiteDatabase) {
        listOf(
            Triple("income_events", "replacesEventId", 1L),
            Triple("custody_transfers", "replacesTransferId", 1L),
            Triple("settlement_payments", "replacesPaymentId", 1L),
        ).forEach { (table, column, id) ->
            database.query("SELECT `$column` FROM `$table` WHERE id=$id").use { cursor ->
                assertTrue("missing seeded row in $table", cursor.moveToFirst())
                assertTrue("$table.$column must default to null", cursor.isNull(0))
            }
        }
    }

    private fun assertRevisionIndexes(database: SupportSQLiteDatabase) {
        assertUniqueIndex(database, "income_events", "index_income_events_replacesEventId", "replacesEventId")
        assertUniqueIndex(database, "custody_transfers", "index_custody_transfers_replacesTransferId", "replacesTransferId")
        assertUniqueIndex(database, "settlement_payments", "index_settlement_payments_replacesPaymentId", "replacesPaymentId")
    }

    private fun assertUniqueIndex(database: SupportSQLiteDatabase, table: String, index: String, column: String) {
        var found = false
        database.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == index) {
                    found = true
                    assertEquals("$index must be unique", 1, cursor.getInt(uniqueColumn))
                }
            }
        }
        assertTrue("missing index $index", found)
        database.query("PRAGMA index_info(`$index`)").use { cursor ->
            assertTrue("missing indexed column for $index", cursor.moveToFirst())
            assertEquals(column, cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertFalse("$index must cover exactly one column", cursor.moveToNext())
        }
    }

    private fun assertVersionThreeFinancialGraph(database: SupportSQLiteDatabase) {
        database.query("SELECT name, phone, sharedIncomeEligible FROM employees WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("الف", cursor.getString(0))
            assertEquals("09120000000", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        database.query("SELECT type, cardNumberEncrypted, cardLast4, cardFingerprint FROM money_accounts WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CARD", cursor.getString(0))
            assertEquals("cipher-account", cursor.getString(1))
            assertEquals("8045", cursor.getString(2))
            assertEquals("fingerprint-account", cursor.getString(3))
        }
        database.query("SELECT amount, method, cardNumberEncrypted, cardLast4, movementDateEpochDay, movementTimeMinutes FROM income_events WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(120L, cursor.getLong(0))
            assertEquals("CARD", cursor.getString(1))
            assertEquals("cipher-income", cursor.getString(2))
            assertEquals("8045", cursor.getString(3))
            assertEquals(11L, cursor.getLong(4))
            assertEquals(750, cursor.getInt(5))
        }
        database.query("SELECT amount, cardNumberEncrypted, movementTimeMinutes FROM custody_transfers WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20L, cursor.getLong(0))
            assertEquals("cipher-transfer", cursor.getString(1))
            assertEquals(780, cursor.getInt(2))
        }
        database.query("SELECT amount, cardNumberEncrypted, movementTimeMinutes FROM settlement_payments WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(30L, cursor.getLong(0))
            assertEquals("cipher-settlement", cursor.getString(1))
            assertEquals(810, cursor.getInt(2))
        }
        assertSingleLong(database, "SELECT amount FROM custody_transfer_allocations WHERE transferId=1 AND sourceIncomeEventId=1", 20L)
        assertSingleLong(database, "SELECT amount FROM settlement_claim_allocations WHERE paymentId=1 AND sourceIncomeEventId=1", 30L)
        assertSingleLong(database, "SELECT amount FROM settlement_custody_allocations WHERE paymentId=1 AND sourceIncomeEventId=1", 30L)
        assertSingleLong(database, "SELECT COUNT(*) FROM claim_postings", 3L)
        assertSingleLong(database, "SELECT COUNT(*) FROM custody_postings", 4L)
        assertSingleLong(database, "SELECT COUNT(*) FROM audit_logs", 3L)
    }

    private fun assertSingleLong(database: SupportSQLiteDatabase, query: String, expected: Long) {
        database.query(query).use { cursor ->
            assertTrue("query returned no row: $query", cursor.moveToFirst())
            assertEquals(expected, cursor.getLong(0))
        }
    }

    private fun createDatabaseFromExportedSchema(version: Int) {
        val schemaPath = "com.hesabyar.garage.data.local.GarageDatabase/$version.json"
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val root = testAssets.open(schemaPath).bufferedReader().use { JSONObject(it.readText()) }
        val schema = root.getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        repeat(entities.length()) { index ->
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices")
                            repeat(indices?.length() ?: 0) { indexIndex ->
                                db.execSQL(requireNotNull(indices).getJSONObject(indexIndex).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val setupQueries = schema.getJSONArray("setupQueries")
                        repeat(setupQueries.length()) { db.execSQL(setupQueries.getString(it)) }
                        if (version == 3) seedVersionThreeRows(db) else seedVersionOneRows(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase
        helper.close()
    }

    private fun seedVersionOneRows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO employees (id,name,phone,role,notes,createdAt,archivedAt) VALUES (1,'الف','','PAINTER','',1,NULL),(2,'ب','','BODYWORK','',1,NULL)")
        db.execSQL("INSERT INTO money_accounts (id,employeeId,type,title,ownerName,bankName,cardNumberEncrypted,cardLast4,cardFingerprint,createdAt,archivedAt) VALUES (1,1,'CASH','نقد الف','الف','','','',NULL,1,NULL),(2,2,'CASH','نقد ب','ب','','','',NULL,1,NULL)")
        db.execSQL("INSERT INTO financial_years (year,startEpochDay,endEpochDay,status,createdAt,closedAt) VALUES (1405,0,365,'OPEN',1,NULL)")
        db.execSQL("INSERT INTO income_events (id,commandId,financialYear,dateEpochDay,amount,type,receiverEmployeeId,receiverAccountId,method,description,carriedRemainder,sharePerPerson,resultingRemainder,status,createdAt,reversedAt,replacesEventId) VALUES (1,'income-v1',1405,10,100,'TIP',1,1,'CASH','',0,50,0,'POSTED',1,NULL,NULL)")
        db.execSQL("INSERT INTO custody_transfers (id,commandId,financialYear,dateEpochDay,fromEmployeeId,fromAccountId,toEmployeeId,toAccountId,amount,method,note,status,createdAt,reversedAt) VALUES (1,'transfer-v1',1405,10,1,1,2,2,10,'CASH','','POSTED',1,NULL)")
        db.execSQL("INSERT INTO settlement_payments (id,commandId,financialYear,dateEpochDay,fromEmployeeId,fromAccountId,toEmployeeId,destinationAccountId,amount,method,type,note,status,createdAt,reversedAt) VALUES (1,'settlement-v1',1405,10,1,1,2,2,10,'CASH','PAYMENT','','POSTED',1,NULL)")
    }

    private fun seedVersionThreeRows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO employees (id,name,phone,role,notes,createdAt,archivedAt,sharedIncomeEligible) VALUES (1,'الف','09120000000','PAINTER','قدیمی',1,NULL,0),(2,'ب','','BODYWORK','',1,NULL,1)")
        db.execSQL("INSERT INTO money_accounts (id,employeeId,type,title,ownerName,bankName,cardNumberEncrypted,cardLast4,cardFingerprint,createdAt,archivedAt) VALUES (1,1,'CARD','کارت الف','الف','ملت','cipher-account','8045','fingerprint-account',1,NULL),(2,2,'CASH','نقد ب','ب','','','',NULL,1,NULL)")
        db.execSQL("INSERT INTO financial_years (year,startEpochDay,endEpochDay,status,createdAt,closedAt) VALUES (1405,0,365,'OPEN',1,NULL)")
        db.execSQL("INSERT INTO income_events (id,commandId,financialYear,dateEpochDay,amount,type,receiverEmployeeId,receiverAccountId,method,description,carriedRemainder,sharePerPerson,resultingRemainder,status,createdAt,reversedAt,replacesEventId,cardNumberEncrypted,cardLast4,movementDateEpochDay,movementTimeMinutes) VALUES (1,'income-v3',1405,10,120,'TIP',1,1,'CARD','درآمد نسخه سه',0,60,0,'POSTED',1,NULL,NULL,'cipher-income','8045',11,750)")
        db.execSQL("INSERT INTO eligibility_snapshots (id,eventId,employeeId,status,reasonCode,reasonFa,employmentPeriodId,absencePeriodId,absenceDay,shareAmount,evaluatedAt) VALUES (1,1,1,'ELIGIBLE','PRESENT','حاضر',NULL,NULL,NULL,60,1),(2,1,2,'ELIGIBLE','PRESENT','حاضر',NULL,NULL,NULL,60,1)")
        db.execSQL("INSERT INTO claim_postings (id,financialYear,sourceIncomeEventId,employeeId,amountSigned,type,referenceId,businessDateEpochDay,createdAt) VALUES (1,1405,1,1,60,'ENTITLEMENT',1,10,1),(2,1405,1,2,60,'ENTITLEMENT',1,10,1),(3,1405,1,2,-30,'SETTLEMENT_RECEIVED',1,12,1)")
        db.execSQL("INSERT INTO custody_postings (id,financialYear,sourceIncomeEventId,employeeId,accountId,amountSigned,type,referenceId,businessDateEpochDay,createdAt) VALUES (1,1405,1,1,1,120,'INCOME_RECEIPT',1,10,1),(2,1405,1,1,1,-20,'TRANSFER_OUT',1,11,1),(3,1405,1,2,2,20,'TRANSFER_IN',1,11,1),(4,1405,1,1,1,-30,'SETTLEMENT_PAID',1,12,1)")
        db.execSQL("INSERT INTO custody_transfers (id,commandId,financialYear,dateEpochDay,fromEmployeeId,fromAccountId,toEmployeeId,toAccountId,amount,method,note,status,createdAt,reversedAt,cardNumberEncrypted,cardLast4,movementDateEpochDay,movementTimeMinutes) VALUES (1,'transfer-v3',1405,11,1,1,2,2,20,'CARD','انتقال نسخه سه','POSTED',1,NULL,'cipher-transfer','8045',11,780)")
        db.execSQL("INSERT INTO custody_transfer_allocations (transferId,sourceIncomeEventId,amount) VALUES (1,1,20)")
        db.execSQL("INSERT INTO settlement_payments (id,commandId,financialYear,dateEpochDay,fromEmployeeId,fromAccountId,toEmployeeId,destinationAccountId,amount,method,type,note,status,createdAt,reversedAt,cardNumberEncrypted,cardLast4,movementDateEpochDay,movementTimeMinutes) VALUES (1,'settlement-v3',1405,12,1,1,2,2,30,'CARD','PAYMENT','تسویه نسخه سه','POSTED',1,NULL,'cipher-settlement','8045',12,810)")
        db.execSQL("INSERT INTO settlement_claim_allocations (paymentId,sourceIncomeEventId,amount) VALUES (1,1,30)")
        db.execSQL("INSERT INTO settlement_custody_allocations (paymentId,sourceIncomeEventId,amount) VALUES (1,1,30)")
        db.execSQL("INSERT INTO audit_logs (id,financialYear,action,entityType,entityId,description,createdAt) VALUES (1,1405,'POST','INCOME',1,'ثبت درآمد',1),(2,1405,'POST','CUSTODY_TRANSFER',1,'ثبت انتقال',2),(3,1405,'POST','SETTLEMENT',1,'ثبت تسویه',3)")
    }

    private companion object {
        const val TEST_DB = "hesabyar-migration-test"
    }
}
