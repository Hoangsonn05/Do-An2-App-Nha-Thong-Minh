# BÁO CÁO AUDIT TỔNG THỂ SAU TÍCH HỢP — CẢM BIẾN KHÓI MQ-2, CẢNH BÁO 300 PPM & THÔNG GIÓ TỰ ĐỘNG

- **Phạm vi:** Kiểm toán **READ-ONLY** toàn hệ thống sau khi hoàn tất trọn vẹn **4 prompt triển khai (`01` → `04`)** cho phân hệ Cảm biến khói MQ-2 (`ppm`), cảnh báo điện thoại ngưỡng mặc định `300 ppm`, tự động mở cửa sổ & bật quạt thông gió, và đồng bộ Firebase RTDB trên nhánh `master` (`452abb2..91f312f`).
- **Môi trường kiểm thử thực tế:** Local Firebase RTDB Emulator (`--project demo-smarthomeai`, `127.0.0.1:9000` / `10.0.2.2:9000`), AVD `Pixel_9_Pro_API_34` (Android 14 / API 34), JDK 21 (`D:\Program Files\Android Studio\jbr`).
- **Nguyên tắc tuân thủ:** Không sửa bất kỳ file source/test/rules/firmware nào trong quá trình Audit; không tạo `.firebaserc`; không truy cập Firebase Production `appaintm`; không `git push`.

---

## 1. Tóm tắt kết luận

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| **Kết luận tổng thể** | **PASS (Đạt chuẩn tích hợp 10/10 trục)** | Toàn bộ 10/10 trục `A`–`J` đạt yêu cầu; **437/437** bài kiểm thử tự động (Rules + JVM + Android UI/Integration) **PASS 100%**, **0 lỗi Lint**. |
| **Lỗi thực tế (`BUG`)** | **0** | `0 CRITICAL`, `0 HIGH`, `0 MEDIUM`, `0 LOW` — không cần chạy vòng vá lỗi Clustered Remediation. |
| **Rủi ro biên (`RISK`)** | **6 (mức `LOW`)** | Gồm các lưu ý làm nóng đầu dò MQ-2, trường `incident` khi viết firmware C++, biên `window_open == null`, cửa sổ trễ `/events` 1 chu kỳ, dải slider UI `100..1000 ppm` và cờ `--test-concurrency=1` khi gộp nhiều file test Rules. |
| **Chờ kiểm chứng vật lý / Live (`UNVERIFIED`)** | **2** | Chờ nạp firmware C++ lên mạch ESP32 + cảm biến MQ-2 thật và chờ deploy/kiểm thử trên RTDB Production `appaintm`. |
| **Giới hạn kiến trúc (`LIMITATION`)** | **3** | Thông báo đẩy cục bộ qua LAN/app đang chạy (chưa dùng Cloud Functions FCM), xem từ xa qua Cloud chỉ đọc, và đồng bộ `config` một chiều Owner → Cloud. |
| **Cần người dùng quyết định (`USER_DECISION_REQUIRED`)** | **3** | Triển khai firmware C++ cho ESP32, deploy `database.rules.json` lên `appaintm`, và commit thư mục tài liệu `docs/mq2-smoke-ventilation/`. |

---

## 2. Lịch sử Commit tích hợp (`452abb2..91f312f`)

Baseline trước đợt tích hợp MQ-2 là commit `452abb2` (`Cập nhật tự động nhãn dữ liệu cũ trên HomeScreen khi ngoại tuyến quá 5 phút`). Tổng cộng **30 tệp** thay đổi (`+3.187 / -89` dòng):

