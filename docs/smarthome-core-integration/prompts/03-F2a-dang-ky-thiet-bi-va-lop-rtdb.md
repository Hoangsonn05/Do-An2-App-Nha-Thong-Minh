# PROMPT 03 — F2a: ĐĂNG KÝ THIẾT BỊ & XÂY DỰNG LỚP DỮ LIỆU RTDB CHO GATEWAY

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Firebase RTDB Architect & Senior Android Engineer xây dựng toàn bộ tầng model, mapper (`CloudMapper`), path builder (`RtdbPaths`), interface `CloudDeviceRepository` và implementation `RtdbDeviceRepository` tuân thủ 100% `database.rules.src.json` hiện tại (`CD-2`), cùng bộ unit test thuần Kotlin (không chạy Gradle/Emulator ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc LẬP):** Là Independent QA / Rules Test Engineer đọc diff + handoff Nhánh A, bổ sung file test Rules Emulator mới (`cloud-rules/test/gateway-payload.test.mjs`) để chứng minh payload thực tế của `CloudMapper` khớp Rules hiện tại (`demo-smarthomeai`), và chạy toàn bộ JVM + Rules Build + Rules Emulator + Debug Build.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (commit kết thúc của Prompt `02`) và `git status --porcelain` sạch.
2. Đọc lại `cloud-rules/database.rules.src.json` (dòng 80–272), `esp32/SmartHomeESP32/SmartHomeESP32.ino` (dòng 610–875), `CloudModels.kt`, `CloudSession.kt`, `FirebaseProvider.kt`, `RtdbPaths.kt`, `RtdbCloudRepository.kt`, `CloudErrorMapper.kt`.
3. Đối chiếu từng ràng buộc Rules & firmware với yêu cầu mục 4–13.
4. Chốt thiết kế schema mapper & repository theo mục 9–12.
5. Triển khai các file mục 6 (kèm viết `CloudMapperTest.kt`, `RtdbDevicePathTest.kt`, `FakeCloudDeviceRepository.kt`, **không chạy**).
6. Không sửa `CloudRepository.kt`, `RtdbCloudRepository.kt`, `SmartHomeViewModel.kt`, hay `cloud-rules/`.
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
3. Kiểm tra từng trường payload trong `CloudMapper` đối chiếu từng rule `.validate` trong `database.rules.src.json`.
4. Tạo file test Rules mới `cloud-rules/test/gateway-payload.test.mjs` (KHÔNG sửa `database.rules.*` và `rules.test.mjs`), chạy `npm run test:build`, `npm run test:rules`, `node --test test/gateway-payload.test.mjs` (chỉ trên `demo-smarthomeai`), và chạy `:app:testDebugUnitTest`, `:app:assembleDebug`.
5. **KHÔNG** sửa code Kotlin trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được trong code Nhánh A.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu có lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Nếu chỉ thêm `cloud-rules/test/gateway-payload.test.mjs` (và test pass), chỉ tạo commit test-only tiếng Việt và `KHÔNG PUSH`.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01` và `02` đã PASS.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `02`.
- **Working tree:** Sạch.

---

## 3. Trạng thái hiện tại đã xác minh

1. `cloud-rules/database.rules.src.json` (`LOCKED` theo `CD-2`):
   - `homes/$homeId/devices/$deviceId` (dòng 80–110): tạo mới bởi `@OWNER`, `$deviceId` khớp `^[A-Za-z0-9_-]{8,64}$`, bắt buộc `name` (1..60, không chứa `'deviceKey'`), `model` (1..40), `status` (`'active'` | `'revoked'`), `createdAt === now`, `createdBy === auth.uid`, `$other: false`.
   - `state/gateway` (dòng 115–146): `@MEMBER && @ACTIVE && newData.exists()`, bắt buộc `online`, `reportedBy`, `receivedAt === now`, `values` (chỉ gồm tối đa 8 khóa: `light`, `door`, `alarm`, `motion`, `monitoring` bool; `temp` `>-50 && <150`; `hum` `0..100`; `mode` chuỗi `1..16`, `$other: false`), tùy chọn `deviceTs`.
   - `config` (dòng 181–193): `@OWNER && @ACTIVE`, bắt buộc `rev === (data.exists() ? data.child('rev').val() + 1 : 1)`, `updatedBy`, `updatedAt === now`, `tempLimit` `0..80`, `humLimit` `0..100`, `coldLimit` `-10..40`, `autoAlarm` bool, `$other: false`.
   - `telemetry/$recordId` (dòng 195–212): create-only bởi `@MEMBER && @ACTIVE`, `$recordId` khớp `^[0-9]{12}_g$`, `source === 'gateway'`, `reportedBy`, `receivedAt === now`, `values { temp, hum }`, tùy chọn `deviceTs`.
   - `events/$eventId` (dòng 255–272): `.indexOn: ["receivedAt"]`, create-only bởi `@MEMBER && @ACTIVE`, `$eventId` khớp `^(g|d)_[A-Za-z0-9]{1,16}_[0-9]{1,10}$` và bằng `'g_' + stream + '_' + seq`, `type` 1..32, `severity` (`info|warn|critical`), `stream` khớp `^[A-Za-z0-9]{1,16}$`, `seq` nguyên `0..9999999999`, `source === 'gateway'`, `reportedBy`, `receivedAt === now`, tùy chọn `deviceTs`, `detail` ≤120 không chứa `'deviceKey'`.
2. `SmartHomeESP32.ino:871-874` (`LOCKED`): `streamId = hex(efuseMac) + "-" + hex(journal.generation)` chứa dấu `-` và có thể dài >16 ký tự ⇒ **không thể đưa thô vào trường `stream` của RTDB Rules**.
3. `CloudRepository.kt` có 3 test double B3 implement trực tiếp ⇒ **không được thêm hàm vào `CloudRepository`**.
4. `CloudSession.kt:26`: `data class CloudComponents(val session: CloudSession, val repository: CloudRepository)` được tạo tại `FirebaseProvider.kt:167`.

---

## 4. Mục tiêu cụ thể

Xây dựng tầng hợp đồng thuần Kotlin và tầng triển khai RTDB cho thiết bị Gateway ESP32:
1. Mở rộng `RtdbPaths` với đầy đủ đường dẫn `devices` và `deviceData` (`state/gateway`, `config`, `telemetry`, `events`).
2. Xây dựng `CloudDeviceModels.kt` và `CloudMapper.kt` thuần Kotlin (không import Firebase) chuẩn hóa payload khớp 100% whitelist Rules hiện tại, băm `streamId` sang `cloudStream` (16 ký tự hex SHA-256), sinh khóa telemetry UTC `yyyyMMddHHmm_g`, phân loại `severity`, lọc sạch từ khóa `'deviceKey'`.
3. Tạo interface `CloudDeviceRepository` và implementation `RtdbDeviceRepository` thực hiện đăng ký/tìm lại device `@ACTIVE` an toàn, ghi `state/gateway`, ghi/đọc `config`, ghi `telemetry`, ghi `events` (có kiểm tra tồn tại khi gặp `PermissionDenied` do create-only), đọc cursor 100 event cuối.
4. Nối `deviceRepository: CloudDeviceRepository? = null` vào cuối `CloudComponents` và `CloudFoundationFactory.create(context)` mà không phá chữ ký cũ.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Bổ sung path builders & validators trong `RtdbPaths.kt` (hàm `telemetryRecordId(epochMs)`, `eventId(cloudStream, seq)`, `homeDevicesPath`, `homeDevicePath`, `deviceGatewayStatePath`, `deviceConfigPath`, `deviceTelemetryPath`, `deviceTelemetryRecordPath`, `deviceEventsPath`, `deviceEventPath`).
- Tạo `CloudDeviceModels.kt` chứa: `DeviceStatus` (`ACTIVE`, `REVOKED`), `CloudDevice`, `DeviceResolveResult` (`Active`, `RevokedOnly`, `MultipleActiveWarning`), `GatewaySnapshot`, `GatewayEventInput`, `CloudConfigState`, `EventSeverity`, `CreateOnlyWriteOutcome` (`WRITTEN`, `ALREADY_EXISTS`).
- Tạo `CloudMapper.kt` (pure Kotlin, nhận `timestampValue: Any` để unit test không phụ thuộc Firebase `ServerValue.TIMESTAMP`).
- Tạo `CloudDeviceRepository.kt` (interface thuần Kotlin, không import Firebase SDK).
- Tạo `RtdbDeviceRepository.kt` (dùng `FirebaseProvider`, `runTimedWrite`, `suspendCancellableCoroutine`, `provider.uid == expectedUid`).
- Cập nhật `CloudComponents` (`CloudSession.kt`) và `CloudFoundationFactory` (`FirebaseProvider.kt`).
- Viết unit test mới: `CloudMapperTest.kt`, `RtdbDevicePathTest.kt`, `FakeCloudDeviceRepository.kt` (dùng chung cho test Prompt 04/05).

### Ngoài phạm vi
- Không viết vòng lặp đồng bộ nền `CloudGatewaySync` (thuộc Prompt `04`).
- Không viết `CloudFallbackObserver` hoặc `observeGatewayState`/`observeRecentEvents` (thuộc Prompt `05`).
- Không sửa `SmartHomeViewModel.kt`, `CloudRepository.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`, hay bất kỳ màn hình UI nào.
- Không sửa Rules hoặc thêm trường mưa/giàn phơi vào `state/gateway/values`.

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/cloud/RtdbPaths.kt` *(chỉ thêm hàm mới, không đổi hàm cũ)*
2. `app/src/main/java/com/example/smarthomeai/cloud/CloudSession.kt` *(thêm `val deviceRepository: CloudDeviceRepository? = null` vào cuối `CloudComponents`)*
3. `app/src/main/java/com/example/smarthomeai/cloud/FirebaseProvider.kt` *(chỉ nối `deviceRepository = RtdbDeviceRepository(provider)` trong `CloudFoundationFactory.create`)*

