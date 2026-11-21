# PROMPT 05 — F3: BỔ SUNG DỮ LIỆU CLOUD DỰ PHÒNG KHI MẤT LAN (CLOUD DATA FALLBACK)

> **Quy trình bắt buộc trong cùng cuộc hội thoại:** Chạy tuần tự **NHÁNH A — MODEL TRIỂN KHAI** → dừng sau commit và báo người dùng chuyển sang **NHÁNH B — MODEL KIỂM THỬ ĐỘC LẬP**. Không gộp hai nhánh, không bắt người dùng gửi lại prompt.

---

## 1. Vai trò và nhiệm vụ

- **NHÁNH A (Model Triển Khai):** Là Senior Android Engineer triển khai lớp đọc dữ liệu Cloud dự phòng (`observeGatewayState`, `observeLatestTelemetry`, `observeRecentEvents`, `findActiveDevice`), `CloudFallbackObserver`, hàm thuần `DataSourceResolver`, và nối vào `CloudDeviceUiBridge.cloudSnapshot` trong `SmartHomeViewModel`.
- **NHÁNH B (Model Kiểm Thử Độc Lập):** Là Independent QA / Test Engineer đọc diff + handoff Nhánh A, kiểm tra nguyên tắc LAN-wins / chỉ đọc / hủy listener khi LAN phục hồi, và thực thi toàn bộ kiểm thử JVM + Build + Emulator.

### Quy trình bắt buộc theo từng nhánh

#### NHÁNH A — MODEL TRIỂN KHAI (15 bước)
1. Kiểm tra `git rev-parse HEAD` (commit kết thúc của Prompt `04`) và `git status --porcelain` sạch.
2. Đọc lại `CloudDeviceModels.kt`, `CloudDeviceRepository.kt`, `RtdbDeviceRepository.kt`, `CloudDeviceUiBridge.kt`, `SmartHomeViewModel.kt`, `database.rules.src.json` (dòng 115–272).
3. Đối chiếu yêu cầu mục 4–13 với source thực tế.
4. Hoàn thiện phương án kỹ thuật mục 9–12.
5. Triển khai phạm vi mục 5–6 (kèm `DataSourceResolverTest.kt`, `CloudFallbackObserverTest.kt`, **không chạy**).
6. Không sửa UI screens (`HomeScreen.kt` thuộc Prompt `06`) và không ghi Cloud vào `SmartHomeState`.
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
3. Kiểm tra logic `DataSourceResolver` (LAN luôn thắng, nhận diện stale), vòng đời `callbackFlow` + `awaitClose` và debounce 5s.
4. Chạy `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` (và kiểm tra AVD/Emulator nếu sẵn sàng). Bổ sung test nếu cần (không sửa implementation).
5. **KHÔNG** sửa code implementation trong `app/src/main/`.
6. **KHÔNG** tự vá lỗi phát hiện được.
7. Phân loại lỗi `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`.
8. Cung cấp bằng chứng tái hiện và `file:dòng`.
9. Xuất báo cáo kiểm thử độc lập.
10. Nếu phát hiện lỗi cần sửa: **DỪNG** và yêu cầu prompt sửa lỗi riêng. Không tạo commit sửa implementation trong Nhánh B.

---

## 2. Checkpoint đầu vào

- **Điều kiện tiên quyết:** Prompt `03` (`F2a`) và Prompt `04` (`F2b`) đã hoàn tất và PASS.
- **Branch:** `master`
- **HEAD bắt buộc:** Commit kết thúc của Prompt `04`.
- **Working tree:** Sạch.

---

## 3. Trạng thái hiện tại đã xác minh

1. Prompt `03` & `04` đã có `RtdbDeviceRepository`, `CloudDeviceUiBridge`, `CloudGatewaySync`, và dữ liệu ghi tại `homes/{h}/devices/{d}`, `state/gateway`, `telemetry/{yyyyMMddHHmm}_g`, `events/g_{stream}_{seq}`.
2. `SmartHomeViewModel.kt:106`: khi LAN mất kết nối, `esp32Online` trở thành `false` nhưng dữ liệu cũ trong `SmartHomeState` + `lastSeen` (Local Cache từ `Storage`) vẫn giữ nguyên.
3. `database.rules.src.json`:
   - `devices`: `@MEMBER` được đọc (dòng 81).
   - `state/gateway`: `@MEMBER` được đọc (dòng 116).
   - `telemetry`: `@MEMBER` được đọc (dòng 196), sắp theo key `yyyyMMddHHmm_g` nên `limitToLast(1)` trả bản ghi mới nhất theo thứ tự khóa từ điển UTC.
   - `events`: `@MEMBER` được đọc (dòng 256), có `.indexOn: ["receivedAt"]` (dòng 257) nên `orderByChild("receivedAt").limitToLast(20)` hợp lệ.

