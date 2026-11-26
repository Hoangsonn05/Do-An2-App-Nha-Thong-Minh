# PROMPT 03 — MQ2-D3: ĐIỀU PHỐI CẢNH BÁO KHÓI 300 PPM, THÔNG BÁO ĐẨY & TỰ ĐỘNG MỞ CỬA SỔ, QUẠT TRONG VIEWMODEL

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Kotlin Coroutines & Smart Home Automation Architect xây dựng bộ điều phối thuần Kotlin `SmokeVentilationCoordinator.kt` và tích hợp vào `SmartHomeViewModel.kt` để thực thi trọn vẹn kịch bản nghiệp vụ: khi nồng độ khói **MQ-2 `>= 300 ppm`** (hoặc nhận cờ/sự kiện báo khói từ ESP32) thì **tự động cảnh báo về điện thoại (`Notifier`)** đồng thời **tự động mở cửa sổ & bật quạt thông gió** (hỗ trợ 2 lớp: tôn trọng trạng thái tự mở của firmware hoặc chủ động bắn lệnh HTTP fallback từ App), kèm điều khiển thủ công `setWindow`, `setFan`, `setSmokeConfig` và bộ unit test `SmokeVentilationCoordinatorTest.kt` (không chạy Gradle ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent Concurrency & Safety QA Engineer đọc Handoff + `git diff` của Nhánh A, kiểm tra đặc biệt nguy cơ deadlock `network: Mutex` (non-reentrant), chống gửi lệnh lặp, chống thông báo kép, hành vi khóa an toàn khi khói còn vượt ngưỡng, và chạy thực tế toàn bộ JVM unit tests + `assembleDebug`.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (khớp commit kết thúc của Prompt `02`) và `git status --porcelain -- app cloud-rules esp32` sạch.
2. Đọc lại toàn bộ `SmartHomeViewModel.kt`, `Models.kt`, `HardwareStatus.kt`, `Esp32Client.kt`, `Insights.kt`, `Security.kt`, `Notifier.kt`, `CloudGatewaySync.kt`.
3. Đối chiếu từng yêu cầu ở Mục 4–13 với luồng giữ khóa `network: Mutex` trong `SmartHomeViewModel`.
4. Chốt thiết kế `SmokeVentilationCoordinator.kt` và tích hợp vào `SmartHomeViewModel.kt` theo Mục 9–12.
5. Triển khai các file thuộc Mục 6, kèm viết `SmokeVentilationCoordinatorTest.kt` (**không chạy**).
6. Không sửa bất kỳ file nào ngoài Mục 6.
7. Chỉ chạy **static checks** quy định tại Mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy emulator / dev server.**
11. **KHÔNG chạy Gradle build.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất **Handoff Report** đúng mẫu Mục 16.
14. Tạo **01 Git commit tiếng Việt** theo Mục 18 (**TUYỆT ĐỐI KHÔNG `git push`**).
15. **DỪNG** hoàn toàn và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc toàn bộ `git show --stat HEAD` và `git diff HEAD~1..HEAD`.
3. Kiểm tra kỹ luồng gọi hàm bên trong `network.withLock` của `SmartHomeViewModel.kt` (đảm bảo 0 gọi lồng `network.withLock` gây deadlock) và kiểm tra đầy đủ các tiêu chí Mục 13–14.
4. Bổ sung test độc lập nếu thiếu độ phủ và trực tiếp chạy toàn bộ lệnh kiểm thử tại Mục 14.
5. **KHÔNG** tự sửa code sản xuất trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được trong code của Nhánh A.
7. Phân loại mọi lỗi phát hiện theo `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Nếu `PASS` hoàn toàn: xác nhận đủ điều kiện chuyển sang **Prompt 04**.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01` và Prompt `02` đã hoàn tất và đạt `PASS` ở Nhánh B.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `02` (`"Mở rộng Firebase RTDB Rules và đồng bộ dữ liệu khói MQ-2 lên Cloud"` hoặc commit test-only ngay sau đó của Nhánh B Prompt `02`).
- **Working tree:** `git status --porcelain -- app cloud-rules esp32` rỗng hoàn toàn.

---

## 3. Trạng thái hiện tại đã xác minh

1. `SmartHomeViewModel.kt`:
   - Dòng `40`: `private val network = Mutex()` — `Mutex` của `kotlinx.coroutines` **không reentrant**.
   - Dòng `190-203`: `refreshLocked()` được gọi bên trong `network.withLock { ... }`, tuần tự thực hiện `Esp32Client.readSensor(...)` → `applySensor(data)` → `syncEventsLocked(security)`.
   - Dòng `213-227`: `syncEventsLocked` mới chỉ ánh xạ `motion_home`, `motion_waiting`, `rain_started`, `rain_cleared`, `servo_overdue`, `servo_blocked`, `exit_waiting`; chưa ánh xạ `smoke_detected`, `smoke_auto_vent`, `smoke_cleared`.
   - Dòng `336-354`: `updateClothesline` là mẫu chuẩn cho điều khiển cơ cấu ngoài `DeviceType` (cập nhật cờ busy → gửi lệnh POST → `refreshLocked()` xác nhận trạng thái thực tế từ phần cứng → cập nhật message).
   - Dòng `387-421`: `applySensor` ghi `SensorSample(now, d.temp, d.hum)` chưa truyền `d.hardware?.smokePpm`, và mới gọi `environmentGate.evaluate(...)`, chưa đánh giá ngưỡng khói `300 ppm` hay tự động kích hoạt cửa sổ & quạt.

---

## 4. Mục tiêu cụ thể

1. Xây dựng `SmokeVentilationCoordinator.kt` (thuần Kotlin, không phụ thuộc Android framework để test 100% trên JVM) đóng gói toàn bộ quyết định nghiệp vụ:
   - Đánh giá cảnh báo khói vượt ngưỡng (mặc định **`300 ppm`**) qua `SmokeSafetyGate` hoặc cờ `smokeAlert == true`, khử trùng lặp thông báo khi ESP32 đồng thời phát sự kiện `smoke_detected` / `smoke_auto_vent`.
   - Xác định kế hoạch **Tự động mở Cửa sổ & bật Quạt (`AutoVentilationPlan`)** theo cơ chế 2 lớp (không gửi lệnh thừa nếu ESP32 đã tự mở; tự động bắn lệnh nếu `smoke_ppm >= limit` mà cửa sổ đang đóng hoặc quạt đang tắt, có cooldown `5_000 ms`, hoạt động xuyên qua cả chế độ `AWAY`/`SLEEP`).
   - Kiểm tra điều kiện cho phép điều khiển thủ công cửa sổ/quạt (`ManualVentilationDecision`): khóa đóng cửa sổ và khóa tắt quạt khi đang có khói vượt ngưỡng & chế độ tự động đang bật; khóa điều khiển khi đang `AWAY`/`protecting` nếu **không** có khói.
2. Tích hợp `SmokeVentilationCoordinator` vào `SmartHomeViewModel.kt`:
   - Ghi nhận `smokePpm = d.hardware?.smokePpm` vào `SensorSample` (cả `samples` 1 phút/lần và `liveSamples` 1 giây/lần).
   - Đồng bộ sự kiện `smoke_detected`, `smoke_auto_vent`, `smoke_cleared` sang `AlertType.SMOKE` trong `syncEventsLocked` và bắn thông báo điện thoại qua `Notifier`.
   - Thực thi tự động mở cửa sổ + bật quạt an toàn bên trong `refreshLocked()` (tuyệt đối không gọi lồng `network.withLock`).
   - Cung cấp 3 hàm public cho tầng UI: `setWindow(open: Boolean)`, `setFan(on: Boolean)`, `setSmokeConfig(alertsEnabled: Boolean, thresholdPpm: Int, autoVentilation: Boolean)`.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Tạo mới `app/src/main/java/com/example/smarthomeai/data/SmokeVentilationCoordinator.kt`.
- Cập nhật `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`.
- Tạo mới unit test `app/src/test/java/com/example/smarthomeai/data/SmokeVentilationCoordinatorTest.kt`.

### Ngoài phạm vi
- Không sửa `Models.kt`, `HardwareStatus.kt`, `Esp32Client.kt`, `Storage.kt`, `Notifier.kt`.
- Không sửa `cloud/**`, `cloud-rules/**`, `ui/**`, `MainActivity.kt`, `esp32/**`.

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`

### Thêm mới (`NEW`)
1. `app/src/main/java/com/example/smarthomeai/data/SmokeVentilationCoordinator.kt`
2. `app/src/test/java/com/example/smarthomeai/data/SmokeVentilationCoordinatorTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/data/Models.kt`
- `app/src/main/java/com/example/smarthomeai/data/HardwareStatus.kt`
- `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`
- `app/src/main/java/com/example/smarthomeai/data/Storage.kt`
- `app/src/main/java/com/example/smarthomeai/data/Notifier.kt`
- Toàn bộ `cloud/**`, `ui/**`, `MainActivity.kt`, `cloud-rules/**`, `esp32/**`.

---

## 8. Dependency

- **Phụ thuộc đầu vào:** Prompt `01` (`MQ2-D1`) và Prompt `02` (`MQ2-D2`) đã PASS.
- **Làm đầu vào bắt buộc cho:** **Prompt `04` (MQ2-D4)**.

---

## 9. Kiến trúc triển khai

1. **Thiết kế `SmokeVentilationCoordinator.kt` (Pure Kotlin):**
   ```kotlin
   data class AutoVentilationPlan(
       val alertTitle: String? = null,
       val openWindow: Boolean = false,
       val turnOnFan: Boolean = false
   ) {
       val hasCommands: Boolean get() = openWindow || turnOnFan
   }

   sealed class ManualVentilationDecision {
       data object Allowed : ManualVentilationDecision()
       data class Rejected(val reason: String) : ManualVentilationDecision()
   }
   ```
   - `SmokeVentilationCoordinator` bọc `SmokeSafetyGate` bên trong, theo dõi:
     - `lastAutoDispatchMs: Long = 0L` (dùng clock truyền vào `elapsedMs: Long` để dễ test tất định trên JVM, không gọi cứng `SystemClock` bên trong class pure Kotlin!).
     - `eventAlertedInCurrentEpisode: Boolean = false` (nếu `syncEventsLocked` đã phát cảnh báo từ event `smoke_detected`/`smoke_auto_vent` trong đợt khói hiện tại, không phát trùng cảnh báo thứ hai từ `applySensor`, và tự reset khi khói hạ dưới vùng trễ hysteresis).
   - Các hàm chính:
     - `onSensorUpdated(state: SmartHomeState, incomingHardware: HardwareStatus?, elapsedMs: Long): AutoVentilationPlan`
     - `onSmokeEventsSynced(eventKinds: Collection<String>)`
     - `evaluateWindowCommand(state: SmartHomeState, targetOpen: Boolean): ManualVentilationDecision`
     - `evaluateFanCommand(state: SmartHomeState, targetOn: Boolean): ManualVentilationDecision`
     - `reset()` (gọi trong `setConnection` khi đổi IP ESP32, cùng chỗ với `environmentGate = EnvironmentEvents()`).
2. **Tích hợp trong `SmartHomeViewModel.kt`:**
   - Trong `refreshLocked()` (đang giữ `network` lock):
     1. Đọc `data = Esp32Client.readSensor(...)`.
     2. Gọi `val autoPlan = applySensor(data)` (trả về `AutoVentilationPlan` để xử lý sau khi đồng bộ events!).
     3. Đồng bộ `syncEventsLocked(security)` như hiện tại — nếu trong `fresh` có `smoke_detected` hoặc `smoke_auto_vent`, gọi `smokeCoordinator.onSmokeEventsSynced(...)`.
     4. Nếu `autoPlan.alertTitle != null` và đợt khói này **chưa** được thông báo bởi `syncEventsLocked`: gọi `addAlert(AlertType.SMOKE, autoPlan.alertTitle)`.
     5. Nếu `autoPlan.hasCommands`: gọi hàm nội bộ `executeAutoVentilationLocked(autoPlan)` ngay trong `refreshLocked()` (**KHÔNG gọi `network.withLock` lồng nhau**):
        - Nếu `autoPlan.openWindow`: gọi `Esp32Client.sendWindow(ip, true, key)`
        - Nếu `autoPlan.turnOnFan`: gọi `Esp32Client.sendFan(ip, true, key)`
        - Đọc lại 1 lần `Esp32Client.readSensor(ip, key)` để cập nhật ngay trạng thái `windowOpen` / `fanOn` xác nhận lên `_state`, và ghi `LogItem("smoke", ...)` vào `logs`!

---

## 10. Luồng dữ liệu

```text
Chu kỳ refreshLocked() (mỗi 1s trên Dispatchers.IO, giữ network Mutex)
  │
  ├─ 1. Esp32Client.readSensor() ──► d.hardware (smokePpm, smokeAlert, windowOpen, fanOn)
  ├─ 2. applySensor(d) ────────────► cập nhật _state + SensorSample(..., smokePpm)
  │                                  + tính AutoVentilationPlan từ SmokeVentilationCoordinator
  ├─ 3. syncEventsLocked() ────────► nhận smoke_detected / smoke_auto_vent (nếu firmware gửi)
  │                                  ──► AlertItem(SMOKE) + Notifier.notify()
  ├─ 4. Nếu chưa có event trùng ───► addAlert(SMOKE, "Cảnh báo khói vượt ngưỡng · <X> ppm...")
  │                                  ──► Notifier.notify() báo về điện thoại
  └─ 5. Nếu autoPlan.hasCommands ──► sendWindow(true) & sendFan(true) trực tiếp qua LAN
                                     ──► đọc lại trạng thái xác nhận + cập nhật _state
                                     ──► CloudGatewaySync tự đẩy state & event mới lên RTDB
```

---

## 11. Logic nghiệp vụ

1. **Điều kiện kích hoạt Cảnh báo & Thông gió tự động (`300 ppm` mặc định):**
   - Ngưỡng hiệu lực: `val limit = state.smokeLimit.coerceIn(100, 2000)` (mặc định `300`).
   - Trạng thái nguy hiểm khói (`hazard`): `(incomingHardware?.smokePpm != null && incomingHardware.smokePpm >= limit) || incomingHardware?.smokeAlert == true`.
   - Khi đi vào vùng `hazard`:
     - Nếu `state.smokeAlertsEnabled == true`: tạo cảnh báo `"Cảnh báo khói vượt ngưỡng · ${ppm} ppm · Tự động mở cửa sổ & bật quạt"` (nếu `effectiveSmokeAuto` đang bật và mạch có cửa sổ/quạt) hoặc `"Cảnh báo khói vượt ngưỡng · ${ppm} ppm"`.
     - Nếu `state.effectiveSmokeAuto == true`:
       - Kiểm tra `openWindow = incomingHardware?.hasWindow == true && incomingHardware.windowOpen == false`
       - Kiểm tra `turnOnFan = incomingHardware?.hasFan == true && incomingHardware.fanOn == false`
       - Chỉ trả về `openWindow / turnOnFan = true` khi `lastAutoDispatchMs == 0L || elapsedMs - lastAutoDispatchMs >= 5_000L` (chống gửi dồn dập).
   - **Tương tác với chế độ bảo vệ (`AWAY` / `SLEEP`):** An toàn cháy nổ/khói độc luôn có độ ưu tiên cao hơn khóa điều khiển bảo vệ ⇒ tự động mở cửa sổ & quạt vẫn chạy ngay cả khi `mode == HomeMode.AWAY` hoặc `security?.protecting == true`.
2. **Xử lý sự kiện từ `/events` trong `syncEventsLocked`:**
   - `"smoke_detected"` -> `AlertType.SMOKE`. Nếu trong cùng `fresh` batch có `"smoke_auto_vent"` cùng `boot` (`seq == event.seq + 1`), gộp tiêu đề thành `"Phát hiện khói vượt ngưỡng · Đã mở cửa sổ và bật quạt"`, và bỏ qua tạo alert riêng cho bản ghi `"smoke_auto_vent"` liền kề đó để không hiện 2 thẻ cảnh báo trùng nhau! (Nếu chỉ có `"smoke_auto_vent"` đứng riêng lẻ thì vẫn tạo `AlertType.SMOKE` với `event.title`).
   - `"smoke_cleared"` -> `AlertType.SMOKE` với tiêu đề `"Nồng độ khói đã về mức an toàn"`.
3. **Điều khiển thủ công `setWindow(open: Boolean)` & `setFan(on: Boolean)`:**
   - Từ chối ngay nếu `!_loggedIn.value || !s.esp32Online || s.ventilationBusy || s.modeBusy || s.alarmSettingsBusy`.
   - Từ chối nếu thiết bị chưa báo có phần cứng tương ứng (`!hasWindow` / `!hasFan`), ghi `ventilationMessage` hướng dẫn kết nối phần cứng.
   - Nếu người dùng bấm **Đóng cửa sổ (`open = false`)** hoặc **Tắt quạt (`on = false`)** trong khi `s.ventilationSafetyLocked == true` (khói vẫn `>= smokeLimit` và tự động đang bật): **TỪ CHỐI** và đặt `ventilationMessage = "Đang có khói vượt ngưỡng: giữ cửa sổ và quạt mở để thoát khói."`.
   - Nếu **không** có khói (`!s.smokeHazardActive`) mà nhà đang bật bảo vệ (`s.mode == HomeMode.AWAY || s.security?.protecting == true`): **TỪ CHỐI** thao tác thủ công và đặt `ventilationMessage = "Chuyển về Ở nhà trước khi điều khiển cửa sổ và quạt."`.
   - Khi hợp lệ: bật `ventilationBusy = true`, vào `network.withLock` gọi `Esp32Client.sendWindow` / `sendFan`, gọi `refreshLocked()`, đối chiếu `hardware?.windowOpen == open` / `hardware?.fanOn == on`, cập nhật `ventilationMessage` và `logs`, tắt `ventilationBusy = false` trong `finally`.
4. **Cập nhật cấu hình `setSmokeConfig(alertsEnabled: Boolean, thresholdPpm: Int, autoVentilation: Boolean)`:**
   - Chuẩn hóa `val cleanLimit = thresholdPpm.coerceIn(100, 2000)`.
   - Cập nhật ngay `_state` (`smokeAlertsEnabled = alertsEnabled`, `smokeLimit = cleanLimit`, `smokeAutoVentilation = autoVentilation`) để lưu xuống `Storage` và đồng bộ lên Firebase `config` ngay cả khi chưa cắm mạch ESP32!
   - Nếu `_loggedIn.value && s.esp32Online && s.hardware?.smokeAutoEnabled != null` (firmware đã hỗ trợ nhận cấu hình khói): đồng bộ thêm xuống ESP32 qua `Esp32Client.sendSmokeConfig(s.esp32Ip, autoVentilation, cleanLimit, s.deviceKey)` bên trong `network.withLock`.

---

## 12. Xử lý lỗi và concurrency

- **QUY TẮC SỐNG CÒN CHỐNG DEADLOCK:** `network: Mutex` trong `SmartHomeViewModel` không reentrant. Tuyệt đối **không** gọi `network.withLock` bên trong `refreshLocked()`, `applySensor()`, `syncEventsLocked()` hay bất kỳ hàm `*Locked()` nào! Mọi thao tác gửi lệnh tự động trong chu kỳ polling phải dùng hàm nội bộ `*Locked()` chạy tiếp nối ngay trong thân `refreshLocked()`.
- Luôn đặt `_state.update { it.copy(ventilationBusy = false) }` trong khối `finally` để không bao giờ kẹt trạng thái loading khi mạng lỗi hoặc coroutine bị hủy.
- Không nuốt `CancellationException`.

---

## 13. Bất biến cần bảo toàn

1. Vòng lặp polling LAN 1 giây, điều khiển Đèn/Cửa/Còi (`setDevice`), Giàn phơi (`setClothesline`), Chế độ nhà (`setMode`), Khuôn mặt (`openDoorForFace`) và Cloud Sync/Fallback giữ nguyên 100% hành vi hiện hữu.
2. Không tạo cảnh báo khói giả khi `smokePpm == null` và `smokeAlert != true`.
3. Khi nồng độ khói hạ xuống dưới ngưỡng (`< 300 ppm`), **không tự động đóng cửa sổ hay tắt quạt** (giữ nguyên mở/bật để thông thoáng cho tới khi người dùng chủ động đóng/tắt).
4. `SmokeVentilationCoordinator.kt` không import `android.os.SystemClock` hay `com.google.firebase.*`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `SmokeVentilationCoordinator.kt` triển khai đầy đủ logic đánh giá ngưỡng `300 ppm` + hysteresis, gộp/chống trùng cảnh báo với `/events`, kế hoạch mở cửa sổ + quạt 2 lớp có cooldown `5_000 ms`, và kiểm tra quyền điều khiển thủ công.
2. `SmartHomeViewModel.kt` nối đầy đủ `SmokeVentilationCoordinator`, lưu `smokePpm` vào `SensorSample`, bắn `Notifier` cho `AlertType.SMOKE`, thực thi auto-ventilation không deadlock, và cung cấp `setWindow`, `setFan`, `setSmokeConfig`.
3. `SmokeVentilationCoordinatorTest.kt` bao phủ tối thiểu 10 kịch bản:
   - `299 ppm` không cảnh báo, không phát lệnh mở cửa sổ/quạt.
   - `300 ppm` khi cửa sổ đóng & quạt tắt → phát cảnh báo + `openWindow = true, turnOnFan = true`.
   - `320 ppm` khi firmware đã tự mở sẵn (`windowOpen = true, fanOn = true`) → phát cảnh báo 1 lần nhưng `openWindow = false, turnOnFan = false` (không bắn lệnh thừa).
   - `350 ppm` khi nhà đang ở chế độ `AWAY` (`protecting = true`) → vẫn tự động `openWindow = true, turnOnFan = true`.
   - Cooldown `5_000 ms`: nếu ESP32 chưa phản hồi mở tại giây thứ 2 (`elapsed = 2000`), chưa bắn lại lệnh; sau `5_000 ms` mới thử lại.
   - Chống thông báo kép khi `onSmokeEventsSynced(listOf("smoke_detected"))` đã chạy trong cùng đợt khói.
   - Hysteresis: hạ từ `340 ppm` về `270 ppm` chưa re-arm; hạ về `240 ppm` re-arm; tăng lại `305 ppm` báo đợt mới.
   - Chặn đóng cửa sổ (`targetOpen = false`) và chặn tắt quạt (`targetOn = false`) khi `ventilationSafetyLocked == true`, nhưng cho phép mở cửa sổ / bật quạt.
   - Khi hết khói (`120 ppm`) ở chế độ `HOME`, cho phép đóng cửa sổ và tắt quạt bình thường; ở chế độ `AWAY`, chặn điều khiển thủ công.
   - Khi firmware cũ chưa gắn cửa sổ/quạt (`hasWindow = false, hasFan = false`), từ chối điều khiển với thông báo rõ ràng.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → Toàn bộ JVM unit tests cũ + `SmokeVentilationCoordinatorTest` **100% PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ đúng 3 file thuộc Mục 6 (`1` sửa + `2` thêm mới).
2. Kiểm tra không có gọi lồng `network.withLock` bên trong `refreshLocked` / `applySensor` / `syncEventsLocked`:
   `Select-String -Path app\src\main\java\com\example\smarthomeai\data\SmartHomeViewModel.kt -Pattern "network\.withLock" -Context 2,4`
3. Kiểm tra `SmokeVentilationCoordinator.kt` không phụ thuộc `SystemClock` hay Firebase:
   `Select-String -Path app\src\main\java\com\example\smarthomeai\data\SmokeVentilationCoordinator.kt -Pattern "android\.os\.SystemClock|com\.google\.firebase"` → `0` kết quả.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: SmartHomeViewModel chưa điều phối cảnh báo khói 300 ppm về điện thoại và chưa tự động mở cửa sổ, bật quạt.
EVIDENCE: <file:dòng & kết quả static checks mục 15>
CHANGES: <danh sách file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy :app:testDebugUnitTest và :app:assembleDebug).
SCOPE: OK
RISKS: None | Đã kiểm tra tĩnh tránh gọi lồng network.withLock.
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác sạch bằng `git revert <commit_hash_prompt_03>`.
- Không tác động schema DB hay dữ liệu Cloud đã lưu.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short`.
2. `git add` tường minh đúng 3 file thuộc Mục 6.
3. Kiểm tra `git diff --cached --stat`.
4. Tạo commit tiếng Việt:
   `git commit -m "Tích hợp cảnh báo khói 300 ppm và tự động mở cửa sổ, quạt trong ViewModel"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Phát hiện bất kỳ luồng nào có thể gọi lồng `network.withLock` hoặc giữ khóa mạng vô hạn.
- Nhánh B phát hiện bất kỳ unit test nào fail hoặc lỗi biên dịch → Nhánh B **DỪNG**, báo cáo phân loại lỗi kèm bằng chứng và không tự sửa code sản xuất.
