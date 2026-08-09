#!/usr/bin/env bash

set -Eeuo pipefail

fail() {
  printf 'Test failure: %s\n' "$*" >&2
  exit 1
}

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT
mkdir -p "$workspace/bin" "$workspace/release" "$workspace/debug"

cat > "$workspace/bin/apksigner" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$APKSIGNER_LOG"
exit 0
MOCK
cat > "$workspace/bin/curl" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$CURL_LOG"
output_file=""
while [[ "$#" -gt 0 ]]; do
  if [[ "$1" == "--output" ]]; then
    shift
    output_file="$1"
  fi
  shift
done
response="${MOCK_TELEGRAM_RESPONSE:-}"
if [[ -z "$response" ]]; then
  response='{"ok":true,"result":[{},{}]}'
fi
printf '%s' "$response" > "$output_file"
printf '%s' "${MOCK_HTTP_STATUS:-200}"
MOCK
chmod +x "$workspace/bin/apksigner" "$workspace/bin/curl"

version="0.1.1-fix-unsigned-warning-abcdef0"
release_apk="$workspace/release/Saki.Android-Release-v${version}.apk"
debug_apk="$workspace/debug/Saki.Android-Debug-v${version}.apk"
printf 'release' > "$release_apk"
printf 'debug' > "$debug_apk"

export APKSIGNER_BIN="$workspace/bin/apksigner"
export APKSIGNER_LOG="$workspace/apksigner.log"
export CURL_LOG="$workspace/curl.log"
export GITHUB_REPOSITORY="example/Saki.Android"
export GITHUB_SERVER_URL="https://github.example"
export GITHUB_SHA="abcdef0123456789"
export TELEGRAM_BOT_TOKEN="123456:test-token"
export TELEGRAM_CHAT_ID="@saki_builds"
export PATH="$workspace/bin:$PATH"

bash "$script_directory/publish-telegram-apks.sh" "$workspace/release" "$workspace/debug"
grep -q 'sendMediaGroup' "$CURL_LOG" || fail "Telegram media-group endpoint was not used."
grep -q 'attach://release' "$CURL_LOG" || fail "Release attachment is missing from media JSON."
grep -q 'attach://debug' "$CURL_LOG" || fail "Debug attachment is missing from media JSON."
grep -q '^release=@' "$CURL_LOG" || fail "Release multipart attachment was not sent."
grep -q '^debug=@' "$CURL_LOG" || fail "Debug multipart attachment was not sent."
grep -q "filename=$(basename "$release_apk")" "$CURL_LOG" || fail "Release filename was not preserved."
grep -q "filename=$(basename "$debug_apk")" "$CURL_LOG" || fail "Debug filename was not preserved."
[[ "$(wc -l < "$APKSIGNER_LOG")" -eq 2 ]] || fail "Both APK signatures were not verified."

mv "$debug_apk" "$workspace/debug/Saki.Android-Debug-v0.1.2-codex-example-abcdef0.apk"
if bash "$script_directory/publish-telegram-apks.sh" "$workspace/release" "$workspace/debug" >/dev/null 2>&1; then
  fail "Mismatched APK versions were accepted."
fi
mv "$workspace/debug/Saki.Android-Debug-v0.1.2-codex-example-abcdef0.apk" "$debug_apk"

export MOCK_TELEGRAM_RESPONSE='{"ok":false,"description":"denied"}'
if bash "$script_directory/publish-telegram-apks.sh" "$workspace/release" "$workspace/debug" >/dev/null 2>&1; then
  fail "A rejected Telegram response was accepted."
fi
unset MOCK_TELEGRAM_RESPONSE

export MOCK_HTTP_STATUS=500
if bash "$script_directory/publish-telegram-apks.sh" "$workspace/release" "$workspace/debug" >/dev/null 2>&1; then
  fail "An HTTP 500 response was accepted."
fi
unset MOCK_HTTP_STATUS

mv "$release_apk" "$workspace/release/Saki.Android-Release-v${version}-unsigned.apk"
if bash "$script_directory/publish-telegram-apks.sh" "$workspace/release" "$workspace/debug" >/dev/null 2>&1; then
  fail "An unsigned Release APK was accepted."
fi

printf 'publish-telegram-apks.sh tests passed.\n'
