# Building Legacy Trilogy

## Requirements

- Android SDK and NDK
- Java and Gradle
- Git with submodule support
- Android ARM64 build environment

## Clone

    git clone --recurse-submodules https://github.com/erpiopio/LegacyTrilogy-Thor.git
    cd LegacyTrilogy-Thor

## DragonByteZ

DragonByteZ is maintained by spicybung.
Legacy Trilogy uses the upstream repository as a Git submodule.

Apply the Trilogy adaptations before building:

    git -C tools/DragonByteZ apply ../../patches/dragonbytez-trilogy.patch

The patch adds support for the verified Spanish-expanded
Buu's Fury ROM and required compilation adjustments.

## Android

The Android project is located in android/.

    cd android
    ./gradlew :app:assembleDebug

The application uses three separately compiled ARM64 game engines.
The current project includes prebuilt engine libraries.
See the three individual repositories for their source projects.

No ROM or BIOS files are included.

## Credits and licenses

See CREDITS.md and licenses/.
