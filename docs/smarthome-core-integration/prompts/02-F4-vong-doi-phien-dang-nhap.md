# PROMPT 02 — F4: HOÀN THIỆN ĐỒNG BỘ VÒNG ĐỜI PHIÊN ĐĂNG NHẬP (AUTH & SESSION LIFECYCLE)

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Engineer kết nối trạng thái `CloudSession` / `CloudHomeManager` vào `SmartHomeViewModel` và giao diện yêu cầu đăng nhập lại trên `CloudHomeSection`/`HomeScreen`, bảo toàn tuyệt đối LAN-first và `MainActivity.kt` (`CD-3`).
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Test Engineer đọc diff + handoff của Nhánh A, kiểm tra concurrency/session/LAN-first và chạy bộ kiểm thử JVM + Instrumented + Build.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (khớp commit Nhánh A/B cuối cùng của Prompt 01) và `git status --porcelain` sạch.
2. Đọc lại `SmartHomeViewModel.kt`, `CloudHomeManager.kt`, `FirebaseProvider.kt`, `AuthRepository.kt`, `MainActivity.kt`, `HomeScreen.kt`, `CloudHomeSection.kt`.
3. Đối chiếu yêu cầu mục 4–13 với source thực tế.
4. Hoàn thiện phương án kỹ thuật mục 9–12.
5. Triển khai toàn bộ phạm vi mục 5–6 (kèm viết `CloudSessionLifecycleTest.kt`, **không chạy**).
6. Không mở rộng phạm vi sang `MainActivity.kt` hay `AuthRepository.kt`.
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
3. Kiểm tra code thực tế đối chiếu mục 13–14.
4. Viết bổ sung test (nếu cần) và chạy kiểm thử theo lệnh mục 14.
5. **KHÔNG** sửa implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện và `file:dòng` chính xác.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `01` (`F1`) đã có commit của Nhánh A và báo cáo **`PASS`** từ Nhánh B.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `01` (xác minh bằng `git log -n 3 --oneline` trước khi sửa).
- **Working tree:** Sạch (`git status --porcelain` rỗng).

---

## 3. Trạng thái hiện tại đã xác minh

1. `SmartHomeViewModel.kt:35-39`:
   - `_loggedIn = MutableStateFlow(auth.isSignedIn())` chỉ đọc 1 lần khi tạo ViewModel.
   - Dòng 54–59: Vòng lặp polling LAN kiểm tra `if (_loggedIn.value) network.withLock { if (_loggedIn.value) refreshLocked() }`. Nếu gán `_loggedIn.value = false` sẽ **tắt hoàn toàn LAN polling và điều khiển thiết bị** (dòng 161, 185, 211, 233, 251).
   - Dòng 84–87: `finishLogin(email)` cập nhật `userEmail`, `_loggedIn.value = true`, `refresh()`; `logout()` gọi `verifyLiveFace(null)`, `cloudManager.onSignedOut()`, `auth.signOut()`, `_loggedIn.value = false`, xóa `userEmail`/`esp32Online`.
2. `CloudHomeManager.kt`:
   - `session.observeSession()` (từ `FirebaseCloudSession` qua `AuthStateListener`) đã xử lý `Unavailable`, `SignedOut`, `SignedIn(uid)` với `lifecycleMutex` + `sessionGen` + `cancelAndJoin()`.
   - Khi mất mạng WAN nhưng FirebaseAuth còn cached `currentUser`, `FirebaseCloudSession` giữ `CloudSessionState.SignedIn(uid)` (không chuyển `SignedOut`).
   - Khi token/session bị thu hồi bên ngoài hoặc `auth` đăng xuất từ nơi khác, `CloudHomeManager` chuyển `CloudStatus.SIGNED_OUT`, nhưng `SmartHomeViewModel` không biết để nhắc người dùng đăng nhập lại hoặc cập nhật `userEmail` khi đổi tài khoản.
3. `MainActivity.kt` (`LOCKED` theo `CD-3`):
   - `NavHost` có route `"login"` (dòng 77–82) và `"home"` (dòng 83–85); `HomeScreen` nhận `onNavigate: (String) -> Unit`.
   - `AuthRepository.currentEmail` đọc email/displayName của user Firebase hiện tại (`AuthRepository.kt:19`).

---

## 4. Mục tiêu cụ thể

