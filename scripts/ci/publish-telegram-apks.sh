#!/usr/bin/env bash

set -Eeuo pipefail

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' is not available."
}

require_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is not configured."
}

find_apksigner() {
  if [[ -n "${APKSIGNER_BIN:-}" ]]; then
    [[ -x "$APKSIGNER_BIN" ]] || fail "APKSIGNER_BIN is not executable."
    printf '%s\n' "$APKSIGNER_BIN"
    return
  fi

  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return
  fi

  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]] ||
    fail "apksigner was not found in PATH or the Android SDK."

  local apksigner
  apksigner="$({
    find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner -print
  } | sort -V | tail -n 1)"
  [[ -n "$apksigner" && -x "$apksigner" ]] ||
    fail "apksigner was not found in the Android SDK build-tools."
  printf '%s\n' "$apksigner"
}

find_single_apk() {
  local artifact_directory="$1"
  local build_label="$2"
  [[ -d "$artifact_directory" ]] ||
    fail "$build_label artifact directory '$artifact_directory' does not exist."

  local apk_files=()
  while IFS= read -r -d '' apk_file; do
    apk_files+=("$apk_file")
  done < <(find "$artifact_directory" -type f -name '*.apk' -print0)

  [[ "${#apk_files[@]}" -eq 1 ]] ||
    fail "Expected exactly one $build_label APK, found ${#apk_files[@]}."
  printf '%s\n' "${apk_files[0]}"
}

telegram_error_description() {
  local response_file="$1"
  jq -r 'if type == "object" then (.description // "no error description") else "invalid JSON response" end' \
    "$response_file" 2>/dev/null || printf 'invalid JSON response\n'
}

require_value TELEGRAM_BOT_TOKEN
require_value TELEGRAM_CHAT_ID
require_value GITHUB_SHA
require_value GITHUB_REPOSITORY
require_value GITHUB_SERVER_URL

[[ ! "$TELEGRAM_BOT_TOKEN" =~ [[:space:]] && "$TELEGRAM_BOT_TOKEN" == *:* ]] ||
  fail "TELEGRAM_BOT_TOKEN is not in the expected bot token format."
[[ ! "$TELEGRAM_CHAT_ID" =~ [[:space:]] ]] ||
  fail "TELEGRAM_CHAT_ID must not contain whitespace."
if [[ -n "${TELEGRAM_MESSAGE_THREAD_ID:-}" && ! "$TELEGRAM_MESSAGE_THREAD_ID" =~ ^[1-9][0-9]*$ ]]; then
  fail "TELEGRAM_MESSAGE_THREAD_ID must be a positive integer when configured."
fi
[[ "$GITHUB_SHA" =~ ^[0-9a-fA-F]{7,64}$ ]] || fail "GITHUB_SHA is invalid."

require_command curl
require_command jq
require_command sha256sum

release_directory="${1:-artifacts/release}"
debug_directory="${2:-artifacts/debug}"
release_apk="$(find_single_apk "$release_directory" "Release")"
debug_apk="$(find_single_apk "$debug_directory" "Debug")"
release_name="$(basename "$release_apk")"
debug_name="$(basename "$debug_apk")"

[[ "${release_name,,}" != *-unsigned.apk ]] ||
  fail "Refusing to publish unsigned APK '$release_name'."
[[ "$release_name" =~ ^Saki\.Android-Release-v(.+)\.apk$ ]] ||
  fail "Release APK has an unexpected name: '$release_name'."
release_version="${BASH_REMATCH[1]}"
[[ "$debug_name" =~ ^Saki\.Android-Debug-v(.+)\.apk$ ]] ||
  fail "Debug APK has an unexpected name: '$debug_name'."
debug_version="${BASH_REMATCH[1]}"
[[ "$release_version" == "$debug_version" ]] ||
  fail "Release and Debug APK versions do not match."

apksigner="$(find_apksigner)"
for apk in "$release_apk" "$debug_apk"; do
  "$apksigner" verify --verbose --print-certs "$apk" >/dev/null 2>&1 ||
    fail "APK signature verification failed for '$(basename "$apk")'."
done

release_checksum="$(sha256sum "$release_apk" | awk '{print $1}')"
debug_checksum="$(sha256sum "$debug_apk" | awk '{print $1}')"
[[ "$release_checksum" =~ ^[0-9a-f]{64}$ ]] || fail "Could not calculate the Release APK checksum."
[[ "$debug_checksum" =~ ^[0-9a-f]{64}$ ]] || fail "Could not calculate the Debug APK checksum."

commit_message="${COMMIT_MESSAGE:-No commit message}"
commit_message="${commit_message//$'\r'/}"
commit_message="${commit_message//$'\n'/ }"
commit_message="${commit_message:0:300}"
short_sha="${GITHUB_SHA:0:7}"
commit_url="${GITHUB_SERVER_URL%/}/${GITHUB_REPOSITORY}/commit/${GITHUB_SHA}"
common_caption="Saki.Android CI build
Version: v${release_version}
Commit: ${short_sha} ${commit_message}
URL: ${commit_url}"
release_caption="${common_caption}
Build: Release
SHA-256: ${release_checksum}"
debug_caption="Saki.Android Debug
Version: v${debug_version}
SHA-256: ${debug_checksum}"
media_json="$(jq -cn \
  --arg release_caption "$release_caption" \
  --arg debug_caption "$debug_caption" \
  '[
    {type: "document", media: "attach://release", caption: $release_caption},
    {type: "document", media: "attach://debug", caption: $debug_caption}
  ]')"

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

curl_args=(
  --silent
  --show-error
  --fail-with-body
  --connect-timeout 15
  --max-time 300
  --output "$response_file"
  --write-out '%{http_code}'
  --form-string "chat_id=${TELEGRAM_CHAT_ID}"
  --form-string "media=${media_json}"
)
if [[ -n "${TELEGRAM_MESSAGE_THREAD_ID:-}" ]]; then
  curl_args+=(--form-string "message_thread_id=${TELEGRAM_MESSAGE_THREAD_ID}")
fi
curl_args+=(
  --form "release=@${release_apk};filename=${release_name};type=application/vnd.android.package-archive"
  --form "debug=@${debug_apk};filename=${debug_name};type=application/vnd.android.package-archive"
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMediaGroup"
)

curl_exit=0
http_status="$(curl "${curl_args[@]}")" || curl_exit=$?
if [[ "$curl_exit" -ne 0 ]]; then
  if [[ "$http_status" =~ ^[0-9]{3}$ && -s "$response_file" ]]; then
    description="$(telegram_error_description "$response_file")"
    fail "Telegram upload failed (curl exit $curl_exit, HTTP $http_status): $description"
  fi
  fail "Telegram upload failed (curl exit $curl_exit, HTTP ${http_status:-unknown})."
fi
if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
  description="$(telegram_error_description "$response_file")"
  fail "Telegram upload failed with HTTP $http_status: $description"
fi
if ! jq -e 'type == "object" and .ok == true and (.result | type == "array" and length == 2)' \
  "$response_file" >/dev/null 2>&1; then
  description="$(telegram_error_description "$response_file")"
  fail "Telegram API rejected the upload: $description"
fi

printf 'Published %s and %s to Telegram.\n' "$release_name" "$debug_name"
