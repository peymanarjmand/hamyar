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
import androidx.compose.ui.platform.testTag
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
    val balances by viewModel.balances.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    var pendingDisable by remember { mutableStateOf<EmployeeEntity?>(null) }
    fun setEligibility(employee: EmployeeEntity, enabled: Boolean) {
        viewModel.updateEmployee(
            UpdateEmployeeCommand(employee.id, employee.name, employee.phone, employee.role, employee.notes, enabled),
        ) {}
    }
    val orderedEmployees = remember(employees, balances) {
        employees.sortedWith(
            compareBy<EmployeeEntity> { employee -> balances.firstOrNull { it.employeeId == employee.id }?.isFullySettled != false }
                .thenByDescending { employee -> balances.firstOrNull { it.employeeId == employee.id }?.custody ?: 0L }
                .thenBy { it.name },
        )
    }
    Scaffold(
        topBar = { ScreenHeader("پرسنل", "سهم، پول مشترک و نتیجه حساب هر شخص") },
        floatingActionButton = { ExtendedFloatingActionButton(text = { Text("افزودن پرسنل") }, icon = { Icon(Icons.Rounded.PersonAdd, null) }, onClick = { nav.navigate("employee/add") }) },
    ) { padding ->
        if (employees.isEmpty()) EmptyState("پرسنلی ثبت نشده", "برای شروع اولین نفر را اضافه کنید.", Modifier.padding(padding))
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(orderedEmployees, key = { it.id }) { employee ->
                val balance = balances.firstOrNull { it.employeeId == employee.id }
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("employee/${employee.id}") }, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) { Text(employee.name.take(1), Modifier.padding(14.dp), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(employee.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(employee.role.labelFa() + if (employee.phone.isNotBlank()) " • ${employee.phone.toPersianDigits()}" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronLeft, null)
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("سهم از انعام و ضایعات", fontWeight = FontWeight.Bold)
                                Text(
                                    "برای ثبت‌های جدید: ${if (employee.sharedIncomeEligible) "روشن" else "خاموش"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = employee.sharedIncomeEligible,
                                onCheckedChange = { enabled -> if (enabled) setEligibility(employee, true) else pendingDisable = employee },
                                modifier = Modifier.testTag("employee-share-switch-${employee.id}"),
                            )
                        }
                        balance?.let { PersonFinancialSummary(it, year, compact = true) }
                    }
                }
            }
        }
    }
    pendingDisable?.let { employee ->
        AlertDialog(
            onDismissRequest = { pendingDisable = null },
            title = { Text("خاموش‌کردن سهم ${employee.name}", fontWeight = FontWeight.Bold) },
            text = { Text("از درآمدهایی که بعد از این ثبت می‌شوند سهم نمی‌گیرد؛ اما همچنان می‌تواند پول مشترک را دریافت یا منتقل کند. سهم‌ها و تراکنش‌های قبلی تغییر نمی‌کنند.") },
            confirmButton = {
                Button({ setEligibility(employee, false); pendingDisable = null }) { Text("خاموش شود") }
            },
            dismissButton = { TextButton({ pendingDisable = null }) { Text("انصراف") } },
        )
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
    var notes by remember { mutableStateOf("") }
    var sharedIncomeEligible by remember { mutableStateOf(true) }
    FormScaffold("افزودن پرسنل", onDone) {
        OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("شماره تماس — اختیاری") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        ChoiceField("نقش در تعمیرگاه*", EmployeeRole.entries, role, { it.labelFa() }, { role = it })
        PersianDateField("اولین روز همکاری*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasEnd, { hasEnd = it }); Spacer(Modifier.width(8.dp)); Text("همکاری پایان مشخص دارد") }
        if (hasEnd) PersianDateField("آخرین روز همکاری", end, { end = it })
        OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت — اختیاری") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
        EligibilitySetting(sharedIncomeEligible, { sharedIncomeEligible = it })
        Text("روز شروع و آخرین روز همکاری هر دو در محاسبه سهم لحاظ می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("ثبت پرسنل", enabled = name.trim().length >= 2 && (!hasEnd || end.toEpochDay() >= start.toEpochDay())) {
            viewModel.addEmployee(AddEmployeeCommand(name, phone, role, start.toEpochDay(), if (hasEnd) end.toEpochDay() else null, notes, sharedIncomeEligible), onSuccess = { onDone() })
        }
    }
}

@Composable
fun EditEmployeeScreen(viewModel: AppViewModel, employeeId: Long, onDone: () -> Unit) {
    val employee by produceState<EmployeeEntity?>(null, employeeId) { value = viewModel.employee(employeeId) }
    val person = employee ?: return
    var name by remember(person.id) { mutableStateOf(person.name) }
    var phone by remember(person.id) { mutableStateOf(person.phone) }
    var role by remember(person.id) { mutableStateOf(person.role) }
    var notes by remember(person.id) { mutableStateOf(person.notes) }
    var sharedIncomeEligible by remember(person.id) { mutableStateOf(person.sharedIncomeEligible) }

    FormScaffold("ویرایش مشخصات پرسنل", onDone) {
        OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("شماره تماس — اختیاری") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        ChoiceField("نقش در تعمیرگاه*", EmployeeRole.entries, role, { it.labelFa() }, { role = it })
        OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت — اختیاری") }, modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(14.dp))
        EligibilitySetting(sharedIncomeEligible, { sharedIncomeEligible = it })
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text("ویرایش مشخصات، سهم‌ها و تراکنش‌های ثبت‌شده قبلی را تغییر نمی‌دهد.", Modifier.padding(14.dp))
        }
        PrimaryButton("ذخیره تغییرات", enabled = name.trim().length >= 2) {
            viewModel.updateEmployee(UpdateEmployeeCommand(person.id, name, phone, role, notes, sharedIncomeEligible), onDone)
        }
    }
}

