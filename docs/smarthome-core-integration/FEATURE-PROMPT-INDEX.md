# MỤC LỤC PROMPT TRIỂN KHAI — SMART HOME AI (F1–F5)

Tài liệu này lập chỉ mục chính xác **7 prompt triển khai** cho 5 nhóm tính năng `F1`–`F5` (tối đa 2 prompt/tính năng, tổng ≤ 10 prompt). Thứ tự thực hiện phản ánh đúng đồ thị phụ thuộc thực tế từ source code, không ép theo thứ tự mã chữ cái `F1..F5`.

---

## 1. Bảng tổng hợp các Prompt triển khai

| Thứ tự | Mã & File Prompt | Nhóm tính năng | Số Prompt / Nhóm | Phụ thuộc tiên quyết | Trạng thái đầu vào | Thông điệp Git Commit (Nhánh A) |
|---|---|---|---|---|---|---|
| **1** | [`prompts/01-F1-mot-nha-mot-chu-so-huu.md`](prompts/01-F1-mot-nha-mot-chu-so-huu.md) | **F1** — Single Home / Single Owner | 1 / 1 | Checkpoint `0cea70f` | Clean working tree tại `0cea70f` | `Tích hợp quản lý một nhà cho chủ sở hữu` |
| **2** | [`prompts/02-F4-vong-doi-phien-dang-nhap.md`](prompts/02-F4-vong-doi-phien-dang-nhap.md) | **F4** — Auth & Session Lifecycle | 1 / 1 | Prompt `01` (PASS) | Commit & báo cáo PASS của `01` | `Hoàn thiện vòng đời phiên đăng nhập` |
| **3** | [`prompts/03-F2a-dang-ky-thiet-bi-va-lop-rtdb.md`](prompts/03-F2a-dang-ky-thiet-bi-va-lop-rtdb.md) | **F2 (Phần 1/2)** — Device Registration & RTDB Layer | 1 / 2 | Prompt `01` (PASS); chạy sau `02` để nối tuần tự | Commit & báo cáo PASS của `02` *(logic độc lập `02`)* | `Bổ sung đăng ký thiết bị và lớp dữ liệu RTDB cho Gateway` |
| **4** | [`prompts/04-F2b-dong-bo-gateway-len-cloud.md`](prompts/04-F2b-dong-bo-gateway-len-cloud.md) | **F2 (Phần 2/2)** — Gateway State/Config/Telemetry/Events Sync | 2 / 2 | Prompt `02` + Prompt `03` (PASS) | Commit & báo cáo PASS của `03` | `Đồng bộ trạng thái ESP32 lên Firebase` |
| **5** | [`prompts/05-F3-du-lieu-cloud-du-phong.md`](prompts/05-F3-du-lieu-cloud-du-phong.md) | **F3** — Cloud Data Fallback (Backend/Domain) | 1 / 1 | Prompt `03` + Prompt `04` (PASS) | Commit & báo cáo PASS của `04` | `Bổ sung dữ liệu Cloud dự phòng khi mất LAN` |
| **6** | [`prompts/06-F5a-giao-dien-trang-thai-va-nguon-du-lieu.md`](prompts/06-F5a-giao-dien-trang-thai-va-nguon-du-lieu.md) | **F5 (Phần 1/2)** — UI Trạng thái Sync & Hybrid View | 1 / 2 | Prompt `04` + Prompt `05` (PASS) | Commit & báo cáo PASS của `05` | `Hiển thị trạng thái đồng bộ và nguồn dữ liệu Cloud` |
| **7** | [`prompts/07-F5b-don-gian-hoa-cai-dat-va-thong-tin.md`](prompts/07-F5b-don-gian-hoa-cai-dat-va-thong-tin.md) | **F5 (Phần 2/2)** — UI Cài đặt & Thông tin hệ thống | 2 / 2 | Prompt `01`, `02`, `06` (PASS) | Commit & báo cáo PASS của `06` | `Đơn giản hóa cài đặt và cập nhật thông tin ứng dụng` |

---

## 2. Chi tiết Phạm vi File & Điểm giao nhau giữa các Prompt

### 2.1 Ma trận File Scope theo Prompt

