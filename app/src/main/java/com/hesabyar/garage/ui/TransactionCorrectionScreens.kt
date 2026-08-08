package com.hesabyar.garage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hesabyar.garage.data.*
import com.hesabyar.garage.data.local.EmployeeEntity
import com.hesabyar.garage.data.local.MoneyAccountEntity
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.util.*
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.util.Locale

@Composable
fun CorrectIncomeScreen(
    viewModel: AppViewModel,
    eventId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val event by produceState<com.hesabyar.garage.data.local.IncomeEventEntity?>(null, eventId) {
        value = viewModel.incomeEvent(eventId)
    }
    val employees by viewModel.employees.collectAsState()
    val years by viewModel.years.collectAsState()
    val item = event ?: return CorrectionLoading()
    var type by remember(item.id) { mutableStateOf(item.type) }
    var date by remember(item.id) { mutableStateOf(PersianDate.fromEpochDay(item.dateEpochDay)) }
    var amount by remember(item.id) { mutableStateOf(item.amount.toString()) }
    var receiverId by remember(item.id) { mutableLongStateOf(item.receiverEmployeeId) }
    var method by remember(item.id) { mutableStateOf(item.method) }
    var cardNumber by remember(item.id) { mutableStateOf("") }
    var removeStoredCard by remember(item.id) { mutableStateOf(false) }
    var movementDate by remember(item.id) { mutableStateOf(item.movementDateEpochDay?.let(PersianDate::fromEpochDay)) }
    var movementTime by remember(item.id) { mutableStateOf(item.movementTimeMinutes.toMovementInput()) }
    var description by remember(item.id) { mutableStateOf(item.description) }
    var reason by remember(item.id) { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<EligibilityPreview>>(emptyList()) }
    LaunchedEffect(date, employees) { preview = viewModel.previewEligibility(date.toEpochDay()) }

    val receiver = employees.firstOrNull { it.id == receiverId }
    val amountLong = amount.toLongOrNull()
    val eligibleCount = preview.count { it.result.isEligible }
    val closed = years.firstOrNull { it.year == item.financialYear }?.status == FinancialYearStatus.CLOSED
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)

    FormScaffold("اصلاح درآمد", onBack) {
        CorrectionNotice(closed)
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
            { receiverId = it.id },
        )
        MovementDetailsFields(
            method,
            { method = it; if (it != MoneyMethod.CARD) removeStoredCard = false },
            cardNumber,
            { cardNumber = it; if (it.isNotBlank()) removeStoredCard = false },
            movementDate,
            { movementDate = it },
            movementTime,
            { movementTime = it },
            allowedMethods = setOf(MoneyMethod.CASH, MoneyMethod.CARD, MoneyMethod.POS),
        )
        if (method == MoneyMethod.CARD && item.method == MoneyMethod.CARD && (item.cardLast4.isNotBlank() || item.cardNumberEncrypted.isNotBlank())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(removeStoredCard, { removeStoredCard = it; if (it) cardNumber = "" })
                Column {
                    Text("حذف شماره کارت قبلی")
                    Text(
                        if (removeStoredCard) "شماره قبلی از نسخه اصلاحی حذف می‌شود." else "اگر شماره جدید وارد نکنید، شماره قبلی ${item.cardLast4.takeIf(String::isNotBlank)?.let { "با پایان ${it.toPersianDigits()}" }.orEmpty()} حفظ می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            description,
            { description = it },
            label = { Text("شرح، نام مشتری یا خودرو — اختیاری") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("پیش‌نمایش اثر", fontWeight = FontWeight.Bold)
                Text("مبلغ ثبت قبلی: ${formatMoney(item.amount)}")
                Text("سهم قبلی هر نفر: ${formatMoney(item.sharePerPerson)}")
                Text("سهم‌ها برای ${eligibleCount.toString().toPersianDigits()} نفر دوباره محاسبه می‌شود.")
                if (amountLong != null && amountLong > 0 && eligibleCount > 0) {
                    val distribution = AccountingEngine.distribute(amountLong, item.carriedRemainder, eligibleCount)
                    Text("سهم هر نفر بعد از اصلاح: ${formatMoney(distribution.sharePerPerson)}")
                    Text("باقیمانده بعد از اصلاح: ${formatMoney(distribution.resultingRemainder)}")
                }
            }
        }
        CorrectionReasonField(reason) { reason = it }
        PrimaryButton(
            "ثبت اصلاح درآمد",
            enabled = !closed && amountLong != null && amountLong > 0 && receiver != null && eligibleCount > 0 &&
                date.year == item.financialYear && date.toEpochDay() <= LocalDate.now().toEpochDay() &&
                cardValid && movementTimeValid && reason.trim().length >= 3,
        ) {
            viewModel.correctIncome(
                CorrectIncomeCommand(
                    originalEventId = item.id,
                    dateEpochDay = date.toEpochDay(),
                    amount = amountLong!!,
                    type = type,
                    receiverEmployeeId = receiverId,
                    receiverAccountId = item.receiverAccountId.takeIf { receiverId == item.receiverEmployeeId && method == item.method },
                    description = description,
                    method = method,
                    cardNumber = cardUpdate(method, item.method, cardNumber, removeStoredCard),
                    movementDateEpochDay = movementDate?.toEpochDay(),
                    movementTimeMinutes = MovementTimeInput.parse(movementTime),
                    correctionReason = reason,
                ),
                onSaved,
            )
        }
    }
}

