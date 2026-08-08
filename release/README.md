# Release archive

Release material is grouped by semantic version. APK and AAB binaries are ignored recursively and are published as GitHub Release or store assets, not committed to Git.

| Version | Channel | Contents |
| --- | --- | --- |
| `v1.0.0` | Local debug-signed | Initial APK and release notes |
| `v1.0.1` | Local debug-signed | Patch APK, install guide, checksum, screenshot, historical audit, and notes |
| `v1.1.0` | Cafe Bazaar / permanent signing | Store build record, listing, icon, checksum, and release notes |
| `v1.2.0` | Permanent-signed Cafe Bazaar candidate; Play Protect pending | Frozen local channel copies, exact-hash gate, appeal record, checksums, install guide, release record, store copy, icon, and RTL screenshots; automated GitHub candidate still pending |
| `v1.3.0` | Verified permanent-signed candidate; Play Protect pending | Per-person shared-income eligibility, dashboard crash fix, financial-summary UX, Room/backup compatibility, exact hash/signature record, notes, install guide, store and GitHub material |
| `v1.4.0` | Verified local permanent-signed candidate; public approval pending | Optional app/device lock and recovery, append-only transaction corrections, POS income, Room 3→4 and backup schema 5; local exact-file verification and 1.3.0 update evidence exist, while protected-workflow provenance, Play Protect, and publication remain pending |
| `v1.4.1` | Verified local permanent-signed candidate; public approval pending | Flexible older-income correction through atomic suffix replay, password-protected audited transaction deletion, Room 4 continuity, backup schema 6, exact local hash/signature/update evidence; protected-workflow provenance, VirusTotal, Play Protect, and publication remain pending |

The stable public alias `HesabYar-Garage-latest.apk` is created only in the publishing workflow's temporary `dist/` directory. It must not be kept as a stale local file in this archive.

See [the release process](../docs/RELEASING.md) before building, tagging, or publishing.
