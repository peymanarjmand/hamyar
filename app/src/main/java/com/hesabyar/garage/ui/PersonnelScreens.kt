package com.hesabyar.garage.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hesabyar.garage.data.*
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.util.*

fun EmployeeRole.labelFa(): String = when (this) {
    EmployeeRole.BODYWORK -> "صافکار"
    EmployeeRole.PAINTER -> "نقاش"
    EmployeeRole.BOTH -> "صافکار و نقاش"
    EmployeeRole.OTHER -> "سایر"
}

internal enum class PersonnelDetailItemSection(val keyPrefix: String) {
    ACCOUNT("account"),
    EMPLOYMENT("employment"),
    ABSENCE("absence"),
    HISTORY("history"),
}

internal fun personnelDetailItemKey(section: PersonnelDetailItemSection, rawId: String): String =
    "${section.keyPrefix}:$rawId"

@Composable
fun PersonnelScreen(viewModel: AppViewModel, nav: NavController) {
    val employees by viewModel.employees.collectAsState()
    Scaffold(
        topBar = { ScreenHeader("پرسنل", "مشخصات، همکاری، غیبت و حساب هر شخص") },
        floatingActionButton = { ExtendedFloatingActionButton(text = { Text("افزودن پرسنل") }, icon = { Icon(Icons.Rounded.PersonAdd, null) }, onClick = { nav.navigate("employee/add") }) },
    ) { padding ->
        if (employees.isEmpty()) EmptyState("پرسنلی ثبت نشده", "برای شروع اولین نفر را اضافه کنید.", Modifier.padding(padding))
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(employees, key = { it.id }) { employee ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("employee/${employee.id}") }, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) { Text(employee.name.take(1), Modifier.padding(14.dp), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(employee.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(employee.role.labelFa() + if (employee.phone.isNotBlank()) " • ${employee.phone.toPersianDigits()}" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null)
                    }
                }
            }
        }
    }
}

@Composable
fun AddEmployeeScreen(viewModel: AppViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(EmployeeRole.BODYWORK) }
    var start by remember { mutableStateOf(PersianDate.today()) }
    var hasEnd by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(PersianDate.today()) }
    FormScaffold("افزودن پرسنل", onDone) {
        OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("شماره تماس — اختیاری") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        ChoiceField("نقش در تعمیرگاه*", EmployeeRole.entries, role, { it.labelFa() }, { role = it })
        PersianDateField("اولین روز همکاری*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasEnd, { hasEnd = it }); Spacer(Modifier.width(8.dp)); Text("همکاری پایان مشخص دارد") }
        if (hasEnd) PersianDateField("آخرین روز همکاری", end, { end = it })
        Text("روز شروع و آخرین روز همکاری هر دو در محاسبه سهم لحاظ می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("ثبت پرسنل", enabled = name.trim().length >= 2 && (!hasEnd || end.toEpochDay() >= start.toEpochDay())) {
            viewModel.addEmployee(AddEmployeeCommand(name, phone, role, start.toEpochDay(), if (hasEnd) end.toEpochDay() else null), onSuccess = { onDone() })
        }
    }
}

