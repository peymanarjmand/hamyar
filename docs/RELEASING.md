# Release guide

This project uses a two-stage release pipeline: a manually triggered workflow creates a private signed candidate, and the tag workflow publishes that exact Play Protect-approved artifact without rebuilding it. The published Release contains both a versioned asset and the stable `HesabYar-Garage-latest.apk` asset used by the README download button.

For Cafe Bazaar account setup, store metadata, first publication, and the repeatable update playbook, use [the dedicated Persian Cafe Bazaar guide](CAFE_BAZAAR_RELEASE_FA.md).

## رفع خطای فعلی ۴۰۴ دانلود

در ممیزی read-only مورخ ۲۰۲۶-۰۷-۱۶، لینک داخل README به نشانی درست زیر resolve می‌شد:

```text
https://github.com/peymanarjmand/hamyar/releases/latest/download/HesabYar-Garage-latest.apk
```

خود لینک اشتباه نیست. خطای ۴۰۴ به این دلیل رخ می‌دهد که در remote هیچ tag منتشرشده، هیچ GitHub Release و هیچ asset با نام `HesabYar-Garage-latest.apk` وجود نداشت. tagهای annotated نسخه‌های `v1.0.0` و `v1.0.1` فقط در clone محلی بودند. صفحه workflow با نام `Publish signed APK` نیز صفر اجرا داشت، چون این workflow فقط با push شدن tag مطابق `v*.*.*` شروع می‌شود. موفق‌شدن workflow عادی `Android CI` کافی نیست؛ آن workflow APK عمومی منتشر نمی‌کند و فقط گزارش‌های verification را به‌عنوان artifact نگه می‌دارد.

لینک `/releases/latest/download/...` فقط زمانی پاسخ می‌دهد که هر دو شرط برقرار باشد:

1. یک Release واقعی و منتشرشده در GitHub وجود داشته و به‌عنوان latest انتخاب شده باشد؛ tag محلی، tag بدون Release، draft و اجرای CI عادی کافی نیستند.
2. همان Release دارای asset با نام **دقیق** `HesabYar-Garage-latest.apk` باشد؛ نام نسخه‌دار به‌تنهایی این URL را فعال نمی‌کند.

وضعیت را بدون تغییر remote می‌توان با این فرمان‌ها و صفحه‌ها بررسی کرد:

```powershell
git tag -n --sort=-creatordate
git ls-remote --tags origin
```

