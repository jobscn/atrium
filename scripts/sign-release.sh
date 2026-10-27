#!/usr/bin/env bash
# Sign a local release with a persistent, private keystore. Does not generate keys,
# accept SDK terms, upload secrets, or publish a GitHub Release.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
: "${ATRIUM_KEYSTORE:?Set ATRIUM_KEYSTORE to the private .p12 or .jks file}"
: "${ATRIUM_STORE_PASSWORD_FILE:?Set ATRIUM_STORE_PASSWORD_FILE to the private password file}"
ALIAS="${ATRIUM_KEY_ALIAS:-atrium}"
TOOLS="$ANDROID_HOME/build-tools/34.0.0"
INPUT="${1:-$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk}"
OUTPUT="${2:-$ROOT/dist/atrium-0.1.1.apk}"
mkdir -p "$(dirname -- "$OUTPUT")"
ALIGNED="$(mktemp "$(dirname -- "$OUTPUT")/.aligned-XXXXXX.apk")"
trap 'rm -f "$ALIGNED"' EXIT
"$TOOLS/zipalign" -f -p 4 "$INPUT" "$ALIGNED"
KEY_PASSWORD_ARGS=()
if [ -n "${ATRIUM_KEY_PASSWORD_FILE:-}" ]; then
  KEY_PASSWORD_ARGS=(--key-pass "file:$ATRIUM_KEY_PASSWORD_FILE")
fi
"$TOOLS/apksigner" sign --v4-signing-enabled false --ks "$ATRIUM_KEYSTORE" --ks-key-alias "$ALIAS" \
  --ks-pass "file:$ATRIUM_STORE_PASSWORD_FILE" "${KEY_PASSWORD_ARGS[@]}" \
  --out "$OUTPUT" "$ALIGNED"
"$TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT"
"$TOOLS/zipalign" -c -p 4 "$OUTPUT"
(cd -- "$(dirname -- "$OUTPUT")" && sha256sum "$(basename -- "$OUTPUT")" > "$(basename -- "$OUTPUT").sha256")
printf '\nSigned APK: %s\n' "$OUTPUT"
