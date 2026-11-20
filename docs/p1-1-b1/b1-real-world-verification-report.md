# P1.1-B1 — Real-World Verification & Minor Cleanup Report

- **Timestamp (Local):** 2026-11-21 00:20:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Architecture Baseline:** P1.1-B0 RTDB-only rev.2 & P1.1-B1 RTDB Cloud Foundation Plan
- **Final Gate:** **`B1 CONDITIONAL PASS`** *(A1, A3, Minor Cleanup MIN-01/02/03/05, Rules, JVM, Android Instrumented, Build & SHA-256 Integrity PASS; A2 physical phone + ESP32 LAN Offline Regression is `PENDING USER VERIFICATION`)*

---

## A. Execution Summary

All Minor Cleanup findings in scope (`MIN-01`, `MIN-02`, `MIN-03`, `MIN-05`) were implemented with minimal, surgical diffs while preserving the existing architecture and 100% of LOCKED files. `MIN-04` was intentionally deferred to B3 as instructed.

Real-world Android verification was executed on the active Android Emulator (`emulator-5554`, AVD `Pixel_9_Pro_API_34`, Android 14 / API 34) for **A1 (App Smoke Test)** and **A3 (Cloud Initialization Integration)** using an isolated local RTDB Emulator (`--project demo-smarthomeai`, `127.0.0.1:9000` / `10.0.2.2:9000`). **A2 (LAN Offline Regression)** requires physical Android hardware and physical ESP32 hardware on the same LAN with WAN disconnected; because no physical Android phone or physical ESP32 is attached to this environment, A2 was not simulated and remains explicitly **`PENDING USER VERIFICATION`**.

| Verification Area | Status | Evidence |
|---|---|---|
| A1. Android App Smoke Test | **PASS (AVD)** | Debug APK installed, cold start 3267 ms, Firebase init OK, live tab nav OK, 0 crashes in `logcat`, 54/54 `AppSmokeTest` PASS |
| A2. LAN Offline Regression (Physical HW) | **PENDING USER VERIFICATION** | No physical phone (`adb devices -l` shows only `emulator-5554`) and no physical ESP32 with WAN disconnected |
| A3. Cloud Initialization Integration | **PASS (AVD + Local RTDB Emulator)** | 8/8 `CloudInitializationIntegrationTest` PASS (0 skipped) |
| B. Minor Cleanup (MIN-01/02/03/05) | **PASS** | Rules 76/76 PASS, Build Rules 4/4 PASS, JVM 127/127 PASS, `assembleDebug` PASS |
| Locked Files SHA-256 Integrity | **PASS** | 22/22 LOCKED files unchanged (0 mismatches) |
| Production Isolation (B1-D Locked) | **PASS** | Zero access to `appaintm` or Firebase Console |

---

## B. Real-World Verification

### Device / AVD Environment Used
- **ADB Target:** `emulator-5554`
- **AVD Name:** `Pixel_9_Pro_API_34` (`sdk_gphone64_x86_64`)
- **Android Version:** Android 14 (API 34)
- **Physical Phone / Physical ESP32 Attached:** None

### 1. A1 — Android App Smoke Test (`PASS on AVD`)
- **Executed At:** 2026-11-21 00:13:13 – 00:13:58 +07:00
- **APK Installed:** `app/build/outputs/apk/debug/app-debug.apk` (`adb -s emulator-5554 install -r` -> `Success`)
- **App Startup:** Cold launch `com.example.smarthomeai/.MainActivity` completed (`Status: ok`, `LaunchState: COLD`, `TotalTime: 3267 ms`, `PID: 6752`, `topResumedActivity=com.example.smarthomeai/.MainActivity`).
- **Firebase Initialization (`logcat`):**
  - `FirebaseApp: Device unlocked: initializing all Firebase APIs for app [DEFAULT]`
  - `FirebaseInitProvider: FirebaseApp initialization successful`
  - Zero `FirebaseDatabase` calls or production RTDB connections at startup.
- **Auth State & Home Rendering:** Existing signed-in session rendered `HomeScreen` cleanly (`Nhà của tôi`, `ESP32 mất kết nối`, `Cửa chính`, `Đèn phòng khách`, `Giàn phơi`, `Còi cảnh báo`, bottom navigation `Trang chủ`, `Khuôn mặt`, `Nhật ký`, `Cài đặt`).
- **Live Screen Navigation:** Tapped `Nhật ký` -> `Cài đặt` -> `Khuôn mặt` -> `Trang chủ`; process remained alive (`PID: 6752`) with **0 crashes / 0 uncaught exceptions / 0 `AndroidRuntime` errors** in `logcat`.
- **Automated UI Smoke Suite (`AppSmokeTest`):** **54/54 PASS (0 failed, 0 skipped)** covering Login, Home, Devices, Door, Security Mode, Alarm Settings, Clothesline, Sensors/Environment, Alerts/Event Journal, Automation, Settings, System Readiness, Owner Confirmation, and TFLite `FaceEngine`.

