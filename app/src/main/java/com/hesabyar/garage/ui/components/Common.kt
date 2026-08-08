package com.hesabyar.garage.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesabyar.garage.data.local.FinancialYearEntity
import com.hesabyar.garage.domain.FinancialYearStatus
import com.hesabyar.garage.domain.MoneyMethod
import com.hesabyar.garage.domain.PersonBalance
import com.hesabyar.garage.ui.theme.Danger
import com.hesabyar.garage.ui.theme.Success
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.PersianDateInput
import com.hesabyar.garage.util.IranianCardValidator
import com.hesabyar.garage.util.MovementTimeInput
import com.hesabyar.garage.util.formatMoney
import com.hesabyar.garage.util.toLatinDigits
import com.hesabyar.garage.util.toPersianDigits

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSelector(
    selected: Int,
    years: List<FinancialYearEntity>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = years.firstOrNull { it.year == selected }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        Surface(
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("سال ${selected.toString().toPersianDigits()}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text(if (current?.status == FinancialYearStatus.CLOSED) "بسته" else "باز") },
                )
            }
        }
        ExposedDropdownMenu(expanded, { expanded = false }) {
            years.ifEmpty { listOf(FinancialYearEntity(selected, 0, 0)) }.forEach { year ->
                DropdownMenuItem(
                    text = { Text("سال ${year.year.toString().toPersianDigits()} — ${if (year.status == FinancialYearStatus.CLOSED) "بسته" else "باز"}") },
                    onClick = { expanded = false; onSelected(year.year) },
                )
            }
        }
    }
}

