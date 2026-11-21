# P1.1-B3 — Cloud Session, Multi-Home & Member Integration Report

- **Timestamp (Local):** 2026-11-21 07:45:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Git Baseline Checkpoint:** `d63eb07` (`checkpoint task audit b3`)
- **Architecture Baseline:** `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (RTDB-only rev.2), `docs/P1.1-B1_RTDB_Cloud_Foundation_Plan.md`, `docs/p1-1-b1/b1-real-world-verification-report.md` (`B1 CONDITIONAL PASS`), `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md` (`B2 PASS`)
- **Final Gate:** **`B3 PASS`** *(All B3.1–B3.4, B2-F5, MIN-04, Rules Emulator, JVM, Android Instrumented, Debug Build, LOCKED SHA-256 Integrity & Production Isolation criteria verified; prerequisite gates preserved: B1 remains `CONDITIONAL PASS` pending A2 physical hardware verification; B1-D Production Audit/Deploy remains LOCKED)*

---

## 1. Executive Summary

Phase **P1.1-B3** integrated the P1.1-B1/B2 RTDB-only Cloud Foundation into the Android application layer with full Auth session lifecycle management (**B2-F5**), bounded parallel multi-home metadata fetching (**MIN-04**), Multi-Home state & switching, role-aware Member observation & atomic multi-path Leave/Remove operations, and non-blocking Cloud UI cards — while keeping **zero** Firebase SDK imports in `SmartHomeViewModel`, **zero** changes to `MainActivity.kt` or any `VERIFIED-HW` / LOCKED file, and **zero** Production Firebase operations.

Key outcomes:
- **B3.1 & B2-F5 Resolved:** Introduced pure-Kotlin `CloudSession` / `CloudSessionState` and `CloudHomeManager` backed by `FirebaseCloudSession` (`FirebaseAuth.AuthStateListener` inside `cloud/`). Sign-out and account switch deterministically `cancelAndJoin()` old `observeHomes()` / `observeMembers()` / mutation jobs and clear Cloud UI state immediately, guarded by a monotonic `sessionGeneration` token and repository-level UID verification.
- **B3.2 & MIN-04 Resolved:** Replaced sequential `fetchHomeMeta()` loops in `RtdbCloudRepository.observeHomes()` with pure-Kotlin `RtdbHomeMetaFetcher.fetchAllBounded()` (`Semaphore(4)` bounded concurrency + single aggregate `withTimeoutOrNull(timeoutMs)`) and `RtdbHomeMerger.mergeHomesDetailed()` so one failing/revoked home never drops valid homes and total latency never scales linearly with home count.
- **B3.3 Resolved:** Extended `CloudRepository` & `RtdbCloudRepository` with `leaveHome(homeId)` and `removeMember(homeId, memberUid)` using root-level multi-path atomic deletes (`homes/{homeId}/members/{uid} = null`, `userHomes/{uid}/{homeId} = null`) validated against existing RTDB Rules (zero Rules edits needed). Invitation / Join / QR was confirmed to have no B0/Rules schema contract and is explicitly deferred to a dedicated design phase.
- **B3.4 Resolved:** Added `CloudHomeSection.kt` (`CloudHomeBannerCard`, `CloudHomeManagementCard`) wired into `HomeScreen.kt` and `SettingsScreen.kt` via optional `CloudUiBridge` without touching `MainActivity.kt`. Clearly distinguishes all 7 required UI states and never hides or disables LAN ESP32 controls when Cloud is offline, unavailable, or revoked.
- **Verification:** `test:build` **4/4 PASS**, `test:rules` **80/80 PASS** (`--project demo-smarthomeai`), JVM Unit Tests **151/151 PASS**, `assembleDebug` **BUILD SUCCESSFUL**, Android Instrumented Tests **69/69 PASS** on `Pixel_9_Pro_API_34 (AVD) - 14` with Local RTDB Emulator, **19/19 LOCKED files SHA-256 MATCH**.

---

## 2. Source Baseline

- **Git Commit Before B3 Edits:** `d63eb07` (`checkpoint task audit b3`), clean working tree.
- **Prerequisite Phase Status (Unchanged):**
  - **B1:** `CONDITIONAL PASS` (`A2` physical phone + ESP32 LAN Offline Hardware Test remains `PENDING USER VERIFICATION`).
  - **B2:** `PASS`.
  - **B1-D (Production Audit/Deploy):** `LOCKED`.
- **Toolchain Verified:**
  - Node.js `v20.20.2`, Android Studio JBR OpenJDK `21.0.4` (`D:\Program Files\Android Studio\jbr`), Gradle `8.10.2`, AGP `8.5.2`, Kotlin `1.9.24`, `firebase-bom:33.1.2`, Coroutines `1.8.1`.
  - Connected Android Emulator: `emulator-5554` (`Pixel_9_Pro_API_34`, Android 14 / API 34).

---

## 3. Architecture Changes

Layer boundary preserved strictly:
```text
UI (HomeScreen, SettingsScreen, CloudHomeSection)
  └─► CloudUiBridge (pure Kotlin StateFlow<CloudUiState> + actions)
        └─► SmartHomeViewModel (0 Firebase SDK imports)
              └─► CloudHomeManager (pure Kotlin session/home/member coordinator)
                    ├─► CloudSession (pure Kotlin) ◄── FirebaseCloudSession (cloud/)
                    └─► CloudRepository (pure Kotlin) ◄── RtdbCloudRepository (cloud/)

