# Release guide

This project publishes APKs through GitHub Releases. The release workflow creates both a versioned asset and the stable `HesabYar-Garage-latest.apk` asset used by the README download button.

## 1. Create the public repository

Create an empty public GitHub repository, preferably named `hesabyar-garage-android`. Do not initialize it with a README, license, or `.gitignore` because those files already exist locally.

```bash
git remote add origin https://github.com/<your-username>/hesabyar-garage-android.git
git push -u origin main
```

Recommended GitHub **About** description:

```text
Offline-first Persian Android app for auditable garage workforce, tip custody, settlement, and Jalali financial-year management.
```

Recommended topics:

```text
android kotlin jetpack-compose room offline-first persian rtl jalali-calendar accounting material3 android-keystore open-source
```

## 2. Create one permanent Android release key

Do this once before the first public release:

```bash
keytool -genkeypair -v \
  -keystore hesabyar-release.jks \
  -alias hesabyar \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Peyman Arjmand, O=HesabYar Garage"
```

Back up the keystore and its passwords in a secure offline location. Losing or changing this key prevents existing users from installing future updates over the previous version. Never add the keystore or passwords to Git.

## 3. Configure GitHub Actions secrets

Convert the keystore to a single-line Base64 value.

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("hesabyar-release.jks")) | Set-Clipboard
```

Linux/macOS:

```bash
base64 < hesabyar-release.jks | tr -d '\n'
```

Add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `hesabyar` or the alias you selected |
| `ANDROID_KEY_PASSWORD` | Key password |

The workflow writes the key only to the temporary GitHub runner, builds the signed APK, verifies its signature, and deletes the temporary key file.

## 4. Prepare a version

Before tagging:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move relevant entries from `Unreleased` in `CHANGELOG.md` to the new version.
3. Run the local verification suite.

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

## 5. Publish

Create and push an annotated semantic-version tag:

```bash
git tag -a v1.0.0 -m "HesabYar Garage 1.0.0"
git push origin v1.0.0
```

The `Publish signed APK` workflow will:

1. validate the four signing secrets;
2. run unit tests and Android Lint;
3. build a minified, signed Release APK;
4. verify the APK signature;
5. generate `SHA256SUMS.txt`;
6. publish `HesabYar-Garage-v1.0.0.apk` and `HesabYar-Garage-latest.apk` as Release assets.

After the workflow succeeds, the README download link resolves directly to:

```text
https://github.com/<your-username>/hesabyar-garage-android/releases/latest/download/HesabYar-Garage-latest.apk
```

## Manual publication of the provided 1.0.0 APK

The already-built APK remains available locally at `release/HesabYar-Garage-v1.0.0.apk` and is intentionally ignored by Git. If you publish this exact file manually, upload it twice—once with its versioned name and once as `HesabYar-Garage-latest.apk`—along with `release/SHA256.txt` and `release/RELEASE_NOTES_v1.0.0.md`.

From PowerShell, after pushing the `v1.0.0` tag:

```powershell
Copy-Item release/HesabYar-Garage-v1.0.0.apk release/HesabYar-Garage-latest.apk -Force

gh release create v1.0.0 `
  "release/HesabYar-Garage-v1.0.0.apk#HesabYar Garage 1.0.0" `
  "release/HesabYar-Garage-latest.apk#Latest HesabYar Garage APK" `
  "release/SHA256.txt#SHA-256 checksum" `
  --verify-tag `
  --title "HesabYar Garage 1.0.0" `
  --notes-file release/RELEASE_NOTES_v1.0.0.md
```

The same signing key must be used for all updates installed over that APK. For a long-lived public project, configure the permanent release key and automated workflow before the first public release.

## Post-release verification

- Open the README download button in a signed-out browser session.
- Compare the downloaded APK with `SHA256SUMS.txt`.
- Install over the previous version to verify signing-key continuity.
- Confirm the package name is `com.hesabyar.garage` and existing data remains available.
