# BÁO CÁO AUDIT TỔNG THỂ SAU TÍCH HỢP (POST-INTEGRATION AUDIT REPORT)

- **Phạm vi:** Kiểm tra Read-Only toàn hệ thống sau khi hoàn tất 7 prompt triển khai (`01` → `07`) cho `F1`–`F5` trên nhánh `master` (`0cea70f..14b5f3b`).
- **Môi trường kiểm thử:** Local Firebase RTDB Emulator (`--project demo-smarthomeai`, `127.0.0.1:9000` / `10.0.2.2:9000`), AVD `Pixel_9_Pro_API_34` (Android 14), JDK `D:\Program Files\Android Studio\jbr`.
- **Nguyên tắc tuân thủ:** Không sửa source code app/rules/firmware; không tạo `.firebaserc`; không truy cập `appaintm` hoặc Firebase Console (`B1-D` giữ `LOCKED`); không `git push`.

---

## 1. Tóm tắt kết luận

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| **Kết luận tổng thể** | **PASS (Đạt chuẩn tích hợp)** | 12/12 trục `A`–`L` đều đạt, toàn bộ bộ kiểm thử tự động PASS 100%, 0 lỗi Lint. |
| **Lỗi có thật (`BUG`)** | **1 (đã khắc phục)** | `0 CRITICAL`, `0 HIGH`, `0 MEDIUM`, `1 LOW` (`RISK-04` được phân loại lại thành `BUG` mức `LOW` và đã vá + xác minh sau Audit) |
| **Rủi ro tiềm ẩn (`RISK`)** | **6 còn lại** | Toàn bộ ở mức `LOW` (các tình huống biên thời gian thực/đồng hồ/race hẹp; `RISK-04` đã chuyển sang trạng thái đã khắc phục) |
| **Thiếu bằng chứng (`UNVERIFIED`)** | **2** | Auth Google live trên `appaintm` & kiểm thử phần cứng ESP32 thật (`C1` / `B1-A2`) |
| **Giới hạn cố định (`LIMITATION`)** | **4** | Do khóa `LOCKED` (`CD-2`, `CD-3`, `Storage.kt`) và kiến trúc `1 nhà – 1 thiết bị` |
| **Cần người dùng duyệt (`USER_DECISION_REQUIRED`)** | **4** | Mở khóa `B1-D`, lịch test phần cứng `C1`, chính sách retention RTDB, `C4` Release Signing |

---

## 2. Lịch sử Commit tích hợp (`0cea70f..14b5f3b`)

Baseline trước đợt tích hợp `F1`–`F5` là commit `0cea70f` (`checkpoint task audit b4`).

| STT | Commit | Ngày | Thông điệp | Phạm vi Prompt |
|---|---|---|---|---|
| 1 | `e71bf28` | 2026-11-21 | Lập kế hoạch tích hợp các tính năng lõi | Planning |
| 2 | `6409800` | 2026-11-21 | Tích hợp quản lý một nhà cho chủ sở hữu | Prompt 01 (`F1` – Nhánh A) |
| 3 | `461867a` | 2026-11-21 | Bổ sung kiểm thử giao diện cho chế độ một nhà chủ sở hữu | Prompt 01 (`F1` – Nhánh B) |
| 4 | `5041a96` | 2026-11-21 | Hoàn thiện vòng đời phiên đăng nhập | Prompt 02 (`F4` – Nhánh A) |
| 5 | `9dad2f3` | 2026-11-21 | Sửa nhánh UNAVAILABLE trong bộ đánh giá đồng bộ phiên | Prompt 02 (`F4` – Nhánh A fix) |
| 6 | `487e18a` | 2026-11-21 | Bổ sung kiểm thử giao diện điều hướng đăng nhập lại khi mất phiên Cloud | Prompt 02 (`F4` – Nhánh B) |
| 7 | `7429772` | 2026-11-21 | Bổ sung đăng ký thiết bị và lớp dữ liệu RTDB cho Gateway | Prompt 03 (`F2a` – Nhánh A) |
| 8 | `e267dff` | 2026-11-21 | Bổ sung kiểm thử quy tắc Emulator cho dữ liệu Gateway | Prompt 03 (`F2a` – Nhánh B) |
| 9 | `f79cc10` | 2026-11-21 | Đồng bộ trạng thái ESP32 lên Firebase | Prompt 04 (`F2b`) |
| 10 | `4fe2a81` | 2026-11-21 | Bổ sung dữ liệu Cloud dự phòng khi mất LAN | Prompt 05 (`F3`) |
| 11 | `212f1c0` | 2026-11-21 | Hiển thị trạng thái đồng bộ và nguồn dữ liệu Cloud | Prompt 06 (`F5a`) |
| 12 | `a1ac300` | 2026-11-21 | Sửa import kiểm thử giao diện CloudHybridViewUiTest | Prompt 06 (`F5a` – Nhánh B fix) |
| 13 | `14b5f3b` | 2026-11-21 | Đơn giản hóa cài đặt và cập nhật thông tin ứng dụng | Prompt 07 (`F5b`) |