### Thêm mới (`NEW`)
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceModels.kt`
2. `app/src/main/java/com/example/smarthomeai/cloud/CloudMapper.kt`
3. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceRepository.kt`
4. `app/src/main/java/com/example/smarthomeai/cloud/RtdbDeviceRepository.kt`
5. `app/src/test/java/com/example/smarthomeai/cloud/CloudMapperTest.kt`
6. `app/src/test/java/com/example/smarthomeai/cloud/RtdbDevicePathTest.kt`
7. `app/src/test/java/com/example/smarthomeai/cloud/FakeCloudDeviceRepository.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`
- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `cloud-rules/database.rules.src.json`, `cloud-rules/database.rules.json`, `cloud-rules/test/rules.test.mjs`
- Toàn bộ file `ui/**`, `data/**`, `esp32/**`, `app/build.gradle.kts`, `AndroidManifest.xml`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `01` (`F1`) đã chốt cách lấy `selectedHomeId`; khuyến nghị chạy sau Prompt `02` để giữ tuyến Git tuần tự.
- Làm đầu vào bắt buộc cho: **Prompt `04` (F2b)** và **Prompt `05` (F3)**.

---

## 9. Kiến trúc triển khai

1. **`CloudDeviceRepository` (interface thuần Kotlin):**
   ```kotlin
   interface CloudDeviceRepository {
       suspend fun resolveOrRegisterDevice(expectedUid: String, homeId: String): CloudResult<DeviceResolveResult>
       suspend fun publishGatewayState(expectedUid: String, homeId: String, deviceId: String, payload: Map<String, Any>): CloudResult<Unit>
       suspend fun readConfig(expectedUid: String, homeId: String, deviceId: String): CloudResult<CloudConfigState?>
       suspend fun publishConfig(expectedUid: String, homeId: String, deviceId: String, payload: Map<String, Any>): CloudResult<Unit>
       suspend fun publishTelemetry(expectedUid: String, homeId: String, deviceId: String, recordId: String, payload: Map<String, Any>): CloudResult<CreateOnlyWriteOutcome>
       suspend fun readRecentEventCursors(expectedUid: String, homeId: String, deviceId: String, limit: Int = 100): CloudResult<Map<String, Long>>
       suspend fun publishEvent(expectedUid: String, homeId: String, deviceId: String, eventId: String, payload: Map<String, Any>): CloudResult<CreateOnlyWriteOutcome>
   }
   ```
   *(Lưu ý: Khi chuyển `CloudMapper` build payload sang tầng repository hoặc truyền snapshot/input model thay vì `Map<String, Any>` cũng hợp lệ, miễn `ServerValue.TIMESTAMP` chỉ nằm trong `RtdbDeviceRepository`, còn `CloudMapper` thuần Kotlin có thể kiểm chứng 100% trong `CloudMapperTest`.)*
