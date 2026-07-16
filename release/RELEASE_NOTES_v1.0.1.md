# HesabYar Garage 1.0.1

This patch release fixes the personnel detail crash and makes Solar Hijri date entry substantially faster.

## Fixed

- Personnel details no longer crash when account, employment, absence, or history records have identical numeric IDs.
- Changing the selected month or year now keeps the day inside the valid range, including leap and non-leap Esfand.

## Improved date entry

- Select all twelve Persian month names from a dropdown.
- Select only valid days for the chosen month from a dynamic dropdown.
- Enter year, month, or day directly with Persian, Arabic, or Latin digits.
- Invalid calendar values are explained and cannot be confirmed.

## Updating from 1.0.0

The locally supplied APK is signed with the same certificate as 1.0.0. Install it directly over the existing app; Android keeps the current workshop records and settings. Creating an encrypted backup before any update is still recommended.

## Compatibility

- Android 8.0 (API 26) and newer
- Optimized for Samsung Galaxy A52s 5G and other arm64 Android phones
- Package: `com.hesabyar.garage`
- No Internet permission

## Integrity

Verify the downloaded APK using the SHA-256 checksum supplied with this release.