---

## 3. Đối chiếu SHA-256 các tệp `LOCKED` (`0cea70f` vs `HEAD`)

`git diff --stat 0cea70f HEAD -- <7 tệp LOCKED>` trả về rỗng; toàn bộ SHA-256 khớp 100% với baseline `0cea70f`:

| Tệp `LOCKED` | SHA-256 (`0cea70f` & `HEAD`) | Khớp |
|---|---|---|
| `app/src/main/java/com/example/smarthomeai/MainActivity.kt` | `170B939136281E51DB0884A5B05B2E41C238784E6A63731D376010EE02287045` | `True` |
| `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` | `1D9A9FFB8C03262FF60CB2AD60CCA40A4A23206F4338209B54F61C647BF6E70A` | `True` |
| `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt` | `685422F1F492EB4562765970013E68CCEC39D1EDD9B9336E2E4DEE990E3D6334` | `True` |
| `app/src/main/java/com/example/smarthomeai/data/Storage.kt` | `B54E6444463DC928B72C701F3D4FBD24AFE6FF43F2E42B9CC5F6E4EAC143B96A` | `True` |
| `esp32/SmartHomeESP32/SmartHomeESP32.ino` | `6170D0D1F2DF2B026EF59D1A4756C65B062C619C7B7A275B3A14E019EA3FD139` | `True` |
| `cloud-rules/database.rules.src.json` | `F4BA2441A430788800203ECE8C89B637D4A3926A71778A415664CA5122DCFAA1` | `True` |
| `cloud-rules/database.rules.json` | `4154D49C2D0AD3DF3FB8C7E7F866778F89D8F3CA06619541CB38E677D5946E71` | `True` |

---

## 4. Kết quả thực thi bộ kiểm thử (Runtime Verification)