### 2. A2 — LAN Offline Regression (`PENDING USER VERIFICATION`)
- **Status:** `PENDING USER VERIFICATION` (not executed; not simulated).
- **Reason:** Requires physical phone + physical ESP32 hardware on the same LAN with Internet/WAN disconnected. Only `emulator-5554` is present.
- **Required Manual Checklist Before Upgrading to Full `B1 PASS`:**
  1. Connect physical phone (Debug APK installed) and ESP32 to the same LAN, disconnect router WAN/Internet while keeping LAN active.
  2. Verify device control (light/door/alarm) over HTTP LAN.
  3. Verify live sensor readings (temperature/humidity/rain/obstacle/distance) over HTTP LAN.
  4. Verify Security Mode transitions over HTTP LAN.
  5. Verify Clothesline control & rain automation over HTTP LAN.
  6. Verify Event Journal retrieval over HTTP LAN.
  7. Record timestamp, actions, results, and `logcat` confirming zero RTDB/Internet dependency.

### 3. A3 — Cloud Initialization Integration (`PASS on AVD + Local RTDB Emulator`)
- **Test Class:** `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudInitializationIntegrationTest.kt`
- **Execution Command:** `node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"`
- **Result:** **8/8 PASS (0 failed, 0 skipped)** on `Pixel_9_Pro_API_34 (API 34)` with local RTDB Emulator (`--project demo-smarthomeai`, `10.0.2.2:9000`):
  1. `firebaseProvider_retainsApplicationContextNotWrapperOrActivityContext`: Verified `FirebaseProvider` stores `applicationContext` instead of wrapper/Activity context.
  2. `firebaseProvider_whenFirebaseNotInitialized_returnsUnavailable`: Verified `observeHomes`, `createHome`, `observeMembers` return `CloudResult.Unavailable` without crash when Firebase is uninitialized.
  3. `firebaseProvider_withInvalidSdkUrl_catchesDatabaseExceptionAndReturnsUnavailable`: Verified real `FirebaseDatabase.getInstance(app, invalidUrl)` `DatabaseException` is caught via `runCatching` and returns `CloudResult.Unavailable`.
  4. `rtdbRepository_withInitializedIsolatedAppAndNoAuth_returnsUnauthenticated`: Verified initialized isolated `FirebaseApp` without signed-in UID returns `CloudError.Unauthenticated`.
  5. `rtdbRepository_whenOffline_createHomeReturnsTimeoutCleanly`: Verified offline database (`db.goOffline()`) hits `timeoutMs` and returns `CloudError.Timeout`.
  6. `rtdbRepository_whenCoroutineCancelled_propagatesCancellationException`: Verified coroutine cancellation propagates `CancellationException` and is never swallowed into `CloudResult.Err`.
  7. `rtdbRepository_whenCollectorsStop_detachesListenersCleanly`: Verified cancelling collectors of `observeHomes()` and `observeMembers()` exits cleanly via `awaitClose` without hang/leak.
  8. `rtdbRepository_againstLocalEmulator_mapsPermissionDeniedWhenUnauthTokenHitsRules`: Verified live Android SDK round-trip against local `demo-smarthomeai` RTDB Emulator (`10.0.2.2:9000`) maps server rule rejection to `CloudError.PermissionDenied`.

---

## C. Minor Findings Resolution

- **MIN-01 (`userHomes` owner membership consistency) — RESOLVED:**
  - Updated `userHomes/$uid/$homeId/.write` in `cloud-rules/database.rules.src.json` to additionally require `root.child('homes').child($homeId).child('members').child($uid).child('role').val() === 'owner'` while keeping `!data.exists()`, `newData.val() === 'owner'`, `auth.uid === $uid`, and `@OWNER`.
  - Rebuilt `cloud-rules/database.rules.json` via `build-rules.mjs` (zero unexpanded `@` tokens).
  - Added Emulator test `#11b [MIN-01]` verifying that creating `meta` and skipping `members/uA` causes `userHomes/uA/$homeId` write to DENY, while existing `#11` continues to PASS.
- **MIN-02 (Safe `DataSnapshot` parsing) — RESOLVED:**
  - Removed all direct `getValue(String::class.java)` and `getValue(Long::class.java)` calls in `RtdbCloudRepository.kt`.
  - Added safe `snapshot.value` casting (`safeString`, `safeLong` accepting only finite `Number` -> `Long`, `parseHomeMetaValues`, `parseMemberValues`).
  - Guarded callback thread `onDataChange` handlers with `try/catch` that rethrows `CancellationException` and converts `DatabaseException`/unexpected errors to `CloudResult.Err(CloudError.InvalidData, ...)` or `HomeMetaFetchResult.Failed(CloudError.InvalidData, ...)` without crashing the callback thread.
  - Added 8 JVM unit tests in `RtdbSnapshotParsingTest.kt` covering `meta/name` as `Map`, `meta/ownerUid` wrong types (`Long`/`Map`/`List`/`Boolean`/blank/`null`), `members/addedAt` as `String` (safely yields `null`), `members/role` as `Map` (element skipped), `members/addedBy` as `Map`, and `safeLong` non-finite/non-Number handling.
