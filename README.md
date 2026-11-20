# Smart Home 3.6 · Bản demo

APK demo hiện tại nằm trong thư mục `dist/` trên máy phát triển. Để chạy mô hình, cài APK rồi nạp [firmware ESP32](esp32/SmartHomeESP32/SmartHomeESP32.ino). Điện thoại và ESP32 dùng cùng Wi-Fi. Trong **Cài đặt**, nhập IP ESP32 hiển thị trên Serial Monitor (115200) và cấp quyền thông báo trên Android.

Có thể nhập Wi-Fi bằng cách sao chép `esp32/SmartHomeESP32/wifi_config.example.h` thành `wifi_config.h` trong cùng thư mục và điền thông tin mạng.

Arduino IDE: chọn ESP32 Dev Module, Flash Mode DIO, Flash Frequency 40 MHz. Cài DHT sensor library, Adafruit Unified Sensor và ESP32Servo. Nếu ESP32 chưa kết nối Wi-Fi, dùng trang cấu hình của mạng SmartHome-Setup (mật khẩu mặc định smarthome123) tại http://192.168.4.1.

## Hai chế độ

- **Ở nhà:** điều khiển đèn, cửa và giàn phơi. Khi PIR phát hiện một đợt chuyển động mới, còi kêu 3 giây. Cửa tự đóng sau 5 giây liên tục khi vùng cửa trống.
- **Ra ngoài:** tắt đèn ngay, đóng cửa và bật bảo vệ khi vùng cửa an toàn. Nếu cửa đang mở và có vật cản hoặc mất echo, ESP32 chờ và app hiển thị chưa bật bảo vệ. Khi bảo vệ, PIR kích còi 10 giây cho mỗi đợt báo động.
- PIR cần khoảng 60 giây ổn định sau khi cấp nguồn. Giàn phơi có thể tự thu khi mưa trong cả hai chế độ.

## Giao diện và thông báo

- **Trang chủ** hiển thị kết nối, chế độ, trạng thái cửa, nhiệt độ, độ ẩm, mưa và chuyển động. Chạm **Xem tất cả cảm biến** để xem hồng ngoại, khoảng cách, biểu đồ và xuất CSV.
- **Khuôn mặt:** chạm **Bắt đầu quét**. Khung xanh và tên xuất hiện khi xác nhận thành viên; khung đỏ ghi **Người lạ** khi không khớp. Thành viên được xác nhận sẽ tự mở cửa và chuyển về **Ở nhà**. Nhận diện bằng camera điện thoại dành cho mô hình demo.
- **Nhật ký** lưu cảnh báo và sự kiện. Android hiện thông báo khi app đang nhận dữ liệu qua Wi-Fi. Chạm thông báo để xem sự kiện. Có thông báo khi mưa, tạnh mưa, có chuyển động, camera nhận diện người lạ, cửa chưa thể đóng hoặc môi trường vượt ngưỡng.
- **Cài đặt** quản lý thành viên, kết nối và thông báo. Mặc định báo nóng từ 35°C, lạnh từ 18°C và độ ẩm từ 80%. App báo một lần khi đi vào vùng vượt ngưỡng.

## Chân nối

| Thiết bị | Chân | GPIO |
|---|---|---|
| DHT22 | DATA | 4 |
| PIR SR505 | OUT | 32 |
| FC-51 | OUT | 27 |
| SRF05 | TRIG / ECHO | 16 / 17 |
| Cảm biến mưa | DO | 33 |
| Servo cửa | Signal | 18 |
| Servo giàn phơi | Signal | 19 |
| LED xanh / vàng | Qua điện trở | 25 / 26 |
| Còi | Điều khiển | 14 |

Hai servo dùng nguồn 5V ngoài đủ dòng, GND chung ESP32; không cấp servo từ chân 3.3V. ECHO SRF05: ECHO → 1kΩ → GPIO17; GPIO17 → 2kΩ → GND. LED nối tiếp điện trở 220–330Ω. Kiểm tra đầu ra cảm biến phù hợp logic 3.3V.

Còi mặc định thụ động PWM 2000Hz (`BUZZER_PASSIVE=true`), còi chủ động đổi false. Chỉ đấu trực tiếp còi hai chân vào GPIO14/GND khi thông số còi phù hợp GPIO 3.3V, dòng thấp; còi cần nguồn/dòng lớn dùng mạch kích phù hợp. Chưa xác định thông số còi thực tế của người dùng.

Cửa/giàn phơi báo vị trí servo được lệnh, không có cảm biến hành trình xác nhận cơ khí; cần hiệu chỉnh góc và thử mô hình thực tế.

## Kiểm tra bản dựng

- Android: JDK 17, SDK 34. Chạy `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.
- Firmware: nạp `esp32/SmartHomeESP32/SmartHomeESP32.ino`.
- Cửa và giàn phơi báo vị trí đã lệnh cho servo. Khi demo trên mạch thật, kiểm tra thêm hành trình cơ khí, cảm biến và camera.

## Cấu trúc dự án

- `app/src/main/`: ứng dụng Android, giao diện và mô hình nhận diện khuôn mặt.
- `app/src/test/`: kiểm thử logic trên JVM.
- `app/src/androidTest/`: kiểm thử giao diện Android.
- `esp32/SmartHomeESP32/`: firmware và mẫu cấu hình Wi-Fi.
- `docs/VALIDATION.md`: kết quả kiểm tra bản demo hiện tại.
- `gradle/` và `gradlew`: công cụ build Gradle.

Mở thư mục dự án bằng Android Studio để cấu hình Android SDK và đồng bộ Gradle. Khi không có `app/google-services.json`, ứng dụng dùng luồng tài khoản demo. Muốn dùng đăng nhập Firebase, thêm cấu hình riêng của bạn vào đường dẫn đó.

APK debug sau khi build nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Các tệp cài đặt, cấu hình máy và công cụ cục bộ được Git bỏ qua. Những bản cũ được chuyển vào `.local-archive/cleanup-2026-11-11/` trên máy hiện tại.
