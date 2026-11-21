# BÁO CÁO HOÀN TẤT LẬP KẾ HOẠCH TÍCH HỢP — SMART HOME AI CORE INTEGRATION

- **Nhiệm vụ:** READ-ONLY MASTER PLANNING (`F1`–`F5` + Audit Plan)
- **Mô hình mục tiêu:** **MỘT NHÀ — MỘT ESP32 — MỘT TÀI KHOẢN CHỦ NHÀ**
- **Checkpoint đầu vào xác minh:** `0cea70f3cd83f69568ab35a00af013930544450f` (`master` — `"checkpoint task audit b4"`)

---

## 1. Xác nhận đã đọc & đối chiếu Source Code thực tế

Trong phiên lập kế hoạch này, đã đọc trực tiếp từ workspace `d:\MMO\DoAn2\app-nha-thong-minh`:
- **Toàn bộ lớp Cloud (`app/src/main/java/com/example/smarthomeai/cloud/`):** `CloudModels.kt`, `CloudRepository.kt`, `CloudSession.kt`, `FirebaseProvider.kt`, `RtdbPaths.kt`, `RtdbPayloadBuilder.kt`, `CloudErrorMapper.kt`, `RtdbCloudRepository.kt`, `RtdbHomeMerger.kt`, `CloudHomeManager.kt`.
- **Lớp Data & Entry Android (`app/src/main/java/com/example/smarthomeai/`):** `MainActivity.kt`, `data/SmartHomeViewModel.kt`, `data/AuthRepository.kt`, `data/Esp32Client.kt`, `data/Models.kt`, `data/Security.kt`, `data/Storage.kt`, `data/HardwareStatus.kt`.
- **Lớp UI Android:** `ui/components/CloudHomeSection.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/SystemScreen.kt` (kèm grep call sites tại `AlertsScreen.kt`, `Insights.kt`).
- **Cấu hình Build & Manifest:** `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `README.md`. *(Không đọc/xuất nội dung `app/google-services.json`.)*
- **RTDB Rules & Tooling:** `cloud-rules/database.rules.src.json`, `cloud-rules/build-rules.mjs`, `cloud-rules/package.json`, `cloud-rules/README.md`.
- **Firmware ESP32:** `esp32/SmartHomeESP32/SmartHomeESP32.ino` (đoạn dòng 280–880 + grep `stream_id`, endpoints `/sensor`, `/events`, `/clock`, `DEVICE_KEY`, `Preferences`).
- **Tài liệu lịch sử & B0–C0:** `docs/VALIDATION.md`, `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (mục `§6.3–6.7`), `docs/p1-1-b1/b1-real-world-verification-report.md`, `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md` (dòng 1–120), `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md` (dòng 1–250), `docs/p1-1-c0/c0-core-scope-rebaseline-report.md`, `docs/p1-1-c0/c0-execution-handoff.md`.
- **Kiểm tra cấu trúc Test:** Grep toàn bộ `@Test` và `CloudRepository`/`CloudUiBridge`/`CloudHomeManager` trong `app/src/test/**/cloud/*.kt` và `app/src/androidTest/**/cloud/*.kt`, `AppSmokeTest.kt`, cộng đọc trực tiếp các đoạn implement/call-site tại `CloudSessionAndMultiHomeTest.kt:150-180` và `CloudB3IntegrationTest.kt:80-425`.
- **Graphify index:** Kiểm tra `Test-Path graphify-out` trả `False` (repo chưa tạo đồ thị `graphify-out/`).

---

## 2. Checkpoint thực tế & Bất biến tuân thủ

| Hạng mục | Giá trị kiểm tra trước lập kế hoạch |
|---|---|
| **HEAD** | `0cea70f3cd83f69568ab35a00af013930544450f` (`checkpoint task audit b4`) |
| **Branch** | `master` |
| **Working tree trước lập kế hoạch** | Sạch (`0` file sửa, `0` file untracked) |
| **Chỉnh sửa source / Gradle / Rules / Firmware** | **0** (chỉ tạo tài liệu `.md` trong `docs/smarthome-core-integration/`) |
| **Chạy Build / Unit Test / Android Test / Emulator** | **0** (tuân thủ giới hạn READ-ONLY Planning) |
| **Truy cập Production `appaintm` / Console / `git push`** | **0** |

---

## 3. Tổng số Prompt & Thứ tự triển khai đề xuất

Đã tạo tổng cộng **11 file Markdown** trong `docs/smarthome-core-integration/`, gồm **4 tài liệu điều phối** và **7 prompt triển khai độc lập** (bao phủ đầy đủ `F1`–`F5`, ≤ 2 prompt mỗi tính năng):

