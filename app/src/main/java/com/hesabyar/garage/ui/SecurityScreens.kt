package com.hesabyar.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesabyar.garage.data.AppSecurityRepository
import com.hesabyar.garage.data.AppSecuritySettings
import com.hesabyar.garage.security.DeviceAuthenticationAvailability
import com.hesabyar.garage.security.DeviceAuthenticationGateway
import com.hesabyar.garage.security.DeviceAuthenticationResult
import com.hesabyar.garage.ui.components.ChoiceField
import com.hesabyar.garage.ui.components.PrimaryButton
import com.hesabyar.garage.util.toPersianDigits

private enum class RecoveryMethod { QUESTION, CODE }

private data class LockTimeoutOption(val seconds: Int, val label: String)

private val lockTimeoutOptions = listOf(
    LockTimeoutOption(0, "بلافاصله"),
    LockTimeoutOption(30, "پس از ۳۰ ثانیه"),
    LockTimeoutOption(60, "پس از ۱ دقیقه"),
    LockTimeoutOption(300, "پس از ۵ دقیقه"),
    LockTimeoutOption(900, "پس از ۱۵ دقیقه"),
)

private const val CUSTOM_QUESTION = "پرسش دلخواه"
private val suggestedRecoveryQuestions = listOf(
    "نام اولین ابزاری که برای کار خریدید چه بود؟",
    "اولین استادکار خود را با چه نامی به یاد دارید؟",
    "نام محلهٔ دوران کودکی شما چه بود؟",
    CUSTOM_QUESTION,
)