@Composable
fun PersonnelDetailScreen(viewModel: AppViewModel, employeeId: Long, nav: NavController) {
    val employee by produceState<EmployeeEntity?>(null, employeeId) { value = viewModel.employee(employeeId) }
    val accounts by viewModel.accounts(employeeId).collectAsState(initial = emptyList())
    val employment by viewModel.employment(employeeId).collectAsState(initial = emptyList())
    val absences by viewModel.absences(employeeId).collectAsState(initial = emptyList())
    val history by viewModel.history(employeeId).collectAsState(initial = emptyList())
    val balances by viewModel.balances.collectAsState()
    val balance = balances.firstOrNull { it.employeeId == employeeId }
    var revealedCard by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showEndEmployment by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val person = employee
    if (person == null) { EmptyState("پرسنل پیدا نشد", "ممکن است سابقه غیرفعال شده باشد."); return }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.background(MaterialTheme.colorScheme.primary).padding(20.dp)) {
                IconButton({ nav.popBackStack() }) { Icon(Icons.Rounded.ArrowForward, "بازگشت", tint = MaterialTheme.colorScheme.onPrimary) }
                Text(person.name, color = MaterialTheme.colorScheme.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(person.role.labelFa() + if (person.phone.isNotBlank()) " • ${person.phone.toPersianDigits()}" else "", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
                Spacer(Modifier.height(16.dp))
                if (balance != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        BalanceLabel(balance)
                        Spacer(Modifier.height(7.dp))
                        Row { Text("سهم باقی‌مانده: ${formatMoney(balance.claim)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("امانت: ${formatMoney(balance.custody)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { SectionTitle("کارت‌ها و پول نقد", "افزودن کارت") { nav.navigate("employee/$employeeId/card") } }
        items(accounts, key = { personnelDetailItemKey(PersonnelDetailItemSection.ACCOUNT, it.id.toString()) }) { account ->
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (account.type == AccountType.CARD) Icons.Rounded.CreditCard else Icons.Rounded.Payments, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                        Text(account.title, fontWeight = FontWeight.Bold)
                        if (account.type == AccountType.CARD) {
                            val full = revealedCard?.takeIf { it.first == account.id }?.second
                            Text(if (full != null) IranianCardValidator.formatFull(full) else "****-****-****-${account.cardLast4.toPersianDigits()}")
                            Text("${account.bankName} • به نام ${account.ownerName}", style = MaterialTheme.typography.bodySmall)
                        } else Text("حساب امانت نقدی", style = MaterialTheme.typography.bodySmall)
                    }
                    if (account.type == AccountType.CARD) {
                        IconButton({
                            val current = revealedCard
                            if (current?.first == account.id) revealedCard = null else viewModel.revealCard(account.id) { revealedCard = account.id to it }
                        }) { Icon(if (revealedCard?.first == account.id) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "نمایش شماره") }
                        if (revealedCard?.first == account.id) IconButton({ clipboard.setText(AnnotatedString(revealedCard!!.second)) }) { Icon(Icons.Rounded.ContentCopy, "کپی") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("دوره‌های همکاری", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (employment.any { it.endEpochDay == null }) TextButton({ showEndEmployment = true }) { Text("پایان همکاری") }
                else TextButton({ nav.navigate("employee/$employeeId/employment") }) { Text("افزودن دوره") }
            }
        }
        items(employment, key = { personnelDetailItemKey(PersonnelDetailItemSection.EMPLOYMENT, it.id.toString()) }) { period -> TimelineCard(Icons.Rounded.Work, "همکاری", "از ${PersianDate.fromEpochDay(period.startEpochDay).format()} تا ${period.endEpochDay?.let(PersianDate::fromEpochDay)?.format() ?: "ادامه دارد"}") }
        item { SectionTitle("غیبت‌های موقت", "ثبت غیبت") { nav.navigate("employee/$employeeId/absence") } }
        if (absences.isEmpty()) item { EmptyState("غیبتی ثبت نشده", "این شخص در دوره‌های همکاری حاضر محسوب می‌شود.") }
        else items(absences, key = { personnelDetailItemKey(PersonnelDetailItemSection.ABSENCE, it.id.toString()) }) { absence ->
            val text = "از ${PersianDate.fromEpochDay(absence.startEpochDay).format()} تا ${absence.returnEpochDay?.let(PersianDate::fromEpochDay)?.format() ?: "بازگشت ثبت نشده"}"
            TimelineCard(Icons.Rounded.EventBusy, "غیبت موقت", text + if (absence.note.isBlank()) "" else " • ${absence.note}")
        }
        item { SectionTitle("حساب و تاریخچه سال", null, null) }
        if (history.isEmpty()) item { EmptyState("هنوز سابقه مالی ندارد", "سهم‌ها، دریافت امانت، انتقال و تسویه اینجا نمایش داده می‌شوند.") }
        else items(history.take(50), key = { personnelDetailItemKey(PersonnelDetailItemSection.HISTORY, it.rowId) }) { row -> HistoryCard(row) { row.sourceIncomeEventId?.let { nav.navigate("income/$it") } } }
    }
    if (showEndEmployment) EndEmploymentDialog(viewModel, employeeId, { showEndEmployment = false }) { showEndEmployment = false }
}

@Composable
private fun SectionTitle(title: String, action: String?, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null && onClick != null) TextButton(onClick) { Text(action) }
    }
}

@Composable
private fun TimelineCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(text.toPersianDigits(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun HistoryCard(row: PersonHistoryRow, onClick: () -> Unit) {
    if (row.category == "ELIGIBILITY") {
        val parts = row.detail.split('|', limit = 2)
        val source = if (parts.firstOrNull() == "TIP") "انعام مشتری" else "فروش ضایعات"
        val reason = parts.getOrElse(1) { "" }
        val eligible = row.entryType == "ELIGIBLE"
        Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (eligible) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (eligible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("$source — ${if (eligible) "سهم دارد" else "سهم ندارد"}", fontWeight = FontWeight.Bold)
                    Text("${PersianDate.fromEpochDay(row.businessDateEpochDay).format()} • $reason", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (eligible) formatMoney(row.amountSigned) else "—", fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    val (title, sign) = when (row.entryType) {
        "ENTITLEMENT" -> "سهم از درآمد مشترک" to "+"
        "INCOME_RECEIPT" -> "دریافت امانت گروه" to "+"
        "TRANSFER_IN" -> "دریافت انتقال امانت" to "+"
        "TRANSFER_OUT" -> "ارسال انتقال امانت" to "−"
        "SETTLEMENT_PAID" -> "پرداخت تسویه" to "−"
        "SETTLEMENT_RECEIVED" -> "دریافت تسویه" to "−"
        "SELF_OFFSET" -> "تهاتر سهم و امانت" to "−"
        else -> "گردش حساب" to if (row.amountSigned >= 0) "+" else "−"
    }
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(PersianDate.fromEpochDay(row.businessDateEpochDay).format(), style = MaterialTheme.typography.bodySmall) }
            Text("$sign${formatMoney(kotlin.math.abs(row.amountSigned))}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EndEmploymentDialog(viewModel: AppViewModel, employeeId: Long, onDismiss: () -> Unit, onDone: () -> Unit) {
    var date by remember { mutableStateOf(PersianDate.today()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت پایان همکاری", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("این تاریخ آخرین روز کاری است و خود این روز سهم دارد. از روز بعد سهمی محاسبه نمی‌شود.")
                PersianDateField("آخرین روز کاری", date, { date = it })
            }
        },
        confirmButton = { Button({ viewModel.endEmployment(employeeId, date.toEpochDay(), onDone) }) { Text("ثبت پایان همکاری") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun AddAbsenceScreen(viewModel: AppViewModel, employeeId: Long, onDone: () -> Unit) {
    var start by remember { mutableStateOf(PersianDate.today()) }
    var hasReturn by remember { mutableStateOf(true) }
    var returned by remember { mutableStateOf(PersianDate.today()) }
    var note by remember { mutableStateOf("") }
    FormScaffold("ثبت غیبت موقت", onDone) {
        PersianDateField("اولین روز غیبت*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasReturn, { hasReturn = it }); Spacer(Modifier.width(8.dp)); Text("روز بازگشت مشخص است") }
        if (hasReturn) PersianDateField("روز بازگشت — حاضر محسوب می‌شود", returned, { returned = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text("روزهای ۱ تا ۱۴ غیبت سهم دارند؛ از روز ۱۵ به بعد سهم محاسبه نمی‌شود. جمعه و تعطیلات نیز شمرده می‌شوند.", Modifier.padding(14.dp))
        }
        PrimaryButton("ثبت غیبت", enabled = !hasReturn || returned.toEpochDay() > start.toEpochDay()) {
            viewModel.addAbsence(employeeId, start.toEpochDay(), if (hasReturn) returned.toEpochDay() else null, note) { onDone() }
        }
    }
}

@Composable
fun AddEmploymentScreen(viewModel: AppViewModel, employeeId: Long, onDone: () -> Unit) {
    var start by remember { mutableStateOf(PersianDate.today()) }
    var hasEnd by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(PersianDate.today()) }
    FormScaffold("دوره همکاری جدید", onDone) {
        PersianDateField("اولین روز همکاری*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasEnd, { hasEnd = it }); Spacer(Modifier.width(8.dp)); Text("آخرین روز مشخص است") }
        if (hasEnd) PersianDateField("آخرین روز همکاری", end, { end = it })
        Text("برای بازگشت پس از پایان همکاری، یک دوره جدید ثبت کنید. این مورد با غیبت موقت فرق دارد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("ثبت دوره", enabled = !hasEnd || end.toEpochDay() >= start.toEpochDay()) {
            viewModel.addEmployment(employeeId, start.toEpochDay(), if (hasEnd) end.toEpochDay() else null) { onDone() }
        }
    }
}

@Composable
fun AddCardScreen(viewModel: AppViewModel, employeeId: Long, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    val valid = IranianCardValidator.isValid(card)
    FormScaffold("افزودن کارت بانکی", onDone) {
        OutlinedTextField(title, { title = it }, label = { Text("عنوان کارت؛ مثلاً کارت ملت") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(owner, { owner = it }, label = { Text("نام صاحب کارت*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(bank, { bank = it }, label = { Text("نام بانک") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(
            IranianCardValidator.formatFull(card),
            { card = IranianCardValidator.normalize(it).take(16) },
            label = { Text("شماره کارت ۱۶ رقمی*") },
            isError = card.length == 16 && !valid,
            supportingText = { Text(if (card.length == 16 && !valid) "شماره کارت از نظر کنترل بانکی معتبر نیست" else "شماره کامل فقط روی گوشی و در پشتیبان رمزدار نگهداری می‌شود") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        )
        PrimaryButton("ذخیره کارت", enabled = valid && owner.isNotBlank()) {
            viewModel.addCard(AddCardCommand(employeeId, title, owner, bank, card)) { onDone() }
        }
    }
}

@Composable
fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowForward, "بازگشت") }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
