# Local release-candidate record — HesabYar Garage 1.4.0

## Lifecycle state

- State: local permanent-signed candidate; unpublished; protected-workflow and exact-file public approval pending
- Signed candidate: built locally with `assemblePublicRelease` from source commit `284203b148c37d5f3099595cdfdb7ce8afe40b3a`
- APK: `cafe-bazaar/HesabYar-Garage-CafeBazaar-v1.4.0.apk` (ignored by Git)
- SHA-256: `f8639b3948330ad6f0cfc5059a866a77c7c640836560eebb1e926c5c0df1efe8`; size: `2,273,983` bytes
- Tag, GitHub Release, Cafe Bazaar upload, and public publication: not performed and not authorized by this record

## Version contract

- Application ID: `com.hesabyar.garage`
- Version name: `1.4.0`
- Version code: `6`
- Room database: `4` with a non-destructive `3 -> 4` migration
- Portable backup schema: `5`, accepting schemas `1..5`
- Minimum SDK: `26`
- Target/compile SDK: `36`
- Internet permission: forbidden

## Behavior contract

- App locking is optional and remains disabled until the user enables it.
- Unlocking with Android device authentication is a separate optional setting.
- Password recovery supports a Persian recovery question/answer and a random recovery code.
- Income, custody-transfer, and settlement corrections are append-only: the original record remains and the replacement links back to it.
- POS is available only for tip or scrap-income receipt, not custody transfer or settlement.

## Data-compatibility contract

- Room migration `3 -> 4` adds correction linkage/index support without destructive migration.
- Existing financial rows and their ledger relationships must survive update-in-place.
- Backup schema 5 must round-trip POS values and all correction links while continuing to accept schemas 1 through 5.

## Required permanent signing contract

The local 1.4.0 candidate uses the existing permanent certificate SHA-256:

`f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`

`apksigner` verified APK Signature Scheme v2, exactly one signer, and the same certificate as the exact Cafe Bazaar 1.3.0 APK. A protected GitHub Actions candidate and its provenance record are still pending.

## Verification status

- [x] Local clean unit-test, lint, Android-test assembly, R8, release build, and permanent-signed candidate build: 46/46 JVM tests passed in 13 suites
- [x] Full connected instrumentation suite on Android 15 / API 35: 26/26 tests passed in 10 classes
- [x] RTL emulator QA with synthetic data for optional security, locked-app, POS, and correction before/after flows
- [x] Exact permanent-signed 1.3.0 -> exact local 1.4.0 update with `adb install -r`; package data inode and first-install time stayed unchanged, and the synthetic workshop, employee, income, custody, and history survived
- [x] Local signed-candidate metadata, one-signer v2 signature, certificate continuity, 16 KiB-compatible alignment, exact permission allowlist, package, versions, and SDK checks
- [x] Exact local APK SHA-256 and `cafe-bazaar/SHA256SUMS.txt`
- [ ] Protected `main` workflow candidate run ID, artifact name, and provenance binding
- [ ] VirusTotal report and any required Google appeal
- [ ] Certified physical-device installation with Play Protect enabled and no warning
- [ ] Owner authorization for tag, push, GitHub Release, Cafe Bazaar upload, or publication

Local verification is complete for the recorded APK, but it is not yet authorized for public upload. The exact-file VirusTotal and certified physical-device Play Protect evidence must be completed against the eventual protected-workflow artifact; any byte-changing rebuild invalidates the local hash and update evidence.
