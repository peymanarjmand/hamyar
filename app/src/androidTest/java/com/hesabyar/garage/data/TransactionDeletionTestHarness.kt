package com.hesabyar.garage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.hesabyar.garage.security.CredentialHasher

internal class TransactionDeletionTestHarness private constructor(
    private val service: TransactionDeletionService,
    private val securityDataStore: DataStore<Preferences>,
    private val password: CharArray,
) {
    suspend fun deleteIncome(eventId: Long, reason: String) {
        service.deleteIncome(eventId, reason, password.copyOf()).requireSuccess()
    }

    suspend fun deleteTransfer(transferId: Long, reason: String) {
        service.deleteTransfer(transferId, reason, password.copyOf()).requireSuccess()
    }

    suspend fun deleteSettlement(paymentId: Long, reason: String) {
        service.deleteSettlement(paymentId, reason, password.copyOf()).requireSuccess()
    }

    suspend fun close() {
        securityDataStore.edit { it.clear() }
        password.fill('\u0000')
    }

    companion object {
        suspend fun create(context: Context, repository: GarageRepository): TransactionDeletionTestHarness {
            val security = AppSecurityRepository(
                context = context,
                credentialHasher = CredentialHasher(iterations = 1_000),
            )
            val dataStore = security.dataStoreForDeletionTest()
            dataStore.edit { it.clear() }
            val password = "رمزِحذف‌آزمایشی۱۲۳".toCharArray()
            security.enableLock(
                password = password.copyOf(),
                recoveryQuestion = "پرسش بازیابی آزمایشی چیست؟",
                recoveryAnswer = "پاسخ بازیابی آزمایشی".toCharArray(),
            ).requireSuccess()
            return TransactionDeletionTestHarness(
                service = TransactionDeletionService(security, repository),
                securityDataStore = dataStore,
                password = password,
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun AppSecurityRepository.dataStoreForDeletionTest(): DataStore<Preferences> {
    val field = AppSecurityRepository::class.java.getDeclaredField("dataStore")
    field.isAccessible = true
    return field.get(this) as DataStore<Preferences>
}

private fun <T> SecurityResult<T>.requireSuccess(): T = when (this) {
    is SecurityResult.Success -> value
    SecurityResult.Incorrect -> error("رمز آزمایشی حذف پذیرفته نشد")
    is SecurityResult.Blocked -> error("مجوز آزمایشی حذف تا $retryAtEpochMillis مسدود شد")
}
