package com.hesabyar.garage.domain

import org.junit.Assert.*
import org.junit.Test

class AccountingEngineTest {
    @Test fun `nine hundred is split exactly among three`() {
        val result = AccountingEngine.distribute(900, 0, 3)
        assertEquals(300, result.sharePerPerson)
        assertEquals(0, result.resultingRemainder)
    }

    @Test fun `remainder is carried into next income`() {
        val first = AccountingEngine.distribute(1_000, 0, 3)
        assertEquals(333, first.sharePerPerson)
        assertEquals(1, first.resultingRemainder)
        val next = AccountingEngine.distribute(899, first.resultingRemainder, 3)
        assertEquals(300, next.sharePerPerson)
        assertEquals(0, next.resultingRemainder)
    }

    @Test fun `distribution invariant holds for many values`() {
        for (amount in 1L..2_000L) for (count in 1..12) {
            val result = AccountingEngine.distribute(amount, 0, count)
            assertEquals(amount, result.sharePerPerson * count + result.resultingRemainder)
        }
    }

    @Test fun `settlement separates self offset then real payments`() {
        val suggestions = AccountingEngine.suggestSettlement(
            listOf(
                PersonBalance(1, "قاسم", claim = 300, custody = 900),
                PersonBalance(2, "شمس‌الله", claim = 300, custody = 0),
                PersonBalance(3, "علی", claim = 300, custody = 0),
            ),
        )
        assertEquals(3, suggestions.size)
        assertEquals(SettlementType.SELF_OFFSET, suggestions.first().type)
        assertEquals(300, suggestions.first().amount)
        assertEquals(600, suggestions.drop(1).sumOf { it.amount })
    }

    @Test fun `custody transfer changes debt holder not total`() {
        val before = listOf(
            PersonBalance(1, "قاسم", 300, 900),
            PersonBalance(2, "شمس‌الله", 300, 0),
            PersonBalance(3, "علی", 300, 0),
        )
        val transfer = 100L
        val after = listOf(
            before[0].copy(custody = before[0].custody - transfer),
            before[1].copy(custody = before[1].custody + transfer),
            before[2],
        )
        assertEquals(before.sumOf { it.custody }, after.sumOf { it.custody })
        assertEquals(-500, after[0].net)
        assertEquals(200, after[1].net)
        assertEquals(300, after[2].net)
    }
}
