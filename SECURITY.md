# Security policy

## Supported versions

Security fixes are applied to the latest published release and the `main` branch.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Older releases | Best effort |

## Reporting a vulnerability

Please use GitHub's **private vulnerability reporting** feature under the repository's Security tab. Include:

- affected version and Android version;
- clear reproduction steps;
- impact on local data, backups, card data, or ledger integrity;
- relevant logs with all personal and financial data removed.

Do not disclose the issue publicly until a fix is available. A report will normally be acknowledged within seven days.

## Security boundaries

- The application is offline-first and intentionally has no Internet permission.
- The optional app lock is a local UI-access control, not full-database encryption. It is disabled by default and does not replace a secure Android screen lock.
- App-password, recovery-answer, and recovery-code plaintext is not stored. Salted PBKDF2 verifiers and the displayed recovery question live in a separate device-local DataStore and are intentionally excluded from portable financial backups.
- Optional device unlock uses Android `BiometricPrompt`; fingerprint, face, pattern, PIN, and device-password data remain inside Android. Anyone able to unlock the device can unlock the app when this option is enabled.
- Repeated failed app-password or recovery attempts trigger persistent exponential delays but never erase personnel or financial data.
- Bank card values are encrypted by Android Keystore, but device compromise can weaken platform protections.
- Portable backups are protected by the password chosen by the user. Lost backup passwords cannot be recovered.
- Signing keys, keystores, passwords, and real workshop data must never be committed to this repository.
