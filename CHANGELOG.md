# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [Unreleased]

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

[Unreleased]: ../../compare/v1.0.1...HEAD
[1.0.1]: ../../compare/v1.0.0...v1.0.1
[1.0.0]: ../../releases/tag/v1.0.0
