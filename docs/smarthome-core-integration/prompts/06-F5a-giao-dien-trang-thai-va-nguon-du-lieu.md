# PROMPT 06 — F5a: HIỂN THỊ TRẠNG THÁI ĐỒNG BỘ VÀ NGUỒN DỮ LIỆU CLOUD TRÊN HOME

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Compose UI Engineer cập nhật `CloudHomeSection.kt` và `HomeScreen.kt` (có thể tách widget nhỏ `CloudSyncStatus.kt`) để hiển thị trạng thái kết nối/đồng bộ Gateway, nguồn dữ liệu `LAN / Cloud / Bộ nhớ máy`, thời điểm cập nhật, dữ liệu cũ, và chế độ xem Cloud chỉ đọc khi ra ngoài LAN — bảo toàn 100% tính năng LAN và tương thích test B3/Smoke.
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Compose UI Test Engineer đọc diff + handoff Nhánh A, rà soát UI & Accessibility (không emoji, contrast, touch target ≥44dp, text wrap không cắt xén), tạo/chạy bộ test `CloudHybridViewUiTest` và chạy lại toàn bộ `AppSmokeTest` + `CloudB3IntegrationTest` + `testDebugUnitTest` + `assembleDebug`.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (commit kết thúc của Prompt `05`) và `git status --porcelain` sạch.
2. Đọc lại `HomeScreen.kt`, `CloudHomeSection.kt`, `CloudDeviceUiBridge.kt`, `DataSourceResolver.kt`, `Theme.kt`, `Components.kt`, `AppSmokeTest.kt` (dòng 210–340, 370–450).
3. Đối chiếu từng yêu cầu mục 4–13 với source thực tế.
4. Hoàn thiện thiết kế UI mục 9–12.
5. Triển khai phạm vi mục 5–6 (kèm viết `CloudHybridViewUiTest.kt`, **không chạy**).
6. Không sửa `SettingsScreen.kt` hay `SystemScreen.kt` (thuộc Prompt `07`), không sửa `SmartHomeViewModel.kt`.
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
3. Kiểm tra code thực tế đối chiếu mục 13–14 (đặc biệt giữ nguyên chuỗi `"ESP32 mất kết nối"`, nút điều khiển khóa khi nguồn `CLOUD`, không emoji, chữ wrap).
4. Chạy `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` và `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"`.
5. **KHÔNG** sửa implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện và `file:dòng`.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu có lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `04` (`F2b`) và Prompt `05` (`F3`) đã hoàn tất và PASS.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `05`.
- **Working tree:** Sạch.

---

## 3. Trạng thái hiện tại đã xác minh

1. `HomeScreen.kt:21-24`:
   - `fun HomeScreen(state: SmartHomeState, onSetDevice: (DeviceType, Boolean) -> Unit, onMode: (HomeMode) -> Unit, onRefresh: () -> Unit, onNavigate: (String) -> Unit, onClothesline: (Boolean) -> Unit = {}, onRainRetraction: (Boolean) -> Unit = {}, cloudBridge: CloudUiBridge? = rememberActiveCloudBridge())`
   - Dòng 28: `StatusPill(if (state.esp32Online) "ESP32 đã kết nối" else "ESP32 mất kết nối", ...)` → `AppSmokeTest.kt:227-228` tìm đúng text `"ESP32 mất kết nối"`.
   - Dòng 37: `CloudHomeBannerFromBridge(cloudBridge, ...) { onNavigate("settings") }`.
   - Dòng 43–63: Card cảm biến chỉ đọc từ `SmartHomeState` (`state.temperature`, `state.humidity`, `state.lastSeen`).
   - `DoorControlCard`, `DeviceControlRow`, `ClotheslineCard`, `AlarmControlCard` đều khóa nút điều khiển dựa trên `state.esp32Online`.
2. `CloudHomeSection.kt`:
   - Chưa nhận `CloudDeviceUiBridge` nên chưa hiển thị trạng thái đồng bộ Gateway (`SYNCING` / `OK` / `PAUSED_LAN_OFFLINE` / `BLOCKED` / `ERROR`).
3. `Theme.kt` & `Components.kt`:
   - Đã có sẵn `WhiteCard`, `StatusPill`, `IconBadge`, màu chuẩn (`Blue600`, `Blue50`, `GreenOk`, `GreenSoft`, `OrangeWarn`, `YellowSoft`, `RedAlert`, `RedSoft`, `TextGray`, `TextDark`, `OffGray`).

---

## 4. Mục tiêu cụ thể

