package com.hesabyar.garage.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hesabyar.garage.BuildConfig
import com.hesabyar.garage.data.RecordSettlementCommand
import com.hesabyar.garage.data.local.AuditLogEntity
import com.hesabyar.garage.data.local.EmployeeEntity
import com.hesabyar.garage.data.local.MoneyAccountEntity
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.ui.theme.Danger
import com.hesabyar.garage.ui.theme.Success
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.IranianCardValidator
import com.hesabyar.garage.util.MovementTimeInput
import com.hesabyar.garage.util.formatMoney
import com.hesabyar.garage.util.toPersianDigits
import com.hesabyar.garage.util.toLatinDigits
import java.time.LocalDate

@Composable
fun SettlementScreen(viewModel: AppViewModel, nav: NavController) {
    val balances by viewModel.balances.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val custodyRows by viewModel.custodyAccounts.collectAsState()
    val remainder by viewModel.remainder.collectAsState()
    var selectedSuggestion by remember { mutableStateOf<SettlementSuggestion?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("تسویه حساب", "محاسبه زنده؛ فقط پرداخت ثبت‌شده حساب را تغییر می‌دهد") }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ nav.navigate("transfer") }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("انتقال پول مشترک") }
                OutlinedButton({ nav.navigate("history") }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(6.dp)); Text("تاریخچه") }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("محاسبه تا امروز", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (suggestions.isEmpty() && remainder == 0L) "حساب همه تسویه است" else "${suggestions.size.toString().toPersianDigits()} اقدام پیشنهادی باقی مانده")
                    if (remainder > 0) Text("${formatMoney(remainder)} در صندوق باقیمانده تقسیم نگهداری می‌شود.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("مانده افراد", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (balances.isEmpty()) item { EmptyState("هنوز حسابی شکل نگرفته", "پس از ثبت درآمد، مانده هر شخص اینجا نمایش داده می‌شود.") }
        else items(balances, key = { it.employeeId }) { balance ->
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(balance.employeeName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    BalanceLabel(balance)
                    Spacer(Modifier.height(6.dp))
                    PersonFinancialSummary(balance, year, compact = true)
                }
            }
        }
        item { Text("پرداخت‌های پیشنهادی", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (suggestions.isEmpty()) item { EmptyState("پرداختی لازم نیست", "حساب افراد در وضعیت فعلی متعادل است.") }
        else items(suggestions, key = { "${it.fromEmployeeId}-${it.toEmployeeId}-${it.type}" }) { suggestion ->
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(if (suggestion.type == SettlementType.SELF_OFFSET) "برداشت سهم ${suggestion.fromName} از پول نزد خودش" else "${suggestion.fromName} ← ${suggestion.toName}", fontWeight = FontWeight.Bold)
                    Text(formatMoney(suggestion.amount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(if (suggestion.type == SettlementType.SELF_OFFSET) "سهم شخص از پول مشترکی که خودش نگه داشته برداشته می‌شود." else "پرداخت واقعی باید پس از انجام ثبت شود.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton({ selectedSuggestion = suggestion }, Modifier.fillMaxWidth()) { Text(if (suggestion.type == SettlementType.SELF_OFFSET) "ثبت برداشت سهم" else "ثبت پرداخت واقعی") }
                }
            }
        }
    }
    selectedSuggestion?.let { suggestion ->
        SettlementDialog(viewModel, suggestion, custodyRows, { selectedSuggestion = null }) { command ->
            viewModel.settle(command) { selectedSuggestion = null }
        }
    }
}

@Composable
private fun SettlementDialog(
    viewModel: AppViewModel,
    suggestion: SettlementSuggestion,
    custodyRows: List<com.hesabyar.garage.data.local.AccountCustodyRow>,
    onDismiss: () -> Unit,
    onConfirm: (RecordSettlementCommand) -> Unit,
) {
    val year by viewModel.selectedYear.collectAsState()
    val fromAccounts by viewModel.accounts(suggestion.fromEmployeeId).collectAsState(initial = emptyList())
    var fromAccount by remember { mutableStateOf<MoneyAccountEntity?>(null) }
    var method by remember { mutableStateOf(MoneyMethod.CASH) }
    var cardNumber by remember { mutableStateOf("") }
    var movementDate by remember { mutableStateOf<PersianDate?>(null) }
    var movementTime by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(suggestion.amount.toString()) }
    var date by remember { mutableStateOf(defaultDateForYear(year)) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(fromAccounts, custodyRows) { fromAccount = fromAccounts.firstOrNull { a -> custodyRows.any { it.accountId == a.id && it.amount > 0 } } }
    val amountLong = amount.toLongOrNull()
    val available = custodyRows.firstOrNull { it.accountId == fromAccount?.id }?.amount ?: 0
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (suggestion.type == SettlementType.SELF_OFFSET) "ثبت برداشت سهم از پول نزد خودش" else "ثبت پرداخت واقعی", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (suggestion.type == SettlementType.SELF_OFFSET) "${suggestion.fromName} سهم خود را از پول نزد خودش برمی‌دارد." else "${suggestion.fromName} به ${suggestion.toName} پرداخت می‌کند.")
                ChoiceField("از محل نگهداری پول مشترک*", fromAccounts.filter { a -> custodyRows.any { it.accountId == a.id && it.amount > 0 } }, fromAccount, { it.title }, { fromAccount = it })
                if (suggestion.type == SettlementType.PAYMENT) {
                    MovementDetailsFields(method, { method = it }, cardNumber, { cardNumber = it }, movementDate, { movementDate = it }, movementTime, { movementTime = it })
                }
                MoneyField(amount, { amount = it })
                PersianDateField("تاریخ حسابداری تسویه*", date, { date = it })
                OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth())
                Text("سقف این پیشنهاد: ${formatMoney(suggestion.amount)} • موجودی حساب: ${formatMoney(available)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = fromAccount ?: return@Button
                    onConfirm(
                        RecordSettlementCommand(
                            year, date.toEpochDay(), suggestion.fromEmployeeId, source.id,
                            suggestion.toEmployeeId, if (suggestion.type == SettlementType.SELF_OFFSET) source.id else null,
                            amountLong!!, if (suggestion.type == SettlementType.SELF_OFFSET) MoneyMethod.CASH else method,
                            suggestion.type, note,
                            cardNumber = if (suggestion.type == SettlementType.PAYMENT) cardNumber else "",
                            movementDateEpochDay = if (suggestion.type == SettlementType.PAYMENT) movementDate?.toEpochDay() else null,
                            movementTimeMinutes = if (suggestion.type == SettlementType.PAYMENT) MovementTimeInput.parse(movementTime) else null,
                        ),
                    )
                },
                enabled = amountLong != null && amountLong in 1..minOf(suggestion.amount, available) && fromAccount != null && date.year == year && cardValid && movementTimeValid,
            ) { Text("ثبت نهایی") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun MoreScreen(viewModel: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val securitySettings by viewModel.securitySettings.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val yearEntity by viewModel.selectedYearEntity.collectAsState()
    var passwordMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showGraceDaysDialog by remember { mutableStateOf(false) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val password = backupPassword
        if (uri != null && password != null) viewModel.createBackup(password) { export ->
            val result = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(export.encryptedBytes) } ?: error("امکان نوشتن فایل وجود ندارد") }
            if (result.isSuccess) viewModel.recordBackupSuccess(export.checksum)
            else viewModel.notifyMessage("ذخیره فایل پشتیبان انجام نشد")
        }
        backupPassword = null
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            restoreBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            passwordMode = "RESTORE"
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("بیشتر", "پشتیبان، سال‌های مالی و تنظیمات") }
        item { MoreSectionTitle("امنیت و پشتیبان") }
        item {
            val securitySubtitle = when {
                securitySettings == null -> "در حال بررسی تنظیمات امنیتی"
                securitySettings?.lockEnabled == true -> if (securitySettings?.deviceAuthEnabled == true) {
                    "قفل برنامه و باز کردن با قفل گوشی فعال است"
                } else {
                    "قفل برنامه فعال است"
                }
                else -> "اختیاری؛ تعریف رمز، بازیابی و قفل گوشی"
            }
            MoreItem(Icons.Rounded.Security, "قفل و ورود امن", securitySubtitle) {
                nav.navigate(AppRoute.SECURITY)
            }
        }
        item {
            MoreItem(Icons.Rounded.Backup, "ساخت فایل پشتیبان رمزدار", if (settings.lastBackupAt == 0L) "هنوز پشتیبانی ساخته نشده" else "آخرین پشتیبان ثبت شده است") {
                passwordMode = "BACKUP"
            }
        }
        item { MoreItem(Icons.Rounded.Restore, "بازیابی کامل اطلاعات", "فایل پشتیبان را از حافظه گوشی انتخاب کنید") { openLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) } }
        item { MoreSectionTitle("سال مالی") }
        item { MoreItem(Icons.Rounded.History, "تاریخچه سال‌ها", "مشاهده درآمد، انتقال‌ها و تسویه‌ها") { nav.navigate("history") } }
        item {
            MoreItem(Icons.Rounded.Inventory, "بستن نهایی سال ${year.toString().toPersianDigits()}", if (yearEntity?.status == FinancialYearStatus.CLOSED) "این سال بسته و فقط‌خواندنی است" else "پس از تسویه کامل و ساخت پشتیبان نهایی") {
                nav.navigate("year-close")
            }
        }
        item { MoreSectionTitle("قوانین محاسبه") }
        item {
            val subtitle = if (settings.absenceGraceDays == 0) {
                "غیبت از روز اول سهم ندارد"
            } else {
                "${settings.absenceGraceDays} روز اول سهم دارد؛ از روز ${settings.absenceGraceDays + 1} سهم ندارد"
            }
            MoreItem(Icons.Rounded.Gavel, "روزهای غیبت مشمول سهم", subtitle.toPersianDigits()) { showGraceDaysDialog = true }
        }
        item { MoreSectionTitle("درباره برنامه") }
        item { MoreItem(Icons.Rounded.WifiOff, "کاملاً آفلاین", "بدون تبلیغ، حساب اینترنتی یا دسترسی شبکه") {} }
        item { MoreItem(Icons.Rounded.PrivacyTip, "حریم خصوصی", "اطلاعات ذخیره‌شده، امنیت، پشتیبان و حذف داده") { nav.navigate(AppRoute.PRIVACY) } }
        item { MoreItem(Icons.Rounded.Info, "نسخه برنامه", "${BuildConfig.VERSION_NAME.toPersianDigits()} • همه مبلغ‌ها به تومان") {} }
        item { DeveloperCard(context) { viewModel.notifyMessage("برنامه‌ای برای بازکردن این نشانی پیدا نشد") } }
    }

    if (showGraceDaysDialog) AbsenceGraceDaysDialog(
        initialDays = settings.absenceGraceDays,
        onDismiss = { showGraceDaysDialog = false },
        onConfirm = { days -> viewModel.setAbsenceGraceDays(days) { showGraceDaysDialog = false } },
    )

    passwordMode?.let { mode ->
        PasswordDialog(
            title = if (mode == "BACKUP") "رمز فایل پشتیبان" else "رمز بازیابی",
            explanation = if (mode == "BACKUP") "این رمز برای انتقال امن شماره کارت‌ها به گوشی دیگر لازم است. آن را فراموش نکنید." else "همان رمزی را وارد کنید که هنگام ساخت پشتیبان تعیین کردید.",
            onDismiss = { passwordMode = null; restoreBytes = null },
        ) { password ->
            passwordMode = null
            if (mode == "BACKUP") {
                backupPassword = password
                createLauncher.launch("hesabyar-${year}-${PersianDate.today().format().replace('/', '-')}.hybackup")
            } else {
                val bytes = restoreBytes
                if (bytes != null) viewModel.restoreBackup(bytes, password) { restoreBytes = null }
            }
        }
    }
}