2. **Tách `RtdbDeviceRepository` khỏi `RtdbCloudRepository`:** Không chạm file cũ để giữ 0% rủi ro hồi quy B1–B3. Tái sử dụng `CloudErrorMapper`, `RtdbPaths`, `RtdbCloudRepository.safeString` / `safeLong` (có thể giữ hàm parse nội bộ an toàn ngay trong `RtdbDeviceRepository` nếu không sửa `RtdbCloudRepository`).

---

## 10. Luồng dữ liệu

- **Đăng ký thiết bị:** `resolveOrRegisterDevice(expectedUid, homeId)` → kiểm tra `provider.isConfigured` + `provider.uid == expectedUid` → đọc `homes/{homeId}/devices` một lần → áp dụng D1 → nếu cần tạo mới dùng `devicesRef.push().key` (đã khớp regex `8..64`) → `setValue(devicePayload).await()`.
- **Ghi `state/gateway` / `config` / `telemetry` / `events`:** `CloudMapper` chuyển từ domain input → `Map<String, Any>` (truyền `ServerValue.TIMESTAMP` từ `RtdbDeviceRepository`) → ghi qua `runTimedWrite(timeoutMs = 10_000L)`.
- **Create-only (`telemetry`, `events`):** Khi `setValue` bị `PermissionDenied` → đọc một lần tại chính `recordPath` / `eventPath` đó: nếu `snapshot.exists()` trả `CloudResult.Ok(CreateOnlyWriteOutcome.ALREADY_EXISTS)` (dedup an toàn); nếu không tồn tại trả `CloudResult.Err(CloudError.PermissionDenied, ...)`.

