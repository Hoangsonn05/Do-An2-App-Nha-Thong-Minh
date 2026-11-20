# P1.1-B2 — Firebase Configuration & Readiness Verification Report

- **Timestamp (Local):** 2026-11-21 00:40:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Git Baseline Checkpoint:** `5b1fa63` (`checkpoint đóng task B1`)
- **Architecture Baseline:** `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (RTDB-only rev.2), `docs/P1.1-B1_RTDB_Cloud_Foundation_Plan.md`, `docs/p1-1-b1/b1-real-world-verification-report.md` (`B1 CONDITIONAL PASS`)
- **B2 Final Gate:** **`B2 PASS`** *(All B2.1–B2.4 local/Emulator readiness criteria verified; B1 remains `B1 CONDITIONAL PASS` pending A2 physical hardware verification; B1-D Production Audit/Deploy remains LOCKED)*

---

## 1. Executive Summary

Phase **P1.1-B2** audited and verified Firebase project configuration, RTDB-only Cloud Foundation readiness, Firebase Authentication compatibility, and Local RTDB Emulator isolation strictly within the local repository and local/AVD Emulator environment — with **zero Production Firebase access**.

All core project/package/RTDB settings (`com.example.smarthomeai`, project `appaintm`, project number `883290202549`, region `asia-southeast1`, endpoint `https://appaintm-default-rtdb.asia-southeast1.firebasedatabase.app`), Firebase BoM/SDKs (`firebase-bom:33.1.2` -> `firebase-auth:23.0.0`, `firebase-database:21.0.0`), Debug/Release parity, and Emulator isolation (`--project demo-smarthomeai`, `127.0.0.1:9000` / `10.0.2.2:9000`, zero `.firebaserc`) were verified directly from source and build outputs.

One concrete **MEDIUM** compatibility gap (`B2-F4`) was identified and fixed with a minimal surgical diff:
- `RtdbCloudRepository.createHome()` and `observeMembers()` checked only `provider.uid == null` instead of validating `!RtdbPaths.isValidUid(currentUid)` (unlike `observeHomes()`), allowing blank/invalid UIDs to bypass the unauthenticated guard in `observeMembers()` or be misclassified as `InvalidData` in `createHome()`.
- `FirebaseProvider.defaultUidResolver(app)` called `FirebaseAuth.getInstance().currentUser?.uid` instead of binding to the resolved `FirebaseApp` (`FirebaseAuth.getInstance(app)`) and filtering blank strings.

Both were fixed within allowed `cloud/` files without touching any LOCKED files, and verified with 6 new JVM unit tests + 2 new Android instrumented tests (including unreachable Local Emulator verification). All verification suites passed (`test:build` 4/4, `test:rules` 76/76, JVM 133/133, Android Instrumented 64/64, `assembleDebug` PASS, 22/22 LOCKED SHA-256 MATCH).

---

## 2. Baseline & Source Checkpoint

- **Prior Phase Checkpoint:** `docs/p1-1-b1/b1-real-world-verification-report.md`
  - **B1 Gate (preserved, NOT upgraded to FULL PASS):** `B1 CONDITIONAL PASS`
  - Rules Build 4/4 PASS, RTDB Rules Emulator 76/76 PASS, JVM 127/127 PASS, Android Instrumented 62/62 PASS, `assembleDebug` PASS, LOCKED 22/22 SHA-256 MATCH, MIN-01/02/03/05 RESOLVED, MIN-04 DEFERRED to B3, A1 & A3 PASS on AVD + Local RTDB Emulator, **A2 (`PENDING USER VERIFICATION`)** still requires physical phone + physical ESP32 with WAN disconnected.
- **Git Baseline Before B2 Edits:** Commit `5b1fa63`, clean working tree (`git status --short` empty).
- **Local Toolchain Verified:**
  - Node.js: `v20.20.2`
  - System PATH Java: `26.0.2.1` (bypassed via explicit `JAVA_HOME`)
  - Android Studio JBR: OpenJDK `21.0.4` (`D:\Program Files\Android Studio\jbr`, configured in `.gradle/config.properties` and `cloud-rules/scripts/run-emulator.mjs`)
  - Gradle Wrapper: `8.10.2` (`AGP 8.5.2`, `Kotlin 1.9.24`, `google-services 4.4.2`)
  - Active Android Emulator (ADB): `emulator-5554` (`Pixel_9_Pro_API_34`, Android 14 / API 34)

