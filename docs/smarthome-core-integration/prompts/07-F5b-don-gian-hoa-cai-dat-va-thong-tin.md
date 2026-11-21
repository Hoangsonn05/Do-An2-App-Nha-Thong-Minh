# PROMPT 07 — F5b: ĐƠN GIẢN HÓA MÀN HÌNH CÀI ĐẶT VÀ CẬP NHẬT THÔNG TIN HỆ THỐNG

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Compose UI Engineer hoàn thiện `SettingsScreen.kt` (làm rõ mục `"Thành viên"` là nhận diện khuôn mặt cục bộ mà không xóa FaceStore, cập nhật dialog `"Về ứng dụng"` đã lỗi thời) và bổ sung dòng chẩn đoán Cloud & Gateway sync trên `SystemScreen.kt`.
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Compose UI Test Engineer đọc diff + handoff Nhánh A, viết/chạy `CloudSettingsSingleOwnerUiTest`, chạy toàn bộ bộ kiểm thử JVM + Lint + Debug Build + Emulator Rules + Android Instrumented Tests để xác nhận kết thúc toàn bộ đợt tích hợp F1–F5.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (commit kết thúc của Prompt `06`) và `git status --porcelain` sạch.
2. Đọc lại `SettingsScreen.kt` (dòng 60–125), `SystemScreen.kt` (dòng 25–101), `CloudHomeSection.kt`, `AppSmokeTest.kt` (dòng 345–373), `CloudB3IntegrationTest.kt` (dòng 336–421).
3. Đối chiếu yêu cầu mục 4–13 với source thực tế.
4. Hoàn thiện thiết kế UI mục 9–12.
5. Triển khai phạm vi mục 5–6 (kèm viết `CloudSettingsSingleOwnerUiTest.kt`, **không chạy**).
6. Không sửa `app/build.gradle.kts`, `HomeScreen.kt`, `SmartHomeViewModel.kt`, `FaceStore`/`FaceEngine.kt`.
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
3. Kiểm tra code thực tế đối chiếu mục 13–14 (đặc biệt mục `"Thành viên"` FaceStore vẫn nguyên chức năng thêm/xóa khuôn mặt, dialog `"Về ứng dụng"` phản ánh đúng kiến trúc hiện tại).
4. Chạy full bộ kiểm thử local: `test:build`, `test:rules`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:connectedDebugAndroidTest` (trên `demo-smarthomeai` + AVD).
5. **KHÔNG** sửa implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện và `file:dòng`.
9. Xuất báo cáo kiểm thử độc lập (là căn cứ chuyển sang bước Audit tổng thể).
10. Nếu có lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01`, `02`, `06` đã hoàn tất và PASS.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `06`.
- **Working tree:** Sạch.

---

## 3. Trạng thái hiện tại đã xác minh

1. `SettingsScreen.kt`:
   - Dòng 62–64: `if (cloudBridge != null) { item { CloudHomeManagementFromBridge(cloudBridge) } }` (đã ở `singleHomeMode = true` từ Prompt `01`).
   - Dòng 66–76: `SectionTitle("Thành viên", action = "Thêm") { onNavigate("register") }`, hiển thị `state.registeredUsers` từ `FaceStore` cục bộ kèm nút xóa khuôn mặt (`deleteName`). `AppSmokeTest.kt:347-370` gọi `SettingsScreen(...)` theo vị trí tham số và kiểm thử mục khuôn mặt này!
   - Dòng 82 & 115–116: Dialog `"Về ứng dụng"` vẫn ghi `"Smart Home · 3.6"` và nội dung lỗi thời: `"Điều khiển và đồng bộ dùng chung LAN; chưa có thông báo cloud từ xa."`.
2. `SystemScreen.kt`:
   - Dòng 28–29: `fun SystemScreen(state: SmartHomeState, firebaseConfigured: Boolean, onRefresh: () -> Unit, onBack: () -> Unit, onNavigate: (String) -> Unit)`
   - Dòng 71–73: mới chỉ ghi `DetailLine("Tài khoản", if (firebaseConfigured) "Firebase Authentication" else "Chế độ tài khoản demo")` và dòng mô tả LAN, chưa hiển thị trạng thái Cloud Home hay trạng thái đồng bộ Gateway.

---