---

## 11. Logic nghiệp vụ

1. **D1 — Quy tắc `resolveOrRegisterDevice`:**
   - Parse toàn bộ child dưới `homes/{homeId}/devices`.
   - Lọc thiết bị có `status == "active"`:
     - Nếu `>= 1` bản `active`: sắp xếp tăng dần theo `(createdAt ?: Long.MAX_VALUE, deviceId)` và chọn phần tử đầu tiên. Nếu `activeDevices.size > 1`, gắn cờ `hasMultipleActive = true` trong `DeviceResolveResult` (không tự xóa bất kỳ device nào).
     - Nếu `0` bản `active` nhưng có `>= 1` bản `revoked`: trả `DeviceResolveResult.RevokedOnly(revokedIds)` — **tuyệt đối không tạo device mới**.
     - Nếu nhánh `devices` rỗng hoàn toàn (`!snapshot.exists()` hoặc 0 child): tạo đúng 1 device bằng `push().key`, `name = "ESP32 Smart Home"`, `model = "esp32-smarthome"`, `status = "active"`, `createdAt = ServerValue.TIMESTAMP`, `createdBy = expectedUid` (0 `deviceKey`, 0 IP, 0 MAC).
2. **D2 — Chuẩn hóa `cloudStream` & `Event`:**
   - `toCloudStream(rawStream: String): String`: tính `MessageDigest.getInstance("SHA-256")` trên UTF-8 của `rawStream.trim()`, chuyển hex chữ thường và lấy **16 ký tự đầu** (`^[0-9a-f]{16}$`, luôn hợp lệ regex Rules `^[A-Za-z0-9]{1,16}$`). Ném `IllegalArgumentException` nếu `rawStream.isBlank()`.
   - `eventId = "g_${cloudStream}_${seq}"` với `seq in 0L..9_999_999_999L`.
   - `type = kind.trim().take(32)` (bắt buộc không rỗng).
   - `detail = "boot=$boot;incident=$incident".take(120)` và kiểm tra **không chứa `"deviceKey"`** (ignoreCase).
   - `severity`:
     - `"critical"`: `alarm_started`, `motion_alarm`, `proximity_alarm`
     - `"warn"`: `rain_started`, `servo_overdue`, `servo_blocked`, `exit_waiting`, `motion_entry`, `motion_waiting`, `alarm_silent`, `clothes_rain_retract`, `proximity_detected`, `motion_detected`
     - `"info"`: mọi `kind` hợp lệ còn lại.
   - `deviceTs`: chỉ thêm vào payload nếu `timestamp > 0L`.
   - `source = "gateway"`, `reportedBy = uid`, `receivedAt = timestampValue`.
