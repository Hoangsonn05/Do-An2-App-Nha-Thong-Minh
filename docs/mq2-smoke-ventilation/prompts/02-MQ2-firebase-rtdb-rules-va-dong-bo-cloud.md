# PROMPT 02 — MQ2-D2: MỞ RỘNG FIREBASE RTDB SECURITY RULES & ĐỒNG BỘ DỮ LIỆU KHÓI MQ-2, CỬA SỔ, QUẠT LÊN CLOUD

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Firebase Realtime Database Security Architect & Senior Kotlin Cloud Engineer cập nhật đồng bộ `database.rules.src.json` + `database.rules.json` (tương thích ngược 100% với payload v1) và mở rộng toàn bộ tầng Cloud Kotlin (`CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`, `FakeCloudDeviceRepository.kt`) để đồng bộ nồng độ khói `smokePpm`, `smokeAlert`, trạng thái `window`, `fan`, ngưỡng `smokeLimit` (`300`), `smokeAuto` và phân loại sự kiện khói `CRITICAL`, kèm viết `CloudSmokeSyncTest.kt` và `cloud-rules/test/smoke-mq2-rules.test.mjs` (không chạy Emulator/Gradle ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent Cloud & Security QA Engineer kiểm tra diff của Nhánh A, chạy `npm run test:build`, `npm run test:rules`, chạy bộ test Rules mới `smoke-mq2-rules.test.mjs` và `gateway-payload.test.mjs` trên Firebase Local Emulator (`demo-smarthomeai`), đồng thời chạy `:app:testDebugUnitTest` và `:app:assembleDebug`.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (khớp commit kết thúc của Prompt `01`) và `git status --porcelain -- app cloud-rules esp32` sạch.
2. Đọc lại `cloud-rules/database.rules.src.json`, `cloud-rules/build-rules.mjs`, `cloud-rules/test/gateway-payload.test.mjs` (dòng 200–376), `CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`, `FakeCloudDeviceRepository.kt`.
3. Đối chiếu từng ràng buộc `.validate` trong Rules với các test cũ (`rules.test.mjs` & `gateway-payload.test.mjs`) và mục tiêu Mục 4–13.
4. Chốt schema mở rộng backward-compatible theo Mục 9–12.
5. Triển khai các file thuộc Mục 6, kèm viết `CloudSmokeSyncTest.kt` và `cloud-rules/test/smoke-mq2-rules.test.mjs` (**không chạy**).
6. Không sửa `CloudRepository.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`, `SmartHomeViewModel.kt`, hay các file test Rules cũ.
7. Chỉ chạy **static checks** quy định tại Mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy Firebase Emulator hay `npm test`.**
11. **KHÔNG chạy Gradle build.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất **Handoff Report** đúng mẫu Mục 16.
14. Tạo **01 Git commit tiếng Việt** theo Mục 18 (**TUYỆT ĐỐI KHÔNG `git push`**).
15. **DỪNG** hoàn toàn và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc toàn bộ `git show --stat HEAD` và `git diff HEAD~1..HEAD`.
3. Kiểm tra từng biểu thức `.validate` trong `database.rules.src.json` đối chiếu với `CloudMapper.kt` và `RtdbDeviceRepository.kt`.
4. Chạy `npm run build:rules` + kiểm tra `git diff cloud-rules/database.rules.json` (xác nhận bản build từ `build-rules.mjs` khớp 100%), chạy toàn bộ Rules Emulator (`rules.test.mjs`, `gateway-payload.test.mjs`, `smoke-mq2-rules.test.mjs`) chỉ trên `demo-smarthomeai`, và chạy `:app:testDebugUnitTest`, `:app:assembleDebug`.
5. **KHÔNG** tự sửa code sản xuất hay Rules.
6. **KHÔNG** tự vá lỗi phát hiện được trong code của Nhánh A.
7. Phân loại mọi lỗi phát hiện theo `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Nếu `PASS` hoàn toàn: xác nhận đủ điều kiện chuyển sang **Prompt 03**.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01` (`MQ2-D1`) đã hoàn tất Nhánh A và vượt qua Nhánh B (`PASS`).
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `01` (`"Bổ sung hợp đồng dữ liệu cảm biến khói MQ-2, cửa sổ và quạt"` hoặc commit test-only ngay sau đó nếu Nhánh B Prompt `01` có bổ sung test).
- **Working tree:** `git status --porcelain -- app cloud-rules esp32` rỗng hoàn toàn.

---

## 3. Trạng thái hiện tại đã xác minh

1. `cloud-rules/database.rules.src.json`:
   - `state/gateway/values` (dòng 131–142) & `state/device/values` (dòng 163–174): hiện chỉ cho phép 8 khóa (`light`, `door`, `alarm`, `motion`, `monitoring`, `temp`, `hum`, `mode`), khóa bằng `"$other": { ".validate": false }`.
   - `config` (dòng 181–193): yêu cầu `newData.hasChildren(['rev','updatedBy','updatedAt','tempLimit','humLimit','coldLimit','autoAlarm'])` và khóa `"$other": { ".validate": false }`. Tại `gateway-payload.test.mjs:287`, bài test hiện hữu ghi `config` **chỉ với 7 khóa này** ⇒ không được đưa khóa mới vào `newData.hasChildren([...])` bắt buộc!
   - `telemetry/$recordId/values` (dòng 204–209): hiện chỉ cho phép `temp` và `hum`, khóa `"$other": { ".validate": false }`.
   - `commands/$cmdId/type` (dòng 220–222): hiện chỉ cho phép `'light.set'`, `'door.set'`, `'alarm.set'`, `'mode.set'`, `'clothes.set'`.
2. `CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`:
   - Chưa có trường `smokePpm`, `smokeAlert`, `window`, `fan` trong `GatewaySnapshot`, `GatewayStateSnapshot`, `CloudTelemetrySnapshot`, `GatewayInputSnapshot`.
   - `CloudConfigState` và `CloudMapper.buildConfigPayload` chưa có `smokeLimit` (mặc định `300`) và `smokeAuto` (mặc định `true`).
   - `CloudMapper.CRITICAL_EVENT_KINDS` (`CloudMapper.kt:40-44`) hiện mới có `alarm_started`, `motion_alarm`, `proximity_alarm` — chưa có `smoke_detected` và `smoke_auto_vent`.

---

## 4. Mục tiêu cụ thể

1. Mở rộng `cloud-rules/database.rules.src.json` (và cập nhật khớp 100% sang `cloud-rules/database.rules.json`) hỗ trợ:
   - `smokePpm` (`0..10000`), `smokeAlert` (`bool`), `window` (`bool`), `fan` (`bool`) trong `state/gateway/values` và `state/device/values`.
   - `smokeLimit` (`50..5000`) và `smokeAuto` (`bool`) dạng tùy chọn hợp lệ trong `config`.
   - `smokePpm` (`0..10000`) trong `telemetry/$recordId/values`.
   - `'window.set'` và `'fan.set'` trong `commands/$cmdId/type`.
2. Mở rộng `CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt` với đầy đủ các tham số mặc định ở cuối constructor/hàm để tất cả unit test Cloud cũ (`CloudMapperTest`, `CloudGatewaySyncTest`, `CloudFallbackObserverTest`, `RtdbSnapshotParsingTest`) giữ nguyên 100% khả năng biên dịch và kết quả PASS.
3. Nối dữ liệu khói MQ-2, cửa sổ, quạt và cấu hình khói từ `SmartHomeState.toGatewayInput()` vào `CloudGatewaySync.kt` để tự động đồng bộ `state/gateway`, `config`, `telemetry` và `events` lên Firebase RTDB khi ESP32 online.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Cập nhật `cloud-rules/database.rules.src.json` và `cloud-rules/database.rules.json`.
- Cập nhật `CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`.
- Cập nhật test double `FakeCloudDeviceRepository.kt` để parse/lưu đúng các trường mới khi test sync/fallback.
- Tạo mới `CloudSmokeSyncTest.kt` (JVM) và `cloud-rules/test/smoke-mq2-rules.test.mjs` (Firebase Emulator Rules test).

### Ngoài phạm vi
- Không triển khai gửi lệnh điều khiển từ xa (Remote Command Execution / ACK) từ App qua Cloud xuống ESP32 (vẫn giữ kiến trúc LAN-first điều khiển trực tiếp, chỉ mở sẵn schema `'window.set'`/`'fan.set'` trên Rules cho tương lai).
- Không sửa `SmartHomeViewModel.kt` (thuộc Prompt `03`) hoặc bất kỳ màn hình UI nào (thuộc Prompt `04`).
- Không truy cập hay deploy lên Firebase Production `appaintm` (`B1-D` giữ `LOCKED`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `cloud-rules/database.rules.src.json`
2. `cloud-rules/database.rules.json`
3. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceModels.kt`
4. `app/src/main/java/com/example/smarthomeai/cloud/CloudMapper.kt`
5. `app/src/main/java/com/example/smarthomeai/cloud/RtdbDeviceRepository.kt`
6. `app/src/main/java/com/example/smarthomeai/cloud/CloudGatewaySync.kt`
7. `app/src/test/java/com/example/smarthomeai/cloud/FakeCloudDeviceRepository.kt`

### Thêm mới (`NEW`)
1. `app/src/test/java/com/example/smarthomeai/cloud/CloudSmokeSyncTest.kt`
2. `cloud-rules/test/smoke-mq2-rules.test.mjs`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`
- `app/src/main/java/com/example/smarthomeai/data/**`
- `app/src/main/java/com/example/smarthomeai/ui/**`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `cloud-rules/build-rules.mjs`, `cloud-rules/test/rules.test.mjs`, `cloud-rules/test/gateway-payload.test.mjs`, `cloud-rules/test/build-rules.test.mjs`
- `esp32/**`

---

## 8. Dependency

- **Phụ thuộc đầu vào:** Prompt `01` (`MQ2-D1`) đã PASS (cung cấp `HardwareStatus.smokePpm`, `windowOpen`, `fanOn`, `SmartHomeState.smokeLimit`, `smokeAutoVentilation`, `SensorSample.smokePpm`).
- **Làm đầu vào bắt buộc cho:** **Prompt `03` (MQ2-D3)** và **Prompt `04` (MQ2-D4)**.

---

## 9. Kiến trúc triển khai

1. **Quy tắc mở rộng `database.rules.src.json` & `database.rules.json`:**
   - Trong cả `state/gateway/values` và `state/device/values` (đặt ngay trước `"$other": { ".validate": false }`):
     ```json
     "smokePpm": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 10000" },
     "smokeAlert": { ".validate": "newData.isBoolean()" },
     "window": { ".validate": "newData.isBoolean()" },
     "fan": { ".validate": "newData.isBoolean()" },
     ```
   - Trong `config`: **GIỮ NGUYÊN** `.validate` cấp cha (`newData.hasChildren(['rev','updatedBy','updatedAt','tempLimit','humLimit','coldLimit','autoAlarm'])...`), chỉ thêm 2 khóa con tùy chọn trước `"$other": { ".validate": false }`:
     ```json
     "smokeLimit": { ".validate": "newData.isNumber() && newData.val() >= 50 && newData.val() <= 5000" },
     "smokeAuto": { ".validate": "newData.isBoolean()" },
     ```
   - Trong `telemetry/$recordId/values` (trước `"$other": { ".validate": false }`):
     ```json
     "smokePpm": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 10000" },
     ```
   - Trong `commands/$cmdId/type`:
     ```json
     ".validate": "newData.val() === 'light.set' || newData.val() === 'door.set' || newData.val() === 'alarm.set' || newData.val() === 'mode.set' || newData.val() === 'clothes.set' || newData.val() === 'window.set' || newData.val() === 'fan.set'"
     ```
   - Lưu ý quan trọng cho Nhánh A: vì các dòng thêm mới chỉ là `.validate` không chứa macro `@TOKEN`, hãy cập nhật đồng bộ chính xác cùng nội dung JSON (format 2 spaces + newline cuối file) vào `cloud-rules/database.rules.json` để khớp tuyệt đối đầu ra của `build-rules.mjs`.
2. **Mở rộng `CloudDeviceModels.kt` (tất cả trường mới có default ở cuối):**
   - `GatewaySnapshot`: thêm `val smokePpm: Int? = null, val smokeAlert: Boolean? = null, val window: Boolean? = null, val fan: Boolean? = null` (cập nhật cả secondary constructor nhận `mode: HomeMode` với default tương ứng ở cuối).
   - `CloudConfigState`: thêm `val smokeLimit: Int = 300, val smokeAuto: Boolean = true`.
   - `GatewayStateSnapshot`: thêm `val smokePpm: Int? = null, val smokeAlert: Boolean? = null, val window: Boolean? = null, val fan: Boolean? = null`.
   - `CloudTelemetrySnapshot`: thêm `val smokePpm: Int? = null`.
3. **Mở rộng `CloudMapper.kt`:**
   - Hằng số biên: `const val MIN_SMOKE_PPM_INCLUSIVE = 0`, `const val MAX_SMOKE_PPM_INCLUSIVE = 10_000`, `const val MIN_CONFIG_SMOKE_LIMIT = 50`, `const val MAX_CONFIG_SMOKE_LIMIT = 5_000`.
   - `CRITICAL_EVENT_KINDS`: thêm `"smoke_detected"`, `"smoke_auto_vent"`.
   - `buildGatewayStatePayload`: ghi thêm vào `values`:
     - `snapshot.smokePpm?.takeIf { it in MIN_SMOKE_PPM_INCLUSIVE..MAX_SMOKE_PPM_INCLUSIVE }?.let { put("smokePpm", it) }`
     - `snapshot.smokeAlert?.let { put("smokeAlert", it) }`
     - `snapshot.window?.let { put("window", it) }`
     - `snapshot.fan?.let { put("fan", it) }`
     *(Tuyệt đối vẫn không đưa `raining`, `clothesRetracted`, `obstacle`, `distanceCm`, `deviceKey` vào `values`!)*
   - `buildConfigPayload`: thêm 2 tham số có mặc định ở cuối `smokeLimit: Int = 300, smokeAuto: Boolean = true`, kiểm tra `require(smokeLimit in MIN_CONFIG_SMOKE_LIMIT..MAX_CONFIG_SMOKE_LIMIT)`, ghi `"smokeLimit" to smokeLimit` và `"smokeAuto" to smokeAuto`.
   - `buildTelemetryPayload`: truyền `smokePpm = sample.smokePpm` từ `SensorSample` sang overload `buildTelemetryPayload(..., smokePpm: Int? = null)`, ghi `"smokePpm"` vào `values` nếu trong `0..10_000`; `if (values.isEmpty()) return null` (như vậy bản ghi chỉ có `smokePpm` hợp lệ vẫn được ghi).
4. **Mở rộng `RtdbDeviceRepository.kt` & `CloudGatewaySync.kt`:**
   - `parseGatewayStateValues`: thêm các tham số `rawSmokePpm: Any? = null, rawSmokeAlert: Any? = null, rawWindow: Any? = null, rawFan: Any? = null`, kiểm tra `smokePpm in 0..10_000`.
   - `parseTelemetryValues`: thêm `rawSmokePpm: Any? = null`; trả `null` chỉ khi `temp == null && hum == null && smokePpm == null`.
   - `parseConfigValues`: thêm `rawSmokeLimit: Any? = null, rawSmokeAuto: Any? = null` — nếu bản ghi cũ trên RTDB chưa có 2 trường này thì fallback mặc định `smokeLimit = 300`, `smokeAuto = true` (nếu có nhưng sai biên `!< 50..5000` thì trả `null`).
   - `GatewayInputSnapshot` & `SmartHomeState.toGatewayInput()` trong `CloudGatewaySync.kt`: nối thêm `smokePpm = hardware?.smokePpm`, `smokeAlert = if (esp32Online) smokeHazardActive else hardware?.smokeAlert`, `windowOpen = hardware?.windowOpen`, `fanOn = hardware?.fanOn`, `smokeLimit = smokeLimit`, `smokeAuto = effectiveSmokeAuto`, và đưa vào `GatewayValues` + `ConfigValues` để `CloudGatewaySync` tự phát hiện thay đổi và đẩy lên RTDB theo đúng debounce 2s / config rev hiện có!

---

## 10. Luồng dữ liệu

```text
SmartHomeState (LAN)
  └─ toGatewayInput() (smokePpm, smokeAlert, windowOpen, fanOn, smokeLimit=300, smokeAuto)
       │
       ▼
CloudGatewaySync (debounce 2s / heartbeat 60s / config rev+1 / UTC telemetry / SHA-256 events)
       │
       ▼
CloudMapper ──► RtdbDeviceRepository ──► Firebase RTDB (database.rules.json kiểm duyệt)
                                                │
                                                ▼ (Khi mất LAN > 5s)
                                       CloudFallbackObserver ──► CloudDeviceSnapshot
```

---

## 11. Logic nghiệp vụ

1. **Phân loại mức độ nghiêm trọng sự kiện (`CloudMapper.classifySeverity`):**
   - `smoke_detected` và `smoke_auto_vent` bắt buộc phân loại là `EventSeverity.CRITICAL` (`"critical"`).
   - `smoke_cleared`, `window_opened`, `window_closed`, `fan_on`, `fan_off`, `smoke_rule_changed` phân loại là `EventSeverity.INFO` (`"info"`).
2. **Đồng bộ `state/gateway` khi chưa gắn phần cứng MQ-2:**
   - Khi firmware cũ trả `hardware?.smokePpm == null`, `windowOpen == null`, `fanOn == null`: `CloudMapper` **bỏ qua** các khóa `null` đó trong `values` (chỉ ghi các khóa hiện có giá trị), tiết kiệm băng thông RTDB và tương thích 100% với cấu trúc cũ.
3. **Đồng bộ `config`:**
   - Mỗi khi người dùng đổi `smokeLimit` (ví dụ từ `300` sang `400`) hoặc đổi `smokeAutoVentilation`, `ConfigValues` trong `CloudGatewaySync` thay đổi ⇒ tự động đọc `config.rev` hiện tại và ghi bản mới với `rev + 1` kèm `smokeLimit` và `smokeAuto`.

---

## 12. Xử lý lỗi và concurrency

- Không chạm vào `network: Mutex` của `SmartHomeViewModel`; `CloudGatewaySync` vẫn đọc qua `gatewayInputFlow` bất đồng bộ hoàn toàn.
- Bảo toàn cơ chế retry 1 lần khi xung đột `config.rev` và cơ chế kiểm tra tồn tại khi gặp `PermissionDenied` trên node create-only (`telemetry`, `events`).
- Kiểm tra `provider.uid == expectedUid` trước và sau mọi thao tác mạng trong `RtdbDeviceRepository`.

---

## 13. Bất biến cần bảo toàn

1. **100% test Rules cũ** (`cloud-rules/test/rules.test.mjs` và `cloud-rules/test/gateway-payload.test.mjs`) phải giữ nguyên không sửa và **PASS 100%**.
2. Các trường bị cấm đưa lên `state/gateway/values` theo `CD-2` (`raining`, `clothesRetracted`, `obstacle`, `distanceCm`, `deviceKey`, `esp32Ip`) vẫn bị chặn tuyệt đối bởi cả `CloudMapper` lẫn `"$other": { ".validate": false }`.
3. **0 import Firebase SDK** trong `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudGatewaySync.kt`.
4. Tuyệt đối chỉ chạy Emulator với `--project demo-smarthomeai`, không kết nối Production `appaintm`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `database.rules.src.json` và `database.rules.json` đồng bộ hoàn toàn, mở đúng các khóa `smokePpm`, `smokeAlert`, `window`, `fan`, `smokeLimit`, `smokeAuto`, `window.set`, `fan.set` và giữ `$other: false`.
2. `CloudMapper`, `CloudDeviceModels`, `RtdbDeviceRepository`, `CloudGatewaySync` và `FakeCloudDeviceRepository` hỗ trợ đầy đủ luồng ghi & đọc dữ liệu khói/cửa sổ/quạt.
3. `CloudSmokeSyncTest.kt` kiểm chứng:
   - `CloudMapper` xuất đúng `smokePpm`, `smokeAlert`, `window`, `fan` trong biên `0..10000`, loại bỏ giá trị âm hoặc `> 10000`.
   - `CloudMapper` phân loại `smoke_detected` và `smoke_auto_vent` là `CRITICAL`.
   - `CloudMapper.buildTelemetryPayload` hoạt động cả khi có đủ `(temp, hum, smokePpm)` lẫn khi chỉ có `smokePpm`.
   - `CloudGatewaySync` tự động đẩy cập nhật `state/gateway` khi `smokePpm` hoặc `windowOpen`/`fanOn` thay đổi và đẩy `config` (`rev + 1`) khi `smokeLimit` đổi.
   - `RtdbDeviceRepository.parseConfigValues` fallback `smokeLimit = 300`, `smokeAuto = true` khi đọc dữ liệu config cũ chưa có trường khói.
4. `cloud-rules/test/smoke-mq2-rules.test.mjs` kiểm chứng trên Emulator:
   - ALLOW ghi `state/gateway` có `smokePpm: 320, smokeAlert: true, window: true, fan: true`.
   - DENY ghi `smokePpm: -1` hoặc `smokePpm: 10001` hoặc `window: "open"`.
   - ALLOW ghi `config` có `smokeLimit: 300, smokeAuto: true` và ALLOW cả payload v1 không có `smokeLimit`; DENY `smokeLimit: 10` hoặc `smokeLimit: 6000`.
   - ALLOW ghi `telemetry` có `smokePpm: 280` (kèm hoặc không kèm `temp`/`hum`); DENY `smokePpm: -5`.
   - ALLOW tạo command `'window.set'` và `'fan.set'`; DENY command lạ `'heater.set'`.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `cd cloud-rules; npm run build:rules; git diff --exit-code database.rules.json` → xác nhận `database.rules.json` khớp tuyệt đối `database.rules.src.json`.
2. `cd cloud-rules; npm run test:build; npm run test:rules` → toàn bộ Rules build tests + `rules.test.mjs` **PASS**.
3. `cd cloud-rules; node scripts/run-emulator.mjs "node --test test/gateway-payload.test.mjs test/smoke-mq2-rules.test.mjs"` → **100% PASS** trên `demo-smarthomeai`.
4. Từ thư mục gốc dự án: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` → toàn bộ JVM unit tests cũ + `CloudSmokeSyncTest` **PASS** và build thành công.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ đúng 9 file thuộc Mục 6 (`7` sửa + `2` thêm mới).
2. Kiểm tra 0 `@TOKEN` chưa mở rộng trong `cloud-rules/database.rules.json`:
   `Select-String -Path cloud-rules\database.rules.json -Pattern "@[A-Z]+"` → `0` kết quả.
3. Kiểm tra 0 import `com.google.firebase` trong `CloudDeviceModels.kt`, `CloudMapper.kt`, `CloudGatewaySync.kt`:
   `Select-String -Path app\src\main\java\com\example\smarthomeai\cloud\CloudDeviceModels.kt,app\src\main\java\com\example\smarthomeai\cloud\CloudMapper.kt,app\src\main\java\com\example\smarthomeai\cloud\CloudGatewaySync.kt -Pattern "com\.google\.firebase"` → `0` kết quả.
4. Kiểm tra các file test Rules cũ không bị đụng chạm:
   `git diff -- cloud-rules/test/rules.test.mjs cloud-rules/test/gateway-payload.test.mjs` → rỗng.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Firebase RTDB Rules và tầng Cloud sync chưa hỗ trợ trường smokePpm, smokeAlert, window, fan, smokeLimit, smokeAuto.
EVIDENCE: <file:dòng & kết quả static checks mục 15>
CHANGES: <danh sách file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy Rules Emulator và Gradle unit tests).
SCOPE: OK
RISKS: None | Cần Nhánh B kiểm chứng thực tế bằng Firebase Local Emulator (demo-smarthomeai).
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác sạch bằng `git revert <commit_hash_prompt_02>`.
- Không có bất kỳ thay đổi nào được đẩy lên Firebase Production (`appaintm`).

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short`.
2. `git add` tường minh đúng 9 file thuộc Mục 6.
3. Kiểm tra `git diff --cached --stat`.
4. Tạo commit tiếng Việt:
   `git commit -m "Mở rộng Firebase RTDB Rules và đồng bộ dữ liệu khói MQ-2 lên Cloud"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Phát hiện xung đột giữa `database.rules.src.json` và bất kỳ bài test nào trong `rules.test.mjs` hoặc `gateway-payload.test.mjs`.
- Nhánh B phát hiện `npm run build:rules` tạo ra khác biệt so với `database.rules.json` hoặc bất kỳ test Emulator/JVM nào fail → Nhánh B **DỪNG**, khôi phục working tree sạch nếu `build:rules` vừa sửa file và báo cáo lỗi chi tiết (không tự sửa code của Nhánh A).