## 4. Mục tiêu cụ thể

Hoàn thiện trải nghiệm người dùng trên `SettingsScreen` và `SystemScreen` theo mô hình **Một nhà — Một ESP32 — Một chủ sở hữu**:
1. Giữ nguyên hoàn toàn tính năng quản lý khuôn mặt mở cửa cục bộ (`FaceStore`) tại mục `"Thành viên"` trên `SettingsScreen`, đồng thời bổ sung mô tả phụ minh bạch để người dùng (và lập trình viên sau này) không nhầm với quản lý thành viên Cloud.
2. Cập nhật nội dung hộp thoại `"Về ứng dụng"` tại `SettingsScreen.kt:115-116` phản ánh đúng kiến trúc hiện tại: điều khiển phần cứng qua LAN (`LAN-first`), đồng bộ trạng thái/cảm biến/sự kiện lên Firebase RTDB cho 1 nhà – 1 chủ nhà, xem dữ liệu Cloud khi ra ngoài LAN (chỉ đọc, chưa có điều khiển từ xa qua Cloud hoặc thông báo đẩy từ xa), khuôn mặt và lịch sử cục bộ lưu trên máy.
3. Bổ sung thông tin chẩn đoán Cloud Home & Gateway Sync vào `SystemScreen.kt` (thông qua `cloudBridge`/`deviceBridge` có tham số mặc định ở cuối chữ ký).
4. Tinh chỉnh nhãn hiển thị của `CloudHomeManagementCard` ở chế độ một nhà trong `CloudHomeSection.kt` nếu cần đồng bộ ngôn ngữ với `SettingsScreen` (ví dụ tiêu đề `"Nhà Cloud"` thay cho `"Nhà Cloud & thành viên"` khi `singleHomeMode == true`).

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Trong `SettingsScreen.kt`:
  - Giữ nguyên `SectionTitle("Thành viên", action = "Thêm")` và luồng thêm/xóa `FaceStore`; thêm dòng ghi chú ngắn ở đầu `WhiteCard`: `"Khuôn mặt mở cửa cục bộ trên điện thoại (không đồng bộ lên Cloud)."`
  - Cập nhật nội dung `AlertDialog` `"Về ứng dụng"` (`SettingsScreen.kt:115-116`) phản ánh chính xác kiến trúc RTDB + LAN-first hiện tại (giữ tiêu đề `"Smart Home · 3.6"` nếu không thay đổi `versionName` trong `app/build.gradle.kts`).
- Trong `SystemScreen.kt`:
  - Thêm 2 tham số tùy chọn ở cuối: `cloudBridge: CloudUiBridge? = rememberActiveCloudBridge(), deviceBridge: CloudDeviceUiBridge? = rememberActiveCloudDeviceBridge()`.
  - Hiển thị thêm các dòng chẩn đoán trong card `"Kết nối & tự động hóa"` (hoặc card riêng):
    - `Nhà Cloud`: tên nhà chính hoặc trạng thái (`Chưa tạo nhà` / `Chưa đăng nhập` / `Chế độ demo`).
    - `Đồng bộ Gateway`: trạng thái `GatewaySyncPhase` (`Đang đồng bộ`, `OK · HH:mm`, `Tạm dừng khi mất LAN`, `Bị thu hồi`, `Lỗi`).
    - Cập nhật dòng mô tả kiến trúc bên dưới: `"Điều khiển: Ứng dụng → HTTP LAN → ESP32. Dự phòng xem từ xa: Firebase RTDB (1 nhà). Khuôn mặt xử lý trên điện thoại."`
- Trong `CloudHomeSection.kt` *(nếu cần)*:
  - Khi `singleHomeMode == true` trong `CloudHomeManagementCard`: tiêu đề `SectionTitle` hiển thị `"Nhà Cloud"` (thay vì `"Nhà Cloud & thành viên"`), vẫn giữ `"Nhà Cloud & thành viên"` khi `singleHomeMode == false` để `CloudB3IntegrationTest` không đổi bất kỳ kỳ vọng nào.
- Tạo mới `CloudSettingsSingleOwnerUiTest.kt` trong `androidTest`: kiểm tra `SettingsScreen` hiển thị mục `"Thành viên"` (FaceStore) với mô tả cục bộ, ẩn thành viên Cloud/switcher ở `singleHomeMode`, nội dung `"Về ứng dụng"` đã cập nhật, và `SystemScreen` hiển thị dòng Cloud/Gateway.