3. **D4 & CD-2 — Mapping `state/gateway`:**
   - Chỉ đưa vào `values` đúng **8 khóa được phép**: `light`, `door`, `alarm`, `motion`, `monitoring` (`Boolean`); `mode` (`String`, lấy `HomeMode.name`, dài 1..16); `temp` (chỉ thêm khi khác `null` và trong `-49..149`); `hum` (chỉ thêm khi khác `null` và trong `0..100`).
   - **Cấm tuyệt đối** đưa `raining`, `clothesRetracted`, `rainRetractEnabled`, `obstacle`, `distanceCm`, `deviceKey`, `esp32Ip` vào `values` (`CD-2`).
   - Top-level payload: `online` (`Boolean`), `reportedBy` (`uid`), `receivedAt` (`timestampValue`), `values`, và `deviceTs` (nếu `lastSeen > 0L`).
4. **D5 — Mapping `telemetry`:**
   - Khóa `yyyyMMddHHmm_g` tính bằng `SimpleDateFormat("yyyyMMddHHmm", Locale.US)` với **`TimeZone.getTimeZone("UTC")`** từ `sample.timestamp`.
   - Chỉ tạo khi có ít nhất `temp` (trong `-49..149`) hoặc `hum` (trong `0..100`). Payload: `source = "gateway"`, `reportedBy = uid`, `receivedAt = timestampValue`, `deviceTs = sample.timestamp`, `values = mapOf("temp" to ..., "hum" to ...)`.
5. **D6 — Mapping `config`:**
   - Payload gồm đúng 7 khóa Rules dòng 184: `rev` (`previousRev + 1`, tối thiểu `1`), `updatedBy = uid`, `updatedAt = timestampValue`, `tempLimit` (`coerceIn(0, 80)` hoặc `require` trong dải), `humLimit` (`0..100`), `coldLimit` (`-10..40`), `autoAlarm` (`Boolean`).
6. **`readRecentEventCursors`:**
   - Query `db.getReference(RtdbPaths.deviceEventsPath(homeId, deviceId)).orderByChild("receivedAt").limitToLast(limit)`.
   - Duyệt children: đọc `stream` (chuỗi khớp `^[A-Za-z0-9]{1,16}$`) và `seq` (`safeLong`), tính `maxOf` theo từng `cloudStream` trả về `Map<String, Long>`.

---

## 12. Xử lý lỗi và concurrency

- Mọi hàm `suspend` trong `RtdbDeviceRepository` phải kiểm tra trước:
  1. `!provider.isConfigured || db == null` → `CloudResult.Unavailable`.
  2. `val uid = provider.uid; if (uid != expectedUid || !RtdbPaths.isValidUid(uid))` → `CloudResult.Err(CloudError.Unauthenticated, ...)`.
  3. Kiểm tra lại `provider.uid != expectedUid` ngay sau khi thao tác mạng hoàn tất trước khi trả `CloudResult.Ok` (chống race đổi tài khoản giữa chừng).
- Bọc mọi write và single-value read bằng `withTimeout(timeoutMs)` (`10_000L`), chỉ bắt `TimeoutCancellationException` thành `CloudError.Timeout` và rethrow mọi `CancellationException` khác.
- Parse `DataSnapshot` an toàn bằng `safeString`/`safeLong`/`safeBoolean`, không dùng `getValue(Class)` (`MIN-02`).

---

## 13. Bất biến cần bảo toàn