| Hạng mục / Lệnh chạy | Số lượng | Kết quả | Chi tiết thực tế quan sát được |
|---|---|---|---|
| `cd cloud-rules; npm run test:build` | 4 tests | **PASS** | 4/4 pass, 0 fail, 0 skip (`test/build-rules.test.mjs`) |
| `cd cloud-rules; npm run test:rules` | 80 tests (9 suites) | **PASS** | 80/80 pass, 0 fail, 0 skip (`test/rules.test.mjs` trên `demo-smarthomeai`) |
| `node scripts/run-emulator.mjs "node --test test/gateway-payload.test.mjs"` | 19 tests (6 suites) | **PASS** | 19/19 pass, 0 fail, 0 skip (`test/gateway-payload.test.mjs`) |
| `.\gradlew.bat :app:testDebugUnitTest --console=plain` | 197 tests (23 XML) | **PASS** | 197 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :app:lintDebug --console=plain` | — | **PASS** | 0 errors, 18 warnings (`16 GradleDependency`, `1 ModifierParameter`, `1 DiscouragedApi`) |
| `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` | — | **PASS** | `BUILD SUCCESSFUL` |
| `node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` | 86 tests | **PASS** | 86/86 pass trên `Pixel_9_Pro_API_34(AVD) - 14`, 0 failed, 0 skipped |

Chi tiết `connectedDebugAndroidTest` (86 tests từ `app/build/outputs/androidTest-results/connected`):
- `com.example.smarthomeai.AppSmokeTest`: `54`
- `com.example.smarthomeai.cloud.CloudInitializationIntegrationTest`: `10`
- `com.example.smarthomeai.cloud.CloudB3IntegrationTest`: `5`
- `com.example.smarthomeai.cloud.CloudSingleOwnerUiTest`: `5`
- `com.example.smarthomeai.cloud.CloudSettingsSingleOwnerUiTest`: `5`
- `com.example.smarthomeai.cloud.CloudHybridViewUiTest`: `5`
- `com.example.smarthomeai.cloud.CloudSessionUiTest`: `2`

---

## 5. Kết quả Audit theo 12 Trục (`A` – `L`)

### Trục A — Architecture Integration Audit: **OK** *(1 `RISK` mức LOW)*
- **Ranh giới layer & import Firebase:** `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt:1-30` và toàn bộ 16 tệp dưới `app/src/main/java/com/example/smarthomeai/ui/` có **0** import `com.google.firebase`. Firebase chỉ tồn tại trong `cloud/` (`FirebaseProvider.kt:164-173`), nối qua `CloudFoundationFactory.create(app)` (`SmartHomeViewModel.kt:35`).
- **Chỉ dùng Auth & RTDB:** Grep `firestore`, `firebase.storage`, `firebase.functions` trong `app/src` trả về 0 kết quả; `app/build.gradle.kts:72-74` chỉ khai báo `firebase-bom:33.1.2`, `firebase-auth`, `firebase-database`.
- **Bridge Registry:** `CloudUiBridgeRegistry` (`CloudHomeManager.kt:111-127`) và `CloudDeviceUiBridgeRegistry` (`CloudDeviceUiBridge.kt:47-63`) dùng `@Volatile WeakReference`, đăng ký trong `SmartHomeViewModel.init` (`SmartHomeViewModel.kt:105-106`) và `unregister` có kiểm tra danh tính (`current == null || current === bridge`) trong `onCleared()` (`SmartHomeViewModel.kt:181, 184`).
- **Tuân thủ `REMOVE = NONE`:** `git diff --name-status --diff-filter=D 0cea70f..HEAD` không có tệp nào bị xóa; các mặc định B3 trên `CloudHomeBannerCard` / `CloudHomeManagementCard` (`singleHomeMode = false` tại `CloudHomeSection.kt:85, 298`) được giữ nguyên.
- **`RISK-01` (LOW):** `rememberActiveCloudBridge()` (`CloudHomeSection.kt:25`) và `rememberActiveCloudDeviceBridge()` (`CloudDeviceUiBridge.kt:66-67`) dùng `remember { ...currentOrNull() }`, có thể giữ `null` nếu Compose chạy trước khi bridge được đăng ký (an toàn trong `MainActivity.kt:46-95` vì `SmartHomeViewModel` khởi tạo trước `NavHost`).

### Trục B — Firebase RTDB Schema & Security Rules Compatibility: **OK**
Đối chiếu `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `RtdbPaths.kt`, `CloudGatewaySync.kt` với `cloud-rules/database.rules.src.json` và xác nhận thực tế bằng 99 test Emulator (`rules.test.mjs` + `gateway-payload.test.mjs`):
- **`homes/$homeId/devices/$deviceId`:** `RtdbPaths.isValidEntityId` khớp `^[A-Za-z0-9_-]{8,64}$` (`RtdbPaths.kt:12, 20-21`; `database.rules.src.json:80`); `CloudMapper.buildDeviceRegistrationPayload` (`CloudMapper.kt:89-116`) xuất đúng 5 trường `name`, `model`, `status`, `createdAt` (`ServerValue.TIMESTAMP` tại `RtdbDeviceRepository.kt:82`), `createdBy`.
- **`state/gateway/values`:** `CloudMapper.buildGatewayStatePayload` (`CloudMapper.kt:122-158`) giữ đúng 8 khóa whitelist (`light`, `door`, `alarm`, `motion`, `monitoring`, `mode`, và `temp`/`hum` tùy chọn hợp lệ ngưỡng), kèm `online`, `reportedBy`, `receivedAt` (`ServerValue.TIMESTAMP` tại `RtdbDeviceRepository.kt:287`), `deviceTs`; không đẩy trường mưa/giàn phơi/IP/`deviceKey`.
- **`config`:** `CloudMapper.buildConfigPayload` (`CloudMapper.kt:160-189`) tăng `rev = previousRev + 1` (hoặc `1`), `SmartHomeViewModel.kt:484-485` ép ngưỡng trong khoảng cho phép, `RtdbDeviceRepository.kt:359` dùng `ServerValue.TIMESTAMP`.
- **`telemetry/{yyyyMMddHHmm}_g`:** `RtdbPaths.telemetryRecordId` (`RtdbPaths.kt:84-91`) tạo khóa UTC `^[0-9]{12}_g$`; `CloudMapper.buildTelemetryPayload` (`CloudMapper.kt:203-228`) trả `null` nếu thiếu cả `temp` và `hum` hợp lệ; khớp `database.rules.src.json:195-212`.
- **`events/g_{cloudStream}_{seq}`:** `CloudMapper.toCloudStream` (`CloudMapper.kt:69-81`) băm SHA-256 lấy 16 hex chữ thường (`^[0-9a-f]{16}$`), `RtdbPaths.eventId` (`RtdbPaths.kt:128-132`) ghép `g_${validStream}_$validSeq`, khớp `database.rules.src.json:255-272`.

