package com.hesabyar.garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hesabyar.garage.ui.components.PersianDateField
import com.hesabyar.garage.ui.components.PrimaryButton
import com.hesabyar.garage.ui.theme.DeepTeal
import com.hesabyar.garage.util.PersianDate

@Composable
fun OnboardingScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var shop by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(PersianDate.today()) }
    var showPrivacy by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(DeepTeal).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(56.dp))
        Text("حساب‌یار تعمیرگاه", color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("حساب انعام‌ها، دقیق و همیشه همراه شما", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("راه‌اندازی اولیه", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("اطلاعات فقط روی همین گوشی می‌ماند و بدون اینترنت کار می‌کند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(shop, { shop = it }, label = { Text("نام تعمیرگاه") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(manager, { manager = it }, label = { Text("نام مدیر و پرسنل اول") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("شماره تماس — اختیاری") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                PersianDateField("تاریخ شروع همکاری مدیر", start, { start = it })
                TextButton(onClick = { showPrivacy = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PrivacyTip, null)
                    Spacer(Modifier.width(6.dp))
                    Text("حریم خصوصی و شیوه نگهداری اطلاعات")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = privacyAccepted, onCheckedChange = { privacyAccepted = it })
                    Text("با ذخیره محلی اطلاعات طبق متن حریم خصوصی موافقم.", style = MaterialTheme.typography.bodySmall)
                }
                PrimaryButton("شروع مدیریت تعمیرگاه", enabled = shop.isNotBlank() && manager.length >= 2 && privacyAccepted) {
                    viewModel.finishOnboarding(shop, manager, phone, start) {}
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TrustItem(Icons.Rounded.WifiOff, "کاملاً آفلاین")
            TrustItem(Icons.Rounded.Security, "پشتیبان رمزدار")
        }
        Spacer(Modifier.height(30.dp))
    }
    if (showPrivacy) PrivacyPolicyDialog(
        onDismiss = { showPrivacy = false },
        onAccept = {
            privacyAccepted = true
            showPrivacy = false
        },
    )
}

@Composable
private fun TrustItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
    }
}
