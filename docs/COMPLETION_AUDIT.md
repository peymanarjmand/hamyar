# ممیزی تحویل محلی نسخه ۱.۰.۱

این گزارش مربوط به APK تحویلی موجود در پوشه محلی `release` است. نسخه‌ای که GitHub Actions با کلید دائمی انتشار امضا می‌کند SHA-256 جدید خود را در فایل `SHA256SUMS.txt` همان GitHub Release خواهد داشت.

- فایل: `release/HesabYar-Garage-v1.0.1.apk`
- SHA-256: `ED68EEBC5B42F047CC2C43EDA1A866DB059C4582EE4E043A9E0F16CFABF82809`
- شناسه بسته: `com.hesabyar.garage`
- نسخه: `1.0.1` / کد نسخه `2`
- حداقل Android: 8 / API 26
- هدف و Compile SDK: API 36
- معماری‌ها: arm64-v8a، armeabi-v7a، x86 و x86_64
- امضا: APK Signature Scheme v2، یک امضاکننده؛ SHA-256 گواهی `1142a44b64085eab39420823a83618e9daf6b0082a6b260390eed09c0ec7c559`
- مجوز اینترنت: ندارد

## کنترل‌های انجام‌شده

- `testDebugUnitTest`: ۲۳ آزمون، ۰ شکست
- `connectedDebugAndroidTest` روی Android 15: ۴ آزمون، ۰ شکست
- `lintDebug`: بدون خطا
- `assembleRelease`: موفق
- نصب درجا روی نسخه ۱.۰.۰ با همان امضا و حفظ داده پرسنل: موفق
- بازکردن جزئیات پرسنلی که قبلاً باعث Crash می‌شد: موفق؛ `AndroidRuntime fatal=none`
- کنترل Dropdown ماه/روز و ورودی عددی تاریخ شمسی روی Android 15: موفق
- بازبینی تصویری صفحه راه‌اندازی فارسی، RTL و کنتراست نوار وضعیت: موفق
- بازیابی بکاپ رمزدار پس از پاک‌کردن دیتابیس، شامل شماره کامل کارت: موفق
- انتقال جزئی امانت و ثابت‌ماندن مجموع سهم/امانت: موفق
