#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: verify-play-protect-approval.sh APK RECORD PACKAGE VERSION_NAME VERSION_CODE SIGNER_SHA256

Verifies that a public APK is byte-for-byte identical to the APK recorded as
clear on a Play-Protect-enabled physical device. The record is intentionally a
manual attestation because Play Protect does not expose this device check to CI.
EOF
  exit 2
}

[[ $# -eq 6 ]] || usage

apk_path="$1"
record_path="$2"
actual_package="$3"
actual_version_name="$4"
actual_version_code="$5"
actual_signer_sha256="$(printf '%s' "$6" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"

[[ -f "$apk_path" ]] || { echo "APK not found: $apk_path" >&2; exit 1; }
[[ -f "$record_path" ]] || { echo "Play Protect approval record not found: $record_path" >&2; exit 1; }

declare -A record=()
while IFS='=' read -r key value; do
  key="${key//$'\r'/}"
  value="${value//$'\r'/}"
  [[ -z "$key" || "$key" == \#* ]] && continue
  case "$key" in
    STATUS|APK_SHA256|PACKAGE_NAME|VERSION_NAME|VERSION_CODE|SIGNER_CERT_SHA256|\
    VIRUSTOTAL_REPORT_URL|GOOGLE_APPEAL_SUBMITTED_AT|PLAY_PROTECT_DEVICE_TESTED_AT|\
    PLAY_PROTECT_RESULT|CANDIDATE_RUN_ID|CANDIDATE_ARTIFACT_NAME|SOURCE_COMMIT) ;;
    *) echo "Unknown Play Protect record key: $key" >&2; exit 1 ;;
  esac
  [[ -z "${record[$key]+x}" ]] || { echo "Duplicate Play Protect record key: $key" >&2; exit 1; }
  record[$key]="$value"
done < "$record_path"

required_keys=(
  STATUS APK_SHA256 PACKAGE_NAME VERSION_NAME VERSION_CODE SIGNER_CERT_SHA256
  VIRUSTOTAL_REPORT_URL GOOGLE_APPEAL_SUBMITTED_AT PLAY_PROTECT_DEVICE_TESTED_AT
  PLAY_PROTECT_RESULT
)
for key in "${required_keys[@]}"; do
  [[ -n "${record[$key]:-}" ]] || { echo "Missing Play Protect record value: $key" >&2; exit 1; }
done

actual_apk_sha256="$(sha256sum "$apk_path" | awk '{print $1}' | tr '[:upper:]' '[:lower:]')"
record_apk_sha256="$(printf '%s' "${record[APK_SHA256]}" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
record_signer_sha256="$(printf '%s' "${record[SIGNER_CERT_SHA256]}" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"

[[ "${record[STATUS]}" == "approved" ]] || { echo "Play Protect status is not approved" >&2; exit 1; }
[[ "${record[PLAY_PROTECT_RESULT]}" == "clear" ]] || { echo "Physical-device Play Protect result is not clear" >&2; exit 1; }
[[ "$actual_apk_sha256" == "$record_apk_sha256" ]] || { echo "APK SHA-256 does not match the Play Protect-approved file" >&2; exit 1; }
[[ "$actual_package" == "${record[PACKAGE_NAME]}" ]] || { echo "Package name does not match the approval record" >&2; exit 1; }
[[ "$actual_version_name" == "${record[VERSION_NAME]}" ]] || { echo "Version name does not match the approval record" >&2; exit 1; }
[[ "$actual_version_code" == "${record[VERSION_CODE]}" ]] || { echo "Version code does not match the approval record" >&2; exit 1; }
[[ "$actual_signer_sha256" == "$record_signer_sha256" ]] || { echo "Signer does not match the approval record" >&2; exit 1; }
[[ "${record[VIRUSTOTAL_REPORT_URL]}" == "https://www.virustotal.com/gui/file/${record_apk_sha256}/detection" ]] || {
  echo "VirusTotal report URL does not match the approved APK hash" >&2
  exit 1
}

parse_utc_date() {
  local field_name="$1"
  local raw_date="$2"
  local epoch normalized
  [[ "$raw_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || {
    echo "$field_name must use YYYY-MM-DD" >&2
    return 1
  }
  epoch="$(date -u -d "$raw_date" +%s 2>/dev/null)" || {
    echo "$field_name is not a real calendar date" >&2
    return 1
  }
  normalized="$(date -u -d "@$epoch" +%F)"
  [[ "$normalized" == "$raw_date" ]] || {
    echo "$field_name is not a canonical calendar date" >&2
    return 1
  }
  printf '%s' "$epoch"
}

tested_epoch="$(parse_utc_date PLAY_PROTECT_DEVICE_TESTED_AT "${record[PLAY_PROTECT_DEVICE_TESTED_AT]}")"
today_epoch="$(date -u -d "$(date -u +%F)" +%s)"

(( tested_epoch <= today_epoch )) || { echo "Physical-device test date cannot be in the future" >&2; exit 1; }

if [[ "${record[GOOGLE_APPEAL_SUBMITTED_AT]}" != "not-required" ]]; then
  appeal_epoch="$(parse_utc_date GOOGLE_APPEAL_SUBMITTED_AT "${record[GOOGLE_APPEAL_SUBMITTED_AT]}")"
  (( appeal_epoch <= today_epoch )) || { echo "Google appeal date cannot be in the future" >&2; exit 1; }
  (( tested_epoch >= appeal_epoch )) || { echo "Physical-device retest cannot predate the Google appeal" >&2; exit 1; }
  (( tested_epoch - appeal_epoch >= 7 * 24 * 60 * 60 )) || {
    echo "Physical-device retest must occur at least 7 days after the Google appeal" >&2
    exit 1
  }
fi

echo "Play Protect approval verified for ${actual_package} ${actual_version_name} (${actual_version_code})"
echo "APK SHA-256: ${actual_apk_sha256}"
