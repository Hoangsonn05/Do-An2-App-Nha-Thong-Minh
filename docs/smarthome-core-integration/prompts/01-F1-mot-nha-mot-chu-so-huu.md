# PROMPT 01 — F1: TÍCH HỢP CHẾ ĐỘ MỘT NHÀ — MỘT CHỦ SỞ HỮU (SINGLE HOME / SINGLE OWNER)

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Engineer triển khai **F1 (Single Home / Single Owner)** trên tầng Domain/UI coordinator (`CloudHomeManager`, `CloudHomeSection`, `SmartHomeViewModel`) và tạo unit test cho logic mới (không chạy build/test ở Nhánh A).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Test Engineer đọc diff + handoff của Nhánh A, kiểm tra code thực tế, viết bổ sung (nếu cần) và thực thi kiểm thử JVM + UI + Emulator cho F1 mà không sửa implementation.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` và `git status --porcelain`.
2. Đọc lại các file nguồn trong phạm vi trước khi sửa.
3. Đối chiếu từng yêu cầu mục 4–13 với source thực tế.
4. Chốt phương án kỹ thuật chi tiết theo đúng thiết kế mục 9–12.
5. Triển khai toàn bộ phạm vi mục 5–6 (kèm viết file unit test mới, **không chạy**).
6. Không mở rộng phạm vi ra ngoài mục 6.
7. Chỉ chạy static checks ở mục 15.
8. **KHÔNG chạy unit tests.**
9. **KHÔNG chạy Android instrumented tests.**
10. **KHÔNG chạy Firebase Emulator.**
11. **KHÔNG chạy Gradle build.**
12. **KHÔNG chạy benchmark hoặc workload xác minh.**
13. Xuất báo cáo Handoff đúng mẫu mục 16.
14. Tạo 01 Git commit tiếng Việt riêng theo mục 18 (`TUYỆT ĐỐI KHÔNG PUSH`).
15. **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)** để thực thi Nhánh B ngay trong hội thoại này.

#### NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP (10 bước)
1. Đọc báo cáo Handoff của Nhánh A.
2. Đọc toàn bộ `git diff` và `git show --stat HEAD` của commit Nhánh A.
3. Kiểm tra code thực tế đối chiếu tiêu chí mục 14 và bất biến mục 13.
4. Viết bổ sung test kiểm chứng độc lập (nếu thiếu độ phủ) và chạy lệnh kiểm thử tại mục 14/Nhánh B.
5. **KHÔNG** tự ý thay đổi source implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được trong code của Nhánh A.
7. Phân loại mọi lỗi phát hiện theo mức độ `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện cụ thể (tên test, stack trace/assertion, file:dòng).
9. Xuất báo cáo kiểm thử độc lập (kèm kết quả chính xác số test pass/fail/skip).
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu người dùng phê duyệt prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B (nếu có thêm file test mới, chỉ commit test-only với thông điệp tiếng Việt và `KHÔNG PUSH`).

---

## 2. Checkpoint đầu vào

- **Branch:** `master`
- **HEAD bắt buộc:** `0cea70f3cd83f69568ab35a00af013930544450f` (`0cea70f` — `"checkpoint task audit b4"`)
- **Working tree:** Sạch (`git status --porcelain` rỗng)
- Lệnh kiểm tra đầu phiên: `git rev-parse HEAD; git status --porcelain`

---

## 3. Trạng thái hiện tại đã xác minh