@Composable
internal fun EligibilitySetting(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("سهم از انعام و ضایعات", fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "در ثبت‌های جدید، در صورت حضور سهم می‌گیرد." else "سهم نمی‌گیرد؛ اما می‌تواند پول مشترک را دریافت یا منتقل کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(enabled, onEnabledChange, Modifier.testTag("shared-income-eligibility-switch"))
        }
    }
}

@Composable
fun PersonnelDetailScreen(viewModel: AppViewModel, employeeId: Long, nav: NavController) {
    val employee by viewModel.employeeFlow(employeeId).collectAsState(initial = null)
    val settings by viewModel.settings.collectAsState()
    val accounts by viewModel.accounts(employeeId).collectAsState(initial = emptyList())
    val employment by viewModel.employment(employeeId).collectAsState(initial = emptyList())
    val absences by viewModel.absences(employeeId).collectAsState(initial = emptyList())
    val history by viewModel.history(employeeId).collectAsState(initial = emptyList())
    val balances by viewModel.balances.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val balance = balances.firstOrNull { it.employeeId == employeeId }
    var revealedCard by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showEndEmployment by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val person = employee
    if (person == null) { EmptyState("پرسنل پیدا نشد", "ممکن است سابقه غیرفعال شده باشد."); return }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.background(MaterialTheme.colorScheme.primary).padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ nav.popBackStack() }) { Icon(Icons.Rounded.ArrowForward, "بازگشت", tint = MaterialTheme.colorScheme.onPrimary) }
                        TextButton({ nav.navigate("employee/$employeeId/edit") }) {
                            Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("ویرایش", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(person.name, color = MaterialTheme.colorScheme.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(person.role.labelFa() + if (person.phone.isNotBlank()) " • ${person.phone.toPersianDigits()}" else "", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
                    Text(
                        if (person.sharedIncomeEligible) "سهم از ثبت‌های جدید انعام و ضایعات: روشن" else "سهم از ثبت‌های جدید انعام و ضایعات: خاموش",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (person.notes.isNotBlank()) Text(person.notes, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    if (balance != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) { PersonFinancialSummary(balance, year) }
                    }
                }
            }
            item { SectionTitle("محل‌های نگهداری پول مشترک", null, null) }
            items(accounts, key = { personnelDetailItemKey(PersonnelDetailItemSection.ACCOUNT, it.id.toString()) }) { account ->
                Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (account.type) {
                                AccountType.CARD -> Icons.Rounded.CreditCard
                                AccountType.POS -> Icons.Rounded.PointOfSale
                                AccountType.CASH -> Icons.Rounded.Payments
                            },
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                            Text(account.title, fontWeight = FontWeight.Bold)
                            if (account.type == AccountType.CARD) {
                                val full = revealedCard?.takeIf { it.first == account.id }?.second
                                Text(
                                    when {
                                        full != null -> IranianCardValidator.formatFull(full)
                                        account.cardLast4.isNotBlank() -> "****-****-****-${account.cardLast4.toPersianDigits()}"
                                        else -> "بدون شماره ثابت؛ جزئیات هنگام تراکنش اختیاری است"
                                    },
                                )
                                if (account.cardNumberEncrypted.isNotBlank()) Text("${account.bankName} • به نام ${account.ownerName}", style = MaterialTheme.typography.bodySmall)
                            } else Text(
                                if (account.type == AccountType.POS) "وجه دریافت‌شده از دستگاه کارت‌خوان" else "محل نگهداری پول نقد مشترک",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (account.type == AccountType.CARD && account.cardNumberEncrypted.isNotBlank()) {
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
            items(employment, key = { personnelDetailItemKey(PersonnelDetailItemSection.EMPLOYMENT, it.id.toString()) }) { period ->
                TimelineCard(
                    Icons.Rounded.Work,
                    "همکاری",
                    "از ${PersianDate.fromEpochDay(period.startEpochDay).format()} تا ${period.endEpochDay?.let(PersianDate::fromEpochDay)?.format() ?: "ادامه دارد"}",
                    actionLabel = "ویرایش",
                    onClick = { nav.navigate("employee/$employeeId/employment/${period.id}") },
                )
            }
            item { SectionTitle("غیبت‌های موقت", "ثبت غیبت") { nav.navigate("employee/$employeeId/absence") } }
            if (absences.isEmpty()) item { EmptyState("غیبتی ثبت نشده", "این شخص در دوره‌های همکاری حاضر محسوب می‌شود.") }
            else items(absences, key = { personnelDetailItemKey(PersonnelDetailItemSection.ABSENCE, it.id.toString()) }) { absence ->
                val text = "از ${PersianDate.fromEpochDay(absence.startEpochDay).format()} تا ${absence.returnEpochDay?.let(PersianDate::fromEpochDay)?.format() ?: "بازگشت ثبت نشده"}"
                val policy = absencePolicyText(absence, settings.absenceGraceDays)
                TimelineCard(
                    Icons.Rounded.EventBusy,
                    if (absence.returnEpochDay == null) "غیبت باز" else "غیبت موقت",
                    text + if (absence.note.isBlank()) "" else " • ${absence.note}",
                    status = policy,
                    actionLabel = if (absence.returnEpochDay == null) "ثبت بازگشت" else "ویرایش",
                    onClick = { nav.navigate("employee/$employeeId/absence/${absence.id}") },
                )
            }
            item { SectionTitle("حساب و تاریخچه سال", null, null) }
            if (history.isEmpty()) item { EmptyState("هنوز سابقه مالی ندارد", "سهم‌ها، دریافت پول مشترک، انتقال و تسویه اینجا نمایش داده می‌شوند.") }
            else items(history.take(50), key = { personnelDetailItemKey(PersonnelDetailItemSection.HISTORY, it.rowId) }) { row -> HistoryCard(row) { row.sourceIncomeEventId?.let { nav.navigate("income/$it") } } }
        }
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
private fun TimelineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    status: String? = null,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text.toPersianDigits(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                status?.let { Text(it.toPersianDigits(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
            }
            actionLabel?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private fun absencePolicyText(absence: AbsencePeriodEntity, graceDays: Int): String {
    val policy = AbsencePolicy.evaluate(absence.startEpochDay, absence.returnEpochDay, java.time.LocalDate.now().toEpochDay(), graceDays)
    if (!policy.hasStarted) return "طبق قانون فعلی: این غیبت هنوز شروع نشده است"
    if (policy.isOpen) {
        return if (policy.currentlyEligible == true) {
            "طبق قانون فعلی: اکنون سهم دارد • روز ${policy.absentDays} از $graceDays"
        } else {
            "طبق قانون فعلی: اکنون سهم ندارد • ${policy.absentDays} روز غیبت"
        }
    }
    return when {
        policy.absentDays == 0 -> "طبق قانون فعلی: روز غیبتی ثبت نشده است"
        policy.ineligibleDays == 0 -> "طبق قانون فعلی: تمام ${policy.absentDays} روز سهم دارد"
        policy.eligibleDays == 0 -> "طبق قانون فعلی: تمام ${policy.absentDays} روز سهم ندارد"
        else -> "طبق قانون فعلی: ${policy.eligibleDays} روز سهم دارد • ${policy.ineligibleDays} روز سهم ندارد"
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
    val normalDirection = when (row.entryType) {
        "ENTITLEMENT", "INCOME_RECEIPT", "TRANSFER_IN" -> 1
        "TRANSFER_OUT", "SETTLEMENT_PAID", "SETTLEMENT_RECEIVED", "SELF_OFFSET" -> -1
        else -> if (row.amountSigned >= 0) 1 else -1
    }
    val isCorrectionReversal = row.amountSigned != 0L &&
        (if (row.amountSigned > 0) 1 else -1) != normalDirection
    val title = if (isCorrectionReversal) {
        "خنثی‌سازی ثبت قبلی برای اصلاح"
    } else when (row.entryType) {
        "ENTITLEMENT" -> "سهم از درآمد مشترک"
        "INCOME_RECEIPT" -> "دریافت پول مشترک"
        "TRANSFER_IN" -> "دریافت پول مشترک از همکار"
        "TRANSFER_OUT" -> "تحویل پول مشترک به همکار"
        "SETTLEMENT_PAID" -> "پرداخت تسویه"
        "SETTLEMENT_RECEIVED" -> "دریافت تسویه"
        "SELF_OFFSET" -> "برداشت سهم از پول نزد خودش"
        else -> "گردش حساب"
    }
    val sign = if (row.amountSigned >= 0) "+" else "−"
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
    AbsenceFormScreen(viewModel, employeeId, absenceId = null, onDone)
}

@Composable
fun EditAbsenceScreen(viewModel: AppViewModel, employeeId: Long, absenceId: Long, onDone: () -> Unit) {
    AbsenceFormScreen(viewModel, employeeId, absenceId, onDone)
}

@Composable
private fun AbsenceFormScreen(viewModel: AppViewModel, employeeId: Long, absenceId: Long?, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val existing by produceState<AbsencePeriodEntity?>(null, absenceId) {
        value = absenceId?.let { viewModel.absence(it) }
    }
    if (absenceId != null && existing == null) return
    val record = existing
    var start by remember(record?.id) { mutableStateOf(record?.startEpochDay?.let(PersianDate::fromEpochDay) ?: PersianDate.today()) }
    var hasReturn by remember(record?.id) { mutableStateOf(record?.returnEpochDay != null) }
    var returned by remember(record?.id) { mutableStateOf(record?.returnEpochDay?.let(PersianDate::fromEpochDay) ?: PersianDate.today()) }
    var note by remember(record?.id) { mutableStateOf(record?.note.orEmpty()) }
    val editing = record != null

    FormScaffold(if (editing) "ویرایش غیبت موقت" else "ثبت غیبت موقت", onDone) {
        PersianDateField("اولین روز غیبت*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasReturn, { hasReturn = it }); Spacer(Modifier.width(8.dp)); Text("روز بازگشت مشخص است") }
        if (hasReturn) PersianDateField("روز بازگشت — حاضر محسوب می‌شود", returned, { returned = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(
                if (settings.absenceGraceDays == 0) {
                    "طبق قانون فعلی، غیبت از روز اول سهم ندارد. جمعه و تعطیلات نیز شمرده می‌شوند. درآمدهای قبلی تغییر نمی‌کنند."
                } else {
                    "طبق قانون فعلی، ${settings.absenceGraceDays} روز اول غیبت سهم دارد و از روز ${(settings.absenceGraceDays + 1)} سهم ندارد. جمعه و تعطیلات نیز شمرده می‌شوند. درآمدهای قبلی تغییر نمی‌کنند."
                }.toPersianDigits(),
                Modifier.padding(14.dp),
            )
        }
        PrimaryButton(
            if (editing && record.returnEpochDay == null && hasReturn) "ثبت بازگشت و ذخیره" else if (editing) "ذخیره تغییرات" else "ثبت غیبت",
            enabled = !hasReturn || returned.toEpochDay() > start.toEpochDay(),
        ) {
            val returnDay = if (hasReturn) returned.toEpochDay() else null
            if (record == null) {
                viewModel.addAbsence(employeeId, start.toEpochDay(), returnDay, note) { onDone() }
            } else {
                viewModel.updateAbsence(record.id, employeeId, start.toEpochDay(), returnDay, note, onDone)
            }
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
fun EditEmploymentScreen(viewModel: AppViewModel, employeeId: Long, periodId: Long, onDone: () -> Unit) {
    val existing by produceState<EmploymentPeriodEntity?>(null, periodId) { value = viewModel.employmentPeriod(periodId) }
    val period = existing ?: return
    var start by remember(period.id) { mutableStateOf(PersianDate.fromEpochDay(period.startEpochDay)) }
    var hasEnd by remember(period.id) { mutableStateOf(period.endEpochDay != null) }
    var end by remember(period.id) { mutableStateOf(period.endEpochDay?.let(PersianDate::fromEpochDay) ?: PersianDate.today()) }
    FormScaffold("ویرایش دوره همکاری", onDone) {
        PersianDateField("اولین روز همکاری*", start, { start = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasEnd, { hasEnd = it }); Spacer(Modifier.width(8.dp)); Text("همکاری پایان مشخص دارد") }
        if (hasEnd) PersianDateField("آخرین روز همکاری", end, { end = it })
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text("بازه جدید نباید با دوره دیگری هم‌پوشانی داشته باشد یا غیبت ثبت‌شده‌ای را بیرون دوره همکاری قرار دهد. سهم‌های قبلی تغییر نمی‌کنند.", Modifier.padding(14.dp))
        }
        PrimaryButton("ذخیره دوره همکاری", enabled = !hasEnd || end.toEpochDay() >= start.toEpochDay()) {
            viewModel.updateEmployment(period.id, employeeId, start.toEpochDay(), if (hasEnd) end.toEpochDay() else null, onDone)
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