- [صفحه Releases پروژه](https://github.com/peymanarjmand/hamyar/releases)
- [workflow انتشار APK امضاشده](https://github.com/peymanarjmand/hamyar/actions/workflows/publish-release.yml)

در محیط ممیزی، GitHub CLI (`gh`) نصب نبود. این موضوع علت ۴۰۴ نیست، اما فرمان‌های انتشار دستی مبتنی بر `gh` بدون نصب و ورود به CLI اجرا نمی‌شوند.

### چک‌لیست رفع پس از آماده‌شدن نسخه ۱.۴.۱

تا قبل از تکمیل توسعه، تأیید تست‌ها، تأیید Play Protect فایل دقیق و اجازه صریح مالک پروژه، هیچ tag یا Release نسازید و tagهای محلی قدیمی را صرفاً برای رفع سریع ۴۰۴ push نکنید.

#### ۱. فریز نسخه و سازگاری داده

- [x] اصلاح درآمد با ترتیب دلخواه، بازسازی اتمیک suffix و حذف حسابرسی‌شدهٔ درآمد/انتقال/تسویه در RTL نهایی شده باشند.
- [x] هر حذف فقط با رمز دقیق برنامه انجام شود، در سال بسته یا با وابستگی ناسازگار fail-closed بماند و دلیل/رکورد حذف‌شده در تاریخچه قابل مشاهده باشد.
- [x] `versionName` برابر `1.4.1` و `versionCode` برابر `7` باشد.
- [x] بخش `Unreleased` در `CHANGELOG.md` با تاریخ واقعی انتشار به `1.4.1` منتقل شود.
- [x] `release/v1.4.1/RELEASE_NOTES.md` بازبینی و نهایی شود.
- [x] Room روی نسخه ۴ باقی بماند؛ چون وضعیت `DELETED` در ستون متنی موجود ذخیره می‌شود، هیچ migration یا بازسازی دیتابیس لازم و هیچ destructive migration مجاز نیست.
- [x] backup جدید با schema 6 round-trip شود و بازیابی backupهای schema 1 تا 6 با مقادیر سازگار موفق باشد.
- [ ] پیش از آزمون ارتقای واقعی، داخل خود برنامه backup رمزدار گرفته شود.

#### ۲. verification کامل

در Windows PowerShell:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

- [x] تست‌ها ثابت کنند اصلاح آخرین، میانی و قدیمی‌ترین درآمد پشت‌سرهم ممکن است، eligibility snapshot ثابت می‌ماند و remainder/claim/custody پس از replay متوازن است.
- [x] تست‌های حذف درآمد، انتقال و تسویه ثابت کنند اثر دفتر دقیقاً خنثی می‌شود، رکورد `DELETED` و audit باقی می‌مانند و تراکنش از جمع‌های فعال خارج می‌شود.
- [x] تست‌های رمز حذف ثابت کنند قفل خاموش، رمز اشتباه، نشست باز یا device authentication هیچ mutation مالی ایجاد نمی‌کند و retry پایدار رعایت می‌شود.
- [x] تست‌های سال بسته، تخصیص وابسته و rollback اتمیک موفق باشند.
- [x] تست POS ثابت کند این روش فقط برای دریافت انعام یا فروش ضایعات در دسترس است و برای انتقال یا تسویه پذیرفته نمی‌شود.
- [x] navigation و تمام صفحه‌های جدید روی دستگاه/emulator در RTL بازبینی شوند.
- [x] نصب درجا روی APK دائمی ۱.۴.۰ با **همان کلید دائمی**، بدون حذف یا بازسازی Room 4 و با حفظ داده‌های موجودِ ساختگی تأیید شود.
- [x] diff نهایی فاقد APK/AAB، keystore، secret، اطلاعات واقعی پرسنلی/مالی و شماره کارت واقعی باشد.

#### ۳. امضای دائمی؛ مانع مهم انتشار

خروجی محلی قدیمی هنگام ممیزی با `CN=Android Debug` و SHA-256 گواهی `1142a44b64085eab39420823a83618e9daf6b0082a6b260390eed09c0ec7c559` امضا شده بود و نباید منتشر شود. تنظیم build اکنون اصلاح شده است: وقتی متغیرهای `HESABYAR_KEYSTORE_*` حاضر نباشند، `assembleRelease` خروجی را بدون امضا می‌سازد و `assemblePublicRelease` صریحاً شکست می‌خورد. بنابراین artifact عمومی فقط با کلید دائمی قابل ساخت است.

- [ ] یک keystore دائمی مخصوص انتشار ساخته و نسخه پشتیبان آن خارج از Git و در محل امن نگهداری شود.
- [ ] پنج secret با نام‌های `ANDROID_KEYSTORE_BASE64`، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS`، `ANDROID_KEY_PASSWORD` و `ANDROID_SIGNING_CERT_SHA256` در GitHub Actions تنظیم شوند.
- [ ] fingerprint گواهی دائمی ثبت، در `ANDROID_SIGNING_CERT_SHA256` pin و در تمام نسخه‌های عمومی بعدی ثابت نگه داشته شود.
- [ ] APK تولیدشده توسط workflow با `apksigner verify --verbose --print-certs` بررسی و signer آن با کلید دائمی تطبیق داده شود؛ signer نباید `Android Debug` باشد.

اگر کاربری قبلاً APK محلی debug-signed را نصب کرده باشد، Android نسخه permanent-signed را روی آن update نمی‌کند. مسیر امن انتقال: backup رمزدار داخل برنامه، حذف نسخه debug، نصب نسخه عمومی permanent-signed و restore همان backup است. پس از اولین انتشار عمومی، تغییر یا گم‌کردن کلید دائمی باعث قطع امکان ارتقای درجا می‌شود.

گواهی دائمیِ تأییدشده برای APK کافه‌بازار ۱.۱.۰ و تمام به‌روزرسانی‌های عمومی بعدی:

```text
f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f
```

#### ۳٫۱. گیت Play Protect و هش دقیق فایل

امضای معتبر به‌تنهایی برای انتشار کافی نیست. هر APK عمومی باید ابتدا در VirusTotal ثبت و با Play Protect روشن روی دستگاه فیزیکی certified بدون هشدار نصب شود. اگر همان آزمون نخست بدون هشدار clear بود، مقدار `GOOGLE_APPEAL_SUBMITTED_AT=not-required` ثبت می‌شود و فاصلهٔ انتظار appeal موضوعیت ندارد. فقط در صورت هشدار توسعه‌دهندهٔ ناشناخته یا مسدودشدن نصب، appeal رسمی Google با SHA-256 همان فایل انجام، تاریخ واقعی UTC ثبت و پس از حداقل هفت روز کامل همان APK دوباره آزمایش می‌شود.

فایل APK باید byte-for-byte با `APK_SHA256` رکورد برابر باشد. package، versionName، versionCode و fingerprint نیز دوباره کنترل می‌شوند. `scripts/verify-play-protect-approval.sh` و workflow انتشار در وضعیت `pending`، نتیجه‌ای غیر از `clear` یا هر اختلافی شکست می‌خورند. عوض‌کردن کلید دائمی، بازگشت به debug signing یا ساخت فایل جدید برای دورزدن هشدار ممنوع است.

`vcsInfo.include` برای build عمومی غیرفعال است تا شناسهٔ Git وارد بسته نشود، اما این به معنی مجازبودن rebuild نیست: مرحلهٔ امضا نیز می‌تواند بایت‌های APK را تغییر دهد. نامزد خصوصی باید یک‌بار ساخته، با run ID و artifact name نگهداری و همان فایل دقیق پس از تأیید منتشر شود.

#### ۴. انتشار خودکار؛ فقط بعد از اجازه صریح

پس از نهایی‌شدن موارد بالا و دریافت اجازه برای tag/push، نمونه فرمان نسخه ۱.۴.۱ چنین است:

```bash
git tag -a v1.4.1 -m "HesabYar Garage 1.4.1"
git push origin v1.4.1
```

پیش از tag، workflow با نام `Prepare signed Play Protect candidate` را با `workflow_dispatch` روی commit موردنظر اجرا کنید. فایل artifact خصوصی را دانلود و همان فایل را در VirusTotal/Play Protect بررسی کنید؛ سپس run ID، نام artifact، source commit، هش و تاریخ‌ها را در `PLAY_PROTECT_APPROVAL.env` ثبت و کامیت کنید. فقط بعد از تأیید exact-file و اجازه مالک، tag بالا push می‌شود. workflow tag فایل را از همان run دانلود می‌کند و بدون rebuild این assetها را منتشر می‌کند:

- `HesabYar-Garage-v1.4.1.apk`
- `HesabYar-Garage-latest.apk`
- `SHA256SUMS.txt`

اگر artifact خصوصی منقضی شد یا APK دوباره ساخته شد، هش جدید محسوب می‌شود و VirusTotal/اعتراض/تست باید تکرار شوند. tag عمومی را force-move نکنید؛ اگر محتوای app یا build بعد از نامزد تأییدشده تغییر کرد، نامزد و تأیید جدید بسازید.

#### ۵. کنترل پس از انتشار

- [ ] Release در حالت published/latest باشد، نه draft یا prerelease، و هر سه asset بالا را داشته باشد.
- [ ] لینک README در مرورگر signed-out واقعاً APK را دانلود کند.
- [ ] SHA-256 فایل دانلودشده با `SHA256SUMS.txt` یکی باشد.
- [ ] امضا و fingerprint APK دانلودشده با کلید دائمی تطبیق داشته باشد.
- [ ] `adb install -r` روی نسخه عمومی قبلی با همان signer موفق باشد و داده، migration و بازکردن صفحات حساس بررسی شود.
- [ ] URL مستقیم زیر پاسخ موفق بدهد:

```text
https://github.com/peymanarjmand/hamyar/releases/latest/download/HesabYar-Garage-latest.apk
```

## 1. Verify the public repository

The canonical repository currently exists at `https://github.com/peymanarjmand/hamyar`. Confirm that `origin` points to the intended repository before any future publication:

```bash
git remote -v
git ls-remote origin HEAD
```

For a new fork or renamed repository, update `origin` and every absolute release URL in the documentation before creating a tag.

Recommended GitHub **About** description:

```text
Offline-first Persian Android app for auditable garage workforce, tip custody, settlement, and Jalali financial-year management.
```

Recommended topics:

```text
android kotlin jetpack-compose room offline-first persian rtl jalali-calendar accounting material3 android-keystore open-source
```

## 2. Create one permanent Android release key

Do this once before the first public release:

```bash
keytool -genkeypair -v \
  -keystore hesabyar-release.jks \
  -alias hesabyar \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Peyman Arjmand, O=HesabYar Garage"
```

Back up the keystore and its passwords in a secure offline location. Losing or changing this key prevents existing users from installing future updates over the previous version. Never add the keystore or passwords to Git.

Record the public certificate SHA-256 fingerprint and keep it with the release record:

```bash
keytool -list -v -keystore hesabyar-release.jks -alias hesabyar
```

## 3. Configure GitHub Actions secrets

Convert the keystore to a single-line Base64 value.

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("hesabyar-release.jks")) | Set-Clipboard
```

Linux/macOS:

```bash
base64 < hesabyar-release.jks | tr -d '\n'
```

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `hesabyar` or the alias you selected |
| `ANDROID_KEY_PASSWORD` | Key password |
| `ANDROID_SIGNING_CERT_SHA256` | Permanent certificate SHA-256 fingerprint, with or without colons |

Create a GitHub Actions environment named `public-release-signing`, require an owner/reviewer approval for it, and restrict deployment branches to `main`. Create a separate `public-release-publishing` environment with owner/reviewer approval and restrict it to protected semantic-version tags. Add a repository tag rule that prevents force-updating/deleting `v*.*.*` tags and limits their creation to the owner. The workflows additionally reject non-main candidate dispatches and tags whose commit is not contained in `origin/main`.

The private candidate workflow writes the key only to the temporary GitHub runner, builds and verifies the signed APK, uploads it as a private 90-day artifact, and deletes the temporary key file. The tag workflow never receives the private key and never rebuilds the approved APK.

## 4. Prepare a version

Before tagging:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move relevant entries from `Unreleased` in `CHANGELOG.md` to the new version.
3. Run the local verification suite.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease
```

This command verifies that the release variant compiles; without signing variables its APK is intentionally unsigned. A local distributable build must use `assemblePublicRelease` with every `HESABYAR_KEYSTORE_*` variable configured.

## 5. Prepare, approve, and publish

Run `Prepare signed Play Protect candidate` manually for the final source commit. Download the exact private artifact, submit that APK to VirusTotal and Google when needed, and retest it on a certified physical device with Play Protect enabled. Record the run ID, artifact name, source commit, exact SHA-256, report URL, and dates in `release/v<version>/PLAY_PROTECT_APPROVAL.env`.

Create and push an annotated semantic-version tag only after the record passes `scripts/verify-play-protect-approval.sh`, the version is final, and the repository owner has explicitly authorized publication:

```bash
git tag -a v1.4.1 -m "HesabYar Garage 1.4.1"
git push origin v1.4.1
```

The `Publish Play Protect-approved APK` workflow will:

1. run unit tests, Android Lint, and Android-test assembly against the tagged source;
2. download the exact private candidate by recorded Actions run ID and artifact name;
3. prove that app/build inputs have not changed since the candidate source commit;
4. verify exact SHA-256, signature, pinned certificate, application ID, tag/version, SDKs, alignment, the exact reviewed permission allowlist, and Play Protect approval;
5. generate `SHA256SUMS.txt`;
6. publish that same APK as `HesabYar-Garage-v1.4.1.apk` and `HesabYar-Garage-latest.apk` using `release/v1.4.1/RELEASE_NOTES.md`.

After the workflow succeeds, the README download link resolves directly to:

```text
https://github.com/peymanarjmand/hamyar/releases/latest/download/HesabYar-Garage-latest.apk
```

## No manual publication fallback

Do not create a public GitHub Release with `gh release create`, the web UI, or a locally rebuilt APK. Those paths bypass the exact-artifact provenance and Play Protect approval gate. If the automated workflow or its private artifact is unavailable, repair/re-run the candidate workflow and repeat VirusTotal and the certified-device test for the new exact SHA-256; repeat Google appeal and its mandatory wait only when that exact APK triggers a warning or block. The `latest` alias is created only inside the publishing runner's temporary `dist/` directory and must not be left in the repository tree.

## Post-release verification

- Open the README download button in a signed-out browser session.
- Compare the downloaded APK with `SHA256SUMS.txt`.
- Install over the previous version to verify signing-key continuity.
- Confirm the package name is `com.hesabyar.garage` and existing data remains available.
