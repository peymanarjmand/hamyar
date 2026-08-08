package com.hesabyar.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DashboardItemKeyTest {
    @Test
    fun `same numeric id in balance and income sections produces unique keys`() {
        val balanceKey = dashboardItemKey(DashboardItemSection.BALANCE, 1)
        val incomeKey = dashboardItemKey(DashboardItemSection.INCOME, 1)

        assertNotEquals(balanceKey, incomeKey)
        assertEquals("balance:1", balanceKey)
        assertEquals("income:1", incomeKey)
    }

    @Test
    fun `different rows in one dashboard section retain unique keys`() {
        assertNotEquals(
            dashboardItemKey(DashboardItemSection.BALANCE, 1),
            dashboardItemKey(DashboardItemSection.BALANCE, 2),
        )
    }
}