| STT | Commit | Ngày | Thông điệp | Phạm vi Prompt |
|---|---|---|---|---|
| 1 | `d71b411` | 2026-11-26 | `Bổ sung hợp đồng dữ liệu cảm biến khói MQ-2, cửa sổ và quạt` | Prompt 01 (`MQ2-D1` – Nhánh A & B) |
| 2 | `0cf8f9c` | 2026-11-26 | `Mở rộng Firebase RTDB Rules và đồng bộ dữ liệu khói MQ-2 lên Cloud` | Prompt 02 (`MQ2-D2` – Nhánh A & B) |
| 3 | `a0e04db` | 2026-11-26 | `Tích hợp cảnh báo khói 300 ppm và tự động mở cửa sổ, quạt trong ViewModel` | Prompt 03 (`MQ2-D3` – Nhánh A) |
| 4 | `20bd3da` | 2026-11-26 | `Bổ sung test kiểm thử độc lập cho SmokeVentilationCoordinator` | Prompt 03 (`MQ2-D3` – Nhánh B) |
| 5 | `5d91684` | 2026-11-26 | `Hoàn thiện giao diện cảm biến khói MQ-2, cảnh báo ppm và điều khiển cửa sổ, quạt` | Prompt 04 (`MQ2-D4` – Nhánh A) |
| 6 | `91f312f` | 2026-11-26 | `Sửa assertion kiểm thử UI MQ-2 và bổ sung test tích hợp màn hình` | Prompt 04 (`MQ2-D4` – Nhánh B) |

---

## 3. Đối chiếu SHA-256 các tệp `LOCKED` (`452abb2` vs `HEAD`)

`git diff --stat 452abb2 HEAD -- <10 tệp LOCKED>` trả về rỗng hoàn toàn; SHA-256 bảo toàn 100%:

| Tệp `LOCKED` | SHA-256 (`452abb2` & `HEAD`) | Khớp |
|---|---|---|
| `esp32/SmartHomeESP32/SmartHomeESP32.ino` | `6170D0D1F2DF2B026EF59D1A4756C65B062C619C7B7A275B3A14E019EA3FD139` | `True` |
| `app/src/main/java/com/example/smarthomeai/data/Notifier.kt` | `8F156C81B5CA10AC065CCA9CB2A64EB77BAA498B22D7D688FB63203608BFD1AE` | `True` |
| `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` | `1D9A9FFB8C03262FF60CB2AD60CCA40A4A23206F4338209B54F61C647BF6E70A` | `True` |
| `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt` | `DD3E450D5B092357B3A7F1E4681A9413B7DEDAA2E803D286A6AADE73BBB9D6C6` | `True` |
| `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt` | `1C0981D2682982A8723A6A087FB5623CBEB6A54D557BDA1A1B07AEE501FF91E0` | `True` |
| `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt` | `9D7AA21A89F777F4530EB8A5ED1AAE48931EA6A9E563A36690438C70F7FE7B83` | `True` |
| `app/src/main/java/com/example/smarthomeai/ui/components/Components.kt` | `C1339F5205CAD566CB8C40FABECC405BB8FE919658AD87E8411DCDA60B909A2A` | `True` |
| `app/src/androidTest/java/com/example/smarthomeai/AppSmokeTest.kt` | `460BDA1C12840E49A691ED84FCEC03A8FF013BCA2074DBE36070A0358F50FA36` | `True` |
| `cloud-rules/test/rules.test.mjs` | `89892CCE605B8133B12DB10F17D55CC164A70ADA314EFDDDB4D9244EF924E382` | `True` |
| `cloud-rules/test/gateway-payload.test.mjs` | `C609798A82E3E3BE4175831F77337BE6CCD0C4E6421C9056C14AAA07AC39AA6D` | `True` |

---

## 4. Kết quả thực thi bộ kiểm thử (Runtime Verification)

