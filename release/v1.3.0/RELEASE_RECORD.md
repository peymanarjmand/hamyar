# Release record — HesabYar Garage 1.3.0

## Version contract

- Application ID: `com.hesabyar.garage`
- Version name: `1.3.0`
- Version code: `5`
- Room database: `3` with explicit migrations `1 -> 2 -> 3`
- Portable backup schema: `4`, accepting schemas `1..4`
- Minimum SDK: `26`
- Target/compile SDK: `36`
- Internet permission: forbidden

## Permanent signing contract

Every public 1.3.0 APK must use the existing permanent signer:

`f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`

## Verification state

- Candidate: `cafe-bazaar/HesabYar-Garage-CafeBazaar-v1.3.0.apk` (`1,711,219` bytes)
- APK SHA-256: `6333F14BCE98F284F703816301BD01990B538ADD98F5AB9FAE6237052AFA56A5`
- JVM tests: 33 tests in 11 suites, 0 failures/errors/skips
- Android instrumentation: 16 tests on Android 15, 0 failures/errors/skips
- RTL dashboard/personnel visual QA: passed on Android 15 with synthetic data; evidence is under `screenshots/`
- Clean build: `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`, R8, and `assemblePublicRelease` passed
- APK signature: Scheme v2, one signer, certificate SHA-256 matches the permanent signing contract
- APK metadata: package `com.hesabyar.garage`, version `1.3.0`/`5`, min SDK 26, target SDK 36, four packaged ABIs
- Permissions: no Internet permission; only the AndroidX non-exported dynamic-receiver protection permission is present
- APK alignment: `zipalign -c -P 16 -v 4` passed
- Update-in-place: exact candidate installed over permanent-signed 1.2.0 on Android 15; Room migrated 2→3, the process remained alive, and Logcat contained no fatal exception
- Exact-file VirusTotal, Google appeal, and certified physical-device Play Protect retest: pending

No tag, push, GitHub Release, Cafe Bazaar upload, or public publication is authorized by this record. Any byte-changing rebuild produces a new SHA-256 and invalidates the pending Play Protect workflow for this candidate.
