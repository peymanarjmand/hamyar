package com.hesabyar.garage.data

/**
 * Keeps password verification and the requested financial mutation in one
 * controlled operation. An unlocked session or device authentication is never
 * accepted as authorization for deleting a transaction.
 */
internal class TransactionDeletionService(
    private val appSecurityRepository: AppSecurityRepository,
    private val garageRepository: GarageRepository,
) {
    private val authority = Any()

    init {
        garageRepository.bindTransactionDeletionAuthority(authority)
    }

    suspend fun deleteIncome(eventId: Long, reason: String, password: CharArray): SecurityResult<Unit> =
        authorize(password) { garageRepository.deleteIncomeAfterAuthorization(eventId, reason, authority) }

    suspend fun deleteTransfer(transferId: Long, reason: String, password: CharArray): SecurityResult<Unit> =
        authorize(password) { garageRepository.deleteTransferAfterAuthorization(transferId, reason, authority) }

    suspend fun deleteSettlement(paymentId: Long, reason: String, password: CharArray): SecurityResult<Unit> =
        authorize(password) { garageRepository.deleteSettlementAfterAuthorization(paymentId, reason, authority) }

    private suspend fun authorize(
        password: CharArray,
        mutation: suspend () -> Unit,
    ): SecurityResult<Unit> = try {
        when (val result = appSecurityRepository.verifyPassword(password)) {
            is SecurityResult.Success -> {
                mutation()
                SecurityResult.Success(Unit)
            }
            SecurityResult.Incorrect -> SecurityResult.Incorrect
            is SecurityResult.Blocked -> result
        }
    } finally {
        password.fill('\u0000')
    }
}
