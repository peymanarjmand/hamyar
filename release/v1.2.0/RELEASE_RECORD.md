# Release record — HesabYar Garage 1.2.0

## Version contract

- Application ID: `com.hesabyar.garage`
- Version name: `1.2.0`
- Version code: `4`
- Room database: `2` with explicit migration `1 -> 2`
- Portable backup schema: `3`, accepting schemas `1..3`
- Minimum SDK: `26`
- Target/compile SDK: `36`
- Internet permission: forbidden

## Permanent signing contract

The public 1.2.0 APK must use the same permanent signer as the verified Cafe Bazaar 1.1.0 artifact:

`f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`

The historical local 1.0.x debug signer is intentionally not accepted for public updates.

## Verification evidence

- Exact local Cafe Bazaar candidate: `cafe-bazaar/HesabYar-Garage-CafeBazaar-v1.2.0.apk`
- Size: `1,694,831` bytes
- SHA-256: `D9A6E3C1731A852657762F7CDBCDE8D65E7B1154538B7C0D4008762C88813853`
- APK Signature Scheme v2: verified
- Signer count: `1`
- Permanent signer SHA-256: `f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`
- APK metadata: package `com.hesabyar.garage`, version `1.2.0` (`4`), min SDK `26`, target SDK `36`
- Internet permission: absent
- 16 KiB page-aware zip alignment: verified
- JVM verification: `31` tests in `10` suites, no failures
- Android 15 instrumentation: `11` tests, no failures or skips
- `lintDebug`, AndroidTest assembly, R8/minified public Release assembly: successful
- Update test: public `1.1.0` (`3`) updated in place to `1.2.0` (`4`) with the workshop and employee data preserved and no crash
- Final APK reinstall and cold start: successful with preserved data and zero crash-buffer entries
- RTL visual QA: personnel edit, open absence status, return registration, configurable rule, and developer contact actions inspected on Android 15
- Bale and Telegram actions: both handed off to an external handler after an explicit tap
- Candidate integrity: release VCS metadata is omitted and this exact signed APK is frozen; it must be reused rather than rebuilt after approval
- Release automation audit: `actionlint 1.7.12`, YAML parsing, shell syntax, and approval-verifier positive/negative tests passed; the pending record is confirmed to fail closed
- Publication hardening: immutable Action SHAs, protected signing/publishing environments, `origin/main` tag ancestry, workflow-ID provenance, one-signer/v2 checks, and valid non-future approval dates with a seven-day minimum wait are enforced

## Play Protect release gate

- The earlier `FBE0CAFC...B841` candidate displayed Google's uncommon/unknown-developer block and is superseded.
- The exact frozen candidate above has a prepared VirusTotal/Google appeal record.
- This local file is the current Cafe Bazaar appeal candidate. The automated GitHub path remains pending until `Prepare signed Play Protect candidate` produces a private Actions artifact; if that artifact has a different hash, its own VirusTotal/Play Protect approval is required.
- Current status: `pending`; the exact APK has not yet passed the required post-review physical-device Play Protect retest.
- `PLAY_PROTECT_APPROVAL.env` intentionally blocks the tag publication workflow until status is `approved`, result is `clear`, and all exact-file evidence matches.

No tag, push, GitHub Release, store upload, or public publication is authorized by this record alone.