### Trục C — Authentication & Session Lifecycle: **OK** *(1 `RISK` mức LOW, 2 `LIMITATION`, 1 `UNVERIFIED`)*
- **Thu hồi phiên bên ngoài:** `FirebaseCloudSession.observeSession()` (`FirebaseProvider.kt:139-158`) và `CloudHomeManager.applySessionState()` (`CloudHomeManager.kt:259-283`) đưa Cloud về `CloudStatus.SIGNED_OUT`; `CloudSessionSyncEvaluator.evaluate` (`CloudHomeManager.kt:167-171`) bật `shouldRequireReauth = true` khi `localLoggedIn == true && cloudStatus == SIGNED_OUT`; `SmartHomeViewModel.kt:107-123, 136-141` không chạm `_loggedIn` và không ngắt vòng lặp LAN.
- **Mất WAN khi còn cached user:** `AuthRepository.isSignedIn()` (`AuthRepository.kt:19`) giữ `true`; `CloudErrorMapper` (`CloudErrorMapper.kt:32, 46`) ánh xạ lỗi mạng/timeout sang `Offline`/`Timeout`; `CloudHomeManager` (`CloudHomeManager.kt:633-638`) giữ `currentUid` với `CloudStatus.ERROR` và `shouldRequireReauth = false` (`CloudHomeManager.kt:172-184`).
- **Đổi UID:** `CloudHomeManager.applySessionState` (`CloudHomeManager.kt:259-299`) hủy & join toàn bộ job cũ (`memberSwitchJob`, `membersJob`, `homesJob`, `activeMutationJob`), xóa cờ trạng thái tạm; `CloudGatewaySync` (`CloudGatewaySync.kt:155-162`) và `CloudFallbackObserver` (`CloudFallbackObserver.kt:70-78`) dùng `collectLatest` theo `(uid, homeId, ...)`; `RtdbDeviceRepository` (`RtdbDeviceRepository.kt:505-529, 639-675`) kiểm tra `expectedUid` trước/sau; `SmartHomeViewModel.kt:124-129` cập nhật `userEmail`.
- **Chế độ Demo khi thiếu `google-services.json`:** `app/build.gradle.kts:7-9` áp plugin có điều kiện; `FirebaseProvider.isConfigured` (`FirebaseProvider.kt:47-55, 81-88`) trả `false`; `CloudHomeManager` ở `CloudStatus.UNAVAILABLE` (`CloudHomeManager.kt:278-280`), `CloudGatewaySync` ở `IDLE` (`CloudGatewaySync.kt:177-180`), `CloudFallbackObserver` trả `null` (`CloudFallbackObserver.kt:101-104`).
- **`RISK-02` (LOW):** Nếu `RtdbDeviceRepository` gặp `CloudError.Unauthenticated` trước `AuthStateListener` / `RtdbCloudRepository.observeHomes()`, `CloudFallbackObserver` (`CloudFallbackObserver.kt:188-191`) xóa `cloudSnapshot` và `CloudGatewaySync` (`CloudGatewaySync.kt:701-736`) dừng ở `ERROR`, nhưng chưa tự kích `CloudHomeManager` bật `reauthRequired = true` ngay (chờ luồng `cloudManager.state`).

### Trục D — Gateway Registration & Cloud Sync: **OK** *(1 `RISK` mức LOW, 1 `LIMITATION`)*
- **`resolveOrRegisterDevice`:** `RtdbDeviceRepository.evaluateDevicesSnapshot` (`RtdbDeviceRepository.kt:749-771`) chọn thiết bị `active` cũ nhất theo `(createdAt, id)`, báo `hasMultipleActive` khi > 1 `active`, trả `RevokedOnly` khi toàn bộ bị thu hồi → `CloudGatewaySync` (`CloudGatewaySync.kt:254-275`) vào `GatewaySyncPhase.BLOCKED` và không ghi `deviceData`. Không gửi `deviceKey`/IP/MAC.
- **Tần suất ghi `state/gateway`:** `CloudGatewaySync.kt:377-411, 543-651, 800-802` đảm bảo `STATE_MIN_INTERVAL_MS = 2_000L` (debounce), `HEARTBEAT_INTERVAL_MS = 60_000L`, `STATE_FRESHNESS_WINDOW_MS = 5_000L`, và chỉ ghi `online = false` một lần khi chuyển `true -> false` (reset `hasReportedOnlineInSession = false`; nếu lỗi mạng tạm thời thì giữ cờ để retry).
- **Đồng bộ `config` & `telemetry`:** `syncConfig` (`CloudGatewaySync.kt:413-473`) bỏ qua nếu giá trị không đổi, tự đọc lại và retry 1 lần khi `PermissionDenied` (`rev` tăng đơn điệu). `SmartHomeViewModel.kt:387-388` lấy mẫu `>= 60_000L` ms; `syncTelemetry` (`CloudGatewaySync.kt:475-499`) chỉ ghi tối đa 1 mẫu/phút UTC.
- **`RISK-03` (LOW):** `RtdbDeviceRepository.resolveOrRegisterDevice` (`RtdbDeviceRepository.kt:58-90`) đọc `devicesRef` rồi mới `push().setValue(...)` (không transaction). Hai máy cùng đăng nhập 1 tài khoản Owner đăng ký lần đầu đồng thời trên `devices` rỗng có thể tạo 2 thiết bị `active` trong phiên đầu, trước khi các phiên sau hội tụ về bản ghi cũ nhất kèm cảnh báo.