@Composable
private fun AbsenceGraceDaysDialog(initialDays: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by remember(initialDays) { mutableStateOf(initialDays.toString()) }
    val days = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("روزهای غیبت مشمول سهم", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تعداد روزهای ابتدای هر غیبت را تعیین کنید که فرد همچنان از انعام و فروش ضایعات سهم داشته باشد.")
                OutlinedTextField(
                    value = value.toPersianDigits(),
                    onValueChange = { value = it.toLatinDigits().filter(Char::isDigit).take(3) },
                    label = { Text("تعداد روز؛ از ۰ تا ۳۶۵") },
                    supportingText = { Text("مقدار پیش‌فرض برنامه ۱۴ روز است.") },
                    isError = days !in EligibilityEngine.MIN_GRACE_DAYS..EligibilityEngine.MAX_GRACE_DAYS,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("این تغییر فقط در محاسبات بعدی استفاده می‌شود؛ سهم‌ها و دلیل‌های ثبت‌شده قبلی ثابت می‌مانند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { days?.let(onConfirm) }, enabled = days in EligibilityEngine.MIN_GRACE_DAYS..EligibilityEngine.MAX_GRACE_DAYS) { Text("ذخیره قانون") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DeveloperCard(context: android.content.Context, onOpenFailed: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("توسعه‌دهنده", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(DeveloperContact.NAME, fontWeight = FontWeight.Bold)
                }
            }
            Text(DeveloperContact.EMAIL, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { if (!openDeveloperProfile(context, DeveloperContact.bale)) onOpenFailed() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("پیام در بله • ${DeveloperContact.BALE_ID}") }
            OutlinedButton(
                onClick = { if (!openDeveloperProfile(context, DeveloperContact.telegram)) onOpenFailed() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("پیام در تلگرام • ${DeveloperContact.TELEGRAM_ID}") }
        }
    }
}

@Composable
private fun PasswordDialog(title: String, explanation: String, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val isBackup = title.contains("پشتیبان")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation)
                OutlinedTextField(password, { password = it }, label = { Text("رمز؛ حداقل ۶ نویسه") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (isBackup) OutlinedTextField(confirm, { confirm = it }, label = { Text("تکرار رمز") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = { Button({ onConfirm(password.toCharArray()) }, enabled = password.length >= 6 && (!isBackup || password == confirm)) { Text("ادامه") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun MoreSectionTitle(title: String) { Text(title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronLeft, null)
        }
    }
}

@Composable
fun AnnualCloseScreen(viewModel: AppViewModel, onDone: () -> Unit) {
    val year by viewModel.selectedYear.collectAsState()
    val entity by viewModel.selectedYearEntity.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val remainder by viewModel.remainder.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var confirmation by remember { mutableStateOf("") }
    val ended = entity?.let { LocalDate.now().toEpochDay() > it.endEpochDay } == true
    val claimsZero = balances.all { it.claim == 0L }
    val custodyTotal = balances.sumOf { it.custody }
    val custodyReady = custodyTotal == remainder
    val hasBackup = settings.lastBackupChecksum.isNotBlank()
    val open = entity?.status == FinancialYearStatus.OPEN
    FormScaffold("بستن سال ${year.toString().toPersianDigits()}", onDone) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(20.dp)) {
            Text("پس از بستن، ثبت یا ویرایش رویدادهای این سال ممکن نیست. تاریخچه کامل فقط‌خواندنی باقی می‌ماند و دوره همکاری پرسنل به سال بعد ادامه دارد.", Modifier.padding(16.dp))
        }
        CloseCheck("سال به پایان رسیده است", ended)
        CloseCheck("همه سهم‌ها تسویه شده‌اند", claimsZero)
        CloseCheck("هیچ پول مشترکی جز باقیمانده تقسیم نزد افراد نمانده", custodyReady)
        CloseCheck("پشتیبان نهایی ساخته شده است", hasBackup)
        if (remainder > 0) Text("${formatMoney(remainder)} باقیمانده به‌همراه نگهدارنده آن به سال ${year + 1} منتقل می‌شود.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit) }, label = { Text("برای تأیید، عدد سال را وارد کنید") }, modifier = Modifier.fillMaxWidth())
        PrimaryButton("بستن نهایی سال", enabled = open && ended && claimsZero && custodyReady && hasBackup && confirmation.toIntOrNull() == year) {
            viewModel.closeYear(settings.lastBackupChecksum, settings.lastBackupAt) { onDone() }
        }
        if (!ended) Text("این سال هنوز پایان نیافته است؛ بستن نهایی فقط بعد از آخر اسفند فعال می‌شود.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CloseCheck(text: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().background(if (done) Success.copy(alpha = .1f) else Danger.copy(alpha = .1f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (done) Success else Danger); Spacer(Modifier.width(8.dp)); Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun YearHistoryScreen(viewModel: AppViewModel, nav: NavController) {
    val year by viewModel.selectedYear.collectAsState()
    val years by viewModel.years.collectAsState()
    val incomes by viewModel.incomeHistory.collectAsState()
    val transfers by viewModel.transferHistory.collectAsState()
    val settlements by viewModel.settlementHistory.collectAsState()
    val employees by viewModel.employees.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "بازگشت") }
            Text("تاریخچه سالانه", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            YearSelector(year, years, viewModel::selectYear)
        }
        PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("درآمدها") })
            Tab(tab == 1, { tab = 1 }, text = { Text("جابه‌جایی پول") })
            Tab(tab == 2, { tab = 2 }, text = { Text("تسویه") })
        }
        when (tab) {
            0 -> if (incomes.isEmpty()) EmptyState("درآمدی ندارد", "در این سال رویدادی ثبت نشده است.") else LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(incomes, key = { it.id }) { IncomeRow(it, onClick = { nav.navigate("income/${it.id}") }) } }
            1 -> if (transfers.isEmpty()) EmptyState("انتقالی ندارد", "انتقال پول مشترک بین افراد اینجا ثبت می‌شود.") else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(transfers, key = { it.id }) { transfer ->
                    val from = employees.firstOrNull { it.id == transfer.fromEmployeeId }?.name ?: "نامشخص"
                    val to = employees.firstOrNull { it.id == transfer.toEmployeeId }?.name ?: "نامشخص"
                    HistoryOperationCard("انتقال پول مشترک: $from ← $to", transfer.amount, transfer.dateEpochDay, transfer.note, transfer.method, transfer.status == RecordStatus.DELETED) { nav.navigate("transfer/${transfer.id}") }
                }
            }
            else -> if (settlements.isEmpty()) EmptyState("تسویه‌ای ندارد", "پرداخت‌های واقعی و برداشت سهم از پول نزد خود فرد اینجا ثبت می‌شوند.") else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(settlements, key = { it.id }) { payment ->
                    val from = employees.firstOrNull { it.id == payment.fromEmployeeId }?.name ?: "نامشخص"
                    val to = employees.firstOrNull { it.id == payment.toEmployeeId }?.name ?: "نامشخص"
                    HistoryOperationCard(if (payment.type == SettlementType.SELF_OFFSET) "برداشت سهم $from از پول نزد خودش" else "پرداخت: $from ← $to", payment.amount, payment.dateEpochDay, payment.note, if (payment.type == SettlementType.SELF_OFFSET) null else payment.method, payment.status == RecordStatus.DELETED) { nav.navigate("settlement/${payment.id}") }
                }
            }
        }
    }
}