LAN Control (100% UNCHANGED):
UI ─► SmartHomeViewModel ─► Esp32Client ─► HTTP LAN ─► ESP32
```

Key architectural decisions:
1. **Zero Firebase SDK in ViewModel:** `CloudFoundationFactory.create(context)` encapsulates `FirebaseProvider`, `FirebaseCloudSession`, and `RtdbCloudRepository` inside `com.example.smarthomeai.cloud`, returning pure-Kotlin `CloudComponents`.
2. **Zero Edits to `MainActivity.kt` & `Storage.kt`:** `CloudUiState` is kept completely separate from `SmartHomeState` (so Cloud state is never persisted into local SharedPreferences and cannot leak across accounts). `CloudUiBridgeRegistry` (`WeakReference<CloudUiBridge>`, registered in `SmartHomeViewModel.init` and unregistered in `onCleared()`) connects `HomeScreen`/`SettingsScreen` to the active ViewModel without modifying `MainActivity.kt` and without instantiating `SmartHomeViewModel` during isolated Compose smoke tests.
3. **RTDB-Only & Zero Rules Modifications:** Existing `cloud-rules/database.rules.src.json` and `cloud-rules/database.rules.json` already enforce every required multi-path Leave/Remove invariant; no Rules modifications were needed or made.

---

## 4. Auth Lifecycle (B3.1)

- **`CloudSession.kt` (New, pure Kotlin):** Defines `CloudSessionState` (`Unavailable`, `SignedOut`, `SignedIn(uid)`) and `CloudSession.observeSession(): Flow<CloudSessionState>`.
- **`FirebaseProvider.kt` & `FirebaseCloudSession`:**
  - Subscribes to `FirebaseAuth.getInstance(app).addAuthStateListener(listener)` inside `callbackFlow` and removes the listener cleanly in `awaitClose`.
  - When `!provider.isConfigured`, emits `CloudSessionState.Unavailable` without touching `FirebaseAuth` or `FirebaseDatabase` (preserving lazy initialization).
  - Validates UID with `RtdbPaths.isValidUid(rawUid)` before emitting `SignedIn(uid)`; blank/invalid/null UIDs normalize to `SignedOut`.
- **`CloudHomeManager.kt`:**
  - Serializes session transitions with `lifecycleMutex` + monotonic `sessionGen`.
  - On Sign-in: clears previous state, sets `CloudStatus.LOADING`, starts `observeHomes()` for the new UID.
  - On Sign-out (both reactive `SignedOut` and synchronous `onSignedOut()` invoked from `SmartHomeViewModel.logout()`): cancels & joins active `membersJob`, `homesJob`, `memberSwitchJob`, and `activeMutationJob`, clears `homes`, `selectedHomeId`, `members`, and notices immediately.
  - On Account Switch (`uA -> uB`): `cancelAndJoin()` completes cancellation of `uA`'s flows before launching `uB`'s flows; generation check prevents any late `uA` emission or mutation from overwriting `uB` state.
  - `CancellationException` is always rethrown (never converted into `CloudError`).
  - `AuthRepository.kt` remains untouched (LOCKED).

---

## 5. Multi-Home (B3.2)

- **State Machine (`CloudUiState`):** Explicitly models `CloudStatus` (`UNAVAILABLE`, `SIGNED_OUT`, `LOADING`, `EMPTY`, `READY`, `ACCESS_REVOKED`, `ERROR`) and `MembersStatus` (`IDLE`, `LOADING`, `READY`, `ACCESS_REVOKED`, `ERROR`).
- **Home Creation:** Validates & trims name via `RtdbPayloadBuilder.validateAndNormalizeHomeName()` (1–60 chars, non-blank, rejects `deviceKey`), writes sequentially (`meta -> members -> userHomes`), and selects the newly created home automatically.
- **Home Selection & Switching:** Switching active home via `selectHome(homeId)` cancels & joins the prior home's `observeMembers` flow before collecting the new home's members, preventing cross-home member mixing.
- **Revoked / Deleted Home Handling:**
  - When `observeHomes()` drops the previously selected home (not initiated by self-leave), `CloudHomeManager` switches to another valid home (or `ACCESS_REVOKED` if none remain) with an explicit `revokedNotice`.
  - When `observeMembers(homeId)` receives `CloudError.PermissionDenied` (e.g., stale `userHomes` entry after membership revocation), `homeId` is recorded in `revokedHomeIds`, immediately removed from displayed `homes`, and never shown as a valid home.

---

## 6. Member Management (B3.3)

- **Member Observation:** `observeMembers(homeId)` displays UID, `CloudRole` (`OWNER` / `MEMBER`), `addedBy`, and `addedAt` sorted deterministically (OWNER first, then `addedAt`, then `uid`).
- **Role-Based Permissions:**
  - `CloudUiState.canLeaveSelectedHome` is `true` strictly for `MEMBER` on a valid selected home (OWNER cannot leave).
  - `CloudUiState.canRemoveMember(member)` is `true` strictly when caller is `OWNER`, target is `MEMBER`, and `member.uid != currentUid`.
- **Atomic Multi-Path Delete:**
  - `RtdbPayloadBuilder.buildRemoveMembershipUpdates(homeId, memberUid)` validates IDs and produces the exact 2-path root update map:
    - `homes/{homeId}/members/{memberUid} -> null`
    - `userHomes/{memberUid}/{homeId} -> null`
  - Both `RtdbCloudRepository.leaveHome(homeId)` and `RtdbCloudRepository.removeMember(homeId, memberUid)` execute a single atomic `db.reference.updateChildren(updates).await()` under `runTimedWrite`.
  - `removeMember` also rejects `memberUid == currentUid` locally with `CloudError.PermissionDenied` before network access, and RTDB Rules reject any unauthorized call (`OWNER` self-leave, `MEMBER` removing others, role escalation).
- **Invitation / Join / QR Disposition:** Confirmed against B0 rev.2 and `database.rules.src.json` that no Invitation / Join / QR schema or contract exists. Per §8, no schema or Rules were invented; Invitation / Join / QR is explicitly deferred for separate contract design. `deviceKey` is never written to RTDB.

---

## 7. MIN-04 Resolution

- **Previous Bottleneck:** `RtdbCloudRepository.observeHomes()` fetched each home's `meta` sequentially inside a `for` loop, each with its own independent `withTimeout(timeoutMs)`, causing linear `N * timeoutMs` worst-case latency.
- **B3 Implementation (`RtdbHomeMetaFetcher.fetchAllBounded` & `RtdbHomeMerger.mergeHomesDetailed`):**
  1. **Bounded Concurrency:** Uses `Semaphore(MAX_CONCURRENT_META_FETCHES)` (default `4`, configurable) with `async`/`awaitAll` over deduplicated `homeIds` — prevents firing hundreds of simultaneous RTDB reads.
  2. **Aggregate Timeout:** Wraps the entire batch in a single `withTimeoutOrNull(aggregateTimeoutMs)` (`10_000L` default) and removes per-item independent `withTimeout` inside `fetchHomeMeta()`. Total wait is strictly capped at `aggregateTimeoutMs`.
  3. **Collector Cancellation:** Cancelling the collector cancels all in-flight `async` tasks immediately (triggering `invokeOnCancellation { ref.removeEventListener(listener) }`) and propagates `CancellationException`.
  4. **Per-Home Isolation & Partial Preservation:**
     - `PermissionDenied` and `NotFound` are isolated and skipped without breaking valid homes.
     - Completed valid homes are preserved in `ConcurrentHashMap` even if another home fails (`InvalidData`) or times out under the aggregate budget (`mergeHomesDetailed().toCloudResult()`).
     - `Unauthenticated` always surfaces as `CloudResult.Err(CloudError.Unauthenticated)`. When 0 valid homes resolve and failures occurred, exact classified error (`Offline`, `Timeout`, `InvalidData`, `Unknown`) surfaces as `CloudResult.Err`.
  5. **Design Simplicity:** Retains one-shot `addListenerForSingleValueEvent` metadata fetches; no per-home persistent listeners added.

---

## 8. B2-F5 Resolution

- **Previous Gap:** `RtdbCloudRepository.observeHomes()` and `observeMembers()` read `provider.uid` once upon Flow collection; without Auth-state-driven cancellation, old flows could stay attached under the prior UID after sign-out or account switch.
- **B3 Resolution:**
  1. `FirebaseCloudSession` observes `FirebaseAuth.AuthStateListener` and emits normalized `CloudSessionState` transitions.
  2. `CloudHomeManager` cancels and awaits (`cancelAndJoin()`) old `homesJob`, `membersJob`, `memberSwitchJob`, and `activeMutationJob` on every UID transition before collecting the new UID's flows.
  3. `SmartHomeViewModel.logout()` also invokes `cloudManager.onSignedOut()` synchronously before `auth.signOut()`, eliminating any window of stale UI data.
  4. `RtdbCloudRepository.observeHomes()` additionally re-checks `provider.uid == currentUid` after async metadata fetch before emitting (defense-in-depth).

---

## 9. UI Changes

- **`app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt` (New):**
  - `CloudHomeBannerCard` (integrated on `HomeScreen`): displays active home name, role/status pill, progress bar while loading/busy, home switcher dialog, create-home dialog (with input validation blocking blank, >60 chars, and `deviceKey`), retry action, feedback banners, and quick link to member management.
  - `CloudHomeManagementCard` (integrated on `SettingsScreen`): displays full home list with selection state, member list (UID, role, `addedBy`, `addedAt`), OWNER-only `Remove Member` action with confirmation `AlertDialog`, MEMBER-only `Leave Home` action with confirmation `AlertDialog`, and explicit note that Invitation/Join/QR is reserved for a separate design phase.
  - Explicitly distinguishes all 7 states: Cloud unavailable (`UNAVAILABLE`), signed out (`SIGNED_OUT`), no home (`EMPTY`), loading (`LOADING`), access revoked (`ACCESS_REVOKED`), connection error (`ERROR` with `Offline`/`Timeout`), and Rules denial (`PermissionDenied` on load or action).
  - Follows existing Material 3 theme (`Theme.kt`), uses Material Icons only (zero emojis), ≥44–48dp touch targets, and non-truncating wrapped text.
- **`HomeScreen.kt` & `SettingsScreen.kt` (Minimal Hooks):**
  - Added single optional parameter `cloudBridge: CloudUiBridge? = rememberActiveCloudBridge()` preserving 100% source/binary compatibility with `MainActivity.kt` and `AppSmokeTest.kt`.

---

## 10. Files Changed

### Added
- `app/src/main/java/com/example/smarthomeai/cloud/CloudSession.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`
- `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`
- `app/src/test/java/com/example/smarthomeai/cloud/CloudSessionAndMultiHomeTest.kt`
- `app/src/test/java/com/example/smarthomeai/cloud/RtdbHomeMetaFetcherTest.kt`
- `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudB3IntegrationTest.kt`
- `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md`

### Modified (Within Approved B3 Scope)
- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt` (`+3/-1`)
- `app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt` (`+77/-3`)
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbPayloadBuilder.kt` (`+13/-0`)
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbHomeMerger.kt` (`+155/-16`)
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt` (`+104/-47`)
- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt` (`+28/-2`)
- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt` (`+8/-1`)
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt` (`+6/-1`)
- `app/src/test/java/com/example/smarthomeai/cloud/FakeCloudRepository.kt` (`+154/-18`)
- `app/src/test/java/com/example/smarthomeai/cloud/RtdbPayloadBuilderTest.kt` (`+33/-0`)
- `cloud-rules/test/rules.test.mjs` (`+106/-0` — 4 new B3 emulator tests `#18b`, `#20c`, `#21b`, `#22b`)

