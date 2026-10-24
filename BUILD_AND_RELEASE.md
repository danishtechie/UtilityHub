# Build and Release Guide

This document outlines the build, signing, and deployment steps for **UtilityHub**.

---

## 🛠️ Build Variants

- **Debug Build**:
  ```bash
  ./gradlew :app:assembleDebug
  ```
  Generates the debug APK at `app/build/outputs/apk/debug/app-debug.apk` with application ID `com.utilityhub.app.debug`.

- **Release App Bundle (AAB)**:
  ```bash
  ./gradlew :app:bundleRelease
  ```
  Generates the release Android App Bundle at `app/build/outputs/bundle/release/app-release.aab`.

- **Release APK**:
  ```bash
  ./gradlew :app:assembleRelease
  ```

---

## 🔑 Release Signing Setup

Release keystore credentials must never be committed to source control.

1. Generate a keystore (if you don't already have one):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias utilityhub -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Store signing credentials in your global `~/.gradle/gradle.properties` file:
   ```properties
   UTILITYHUB_STORE_FILE=/path/to/release.jks
   UTILITYHUB_STORE_PASSWORD=your_keystore_password
   UTILITYHUB_KEY_ALIAS=utilityhub
   UTILITYHUB_KEY_PASSWORD=your_key_password
   ```

---

## 🔐 Firebase Authentication Setup

UtilityHub uses Firebase Authentication for secure user accounts.

1. Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Register an Android app with package name `com.utilityhub.app` (and `com.utilityhub.app.debug` for testing).
3. Enable **Email/Password** sign-in under Authentication → Sign-in method.
4. Download `google-services.json` and place it in the `app/` directory (`app/google-services.json`).
5. `google-services.json` is git-ignored and will not be committed to GitHub.

---

## 🧪 Testing Commands

```bash
./gradlew :core-logic:test            # Pure Kotlin JVM unit tests
./gradlew :app:testDebugUnitTest      # Android ViewModel and tool unit tests
```

---

## 👤 Developer Contact

- **Lead Developer**: Danish Baigh
- **Email**: [dmaqbool006@gmail.com](mailto:dmaqbool006@gmail.com)
- **Instagram**: [@dan.ishh6](https://www.instagram.com/dan.ishh6/)
