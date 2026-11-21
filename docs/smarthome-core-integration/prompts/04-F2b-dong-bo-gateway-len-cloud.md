# PROMPT 04 — F2b: ĐỒNG BỘ TRẠNG THÁI GATEWAY ESP32 LÊN FIREBASE RTDB

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Engineer xây dựng coordinator `CloudGatewaySync`, giao diện cầu nối `CloudDeviceUiBridge` + `CloudDeviceUiBridgeRegistry`, và nối vào `SmartHomeViewModel` để đẩy `devices`, `state/gateway`, `config`, `telemetry`, `events` từ ESP32 lên RTDB hoàn toàn bất đồng bộ ngoài `network: Mutex`.
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Test Engineer đọc diff + handoff Nhánh A, kiểm tra concurrency/isolation/LAN-first, thực thi unit test `CloudGatewaySyncTest` và kiểm thử tích hợp Emulator (nếu viết bổ sung test emulator).

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (commit kết thúc của Prompt `03`) và `git status --porcelain` sạch.
2. Đọc lại `SmartHomeViewModel.kt`, `CloudHomeManager.kt`, `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudDeviceRepository.kt`, `RtdbDeviceRepository.kt`, `FakeCloudDeviceRepository.kt`.
3. Đối chiếu thiết kế mục 4–13 với source thực tế.
4. Hoàn thiện phương án kỹ thuật mục 9–12.
5. Triển khai phạm vi mục 5–6 (kèm `CloudGatewaySyncTest.kt`, **không chạy**).
6. Không mở rộng sang Fallback Observer (`05`) hay UI (`06`).
7. Chỉ chạy static checks ở mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy Firebase Emulator.**
11. **KHÔNG chạy Gradle build.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất báo cáo Handoff đúng mẫu mục 16.
14. Tạo 01 Git commit tiếng Việt riêng theo mục 18 (`TUYỆT ĐỐI KHÔNG PUSH`).
15. **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc `git diff` và commit của Nhánh A.
3. Kiểm tra logic coroutine/cancellation/debounce/backoff/cursor và xác nhận zero lock contention trên `network: Mutex`.
4. Chạy `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` (và kiểm tra `connectedDebugAndroidTest` nếu có AVD). Bổ sung test nếu thiếu độ phủ (không sửa implementation).
5. **KHÔNG** sửa implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện và `file:dòng`.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu có lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `02` (`F4`) và Prompt `03` (`F2a`) đã hoàn tất và PASS.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `03`.
- **Working tree:** Sạch.

---

## 3. Trạng thái hiện tại đã xác minh

1. Prompt `03` đã cung cấp: `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudDeviceRepository.kt`, `RtdbDeviceRepository.kt`, `CloudComponents.deviceRepository: CloudDeviceRepository? = null`.
2. `SmartHomeViewModel.kt`:
   - `network: Mutex` bao toàn bộ `refreshLocked()`, `commandLocked()`, `applyModeLocked()`, `updateClothesline()`, `setRules()`, `setTiming()`, `setAlarmSettings()`, `setConnection()`.
   - `_state: MutableStateFlow<SmartHomeState>` chứa đầy đủ `esp32Online`, `lastSeen`, `temperature`, `humidity`, `motionDetected`, `light`, `doorOpen`, `alarm`, `mode`, `monitoring`, `deviceEvents` (`≤200`), `samples`, `temperatureLimit`, `humidityLimit`, `coldLimit`, `autoAlarm`.
   - `applySensor()` ghi tối đa 1 `SensorSample` mỗi `60_000L` ms; `syncEventsLocked()` nối thêm `deviceEvents` theo `seq` tăng dần.
   - `onCleared()` gọi `CloudUiBridgeRegistry.unregister(cloudManager)` và `cloudManager.clear()`.
3. `CloudHomeManager.state` phát ra `(currentUid, selectedHomeId, status, selectedHome?.role)`.

---

## 4. Mục tiêu cụ thể