- **MIN-03 (`FirebaseProvider` hardening) — RESOLVED:**
  - Stored `context.applicationContext ?: context` (`appContext`) instead of retaining Activity context.
  - Added URL syntax validation (`isValidDatabaseUrl`) and wrapped `FirebaseDatabase.getInstance(url)` in `runCatching` so missing/invalid URLs return `null` -> `CloudResult.Unavailable` without crashing.
  - Confirmed zero hardcoded production URLs and zero `setPersistenceEnabled()` calls.
  - Added 5 JVM unit tests in `FirebaseProviderTest.kt` plus Android instrumented tests in `CloudInitializationIntegrationTest.kt`.
- **MIN-04 (Parallel `fetchHomeMeta`) — DEFERRED:**
  - Intentionally not implemented in B1. Deferred to B3 when multi-Home scale or realtime metadata warrants optimization. No per-home listeners added.
- **MIN-05 (Emulator test assertions & documentation) — RESOLVED:**
  - Added `#49c [MIN-05]` asserting `assertFails` for Owner deleting parent `telemetry` and parent `events` container nodes.
  - Added `-10s` (`minus10s`) timestamp rejection check for `members/uB2/addedAt` in `#54`.
  - Added explanatory comment in `#47b` noting `% 1 === 0` is defense-in-depth because the `$eventId` regex `/^(g|d)_[A-Za-z0-9]{1,16}_[0-9]{1,10}$/` already disallows decimal points.

---

## D. Files Changed

### Modified (within allowed scope)
- `cloud-rules/database.rules.src.json`
- `cloud-rules/database.rules.json`
- `cloud-rules/test/rules.test.mjs`
- `app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`
- `docs/p1-1-b1/rules-test-report.txt`

### Added (within allowed scope)
- `app/src/test/java/com/example/smarthomeai/cloud/RtdbSnapshotParsingTest.kt`
- `app/src/test/java/com/example/smarthomeai/cloud/FirebaseProviderTest.kt`
- `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudInitializationIntegrationTest.kt`
- `docs/p1-1-b1/b1-real-world-verification-report.md`

---

## E. Tests & Build Results

| Command | Result | Exact Counts |
|---|---|---|
| `npm run test:build` | **PASS** | **4/4** passed, 0 failed, 0 skipped |
| `npm run test:rules` | **PASS** | **76/76** passed (74 prior + `#11b` + `#49c`), 0 failed, 0 skipped |
| `.\gradlew.bat testDebugUnitTest --rerun-tasks --console=plain` | **PASS** | **127/127** passed (88 legacy + 26 prior B1 + 13 new MIN-02/MIN-03), 0 failed, 0 skipped |
| `.\gradlew.bat assembleDebug --console=plain` | **PASS** | `BUILD SUCCESSFUL` |
| `node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` | **PASS** | **62/62** passed (54 `AppSmokeTest` + 8 `CloudInitializationIntegrationTest`), 0 failed, 0 skipped |

---

## F. SHA-256 Integrity (22/22 LOCKED Files Verified — 0 Mismatches)

| File | SHA-256 (Baseline == Post-Cleanup) | Status |
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

## G. Production Isolation

- Zero Production Audit, zero Production Rules read/write/deploy, zero Firebase Console modifications.
- All emulator executions used `--project demo-smarthomeai` strictly on loopback (`127.0.0.1:9000` / `10.0.2.2:9000`).
- Grep verification across `app/src/**/cloud/**` and `cloud-rules/**` confirmed zero hardcoded `appaintm` URLs and zero `setPersistenceEnabled()` calls.
- **B1-D remains LOCKED.**

---

## H. Remaining Risks

1. **A2 Hardware Verification Pending:** Real-world offline LAN regression on physical phone + ESP32 with WAN disconnected has not yet been executed by the user.
2. **MIN-04 Deferred:** Sequential `fetchHomeMeta` in `RtdbCloudRepository.observeHomes()` scales linearly with home count; deferred to B3 per spec.
3. **Production RTDB State Unknown (B1-D):** Existing `appaintm` RTDB rules/data remain unaudited and untouched; B1-D deployment stays locked until explicit user audit & written approval.

---

## I. Final Gate

**`B1 CONDITIONAL PASS`**

- **Passed:** Rules (76/76), Rules Build (4/4), JVM Unit Tests (127/127), Debug APK Build (`assembleDebug`), AVD App Smoke Test A1 + `AppSmokeTest` (54/54), Cloud Initialization Integration A3 (8/8 with live local RTDB Emulator), Minor Cleanup (`MIN-01`, `MIN-02`, `MIN-03`, `MIN-05`; `MIN-04` deferred), SHA-256 LOCKED Integrity (22/22), Production Isolation.
- **Pending Condition for Full `B1 PASS`:** User execution and sign-off of **A2 (LAN Offline Regression on physical Android phone + ESP32 hardware with WAN disconnected)**.
