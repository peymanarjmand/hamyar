# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [Unreleased]

## [1.4.1] - 2026-07-18

### Fixed

- Fixed income correction getting permanently blocked after the first edit. An income can now be corrected in logical history order while newer unaffected incomes are atomically reversed and replayed with their frozen eligibility decisions.
- Preserved remainder, claim, custody, and FIFO invariants when correcting or deleting an older income; genuinely dependent transfer or settlement allocations still fail closed until their dependency is changed or deleted.

### Added

- Added audited deletion for posted income, custody-transfer, and settlement transactions in an open financial year.
- Deleted transactions remain visible as inactive history entries with their deletion reason and audit log, while their ledger effect is reversed and excluded from active balances.
- Added an exact app-password challenge for every deletion attempt, including persistent retry throttling. Deletion is unavailable until the optional app lock has a password configured.

### Changed

- Income correction now reflows the affected suffix rather than requiring the user to manually edit every newer income from newest to oldest.
- Prepared Android version `1.4.1` with `versionCode 7`, Room database 4, and portable backup schema 6.

### Compatibility

- Keeps Room at version 4 because the new `DELETED` status uses the existing text status columns; no table, index, foreign-key, or destructive database migration is introduced.
- Backup schema 6 preserves deleted statuses and deletion audit records, accepts schemas 1 through 6, and leaves the separate app-lock DataStore outside financial backups.
- Supports update-in-place over permanently signed 1.4.0 installations with the same package and signing certificate; existing personnel, financial rows, encrypted cards, correction links, and audit history remain in place.

## [1.4.0] - 2026-07-17

### Added

- Added an optional app lock with exact Unicode/Persian password support, configurable background timeout, password change, Persian recovery question/answer, and a one-time 20-digit recovery code.
- Added optional unlock through Android's system biometric/device-credential prompt without exposing fingerprint, face, or device-PIN data to the app.
- Added POS as an explicit offline receipt method for tip and scrap income, with a dedicated holder-level POS custody account and no payment-network integration.
- Added full transaction correction forms for income, custody transfer, and settlement fields, including required audit reasons and before/after impact previews.

### Changed

- Financial corrections now reverse the original append-only ledger effect and post a linked replacement; closed years remain immutable and dependent income/source chains must be corrected newest-first.
- Prepared Android version `1.4.0` with `versionCode 6`, Room database 4, and portable backup schema 5.

### Security

- Stores only salted PBKDF2 verifiers for the app password, recovery answer, and recovery code in a device-local security DataStore that is excluded from financial backups.
- Persists exponential attempt throttling without deleting financial data, hides protected content until security state is loaded, and suppresses protected-app previews in Android Recents when the lock is enabled.
- Keeps the app lock disabled after upgrading an existing installation unless the user explicitly enables it.

### Compatibility

- Added the non-destructive Room `3→4` migration and exported schema, preserving all existing 1.3.0 personnel, ledger, encrypted-card, movement, allocation, and audit rows.
- Backup schema 5 restores schemas 1 through 5 and preserves POS and correction links while security credentials remain intentionally device-local.

## [1.3.0] - 2026-07-16

### Added

- Added a per-person “shared tip and scrap share” setting. Disabled personnel remain valid money receivers and transfer participants but receive no claim from newly posted shared income.
- Added year-scoped personnel summaries for total entitlement, settled entitlement, currently held shared money, and the exact receive/pay/self-offset action.
- Added Room 2→3 and backup schema 4 compatibility so the new setting survives restarts, upgrades, and encrypted backup/restore; older data defaults safely to enabled.

### Fixed

- Fixed the Home-screen crash caused by colliding personnel and income row keys when scrolling a populated dashboard.
- Prevented equal non-zero claim and held-money balances from being shown as settled when a self-offset still needs to be recorded.

### Changed

- Replaced ambiguous “custody/trust” user-facing wording with explicit “shared money held by a person” and “shared money transfer” language.
- Prepared Android version `1.3.0` with `versionCode 5`, Room database 3, and portable backup schema 4.

## [1.2.0] - 2026-07-16

### Added