---

## 4. Mục tiêu cụ thể

Cung cấp dữ liệu Cloud gần nhất khi điện thoại không kết nối được ESP32 qua LAN, sẵn sàng cho UI Hybrid View (Prompt `06`):
1. Bổ sung các phương thức **chỉ đọc / quan sát** trên `CloudDeviceRepository` và `RtdbDeviceRepository`: `findActiveDevice` (không tạo mới khi chỉ xem fallback), `observeGatewayState`, `observeLatestTelemetry`, `observeRecentEvents`.
2. Xây dựng `CloudFallbackObserver` chỉ bật listener khi `!esp32Online` (sau debounce `5_000L` ms) và Cloud ở trạng thái `READY`, tự động hủy toàn bộ listener khi LAN online lại hoặc phiên thay đổi.
3. Xây dựng hàm thuần `DataSourceResolver` phân biệt rõ 4 nguồn: `LAN`, `CLOUD`, `LOCAL_CACHE`, `NONE`, kèm xác định trạng thái dữ liệu cũ (`isStale`) và metadata `receivedAt`/`deviceTs`.
4. Xuất `cloudSnapshot: StateFlow<CloudDeviceSnapshot?>` qua `CloudDeviceUiBridge` (có giá trị mặc định để không gãy implementor cũ) mà **tuyệt đối không trộn vào `SmartHomeState`** và **không ghi ngược xuống ESP32**.

---

## 5. Phạm vi chức năng

### Trong phạm vi
- Mở rộng `CloudDeviceModels.kt`:
  - `enum class DataSource { LAN, CLOUD, LOCAL_CACHE, NONE }`
  - `data class GatewayStateSnapshot(val online: Boolean, val reportedBy: String?, val receivedAt: Long, val deviceTs: Long?, val temp: Int?, val hum: Int?, val motion: Boolean?, val light: Boolean?, val door: Boolean?, val alarm: Boolean?, val monitoring: Boolean?, val mode: HomeMode?)`
  - `data class CloudTelemetrySnapshot(val recordId: String, val temp: Int?, val hum: Int?, val receivedAt: Long, val deviceTs: Long?)`
  - `data class CloudEventRecord(val id: String, val type: String, val severity: String, val stream: String, val seq: Long, val receivedAt: Long, val deviceTs: Long?, val detail: String?)`
  - `data class CloudDeviceSnapshot(val deviceId: String, val gatewayState: GatewayStateSnapshot? = null, val latestTelemetry: CloudTelemetrySnapshot? = null, val recentEvents: List<CloudEventRecord> = emptyList(), val loading: Boolean = false, val error: CloudError? = null, val errorMessage: String? = null)` (kèm computed `bestReceivedAt`, `bestDeviceTs`).
  - `data class ResolvedDataSource(val source: DataSource, val updatedAt: Long?, val deviceTs: Long?, val isStale: Boolean, val gatewayOnline: Boolean?)`
- Mở rộng `CloudDeviceRepository` + `RtdbDeviceRepository` (chỉ thêm method đọc):
  - `suspend fun findActiveDevice(expectedUid: String, homeId: String): CloudResult<CloudDevice?>` *(chỉ đọc node `devices`, chọn active cũ nhất, KHÔNG tự tạo device)*
  - `fun observeGatewayState(expectedUid: String, homeId: String, deviceId: String): Flow<CloudResult<GatewayStateSnapshot?>>`
  - `fun observeLatestTelemetry(expectedUid: String, homeId: String, deviceId: String): Flow<CloudResult<CloudTelemetrySnapshot?>>`
  - `fun observeRecentEvents(expectedUid: String, homeId: String, deviceId: String, limit: Int = 20): Flow<CloudResult<List<CloudEventRecord>>>`
- Tạo `CloudFallbackObserver.kt` và `DataSourceResolver.kt`.
- Cập nhật `CloudDeviceUiBridge.kt` (thêm `val cloudSnapshot: StateFlow<CloudDeviceSnapshot?> get() = MutableStateFlow(null)` hoặc default field tương thích) và nối trong `SmartHomeViewModel.kt`.
- Cập nhật `FakeCloudDeviceRepository.kt` và viết `DataSourceResolverTest.kt`, `CloudFallbackObserverTest.kt`.

