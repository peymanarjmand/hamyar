package com.hesabyar.garage.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.hesabyar.garage.ui.theme.HesabYarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacyPolicyInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacyPolicyIsReadableInRtlAndRequiresExplicitAcceptance() {
        var accepted = false

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme {
                    PrivacyPolicyDialog(onDismiss = {}, onAccept = { accepted = true })
                }
            }
        }

        composeRule.onNodeWithText("حریم خصوصی").assertIsDisplayed()
        composeRule.onNodeWithText("آخرین به‌روزرسانی: ۲۷ تیر ۱۴۰۵").assertIsDisplayed()
        composeRule.onNodeWithText("می‌پذیرم").performClick()
        composeRule.runOnIdle { assertTrue(accepted) }
    }
}