Kiểm tra từ source tại `0cea70f`:
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`:
   - Dòng 43–76: `CloudUiState` có `status`, `homes`, `selectedHomeId`, `membersStatus`, `members`, `busy`, `revokedNotice`, `feedback`.
   - Dòng 125–129: `class CloudHomeManager(scope, session, repository) : CloudUiBridge` — đang được gọi với 3 tham số tại 9 chỗ trong `CloudSessionAndMultiHomeTest.kt`, 1 chỗ tại `CloudB3IntegrationTest.kt:191`, và 1 chỗ tại `SmartHomeViewModel.kt:38`.
   - Dòng 310–366: `createHome(name)` chỉ kiểm tra `current.busy`, không chặn tạo thêm nhà khi đã có nhà hợp lệ.
   - Dòng 548: `handleHomesResult` gán `nextSelectedId = validHomes.firstOrNull()?.id`, không ưu tiên `CloudRole.OWNER` và không có tiêu chí chọn tất định.
2. `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`:
   - Dòng 27–54: `CloudHomeBannerFromBridge` và `CloudHomeManagementFromBridge` gọi `CloudHomeBannerCard` và `CloudHomeManagementCard`.
   - Dòng 135–153: `CloudHomeBannerCard` hiển thị 2 nút `"Nhà (${cloudState.homes.size})"` (mở `HomeSwitcherDialog`) và `"Thành viên"`.
   - Dòng 246–445: `CloudHomeManagementCard` hiển thị nút `"Tạo nhà"` trên `SectionTitle`, danh sách tất cả nhà, danh sách `Thành viên`, nút `"Rời khỏi nhà"`, nút xóa thành viên, và ghi chú B4 lỗi thời (dòng 437–444).
   - `CloudB3IntegrationTest.kt:357, 398` gọi trực tiếp `CloudHomeManagementCard(...)` và kiểm thử hành vi OWNER/MEMBER B3.
3. `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt` và `RtdbCloudRepository.kt`:
   - `createHome` tạo 3 node: `homes/{h}/meta` → `homes/{h}/members/{uid}` → `userHomes/{uid}/{h}`. `database.rules.src.json:30` (`MIN-01`) bắt buộc phải giữ `members/{ownerUid}` và `userHomes/{ownerUid}/{homeId}`.

---

## 4. Mục tiêu cụ thể

Hoàn thiện trải nghiệm **Một nhà — Một chủ sở hữu (`singleHomeMode`)** trong `CloudHomeManager` và `CloudHomeSection`:
1. Tự động nhận diện và chọn đúng 1 nhà chính thuộc quyền `OWNER` sau đăng nhập.
2. Chỉ cho phép tạo nhà mới khi tài khoản chưa có nhà `OWNER` hợp lệ (`EMPTY` hoặc `ACCESS_REVOKED`).
3. Ẩn toàn bộ UI chuyển nhiều nhà (`Nhà (N)`, `HomeSwitcherDialog`), danh sách/thao tác thành viên Cloud và ghi chú B4 khi chạy ở chế độ một nhà.
4. Nếu phát hiện tài khoản cũ có >1 nhà `OWNER` trên Cloud: **không xóa** nhà cũ, hiển thị cảnh báo minh bạch kèm bộ chọn nhà chính tạm thời trong phiên. Nếu chỉ có nhà `MEMBER` (0 nhà `OWNER`): không chọn làm nhà chính ở chế độ một nhà và hiển thị thông báo rõ tính năng chia sẻ nhà đang tạm hoãn.
5. Bảo toàn 100% logic & bộ test B3 (`FREEZE`).

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Thêm `singleHomeMode: Boolean = false` vào `CloudHomeManager`, truyền `singleHomeMode = true` từ `SmartHomeViewModel`.
- Thêm thuộc tính suy diễn vào `CloudUiState` (có giá trị mặc định giữ tương thích nguồn) để nhận diện trạng thái nhiều nhà sở hữu hoặc tài khoản chỉ có nhà `MEMBER`.
- Cài đặt quy tắc chọn nhà chính tất định và guard `createHome()` khi `singleHomeMode == true`.
- Thêm `singleHomeMode: Boolean = false` vào `CloudHomeBannerCard` và `CloudHomeManagementCard`, đồng thời đặt mặc định `singleHomeMode: Boolean = true` ở `CloudHomeBannerFromBridge` và `CloudHomeManagementFromBridge`.
- Viết unit test JVM mới `CloudSingleHomeModeTest.kt` (và tùy chọn UI instrumented test mới `CloudSingleOwnerUiTest.kt` nếu viết ở Nhánh A hoặc Nhánh B).

### Ngoài phạm vi
- Không sửa `CloudRepository` / `RtdbCloudRepository` / `RtdbPayloadBuilder` / `database.rules.*`.
- Không tự động xóa bất kỳ node `homes/*`, `members/*`, `userHomes/*` nào của tài khoản nhiều nhà.
- Không sửa chuỗi hay hành vi dialog `"Thành viên"` FaceStore trong `SettingsScreen.kt` (thuộc Prompt `07`).
- Không đồng bộ `_loggedIn` với `CloudSession` (thuộc Prompt `02`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt`
2. `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`
3. `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt` *(chỉ sửa dòng 38 để truyền `singleHomeMode = true` vào `CloudHomeManager`)*

### Thêm mới (`NEW` — Test)
1. `app/src/test/java/com/example/smarthomeai/cloud/CloudSingleHomeModeTest.kt`
2. *(Tùy chọn)* `app/src/androidTest/java/com/example/smarthomeai/cloud/CloudSingleOwnerUiTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbCloudRepository.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbPayloadBuilder.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/RtdbHomeMerger.kt`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`
- `app/src/main/java/com/example/smarthomeai/data/Storage.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`
- Tất cả file test B3/Smoke hiện có: `CloudSessionAndMultiHomeTest.kt`, `FakeCloudRepository.kt`, `CloudB3IntegrationTest.kt`, `AppSmokeTest.kt`, `CloudInitializationIntegrationTest.kt`.
- `cloud-rules/**`, `esp32/**`, `app/build.gradle.kts`, `AndroidManifest.xml`, `app/google-services.json`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Checkpoint `0cea70f` sạch.
- Làm đầu vào bắt buộc cho: **Prompt `02` (F4)**, **Prompt `03` (F2a)**, **Prompt `04` (F2b)** và **Prompt `07` (F5b)**.

---

## 9. Kiến trúc triển khai

1. **`CloudHomeManager(scope, session, repository, singleHomeMode: Boolean = false)`:**
   - Mặc định `false` để toàn bộ 9 lời gọi trong `CloudSessionAndMultiHomeTest.kt` và 1 lời gọi trong `CloudB3IntegrationTest.kt` giữ nguyên hành vi B3.
   - `SmartHomeViewModel.kt:38` khởi tạo: `CloudHomeManager(viewModelScope, cloudComponents.session, cloudComponents.repository, singleHomeMode = true)`.
2. **`CloudUiState` (trong `CloudHomeManager.kt`):**
   - Bổ sung các trường có giá trị mặc định (hoặc property suy diễn trên `homes`) gồm:
     - `ownedHomesCount: Int` (`homes.count { it.role == CloudRole.OWNER }`)
     - `hasMultipleOwnedHomes: Boolean` (`ownedHomesCount > 1`)
     - `ignoredMemberHomesCount: Int = 0` (hoặc suy ra `homes.count { it.role == CloudRole.MEMBER }` khi `singleHomeMode == true`)
   - Lưu ý giữ mọi constructor call cũ của `CloudUiState(...)` trong test hoạt động bình thường nhờ default parameters / computed properties.
3. **UI Flag trên `CloudHomeSection.kt`:**
   - `CloudHomeBannerCard(..., singleHomeMode: Boolean = false)`
   - `CloudHomeManagementCard(..., singleHomeMode: Boolean = false)`
   - `CloudHomeBannerFromBridge(bridge: CloudUiBridge, singleHomeMode: Boolean = true, onOpenManagement: () -> Unit = {})` — giữ trailing lambda `onOpenManagement` hoạt động với `HomeScreen.kt:37` (`CloudHomeBannerFromBridge(cloudBridge) { onNavigate("settings") }`)!
   - `CloudHomeManagementFromBridge(bridge: CloudUiBridge, singleHomeMode: Boolean = true)`

---

## 10. Luồng dữ liệu

```text
CloudSession.observeSession() -> CloudHomeManager.handleHomesResult()
  ├─ [singleHomeMode = false]: giữ nguyên logic B3 (firstOrNull, observeMembers)
  └─ [singleHomeMode = true]:
       ├─ Lọc danh sách ownedHomes = validHomes.filter { it.role == CloudRole.OWNER }
       ├─ Nếu ownedHomes.isNotEmpty():
       │    ├─ Nếu vừa tạo pendingCreatedHomeId hợp lệ -> chọn pendingCreatedHomeId
       │    ├─ Else nếu prevSelectedId thuộc ownedHomes -> giữ prevSelectedId
       │    └─ Else -> chọn ownedHomes.minBy { it.id }.id (nhà OWNER push() cũ nhất)
       │    └─ Không gọi observeMembers() khi không cần hiển thị thành viên (hoặc giữ idle, không phá state)
       └─ Nếu ownedHomes.isEmpty():
            ├─ Nếu validHomes có nhà MEMBER -> selectedHomeId = null, status = EMPTY (kèm cờ/notice tài khoản chỉ có nhà chia sẻ đang tạm hoãn)
            └─ Nếu validHomes rỗng -> giữ EMPTY hoặc ACCESS_REVOKED như B3
```

---

## 11. Logic nghiệp vụ

1. **Tự động chọn nhà chính (`singleHomeMode = true`):**
   - Chỉ chọn từ tập nhà `CloudRole.OWNER`. Tiêu chí tất định khi chưa có lựa chọn hợp lệ trước đó: `ownedHomes.minByOrNull { it.id }` (vì Firebase `push()` ID sắp xếp tăng dần theo thời gian tạo).
   - Nếu `prevSelectedId` là nhà `OWNER` vẫn còn trong `ownedHomes`, giữ nguyên (giúp lựa chọn tạm thời của user trong phiên khi có >1 nhà cũ được bảo toàn).
2. **Bảo vệ `createHome(name)` trong `singleHomeMode = true`:**
   - Từ chối ngay bằng `setFeedback(gen, isError = true, error = CloudError.Conflict, message = "Tài khoản đã có nhà Cloud. Mô hình hiện tại chỉ hỗ trợ một nhà.")` nếu:
     - `current.status == CloudStatus.LOADING` (chưa tải xong danh sách nhà để biết đã có nhà hay chưa), HOẶC
     - `current.homes.any { it.role == CloudRole.OWNER }` (đã tồn tại nhà sở hữu hợp lệ), HOẶC
     - `current.busy == true` (đang thực hiện thao tác khác, chống double-tap).
   - Chỉ cho phép tạo nhà khi `current.status in setOf(CloudStatus.EMPTY, CloudStatus.ACCESS_REVOKED)` và chưa có nhà `OWNER` nào.
3. **Xử lý tài khoản cũ có nhiều nhà (Không phá hủy):**
   - **Không xóa** `homes/*` hay `userHomes/*`.
   - `CloudUiState.homes` vẫn lưu danh sách thực tế để không mất dữ liệu nhận diện, `selectedHomeId` trỏ vào nhà chính.
   - Trên `CloudHomeBannerCard` & `CloudHomeManagementCard` khi `singleHomeMode == true` và `ownedHomes.size > 1`:
     - Hiện banner cảnh báo màu vàng/cam: `"Phát hiện ${ownedHomes.size} nhà Cloud trên tài khoản cũ. Đang dùng nhà chính \"${selectedHome?.name}\". Hệ thống không tự xóa nhà cũ."`
     - Trong `CloudHomeManagementCard` (Cài đặt), cho phép chạm chọn 1 nhà trong nhóm `OWNER` làm nhà hoạt động trong phiên hiện tại (gọi lại `onSelectHome(home.id)`), nhưng **ẩn** nút `"Tạo nhà"` trên `SectionTitle`.
4. **Xử lý tài khoản cũ chỉ có nhà `MEMBER` (0 nhà `OWNER`):**
   - Ở `singleHomeMode = true`: `selectedHomeId = null`. Hiển thị mô tả rõ: `"Tài khoản đang chỉ tham gia nhà được chia sẻ; chế độ chia sẻ nhiều thành viên hiện tạm hoãn. Bạn có thể tạo nhà riêng cho ESP32 của mình."` và cho phép tạo nhà mới nếu chưa có nhà `OWNER`.
5. **Thu gọn UI chế độ một nhà (`singleHomeMode = true`):**
   - `CloudHomeBannerCard`: ẩn nút `"Nhà (...)"`, ẩn `HomeSwitcherDialog`, ẩn nút `"Thành viên"`. Khi `READY`, hiển thị tên nhà chính, pill `"Chủ sở hữu · Đã kết nối Cloud"` (hoặc nhãn tương đương không gây hiểu nhầm đa thành viên), và một nút gọn `"Quản lý nhà"` (`onOpenManagement`) hoặc giữ card gọn nhẹ.
   - `CloudHomeManagementCard`: ẩn nút `"Tạo nhà"` khi đã có nhà `OWNER`; ẩn danh sách thành viên Cloud (`MembersStatus` / `MemberRow`), ẩn nút `"Rời khỏi nhà"`, ẩn ghi chú B4 (`"Mời thành viên mới..."`). Nếu chỉ có 1 nhà `OWNER`, hiển thị thông tin nhà duy nhất (`name`, `Chủ nhà (OWNER)`), nút tải lại khi cần.
   - Khi `singleHomeMode == false`: giữ nguyên **100%** giao diện B3 để `CloudB3IntegrationTest` PASS không đổi 1 assertion nào.

---

## 12. Xử lý lỗi và concurrency

- Giữ nguyên `lifecycleMutex`, `sessionGen`, `cancelAndJoin()`, `onSignedOut()` của `CloudHomeManager`.
- Trong `singleHomeMode = true`, nếu `activeSelectedHomeId` đặt là `null` hoặc không còn cần `observeMembers`, `switchMemberObserver(gen, uid, null)` hủy sạch `membersJob` cũ mà không phát sinh listener thừa.
- Không thêm bất kỳ network call hay coroutine leak nào; nếu `createHome` thành công, `pendingCreatedHomeId` vẫn được tôn trọng để chọn ngay nhà vừa tạo.
- Chống double-tap `createHome` bằng kiểm tra `current.busy` trước khi gọi `launchMutation`.

---

## 13. Bất biến cần bảo toàn

- `REMOVE = NONE`: không xóa `observeMembers`, `leaveHome`, `removeMember` khỏi `CloudRepository`/`CloudHomeManager`/`RtdbCloudRepository`.
- Không sửa `database.rules.src.json` hoặc `database.rules.json`; giữ nguyên `members/{ownerUid}` và `userHomes/{ownerUid}/{homeId}` khi tạo nhà (`MIN-01`).
- Không xóa dữ liệu cũ của tài khoản nhiều nhà.
- Không sửa chữ ký gọi sẵn của `CloudHomeManager(scope, session, repository)`, `CloudHomeManagementCard(...)`, `CloudHomeBannerCard(...)` và `CloudHomeBannerFromBridge(bridge) { ... }` (chỉ thêm tham số mới có giá trị mặc định).
- Không chạm vào `SettingsScreen.kt:66` (`"Thành viên"` cục bộ).

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `SmartHomeViewModel` khởi tạo `CloudHomeManager` với `singleHomeMode = true`.
2. `CloudHomeManager` ở `singleHomeMode = true` tự chọn nhà `OWNER` có `id` nhỏ nhất, chặn `createHome` khi đã có nhà `OWNER` hoặc đang `LOADING`/`busy`, cô lập nhà `MEMBER`, phát hiện `>1` nhà `OWNER` mà không xóa dữ liệu.
3. `CloudHomeSection` ở `singleHomeMode = true` ẩn `Nhà (N)`, `HomeSwitcherDialog`, nút `Thành viên`, danh sách thành viên Cloud, nút rời nhà, ghi chú B4, và ẩn tạo nhà mới khi đã có nhà `OWNER`.
4. Ở `singleHomeMode = false`, 100% hành vi và UI giống B3.
5. `CloudSingleHomeModeTest.kt` phủ đủ: chọn nhà `OWNER` cũ nhất, chặn `createHome` khi đã có nhà, double-tap `createHome`, cảnh báo >1 nhà `OWNER`, xử lý tài khoản chỉ có `MEMBER`, giữ nguyên hành vi B3 khi `singleHomeMode = false`.

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → kỳ vọng toàn bộ `151` test cũ + toàn bộ test mới trong `CloudSingleHomeModeTest` **PASS** (0 failed, 0 skipped).
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. Nếu emulator `emulator-5554` sẵn sàng: `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` → toàn bộ `69` test cũ (gồm `AppSmokeTest` và `CloudB3IntegrationTest`) + test UI Single Owner mới **PASS**.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

Nhánh A bắt buộc chạy các lệnh kiểm tra tĩnh sau và đính kèm kết quả vào Handoff Report:
1. `git diff --stat` → chỉ gồm `CloudHomeManager.kt`, `CloudHomeSection.kt`, `SmartHomeViewModel.kt`, cộng file test mới.
2. Kiểm tra chữ ký tương thích B3: `Select-String -Path app/src/test,app/src/androidTest -Recurse -Pattern 'CloudHomeManager\(|CloudHomeManagementCard\(|CloudHomeBannerCard\('` *(PowerShell dùng `Get-ChildItem ... -Recurse | Select-String`)* để chắc chắn mọi call site cũ vẫn khớp tham số.
3. `Select-String -Path app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt,app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt -Pattern 'com\.google\.firebase'` → **0 matches**.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Đóng gap F1 (Single Home / Single Owner) trên CloudHomeManager, CloudHomeSection và SmartHomeViewModel.
EVIDENCE: <liệt kê file:dòng đã kiểm tra và static grep output>
CHANGES: <liệt kê ngắn từng file đã sửa/thêm trong phạm vi>
VERIFICATION: NOT RUN BY BRANCH A (tuân thủ quy tắc 2 model: chỉ chạy static checks, chưa chạy Gradle/Unit/Instrumented/Emulator).
SCOPE: OK
RISKS: None | <nếu có>
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, thông báo rõ cho người dùng: **"Nhánh A hoàn tất và đã commit. Vui lòng chuyển sang Model Kiểm thử độc lập (Nhánh B) để kiểm tra và chạy test cho Prompt 01."**

---

## 17. Rollback

- Để hoàn tác sạch thay đổi của Prompt 01: `git revert <commit_hash_prompt_01>` (không `reset --hard`, không sửa lịch sử).
- Không có migration schema/RTDB nào được tạo, dữ liệu tài khoản nhiều nhà vẫn nguyên vẹn.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng các file thuộc mục 6.
2. Stage theo danh sách tường minh:
   `git add app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt app/src/test/java/com/example/smarthomeai/cloud/CloudSingleHomeModeTest.kt` *(kèm `CloudSingleOwnerUiTest.kt` nếu có)*.
3. Kiểm tra `git diff --cached --stat`.
4. Commit với thông điệp tiếng Việt 100%:
   `git commit -m "Tích hợp quản lý một nhà cho chủ sở hữu"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng (không tự ý sửa ngoài phạm vi) nếu gặp một trong các điều kiện sau:
- HEAD không phải `0cea70f3cd83f69568ab35a00af013930544450f` hoặc working tree không sạch.
- Việc ẩn UI chế độ một nhà buộc phải thay đổi tham số không có mặc định hoặc làm thay đổi bất kỳ assertion nào trong `CloudB3IntegrationTest.kt` hay `AppSmokeTest.kt` (`CD-4`).
- Phát hiện yêu cầu phải sửa `CloudRepository.kt`, `RtdbCloudRepository.kt`, `database.rules.*` hoặc xóa dữ liệu trên RTDB.
