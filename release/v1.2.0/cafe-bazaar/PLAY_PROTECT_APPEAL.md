# Play Protect review — HesabYar Garage 1.2.0

## Exact candidate

- Developer name: `Peyman Arjmand`
- Developer email: `peymanarjmand308@gmail.com`
- Package name: `com.hesabyar.garage`
- Version: `1.2.0` (`4`)
- APK: `HesabYar-Garage-CafeBazaar-v1.2.0.apk`
- APK SHA-256: `D9A6E3C1731A852657762F7CDBCDE8D65E7B1154538B7C0D4008762C88813853`
- Signer certificate SHA-256: `f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f`
- VirusTotal report: <https://www.virustotal.com/gui/file/d9a6e3c1731a852657762f7cdbcde8d65e7b1154538b7c0d4008762c88813853/detection>
- Current gate status: `pending`

This is the frozen local **Cafe Bazaar** candidate. It is not yet associated with a GitHub Actions candidate run. If the automated GitHub Release path is used later, run `Prepare signed Play Protect candidate` first and repeat the exact-file review for the downloaded Actions artifact whenever its SHA-256 differs.

The earlier candidate with SHA-256 `FBE0CAFC8A6918295EB3E345D7AFFD35DD23D0A8886B294BD6EC5441EEACB841` displayed Google's uncommon-developer block. It has been superseded by the frozen exact candidate above; do not appeal or upload both files interchangeably, and do not rebuild the new candidate after approval.

## Ready-to-submit English explanation

> HesabYar Garage is an offline Persian workforce and shared-income accounting app for a small auto body repair shop. It requests no INTERNET permission and contains no advertising, analytics, remote-code loading, hidden downloads, accessibility service, device administrator, overlay, SMS, contacts, or location access. Business data is stored locally with Room and DataStore. Saved card numbers are encrypted with Android Keystore, and portable backups are password-encrypted and accessed only through Android's user-selected Storage Access Framework. Bale and Telegram profile links open an external app only after an explicit user tap and do not attach or transmit app data. The APK is signed with our permanent 4096-bit RSA release key. Play Protect reports the developer as uncommon/unknown; we believe this is a false positive and request review of the exact SHA-256 supplied above.

## Completion checklist

- [ ] Upload the exact APK to VirusTotal and confirm the report SHA-256.
- [ ] Submit the Google Play Protect appeal with the exact package and SHA-256.
- [ ] Wait at least 7 full days (Cafe Bazaar recommends a 7–10 day review window).
- [ ] Reinstall the same APK on a certified physical device with Play Protect enabled.
- [ ] Confirm that installation completes without an uncommon-developer or blocked-app warning.
- [ ] Set the version-scoped approval record to `approved`/`clear` with the actual dates.
- [ ] Run `scripts/verify-play-protect-approval.sh` against the exact APK before any tag, GitHub Release, or Cafe Bazaar submission.