---

## 3. Configuration Inventory

| Component / Artifact | Location | Verified State (Secrets Redacted) |
|---|---|---|
| Firebase Config File (LOCKED, gitignored) | `app/google-services.json` | Exists locally; gitignored via `.gitignore:20`; 0 commits in git history; `project_id=appaintm`, `project_number=883290202549`, `firebase_url=https://appaintm-default-rtdb.asia-southeast1.firebasedatabase.app`, `package_name=com.example.smarthomeai`, `mobilesdk_app_id=1:883290202549:android:*`, 1 non-empty `api_key` (redacted), 2 `oauth_client` entries (type `1` Android w/ 1 SHA-1 `certificate_hash`, type `3` Web) |
| Generated Firebase Resources (Debug & Release) | `app/build/generated/res/process{Debug,Release}GoogleServices/values/values.xml` | Byte-identical between Debug and Release (`SHA-256 MATCH: True`); defines `project_id=appaintm`, `gcm_defaultSenderId=883290202549`, `firebase_database_url=https://appaintm-default-rtdb.asia-southeast1.firebasedatabase.app`, `google_app_id`, `default_web_client_id` (redacted), `google_api_key` (redacted) |
| Root Gradle Plugins | `build.gradle.kts` | `com.android.application:8.5.2`, `org.jetbrains.kotlin.android:1.9.24`, `com.google.gms.google-services:4.4.2` |
| App Gradle Config | `app/build.gradle.kts` | `namespace="com.example.smarthomeai"`, `applicationId="com.example.smarthomeai"`, conditional `google-services` plugin apply only if `google-services.json` exists; no flavors, no `applicationIdSuffix`; `release.isMinifyEnabled=false` |
| Firebase & Coroutines SDKs (Debug & Release) | `app/build.gradle.kts` (verified via `:app:dependencies`) | `firebase-bom:33.1.2` -> `firebase-auth:23.0.0`, `firebase-database:21.0.0`; `kotlinx-coroutines-{core,android,play-services,test}:1.8.1`; **zero** `firebase-firestore` or `firebase-storage` in `debugRuntimeClasspath` or `releaseRuntimeClasspath` |
| Android Manifest | `app/src/main/AndroidManifest.xml` | `INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`; `allowBackup="false"`, `fullBackupContent="false"`, `usesCleartextTraffic="true"` (required for local HTTP LAN ESP32 and local Emulator `10.0.2.2:9000`) |
| Firebase Initialization & RTDB Provider | `app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt` | Lazy init, retains `applicationContext`, checks `FirebaseApp.getApps(ctx).isNotEmpty()`, reads `app.options.databaseUrl`, validates URL syntax, wraps `FirebaseDatabase.getInstance(url)` in `runCatching`, zero hardcoded production URL, zero `setPersistenceEnabled()` calls |
| Cloud Repository Implementation | `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt` | Implements 3 B1 methods (`observeHomes`, `createHome`, `observeMembers`); strictly RTDB-only; validates UID before all operations (hardened in B2) |
| Firebase Auth Integration (LOCKED) | `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` | Uses default `FirebaseAuth.getInstance()` for Email/Password and Google Sign-In (`default_web_client_id`); untouched (SHA-256 MATCH) |
| Local RTDB Emulator Config | `cloud-rules/firebase.json`, `cloud-rules/scripts/run-emulator.mjs`, `cloud-rules/test/rules.test.mjs` | Zero `.firebaserc` in repo; host `127.0.0.1`, port `9000`, `singleProjectMode: true`; hardcoded `--project demo-smarthomeai --only database` |
| Secret & Ignore Hygiene | `.gitignore`, `git ls-files`, `git log --all` | `app/google-services*.json`, `backup/*`, `*.jks`, `*.keystore`, `.env*`, `wifi_config.h`, `secrets.properties`, `database-debug.log*` all gitignored; 0 secrets in tracked files or commit history |

---

## 4. Audit & Verification Results (B2.1 – B2.4)

### Severity Classification of Findings