Hiển thị rõ ràng trạng thái đồng bộ Gateway, nguồn dữ liệu đang xem và dữ liệu Cloud dự phòng trên `HomeScreen` + `CloudHomeSection`:
1. Hiển thị badge/pill nguồn dữ liệu hiện hành (`Nguồn: LAN trực tiếp` / `Nguồn: Cloud` / `Nguồn: Bộ nhớ máy` / `Chưa có dữ liệu`), thời điểm cập nhật (`nowString` / `timeOnly`), và cảnh báo rõ khi dữ liệu cũ (`> 5 phút` hoặc Gateway đang offline).
2. Hiển thị trạng thái đồng bộ Gateway (ESP32 → Cloud) và thời điểm đồng bộ gần nhất trên `CloudHomeBannerCard` / `CloudHomeBannerFromBridge`.
3. Khi ở ngoài LAN và `resolvedSource == DataSource.CLOUD`: hiển thị các thông số Cloud mới nhất (nhiệt độ, độ ẩm, chuyển động, đèn, cửa, còi, chế độ an ninh, và tóm tắt sự kiện gần nhất từ Cloud nếu có) dưới dạng **chỉ đọc** minh bạch, trong khi toàn bộ nút điều khiển phần cứng LAN vẫn khóa đúng như thiết kế.
4. Giữ nguyên tương thích mọi lời gọi `HomeScreen(...)` trong `MainActivity.kt` và `AppSmokeTest.kt`.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Thêm tham số tùy chọn cuối cùng trên `HomeScreen`: `deviceBridge: CloudDeviceUiBridge? = rememberActiveCloudDeviceBridge()`.
- Thu thập `deviceBridge?.gatewaySync?.collectAsState()` và `deviceBridge?.cloudSnapshot?.collectAsState()` trong `HomeScreen` và tính `resolved = DataSourceResolver.resolve(...)` bằng `remember` / state hiện tại.
- Hiển thị ngay dưới hàng trạng thái ESP32 trên `HomeScreen`:
  - Giữ nguyên pill `"ESP32 đã kết nối"` / `"ESP32 mất kết nối"`.
  - Bổ sung pill/dòng trạng thái nguồn dữ liệu (`LAN trực tiếp`, `Cloud (chỉ xem)`, `Bộ nhớ máy`) kèm thời điểm cập nhật và badge `"Dữ liệu cũ"` nếu `resolved.isStale`.
- Khi `resolved.source == DataSource.CLOUD`:
  - Card cảm biến hiển thị nhiệt độ, độ ẩm, chuyển động lấy từ `cloudSnapshot`, gắn nhãn nguồn `"Đang hiển thị dữ liệu Cloud · chỉ đọc"` + thời điểm cập nhật server (`receivedAt`) + trạng thái Gateway (`online`/`offline`).
  - Hiển thị card/mục gọn tóm tắt trạng thái từ xa (`Đèn`, `Cửa`, `Còi`, `Chế độ`) và tối đa 3 sự kiện Cloud gần nhất (nếu có) ở dạng chỉ đọc (bao gồm sự kiện mưa / giàn phơi từ `events` theo `CD-2`).
- Trên `CloudHomeSection.kt` (có thể trích helper vào `CloudSyncStatus.kt`):
  - `CloudHomeBannerFromBridge` nhận thêm `deviceBridge: CloudDeviceUiBridge? = null` (có mặc định) và truyền xuống `CloudHomeBannerCard` (tham số cuối `gatewaySyncState: GatewaySyncUiState? = null`).
  - Khi có `gatewaySyncState` (khác `IDLE`): hiển thị 1 dòng/pill trạng thái đồng bộ Gateway (ví dụ: `"Đang đăng ký thiết bị..."`, `"Đồng bộ Cloud OK · HH:mm"`, `"Tạm dừng đồng bộ (ESP32 mất LAN)"`, `"Thiết bị bị thu hồi trên Cloud"`, `"Lỗi đồng bộ Cloud"` kèm mô tả/cảnh báo nếu có nhiều device active).
- Viết Compose UI test mới `CloudHybridViewUiTest.kt` trong `androidTest`.