- **0 import Firebase** trong `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudDeviceRepository.kt`, `CloudSession.kt`, `RtdbPaths.kt`.
- **0 `deviceKey`**, password, token, IP LAN, MAC hoặc dữ liệu sinh trắc học trong mọi payload RTDB.
- **0 chỉnh sửa** `cloud-rules/database.rules.*` (`CD-2`) và `esp32/**`.
- **0 chỉnh sửa** `CloudRepository.kt`, `RtdbCloudRepository.kt` (bảo toàn 3 test double B3).
- `FirebaseProvider.kt` không được gọi `setPersistenceEnabled(...)` và không hardcode URL `appaintm`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `RtdbPaths` tạo và validate đúng đường dẫn `devices`, `state/gateway`, `config`, `telemetry`, `events`.
2. `CloudMapper` chuyển đổi đủ 5 loại payload (`device`, `state/gateway`, `config`, `telemetry`, `event`), băm `cloudStream` đúng 16 hex từ `streamId` chứa dấu `-`, tính `telemetry` UTC, phân loại đủ 3 mức `severity`, lọc `deviceKey`, loại bỏ các trường ngoài whitelist `CD-2`.
3. `RtdbDeviceRepository` triển khai đầy đủ 7 hàm trên `CloudDeviceRepository` với `expectedUid` guard, `runTimedWrite`, và phân biệt create-only `ALREADY_EXISTS` vs `PermissionDenied`.
4. `CloudComponents` giữ tương thích 2 tham số cũ (default `deviceRepository = null`).
5. `CloudMapperTest.kt` và `RtdbDevicePathTest.kt` phủ đầy đủ edge cases biên Rules (`temp` ngoài `(-50,150)`, `hum` ngoài `0..100`, `streamId` chứa `-` & >16 chars, `deviceKey` trong tên, UTC boundary `23:59`/`00:00`, `rev` config, `deviceTs` = 0 bị bỏ qua).

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → `151`+ test cũ + `CloudMapperTest` + `RtdbDevicePathTest` **PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. Tạo file mới `cloud-rules/test/gateway-payload.test.mjs` (không sửa `database.rules.*` hay `rules.test.mjs`), xác nhận trên Emulator `demo-smarthomeai`:
   - Tạo `devices/{id}` không có `deviceKey` → ALLOW; có `deviceKey` trong `name` hoặc trường thừa → DENY.
   - Ghi `state/gateway` từ `CloudMapper` (đã xóa field mưa/giàn phơi) khi `@ACTIVE` → ALLOW; ghi thiếu `@ACTIVE` hoặc thêm trường `raining`/`clothesRetracted` → DENY.
   - Ghi `config` với `rev=1`, sau đó `rev=2` → ALLOW; ghi `rev=1` lần 2 → DENY.
   - Ghi `telemetry/{yyyyMMddHHmm}_g` tạo mới → ALLOW; ghi đè cùng khóa → DENY.
   - Ghi `events/g_{16hex}_{seq}` tạo mới → ALLOW; dùng `stream` có dấu `-` hoặc dài 17 ký tự → DENY; ghi đè cùng khóa → DENY; UID khác (không phải member) → DENY.
4. Chạy `cd cloud-rules; npm run test:build; npm run test:rules` và chạy file test mới qua `run-emulator.mjs` → **100% PASS**.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ đúng các file trong mục 6.
2. Kiểm tra 0 import `com.google.firebase` trong `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudDeviceRepository.kt`, `RtdbPaths.kt`, `CloudSession.kt`.
3. Grep `"deviceKey"` toàn thư mục `app/src/main/java/com/example/smarthomeai/cloud/` → chỉ xuất hiện trong chuỗi guard/kiểm tra từ chối hoặc comment giải thích, không có key map nào tên `deviceKey`.
4. Grep `setPersistenceEnabled` và `appaintm` trong `app/src/main/` → 0 matches thực thi.
5. Kiểm tra `CloudRepository.kt` và `RtdbCloudRepository.kt` không có thay đổi: `git diff -- app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt` rỗng.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Xây dựng tầng model, CloudMapper, RtdbPaths và RtdbDeviceRepository cho Gateway (F2a).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy JVM test, assembleDebug và Rules Emulator).
SCOPE: OK
RISKS: None | Cần Nhánh B kiểm chứng thực tế trên Local RTDB Emulator (gateway-payload.test.mjs).
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác sạch bằng `git revert <commit_hash_prompt_03>`.
- Không sửa Rules hay dữ liệu Production; chưa có caller runtime nào chạy trong `SmartHomeViewModel`.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. `git add` tường minh các file ở mục 6.
3. `git diff --cached --stat` xác nhận đúng phạm vi.
4. Commit tiếng Việt:
   `git commit -m "Bổ sung đăng ký thiết bị và lớp dữ liệu RTDB cho Gateway"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng (User Decision Required) nếu:
- Phát hiện một ràng buộc trong `database.rules.src.json` từ chối một trường bắt buộc mà không thể chuyển đổi phía client trong `CloudMapper` mà không sửa Rules (`CD-2`).
- Nhánh B chạy `gateway-payload.test.mjs` thấy Rules từ chối payload hợp lệ của `CloudMapper` → Nhánh B **DỪNG**, báo cáo chi tiết và không tự sửa Rules hay implementation.
- Phát hiện cần sửa `CloudRepository.kt` làm gãy test double B3.