| ID | Severity | Area | Finding | Resolution / Disposition |
|---|---|---|---|---|
| **B2-F4** | **MEDIUM** | B2.3 / B2.2 | `RtdbCloudRepository.observeMembers()` omitted `!RtdbPaths.isValidUid(currentUid)` (only checked `== null`), allowing blank/invalid UIDs to attach RTDB listeners; `createHome()` mapped blank/invalid UIDs to `InvalidData` instead of `Unauthenticated`; `FirebaseProvider.defaultUidResolver()` did not bind `FirebaseAuth.getInstance(app)` or filter blank strings. | **RESOLVED in B2** (minimal diff in `RtdbCloudRepository.kt` & `FirebaseProvider.kt` + JVM & Android tests). |
| **B2-F1** | **LOW** | B2.1 | `app/google-services.json` contains 1 Android `certificate_hash` (debug keystore) and `app/build.gradle.kts` has no `signingConfigs.release`. Release Google Sign-In will require adding the Release keystore SHA-1 when Release signing is introduced. Does not affect RTDB or Email/Password Auth. | **Documented (No B2 action; `google-services.json` LOCKED & Production Console out of scope).** |
| **B2-F2** | **INFORMATIONAL** | B2.1 / B2.2 | `cloud-rules/package-lock.json` includes transitive `@firebase/firestore` packages pulled by `@firebase/rules-unit-testing`/`firebase` JS devDependencies in Node test tooling only. Never imported or executed; zero Firestore dependencies exist in Android Gradle classpaths or Kotlin source. | **Verified benign (Android & Cloud Foundation remain 100% RTDB-only).** |
| **B2-F5** | **INFORMATIONAL** | B2.3 | `RtdbCloudRepository.observeHomes()` and `observeMembers()` read `provider.uid` once upon Flow collection and do not subscribe to `FirebaseAuth.AuthStateListener` internally. Future B3 UI/ViewModel wiring must cancel and re-collect active Flows on sign-in/sign-out/account switch. | **Documented for B3 (no B2/B3 redesign).** |

### B2.1 — Firebase Project Configuration Audit: **PASS**
- Project ID (`appaintm`), project number (`883290202549`), Android package (`com.example.smarthomeai`), RTDB region (`asia-southeast1`), and RTDB URL (`https://appaintm-default-rtdb.asia-southeast1.firebasedatabase.app`) are 100% consistent across `app/google-services.json`, `app/build.gradle.kts`, and generated Debug/Release `values.xml`.
- Firebase BoM (`33.1.2`) resolves `firebase-auth:23.0.0` and `firebase-database:21.0.0` identically across Debug and Release; Coroutines pinned at `1.8.1` for Kotlin `1.9.24`.
- Zero Firestore or Cloud Storage SDK dependencies in Android.
- `google-services.json` was neither modified nor printed in full.

### B2.2 — RTDB Configuration Readiness: **PASS**
- **Lazy initialization from valid config:** Verified via `FirebaseProvider` source inspection and `FirebaseConfigurationReadinessTest` (`providerDoesNotEagerlyInstantiateDatabaseOnConstructionOrMetadataRead`).
- **No hardcoded production URLs:** Grep across `app/src/**` and `cloud-rules/**` (excluding gitignored `app/google-services.json`) confirmed zero hardcoded `appaintm` endpoints.
- **No Firestore / no unauthorized persistence:** Zero Firestore imports/dependencies in Android; zero `setPersistenceEnabled(...)` calls anywhere in the codebase.
- **Edge cases verified:**
  1. *Firebase not initialized / FirebaseApp does not exist:* Verified in `FirebaseProviderTest`, `FirebaseConfigurationReadinessTest`, and `CloudInitializationIntegrationTest` -> returns `CloudResult.Unavailable` without crash or DB instantiation.
  2. *RTDB URL missing or malformed:* Verified null/blank, non-HTTP schemes (`ftp`, `ws`, `wss`, `file`, `javascript`), missing host (`https:///only-path`), invalid port (`http://127.0.0.1:abc`), whitespace, and SDK-rejected non-root path -> returns `null` / `CloudResult.Unavailable` cleanly.
  3. *No Internet / Offline:* Verified `db.goOffline()` (`CloudInitializationIntegrationTest`) returns `CloudError.Timeout` cleanly, and `CloudErrorMapper` maps `CODE_DISCONNECTED`/`CODE_UNAVAILABLE`/`CODE_NETWORK_ERROR` & offline `DatabaseException` to `CloudError.Offline`.
  4. *Local Emulator unavailable:* Verified via new `CloudInitializationIntegrationTest.rtdbRepository_whenLocalEmulatorUnavailable_failsCleanlyWithTimeoutAndNeverContactsProduction` against closed port `10.0.2.2:19999` -> returns `CloudError.Timeout` cleanly within `timeoutMs` with zero Production fallback.
  5. *Debug vs Release consistency:* Verified `:app:processDebugGoogleServices` and `:app:processReleaseGoogleServices` produce byte-identical `values.xml` (SHA-256 MATCH), and `debugRuntimeClasspath` vs `releaseRuntimeClasspath` resolve identical Firebase/Coroutines versions.
