# Mihomo browser third-party dependency lock

This file records the immutable external inputs for the embedded mihomo browser work.

## libmihomo-android

- Source: `daaass142/libmihomo-android`
- Wrapper release: `v0.3.2-alpha.20260827`
- Bundled mihomo: `v1.19.30`
- Bridge ABI: `3`
- Artifact: `libmihomo-android-v0.3.2-alpha.20260827.aar`
- SHA-256: `6acc2446392ecea0307609147387c89ba38f4697d5d3d4c80e10fd51ea4265e7`
- Supported ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- License: GPL-3.0

The build must verify the AAR checksum before compilation. Do not consume `latest`,
a moving branch, or an unverified AAR.

## Zashboard

- Source: `Zephyruso/zashboard`
- Release: `v3.24.0`
- Artifact: `dist-no-fonts.zip`
- SHA-256: `4f80f0b8d22433cff40901bccb65a58b14cea19ec4c8a0c9615040e52e96f181`
- License: MIT

Zashboard is not consumed in Phase 1. The values are locked now so Phase 4 can
embed an exact offline asset instead of following `latest`.

## License compatibility

EinkBro is GPL-3.0-or-later and libmihomo-android is GPL-3.0, so the planned
combined distribution remains under GPL-compatible terms. Zashboard is MIT and
can be redistributed inside the GPL application while preserving its copyright
and license notice.

## Android build compatibility

The current EinkBro build uses AGP 8.13.2, compileSdk 36, minSdk 24, and Java 17.
This satisfies libmihomo-android's AGP 8.5.1+ and minSdk 21 requirements.

The mihomo-enabled build intentionally drops the legacy `x86` APK because the
pinned AAR does not ship an x86 native library.