---

## 11. Test Results

All Gradle commands ran with `$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"` (OpenJDK 21.0.4). All Emulator tests ran strictly against `--project demo-smarthomeai` on loopback (`127.0.0.1:9000` / `10.0.2.2:9000`).

| Suite / Command | Result | Exact Counts |
|---|---|---|
| `cd cloud-rules && npm run test:build` | **PASS** | **4/4** passed, 0 failed, 0 skipped |
| `cd cloud-rules && npm run test:rules` | **PASS** | **80/80** passed (76 prior + 4 new B3 tests `#18b`, `#20c`, `#21b`, `#22b`), 0 failed, 0 skipped |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain` | **PASS** | **151/151** passed (88 legacy + 45 prior B1/B2 + 18 new B3 tests), 0 failed, 0 errors, 0 skipped |
| `.\gradlew.bat :app:assembleDebug --console=plain` | **PASS** | `BUILD SUCCESSFUL` |
| `cd cloud-rules && node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` | **PASS** | **69/69** passed on `Pixel_9_Pro_API_34(AVD) - 14` (54 `AppSmokeTest` + 10 `CloudInitializationIntegrationTest` + 5 `CloudB3IntegrationTest`), 0 failed, 0 errors, 0 skipped |

### JVM Unit Test Breakdown (151/151)
- Legacy Hardware / LAN / Face (`88`): `Esp32ClientTest` (25), `SecurityTest` (19), `HardwareStatusTest` (17), `FaceMathTest` (13), `InsightsTest` (10), `AlarmSettingsTest` (4)
- Cloud B1/B2/B3 (`63`): `CloudSessionAndMultiHomeTest` (11, **new**), `RtdbSnapshotParsingTest` (8), `RtdbPayloadBuilderTest` (8, **+2 new**), `CloudIsolationTest` (7), `FakeCloudRepositoryTest` (6), `FirebaseConfigurationReadinessTest` (6), `FirebaseProviderTest` (5), `RtdbHomeMetaFetcherTest` (5, **new**), `CloudErrorMapperTest` (4), `RtdbPathTest` (3)

---

## 12. SHA-256 LOCKED Integrity (19/19 Untouched LOCKED Files + 2 Rules Files — 0 Mismatches)

| File | SHA-256 (Baseline == Post-B3) | Status |
|---|---|---|
| `app/google-services.json` | `E4D24F478C0EA8F9A860ADC2A03C8AEB3F0A04FA8A97C30CF62A9201B29814BE` | MATCH |
| `app/src/main/java/com/example/smarthomeai/MainActivity.kt` | `170B939136281E51DB0884A5B05B2E41C238784E6A63731D376010EE02287045` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` | `1D9A9FFB8C03262FF60CB2AD60CCA40A4A23206F4338209B54F61C647BF6E70A` | MATCH |
| `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt` | `685422F1F492EB4562765970013E68CCEC39D1EDD9B9336E2E4DEE990E3D6334` | MATCH |
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
| `app/src/main/java/com/example/smarthomeai/ui/screens/LoginScreen.kt` | `061023920D1F1097B642F705C53540BBF1C111A3FED0A4AABD115156FC7F94D4` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/SensorChart.kt` | `419646799873A0D2832EC02758C8C55DCD299A5F3654CF849BF5418DD87B1300` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt` | `9E7C3F089F250D16F136C1D21FA262DADC87716B39FD6B947134114850427277` | MATCH |
| `app/src/main/java/com/example/smarthomeai/ui/theme/Theme.kt` | `F8450DD0D5510C7FCD18C1E81BD4A1654EDA682BEAB0C08B52EF9D43FA9E7EE5` | MATCH |
| `cloud-rules/database.rules.src.json` | `F4BA2441A430788800203ECE8C89B637D4A3926A71778A415664CA5122DCFAA1` | UNCHANGED |
| `cloud-rules/database.rules.json` | `4154D49C2D0AD3DF3FB8C7E7F866778F89D8F3CA06619541CB38E677D5946E71` | UNCHANGED |

