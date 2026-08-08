#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repo_root/scripts/verify-apk-permissions.sh"
package_name="com.hesabyar.garage"

exact_permissions="$(printf '%s\n' \
  "package: $package_name" \
  "uses-permission: name='android.permission.USE_BIOMETRIC'" \
  "uses-permission: name='android.permission.USE_FINGERPRINT' maxSdkVersion='27'" \
  "permission: $package_name.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" \
  "uses-permission: name='$package_name.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'")"

printf '%s\n' "$exact_permissions" | bash "$verifier" "$package_name" >/dev/null

expect_failure() {
  local report="$1"
  local description="$2"
  if printf '%s\n' "$report" | bash "$verifier" "$package_name" >/dev/null 2>&1; then
    echo "$description unexpectedly passed" >&2
    exit 1
  fi
}

missing_fingerprint="$(printf '%s\n' "$exact_permissions" | grep -Fv 'android.permission.USE_FINGERPRINT')"
with_internet="$(printf '%s\n' "$exact_permissions" "uses-permission: name='android.permission.INTERNET'")"
with_debug_receiver="$(
  printf '%s\n' "$exact_permissions" |
    sed "s/$package_name\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION/$package_name.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION/g"
)"

expect_failure "$missing_fingerprint" "Missing reviewed permission"
expect_failure "$with_internet" "Unexpected Internet permission"
expect_failure "$with_debug_receiver" "Debug-package receiver permission"

if printf '%s\n' "$exact_permissions" | bash "$verifier" 'invalid-package' >/dev/null 2>&1; then
  echo "Invalid package name unexpectedly passed" >&2
  exit 1
fi

echo "APK permission allowlist verifier tests passed"
