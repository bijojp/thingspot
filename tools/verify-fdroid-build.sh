#!/usr/bin/env bash
# Checks the promises the F-Droid build makes (ADR 0001, ADR 0004):
#   1. no proprietary Google libraries on its release classpath;
#   2. no INTERNET permission in its release APK.
# Run after `./gradlew assembleFdroidRelease`. Needs ANDROID_HOME (or local.properties sdk.dir).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}"
if [[ -z "$sdk" ]]; then
    echo "Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 2
fi

forbidden='com\.google\.android\.gms|com\.google\.firebase|com\.android\.billingclient|com\.google\.mlkit|com\.google\.android\.play'
deps="$(./gradlew -q :app:dependencies --configuration fdroidReleaseRuntimeClasspath)"
if grep -E "$forbidden" <<<"$deps"; then
    echo "FAIL: the F-Droid build pulls in a proprietary Google library (listed above)." >&2
    exit 1
fi

apk="$(find app/build/outputs/apk/fdroid/release -name '*.apk' | head -1)"
if [[ -z "$apk" ]]; then
    echo "No F-Droid release APK found; run ./gradlew assembleFdroidRelease first." >&2
    exit 2
fi
aapt2="$(find "$sdk/build-tools" -name aapt2 -type f | sort | tail -1)"
if "$aapt2" dump permissions "$apk" | grep -q 'android.permission.INTERNET'; then
    echo "FAIL: the F-Droid APK declares INTERNET; ThingSpot must not." >&2
    exit 1
fi

echo "OK: F-Droid build has no proprietary Google libraries and no INTERNET permission."