Architecture grep verification:
- `com.google.firebase` in `SmartHomeViewModel.kt`: **0 matches**.
- `com.google.firebase` in `CloudHomeManager.kt`, `CloudSession.kt`, `CloudRepository.kt`, `CloudHomeSection.kt`: **0 matches**.
- Hardcoded `appaintm` URLs in code: **0 matches** (2 matches are doc comments in test classes stating production isolation).
- `setPersistenceEnabled(...)` calls in code: **0 matches** (1 match is a KDoc comment in `FirebaseProvider.kt`).
- Firestore imports/references in `app/src`: **0 matches**.

---

## 13. LAN Regression Assessment

- **Zero LAN Protocol / Hardware Impact:** `Esp32Client.kt`, `Storage.kt`, `esp32/**`, HTTP endpoints, JSON schema, and all `SmartHomeViewModel` LAN polling/command/mode/clothesline/alarm/face-door methods were untouched.
- **State Isolation:** `CloudUiState` is completely decoupled from `SmartHomeState`; Cloud errors (`Offline`, `Timeout`, `PermissionDenied`, `Unavailable`) never mutate `esp32Online` and never block `network: Mutex` or LAN operations.
- **Verified Automatically:** All 88 legacy JVM tests (`Esp32ClientTest`, `SecurityTest`, `HardwareStatusTest`, `InsightsTest`, `AlarmSettingsTest`, `FaceMathTest`), all 54 `AppSmokeTest` UI tests, and `CloudB3IntegrationTest.homeScreen_whenCloudOfflineOrRevoked_doesNotCrashAndKeepsLanControlsWorking` passed 100%.
- **Physical Hardware Offline Note:** B1 `A2` physical phone + ESP32 LAN-only (WAN disconnected) hardware verification remains `PENDING USER VERIFICATION` as carried forward from B1.