- **Secret hygiene:** `git ls-files` and `git log --all` confirmed zero committed `google-services.json`, `.firebaserc`, keystores, `.env`, `wifi_config.h`, or backups.

### B2.3 — Firebase Authentication Compatibility: **PASS**
- UID is obtained exclusively from `FirebaseAuth` via `FirebaseProvider` (bound to the resolved `FirebaseApp`, sharing the exact same default `FirebaseAuth` instance as `AuthRepository`).
- No second authentication mechanism created; `AuthRepository.kt` and `LoginScreen.kt` remain untouched (SHA-256 MATCH).
- After resolving `B2-F4`, all 3 `RtdbCloudRepository` methods (`observeHomes`, `createHome`, `observeMembers`) reject `null`, blank, whitespace-padded, or forbidden-character UIDs with `CloudResult.Err(CloudError.Unauthenticated)` before performing any RTDB operation.
- Auth token failures (`CODE_EXPIRED_TOKEN`, `CODE_INVALID_TOKEN`, expired/invalid token `DatabaseException`) map consistently to `CloudError.Unauthenticated`.

### B2.4 — Configuration Tests: **PASS**
- Added 6 targeted JVM unit tests in `FirebaseConfigurationReadinessTest.kt` and 2 targeted Android instrumented tests in `CloudInitializationIntegrationTest.kt` strictly within allowed scope.
- Zero tests simulate or claim Production verification.

---

## 5. Changes Implemented (Bounded Phase C)

1. **`app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt`**
   - Updated `defaultUidResolver(app)` to read `FirebaseAuth.getInstance(app).currentUser?.uid?.takeIf { it.isNotBlank() }` (binds Auth explicitly to the resolved `FirebaseApp` and normalizes blank UIDs to `null`).
2. **`app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`**
   - Aligned `createHome(name)` and `observeMembers(homeId)` UID checks with `observeHomes()` by enforcing `currentUid == null || !RtdbPaths.isValidUid(currentUid)` -> `CloudResult.Err(CloudError.Unauthenticated, "User is not signed in.")` prior to any RTDB operation.
3. **`app/src/test/java/com/example/smarthomeai/cloud/FirebaseConfigurationReadinessTest.kt` (New)**
   - Added 6 JVM unit tests verifying missing `FirebaseApp`, malformed/non-HTTP RTDB URLs, URL trimming, lazy DB instantiation, `DatabaseException` Auth/Offline mapping, and UID contract validation.
4. **`app/src/androidTest/java/com/example/smarthomeai/cloud/CloudInitializationIntegrationTest.kt`**
   - Added `rtdbRepository_withBlankOrInvalidUidOnInitializedApp_returnsUnauthenticatedWithoutTouchingEmulator` verifying all 3 repo operations reject blank/invalid UIDs on an initialized isolated app.
   - Added `rtdbRepository_whenLocalEmulatorUnavailable_failsCleanlyWithTimeoutAndNeverContactsProduction` verifying unreachable local emulator port (`10.0.2.2:19999`) fails cleanly with `Timeout`/`Offline` and never contacts Production.

---

## 6. Files Changed

