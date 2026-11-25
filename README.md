# Storage Tracker (Android)

Tracks how your phone's storage changes over time. It takes periodic snapshots, compares any two of them, and shows where the space goes: per app, per file type and per folder, with an alert when free space drops sharply.

- Package: `com.binarybug.storagetracker`
- Android 12 or newer (`minSdk 31`), `targetSdk 34`, `compileSdk 36.1`
- **No third-party or AndroidX libraries.** Everything uses the Android framework: `Os.lstat` / `StatFs` for scanning, `StorageStatsManager` for per-app sizes, `JobScheduler` for the schedule, `NotificationManager` for alerts, `SharedPreferences` for settings, `JsonWriter`/`JsonReader` for snapshot files, and native views plus two custom-drawn charts for the UI. The only build-time downloads are the Android Gradle Plugin (with its built-in Kotlin) and JUnit.
- Sizes use decimal units (1 GB = 1,000,000,000 bytes), like Android Settings.

## What it does

- **Overview:** live storage bar, change since the previous snapshot, "Where your storage goes" (apps, app data, cache, photos, videos, audio, documents, archives, installers, other files, unmeasurable), a "Why System looks so big" explanation with the phone's real numbers, a used/free trend chart with range options (last 5 snapshots, last 10, 1 day, 1 week, all), an "unattributed space over time" chart that warns when it jumps by 1 GB or more, the top category changes (so a 300 MB jump shows which categories caused it), a "full in about N days" forecast from the last two weeks of snapshots (one-time jumps such as installing a big app or deleting a download are left out of the pace and mentioned underneath, so one event does not distort it), a **stability card** with an arrow for where free space is heading over 1 day, 3 days or 1 week (↑ gaining, ↓ losing, → stable; it compares typical free space at the start and end of the period, so a quick jump that fades within hours, like clearing a cache Android refills, counts as stable), a **Memory (RAM)** card with available RAM and swap use, biggest changes, and scan coverage. Shortcuts: **Free up space** opens Android's own storage page, **Share report** sends a plain-text summary.
- **Apps:** every installed app's code, data and cache size; sort by size, data, cache, growth or **unused** (not opened for 30+ days, biggest first); user apps or all apps; search. Tap an app to open its Android "App info" page to clear or uninstall it.
- **Changes:** compare any two snapshots as **Folders**, **Files** or **Categories**; filter by grew/shrank, minimum size (from "Any change" up to 1 GB) and name. The Categories view shows how each category contributed to the change in used space; tap a category to see the exact files in it that changed.
- **Browse:** drill into folders with the change per folder; browse by **category** (the same groups and colours as the Overview, each opening its biggest files); the 50 largest files.
- **Status:** whether the background job is registered, last run and result, approximate next run, on/off switch and interval, alert settings (free-space drop, app/folder growth, a once-a-day warning under 10% free), snapshot retention, the permission and battery checks that decide whether scheduled scans work, and the **snapshot table** (the equivalent of the Windows dashboard's snapshot table; a collapsible card, closed by default so the screen does not keep growing with every scan, tap its header to open it): every snapshot with its change versus the previous one, skipped folders and whether apps were measured; tap one to compare it, use it as the "from" or "to" of Changes, or **delete** it. **Export** saves all snapshots into one zip file (to Downloads, Drive, anywhere you pick) and **Import** adds them back, so your history survives a reinstall; snapshots already on the phone are kept as they are.
- **Notifications:** tapping an alert opens the screen it is about: a free-space drop opens Changes, a low-space warning opens the Overview, and a growth alert opens the grown folder in Browse (or the Apps tab for an app).
- **Quick Settings tile "Scan storage":** one tap in the notification shade starts a scan. Add it from Status > "Add Quick Settings tile" (Android 13+), or by editing your Quick Settings tiles by hand.
- **Top-right button:** play = scan now; it turns into stop while scanning, and a banner shows live progress.
- **Schedule:** Android's `JobScheduler` (default every 2 hours, restored after a restart, only when the battery is not low). A scheduled run is skipped when the newest snapshot is under 30 minutes old; manual scans always run.
- **Accessibility:** 48dp touch targets, screen-reader labels and headings, dark mode, text in sp, and meaning never carried by colour alone.

Known limitation: Android hides other apps' private data and `Android/data` from normal apps, even with All files access. The scanner sees shared storage; apps are measured through Usage access; whatever Android itself cannot attribute (system data, compiled app code, logs, update files, file-system overhead) is shown as "System & unmeasurable". It cannot be reduced without root.

## Requirements

**On the computer (Windows, macOS or Linux)**

| Need | Version | Notes |
|---|---|---|
| JDK | 17 or newer (21 recommended) | `java -version` should show it; set `JAVA_HOME` to it. |
| Android SDK | platform **36.1**, Build-Tools **36.0.0**, Platform-Tools (adb) | Easiest: install Android Studio (2026.1 or newer) and let its setup wizard download them. |
| Gradle | nothing to install | The project includes the Gradle wrapper (`gradlew` / `gradlew.bat`). Its first run downloads the pinned **Gradle 9.8.1** (checksum-verified) into `~/.gradle`. If your network blocks that download, install Gradle 9.6 or newer yourself (required by Android Gradle Plugin 9.5) and type `gradle` instead of `./gradlew`. |
| SDK location | `ANDROID_HOME`, or `sdk.dir` in `local.properties` | Android Studio writes `local.properties` for you (it is machine-specific; do not share it). |

**On the phone**

- Android 12 or newer, a USB cable, and USB debugging switched on (steps below).

**Optional: building behind an artifact mirror**

By default the build uses the public repositories (Google, Maven Central, Gradle Plugin Portal). If your network requires a mirror (Artifactory, Nexus, ...), set these Gradle properties, for example in `~/.gradle/gradle.properties` (user-level, not part of the project) or as the matching environment variables. Each one replaces only the repository it names:

```properties
mavenMirrorGoogle=https://mirror.example.com/maven-google      # mirrors maven.google.com
mavenMirrorCentral=https://mirror.example.com/maven-central     # mirrors Maven Central
mavenMirrorPlugins=https://mirror.example.com/gradle-plugins    # mirrors plugins.gradle.org/m2
mavenMirrorToken=...                                            # optional, sent as "Authorization: Bearer ..."
```

Never commit the token. If your mirror holds back brand-new releases, pin an older version in `gradle/libs.versions.toml`.

## Set up the phone for development

1. Settings > About phone > tap **Build number** 7 times to enable Developer options.
2. Developer options > turn on **USB debugging**.
3. Some manufacturers add extra switches that adb needs to install apps or change permissions, for example "Install via USB" and "USB debugging (Security settings)" on Oppo/realme/Xiaomi phones. Turn those on if an install or a permission command is refused.
4. Connect the cable, choose **File transfer** mode, and tap **Allow** on the "Allow USB debugging?" prompt (tick "Always allow from this computer" if you like).
5. Run `adb devices`: your phone must be listed as `device` (not `unauthorized`).

## Build, test, install and update

Run everything from the project folder. Add the SDK's `platform-tools` folder to `PATH` so that `adb` works.

### Step 1: bootstrap Gradle with the wrapper (once per computer)

The wrapper is a tiny launcher committed with the project (`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`). You never run the jar by hand; the `gradlew` script starts it. On its first run it downloads the exact Gradle version this project was built with (9.8.1, checksum-verified) and then starts it. Every later run reuses that download.

```bash
./gradlew --version          # macOS / Linux / Git Bash.  Windows PowerShell: .\gradlew.bat --version
```

You should see `Gradle 9.8.1`. A JDK 17+ must be installed first (the wrapper does not install Java). If the download is blocked on your network, install Gradle 9.6+ yourself and type `gradle` wherever this file says `./gradlew`.

### Step 2: build and test with Gradle

**macOS / Linux / Git Bash**

```bash
./gradlew :app:assembleDebug        # build   -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests (report: app/build/reports/tests/testDebugUnitTest/index.html)
```

**Windows PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

### Step 3: install on the phone with adb

**macOS / Linux / Git Bash**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"      # macOS: ~/Library/Android/sdk   Windows (Git Bash): "$LOCALAPPDATA/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

adb devices                      # the phone must say "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk    # install, or UPDATE keeping data and permissions
adb shell am start -n com.binarybug.storagetracker/.MainActivity   # open the app
```

**Windows PowerShell**

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools"

adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.binarybug.storagetracker/.MainActivity
```

### Update an app that is already installed (keeps its data)

An update replaces the app's code but keeps its snapshots, settings and granted permissions. Use one of these:

```bash
./gradlew :app:installDebug                                 # builds if needed, then installs/updates on the connected phone (Windows: .\gradlew.bat)
# or, with an APK you already built:
adb install -r app/build/outputs/apk/debug/app-debug.apk    # -r = replace the installed app and keep its data
```

In Android Studio the **Run** button does the same thing. To confirm it was an update and not a reinstall, `adb shell dumpsys package com.binarybug.storagetracker | grep -E "firstInstallTime|lastUpdateTime"` should show the same `firstInstallTime` and a newer `lastUpdateTime`.

If the update is refused:

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the installed app was signed with a different key. Debug builds are signed with the debug keystore of the computer that built them (`~/.android/debug.keystore`), so an APK built on another computer cannot update one installed from this computer. Either build on the original computer, copy that keystore across, or uninstall first (which deletes the app's data).
- `INSTALL_FAILED_VERSION_DOWNGRADE`: the phone has a newer `versionCode`. Raise `versionCode` in `app/build.gradle.kts`, or for a debug build add `-d`: `adb install -r -d <apk>`.
- More than one phone connected: add `-s <serial>` (serials are listed by `adb devices`) or set `ANDROID_SERIAL`. `./gradlew :app:installDebug` installs on every connected device.

### Versioning

Versions are `major.minor.patch`. A bug-fix release raises the patch (1.1.0 to 1.1.1); a feature release raises the minor (1.1.x to 1.2.0); a major release raises the first number. Before every release you install, bump `versionCode` (+1, it must only go up) and `versionName` in `app/version.properties`. The version shows in Android's App info as `<versionName> by binaryBug`, and in the app under Status > About.

### Release build (signed and optimized)

A release APK must be signed with your own key, and it is shrunk and optimized with R8. Android refuses unsigned APKs.

1. **Create a key once** (keep the `.jks` file safe and backed up: if you lose it you can never update installed copies, only uninstall and reinstall):

```bash
keytool -genkeypair -keystore ~/.android/storagetracker-release.jks -storetype PKCS12 -alias storagetracker \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Storage Tracker, O=<your name>"     # prompts for a password
```

2. **Tell Gradle where it is**, in `~/.gradle/gradle.properties` (user-level, not part of the project, never commit it):

```properties
releaseStoreFile=/home/you/.android/storagetracker-release.jks
releaseStorePassword=...
releaseKeyAlias=storagetracker
releaseKeyPassword=...
```

3. **Build and install:**

```bash
./gradlew :app:assembleRelease     # -> app/build/outputs/apk/release/app-release.apk (unsigned if the properties are missing)
adb install -r app/build/outputs/apk/release/app-release.apk
```

- Keep `app/build/outputs/mapping/release/mapping.txt` for every version you ship: it translates the optimized class names in crash reports back to readable ones.
- **Debug and release builds use different signing keys, so one cannot update the other.** To switch, run `adb uninstall com.binarybug.storagetracker` first (this deletes the app's snapshots, settings and permissions).
- A release build is not debuggable, so `adb shell run-as ...` does not work on it. Unit tests run on the debug variant only (`./gradlew :app:testDebugUnitTest`).

### Clean reinstall

Removes snapshots, settings and every permission, so the app starts like a first install:

```bash
adb uninstall com.binarybug.storagetracker
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Android Studio:** open this folder and let Gradle sync. It uses the wrapper automatically, so Step 1 happens by itself. If your network blocks the Gradle download, install Gradle 9.6+ and go to Settings > Build, Execution, Deployment > Build Tools > Gradle > **Use Gradle from: Specified location**. Do not let Studio fall back to an older Gradle: the error mentioning `ProjectTypeBinding` means Gradle is too old for this project.

### Permissions on first run

The Status tab shows the state of each one and has a button for it:

- **All files access** (needed to scan files): Status > Grant all files access.
- **Usage access** (needed for the Apps tab and the breakdown): Apps or Status > Grant Usage access, switch it on, then scan again.
- **Notifications** (free-space alerts) and **battery: Unrestricted** (so Android does not hold the schedule back). Some manufacturers also have "auto-launch" or "background activity" switches in system settings.
- On phones that allow it, adb can grant them for you (some manufacturers refuse; see the phone setup above):

```bash
adb shell appops set com.binarybug.storagetracker MANAGE_EXTERNAL_STORAGE allow
adb shell appops set com.binarybug.storagetracker GET_USAGE_STATS allow
adb shell pm grant com.binarybug.storagetracker android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +com.binarybug.storagetracker
```

### Checking what the app did on the phone (debug builds)

```bash
adb shell dumpsys jobscheduler | grep storagetracker                           # is the schedule registered
adb shell run-as com.binarybug.storagetracker cat shared_prefs/storage_tracker.xml   # last run record
adb shell run-as com.binarybug.storagetracker ls -l files/snapshots              # snapshot files
adb logcat -d -b crash                                                          # empty means no crash
```

### Troubleshooting

- `unauthorized` in `adb devices`: accept the prompt on the phone, or revoke USB debugging authorizations in Developer options and reconnect.
- No device listed: use a data cable, set File transfer mode, then `adb kill-server` and `adb devices`.
- `Plugin ... was not found` or TLS/certificate errors while downloading: your network may intercept HTTPS or block the public repositories; use the mirror settings above.
- Gradle uses the wrong Java: set `JAVA_HOME`, or `org.gradle.java.home=/path/to/jdk` in `~/.gradle/gradle.properties`.
- `./gradlew` cannot download Gradle (blocked network, or HTTPS interception that Java does not trust): install Gradle 9.6+ yourself and use `gradle` instead, or ask your network team about the certificate. The wrapper only needs `services.gradle.org`.
- `./gradlew: Permission denied` on macOS/Linux: run `chmod +x gradlew` once.

## Tests

- **Unit tests** (`app/src/test`, 71 tests): comparison logic, per-app diff, category diff, breakdown arithmetic, snapshot picking, snapshot export/import archive, unattributed-space trend, unused apps, forecast (including one-time jumps), free-space stability, alert rules and alert targets, memory info, report text, trend ranges and byte formatting. Run with `./gradlew :app:testDebugUnitTest`.
- **No UI automation (instrumented tests).** The app is small, and each screen was checked by hand on a real device, so there is no `androidTest` source set. Add one if your release process requires an automated UI pass rate.
