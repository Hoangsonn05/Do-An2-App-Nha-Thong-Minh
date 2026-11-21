# P1.1-C0 — Execution Handoff Manifest (Branch A → Branch B)

- **Timestamp (Local):** 2026-11-21 09:38:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Phase:** `P1.1-C0` — Core Scope Rebaseline & Gap Audit (**READ-ONLY**, Branch A: Execution Model)
- **Branch A Status:** **COMPLETE — READY FOR INDEPENDENT REVIEW (BRANCH B)**
- **Target Deliverable Reviewed:** `docs/p1-1-c0/c0-core-scope-rebaseline-report.md`

---

## 1. Source Checkpoint & Git Status

- **Active Branch:** `master`
- **HEAD Commit:** `e7a805cbb2d9c1845e113b423a450b9d989748e0` (`e7a805c`, `2026-11-21 08:05:52 +0700`, `"checkpoint task  b3"`)
- **Commit History Verified (`git log -n 5`):**
  1. `e7a805c` — `checkpoint task  b3`
  2. `d63eb07` — `checkpoint task audit b3` *(thực tế là diff/báo cáo P1.1-B2)*
  3. `5b1fa63` — `checkpoint đóng task B1`
  4. `870616b` — `checkpoint task 1.B1`
  5. `2bdc59f` — `version 3.6 gốc`
- **Git Status (Pre-C0):**
  - 0 tracked files modified.
  - 2 pre-existing untracked files under `docs/p1-1-b4/`:
    - `docs/p1-1-b4/b4-execution-handoff.md`
    - `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md`
  - Không có `b4-independent-validation-report.md` (B4 chưa chạy Branch B trước khi chuyển sang Phương án A).
- **Git Changes Created by Branch A (C0):**
  - **Added (strictly 2 Markdown reports):**
    1. `docs/p1-1-c0/c0-core-scope-rebaseline-report.md`
    2. `docs/p1-1-c0/c0-execution-handoff.md`
  - **Modified tracked files:** **0**
  - **Deleted files:** **0**

---

## 2. Danh sách file & tài liệu đã kiểm tra (Read Inventory)

### A. Tài liệu dự án & lịch sử
- `README.md`
- `docs/VALIDATION.md`
- `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (RTDB-only rev.2)
- `docs/P1.1-B1_RTDB_Cloud_Foundation_Plan.md`
- `docs/p1-1-b1/b1-real-world-verification-report.md`
- `docs/p1-1-b1/rules-test-report.txt` (lines 1–80, 575–639)
- `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md`
- `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md`
- `docs/p1-1-b4/b4-execution-handoff.md`
- `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` (lines 1–250)
- `cloud-rules/README.md`
- *(Ghi nhận kiểm tra sự tồn tại: `P1.1-A Initial Audit` không tồn tại dưới dạng file trong `docs/` hoặc lịch sử Git.)*

### B. Source Code Android — Cloud (`app/src/main/java/com/example/smarthomeai/cloud/`)
- `CloudModels.kt`
- `CloudRepository.kt`
- `CloudSession.kt`
- `FirebaseProvider.kt`
- `RtdbPaths.kt`
- `RtdbPayloadBuilder.kt`
- `CloudErrorMapper.kt`
- `RtdbHomeMerger.kt`
- `RtdbCloudRepository.kt`
- `CloudHomeManager.kt`

### C. Source Code Android — Data, Core, UI & Face
- `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`
- `app/src/main/java/com/example/smarthomeai/data/Storage.kt`
- `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`
- `app/src/main/java/com/example/smarthomeai/data/Models.kt`
- `app/src/main/java/com/example/smarthomeai/data/Security.kt` (lines 1–60)
- `app/src/main/java/com/example/smarthomeai/data/HardwareStatus.kt`
- `app/src/main/java/com/example/smarthomeai/face/FaceEngine.kt` (lines 1–45)
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/LoginScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/SystemScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`

