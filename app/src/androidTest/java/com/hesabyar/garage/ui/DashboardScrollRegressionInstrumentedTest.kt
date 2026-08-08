package com.hesabyar.garage.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.hesabyar.garage.HesabYarApplication
import com.hesabyar.garage.data.AddEmployeeCommand
import com.hesabyar.garage.data.RegisterIncomeCommand
import com.hesabyar.garage.domain.EmployeeRole
import com.hesabyar.garage.domain.IncomeType
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.ui.theme.HesabYarTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class DashboardScrollRegressionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun employeeAndIncomeWithSameIdCanBeComposedAndScrolledWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as HesabYarApplication
        runBlocking {
            application.container.database.clearAllTables()
            application.container.settingsRepository.finishOnboarding("تعمیرگاه آزمایشی", 1405)
            val repository = application.container.garageRepository
            repository.ensureYear(1405)
            val start = PersianDate(1405, 1, 1).toEpochDay()
            val receiver = repository.addEmployee(AddEmployeeCommand("پرسنل اول", "", EmployeeRole.PAINTER, start))
            repository.addEmployee(AddEmployeeCommand("پرسنل دوم", "", EmployeeRole.BODYWORK, start))
            repository.registerIncome(
                RegisterIncomeCommand(
                    financialYear = 1405,
                    dateEpochDay = start + 1,
                    amount = 1_000,
                    type = IncomeType.TIP,
                    receiverEmployeeId = receiver,
                    receiverAccountId = null,
                    description = "آزمون برخورد شناسه",
                    method = MoneyMethod.CASH,
                ),
            )
        }
        val viewModel = AppViewModel(application.container)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme { DashboardScreen(viewModel, rememberNavController()) }
            }
        }

        composeRule.waitForIdle()
        val dashboard = composeRule.onNode(hasScrollAction())
        dashboard.performScrollToNode(hasText("آخرین درآمدها"))
        composeRule.onNodeWithText("آخرین درآمدها").assertIsDisplayed()
        dashboard.performScrollToNode(hasText("انعام مشتری"))
        composeRule.onNodeWithText("انعام مشتری").assertIsDisplayed()
        dashboard.performScrollToNode(hasText("پشتیبان اطلاعات"))
        composeRule.onNodeWithText("پشتیبان اطلاعات").assertIsDisplayed()
    }
}
