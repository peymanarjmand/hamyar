package com.hesabyar.garage.data

import com.hesabyar.garage.data.local.*
import org.junit.Assert.*
import org.junit.Test

class BackupReflectionContractTest {
    @Test fun `all backed up entities expose a constructor matching explicit backup schema`() {
        val classes = listOf(
            EmployeeEntity::class.java,
            EmploymentPeriodEntity::class.java,
            AbsencePeriodEntity::class.java,
            MoneyAccountEntity::class.java,
            FinancialYearEntity::class.java,
            IncomeEventEntity::class.java,
            EligibilitySnapshotEntity::class.java,
            ClaimPostingEntity::class.java,
            CustodyPostingEntity::class.java,
            CustodyTransferEntity::class.java,
            CustodyTransferAllocationEntity::class.java,
            SettlementPaymentEntity::class.java,
            SettlementClaimAllocationEntity::class.java,
            SettlementCustodyAllocationEntity::class.java,
            AnnualCloseEntity::class.java,
            AnnualCloseEmployeeSnapshotEntity::class.java,
            AuditLogEntity::class.java,
        )
        classes.forEach { clazz ->
            val fields = backupFieldNames(clazz).map(clazz::getDeclaredField)
            val constructor = clazz.declaredConstructors.firstOrNull { it.parameterCount == fields.size }
            assertNotNull("missing full constructor for ${clazz.simpleName}", constructor)
            constructor!!.parameterTypes.zip(fields.map { it.type }).forEachIndexed { index, (parameter, field) ->
                assertEquals("field order mismatch in ${clazz.simpleName} at $index", boxed(field), boxed(parameter))
            }
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        else -> type
    }
}