Triển khai động cơ đồng bộ Gateway (`CloudGatewaySync`) từ điện thoại (Gateway) lên Firebase RTDB:
1. Gắn vòng đời chặt vào `(currentUid, selectedHomeId, status == READY, role == OWNER)` của `CloudHomeManager.state` bằng `collectLatest` để hủy tức thì mọi job cũ khi đổi UID, đăng xuất hoặc thu hồi quyền.
2. Tự động `resolveOrRegisterDevice` để đạt điều kiện `@ACTIVE` trước mọi thao tác ghi `deviceData`.
3. Đồng bộ `state/gateway` (debounce 2s khi đổi giá trị, heartbeat 60s, gửi `online = false` 1 lần khi mất LAN sau khi đã online), `config`, `telemetry/{yyyyMMddHHmm}_g` (UTC, chỉ mẫu mới nhất), và `events/g_{cloudStream}_{seq}` (cursor + batch tối đa 20 + chống trùng create-only).
4. Áp dụng backoff có giới hạn khi lỗi `Offline`/`Timeout` và dừng ngay khi lỗi `PermissionDenied`/`InvalidData`/`Unauthenticated`.
5. Xuất trạng thái đồng bộ ra `CloudDeviceUiBridge` / `CloudDeviceUiBridgeRegistry` mà **không động vào `network: Mutex`**.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Tạo `CloudDeviceUiBridge.kt`:
  - `enum class GatewaySyncPhase { IDLE, WAITING_HOME, REGISTERING, SYNCING, OK, PAUSED_LAN_OFFLINE, BLOCKED, ERROR }`
  - `data class GatewaySyncUiState(val phase: GatewaySyncPhase = GatewaySyncPhase.IDLE, val activeDeviceId: String? = null, val lastSyncAt: Long? = null, val warningMessage: String? = null, val lastError: CloudError? = null, val errorMessage: String? = null)`
  - `interface CloudDeviceUiBridge { val gatewaySync: StateFlow<GatewaySyncUiState>; fun retrySync() = Unit }`
  - `object CloudDeviceUiBridgeRegistry` (`WeakReference<CloudDeviceUiBridge>`) + helper Compose `rememberActiveCloudDeviceBridge()`.
- Tạo `CloudGatewaySync.kt` nhận `(scope, cloudStateFlow, gatewayInputFlow, deviceRepository, timeProvider)` chạy ngoài `network: Mutex`, cài đặt đầy đủ `D1`–`D8`.
- Kết nối trong `SmartHomeViewModel.kt`:
  - Khởi tạo `CloudGatewaySync` nếu `cloudComponents.deviceRepository != null`.
  - Tạo `gatewayInputFlow` ánh xạ từ `_state` (`.map { it.toGatewayInput() }.flowOn(Dispatchers.Default).distinctUntilChanged()`).
  - Đăng ký `CloudDeviceUiBridgeRegistry` trong `init` và `unregister` + `gatewaySync.clear()` trong `onCleared()`.
- Tạo unit test `CloudGatewaySyncTest.kt` dùng `FakeCloudDeviceRepository` và `TestScope`.