| Thứ tự | File | Tính năng | Phụ thuộc | Commit dự kiến (Nhánh A) |
|---|---|---|---|---|
| **1** | [`prompts/01-F1-mot-nha-mot-chu-so-huu.md`](prompts/01-F1-mot-nha-mot-chu-so-huu.md) | **F1** — Single Home / Single Owner | Checkpoint `0cea70f` | `Tích hợp quản lý một nhà cho chủ sở hữu` |
| **2** | [`prompts/02-F4-vong-doi-phien-dang-nhap.md`](prompts/02-F4-vong-doi-phien-dang-nhap.md) | **F4** — Auth & Session Lifecycle | `01` PASS | `Hoàn thiện vòng đời phiên đăng nhập` |
| **3** | [`prompts/03-F2a-dang-ky-thiet-bi-va-lop-rtdb.md`](prompts/03-F2a-dang-ky-thiet-bi-va-lop-rtdb.md) | **F2 (1/2)** — Device Registration & RTDB Layer | `01` PASS *(xếp sau `02`)* | `Bổ sung đăng ký thiết bị và lớp dữ liệu RTDB cho Gateway` |
| **4** | [`prompts/04-F2b-dong-bo-gateway-len-cloud.md`](prompts/04-F2b-dong-bo-gateway-len-cloud.md) | **F2 (2/2)** — Gateway State/Config/Telemetry/Events Sync | `02` + `03` PASS | `Đồng bộ trạng thái ESP32 lên Firebase` |
| **5** | [`prompts/05-F3-du-lieu-cloud-du-phong.md`](prompts/05-F3-du-lieu-cloud-du-phong.md) | **F3** — Cloud Data Fallback (Domain/Repo) | `03` + `04` PASS | `Bổ sung dữ liệu Cloud dự phòng khi mất LAN` |
| **6** | [`prompts/06-F5a-giao-dien-trang-thai-va-nguon-du-lieu.md`](prompts/06-F5a-giao-dien-trang-thai-va-nguon-du-lieu.md) | **F5 (1/2)** — UI Trạng thái Sync & Hybrid View | `04` + `05` PASS | `Hiển thị trạng thái đồng bộ và nguồn dữ liệu Cloud` |
| **7** | [`prompts/07-F5b-don-gian-hoa-cai-dat-va-thong-tin.md`](prompts/07-F5b-don-gian-hoa-cai-dat-va-thong-tin.md) | **F5 (2/2)** — UI Cài đặt & Thông tin hệ thống | `01`, `02`, `06` PASS | `Đơn giản hóa cài đặt và cập nhật thông tin ứng dụng` |

Sau khi Prompt `07` đạt `PASS` từ Nhánh B, chuyển sang thực thi [`AUDIT-PLAN-POST-INTEGRATION.md`](AUDIT-PLAN-POST-INTEGRATION.md) trước khi chạy kiểm thử phần cứng `C1` và `B1-D`/`C4`.

---

## 4. Các quyết định kỹ thuật trọng yếu đã áp dụng

1. **Bảo vệ 100% test double B3 (`CD-4`, `D9`):** Không thêm method abstract vào `CloudRepository` hay `CloudUiBridge` (đang có 3 test double trong B3); tách toàn bộ hợp đồng thiết bị sang `CloudDeviceRepository` và `CloudDeviceUiBridge` (`CloudDeviceUiBridgeRegistry`), gắn cờ `singleHomeMode: Boolean = false` trên card UI (mặc định `true` trên `*FromBridge`).
2. **Giải quyết bất tương thích `stream_id` firmware vs Rules `events` (`D2`, `CD-2`):** Firmware tạo `streamId` có dấu `-` và có thể vượt 16 ký tự; `CloudMapper.toCloudStream()` băm SHA-256 sang đúng 16 ký tự hex chữ thường hợp lệ regex Rules `^[A-Za-z0-9]{1,16}$` mà không đụng firmware.
3. **Tuân thủ `CD-2` (Giữ nguyên 100% `database.rules.*`):** `state/gateway/values` chỉ đồng bộ đúng 8 trường hợp lệ (`light`, `door`, `alarm`, `motion`, `monitoring`, `temp`, `hum`, `mode`); dữ liệu mưa / giàn phơi chỉ đẩy qua `events` (`rain_started`, `rain_cleared`, `clothes_rain_retract`, ...).
4. **Định danh & Vòng đời `deviceId` không dùng `deviceKey` (`D1`):** Tạo 1 lần bằng `push()`, mỗi phiên đọc `homes/{h}/devices` chọn device `active` cũ nhất; nếu tất cả `revoked` → `BLOCKED` không tự tạo mới; nếu có `>1` `active` → cảnh báo, không xóa.
5. **Xử lý `create-only` trên `events` / `telemetry` (`D3`, `D5`):** Khóa `telemetry` tính theo phút UTC (`yyyyMMddHHmm_g`); khởi tạo cursor từ 100 event cuối; khi ghi bị `PermissionDenied`, đọc lại chính key đó để phân biệt trùng (bỏ qua an toàn) vs từ chối quyền thật (dừng sync).
6. **LAN-first & Auth Lifecycle không sửa `MainActivity.kt` (`CD-3`, `D7`, `D11`):** Tách hoàn toàn Cloud sync/fallback khỏi `SmartHomeViewModel.network` Mutex; khi mất phiên Auth bên ngoài vẫn giữ `_loggedIn = true` cho LAN và hiển thị nút `"Đăng nhập lại"` qua `HomeScreen`; mất WAN còn cached user giữ nguyên `SignedIn`.

