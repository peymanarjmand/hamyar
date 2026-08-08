package com.hesabyar.garage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EmployeeEntity::class,
        EmploymentPeriodEntity::class,
        AbsencePeriodEntity::class,
        MoneyAccountEntity::class,
        FinancialYearEntity::class,
        IncomeEventEntity::class,
        EligibilitySnapshotEntity::class,
        ClaimPostingEntity::class,
        CustodyPostingEntity::class,
        CustodyTransferEntity::class,
        CustodyTransferAllocationEntity::class,
        SettlementPaymentEntity::class,
        SettlementClaimAllocationEntity::class,
        SettlementCustodyAllocationEntity::class,
        AnnualCloseEntity::class,
        AnnualCloseEmployeeSnapshotEntity::class,
        AuditLogEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun dao(): GarageDao

    companion object {
        fun create(context: Context): GarageDatabase = Room.databaseBuilder(
            context.applicationContext,
            GarageDatabase::class.java,
            "hesabyar-garage.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("income_events", "custody_transfers", "settlement_payments").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN cardNumberEncrypted TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN cardLast4 TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN movementDateEpochDay INTEGER")
                    db.execSQL("ALTER TABLE $table ADD COLUMN movementTimeMinutes INTEGER")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN sharedIncomeEligible INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custody_transfers ADD COLUMN replacesTransferId INTEGER")
                db.execSQL("ALTER TABLE settlement_payments ADD COLUMN replacesPaymentId INTEGER")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_income_events_replacesEventId ON income_events(replacesEventId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custody_transfers_replacesTransferId ON custody_transfers(replacesTransferId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_settlement_payments_replacesPaymentId ON settlement_payments(replacesPaymentId)")
            }
        }
    }
}