@Composable
fun CorrectTransferScreen(
    viewModel: AppViewModel,
    transferId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val detail by produceState<TransferDetailData?>(null, transferId) { value = viewModel.transferDetail(transferId) }
    val employees by viewModel.employees.collectAsState()
    val years by viewModel.years.collectAsState()
    val custodyRows by viewModel.custodyAccounts.collectAsState()
    val data = detail ?: return CorrectionLoading()
    val original = data.transfer
    var fromEmployeeId by remember(original.id) { mutableLongStateOf(original.fromEmployeeId) }
    val fromAccounts by remember(fromEmployeeId, employees) {
        employees.firstOrNull { it.id == fromEmployeeId }?.let { viewModel.accounts(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var fromAccountId by remember(original.id) { mutableLongStateOf(original.fromAccountId) }
    var toEmployeeId by remember(original.id) { mutableLongStateOf(original.toEmployeeId) }
    var method by remember(original.id) { mutableStateOf(original.method) }
    var amount by remember(original.id) { mutableStateOf(original.amount.toString()) }
    var date by remember(original.id) { mutableStateOf(PersianDate.fromEpochDay(original.dateEpochDay)) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var cardNumber by remember(original.id) { mutableStateOf("") }
    var removeStoredCard by remember(original.id) { mutableStateOf(false) }
    var movementDate by remember(original.id) { mutableStateOf(original.movementDateEpochDay?.let(PersianDate::fromEpochDay)) }
    var movementTime by remember(original.id) { mutableStateOf(original.movementTimeMinutes.toMovementInput()) }
    var reason by remember(original.id) { mutableStateOf("") }

    val fromEmployee = employees.firstOrNull { it.id == fromEmployeeId }
    val toEmployee = employees.firstOrNull { it.id == toEmployeeId }
    val fromAccount = fromAccounts.firstOrNull { it.id == fromAccountId }
    val visibleAccounts = fromAccounts.filter { account ->
        custodyRows.any { it.accountId == account.id && it.amount > 0 } || account.id == original.fromAccountId
    }
    val available = (custodyRows.firstOrNull { it.accountId == fromAccountId }?.amount ?: 0L) +
        if (fromAccountId == original.fromAccountId) original.amount else 0L
    val amountLong = amount.toLongOrNull()
    val closed = years.firstOrNull { it.year == original.financialYear }?.status == FinancialYearStatus.CLOSED
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)

    FormScaffold("اصلاح انتقال پول مشترک", onBack) {
        CorrectionNotice(closed)
        ChoiceField("فرستنده پول*", employees, fromEmployee, { it.name }, {
            fromEmployeeId = it.id
            fromAccountId = 0
            if (toEmployeeId == it.id) toEmployeeId = 0
        })
        ChoiceField("حساب مبدأ*", visibleAccounts, fromAccount, { account ->
            val value = (custodyRows.firstOrNull { it.accountId == account.id }?.amount ?: 0L) + if (account.id == original.fromAccountId) original.amount else 0L
            "${account.title} — ${formatMoney(value)}"
        }, { fromAccountId = it.id }, enabled = fromEmployee != null)
        ChoiceField("گیرنده پول*", employees.filter { it.id != fromEmployeeId }, toEmployee, { it.name }, { toEmployeeId = it.id })
        MovementDetailsFields(
            method,
            { method = it },
            cardNumber,
            { cardNumber = it; if (it.isNotBlank()) removeStoredCard = false },
            movementDate,
            { movementDate = it },
            movementTime,
            { movementTime = it },
        )
        StoredCardChoice(original.method, original.cardLast4, original.cardNumberEncrypted, method, removeStoredCard) {
            removeStoredCard = it
            if (it) cardNumber = ""
        }
        MoneyField(amount, { amount = it }, label = "مبلغ انتقال")
        PersianDateField("تاریخ حسابداری انتقال*", date, { date = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth())
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مبلغ ثبت قبلی: ${formatMoney(original.amount)}", fontWeight = FontWeight.Bold)
                Text("مبلغ نسخهٔ جدید: ${amountLong?.let(::formatMoney) ?: "—"}")
                Text("ثبت قبلی خنثی و نسخهٔ جدید با منشأ FIFO تازه ثبت می‌شود.")
            }
        }
        CorrectionReasonField(reason) { reason = it }
        PrimaryButton(
            "ثبت اصلاح انتقال",
            enabled = !closed && fromAccount != null && toEmployee != null && amountLong != null && amountLong in 1..available &&
                fromEmployeeId != toEmployeeId && date.year == original.financialYear &&
                date.toEpochDay() <= LocalDate.now().toEpochDay() && cardValid && movementTimeValid &&
                reason.trim().length >= 3,
        ) {
            viewModel.correctTransfer(
                CorrectTransferCommand(
                    originalTransferId = original.id,
                    dateEpochDay = date.toEpochDay(),
                    fromAccountId = fromAccountId,
                    toAccountId = original.toAccountId.takeIf { toEmployeeId == original.toEmployeeId && method == original.method },
                    toEmployeeId = toEmployeeId,
                    amount = amountLong!!,
                    method = method,
                    note = note,
                    cardNumber = cardUpdate(method, original.method, cardNumber, removeStoredCard),
                    movementDateEpochDay = movementDate?.toEpochDay(),
                    movementTimeMinutes = MovementTimeInput.parse(movementTime),
                    correctionReason = reason,
                ),
                onSaved,
            )
        }
    }
}

@Composable
fun CorrectSettlementScreen(
    viewModel: AppViewModel,
    paymentId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val detail by produceState<SettlementDetailData?>(null, paymentId) { value = viewModel.settlementDetail(paymentId) }
    val employees by viewModel.employees.collectAsState()
    val years by viewModel.years.collectAsState()
    val custodyRows by viewModel.custodyAccounts.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val data = detail ?: return CorrectionLoading()
    val original = data.payment
    var type by remember(original.id) { mutableStateOf(original.type) }
    var fromEmployeeId by remember(original.id) { mutableLongStateOf(original.fromEmployeeId) }
    val fromAccounts by remember(fromEmployeeId, employees) {
        employees.firstOrNull { it.id == fromEmployeeId }?.let { viewModel.accounts(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var fromAccountId by remember(original.id) { mutableLongStateOf(original.fromAccountId) }
    var toEmployeeId by remember(original.id) { mutableLongStateOf(original.toEmployeeId) }
    val effectiveToEmployeeId = if (type == SettlementType.SELF_OFFSET) fromEmployeeId else toEmployeeId
    val toAccounts by remember(effectiveToEmployeeId, employees) {
        employees.firstOrNull { it.id == effectiveToEmployeeId }?.let { viewModel.accounts(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var destinationAccountId by remember(original.id) { mutableStateOf(original.destinationAccountId) }
    var method by remember(original.id) { mutableStateOf(original.method.takeIf { it != MoneyMethod.POS } ?: MoneyMethod.CASH) }
    var amount by remember(original.id) { mutableStateOf(original.amount.toString()) }
    var date by remember(original.id) { mutableStateOf(PersianDate.fromEpochDay(original.dateEpochDay)) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var cardNumber by remember(original.id) { mutableStateOf("") }
    var removeStoredCard by remember(original.id) { mutableStateOf(false) }
    var movementDate by remember(original.id) { mutableStateOf(original.movementDateEpochDay?.let(PersianDate::fromEpochDay)) }
    var movementTime by remember(original.id) { mutableStateOf(original.movementTimeMinutes.toMovementInput()) }
    var reason by remember(original.id) { mutableStateOf("") }

    val fromEmployee = employees.firstOrNull { it.id == fromEmployeeId }
    val toEmployee = employees.firstOrNull { it.id == effectiveToEmployeeId }
    val fromAccount = fromAccounts.firstOrNull { it.id == fromAccountId }
    val compatibleToAccounts = toAccounts.filter { it.matches(method) }
    val destinationAccount = compatibleToAccounts.firstOrNull { it.id == destinationAccountId }
    val visibleFromAccounts = fromAccounts.filter { account ->
        custodyRows.any { it.accountId == account.id && it.amount > 0 } || account.id == original.fromAccountId
    }
    val available = (custodyRows.firstOrNull { it.accountId == fromAccountId }?.amount ?: 0L) +
        if (fromAccountId == original.fromAccountId) original.amount else 0L
    val amountLong = amount.toLongOrNull()
    fun restoredClaim(employeeId: Long): Long = Math.addExact(
        balances.firstOrNull { it.employeeId == employeeId }?.claim ?: 0L,
        if (employeeId == original.toEmployeeId) original.amount else 0L,
    )
    fun restoredCustody(employeeId: Long): Long = Math.addExact(
        balances.firstOrNull { it.employeeId == employeeId }?.custody ?: 0L,
        if (employeeId == original.fromEmployeeId) original.amount else 0L,
    )
    val settlementLimit = if (type == SettlementType.SELF_OFFSET) {
        minOf(restoredClaim(fromEmployeeId), restoredCustody(fromEmployeeId))
    } else {
        val payerDebt = Math.subtractExact(restoredCustody(fromEmployeeId), restoredClaim(fromEmployeeId)).coerceAtLeast(0L)
        val receiverCredit = Math.subtractExact(restoredClaim(toEmployeeId), restoredCustody(toEmployeeId)).coerceAtLeast(0L)
        minOf(payerDebt, receiverCredit)
    }
    val maximumAmount = minOf(available, settlementLimit)
    val closed = years.firstOrNull { it.year == original.financialYear }?.status == FinancialYearStatus.CLOSED
    val cardValid = cardNumber.isBlank() || IranianCardValidator.isValid(cardNumber)
    val movementTimeValid = MovementTimeInput.isValidOrBlank(movementTime)

    FormScaffold("اصلاح تسویه", onBack) {
        CorrectionNotice(closed)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(type == SettlementType.PAYMENT, { type = SettlementType.PAYMENT }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("پرداخت واقعی") }
            SegmentedButton(type == SettlementType.SELF_OFFSET, {
                type = SettlementType.SELF_OFFSET
                destinationAccountId = fromAccountId
            }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("برداشت سهم خود") }
        }
        ChoiceField("پرداخت‌کننده*", employees, fromEmployee, { it.name }, {
            fromEmployeeId = it.id
            fromAccountId = 0
            if (toEmployeeId == it.id) toEmployeeId = 0
            if (type == SettlementType.SELF_OFFSET) destinationAccountId = null
        })
        ChoiceField("از محل نگهداری پول مشترک*", visibleFromAccounts, fromAccount, { account ->
            val value = (custodyRows.firstOrNull { it.accountId == account.id }?.amount ?: 0L) + if (account.id == original.fromAccountId) original.amount else 0L
            "${account.title} — ${formatMoney(value)}"
        }, {
            fromAccountId = it.id
            if (type == SettlementType.SELF_OFFSET) destinationAccountId = it.id
        })
        if (type == SettlementType.PAYMENT) {
            ChoiceField("دریافت‌کننده سهم*", employees.filter { it.id != fromEmployeeId }, toEmployee, { it.name }, {
                toEmployeeId = it.id
                destinationAccountId = null
            })
            ChoiceField("حساب مقصد — اختیاری", compatibleToAccounts, destinationAccount, { it.title }, { destinationAccountId = it.id })
            if (destinationAccountId != null) TextButton({ destinationAccountId = null }) { Text("حذف حساب مقصد") }
            MovementDetailsFields(
                method,
                {
                    method = it
                    if (destinationAccountId != null && toAccounts.none { account -> account.id == destinationAccountId && account.matches(it) }) {
                        destinationAccountId = null
                    }
                },
                cardNumber,
                { cardNumber = it; if (it.isNotBlank()) removeStoredCard = false },
                movementDate,
                { movementDate = it },
                movementTime,
                { movementTime = it },
            )
            StoredCardChoice(original.method, original.cardLast4, original.cardNumberEncrypted, method, removeStoredCard) {
                removeStoredCard = it
                if (it) cardNumber = ""
            }
        } else {
            Text("سهم ${fromEmployee?.name ?: "همان شخص"} از پول مشترک نزد خودش برداشته می‌شود.")
        }
        MoneyField(amount, { amount = it })
        PersianDateField("تاریخ حسابداری تسویه*", date, { date = it })
        OutlinedTextField(note, { note = it }, label = { Text("توضیح — اختیاری") }, modifier = Modifier.fillMaxWidth())
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مبلغ ثبت قبلی: ${formatMoney(original.amount)}", fontWeight = FontWeight.Bold)
                Text("سقف معتبر پس از خنثی‌سازی: ${formatMoney(maximumAmount)}")
                Text("ثبت قبلی در سابقه می‌ماند و فقط نسخهٔ جدید در محاسبات جاری اثر دارد.")
            }
        }
        CorrectionReasonField(reason) { reason = it }
        PrimaryButton(
            "ثبت اصلاح تسویه",
            enabled = !closed && fromAccount != null && toEmployee != null && amountLong != null &&
                amountLong in 1..maximumAmount &&
                (type == SettlementType.SELF_OFFSET || fromEmployeeId != toEmployeeId) &&
                date.year == original.financialYear && date.toEpochDay() <= LocalDate.now().toEpochDay() &&
                cardValid && movementTimeValid && reason.trim().length >= 3,
        ) {
            val selfOffset = type == SettlementType.SELF_OFFSET
            viewModel.correctSettlement(
                CorrectSettlementCommand(
                    originalPaymentId = original.id,
                    dateEpochDay = date.toEpochDay(),
                    fromEmployeeId = fromEmployeeId,
                    fromAccountId = fromAccountId,
                    toEmployeeId = if (selfOffset) fromEmployeeId else toEmployeeId,
                    destinationAccountId = if (selfOffset) fromAccountId else destinationAccountId,
                    amount = amountLong!!,
                    method = if (selfOffset) MoneyMethod.CASH else method,
                    type = type,
                    note = note,
                    cardNumber = if (selfOffset) "" else cardUpdate(method, original.method, cardNumber, removeStoredCard),
                    movementDateEpochDay = if (selfOffset) null else movementDate?.toEpochDay(),
                    movementTimeMinutes = if (selfOffset) null else MovementTimeInput.parse(movementTime),
                    correctionReason = reason,
                ),
                onSaved,
            )
        }
    }
}

@Composable
private fun CorrectionNotice(closed: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (closed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            if (closed) {
                "این تراکنش در سال بسته ثبت شده و قابل اصلاح نیست."
            } else {
                "ثبت اصلی در سابقه می‌ماند؛ یک اصلاح جدا ثبت می‌شود و فقط نسخه جدید در محاسبات جاری اثر دارد."
            },
            Modifier.padding(14.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CorrectionReasonField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text("دلیل اصلاح*") },
        supportingText = { Text("این توضیح در سابقه حسابرسی باقی می‌ماند.") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        isError = value.isNotBlank() && value.trim().length < 3,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun StoredCardChoice(
    previousMethod: MoneyMethod,
    previousLast4: String,
    previousEncrypted: String,
    method: MoneyMethod,
    remove: Boolean,
    onRemoveChange: (Boolean) -> Unit,
) {
    if (method != MoneyMethod.CARD || previousMethod != MoneyMethod.CARD || (previousLast4.isBlank() && previousEncrypted.isBlank())) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(remove, onRemoveChange)
        Column {
            Text("حذف شماره کارت قبلی")
            Text(
                if (remove) "شماره قبلی از نسخه اصلاحی حذف می‌شود." else "اگر شماره جدید وارد نکنید، شماره قبلی حفظ می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorrectionLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

private fun cardUpdate(
    method: MoneyMethod,
    previousMethod: MoneyMethod,
    entered: String,
    removeStoredCard: Boolean,
): String? = when {
    method != MoneyMethod.CARD -> ""
    removeStoredCard -> ""
    entered.isNotBlank() -> entered
    previousMethod == MoneyMethod.CARD -> null
    else -> ""
}

private fun Int?.toMovementInput(): String = this?.let {
    "%02d%02d".format(Locale.US, it / 60, it % 60)
}.orEmpty()

private fun MoneyAccountEntity.matches(method: MoneyMethod): Boolean = when (method) {
    MoneyMethod.CASH -> type == AccountType.CASH
    MoneyMethod.CARD -> type == AccountType.CARD
    MoneyMethod.POS -> type == AccountType.POS
}
