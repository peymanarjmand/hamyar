package com.hesabyar.garage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val POLICY_UPDATED_AT = "۲۷ تیر ۱۴۰۵"

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    FormScaffold("حریم خصوصی", onBack) {
        PrivacyPolicyContent()
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حریم خصوصی", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PrivacyPolicyContent()
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("می‌پذیرم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun ColumnScope.PrivacyPolicyContent() {
    Text(
        "آخرین به‌روزرسانی: $POLICY_UPDATED_AT",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PrivacySection(
        "خلاصه",
        "حساب‌یار یک برنامه کاملاً آفلاین است. برنامه مجوز اینترنت ندارد و از حساب کاربری، تبلیغات، ابزار تحلیل رفتار یا سرویس ابری استفاده نمی‌کند. اطلاعات به‌صورت خودکار برای ناشر یا شخص دیگری ارسال نمی‌شود.",
    )
    PrivacySection(
        "اطلاعاتی که ثبت می‌کنید",
        "نام تعمیرگاه؛ نام، نقش، شماره تماس اختیاری، وضعیت سهم‌گیری، دوره همکاری و غیبت پرسنل؛ درآمدها، سهم‌ها، پول‌های مشترک نزد افراد، انتقال‌ها، تسویه‌ها، روش دریافت از جمله دستگاه پوز و دلیل اصلاح یا حذف حسابداری‌شده تراکنش؛ و در صورت انتخاب شما شماره کارت و زمان دقیق جابه‌جایی پول در دستگاه ذخیره می‌شود. این اطلاعات فقط برای محاسبات، گزارش‌ها، تاریخچه مالی و پشتیبان‌گیری استفاده می‌شوند.",
    )
    PrivacySection(
        "نگهداری و امنیت",
        "داده‌ها در فضای خصوصی برنامه روی دستگاه نگهداری می‌شوند و پشتیبان‌گیری خودکار اندروید برای برنامه غیرفعال است. شماره کامل کارت با Android Keystore و AES-GCM رمزگذاری و به‌طور پیش‌فرض ماسک می‌شود. فایل پشتیبان قابل‌حمل نیز با رمز انتخابی شما و رمزنگاری احرازشده محافظت می‌شود.",
    )
    PrivacySection(
        "قفل اختیاری برنامه",
        "قفل برنامه به‌طور پیش‌فرض خاموش است. در صورت فعال‌سازی، متن خام رمز و پاسخ بازیابی ذخیره نمی‌شود و فقط مشتق‌های نمک‌دار آن‌ها، متن پرسش، تنظیمات قفل و مشتق کد بازیابی در فایل تنظیمات امنیتی جداگانه روی همین گوشی می‌ماند. همین رمز برای تأیید جداگانه هر حذف تراکنش استفاده می‌شود؛ بازبودن برنامه یا تأیید قفل گوشی جای رمز حذف را نمی‌گیرد. این اطلاعات داخل پشتیبان مالی قرار نمی‌گیرند. پیش‌نمایش برنامه در صفحه برنامه‌های اخیر نیز محافظت می‌شود و در Androidهای قدیمی‌تر ممکن است اسکرین‌شات محدود شود. قفل برنامه جایگزین قفل امن گوشی یا رمز فایل پشتیبان نیست؛ تلاش‌های ناموفق فقط با تأخیر محدود می‌شوند و باعث حذف داده نمی‌شوند.",
    )
    PrivacySection(
        "اثر انگشت و قفل گوشی",
        "اگر خودتان «باز کردن با قفل گوشی» را فعال کنید، پنجره استاندارد Android از اثر انگشت، چهره یا رمز صفحه استفاده می‌کند. حساب‌یار به الگوی زیستی یا رمز گوشی دسترسی ندارد و فقط نتیجه موفق یا ناموفق را دریافت می‌کند. هر فردی که بتواند قفل گوشی را باز کند، در این حالت می‌تواند برنامه را نیز باز کند.",
    )
    PrivacySection(
        "انتقال و اشتراک‌گذاری",
        "برنامه هیچ داده‌ای را خودکار بارگذاری یا همگام‌سازی نمی‌کند. هنگام ساخت یا بازیابی پشتیبان، محل فایل را خودتان از طریق انتخاب‌گر استاندارد اندروید تعیین می‌کنید؛ امنیت مقصد انتخابی و هرگونه ارسال بعدی فایل بر عهده شماست. دکمه‌های بله و تلگرام فقط با اقدام شما برنامه بیرونی یا صفحه عمومی توسعه‌دهنده را باز می‌کنند و هیچ اطلاعات تعمیرگاه را همراه لینک نمی‌فرستند؛ ادامه ارتباط تابع سیاست همان سرویس بیرونی است.",
    )
    PrivacySection(
        "نگهداری و حذف",
        "اطلاعات تا زمانی که داده‌های برنامه را از تنظیمات اندروید پاک کنید یا برنامه را حذف کنید، روی دستگاه می‌مانند. فایل‌های پشتیبان باید جداگانه از محل انتخابی شما حذف شوند. گزینه حذف تراکنش، ردیف تاریخی را پاک نمی‌کند؛ فقط اثر آن را با ثبت‌های معکوس از محاسبات خارج می‌کند و دلیل حذف را برای حسابرسی نگه می‌دارد.",
    )
    PrivacySection(
        "اصلاح، حذف تراکنش و دستگاه پوز",
        "اصلاح تراکنش، ثبت اصلی را پاک یا بازنویسی نمی‌کند؛ نسخه قبلی خنثی و یک نسخه جایگزین با دلیل اصلاح ثبت می‌شود. حذف حسابداری‌شده نیز فقط در سال باز و پس از تأیید رمز برنامه انجام می‌شود؛ تراکنش حذف‌شده، دلیل حذف و ثبت‌های معکوس در تاریخچه و پشتیبان می‌مانند، اما مبلغ آن دیگر در حساب‌ها اثر ندارد. روش پوز فقط نشان می‌دهد درآمد در دنیای واقعی از کارت‌خوان دریافت شده است؛ برنامه به دستگاه پوز یا بانک متصل نمی‌شود، پرداختی پردازش نمی‌کند و شماره کارت مشتری را برای پوز ثبت نمی‌کند.",
    )
    PrivacySection(
        "انتخاب و مسئولیت شما",
        "شماره تماس، شماره کارت و زمان دقیق تراکنش اختیاری‌اند. اطلاعات اشخاص دیگر را با اطلاع آن‌ها ثبت کنید، برای پشتیبان رمز قوی بگذارید و از دستگاه و فایل پشتیبان محافظت کنید.",
    )
    PrivacySection(
        "پرسش یا تغییرات",
        "اطلاعات تماس پیمان ارجمند در بخش «توسعه‌دهنده» برنامه در دسترس است. اگر شیوه پردازش داده‌ها در نسخه‌ای تغییر کند، این متن و تاریخ آن نیز پیش از انتشار همان نسخه به‌روزرسانی می‌شود.",
    )
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(body, style = MaterialTheme.typography.bodyMedium)
}
