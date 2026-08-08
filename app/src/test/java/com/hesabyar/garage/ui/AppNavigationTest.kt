package com.hesabyar.garage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun `home always pops to root and requests scroll from every route`() {
        val routes = listOf(
            AppRoute.HOME,
            AppRoute.PEOPLE,
            "employee/{id}",
            "income/{id}",
            "income/{id}/edit",
            "transfer",
            "transfer/{id}/edit",
            "settlement/{id}",
            "settlement/{id}/edit",
            "history",
            "year-close",
            AppRoute.PRIVACY,
            AppRoute.SECURITY,
        )

        routes.forEach { route ->
            val plan = topLevelNavigationPlan(route, AppRoute.HOME)
            assertEquals(TopLevelNavigationAction.POP_TO_ROOT, plan.action)
            assertEquals(AppRoute.HOME, plan.targetRoute)
            assertTrue(plan.requestHomeScroll)
        }
    }

    @Test
    fun `nested routes select their owning top level destination`() {
        val expected = mapOf(
            "employee/add" to AppRoute.PEOPLE,
            "employee/{id}/edit" to AppRoute.PEOPLE,
            "employee/{id}/absence" to AppRoute.PEOPLE,
            "employee/{id}/absence/{absenceId}" to AppRoute.PEOPLE,
            "employee/{id}/employment/{periodId}" to AppRoute.PEOPLE,
            "income/{id}" to AppRoute.INCOME,
            "income/{id}/edit" to AppRoute.INCOME,
            "transfer" to AppRoute.SETTLEMENT,
            "transfer/{id}" to AppRoute.SETTLEMENT,
            "transfer/{id}/edit" to AppRoute.SETTLEMENT,
            "settlement/{id}" to AppRoute.SETTLEMENT,
            "settlement/{id}/edit" to AppRoute.SETTLEMENT,
            "history" to AppRoute.MORE,
            "year-close" to AppRoute.MORE,
            AppRoute.PRIVACY to AppRoute.MORE,
            AppRoute.SECURITY to AppRoute.MORE,
        )

        expected.forEach { (route, parent) -> assertEquals(parent, parentTopLevelRoute(route)) }
    }

    @Test
    fun `reselecting a non-home root is a no-op and nested child pops to root`() {
        assertEquals(
            TopLevelNavigationAction.NONE,
            topLevelNavigationPlan(AppRoute.PEOPLE, AppRoute.PEOPLE).action,
        )
        assertEquals(
            TopLevelNavigationAction.POP_TO_ROOT,
            topLevelNavigationPlan("employee/{id}", AppRoute.PEOPLE).action,
        )
        assertEquals(
            TopLevelNavigationAction.NAVIGATE,
            topLevelNavigationPlan("employee/{id}", AppRoute.INCOME).action,
        )
    }

    @Test
    fun `correction replacement removes editor and reversed detail from every parent stack`() {
        val plans = listOf(
            incomeCorrectionNavigationPlan(12) to listOf(AppRoute.MORE, "history", AppRoute.INCOME_DETAIL, "income/{id}/edit"),
            transferCorrectionNavigationPlan(13) to listOf(AppRoute.SETTLEMENT, AppRoute.TRANSFER_DETAIL, "transfer/{id}/edit"),
            settlementCorrectionNavigationPlan(14) to listOf(AppRoute.MORE, "history", AppRoute.SETTLEMENT_DETAIL, "settlement/{id}/edit"),
        )

        plans.forEach { (plan, stack) ->
            val originalIndex = stack.indexOfLast { it == plan.originalDetailRoute }
            assertTrue(originalIndex >= 0)
            val resultingStack = stack.take(originalIndex) + plan.replacementRoute

            assertTrue(plan.originalDetailRoute !in resultingStack)
            assertTrue(resultingStack.none { it.endsWith("/edit") })
            assertEquals(plan.replacementRoute, resultingStack.last())
        }
        assertEquals(listOf(AppRoute.MORE, "history", "income/12"), listOf(AppRoute.MORE, "history") + incomeCorrectionNavigationPlan(12).replacementRoute)
    }
}