---

## 5. Các vấn đề chưa thể giải quyết / Giới hạn ghi nhận

1. **`B1-A2` (`PENDING USER VERIFICATION`):** Kiểm thử hồi quy LAN offline trên điện thoại Android thật + ESP32 thật (`C1`) vẫn chờ phần cứng của người dùng; chỉ cho phép nghiệm thu cuối sau khi `C1` hoàn tất (`CD-1`).
2. **`B1-D` (`LOCKED`):** Production RTDB `appaintm` chưa được audit read-only / backup / deploy (`CD-5`). Toàn bộ 7 prompt chỉ chạy trên Local Emulator `demo-smarthomeai`.
3. **Trạng thái giàn phơi / mưa realtime khi vắng nhà (`CD-2`):** Không thể xem `clothesRetracted` / `raining` hiện thời trên Cloud nếu không sửa `database.rules.src.json`; người dùng chỉ xem được các sự kiện mưa/giàn phơi gần nhất qua `events`.
4. **Không gắn chặt `deviceId` với phần cứng ESP32 vật lý:** Nếu chủ nhà đổi mạch ESP32 hoặc kết nối nhầm mạch khác trong LAN, Cloud dùng chung 1 logical `deviceId` của nhà chính (phù hợp mô hình 1 nhà – 1 ESP32, ngoại trừ nhận diện `streamId` mới qua SHA-256).
5. **Back stack sau khi `"Đăng nhập lại"` (`CD-3`):** Vì `MainActivity.kt` bị khóa, `LoginScreen` khi đăng nhập lại từ banner trên `HomeScreen` sẽ push thêm màn hình `"home"` mới lên trên màn hình `"home"` cũ.
6. **Lưu giữ & Phân tách cục bộ (`D13`, `Storage.kt`):** Chưa có cơ chế tự dọn `telemetry`/`events` cũ (~1.440 mẫu telemetry/ngày khi chạy liên tục) và `Storage.kt` vẫn dùng chung `SharedPreferences`/SQLite trên một máy khi đổi tài khoản.

---

## 6. Những điều người dùng cần biết trước khi bắt đầu

1. **Cách chạy mỗi Prompt:** Mở một phiên (hoặc dùng cùng phiên tuần tự), đưa nội dung file prompt cho **Model Triển khai (Nhánh A)**. Khi Nhánh A commit xong và dừng lại, **chuyển sang Model Kiểm thử độc lập (Nhánh B)** ngay trong cùng cuộc hội thoại đó (không cần dán lại prompt) để Nhánh B kiểm tra diff, chạy Gradle/Emulator và chốt `PASS` trước khi sang prompt kế tiếp.
2. **Thứ tự bắt buộc:** Chạy tuần tự `01 → 02 → 03 → 04 → 05 → 06 → 07` vì nhiều file lõi (`SmartHomeViewModel.kt`, `CloudHomeSection.kt`, `RtdbDeviceRepository.kt`) giao nhau giữa các prompt.
3. **Tuyệt đối không `git push`** trong suốt 7 prompt; tất cả commit tiếng Việt đều giữ cục bộ để dễ rà soát hoặc `git revert` khi cần.
4. **Emulator để test Nhánh B:** Các prompt `03`–`07` cần chạy với `$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"` và Firebase Emulator `--project demo-smarthomeai` (cùng AVD `emulator-5554` cho UI test).

---

## 7. Khuyến nghị Prompt đầu tiên nên chạy

Bắt đầu ngay với:
👉 **[`docs/smarthome-core-integration/prompts/01-F1-mot-nha-mot-chu-so-huu.md`](prompts/01-F1-mot-nha-mot-chu-so-huu.md)**
*(Nhánh A: tích hợp `singleHomeMode` cho `CloudHomeManager`, `CloudHomeSection` và `SmartHomeViewModel`, commit với thông điệp `"Tích hợp quản lý một nhà cho chủ sở hữu"` rồi chuyển sang Nhánh B kiểm thử độc lập).*
