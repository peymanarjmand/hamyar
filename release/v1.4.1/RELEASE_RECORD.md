# Release-candidate record — HesabYar Garage 1.4.1

## Lifecycle state

- State: local permanent-signed candidate verified; protected-workflow/publication gates pending
- Protected-workflow source commit: pending until the verified change reaches `main` and the private candidate workflow runs
- APK: `cafe-bazaar/HesabYar-Garage-CafeBazaar-v1.4.1.apk` (ignored by Git)
- Exact local APK SHA-256: `74f54975b8fe902e3c2410244805bb7d17bc9e3c4666c9bd588645f1edde90fb`
- Exact local APK size: `2,290,363` bytes
- Tag, GitHub Release, Cafe Bazaar upload, and public publication: not performed and not authorized by this record

## Version contract

- Application ID: `com.hesabyar.garage`
- Version name: `1.4.1`
- Version code: `7`
- Room database: `4`; no DDL or migration change from 1.4.0
- Portable backup schema: `6`, accepting schemas `1..6`
- Minimum SDK: `26`
- Target/compile SDK: `36`
- Internet permission: forbidden

## Behavior contract

- Correcting an older active income atomically reverses and replays the affected newer active income suffix while preserving frozen eligibility statuses and reasons.
- A real active transfer/settlement source dependency still blocks unsafe income correction or deletion until the dependency is changed or deleted.
- Income, custody-transfer, and settlement deletion is append-only: ledger effects are reversed, the record becomes `DELETED`, and the deletion reason/audit remain queryable.
- Deleted rows are excluded from active balances and lists but remain visible in transaction/year history as inactive records.
- Every deletion requires a fresh exact app-password verification; an unlocked session, biometric prompt, or device credential is not a substitute.
- Closed financial years remain immutable and every multi-table mutation is atomic.

## Data-compatibility contract

- Room remains at version 4 because `DELETED` is stored in the existing text status columns; no table, index, foreign-key, or destructive migration is introduced.
- Existing 1.4.0 personnel, employment/absence, income, claim, custody, transfer, settlement, encrypted card, correction-link, FIFO, year, and audit rows must survive update-in-place.
- Backup schema 6 must round-trip deleted income/transfer/settlement states and DELETE audit rows while continuing to restore schemas 1 through 6.
- The app-security DataStore remains excluded from financial backup and restore.

## Permanent signing contract

The final candidate must use the existing permanent certificate SHA-256:

`f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`

The local candidate has exactly one RSA-4096 signer, APK Signature Scheme v2, and the same package/signing identity as the exact 1.4.0 candidate. `apksigner`, `aapt`, and `zipalign -c -P 16 -v 4` checks passed. The protected-workflow candidate still requires its own provenance and exact-file approval record.

## Verification status

- [x] Debug Kotlin and Android-test compilation
- [x] JVM test suite: 47 tests in 14 suites, zero failures/errors/skips
- [x] Targeted repository, correction/deletion ledger, backup-schema-6, app-password security, and Compose checks: 18/18 passed
- [x] Full connected instrumentation suite: 35/35 passed on Android 15 / API 35
- [x] Full clean unit, lint, Android-test assembly, R8/release build, and `assemblePublicRelease`
- [x] RTL visual QA with synthetic data for delete confirmation, deleted history, and preserved update-in-place state
- [x] Local permanent-signed candidate metadata/signature/alignment/permission checks
- [x] Exact permanent-signed 1.4.0 code 6 -> 1.4.1 code 7 `adb install -r` test; first-install time and synthetic financial/history data preserved
- [ ] Protected `main` workflow candidate run ID, artifact name, source commit, and provenance binding
- [ ] VirusTotal report and any required Google appeal
- [ ] Certified physical-device installation with Play Protect enabled and no warning
- [ ] Owner authorization for tag, push, GitHub Release, Cafe Bazaar upload, or publication

No pending checkbox may be inferred from an older version's evidence. Any byte-changing rebuild creates a new APK hash and requires exact-file verification again.