### Ngoài phạm vi
- Không sửa `SettingsScreen.kt` hay `SystemScreen.kt` (thuộc Prompt `07`).
- Không mở khóa nút điều khiển `DoorControlCard`, `DeviceControlRow`, `ClotheslineCard`, `AlarmControlCard` khi `esp32Online == false`.
- Không sửa `AppSmokeTest.kt`, `CloudB3IntegrationTest.kt`, `SmartHomeViewModel.kt`, `MainActivity.kt`.

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt`
2. `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`

### Thêm mới (`NEW`)
1. *(Tùy chọn, khuyến nghị để giữ `CloudHomeSection.kt` gọn)* `app/src/main/java/com/example/smarthomeai/ui/components/CloudSyncStatus.kt`
2. `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudHybridViewUiTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`, `Models.kt`, `Esp32Client.kt`, `Storage.kt`, `AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/**`
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`, `SystemScreen.kt`
- `app/src/androidTest/java/com/example/smarthomeai/AppSmokeTest.kt`, `CloudB3IntegrationTest.kt`, `CloudInitializationIntegrationTest.kt`
- `cloud-rules/**`, `esp32/**`, `app/build.gradle.kts`, `AndroidManifest.xml`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `04` (`F2b`) và Prompt `05` (`F3`) đã PASS.
- Làm đầu vào bắt buộc cho: **Prompt `07` (F5b)**.

---

## 9. Kiến trúc triển khai

- `HomeScreen` đọc hai luồng tách biệt:
  1. `state: SmartHomeState` (nguồn LAN & Cache cục bộ).
  2. `deviceBridge: CloudDeviceUiBridge?` (`gatewaySync` & `cloudSnapshot`).
- Dùng `DataSourceResolver.resolve(esp32Online = state.esp32Online, localLastSeen = state.lastSeen, snapshot = cloudSnapshot, nowMs = System.currentTimeMillis())` để xác định nguồn hiển thị, không tự viết logic so sánh rời rạc trong UI.
- Sử dụng `Icons.Default.*` (Material Icons) và `StatusPill` / `WhiteCard` từ `Components.kt` + `Theme.kt`; tuân thủ UI/UX chuẩn: **không dùng emoji**, chạm tối thiểu `44.dp`, text co giãn/wrap bình thường không bị cắt xén mất nghĩa.

---

## 10. Luồng dữ liệu

```text
state.esp32Online == true
  └─► DataSource.LAN -> HomeScreen hiển thị 100% số liệu trực tiếp từ SmartHomeState
                        + badge "Nguồn: LAN trực tiếp" + trạng thái Gateway sync ở Cloud banner

state.esp32Online == false
  ├─ [cloudSnapshot mới hơn localLastSeen] -> DataSource.CLOUD
  │     ├─ Pill "ESP32 mất kết nối" (giữ nguyên để pass AppSmokeTest)
  │     ├─ Badge "Nguồn: Cloud (chỉ xem)" + thời điểm + badge "Dữ liệu cũ" nếu isStale
  │     ├─ Card cảm biến đọc từ cloudSnapshot (temp, hum, motion)
  │     ├─ Card tóm tắt trạng thái từ xa & 3 sự kiện Cloud gần nhất (chỉ đọc)
  │     └─ Toàn bộ nút điều khiển LAN (Đèn, Cửa, Giàn phơi, Còi) VẪN KHÓA
  └─ [cloudSnapshot == null hoặc cũ hơn localLastSeen] -> DataSource.LOCAL_CACHE / NONE
        └─ Giữ nguyên hiển thị từ SmartHomeState + badge "Nguồn: Bộ nhớ máy" + "Dữ liệu cũ" nếu > 5 phút
```

---

## 11. Logic nghiệp vụ

1. **Nhãn & màu trạng thái nguồn dữ liệu:**
   - `LAN`: `"Nguồn: LAN trực tiếp"` (`Blue600` / `Blue50`).
   - `CLOUD`: `"Nguồn: Cloud (chỉ đọc)"` (`GreenOk` / `GreenSoft` nếu không cũ; `OrangeWarn` / `YellowSoft` nếu `isStale`).
   - `LOCAL_CACHE`: `"Nguồn: Bộ nhớ máy"` (`TextGray` / `OffGray` hoặc `YellowSoft` nếu cũ).
   - `NONE`: `"Chưa có dữ liệu nguồn"` (`TextGray` / `OffGray`).
2. **Nhãn trạng thái đồng bộ Gateway (`GatewaySyncPhase`):**
   - `REGISTERING`: `"Đang đăng ký thiết bị Gateway…"`
   - `SYNCING`: `"Đang đồng bộ dữ liệu lên Cloud…"`
   - `OK`: `"Đồng bộ Cloud gần nhất lúc ${timeOnly(lastSyncAt)}"`
   - `PAUSED_LAN_OFFLINE`: `"Đồng bộ tạm dừng · ESP32 mất kết nối LAN"`
   - `BLOCKED`: `"Thiết bị Cloud đã bị thu hồi — không thể đồng bộ"`
   - `ERROR`: `"Lỗi đồng bộ Cloud: ${errorMessage ?: ...}"` (kèm nút `"Thử lại"` gọi `deviceBridge.retrySync()` nếu cần).
   - `WAITING_HOME` / `IDLE`: không làm rối UI hoặc hiển thị ngắn `"Chờ chọn nhà Cloud để đồng bộ"`.
3. **Bảo toàn hiển thị mưa / giàn phơi khi ở chế độ `CLOUD` (`CD-2`):**
   - Vì `state/gateway/values` không có trường `raining`/`clothesRetracted`, ô `"Mưa"` hiển thị `"Xem trong sự kiện Cloud"` hoặc `"—"` (không bịa giá trị), và khối sự kiện Cloud gần nhất hiển thị các sự kiện `rain_started` / `rain_cleared` / `clothes_rain_retract` nếu có.

---

## 12. Xử lý lỗi và concurrency

- UI chỉ quan sát `StateFlow` qua `.collectAsState()`; không khởi chạy tác vụ mạng trực tiếp trong Composable.
- Khi `deviceBridge == null` (ví dụ trong `AppSmokeTest` gọi `HomeScreen(state, ...)` không có ViewModel), UI vẫn render mượt mà như bình thường (chỉ ẩn phần Gateway sync nếu bridge null, vẫn hiển thị nguồn `LAN` / `Bộ nhớ máy` từ `state`).

---

## 13. Bất biến cần bảo toàn

- **Giữ nguyên chuỗi `"ESP32 mất kết nối"` và `"ESP32 đã kết nối"`** trong `HomeScreen.kt:28` (`AppSmokeTest.kt:228`).
- **Giữ nguyên thứ tự các tham số cũ của `HomeScreen`**, chỉ thêm `deviceBridge` ở cuối có mặc định.
- **Không mở khóa bất kỳ nút điều khiển nào khi `esp32Online == false`.**
- **Không dùng emoji** trong nhãn UI mới (`Icons.Default.*` thay thế).
- **Không import Firebase SDK** trong UI.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `HomeScreen` hiển thị nguồn dữ liệu `LAN trực tiếp` / `Cloud (chỉ đọc)` / `Bộ nhớ máy`, thời điểm cập nhật và dấu hiệu `Dữ liệu cũ`.
2. Khi `esp32Online == false` và có `cloudSnapshot` mới hơn, số đo & trạng thái từ `cloudSnapshot` hiển thị ở dạng chỉ đọc, còn nút điều khiển (Đèn, Cửa, Còi, Giàn phơi) vẫn ở trạng thái disabled (`!enabled`).
3. Khi `esp32Online == true`, 100% giá trị hiển thị lấy từ `SmartHomeState` (LAN thắng tuyệt đối), không bị `cloudSnapshot` cũ ghi đè.
4. `CloudHomeBannerCard` hiển thị trạng thái đồng bộ Gateway từ `GatewaySyncUiState`.
5. `CloudHybridViewUiTest.kt` kiểm chứng đủ 4 tình huống (LAN trực tiếp thắng Cloud, Cloud fallback chỉ đọc khi mất LAN + nút điều khiển vẫn khóa, Local cache + stale badge, Gateway sync status banner).

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → toàn bộ JVM tests **PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` → `54` `AppSmokeTest` + `10` `CloudInitializationIntegrationTest` + `5` `CloudB3IntegrationTest` + `CloudHybridViewUiTest` **PASS** (0 failed, 0 skipped).

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chạm `HomeScreen.kt`, `CloudHomeSection.kt`, cộng `CloudSyncStatus.kt` (nếu tách) và `CloudHybridViewUiTest.kt`.
2. Kiểm tra chữ ký `HomeScreen(...)` tại mọi chỗ gọi trong `MainActivity.kt` và `AppSmokeTest.kt`: 0 call site bị lỗi tham số.
3. Grep xác nhận `"ESP32 mất kết nối"` vẫn tồn tại trong `HomeScreen.kt`.
4. Grep `com.google.firebase` trong `HomeScreen.kt`, `CloudHomeSection.kt`, `CloudSyncStatus.kt` → **0 matches**.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Hiển thị trạng thái đồng bộ Gateway, nguồn dữ liệu và Hybrid View chỉ đọc trên HomeScreen & CloudHomeSection (F5a).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy full Android Instrumented tests).
SCOPE: OK
RISKS: None | <nếu có>
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- `git revert <commit_hash_prompt_06>`.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. `git add` tường minh các file mục 6.
3. `git diff --cached --stat`.
4. Commit tiếng Việt:
   `git commit -m "Hiển thị trạng thái đồng bộ và nguồn dữ liệu Cloud"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- `AppSmokeTest.kt` hoặc `CloudB3IntegrationTest.kt` cần phải sửa assertion mới pass được UI mới (`CD-4`).
- Giao diện cần hiển thị trường `clothesRetracted`/`raining` realtime từ Cloud mà `state/gateway` Rules không có (`CD-2` chỉ cho phép hiển thị qua `events` hoặc giữ `"—"`).
- Cần sửa `MainActivity.kt` để truyền thêm tham số vào `HomeScreen`.
