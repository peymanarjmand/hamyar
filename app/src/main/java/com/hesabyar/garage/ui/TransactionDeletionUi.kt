package com.hesabyar.garage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hesabyar.garage.data.AppSecuritySettings
import com.hesabyar.garage.util.toPersianDigits
import kotlinx.coroutines.delay

internal fun deletionRetrySeconds(blockedUntilEpochMillis: Long, nowEpochMillis: Long): Long {
    val remainingMillis = (blockedUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    return if (remainingMillis == 0L) 0L else (remainingMillis + 999L) / 1_000L
}

@Composable
internal fun TransactionDeletionAction(
    viewModel: AppViewModel,
    onOpenSecurity: () -> Unit,
    onDelete: (
        reason: String,
        password: CharArray,
        onSuccess: () -> Unit,
        onFinished: () -> Unit,
    ) -> Unit,
    onDeleted: () -> Unit,
) {
    val security by viewModel.securitySettings.collectAsState()
    val busy by viewModel.busy.collectAsState()
    TransactionDeletionActionContent(
        security = security,
        busy = busy,
        onOpenSecurity = onOpenSecurity,
        onDelete = onDelete,
        onDeleted = onDeleted,
    )
}

@Composable
internal fun TransactionDeletionActionContent(
    security: AppSecuritySettings?,
    busy: Boolean,
    onOpenSecurity: () -> Unit,
    onDelete: (
        reason: String,
        password: CharArray,
        onSuccess: () -> Unit,
        onFinished: () -> Unit,
    ) -> Unit,
    onDeleted: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordRequired by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            if (security?.lockEnabled == true) showDeleteDialog = true else showPasswordRequired = true
        },
        enabled = !busy && !submitting,
        modifier = Modifier.fillMaxWidth().testTag("transaction-delete"),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("حذف این تراکنش")
    }

    if (showDeleteDialog) {
        TransactionDeletionDialog(
            blockedUntilEpochMillis = security?.blockedUntilEpochMillis ?: Long.MAX_VALUE,
            inFlight = busy || submitting,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { reason, password ->
                if (!submitting && !busy) {
                    submitting = true
                    onDelete(
                        reason,
                        password,
                        {
                            showDeleteDialog = false
                            onDeleted()
                        },
                        { submitting = false },
                    )
                } else {
                    password.fill('\u0000')
                }
            },
        )
    }
    if (showPasswordRequired) {
        AlertDialog(
            onDismissRequest = { showPasswordRequired = false },
            title = { Text("رمز برنامه لازم است", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (security == null) {
                        "تنظیمات امنیتی هنوز آماده نشده است؛ کمی بعد دوباره تلاش کنید."
                    } else {
                        "برای حذف تراکنش، ابتدا در بخش «قفل و ورود امن» یک رمز برنامه تعریف کنید."
                    },
                )
            },
            confirmButton = {
                if (security != null) TextButton(onClick = {
                    showPasswordRequired = false
                    onOpenSecurity()
                }) { Text("رفتن به تنظیمات امنیت") }
            },
            dismissButton = { TextButton(onClick = { showPasswordRequired = false }) { Text("انصراف") } },
        )
    }
}

@Composable
internal fun TransactionDeletionDialog(
    blockedUntilEpochMillis: Long,
    inFlight: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, password: CharArray) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var now by remember(blockedUntilEpochMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(blockedUntilEpochMillis) {
        while (deletionRetrySeconds(blockedUntilEpochMillis, now) > 0L) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val retrySeconds = deletionRetrySeconds(blockedUntilEpochMillis, now)
    AlertDialog(
        modifier = Modifier.testTag("transaction-delete-dialog"),
        onDismissRequest = { if (!inFlight) onDismiss() },
        title = { Text("حذف تراکنش", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("مبلغ این تراکنش از حساب‌ها خنثی می‌شود؛ خود تراکنش و سابقه حذف در تاریخچه باقی می‌ماند.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل حذف*") },
                    supportingText = { Text("این توضیح در سابقه حسابرسی باقی می‌ماند.") },
                    isError = reason.isNotBlank() && reason.trim().length < 3,
                    enabled = !inFlight,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("transaction-delete-reason"),
                )
                AppPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "رمز برنامه",
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth().testTag("transaction-delete-password"),
                )
                if (retrySeconds > 0L) {
                    Text(
                        "به‌دلیل تلاش‌های ناموفق، ${retrySeconds.toString().toPersianDigits()} ثانیه دیگر دوباره امتحان کنید.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val credential = password.toCharArray()
                    password = ""
                    onConfirm(reason.trim(), credential)
                },
                enabled = !inFlight && reason.trim().length >= 3 && password.isNotEmpty() && retrySeconds == 0L,
                modifier = Modifier.testTag("transaction-delete-confirm"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف از محاسبات") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !inFlight) { Text("انصراف") } },
    )
}
