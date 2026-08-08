package com.hesabyar.garage.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.hesabyar.garage.ui.theme.HesabYarTheme
import org.junit.Rule
import org.junit.Test

class PersonnelEligibilitySettingInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eligibilitySwitchExplainsCustodyIndependenceAndChangesStateInRtl() {
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme { EligibilitySetting(enabled, { enabled = it }) }
            }
        }

        composeRule.onNodeWithText("سهم از انعام و ضایعات").assertExists()
        composeRule.onNodeWithTag("shared-income-eligibility-switch").assertIsOn().performClick()
        composeRule.onNodeWithTag("shared-income-eligibility-switch").assertIsOff()
        composeRule.onNodeWithText("سهم نمی‌گیرد؛ اما می‌تواند پول مشترک را دریافت یا منتقل کند.").assertExists()
    }
}