### Trục E — Cloud Fallback & LAN-first Isolation: **OK** *(`RISK-04` đã được khắc phục)*
- **Debounce & hủy tức thì:** `CloudFallbackObserver` (`CloudFallbackObserver.kt:70-78, 95-110, 263`) chờ `DEFAULT_FALLBACK_DEBOUNCE_MS = 5_000L` khi mất LAN rồi mới query và gắn 3 listener; khi `esp32Online == true`, `collectLatest` hủy ngay coroutine và xóa `_cloudSnapshot`.
- **LAN thắng tuyệt đối & cô lập `network` Mutex:** `DataSourceResolver.resolve` (`DataSourceResolver.kt:38-46`) luôn trả `DataSource.LAN` khi `esp32Online == true`. `SmartHomeViewModel.network` (`SmartHomeViewModel.kt:40`) chỉ được dùng cho thao tác LAN; `CloudHomeManager`, `CloudGatewaySync`, `CloudFallbackObserver` chạy độc lập trên `Dispatchers.IO` (`SmartHomeViewModel.kt:58, 70`), không tham chiếu `network` hay `Esp32Client`.
- **Chỉ đọc trên Cloud/Cache:** Mọi nút điều khiển phần cứng ở UI (`HomeScreen.kt:189, 216, 218, 250, 273`, `Components.kt:166`, `SecurityCard.kt:45, 52, 60`, `AlarmSettingsCard.kt:15`, `CameraScreens.kt:160, 266`) và ViewModel (`SmartHomeViewModel.kt:248, 273, 298, 320, 338, 357, 371`) đều khóa khi `esp32Online == false`. Không có luồng ghi ngược từ Cloud xuống ESP32.
- **`RISK-04` (`BUG` LOW – ĐÃ KHẮC PHỤC):** Trước đây `HomeScreen.kt` chỉ tính `DataSourceResolver.resolve(...)` trong `remember(...)` mà không cập nhật `nowMs` theo thời gian. Đã bổ sung `LaunchedEffect` + `nowMs` ticker theo chu kỳ tối đa `30_000L` ms cho đến mốc `updatedAt + STALE_THRESHOLD_MS + 1L` (`HomeScreen.kt:38-73`), kèm test `homeScreen_whenIdleOfflineOverFiveMinutes_automaticallyShowsStaleBadge` (`CloudHybridViewUiTest.kt:262-301`).

### Trục F — Data Consistency & Event Idempotency: **OK** *(1 `RISK` mức LOW)*
- **Create-only không kẹt cursor:** `RtdbDeviceRepository.writeCreateOnly` (`RtdbDeviceRepository.kt:465-503`) khi gặp `PermissionDenied` đều kiểm tra lại `readSingleSnapshot(ref)` và trả `CreateOnlyWriteOutcome.ALREADY_EXISTS` nếu node đã tồn tại → `CloudGatewaySync.syncEvents` (`CloudGatewaySync.kt:528-536`) và `syncTelemetry` (`CloudGatewaySync.kt:491-495`) vẫn tiến con trỏ bình thường.
- **Thứ tự `seq` & batch 20:** `CloudGatewaySync.syncEvents` (`CloudGatewaySync.kt:503-510, 804`) sắp xếp `compareBy<DeviceEvent> { it.seq }.thenBy { it.stream }`, giới hạn `MAX_EVENTS_PER_CYCLE = 20`. Phía đọc `CloudFallbackObserver` (`CloudFallbackObserver.kt:264`) dùng `DEFAULT_EVENTS_LIMIT = 20`, sắp xếp giảm dần theo `(receivedAt, seq, id)` (`RtdbDeviceRepository.kt:911-920`).
- **Dữ liệu cũ (`> 5 phút` hoặc `gateway.online == false`):** `DataSourceResolver.kt:18, 50-59, 67, 76` đặt `STALE_THRESHOLD_MS = 300_000L` và đánh dấu `isStale = true` đúng cả 2 điều kiện.
- **`RISK-05` (LOW – Lệch đồng hồ):** `DataSourceResolver.resolve` (`DataSourceResolver.kt:10-16, 50-52`) so sánh `cloudReceivedAt` (giờ server) với `localLastSeen` / `nowMs` (giờ điện thoại). Nếu đồng hồ điện thoại chạy nhanh hơn server, máy có thể chọn `LOCAL_CACHE` thay vì `CLOUD`; nếu đồng hồ điện thoại chậm hơn server > 5 phút, kiểm tra tuổi `> 5 phút` có thể bị trễ (trừ khi `gateway.online == false`).

### Trục G — Android Lifecycle & Coroutine Safety: **OK** *(1 `RISK` mức LOW)*
- **Scope:** 0 `GlobalScope`, 0 `runBlocking`, 0 `CoroutineScope(` tự tạo trong `app/src/main`. Toàn bộ luồng Cloud gắn `viewModelScope` (`SmartHomeViewModel.kt:46, 53-71`).
- **`onCleared()`:** `SmartHomeViewModel.kt:180-187` gọi `unregister` hai bridge và `clear()` theo thứ tự an toàn trên `cloudFallbackObserver`, `gatewaySync`, `cloudManager` trước `super.onCleared()`.
- **`callbackFlow` & cancellation:** Toàn bộ 4 `callbackFlow` (`FirebaseProvider.kt:104-116`, `RtdbCloudRepository.kt:40-109, 183-235`, `RtdbDeviceRepository.kt:616-680`) đều có `awaitClose { ... removeEventListener ... }`. `CloudErrorMapper.kt:45-51` và các khối `catch` trong `cloud/` đều rethrow `CancellationException` (chỉ bắt `TimeoutCancellationException`). Không có deadlock `lifecycleMutex` lồng nhau (`CloudHomeManager.kt:223, 260`).
- **`RISK-06` (LOW):** `CloudHomeManager.onSignedOut()` (`CloudHomeManager.kt:350-365`) tăng `sessionGen` không qua `lifecycleMutex`, còn `applySessionState` (`CloudHomeManager.kt:261-295`) không kiểm tra lại `sessionGen.get() == gen` sau `cancelAndJoin()`, và `handleHomesResult`/`handleMembersResult` (`CloudHomeManager.kt:626-633, 728-750`) gán `_uiState.value = ...` ngoài khối cập nhật atomic — tồn tại cửa sổ race rất hẹp nếu đăng xuất/clear đồng thời giữa chừng.