| Prompt | File ĐƯỢC PHÉP SỬA (`WRITE`) | File ĐƯỢC TẠO MỚI (`NEW`) | File TEST MỚI (Nhánh A / Nhánh B) | File ĐẶC BIỆT BỊ CẤM SỬA |
|---|---|---|---|---|
| **01 (F1)** | `cloud/CloudHomeManager.kt`<br>`ui/components/CloudHomeSection.kt`<br>`data/SmartHomeViewModel.kt` *(chỉ dòng truyền `singleHomeMode = true`)* | *(Không)* | JVM: `CloudSingleHomeModeTest.kt`<br>Instrumented (tùy chọn): `CloudSingleOwnerUiTest.kt` | `CloudRepository.kt`, `RtdbCloudRepository.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `MainActivity.kt`, tất cả file test B3 |
| **02 (F4)** | `data/SmartHomeViewModel.kt`<br>`cloud/CloudHomeManager.kt` *(chỉ nếu thêm cờ `reauthRequired` có mặc định)*<br>`ui/components/CloudHomeSection.kt`<br>`ui/screens/HomeScreen.kt` *(chỉ truyền `onReauth`)* | *(Không)* | JVM: `CloudSessionLifecycleTest.kt` | `MainActivity.kt`, `AuthRepository.kt`, `LoginScreen.kt`, `SettingsScreen.kt`, `Esp32Client.kt`, `Storage.kt` |
| **03 (F2a)** | `cloud/RtdbPaths.kt` *(chỉ thêm hàm mới)*<br>`cloud/CloudSession.kt` *(thêm field mặc định cuối `CloudComponents`)*<br>`cloud/FirebaseProvider.kt` *(chỉ wiring trong `CloudFoundationFactory`)* | `cloud/CloudDeviceModels.kt`<br>`cloud/CloudMapper.kt`<br>`cloud/CloudDeviceRepository.kt`<br>`cloud/RtdbDeviceRepository.kt` | JVM: `CloudMapperTest.kt`, `RtdbDevicePathTest.kt`, `FakeCloudDeviceRepository.kt`<br>Rules (Nhánh B): `cloud-rules/test/gateway-payload.test.mjs` | `CloudRepository.kt`, `RtdbCloudRepository.kt`, `SmartHomeViewModel.kt`, `cloud-rules/database.rules.*`, `cloud-rules/test/rules.test.mjs`, `Esp32Client.kt`, `.ino` |
| **04 (F2b)** | `data/SmartHomeViewModel.kt` | `cloud/CloudGatewaySync.kt`<br>`cloud/CloudDeviceUiBridge.kt` | JVM: `CloudGatewaySyncTest.kt` | `CloudRepository.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`, `Esp32Client.kt`, `Storage.kt`, `MainActivity.kt`, UI screens |
| **05 (F3)** | `cloud/CloudDeviceModels.kt`<br>`cloud/CloudDeviceRepository.kt`<br>`cloud/RtdbDeviceRepository.kt`<br>`cloud/CloudDeviceUiBridge.kt` *(chỉ thêm getter mặc định `cloudSnapshot`)*<br>`data/SmartHomeViewModel.kt`<br>`FakeCloudDeviceRepository.kt` *(nếu thêm test double cho read)* | `cloud/CloudFallbackObserver.kt`<br>`cloud/DataSourceResolver.kt` | JVM: `DataSourceResolverTest.kt`, `CloudFallbackObserverTest.kt` | `Models.kt` (`SmartHomeState`), `Esp32Client.kt`, `Storage.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `MainActivity.kt` |
| **06 (F5a)** | `ui/screens/HomeScreen.kt`<br>`ui/components/CloudHomeSection.kt` | `ui/components/CloudSyncStatus.kt` *(tùy chọn, dùng chung component nhỏ)* | Instrumented: `CloudHybridViewUiTest.kt` | `SmartHomeViewModel.kt`, `CloudGatewaySync.kt`, `CloudFallbackObserver.kt`, `SettingsScreen.kt`, `AppSmokeTest.kt`, `CloudB3IntegrationTest.kt` |
| **07 (F5b)** | `ui/screens/SettingsScreen.kt`<br>`ui/screens/SystemScreen.kt`<br>`ui/components/CloudHomeSection.kt` *(chỉ khi cần tinh chỉnh nhãn chế độ một nhà)* | *(Không)* | Instrumented: `CloudSettingsSingleOwnerUiTest.kt` | `app/build.gradle.kts`, `HomeScreen.kt`, `SmartHomeViewModel.kt`, `FaceStore`/`FaceEngine.kt`, `MainActivity.kt`, `AppSmokeTest.kt` |

### 2.2 File giao nhau giữa nhiều Prompt & Quy tắc tránh xung đột

Các file dưới đây nằm trong phạm vi sửa của nhiều hơn 1 prompt — **bắt buộc thực hiện tuần tự nghiêm ngặt (chờ Nhánh B của prompt trước PASS mới chạy prompt sau)**, tuyệt đối không chạy song song hai prompt cùng chạm một file:

| File giao nhau | Các Prompt chạm tới | Quy tắc ranh giới trách nhiệm |
|---|---|---|
| `data/SmartHomeViewModel.kt` | `01`, `02`, `04`, `05` | `01`: chỉ bật `singleHomeMode = true`. `02`: chỉ đồng bộ phiên (`reauthRequired`, `userEmail`). `04`: chỉ gắn `CloudGatewaySync` + đăng ký `CloudDeviceUiBridge`. `05`: chỉ nối `CloudFallbackObserver` theo `!esp32Online`. |
| `ui/components/CloudHomeSection.kt` | `01`, `02`, `06`, `07` | `01`: ẩn multi-home/member bằng `singleHomeMode`. `02`: thêm `onReauth` cho nút `"Đăng nhập lại"`. `06`: thêm trạng thái sync Gateway trên banner. `07`: chỉnh nhãn nhỏ ở Settings nếu cần. Mỗi bước giữ nguyên tham số mặc định của các bước trước. |
| `cloud/CloudHomeManager.kt` | `01`, `02` | `01`: thêm `singleHomeMode`, nhà chính, chặn tạo nhà thứ 2. `02`: chỉ đọc/cập nhật nhẹ nếu cần đặt cờ `reauthRequired` trong `CloudUiState`. |
| `cloud/CloudDeviceRepository.kt` & `RtdbDeviceRepository.kt` | `03`, `05` | `03`: các hàm ghi & resolve device + đọc `config`/`cursors`. `05`: bổ sung các hàm `observe*`/`findActiveDevice` chỉ đọc phục vụ Fallback. |
| `cloud/CloudDeviceUiBridge.kt` & `CloudDeviceModels.kt` | `04`, `05` | `04`: tạo models + bridge cho `gatewaySync`. `05`: bổ sung `CloudDeviceSnapshot`, `DataSource` và property mặc định `cloudSnapshot`. |
| `ui/screens/HomeScreen.kt` | `02`, `06` | `02`: chỉ truyền `onReauth = { onNavigate("login") }`. `06`: thêm `deviceBridge` (có mặc định) ở cuối chữ ký và render Hybrid View + nguồn dữ liệu. |

---

## 3. Quy tắc bàn giao & Điều kiện chuyển tiếp giữa các Prompt

### 3.1 Điều kiện bắt đầu một Prompt bất kỳ (`P[k]`)
1. `P[k-1]` (nếu `k > 1`) đã có đủ: commit riêng của **Nhánh A**, báo cáo kiểm thử độc lập của **Nhánh B** kết luận **`PASS`**, và (nếu Nhánh B có phát hiện lỗi) đã chạy xong prompt vá lỗi được phê duyệt riêng và đạt `PASS` lại.
2. `git status --porcelain` sạch hoàn toàn trước khi bắt đầu Nhánh A của `P[k]`.
3. HEAD thực tế khớp đúng commit kết thúc của `P[k-1]` (hoặc `0cea70f3cd83f69568ab35a00af013930544450f` đối với `01`).

### 3.2 Quy tắc chuyển từ Nhánh A sang Nhánh B trong mỗi Prompt
1. Nhánh A **chỉ** viết code và static verification (grep, đối chiếu signature), **tuyệt đối không** chạy unit test, Android test, Gradle build, hay Firebase Emulator.
2. Nhánh A xuất Handoff Report theo đúng khung trong `01..07`, tạo **01 Git commit tiếng Việt**, xác nhận `git status --short` sạch, rồi **DỪNG** và yêu cầu người dùng chuyển sang Model Kiểm thử độc lập (Nhánh B).
3. Nhánh B kiểm tra diff/commit của Nhánh A, chạy kiểm thử thực tế (JVM, Emulator Rules, `assembleDebug`, Android Instrumented tùy prompt), không tự sửa implementation, xuất báo cáo phân loại lỗi `CRITICAL/HIGH/MEDIUM/LOW`. Nếu `PASS`, người dùng chuyển sang Prompt tiếp theo; nếu `FAIL`, dừng lại yêu cầu prompt sửa lỗi riêng.

### 3.3 Điều kiện kết thúc toàn bộ 7 Prompt
Khi Prompt `07` đạt `PASS` từ Nhánh B:
- Không tự sửa thêm code; chuyển sang thực hiện kế hoạch trong [`AUDIT-PLAN-POST-INTEGRATION.md`](AUDIT-PLAN-POST-INTEGRATION.md).
- Chỉ sau khi Audit tổng thể hoàn tất (và xử lý hết các cụm lỗi nếu có), mới bước sang giai đoạn `C1` (kiểm thử phần cứng thật đóng `B1-A2`) và `C3`/`C4` (`B1-D` Production Audit có phê duyệt văn bản, Release signing).
