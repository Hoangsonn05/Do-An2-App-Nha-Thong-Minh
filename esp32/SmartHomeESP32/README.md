# Firmware SmartHomeAI cho bo Long_BoSung

Bản này dựa trên commit bcd7a2b3109ca2c434f0a19c7d737f3d588e5a4c của repo Do-An2-App-Nha-Thong-Minh. Mở SmartHomeESP32.ino bằng Arduino IDE, giữ LongSensors.h trong cùng thư mục và chọn ESP32 Dev Module. Thư viện cần có là DHT sensor library by Adafruit, Adafruit Unified Sensor, ESP32Servo và MFRC522. Wifi cấu hình qua SmartHome-Setup hoặc wifi_config.h theo file mẫu.

## Phần cứng

- DHT22 GPIO4, PIR GPIO27, FC-51 GPIO14. SRF05 dùng TRIG GPIO26 và ECHO GPIO25 qua bộ chia áp của bo.
- Cảm biến mưa dùng AO GPIO34, MQ-2 dùng AO GPIO35. MQ-2 cấp 5 V cần mạch chia áp ngoài, theo phép quy đổi trong firmware dùng 12 kΩ nối từ AO đến GPIO35 và 20 kΩ từ GPIO35 xuống GND.
- BH1750 và LCD dùng SDA GPIO32/SCL GPIO33. BH1750 mặc định 0x23. LCD dùng backpack PCF8574 thông dụng ở 0x27 hoặc 0x3F và cần bộ chuyển mức hai chiều nếu chạy 5 V.
- Reed dùng GPIO36 với điện trở 10 kΩ kéo lên 3V3, tiếp điểm còn lại nối GND.
- RC522 dùng 3V3, SCK 22, MISO 16, MOSI 13, SS 5, RST 17.
- Servo cửa chính, cửa sổ và giàn phơi dùng GPIO18, 19 và 21. Nguồn servo 5 V có GND chung với ESP32. Thử khi tay đòn chưa gắn vào cơ cấu vì firmware chính có thể đưa servo về vị trí mặc định ngay lúc khởi động.
- LED đèn GPIO2 bật HIGH, LED cảnh báo GPIO15 bật LOW. Relay quạt GPIO23 mặc định bật LOW. Nếu module của bạn kích HIGH, đổi FAN_ACTIVE_HIGH trong SmartHomeESP32.ino trước khi nối quạt.
- Buzzer chủ động 5 V, tối đa 15 mA, cắm J20.1 vào cực dương và J20.2 vào cực âm. GPIO12 điều khiển qua Q1 (2N3904), bật HIGH, tắt LOW. Buzzer chạy cùng nhịp LED cảnh báo HOME/AWAY. Giữ R6 10 kΩ kéo chân B của Q1 xuống GND để GPIO12 không bị kéo HIGH khi reset.

## Kiểm tra

- Dùng nút bật/tắt báo động trên app ở chế độ HOME để thử LED cảnh báo và buzzer. Nếu xuất hiện lỗi nạp flash, tháo mạch buzzer khỏi GPIO12 lúc nạp và kiểm tra lại mạch kéo xuống trước khi cắm lại.

- Địa chỉ ESP32 hiện trong Serial Monitor ở 115200 baud. Mở http://IP_ESP32/sensor để xem light_lux, reed_closed, rain_raw, rfid_ready và lcd_ready. Các trường mới có ở firmware/API, app GitHub hiện tại chưa có phần hiển thị riêng cho chúng.
- Khi quẹt thẻ, Serial in RFID UID. Mặc định chỉ đọc UID. Để mở cửa mô hình bằng thẻ ở chế độ HOME, điền UID liền mạch vào allowedUid trong LongSensors.h rồi nạp lại.
- Lấy giá trị rain_raw khi tấm khô và ướt. Chỉnh rainWetAdc và rainDryAdc nếu module giảm số đọc khi ướt. Hai giá trị mặc định chỉ là điểm bắt đầu. Trên thiết bị mới, chế độ tự thu giàn phơi mặc định tắt. Nếu ESP32 đã lưu cấu hình cũ, kiểm tra công tắc tự thu trong app trước khi cấp nguồn cho servo.
- Dùng MQ-2 như chức năng thông gió thử nghiệm của mô hình. Số ppm là ước tính và phụ thuộc hiệu chuẩn cảm biến.

## Đèn tự động

- Ở chế độ HOME, PIR phát hiện chuyển động thì LED đèn GPIO2 bật. Khi ngõ OUT của PIR về LOW, LED tắt. Thời gian giữ sáng phụ thuộc vào module PIR. Chờ PIR ổn định 60 giây sau khi cấp nguồn.
- Lệnh bật/tắt trên app có hiệu lực đến khi trạng thái PIR thay đổi. Chế độ AWAY giữ hành vi tắt đèn.
- BH1750 vẫn đo lux và gửi số đo qua API. Độ sáng không điều khiển LED.
