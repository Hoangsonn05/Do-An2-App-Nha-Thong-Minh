# KẾ HOẠCH AUDIT TỔNG THỂ SAU TÍCH HỢP (POST-INTEGRATION AUDIT PLAN)

- **Điều kiện kích hoạt:** **CHỈ BẮT ĐẦU** sau khi toàn bộ 7 prompt triển khai (`01` → `07`) cho `F1`–`F5` đã hoàn tất Nhánh A, vượt qua Nhánh B (`PASS`) và đã có commit sạch trên nhánh `master`.
- **Nguyên tắc:** Đây là kế hoạch kiểm tra toàn hệ thống theo chiều sâu (READ-ONLY Audit trước, phân cụm lỗi sau). **Không** tự ý vá rải rác trong lúc Audit; **không** truy cập Production `appaintm` (`B1-D` vẫn `LOCKED` trừ khi có bước phê duyệt riêng); **không** `git push`.

---

## 1. Phạm vi 12 Trục Audit Bắt buộc (A – L)

| Trục | Tên trục Audit | Trọng tâm kiểm tra thực tế trên Source + Runtime Emulator |
|---|---|---|
| **A** | **Architecture Integration Audit** | Kiểm tra ranh giới layer (`ui/` ↔ `data/` ↔ `cloud/` ↔ Firebase SDK): 0 import `com.google.firebase` trong `SmartHomeViewModel.kt`, UI screens/components; 0 Firestore/Storage/Functions; các bridge `CloudUiBridgeRegistry` / `CloudDeviceUiBridgeRegistry` đăng ký và hủy đăng ký đúng chuẩn; không vi phạm `REMOVE = NONE`. |
| **B** | **Firebase RTDB Schema & Security Rules Compatibility** | Đối chiếu toàn bộ payload thực tế sinh bởi `CloudMapper` và `RtdbDeviceRepository` với `cloud-rules/database.rules.src.json` thông qua Local RTDB Emulator (`--project demo-smarthomeai`): xác nhận `devices/$d`, `state/gateway/values` (đúng 8 khóa), `config` (tăng `rev`), `telemetry/{yyyyMMddHHmm}_g` (UTC), `events/g_{cloudStream}_{seq}` (SHA-256 16 hex), cô lập UID lạ bị `PERMISSION_DENIED`. |
| **C** | **Authentication & Session Lifecycle** | Kiểm tra `SmartHomeViewModel` + `CloudHomeManager` + `FirebaseCloudSession`: thu hồi phiên bên ngoài (`reauthRequired` bật, LAN vẫn chạy); mất WAN khi còn cached user (không ép đăng xuất); chuyển UID (hủy toàn bộ job sync/fallback cũ, cập nhật `userEmail`); chế độ demo khi thiếu `google-services.json`. |
| **D** | **Gateway Registration & Cloud Sync** | Kiểm tra quy trình `resolveOrRegisterDevice` (chọn `active` cũ nhất, `BLOCKED` khi tất cả `revoked`, cảnh báo khi >1 `active`, 0 `deviceKey`/IP/MAC), tần suất ghi `state/gateway` (debounce 2s / heartbeat 60s / offline 1 lần), đồng bộ `config` khi thay đổi ngưỡng, `telemetry` 1 mẫu/phút. |
| **E** | **Cloud Fallback & LAN-first Isolation** | Kiểm tra `CloudFallbackObserver` & `DataSourceResolver`: debounce 5s khi mất LAN, hủy ngay lập tức khi `esp32Online == true`, `LAN` luôn thắng `CLOUD`/`LOCAL_CACHE`, Cloud timeout/offline không bao giờ chạm `network: Mutex` hay làm trễ thao tác thiết bị/cửa/giàn phơi, không bao giờ mở khóa nút điều khiển hoặc ghi ngược xuống ESP32. |
| **F** | **Data Consistency & Event Idempotency** | Kiểm tra xử lý create-only trên `events` & `telemetry` (gặp `PermissionDenied` kiểm tra tồn tại không kẹt cursor), thứ tự `seq` tăng dần, giới hạn batch 20 event/chu kỳ, nhận diện dữ liệu cũ (`> 5 phút` hoặc `gateway.online == false`), ghi nhận lệch đồng hồ `receivedAt` vs `lastSeen`. |
| **G** | **Android Lifecycle & Coroutine Safety** | Kiểm tra mọi `CoroutineScope` gắn `viewModelScope`, `onCleared()` gọi `clear()` trên cả `CloudHomeManager`, `CloudGatewaySync`, `CloudFallbackObserver`, `callbackFlow` có `awaitClose { removeEventListener }`, không nuốt `CancellationException` ngoài `TimeoutCancellationException`, `lifecycleMutex` + `sessionGen` không deadlock. |
| **H** | **UI/UX Integration** | Kiểm tra `HomeScreen`, `CloudHomeSection`, `SettingsScreen`, `SystemScreen`: trạng thái Cloud/Gateway/nguồn dữ liệu/thời điểm cập nhật rõ ràng; ẩn toàn bộ Multi-Home switcher & Cloud member management ở runtime nhưng vẫn giữ tương thích test B3; nút `"Đăng nhập lại"` điều hướng hợp lệ; mục `"Thành viên"` ở `SettingsScreen` giữ nguyên FaceStore kèm nhãn phân biệt; text `"Về ứng dụng"` chính xác. |
| **I** | **Legacy Feature Regression** | Chạy lại toàn bộ 88 legacy JVM tests, 54 `AppSmokeTest`, `CloudInitializationIntegrationTest`, `CloudB3IntegrationTest`, 80+ `rules.test.mjs`, kiểm tra SHA-256 của toàn bộ file `KEEP`/`LOCKED` (`MainActivity.kt`, `AuthRepository.kt`, `Esp32Client.kt`, `Storage.kt`, `SmartHomeESP32.ino`, `database.rules.*`). |
| **J** | **Security & Sensitive Data** | Grep toàn bộ `app/src` và logcat/payload: 0 `deviceKey`, 0 `password`, 0 `idToken`, 0 vector/ảnh khuôn mặt đẩy lên RTDB hoặc ghi log plaintext; `AndroidManifest.xml` giữ `allowBackup="false"`; không có URL `appaintm` hardcode trong source. |
| **K** | **Memory / Performance / Resource Management** | Kiểm tra rò rỉ `Activity`/`Context` (`WeakReference` trong registry, `applicationContext` trong `FirebaseProvider`), kích thước query đọc (`limitToLast(100)` khi khởi tạo cursor, `limitToLast(20)` fallback events, `limitToLast(1)` telemetry), ước tính lưu lượng ghi RTDB (~1.440 mẫu telemetry/ngày + state debounce), chưa triển khai retention tự động. |
| **L** | **Production Isolation** | Xác nhận 100% kiểm thử chỉ chạy trên `demo-smarthomeai` (`127.0.0.1:9000` / `10.0.2.2:9000`), không có `.firebaserc`, không truy cập Firebase Console hoặc `appaintm`, `B1-D` vẫn `LOCKED`, `B1-A2` vẫn `PENDING USER VERIFICATION` cho tới khi người dùng chạy kiểm thử phần cứng C1. |

