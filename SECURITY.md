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
- Bank card values are encrypted by Android Keystore, but device compromise can weaken platform protections.
- Portable backups are protected by the password chosen by the user. Lost backup passwords cannot be recovered.
- Signing keys, keystores, passwords, and real workshop data must never be committed to this repository.
