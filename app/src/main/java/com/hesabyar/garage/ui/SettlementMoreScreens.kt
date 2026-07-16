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
import com.hesabyar.garage.data.RecordSettlementCommand
import com.hesabyar.garage.data.local.EmployeeEntity
import com.hesabyar.garage.data.local.MoneyAccountEntity
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.ui.theme.Danger
import com.hesabyar.garage.ui.theme.Success
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.formatMoney
import com.hesabyar.garage.util.toPersianDigits
import java.time.LocalDate

@Composable
fun SettlementScreen(viewModel: AppViewModel, nav: NavController) {
    val balances by viewModel.balances.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val custodyRows by viewModel.custodyAccounts.collectAsState()
    val remainder by viewModel.remainder.collectAsState()
    var selectedSuggestion by remember { mutableStateOf<SettlementSuggestion?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("تسویه حساب", "محاسبه زنده؛ فقط پرداخت ثبت‌شده حساب را تغییر می‌دهد") }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ nav.navigate("transfer") }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("انتقال امانت") }
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
                    Row { Text("طلب: ${formatMoney(balance.claim)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("امانت: ${formatMoney(balance.custody)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text("پرداخت‌های پیشنهادی", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (suggestions.isEmpty()) item { EmptyState("پرداختی لازم نیست", "حساب افراد در وضعیت فعلی متعادل است.") }
        else items(suggestions, key = { "${it.fromEmployeeId}-${it.toEmployeeId}-${it.type}" }) { suggestion ->
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(if (suggestion.type == SettlementType.SELF_OFFSET) "تهاتر داخلی ${suggestion.fromName}" else "${suggestion.fromName} ← ${suggestion.toName}", fontWeight = FontWeight.Bold)
                    Text(formatMoney(suggestion.amount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(if (suggestion.type == SettlementType.SELF_OFFSET) "سهم شخص از امانتی که خودش نگه داشته کم می‌شود." else "پرداخت واقعی باید پس از انجام ثبت شود.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton({ selectedSuggestion = suggestion }, Modifier.fillMaxWidth()) { Text(if (suggestion.type == SettlementType.SELF_OFFSET) "ثبت تهاتر" else "ثبت پرداخت واقعی") }
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
    val toAccounts by viewModel.accounts(suggestion.toEmployeeId).collectAsState(initial = emptyList())
    var fromAccount by remember { mutableStateOf<MoneyAccountEntity?>(null) }
    var destination by remember { mutableStateOf<MoneyAccountEntity?>(null) }
    var amount by remember { mutableStateOf(suggestion.amount.toString()) }
    var date by remember { mutableStateOf(defaultDateForYear(year)) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(fromAccounts, custodyRows) { fromAccount = fromAccounts.firstOrNull { a -> custodyRows.any { it.accountId == a.id && it.amount > 0 } } }
    LaunchedEffect(toAccounts) { destination = toAccounts.firstOrNull() }
    val amountLong = amount.toLongOrNull()
    val available = custodyRows.firstOrNull { it.accountId == fromAccount?.id }?.amount ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (suggestion.type == SettlementType.SELF_OFFSET) "ثبت تهاتر داخلی" else "ثبت پرداخت واقعی", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (suggestion.type == SettlementType.SELF_OFFSET) "${suggestion.fromName} سهم خود را از پول نزد خودش برمی‌دارد." else "${suggestion.fromName} به ${suggestion.toName} پرداخت می‌کند.")
                ChoiceField("از حساب امانت*", fromAccounts.filter { a -> custodyRows.any { it.accountId == a.id && it.amount > 0 } }, fromAccount, { it.title }, { fromAccount = it })
                if (suggestion.type == SettlementType.PAYMENT) ChoiceField("مقصد دریافت؛ نقد یا کارت", toAccounts, destination, { if (it.type == AccountType.CARD) "${it.title} • ${it.cardLast4}" else it.title }, { destination = it })
                MoneyField(amount, { amount = it })
                PersianDateField("تاریخ", date, { date = it })
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
                            suggestion.toEmployeeId, if (suggestion.type == SettlementType.PAYMENT) destination?.id else source.id,
                            amountLong!!, if (destination?.type == AccountType.CARD) MoneyMethod.CARD else MoneyMethod.CASH,
                            suggestion.type, note,
                        ),
                    )
                },
                enabled = amountLong != null && amountLong in 1..minOf(suggestion.amount, available) && fromAccount != null && date.year == year,
            ) { Text("ثبت نهایی") }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun MoreScreen(viewModel: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val yearEntity by viewModel.selectedYearEntity.collectAsState()
    var passwordMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
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
        item { MoreSectionTitle("درباره برنامه") }
        item { MoreItem(Icons.Rounded.WifiOff, "کاملاً آفلاین", "بدون تبلیغ، حساب اینترنتی یا دسترسی شبکه") {} }
        item { MoreItem(Icons.Rounded.Gavel, "قانون غیبت", "روزهای ۱ تا ۱۴ سهم دارد؛ از روز ۱۵ سهم ندارد") {} }
        item { MoreItem(Icons.Rounded.Info, "حساب‌یار تعمیرگاه", "نسخه ۱.۰ • همه مبلغ‌ها به تومان") {} }
    }

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
        CloseCheck("هیچ امانتی جز باقیمانده تقسیم نمانده", custodyReady)
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
    val incomes by viewModel.incomes.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val settlements by viewModel.settlements.collectAsState()
    val employees by viewModel.employees.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.Rounded.ArrowForward, "بازگشت") }
            Text("تاریخچه سالانه", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            YearSelector(year, years, viewModel::selectYear)
        }
        PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("درآمدها") })
            Tab(tab == 1, { tab = 1 }, text = { Text("امانت") })
            Tab(tab == 2, { tab = 2 }, text = { Text("تسویه") })
        }
        when (tab) {
            0 -> if (incomes.isEmpty()) EmptyState("درآمدی ندارد", "در این سال رویدادی ثبت نشده است.") else LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(incomes, key = { it.id }) { IncomeRow(it, onClick = { nav.navigate("income/${it.id}") }) } }
            1 -> if (transfers.isEmpty()) EmptyState("انتقالی ندارد", "انتقال امانت بین افراد اینجا ثبت می‌شود.") else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(transfers, key = { it.id }) { transfer ->
                    val from = employees.firstOrNull { it.id == transfer.fromEmployeeId }?.name ?: "نامشخص"
                    val to = employees.firstOrNull { it.id == transfer.toEmployeeId }?.name ?: "نامشخص"
                    HistoryOperationCard("انتقال امانت: $from ← $to", transfer.amount, transfer.dateEpochDay, transfer.note) { nav.navigate("transfer/${transfer.id}") }
                }
            }
            else -> if (settlements.isEmpty()) EmptyState("تسویه‌ای ندارد", "پرداخت‌های واقعی و تهاترها اینجا ثبت می‌شوند.") else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(settlements, key = { it.id }) { payment ->
                    val from = employees.firstOrNull { it.id == payment.fromEmployeeId }?.name ?: "نامشخص"
                    val to = employees.firstOrNull { it.id == payment.toEmployeeId }?.name ?: "نامشخص"
                    HistoryOperationCard(if (payment.type == SettlementType.SELF_OFFSET) "تهاتر داخلی $from" else "پرداخت: $from ← $to", payment.amount, payment.dateEpochDay, payment.note) { nav.navigate("settlement/${payment.id}") }
                }
            }
        }
    }
}

