#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: aapt dump permissions APK | verify-apk-permissions.sh PACKAGE

Verifies that the release APK requests exactly the reviewed offline permission
set. The custom AndroidX receiver permission is bound to the release package.
EOF
  exit 2
}

[[ $# -eq 1 ]] || usage
package_name="$1"
[[ "$package_name" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$ ]] || {
  echo "Invalid package name for permission verification: $package_name" >&2
  exit 1
}

permission_report="$(cat)"
actual_permissions="$(
  printf '%s\n' "$permission_report" |
    sed -n "s/^uses-permission[^:]*: name='\([^']*\)'.*/\1/p" |
    LC_ALL=C sort -u
)"
expected_permissions="$(
  printf '%s\n' \
    'android.permission.USE_BIOMETRIC' \
    'android.permission.USE_FINGERPRINT' \
    "${package_name}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" |
    LC_ALL=C sort
)"

if printf '%s\n' "$actual_permissions" | grep -Fxq 'android.permission.INTERNET'; then
  echo "Release APK unexpectedly requests Internet permission" >&2
  exit 1
fi

if [[ "$actual_permissions" != "$expected_permissions" ]]; then
  echo "Release APK permission set does not match the reviewed allowlist" >&2
  printf 'Expected permissions:\n%s\n' "$expected_permissions" >&2
  printf 'Actual permissions:\n%s\n' "${actual_permissions:-<none>}" >&2
  exit 1
fi

echo "Release APK permission allowlist verified for $package_name"
