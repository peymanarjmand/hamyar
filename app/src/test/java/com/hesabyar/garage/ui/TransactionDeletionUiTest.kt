package com.hesabyar.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDeletionUiTest {
    @Test
    fun retrySecondsRoundsUpAndStopsAtBoundary() {
        assertEquals(0L, deletionRetrySeconds(1_000L, 1_000L))
        assertEquals(1L, deletionRetrySeconds(1_001L, 1_000L))
        assertEquals(1L, deletionRetrySeconds(2_000L, 1_001L))
        assertEquals(2L, deletionRetrySeconds(2_001L, 1_000L))
        assertEquals(0L, deletionRetrySeconds(999L, 1_000L))
    }
}
