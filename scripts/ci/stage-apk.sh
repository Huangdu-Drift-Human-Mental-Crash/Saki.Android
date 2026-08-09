#!/usr/bin/env bash

set -Eeuo pipefail

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

[[ "$#" -eq 2 ]] || fail "Usage: $0 <debug|release> <output-directory>"

build_type="$1"
output_directory="$2"
case "$build_type" in
  debug)
    build_label="Debug"
    ;;
  release)
    build_label="Release"
    ;;
  *)
    fail "Build type must be 'debug' or 'release'."
    ;;
esac

command -v jq >/dev/null 2>&1 || fail "Required command 'jq' is not available."

metadata="app/build/outputs/apk/${build_type}/output-metadata.json"
[[ -f "$metadata" ]] || fail "APK metadata '$metadata' does not exist."

element_count="$(jq -er '.elements | length' "$metadata")" ||
  fail "APK metadata does not contain an elements array."
[[ "$element_count" -eq 1 ]] ||
  fail "Expected exactly one $build_type APK, found $element_count metadata entries."

version_name="$(jq -er '.elements[0].versionName | select(type == "string" and length > 0)' "$metadata")" ||
  fail "APK metadata does not contain a versionName."
output_file="$(jq -er '.elements[0].outputFile | select(type == "string" and length > 0)' "$metadata")" ||
  fail "APK metadata does not contain an outputFile."

[[ "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z0-9-]+-[0-9a-f]{7}(-dirty)?$ ]] ||
  fail "APK versionName '$version_name' is not a Saki Git version."
[[ "$output_file" != */* && "$output_file" != *\\* ]] ||
  fail "APK outputFile must be a filename, but was '$output_file'."

source_apk="$(dirname "$metadata")/$output_file"
[[ -s "$source_apk" ]] || fail "APK '$source_apk' does not exist or is empty."

unsigned_suffix=""
if [[ "$build_type" == "release" && "${output_file,,}" == *unsigned* ]]; then
  unsigned_suffix="-unsigned"
fi
artifact_name="Saki.Android-${build_label}-v${version_name}${unsigned_suffix}.apk"

mkdir -p "$output_directory"
if find "$output_directory" -maxdepth 1 -type f -name '*.apk' -print -quit | grep -q .; then
  fail "Output directory '$output_directory' already contains an APK."
fi
cp -- "$source_apk" "$output_directory/$artifact_name"

printf 'Staged %s\n' "$output_directory/$artifact_name"
