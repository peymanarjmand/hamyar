# GitHub release material — 1.4.1 draft

A verified local permanent-signed APK exists with its checksum and update-in-place evidence, but no protected-workflow-approved candidate, tag, GitHub Release, published checksum file, or public asset exists for v1.4.1 yet.

After every release gate passes and the owner explicitly authorizes publication, use `../RELEASE_NOTES.md` as the GitHub Release body. The publishing workflow must obtain the exact approved private candidate without rebuilding it and generate:

- `HesabYar-Garage-v1.4.1.apk`
- `HesabYar-Garage-latest.apk`
- `SHA256SUMS.txt`

Do not commit generated binaries, copy the older 1.4.0 hash, invent evidence, or publish a locally rebuilt APK through the web UI/CLI.
