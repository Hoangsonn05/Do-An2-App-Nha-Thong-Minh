# PROMPT 04 — MQ2-D4: GIAO DIỆN HIỂN THỊ KHÓI MQ-2 (PPM), CẢNH BÁO 300 PPM, ĐIỀU KHIỂN CỬA SỔ & QUẠT VÀ CLOUD VIEW

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Jetpack Compose UI/UX Engineer xây dựng thẻ chuyên dụng `SmokeVentilationCard.kt` và tích hợp hiển thị nồng độ khói **MQ-2 (`ppm`)**, trạng thái cảnh báo vượt ngưỡng **`300 ppm`**, điều khiển **Cửa sổ** & **Quạt thông gió**, biểu đồ lịch sử `ppm`, hộp thoại cấu hình `SmokeConfigDialog`, chế độ xem từ xa qua Firebase Cloud (`CloudSyncStatus.kt`) và nối callback trong `MainActivity.kt`, kèm viết bộ kiểm thử giao diện `SmokeVentilationUiTest.kt` (không chạy Gradle/AVD ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent UI/Accessibility QA Engineer kiểm tra diff của Nhánh A đối chiếu toàn bộ anti-patterns UI (0 emoji, touch target ≥ 48dp, tương phản màu, wrap text) và các bất biến bảo vệ `AppSmokeTest.kt`, sau đó trực tiếp chạy `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` (và `:app:connectedDebugAndroidTest` nếu có AVD/thiết bị đang bật).

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (khớp commit kết thúc của Prompt `03`) và `git status --porcelain -- app cloud-rules esp32` sạch.
2. Đọc lại `HomeScreen.kt`, `EnvironmentScreen.kt`, `SensorChart.kt`, `AlertsScreen.kt`, `AutomationScreen.kt`, `SettingsScreen.kt`, `SystemScreen.kt`, `CloudSyncStatus.kt`, `Components.kt`, `Theme.kt`, `MainActivity.kt`, và đọc kỹ `AppSmokeTest.kt` (đặc biệt dòng 240–382).
3. Đối chiếu từng yêu cầu UI/UX và bất biến hồi quy ở Mục 4–13.
4. Chốt thiết kế component và chữ ký hàm Composable (giữ default arguments) theo Mục 9–12.
5. Triển khai các file thuộc Mục 6, kèm viết `SmokeVentilationUiTest.kt` (**không chạy**).
6. Không sửa bất kỳ file nào ngoài Mục 6 (đặc biệt cấm sửa `AppSmokeTest.kt`, `data/**`, `cloud/**`).
7. Chỉ chạy **static checks** quy định tại Mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy Android Emulator / thiết bị.**
11. **KHÔNG chạy Gradle build / lint.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất **Handoff Report** đúng mẫu Mục 16.
14. Tạo **01 Git commit tiếng Việt** theo Mục 18 (**TUYỆT ĐỐI KHÔNG `git push`**).
15. **DỪNG** hoàn toàn và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc toàn bộ `git show --stat HEAD` và `git diff HEAD~1..HEAD`.
3. Kiểm tra chặt chẽ chữ ký các Composable, các bất biến `AppSmokeTest` (Mục 13), và quy chuẩn UI/UX (không dùng emoji, semantics label đầy đủ).
4. Bổ sung test nếu thiếu độ phủ và trực tiếp chạy các lệnh kiểm thử/build/lint tại Mục 14.
5. **KHÔNG** tự sửa code sản xuất trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được trong code của Nhánh A.
7. Phân loại mọi lỗi phát hiện theo `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu người dùng phê duyệt prompt sửa lỗi riêng. Nếu `PASS` hoàn toàn: xác nhận hoàn tất bộ 4 prompt và sẵn sàng chuyển sang **Post-Integration Audit**.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01`, `02`, `03` đã hoàn tất và đạt `PASS` ở Nhánh B.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `03` (`"Tích hợp cảnh báo khói 300 ppm và tự động mở cửa sổ, quạt trong ViewModel"` hoặc commit test-only ngay sau đó của Nhánh B Prompt `03`).
- **Working tree:** `git status --porcelain -- app cloud-rules esp32` rỗng hoàn toàn.

---

## 3. Trạng thái hiện tại đã xác minh

1. `HomeScreen.kt`:
   - Dòng `149-156`: Thẻ cảm biến nhanh mới hiển thị 4 ô (`Nhiệt độ`, `Độ ẩm`, `Mưa`, `Chuyển động`).
   - Dòng `184-189`: Danh sách thiết bị gồm `DeviceControlRow(LIGHT)`, `ClotheslineCard`, `AlarmControlCard`.
   - `AppSmokeTest.kt:240-245` (`deviceControlsDisabledWhenOffline`): kiểm tra `compose.onAllNodes(isToggleable()).assertAll(isNotEnabled())` trên `HomeScreen(SmartHomeState(), ...)` ⇒ **mọi `Switch` / `FilterChip` trên `HomeScreen` bắt buộc phải `enabled = false` khi `!state.esp32Online`!**
2. `EnvironmentScreen.kt`:
   - Dòng `79-86`: Dropdown biểu đồ mới có `"Nhiệt độ"` và `"Độ ẩm"`.
   - Dòng `148-174` (`LiveSensorsCard`): chưa có dòng `Khói MQ-2`, `Cửa sổ`, `Quạt`.
   - Dòng `193-214` (`EnvironmentConfigDialog`): `AppSmokeTest.kt:273-282` gọi `compose.onNode(isToggleable()).performClick()` ngay khi mở `"Cảnh báo môi trường"` ⇒ **`EnvironmentConfigDialog` BẮT BUỘC giữ nguyên duy nhất 1 `SmartSwitch` và giữ nguyên chữ ký 4 tham số `(Boolean, Int, Int, Int) -> Unit`!**
3. `CloudSyncStatus.kt`:
   - `cloudEventTitle` (dòng `81-120`) chưa có 8 mã sự kiện khói/cửa sổ/quạt.
   - `CloudReadOnlySummaryCard` (dòng `287-414`) chưa hiển thị chỉ số Khói (`ppm`), Cửa sổ và Quạt khi xem từ xa qua Cloud.
4. `AlertsScreen.kt:28, 80`: Bộ lọc `categories` chưa có `AlertType.SMOKE`.
5. `MainActivity.kt:83-94`: Chưa truyền `vm::setWindow`, `vm::setFan`, `vm::setSmokeConfig` xuống các màn hình.

---

## 4. Mục tiêu cụ thể

1. Xây dựng `ui/components/SmokeVentilationCard.kt` hiển thị đồng bộ trên Trang chủ:
   - Nồng độ khói **MQ-2** theo đơn vị **`ppm`** (ví dụ `125 ppm` màu xanh `Blue600`/`GreenOk` khi an toàn; `340 ppm` kèm pill `"Cảnh báo khói!"` màu `RedAlert`/`RedSoft` nổi bật khi `>= state.smokeLimit`, mặc định `300 ppm`).
   - Điều khiển trực quan **Cửa sổ** (`Mở cửa sổ` / `Đóng cửa sổ`) và **Quạt thông gió** (`Bật quạt` / `Tắt quạt`), kèm công tắc `"Tự mở cửa sổ & quạt khi có khói"` và nút mở hộp thoại chỉnh ngưỡng (`SmokeConfigDialog`, mặc định `300 ppm`).
   - Khi chưa có phần cứng MQ-2/Cửa sổ/Quạt trên ESP32 (`!hasMq2 && !hasWindow && !hasFan`), hiển thị gọn gàng trạng thái **"Sẵn sàng tích hợp phần cứng MQ-2"** kèm thông tin ngưỡng đặt sẵn (`300 ppm`) và sơ đồ chân khuyến nghị (`MQ-2 GPIO34, Cửa sổ GPIO21, Quạt GPIO22`), không hiển thị nút giả đánh lừa người dùng.
2. Hiển thị chỉ số **Khói MQ-2 (`ppm`)** trên thẻ tổng quan `HomeScreen`, trong `LiveSensorsCard` của `EnvironmentScreen`, trên biểu đồ lịch sử (`SensorChart.kt` + `EnvironmentScreen.kt`), và trong chế độ xem từ xa **Cloud chỉ đọc** (`CloudSyncStatus.kt`).
3. Bổ sung lọc cảnh báo `"Khói / Khí gas"` (`AlertType.SMOKE`) tại `AlertsScreen.kt`, liên kết cấu hình tại `AutomationScreen.kt` & `SettingsScreen.kt`, dòng chẩn đoán phần cứng MQ-2 tại `SystemScreen.kt`, và nối đầy đủ callback từ `SmartHomeViewModel` tại `MainActivity.kt`.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Tạo mới `app/src/main/java/com/example/smarthomeai/ui/components/SmokeVentilationCard.kt` (gồm `SmokeVentilationCard` và `SmokeConfigDialog`).
- Cập nhật `CloudSyncStatus.kt`, `HomeScreen.kt`, `EnvironmentScreen.kt`, `SensorChart.kt`, `AlertsScreen.kt`, `AutomationScreen.kt`, `SettingsScreen.kt`, `SystemScreen.kt`, `MainActivity.kt`.
- Tạo mới file kiểm thử giao diện `app/src/androidTest/java/com/example/smarthomeai/SmokeVentilationUiTest.kt`.

### Ngoài phạm vi
- Không sửa bất kỳ file nào trong `data/**`, `cloud/**`, `cloud-rules/**`, `esp32/**`.
- Không sửa `EnvironmentConfigDialog` hay bất kỳ file test hiện hữu nào (`AppSmokeTest.kt`, `CloudHybridViewUiTest.kt`, `CloudSettingsSingleOwnerUiTest.kt`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/ui/components/CloudSyncStatus.kt`
2. `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt`
3. `app/src/main/java/com/example/smarthomeai/ui/screens/EnvironmentScreen.kt`
4. `app/src/main/java/com/example/smarthomeai/ui/screens/SensorChart.kt`
5. `app/src/main/java/com/example/smarthomeai/ui/screens/AlertsScreen.kt`
6. `app/src/main/java/com/example/smarthomeai/ui/screens/AutomationScreen.kt`
7. `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`
8. `app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt`
9. `app/src/main/java/com/example/smarthomeai/MainActivity.kt`

### Thêm mới (`NEW`)
1. `app/src/main/java/com/example/smarthomeai/ui/components/SmokeVentilationCard.kt`
2. `app/src/androidTest/java/com/example/smarthomeai/SmokeVentilationUiTest.kt`

---

## 7. File bị cấm sửa

- Toàn bộ `app/src/main/java/com/example/smarthomeai/data/**`
- Toàn bộ `app/src/main/java/com/example/smarthomeai/cloud/**`
- `app/src/main/java/com/example/smarthomeai/ui/components/Components.kt` (`DeviceControlRow` giữ nguyên cho 3 thiết bị `DeviceType`)
- `cloud-rules/**`, `esp32/**`
- Toàn bộ các file test hiện có (`AppSmokeTest.kt`, `CloudHybridViewUiTest.kt`, v.v.)

---

## 8. Dependency

- **Phụ thuộc đầu vào:** Prompt `02` (các trường `smokePpm`, `window`, `fan` trên `CloudDeviceSnapshot`/`GatewayStateSnapshot`) và Prompt `03` (`setWindow`, `setFan`, `setSmokeConfig` trên `SmartHomeViewModel`).
- **Làm đầu vào bắt buộc cho:** **Post-Integration Audit** (`AUDIT-PLAN-POST-INTEGRATION.md`).

---

## 9. Kiến trúc triển khai

1. **Quy chuẩn UI/UX bắt buộc (UI/UX Pro Max):**
   - **0 Emoji:** Chỉ dùng Material Vector Icons (`Icons.Default.Air`, `Icons.Default.WarningAmber`, `Icons.Default.Sensors`, `Icons.Default.MeetingRoom`, `Icons.Default.CheckCircle`, `Icons.Default.Tune`).
   - **Touch target & Semantics:** Mọi nút bấm `heightIn(min = 48.dp)`, mọi `SmartSwitch` có `label` rõ ràng để hỗ trợ TalkBack & Compose UI test; mọi badge/nhãn cho phép tự xuống dòng (không `maxLines = 1` làm cắt mất số `ppm` hay cảnh báo).
   - **Tương phản cảnh báo:** Khi `state.smokeHazardActive == true`, thẻ `SmokeVentilationCard` hiển thị banner cảnh báo nền `RedSoft`, chữ/icon `RedAlert` (`>= 4.5:1`), nêu rõ số đo `ppm` hiện tại và trạng thái cửa sổ/quạt.
2. **Chữ ký Composable tương thích ngược 100% (thêm tham số mới ở cuối kèm default):**
   - `HomeScreen`: giữ nguyên 8 tham số đầu hiện có (`state`, `onSetDevice`, `onMode`, `onRefresh`, `onNavigate`, `onClothesline`, `onRainRetraction`, `cloudBridge`, `deviceBridge`), thêm ở cuối:
     ```kotlin
     onWindow: (Boolean) -> Unit = {},
     onFan: (Boolean) -> Unit = {},
     onSmokeConfig: (Boolean, Int, Boolean) -> Unit = { _, _, _ -> }
     ```
   - `EnvironmentScreen`: giữ nguyên 6 tham số đầu hiện có (`state`, `onRefresh`, `onNavigate`, `onEnvironmentConfig`, `onBack`, `mainPage`), thêm ở cuối:
     ```kotlin
     onSmokeConfig: (Boolean, Int, Boolean) -> Unit = { _, _, _ -> }
     ```
   - `SettingsScreen`: giữ nguyên 12 tham số đầu hiện có, thêm ở cuối:
     ```kotlin
     onSmokeConfig: (Boolean, Int, Boolean) -> Unit = { _, _, _ -> }
     ```
3. **Thiết kế `SmokeVentilationCard` & `SmokeConfigDialog` (`ui/components/SmokeVentilationCard.kt`):**
   - `testTag("smoke-ventilation-card")` trên `WhiteCard`.
   - Hiển thị tiêu đề `"Phòng khói & Thông gió (MQ-2)"`.
   - **Trường hợp 1 — Chưa phát hiện phần cứng mới (`h?.hasMq2 != true && h?.hasWindow != true && h?.hasFan != true`):**
     - Hiển thị dòng trạng thái `"Chờ tích hợp phần cứng ESP32 (MQ-2 · Cửa sổ · Quạt)"` và mô tả `"Khung phần mềm & Cloud đã sẵn sàng. Mặc định báo động từ ${state.smokeLimit} ppm, tự mở cửa sổ và bật quạt."`.
     - Có `TextButton("Cài đặt ngưỡng khói (${state.smokeLimit} ppm)")` mở `SmokeConfigDialog` (để người dùng xem/chỉnh sẵn cấu hình App + Cloud ngay từ bây giờ; lưu ý **không** đặt `Switch` bật sẵn trên thẻ khi offline).
   - **Trường hợp 2 — Đã có dữ liệu phần cứng (`hasMq2 || hasWindow || hasFan`):**
     - Hiển thị nồng độ khói trực tiếp: `h?.smokePpm?.let { "$it ppm" } ?: if (!state.esp32Online) "Mất kết nối" else "Đang làm nóng cảm biến"`.
     - Nếu `state.smokeHazardActive`: hiển thị khối cảnh báo nổi bật màu `RedAlert`/`RedSoft`: `"Cảnh báo khói vượt ngưỡng (${state.smokeLimit} ppm)! Hệ thống tự động mở cửa sổ và bật quạt."`.
     - Nếu `h?.hasWindow == true`: hiển thị trạng thái cửa sổ (`"Đang mở"` / `"Đã đóng"` / `"Chưa rõ"`) + cặp nút `Button("Mở cửa sổ")` & `OutlinedButton("Đóng cửa sổ")`:
       - Nút `"Mở cửa sổ"` bật khi `state.esp32Online && !state.ventilationBusy && !state.modeBusy && h.windowOpen != true && (state.smokeHazardActive || (state.mode != HomeMode.AWAY && state.security?.protecting != true))`.
       - Nút `"Đóng cửa sổ"` bật khi `state.esp32Online && !state.ventilationBusy && !state.modeBusy && h.windowOpen != false && !state.ventilationSafetyLocked && state.mode != HomeMode.AWAY && state.security?.protecting != true`.
     - Nếu `h?.hasFan == true`: hiển thị trạng thái quạt (`"Đang bật"` / `"Đang tắt"` / `"Chưa rõ"`) + cặp nút `Button("Bật quạt")` & `OutlinedButton("Tắt quạt")` với quy tắc khóa an toàn tương tự (`!state.ventilationSafetyLocked`).
     - Hàng công tắc `"Tự mở cửa sổ & quạt khi có khói"` dùng `SmartSwitch` với **`enabled = state.esp32Online && !state.ventilationBusy && !state.modeBusy`** (đảm bảo khi `!state.esp32Online` thì công tắc bị disable, thỏa mãn tuyệt đối `AppSmokeTest.deviceControlsDisabledWhenOffline`!).
     - Nếu `state.ventilationSafetyLocked`: hiển thị dòng chú thích màu `OrangeWarn`: `"Đang có khói (≥ ${state.smokeLimit} ppm): khóa đóng cửa sổ và tắt quạt để đảm bảo an toàn."`.
     - Hiển thị `LinearProgressIndicator` khi `state.ventilationBusy` và `state.ventilationMessage` nếu có.
   - **`SmokeConfigDialog`:**
     - Hộp thoại riêng biệt cho phép bật/tắt `"Cảnh báo khói về điện thoại"`, bật/tắt `"Tự động mở cửa sổ & bật quạt"`, chọn ngưỡng `ppm` bằng `Slider` (`100..1000 ppm`, bước `50 ppm`) + hàng nút chọn nhanh `200 ppm`, `300 ppm (Mặc định)`, `500 ppm`.
4. **Cập nhật Biểu đồ & Màn hình Cảm biến (`EnvironmentScreen.kt` + `SensorChart.kt`):**
   - Trong `SensorChart.kt`: thêm overload hoặc tham số mặc định `valueSelector: ((SensorSample) -> Float)? = null, labelName: String? = null` (giữ nguyên hàm `SensorChart(samples, temperature)` cũ tương thích 100%). Khi người dùng chọn `"Khói MQ-2"` trên `EnvironmentScreen`, lọc `samples.filter { it.smokePpm != null }` và vẽ đường biểu đồ theo đơn vị `ppm` (Thấp nhất / Trung bình / Cao nhất kèm hậu tố `" ppm"` từ `summarizeSmoke`).
   - Trong `LiveSensorsCard` (`EnvironmentScreen.kt`): thêm hiển thị `SensorDetail("Khói MQ-2", ...)` (ví dụ `"320 ppm · Vượt ngưỡng"` hoặc `"110 ppm"` hoặc `"Chưa gắn cảm biến"`), và nếu `hasWindow` / `hasFan` thì hiển thị thêm `SensorDetail("Cửa sổ", ...)` & `SensorDetail("Quạt thông gió", ...)`.
   - Thêm thẻ `SettingsLink("Cảnh báo khói MQ-2 & Thông gió", Icons.Default.Air, ...)` ngay dưới thẻ `"Cảnh báo môi trường"` để mở `SmokeConfigDialog` (**không chạm vào `EnvironmentConfigDialog`**).
5. **Cập nhật Cloud Read-Only (`CloudSyncStatus.kt`) & `HomeScreen.kt`:**
   - Thêm `internal fun CloudDeviceSnapshot.effectiveSmokePpm(): Int?` (ưu tiên bản mới hơn giữa `gatewayState?.smokePpm` và `latestTelemetry?.smokePpm`, giống hệt `effectiveTemp()`/`effectiveHum()`).
   - Trên thẻ cảm biến của `HomeScreen.kt`: thêm dòng hiển thị `Reading("Khói MQ-2", smokeText, ...)` (lấy `effectiveSmokePpm()` khi đang ở Cloud Fallback hoặc `state.hardware?.smokePpm` khi ở LAN, định dạng `"$it ppm"` hoặc `"—"`).
   - Trong `CloudReadOnlySummaryCard` (`CloudSyncStatus.kt`): thêm hàng `CloudStateMetric` cho `"Khói MQ-2"` (`"${ppm} ppm"` hoặc `"—"`), `"Cửa sổ"` (`"Đang mở"` / `"Đã đóng"` / `"—"`), `"Quạt"` (`"Đang bật"` / `"Đang tắt"` / `"—"`), và bổ sung đủ 8 mã event mới vào `cloudEventTitle`.

---

## 10. Luồng dữ liệu

```text
SmartHomeViewModel.state (StateFlow<SmartHomeState>) + cloudSnapshot (StateFlow<CloudDeviceSnapshot?>)
  │
  ├─► MainActivity (AppRoot) nối vm::setWindow, vm::setFan, vm::setSmokeConfig
  │
  ├─► HomeScreen
  │     ├─ Ô đọc nhanh: Nhiệt độ | Độ ẩm | Mưa | Chuyển động | Khói MQ-2 (ppm)
  │     ├─ SmokeVentilationCard (ppm trực tiếp, cảnh báo đỏ >= 300 ppm, nút Cửa sổ & Quạt)
  │     └─ CloudReadOnlySummaryCard (hiển thị Khói ppm, Cửa sổ, Quạt khi ngoài mạng LAN)
  │
  ├─► EnvironmentScreen ──► LiveSensorsCard + Biểu đồ "Khói MQ-2" (ppm) + SmokeConfigDialog
  ├─► AlertsScreen ───────► Lọc danh mục "Khói / Khí gas" (AlertType.SMOKE)
  └─► Settings / Automation / SystemScreen ──► Cấu hình ngưỡng 300 ppm & Chẩn đoán MQ-2
```

---

## 11. Logic nghiệp vụ

1. **Hiển thị đúng đơn vị `ppm` và trạng thái biên:**
   - Khi `smokePpm != null`: luôn hiển thị kèm đơn vị cách 1 khoảng trắng: `"$smokePpm ppm"` (ví dụ `"85 ppm"`, `"300 ppm"`).
   - Khi `!state.esp32Online`: không hiển thị banner đỏ `"Đang báo động khói"` giả từ số đo cache cũ (giống nguyên tắc `AppSmokeTest.offlineAlarmDoesNotPresentCachedReadingAsLiveWarning`), mà hiển thị rõ trạng thái mất kết nối / số đo lần cuối.
2. **Bảo vệ an toàn thao tác trên UI:**
   - Khi `state.ventilationSafetyLocked == true` (`esp32Online && smoke >= 300 ppm && autoEnabled`), nút `"Đóng cửa sổ"` và `"Tắt quạt"` bị vô hiệu hóa (`enabled = false`), đồng thời hiển thị giải thích rõ ràng vì sao đang khóa.
   - Khi khói hạ xuống mức an toàn (`< 300 ppm`, ví dụ `140 ppm`) ở chế độ `HOME`, nút `"Đóng cửa sổ"` và `"Tắt quạt"` mở khóa trở lại để chủ nhà tự đóng/tắt.

---

## 12. Xử lý lỗi và concurrency

- Mọi trạng thái hộp thoại cục bộ (`showSmokeConfig`, giá trị slider tạm) dùng `rememberSaveable` để sống sót qua xoay màn hình.
- Không thực hiện bất kỳ lời gọi mạng hay I/O trực tiếp nào trong hàm `@Composable` (mọi hành động đều ủy quyền qua callback về `SmartHomeViewModel`).

---

## 13. Bất biến cần bảo toàn

1. **`AppSmokeTest.deviceControlsDisabledWhenOffline`:** Khi render `HomeScreen(SmartHomeState(), ...)` (`esp32Online = false`), `onAllNodes(isToggleable()).assertAll(isNotEnabled())` bắt buộc PASS.
2. **`AppSmokeTest.allThreeDevicesAreVisibleAndCanBeControlledFromHome` & `armedDevicesRequireDisarmingFirst`:** `DeviceType.entries` (`LIGHT`, `DOOR`, `ALARM`) trên `HomeScreen` giữ nguyên `contentDescription` và hành vi bật/tắt.
3. **`AppSmokeTest.environmentConfigurationCallsSave`:** `EnvironmentConfigDialog` giữ nguyên đúng 1 `SmartSwitch` duy nhất và nút `"Lưu thiết lập"`.
4. **0 Emoji** trong bất kỳ chuỗi UI nào thuộc toàn bộ các file sửa/mới.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. Hoàn thiện `SmokeVentilationCard.kt` + cập nhật đầy đủ 9 file UI/`MainActivity` với chữ ký tương thích ngược 100%.
2. `SmokeVentilationUiTest.kt` kiểm chứng tối thiểu 6 kịch bản Compose UI:
   - Khi `SmartHomeState()` chưa có phần cứng MQ-2 (`hasMq2 = false`), hiển thị trạng thái chờ phần cứng và ngưỡng mặc định `300 ppm`, không có nút điều khiển giả đang bật.
   - Khi `esp32Online = true, smokePpm = 120, hasMq2 = true, hasWindow = true, windowOpen = false, hasFan = true, fanOn = false`: hiển thị `"120 ppm"`, không hiện cảnh báo khói, bấm `"Mở cửa sổ"` gọi `onWindow(true)`, bấm `"Bật quạt"` gọi `onFan(true)`.
   - Khi `esp32Online = true, smokePpm = 320, smokeLimit = 300, hasMq2 = true, hasWindow = true, windowOpen = true, hasFan = true, fanOn = true, smokeAutoVentilation = true`: hiển thị `"320 ppm"`, hiện cảnh báo vượt ngưỡng, và nút `"Đóng cửa sổ"` + `"Tắt quạt"` đều `assertIsNotEnabled()`.
   - Khi `esp32Online = false`: mọi nút và công tắc trên `SmokeVentilationCard` đều `assertIsNotEnabled()`, không hiện cảnh báo khói trực tiếp giả.
   - Hộp thoại `SmokeConfigDialog` cho phép lưu ngưỡng mới (ví dụ chọn nhanh `500 ppm`) và trả đúng callback `onSmokeConfig`.
   - `CloudReadOnlySummaryCard` hiển thị đúng `"340 ppm"`, trạng thái Cửa sổ `"Đang mở"`, Quạt `"Đang bật"` và tiêu đề sự kiện `"Có khói · tự động mở cửa sổ và bật quạt"` khi xem từ dữ liệu Cloud Fallback.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → **100% PASS**.
2. `.\gradlew.bat :app:lintDebug --console=plain` → **PASS** (0 lỗi lint mới).
3. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
4. Nếu có thiết bị Android / AVD đang kết nối (`adb devices` có `device`): chạy `.\gradlew.bat :app:connectedDebugAndroidTest --console=plain` để xác nhận cả `AppSmokeTest`, `CloudHybridViewUiTest` và `SmokeVentilationUiTest` đều **PASS** (nếu máy hiện chưa bật AVD, ghi nhận rõ `PENDING AVD EXECUTION` cho phần instrumented runtime nhưng bắt buộc `assembleDebugAndroidTest` phải thành công 100%).

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ đúng 11 file thuộc Mục 6 (`9` sửa + `2` thêm mới).
2. Kiểm tra `EnvironmentConfigDialog` vẫn chỉ có duy nhất 1 `SmartSwitch`:
   `Select-String -Path app\src\main\java\com\example\smarthomeai\ui\screens\EnvironmentScreen.kt -Pattern "fun EnvironmentConfigDialog" -Context 0,22`
3. Kiểm tra `AppSmokeTest.kt` không bị chỉnh sửa:
   `git diff -- app/src/androidTest/java/com/example/smarthomeai/AppSmokeTest.kt` → rỗng.
4. Kiểm tra đầy đủ hiển thị đơn vị `ppm` và kết nối callback ở `MainActivity.kt`:
   `Select-String -Path app\src\main\java\com\example\smarthomeai\MainActivity.kt,app\src\main\java\com\example\smarthomeai\ui\components\SmokeVentilationCard.kt -Pattern "setWindow|setFan|setSmokeConfig|ppm"`

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Chưa có giao diện hiển thị nồng độ khói MQ-2 (ppm), cảnh báo ngưỡng 300 ppm, điều khiển cửa sổ/quạt và hiển thị dự phòng trên Cloud.
EVIDENCE: <file:dòng & kết quả static checks mục 15>
CHANGES: <danh sách file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy unit tests, lintDebug, assembleDebug và instrumented tests).
SCOPE: OK
RISKS: None | Đã giữ nguyên chữ ký Composable cũ và bảo toàn các bất biến AppSmokeTest.
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và thông báo rõ để người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác sạch bằng `git revert <commit_hash_prompt_04>`.
- Không ảnh hưởng dữ liệu người dùng hay cấu hình Firebase.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short`.
2. `git add` tường minh đúng 11 file thuộc Mục 6.
3. Kiểm tra `git diff --cached --stat`.
4. Tạo commit tiếng Việt:
   `git commit -m "Hoàn thiện giao diện cảm biến khói MQ-2, cảnh báo ppm và điều khiển cửa sổ, quạt"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Phát hiện bất kỳ thay đổi chữ ký nào làm gãy khả năng biên dịch của bộ test cũ (`AppSmokeTest`, `CloudHybridViewUiTest`, `CloudSettingsSingleOwnerUiTest`).
- Nhánh B phát hiện lỗi `lintDebug`, lỗi compile hoặc test fail → Nhánh B **DỪNG**, phân loại mức độ lỗi kèm bằng chứng và yêu cầu prompt vá lỗi riêng.
