# P1.1-C0 — Core Scope Rebaseline & Gap Audit Report

- **Timestamp (Local):** 2026-11-21 09:35:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Phase:** `P1.1-C0` — Core Scope Rebaseline & Gap Audit (**READ-ONLY**, Branch A: Execution Model)
- **Approved Target Scope:** **Phương án A — MỘT NHÀ — MỘT ESP32 — MỘT TÀI KHOẢN CHỦ NHÀ**
- **Architecture Baseline:** `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (RTDB-only rev.2), `docs/p1-1-b1/b1-real-world-verification-report.md` (`B1 CONDITIONAL PASS`), `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md` (`B2 PASS`), `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md` (`B3 PASS`)
- **Production Lock:** **`B1-D` remains `LOCKED`** — zero Production RTDB, Auth, Rules, or Console access.
- **B1-A2 Hardware Gate:** **`PENDING USER VERIFICATION`** (chưa có bằng chứng phần cứng thực tế).

---

## 1. Executive Summary

Sau các phase `P1.1-B0` đến `P1.1-B3` (và bản nháp thiết kế `P1.1-B4`), người dùng đã phê duyệt chuyển hướng dự án **SmartHomeAI** sang **Phương án A: MỘT NHÀ — MỘT ESP32 — MỘT TÀI KHOẢN CHỦ NHÀ**, tập trung hoàn thiện đường lõi ổn định, an toàn, có kiểm chứng phần cứng thực tế và giữ nguyên kiến trúc **LAN-first** (`Android → Esp32Client → HTTP LAN → ESP32`). Các tính năng cộng tác/mở rộng (Invitation / Join / QR, Cloud Functions v2, Multi-home nâng cao, Multi-ESP32, quản trị thành viên Cloud) được chủ động tạm hoãn (`DEFERRED`), **không xóa** (`DEFER ≠ DELETE`).

Kết quả đối chiếu source code, cấu hình Gradle/Firebase, RTDB Security Rules và tài liệu lịch sử tại checkpoint `e7a805c` chỉ ra 5 kết luận trọng yếu:

1. **Không đề xuất xóa (`REMOVE = NONE`) trong C0 và roadmap C1–C4:** Dù dự án thu hẹp về một nhà và một chủ nhà, **không thể xóa** nhánh `homes/{homeId}/members/{ownerUid}`, `userHomes/{ownerUid}/{homeId}`, hoặc các payload/rule nền tảng liên quan. Lý do kỹ thuật trực tiếp từ `cloud-rules/database.rules.src.json:30` (`MIN-01`): rule `.write` của `userHomes/$uid/$homeId` **bắt buộc** `homes/{homeId}/members/{uid}/role === 'owner'` tồn tại trước khi ghi chỉ mục `userHomes`, và `RtdbCloudRepository.observeHomes()` dựa vào `userHomes/{uid}` để khám phá `homeId` vì root `homes` có `.read: false`. Xóa các thành phần này sẽ phá vỡ `createHome()` và `observeHomes()`.
2. **Gateway Sync cốt lõi chưa được triển khai (`NOT-IMPLEMENTED`):** `RtdbCloudRepository` hiện mới có 5 hàm B1/B3 (`observeHomes`, `createHome`, `observeMembers`, `leaveHome`, `removeMember`). Chưa có bất kỳ code nào đăng ký `homes/{homeId}/devices/{deviceId}`, chưa có `CloudMapper`, chưa ghi `state/gateway`, `config`, `telemetry/*_g`, `events/g_{stream}_{seq}`, và chưa có cơ chế đồng bộ Cloud/Local.
3. **Điều kiện tiên quyết `@ACTIVE` và khoảng trống schema trong Rules hiện tại:** `cloud-rules/database.rules.src.json` yêu cầu `@ACTIVE` (`homes/$homeId/devices/$deviceId/status === 'active'`) để ghi vào `state/gateway`, `config`, `telemetry`, và `events`. Ngoài ra, `state/gateway/values` (dòng 131–142, có `$other: false`) hiện chưa khai báo các trường cho giàn phơi/mưa/vật cản (`raining`, `clothesRetracted`, `rainRetractEnabled`, `obstacle`, `distanceCm`), và `config` (dòng 181–193) chưa có các ngưỡng/bật-tắt cảnh báo mở rộng khác. C2 có thể triển khai ngay trong phạm vi Rules hiện tại cho các trường đã hỗ trợ (`light`, `door`, `alarm`, `motion`, `monitoring`, `temp`, `hum`, `mode`, và `events`), hoặc xin user phê duyệt riêng nếu muốn mở rộng schema Rules.
4. **Lệch vòng đời Auth giữa `SmartHomeViewModel._loggedIn` và `CloudSession`:** `SmartHomeViewModel.kt:35` khởi tạo `_loggedIn` một lần lúc tạo ViewModel (`auth.isSignedIn()`) và chỉ cập nhật thủ công trong `login`/`register`/`loginWithGoogle`/`logout`, chưa lắng nghe `CloudSession.observeSession()`. Nếu token/session Firebase bị mất bên ngoài, `CloudHomeManager` chuyển `SIGNED_OUT` nhưng `_loggedIn` và UI chính vẫn giữ trạng thái đăng nhập cũ.
5. **Tách biệt rõ giữa phần cứng đã xác minh và phần chưa xác minh:** `B1-A2` (kiểm thử hồi quy LAN offline trên điện thoại thật + ESP32 thật khi ngắt WAN) vẫn ở trạng thái **`PENDING USER VERIFICATION`**. Các tính năng Cloud hiện có (`B1–B3`) mới chỉ có bằng chứng Unit Test / AVD + Local RTDB Emulator (`demo-smarthomeai`), chưa có bằng chứng Production (`B1-D` đang `LOCKED`).

---

## 2. Source Checkpoint

Kiểm tra trực tiếp bằng `git` và đọc tệp cấu hình trong workspace `d:\MMO\DoAn2\app-nha-thong-minh`:

| Hạng mục | Giá trị quan sát trực tiếp `[REPO]` |
|---|---|
| **Branch** | `master` |
| **HEAD Commit** | `e7a805cbb2d9c1845e113b423a450b9d989748e0` (`e7a805c`) — `2026-11-21 08:05:52 +0700` — `"checkpoint task  b3"` |
| **Lịch sử commit gần nhất** | `e7a805c` (`checkpoint task  b3`) → `d63eb07` (`checkpoint task audit b3`) → `5b1fa63` (`checkpoint đóng task B1`) → `870616b` (`checkpoint task 1.B1`) → `2bdc59f` (`version 3.6 gốc`) |
| **Git Status (trước C0)** | 0 file tracked bị sửa (`modified: 0`); có 2 file untracked có sẵn thuộc B4 thiết kế chưa commit: `docs/p1-1-b4/b4-execution-handoff.md`, `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` *(không có `b4-independent-validation-report.md`)* |
| **Android Application** | `namespace = "com.example.smarthomeai"`, `applicationId = "com.example.smarthomeai"`, `compileSdk = 34`, `minSdk = 24`, `targetSdk = 34`, `versionCode = 11`, `versionName = "3.6.0"`, `release.isMinifyEnabled = false`, chưa cấu hình `signingConfigs.release` |
| **Gradle / AGP / Kotlin** | Gradle Wrapper `8.10.2`, AGP `8.5.2`, Kotlin `1.9.24`, Compose Compiler `1.5.14`, `JavaVersion.VERSION_17`, `google-services` plugin `4.4.2` (chỉ apply khi tồn tại `app/google-services.json`) |
| **Firebase / Coroutines / AI SDKs** | `firebase-bom:33.1.2` (`firebase-auth`, `firebase-database`; B2 xác nhận resolve sang `23.0.0` và `21.0.0`, 0 Firestore/Storage/Functions); `kotlinx-coroutines-{core,android,play-services,test}:1.8.1`; Compose BoM `2024.06.00`; CameraX `1.3.4`; `mlkit:face-detection:16.1.6`; `tensorflow-lite:2.14.0` |
| **Node / Rules Tooling (`cloud-rules/package.json`)** | `firebase-tools ^13.29.1`, `@firebase/rules-unit-testing ^3.0.4`, `firebase ^10.14.1` (chỉ phục vụ local Rules build & Emulator test; báo cáo B1–B3 ghi nhận JBR OpenJDK `21.0.4` tại `D:\Program Files\Android Studio\jbr` và Node `v20.20.2`) |
| **Firebase Config (`app/google-services.json`)** | File gitignored tồn tại cục bộ (`app/build.gradle.kts:7` điều kiện kích hoạt plugin; B2 audit ghi nhận `project_id=appaintm`, `project_number=883290202549`, RTDB `https://appaintm-default-rtdb.asia-southeast1.firebasedatabase.app`, 1 debug SHA-1 `certificate_hash`). Không đọc/xuất secrets trong C0. |

*Lưu ý lịch sử commit:* Commit `d63eb07` mang thông điệp `"checkpoint task audit b3"`, nhưng nội dung commit thực tế (`git log --stat`) là chỉnh sửa + báo cáo của phase **P1.1-B2** (`b2-firebase-configuration-readiness-report.md`, `FirebaseConfigurationReadinessTest.kt`, sửa `FirebaseProvider.kt`, `RtdbCloudRepository.kt`, `CloudInitializationIntegrationTest.kt`).

---

## 3. Tài liệu & Source đã đọc

### 3.1 Tài liệu lịch sử đã đọc & đối chiếu bản Canonical

| Tài liệu | Trạng thái trong repo | Bản Canonical / Đánh giá đối chiếu |
|---|---|---|
| **P1.1-A Initial Audit** | **KHÔNG TỒN TẠI** thành file riêng trong `docs/` hay lịch sử Git | Được nhắc ngắn gọn trong `b4-invitation-join-qr-contract-report.md` §2.4 với trạng thái `CONDITIONAL GO`. Tài liệu baseline trước Cloud hiện có trong repo là `README.md` và `docs/VALIDATION.md` (ghi ngày 11/11/2026 tại commit gốc `2bdc59f`). |
| `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` | Có (commit `870616b`) | **CANONICAL** cho kiến trúc **RTDB-only rev.2** (updated 20/11/2026, tích hợp F1–F10). *Lưu ý lệch đánh số lộ trình lịch sử:* B0 §10 ban đầu đặt B2=Gateway Sync, B3=Đa Home+Invite+QR `deviceKey`, B4=Remote Command; thực tế repo đã tách B2=Config Readiness, B3=Session/Multi-Home/Member, B4=Invite/Join/QR (và bỏ hoàn toàn việc đưa `deviceKey` vào QR/Cloud). Quyết định RTDB-only của B0 rev.2 giữ nguyên. |
| `docs/P1.1-B1_RTDB_Cloud_Foundation_Plan.md` | Có (commit `870616b`) | Kế hoạch thực thi B1 Local (`B1-D` deploy production tách riêng và bị khóa). |
| `docs/p1-1-b1/b1-real-world-verification-report.md` | Có (commit `5b1fa63`) | **CANONICAL** cho kết quả B1: **`B1 CONDITIONAL PASS`** (A1 AVD PASS, A3 AVD+Emulator PASS, MIN-01/02/03/05 resolved, MIN-04 deferred sang B3, **`B1-A2` = `PENDING USER VERIFICATION`**, `B1-D` = `LOCKED`). |
| `docs/p1-1-b1/rules-test-report.txt` | Có (commit `5b1fa63`) | Log chạy thực tế của B1 (4/4 `test:build`, 76/76 `test:rules`). *Ghi chú:* chưa được ghi đè lại ở B3 khi `rules.test.mjs` tăng lên 80 tests. |
| `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md` | Có (commit `d63eb07`) | **CANONICAL** cho B2 (`B2 PASS` local/emulator; sửa `B2-F4`; ghi nhận `B2-F1` thiếu Release SHA-1, `B2-F2` transitive `@firebase/firestore` trong Node `package-lock.json` vô hại, `B2-F5` AuthStateListener cho B3). |
| `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md` | Có (commit `e7a805c`) | **CANONICAL** cho B3 (`B3 PASS` local/emulator; xử lý `B2-F5` & `MIN-04`; mở rộng `CloudRepository` lên 5 hàm; thêm `CloudHomeManager`, `CloudHomeSection`, `CloudB3IntegrationTest`; báo cáo Rules 80/80, JVM 151/151, Instrumented 69/69). |
| `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` & `docs/p1-1-b4/b4-execution-handoff.md` | Untracked (chưa qua Branch B review) | Tài liệu thiết kế B4 (Branch A). Chưa có `b4-independent-validation-report.md`. Theo Phương án A, toàn bộ phần Invitation/Join/QR chuyển sang **`DEFERRED`**, giữ nguyên file làm tài liệu tham khảo tương lai. |
| `README.md`, `docs/VALIDATION.md`, `cloud-rules/README.md` | Có | Tài liệu mô tả hệ thống 3.6 gốc, kết quả kiểm tra 11/11/2026 và hướng dẫn `cloud-rules/`. |

### 3.2 Source files đã đọc trực tiếp trong C0

- **Cloud (`app/src/main/java/com/example/smarthomeai/cloud/`):** `CloudModels.kt`, `CloudRepository.kt`, `CloudSession.kt`, `FirebaseProvider.kt`, `RtdbPaths.kt`, `RtdbPayloadBuilder.kt`, `CloudErrorMapper.kt`, `RtdbHomeMerger.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`.
- **Data / Core Android:** `AuthRepository.kt`, `SmartHomeViewModel.kt`, `MainActivity.kt`, `Storage.kt`, `Esp32Client.kt`, `Models.kt`, `Security.kt`, `HardwareStatus.kt`, `FaceEngine.kt` (phần đầu).
- **UI:** `LoginScreen.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `SystemScreen.kt`, `CloudHomeSection.kt`.
- **Cấu hình Build / Manifest:** `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `app/src/main/AndroidManifest.xml`, `.gitignore`.
- **Firebase Rules & Tooling:** `cloud-rules/database.rules.src.json`, `cloud-rules/build-rules.mjs`, `cloud-rules/firebase.json`, `cloud-rules/package.json`, `cloud-rules/scripts/run-emulator.mjs`, `cloud-rules/test/rules.test.mjs` (cuối file + thống kê).
- **Firmware ESP32:** `esp32/SmartHomeESP32/SmartHomeESP32.ino` (cấu hình, endpoint HTTP, `Preferences` NVS, `DEVICE_KEY`, vòng lặp chính).
- **Tests Cloud:** `CloudSessionAndMultiHomeTest.kt`, `CloudB3IntegrationTest.kt`, danh sách hàm test toàn bộ `app/src/test/**/cloud/*.kt` và `app/src/androidTest/**/cloud/*.kt`.

---

## 4. Phạm vi chính thức đã khóa (Phương án A)

**Nguyên tắc trung tâm:** **MỘT NHÀ — MỘT ESP32 — MỘT TÀI KHOẢN CHỦ NHÀ.**

### 4.1 Chức năng trong phạm vi cốt lõi (11 hạng mục bắt buộc)

1. Firebase Authentication: đăng nhập và đăng xuất tài khoản chủ nhà (`Email/Password` & `Google Sign-In`).
2. Kết nối Android với một ESP32 qua HTTP LAN (`Esp32Client`).
3. Điều khiển thiết bị nhà thông minh (đèn, cửa chính, còi cảnh báo).
4. Giám sát dữ liệu cảm biến (nhiệt độ, độ ẩm, chuyển động, mưa, vật cản/khoảng cách).
5. Chế độ an ninh (`HOME`, `AWAY`, `SLEEP`, timing, alarm settings, cửa tự đóng, nhận diện khuôn mặt mở cửa cục bộ).
6. Điều khiển giàn phơi thông minh (thu vào / phơi ra, tự thu khi mưa).
7. Nhật ký sự kiện (`events` từ ESP32 theo `stream` + `seq`, cảnh báo, log cục bộ).
8. Lưu trữ dữ liệu cục bộ (`SharedPreferences` + SQLite `smart_home_history.db` + `FaceStore` + ảnh evidence).
9. Firebase Realtime Database (RTDB) cho dữ liệu Cloud thực sự cần thiết phục vụ 1 chủ nhà và 1 ESP32.
10. Quản lý vòng đời phiên đăng nhập và xử lý lỗi đồng bộ (sync cancellation khi sign-out/đổi tài khoản, phân loại lỗi Cloud rõ ràng).
11. Đảm bảo điều khiển LAN hoạt động độc lập với Internet/WAN.

### 4.2 Chức năng tạm hoãn (`DEFERRED` — không xóa)

- Invitation / Join / QR (`docs/p1-1-b4/*`).
- Quản lý thành viên Cloud (mời, xem danh sách nhiều thành viên, rời nhà, xóa thành viên).
- Chia sẻ quyền điều khiển cho người dùng khác.
- Multi-home (chuyển đổi nhiều nhà, quản trị đa nhà).
- Multi-ESP32 (quản lý nhiều bộ điều khiển trên một nhà).
- Backend dành riêng cho lời mời & Firebase Cloud Functions v2 (hoặc nâng cấp Blaze).
- Quản trị thành viên, lịch sử lời mời, thống kê nâng cao, điều khiển thiết bị qua Cloud (`commands`/`acks` + ESP32 REST/SSE) nếu không trực tiếp phục vụ một tài khoản chủ nhà qua LAN-first.

### 4.3 Nguyên tắc kiến trúc & bảo vệ bất biến

1. RTDB là Cloud Database duy nhất (`appaintm`, `asia-southeast1`); **không Firestore**.
2. `Android → HTTP LAN → ESP32` là đường điều khiển chính; không chuyển điều khiển thiết bị sang Cloud.
3. Android local storage dùng `SharedPreferences` + SQLite theo thiết kế hiện tại; ESP32 dùng NVS (`Preferences`).
4. Không đưa `deviceKey`, mật khẩu, thông tin xác thực hay biometric vectors/images lên RTDB hay QR.
5. Không thay đổi HTTP protocol, JSON schema LAN, ESP32 firmware, hoặc các tính năng `VERIFIED-HW`.
6. Không truy cập/sửa/triển khai Production Rules (`B1-D` giữ `LOCKED`); không nâng cấp Blaze; không thêm Cloud Functions/backend; không tái cấu trúc lớn chỉ để xóa tính năng `DEFERRED`.

---

## 5. Ma trận KEEP / FREEZE / DEFER / REMOVE

| Nhóm / Thành phần | File / Symbol cụ thể `[REPO]` | Phân loại | Lý do & Điều kiện |
|---|---|---|---|
| **LAN / ESP32 / Hardware Core** | `Esp32Client.kt`, `HardwareStatus.kt`, `Security.kt`, `AlarmSettings.kt`, `Models.kt`, `Insights.kt`, `Notifier.kt`, `Storage.kt`, `esp32/SmartHomeESP32/*`, `face/FaceEngine.kt`, `face/FaceMath.kt`, `CameraScreens.kt`, `EnvironmentScreen.kt`, `AlertsScreen.kt`, `AutomationScreen.kt`, `SystemScreen.kt`, `OwnerConfirmation.kt`, `SecurityCard.kt`, `AlarmSettingsCard.kt`, `Components.kt`, `SensorChart.kt`, `Theme.kt` | **KEEP** | Trực tiếp phục vụ chức năng 2–8, 11 và toàn bộ phần `VERIFIED-HW` / `VERIFIED-SOFTWARE` LAN-first. Tuyệt đối không thay đổi protocol/firmware. |
| **Auth Core** | `AuthRepository.kt`, `LoginScreen.kt`, `MainActivity.kt` (điều hướng hiện tại) | **KEEP** | Phục vụ đăng nhập/đăng xuất Firebase Auth (Email/Password & Google). |
| **Cloud Foundation Core** | `CloudModels.kt`, `CloudSession.kt`, `FirebaseProvider.kt`, `FirebaseCloudSession`, `CloudFoundationFactory`, `CloudErrorMapper.kt`, `RtdbPaths.kt` (core paths), `RtdbPayloadBuilder.kt` (core payloads + `deviceKey` guard), `RtdbHomeMerger.kt`, `RtdbCloudRepository.kt` (`observeHomes`, `createHome` + mở rộng Gateway Sync ở C2), `CloudHomeManager.kt` (lifecycle + `CloudUiBridgeRegistry` + single-home binding) | **KEEP** | Nền tảng RTDB-only an toàn theo UID, xử lý lỗi, hủy listener khi sign-out/đổi UID, và bridge sạch không leak Firebase SDK vào `SmartHomeViewModel`. |
| **ViewModel & Màn hình tích hợp** | `SmartHomeViewModel.kt`, `HomeScreen.kt`, `SettingsScreen.kt` | **KEEP** | Điểm nối giữa Auth, LAN, Storage và Cloud UI Bridge; cần đồng bộ `_loggedIn` với `CloudSession` và gắn kích hoạt Gateway Sync ở C2. |
| **UI Cloud Banner / Card** | `CloudHomeSection.kt` (`CloudHomeBannerCard`, `CloudHomeManagementCard`) | **KEEP (Simplify UI in C2)** | Giữ component và các trạng thái lỗi/loading/offline/revoked; ở C2 ẩn/thu gọn nút đa nhà (`Nhà (N)`, `HomeSwitcherDialog`) và UI quản lý thành viên Cloud để khớp 1 nhà – 1 chủ nhà. |
| **RTDB Rules Infrastructure** | `cloud-rules/database.rules.src.json`, `database.rules.json`, `build-rules.mjs`, `firebase.json`, `package.json`, `scripts/run-emulator.mjs`, `test/build-rules.test.mjs`, `test/rules.test.mjs` | **KEEP (Freeze unused branches)** | Bảo vệ dữ liệu RTDB theo UID (`@OWNER` / `@MEMBER` / `@ACTIVE`) và kiểm chứng cục bộ qua `--project demo-smarthomeai`. |
| **Multi-home & Cloud Member Logic** | `CloudRepository.observeMembers`, `leaveHome`, `removeMember`; `RtdbPayloadBuilder.buildRemoveMembershipUpdates`; `RtdbHomeMetaFetcher` (>1 home concurrency); `CloudHomeManager.selectHome`, `leaveSelectedHome`, `removeMember`, `canLeaveSelectedHome`, `canRemoveMember`; `CloudSessionAndMultiHomeTest.kt`, `RtdbHomeMetaFetcherTest.kt`, `CloudB3IntegrationTest` (cases multi-home/member), `rules.test.mjs` (#15–#22) | **FREEZE** | Không phát triển thêm, không xóa. Đã hoàn thành và có test bảo vệ từ B3; giữ nguyên để bảo đảm `MIN-01` (`members/{ownerUid}`), bảo toàn rollback và tránh hồi quy không cần thiết. Không ảnh hưởng chức năng cốt lõi 1 nhà khi ẩn UI tương tác ở C2. |
| **Unused RTDB Rules Branches** | `homes/$h/deviceData/$d/{state/device, commands, acks, telemetry/*_d, events/d_*}`, `devices/$d/deviceAuthUid`, `users/$uid`, nhánh member phụ trong `members/$uid` và `userHomes/$uid` | **FREEZE** | Không dùng trong Phương án A (1 chủ nhà, LAN-first), nhưng Rules hiện tại mặc định deny-by-default và đã có 80 Emulator tests xác minh. Sửa/xóa Rules làm tăng rủi ro bảo mật vô ích. |
| **Invitation / Join / QR & Cloud Functions** | `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md`, `docs/p1-1-b4/b4-execution-handoff.md`; đề xuất `CloudInvitationRepository`, `QrInvitationParser`, `mlkit:barcode-scanning`, `zxing:core`, Cloud Functions v2 / Blaze / HTTPS Admin Backend, Multi-ESP32, Multi-home nâng cao | **DEFER** | Chuyển sang phase tương lai theo đúng Phương án A. Chưa triển khai trong source Android/Gradle/Rules; giữ nguyên tài liệu thiết kế B4, không xóa. |
| **Production Audit / Deploy** | `B1-D` (Production RTDB `appaintm` read-only audit, backup & Rules deploy) | **DEFER / LOCKED** | Giữ `LOCKED` trong C0–C2; chỉ thực hiện bước Audit/Backup có user phê duyệt từ C3 và Deploy trước C4 Acceptance. |
| **Bất kỳ file/class/Rules/dependency nào** | **Không có mục nào đề xuất xóa** | **REMOVE: NONE** | Xem kiểm chứng đủ 5 tiêu chí A0.4 ngay bên dưới. |

### Kiểm chứng tiêu chí REMOVE (`REMOVE = NONE`)

Theo A0.4, một thành phần chỉ được đề xuất `REMOVE` nếu thỏa **đồng thời cả 5 điều kiện**:
1. *Không còn được sử dụng* — Các lớp/hàm B3 (`observeMembers`, `leaveHome`, `removeMember`, `RtdbHomeMetaFetcher`) vẫn nằm trên `CloudRepository` / `CloudUiBridge` và được sử dụng bởi 18 unit test B3, 5 instrumented test B3, và `CloudHomeSection`.
2. *Không có dependency trực tiếp/gián tiếp từ `KEEP`* — **THẤT BẠI:** `createHome()` (`KEEP`) phụ thuộc trực tiếp vào `RtdbPayloadBuilder.buildOwnerMemberPayload` và `homes/{h}/members/{uid}` do ràng buộc Rules `MIN-01` (`database.rules.src.json:30`). Tương tự `observeHomes()` phụ thuộc `userHomes/{uid}` và `RtdbHomeMetaFetcher`.
3. *Không ảnh hưởng RTDB Rules hoặc schema hiện tại* — **THẤT BẠI:** Xóa logic `members` phía client hoặc xóa nhánh `members`/`userHomes` trong Rules phá vỡ invariant của 80/80 Emulator test và `MIN-01`.
4. *Không ảnh hưởng khả năng rollback* — **THẤT BẠI:** Xóa code B3 đã commit ở `e7a805c` làm mất trạng thái sạch của checkpoint B3 và buộc sửa hàng loạt file/test (`CloudRepository`, `RtdbCloudRepository`, `CloudHomeManager`, `FakeCloudRepository`, `rules.test.mjs`).
5. *Việc xóa thực sự có lợi hơn giữ lại* — **THẤT BẠI:** Giữ nguyên code B3 ở trạng thái `FREEZE` (chỉ thu gọn UI hiển thị ở C2) tốn **0 rủi ro hồi quy**, giữ nguyên 151 JVM + 69 Instrumented + 80 Emulator tests, và bảo toàn đường nâng cấp nếu sau này mở lại tính năng chia sẻ.

---

## 6. Ma trận Dependency

| Thành phần | Dependency (Trực tiếp & Gián tiếp) | Phân loại | Rủi ro nếu loại bỏ | Đề xuất |
|---|---|---|---|---|
| `homes/{h}/members/{ownerUid}` & `buildOwnerMemberPayload` | `database.rules.src.json:30` (`userHomes/$uid/$homeId/.write` kiểm tra `members/$uid/role === 'owner'`); `RtdbCloudRepository.createHome()` Step 2 & Step 3 | **KEEP** *(chỉ owner entry)* | **RTDB Permission Regression:** Step 3 của `createHome()` bị `PERMISSION_DENIED` ngay lập tức (`MIN-01`). | Giữ nguyên write `members/{ownerUid}` khi tạo nhà; giữ nguyên Rules. |
| `userHomes/{uid}/{homeId}` & `RtdbPaths.userHomesPath` | `RtdbCloudRepository.observeHomes()` & `createHome()` Step 3; `database.rules.src.json:26-34` | **KEEP** | **Session / Cloud Discovery Failure:** Do `homes/` có `.read: false`, app không thể liệt kê `homeId` của chủ nhà sau đăng nhập lại / đổi thiết bị. | Giữ nguyên chỉ mục `userHomes/{ownerUid}/{homeId}`. |
| `homes/{h}/devices/{deviceId}` & `@ACTIVE` macro | `database.rules.src.json:80-110, 117, 183, 198, 259`; `build-rules.mjs:12` | **KEEP (Cần tích hợp ở C2)** | **RTDB Permission Regression:** Toàn bộ ghi `state/gateway`, `config`, `telemetry`, `events` đều yêu cầu `@ACTIVE` (`devices/$deviceId/status === 'active'`), sẽ bị `PERMISSION_DENIED` nếu chưa tạo node device. | C2 phải đăng ký 1 `deviceId` (không chứa `deviceKey`) cho ESP32 duy nhất với `status = "active"`. |
| `CloudSession` / `FirebaseCloudSession` / `CloudHomeManager` lifecycle | `SmartHomeViewModel.kt:38, 87, 98`; `FirebaseProvider.observeAuthUid()`; `lifecycleMutex` + `sessionGen` | **KEEP** | **Authentication & Session Lifecycle Regression:** Không tự hủy job/listener RTDB khi đăng xuất hoặc đổi tài khoản; dễ rò dữ liệu giữa các phiên. | Giữ nguyên và kết nối `CloudSession` để đồng bộ ngược lại `_loggedIn` trong `SmartHomeViewModel` ở C2. |
| `SmartHomeViewModel._loggedIn` & `AuthRepository` | `MainActivity.kt:50, 75, 76`; `SmartHomeViewModel.kt:35, 56, 87`; `LoginScreen.kt` | **KEEP** | **Authentication & LAN Regression:** Mất cổng vào app và ngắt vòng polling LAN (`if (_loggedIn.value)` dòng 56). | Giữ `AuthRepository` (`LOCKED`), sửa đồng bộ `_loggedIn` với `CloudSession` trong `SmartHomeViewModel` (C2). |
| `CloudHomeManager.selectHome`, `leaveSelectedHome`, `removeMember`, `observeMembers` | `CloudUiBridge`, `CloudHomeSection.kt`, `CloudSessionAndMultiHomeTest.kt`, `CloudB3IntegrationTest.kt` | **FREEZE** | **UI & Test Suite Regression:** Phá vỡ compile/test B3 (`CloudRepository` 5 hàm, `FakeCloudRepository`, 18 unit tests + 5 instrumented tests) mà không mang lại lợi ích cho 1 nhà. | Đóng băng ở tầng Domain/Repo; ở C2 chỉ ẩn nút/dialog đa nhà và thành viên trên UI khi dùng chế độ 1 nhà. |
| `RtdbHomeMetaFetcher` & `RtdbHomeMerger` | `RtdbCloudRepository.observeHomes()` dòng 82–94; `RtdbHomeMetaFetcherTest.kt`; `CloudIsolationTest.kt` | **KEEP (Merge) / FREEZE (Multi-home concurrency >1)** | **Cloud Discovery & Error Classification Regression:** `observeHomes()` không merge được metadata `homes/{h}/meta` hoặc đánh mất phân loại lỗi `Offline`/`Timeout`/`PermissionDenied`. | Giữ nguyên cho 1 nhà (chi phí với `N=1` bằng 1 fetch duy nhất). |
| `CloudHomeSection.kt` (`CloudHomeBannerCard`, `CloudHomeManagementCard`) | `HomeScreen.kt:35-39`; `SettingsScreen.kt:62-64`; `CloudUiBridgeRegistry` | **KEEP (Simplify UI in C2)** | **UI Regression:** Mất hiển thị trạng thái Cloud (`UNAVAILABLE`, `SIGNED_OUT`, `EMPTY`, `ERROR`, `ACCESS_REVOKED`) và nút tạo nhà lần đầu trên `HomeScreen`/`SettingsScreen`. | Giữ 2 card và bridge; C2 ẩn nút chuyển nhiều nhà, nút tạo thêm nhà khi đã có nhà, danh sách thành viên Cloud và dòng ghi chú B4. |
| `SettingsScreen.kt:66-77` ("Thành viên") & `FaceStore` / `FaceEngine` | `SmartHomeState.registeredUsers`; `MainActivity.kt:61, 88-89`; `CameraScreens.kt`; `OwnerConfirmation.kt` | **KEEP** | **Feature & Security Regression (Local Face Door Unlock):** Nhầm lẫn xóa mất quản lý khuôn mặt mở cửa cục bộ vì trùng chữ "Thành viên" với Cloud member! | **Tuyệt đối không xóa hoặc trộn với Cloud member.** Đổi nhãn UI nếu cần rõ nghĩa ở C2 (ví dụ: "Khuôn mặt mở cửa"), không đưa vector lên Cloud. |
| `Esp32Client.kt`, `Storage.kt`, `SmartHomeESP32.ino` | `SmartHomeViewModel.kt`; `HardwareStatus.kt`; `Security.kt` | **KEEP (`LOCKED` protocol/HW)** | **LAN & VERIFIED-HW Regression:** Mất điều khiển LAN, mất đồng bộ event journal, mất lưu trữ SQLite/Prefs hoặc hỏng firmware đã xác minh. | Không sửa `Esp32Client`, `Storage` schema hay `.ino` trong C0–C2. |
| `docs/p1-1-b4/*` (Invitation/Join/QR design) | Không có reference từ code Kotlin/Gradle/Rules | **DEFER** | Không có rủi ro runtime khi giữ tài liệu; xóa sẽ mất tài liệu thiết kế đã tốn công xây dựng cho phase sau. | Giữ nguyên trong `docs/p1-1-b4/`, chưa triển khai code hay phụ thuộc mới. |

---

## 7. Phân tích rủi ro hồi quy

Các thành phần nhạy cảm được gắn nhãn rủi ro riêng để ngăn chặn hồi quy khi bước vào C1–C4:

| Nhóm rủi ro | Thành phần liên quan `[REPO]` | Mô tả nguy cơ hồi quy cụ thể | Biện pháp phòng ngừa |
|---|---|---|---|
| **Authentication Regression** | `AuthRepository.kt`, `SmartHomeViewModel.kt:35, 61-87`, `FirebaseProvider.kt:93-117`, `app/google-services.json` | 1. Đổi `FirebaseAuth` sang instance sai hoặc bỏ điều kiện `auth.configured` làm hỏng luồng demo khi thiếu `google-services.json` (`README.md:57`).<br>2. Không đồng bộ `_loggedIn` với `CloudSession` khiến UI nghĩ đã đăng nhập khi token Firebase đã bị thu hồi/hết hạn.<br>3. Release build dùng Google Sign-In sẽ lỗi nếu chưa thêm Release SHA-1 (`B2-F1`). | Giữ `AuthRepository.kt` không đổi nếu chưa cần; chỉ đồng bộ `_loggedIn` với `CloudSessionState` cẩn thận không chặn offline cached session khi mất WAN; xử lý SHA-1 Release ở C4. |
| **RTDB Permission Regression** | `cloud-rules/database.rules.src.json:30, 39, 63, 117, 131-142, 183, 198, 259`; `build-rules.mjs`; `RtdbCloudRepository.kt` | 1. Xóa `members/{ownerUid}` làm hỏng Step 3 `createHome()` (`MIN-01`).<br>2. Ghi `state/gateway`, `config`, `telemetry`, `events` khi chưa có `homes/{h}/devices/{d}` ở trạng thái `active` (`@ACTIVE`) sẽ nhận `PERMISSION_DENIED`.<br>3. Ghi các trường ngoài whitelist (`raining`, `clothesRetracted`, `deviceKey`) bị `$other: false` từ chối toàn bộ payload. | Giữ nguyên `members/{ownerUid}` & `userHomes`; đăng ký 1 device `@ACTIVE` trong C2; map payload chặt bằng `CloudMapper` whitelist khớp chính xác `database.rules.src.json`. |
| **Session Lifecycle Regression** | `CloudHomeManager.kt` (`lifecycleMutex`, `sessionGen`, `cancelAndJoin()`, `onSignedOut()`), `RtdbCloudRepository.observeHomes` (dòng 90) | Xóa hoặc đơn giản hóa bỏ `sessionGen`/`cancelAndJoin()` khiến callback cũ hoặc mutation chậm sau logout/switch ghi đè state mới hoặc rò listener RTDB nền. | Giữ nguyên toàn bộ cơ chế `lifecycleMutex`, `sessionGen`, `cancelAndJoin()` của B3; bổ sung hủy Gateway Sync job theo đúng `sessionGen`. |
| **LAN Regression** | `SmartHomeViewModel.kt:54-59, 102-393` (`network: Mutex`), `Esp32Client.kt`, `SmartHomeESP32.ino` | 1. Đặt lời gọi Cloud hoặc `withTimeout(10_000L)` bên trong `network.withLock { ... }` hoặc chặn `refreshLocked()` khi Cloud timeout sẽ làm đơ polling LAN 1s và điều khiển thiết bị.<br>2. Đổi `_loggedIn` thành `false` khi mất Internet sẽ **tắt luôn vòng lặp LAN** (`if (_loggedIn.value)` dòng 56)! | Đẩy dữ liệu Cloud chạy bất đồng bộ tách khỏi `network.withLock`; khi mất WAN nhưng Firebase user vẫn còn cached `currentUser`, `CloudSessionState` vẫn là `SignedIn(uid)` (không chuyển `SignedOut`), tuyệt đối không ép logout khi mất mạng. |
| **UI Regression** | `HomeScreen.kt:35-39`, `SettingsScreen.kt:62-77`, `CloudHomeSection.kt`, `CloudB3IntegrationTest.kt` | 1. Sửa trực tiếp `CloudHomeSection.kt` làm ẩn các card quản lý thành viên sẽ làm FAIL 2 test Compose trong `CloudB3IntegrationTest.kt:337-420` nếu không cập nhật/tách flag rõ ràng.<br>2. Nhầm `SettingsScreen.kt:66` ("Thành viên" — khuôn mặt cục bộ) với Cloud member làm mất tính năng mở cửa bằng khuôn mặt. | Tách rõ "Thành viên (Khuôn mặt cục bộ)" và "Nhà Cloud"; thống nhất chính sách test UI cho chế độ 1 nhà trước khi sửa `CloudHomeSection.kt` (`CD-4`). |
| **Data Consistency Regression** | `Storage.kt` (`SharedPreferences` + SQLite), `RtdbCloudRepository.kt:139-179`, `SmartHomeViewModel.kt:117-158` | 1. `createHome()` ghi tuần tự 3 bước chưa có cơ chế phục hồi nếu đứt bước 2/3 (có thể tạo `homeId` mồ côi nếu user bấm tạo lại, hoặc tạo nhiều hơn 1 nhà nếu bấm nhiều lần).<br>2. `Storage.kt` không phân tách theo `uid`; nếu đổi tài khoản khác trên cùng điện thoại, dữ liệu LAN/IP/events cục bộ vẫn giữ nguyên.<br>3. Ghi đè trạng thái LAN từ Cloud sẽ sai vì LAN là nguồn sự thật thời gian thực. | C2 thực thi quy tắc "1 nhà cho 1 chủ nhà" (kiểm tra danh sách nhà hiện có trước khi cho phép tạo mới), chuẩn hóa LAN-wins khi đồng bộ, và đọc `events` bằng khóa tất định `g_{stream}_{seq}`. |

---

## 8. Trạng thái chức năng cốt lõi

Áp dụng nghiêm ngặt 8 nhãn chuẩn; **không tự nâng nhãn** chỉ vì có code hoặc báo cáo PASS cục bộ:

| Chức năng | Code tồn tại | Unit Test (JVM) | Local Emulator / AVD | Thiết bị thật (HW) | Trạng thái chính thức |
|---|---|---|---|---|---|
| 1. Firebase Auth (Login/Register/Google/Logout) | Có (`AuthRepository`, `LoginScreen`, `SmartHomeViewModel`) | Không test trực tiếp FirebaseAuth SDK | Đã smoke-test UI đăng nhập trên AVD (`AppSmokeTest`) | Chưa có log test thiết bị thật với Google/Email live | `IMPLEMENTED-UNVERIFIED` |
| 2. Kết nối Android ↔ 1 ESP32 qua HTTP LAN | Có (`Esp32Client.kt`, `SmartHomeESP32.ino`) | 25 tests (`Esp32ClientTest`) | Smoke UI (`AppSmokeTest`) | Xác nhận phần cứng từ baseline 3.6; **B1-A2 offline regression chưa chạy** | `VERIFIED-HW` *(với điều kiện re-verify B1-A2 offline WAN)* |
| 3. Điều khiển thiết bị (Đèn, Cửa, Còi) | Có (`SmartHomeViewModel`, `HomeScreen`, `.ino`) | Thuộc 88 legacy JVM tests | 54 `AppSmokeTest` + `CloudB3IntegrationTest` | Baseline v3.6 đã xác minh phần cứng (`B0 §1`); cần xác nhận lại cùng B1-A2 | `VERIFIED-HW` *(cần re-verify B1-A2)* |
| 4. Giám sát cảm biến (DHT22, PIR, FC-51, SRF05, Mưa) | Có (`Models`, `HardwareStatus`, `EnvironmentScreen`, `.ino`) | `HardwareStatusTest` (17), `InsightsTest` (10) | `AppSmokeTest` (AVD) | Baseline v3.6 (`B0 §1`); `VALIDATION.md:24` ghi chú cảm biến thực tế cần kiểm tra lại khi demo | `VERIFIED-HW` *(cần re-verify B1-A2)* |
| 5. Chế độ an ninh (`HOME`/`AWAY`/`SLEEP`, timing, alarm settings, face unlock) | Có (`Security.kt`, `AlarmSettings.kt`, `FaceEngine.kt`, `.ino`) | `SecurityTest` (19), `AlarmSettingsTest` (4), `FaceMathTest` (13) | `AppSmokeTest` (AVD) | Baseline v3.6 (`B0 §1`); camera nhận diện người thật theo `VALIDATION.md:24` cần kiểm tra trên máy thật | `VERIFIED-HW` *(riêng camera nhận diện người thật trên HW ghi nhận ở C1/C4)* |
| 6. Điều khiển giàn phơi thông minh & tự thu mưa | Có (`HomeScreen`, `HardwareStatus`, `.ino` `ClotheslineController`) | Thuộc legacy JVM tests | `AppSmokeTest` (AVD) | Baseline v3.6 (`B0 §1`) | `VERIFIED-HW` *(cần re-verify B1-A2)* |
| 7. Nhật ký sự kiện (`events` ESP32 + `alerts` + `logs`) | Có (`SmartHomeViewModel.syncEventsLocked`, `Security.kt`, `AlertsScreen`, `.ino`) | Thuộc `SecurityTest` | `AppSmokeTest` (AVD) | Baseline v3.6 (`B0 §1`) | `VERIFIED-HW` *(cần re-verify B1-A2)* |
| 8. Lưu trữ cục bộ (`SharedPreferences` + SQLite + `FaceStore`) | Có (`Storage.kt`, `FaceEngine.kt`) | Gián tiếp trong legacy tests | `AppSmokeTest` (AVD) | Chưa có test tách biệt riêng trên HW sau B3 | `IMPLEMENTED-UNVERIFIED` *(phần cốt lõi chạy trên AVD trong smoke suite)* |
| 9. RTDB Cloud Foundation (Homes/Members CRUD local emulator) | Có (`cloud/*.kt`, `cloud-rules/*`) | 63 Cloud JVM tests | 15 Android Instrumented tests + 80 Rules Emulator tests (`demo-smarthomeai`) | Chưa test với `appaintm` thật (`B1-D` `LOCKED`) | `VERIFIED-SOFTWARE` *(chỉ trên Local Emulator, chưa Production)* |
| 10. Gateway Sync lên RTDB (`devices`, `state/gateway`, `config`, `telemetry`, `events`) | **Chưa có code** (chỉ có Rules + mẫu trong B0) | 0 | Chỉ có Rules Emulator tests với dữ liệu giả lập trong `rules.test.mjs` | Không có | `NOT-IMPLEMENTED` |
| 11. Vòng đời phiên đăng nhập & hủy sync đồng bộ | Có một phần (`CloudHomeManager`, `FirebaseCloudSession`), nhưng `_loggedIn` chưa nghe `CloudSession` | 11 tests (`CloudSessionAndMultiHomeTest`) | 5 tests (`CloudB3IntegrationTest`) | Chưa test E2E trên máy thật với Firebase Auth thật | `INCOMPLETE` |
| 12. Độc lập LAN khi mất WAN (`B1-A2`) | Có kiến trúc tách biệt (`CloudUiState` ≠ `SmartHomeState`) | `CloudIsolationTest` (7) | `CloudB3IntegrationTest` (fake/local emulator) | **Chưa chạy** (`PENDING USER VERIFICATION`) | `IMPLEMENTED-UNVERIFIED` *(cổng `B1-A2` = `PENDING USER VERIFICATION`)* |

---

## 9. Phân tích GAP chi tiết (20 hạng mục bắt buộc theo A0.7)

| # | Chức năng | Source hiện tại | Bằng chứng | Trạng thái | GAP | Phase xử lý |
|---|---|---|---|---|---|---|
| 1 | **Firebase Auth** | `AuthRepository.kt` (Email/Pass, Google Credential Manager); `app/google-services.json` hiện có | Đọc source `AuthRepository.kt:16-56`; B2 audit cấu hình & `process{Debug,Release}GoogleServices` MATCH | `IMPLEMENTED-UNVERIFIED` | Chưa có automated/manual log xác nhận login Email & Google thật trên `appaintm`; Release build thiếu Release SHA-1 (`B2-F1`). | `P1.1-C2` (Auth E2E) & `P1.1-C4` (Release SHA-1) |
| 2 | **Đăng nhập / đăng xuất** | `LoginScreen.kt`, `SmartHomeViewModel.login/register/loginWithGoogle/logout`, `MainActivity.kt` | Đọc `SmartHomeViewModel.kt:61-87`; `AppSmokeTest` (AVD) | `IMPLEMENTED-UNVERIFIED` | Khi `auth.configured == true`, đăng xuất xóa `userEmail`/`esp32Online` & gọi `cloudManager.onSignedOut()`, nhưng không có luồng tự động quay về `LoginScreen` nếu phiên mất từ bên ngoài. | `P1.1-C2` |
| 3 | **Session lifecycle** | `CloudSession.kt`, `FirebaseCloudSession` (`FirebaseProvider.kt:139-158`), `CloudHomeManager.kt:172-212`, `SmartHomeViewModel.kt:35` | `CloudSessionAndMultiHomeTest` (11 JVM PASS), `CloudB3IntegrationTest` (5 Instrumented PASS) | `INCOMPLETE` | `SmartHomeViewModel._loggedIn` chỉ đọc `auth.isSignedIn()` 1 lần ở `init`, không subscribe `CloudSession.observeSession()`. Ngoài ra, chưa có cơ chế dừng/khởi động lại Gateway Sync job theo `sessionGen`. | `P1.1-C2` & `P1.1-C3` |
| 4 | **Một tài khoản chủ nhà** | `CloudHomeManager` & `CloudHomeSection` hiện hiển thị UI đa nhà, đa thành viên, Leave/Remove & ghi chú B4 (`CloudHomeSection.kt:437-444`) | Đọc `CloudHomeSection.kt:129-446`, `CloudHomeManager.kt` | `INCOMPLETE` | UI/UX vẫn ở chế độ Multi-Home / Multi-Member của B3: cho phép tạo vô hạn nhà mới, hiển thị nút `Nhà (N)` / `Thành viên` / `member` actions. Cần tinh giản hiển thị 1 nhà – 1 chủ nhà theo Phương án A. | `P1.1-C2` |
| 5 | **Liên kết tài khoản với một nhà** | `RtdbCloudRepository.observeHomes/createHome`; `RtdbHomeMerger`; `RtdbPayloadBuilder`; `database.rules.src.json` | 80 Rules Emulator tests + B1/B2/B3 unit & instrumented tests (trên `demo-smarthomeai`) | `VERIFIED-SOFTWARE` *(cho nhiều nhà trên emulator)* / `INCOMPLETE` *(cho ràng buộc 1 nhà)* | Chưa có quy tắc/UX đảm bảo 1 chủ nhà chỉ gắn với **1 nhà mặc định** (tránh tạo lặp nhiều `homeId` mồ côi hoặc nhiều nhà dư thừa); chưa tích hợp liên kết 1 thiết bị ESP32 (`devices/{deviceId}`) vào nhà đó. | `P1.1-C2` |
| 6 | **RTDB read/write** | Đã có read/write cho `userHomes`, `homes/{h}/meta`, `homes/{h}/members`. **Chưa có** read/write cho `devices`, `state/gateway`, `config`, `telemetry`, `events` | Đọc `CloudRepository.kt:9-15`, `RtdbCloudRepository.kt:40-283`, `RtdbPaths.kt:38-63` | `INCOMPLETE` | Thiếu toàn bộ lớp `CloudMapper` và các hàm RTDB read/write cốt lõi cho thiết bị ESP32 (`registerOrActivateDevice`, `publishGatewayState`, `publishConfig`, `publishTelemetry`, `publishEvents`, lịch sử đọc khi LAN offline). | `P1.1-C2` |
| 7 | **Cloud error handling** | `CloudErrorMapper.kt`, `CloudHomeManager.describeLoadError/describeActionError`, `CloudHomeSection.kt` | `CloudErrorMapperTest` (4), `CloudInitializationIntegrationTest` (10), `CloudB3IntegrationTest` | `VERIFIED-SOFTWARE` *(cho Homes/Members)* / `INCOMPLETE` *(cho Gateway Sync)* | Chưa có xử lý lỗi/backoff/retry riêng cho tác vụ ghi nền Gateway Sync (`PermissionDenied` khi thiếu `@ACTIVE`, `Offline`, `Timeout`, trùng khóa create-only `telemetry`/`events` theo `B0 §6.3`). | `P1.1-C2` & `P1.1-C3` |
| 8 | **Local storage** | `Storage.kt` (`SharedPreferences("smart_home")` + SQLite `smart_home_history.db`), `FaceStore` | Đọc `Storage.kt:9-94`; legacy tests + `AppSmokeTest` | `IMPLEMENTED-UNVERIFIED` | Lưu trữ không tách namespace theo `uid` (chấp nhận ở mức single-owner nhưng cần ghi rõ giới hạn); chưa lưu metadata liên kết `cloudHomeId` / `cloudDeviceId` hoặc cursor đồng bộ Cloud đã đẩy gần nhất (`lastSyncedEventCursor`, `lastTelemetryMinute`). | `P1.1-C2` & `P1.1-C3` |
| 9 | **LAN device control** | `Esp32Client.sendCommand`, `SmartHomeViewModel.setDevice/commandLocked`, `HomeScreen`, `.ino` (`/relay`) | `Esp32ClientTest` (25), `AppSmokeTest` (54), baseline v3.6 | `VERIFIED-HW` *(cần re-verify `B1-A2`)* | Chức năng LAN đầy đủ, **nhưng chưa xác nhận hồi quy offline trên phần cứng thật (`B1-A2`)** và chưa đẩy trạng thái/audit sang RTDB `state/gateway`. | `P1.1-C1` (`B1-A2`) & `P1.1-C2` (Cloud mirror) |
| 10 | **Sensor monitoring** | `Esp32Client.readSensor`, `SmartHomeViewModel.applySensor`, `EnvironmentScreen`, `SensorChart`, `.ino` (`/sensor`) | `HardwareStatusTest` (17), `InsightsTest` (10), baseline v3.6 | `VERIFIED-HW` *(cần re-verify `B1-A2`)* | Cần tái xác nhận phần cứng ở C1 (`B1-A2`). Ở tầng Cloud: chưa đồng bộ `state/gateway` và `telemetry/{yyyyMMddHHmm}_g`; lưu ý `state/gateway/values` trong Rules chưa có trường `raining`/`distanceCm`/`obstacle`. | `P1.1-C1` & `P1.1-C2` |
| 11 | **Security mode** | `Security.kt`, `AlarmSettings.kt`, `SmartHomeViewModel.setMode/setTiming/setAlarmSettings/openDoorForFace`, `SecurityCard`, `.ino` | `SecurityTest` (19), `AlarmSettingsTest` (4), `FaceMathTest` (13), baseline v3.6 | `VERIFIED-HW` *(cần re-verify `B1-A2`)* | Chưa đồng bộ trạng thái `mode`/`monitoring` và `config` (`tempLimit`, `humLimit`, `coldLimit`, `autoAlarm`) lên RTDB; camera nhận diện người thật cần tái xác nhận trên thiết bị thật ở C1/C4. | `P1.1-C1` & `P1.1-C2` |
| 12 | **Clothesline** | `HardwareStatus.kt` (`hasClothesline`, `clothesRetracted`, `rainRetractEnabled`), `HomeScreen.ClotheslineCard`, `SmartHomeViewModel.updateClothesline`, `.ino` | Legacy tests, `AppSmokeTest`, baseline v3.6 | `VERIFIED-HW` *(cần re-verify `B1-A2`)* | Phần LAN hoàn chỉnh. **GAP Cloud Schema:** `database.rules.src.json:131-142` chưa có trường `clothesRetracted` / `rainRetractEnabled` / `raining` trong `state/gateway/values` (chỉ ghi nhận được sự kiện `clothes_rain_retract` qua `events`). Cần quyết định `CD-2`. | `P1.1-C1` & `P1.1-C2` (chờ `CD-2`) |
| 13 | **Event journal** | `Esp32Client.readEvents`, `SmartHomeViewModel.syncEventsLocked`, `Security.kt` (`DeviceEvent`), `AlertsScreen`, `.ino` (`/events`, ring buffer 64 sự kiện) | `SecurityTest`, `AppSmokeTest`, baseline v3.6 | `VERIFIED-HW` *(cần re-verify `B1-A2`)* | Chưa đồng bộ sự kiện sang RTDB `homes/{h}/deviceData/{d}/events/g_{stream}_{seq}` (khóa idempotent đã có sẵn trong Rules dòng 255–272). | `P1.1-C1` & `P1.1-C2` |
| 14 | **LAN offline operation** | Vòng polling `SmartHomeViewModel.kt:54-59`; tách `CloudUiState` khỏi `SmartHomeState` | `CloudIsolationTest` (7), `CloudB3IntegrationTest.homeScreen_whenCloudOfflineOrRevoked...` | `IMPLEMENTED-UNVERIFIED` | **GAP trọng yếu:** (1) `B1-A2` trên điện thoại thật + ESP32 thật khi ngắt WAN vẫn là `PENDING USER VERIFICATION`; (2) khi app đã logout mà có `google-services.json`, user không thể đăng nhập mới nếu không có Internet (chỉ dùng được khi phiên Firebase Auth đã đăng nhập sẵn từ trước). | `P1.1-C1` (`B1-A2`) & `P1.1-C3` |
| 15 | **WAN loss handling** | `CloudErrorMapper` (`Offline`/`Timeout`), `CloudHomeManager.describeLoadError`, `RtdbCloudRepository.runTimedWrite/fetchAllBounded` (`10_000L` timeout) | `CloudInitializationIntegrationTest` (`goOffline`, closed port `19999`), `CloudB3IntegrationTest` | `VERIFIED-SOFTWARE` *(cho Homes/Members)* / `NOT-IMPLEMENTED` *(cho Gateway Sync)* | Chưa có kiểm thử/xử lý mất WAN khi đang chạy vòng lặp Gateway Sync, chưa có hàng đợi/bỏ qua an toàn khi mất mạng tránh văng ngoại lệ hoặc nghẽn coroutines. | `P1.1-C3` |
| 16 | **Reconnection/recovery** | LAN tự phục hồi qua vòng lặp 1s (`SmartHomeViewModel.kt:55-58`). Cloud mới chỉ có nút `retry()` thủ công (`CloudHomeManager.kt:470`) | Đọc `SmartHomeViewModel.kt`, `CloudHomeManager.kt`, `FirebaseProvider.kt` | `INCOMPLETE` | Chưa có quan sát `.info/connected` (đã hoãn từ B1 sang B2+/C3), chưa có tự động hồi phục sync hoặc catch-up dữ liệu sau khi WAN có lại; ESP32 reconnect trên HW cần kiểm tra trong C1. | `P1.1-C1` (LAN) & `P1.1-C3` (Cloud) |
| 17 | **Cloud/local consistency** | `CloudUiState` tách khỏi `SmartHomeState`; chưa có đọc Cloud làm fallback khi LAN offline | Đọc `SmartHomeViewModel.kt`, `Storage.kt`, `HomeScreen.kt` | `NOT-IMPLEMENTED` | Khi ra ngoài LAN (`esp32Online == false`), app hiện chỉ hiển thị dữ liệu cache cục bộ từ `Storage` trên máy đó, chưa đọc `state/gateway` / `telemetry` / `events` từ RTDB (và gắn nhãn nguồn Cloud + `receivedAt` như `B0 §6.7`), cũng chưa giải quyết xung đột LAN-wins. | `P1.1-C2` & `P1.1-C3` |
| 18 | **Android UI integration** | `HomeScreen` gắn `CloudHomeBannerCard`; `SettingsScreen` gắn `CloudHomeManagementCard` + `"Thành viên"` (FaceStore) | Đọc `HomeScreen.kt`, `SettingsScreen.kt`, `CloudHomeSection.kt`; `CloudB3IntegrationTest` | `INCOMPLETE` | UI đang hiển thị nút đa nhà, thành viên Cloud, ghi chú B4 lỗi thời (`CloudHomeSection.kt:437-444`), và `SettingsScreen.kt:116` "Về ứng dụng" còn nội dung cũ ("chưa có thông báo cloud..."). Chưa có UI hiển thị trạng thái đồng bộ thiết bị Cloud / xem dữ liệu Cloud khi vắng nhà. | `P1.1-C2` |
| 19 | **Security Rules** | `cloud-rules/database.rules.src.json` & `database.rules.json` (76 tests B1 + 4 tests B3 = 80 Emulator tests) | `docs/p1-1-b1/rules-test-report.txt` (76/76 B1), B3 report (80/80); **Chưa chạm Production `appaintm`** | `VERIFIED-SOFTWARE` *(Local Emulator)* / `LOCKED` *(Production)* | 1. Rules **Production trên `appaintm` chưa được kiểm tra/backup/deploy** (`B1-D` `LOCKED`).<br>2. Chưa có test Rules mô phỏng luồng Android Gateway thực tế sau khi thêm `devices` + `state/gateway` + `config` + `telemetry` + `events` ở C2.<br>3. File `rules-test-report.txt` vẫn lưu kết quả 76 tests của B1, chưa cập nhật bản 80+ tests. | `P1.1-C3` (Emulator + `B1-D` Audit/Deploy có duyệt) |
| 20 | **Debug/Release readiness** | `app/build.gradle.kts` (`isMinifyEnabled = false`, không có `signingConfigs.release`), `google-services.json` chỉ có 1 Debug SHA-1 (`B2-F1`), `SettingsScreen.kt:116` | Đọc `app/build.gradle.kts`, B2 report §4 (`B2-F1`), `VALIDATION.md` | `INCOMPLETE` | Chưa có Release keystore/signing config, chưa đăng ký Release SHA-1 cho Google Sign-In, chưa chạy `lintDebug` sau các thay đổi B1–B3, chưa có E2E acceptance cho bản dựng cuối. | `P1.1-C4` |

*Ghi chú về Nhận diện khuôn mặt cục bộ:* `FaceEngine.kt`, `FaceMath.kt`, `FaceStore` và `SettingsScreen.kt:66` ("Thành viên") hoạt động 100% on-device và là chức năng thuộc LAN/An ninh cốt lõi (mục 5 & 8). Không đưa embedding/ảnh khuôn mặt lên RTDB (tuân thủ Nguyên tắc II.7).

---

## 10. Đánh giá dữ liệu & RTDB Rules (A0.6)

### 10.1 Trả lời dứt điểm 6 yêu cầu bảo vệ dữ liệu một chủ nhà

1. **UID nào sở hữu nhà?**
   `homes/{homeId}/meta/ownerUid` lưu chính xác `auth.uid` của tài khoản chủ nhà khi tạo (`database.rules.src.json:47`, bất biến sau khi tạo). Toàn bộ quyền `@OWNER` trong Rules đều so khớp `root.child('homes').child($homeId).child('meta').child('ownerUid').val() === auth.uid` (`build-rules.mjs:11`).
2. **Cách liên kết tài khoản chủ nhà với `homeId`:**
   `RtdbCloudRepository.createHome()` ghi tuần tự 3 node: `homes/{homeId}/meta` (`ownerUid = currentUid`) → `homes/{homeId}/members/{currentUid}` (`role = "owner"`) → `userHomes/{currentUid}/{homeId} = "owner"`. Khi khởi động phiên, `observeHomes()` đọc `userHomes/{currentUid}` để lấy danh sách `homeId` rồi kiểm tra từng `homes/{homeId}/meta` (vì node gốc `homes` có `.read: false`). Với Phương án A, C2 cần bổ sung cơ chế chọn/giữ đúng **1 nhà duy nhất** cho chủ nhà (nếu `userHomes/{uid}` đã có nhà hợp lệ thuộc quyền `OWNER` thì dùng nhà đó, không khuyến khích tạo nhiều nhà).
3. **Các path RTDB thực sự cần cho chức năng cốt lõi (1 nhà – 1 ESP32 – 1 chủ nhà):**
   - `userHomes/{ownerUid}/{homeId}` — chỉ mục tìm nhà của chủ nhà.
   - `homes/{homeId}/meta` — thông tin nhà + `ownerUid` gốc phân quyền.
   - `homes/{homeId}/members/{ownerUid}` — bắt buộc giữ cho chủ nhà vì ràng buộc `MIN-01` (`database.rules.src.json:30`).
   - `homes/{homeId}/devices/{deviceId}` — đại diện cho **1 ESP32 duy nhất** với `status = "active"`, `createdBy = ownerUid`, **không có `deviceKey`** và **không cần `deviceAuthUid`** (vì ESP32 chưa giao tiếp trực tiếp Cloud).
   - `homes/{homeId}/deviceData/{deviceId}/state/gateway` — trạng thái hiện tại do Android (gateway) đẩy lên.
   - `homes/{homeId}/deviceData/{deviceId}/config` — cấu hình ngưỡng cảnh báo (`tempLimit`, `humLimit`, `coldLimit`, `autoAlarm`, `rev`).
   - `homes/{homeId}/deviceData/{deviceId}/telemetry/{yyyyMMddHHmm}_g` — mẫu nhiệt độ/độ ẩm theo phút.
   - `homes/{homeId}/deviceData/{deviceId}/events/g_{stream}_{seq}` — nhật ký sự kiện ESP32 dedup tất định theo `stream` + `seq`.
4. **Những Rules hiện tại nào đang bảo vệ dữ liệu cốt lõi?**
   - Root `.read: false`, `.write: false` (`database.rules.src.json:3-4`), **tuyệt đối không hạ xuống `auth != null`**.
   - Cô lập chặt theo UID qua `@OWNER` và `@MEMBER` (mọi user lạ đọc/ghi `homes/{h}/*` hoặc `userHomes/{otherUid}` đều nhận `PERMISSION_DENIED`).
   - Chống giả mạo thời gian (`now === ServerValue.TIMESTAMP`), chống ghi đè `ownerUid`, chống tạo khóa ngắn/đoán trước (`$homeId`/`$deviceId` regex `^[A-Za-z0-9_-]{8,64}$`), chống partial write ở `state/gateway` & `config`, chặn trường lạ qua `$other: false` và chặn từ khóa `'deviceKey'` trong tên/detail.
   - Yêu cầu `@ACTIVE` (`devices/$deviceId/status === 'active'`) trước khi ghi `state/gateway`, `config`, `telemetry`, `events`.
5. **Các path `members` / `userHomes` nào vẫn phải giữ?**
   **Phải giữ cả `homes/{homeId}/members/{ownerUid}` và `userHomes/{ownerUid}/{homeId}`.** Trong `database.rules.src.json:30`, khi chủ nhà tạo entry `userHomes/{uid}/{homeId} = 'owner'`, Rules kiểm tra:
   `root.child('homes').child($homeId).child('members').child($uid).child('role').val() === 'owner'`.
   Nếu bỏ ghi `members/{ownerUid}`, `createHome()` sẽ thất bại ở bước 3. Ngược lại, trong `build-rules.mjs:9`, `@MEMBER` tự động bao gồm `@OWNER` (`...members.child(auth.uid).exists() || @OWNER`), nên chủ nhà hợp lệ luôn có đủ quyền đọc/ghi dữ liệu nhà mình.
6. **Có thể bỏ giao diện quản lý thành viên mà không thay đổi Rules hay không?**
   **CÓ.** Việc ẩn/tinh giản danh sách thành viên Cloud, nút rời nhà/xóa thành viên, nút chuyển nhiều nhà trong `CloudHomeSection.kt` hoàn toàn là thay đổi tầng Presentation/UI (Android), không động chạm đến `database.rules.src.json`, `database.rules.json`, hay bất kỳ đường dẫn RTDB nào.

### 10.2 Lưu ý giới hạn schema hiện tại cho C2

Đối chiếu `SmartHomeState` / `HardwareStatus` với `database.rules.src.json`:
- `state/gateway/values` (dòng 131–142) hiện **chỉ cho phép**: `light`, `door`, `alarm`, `motion`, `monitoring` (`bool`), `temp` (`>-50 && <150`), `hum` (`0..100`), `mode` (`string 1..16`), và có `"$other": { ".validate": false }`.
- Các trạng thái đặc thù của giàn phơi và cảm biến mở rộng (**`raining`**, **`clothesRetracted`**, **`rainRetractEnabled`**, **`obstacle`**, **`distanceCm`**) **chưa có** trong `state/gateway/values`. Riêng các sự kiện chuyển đổi mưa/giàn phơi (`rain_started`, `rain_cleared`, `clothes_rain_retract`) có thể ghi hợp lệ vào `events/g_{stream}_{seq}`.
- **Hệ quả:** Ở C2, nếu giữ Rules nguyên vẹn (ít rủi ro nhất), `CloudMapper` chỉ map đúng 8 khóa hợp lệ vào `state/gateway/values` và đẩy sự kiện giàn phơi/mưa qua `events`. Nếu người dùng muốn xem trạng thái giàn phơi/mưa realtime trên Cloud khi vắng nhà, cần quyết định mở rộng whitelist trong `database.rules.src.json` (`CD-2`).

### 10.3 Khẳng định về Production Rules (`B1-D`)

Static review trên `cloud-rules/database.rules.src.json` chỉ xác nhận tính nhất quán của mã nguồn Rules cục bộ và kết quả Local Emulator trước đây (`demo-smarthomeai`). **Không thể và không được phép** kết luận Production Rules trên Firebase project `appaintm` an toàn chỉ từ static review, vì Rules thực tế đang chạy và dữ liệu sẵn có trên `appaintm` chưa từng được audit read-only hay backup. `B1-D` tiếp tục giữ trạng thái **`LOCKED`**.

---

## 11. Trạng thái B1-A2

- **Trạng thái chính thức:** **`PENDING USER VERIFICATION`** *(bảo lưu nguyên vẹn từ `b1-real-world-verification-report.md`, `b2-firebase-configuration-readiness-report.md`, `b3-cloud-session-multi-home-member-report.md`)*.
- **Lý do chưa thể đóng trong C0:** Không có thiết bị Android vật lý và phần cứng ESP32 thật gắn kết nối mạng LAN không Internet để kiểm chứng, và C0 là phase read-only.
- **Lưu ý kỹ thuật cần kiểm tra kèm khi chạy B1-A2 ở C1:**
  1. Nếu tài khoản đã đăng nhập sẵn trước khi tắt WAN (Firebase Auth lưu cached user cục bộ), app mở vào `HomeScreen` và vòng polling LAN 1s (`SmartHomeViewModel.kt:55-58`) phải hoạt động bình thường không cần Internet.
  2. Cloud lỗi (`Offline` / `Timeout`) phải không làm đổi `esp32Online`, không làm chậm hoặc khóa `network: Mutex`, và không chặn các nút điều khiển thiết bị/cửa/giàn phơi/chế độ.
  3. **Danh sách kiểm tra bắt buộc cho user ký xác nhận đóng B1-A2 ở C1:**
     - Điện thoại Android thật (cài bản build xác nhận) + ESP32 thật nạp `SmartHomeESP32.ino` cùng Wi-Fi LAN, **ngắt WAN/Internet khỏi router**.
     - Điều khiển Đèn, Cửa chính, Còi qua HTTP LAN.
     - Đọc số liệu cảm biến (DHT22 nhiệt độ/độ ẩm, PIR, FC-51, SRF05 khoảng cách, cảm biến mưa).
     - Chuyển đổi chế độ an ninh (`Ở nhà` / `Đi ngủ` / `Ra ngoài`), kiểm tra bảo vệ & hẹn giờ.
     - Điều khiển Giàn phơi (Thu vào / Phơi ra / Tự thu khi mưa).
     - Đồng bộ Nhật ký sự kiện (`events` từ `/events`).
     - Thử ngắt/kết nối lại Wi-Fi ESP32 hoặc mất nguồn ESP32 để kiểm tra phục hồi kết nối.

---

## 12. Roadmap điều chỉnh C1–C4 (theo Phương án A)

| Phase | Tên | Mục tiêu & Phạm vi chính | Tiêu chí vào (Entry) | Tiêu chí ra (Exit / PASS) |
|---|---|---|---|---|
| **P1.1-C1** | **LAN & Hardware Regression** | Xác minh đầy đủ 5 nhóm `VERIFIED-HW` trên phần cứng thật; đóng **`B1-A2`** (LAN hoạt động khi mất WAN); kiểm tra phục hồi mất/có lại kết nối ESP32/Wi-Fi; xác nhận camera/face unlock trên máy thật nếu có. **Tuyệt đối không sửa ESP32 firmware hay HTTP protocol.** | C0 PASS; User bố trí điện thoại thật + ESP32 thật. *(Nếu phần cứng chưa sẵn sàng ngay, xem ghi chú song song bên dưới)* | Biên bản + log thực tế của người dùng xác nhận đủ checklist B1-A2; 0 hồi quy `VERIFIED-HW`; B1-A2 mới được nâng từ `PENDING USER VERIFICATION` → `PASS`. |
| **P1.1-C2** | **Core Cloud Integration (1 Home – 1 ESP32 – 1 Owner)** | 1. Đồng bộ `SmartHomeViewModel._loggedIn` với `CloudSession` (giữ an toàn khi mất WAN với cached user).<br>2. Tinh giản `CloudHomeSection.kt` cho 1 nhà – 1 chủ nhà (ẩn Multi-Home switcher, danh sách/thao tác thành viên Cloud, ghi chú B4; sửa chữ `"Về ứng dụng"` lỗi thời ở `SettingsScreen.kt:116`).<br>3. Đăng ký/khởi tạo 1 thiết bị ESP32 (`homes/{h}/devices/{d}`, `status="active"`, 0 `deviceKey`).<br>4. Viết `CloudMapper` & mở rộng `CloudRepository`/`RtdbCloudRepository` + `CloudHomeManager`: đẩy `state/gateway`, `config`, `telemetry/*_g`, `events/g_{stream}_{seq}`, và đọc state Cloud khi vắng nhà (LAN offline) theo `B0 §6.7`.<br>5. Bảo đảm 100% không chặn `network: Mutex` / LAN. | C0 PASS; User chốt các Open Decisions `CD-1..CD-4`. | JVM unit tests mới + cũ PASS; `assembleDebug` PASS; Local Emulator + AVD Instrumented tests PASS; 0 `deviceKey` lên RTDB; 0 thay đổi ESP32/HTTP LAN/file LOCKED phần cứng. |
| **P1.1-C3** | **Core Reliability & Security** | 1. Bổ sung Emulator tests cho toàn bộ path ghi Gateway (`devices`, `state/gateway`, `config`, `telemetry`, `events`) & cô lập UID, cập nhật báo cáo Rules.<br>2. Hoàn thiện error handling, retry/backoff có giới hạn khi mất WAN, `.info/connected` (read-only) & tự đồng bộ bù khi WAN có lại.<br>3. Kiểm tra hủy job/listener khi sign-out / đổi tài khoản giữa chừng, đảm bảo LAN-wins khi online cùng lúc.<br>4. Thực hiện **`B1-D` Read-Only Production Audit + Backup** trên `appaintm` (chỉ khi có phê duyệt bằng văn bản của user; deploy Rules mới sau khi Emulator PASS + Review độc lập + Backup). | C2 PASS; User phê duyệt tiếp cận read-only `appaintm` cho `B1-D`. | Toàn bộ Rules Emulator tests PASS; Reliability/Cancellation/WAN-recovery tests PASS; Audit & Backup `appaintm` hoàn tất (và Rules Production sẵn sàng/đã deploy theo phê duyệt). |
| **P1.1-C4** | **Final Acceptance & Release** | Kiểm thử toàn hệ thống (JVM + Rules Emulator + Android Instrumented + `lintDebug`); kiểm tra Debug & Release readiness (xử lý Release keystore & `B2-F1` Release SHA-1 nếu build Release); nghiệm thu E2E trên điện thoại thật + ESP32 thật + Cloud thật; chốt tài liệu bàn giao cuối. | **B1-A2 đã PASS** (C1 hoàn tất), C2 PASS, C3 PASS (bao gồm `B1-D`). | Full test suite + build PASS; Hardware + Cloud E2E Acceptance PASS; 0 lỗi Lint nghiêm trọng; bàn giao hoàn chỉnh. |

*Ghi chú điều phối C1 vs C2:* Theo thứ tự chuẩn của roadmap, **C1 thực hiện trước C2** để đóng nợ `B1-A2` càng sớm càng tốt. Tuy nhiên, nếu người dùng chưa có mạch ESP32/điện thoại thử ngay lúc chốt C0, có thể cho phép chạy **C2 (chỉ phần mềm + Local Emulator)** trước/song song vì C2 không sửa firmware hay `Esp32Client`, với điều kiện cứng: **C1 (`B1-A2`) vẫn bắt buộc hoàn tất trước khi vào C4 Final Acceptance** (`CD-1`).

---

## 13. Phạm vi triển khai sau C0 (A0.9 — Bounded File Scope)

Không cấp quyền sửa source chung chung cho các phase sau. Danh sách ranh giới file sau C0:

| Phân loại phạm vi | File / Module cụ thể | Lý do & Dependency phụ thuộc |
|---|---|---|
| **1. Giữ nguyên (Keep As-Is)** | `CloudModels.kt` *(trừ khi bổ sung model thuần Kotlin cho device/state/telemetry/event ở C2)*, `CloudSession.kt`, `CloudErrorMapper.kt`, `RtdbPaths.kt` *(chỉ thêm builder cho `devices`/`deviceData` ở C2)*, `RtdbHomeMerger.kt`, `FirebaseProvider.kt`, `LoginScreen.kt`, `CameraScreens.kt`, `EnvironmentScreen.kt`, `AlertsScreen.kt`, `AutomationScreen.kt`, `SystemScreen.kt`, `ui/components/{AlarmSettingsCard, Components, OwnerConfirmation, SecurityCard}.kt`, `ui/screens/SensorChart.kt`, `ui/theme/Theme.kt`, `data/{AlarmSettings, HardwareStatus, Insights, Models, Notifier, Security}.kt`, `face/{FaceEngine, FaceMath}.kt`, `cloud-rules/{build-rules.mjs, firebase.json, package.json, scripts/run-emulator.mjs}` | Đã hoạt động ổn định hoặc đã có test B1–B3 / legacy kiểm chứng; không sửa ngoài phạm vi trực tiếp của C2/C3. |
| **2. Cần tích hợp (Integrate in C2/C3)** | - `app/src/main/java/com/example/smarthomeai/cloud/`: thêm `CloudMapper.kt` (mới) + mở rộng `CloudRepository.kt`, `RtdbPaths.kt`, `RtdbPayloadBuilder.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`<br>- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt` (đồng bộ `_loggedIn` ↔ `CloudSession`, đẩy Gateway Sync tách khỏi `network: Mutex`, hiển thị fallback Cloud khi LAN offline)<br>- `app/src/main/java/com/example/smarthomeai/data/Storage.kt` *(chỉ nếu cần lưu cursor sync hoặc `cloudHomeId`/`deviceId` gắn nhà, giữ nguyên cấu trúc bảng hiện tại)*<br>- Tests tương ứng trong `app/src/test/**/cloud/` và `app/src/androidTest/**/cloud/`, `cloud-rules/test/rules.test.mjs` | Phục vụ C2 (Gateway Sync + Session sync) và C3 (WAN recovery + Rules Emulator coverage cho nhánh `devices`/`deviceData`). |
| **3. Cần tối giản (Simplify UI in C2)** | - `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt` (ẩn UI chuyển đa nhà, nút tạo thêm nhà khi đã có nhà, danh sách/thao tác thành viên Cloud, và ghi chú B4 dòng 437–444)<br>- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt` (cập nhật hook hiển thị thông tin Cloud đọc được khi vắng nhà nếu có)<br>- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt` (cập nhật text hộp thoại "Về ứng dụng" dòng 116; làm rõ nhãn "Thành viên" dòng 66 là khuôn mặt mở cửa cục bộ để tránh nhầm với Cloud member) | Phù hợp phạm vi Phương án A (1 nhà – 1 chủ nhà) mà không phá vỡ `CloudHomeManager` hay Rules. |
| **4. Đóng băng (Freeze)** | Các method `observeMembers`, `leaveHome`, `removeMember` trong `CloudRepository`/`RtdbCloudRepository`; `buildRemoveMembershipUpdates`; `selectHome`, `leaveSelectedHome`, `removeMember` trong `CloudHomeManager`; `CloudSessionAndMultiHomeTest.kt`, `RtdbHomeMetaFetcherTest.kt`; các nhánh Rules `state/device`, `commands`, `acks`, `telemetry/*_d`, `events/d_*`, `deviceAuthUid`, `users/$uid` | Giữ nguyên trong repo để không phá `MIN-01`, không phá 151 JVM / 80 Rules tests và đảm bảo rollback tức thì. |
| **5. Tạm hoãn (Defer)** | `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md`, `docs/p1-1-b4/b4-execution-handoff.md`; bất kỳ code/dependency nào về QR (`mlkit:barcode-scanning`, `zxing:core`), Cloud Functions v2, HTTPS Admin SDK backend, Blaze, Multi-ESP32, Multi-home nâng cao | Ngoài phạm vi Phương án A; không chạm tới trong C1–C4. |
| **6. Tuyệt đối không sửa (Strictly Locked)** | `esp32/SmartHomeESP32/SmartHomeESP32.ino`, `esp32/SmartHomeESP32/wifi_config.example.h`, `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`, HTTP protocol & JSON payload LAN, `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` *(trừ khi có lỗi Auth phát sinh bắt buộc phải xin phép)*, `app/google-services.json`, mọi tính năng `VERIFIED-HW` | Bảo vệ 100% tính năng phần cứng đã xác minh và tách biệt đường điều khiển LAN. |
| **7. Cần User Approval trước khi sửa** | - `cloud-rules/database.rules.src.json` & `cloud-rules/database.rules.json` (chỉ nếu user chọn mở rộng `state/gateway/values` cho giàn phơi/mưa hoặc trước `B1-D` Production deploy)<br>- `app/src/main/java/com/example/smarthomeai/MainActivity.kt` & `AndroidManifest.xml` (chỉ nếu user muốn tự động pop về `LoginScreen` khi phiên Auth mất giữa chừng)<br>- `app/build.gradle.kts` (chỉ tại C4 nếu thêm `signingConfigs.release` — tuyệt đối không thêm Firestore/Functions/QR deps)<br>- **Bất kỳ thao tác nào lên Production Firebase `appaintm`** (`B1-D` audit, backup, deploy Rules) | Chạm tới Security Rules, Navigation/Lifecycle gốc, Release signing hoặc môi trường Production. |

---

## 14. Open Decisions (Cần người dùng quyết định)

| ID | Vấn đề cần quyết định | Phương án lựa chọn | Tác động |
|---|---|---|---|
| **CD-1** | **Thứ tự chạy C1 vs C2 khi chưa có phần cứng ngay** | **(a) Khuyến nghị:** Chạy **C1 trước** nếu đã có điện thoại + ESP32 sẵn sàng. Nếu chưa có mạch ngay, cho phép chạy **C2 (code + Local Emulator)** trước, nhưng **B1-A2 vẫn khóa C4**.<br>(b) Dừng chờ hoàn tất C1 mới bắt đầu C2. | Quyết định tiến độ ngay sau khi Branch B duyệt C0. |
| **CD-2** | **Dữ liệu giàn phơi / mưa / khoảng cách trong `state/gateway/values` (C2)** | **(a) Khuyến nghị tối giản:** Giữ nguyên 100% `database.rules.src.json` hiện tại; `state/gateway/values` chỉ đồng bộ 8 trường đang được phép (`light`, `door`, `alarm`, `motion`, `monitoring`, `temp`, `hum`, `mode`), còn sự kiện mưa/giàn phơi (`rain_started`, `rain_cleared`, `clothes_rain_retract`) ghi vào `events`.<br>(b) Xin duyệt mở rộng `state/gateway/values` trong `database.rules.src.json` + build lại Rules + thêm Emulator tests để hiển thị cả `clothesRetracted`/`raining` từ xa. | Quyết định có cần sửa RTDB Rules trong C2 hay không. |
| **CD-3** | **Xử lý khi FirebaseAuth mất phiên bên ngoài (`MainActivity.kt`)** | **(a) Khuyến nghị ít xâm lấn:** Đồng bộ `_loggedIn` trong `SmartHomeViewModel` với `CloudSession` (khi mất phiên chắc chắn và không ở chế độ demo), và hiển thị nút/thông báo `"Đăng nhập lại"` rõ ràng trên UI nếu chưa muốn mở khóa `MainActivity.kt`.<br>(b) Phê duyệt sửa tối thiểu `MainActivity.kt` để tự động `LaunchedEffect(loggedIn)` điều hướng về `"login"` khi `loggedIn == false`. | Quyết định việc mở khóa `MainActivity.kt` ở C2. |
| **CD-4** | **Chính sách test UI B3 khi tinh giản `CloudHomeSection.kt` ở C2** | **(a) Khuyến nghị:** Tách/gắn cờ hiển thị `singleHomeOwnerMode: Boolean = true` (hoặc giữ composable `CloudHomeManagementCard` hỗ trợ kiểm thử khi truyền flag, còn mặc định runtime dùng chế độ 1 nhà – 1 chủ nhà) để vừa tinh giản UX thật vừa không phải xóa bài test B3 đã đóng băng; hoặc cập nhật 2 test Compose trong `CloudB3IntegrationTest.kt` sang hành vi 1 nhà nếu ẩn hẳn. | Tránh xung đột giữa C2 UI simplification và bộ test B3 `FREEZE`. |
| **CD-5** | **Thời điểm & phạm vi `B1-D` Production Audit / Deploy** | Thực hiện **Read-Only Audit + Backup `appaintm`** đầu **C3** (bắt buộc có user chạy/duyệt `firebase login` và xem diff trước khi deploy bất kỳ thay đổi Rules nào). | Điều kiện tiên quyết trước khi app thật ghi dữ liệu lên `appaintm`. |
| **CD-6** | **Release Signing & Google Sign-In SHA-1 (`B2-F1`)** | Xác nhận mục tiêu bàn giao cuối (C4) chỉ nghiệm thu bản **Debug APK** hay cần build **Release APK** có ký keystore riêng (nếu Release có dùng Google Sign-In thì phải đăng ký Release SHA-1 trong Firebase Console). | Ảnh hưởng phạm vi C4 (`app/build.gradle.kts`). |

---

## 15. Kết luận Gate (Go / Conditional Go / No-Go)

**Kết luận C0 (Branch A): `CONDITIONAL GO`**

- **Đủ điều kiện chốt lại phạm vi Phương án A (MỘT NHÀ — MỘT ESP32 — MỘT TÀI KHOẢN CHỦ NHÀ)** với kiến trúc RTDB-only + LAN-first không đổi, `REMOVE = NONE`, và roadmap điều chỉnh `C1 → C2 → C3 → C4`.
- **Các điều kiện ràng buộc trước khi viết bất kỳ dòng code nào ở phase tiếp theo:**
  1. **Branch B (Independent Review Model)** hoàn tất static review độc lập và đạt `PASS` hoặc `CONDITIONAL PASS`.
  2. **Người dùng** phê duyệt báo cáo C0, danh sách file được phép chạm trong phạm vi sau C0 (§13), và chốt các Open Decisions (`CD-1..CD-6`).
  3. **Bảo lưu nguyên trạng các gate chưa đóng:** `B1-A2` giữ **`PENDING USER VERIFICATION`** cho đến khi C1 nghiệm thu phần cứng thật; `B1-D` giữ **`LOCKED`** cho đến khi có audit + backup + phê duyệt bằng văn bản.