- Added complete personnel-profile editing for name, phone, role, and notes, plus validated editing of employment periods.
- Added direct editing of temporary absences, including a dedicated return-registration action for open absences and a current eligibility-status explanation.
- Added a configurable 0–365-day absence grace policy, persisted in DataStore and password-encrypted backups with a default of 14 days for older installations and backups.
- Added an in-app developer card for Peyman Arjmand with direct Bale and Telegram profile actions.
- Added version-scoped release directories, a release index, and stricter automated APK metadata, permission, alignment, and version-code verification.

### Changed

- Eligibility previews and newly posted income snapshots now use the current absence grace setting while every historical snapshot remains immutable.
- Bumped the portable backup format to schema 3; schema 1 and 2 backups remain supported and receive the historical 14-day default.
- Prepared Android version `1.2.0` with `versionCode 4` for update-in-place installation over the permanently signed 1.1.0 build.

### Security

- Added a two-stage exact-APK Play Protect publication gate with protected environments, immutable Action pins, workflow/source provenance, permanent-signer checks, and a mandatory certified-device approval record before release.

## [1.1.0] - 2026-07-16

### Added

- Added an in-app Persian privacy policy, available before onboarding data is saved and later from the More section.
- Added a Cafe Bazaar publication guide, ready-to-paste store listing copy, and a signer-fingerprint guard for public release automation.
- Added an explicit cash/card-transfer method to shared-income receipt, custody transfer, and real-settlement forms.
- Added optional transaction-scoped card number and exact Solar Hijri date/time metadata; card-transfer records remain valid when every optional detail is blank.
- Added detail/history presentation for the movement method, masked card suffix, and exact transaction moment when supplied.
- Added an explicit Room 1-to-2 migration and exported schema for the optional movement metadata.
- Added backward-compatible backup schema 2 handling that still restores schema 1 files and safely defaults fields that did not exist before 1.1.0.

### Changed

- Removed the real workshop and manager names from onboarding defaults and made privacy acceptance explicit before initial setup.
- Prevented unsigned environments from silently producing a debug-signed Release APK; public artifacts now require the protected `assemblePublicRelease` task.
- Kept bottom navigation available on nested screens and associated each nested destination with its owning top-level section.
- Made the Home action return to the dashboard root from every route and animate the dashboard list to the top, including when Home is selected again.
- Removed the need to pre-register a personnel bank card before recording a card transfer; internal cash/card custody buckets are resolved automatically.
- Distinguished the required accounting date from the optional real-world transaction date and time.

### Security

- Encrypts transaction-scoped card numbers with Android Keystore, masks them by default, and includes them only inside password-encrypted portable backups.
- Preserves existing rows through a non-destructive migration; no destructive database reset is used.

## [1.0.1] - 2026-07-16

### Fixed

- Prevented the personnel detail screen from crashing when records from separate sections shared the same database ID.
- Clamped the selected day automatically when changing to a shorter Solar Hijri month or a non-leap Esfand.

### Changed

- Replaced the date stepper with faster month/day dropdowns and direct numeric input.
- Accepted Persian, Arabic, and Latin digits while validating Solar Hijri year, month, and day values.

## [1.0.0] - 2026-07-16

### Added

- Offline Persian and RTL Android interface built with Jetpack Compose and Material 3.
- Personnel, employment-period, and absence management with Solar Hijri dates.
- Equal tip and scrap-income sharing with the confirmed 14-day absence rule.
- Immutable eligibility snapshots with per-person explanations.
- Separate claim, custody, transfer, and settlement ledgers.
- Partial cash/card custody transfers with FIFO source traceability.
- Debtor/creditor balances and settlement suggestions.
- Encrypted full card storage using Android Keystore.
- Password-encrypted portable backup and transactional restore.
- Final Solar Hijri year closing with historical year access and remainder rollover.
- Unit, instrumentation, lint, release-install, and integrity verification coverage.

[Unreleased]: ../../compare/v1.4.1...HEAD
[1.4.1]: ../../compare/v1.4.0...v1.4.1
[1.4.0]: ../../compare/v1.3.0...v1.4.0
[1.3.0]: ../../compare/v1.2.0...v1.3.0
[1.2.0]: ../../compare/v1.1.0...v1.2.0
[1.1.0]: ../../compare/v1.0.1...v1.1.0
[1.0.1]: ../../compare/v1.0.0...v1.0.1
[1.0.0]: ../../releases/tag/v1.0.0