### D. Gradle, Manifest, Git, Rules, Firmware & Tests
- `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `app/src/main/AndroidManifest.xml`, `.gitignore`
- `cloud-rules/database.rules.src.json`, `cloud-rules/build-rules.mjs`, `cloud-rules/firebase.json`, `cloud-rules/package.json`, `cloud-rules/scripts/run-emulator.mjs`, `cloud-rules/test/rules.test.mjs` (lines 1580–1703)
- `esp32/SmartHomeESP32/SmartHomeESP32.ino` (lines 1–120, 1140–1255 + grep endpoints / `Preferences` / `DEVICE_KEY`)
- `app/src/test/java/com/example/smarthomeai/cloud/CloudSessionAndMultiHomeTest.kt`
- `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudB3IntegrationTest.kt` (lines 1–120, 280–445)
- Quét danh sách test methods trong `app/src/{test,androidTest}/java/com/example/smarthomeai/cloud/*.kt`

---

## 3. Kết quả Audit tóm tắt

- **Kiến trúc RTDB-only & LAN-first** được giữ nguyên 100%.
- **Ma trận phân loại:**
  - `KEEP`: Toàn bộ LAN/ESP32/HW, Auth, Local Storage, Face Recognition cục bộ, Cloud Foundation cốt lõi, `SmartHomeViewModel`, `HomeScreen`, `SettingsScreen`, `cloud-rules/` infrastructure.
  - `FREEZE`: Logic & test B3 cho Multi-home switching / Member management (`observeMembers`, `leaveHome`, `removeMember`, `CloudSessionAndMultiHomeTest`, `RtdbHomeMetaFetcherTest`) và các nhánh Rules chưa dùng (`state/device`, `commands`, `acks`, `telemetry/*_d`, `events/d_*`, `deviceAuthUid`, `users/$uid`).
  - `DEFER`: Toàn bộ B4 Invitation / Join / QR (`docs/p1-1-b4/*`), Cloud Functions v2, Blaze, Multi-home nâng cao, Multi-ESP32, Cloud Remote Control, và `B1-D` Production Audit/Deploy (giữ `LOCKED` trong C0–C2).
  - `REMOVE`: **NONE (0 mục)** — chứng minh không thỏa 5 điều kiện loại bỏ của A0.4.
- **GAP chính phát hiện trong source:**
  1. Gateway Sync lên RTDB (`devices`, `state/gateway`, `config`, `telemetry`, `events`, `CloudMapper`) đang là **`NOT-IMPLEMENTED`**.
  2. `SmartHomeViewModel._loggedIn` chưa đồng bộ reactive với `CloudSession.observeSession()`.
  3. `CloudHomeSection.kt` vẫn hiển thị UI Multi-Home, Cloud Member và ghi chú B4; `SettingsScreen.kt:116` "Về ứng dụng" đã lỗi thời.
  4. `state/gateway/values` trong `database.rules.src.json:131-142` (`$other: false`) chưa có trường cho giàn phơi/mưa/vật cản (`clothesRetracted`, `rainRetractEnabled`, `raining`, `obstacle`, `distanceCm`).
  5. `B1-A2` vẫn là **`PENDING USER VERIFICATION`**.
  6. `B1-D` (Production Rules/Data trên `appaintm`) vẫn **`LOCKED`**.
  7. `B2-F1` (chưa có `signingConfigs.release` / Release SHA-1 trong `google-services.json`).

---

## 4. Các kết luận kỹ thuật quan trọng

1. **Khớp nối `MIN-01` không thể xóa (`database.rules.src.json:30`):** `userHomes/$uid/$homeId/.write` yêu cầu `homes/{homeId}/members/{uid}/role === 'owner'`. Vì vậy, ngay cả với **1 nhà – 1 chủ nhà**, `createHome()` bắt buộc vẫn phải ghi `homes/{homeId}/members/{ownerUid}` và `userHomes/{ownerUid}/{homeId}`.
2. **Điều kiện `@ACTIVE` chặn trước khi ghi Gateway Sync:** Rules hiện tại yêu cầu `homes/$h/devices/$d/status === 'active'` trước khi ghi `state/gateway`, `config`, `telemetry`, `events`. C2 bắt buộc phải tạo node `homes/{homeId}/devices/{deviceId}` (không chứa `deviceKey`).
3. **Ẩn UI đa nhà/thành viên không cần sửa Rules:** Có thể tinh giản `CloudHomeSection.kt` ở C2 mà không đụng `database.rules.src.json`.
4. **Phân biệt "Thành viên" ở `SettingsScreen.kt:66`:** Mục `"Thành viên"` trong `SettingsScreen` là danh sách **khuôn mặt mở cửa cục bộ (`FaceStore` / `state.registeredUsers`)**, không phải Cloud RTDB member (`CloudHomeManagementCard` ở dòng 63). Tuyệt đối không xóa nhầm hoặc đẩy lên RTDB.
5. **Bất biến LAN offline:** Không được tự đặt `_loggedIn = false` khi mất Internet nếu Firebase user vẫn còn cached session, vì vòng lặp polling ESP32 (`SmartHomeViewModel.kt:55-58`) kiểm tra `if (_loggedIn.value)`.

---

## 5. Danh sách Blocker

| ID | Blocker | Ảnh hưởng tới | Trạng thái |
|---|---|---|---|
| **BLK-01** | Thiếu thiết bị Android vật lý & phần cứng ESP32 thật kết nối LAN ngắt WAN trong môi trường này | Đóng **`B1-A2`** (C1) và nghiệm thu cuối (C4) | `PENDING USER VERIFICATION` |
| **BLK-02** | Production RTDB `appaintm` chưa được audit read-only / backup và chưa có văn bản phê duyệt (`B1-D`) | Kiểm chứng hoặc vận hành thực tế trên `appaintm` (C3/C4) | `LOCKED` |
| **BLK-03** | Chưa có tài liệu `P1.1-A Initial Audit` trong repo (chỉ thấy `VALIDATION.md` & tham chiếu gián tiếp ở B4) | Đối chiếu lịch sử C0 (đã ghi nhận minh bạch, không bịa nội dung) | Đã xử lý trong §3 báo cáo C0 |
| **BLK-04** | Cần người dùng chốt các Open Decisions `CD-1..CD-6` | Xác định phạm vi chính xác trước khi triển khai C1/C2 | Chờ User Decision |

---

## 6. Các rủi ro & giả định cần Independent Review Model (Branch B) xác minh độc lập

1. **Xác minh coupling `MIN-01` & `@MEMBER` / `@OWNER`:** Kiểm tra trực tiếp `cloud-rules/database.rules.src.json:26-78`, `cloud-rules/build-rules.mjs:8-14`, và `RtdbCloudRepository.kt:144-179` để xác nhận kết luận `REMOVE = NONE` đối với `members/{ownerUid}` và `userHomes/{ownerUid}` là chính xác 100%.
2. **Xác minh prerequisite `@ACTIVE` & schema `state/gateway/values`:** Đối chiếu `database.rules.src.json:80-273` để xác nhận C2 cần node `devices/{deviceId}` và xác nhận các trường được phép trong `state/gateway/values` (dòng 131–142) vs `HardwareStatus.kt`.
3. **Xác minh lỗ hổng session `_loggedIn` vs `CloudSession`:** Kiểm tra `SmartHomeViewModel.kt:35-39, 54-87` và `MainActivity.kt:46-96` xem phân tích auth/session lifecycle có chính xác và không làm hỏng luồng offline cached-session hay chế độ demo khi thiếu `google-services.json`.
4. **Xác minh phân tách Face Recognition vs Cloud Members:** Kiểm tra `SettingsScreen.kt:62-77` và `MainActivity.kt:61, 88-93` để chắc chắn không nhầm lẫn giữa hai khái niệm.
5. **Xác minh bảo toàn trạng thái `B1-A2` & `B1-D`:** Xác nhận báo cáo C0 không nâng trạng thái tính năng trái bằng chứng và vẫn giữ `B1-A2 = PENDING USER VERIFICATION`, `B1-D = LOCKED`.
6. **Xác minh giới hạn phạm vi C2/C3/C4:** Đánh giá danh sách file trong §13 của báo cáo C0 xem có bỏ sót dependency hoặc cấp quyền chỉnh sửa quá rộng không (đặc biệt `CloudB3IntegrationTest.kt` nếu sửa UI `CloudHomeSection.kt`).

---

## 7. Hướng dẫn chính xác cho Branch B (Independent Review Model)

Khi người dùng chuyển sang **Independent Test/Review Model** trong cùng cuộc hội thoại này, Branch B cần:

1. Đọc lại phần **VI. PHASE B — INDEPENDENT REVIEW MODEL** trong prompt gốc, cùng với:
   - `docs/p1-1-c0/c0-core-scope-rebaseline-report.md`
   - `docs/p1-1-c0/c0-execution-handoff.md`
   - Các tài liệu `B0–B4` và source liên quan được trích dẫn trong báo cáo.
2. Kiểm tra tính toàn vẹn (`B3`):
   - Đối chiếu `git status --short --untracked-files=all`, `git diff --stat`, và HEAD `e7a805c`.
   - Xác nhận Branch A chỉ tạo 2 file Markdown trong `docs/p1-1-c0/`, không sửa bất kỳ file `.kt`, `.xml`, `.gradle.kts`, `cloud-rules/*`, `esp32/*`, `docs/p1-1-b4/*`, hay `app/google-services.json` nào.
3. Thực hiện Independent Static Review đủ 12 điểm (`B2` mục 1–12), kèm bằng chứng file/class/method/Rules path cụ thể.
4. **Không chạy build, Gradle test, Emulator hay validation workload (`B4`)** vì C0 là read-only audit; nếu phát hiện kết luận cần runtime test để chứng minh thì ghi rõ bài test và phase đề xuất (C1/C2/C3/C4).
5. Phân loại mọi finding (nếu có) theo `CRITICAL / MAJOR / MINOR / INFO` (`B5`), không suy đoán thiếu bằng chứng.
6. Đưa ra kết luận độc lập (`PASS` / `CONDITIONAL PASS` / `FAIL`) theo `B6` và tuân thủ `B7` (không tự sửa source hay tài liệu trong vai trò Review Model).

---

## 8. Các điểm cần User Decision (`CD-1..CD-6`)

1. **`CD-1`:** Chạy C1 trước (nếu có đủ phone thật + ESP32 thật) hay cho phép chạy C2 (phần mềm + Local Emulator) song song/trước trong khi chờ phần cứng cho C1? *(C1 vẫn bắt buộc trước C4.)*
2. **`CD-2`:** Ở C2, giữ nguyên Rules `state/gateway/values` (8 trường + sự kiện mưa/giàn phơi qua `events`) hay phê duyệt mở rộng Rules để đẩy thêm trạng thái giàn phơi/mưa?
3. **`CD-3`:** Khi mất phiên Auth bên ngoài ở C2, chỉ hiển thị trạng thái/nút đăng nhập lại qua `SmartHomeViewModel` hay cho phép sửa tối thiểu `MainActivity.kt` để tự điều hướng về `"login"`?
4. **`CD-4`:** Chính sách đối với 2 UI test thành viên B3 trong `CloudB3IntegrationTest.kt` khi tinh giản UI ở C2 (dùng tham số/chế độ single-owner mặc định hay cập nhật test)?
5. **`CD-5`:** Kế hoạch cung cấp quyền đọc `appaintm` cho `B1-D` Production Audit & Backup ở đầu C3.
6. **`CD-6`:** Mục tiêu bàn giao C4 là Debug APK hay Release APK có ký keystore riêng (liên quan đăng ký Release SHA-1 `B2-F1`).

---

## 9. Xác nhận tuân thủ Handoff Gate (Branch A)

- **Chỉ tạo/cập nhật tài liệu C0 trong `docs/p1-1-c0/`:** **CONFIRMED** (`c0-core-scope-rebaseline-report.md`, `c0-execution-handoff.md`).
- **Không sửa bất kỳ source implementation, UI, Gradle, Rules, firmware hay file B4 nào:** **CONFIRMED**.
- **Không chạy unit test, instrumented test, Gradle build, npm test, Firebase Emulator hay Android Emulator:** **CONFIRMED**.
- **Không chạm Production `appaintm`; `B1-D` giữ `LOCKED`; `B1-A2` giữ `PENDING USER VERIFICATION`:** **CONFIRMED**.