Đồng bộ vòng đời phiên Auth giữa `CloudSession` (`cloudManager.state`) và `SmartHomeViewModel` để:
1. Phát hiện chính xác trường hợp phiên Cloud bị thu hồi / hết hạn bên ngoài (`SignedOut` trong khi `_loggedIn.value && auth.configured`) và hiển thị thông báo + nút `"Đăng nhập lại"` đưa người dùng tới màn hình `"login"` — **tuyệt đối không tắt `_loggedIn`** để bảo toàn điều khiển LAN.
2. Cập nhật `userEmail` khi phiên chuyển sang UID mới hợp lệ (`SignedIn`).
3. Không bao giờ phát sinh yêu cầu đăng nhập lại khi chỉ mất kết nối Internet (vẫn còn cached Firebase user) hoặc khi chạy chế độ demo không có `google-services.json` (`Unavailable`).
4. Giữ nguyên `MainActivity.kt` và `AuthRepository.kt`.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Tách hàm thuần đánh giá chuyển đổi phiên (ví dụ `SessionSyncEvaluator` hoặc helper trong `cloud/` / `data/`) để kiểm thử được 100% logic bằng JVM unit test mà không cần khởi tạo `AndroidViewModel(Application)`.
- Quan sát `cloudManager.state` trong `SmartHomeViewModel.init`:
  - Theo dõi `CloudStatus` và `currentUid`.
  - Khi `auth.configured && _loggedIn.value && cloudState.status == CloudStatus.SIGNED_OUT` → bật `reauthRequired = true` (hoặc hiển thị trực tiếp thông qua `CloudUiState.reauthRequired: Boolean = false` / state trong `CloudHomeManager` / `SmartHomeViewModel`).
  - Khi `cloudState.status == CloudStatus.READY` hoặc `CloudStatus.EMPTY` hoặc `CloudStatus.LOADING` (có `currentUid != null`): nếu UID mới khác UID trước đó hoặc vừa đăng nhập lại → cập nhật `_state.update { it.copy(userEmail = auth.currentEmail ?: it.userEmail) }` và xóa `reauthRequired`.
  - Khi `cloudState.status == CloudStatus.UNAVAILABLE` → giữ nguyên (hỗ trợ demo mode khi `!auth.configured`).
- Trên `CloudHomeSection.kt`: thêm tham số tùy chọn `onReauth: (() -> Unit)? = null` vào cuối `CloudHomeBannerCard` và `CloudHomeBannerFromBridge` (giữ tương thích trailing lambda / existing tests); khi `cloudState.status == CloudStatus.SIGNED_OUT` và `onReauth != null`, hiển thị nút `"Đăng nhập lại"`.
- Trên `HomeScreen.kt`: truyền `onReauth = { onNavigate("login") }` vào `CloudHomeBannerFromBridge`.

