# Version 1.4.1 RTL visual evidence

These screenshots were captured from the exact local permanent-signed candidate on an Android 15 / API 35 emulator with synthetic data only. Candidate SHA-256: `74f54975b8fe902e3c2410244805bb7d17bc9e3c4666c9bd588645f1edde90fb`.

Captured evidence:

- `delete-password-dialog-v1.4.1.png`: RTL delete confirmation requests an audit reason and app password; no password or recovery secret is visible.
- `deleted-income-history-v1.4.1.png`: the deleted income remains visible in red as «حذف‌شده • بدون اثر در حساب» after its accounting effect is removed.
- `deleted-income-detail-v1.4.1.png`: the inactive detail view retains and displays the synthetic deletion reason while stating that the transaction no longer affects calculations.
- `update-before-v1.4.0.png`: synthetic workshop, personnel, income, custody, remainder, and history immediately before the exact update.
- `update-after-v1.4.1.png`: the same values after `adb install -r`; `firstInstallTime` also remained unchanged in package-manager evidence.

Flexible correction order is verified by Room instrumentation rather than a static screenshot. These images contain no real workshop, personnel, phone, card, password, recovery-answer/code, or financial data and are development evidence, not a Cafe Bazaar publishing approval.