---

## 14. Production Isolation

- Zero Firebase Console access; zero Production RTDB reads/writes; zero Production Rules reads/writes/deploys; zero Auth provider changes; zero migrations.
- All emulator tests ran strictly with `--project demo-smarthomeai` on loopback (`127.0.0.1:9000` / `10.0.2.2:9000`).
- Instrumented tests (`CloudInitializationIntegrationTest` and `CloudB3IntegrationTest`) use dedicated isolated secondary `FirebaseApp` instances pointing solely to `10.0.2.2:9000` (`demo-smarthomeai`), never `[DEFAULT]` `FirebaseApp` or `appaintm`.
- **B1-D remains LOCKED.**

---

## 15. Remaining Risks

1. **B1 A2 Physical Hardware Verification (`PENDING USER VERIFICATION`):** Physical Android phone + physical ESP32 LAN-only offline test remains pending user verification; B1 stays `CONDITIONAL PASS`.
2. **B1-D Production Audit & Rules Deployment (`LOCKED`):** Production `appaintm` RTDB rules and existing data remain unaudited; B3 Cloud UI should not be exercised against live Production RTDB until B1-D audit, backup, and written approval are completed.
3. **Invitation / Join / QR Not Yet Designed:** Adding members to a home requires a dedicated schema & security Rules contract (e.g., invite tokens / expiry / rate limits) to be designed in a separate task; `deviceKey` sharing, if ever needed, must remain strictly out-of-band.
4. **Live Authenticated Multi-Account FirebaseAuth End-to-End Against Production:** Verified with local RTDB Emulator and isolated `FirebaseCloudSession` / `CloudHomeManager` / `RtdbCloudRepository` tests; full live FirebaseAuth + Production RTDB testing awaits B1-D approval.