| Hạng mục / Lệnh chạy | Số lượng | Kết quả | Chi tiết thực tế quan sát được |
|---|---|---|---|
| `cd cloud-rules; npm run test:build` | 4 tests | **PASS** | 4/4 pass, 0 fail, 0 skip (`database.rules.src.json` khớp tuyệt đối `database.rules.json`, 0 unexpanded `@TOKEN`) |
| `node scripts/run-emulator.mjs "node --test --test-concurrency=1 test/rules.test.mjs test/gateway-payload.test.mjs test/smoke-mq2-rules.test.mjs"` | 111 tests (20 suites) | **PASS** | 111/111 pass trên `demo-smarthomeai` (`80` `rules.test.mjs` + `19` `gateway-payload.test.mjs` + `12` `smoke-mq2-rules.test.mjs`) |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain` | 226 tests (26 XML) | **PASS** | 226 tests, 0 failures, 0 errors, 0 skipped (`197` legacy + `8` `SmokeSafetyDataTest` + `7` `CloudSmokeSyncTest` + `14` `SmokeVentilationCoordinatorTest`) |
| `.\gradlew.bat :app:lintDebug --console=plain` | — | **PASS** | **0 errors**, 18 warnings có sẵn (`16 GradleDependency`, `1 ModifierParameter`, `1 DiscouragedApi`), **0 warning mới** |
| `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` | — | **PASS** | `BUILD SUCCESSFUL` |
| `node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` | 96 tests | **PASS** | **96/96 pass** trên `Pixel_9_Pro_API_34(AVD) - 14`, 0 failed, 0 skipped |

Chi tiết phân bổ `connectedDebugAndroidTest` (96 tests):
- `com.example.smarthomeai.AppSmokeTest`: `54/54`
- `com.example.smarthomeai.SmokeVentilationUiTest`: `10/10`
- `com.example.smarthomeai.cloud.CloudInitializationIntegrationTest`: `10/10`
- `com.example.smarthomeai.cloud.CloudB3IntegrationTest`: `5/5`
- `com.example.smarthomeai.cloud.CloudSingleOwnerUiTest`: `5/5`
- `com.example.smarthomeai.cloud.CloudSettingsSingleOwnerUiTest`: `5/5`
- `com.example.smarthomeai.cloud.CloudHybridViewUiTest`: `5/5`
- `com.example.smarthomeai.cloud.CloudSessionUiTest`: `2/2`

---

## 5. Kết quả Audit chi tiết theo 10 Trục (`A` – `J`)

### Trục A — Hardware Contract Readiness: **OK** *(2 `RISK` mức LOW, 1 `UNVERIFIED`)*
- **`GET /sensor` (`HardwareStatus.kt:34-73`):** Khớp 1-1 toàn bộ 9 khóa JSON trong bảng hợp đồng (`has_mq2`, `smoke_ppm`, `smoke_alert`, `has_window`, `window_open`, `has_fan`, `fan_on`, `smoke_auto_enabled`, `smoke_threshold_ppm`). `smoke_ppm` kiểm tra hữu hạn, làm tròn nguyên trong dải `0..10000`, giữ `null` khi thiếu/ngoài biên/đang làm nóng. Khi JSON rỗng `{}` trả về `null`; khi nhận JSON từ firmware v3.5 hiện tại (`hardware_profile: "dht11-leds"`, chưa có MQ-2), trả `hasMq2 = false, smokePpm = null, hasWindow = false, hasFan = false`, không phát sinh lỗi hay cảnh báo giả.
- **HTTP Endpoints (`Esp32Client.kt:30-33, 50-57`):** `sendWindow` (`POST /window?state=1|0`), `sendFan` (`POST /fan?state=1|0`), `sendSmokeConfig` (`POST /smoke/config?auto=1|0&threshold=<50..5000>`, chặn ngoài biên `50..5000` trước khi mở kết nối), gửi đầy đủ `X-Device-Key` và phân biệt chính xác mã lỗi HTTP `412` giữa khóa an toàn khói (`window`/`fan`) với khóa mưa (`clothesline`).
- **`GET /events` (`Security.kt:81-88`):** Đầy đủ 8 mã `kind` (`smoke_detected`, `smoke_auto_vent`, `smoke_cleared`, `window_opened`, `window_closed`, `fan_on`, `fan_off`, `smoke_rule_changed`) khớp 100% tiêu đề tiếng Việt trong đặc tả.
- **Lưu ý tích hợp phần cứng:** Xem `RISK-MQ2-01` (làm nóng đầu dò MQ-2) và `RISK-MQ2-02` (gán `event.incident = 0` cho sự kiện khói trên firmware).

### Trục B — 300 ppm Threshold & Dual-Layer Auto-Ventilation: **OK** *(2 `RISK` mức LOW)*
- **Ngưỡng `300 ppm` & Hysteresis `50 ppm` (`Insights.kt:8-14, 37-65`):** `DEFAULT_SMOKE_LIMIT_PPM = 300`, `SMOKE_HYSTERESIS_PPM = 50`. Kích hoạt đúng một lần khi `smokePpm >= 300`, không lặp khi dao động trong `[250..10000]`, chỉ re-arm khi hạ xuống `< 250 ppm`.
- **Bảo vệ tự động 2 lớp (`SmokeVentilationCoordinator.kt:40-116`, `SmartHomeViewModel.kt:205-236`):**
  - *Lớp 1 (Firmware-native):* Khi firmware đã mở sẵn (`windowOpen == true`, `fanOn == true`), `AutoVentilationPlan` trả `openWindow = false, turnOnFan = false` — không bắn lệnh HTTP thừa.
  - *Lớp 2 (App Fallback):* Khi `hazard && effectiveAuto` mà `hasWindow && windowOpen == false` hoặc `hasFan && fanOn == false`, tự động gửi `sendWindow(true)` / `sendFan(true)` xuyên qua cả chế độ bảo vệ `AWAY` / `SLEEP` (`protecting == true`), sau đó đọc lại `/sensor` cập nhật trạng thái xác nhận ngay.
  - *Hết khói giữ an toàn:* Khi `smokePpm < 250`, hệ thống không bao giờ tự gửi lệnh đóng cửa sổ hay tắt quạt; đồng thời khi còn khói vượt ngưỡng (`ventilationSafetyLocked == true`), mọi yêu cầu đóng cửa sổ/tắt quạt thủ công đều bị chặn bởi cả UI lẫn `SmokeVentilationCoordinator.evaluateWindowCommand` / `evaluateFanCommand` (`lines 124-156`).

### Trục C — Phone Alert & Notification Deduplication: **OK** *(1 `RISK` mức LOW, 1 `LIMITATION`)*
- **Luồng thông báo điện thoại (`SmartHomeViewModel.kt:196-205, 247-297`, `Notifier.kt:24-33`):** Khi vượt `300 ppm`, phát đúng `AlertItem(AlertType.SMOKE, ...)` và gọi `Notifier.notify` (kênh `IMPORTANCE_HIGH`, chạm mở thẳng mục cảnh báo tương ứng trong `AlertsScreen` qua `MainActivity.kt:34, 63-68`).
- **Khử trùng lặp kép:**
  1. Trong cùng batch `/events`, cặp sự kiện liên tiếp `smoke_detected` (`seq`) + `smoke_auto_vent` (`seq + 1`) cùng `boot` được gộp thành **1** cảnh báo duy nhất `"Phát hiện khói vượt ngưỡng · Đã mở cửa sổ và bật quạt"` (`lines 247-265`).
  2. `refreshLocked()` đồng bộ `syncEventsLocked()` **trước** khi xét `autoPlan.alertTitle`; nếu trong đợt khói hiện tại đã có `smoke_detected` hoặc `smoke_auto_vent` (`smokeCoordinator.hasEventAlertInCurrentEpisode == true`), cảnh báo từ `/sensor` được triệt tiêu để không rung chuông thông báo 2 lần (`lines 202-204, 271`).

### Trục D — Concurrency & Non-Reentrant Mutex Safety: **OK**
- **0 gọi lồng `network.withLock`:** Kiểm tra toàn bộ 12 điểm gọi `network.withLock` trong `SmartHomeViewModel.kt` (`lines 139, 190, 303, 331, 355, 383, 427, 473, 506, 522, 541, 555, 658`) và toàn bộ chuỗi hàm nội bộ (`refreshLocked`, `executeAutoVentilationLocked`, `syncEventsLocked`, `applySensor`, `commandLocked`, `applyModeLocked`): **0** trường hợp gọi `network.withLock` lồng nhau; `executeAutoVentilationLocked` gọi trực tiếp `Esp32Client.readSensor` thay vì đệ quy `refreshLocked()`.
- **Giải phóng cờ bận & chống nghẽn LAN:** Cả 3 hàm `setWindow`, `setFan`, `setSmokeConfig` đều bọc trong `try { ... } finally { _state.update { it.copy(ventilationBusy = false) } }` (`lines 410, 454, 495`); `AUTO_DISPATCH_COOLDOWN_MS = 5_000L` (`SmokeVentilationCoordinator.kt:24, 101`) ngăn gửi dồn lệnh HTTP mỗi giây nếu phần cứng phản hồi chậm; đổi IP trong `setConnection` gọi `smokeCoordinator.reset()` an toàn trong khóa mạng (`line 661`).

### Trục E — Firebase RTDB Rules & Cloud Sync / Fallback: **OK** *(1 `RISK` mức LOW, 2 `LIMITATION`, 1 `UNVERIFIED`)*
- **Security Rules (`cloud-rules/database.rules.src.json:141-145, 177-181, 192-202, 218-219, 232`):**
  - `state/gateway/values` & `state/device/values`: cho phép `smokePpm` (`0..10000`), `smokeAlert` (`bool`), `window` (`bool`), `fan` (`bool`), giữ chặt `"$other": { ".validate": false }`.
  - `config`: `smokeLimit` (`50..5000`) và `smokeAuto` (`bool`) là khóa tùy chọn hợp lệ, bảo toàn 100% khả năng ghi payload 7 khóa cũ.
  - `telemetry/$recordId/values`: hỗ trợ `smokePpm` (`0..10000`) kèm hoặc độc lập với `temp`/`hum`.
  - `commands/$cmdId/type`: mở sẵn `'window.set'` và `'fan.set'`.
- **Cloud Sync & Fallback (`CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`, `CloudSyncStatus.kt`):**
  - `CloudMapper.classifySeverity` phân loại `smoke_detected` và `smoke_auto_vent` là `CRITICAL` (`"critical"`).
  - `CloudGatewaySync` đẩy tức thời biến động `smokePpm`/`smokeAlert`/`windowOpen`/`fanOn` theo debounce `2s` và tự tăng `config.rev + 1` khi đổi `smokeLimit`/`smokeAuto`.
  - Khi mất LAN `> 5s`, `CloudFallbackObserver` + `HomeScreen` + `CloudReadOnlySummaryCard` hiển thị đầy đủ chỉ số `Khói MQ-2 (ppm)`, `Cửa sổ`, `Quạt` và tiêu đề sự kiện tiếng Việt.

### Trục F — SQLite v2 Migration & Local Storage: **OK**
- **SQLite v1 → v2 (`Storage.kt:11-20, 100-116`):** Nâng cấp DB version từ `1` lên `2` bằng `if (oldVersion < 2) db.execSQL("ALTER TABLE samples ADD COLUMN smoke_ppm INTEGER")`, bảo toàn toàn bộ dữ liệu lịch sử `time, temp, hum` đã lưu trước đó; đọc/ghi cột `smoke_ppm` nullable an toàn qua `c.isNull(3)`.
- **SharedPreferences & Xuất CSV (`Storage.kt:40-42, 76-91`, `Insights.kt:104-122`):** Lưu/khôi phục bền vững `smokeAlertsEnabled`, `smokeLimit` (`300`), `smokeAutoVentilation` và trạng thái phần cứng MQ-2 trong `hardware_json`. Hàm `sensorCsv` xuất đúng 3 cột gốc (kèm BOM `\uFEFF`) khi danh sách mẫu không có dữ liệu khói và tự động xuất 4 cột (`Khói MQ-2 (ppm)`) khi có ít nhất 1 mẫu `smokePpm != null`.

### Trục G — UI/UX Pro Max & Accessibility: **OK** *(1 `RISK` mức LOW)*
- **0 Emoji & Vector Icons:** Quét regex toàn bộ 30 tệp thay đổi xác nhận **0 ký tự emoji**; sử dụng đồng nhất Material Vector Icons (`Icons.Default.Air`, `Icons.Default.WarningAmber`, `Icons.Default.Tune`).
- **Tương phản WCAG AA (≥ 4.5:1):** Đo trực tiếp độ tương phản màu cảnh báo trên `SmokeVentilationCard` và các màn hình:
  - `RedAlert (#BC4444)` trên nền `RedSoft (#FFEEEE)`: **4.62:1** (`≥ 4.5:1`)
  - `RedAlert (#BC4444)` trên nền `CardWhite (#FFFFFF)`: **5.19:1** (`≥ 4.5:1`)
  - `GreenOk (#087F65)` trên nền `CardWhite (#FFFFFF)`: **4.96:1** (`≥ 4.5:1`)
  - `OrangeWarn (#9B6715)` trên nền `CardWhite (#FFFFFF)`: **4.83:1** (`≥ 4.5:1`)
- **Touch Target, Wrap & Semantics:** Mọi nút điều khiển (`Mở cửa sổ`, `Đóng cửa sổ`, `Bật quạt`, `Tắt quạt`, `Cài đặt ngưỡng khói`, `FilterChip` chọn nhanh) đặt `heightIn(min = 48.dp)`; dùng `FlowRow` tự xuống dòng không cắt cụt đơn vị `ppm`; đầy đủ `contentDescription` cho `SmartSwitch` và `SensorChart`; khóa chặt nút `"Đóng cửa sổ"` & `"Tắt quạt"` khi `ventilationSafetyLocked == true` và khóa toàn bộ công tắc khi `!esp32Online`.

### Trục H — Zero Regression trên Legacy Suite: **OK**
- **100% bất biến di sản được giữ nguyên:**
  - `DeviceType.entries` vẫn đúng `[LIGHT, DOOR, ALARM]` (`Models.kt:8-10`).
  - `EnvironmentConfigDialog` giữ nguyên duy nhất 1 `SmartSwitch` và chữ ký 4 tham số (`EnvironmentScreen.kt:231-252`).
  - Mọi phần tử toggleable trên `HomeScreen` đều `isNotEnabled()` khi offline (`SmokeVentilationUiTest.homeScreen_offlineWithMq2Hardware_disablesAllToggleablesAndShowsCard` & `AppSmokeTest.deviceControlsDisabledWhenOffline`).
  - Toàn bộ suite cũ đều PASS 100%: `54/54` `AppSmokeTest`, `10/10` `InsightsTest`, `17/17` `HardwareStatusTest`, `25/25` `Esp32ClientTest`, `19/19` `SecurityTest`, `80/80` `rules.test.mjs`, `19/19` `gateway-payload.test.mjs`, `4/4` `build-rules.test.mjs`.

### Trục I — Security & Sensitive Data Isolation: **OK**
- **Cách ly bí mật & ranh giới Cloud:**
  - **0** rò rỉ `deviceKey`, mật khẩu, token hay địa chỉ IP nội bộ lên payload Firebase RTDB (`GatewayInputSnapshot` không chứa `deviceKey`/`esp32Ip`; bị chặn kép bởi `CloudMapper.validateNoSecretToken`, `RtdbDeviceRepository.containsForbiddenSecretKey` và `.validate` trong Rules).
  - **0** import `com.google.firebase` trong `data/` (ngoài `AuthRepository.kt` có sẵn) và **0** import trong toàn bộ `ui/`.
  - **0** tham chiếu `appaintm`, `demo-smarthomeai`, `10.0.2.2` hay `useEmulator` trong `app/src/main/`; không tồn tại `.firebaserc`.
  - `esp32/SmartHomeESP32/SmartHomeESP32.ino` giữ nguyên SHA-256 gốc, không bị sửa ngoài ý muốn.

### Trục J — Build & Static Quality Gates: **OK**
- `:app:lintDebug`: **0 errors** (0 cảnh báo mới phát sinh từ đợt tích hợp MQ-2).
- `:app:assembleDebug` & `:app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- Cây thư mục mã nguồn (`app/`, `cloud-rules/`, `esp32/`) sạch 100% sau toàn bộ quá trình kiểm thử.

---

## 6. Bảng tổng hợp phát hiện

| ID | Trục | Phân loại | Mức độ | Mô tả | Bằng chứng (`path:line`) | Đề xuất / Hướng xử lý |
|---|---|---|---|---|---|---|
| `RISK-MQ2-01` | A, B | `RISK` | `LOW` | Đầu dò hóa học MQ-2 cần 20–60 giây làm nóng (`preheat`) khi mới cấp điện trên mạch thật, dễ vọt áp đầu đọc ADC ban đầu | `HardwareStatus.kt:46-55`, `SmokeVentilationCard.kt:107-108` | Khi viết firmware ESP32, trả `"smoke_ppm": null` trong 30–60s đầu sau khởi động — App đã hiển thị sẵn `"Đang làm nóng cảm biến"` và bỏ qua báo động giả |
| `RISK-MQ2-02` | A, C | `RISK` | `LOW` | Hàm `recordEvent(kind)` hiện hữu trên ESP32 gán `event.incident = home.incident`; nếu tái sử dụng nguyên trạng cho sự kiện khói khi đang có sự cố `AWAY`, `mergeIncidentAlerts` sẽ gắn sự kiện đó vào timeline sự cố chuyển động | `SmartHomeESP32.ino:621`, `Security.kt:118-134` | Khi viết firmware MQ-2 trên ESP32, ghi `incident = 0` cho các sự kiện `smoke_*`, `window_*`, `fan_*` |
| `RISK-MQ2-03` | B | `RISK` | `LOW` | Lớp 2 Auto-Ventilation kiểm tra `windowOpen == false` / `fanOn == false`; nếu firmware báo `has_window = 1` nhưng trả `window_open = null` thì Lớp 2 chưa phát lệnh fallback | `SmokeVentilationCoordinator.kt:99-100` | Trong firmware ESP32 mới, luôn khởi tạo và xuất tường minh `window_open: 0\|1`, `fan_on: 0\|1` ngay khi bật cờ phần cứng |
| `RISK-MQ2-04` | C | `RISK` | `LOW` | Nếu `GET /sensor` thấy `smoke_ppm >= 300` ở chu kỳ `T` nhưng `GET /events` lỗi mạng tạm thời và mới trả `smoke_detected` ở `T+1`, App có thể tạo 1 cảnh báo từ `/sensor` và 1 cảnh báo từ `/events` | `SmartHomeViewModel.kt:196-204, 241-271` | Chấp nhận cho an toàn khẩn cấp (thà báo đủ còn hơn bỏ sót khi `/events` lỗi); trong mạng LAN ổn định cả hai về cùng chu kỳ và được khử trùng lặp 100% |
| `RISK-MQ2-05` | B, G | `RISK` | `LOW` | `SmokeConfigDialog` giới hạn thanh trượt `100..1000 ppm` trong khi tầng dữ liệu/Cloud cho phép tới `2000`/`5000 ppm` | `SmokeVentilationCard.kt:344, 389`, `Insights.kt:9-12` | Phù hợp ngưỡng an toàn khói hộ gia đình (`200–500 ppm`); mở rộng `valueRange` nếu sau này lắp đặt cho khu vực bếp công nghiệp |
| `RISK-MQ2-06` | E | `RISK` | `LOW` | Chạy song song nhiều file test Rules bằng `node --test` không kèm `--test-concurrency=1` gây xóa chéo DB trên cùng port Emulator `9000` | `cloud-rules/test/smoke-mq2-rules.test.mjs:174-176` | Luôn truyền `--test-concurrency=1` khi gộp nhiều file `.test.mjs` trong một phiên `run-emulator.mjs` |
| `UNV-MQ2-01` | A, B, C | `UNVERIFIED` | `INFO` | Chưa kiểm thử vật lý với mạch ESP32 gắn cảm biến MQ-2 (`GPIO34` ADC1 + cầu phân áp), servo cửa sổ (`GPIO21`) và quạt (`GPIO22`) | `esp32/SmartHomeESP32/SmartHomeESP32.ino`, `MASTER-PLAN-MQ2-SMOKE-VENTILATION.md:67-72` | Thực hiện sau khi người dùng duyệt `UDR-MQ2-01` và lắp mạch phần cứng |
| `UNV-MQ2-02` | E, I | `UNVERIFIED` | `INFO` | Chưa deploy và kiểm chứng `database.rules.json` mới trên dự án Firebase Production `appaintm` | `cloud-rules/database.rules.json` | Thực hiện khi mở khóa cổng `B1-D` theo `UDR-MQ2-02` |
| `LIM-MQ2-01` | C, E | `LIMITATION` | `INFO` | Cảnh báo đẩy về điện thoại hoạt động qua polling LAN / đồng bộ sự kiện khi app chạy, chưa có Cloud Functions + FCM chạy nền khi tắt hẳn app ngoài mạng LAN | `Notifier.kt:24-33`, `SmartHomeViewModel.kt:137-142` | Giới hạn kiến trúc đã định nghĩa trong `AUDIT-PLAN-POST-INTEGRATION.md:31` |
| `LIM-MQ2-02` | E, G | `LIMITATION` | `INFO` | Chế độ xem từ xa qua Firebase Cloud là chỉ đọc (`CloudReadOnlySummaryCard`), chưa gửi lệnh đóng/mở cửa sổ & quạt từ xa qua WAN | `CloudSyncStatus.kt:453-458`, `database.rules.src.json:232` | Tuân thủ bất biến kiến trúc LAN-first (Rules đã mở sẵn `window.set`/`fan.set` cho giai đoạn mở rộng sau) |
| `LIM-MQ2-03` | B, E | `LIMITATION` | `INFO` | `config` (`smokeLimit`, `smokeAuto`) đồng bộ một chiều từ điện thoại Owner lên RTDB | `CloudGatewaySync.kt:440-504` | Nhất quán với mô hình `1 nhà – 1 chủ sở hữu` hiện hành |

---

## 7. Các quyết định cần người dùng phê duyệt (`USER_DECISION_REQUIRED`)

1. **`UDR-MQ2-01` — Bắt đầu giai đoạn Firmware C++ cho mạch ESP32 thật:** Phê duyệt viết và nạp phần mở rộng MQ-2 (`GPIO34` ADC1 qua cầu phân áp `10kΩ/20kΩ`), servo cửa sổ (`GPIO21`), mạch điều khiển quạt (`GPIO22`) và các endpoint `/window`, `/fan`, `/smoke/config` vào `esp32/SmartHomeESP32/SmartHomeESP32.ino` (lưu ý áp dụng luôn 3 khuyến nghị phòng ngừa `RISK-MQ2-01..03`).
2. **`UDR-MQ2-02` — Triển khai Security Rules mới lên Firebase Production `appaintm` (`B1-D`):** Phê duyệt backup dữ liệu hiện tại và deploy `cloud-rules/database.rules.json` lên môi trường Production `appaintm` trước khi thử nghiệm đồng bộ thực địa Wi-Fi ↔ 4G.
3. **`UDR-MQ2-03` — Lưu trữ tài liệu kế hoạch & báo cáo Audit vào Git:** Xác nhận có commit thư mục `docs/mq2-smoke-ventilation/` (bao gồm báo cáo `POST-INTEGRATION-AUDIT-REPORT.md` này) vào nhánh `master` hay không.

---

## 8. Kết luận về Quy trình Vá lỗi theo Cụm (Clustered Remediation)

- **Không phát sinh `BUG` (`0 CRITICAL / 0 HIGH / 0 MEDIUM / 0 LOW`)** trên toàn bộ 10 trục kiểm toán và 437 bài kiểm thử thực tế.
- Toàn bộ hệ thống phần mềm Android + Firebase RTDB cho tính năng **Cảm biến khói MQ-2 (`ppm`), cảnh báo `300 ppm` và tự động mở cửa sổ, bật quạt** đã **SẴN SÀNG 100%** để ghép nối phần cứng ESP32 mà không cần chạy thêm prompt vá lỗi nào.
