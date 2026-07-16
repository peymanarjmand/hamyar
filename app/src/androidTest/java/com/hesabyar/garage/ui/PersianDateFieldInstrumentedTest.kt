package com.hesabyar.garage.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.hesabyar.garage.ui.components.PersianDateField
import com.hesabyar.garage.ui.theme.HesabYarTheme
import com.hesabyar.garage.util.PersianDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PersianDateFieldInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun monthDropdownClampsDayAndDayDropdownSelectsValue() {
        var selected by mutableStateOf(PersianDate(1405, 6, 31))
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme { PersianDateField("تاریخ همکاری", selected, { selected = it }) }
            }
        }

        composeRule.onNodeWithText("تاریخ همکاری").performClick()
        composeRule.onNodeWithTag("jalali-date-month").performClick()
        composeRule.onNodeWithTag("jalali-date-month-option-7").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("jalali-date-day").assertTextContains("۳۰")
        composeRule.onNodeWithTag("jalali-date-day").performClick()
        composeRule.onNodeWithTag("jalali-date-day-option-25").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("jalali-date-day").assertTextContains("۲۵")
        composeRule.onNodeWithText("تأیید تاریخ").performClick()

        composeRule.runOnIdle { assertEquals(PersianDate(1405, 7, 25), selected) }
    }

    @Test
    fun manualPersianAndArabicDigitsAreValidatedBeforeConfirm() {
        var selected by mutableStateOf(PersianDate(1404, 1, 1))
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme { PersianDateField("تاریخ شروع", selected, { selected = it }) }
            }
        }

        composeRule.onNodeWithText("تاریخ شروع").performClick()
        composeRule.onNodeWithTag("jalali-date-year").performTextClearance()
        composeRule.onNodeWithTag("jalali-date-year").performTextInput("۱۴۰۵")
        composeRule.onNodeWithTag("jalali-date-month").performTextClearance()
        composeRule.onNodeWithTag("jalali-date-month").performTextInput("١٢")
        composeRule.onNodeWithTag("jalali-date-day").performTextClearance()
        composeRule.onNodeWithTag("jalali-date-day").performTextInput("۳۰")
        composeRule.onNodeWithText("تأیید تاریخ").assertExists()
        composeRule.onNodeWithText("تأیید تاریخ").assertIsNotEnabled()

        composeRule.onNodeWithTag("jalali-date-day").performTextClearance()
        composeRule.onNodeWithTag("jalali-date-day").performTextInput("29")
        composeRule.onNodeWithText("تأیید تاریخ").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(PersianDate(1405, 12, 29), selected) }
    }
}
