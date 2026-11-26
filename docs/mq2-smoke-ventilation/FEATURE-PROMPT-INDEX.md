# MỤC LỤC BỘ PROMPT TRIỂN KHAI — CẢM BIẾN KHÓI MQ-2, CẢNH BÁO PPM & CỬA SỔ / QUẠT TỰ ĐỘNG

Tài liệu này điều phối **4 prompt triển khai tuần tự** (mỗi prompt tuân thủ nghiêm ngặt chuẩn **2 Nhánh A / B — 19 mục**) để xây dựng hoàn chỉnh tính năng Cảm biến khói MQ-2 (`ppm`, ngưỡng mặc định `300 ppm`), thông báo đẩy về máy, tự động mở cửa sổ & bật quạt, và đồng bộ toàn diện với Firebase Realtime Database trước khi ghép nối phần cứng ESP32.

---

## 1. Bảng tổng hợp thứ tự thực thi các Prompt

| Thứ tự | Mã & Đường dẫn File Prompt | Phân hệ | Phụ thuộc tiên quyết | Trạng thái đầu vào | Thông điệp Git Commit (Nhánh A) |
|---|---|---|---|---|---|
| **1** | [`prompts/01-MQ2-hop-dong-phan-cung-va-tang-du-lieu-lan.md`](prompts/01-MQ2-hop-dong-phan-cung-va-tang-du-lieu-lan.md) | **MQ2-D1** — Hợp đồng phần cứng, Models, `Esp32Client`, `SmokeSafetyGate` & `Storage` (SQLite v2) | Checkpoint gốc `452abb2` | Clean code tree trên `452abb2` | `Bổ sung hợp đồng dữ liệu cảm biến khói MQ-2, cửa sổ và quạt` |
| **2** | [`prompts/02-MQ2-firebase-rtdb-rules-va-dong-bo-cloud.md`](prompts/02-MQ2-firebase-rtdb-rules-va-dong-bo-cloud.md) | **MQ2-D2** — Firebase RTDB Security Rules, `CloudMapper`, `RtdbDeviceRepository` & `CloudGatewaySync` | Prompt `01` (Nhánh B `PASS`) | Commit kết thúc của Prompt `01` | `Mở rộng Firebase RTDB Rules và đồng bộ dữ liệu khói MQ-2 lên Cloud` |
| **3** | [`prompts/03-MQ2-viewmodel-canh-bao-va-tu-dong-mo-cua-so-quat.md`](prompts/03-MQ2-viewmodel-canh-bao-va-tu-dong-mo-cua-so-quat.md) | **MQ2-D3** — `SmokeVentilationCoordinator` & `SmartHomeViewModel` (Cảnh báo 300 ppm + Tự mở Cửa sổ & Quạt) | Prompt `01` + Prompt `02` (Nhánh B `PASS`) | Commit kết thúc của Prompt `02` | `Tích hợp cảnh báo khói 300 ppm và tự động mở cửa sổ, quạt trong ViewModel` |
| **4** | [`prompts/04-MQ2-giao-dien-hien-thi-ppm-canh-bao-va-dieu-khien.md`](prompts/04-MQ2-giao-dien-hien-thi-ppm-canh-bao-va-dieu-khien.md) | **MQ2-D4** — Giao diện `SmokeVentilationCard`, hiển thị `ppm`, Biểu đồ, Cloud Read-only & `MainActivity` | Prompt `02` + Prompt `03` (Nhánh B `PASS`) | Commit kết thúc của Prompt `03` | `Hoàn thiện giao diện cảm biến khói MQ-2, cảnh báo ppm và điều khiển cửa sổ, quạt` |

---

## 2. Ma trận Phạm vi File (`WRITE` / `NEW` / `TEST` / `LOCKED`)