@Composable
private fun HistoryOperationCard(title: String, amount: Long, date: Long, note: String, method: MoneyMethod? = null, deleted: Boolean = false, onClick: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (deleted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                if (deleted) Text("حذف‌شده • بدون اثر در حساب", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(PersianDate.fromEpochDay(date).format() + method?.let { " • ${it.labelFa()}" }.orEmpty() + if (note.isBlank()) "" else " • $note", style = MaterialTheme.typography.bodySmall)
            }
            Text(formatMoney(amount), fontWeight = FontWeight.ExtraBold, color = if (deleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TransferDetailScreen(
    viewModel: AppViewModel,
    transferId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewPrevious: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val detail by produceState<com.hesabyar.garage.data.TransferDetailData?>(null, transferId) { value = viewModel.transferDetail(transferId) }
    val years by viewModel.years.collectAsState()
    val data = detail ?: return
    val correctionAudit by produceState<AuditLogEntity?>(null, data.transfer.replacesTransferId) {
        value = data.transfer.replacesTransferId?.let { viewModel.correctionAudit("CUSTODY_TRANSFER", it) }
    }
    val deletionAudit by produceState<AuditLogEntity?>(null, data.transfer.id, data.transfer.status) {
        value = if (data.transfer.status == RecordStatus.DELETED) viewModel.deletionAudit("CUSTODY_TRANSFER", data.transfer.id) else null
    }
    val editable = data.transfer.status == RecordStatus.POSTED &&
        years.firstOrNull { it.year == data.transfer.financialYear }?.status == FinancialYearStatus.OPEN
    FormScaffold("جزئیات انتقال پول مشترک", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(data.transfer.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(data.transfer.dateEpochDay).format())
                if (data.transfer.note.isNotBlank()) Text(data.transfer.note)
            }
        }
        if (data.transfer.status == RecordStatus.REVERSED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text("این نسخه با یک اصلاح خنثی شده و دیگر در محاسبات جاری اثر ندارد.", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
        }
        if (data.transfer.status == RecordStatus.DELETED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("این تراکنش حذف شده و دیگر در محاسبات اثر ندارد.", fontWeight = FontWeight.Bold)
                deletionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                    Text("دلیل حذف: $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OperationLine("فرستنده", data.from.name)
        OperationLine("حساب مبدأ", data.fromAccount.title + cardSuffix(data.fromAccount))
        OperationLine("گیرنده", data.to.name)
        OperationLine("حساب مقصد", data.toAccount.title + cardSuffix(data.toAccount))
        MovementDetailLines(
            viewModel = viewModel,
            method = data.transfer.method,
            encryptedCard = data.transfer.cardNumberEncrypted.ifBlank { data.toAccount.cardNumberEncrypted },
            cardLast4 = data.transfer.cardLast4.ifBlank { data.toAccount.cardLast4 },
            dateEpochDay = data.transfer.movementDateEpochDay,
            timeMinutes = data.transfer.movementTimeMinutes,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text("این عملیات فقط نگهدارنده پول مشترک را عوض کرده و سهم هیچ فردی را پرداخت نکرده است.", Modifier.padding(14.dp))
        }
        data.transfer.replacesTransferId?.let {
            OperationLine("سابقه اصلاح", "این نسخه جایگزین ثبت شماره ${it.toString().toPersianDigits()} است")
            correctionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                OperationLine("دلیل اصلاح", reason)
            }
            OutlinedButton(onClick = { onViewPrevious(it) }, modifier = Modifier.fillMaxWidth()) {
                Text("مشاهده نسخهٔ قبلی")
            }
        }
        if (editable) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("اصلاح این تراکنش")
        }
        if (editable) TransactionDeletionAction(
            viewModel = viewModel,
            onOpenSecurity = onOpenSecurity,
            onDelete = { reason, password, onSuccess, onFinished ->
                viewModel.deleteTransfer(data.transfer.id, reason, password, onSuccess, onFinished)
            },
            onDeleted = onDeleted,
        )
        SourceList("منشأ مبلغ انتقال‌یافته", data.sources)
    }
}

@Composable
fun SettlementDetailScreen(
    viewModel: AppViewModel,
    paymentId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewPrevious: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val detail by produceState<com.hesabyar.garage.data.SettlementDetailData?>(null, paymentId) { value = viewModel.settlementDetail(paymentId) }
    val years by viewModel.years.collectAsState()
    val data = detail ?: return
    val correctionAudit by produceState<AuditLogEntity?>(null, data.payment.replacesPaymentId) {
        value = data.payment.replacesPaymentId?.let { viewModel.correctionAudit("SETTLEMENT", it) }
    }
    val deletionAudit by produceState<AuditLogEntity?>(null, data.payment.id, data.payment.status) {
        value = if (data.payment.status == RecordStatus.DELETED) viewModel.deletionAudit("SETTLEMENT", data.payment.id) else null
    }
    val editable = data.payment.status == RecordStatus.POSTED &&
        years.firstOrNull { it.year == data.payment.financialYear }?.status == FinancialYearStatus.OPEN
    FormScaffold(if (data.payment.type == SettlementType.SELF_OFFSET) "جزئیات برداشت سهم" else "جزئیات پرداخت تسویه", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(data.payment.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(data.payment.dateEpochDay).format())
                if (data.payment.note.isNotBlank()) Text(data.payment.note)
            }
        }
        if (data.payment.status == RecordStatus.REVERSED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text("این نسخه با یک اصلاح خنثی شده و دیگر در محاسبات جاری اثر ندارد.", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
        }
        if (data.payment.status == RecordStatus.DELETED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("این تراکنش حذف شده و دیگر در محاسبات اثر ندارد.", fontWeight = FontWeight.Bold)
                deletionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                    Text("دلیل حذف: $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OperationLine("پرداخت‌کننده", data.from.name)
        OperationLine("از محل نگهداری پول مشترک", data.fromAccount.title + cardSuffix(data.fromAccount))
        OperationLine("دریافت‌کننده سهم", data.to.name)
        data.destinationAccount?.let { OperationLine("مقصد دریافت", it.title + cardSuffix(it)) }
        if (data.payment.type == SettlementType.PAYMENT) MovementDetailLines(
            viewModel = viewModel,
            method = data.payment.method,
            encryptedCard = data.payment.cardNumberEncrypted.ifBlank { data.destinationAccount?.cardNumberEncrypted.orEmpty() },
            cardLast4 = data.payment.cardLast4.ifBlank { data.destinationAccount?.cardLast4.orEmpty() },
            dateEpochDay = data.payment.movementDateEpochDay,
            timeMinutes = data.payment.movementTimeMinutes,
        )
        data.payment.replacesPaymentId?.let {
            OperationLine("سابقه اصلاح", "این نسخه جایگزین ثبت شماره ${it.toString().toPersianDigits()} است")
            correctionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                OperationLine("دلیل اصلاح", reason)
            }
            OutlinedButton(onClick = { onViewPrevious(it) }, modifier = Modifier.fillMaxWidth()) {
                Text("مشاهده نسخهٔ قبلی")
            }
        }
        if (editable) OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("اصلاح این تراکنش")
        }
        if (editable) TransactionDeletionAction(
            viewModel = viewModel,
            onOpenSecurity = onOpenSecurity,
            onDelete = { reason, password, onSuccess, onFinished ->
                viewModel.deleteSettlement(data.payment.id, reason, password, onSuccess, onFinished)
            },
            onDeleted = onDeleted,
        )
        SourceList("این پول از کدام درآمدها بود؟", data.custodySources)
        SourceList("پرداخت بابت کدام سهم‌ها بود؟", data.claimSources)
    }
}

@Composable
private fun SourceList(title: String, sources: List<com.hesabyar.garage.data.SourceAllocationDetail>) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    sources.forEach { source ->
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (source.event.type == IncomeType.TIP) Icons.Rounded.VolunteerActivism else Icons.Rounded.Recycling, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (source.event.type == IncomeType.TIP) "انعام مشتری" else "فروش ضایعات", fontWeight = FontWeight.Bold)
                    Text(PersianDate.fromEpochDay(source.event.dateEpochDay).format() + if (source.event.description.isBlank()) "" else " • ${source.event.description}", style = MaterialTheme.typography.bodySmall)
                }
                Text(formatMoney(source.amount), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OperationLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(13.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MovementDetailLines(
    viewModel: AppViewModel,
    method: MoneyMethod,
    encryptedCard: String,
    cardLast4: String,
    dateEpochDay: Long?,
    timeMinutes: Int?,
) {
    var fullCard by remember(encryptedCard) { mutableStateOf<String?>(null) }
    OperationLine("روش جابه‌جایی", method.labelFa())
    if (method == MoneyMethod.CARD) {
        OperationLine(
            "شماره کارت",
            fullCard?.let(IranianCardValidator::formatFull)
                ?: cardLast4.takeIf(String::isNotBlank)?.let { "****-****-****-${it.toPersianDigits()}" }
                ?: "ثبت نشده (اختیاری)",
        )
        if (encryptedCard.isNotBlank()) OutlinedButton(
            onClick = {
                if (fullCard != null) fullCard = null
                else viewModel.revealMovementCard(encryptedCard) { fullCard = it }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (fullCard == null) "نمایش شماره کامل کارت" else "پنهان کردن شماره کارت") }
    }
    movementMoment(dateEpochDay, timeMinutes)?.let { OperationLine("زمان دقیق تراکنش", it) }
}

private fun cardSuffix(account: MoneyAccountEntity): String = when (account.type) {
    AccountType.CASH -> " • نقد"
    AccountType.POS -> " • پوز"
    AccountType.CARD -> account.cardLast4.takeIf(String::isNotBlank)?.let { " • ****${it.toPersianDigits()}" } ?: " • کارت‌به‌کارت"
}

private fun defaultDateForYear(year: Int): PersianDate {
    val today = PersianDate.today()
    return when {
        year == today.year -> today
        year < today.year -> PersianDate(year, 12, PersianDate.monthLength(year, 12))
        else -> PersianDate(year, 1, 1)
    }
}
