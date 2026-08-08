package com.hesabyar.garage.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.hesabyar.garage.data.AppSecuritySettings
import com.hesabyar.garage.ui.theme.HesabYarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionDeletionUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deletionDialogRequiresReasonAndPreservesExactPersianPassword() {
        var submittedReason: String? = null
        var submittedPassword: CharArray? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme {
                    TransactionDeletionDialog(
                        blockedUntilEpochMillis = 0L,
                        onDismiss = {},
                        onConfirm = { reason, password ->
                            submittedReason = reason
                            submittedPassword = password
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("حذف تراکنش").assertIsDisplayed()
        composeRule.onNodeWithTag("transaction-delete-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("transaction-delete-reason").performTextInput("  ثبت تکراری  ")
        composeRule.onNodeWithTag("transaction-delete-password").performTextInput("رمزِدقیق۱۲۳")
        composeRule.onNodeWithTag("transaction-delete-confirm").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("ثبت تکراری", submittedReason)
            val exactPassword = requireNotNull(submittedPassword)
            assertEquals("رمزِدقیق۱۲۳", String(exactPassword))
            exactPassword.fill('\u0000')
        }
    }

    @Test
    fun actionPreventsDuplicateSubmissionAndNavigatesOnceAfterSuccess() {
        var deleteCalls = 0
        var deletedCalls = 0
        var success: (() -> Unit)? = null
        var finished: (() -> Unit)? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme {
                    TransactionDeletionActionContent(
                        security = AppSecuritySettings(lockEnabled = true),
                        busy = false,
                        onOpenSecurity = {},
                        onDelete = { _, password, onSuccess, onFinished ->
                            deleteCalls += 1
                            password.fill('\u0000')
                            success = onSuccess
                            finished = onFinished
                        },
                        onDeleted = { deletedCalls += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("transaction-delete").performClick()
        composeRule.onNodeWithTag("transaction-delete-reason").performTextInput("ثبت تکراری")
        composeRule.onNodeWithTag("transaction-delete-password").performTextInput("رمز دقیق")
        composeRule.onNodeWithTag("transaction-delete-confirm").performClick()
        composeRule.onNodeWithTag("transaction-delete-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("transaction-delete-reason").assertIsNotEnabled()
        composeRule.onNodeWithTag("transaction-delete-password").assertIsNotEnabled()

        composeRule.runOnIdle {
            assertEquals(1, deleteCalls)
            success?.invoke()
            finished?.invoke()
        }
        composeRule.onAllNodesWithTag("transaction-delete-dialog").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, deletedCalls) }
    }

    @Test
    fun actionWithoutAppPasswordRoutesToSecurityWithoutDeleting() {
        var securityRoutes = 0
        var deleteCalls = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HesabYarTheme {
                    TransactionDeletionActionContent(
                        security = AppSecuritySettings(lockEnabled = false),
                        busy = false,
                        onOpenSecurity = { securityRoutes += 1 },
                        onDelete = { _, password, _, onFinished ->
                            deleteCalls += 1
                            password.fill('\u0000')
                            onFinished()
                        },
                        onDeleted = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("transaction-delete").performClick()
        composeRule.onNodeWithText("رفتن به تنظیمات امنیت").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, securityRoutes)
            assertEquals(0, deleteCalls)
        }
    }
}