### Ngoài phạm vi
- Không sửa `MainActivity.kt` để tự động pop `NavHost` (`CD-3`).
- Không sửa `AuthRepository.kt` hoặc `LoginScreen.kt`.
- Không tách dữ liệu `Storage.kt` theo UID.
- Không implement `CloudGatewaySync` (thuộc Prompt `04`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`
2. `app/src/main/java/com/example/smarthomeai/cloud/CloudHomeManager.kt` *(chỉ nếu đặt field `reauthRequired: Boolean = false` trong `CloudUiState` hoặc helper kiểm soát đồng bộ phiên)*
3. `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`
4. `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt` *(chỉ dòng gọi `CloudHomeBannerFromBridge` truyền `onReauth = { onNavigate("login") }`)*

### Thêm mới (`NEW` — Test)
1. `app/src/test/java/com/example/smarthomeai/cloud/CloudSessionLifecycleTest.kt` *(kiểm thử logic thuần `SessionSync` + `CloudHomeManager`, không khởi tạo `SmartHomeViewModel` vì cần `Application`)*

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/MainActivity.kt` (`CD-3`)
- `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/LoginScreen.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`, `Storage.kt`, `Models.kt`, `Security.kt`, `HardwareStatus.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`, `RtdbCloudRepository.kt`, `FirebaseProvider.kt`
- Tất cả file test B3/Smoke hiện có.
- `cloud-rules/**`, `esp32/**`, `app/build.gradle.kts`, `AndroidManifest.xml`, `app/google-services.json`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `01` (`F1`) đã hoàn tất và PASS.
- Làm đầu vào bắt buộc cho: **Prompt `04` (F2b)** và **Prompt `07` (F5b)**.

---

## 9. Kiến trúc triển khai

1. **Tách Pure Evaluator để kiểm thử triệt để:**
   - Tạo lớp/hàm thuần `CloudSessionSyncDecision` (trong `CloudHomeManager.kt` hoặc nội bộ `cloud/`): nhận `(firebaseConfigured: Boolean, localLoggedIn: Boolean, prevObservedUid: String?, cloudStatus: CloudStatus, cloudUid: String?)` → trả về `(shouldRequireReauth: Boolean, shouldRefreshUserEmail: Boolean, nextObservedUid: String?)`.
   - `SmartHomeViewModel` khởi chạy 1 coroutine `viewModelScope.launch` thu thập `cloudManager.state` (dùng `.map { it.status to it.currentUid }.distinctUntilChanged()`).
2. **Bảo toàn hành vi `logout()` chủ động:**
   - Khi người dùng bấm `"Đăng xuất"` trong Cài đặt, `SmartHomeViewModel.logout()` set `_loggedIn.value = false` và gọi `cloudManager.onSignedOut()`. Vì `_loggedIn.value` đã là `false`, `shouldRequireReauth` phải là `false` (không bật nhầm cờ reauth sau logout chủ động).
3. **Nút `"Đăng nhập lại"` trên UI:**
   - Trong `CloudHomeBannerCard(..., singleHomeMode: Boolean = false, onReauth: (() -> Unit)? = null)`: khi `cloudState.status == CloudStatus.SIGNED_OUT && onReauth != null`, hiển thị nút `Button`/`OutlinedButton` `"Đăng nhập lại"`.
   - Trong `CloudHomeBannerFromBridge(bridge: CloudUiBridge, singleHomeMode: Boolean = true, onReauth: (() -> Unit)? = null, onOpenManagement: () -> Unit = {})` giữ `onOpenManagement` làm trailing lambda để `HomeScreen` tương thích mượt mà.

---

## 10. Luồng dữ liệu

```text
FirebaseAuth.AuthStateListener
  -> FirebaseCloudSession.observeSession() [SignedIn(uid) | SignedOut | Unavailable]
  -> CloudHomeManager.applySessionState() [cancelAndJoin() job cũ theo sessionGen]
  -> CloudHomeManager.state (status, currentUid)
  -> SmartHomeViewModel collector (distinctUntilChanged on status + currentUid):
       ├─ [SignedOut && _loggedIn && auth.configured]:
       │     _loggedIn GIỮ NGUYÊN true (LAN không tắt) + đánh dấu cần đăng nhập lại
       ├─ [SignedIn (LOADING/READY/EMPTY) && uid != prevUid]:
       │     cập nhật _state.userEmail = auth.currentEmail ?: _state.userEmail, tắt reauth
       ├─ [Unavailable] (chưa cấu hình / demo):
       │     không can thiệp _loggedIn hay reauth
       └─ [!_loggedIn] (vừa logout chủ động):
             tắt reauth
```

---

## 11. Logic nghiệp vụ

1. **Mất Internet (WAN offline) nhưng còn phiên Firebase:** `FirebaseAuth.currentUser` vẫn khác `null` → `CloudSession` vẫn là `SignedIn(uid)` → `CloudHomeManager.state` không chuyển `SIGNED_OUT` (có thể là `ERROR` với `CloudError.Offline` khi tải nhà). Collector **không** đặt `reauthRequired` và **không** tắt `_loggedIn`.
2. **Thu hồi phiên / hết hạn token / đăng xuất từ bên ngoài:** `FirebaseAuth.currentUser` thành `null` (hoặc `RtdbCloudRepository` trả `CloudError.Unauthenticated` khiến `CloudHomeManager` chuyển `CloudStatus.SIGNED_OUT`) → `SmartHomeViewModel` giữ `_loggedIn = true` cho LAN hoạt động bình thường, nhưng UI hiển thị rõ `"Phiên đăng nhập Cloud đã kết thúc. Điều khiển LAN vẫn hoạt động."` kèm nút `"Đăng nhập lại"` gọi `onNavigate("login")`.
3. **Đổi tài khoản sau khi nhấn `"Đăng nhập lại"`:**
   - `LoginScreen` gọi `vm.login(...)` → `AuthRepository` đăng nhập UID mới `uB` → `FirebaseCloudSession` phát `SignedIn("uB")` → `CloudHomeManager` `cancelAndJoin()` toàn bộ luồng `uA`, xóa sạch `CloudUiState` cũ, tải nhà của `uB`; `SmartHomeViewModel` cập nhật `userEmail` mới của `uB`.
   - *(Ghi nhận giới hạn `CD-3` đã biết: vì `MainActivity.kt` LOCKED nên khi `LoginScreen` thành công gọi `nav.navigate("home")`, back stack vẫn giữ lại màn hình `"home"` trước đó. Không sửa `MainActivity.kt`.)*
4. **Phát hiện trạng thái ban đầu khi khởi động app (cold start):** nếu lúc mở app user đã đăng nhập sẵn (`auth.isSignedIn() == true` → `_loggedIn = true`), `prevObservedUid` bắt đầu bằng UID hiện có (hoặc `null`) và đồng bộ khi `CloudHomeManager` nhận `SignedIn(uid)`. Tránh Race lúc mới khởi động khi `CloudHomeManager` khởi tạo ở `CloudStatus.LOADING` (`currentUid == null`): chỉ coi là mất phiên khi `cloudStatus == CloudStatus.SIGNED_OUT` (không nhầm `LOADING` với `SIGNED_OUT`).

---

## 12. Xử lý lỗi và concurrency

- Không đặt bất kỳ lời gọi `auth` hay `cloud` nào vào `SmartHomeViewModel.network` Mutex.
- `SmartHomeViewModel` thu thập `cloudManager.state` trên `viewModelScope` (Dispatcher mặc định/Main hoặc `Dispatchers.Default`); cập nhật `_state` bằng `.update { ... }` nguyên tử.
- Không tự `cancel` hoặc thay đổi `CloudHomeManager.sessionGen` từ `SmartHomeViewModel` — toàn bộ `sessionGen`/`lifecycleMutex`/`cancelAndJoin()` vẫn do `CloudHomeManager` làm chủ.
- Không nuốt `CancellationException`.

---

## 13. Bất biến cần bảo toàn

- **LAN-first bất biến:** Không bao giờ gán `_loggedIn.value = false` từ `CloudSession` listener khi chỉ thu hồi phiên Cloud hoặc mất WAN.
- **Giữ `MainActivity.kt` & `AuthRepository.kt` nguyên vẹn (`CD-3`).**
- **Không phá signature `HomeScreen(...)`** mà `AppSmokeTest.kt` gọi (không thêm tham số bắt buộc hoặc không cần thêm tham số trên `HomeScreen` trong prompt này).
- **Không nhập `com.google.firebase.*` vào `SmartHomeViewModel.kt`, `HomeScreen.kt`, `CloudHomeSection.kt`.**

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. Khi `CloudStatus.SIGNED_OUT` xảy ra lúc `_loggedIn == true && auth.configured == true`, cờ reauth bật lên và giữ nguyên `_loggedIn == true` (vòng LAN polling không dừng).
2. Khi `CloudStatus.ERROR (Offline)` (mất WAN còn cached user) hoặc `CloudStatus.UNAVAILABLE` (demo mode), không bật reauth và không tắt LAN.
3. Khi chuyển sang `SignedIn(uidB)` mới, `userEmail` cập nhật theo tài khoản mới và cờ reauth tắt.
4. Khi gọi `logout()` chủ động, không bật cảnh báo reauth trên màn hình sau khi thoát.
5. `HomeScreen` + `CloudHomeSection` hiển thị nút `"Đăng nhập lại"` khi `SIGNED_OUT` và điều hướng `"login"`.
6. `CloudSessionLifecycleTest.kt` phủ đầy đủ cả 4 kịch bản (Mất WAN giữ phiên, Thu hồi phiên giữ LAN + bật reauth, Đổi UID cập nhật email & hủy state cũ, Demo `Unavailable` không ảnh hưởng).

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → `151` test nền + test `01` + `CloudSessionLifecycleTest` **PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. Nếu có AVD: `cd cloud-rules; node scripts/run-emulator.mjs "..\gradlew.bat -p .. :app:connectedDebugAndroidTest --console=plain"` → toàn bộ `69`+ Instrumented tests **PASS**.

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chạm đúng 4 file được phép sửa ở mục 6 + `CloudSessionLifecycleTest.kt`.
2. Grep xác nhận `MainActivity.kt` và `AuthRepository.kt` 0 dòng thay đổi: `git diff -- app/src/main/java/com/example/smarthomeai/MainActivity.kt app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt` (rỗng).
3. Grep xác nhận `_loggedIn.value = false` trong `SmartHomeViewModel.kt` **chỉ** xuất hiện trong `logout()`.
4. Grep `com.google.firebase` trong `SmartHomeViewModel.kt`, `HomeScreen.kt`, `CloudHomeSection.kt`, `CloudHomeManager.kt` → **0 matches**.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Đồng bộ SmartHomeViewModel với CloudSessionState mà không tắt LAN polling và không sửa MainActivity.kt (F4).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (tuân thủ quy tắc 2 model).
SCOPE: OK
RISKS: Giới hạn chấp nhận theo CD-3 (back stack khi re-auth qua LoginScreen do MainActivity.kt LOCKED).
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, dừng hoàn toàn và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- Hoàn tác an toàn bằng `git revert <commit_hash_prompt_02>`.
- Không tác động dữ liệu `Storage` hoặc RTDB.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. Stage tường minh các file đã sửa/thêm trong mục 6.
3. `git diff --cached --stat` xác nhận đúng phạm vi.
4. Commit tiếng Việt:
   `git commit -m "Hoàn thiện vòng đời phiên đăng nhập"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Cần sửa `MainActivity.kt` hoặc `AuthRepository.kt` mới điều hướng hoặc refresh token được (`CD-3`).
- Phát hiện cách sửa làm ngắt vòng polling LAN (`_loggedIn`) khi mất WAN hoặc thu hồi phiên.
- Phá vỡ chữ ký `HomeScreen(...)` khiến `AppSmokeTest.kt` lỗi biên dịch.
