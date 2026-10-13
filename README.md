# Proximity Demo

[![Android CI](https://github.com/RamzanTingku/ProximityDemo/actions/workflows/android-ci.yml/badge.svg)](https://github.com/RamzanTingku/ProximityDemo/actions/workflows/android-ci.yml)

A Kotlin Multiplatform sample with three tabs:

- **Sensors** – foreground-service location tracking plus a live, searchable list of nearby BLE devices ranked by signal strength.
- **UWB** – guided 3-step ultra-wideband ranging between two phones (create/accept offer → exchange codes → live distance).
- **NFC** – read NDEF text tags and write a demo tag, with a cancellable wait and recent-activity history.

## Download

**[⬇ Download the latest APK](https://github.com/RamzanTingku/ProximityDemo/releases/download/latest/ProximityDemo.apk)**

This link never changes. Every push to `main` rebuilds the APK and replaces the file behind it, so you always get the newest build.
The SHA-256 is published next to it as [`ProximityDemo.apk.sha256`](https://github.com/RamzanTingku/ProximityDemo/releases/download/latest/ProximityDemo.apk.sha256).

Install notes:

1. Open the link on your Android phone (Android 10+) and allow "Install unknown apps" for your browser when asked.
2. Updates install over the previous version because every build is signed with the same key.

UWB needs Android 14+ with UWB hardware on both phones, and NFC needs NFC hardware. Emulators have neither; the app shows an explanation instead of failing.

## Architecture

```
shared/        Kotlin Multiplatform – pure domain layer, no Android imports
  domain/model       entities, capabilities, DomainError / DomainResult
  domain/repository  interfaces (Sensor, Uwb, Nfc, Capability)
  domain/usecase     one class per user action (start tracking, write tag, …)
  domain/codec       UWB code exchange format and validation
androidApp/    Android implementation
  data/              repository implementations, BLE / location / NFC / UWB sources
  service/           thin foreground service
  presentation/      ViewModels (MVVM, StateFlow + one-shot effects)
  ui/                Jetpack Compose (stateful routes + stateless content)
  di/                Koin modules
```

Dependencies point inward: UI → ViewModel → use case → repository interface ← data implementation.
Concurrency uses coroutines and Flow throughout; dispatchers and the clock are injected so everything is testable with virtual time.

## Build and test

```sh
./gradlew :androidApp:assembleDebug                         # debug APK
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug
./gradlew :androidApp:connectedDebugAndroidTest             # Compose UI tests, needs a device/emulator
```

JDK 17–25 works for running Gradle 9.1; CI uses Temurin 21.

## CI/CD

`.github/workflows/android-ci.yml`

- Pull requests and pushes: unit tests + lint.
- Push to `main`: additionally builds a signed release APK and uploads it to the rolling `latest` release.

Required repository secrets for signing: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
Without them locally, release builds fall back to the debug key.
