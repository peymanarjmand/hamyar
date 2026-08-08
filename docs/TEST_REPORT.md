# گزارش آزمون حساب‌یار تعمیرگاه

## وضعیت نسخه ۱.۴.۱ — ۲۰۲۶-۰۷-۱۸

verification محلی نامزد دائمی‌امضای ۱.۴.۱ کامل شده است. این شواهد مجوز انتشار عمومی نیستند؛ نامزد workflow محافظت‌شده، VirusTotal، Play Protect روی دستگاه فیزیکی certified و اجازه مالک همچنان fail-closed و pending هستند.

فرمان‌های اجراشده تا این مرحله:

```powershell
.\gradlew.bat compileDebugKotlin compileDebugAndroidTestKotlin --stacktrace
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin --stacktrace
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hesabyar.garage.data.TransactionCorrectionInstrumentedTest,com.hesabyar.garage.data.BackupSchema6InstrumentedTest,com.hesabyar.garage.data.AppSecurityRepositoryInstrumentedTest,com.hesabyar.garage.ui.TransactionDeletionUiInstrumentedTest" --stacktrace
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease assemblePublicRelease --stacktrace
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

- ۴۷ آزمون JVM در ۱۴ suite: بدون failure، error یا skip
- اجرای هدفمند ۱۸ آزمون Android برای repository، ledger، backup، رمز حذف و Compose: بدون failure، error یا skip
- مجموعه کامل ۳۵ آزمون instrumentation روی شبیه‌ساز Android 15 / API 35: بدون failure، error یا skip
- `lintDebug`، ساخت Android-test، R8، `assembleRelease` و `assemblePublicRelease`: موفق
- نامزد محلی با کلید دائمی قبلی، دقیقاً یک امضاکننده RSA-4096 و APK Signature Scheme v2 ساخته شد؛ fingerprint گواهی `f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f` است.
- APK با اندازه ۲٬۲۹۰٬۳۶۳ بایت و SHA-256 برابر `74f54975b8fe902e3c2410244805bb7d17bc9e3c4666c9bd588645f1edde90fb`، package برابر `com.hesabyar.garage`، نسخه `1.4.1`/کد `7`، alignment معتبر 16 KiB و فقط سه مجوز allowlistشده بررسی شد؛ مجوز `INTERNET` وجود ندارد.
- همان فایل دقیق با `adb install -r` روی نصب دائمی‌امضای ۱.۴.۰/کد ۶ نصب شد. `firstInstallTime` ثابت ماند و کارگاه، پرسنل، درآمد ۱۲۰٬۰۰۰ تومانی، custody، باقیمانده و تاریخچه ساختگی بدون حذف یا بازنشانی حفظ شدند.
- QA بصری RTL روی همان نامزد Release شامل پنجره حذف رمزدار، رکورد قرمز «حذف‌شده • بدون اثر در حساب» و نماهای قبل/بعد ارتقای درجا انجام شد.

### پوشش افزوده‌شده در ۱.۴.۱

- اصلاح پشت‌سرهم آخرین، میانی و قدیمی‌ترین درآمد بدون قفل‌شدن همیشگی مسیر و با حفظ یک نسخه فعال در هر lineage
- ثابت‌ماندن eligibility snapshot درآمدهای جدیدتر هنگام بازسازی suffix و محاسبه دوباره سهم عددصحیح/باقیمانده
- حذف درآمد میانی با وضعیت `DELETED`، خنثی‌شدن اثر دفتر، باقی‌ماندن تاریخچه و audit و بازسازی امن درآمدهای جدیدتر
- رد حذف درآمد دارای تخصیص فعال؛ آزادشدن FIFO پس از حذف حسابرسی‌شده تسویه و انتقال وابسته و سپس امکان حذف درآمد
- ممنوعیت حذف در سال بسته و rollback اتمیک در خطا
- round-trip Backup schema 6 برای وضعیت حذف‌شده، پیوندهای اصلاح و audit حذف، همراه با بازیابی schemaهای قدیمی ۴ و ۵
- اثبات اینکه قفل خاموش یا رمز اشتباه هیچ mutation مالی ایجاد نمی‌کند، رمز دقیق فارسی حذف را انجام می‌دهد و آرایه رمز پس از استفاده پاک می‌شود
- اعتبارسنجی Compose برای دلیل حذف، رمز دقیق و مرز زمان retry

## شواهد نهایی نسخه ۱.۴.۰ — ۲۰۲۶-۰۷-۱۷

فرمان‌های نهایی از ریشه مخزن روی Windows اجرا شدند:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease assemblePublicRelease --stacktrace
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

- ۴۶ آزمون JVM در ۱۳ suite: بدون failure، error یا skip
- ۲۶ آزمون instrumentation در ۱۰ کلاس روی شبیه‌ساز Android 15 / API 35: بدون failure، error یا skip
- `lintDebug`: موفق و بدون error
- ساخت APK آزمون Android، R8، `assembleRelease` و `assemblePublicRelease`: موفق؛ نامزد محلی با کلید دائمی ساخته شد
- APK نهایی: یک امضاکننده v2 با همان fingerprint نسخه ۱.۳.۰، alignment سازگار 16 KiB و allowlist دقیق سه مجوز آفلاین؛ بدون `INTERNET`
- ارتقای فایل دقیق دائمی‌امضای ۱.۳.۰ با دادهٔ ساختگی به SHA-256 `f8639b3948330ad6f0cfc5059a866a77c7c640836560eebb1e926c5c0df1efe8`: موفق؛ inode داده، زمان نصب اولیه، پرسنل، درآمد، custody و تاریخچه حفظ شدند

## پوشش جدید ۱.۴.۰

- قفل برنامه به‌صورت پیش‌فرض خاموش و دروازه نمایش محتوا به‌صورت fail-closed
- مشتق‌سازی PBKDF2 رمز، پاسخ فارسی و کد بازیابی؛ مقایسه ثابت‌زمان و محدودسازی پایدار تلاش‌های ناموفق
- مخفی‌ماندن کد یک‌بارمصرف بازیابی پشت صفحه قفل و قفل‌شدن دقیق پس از timeout
- استفاده اختیاری از پنجره استاندارد Android برای اثر انگشت، چهره یا رمز دستگاه
- ثبت درآمد نقدی، کارت‌به‌کارت و POS؛ محدودبودن POS به درآمد و حفظ تراز claim + remainder = custody
- اصلاح atomic درآمد، انتقال و تسویه با rollback کامل خطا، خنثی‌سازی ثبت قبلی، نسخه جایگزین و تخصیص FIFO
- محاسبه خالص snapshot بستن سال پس از اصلاح درآمد/انتقال/تسویه
- migration واقعی Room از `1→4`، `2→4` و `3→4` با حفظ داده مالی، کارت رمزگذاری‌شده، تخصیص منبع و audit
- round-trip Backup schema 5 برای POS و پیوندهای اصلاح، پذیرش schemaهای 1 تا 5 و بازیابی fixture دقیق schema 4 نسخه ۱.۳ که فیلدهای پیوند اصلاح را ندارد
- قرارداد reflection همه جداول پشتیبان و نبود تنظیمات قفل در فایل پشتیبان مالی
- مسیر ناوبری اصلاح تراکنش از تب اصلی و تاریخچه سالانه، بدون بازگشت به editor نسخه خنثی‌شده

## پوشش پایه حفظ‌شده

- مرزهای inclusive دوره همکاری، غیبت باز/بسته، روز بازگشت و سیاست قابل‌تنظیم ۰ تا ۳۶۵ روز
- تقسیم عدد صحیح تومان، انتقال باقیمانده، پیشنهاد تسویه و انتقال جزئی بدون تغییر مجموع پول
- تبدیل و round-trip تاریخ جلالی، اسفند کبیسه و ورودی ارقام فارسی/عربی/لاتین
- اعتبارسنجی و ماسک کارت، رمزگذاری Keystore و پشتیبان AES-GCM با تشخیص رمز اشتباه/دستکاری
- یکتایی کلیدهای فهرست، Home/scroll-to-top، کنترل‌های Compose در RTL و پذیرش صریح حریم خصوصی
- ثابت‌ماندن eligibility snapshot تاریخی و استقلال سهم شخصی از پول مشترک نزد فرد

## QA بصری RTL

روی شبیه‌ساز API 35 و فقط با داده ساختگی، نامزد دائمی‌امضای ۱.۴.۱ بررسی شد. پنجره حذف دلیل و رمز را بدون نمایش رمز درخواست کرد؛ پس از حذف، مبلغ از محاسبات خارج شد و رکورد با وضعیت قرمز «حذف‌شده • بدون اثر در حساب» در تاریخچه باقی ماند. تصاویر قبل و بعد از ارتقای دقیق ۱.۴.۰ به ۱.۴.۱ نیز حفظ داده و چیدمان RTL را نشان می‌دهند. شواهد در `release/v1.4.1/screenshots/` ثبت شده‌اند. این بررسی شبیه‌ساز جای نصب Play Protect روی دستگاه فیزیکی certified را نمی‌گیرد.

## شواهد تاریخی نسخه ۱.۳.۰

در ۲۰۲۶-۰۷-۱۶، ۳۳ آزمون JVM در ۱۱ suite و ۱۶ آزمون دستگاهی نسخه ۱.۳.۰ بدون شکست اجرا شدند. فایل دائمی‌امضای همان نسخه روی نسخه عمومی ۱.۲.۰ با `adb install -r` و migration غیرتخریبی Room `2→3` نصب درجا شد. این شاهد صرفاً تاریخی است و هیچ APK، hash، امضا، Play Protect یا مجوز انتشار برای ۱.۴.۱ ایجاد نمی‌کند.

## موارد انتشار که هنوز pending هستند

- ساخت نامزد خصوصی ۱.۴.۱ از `main` در workflow محافظت‌شده و ثبت run ID/artifact provenance
- VirusTotal، رکورد واقعی Play Protect و نصب بدون هشدار روی دستگاه فیزیکی certified برای همان هش دقیق workflow
- مجوز مالک برای push، tag، GitHub Release، کافه‌بازار یا هر انتشار عمومی
