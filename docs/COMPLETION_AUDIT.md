# ممیزی تحویل محلی نسخه ۱.۰.۰

این گزارش مربوط به APK تحویلی موجود در پوشه محلی `release` است. نسخه‌ای که GitHub Actions با کلید دائمی انتشار امضا می‌کند SHA-256 جدید خود را در فایل `SHA256SUMS.txt` همان GitHub Release خواهد داشت.

- فایل: `release/HesabYar-Garage-v1.0.0.apk`
- SHA-256: `7A89432781485E892EEB6693D776EC4860D40694325C5A5EBA66E0CDE7CA1249`
- شناسه بسته: `com.hesabyar.garage`
- حداقل Android: 8 / API 26
- هدف و Compile SDK: API 36
- معماری‌ها: arm64-v8a، armeabi-v7a، x86 و x86_64
- امضا: APK Signature Scheme v2، یک امضاکننده
- مجوز اینترنت: ندارد

## کنترل‌های انجام‌شده

- `testDebugUnitTest`: ۱۹ آزمون، ۰ شکست
- `connectedDebugAndroidTest` روی Android 15: ۲ آزمون، ۰ شکست
- `lintDebug`: بدون خطا
- `assembleRelease`: موفق
- نصب همان APK تحویلی روی Android 15: موفق
- Cold Start نسخه Release: موفق
- بازبینی تصویری صفحه راه‌اندازی فارسی، RTL و کنتراست نوار وضعیت: موفق
- بازیابی بکاپ رمزدار پس از پاک‌کردن دیتابیس، شامل شماره کامل کارت: موفق
- انتقال جزئی امانت و ثابت‌ماندن مجموع سهم/امانت: موفق