---

## 16. Rollback

All B3 changes are localized to source and test files with zero Gradle, schema, Rules, or Production impact. To revert cleanly to baseline `d63eb07`:

```powershell
Set-Location "d:\MMO\DoAn2\app-nha-thong-minh"
git checkout -- `
  app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt `
  app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt `
  app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt `
  app/src/main/java/com/example/smarthomeai/cloud/RtdbHomeMerger.kt `
  app/src/main/java/com/example/smarthomeai/cloud/RtdbPayloadBuilder.kt `
  app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt `
  app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt `
  app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt `
  app/src/test/java/com/example/smarthomeai/cloud/FakeCloudRepository.kt `
  app/src/test/java/com/example/smarthomeai/cloud/RtdbPayloadBuilderTest.kt `
  cloud-rules/test/rules.test.mjs
Remove-Item -Force `
  app/src/main/java/com/example/smarthomeai/cloud/CloudSession.kt `
  app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt `
  app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt `
  app/src/test/java/com/example/smarthomeai/cloud/CloudSessionAndMultiHomeTest.kt `
  app/src/test/java/com/example/smarthomeai/cloud/RtdbHomeMetaFetcherTest.kt `
  app/src/androidTest/java/com/example/smarthomeai/cloud/CloudB3IntegrationTest.kt `
  docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md
```

