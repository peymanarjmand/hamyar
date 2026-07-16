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
    val remainder by viewModel.remainder.collectAsState()
    val yearEntity by viewModel.selectedYearEntity.collectAsState()
    var type by remember { mutableStateOf(IncomeType.TIP) }
    var date by remember(year) { mutableStateOf(defaultBusinessDate(year)) }
    var amount by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf<EmployeeEntity?>(null) }
    val accounts by remember(receiver?.id) { receiver?.let { viewModel.accounts(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var account by remember(receiver?.id) { mutableStateOf<MoneyAccountEntity?>(null) }
    var description by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<EligibilityPreview>>(emptyList()) }
    LaunchedEffect(date, employees) { preview = viewModel.previewEligibility(date.toEpochDay()) }
    LaunchedEffect(accounts) { if (account !in accounts) account = accounts.firstOrNull() }
    val eligible = preview.filter { it.result.isEligible }
    val amountLong = amount.toLongOrNull()
    val closed = yearEntity?.status == FinancialYearStatus.CLOSED

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (closed) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("سال انتخاب‌شده بسته است و ثبت جدید ندارد.", Modifier.padding(14.dp)) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(type == IncomeType.TIP, { type = IncomeType.TIP }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("انعام مشتری") }
            SegmentedButton(type == IncomeType.SCRAP, { type = IncomeType.SCRAP }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("فروش ضایعات") }
        }
        PersianDateField("تاریخ دریافت*", date, { date = it })
        MoneyField(amount, { amount = it })
        ChoiceField("چه کسی پول را دریافت کرده؟*", employees, receiver, { it.name }, { receiver = it; account = null })
        ChoiceField("پول کجا دریافت شده؟*", accounts, account, { if (it.type == AccountType.CARD) "${it.title} • ${it.cardLast4}" else it.title }, { account = it }, enabled = receiver != null)
        OutlinedTextField(description, { description = it }, label = { Text("شرح، نام مشتری یا خودرو — اختیاری") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("پیش‌نمایش تقسیم", fontWeight = FontWeight.Bold)
                Text("${eligible.size} نفر سهم دارند${if (remainder > 0) " • ${formatMoney(remainder)} باقیمانده قبلی هم تقسیم می‌شود" else ""}")
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
        PrimaryButton("ثبت و محاسبه سهم‌ها", enabled = !closed && amountLong != null && amountLong > 0 && account != null && eligible.isNotEmpty() && date.year == year) {
            viewModel.registerIncome(RegisterIncomeCommand(year, date.toEpochDay(), amountLong!!, type, receiver!!.id, account!!.id, description)) { onSaved() }
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
    val incomes by viewModel.incomes.collectAsState()
    if (incomes.isEmpty()) EmptyState("هنوز انعام یا ضایعاتی ثبت نشده", "پس از ثبت، سهم هر فرد و محل دریافت پول در تاریخچه می‌ماند.")
    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(incomes, key = { it.id }) { IncomeRow(it, onClick = { nav.navigate("income/${it.id}") }) }
    }
}

@Composable
fun IncomeDetailScreen(viewModel: AppViewModel, eventId: Long, onBack: () -> Unit) {
    val event by produceState<com.hesabyar.garage.data.local.IncomeEventEntity?>(null, eventId) { value = viewModel.incomeEvent(eventId) }
    val snapshots by viewModel.eligibility(eventId).collectAsState(initial = emptyList())
    val employees by viewModel.employees.collectAsState()
    val receiver by produceState<EmployeeEntity?>(null, event?.receiverEmployeeId) { value = event?.receiverEmployeeId?.let { viewModel.employee(it) } }
    val account by produceState<MoneyAccountEntity?>(null, event?.receiverAccountId) { value = event?.receiverAccountId?.let { viewModel.account(it) } }
    var fullCard by remember { mutableStateOf<String?>(null) }
    val item = event ?: return
    FormScaffold(if (item.type == IncomeType.TIP) "جزئیات انعام" else "جزئیات فروش ضایعات", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(formatMoney(item.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(PersianDate.fromEpochDay(item.dateEpochDay).format())
                if (item.description.isNotBlank()) Text(item.description, fontWeight = FontWeight.Medium)
            }
        }
        DetailLine("دریافت‌کننده پول", receiver?.name ?: "—")
        DetailLine("روش دریافت", if (account?.type == AccountType.CARD) "واریز به کارت" else "پول نقد")
        account?.let { selected ->
            if (selected.type == AccountType.CARD) {
                DetailLine("کارت مقصد", fullCard?.let { com.hesabyar.garage.util.IranianCardValidator.formatFull(it) } ?: "****-****-****-${selected.cardLast4.toPersianDigits()}")
                DetailLine("صاحب کارت", selected.ownerName + if (selected.bankName.isBlank()) "" else " • ${selected.bankName}")
                OutlinedButton({ if (fullCard == null) viewModel.revealCard(selected.id) { fullCard = it } else fullCard = null }, Modifier.fillMaxWidth()) {
                    Text(if (fullCard == null) "نمایش شماره کامل کارت" else "پنهان کردن شماره کارت")
                }
            }
        }
        DetailLine("سهم هر فرد", formatMoney(item.sharePerPerson))
        DetailLine("باقیمانده واردشده از قبل", formatMoney(item.carriedRemainder))
        DetailLine("باقیمانده بعد از تقسیم", formatMoney(item.resultingRemainder))
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
    val toAccounts by remember(toEmployee?.id) { toEmployee?.let { viewModel.accounts(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var toAccount by remember(toEmployee?.id) { mutableStateOf<MoneyAccountEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(PersianDate.today()) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(fromAccounts) { if (fromAccount !in fromAccounts) fromAccount = fromAccounts.firstOrNull { account -> custodyRows.any { it.accountId == account.id } } }
    LaunchedEffect(toAccounts) { if (toAccount !in toAccounts) toAccount = toAccounts.firstOrNull() }
    val available = custodyRows.firstOrNull { it.accountId == fromAccount?.id }?.amount ?: 0
    val amountLong = amount.toLongOrNull()

    FormScaffold("انتقال امانت بین افراد", onDone) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null); Spacer(Modifier.width(8.dp))
                Text("این کار سهم کسی را تسویه نمی‌کند؛ فقط نگهدارنده پول مشترک را تغییر می‌دهد. منشأ مبلغ به روش قدیمی‌ترین درآمد اول ثبت می‌شود.")
            }
        }
        ChoiceField("فرستنده امانت*", employees, fromEmployee, { it.name }, { fromEmployee = it; fromAccount = null })
        ChoiceField("حساب مبدأ*", fromAccounts.filter { account -> custodyRows.any { it.accountId == account.id && it.amount > 0 } }, fromAccount, { account ->
            val value = custodyRows.firstOrNull { it.accountId == account.id }?.amount ?: 0
            "${account.title} — ${formatMoney(value)}"
        }, { fromAccount = it }, enabled = fromEmployee != null)
        if (fromAccount != null) Text("امانت قابل انتقال: ${formatMoney(available)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        ChoiceField("گیرنده امانت*", employees, toEmployee, { it.name }, { toEmployee = it; toAccount = null })
        ChoiceField("حساب مقصد؛ نقد یا کارت*", toAccounts, toAccount, { if (it.type == AccountType.CARD) "${it.title} • ${it.cardLast4}" else it.title }, { toAccount = it }, enabled = toEmployee != null)
        MoneyField(amount, { amount = it }, label = "مبلغ انتقال")
        PersianDateField("تاریخ انتقال", date, { date = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (amountLong != null && fromEmployee != null && toEmployee != null) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text("${formatMoney(amountLong)} امانت از ${fromEmployee!!.name} به ${toEmployee!!.name} منتقل می‌شود و در تاریخچه هر دو ثبت خواهد شد.", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
            }
        }
        PrimaryButton("ثبت انتقال امانت", enabled = amountLong != null && amountLong in 1..available && fromAccount != null && toAccount != null && fromAccount!!.id != toAccount!!.id && date.year == year) {
            viewModel.transfer(TransferCustodyCommand(year, date.toEpochDay(), fromAccount!!.id, toAccount!!.id, amountLong!!, note)) { onDone() }
        }
        Spacer(Modifier.height(24.dp))
    }
}