### Ngoài phạm vi
- Không sửa `versionCode` / `versionName` trong `app/build.gradle.kts`.
- Không sửa `FaceStore`, `FaceEngine.kt`, `FaceRegisterScreen`.
- Không sửa `HomeScreen.kt`, `SmartHomeViewModel.kt`, `MainActivity.kt`.
- Không sửa `AppSmokeTest.kt` hoặc `CloudB3IntegrationTest.kt`.

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`
2. `app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt`
3. `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt` *(chỉ điều chỉnh tiêu đề/nhãn khi `singleHomeMode == true`)*

### Thêm mới (`NEW`)
1. `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudSettingsSingleOwnerUiTest.kt`

---

## 7. File bị cấm sửa

- `app/build.gradle.kts`, `AndroidManifest.xml`, `app/google-services.json`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `app/src/main/java/com/example/smarthomeai/data/**`
- `app/src/main/java/com/example/smarthomeai/face/**`
- `app/src/main/java/com/example/smarthomeai/cloud/**`
- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt`
- `app/src/androidTest/java/com/example/smarthomeai/AppSmokeTest.kt`, `CloudB3IntegrationTest.kt`, `CloudInitializationIntegrationTest.kt`, `CloudHybridViewUiTest.kt`
- `cloud-rules/**`, `esp32/**`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `01` (`F1`), `02` (`F4`) và `06` (`F5a`) đã PASS.
- Kết thúc Prompt `07` hoàn thành toàn bộ bộ prompt triển khai `F1`–`F5` và mở cổng sang **Audit Tổng Thể**.

---

## 9. Kiến trúc triển khai

- Bảo toàn tuyệt đối signature gọi từ `MainActivity.kt:87, 91-93` và `AppSmokeTest.kt:347`:
  - `SettingsScreen` giữ nguyên toàn bộ tham số hiện có (`cloudBridge: CloudUiBridge? = rememberActiveCloudBridge()` ở cuối).
  - `SystemScreen` chỉ nối thêm `cloudBridge` và `deviceBridge` có giá trị mặc định vào cuối danh sách tham số.
- Không truyền Firebase class vào UI; chỉ đọc `CloudUiBridge` và `CloudDeviceUiBridge`.

---

## 10. Luồng dữ liệu

- `SettingsScreen` → đọc `state.registeredUsers` (FaceStore) + thu thập `cloudBridge?.state`.
- `SystemScreen` → đọc `state`, `firebaseConfigured` + thu thập `cloudBridge?.state` và `deviceBridge?.gatewaySync`.

---

## 11. Logic nghiệp vụ

1. **Phân biệt Thành viên khuôn mặt vs Cloud:**
   - Giữ tiêu đề section `"Thành viên"` + nút `"Thêm"` + danh sách khuôn mặt `state.registeredUsers` + dialog xác nhận xóa khuôn mặt (`"Xóa khuôn mặt?"`) vì đây là đường nhận diện mở cửa vật lý tại nhà.
   - Thêm mô tả phụ bên trong card: `"Khuôn mặt mở cửa cục bộ trên điện thoại (không lưu trên Cloud)."`
2. **Nội dung hộp thoại `"Về ứng dụng"` cập nhật chính xác:**
   - Nêu rõ 4 điểm sự thật hệ thống:
     1. Nhận diện khuôn mặt xử lý trực tiếp trên điện thoại (`on-device`), không tải ảnh/vector lên Cloud.
     2. Dữ liệu thiết bị & nhật ký cục bộ trên máy dùng chung theo thiết bị hiện tại (`SharedPreferences`/`SQLite`).
     3. Điều khiển thiết bị (Đèn, Cửa, Còi, Giàn phơi, Chế độ bảo vệ) luôn ưu tiên qua mạng Wi-Fi LAN trực tiếp tới ESP32.
     4. Khi đăng nhập Firebase và tạo nhà Cloud (1 nhà – 1 chủ sở hữu), app đồng bộ trạng thái/cảm biến/sự kiện lên Firebase RTDB để xem lại khi ra ngoài LAN (chỉ đọc; chưa hỗ trợ điều khiển từ xa hay thông báo đẩy Cloud).
3. **Thông tin trên `SystemScreen`:**
   - Hiển thị thêm 1–2 `DetailLine` (hoặc `DiagnosticRow`) cho `Nhà Cloud` và `Đồng bộ Gateway` khi có bridge, không phá vỡ các row hiện có (`Cảm biến DHT22`, `Quyền camera`, `Tệp mô hình AI`, `Thành viên`, `Thông báo`).

---

## 12. Xử lý lỗi và concurrency

- UI chỉ đọc `StateFlow` qua `collectAsState()`; không phát sinh side-effect hoặc coroutine thủ công.
- Khi `cloudBridge == null` hoặc `deviceBridge == null` (ví dụ trong smoke test cô lập), các dòng chẩn đoán Cloud hiển thị an toàn `"Không có cầu nối Cloud"` hoặc ẩn gọn gàng, không crash NPE.

---

## 13. Bất biến cần bảo toàn

- **Không xóa hoặc ẩn mục `"Thành viên"` FaceStore ở `SettingsScreen.kt:66`.**
- **Không phá `AppSmokeTest` và `CloudB3IntegrationTest` (`CD-4`).**
- **Không dùng emoji**; dùng Material Icons và `Text` wrap tự nhiên.
- **Không sửa `MainActivity.kt` và `app/build.gradle.kts`.**

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `SettingsScreen` giữ nguyên toàn bộ chức năng thêm/xóa khuôn mặt cục bộ tại mục `"Thành viên"` kèm ghi chú phân biệt cục bộ.
2. Dialog `"Về ứng dụng"` phản ánh đúng cả LAN-first lẫn Cloud RTDB Sync/Fallback hiện tại, không còn thông tin lỗi thời `"chưa có đồng bộ cloud"`.
3. `SettingsScreen` ở runtime `singleHomeMode = true` không hiển thị mục quản lý nhiều nhà hay thành viên Cloud.
4. `SystemScreen` hiển thị thêm trạng thái `Nhà Cloud` và `Đồng bộ Gateway`.
5. `CloudSettingsSingleOwnerUiTest.kt` phủ đầy đủ các tiêu chí trên.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B — Cũng là kiểm tra tổng hợp kết thúc F1–F5)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `cd cloud-rules; npm run test:build; npm run test:rules` (và `gateway-payload.test.mjs` từ Prompt 03) → 100% **PASS**.
2. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → 100% **PASS** (151 cũ + toàn bộ unit test mới từ Prompt 01–05).
3. `.\gradlew.bat :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → 0 lỗi Lint nghiêm trọng, **BUILD SUCCESSFUL**.
4. `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` → 100% **PASS** (69 cũ + toàn bộ UI test mới của Prompt 01, 06, 07).

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chạm đúng 3 file sửa + 1 file test mới ở mục 6.
2. Grep `com.google.firebase` trong `SettingsScreen.kt`, `SystemScreen.kt`, `CloudHomeSection.kt` → **0 matches**.
3. Kiểm tra không có thay đổi nào ở `AppSmokeTest.kt`, `CloudB3IntegrationTest.kt`, `MainActivity.kt`, `app/build.gradle.kts`.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Làm rõ nhãn FaceStore cục bộ, cập nhật thông tin ứng dụng và bổ sung chẩn đoán Cloud/Gateway trên SystemScreen (F5b).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy full suite Local).
SCOPE: OK
RISKS: None | <nếu có>
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- `git revert <commit_hash_prompt_07>`.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. `git add app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt app/src/androidTest/java/com/example/smarthomeai/cloud/CloudSettingsSingleOwnerUiTest.kt`
3. `git diff --cached --stat`.
4. Commit tiếng Việt:
   `git commit -m "Đơn giản hóa cài đặt và cập nhật thông tin ứng dụng"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Việc chỉnh nhãn làm `AppSmokeTest.kt:347-370` hoặc `CloudB3IntegrationTest.kt:336-421` bị lỗi hoặc buộc sửa test cũ (`CD-4`).
- Phát hiện yêu cầu đổi `versionName` trong `app/build.gradle.kts`.
- Có lỗi phát sinh khi Nhánh B chạy toàn bộ bộ test trước khi chuyển sang Audit tổng thể.