---

## 17. Final Gate

**`B3 PASS`**

- **All B3 Acceptance Criteria Met (13/13):**
  1. Auth lifecycle cancels old UID flows before starting new UID flows (`B2-F5` resolved).
  2. Multi-Home operates strictly on current UID with zero cross-UID leakage.
  3. Home selection switches active home and member observer cleanly without mixing home data.
  4. Member list and OWNER/MEMBER role actions strictly follow RTDB Rules.
  5. Leave Home and Remove Member execute single root multi-path atomic deletes.
  6. `MIN-04` resolved with bounded concurrency (`Semaphore(4)`), aggregate timeout, cancellation propagation, and partial-failure preservation.
  7. `B2-F5` resolved across `FirebaseCloudSession`, `CloudHomeManager`, `SmartHomeViewModel`, and `RtdbCloudRepository`.
  8. Cloud offline/unavailable/revoked states never affect LAN control or `esp32Online`.
  9. Zero regressions on `VERIFIED-HW` functionality.
  10. All test suites PASS (`test:build` 4/4, `test:rules` 80/80, JVM 151/151, Android Instrumented 69/69).
  11. Debug build (`assembleDebug`) succeeds with zero new warnings.
  12. All 19 LOCKED files + both Rules files verified unchanged (SHA-256 MATCH).
  13. Zero Production Firebase operations (`B1-D` remains LOCKED).
- **Recommended Next Tasks (do not auto-start):**
  1. Complete user verification of **B1 A2** (physical Android phone + physical ESP32 LAN offline regression).
  2. Dedicated design spec for **Invitation / Join / QR** member onboarding contract (before modifying Rules or adding invite UI).
  3. User-approved **B1-D Production RTDB Read-Only Audit & Rules Deployment** before enabling live Production Cloud traffic.
