#!/usr/bin/env bash

set -Eeuo pipefail

fail() {
  printf 'Test failure: %s\n' "$*" >&2
  exit 1
}

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT
cd "$workspace"

write_metadata() {
  local build_type="$1"
  local version_name="$2"
  local output_file="$3"
  local output_directory="app/build/outputs/apk/$build_type"
  mkdir -p "$output_directory"
  printf 'apk payload' > "$output_directory/$output_file"
  jq -n \
    --arg version_name "$version_name" \
    --arg output_file "$output_file" \
    '{version: 3, elements: [{versionName: $version_name, outputFile: $output_file}]}' \
    > "$output_directory/output-metadata.json"
}

version="0.1.1-codex-example-abcdef0"
write_metadata debug "$version" app-debug.apk
bash "$script_directory/stage-apk.sh" debug artifacts/debug
[[ -s "artifacts/debug/Saki.Android-Debug-v${version}.apk" ]] ||
  fail "Debug APK was not staged with the expected name."

write_metadata release "$version" app-release-unsigned.apk
bash "$script_directory/stage-apk.sh" release artifacts/release
[[ -s "artifacts/release/Saki.Android-Release-v${version}-unsigned.apk" ]] ||
  fail "Unsigned Release APK was not labelled as unsigned."

if bash "$script_directory/stage-apk.sh" debug artifacts/debug >/dev/null 2>&1; then
  fail "Staging unexpectedly overwrote an existing APK."
fi

printf 'stage-apk.sh tests passed.\n'