### Trục H — UI/UX Integration: **OK** *(1 `LIMITATION`)*
- **Hiển thị trạng thái & nguồn dữ liệu:** `HomeScreen.kt:37-72, 123-134`, `CloudSyncStatus.kt:58-79, 142-414`, `SettingsScreen.kt:62-64`, `SystemScreen.kt:96-97, 129-161` hiển thị đầy đủ trạng thái Cloud, trạng thái Gateway, nguồn (`LAN trực tiếp` / `Cloud (chỉ đọc)` / `Bộ nhớ máy`), badge `"Dữ liệu cũ"`, thời điểm cập nhật gần nhất.
- **Ẩn Multi-Home & Member ở runtime nhưng giữ tương thích B3:** `SmartHomeViewModel.kt:46` (`singleHomeMode = true`), `CloudHomeSection.kt:30, 58, 163-198, 268-282, 310-541` ẩn danh sách nhiều nhà & quản lý thành viên ở runtime, nhưng giữ nguyên `singleHomeMode = false` làm tham số mặc định của `CloudHomeBannerCard` (`line 85`) / `CloudHomeManagementCard` (`line 298`) và các `testTag` cho `CloudB3IntegrationTest`.
- **Điều hướng `"Đăng nhập lại"`:** `CloudHomeSection.kt:241-253` → `HomeScreen.kt:67` → `MainActivity.kt:70-82` mở `LoginScreen` và pop về `home` khi thành công (`LIMITATION CD-3`: bấm Back hệ thống trên `LoginScreen` quay lại `home` LAN vì `MainActivity.kt` `LOCKED`).
- **`"Thành viên"` & `"Về ứng dụng"`:** `SettingsScreen.kt:65-83, 113-116` giữ nguyên FaceStore kèm phụ đề `"Khuôn mặt mở cửa cục bộ trên điện thoại (không đồng bộ lên Cloud)."`; `"Về ứng dụng"` (`SettingsScreen.kt:88, 121-123`) khớp `versionName = "3.6.0"` (`app/build.gradle.kts:20`) và mô tả chính xác kiến trúc LAN-first + Cloud read-only 1 nhà.

### Trục I — Legacy Feature Regression: **OK**
- Toàn bộ 7 tệp `LOCKED` giữ nguyên SHA-256 so với `0cea70f`.
- Toàn bộ bộ kiểm thử cũ & mới PASS 100%: `54` `AppSmokeTest`, `10` `CloudInitializationIntegrationTest`, `5` `CloudB3IntegrationTest`, `16` UI tests mới (`F1`–`F5`), `197` JVM unit tests (tăng từ 88 legacy + các suite B1/B3/F1–F5), `80` `rules.test.mjs`, `19` `gateway-payload.test.mjs`, `4` `build-rules.test.mjs`.

### Trục J — Security & Sensitive Data: **OK** *(1 `LIMITATION` có sẵn)*
- **0 dữ liệu nhạy cảm rò rỉ ra Cloud/Log:** Trong `app/src/main` chỉ có duy nhất 1 lệnh `Log.w` tại `RtdbCloudRepository.kt:36` (không log secret/token/vector). `deviceKey` bị chặn bởi `GatewayInputSnapshot` (`CloudGatewaySync.kt:36-72`), `validateNoSecretToken` (`CloudMapper.kt:37, 59-63`), và `containsForbiddenSecretKey` (`RtdbDeviceRepository.kt:682-693`). `password`, `idToken`, vector khuôn mặt (`FaceEngine.kt`) không bao giờ lên RTDB.
- **Không hardcode `appaintm`:** 0 lần xuất hiện `appaintm` trong `app/src/main` (`FirebaseProvider.kt:49-52, 90-91` đọc động từ `FirebaseApp.options.databaseUrl`); `app/google-services.json` hiện không có trong repo.
- **Tắt backup:** `AndroidManifest.xml:12-14` giữ `android:allowBackup="false"`, `android:fullBackupContent="false"`, `data_extraction_rules.xml:3-16`.