---

## 2. Quy trình thực hiện Audit & Phân loại phát hiện

1. **Thực thi (Read-Only):**
   - Kiểm tra `git log` qua cả 7 prompt, kiểm tra SHA-256 toàn bộ file `LOCKED`.
   - Chạy full suite xác minh thực tế trên Local Emulator & AVD (`test:build`, `test:rules` + test mới, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:connectedDebugAndroidTest`).
2. **Phân loại bắt buộc cho mọi phát hiện (không đánh đồng):**
   - **Lỗi có thật (`BUG`):** Hành vi sai hoặc vi phạm bất biến có bằng chứng tái hiện từ code/test/log, kèm mức độ `CRITICAL / HIGH / MEDIUM / LOW`.
   - **Rủi ro tiềm ẩn (`RISK`):** Tình huống biên (ví dụ lệch đồng hồ lớn giữa điện thoại và server, tốc độ tăng trưởng `telemetry`/`events`, 2 máy cùng đăng ký device lần đầu).
   - **Thiếu bằng chứng (`UNVERIFIED`):** Hạng mục chưa chứng minh được do giới hạn môi trường emulator (ví dụ hành vi Auth thật với Google Sign-In live trên `appaintm`, mạng Wi-Fi chuyển sang 4G ngoài đời thực).
   - **Giới hạn phần cứng / bất biến (`LIMITATION`):** Ràng buộc cố định từ `CD-2` (không có trường mưa/giàn phơi trên `state/gateway`), `CD-3` (back stack khi re-auth do `MainActivity.kt` khóa), `Storage.kt` không tách UID, `deviceId` không gắn chặt phần cứng ESP32 vật lý.
   - **Quyết định cần người dùng phê duyệt (`USER_DECISION_REQUIRED`):** Ví dụ mở khóa `B1-D` cho Production Audit & Backup `appaintm`, lên lịch test phần cứng `C1` (`B1-A2`), xử lý tài khoản cũ nhiều nhà nếu phát hiện khi audit Production, chính sách retention dọn dữ liệu RTDB, hoặc cấu hình `C4` Release Signing.
3. **Xuất báo cáo Audit:** Lưu tại `docs/smarthome-core-integration/POST-INTEGRATION-AUDIT-REPORT.md` (không sửa code trong bước Audit).

---

## 3. Quy trình sửa lỗi theo cụm sau Audit (Clustered Remediation)

Nếu báo cáo Audit có **Lỗi có thật** cần vá trước khi nghiệm thu phần cứng `C1` / Production `C3`:
1. **Nhóm theo nguyên nhân gốc (Root-Cause Clustering):** Gộp các lỗi có cùng gốc (ví dụ cùng lỗi concurrency ở `CloudGatewaySync`, cùng lỗi mapping ở `CloudMapper`, cùng lỗi hiển thị ở `HomeScreen`) thành **tối đa 1–3 prompt sửa lỗi theo cụm** thay vì vá lặt vặt từng dòng ở nhiều commit rời rạc.
2. **Mỗi prompt sửa lỗi theo cụm tuân thủ cấu trúc 2 nhánh (Nhánh A vá tối thiểu + commit tiếng Việt; Nhánh B tái kiểm thử toàn bộ cụm + hồi quy liên quan).**
3. **Tuyệt đối không `git push`**; giữ nguyên trạng thái chờ người dùng nghiệm thu cuối.
