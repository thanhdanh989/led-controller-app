# App Android điều khiển LED qua Bluetooth (source code)

Đây là **source code** đầy đủ của app Android (Kotlin, không dùng Compose để tránh
rắc rối khớp phiên bản Kotlin/Compose compiler — dùng layout XML truyền thống, ổn định hơn).
Môi trường của mình không cài được Android SDK/Gradle đầy đủ nên **chưa build ra file .apk
sẵn** được — bạn cần mở bằng Android Studio và bấm Build.

## 1. Cách mở và build

1. Cài **Android Studio** (bản mới, tải tại developer.android.com/studio).
2. Mở Android Studio → **Open** → chọn thư mục `android-app` (thư mục chứa file `settings.gradle.kts`).
3. Android Studio sẽ tự động tải Gradle + các thư viện lần đầu mở (cần internet, có thể mất
   vài phút). Nếu báo thiếu `gradle-wrapper.jar`, chọn **"Try Again"** hoặc vào
   **File > Sync Project with Gradle Files** — Android Studio tự tải lại file này.
4. Cắm điện thoại Android qua USB (bật **Tùy chọn nhà phát triển > Gỡ lỗi USB**), hoặc dùng máy ảo.
5. Bấm nút **Run (▶)** để cài thẳng lên điện thoại, hoặc **Build > Build Bundle(s)/APK(s) > Build APK(s)**
   để xuất ra file `.apk` rồi tự cài.

## 2. App làm được gì

- Quét và kết nối Bluetooth Low Energy (BLE) tới board tên **"ESP32-LED"**.
- Bật/tắt đèn.
- Chỉnh độ sáng (0-255) và tốc độ hiệu ứng (0-100).
- Chọn 1 trong 20 chế độ (danh sách trong `Protocol.kt`, khớp với firmware).
- Chọn màu đơn bằng 3 thanh trượt R/G/B, có xem trước màu.
- Quản lý bảng màu (tối đa 8 màu) dùng cho chế độ Xếp gạch / Quét gradient / Chuyển màu theo bảng.
- Đổi số lượng LED và chân GPIO ngay từ app, không cần nạp lại firmware.

## 3. Quyền cần cấp trên điện thoại

Android sẽ tự hỏi xin quyền khi bấm "Kết nối" lần đầu:
- Android 12 trở lên: quyền **Thiết bị lân cận** (Nearby devices / Bluetooth).
- Android 11 trở xuống: quyền **Vị trí** (bắt buộc theo quy định của Google để quét BLE, app
  không thực sự lấy vị trí của bạn).

## 4. Cấu trúc code

| File | Nội dung |
|---|---|
| `Protocol.kt` | UUID dịch vụ BLE, danh sách 20 chế độ, hàm tạo lệnh JSON |
| `BleController.kt` | Toàn bộ logic quét/kết nối/gửi-nhận BLE |
| `PaletteAdapter.kt` | Danh sách màu dạng lưới (RecyclerView) |
| `MainActivity.kt` | Giao diện chính, nối các nút bấm với BleController |

## 5. Nếu muốn sửa/mở rộng

- Đổi tên thiết bị cần tìm: sửa `BleProtocol.DEVICE_NAME` (phải khớp tên trong firmware `BleHandler.h`).
- Thêm chế độ mới: thêm vào `LED_MODES` trong `Protocol.kt` **và** thêm hàm hiệu ứng tương ứng
  bên firmware (`Effects.h`) — hai bên phải khớp số thứ tự (mode id).
