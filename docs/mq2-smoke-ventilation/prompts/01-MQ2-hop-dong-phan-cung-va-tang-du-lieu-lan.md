# PROMPT 01 — MQ2-D1: HỢP ĐỒNG PHẦN CỨNG, DATA MODELS, ESP32 CLIENT, NGƯỠNG KHÓI 300 PPM & LƯU TRỮ CỤC BỘ

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Data & IoT Protocol Engineer thiết lập toàn bộ hợp đồng dữ liệu phần cứng cho cảm biến khói **MQ-2** (đơn vị `ppm`, ngưỡng mặc định `300 ppm`), cơ cấu **Cửa sổ** & **Quạt thông gió**, mở rộng `Models.kt`, `HardwareStatus.kt`, `Esp32Client.kt`, `Security.kt`, `Insights.kt`, `Storage.kt` (nâng SQLite lên v2 an toàn), kèm bộ unit test `SmokeSafetyDataTest.kt` (không chạy Gradle ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA Engineer đọc Handoff + `git diff` của Nhánh A, đối chiếu từng bất biến hồi quy (`DeviceType.entries`, `sensorCsv`, SQLite v2 migration, biên parse JSON), viết bổ sung test nếu thiếu và chạy thực tế toàn bộ JVM unit tests + `assembleDebug`.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` và `git status --porcelain` theo Mục 2.
2. Đọc lại toàn bộ `Models.kt`, `HardwareStatus.kt`, `Esp32Client.kt`, `Security.kt`, `Insights.kt`, `Storage.kt` và đọc tham chiếu `AppSmokeTest.kt` (dòng 309–382), `InsightsTest.kt`, `Esp32ClientTest.kt`, `HardwareStatusTest.kt`.
3. Đối chiếu từng yêu cầu ở Mục 4–13 với source thực tế.
4. Chốt phương án kỹ thuật chi tiết theo Mục 9–12.
5. Triển khai toàn bộ các file thuộc Mục 6, kèm viết file unit test mới `SmokeSafetyDataTest.kt` (**không chạy**).
6. Không mở rộng phạm vi ra ngoài danh sách file ở Mục 6.
7. Chỉ chạy **static checks** quy định tại Mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy emulator / dev server.**
11. **KHÔNG chạy Gradle build / bundle.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất **Handoff Report** đúng mẫu Mục 16.
14. Tạo **01 Git commit tiếng Việt** theo Mục 18 (**TUYỆT ĐỐI KHÔNG `git push`**).
15. **DỪNG** hoàn toàn và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)** ngay trong cuộc hội thoại.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc toàn bộ `git show --stat HEAD` và `git diff HEAD~1..HEAD`.
3. Kiểm tra code thực tế đối chiếu tiêu chí hoàn thành (Mục 14) và các bất biến bảo toàn (Mục 13).
4. Bổ sung test độc lập nếu thiếu độ phủ và trực tiếp chạy toàn bộ lệnh kiểm thử tại Mục 14.
5. **KHÔNG** tự ý sửa code sản xuất trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được trong code của Nhánh A.
7. Phân loại mọi lỗi phát hiện theo `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể (`file:dòng`, tên test, assertion/log).
9. Xuất báo cáo kiểm thử độc lập (ghi rõ tổng số test pass/fail/skip).
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu người dùng phê duyệt prompt sửa lỗi riêng. Nếu `PASS` hoàn toàn: xác nhận đủ điều kiện chuyển sang **Prompt 02**.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Repo đang ở trạng thái hoàn tất tích hợp Cloud Core (`452abb2`).
- **Branch:** `master`
- **HEAD bắt buộc:** `452abb2399c182b1088e0eb1b3da62970653db89` *(hoặc commit chỉ thêm tài liệu `docs/mq2-smoke-ventilation/` ngay trên `452abb2399c182b1088e0eb1b3da62970653db89`)*.
- **Working tree:** Không có bất kỳ thay đổi nào trong `app/`, `cloud-rules/`, `esp32/` (`git status --porcelain -- app cloud-rules esp32` phải rỗng hoàn toàn; thư mục `docs/mq2-smoke-ventilation/` nếu đang untracked thì giữ nguyên, không `git add` nhầm vào commit code nếu người dùng chưa yêu cầu).
- **Lệnh kiểm tra đầu phiên:**
  ```powershell
  git rev-parse HEAD; git status --porcelain -- app cloud-rules esp32
  ```

---

## 3. Trạng thái hiện tại đã xác minh

1. `Models.kt:8-10`: `enum class DeviceType` chỉ có `LIGHT`, `DOOR`, `ALARM`. Tại `AppSmokeTest.kt:312-316` và `375-378`, bài test duyệt `DeviceType.entries` trên `SmartHomeState()` mặc định ⇒ **cấm thêm phần tử vào `DeviceType`**.
2. `Models.kt:18`: `enum class AlertType { MOTION, STRANGER, DOOR, LOGIN, ENVIRONMENT }` — chưa có `SMOKE`.
3. `Models.kt:24-46`: `SensorSample(val timestamp: Long, val temperature: Int, val humidity: Int)` và `SmartHomeState` chưa có trường nồng độ khói, ngưỡng khói (`300 ppm`), cờ tự động mở cửa sổ/quạt hay trạng thái bận điều khiển thông gió.
4. `HardwareStatus.kt:6-35`: `HardwareStatus` parse dữ liệu phần cứng từ JSON `/sensor` (theo mẫu `has_clothesline`, `clothes_retracted`, `rain_retract_enabled`), chưa có trường cho MQ-2 (`smoke_ppm`), cửa sổ (`window_open`) và quạt (`fan_on`).
5. `Esp32Client.kt:38-71`: Đã có `sendClothesline` (`POST clothesline?state=1|0`) và `sendRainRetraction` (`POST clothesline/auto?enabled=1|0`), chưa có hàm cho `/window`, `/fan`, `/smoke/config`. Dòng `29` hiện ghi cứng thông báo HTTP `412` chỉ cho mưa/giàn phơi.
6. `Security.kt:44-83`: `DeviceEvent.title` chưa ánh xạ các sự kiện `smoke_detected`, `smoke_auto_vent`, `smoke_cleared`, `window_opened`, `window_closed`, `fan_on`, `fan_off`, `smoke_rule_changed`.
7. `Insights.kt:24-70`: Chỉ có `EnvironmentEvents` (nhiệt độ/độ ẩm) và `sensorCsv`. Tại `InsightsTest.kt:54-61`, test kiểm tra chính xác chuỗi `"\uFEFFThời gian,Nhiệt độ (°C),Độ ẩm (%)\r\n"` và hậu tố `",20,50"` khi mẫu chỉ có nhiệt độ/độ ẩm.
8. `Storage.kt:11-93`: `SQLiteOpenHelper` đang ở version `1` với bảng `samples (time INTEGER PRIMARY KEY, temp INTEGER NOT NULL, hum INTEGER NOT NULL)` và `onUpgrade` rỗng.

---

## 4. Mục tiêu cụ thể

1. Mở rộng `Models.kt` và `HardwareStatus.kt` để biểu diễn đầy đủ dữ liệu cảm biến khói **MQ-2** theo đơn vị **`ppm`** (`0..10000`), ngưỡng mặc định **`300 ppm`**, cờ cảnh báo khói, trạng thái & khả năng phần cứng của **Cửa sổ** (`hasWindow`, `windowOpen`) và **Quạt** (`hasFan`, `fanOn`), cùng cấu hình tự động thông gió (`smokeAutoVentilation`).
2. Bổ sung 3 API HTTP LAN chuẩn trên `Esp32Client.kt`: `sendWindow`, `sendFan`, `sendSmokeConfig` dùng phương thức `POST` và xác thực `X-Device-Key` đồng nhất với `sendClothesline`.
3. Tạo bộ đánh giá ngưỡng khói `SmokeSafetyGate` trong `Insights.kt` (kích hoạt tại `>= smokeLimit`, mặc định `300 ppm`, có vùng trễ hysteresis chống dao động liên tục quanh ngưỡng), hàm thống kê `summarizeSmoke` và mở rộng `sensorCsv` có điều kiện tương thích ngược.
4. Bổ sung ánh xạ tiêu đề tiếng Việt cho 8 sự kiện phần cứng MQ-2 / Cửa sổ / Quạt trong `Security.kt`.
5. Nâng cấp `Storage.kt` lưu bền vững cấu hình/trạng thái MQ-2 trong `SharedPreferences` và nâng SQLite `smart_home_history.db` lên **version `2`** (thêm cột `smoke_ppm INTEGER` nullable bằng `ALTER TABLE` khi upgrade từ v1).

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Cập nhật `Models.kt`: thêm `AlertType.SMOKE`, thêm `val smokePpm: Int? = null` vào `SensorSample`, thêm các trường cấu hình & trạng thái thông gió có giá trị mặc định vào `SmartHomeState`.
- Cập nhật `HardwareStatus.kt`: thêm các thuộc tính phần cứng MQ-2/Cửa sổ/Quạt (có mặc định cuối constructor để giữ nguyên mọi lời gọi constructor cũ trong `HardwareStatusTest` và `AppSmokeTest`), cập nhật `HardwareStatus.parse(json)` và các computed extension properties cho trạng thái khói & khóa an toàn thông gió.
- Cập nhật `Esp32Client.kt`: thêm `sendWindow`, `sendFan`, `sendSmokeConfig` và cập nhật thông báo lỗi HTTP `412`.
- Cập nhật `Security.kt`: thêm 8 mã `kind` mới trong `DeviceEvent.title`.
- Cập nhật `Insights.kt`: thêm hằng số ngưỡng mặc định/biên khói, class `SmokeSafetyGate`, enum `SensorMetric` (hoặc hàm `summarizeSmoke`), và mở rộng an toàn `sensorCsv`.
- Cập nhật `Storage.kt`: lưu/đọc `SharedPreferences` + migration SQLite v1 → v2 cho `smoke_ppm`.
- Tạo file unit test `SmokeSafetyDataTest.kt`.

### Ngoài phạm vi
- Không sửa `SmartHomeViewModel.kt` (thuộc Prompt `03`).
- Không sửa bất kỳ file nào trong `cloud/` hoặc `cloud-rules/` (thuộc Prompt `02`).
- Không sửa bất kỳ màn hình/component UI nào hoặc `MainActivity.kt` (thuộc Prompt `04`).
- Không sửa `esp32/SmartHomeESP32/SmartHomeESP32.ino`.

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/data/Models.kt`
2. `app/src/main/java/com/example/smarthomeai/data/HardwareStatus.kt`
3. `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`
4. `app/src/main/java/com/example/smarthomeai/data/Security.kt`
5. `app/src/main/java/com/example/smarthomeai/data/Insights.kt`
6. `app/src/main/java/com/example/smarthomeai/data/Storage.kt`

### Thêm mới (`NEW`)
1. `app/src/test/java/com/example/smarthomeai/data/SmokeSafetyDataTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`
- `app/src/main/java/com/example/smarthomeai/data/Notifier.kt`
- `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/data/AlarmSettings.kt`
- Toàn bộ thư mục `app/src/main/java/com/example/smarthomeai/cloud/**`
- Toàn bộ thư mục `app/src/main/java/com/example/smarthomeai/ui/**`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- Toàn bộ `cloud-rules/**`, `esp32/**`, và mọi file test hiện có (`InsightsTest.kt`, `HardwareStatusTest.kt`, `Esp32ClientTest.kt`, `SecurityTest.kt`, `AppSmokeTest.kt`).

---

## 8. Dependency

- **Phụ thuộc đầu vào:** Checkpoint `452abb2`.
- **Làm đầu vào bắt buộc cho:** **Prompt `02` (MQ2-D2)** và **Prompt `03` (MQ2-D3)**.

---

## 9. Kiến trúc triển khai

1. **Hằng số chuẩn cho MQ-2 (`HardwareStatus.Companion` hoặc `Insights.kt`):**
   - `const val DEFAULT_SMOKE_LIMIT_PPM = 300`
   - `const val MIN_SMOKE_LIMIT_PPM = 100`
   - `const val MAX_SMOKE_LIMIT_PPM = 2000`
   - `const val MIN_WIRE_SMOKE_LIMIT_PPM = 50`
   - `const val MAX_WIRE_SMOKE_LIMIT_PPM = 5000`
   - `const val MAX_SMOKE_PPM = 10_000`
   - `const val SMOKE_HYSTERESIS_PPM = 50`
2. **Mở rộng `Models.kt` (tương thích ngược 100%):**
   - `DeviceType` **GIỮ NGUYÊN** đúng 3 phần tử `LIGHT`, `DOOR`, `ALARM`.
   - `enum class AlertType { MOTION, STRANGER, DOOR, LOGIN, ENVIRONMENT, SMOKE }`
   - `data class SensorSample(val timestamp: Long, val temperature: Int, val humidity: Int, val smokePpm: Int? = null)`
   - Bổ sung vào cuối `SmartHomeState(...)`:
     ```kotlin
     val smokeAlertsEnabled: Boolean = true,
     val smokeLimit: Int = DEFAULT_SMOKE_LIMIT_PPM,
     val smokeAutoVentilation: Boolean = true,
     val ventilationBusy: Boolean = false,
     val ventilationMessage: String? = null
     ```
3. **Mở rộng `HardwareStatus.kt` (thêm ở cuối primary constructor với giá trị mặc định):**
   ```kotlin
   val hasMq2: Boolean = false,
   val smokePpm: Int? = null,
   val smokeAlert: Boolean? = null,
   val hasWindow: Boolean = false,
   val windowOpen: Boolean? = null,
   val hasFan: Boolean = false,
   val fanOn: Boolean? = null,
   val smokeAutoEnabled: Boolean? = null,
   val smokeThresholdPpm: Int = DEFAULT_SMOKE_LIMIT_PPM
   ```
   - Lưu ý quan trọng trong `HardwareStatus.parse(json)`:
     - Vẫn hỗ trợ trường hợp firmware mới gửi các trường MQ-2 ngay cả khi không gửi `hardware_profile`, HOẶC giữ logic: nếu `profile.isBlank()` và JSON **không** chứa bất kỳ khóa nào trong `setOf("has_mq2", "smoke_ppm", "has_window", "has_fan")` thì trả về `null` (bảo toàn 100% `HardwareStatusTest.absentHardwareDoesNotDisableAuthentication`: `HardwareStatus.parse(JSONObject()) == null`!). Nếu `profile.isBlank()` nhưng có các khóa MQ-2/window/fan thì đặt `profile = "mq2-ventilation"` và giữ `authRequired = flag("auth_required") ?: true`.
     - `smokePpm`: đọc `json.optInt("smoke_ppm", -1)` khi `json.has("smoke_ppm") && !json.isNull("smoke_ppm")` (hoặc `json.getDouble("smoke_ppm")` finite làm tròn `toInt()`), chỉ nhận giá trị trong `0..MAX_SMOKE_PPM` (`0..10_000`), ngoài biên trả `null`.
     - `hasMq2 = flag("has_mq2") ?: (smokePpm != null || flag("smoke_alert") != null)`
     - `hasWindow = flag("has_window") ?: (flag("window_open") != null)`
     - `hasFan = flag("has_fan") ?: (flag("fan_on") != null)`
     - `smokeThresholdPpm = json.optInt("smoke_threshold_ppm", DEFAULT_SMOKE_LIMIT_PPM).takeIf { it in MIN_WIRE_SMOKE_LIMIT_PPM..MAX_WIRE_SMOKE_LIMIT_PPM } ?: DEFAULT_SMOKE_LIMIT_PPM`
   - Extension properties trên `SmartHomeState`:
     - `val SmartHomeState.effectiveSmokePpm: Int? get() = hardware?.smokePpm`
     - `val SmartHomeState.effectiveSmokeAuto: Boolean get() = hardware?.smokeAutoEnabled ?: smokeAutoVentilation`
     - `val SmartHomeState.smokeHazardActive: Boolean get() = esp32Online && (hardware?.smokeAlert == true || (hardware?.smokePpm != null && hardware.smokePpm >= smokeLimit))`
     - `val SmartHomeState.ventilationSafetyLocked: Boolean get() = smokeHazardActive && effectiveSmokeAuto`
4. **Mở rộng `Esp32Client.kt`:**
   - `fun sendWindow(ip: String, open: Boolean, key: String = ""): Boolean = request(ip, "window?state=${if (open) 1 else 0}", key, "POST")?.trim() == "OK"`
   - `fun sendFan(ip: String, on: Boolean, key: String = ""): Boolean = request(ip, "fan?state=${if (on) 1 else 0}", key, "POST")?.trim() == "OK"`
   - `fun sendSmokeConfig(ip: String, autoEnabled: Boolean, thresholdPpm: Int, key: String = ""): Boolean`: kiểm tra trước `if (thresholdPpm !in MIN_WIRE_SMOKE_LIMIT_PPM..MAX_WIRE_SMOKE_LIMIT_PPM) return false` (không gọi mạng nếu sai biên, giống `sendAlarmSettings`/`sendTiming`), sau đó gọi `request(ip, "smoke/config?auto=${if (autoEnabled) 1 else 0}&threshold=$thresholdPpm", key, "POST")?.trim() == "OK"`.
   - Với HTTP `412`: nếu `path.startsWith("window") || path.startsWith("fan")` gán `lastError = "Đang có khói vượt ngưỡng và chế độ tự động đang bật. Giữ cửa sổ và quạt mở để thoát khói."`, ngược lại giữ nguyên câu thông báo mưa/giàn phơi hiện tại.

---

## 10. Luồng dữ liệu

```text
ESP32 GET /sensor JSON
  ├─ (Firmware 3.5 hiện tại: không có smoke_ppm/window/fan)
  │    └─ HardwareStatus(hasMq2=false, smokePpm=null, hasWindow=false, hasFan=false)
  └─ (Firmware mới có MQ-2 + Cửa sổ + Quạt)
       └─ HardwareStatus(hasMq2=true, smokePpm=0..10000, windowOpen=true/false, fanOn=true/false, ...)
              │
              ▼
        SmokeSafetyGate.evaluate(enabled, smokePpm, smokeLimit=300)
              ├─ >= 300 ppm lần đầu ──► "Cảnh báo khói vượt ngưỡng · <X> ppm"
              ├─ Dao động 260..350 ppm ► Không spam lặp cảnh báo
              └─ Hạ xuống <= 250 ppm ──► Rearm (sẵn sàng cho đợt khói mới)
```

---

## 11. Logic nghiệp vụ

1. **`SmokeSafetyGate` trong `Insights.kt`:**
   - Thuộc tính trạng thái nội bộ: `private var smokeActive = false`, `private var configuredLimit: Int? = null`.
   - `fun evaluate(enabled: Boolean, smokePpm: Int?, limitPpm: Int = DEFAULT_SMOKE_LIMIT_PPM): String?`:
     - Nếu `!enabled` hoặc `limitPpm != configuredLimit`: đặt `smokeActive = false`, `configuredLimit = limitPpm`. Nếu `!enabled` trả `null`.
     - Nếu `smokePpm == null || smokePpm !in 0..MAX_SMOKE_PPM` trả `null` (giữ nguyên `smokeActive` để tránh mất gói tạm thời gây spam thông báo).
     - Ngưỡng tái trang bị (hysteresis): `val rearmThreshold = (limitPpm - SMOKE_HYSTERESIS_PPM).coerceAtLeast(0)` (với `limitPpm = 300` ⇒ `rearmThreshold = 250`: khi `smokePpm < 250` mới đặt `smokeActive = false` — tức là tại `250..299` nếu trước đó đã báo động thì chưa re-arm, còn nếu trước đó chưa báo động thì `< 300` vẫn an toàn; khi `smokePpm >= limitPpm` và `!smokeActive` thì đặt `smokeActive = true` và trả về `"Cảnh báo khói vượt ngưỡng · ${smokePpm} ppm"`).
   - Hàm `fun reset() { smokeActive = false; configuredLimit = null }` để gọi khi đổi IP thiết bị ESP32.
2. **Thống kê & Xuất CSV (`Insights.kt`):**
   - Giữ nguyên chữ ký `fun summarize(samples: List<SensorSample>, temperature: Boolean): SensorSummary?`.
   - Thêm `fun summarizeSmoke(samples: List<SensorSample>): SensorSummary?`: lọc `val values = samples.mapNotNull { it.smokePpm }`, nếu rỗng trả `null`, ngược lại trả `SensorSummary(values.min(), values.max(), values.average())`.
   - Trong `sensorCsv(samples: List<SensorSample>)`:
     - Kiểm tra `val includeSmoke = samples.any { it.smokePpm != null }`.
     - Nếu `!includeSmoke`: giữ **nguyên vẹn 100%** header `"Thời gian,Nhiệt độ (°C),Độ ẩm (%)\r\n"` và dòng `time,temp,hum`.
     - Nếu `includeSmoke`: xuất header `"Thời gian,Nhiệt độ (°C),Độ ẩm (%),Khói MQ-2 (ppm)\r\n"` và thêm `,${sample.smokePpm?.toString() ?: ""}` cuối mỗi dòng.
3. **Sự kiện thiết bị trong `Security.kt` (`DeviceEvent.title`):**
   - `"smoke_detected"` -> `"Phát hiện khói vượt ngưỡng"`
   - `"smoke_auto_vent"` -> `"Có khói · tự động mở cửa sổ và bật quạt"`
   - `"smoke_cleared"` -> `"Nồng độ khói đã về mức an toàn"`
   - `"window_opened"` -> `"Đã mở cửa sổ"`
   - `"window_closed"` -> `"Đã đóng cửa sổ"`
   - `"fan_on"` -> `"Đã bật quạt thông gió"`
   - `"fan_off"` -> `"Đã tắt quạt thông gió"`
   - `"smoke_rule_changed"` -> `"Đã đổi thiết lập cảnh báo khói"`
4. **Lưu trữ & Migration SQLite (`Storage.kt`):**
   - `SharedPreferences`:
     - Load: `smokeAlertsEnabled = prefs.getBoolean("smoke_alerts", true)`, `smokeLimit = prefs.getInt("smoke_limit", DEFAULT_SMOKE_LIMIT_PPM).coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM)`, `smokeAutoVentilation = prefs.getBoolean("smoke_auto_vent", true)`.
     - Save: ghi đủ 3 khóa trên và ghi thêm các trường `has_mq2`, `smoke_ppm`, `smoke_alert`, `has_window`, `window_open`, `has_fan`, `fan_on`, `smoke_auto_enabled`, `smoke_threshold_ppm` vào `hardware_json`.
   - `SQLiteOpenHelper`:
     - Nâng `DATABASE_VERSION` từ `1` lên `2`.
     - `onCreate`: `CREATE TABLE samples (time INTEGER PRIMARY KEY, temp INTEGER NOT NULL, hum INTEGER NOT NULL, smoke_ppm INTEGER)`
     - `onUpgrade(db, oldVersion, newVersion)`: `if (oldVersion < 2) { db.execSQL("ALTER TABLE samples ADD COLUMN smoke_ppm INTEGER") }`
     - `recordSample(sample)`: `INSERT OR REPLACE INTO samples (time, temp, hum, smoke_ppm) VALUES (?, ?, ?, ?)` (truyền `sample.smokePpm` hoặc `null`).
     - `samples()`: `SELECT time, temp, hum, smoke_ppm FROM samples WHERE time >= ? ORDER BY time`, đọc cột 3 bằng `if (c.isNull(3)) null else c.getInt(3)`.

---

## 12. Xử lý lỗi và concurrency

- Không bao giờ bịa giá trị `0 ppm` khi JSON trả `"smoke_ppm": null` hoặc thiếu khóa `"smoke_ppm"`; bắt buộc giữ `null`.
- Từ chối các giá trị nhiễu/lỗi ngoài biên (`smoke_ppm < 0` hoặc `> 10000`, `NaN`, `Infinity`) thành `null` để không gây cảnh báo giả hay mở cửa sổ nhầm.
- `Esp32Client.sendSmokeConfig` phải từ chối ngay trước khi mở socket nếu `thresholdPpm !in 50..5000`.
- Mọi kết nối HTTP trong `Esp32Client` vẫn đi qua `request(...)` dùng chung `connectTimeout = 2000`, `readTimeout = 2000` và `finally { connection?.disconnect() }`.

---

## 13. Bất biến cần bảo toàn

1. `DeviceType.entries` vẫn chính xác là `[LIGHT, DOOR, ALARM]` (không thêm `WINDOW`, `FAN`).
2. `HardwareStatus.parse(JSONObject())` vẫn trả về `null`; `HardwareStatus("dht11-leds")` với các tham số cũ vẫn biên dịch và giữ nguyên hành vi.
3. `sensorCsv` với danh sách `SensorSample` có `smokePpm == null` vẫn xuất chính xác từng ký tự như phiên bản cũ.
4. Dữ liệu lịch sử nhiệt độ/độ ẩm sẵn có trong SQLite v1 không bị xóa khi nâng cấp lên v2.
5. Không import bất kỳ class Firebase nào vào package `data/`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. Đầy đủ models, parser JSON phần cứng MQ-2/Cửa sổ/Quạt, endpoints `sendWindow`/`sendFan`/`sendSmokeConfig`, `SmokeSafetyGate` (ngưỡng mặc định `300 ppm` + hysteresis `50 ppm`), 8 mã `DeviceEvent`, và SQLite v2 migration.
2. Mọi constructor/hàm cũ giữ tương thích ngược bằng default arguments.
3. File test mới `SmokeSafetyDataTest.kt` bao phủ:
   - Parse JSON firmware 3.5 cũ (`hasMq2 = false`, `smokePpm = null`, `hasWindow = false`, `hasFan = false`).
   - Parse JSON firmware mới có `smoke_ppm = 320`, `window_open = 1`, `fan_on = 1`, `smoke_threshold_ppm = 300`; từ chối `smoke_ppm = -5` và `smoke_ppm = 25000` thành `null`.
   - `Esp32Client.sendWindow`, `sendFan`, `sendSmokeConfig` dùng đúng `POST`, đúng URL query, gửi header `X-Device-Key`, từ chối `thresholdPpm` ngoài `50..5000` trước khi gửi mạng, báo lỗi tiếng Việt rõ ràng khi nhận HTTP `412`.
   - `SmokeSafetyGate`: tại `299 ppm` không báo; tại `300 ppm` báo đúng `"Cảnh báo khói vượt ngưỡng · 300 ppm"`; lên `350 ppm` không báo lặp; hạ về `260 ppm` vẫn giữ trạng thái active; hạ về `249 ppm` re-arm và khi tăng lại `300 ppm` báo lần mới.
   - `sensorCsv`: mẫu không có khói giữ nguyên 3 cột; mẫu có `smokePpm` xuất 4 cột đúng thứ tự thời gian.
   - `DeviceEvent.title` trả đúng tiếng Việt cho cả 8 mã sự kiện mới.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → Toàn bộ unit test cũ (`InsightsTest`, `HardwareStatusTest`, `Esp32ClientTest`, `SecurityTest`, Cloud tests) + `SmokeSafetyDataTest` mới **100% PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL** (chứng minh mọi call-site trong `main` và `androidTest` tương thích 100%).

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chứa đúng các file trong Mục 6.
2. `Select-String -Path app\src\main\java\com\example\smarthomeai\data\Models.kt -Pattern "enum class DeviceType" -Context 0,3` → xác nhận chỉ có `LIGHT`, `DOOR`, `ALARM`.
3. `Select-String -Path app\src\main\java\com\example\smarthomeai\data\*.kt -Pattern "smoke_ppm|sendWindow|sendFan|sendSmokeConfig|SmokeSafetyGate|ALTER TABLE samples"` → xác nhận đủ các thành phần cốt lõi.
4. `Select-String -Path app\src\main\java\com\example\smarthomeai\data\*.kt -Pattern "com\.google\.firebase"` → `0` kết quả.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Chưa có hợp đồng dữ liệu phần cứng MQ-2 (ppm), cửa sổ, quạt, bộ đánh giá ngưỡng 300 ppm và cột lưu trữ SQLite.
EVIDENCE: <file:dòng & kết quả static checks mục 15>
CHANGES: <danh sách file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy :app:testDebugUnitTest và :app:assembleDebug).
SCOPE: OK
RISKS: None | Cần Nhánh B xác minh không hồi quy InsightsTest, HardwareStatusTest, Esp32ClientTest và AppSmokeTest compile.
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác sạch bằng `git revert <commit_hash_prompt_01>` (không dùng `git reset --hard`).
- An toàn dữ liệu: SQLite chỉ thêm cột nullable `smoke_ppm`, không xóa hay biến đổi cột `time, temp, hum`.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` đảm bảo không dính file ngoài Mục 6.
2. `git add` tường minh đúng 7 file thuộc Mục 6 (`6` file `WRITE` + `1` file `NEW` test).
3. Kiểm tra `git diff --cached --stat`.
4. Tạo commit tiếng Việt:
   `git commit -m "Bổ sung hợp đồng dữ liệu cảm biến khói MQ-2, cửa sổ và quạt"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Phát hiện bất kỳ file nào ngoài Mục 6 có thay đổi chưa commit trong `app/`, `cloud-rules/` hoặc `esp32/`.
- Nhánh B phát hiện lỗi biên dịch ở `androidTest` hoặc bất kỳ bài test cũ nào fail → Nhánh B **DỪNG**, phân loại mức độ lỗi và yêu cầu prompt vá lỗi riêng (không tự ý sửa code sản xuất).