### Modified (within allowed B2 scope)
- `app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt` (`+3/-1`)
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt` (`+5/-2`)
- `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudInitializationIntegrationTest.kt` (`+71/-0`)

### Added (within allowed B2 scope)
- `app/src/test/java/com/example/smarthomeai/cloud/FirebaseConfigurationReadinessTest.kt`
- `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md`

### Explicitly Untouched
- All 22 LOCKED files (verified by SHA-256 below).
- All `cloud-rules/` files (verified clean and passing without unnecessary edits).
- All `esp32/**` firmware and HTTP LAN protocol files.

---

## 7. Test Commands & Exact Results

All Gradle commands ran with `$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"` (OpenJDK 21.0.4).

| Command | Result | Exact Counts / Evidence |
|---|---|---|
| `.\gradlew.bat :app:processReleaseGoogleServices --console=plain` | **PASS** | `BUILD SUCCESSFUL`; generated Release `values.xml` SHA-256 == Debug `values.xml` SHA-256 (`True`) |
| `.\gradlew.bat :app:dependencies --configuration {debug,release}RuntimeClasspath` | **PASS** | Identical `firebase-bom:33.1.2`, `firebase-auth:23.0.0`, `firebase-database:21.0.0`, `kotlinx-coroutines-*:1.8.1`; 0 Firestore / 0 Storage |
| `cd cloud-rules && npm run test:build` | **PASS** | **4/4** passed, 0 failed, 0 skipped |
| `cd cloud-rules && npm run test:rules` | **PASS** | **76/76** passed, 0 failed, 0 skipped (`--project demo-smarthomeai`, local emulator only) |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks :app:assembleDebug --console=plain` | **PASS** | **133/133** JVM unit tests passed (127 prior + 6 new `FirebaseConfigurationReadinessTest`), 0 failed, 0 errors, 0 skipped; `:app:assembleDebug` `BUILD SUCCESSFUL` |
| `cd cloud-rules && node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` | **PASS** | **64/64** Android instrumented tests passed on `Pixel_9_Pro_API_34(AVD) - 14` (54 `AppSmokeTest` + 10 `CloudInitializationIntegrationTest`), 0 failed, 0 errors, 0 skipped |

---

## 8. SHA-256 Integrity (22/22 LOCKED Files Verified — 0 Mismatches)

| File | SHA-256 (B1 Baseline == Post-B2) | Status |
|---|---|---|
| `app/google-services.json` | `E4D24F478C0EA8F9A860ADC2A03C8AEB3F0A04FA8A97C30CF62A9201B29814BE` | MATCH |
| `app/src/main/java/com/example/smarthomeai/MainActivity.kt` | `170B939136281E51DB0884A5B05B2E41C238784E6A63731D376010EE02287045` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` | `1D9A9FFB8C03262FF60CB2AD60CCA40A4A23206F4338209B54F61C647BF6E70A` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt` | `685422F1F492EB4562765970013E68CCEC39D1EDD9B9336E2E4DEE990E3D6334` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt` | `8C9B9B222DA85A59991D7EB6AFC21ED2A7E457DD02F730C08A2DDEB8B475416A` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/Storage.kt` | `B54E6444463DC928B72C701F3D4FBD24AFE6FF43F2E42B9CC5F6E4EAC143B96A` | MATCH |
| `esp32/SmartHomeESP32/SmartHomeESP32.ino` | `6170D0D1F2DF2B026EF59D1A4756C65B062C619C7B7A275B3A14E019EA3FD139` | MATCH |
| `esp32/SmartHomeESP32/wifi_config.example.h` | `3F9B8F5AF2D0AE00CC93C3D07C95EB7722B6F23A048E8338B372E2D6BE96EA96` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/components/AlarmSettingsCard.kt` | `D23611A45FE3E1116343255329B15A28D32811E5FED322EE370A87D097B47BD1` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/components/Components.kt` | `C1339F5205CAD566CB8C40FABECC405BB8FE919658AD87E8411DCDA60B909A2A` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/components/OwnerConfirmation.kt` | `84425CB3974E924505E10FD60306B980C55308DE81BCCEAB8C9DB03870935CC0` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/components/SecurityCard.kt` | `88302D92C8E80013832D8674DB6101FDE59F70EB6D0F93CB1676E437571CB67F` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/AlertsScreen.kt` | `A37D4012C955C4BC44ED64AD9B28B582BE7E355186EC97BDF6845F99F6285C96` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/AutomationScreen.kt` | `6DA8B37DD0F294D4898947A2D4AA3A847E68526600FA3EB6BAAD010045FDC57B` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/CameraScreens.kt` | `C29652CB2005637484668F6418B23FB34EE45ED4FC04C04ECCBE60015B86CD03` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/EnvironmentScreen.kt` | `4DBCFB50F586D4390245D6C26032BFBC5716E5D20CF0441D4A0DC0FCC77FBD7F` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt` | `3D5AED7F79797540A9BE42071E889FAE26935C317AF58339C6AEB8D67C261EAD` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/LoginScreen.kt` | `061023920D1F1097B642F705C53540BBF1C111A3FED0A4AABD115156FC7F94D4` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/SensorChart.kt` | `419646799873A0D2832EC02758C8C55DCD299A5F3654CF849BF5418DD87B1300` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt` | `41DE5198A1E7F1CCD347AAE2B0283F0EF10190669F7A92B8977F649355D0DB5D` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt` | `9E7C3F089F250D16F136C1D21FA262DADC87716B39FD6B947134114850427277` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/theme/Theme.kt` | `F8450DD0D5510C7FCD18C1E81BD4A1654EDA682BEAB0C08B52EF9D43FA9E7EE5` | MATCH |

---

## 9. Production Isolation Evidence

- **Zero Production Console / CLI access:** Firebase CLI reported `You are not currently authenticated`; zero `firebase login`, zero `firebase database:*`, and zero `firebase deploy` commands were executed.
- **Demo-only Emulator Enforcement:**
  - Zero `.firebaserc` files exist anywhere in the repository.
  - `cloud-rules/firebase.json` binds only `127.0.0.1:9000` with `"singleProjectMode": true`.
  - `cloud-rules/scripts/run-emulator.mjs` hardcodes `--project demo-smarthomeai --only database`; emulator stdout explicitly confirmed: `Detected demo project ID "demo-smarthomeai", emulated services will use a demo configuration and attempts to access non-emulated services for this project will fail.`
  - `CloudInitializationIntegrationTest.kt` uses dedicated secondary `FirebaseApp` instances (`b1-isolated-offline`, `b1-isolated-online`, `b2-isolated-unreachable`) with fake API key and `db.useEmulator("10.0.2.2", ...)` — never touching `[DEFAULT]` `FirebaseApp` or `appaintm`.
- **B1-D remains LOCKED.**

---

## 10. Remaining Risks

1. **B1 A2 Hardware Regression Pending (`PENDING USER VERIFICATION`):** Real-world LAN offline regression on a physical Android phone + physical ESP32 with WAN disconnected remains unverified; therefore B1 stays `B1 CONDITIONAL PASS`.
2. **Production RTDB Rules & Data Unaudited (B1-D Locked):** Production `appaintm` RTDB rules and existing data have not been inspected or backed up yet (explicitly prohibited in B2); requires separate user-approved B1-D Production Audit before any Production RTDB traffic or deployment.
3. **Auth State Transitions During Active Observers (B2-F5 — B3 Integration Scope):** `RtdbCloudRepository` evaluates `provider.uid` at Flow collection time; B3 wiring must cancel/re-collect active Flows on sign-in/sign-out/account switch.
4. **Release Signing / Google Sign-In SHA-1 (B2-F1 — Future Release Scope):** Only 1 SHA-1 `certificate_hash` is registered in `app/google-services.json`; Release signing setup in a future release phase will require registering the Release SHA-1 in Firebase Console for Google Sign-In.
5. **MIN-04 Deferred (from B1):** Sequential `fetchHomeMeta` in `observeHomes()` remains deferred to B3.

---

## 11. Rollback Instructions

All B2 changes are isolated to 3 tracked files (plus 2 new untracked files) with zero schema, data, Gradle, or Production impact. To revert completely back to the B1 checkpoint (`5b1fa63`):

```powershell
Set-Location "d:\MMO\DoAn2\app-nha-thong-minh"
git checkout -- app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt `
  app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt `
  app/src/androidTest/java/com/example/smarthomeai/cloud/CloudInitializationIntegrationTest.kt
Remove-Item -Force app/src/test/java/com/example/smarthomeai/cloud/FirebaseConfigurationReadinessTest.kt
Remove-Item -Force docs/p1-1-b2/b2-firebase-configuration-readiness-report.md
```

---

## 12. Final Gate

**`B2 PASS`**

- **B2 Acceptance Criteria Met (9/9):** Firebase configuration audited; project/package/RTDB/Debug-Release consistency verified; FirebaseAuth–RTDB compatibility verified and hardened (`B2-F4` resolved); Local Emulator isolation verified; 22/22 LOCKED files intact (SHA-256 MATCH); all tests PASS (`test:build` 4/4, `test:rules` 76/76, JVM 133/133, Android Instrumented 64/64); Debug build PASS; zero Production operations; full report & evidence documented.
- **Unchanged Prerequisite Gates:** B1 remains **`B1 CONDITIONAL PASS`** (pending A2 physical phone + ESP32 LAN Offline Regression); **B1-D remains LOCKED**.
