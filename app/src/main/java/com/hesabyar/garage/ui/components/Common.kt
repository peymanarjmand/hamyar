package com.hesabyar.garage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesabyar.garage.data.local.FinancialYearEntity
import com.hesabyar.garage.domain.FinancialYearStatus
import com.hesabyar.garage.domain.PersonBalance
import com.hesabyar.garage.ui.theme.Danger
import com.hesabyar.garage.ui.theme.Success
import com.hesabyar.garage.util.PersianDate
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).clickable { expanded = true },
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

@Composable
private fun PersianDatePickerDialog(initial: PersianDate, onDismiss: () -> Unit, onConfirm: (PersianDate) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }
    fun clampDay() { day = day.coerceAtMost(PersianDate.monthLength(year, month)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateStepper("سال", year, { year = (year - 1).coerceAtLeast(1300); clampDay() }, { year = (year + 1).coerceAtMost(1600); clampDay() })
                DateStepper("ماه — ${PersianDate.monthNames[month - 1]}", month, { month = if (month == 1) 12 else month - 1; clampDay() }, { month = if (month == 12) 1 else month + 1; clampDay() })
                DateStepper("روز", day, { day = (day - 1).coerceAtLeast(1) }, { day = (day + 1).coerceAtMost(PersianDate.monthLength(year, month)) })
                Text("${day.toString().toPersianDigits()} ${PersianDate.monthNames[month - 1]} ${year.toString().toPersianDigits()}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(PersianDate(year, month, day)) }) { Text("تأیید تاریخ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DateStepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "افزایش") }
        Text("${label}: ${value.toString().toPersianDigits()}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "کاهش") }
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
            modifier = modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
        balance.net > 0 -> { color = Success; text = "باید ${formatMoney(balance.net)} دریافت کند" }
        balance.net < 0 -> { color = Danger; text = "باید ${formatMoney(-balance.net)} بپردازد" }
        else -> { color = MaterialTheme.colorScheme.secondary; text = "حساب تسویه است" }
    }
    Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp)
}