@Composable
fun PersianDateField(label: String, value: PersianDate, onChange: (PersianDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    Box(modifier.clickable { show = true }) {
        OutlinedTextField(
            value = value.format(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Box(Modifier.matchParentSize().clickable { show = true })
    }
    if (show) PersianDatePickerDialog(value, { show = false }, { onChange(it); show = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersianDatePickerDialog(initial: PersianDate, onDismiss: () -> Unit, onConfirm: (PersianDate) -> Unit) {
    var yearText by remember(initial) { mutableStateOf(initial.year.toString()) }
    var monthText by remember(initial) { mutableStateOf(initial.month.toString()) }
    var dayText by remember(initial) { mutableStateOf(initial.day.toString()) }

    val parsed = PersianDateInput.parse(yearText, monthText, dayText)
    val validYear = yearText.toIntOrNull()?.takeIf { it in PersianDateInput.MIN_YEAR..PersianDateInput.MAX_YEAR }
    val validMonth = monthText.toIntOrNull()?.takeIf { it in 1..12 }
    val dayRange = if (validYear != null && validMonth != null) {
        1..PersianDate.monthLength(validYear, validMonth)
    } else {
        1..31
    }

    fun updateCalendarPart(year: Int? = validYear, month: Int? = validMonth) {
        if (year != null) yearText = year.toString()
        if (month != null) monthText = month.toString()
        if (year != null && month != null) {
            val currentDay = dayText.toIntOrNull()
            if (currentDay != null) dayText = PersianDateInput.clampDay(year, month, currentDay).toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "روز و ماه را از فهرست انتخاب کنید یا عدد را مستقیم بنویسید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateNumberField(
                    label = "ماه",
                    value = monthText,
                    onValueChange = { raw ->
                        monthText = PersianDateInput.digits(raw, 2)
                        monthText.toIntOrNull()?.takeIf { it in 1..12 }?.let { month ->
                            if (validYear != null) updateCalendarPart(validYear, month)
                        }
                    },
                    validRange = 1..12,
                    options = 1..12,
                    optionLabel = { month -> "${month.toString().toPersianDigits()} — ${PersianDate.monthNames[month - 1]}" },
                    onOptionSelected = { month -> updateCalendarPart(validYear, month) },
                    suffixText = validMonth?.let { PersianDate.monthNames[it - 1] },
                    testTag = "jalali-date-month",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DateNumberField(
                        label = "روز",
                        value = dayText,
                        onValueChange = { dayText = PersianDateInput.digits(it, 2) },
                        validRange = dayRange,
                        options = dayRange,
                        onOptionSelected = { day -> dayText = day.toString() },
                        testTag = "jalali-date-day",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Done,
                    )
                    DateNumberField(
                        label = "سال",
                        value = yearText,
                        onValueChange = { raw ->
                            yearText = PersianDateInput.digits(raw, 4)
                            val year = yearText.toIntOrNull()?.takeIf { it in PersianDateInput.MIN_YEAR..PersianDateInput.MAX_YEAR }
                            if (year != null && validMonth != null) updateCalendarPart(year, validMonth)
                        },
                        validRange = PersianDateInput.MIN_YEAR..PersianDateInput.MAX_YEAR,
                        testTag = "jalali-date-year",
                        modifier = Modifier.weight(1.25f),
                    )
                }
                Text(
                    parsed?.let { "${it.day.toString().toPersianDigits()} ${PersianDate.monthNames[it.month - 1]} ${it.year.toString().toPersianDigits()}" }
                        ?: "تاریخ واردشده معتبر نیست",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (parsed != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("تأیید تاریخ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    validRange: IntRange,
    modifier: Modifier = Modifier,
    options: IntRange? = null,
    optionLabel: (Int) -> String = { it.toString().toPersianDigits() },
    onOptionSelected: (Int) -> Unit = {},
    suffixText: String? = null,
    testTag: String,
    imeAction: ImeAction = ImeAction.Next,
) {
    var expanded by remember { mutableStateOf(false) }
    val isValid = value.toIntOrNull() in validRange

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options != null) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value.toPersianDigits(),
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = value.isNotEmpty() && !isValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            suffix = suffixText?.let { text -> { Text(text, style = MaterialTheme.typography.bodySmall) } },
            trailingIcon = if (options != null) {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .then(if (options != null) Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable) else Modifier),
            shape = RoundedCornerShape(14.dp),
        )
        if (options != null) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        modifier = Modifier.testTag("$testTag-option-$option"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceField(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.let(itemLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(14.dp),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(itemLabel(item)) }, onClick = { onSelected(item); expanded = false })
            }
        }
    }
}

@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "مبلغ (تومان)") {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.toLatinDigits().filter(Char::isDigit).take(15)) },
        label = { Text(label) },
        suffix = { Text("تومان") },
        supportingText = {
            val amount = value.toLongOrNull()
            if (amount != null && amount > 0) Text(formatMoney(amount))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
fun MovementDetailsFields(
    method: MoneyMethod,
    onMethodChange: (MoneyMethod) -> Unit,
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    movementDate: PersianDate?,
    onMovementDateChange: (PersianDate?) -> Unit,
    movementTime: String,
    onMovementTimeChange: (String) -> Unit,
    allowedMethods: Set<MoneyMethod> = setOf(MoneyMethod.CASH, MoneyMethod.CARD),
) {
    Text("روش جابه‌جایی پول*", fontWeight = FontWeight.Bold)
    val methods = listOf(MoneyMethod.CASH, MoneyMethod.CARD, MoneyMethod.POS).filter { it in allowedMethods }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        methods.forEachIndexed { index, item ->
            SegmentedButton(
                selected = method == item,
                onClick = {
                    onMethodChange(item)
                    if (item != MoneyMethod.CARD) onCardNumberChange("")
                },
                shape = SegmentedButtonDefaults.itemShape(index, methods.size),
            ) {
                Text(
                    when (item) {
                        MoneyMethod.CASH -> "نقدی"
                        MoneyMethod.CARD -> "کارت‌به‌کارت"
                        MoneyMethod.POS -> "پوز"
                    },
                )
            }
        }
    }
    if (method == MoneyMethod.CARD) {
        val normalized = IranianCardValidator.normalize(cardNumber)
        OutlinedTextField(
            value = IranianCardValidator.formatFull(cardNumber),
            onValueChange = { onCardNumberChange(IranianCardValidator.normalize(it).take(16)) },
            label = { Text("شماره کارت — اختیاری") },
            isError = normalized.isNotBlank() && normalized.length == 16 && !IranianCardValidator.isValid(normalized),
            supportingText = {
                Text(
                    when {
                        normalized.isBlank() -> "می‌توانید کارت‌به‌کارت را بدون شماره کارت ثبت کنید."
                        normalized.length < 16 -> "شماره را کامل کنید یا برای ثبت اختیاری، فیلد را خالی بگذارید."
                        IranianCardValidator.isValid(normalized) -> "شماره در این تراکنش به‌صورت رمزنگاری‌شده ذخیره می‌شود."
                        else -> "شماره کارت معتبر نیست."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
    }
    if (method == MoneyMethod.POS) {
        Text(
            "شماره کارت مشتری ذخیره نمی‌شود؛ شماره پیگیری رسید را در صورت نیاز در توضیحات بنویسید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = movementDate != null,
            onCheckedChange = { enabled ->
                onMovementDateChange(if (enabled) PersianDate.today() else null)
                if (!enabled) onMovementTimeChange("")
            },
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text("ثبت تاریخ و زمان دقیق — اختیاری")
            Text("برای رسید یا پیگیری بانکی؛ مستقل از تاریخ حسابداری", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    movementDate?.let { date ->
        PersianDateField("تاریخ دقیق تراکنش — اختیاری", date, { onMovementDateChange(it) })
        OutlinedTextField(
            value = MovementTimeInput.displayInput(movementTime),
            onValueChange = { onMovementTimeChange(MovementTimeInput.digits(it)) },
            label = { Text("ساعت — اختیاری (مثلاً ۱۴:۳۰)") },
            isError = !MovementTimeInput.isValidOrBlank(movementTime),
            supportingText = {
                if (!MovementTimeInput.isValidOrBlank(movementTime)) Text("ساعت باید بین ۰۰:۰۰ تا ۲۳:۵۹ باشد.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(description, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BalanceLabel(balance: PersonBalance, compact: Boolean = false) {
    val color: Color
    val text: String
    when {
        balance.isFullySettled -> { color = MaterialTheme.colorScheme.secondary; text = "حساب این سال تسویه است" }
        balance.claim == balance.custody && balance.claim > 0 -> {
            color = MaterialTheme.colorScheme.primary
            text = "باید ${formatMoney(balance.claim)} از پول نزد خودش به‌عنوان سهم ثبت شود"
        }
        balance.net > 0 -> { color = Success; text = "باید ${formatMoney(balance.net)} دریافت کند" }
        balance.net < 0 -> { color = Danger; text = "باید ${formatMoney(-balance.net)} به دیگران پرداخت کند" }
        else -> { color = MaterialTheme.colorScheme.primary; text = "برداشت سهم از پول نزد خودش باید ثبت شود" }
    }
    Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp)
}

@Composable
fun PersonFinancialSummary(balance: PersonBalance, year: Int, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
        Text(
            "وضعیت سال ${year.toString().toPersianDigits()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 12.dp)) {
                BalanceLabel(balance, compact)
                if (balance.selfOffsetNeeded > 0 && balance.claim != balance.custody) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "برداشت سهم از پول نزد خودش: ${formatMoney(balance.selfOffsetNeeded)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FinancialSummaryValue("کل سهم تا امروز", balance.totalEntitlement, Modifier.weight(1f), compact)
            FinancialSummaryValue("از سهمش تسویه شده", balance.settledEntitlement, Modifier.weight(1f), compact)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("پول مشترک فعلاً نزد او", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(formatMoney(balance.custody), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FinancialSummaryValue(label: String, amount: Long, modifier: Modifier, compact: Boolean) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoney(amount), fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp)
    }
}