### Ngoài phạm vi
- Không tạo `commands` hoặc `acks` (cấm Remote Command).
- Không triển khai đọc dữ liệu Cloud fallback (`CloudFallbackObserver` thuộc Prompt `05`).
- Không sửa các màn hình UI (`HomeScreen`, `SettingsScreen`, `CloudHomeSection` thuộc Prompt `06`/`07`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`

### Thêm mới (`NEW`)
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceUiBridge.kt`
2. `app/src/main/java/com/example/smarthomeai/cloud/CloudGatewaySync.kt`
3. `app/src/test/java/com/example/smarthomeai/cloud/CloudGatewaySyncTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbPaths.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt` *(trừ trường hợp phát hiện thiếu bất khả kháng → phải dừng theo mục 19)*
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`, `AuthRepository.kt`, `Esp32Client.kt`, `Storage.kt`, `Models.kt`, `Security.kt`, `HardwareStatus.kt`
- Tất cả file UI trong `app/src/main/java/com/example/smarthomeai/ui/**`.
- Tất cả file test B3/Smoke hiện có; `cloud-rules/**`; `esp32/**`; `app/build.gradle.kts`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `02` (`F4`) và Prompt `03` (`F2a`) đã PASS.
- Làm đầu vào bắt buộc cho: **Prompt `05` (F3)** và **Prompt `06` (F5a)**.

---

## 9. Kiến trúc triển khai

```text
SmartHomeViewModel._state
   └─ .map { GatewayInputSnapshot } .distinctUntilChanged() (trên Dispatchers.Default, KHÔNG chạm network Mutex)
         │
         ▼
CloudGatewaySync (viewModelScope, IO) ◄── cloudManager.state (collectLatest theo SyncSessionKey)
   ├─ SyncSessionKey(uid, homeId, isReadyOwner)
   ├─ Phase 1: resolveOrRegisterDevice(uid, homeId) -> active deviceId / RevokedOnly (BLOCKED)
   ├─ Phase 2: init session -> readConfig() + readRecentEventCursors(limit = 100)
   └─ Phase 3: vòng lặp phản ứng snapshot mới / heartbeat / retry backoff:
        ├─ state/gateway  -> RtdbDeviceRepository.publishGatewayState(...)
        ├─ config         -> RtdbDeviceRepository.publishConfig(...) (nếu khác & rev+1)
        ├─ telemetry      -> RtdbDeviceRepository.publishTelemetry(...) (chỉ mẫu mới nhất)
        └─ events         -> RtdbDeviceRepository.publishEvent(...) (tối đa 20/chu kỳ, seq tăng dần)
         │
         ▼
CloudDeviceUiBridge.gatewaySync: StateFlow<GatewaySyncUiState> ──► CloudDeviceUiBridgeRegistry
```

---

## 10. Luồng dữ liệu

1. `CloudGatewaySync` quan sát `cloudStateFlow.map { SyncKey(it.currentUid, it.selectedHomeId, it.status == CloudStatus.READY && it.selectedHome?.role == CloudRole.OWNER) }.distinctUntilChanged().collectLatest { key -> ... }`.
2. Khi `!key.ready || key.uid == null || key.homeId == null`: đặt phase `WAITING_HOME` (hoặc `IDLE` khi `SIGNED_OUT`), dừng toàn bộ coroutine sync cũ do `collectLatest` tự động hủy.
3. Khi `key` hợp lệ:
   - Đặt `REGISTERING`, gọi `resolveOrRegisterDevice(uid, homeId)`.
   - Nếu `RevokedOnly`: đặt `BLOCKED` (`"Thiết bị Cloud đã bị thu hồi"`) và dừng phiên sync hiện tại.
   - Nếu `Active(device, hasMultipleActive)`: giữ `activeDeviceId = device.id`; nếu `hasMultipleActive` gắn `warningMessage` (không xóa node nào).
   - Khởi tạo phiên: gọi `readConfig` (lưu `lastKnownConfig` & `lastRev`) và `readRecentEventCursors` (lưu `cursorMap[cloudStream]`).
   - Sau đó `collectLatest` trên luồng input (kết hợp tick `60_000L` cho heartbeat `state/gateway` + retry backoff khi có lỗi tạm thời).

---

## 11. Logic nghiệp vụ

1. **Quy tắc `state/gateway` (D4):**
   - Chỉ đẩy `online = true` khi `input.esp32Online && (nowMs - input.lastSeen <= 5_000L)`.
   - Chỉ ghi khi 8 trường giá trị (`light`, `door`, `alarm`, `motion`, `monitoring`, `temp`, `hum`, `mode`) thay đổi so với lần ghi thành công gần nhất (chờ ít nhất `2_000L` ms giữa 2 lần ghi liên tiếp) **HOẶC** đã quá `60_000L` ms từ lần ghi `online = true` gần nhất (heartbeat).
   - Theo dõi cờ `hasReportedOnlineInSession: Boolean`: nếu từng ghi `online = true` trong phiên này và sau đó `input.esp32Online == false`, ghi **đúng 1 lần** `online = false` với 8 trường hợp lệ gần nhất, chuyển phase UI sang `PAUSED_LAN_OFFLINE`, và đặt lại `hasReportedOnlineInSession = false`.
   - Nếu mới mở app mà LAN đang offline (`hasReportedOnlineInSession == false`), **không** ghi `online = false` thừa lên RTDB; chỉ chuyển phase `PAUSED_LAN_OFFLINE`.
2. **Quy tắc `telemetry` (D5):**
   - Đọc phần tử cuối `latestSample` trong `input.latestSample` (được `SmartHomeViewModel.applySensor` cập nhật 60s/lần).
   - Tính `recordId = RtdbPaths.telemetryRecordId(sample.timestamp)` (UTC `yyyyMMddHHmm_g`).
   - Nếu khác `lastPublishedTelemetryId` trong phiên → gọi `publishTelemetry`. Thành công (hoặc `ALREADY_EXISTS`) thì cập nhật `lastPublishedTelemetryId = recordId`. Không backfill mẫu cũ.
3. **Quy tắc `events` (D3):**
   - Chuyển từng `DeviceEvent` trong `input.recentEvents` (sắp xếp `seq` tăng dần) sang `cloudStream = CloudMapper.toCloudStream(event.stream)`.
   - Lọc các event có `event.seq > (cursorMap[cloudStream] ?: 0L)`.
   - Giới hạn **tối đa 20 event mỗi chu kỳ**, ghi tuần tự từng cái qua `publishEvent`.
   - Khi trả `Ok(WRITTEN)` hoặc `Ok(ALREADY_EXISTS)`: cập nhật ngay `cursorMap[cloudStream] = event.seq`. Nếu trả `Err(...)` thì dừng batch tại đó để chu kỳ sau đẩy tiếp (đảm bảo không nhảy cóc event chưa ghi!).
4. **Quy tắc `config` (D6):**
   - So sánh `ConfigValues(temperatureLimit, humidityLimit, coldLimit, autoAlarm)` với `lastKnownConfig`.
   - Nếu chưa từng có config hoặc giá trị khác → gọi `publishConfig` với `rev = (lastRev ?: 0L) + 1L`.
   - Nếu gặp `PermissionDenied` (do `rev` bị thay đổi đồng thời bởi phiên khác): đọc lại `readConfig` **1 lần**, tính lại `rev = (mới ?: 0L) + 1L` và thử lại **1 lần**; nếu vẫn lỗi thì báo `ERROR`.
5. **Quyền `MEMBER` không tự ghi (Defense-in-depth):**
   - Trong mô hình một chủ sở hữu (`F1`), chỉ khi `role == CloudRole.OWNER` mới kích hoạt `CloudGatewaySync`.

---

## 12. Xử lý lỗi và concurrency

1. **Backoff có giới hạn (D7):**
   - Khi nhận `CloudError.Offline` hoặc `CloudError.Timeout`: giữ trạng thái `ERROR(error, msg)`, tăng dần độ trễ theo dãy `[5_000L, 10_000L, 20_000L, 40_000L, 60_000L]` (trần `60_000L`), reset về index `0` ngay khi có 1 lượt đồng bộ thành công.
2. **Dừng không tự retry khi lỗi cứng:**
   - Khi nhận `CloudError.PermissionDenied`, `CloudError.InvalidData`, `CloudError.Unauthenticated`: đặt trạng thái `ERROR` (hoặc `BLOCKED` nếu device `RevokedOnly`) và **dừng tự retry**; chỉ thử lại khi `SyncSessionKey` thay đổi, người dùng gọi `retrySync()`, hoặc thay đổi sang phiên mới.
3. **Cô lập Mutex & Thread:**
   - Chạy toàn bộ I/O RTDB trong `CloudGatewaySync` trên coroutine riêng của `viewModelScope` + `Dispatchers.IO`.
   - **Tuyệt đối không** gọi `network.withLock` hoặc truyền `network` vào `CloudGatewaySync`.
4. **An toàn Hủy & Đổi phiên:**
   - Dùng `collectLatest` kết hợp kiểm tra `coroutineContext.ensureActive()` / `isActive`; luôn rethrow `CancellationException`.

---

## 13. Bất biến cần bảo toàn

- **LAN-first 100%:** Thời gian phản hồi thao tác LAN (`setDevice`, `setMode`, `updateClothesline`, `refreshLocked`) không bị trễ dù RTDB mất mạng hay timeout.
- **0 Remote Command / ACK:** Không tạo listener hay handler nào dưới `commands/` hoặc `acks/`.
- **0 `deviceKey` / IP LAN / MAC / Face vectors** trong `GatewayInputSnapshot` và mọi hàm sync.
- **0 thay đổi** `CloudRepository.kt`, `CloudHomeManager.kt`, `MainActivity.kt`, `Storage.kt`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `CloudGatewaySync` đăng ký/tìm lại device `@ACTIVE` trước khi ghi `deviceData`, xử lý đúng `RevokedOnly` (`BLOCKED`) và `hasMultipleActive` (cảnh báo, không xóa).
2. Hủy sạch job cũ và không ghi nhầm khi `uid` đổi, đăng xuất, hoặc mất quyền nhà.
3. Không ghi `state/gateway` khi `esp32Online == false` (trừ duy nhất 1 lần `online = false` sau khi từng online); không gửi lại `state` nếu dữ liệu không đổi và chưa tới heartbeat 60s.
4. `events` đẩy theo thứ tự `seq` tăng dần, không vượt 20/batch, cập nhật cursor khi `WRITTEN`/`ALREADY_EXISTS`, dừng đúng chỗ khi lỗi thật.
5. `telemetry` ghi đúng 1 lần theo phút UTC từ mẫu mới nhất, không backfill.
6. Backoff `5s..60s` hoạt động với `Offline`/`Timeout` và dừng với `PermissionDenied`.
7. `CloudGatewaySyncTest.kt` phủ toàn bộ các kịch bản trên bằng `FakeCloudDeviceRepository` và `StandardTestDispatcher`.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → `151`+ test cũ + `CloudGatewaySyncTest` **PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. Kiểm tra thêm một Android Instrumented Test (nếu Nhánh B viết kiểm chứng `SmartHomeViewModel` hoặc tích hợp `RtdbDeviceRepository` + `CloudGatewaySync` với Local RTDB Emulator `demo-smarthomeai`) bằng `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"`.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chạm `SmartHomeViewModel.kt` + 3 file mới ở mục 6.
2. Grep `network.withLock` trong `SmartHomeViewModel.kt`: xác nhận **không có** bất kỳ lời gọi `cloudGatewaySync` hoặc `deviceRepository` nào bên trong khối `network.withLock`.
3. Grep `com.google.firebase` trong `SmartHomeViewModel.kt`, `CloudGatewaySync.kt`, `CloudDeviceUiBridge.kt` → **0 matches**.
4. Grep `deviceKey` trong `CloudGatewaySync.kt`, `CloudDeviceUiBridge.kt` → **0 matches** (ngoài comment bảo vệ nếu có).

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Triển khai CloudGatewaySync & CloudDeviceUiBridge đồng bộ trạng thái ESP32 lên RTDB ngoài network Mutex (F2b).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy JVM test, assembleDebug và Emulator).
SCOPE: OK
RISKS: None | <nếu có>
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- `git revert <commit_hash_prompt_04>`.
- Tắt hoàn toàn đồng bộ nền Gateway mà không ảnh hưởng B1–B3, F1, F4 hay LAN.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. `git add app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceUiBridge.kt app/src/main/java/com/example/smarthomeai/cloud/CloudGatewaySync.kt app/src/test/java/com/example/smarthomeai/cloud/CloudGatewaySyncTest.kt`
3. Kiểm tra `git diff --cached --stat`.
4. Commit tiếng Việt:
   `git commit -m "Đồng bộ trạng thái ESP32 lên Firebase"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Phải sửa `Esp32Client.kt`, `Storage.kt`, `SmartHomeESP32.ino` hoặc `database.rules.*` để đồng bộ được.
- Bất kỳ thao tác đồng bộ Cloud nào đòi hỏi giữ `network: Mutex` của LAN.
- Phát hiện thiếu method trên `CloudDeviceRepository` thuộc file đã khóa cần đánh giá lại phạm vi trước khi mở rộng.
