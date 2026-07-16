package com.hesabyar.garage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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
        ).build()
    }
}