### Trục K — Memory / Performance / Resource Management: **OK** *(1 `USER_DECISION_REQUIRED`)*
- **Chống rò rỉ:** `WeakReference` trong 2 bridge registry + `onCleared()` unregister; `FirebaseProvider.kt:33` dùng `applicationContext`.
- **Giới hạn đọc RTDB:** Khởi tạo cursor `limitToLast(100)` (`CloudGatewaySync.kt:803`, trần cứng `500` tại `RtdbDeviceRepository.kt:696`), fallback events `limitToLast(20)` (`CloudFallbackObserver.kt:264`, trần cứng `100` tại `RtdbDeviceRepository.kt:697`, có `.indexOn: ["receivedAt"]`), latest telemetry `limitToLast(1)` (`RtdbDeviceRepository.kt:193-195`).
- **Ước tính lưu lượng ghi & retention:** `telemetry` tối đa ~1.440 ghi/ngày khi chạy 24/7; `state/gateway` ~1.440 heartbeat/ngày + cập nhật debounce `2s` + 1 ghi offline; `events` tối đa 20/chu kỳ. Chưa triển khai retention tự động trên RTDB (cố ý trì hoãn theo `D13` → cần người dùng chốt ở mục `UDR-03`).

### Trục L — Production Isolation: **OK** *(Các gate giữ nguyên trạng thái an toàn)*
- Mọi tham chiếu `demo-smarthomeai`, `10.0.2.2:9000`, `127.0.0.1:9000`, `useEmulator` chỉ nằm trong `app/src/test/`, `app/src/androidTest/`, và `cloud-rules/` — **0** xuất hiện trong `app/src/main/` hay `app/build.gradle.kts`.
- Không tồn tại `.firebaserc` ở thư mục gốc hoặc `cloud-rules/`.
- Gate **`B1-D`** (Audit/Backup/Deploy Production `appaintm`) vẫn **`LOCKED`**; gate **`B1-A2`** (hồi quy LAN + ESP32 phần cứng thật `C1`) vẫn **`PENDING USER VERIFICATION`**.

---

## 6. Bảng tổng hợp phát hiện

| ID | Trục | Phân loại | Mức độ | Mô tả | Bằng chứng (`path:line`) | Đề xuất |
|---|---|---|---|---|---|---|
| `RISK-01` | A | `RISK` | `LOW` | `rememberActiveCloudBridge()` / `rememberActiveCloudDeviceBridge()` cache `null` nếu compose trước khi bridge đăng ký | `CloudHomeSection.kt:25`, `CloudDeviceUiBridge.kt:66-67` | Giữ nguyên (an toàn với `MainActivity.kt:46-95`), lưu ý nếu tách screen test |
| `RISK-02` | C | `RISK` | `LOW` | `CloudError.Unauthenticated` từ `RtdbDeviceRepository` chưa tự kích `CloudHomeManager` bật `reauthRequired` ngay nếu đến trước `AuthStateListener` | `CloudFallbackObserver.kt:188-191`, `CloudGatewaySync.kt:701-736` | Có thể nối callback báo lỗi `Unauthenticated` về `CloudHomeManager` ở vòng cải tiến sau |
| `RISK-03` | D | `RISK` | `LOW` | `resolveOrRegisterDevice` đọc rồi mới ghi `push()` (không transaction), 2 máy cùng Owner đăng ký lần đầu song song có thể tạo 2 device `active` | `RtdbDeviceRepository.kt:58-90` | Giữ cơ chế cảnh báo + chọn device cũ nhất; theo dõi khi audit Production |
| `RISK-04` | E | `BUG` (đã khắc phục) | `LOW` | `HomeScreen` tính `DataSourceResolver.resolve` trong `remember(...)` không tick theo thời gian khi đứng yên > 5 phút offline | `HomeScreen.kt:38-73`, `CloudHybridViewUiTest.kt:262-301` | **ĐÃ KHẮC PHỤC:** Bổ sung `nowMs` + `LaunchedEffect` ticker tới `STALE_THRESHOLD_MS`; đã xác minh bằng 86/86 instrumented tests PASS |
| `RISK-05` | F | `RISK` | `LOW` | Lệch đồng hồ giữa điện thoại (`nowMs`/`localLastSeen`) và server (`receivedAt`) ảnh hưởng so sánh tuổi dữ liệu & chọn `LOCAL_CACHE` vs `CLOUD` | `DataSourceResolver.kt:10-16, 50-52` | Chấp nhận theo thiết kế (`ServerValue.TIMESTAMP` luôn đúng cho RTDB; `gateway.online == false` vẫn đánh dấu cũ) |
| `RISK-06` | G | `RISK` | `LOW` | Cửa sổ race rất hẹp giữa `onSignedOut()` (không giữ `lifecycleMutex`) với `applySessionState` / `_uiState.value =` ngoài khối atomic | `CloudHomeManager.kt:261-295, 350-365, 626-633, 728-750` | Nếu sau này chuẩn hóa concurrency, chuyển sang `_uiState.update` và kiểm tra `sessionGen` sau `cancelAndJoin()` |
| `RISK-07` | K | `RISK` | `LOW` | Chưa có auto-retention/xóa dữ liệu cũ cho `telemetry` (~1.440 mẫu/ngày) và `events` trên RTDB | `CloudGatewaySync.kt:475-499`, `database.rules.src.json:198, 259` | Cần người dùng chốt chính sách ở `UDR-03` |
| `UNV-01` | C, L | `UNVERIFIED` | `INFO` | Chưa kiểm chứng Auth Google Sign-In live, thu hồi token từ server `appaintm`, và chuyển mạng Wi-Fi ↔ 4G ngoài thực tế | `AuthRepository.kt:35-42`, `FirebaseProvider.kt:139-158` | Kiểm chứng khi mở `B1-D` / test thực địa `C1` |
| `UNV-02` | D, E, L | `UNVERIFIED` | `INFO` | Chưa chạy end-to-end với phần cứng ESP32 thật (`B1-A2` / `C1`: đồng bộ LAN→Cloud và rút nguồn/ngắt Wi-Fi ESP32) | `Esp32Client.kt`, `SmartHomeESP32.ino` | Chạy kịch bản kiểm thử phần cứng `C1` |
| `LIM-01` | B, D, H | `LIMITATION` | `INFO` | `CD-2`: `state/gateway/values` khóa 8 trường, không có mưa/giàn phơi/siêu âm khi xem từ Cloud | `database.rules.src.json:125-144`, `CloudMapper.kt:134-147` | Đúng spec; muốn thêm phải mở khóa `database.rules.src.json` |
| `LIM-02` | C, H | `LIMITATION` | `INFO` | `CD-3`: `MainActivity.kt` (`LOCKED`) đẩy `"login"` đè `"home"`, bấm Back hệ thống ở màn đăng nhập lại quay về `"home"` (LAN vẫn chạy) | `MainActivity.kt:70-82`, `HomeScreen.kt:67` | Giữ nguyên vì `MainActivity.kt` khóa |
| `LIM-03` | C, J | `LIMITATION` | `INFO` | `Storage.kt` (`LOCKED`) lưu `SharedPreferences` (`deviceKey` plaintext) & SQLite không tách UID; `usesCleartextTraffic="true"` cho LAN ESP32 | `Storage.kt:10-55`, `AndroidManifest.xml:18` | Giữ nguyên cho ESP32 LAN |
| `LIM-04` | D | `LIMITATION` | `INFO` | `deviceId` là logical ID trong `homes/$homeId/devices`, không gắn chặt MAC/chipId phần cứng ESP32 | `RtdbDeviceRepository.kt:58-90` | Phù hợp mô hình `1 nhà – 1 ESP32` |