@Composable
fun SecureLoadingScreen(snackbarHostState: SnackbarHostState) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                CircularProgressIndicator()
                Text("در حال بررسی قفل برنامه…", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AppLockScreen(
    viewModel: AppViewModel,
    state: AppAccessState.Locked,
    deviceAuthenticationGateway: DeviceAuthenticationGateway,
    snackbarHostState: SnackbarHostState,
) {
    val busy by viewModel.busy.collectAsState()
    var password by remember(state.sessionId) { mutableStateOf("") }
    var showRecovery by remember(state.sessionId) { mutableStateOf(false) }
    var recoveryMethod by remember(state.sessionId) { mutableStateOf(RecoveryMethod.QUESTION) }
    var recoveryValue by remember(state.sessionId) { mutableStateOf("") }
    var newPassword by remember(state.sessionId) { mutableStateOf("") }
    var confirmPassword by remember(state.sessionId) { mutableStateOf("") }
    val deviceAvailability = deviceAuthenticationGateway.availability()

    fun requestDeviceAuthentication() {
        deviceAuthenticationGateway.authenticate { result ->
            when (result) {
                DeviceAuthenticationResult.Success -> viewModel.recordDeviceAuthenticationSuccess()
                DeviceAuthenticationResult.Canceled -> Unit
                is DeviceAuthenticationResult.Error -> viewModel.notifyMessage(result.message)
            }
        }
    }

    LaunchedEffect(state.sessionId, state.settings.deviceAuthEnabled) {
        if (
            state.settings.deviceAuthEnabled &&
            deviceAvailability == DeviceAuthenticationAvailability.AVAILABLE &&
            viewModel.shouldAutomaticallyPromptDeviceAuthentication(state.sessionId)
        ) requestDeviceAuthentication()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text("حساب‌یار قفل است", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (state.settings.deviceAuthEnabled) {
                            "برای باز کردن برنامه، رمز برنامه را وارد کنید یا از قفل گوشی استفاده کنید."
                        } else {
                            "برای باز کردن برنامه، رمز برنامه را وارد کنید."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!showRecovery) {
                        AppPasswordField(password, { password = it }, "رمز برنامه")
                        PrimaryButton(
                            text = "باز کردن برنامه",
                            enabled = !busy && password.isNotEmpty(),
                        ) { viewModel.unlockWithAppPassword(password.toCharArray()) }

                        if (state.settings.deviceAuthEnabled) {
                            OutlinedButton(
                                onClick = ::requestDeviceAuthentication,
                                enabled = !busy && deviceAvailability == DeviceAuthenticationAvailability.AVAILABLE,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Fingerprint, null)
                                Spacer(Modifier.width(8.dp))
                                Text("باز کردن با قفل گوشی")
                            }
                            if (deviceAvailability != DeviceAuthenticationAvailability.AVAILABLE) {
                                Text(
                                    DeviceAuthenticationGateway.availabilityMessage(deviceAvailability),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        TextButton(onClick = { showRecovery = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("رمز برنامه را فراموش کرده‌ام")
                        }
                    } else {
                        Text("تعریف رمز جدید", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = recoveryMethod == RecoveryMethod.QUESTION,
                                onClick = { recoveryMethod = RecoveryMethod.QUESTION },
                                label = { Text("پرسش بازیابی") },
                            )
                            FilterChip(
                                selected = recoveryMethod == RecoveryMethod.CODE,
                                onClick = { recoveryMethod = RecoveryMethod.CODE },
                                label = { Text("کد بازیابی") },
                            )
                        }
                        if (recoveryMethod == RecoveryMethod.QUESTION) {
                            Text(state.settings.recoveryQuestion, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = recoveryValue,
                                onValueChange = { recoveryValue = it.take(200) },
                                label = { Text("پاسخ بازیابی") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text(
                                "فاصله، نیم‌فاصله، رقم‌های فارسی و شکل‌های رایج «ی» و «ک» یکسان در نظر گرفته می‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = recoveryValue,
                                onValueChange = { recoveryValue = it.take(30) },
                                label = { Text("کد بازیابی ۲۰ رقمی") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        AppPasswordField(newPassword, { newPassword = it }, "رمز جدید برنامه")
                        AppPasswordField(confirmPassword, { confirmPassword = it }, "تکرار رمز جدید")
                        PrimaryButton(
                            text = "ثبت رمز جدید",
                            enabled = !busy && recoveryValue.isNotBlank() &&
                                validAppPassword(newPassword) && newPassword == confirmPassword,
                        ) {
                            if (recoveryMethod == RecoveryMethod.QUESTION) {
                                viewModel.resetAppPasswordWithAnswer(
                                    recoveryValue.toCharArray(),
                                    newPassword.toCharArray(),
                                )
                            } else {
                                viewModel.resetAppPasswordWithCode(
                                    recoveryValue,
                                    newPassword.toCharArray(),
                                ) {}
                            }
                        }
                        TextButton(onClick = { showRecovery = false }) { Text("بازگشت به ورود") }
                    }
                }
            }
            if (busy) CircularProgressIndicator()
        }
    }

}

@Composable
fun SecurityScreen(
    viewModel: AppViewModel,
    deviceAuthenticationGateway: DeviceAuthenticationGateway,
    onBack: () -> Unit,
) {
    val settings by viewModel.securitySettings.collectAsState()
    val security = settings
    if (security == null) {
        FormScaffold("امنیت برنامه", onBack) { CircularProgressIndicator() }
        return
    }

    if (!security.lockEnabled) {
        LockSetupContent(viewModel, onBack)
    } else {
        EnabledSecurityContent(viewModel, security, deviceAuthenticationGateway, onBack)
    }
}

@Composable
private fun LockSetupContent(viewModel: AppViewModel, onBack: () -> Unit) {
    val busy by viewModel.busy.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedQuestion by remember { mutableStateOf(suggestedRecoveryQuestions.first()) }
    var customQuestion by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var timeout by remember {
        mutableStateOf(lockTimeoutOptions.first { it.seconds == AppSecurityRepository.DEFAULT_LOCK_TIMEOUT_SECONDS })
    }
    val question = if (selectedQuestion == CUSTOM_QUESTION) customQuestion else selectedQuestion

    FormScaffold("امنیت برنامه", onBack) {
        SecurityInfoCard(
            icon = Icons.Rounded.LockOpen,
            title = "قفل برنامه اختیاری است",
            text = "تا وقتی خودتان فعال نکنید، رفتار برنامه تغییر نمی‌کند. رمز فقط روی همین گوشی نگهداری می‌شود.",
        )
        Text("تعریف رمز برنامه", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        AppPasswordField(password, { password = it }, "رمز برنامه؛ حداقل ۶ نویسه")
        AppPasswordField(confirmPassword, { confirmPassword = it }, "تکرار رمز برنامه")
        Text(
            "حروف فارسی پشتیبانی می‌شوند. ابتدا و انتهای رمز نباید فاصله داشته باشد و رمز دقیقاً همان‌طور که نوشته‌اید بررسی می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("راه بازیابی", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        ChoiceField(
            label = "پرسش بازیابی",
            items = suggestedRecoveryQuestions,
            selected = selectedQuestion,
            itemLabel = { it },
            onSelected = { selectedQuestion = it },
        )
        if (selectedQuestion == CUSTOM_QUESTION) {
            OutlinedTextField(
                value = customQuestion,
                onValueChange = { customQuestion = it.take(200) },
                label = { Text("پرسش دلخواه؛ ۳ تا ۲۰۰ نویسه") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(200) },
            label = { Text("پاسخ بازیابی") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        WarningCard(
            "پاسخ امنیتی ممکن است حدس‌زدنی باشد. هنگام فعال‌سازی یک کد بازیابی تصادفی هم می‌سازیم؛ آن را بیرون از گوشی و در جای امن نگه دارید.",
        )
        ChoiceField(
            label = "قفل خودکار پس از خروج از برنامه",
            items = lockTimeoutOptions,
            selected = timeout,
            itemLabel = { it.label },
            onSelected = { timeout = it },
        )
        PrimaryButton(
            text = "فعال‌کردن قفل برنامه",
            enabled = !busy && validAppPassword(password) && password == confirmPassword &&
                validRecoveryQuestion(question) && answer.isNotBlank(),
        ) {
            viewModel.enableAppLock(
                password = password.toCharArray(),
                recoveryQuestion = question,
                recoveryAnswer = answer.toCharArray(),
                lockTimeoutSeconds = timeout.seconds,
            ) {}
        }
    }
}

@Composable
private fun EnabledSecurityContent(
    viewModel: AppViewModel,
    settings: AppSecuritySettings,
    deviceAuthenticationGateway: DeviceAuthenticationGateway,
    onBack: () -> Unit,
) {
    val busy by viewModel.busy.collectAsState()
    var timeout by remember(settings.lockTimeoutSeconds) {
        mutableStateOf(lockTimeoutOptions.first { it.seconds == settings.lockTimeoutSeconds })
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    val availability = deviceAuthenticationGateway.availability()

    fun enableDeviceAuthentication() {
        if (availability != DeviceAuthenticationAvailability.AVAILABLE) {
            viewModel.notifyMessage(DeviceAuthenticationGateway.availabilityMessage(availability))
            return
        }
        deviceAuthenticationGateway.authenticate { result ->
            when (result) {
                DeviceAuthenticationResult.Success -> viewModel.setDeviceAuthenticationEnabled(true)
                DeviceAuthenticationResult.Canceled -> Unit
                is DeviceAuthenticationResult.Error -> viewModel.notifyMessage(result.message)
            }
        }
    }

    FormScaffold("امنیت برنامه", onBack) {
        SecurityInfoCard(
            icon = Icons.Rounded.Security,
            title = "قفل برنامه فعال است",
            text = "رمز و پاسخ بازیابی به‌صورت مشتق رمزنگاری‌شده و فقط روی این گوشی نگهداری می‌شوند.",
        )
        Card(shape = MaterialTheme.shapes.large) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("باز کردن با قفل گوشی", fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.deviceAuthEnabled) "فعال؛ اثر انگشت، چهره یا رمز گوشی"
                        else DeviceAuthenticationGateway.availabilityMessage(availability),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.deviceAuthEnabled,
                    enabled = !busy,
                    onCheckedChange = { enabled ->
                        if (enabled) enableDeviceAuthentication()
                        else viewModel.setDeviceAuthenticationEnabled(false)
                    },
                )
            }
        }
        WarningCard("هر فردی که قفل این گوشی را باز کند، در صورت فعال‌بودن این گزینه می‌تواند حساب‌یار را نیز باز کند.")
        ChoiceField(
            label = "قفل خودکار پس از خروج از برنامه",
            items = lockTimeoutOptions,
            selected = timeout,
            itemLabel = { it.label },
            onSelected = {
                timeout = it
                viewModel.setAppLockTimeout(it.seconds)
            },
        )
        HorizontalDivider()
        OutlinedButton(onClick = { dialog = "CHANGE_PASSWORD" }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Password, null)
            Spacer(Modifier.width(8.dp))
            Text("تغییر رمز برنامه")
        }
        OutlinedButton(onClick = { dialog = "QUESTION" }, modifier = Modifier.fillMaxWidth()) {
            Text("تغییر پرسش و پاسخ بازیابی")
        }
        OutlinedButton(onClick = { dialog = "RECOVERY_CODE" }, modifier = Modifier.fillMaxWidth()) {
            Text("ساخت کد بازیابی تازه")
        }
        OutlinedButton(onClick = viewModel::lockNow, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Lock, null)
            Spacer(Modifier.width(8.dp))
            Text("قفل‌کردن برنامه همین حالا")
        }
        TextButton(onClick = { dialog = "DISABLE" }, modifier = Modifier.fillMaxWidth()) {
            Text("غیرفعال‌کردن قفل برنامه", color = MaterialTheme.colorScheme.error)
        }
    }

    when (dialog) {
        "CHANGE_PASSWORD" -> ChangePasswordDialog(
            onDismiss = { dialog = null },
            onConfirm = { current, next ->
                viewModel.changeAppPassword(current.toCharArray(), next.toCharArray()) { dialog = null }
            },
        )
        "QUESTION" -> ChangeRecoveryQuestionDialog(
            onDismiss = { dialog = null },
            onConfirm = { password, question, answer ->
                viewModel.updateRecoveryQuestion(
                    password.toCharArray(),
                    question,
                    answer.toCharArray(),
                ) { dialog = null }
            },
        )
        "RECOVERY_CODE" -> PasswordConfirmationDialog(
            title = "ساخت کد بازیابی تازه",
            explanation = "کد قبلی بلافاصله نامعتبر می‌شود.",
            onDismiss = { dialog = null },
            onConfirm = { password ->
                viewModel.regenerateRecoveryCode(password.toCharArray()) {
                    dialog = null
                }
            },
        )
        "DISABLE" -> PasswordConfirmationDialog(
            title = "غیرفعال‌کردن قفل برنامه",
            explanation = "پس از تأیید، برنامه بدون رمز باز می‌شود. اطلاعات مالی حذف نمی‌شوند.",
            onDismiss = { dialog = null },
            onConfirm = { password ->
                viewModel.disableAppLock(password.toCharArray()) { dialog = null }
            },
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر رمز برنامه", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppPasswordField(current, { current = it }, "رمز فعلی")
                AppPasswordField(next, { next = it }, "رمز جدید")
                AppPasswordField(confirm, { confirm = it }, "تکرار رمز جدید")
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(current, next) },
                enabled = current.isNotEmpty() && validAppPassword(next) && next == confirm,
            ) { Text("تغییر رمز") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ChangeRecoveryQuestionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var selectedQuestion by remember { mutableStateOf(suggestedRecoveryQuestions.first()) }
    var customQuestion by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    val question = if (selectedQuestion == CUSTOM_QUESTION) customQuestion else selectedQuestion
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر راه بازیابی", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppPasswordField(password, { password = it }, "رمز فعلی برنامه")
                ChoiceField(
                    "پرسش بازیابی",
                    suggestedRecoveryQuestions,
                    selectedQuestion,
                    { it },
                    { selectedQuestion = it },
                )
                if (selectedQuestion == CUSTOM_QUESTION) OutlinedTextField(
                    value = customQuestion,
                    onValueChange = { customQuestion = it.take(200) },
                    label = { Text("پرسش دلخواه") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.take(200) },
                    label = { Text("پاسخ تازه") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, question, answer) },
                enabled = password.isNotEmpty() && validRecoveryQuestion(question) && answer.isNotBlank(),
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PasswordConfirmationDialog(
    title: String,
    explanation: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation)
                AppPasswordField(password, { password = it }, "رمز برنامه")
            }
        },
        confirmButton = { Button({ onConfirm(password) }, enabled = password.isNotEmpty()) { Text("تأیید") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun RecoveryCodeDialog(code: String, onAcknowledged: () -> Unit) {
    var acknowledged by remember(code) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("کد بازیابی را نگه دارید", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("این کد فقط یک‌بار نمایش داده می‌شود. آن را بیرون از گوشی و دور از فایل پشتیبان نگه دارید.")
                SelectionContainer {
                    Text(
                        code.chunked(5).joinToString("-").toPersianDigits(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text("کد را در جای امن ذخیره کردم")
                }
            }
        },
        confirmButton = {
            Button(onAcknowledged, enabled = acknowledged) { Text("تمام") }
        },
    )
}

@Composable
internal fun AppPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(128)) },
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "پنهان کردن رمز" else "نمایش رمز",
                )
            }
        },
        enabled = enabled,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecurityInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(3.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun validAppPassword(value: String): Boolean {
    val codePoints = value.codePointCount(0, value.length)
    return codePoints in 6..128 &&
        value.firstOrNull()?.isWhitespace() != true &&
        value.lastOrNull()?.isWhitespace() != true
}

private fun validRecoveryQuestion(value: String): Boolean =
    value.trim().codePointCount(0, value.trim().length) in 3..200
