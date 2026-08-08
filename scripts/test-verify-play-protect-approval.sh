#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repo_root/scripts/verify-play-protect-approval.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

apk="$tmp_dir/candidate.apk"
approved_record="$tmp_dir/approved.env"
initial_clear_record="$tmp_dir/initial-clear.env"
pending_record="$tmp_dir/pending.env"
duplicate_record="$tmp_dir/duplicate.env"
invalid_date_record="$tmp_dir/invalid-date.env"
future_date_record="$tmp_dir/future-date.env"
initial_clear_future_record="$tmp_dir/initial-clear-future.env"
unclear_initial_record="$tmp_dir/unclear-initial.env"
invalid_appeal_marker_record="$tmp_dir/invalid-appeal-marker.env"
too_soon_record="$tmp_dir/too-soon.env"

printf 'deterministic test APK bytes' > "$apk"
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
today="$(date -u +%F)"
appeal_date="$(date -u -d '10 days ago' +%F)"
too_soon_appeal="$(date -u -d '6 days ago' +%F)"
future_date="$(date -u -d '1 day' +%F)"

write_record() {
  local target="$1"
  local status="$2"
  local result="$3"
  local appeal="${4:-$appeal_date}"
  local tested="${5:-$today}"
  cat > "$target" <<EOF
STATUS=$status
APK_SHA256=$apk_sha256
PACKAGE_NAME=com.hesabyar.garage
VERSION_NAME=1.2.0
VERSION_CODE=4
SIGNER_CERT_SHA256=f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f
VIRUSTOTAL_REPORT_URL=https://www.virustotal.com/gui/file/$apk_sha256/detection
GOOGLE_APPEAL_SUBMITTED_AT=$appeal
PLAY_PROTECT_DEVICE_TESTED_AT=$tested
PLAY_PROTECT_RESULT=$result
CANDIDATE_RUN_ID=123456789
CANDIDATE_ARTIFACT_NAME=hesabyar-public-candidate-v1.2.0-123456789
SOURCE_COMMIT=1111111111111111111111111111111111111111
EOF
}

write_record "$approved_record" approved clear
write_record "$initial_clear_record" approved clear not-required "$today"
write_record "$pending_record" pending not-tested
cp "$approved_record" "$duplicate_record"
printf 'STATUS=approved\n' >> "$duplicate_record"
write_record "$invalid_date_record" approved clear 2026-02-30 "$today"
write_record "$future_date_record" approved clear "$appeal_date" "$future_date"
write_record "$initial_clear_future_record" approved clear not-required "$future_date"
write_record "$unclear_initial_record" approved warning not-required "$today"
write_record "$invalid_appeal_marker_record" approved clear not_required "$today"
write_record "$too_soon_record" approved clear "$too_soon_appeal" "$today"

bash "$verifier" \
  "$apk" "$approved_record" com.hesabyar.garage 1.2.0 4 \
  f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f >/dev/null

bash "$verifier" \
  "$apk" "$initial_clear_record" com.hesabyar.garage 1.2.0 4 \
  f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f >/dev/null

expect_failure() {
  local record="$1"
  local description="$2"
  if bash "$verifier" \
    "$apk" "$record" com.hesabyar.garage 1.2.0 4 \
    f1b0da83f792a7b5a6e66638efc4d8913c1dd964055f91843e05f7d4ab05fd9f >/dev/null 2>&1; then
    echo "$description unexpectedly passed" >&2
    exit 1
  fi
}

expect_failure "$pending_record" "Pending record"
expect_failure "$duplicate_record" "Duplicate record"
expect_failure "$invalid_date_record" "Invalid calendar date"
expect_failure "$future_date_record" "Future physical-device test date"
expect_failure "$initial_clear_future_record" "Future initial-clear device test date"
expect_failure "$unclear_initial_record" "Non-clear initial device test"
expect_failure "$invalid_appeal_marker_record" "Unknown no-appeal marker"
expect_failure "$too_soon_record" "Retest before the seven-day wait"

echo "Play Protect approval verifier tests passed"
