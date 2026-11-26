# KẾ HOẠCH AUDIT TỔNG THỂ SAU TÍCH HỢP — CẢM BIẾN KHÓI MQ-2, CẢNH BÁO 300 PPM & THÔNG GIÓ TỰ ĐỘNG

- **Điều kiện kích hoạt:** **CHỈ BẮT ĐẦU** sau khi toàn bộ **4 prompt triển khai (`01` → `04`)** trong [`FEATURE-PROMPT-INDEX.md`](FEATURE-PROMPT-INDEX.md) đã hoàn tất Nhánh A, vượt qua Nhánh B (`PASS`) và đã có commit tiếng Việt sạch trên nhánh `master`.
- **Nguyên tắc vận hành:** Thực hiện **READ-ONLY Audit** toàn hệ thống kết hợp chạy Full Test Suite thực tế trước; **không** tự ý vá rải rác trong lúc Audit; **không** truy cập Firebase Production `appaintm`; **không** `git push`.

---

## 1. Phạm vi 10 Trục Audit Bắt buộc (A – J)

| Trục | Tên trục Audit | Trọng tâm kiểm tra thực tế trên Source + Runtime Emulator |
|---|---|---|
| **A** | **Hardware Contract Readiness** | Đối chiếu 1-1 giữa `HardwareStatus.parse`, `Esp32Client` (`sendWindow`, `sendFan`, `sendSmokeConfig`), `Security.kt` (`DeviceEvent`) với bảng Hợp đồng Phần cứng trong [`MASTER-PLAN-MQ2-SMOKE-VENTILATION.md`](MASTER-PLAN-MQ2-SMOKE-VENTILATION.md): đảm bảo khi nạp code ESP32 sau này là khớp nối tức thì, đồng thời khi cắm mạch ESP32 v3.5 hiện tại (chưa có MQ-2) không phát sinh lỗi hay cảnh báo giả. |
| **B** | **300 ppm Threshold & Dual-Layer Auto-Ventilation** | Kiểm tra `SmokeSafetyGate` và `SmokeVentilationCoordinator`: đúng ngưỡng mặc định `300 ppm`, vùng trễ hysteresis `50 ppm` (`< 250 ppm` mới re-arm), Lớp 1 không bắn lệnh thừa khi firmware đã tự mở, Lớp 2 tự động bắn `window=1` & `fan=1` ngay cả khi đang ở chế độ `AWAY`/`SLEEP`, hết khói giữ nguyên vị trí mở an toàn. |
| **C** | **Phone Alert & Notification Deduplication** | Kiểm tra `SmartHomeViewModel` + `Notifier`: khi vượt `300 ppm` phát đúng `AlertItem(AlertType.SMOKE)` và gọi thông báo đẩy trên điện thoại; không bị thông báo kép khi ESP32 gửi đồng thời `smoke_ppm >= 300` qua `/sensor` và `smoke_detected` + `smoke_auto_vent` qua `/events`. |
| **D** | **Concurrency & Non-Reentrant Mutex Safety** | Kiểm tra toàn bộ đường gọi trong `SmartHomeViewModel.kt`: xác nhận `0` trường hợp gọi lồng `network.withLock` từ bên trong `refreshLocked()` / `applySensor()`, cờ `ventilationBusy` luôn được giải phóng trong `finally`, cooldown `5_000 ms` chống nghẽn HTTP LAN. |
| **E** | **Firebase RTDB Rules & Cloud Sync / Fallback** | Kiểm tra trên Local Firebase Emulator (`--project demo-smarthomeai`): `state/gateway` nhận đúng `smokePpm` (`0..10000`), `smokeAlert`, `window`, `fan`; `config` nhận `smokeLimit` (`50..5000`) + `smokeAuto` đồng thời vẫn tương thích ngược với payload 7 khóa cũ; `telemetry` nhận `smokePpm`; `events` phân loại `smoke_detected`/`smoke_auto_vent` là `critical`; `CloudFallbackObserver` + `CloudReadOnlySummaryCard` hiển thị đầy đủ khi mất LAN. |
| **F** | **SQLite v2 Migration & Local Storage** | Kiểm tra `Storage.kt`: nâng cấp từ DB v1 (`time, temp, hum`) lên v2 (`ALTER TABLE samples ADD COLUMN smoke_ppm INTEGER`) bảo toàn dữ liệu cũ; xuất CSV giữ nguyên 3 cột khi không có mẫu khói và xuất 4 cột khi có mẫu `smokePpm`. |
| **G** | **UI/UX Pro Max & Accessibility** | Kiểm tra `SmokeVentilationCard`, `HomeScreen`, `EnvironmentScreen`, `AlertsScreen`, `SettingsScreen`, `SystemScreen`: 0 emoji, hiển thị rõ đơn vị `ppm`, cảnh báo màu `RedAlert`/`RedSoft` tương phản ≥ 4.5:1, khóa nút `"Đóng cửa sổ"` & `"Tắt quạt"` khi `ventilationSafetyLocked == true`, touch target ≥ 48dp, đầy đủ semantics. |
| **H** | **Zero Regression trên Legacy Suite** | Chạy toàn bộ JVM tests cũ (`InsightsTest`, `HardwareStatusTest`, `Esp32ClientTest`, `SecurityTest`, toàn bộ Cloud tests), toàn bộ Rules tests cũ (`build-rules.test.mjs`, `rules.test.mjs`, `gateway-payload.test.mjs`), và kiểm tra bảo toàn các bất biến của `AppSmokeTest` (`DeviceType.entries`, `EnvironmentConfigDialog`, `deviceControlsDisabledWhenOffline`). |
| **I** | **Security & Sensitive Data Isolation** | Grep toàn bộ source & test mới: 0 lộ `deviceKey`, password, token hay IP nội bộ lên payload Firebase RTDB; `esp32/SmartHomeESP32/SmartHomeESP32.ino` chưa bị sửa ngoài ý muốn khi chưa có phần cứng. |
| **J** | **Build & Static Quality Gates** | Chạy `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` đạt 0 lỗi. |

---

## 2. Phân loại phát hiện & Xử lý sau Audit

1. Mọi phát hiện trong báo cáo Audit (`docs/mq2-smoke-ventilation/POST-INTEGRATION-AUDIT-REPORT.md`) phải được phân loại minh bạch thành:
   - **`BUG` (`CRITICAL / HIGH / MEDIUM / LOW`):** Lỗi thực tế có bằng chứng tái hiện từ code/test/log.
   - **`RISK`:** Tình huống biên (ví dụ thời gian làm nóng đầu dò MQ-2 khi mới cấp nguồn trên mạch thật).
   - **`UNVERIFIED`:** Hạng mục chờ ghép nối mạch ESP32 + cảm biến MQ-2 vật lý thực tế.
   - **`LIMITATION`:** Cảnh báo đẩy hiện hoạt động qua kết nối LAN / đồng bộ sự kiện khi app chạy (chưa dùng Cloud Functions FCM nền).
   - **`USER_DECISION_REQUIRED`:** Ví dụ triển khai `database.rules.json` mới lên Firebase Production `appaintm` hoặc bắt đầu task viết firmware C++ cho mạch ESP32 thật.
2. **Quy trình vá lỗi theo cụm (Clustered Remediation):** Nếu phát hiện `BUG`, gom theo nguyên nhân gốc thành tối đa **1–2 prompt sửa lỗi theo cụm** (tuân thủ chuẩn 2 Nhánh A / B, commit tiếng Việt, không `git push`).
