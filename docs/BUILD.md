# Build and signing

## Pinned project toolchain

- JDK 17 (full JDK, not only a JRE)
- Gradle **8.9**, via the committed official wrapper
- Android Gradle Plugin **8.7.3**
- Android platform **34** and Build Tools **34.0.0**
- Java source / target compatibility **8**

The wrapper verifies the Gradle distribution against its official SHA-256:

```
d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
```

Install the Android SDK through Android Studio or the official command-line tools. Review and accept Google's SDK terms yourself when prompted. The repository does not contain automatic license acceptance or downloaded SDK binaries.

```sh
# After reviewing the SDK terms, install these packages with your SDK manager:
sdkmanager 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew --no-daemon assembleDebug assembleRelease lintDebug
```

Windows: replace `./gradlew` with `gradlew.bat` and set the same environment variables.

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`: installable development APK, **debug key**.
- `app/build/outputs/apk/release/app-release-unsigned.apk`: optimized **unsigned** APK; do not install or distribute this as the signed release.
- `app/build/outputs/mapping/release/mapping.txt`: release obfuscation mapping; retain with the corresponding version.
- `app/build/reports/lint-results-debug.html`: lint report.

## Sign an installable release

Use one persistent private keystore for subsequent updates. Do not commit it, its password, or a base64 copy to the repository. Keep a secure backup: losing it prevents updating an already-installed app with the same signing identity.

`sign-release.sh` does not create a new key or publish anything. Supply your existing private signing materials:

```sh
export ATRIUM_KEYSTORE=/private/path/atrium-release.p12
export ATRIUM_STORE_PASSWORD_FILE=/private/path/store-password.txt
export ATRIUM_KEY_ALIAS=atrium
./scripts/sign-release.sh \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  dist/atrium-0.1.0.apk
```

If the private-key password differs from the keystore password, also set `ATRIUM_KEY_PASSWORD_FILE`. Passwords are read from private files, not command-line arguments. The script aligns the APK, signs it, verifies the signature and alignment, and creates a SHA-256 sidecar. A release certificate is self-signed; it is an app update identity, not an assertion of Google Play review or identity verification.

## Continuous integration

`.github/workflows/android.yml` runs on pushes, pull requests, and manual dispatch. It uses GitHub's `ubuntu-24.04` runner, JDK 17, the checked wrapper, and preinstalled Android SDK 34. Actions are pinned to commit hashes. The job builds both variants, runs debug lint, verifies debug signing, and saves build reports/artifacts for 14 days. CI does **not** contain the release private key and does **not** publish Releases. Its release APK is intentionally unsigned.

CI checks are not device acceptance testing. Verify the signed APK on the target TV and retain logs/screenshots for the release.

## Android TV emulator

An SDK installation may optionally include `emulator` and `system-images;android-34;android-tv;x86`. Check any presented license before accepting. Create an Android TV AVD through Android Studio or `avdmanager`; launch it with the installed `emulator` tool, then:

```sh
adb install -r dist/atrium-0.1.0.apk
adb shell am start -n dev.jobscn.tvhome/.HomeActivity
adb exec-out screencap -p > native-home.png
```

For D-pad validation use a remote or `adb shell input keyevent KEYCODE_DPAD_RIGHT` / `KEYCODE_DPAD_CENTER` / `KEYCODE_BACK`. Emulator screenshots demonstrate native rendering, not compatibility with every manufacturer TV.

Some official Android TV images are `user` builds with debugging disabled by default. Their emulator may finish booting while ADB still reports `offline`; do not disable authentication or alter system security properties to work around this. Enable debugging through the supported device UI when available.

For native layout and interaction testing without a TV ROM, the official AOSP development image `system-images;android-34;default;x86_64` can run with a 1920×1080, 320-dpi landscape AVD profile. This is a genuine Android runtime but **not** a Xiaomi TV or an Android TV ROM compatibility test. Label screenshots accordingly, and keep actual-TV acceptance testing separate.


## Primary references

- [AGP 8.7 compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes)
- [Gradle Wrapper](https://docs.gradle.org/8.9/userguide/gradle_wrapper.html)
- [Android SDK manager](https://developer.android.com/tools/sdkmanager)
- [Android SDK terms](https://developer.android.com/studio/terms)
- [GitHub Ubuntu 24.04 runner software](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
