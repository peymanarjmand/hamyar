package com.hesabyar.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hesabyar.garage.data.local.IncomeWithReceiverRow
import com.hesabyar.garage.domain.FinancialYearStatus
import com.hesabyar.garage.domain.PersonBalance
import com.hesabyar.garage.domain.SettlementType
import com.hesabyar.garage.ui.components.*
import com.hesabyar.garage.ui.theme.Amber
import com.hesabyar.garage.ui.theme.DeepTeal
import com.hesabyar.garage.ui.theme.Teal
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.formatMoney
import com.hesabyar.garage.util.toPersianDigits

internal enum class DashboardItemSection(val keyPrefix: String) {
    BALANCE("balance"),
    INCOME("income"),
}

internal fun dashboardItemKey(section: DashboardItemSection, rawId: Long): String = "${section.keyPrefix}:$rawId"

@Composable
fun DashboardScreen(viewModel: AppViewModel, nav: NavController, scrollToTopRequest: Long = 0L) {
    val settings by viewModel.settings.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val years by viewModel.years.collectAsState()
    val yearEntity by viewModel.selectedYearEntity.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val remainder by viewModel.remainder.collectAsState()
    val activeCount by viewModel.activeEmployeeCount.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val custody by viewModel.custodyAccounts.collectAsState()
    val transferNeeded = suggestions.filter { it.type == SettlementType.PAYMENT }.sumOf { it.amount }
    val listState = rememberLazyListState()
    DashboardScrollToTopEffect(listState, scrollToTopRequest)

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.background(DeepTeal).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سلام، خوش آمدید", color = Color.White.copy(alpha = .75f))
                        Text(settings.shopName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    YearSelector(year, years, viewModel::selectYear)
                }
                Spacer(Modifier.height(18.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .11f)), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Amber)
                            Spacer(Modifier.width(8.dp))
                            Text("وضعیت تسویه تا این لحظه", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(if (yearEntity?.status == FinancialYearStatus.CLOSED) "بسته‌شده" else "سال باز", color = Color.White.copy(alpha = .75f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(formatMoney(transferNeeded), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                suggestions.isEmpty() -> "حساب همه تسویه است"
                                transferNeeded > 0 -> "مبلغ پرداخت بین افراد • ${suggestions.size.toString().toPersianDigits()} اقدام باقی مانده"
                                else -> "${suggestions.size.toString().toPersianDigits()} برداشت سهم از پول نزد خود فرد باقی مانده"
                            },
                            color = Color.White.copy(alpha = .75f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { nav.navigate("settlement") }, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = DeepTeal)) {
                            Text("مشاهده و تسویه", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ArrowBack, null)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("اقدام سریع", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Rounded.AddCard, "ثبت درآمد", Amber, Modifier.weight(1f)) { nav.navigate("income") }
                    QuickAction(Icons.Rounded.SwapHoriz, "انتقال پول مشترک", Teal, Modifier.weight(1f)) { nav.navigate("transfer") }
                    QuickAction(Icons.Rounded.PersonAdd, "پرسنل جدید", DeepTeal, Modifier.weight(1f)) { nav.navigate("employee/add") }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("نمای کلی سال ${year.toString().toPersianDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("پرسنل فعال", activeCount.toString().toPersianDigits(), Icons.Rounded.Groups, Modifier.weight(1f))
                    MetricCard("درآمد مشترک", formatMoney(totalIncome), Icons.Rounded.Payments, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("پول مشترک نزد افراد", formatMoney(custody.sumOf { it.amount }), Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f))
                    MetricCard("باقیمانده تقسیم", formatMoney(remainder), Icons.Rounded.Savings, Modifier.weight(1f))
                }
            }
        }
        if (balances.any { !it.isFullySettled }) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("وضعیت مالی پرسنل", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ nav.navigate("settlement") }) { Text("مشاهده همه") }
                }
            }
            items(
                balances.filter { !it.isFullySettled }.take(3),
                key = { dashboardItemKey(DashboardItemSection.BALANCE, it.employeeId) },
            ) { balance -> BalanceCard(balance, year) { nav.navigate("employee/${balance.employeeId}") } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("آخرین درآمدها", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton({ nav.navigate("history") }) { Text("تاریخچه") }
            }
        }
        if (incomes.isEmpty()) item { EmptyState("هنوز درآمدی ثبت نشده", "اولین انعام مشتری یا فروش ضایعات را ثبت کنید.") }
        else items(incomes.take(4), key = { dashboardItemKey(DashboardItemSection.INCOME, it.id) }) { IncomeRow(it, onClick = { nav.navigate("income/${it.id}") }) }
        item {
            val last = if (settings.lastBackupAt == 0L) "هنوز ساخته نشده" else PersianDate.from(java.time.Instant.ofEpochMilli(settings.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()).format()
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { nav.navigate("more") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("پشتیبان اطلاعات", fontWeight = FontWeight.Bold); Text("آخرین پشتیبان: $last", style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        }
    }
}

@Composable
internal fun DashboardScrollToTopEffect(listState: LazyListState, request: Long) {
    LaunchedEffect(request) {
        if (request > 0L && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(14.dp)) { Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.height(7.dp)); Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(10.dp)); Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BalanceCard(balance: PersonBalance, year: Int, onClick: () -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(balance.employeeName.take(1), Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(balance.employeeName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.ChevronLeft, null)
            }
            PersonFinancialSummary(balance, year, compact = true)
        }
    }
}

@Composable
fun IncomeRow(row: IncomeWithReceiverRow, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val deleted = row.status == "DELETED"
    Card(
        modifier.padding(horizontal = 20.dp).fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (deleted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (row.type == "TIP") Icons.Rounded.VolunteerActivism else Icons.Rounded.Recycling, null, tint = if (deleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (row.type == "TIP") "انعام مشتری" else "فروش ضایعات", fontWeight = FontWeight.Bold)
                if (deleted) Text("حذف‌شده • بدون اثر در حساب", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                val methodLabel = when (row.method) {
                    "CARD" -> "کارت‌به‌کارت"
                    "POS" -> "پوز"
                    else -> "نقدی"
                }
                Text("دریافت‌کننده: ${row.receiverName} • $methodLabel • ${PersianDate.fromEpochDay(row.dateEpochDay).format()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoney(row.amount), fontWeight = FontWeight.ExtraBold, color = if (deleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}
