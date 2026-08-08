package com.hesabyar.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hesabyar.garage.data.EligibilityPreview
import com.hesabyar.garage.data.RegisterIncomeCommand
import com.hesabyar.garage.data.TransferCustodyCommand
import com.hesabyar.garage.data.local.EmployeeEntity
import com.hesabyar.garage.data.local.MoneyAccountEntity
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.IranianCardValidator
import com.hesabyar.garage.util.MovementTimeInput
import com.hesabyar.garage.util.formatMoney
import com.hesabyar.garage.util.toPersianDigits

@Composable
fun IncomeScreen(viewModel: AppViewModel, nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("درآمد مشترک", "انعام مشتری و فروش ضایعات")
        PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("ثبت جدید") })
            Tab(tab == 1, { tab = 1 }, text = { Text("تاریخچه") })
        }
        if (tab == 0) IncomeForm(viewModel) { tab = 1 }
        else IncomeHistory(viewModel, nav)
    }
}

@Composable
private fun IncomeForm(viewModel: AppViewModel, onSaved: () -> Unit) {
    val employees by viewModel.employees.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val remainder by viewModel.remainder.collectAsState()
    val yearEntity by viewModel.selectedYearEntity.collectAsState()
    var type by remember { mutableStateOf(IncomeType.TIP) }
    var date by remember(year) { mutableStateOf(defaultBusinessDate(year)) }
    var amount by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf<EmployeeEntity?>(null) }
    var method by remember { mutableStateOf(MoneyMethod.CASH) }
    var cardNumber by remember { mutableStateOf("") }
    var movementDate by remember { mutableStateOf<PersianDate?>(null) }
    var movementTime by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<EligibilityPreview>>(emptyList()) }
    LaunchedEffect(date, employees, settings.absenceGraceDays) {
        preview = viewModel.previewEligibility(date.toEpochDay())
    }
    val eligible = preview.filter { it.result.isEligible }
    val amountLong = amount.toLongOrNull()
    val closed = yearEntity?.status == FinancialYearStatus.CLOSED
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (closed) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("سال انتخاب‌شده بسته است و ثبت جدید ندارد.", Modifier.padding(14.dp)) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(type == IncomeType.TIP, { type = IncomeType.TIP }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("انعام مشتری") }
            SegmentedButton(type == IncomeType.SCRAP, { type = IncomeType.SCRAP }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("فروش ضایعات") }
        }
        PersianDateField("تاریخ حسابداری دریافت*", date, { date = it })
        MoneyField(amount, { amount = it })
        ChoiceField(
            when (method) {
                MoneyMethod.CASH -> "چه کسی پول نقد را دریافت کرده؟*"
                MoneyMethod.CARD -> "مبلغ به حساب چه کسی واریز شده؟*"
                MoneyMethod.POS -> "وجه کارت‌خوان به حساب متصل به دستگاه چه کسی می‌نشیند؟*"
            },
            employees,
            receiver,
            { it.name },
            { receiver = it },
        )
        if (receiver?.sharedIncomeEligible == false) {
            Text(
                "این شخص سهم نمی‌گیرد و فقط نگهدارنده پول مشترک ثبت می‌شود.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        MovementDetailsFields(
            method,
            { method = it },
            cardNumber,
            { cardNumber = it },
            movementDate,
            { movementDate = it },
            movementTime,
            { movementTime = it },
            allowedMethods = setOf(MoneyMethod.CASH, MoneyMethod.CARD, MoneyMethod.POS),
        )
        OutlinedTextField(description, { description = it }, label = { Text("شرح، نام مشتری یا خودرو — اختیاری") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("پیش‌نمایش تقسیم", fontWeight = FontWeight.Bold)
                Text("${eligible.size.toString().toPersianDigits()} نفر سهم دارند${if (remainder > 0) " • ${formatMoney(remainder)} باقیمانده قبلی هم تقسیم می‌شود" else ""}")
                if (eligible.isEmpty()) {
                    Text(
                        "برای ثبت این درآمد، سهم حداقل یک پرسنل را روشن کنید یا وضعیت همکاری و غیبت را اصلاح کنید.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (amountLong != null && amountLong > 0 && eligible.isNotEmpty()) {
                    val distribution = AccountingEngine.distribute(amountLong, remainder, eligible.size)
                    Text("سهم هر نفر: ${formatMoney(distribution.sharePerPerson)}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    if (distribution.resultingRemainder > 0) Text("باقیمانده شفاف جدید: ${formatMoney(distribution.resultingRemainder)}")
                }
                Spacer(Modifier.height(8.dp))
                preview.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.result.isEligible) Icons.Rounded.CheckCircle else Icons.Rounded.HighlightOff, null, tint = if (item.result.isEligible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp)); Column { Text(item.employee.name, fontWeight = FontWeight.Bold); Text(item.result.reasonFa, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        PrimaryButton("ثبت و محاسبه سهم‌ها", enabled = !closed && amountLong != null && amountLong > 0 && receiver != null && eligible.isNotEmpty() && date.year == year && cardValid && movementTimeValid) {
            viewModel.registerIncome(
                RegisterIncomeCommand(
                    financialYear = year,
                    dateEpochDay = date.toEpochDay(),
                    amount = amountLong!!,
                    type = type,
                    receiverEmployeeId = receiver!!.id,
                    receiverAccountId = null,
                    description = description,
                    method = method,
                    cardNumber = cardNumber,
                    movementDateEpochDay = movementDate?.toEpochDay(),
                    movementTimeMinutes = MovementTimeInput.parse(movementTime),
                ),
            ) { onSaved() }
        }
        if (date.year != year) Text("تاریخ باید در سال مالی ${year} باشد.", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(30.dp))
    }
}

private fun defaultBusinessDate(year: Int): PersianDate {
    val today = PersianDate.today()
    return when {
        year == today.year -> today
        year < today.year -> PersianDate(year, 12, PersianDate.monthLength(year, 12))
        else -> PersianDate(year, 1, 1)
    }
}

@Composable
private fun IncomeHistory(viewModel: AppViewModel, nav: NavController) {
    val incomes by viewModel.incomeHistory.collectAsState()
    if (incomes.isEmpty()) EmptyState("هنوز انعام یا ضایعاتی ثبت نشده", "پس از ثبت، سهم هر فرد و محل دریافت پول در تاریخچه می‌ماند.")
    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(incomes, key = { it.id }) { IncomeRow(it, onClick = { nav.navigate("income/${it.id}") }) }
    }
}

@Composable
fun IncomeDetailScreen(
    viewModel: AppViewModel,
    eventId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewPrevious: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val event by produceState<com.hesabyar.garage.data.local.IncomeEventEntity?>(null, eventId) { value = viewModel.incomeEvent(eventId) }
    val snapshots by viewModel.eligibility(eventId).collectAsState(initial = emptyList())
    val employees by viewModel.employees.collectAsState()
    val years by viewModel.years.collectAsState()
    val receiver by produceState<EmployeeEntity?>(null, event?.receiverEmployeeId) { value = event?.receiverEmployeeId?.let { viewModel.employee(it) } }
    val account by produceState<MoneyAccountEntity?>(null, event?.receiverAccountId) { value = event?.receiverAccountId?.let { viewModel.account(it) } }
    var fullCard by remember { mutableStateOf<String?>(null) }
    val item = event ?: return
    val correctionAudit by produceState<com.hesabyar.garage.data.local.AuditLogEntity?>(null, item.replacesEventId) {
        value = item.replacesEventId?.let { viewModel.correctionAudit("INCOME", it) }
    }
    val deletionAudit by produceState<com.hesabyar.garage.data.local.AuditLogEntity?>(null, item.id, item.status) {
        value = if (item.status == RecordStatus.DELETED) viewModel.deletionAudit("INCOME", item.id) else null
    }
    val editable = item.status == RecordStatus.POSTED &&
        years.firstOrNull { it.year == item.financialYear }?.status == FinancialYearStatus.OPEN
    FormScaffold(if (item.type == IncomeType.TIP) "جزئیات انعام" else "جزئیات فروش ضایعات", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(item.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(item.dateEpochDay).format())
                if (item.description.isNotBlank()) Text(item.description, fontWeight = FontWeight.Medium)
            }
        }
        if (item.status == RecordStatus.REVERSED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text(
                "این نسخه با یک اصلاح خنثی شده و دیگر در محاسبات جاری اثر ندارد.",
                Modifier.padding(14.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        if (item.status == RecordStatus.DELETED) Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("این تراکنش حذف شده و دیگر در محاسبات اثر ندارد.", fontWeight = FontWeight.Bold)
                deletionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                    Text("دلیل حذف: $reason", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        DetailLine("دریافت‌کننده پول", receiver?.name ?: "—")
        DetailLine("روش دریافت", item.method.labelFa())
        if (item.method == MoneyMethod.CARD) {
            val encrypted = item.cardNumberEncrypted.takeIf(String::isNotBlank) ?: account?.cardNumberEncrypted.orEmpty()
            val last4 = item.cardLast4.ifBlank { account?.cardLast4.orEmpty() }
            DetailLine("شماره کارت", fullCard?.let(IranianCardValidator::formatFull) ?: last4.takeIf(String::isNotBlank)?.let { "****-****-****-${it.toPersianDigits()}" } ?: "ثبت نشده (اختیاری)")
            if (encrypted.isNotBlank()) OutlinedButton({
                if (fullCard != null) fullCard = null
                else if (item.cardNumberEncrypted.isNotBlank()) viewModel.revealMovementCard(encrypted) { fullCard = it }
                else account?.let { selected -> viewModel.revealCard(selected.id) { fullCard = it } }
            }, Modifier.fillMaxWidth()) {
                Text(if (fullCard == null) "نمایش شماره کامل کارت" else "پنهان کردن شماره کارت")
            }
        }
        movementMoment(item.movementDateEpochDay, item.movementTimeMinutes)?.let { DetailLine("زمان دقیق دریافت", it) }
        DetailLine("سهم هر فرد", formatMoney(item.sharePerPerson))
        DetailLine("باقیمانده واردشده از قبل", formatMoney(item.carriedRemainder))
        DetailLine("باقیمانده بعد از تقسیم", formatMoney(item.resultingRemainder))
        item.replacesEventId?.let {
            DetailLine("سابقه اصلاح", "این نسخه جایگزین ثبت شماره ${it.toString().toPersianDigits()} است")
            correctionAudit?.description?.substringAfter(": ", "")?.takeIf(String::isNotBlank)?.let { reason ->
                DetailLine("دلیل اصلاح", reason)
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
                viewModel.deleteIncome(item.id, reason, password, onSuccess, onFinished)
            },
            onDeleted = onDeleted,
        )
        Text("افراد و دلیل محاسبه", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        snapshots.forEach { snapshot ->
            val person = employees.firstOrNull { it.id == snapshot.employeeId }
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (snapshot.status == EligibilityStatus.ELIGIBLE) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (snapshot.status == EligibilityStatus.ELIGIBLE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(person?.name ?: "پرسنل", fontWeight = FontWeight.Bold); Text(snapshot.reasonFa, style = MaterialTheme.typography.bodySmall) }
                    Text(if (snapshot.status == EligibilityStatus.ELIGIBLE) formatMoney(snapshot.shareAmount) else "بدون سهم", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("این فهرست هنگام ثبت رویداد ثابت شده است و تغییر سوابق بعدی آن را بی‌صدا عوض نمی‌کند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(13.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransferScreen(viewModel: AppViewModel, onDone: () -> Unit) {
    val employees by viewModel.employees.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val custodyRows by viewModel.custodyAccounts.collectAsState()
    var fromEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    val fromAccounts by remember(fromEmployee?.id) { fromEmployee?.let { viewModel.accounts(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var fromAccount by remember(fromEmployee?.id) { mutableStateOf<MoneyAccountEntity?>(null) }
    var toEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var method by remember { mutableStateOf(MoneyMethod.CASH) }
    var cardNumber by remember { mutableStateOf("") }
    var movementDate by remember { mutableStateOf<PersianDate?>(null) }
    var movementTime by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(PersianDate.today()) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(fromAccounts) { if (fromAccount !in fromAccounts) fromAccount = fromAccounts.firstOrNull { account -> custodyRows.any { it.accountId == account.id } } }
    val available = custodyRows.firstOrNull { it.accountId == fromAccount?.id }?.amount ?: 0
    val amountLong = amount.toLongOrNull()
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)

    FormScaffold("انتقال پول مشترک بین افراد", onDone) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null); Spacer(Modifier.width(8.dp))
                Text("این کار سهم کسی را تسویه نمی‌کند؛ فقط نگهدارنده پول مشترک را تغییر می‌دهد. منشأ مبلغ به روش قدیمی‌ترین درآمد اول ثبت می‌شود.")
            }
        }
        ChoiceField("فرستنده پول*", employees, fromEmployee, { it.name }, { fromEmployee = it; fromAccount = null })
        ChoiceField("حساب مبدأ*", fromAccounts.filter { account -> custodyRows.any { it.accountId == account.id && it.amount > 0 } }, fromAccount, { account ->
            val value = custodyRows.firstOrNull { it.accountId == account.id }?.amount ?: 0
            "${account.title} — ${formatMoney(value)}"
        }, { fromAccount = it }, enabled = fromEmployee != null)
        if (fromAccount != null) Text("موجودی قابل انتقال: ${formatMoney(available)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        ChoiceField("گیرنده پول*", employees.filter { it.id != fromEmployee?.id }, toEmployee, { it.name }, { toEmployee = it })
        MovementDetailsFields(method, { method = it }, cardNumber, { cardNumber = it }, movementDate, { movementDate = it }, movementTime, { movementTime = it })
        MoneyField(amount, { amount = it }, label = "مبلغ انتقال")
        PersianDateField("تاریخ حسابداری انتقال*", date, { date = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (amountLong != null && fromEmployee != null && toEmployee != null) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text("${formatMoney(amountLong)} پول مشترک از ${fromEmployee!!.name} به ${toEmployee!!.name} منتقل می‌شود و در تاریخچه هر دو ثبت خواهد شد.", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
            }
        }
        PrimaryButton("ثبت انتقال", enabled = amountLong != null && amountLong in 1..available && fromAccount != null && toEmployee != null && date.year == year && cardValid && movementTimeValid) {
            viewModel.transfer(
                TransferCustodyCommand(
                    financialYear = year,
                    dateEpochDay = date.toEpochDay(),
                    fromAccountId = fromAccount!!.id,
                    toAccountId = null,
                    amount = amountLong!!,
                    note = note,
                    toEmployeeId = toEmployee!!.id,
                    method = method,
                    cardNumber = cardNumber,
                    movementDateEpochDay = movementDate?.toEpochDay(),
                    movementTimeMinutes = MovementTimeInput.parse(movementTime),
                ),
            ) { onDone() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun MoneyMethod.labelFa(): String = when (this) {
    MoneyMethod.CASH -> "نقدی"
    MoneyMethod.CARD -> "کارت‌به‌کارت"
    MoneyMethod.POS -> "دستگاه کارت‌خوان (پوز)"
}

internal fun movementMoment(dateEpochDay: Long?, timeMinutes: Int?): String? {
    if (dateEpochDay == null) return null
    val date = PersianDate.fromEpochDay(dateEpochDay).format()
    return timeMinutes?.let { "$date • ساعت ${MovementTimeInput.formatMinutes(it)}" } ?: date
}