### Ngoài phạm vi
- Không sửa `Models.kt` (`SmartHomeState`), không ghi đè state LAN bằng dữ liệu Cloud.
- Không sửa `Esp32Client.kt` để đẩy dữ liệu Cloud xuống ESP32.
- Không sửa giao diện `HomeScreen.kt` (thuộc Prompt `06`).

---

## 6. File được phép sửa

### Sửa đổi (`WRITE`)
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceModels.kt`
2. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceRepository.kt`
3. `app/src/main/java/com/example/smarthomeai/cloud/RtdbDeviceRepository.kt`
4. `app/src/main/java/com/example/smarthomeai/cloud/CloudDeviceUiBridge.kt`
5. `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt`
6. `app/src/test/java/com/example/smarthomeai/cloud/FakeCloudDeviceRepository.kt`

### Thêm mới (`NEW`)
1. `app/src/main/java/com/example/smarthomeai/cloud/CloudFallbackObserver.kt`
2. `app/src/main/java/com/example/smarthomeai/cloud/DataSourceResolver.kt`
3. `app/src/test/java/com/example/smarthomeai/cloud/DataSourceResolverTest.kt`
4. `app/src/test/java/com/example/smarthomeai/cloud/CloudFallbackObserverTest.kt`

---

## 7. File bị cấm sửa

- `app/src/main/java/com/example/smarthomeai/data/Models.kt` (`SmartHomeState`)
- `app/src/main/java/com/example/smarthomeai/data/Esp32Client.kt`, `Storage.kt`, `AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt`
- `app/src/main/java/com/example/smarthomeai/cloud/CloudRepository.kt`, `RtdbCloudRepository.kt`, `CloudHomeManager.kt`, `CloudGatewaySync.kt`
- Toàn bộ file UI trong `app/src/main/java/com/example/smarthomeai/ui/**`.
- Tất cả file test B3/Smoke hiện có; `cloud-rules/**`; `esp32/**`; `app/build.gradle.kts`.

---

## 8. Dependency

- Phụ thuộc đầu vào: Prompt `03` (`F2a`) và Prompt `04` (`F2b`) đã PASS.
- Làm đầu vào bắt buộc cho: **Prompt `06` (F5a)**.

---

## 9. Kiến trúc triển khai

```text
SmartHomeViewModel._state.map { it.esp32Online }.distinctUntilChanged()
   + CloudHomeManager.state (uid, homeId, status == READY)
   │
   ▼
CloudFallbackObserver (viewModelScope, IO)
   ├─ Nếu esp32Online == true: HỦY ngay toàn bộ observer Cloud, reset error tạm (hoặc clear cloudSnapshot cũ sau khi LAN đã lấy lại nhịp)
   └─ Nếu esp32Online == false && Cloud READY:
        ├─ delay(5_000L) [debounce tránh nhấp nháy listener khi LAN chỉ rớt 1–2 poll]
        ├─ findActiveDevice(uid, homeId) [read-only]
        └─ combine / launch song song 3 callbackFlow:
             ├─ observeGatewayState(uid, homeId, deviceId)
             ├─ observeLatestTelemetry(uid, homeId, deviceId)
             └─ observeRecentEvents(uid, homeId, deviceId, 20)
        │
        ▼
cloudSnapshot: StateFlow<CloudDeviceSnapshot?> ──► CloudDeviceUiBridge
```

---

## 10. Luồng dữ liệu

1. Khi LAN hoạt động (`esp32Online == true`), `CloudFallbackObserver` đứng im (0 listener đọc thừa trên RTDB).
2. Khi LAN mất liên tục `>= 5_000L` ms và Cloud có nhà `READY`, `CloudFallbackObserver` tìm `activeDevice` và mở 3 `callbackFlow` với `awaitClose { removeEventListener }`.
3. Khi `esp32Online` chuyển lại thành `true`, `collectLatest` hủy lập tức toàn bộ 3 `callbackFlow` và đặt `cloudSnapshot` về `null` (hoặc trạng thái không ưu tiên) để đảm bảo **LAN tự phục hồi phải ưu tiên trạng thái LAN tức thì**.
4. Trên tầng tiêu thụ (chuẩn bị cho Prompt `06`), `DataSourceResolver.resolve(esp32Online, localLastSeen, snapshot, nowMs)` trả về nguồn duy nhất đang có hiệu lực.

---

## 11. Logic nghiệp vụ