@Composable
private fun HistoryOperationCard(title: String, amount: Long, date: Long, note: String, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(PersianDate.fromEpochDay(date).format() + if (note.isBlank()) "" else " • $note", style = MaterialTheme.typography.bodySmall) }
            Text(formatMoney(amount), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TransferDetailScreen(viewModel: AppViewModel, transferId: Long, onBack: () -> Unit) {
    val detail by produceState<com.hesabyar.garage.data.TransferDetailData?>(null, transferId) { value = viewModel.transferDetail(transferId) }
    val data = detail ?: return
    FormScaffold("جزئیات انتقال امانت", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(data.transfer.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(data.transfer.dateEpochDay).format())
                if (data.transfer.note.isNotBlank()) Text(data.transfer.note)
            }
        }
        OperationLine("فرستنده", data.from.name)
        OperationLine("حساب مبدأ", data.fromAccount.title + cardSuffix(data.fromAccount))
        OperationLine("گیرنده", data.to.name)
        OperationLine("حساب مقصد", data.toAccount.title + cardSuffix(data.toAccount))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text("این عملیات فقط نگهدارنده پول مشترک را عوض کرده و سهم هیچ فردی را پرداخت نکرده است.", Modifier.padding(14.dp))
        }
        SourceList("منشأ مبلغ انتقال‌یافته", data.sources)
    }
}

@Composable
fun SettlementDetailScreen(viewModel: AppViewModel, paymentId: Long, onBack: () -> Unit) {
    val detail by produceState<com.hesabyar.garage.data.SettlementDetailData?>(null, paymentId) { value = viewModel.settlementDetail(paymentId) }
    val data = detail ?: return
    FormScaffold(if (data.payment.type == SettlementType.SELF_OFFSET) "جزئیات تهاتر" else "جزئیات پرداخت تسویه", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(data.payment.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(data.payment.dateEpochDay).format())
                if (data.payment.note.isNotBlank()) Text(data.payment.note)
            }
        }
        OperationLine("پرداخت‌کننده", data.from.name)
        OperationLine("از حساب امانت", data.fromAccount.title + cardSuffix(data.fromAccount))
        OperationLine("دریافت‌کننده سهم", data.to.name)
        data.destinationAccount?.let { OperationLine("مقصد دریافت", it.title + cardSuffix(it)) }
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

private fun cardSuffix(account: MoneyAccountEntity): String = if (account.type == AccountType.CARD) " • ****${account.cardLast4.toPersianDigits()}" else " • نقد"

private fun defaultDateForYear(year: Int): PersianDate {
    val today = PersianDate.today()
    return when {
        year == today.year -> today
        year < today.year -> PersianDate(year, 12, PersianDate.monthLength(year, 12))
        else -> PersianDate(year, 1, 1)
    }
}