---

## 7. Các quyết định cần người dùng phê duyệt (`USER_DECISION_REQUIRED`)

1. **`UDR-01` — Mở khóa gate `B1-D` (Production `appaintm`):** Phê duyệt chạy kiểm tra Read-Only trên RTDB Production `appaintm`, backup toàn bộ dữ liệu hiện có, xử lý tài khoản cũ (nếu có tài khoản sở hữu >1 nhà hoặc nhiều thiết bị `active`), và deploy `cloud-rules/database.rules.json`.
2. **`UDR-02` — Lên lịch kiểm thử phần cứng thật (`C1` / `B1-A2`):** Thực hiện kiểm thử trên điện thoại Android thật + mạch ESP32 thật (LAN offline, bật/tắt thiết bị LAN → Cloud sync, chuyển Wi-Fi sang 4G xem Cloud read-only fallback).
3. **`UDR-03` — Chính sách Retention RTDB (`D13`):** Quyết định có cần tự động xóa `telemetry` / `events` cũ hơn N ngày trên app hay dùng Cloud Scheduled Function / xóa thủ công.
4. **`UDR-04` — Cấu hình `C4` Release Signing & `google-services.json`:** Cung cấp `app/google-services.json` và cấu hình keystore phát hành khi chuyển từ kiểm thử Emulator sang bản build kết nối `appaintm`.

---

## 8. Đề xuất sửa lỗi theo cụm (Clustered Remediation)

- **Đã xử lý cụm lỗi phát hiện sau Audit:** `RISK-04` (phân loại lại thành `BUG` mức `LOW`) đã được vá trong `HomeScreen.kt` và kiểm chứng qua `CloudHybridViewUiTest` (86/86 Android instrumented tests PASS, 197/197 JVM unit tests PASS, 0 lỗi Lint). Không còn `BUG` tồn đọng trước `C1`.
- Nếu người dùng muốn xử lý phòng ngừa các `RISK` mức `LOW` còn lại trước khi mở rộng thêm tính năng trong tương lai, còn lại **1 cụm nhẹ (tùy chọn, không bắt buộc cho nghiệm thu `C1`)**:
  - **Cụm tùy chọn 1 (`CloudHomeManager` / Session Edge):** Siết `sessionGen` atomic `_uiState.update` trong `CloudHomeManager.kt` (`RISK-06`) và nối tín hiệu `CloudError.Unauthenticated` từ `RtdbDeviceRepository` sang `CloudHomeManager` (`RISK-02`).
  - ~~**Cụm tùy chọn 2 (`HomeScreen` Stale Ticker):**~~ **Đã hoàn tất & xác minh.**