1. **Quy tắc `DataSourceResolver` (D12):**
   - Hằng số cũ dữ liệu: `STALE_THRESHOLD_MS = 300_000L` (5 phút).
   - **Bước 1 (`LAN` thắng tuyệt đối):** Nếu `esp32Online == true` → trả ngay `ResolvedDataSource(DataSource.LAN, updatedAt = localLastSeen.takeIf { it > 0 }, deviceTs = null, isStale = false, gatewayOnline = true)`.
   - **Bước 2 (`CLOUD` vs `LOCAL_CACHE` khi `!esp32Online`):**
     - Tính `cloudReceivedAt = max(gatewayState?.receivedAt ?: 0L, latestTelemetry?.receivedAt ?: 0L, recentEvents.maxOfOrNull { it.receivedAt } ?: 0L)` (chỉ dùng `> 0L`).
     - Nếu `snapshot != null && cloudReceivedAt > 0L && cloudReceivedAt > localLastSeen`:
       - Nguồn là `DataSource.CLOUD`.
       - `isStale = ((nowMs - cloudReceivedAt) > STALE_THRESHOLD_MS) || (snapshot.gatewayState?.online == false)`.
       - `deviceTs = snapshot.gatewayState?.deviceTs ?: snapshot.latestTelemetry?.deviceTs`.
     - Ngược lại nếu `localLastSeen > 0L`:
       - Nguồn là `DataSource.LOCAL_CACHE`.
       - `updatedAt = localLastSeen`, `isStale = (nowMs - localLastSeen) > STALE_THRESHOLD_MS`.
     - Ngược lại → `DataSource.NONE` (`isStale = true`).
   - *(Ghi chú thiết kế: Nếu đồng hồ điện thoại chạy nhanh hơn giờ server Firebase, `localLastSeen` có thể lớn hơn `cloudReceivedAt` và rơi về `LOCAL_CACHE`. Không tự bịa đồng hồ hoặc trừ bù phức tạp không kiểm chứng được; ghi rõ hành vi này trong KDoc/tài liệu.)*
2. **An toàn kiểu & Parse trên `RtdbDeviceRepository`:**
   - Parse `state/gateway` bằng `safeBoolean`, `safeLong`, `safeString`, chuyển `mode` về `HomeMode` bằng `runCatching { HomeMode.valueOf(modeStr) }.getOrNull()` (không crash nếu gặp chuỗi lạ).
   - Parse `telemetry` với `.orderByKey().limitToLast(1)`.
   - Parse `events` với `.orderByChild("receivedAt").limitToLast(limit)`, sắp xếp giảm dần theo `(receivedAt, id)` hoặc `seq` để UI đọc sự kiện mới nhất ở đầu.
3. **Không dùng Cloud làm đường điều khiển:**
   - `SmartHomeViewModel` giữ nguyên điều kiện `esp32Online` cho `setDevice`, `setMode`, `updateClothesline`, `openDoorForFace`, `setAlarmSettings`, `setRules`, `setTiming`.

---

## 12. Xử lý lỗi và concurrency

- Mọi `callbackFlow` trên `RtdbDeviceRepository` **không** được bọc `withTimeout` (`MIN-04` rule), chỉ đăng ký `ValueEventListener` và gỡ trong `awaitClose`.
- Nếu `provider.uid != expectedUid` trong `onDataChange`, phát `CloudResult.Err(CloudError.Unauthenticated)` và đóng flow; không gửi dữ liệu của UID cũ.
- Khi 1 trong 3 nhánh đọc gặp lỗi (`Offline` / `PermissionDenied`), `CloudFallbackObserver` vẫn giữ dữ liệu hợp lệ đã nhận từ các nhánh còn lại (tương tự `RtdbHomeMerger`), ghi `error` lên `CloudDeviceSnapshot` mà **tuyệt đối không chạm `SmartHomeState.esp32Online` hay `network: Mutex`**.
- `SmartHomeViewModel.onCleared()` gọi `cloudFallbackObserver.clear()`.

---

## 13. Bất biến cần bảo toàn

- **Hai nguồn trạng thái không bao giờ cạnh tranh ghi đè:** `SmartHomeState` chỉ lưu dữ liệu LAN + Local Storage; `CloudDeviceSnapshot` chỉ lưu dữ liệu Cloud.
- **0 ảnh hưởng LAN:** Cloud fallback chậm/timeout/offline không làm nghẽn LAN polling hay nút điều khiển LAN.
- **0 ghi ngược từ Cloud xuống ESP32.**
- **0 chỉnh sửa** `Models.kt`, `Storage.kt`, `Esp32Client.kt`, `MainActivity.kt`.

---

## 14. Tiêu chí hoàn thành

