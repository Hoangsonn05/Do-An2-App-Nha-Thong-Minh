# Kiểm tra Smart Home 3.6

Ngày kiểm tra: 11/11/2026.

## Kết quả đã ghi nhận

- 88 kiểm thử JVM đạt, không có lỗi hoặc thất bại.
- 54 kiểm thử giao diện Android đạt trên máy ảo.
- Sau chỉnh sửa cuối ở phần gộp lượt nhận diện, 7 kiểm thử giao diện liên quan được chạy lại và đạt.
- APK debug 3.6.0 (versionCode 11) build thành công và được xác minh chữ ký v2.
- Android Lint ghi nhận 0 lỗi và 18 cảnh báo.
- Firmware biên dịch thành công cho ESP32 Dev Module, DIO/40 MHz, Arduino-ESP32 3.3.12. Flash khoảng 74%, RAM tĩnh khoảng 15%.
- Kiểm tra logic bộ điều khiển xác nhận thời lượng còi 3/10 giây, chống lặp khi chuyển động duy trì và chờ vùng cửa an toàn trước khi bật bảo vệ.

## Kiểm tra bản mã nguồn công khai sau khi dọn dự án

- Xuất mã nguồn từ danh sách tệp chuẩn bị commit, không dùng cấu hình Firebase hoặc Wi-Fi cá nhân.
- Android build APK thành công; 88 kiểm thử JVM đạt, 0 thất bại và 0 lỗi.
- Firmware cấu hình Wi-Fi trống biên dịch thành công cho ESP32 Dev Module, DIO/40 MHz.
- Rà soát 53 tệp chuẩn bị đưa lên GitHub: không chứa thông tin Wi-Fi cục bộ, khóa riêng hoặc token truy cập.

## Phạm vi kiểm tra

Kiểm thử dùng trạng thái mô phỏng và máy ảo. Camera nhận diện người thật, tín hiệu cảm biến, nguồn điện và hành trình servo cần kiểm tra trên thiết bị thực tế. Thông báo sự kiện áp dụng khi app đang nhận dữ liệu qua cùng mạng Wi-Fi.

## Chạy lại

Cần JDK 17 và Android SDK 34. Trên Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Để chạy bộ kiểm thử giao diện, kết nối thiết bị Android hoặc mở máy ảo rồi chạy `.\gradlew.bat :app:connectedDebugAndroidTest`.

Để kiểm tra firmware, mở `esp32/SmartHomeESP32/SmartHomeESP32.ino` bằng Arduino IDE và biên dịch cho ESP32 Dev Module. Có thể dùng `wifi_config.h` trên máy phát triển, hoặc cấu hình Wi-Fi qua SmartHome-Setup khi sử dụng bản mã nguồn sạch.