| Prompt | File ĐƯỢC PHÉP SỬA (`WRITE`) | File ĐƯỢC TẠO MỚI (`NEW`) | File TEST MỚI (Nhánh A / Nhánh B) | File ĐẶC BIỆT BỊ CẤM SỬA (`LOCKED`) |
|---|---|---|---|---|
| **01 (MQ2-D1)** | `data/Models.kt`<br>`data/HardwareStatus.kt`<br>`data/Esp32Client.kt`<br>`data/Security.kt`<br>`data/Insights.kt`<br>`data/Storage.kt` | *(Không)* | JVM: `app/src/test/java/com/example/smarthomeai/data/SmokeSafetyDataTest.kt` | `DeviceType` enum entries (`LIGHT`, `DOOR`, `ALARM`), `SmartHomeViewModel.kt`, `cloud/**`, `ui/**`, `MainActivity.kt`, `cloud-rules/**`, `esp32/**`, tất cả file test hiện hữu |
| **02 (MQ2-D2)** | `cloud-rules/database.rules.src.json`<br>`cloud-rules/database.rules.json`<br>`cloud/CloudDeviceModels.kt`<br>`cloud/CloudMapper.kt`<br>`cloud/RtdbDeviceRepository.kt`<br>`cloud/CloudGatewaySync.kt`<br>`test/.../cloud/FakeCloudDeviceRepository.kt` | *(Không)* | JVM: `app/src/test/java/com/example/smarthomeai/cloud/CloudSmokeSyncTest.kt`<br>Rules: `cloud-rules/test/smoke-mq2-rules.test.mjs` | `CloudRepository.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`, `SmartHomeViewModel.kt`, `data/**`, `ui/**`, `cloud-rules/test/rules.test.mjs`, `cloud-rules/test/gateway-payload.test.mjs`, `esp32/**` |
| **03 (MQ2-D3)** | `data/SmartHomeViewModel.kt` | `data/SmokeVentilationCoordinator.kt` | JVM: `app/src/test/java/com/example/smarthomeai/data/SmokeVentilationCoordinatorTest.kt` | `data/Models.kt`, `data/Esp32Client.kt`, `data/Storage.kt`, `data/Notifier.kt`, `cloud/**`, `ui/**`, `MainActivity.kt`, `cloud-rules/**`, `esp32/**` |
| **04 (MQ2-D4)** | `ui/components/CloudSyncStatus.kt`<br>`ui/screens/HomeScreen.kt`<br>`ui/screens/EnvironmentScreen.kt`<br>`ui/screens/SensorChart.kt`<br>`ui/screens/AlertsScreen.kt`<br>`ui/screens/AutomationScreen.kt`<br>`ui/screens/SettingsScreen.kt`<br>`ui/screens/SystemScreen.kt`<br>`MainActivity.kt` | `ui/components/SmokeVentilationCard.kt` | Instrumented: `app/src/androidTest/java/com/example/smarthomeai/SmokeVentilationUiTest.kt` | `EnvironmentConfigDialog` (giữ nguyên 1 `SmartSwitch`), `data/**`, `cloud/**`, `cloud-rules/**`, `esp32/**`, `AppSmokeTest.kt` và toàn bộ file test cũ |

---

## 3. Phân định ranh giới (Zero File Overlap trên Source chính)

Bộ 4 prompt được thiết kế **phân tách sạch 100% theo tầng kiến trúc** để không có hai prompt nào sửa đè cùng một file sản xuất:
- **Prompt 01:** Chỉ chạm tầng dữ liệu cục bộ & giao thức LAN (`data/Models.kt`, `HardwareStatus.kt`, `Esp32Client.kt`, `Security.kt`, `Insights.kt`, `Storage.kt`).
- **Prompt 02:** Chỉ chạm tầng Cloud & Rules (`cloud-rules/database.rules.*`, `cloud/CloudDeviceModels.kt`, `CloudMapper.kt`, `RtdbDeviceRepository.kt`, `CloudGatewaySync.kt`, `FakeCloudDeviceRepository.kt`).
- **Prompt 03:** Chỉ chạm bộ điều phối nghiệp vụ (`data/SmokeVentilationCoordinator.kt` mới và `data/SmartHomeViewModel.kt`).
- **Prompt 04:** Chỉ chạm tầng UI Compose & nối callback ở `MainActivity.kt` (`ui/**` + `MainActivity.kt`).

> **Nguyên tắc tuần tự bắt buộc:** Dù không chồng lấn file, Prompt `02` phụ thuộc kiểu dữ liệu của Prompt `01`; Prompt `03` phụ thuộc cả `01` và `02`; Prompt `04` phụ thuộc `02` và `03`. **Bắt buộc chờ Nhánh B của prompt trước xác nhận `PASS` rồi mới bắt đầu Nhánh A của prompt kế tiếp.**

---

## 4. Quy tắc vận hành 2 Nhánh trong từng Prompt

1. **Nhánh A — Model Triển Khai (15 bước):**
   - Kiểm tra `HEAD` và trạng thái cây thư mục sạch.
   - Đọc source thực tế, triển khai code + viết unit/UI test mới (**không chạy**).
   - Chỉ chạy **Static Verification** (Mục 15 của prompt). **TUYỆT ĐỐI KHÔNG** chạy Gradle test, Gradle build, Android emulator hay Firebase Emulator.
   - Xuất **Handoff Report** (Mục 16), tạo **01 Git commit tiếng Việt** (Mục 18, **KHÔNG `git push`**), rồi **DỪNG** báo người dùng chuyển sang Nhánh B.
2. **Nhánh B — Model Kiểm Thử Độc Lập (10 bước):**
   - Đọc Handoff Report + `git show` / `git diff` của Nhánh A.
   - Bổ sung test độc lập nếu thiếu độ phủ, trực tiếp chạy toàn bộ lệnh test/build/emulator tại Mục 14.
   - **KHÔNG** tự sửa code sản xuất. Phân loại lỗi `CRITICAL / HIGH / MEDIUM / LOW` kèm bằng chứng. Nếu `PASS`, xác nhận chuyển sang prompt tiếp theo; nếu có lỗi, dừng và yêu cầu prompt vá lỗi riêng.
3. **Sau khi hoàn tất Prompt 04:** Chạy kế hoạch kiểm toán tổng thể tại [`AUDIT-PLAN-POST-INTEGRATION.md`](AUDIT-PLAN-POST-INTEGRATION.md).
