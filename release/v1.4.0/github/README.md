# GitHub release material — development draft

No tag, GitHub Release, signed APK, checksum, or public asset exists for v1.4.0 yet.

After every release gate passes and the owner explicitly authorizes publication, use `../RELEASE_NOTES.md` as the GitHub Release body. The publishing workflow must obtain the exact approved private candidate without rebuilding it and generate:

- `HesabYar-Garage-v1.4.0.apk`
- `HesabYar-Garage-latest.apk`
- `SHA256SUMS.txt`

Do not commit these generated binaries or invent their hashes in this directory.