### Tiêu chí thiết kế (Nhánh A)
1. `RtdbDeviceRepository` cài đặt đủ 4 method đọc mới với `expectedUid` guard, parse an toàn (`MIN-02`), gỡ listener sạch trong `awaitClose`.
2. `CloudFallbackObserver` không mở listener khi `esp32Online == true`, chờ debounce `5_000L` ms khi LAN mất, hủy tức thì khi LAN có lại hoặc đổi tài khoản/đăng xuất.
3. `DataSourceResolver` ưu tiên `LAN` khi `esp32Online == true`, chọn `CLOUD` khi `cloudReceivedAt > localLastSeen`, `LOCAL_CACHE` khi cache máy mới hơn hoặc không có Cloud, và gắn `isStale` đúng ngưỡng 5 phút hoặc `gateway.online == false`.
4. `DataSourceResolverTest.kt` và `CloudFallbackObserverTest.kt` phủ đầy đủ các trường hợp biên (LAN phục hồi hủy listener, debounce ngăn flicker <5s, timeout/offline không ảnh hưởng LAN, chuyển UID xóa snapshot cũ, `isStale`).

### Lệnh & Kịch bản kiểm thử bắt buộc (Nhánh B)
Chạy trong PowerShell (`$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"`):
1. `.\gradlew.bat :app:testDebugUnitTest --console=plain` → `151`+ test cũ + `DataSourceResolverTest` + `CloudFallbackObserverTest` **PASS**.
2. `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL**.
3. Nếu AVD + Local RTDB Emulator sẵn sàng: chạy `connectedDebugAndroidTest` (cùng test emulator mới nếu Nhánh B bổ sung để kiểm chứng `observeGatewayState`/`observeLatestTelemetry`/`observeRecentEvents`).

---

## 15. Static verification (CHỈ dành cho Nhánh A)

1. `git diff --stat` → chỉ chạm các file trong mục 6.
2. Kiểm tra `Models.kt`, `Esp32Client.kt`, `Storage.kt` không thay đổi.
3. Grep `withTimeout` trong các hàm `observe*` mới của `RtdbDeviceRepository.kt` → **0 matches** bên trong `callbackFlow`.
4. Grep `com.google.firebase` trong `CloudFallbackObserver.kt`, `DataSourceResolver.kt`, `CloudDeviceUiBridge.kt`, `SmartHomeViewModel.kt` → **0 matches**.

---

## 16. Handoff report (Mẫu bắt buộc cuối Nhánh A)

```text
TASK RESULT: PASS (CODE IMPLEMENTED — STATIC CHECKS ONLY)
HYPOTHESIS: CONFIRMED
ROOT CAUSE / GAP: Xây dựng lớp đọc Cloud Fallback, CloudFallbackObserver và DataSourceResolver tách biệt nguồn LAN/Cloud/Local (F3).
EVIDENCE: <file:dòng & kết quả static grep>
CHANGES: <file + thay đổi chính>
VERIFICATION: NOT RUN BY BRANCH A (chờ Nhánh B chạy JVM test và build).
SCOPE: OK
RISKS: Lệch đồng hồ giữa server Firebase và điện thoại có thể ảnh hưởng so sánh receivedAt vs lastSeen (đã ghi rõ trong thiết kế).
REAL-WORLD TEST: PENDING INDEPENDENT TEST (BRANCH B)
OUT-OF-SCOPE: None
```
Sau báo cáo, **DỪNG** và yêu cầu người dùng chuyển sang **Model Kiểm thử độc lập (Nhánh B)**.

---

## 17. Rollback

- `git revert <commit_hash_prompt_05>`.

---

## 18. Git commit (BẮT BUỘC cho Nhánh A)

1. Kiểm tra `git status --short` chỉ chứa đúng file thuộc mục 6.
2. `git add` tường minh các file mục 6.
3. `git diff --cached --stat`.
4. Commit tiếng Việt:
   `git commit -m "Bổ sung dữ liệu Cloud dự phòng khi mất LAN"`
5. **TUYỆT ĐỐI KHÔNG `git push`.**

---

## 19. Điều kiện dừng

Dừng ngay và báo cáo người dùng nếu:
- Cần trộn dữ liệu Cloud trực tiếp vào `SmartHomeState` để hiển thị (vi phạm bất biến chống ghi đè hai nguồn).
- Phát hiện query RTDB (`limitToLast` / `orderByChild`) cần thêm `.indexOn` mới trong `database.rules.src.json` (`CD-2` cấm sửa Rules). *(Ghi chú: `telemetry` dùng `orderByKey` mặc định, `events` đã có `.indexOn: ["receivedAt"]` dòng 257).*
