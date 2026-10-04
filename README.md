# ණය පොත — Android project

This ZIP contains the Android source project. **It does not contain a compiled APK.**
The build environment used to prepare it had no Android SDK/JDK compiler and could not download the SDK. Java compilation, APK installation and real SIM sending have not been verified.

## Features

- Sinhala, mobile layout; one-time shop name setup.
- Customer name/phone search, debt and payment history, balance in integer cents.
- Local PIN setup (4–6 digits), confirmation, retry delay; locks when app goes to background.
- Offline data saved in app-private WebView storage.
- SMS directly through the device's default SMS SIM using Android SmsManager; no gateway or internet permission.
- Review number/message, grant SMS permission, then confirm sending inside the app.
- Multipart Sinhala messages supported; sent status is reported separately from delivery.
- Backup/export and restore through Android's document picker. Backups include the shop name and ledger, not the PIN.

## Windows: generate APK

1. Install Android Studio. Open this folder as a project.
2. In SDK Manager install **Android SDK Platform 35** and **Build Tools 35.0.0**. Use Java 17 or newer compatible with Gradle (Android Studio's bundled JDK is supported).
3. Run **Build-APK.bat**. It downloads Gradle 8.11.1 and project dependencies on the first run. It produces **NayaPotha.apk** in this folder.
4. Alternatively, build the `assembleDebug` Gradle task in Android Studio. Output: `app/build/outputs/apk/debug/app-debug.apk`.

The APK is a debug-signed test build for sideloading. Do not sell this as a production release until compilation/device tests pass and a persistent release signing key is configured. Future updates must use the same signing key and application ID; reinstalling or clearing app data loses the local ledger unless backed up.

## GitHub build alternative

Create your own GitHub repository and upload the whole project, including `.github/workflows/build-apk.yml`. In Actions choose **Build Android APK → Run workflow**. On success, download **NayaPotha-APK** from that run's Artifacts and unzip it to obtain `app-debug.apk`. GitHub Actions account quotas/billing apply; the workflow has not been run by us.

## Install and first use

- Android 8.0+ with a current Android System WebView is required.
- Transfer APK to phone; permit installation from your file manager when Android requests it.
- Create/confirm PIN, enter shop name, add a customer and phone number.
- Choose a default SMS SIM in the phone's SIM settings before sending; the app refuses to guess if there is no default.
- Grant SEND_SMS on first send. Carrier charges and plan limits still apply. Long/Sinhala messages may consume multiple SMS segments.
- Test first with your own consenting number. The app never broadcasts messages to a whole contact list.
- Test permission denial, airplane mode, no SIM, low balance, dual SIM and multipart Sinhala on the actual phone.
- Export backup regularly. To move the existing web ledger here, export from the website and use Restore in this app; data will not move automatically.

## PIN and privacy

The PIN is salted and hashed using PBKDF2; 5 failed attempts trigger a 30-second pause. It is a local screen lock, not a cloud account or encryption of the ledger. Android app-private storage and device security protect local files. Backups are plain JSON and should be kept private. There is no PIN recovery: forgetting it requires clearing app data/reinstalling and restoring your backup. Do not do that before making a backup when you still have access.

## Validation completed

JavaScript syntax and core ledger/backup validation checks were run. Static checks confirm SEND_SMS permission, no INTERNET permission, locked external WebView navigation, and native document-picker backup hooks. Android compilation and runtime tests remain required.
