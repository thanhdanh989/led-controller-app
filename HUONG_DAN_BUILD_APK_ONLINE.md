# Build file .apk online bằng GitHub Actions (không cần cài Android Studio)

Mình đã thêm sẵn file `.github/workflows/build-apk.yml` vào project — file này bảo GitHub
tự động build file .apk mỗi khi bạn tải code lên. Bạn chỉ cần đưa code lên GitHub, không cần
cài gì trên máy.

## Bước 1: Tạo tài khoản GitHub (bỏ qua nếu đã có)

Vào **github.com** → Sign up → làm theo hướng dẫn (miễn phí).

## Bước 2: Tạo repository mới

1. Vào **github.com/new**
2. Đặt tên bất kỳ, ví dụ `led-controller-app`
3. Để **Public** hoặc **Private** đều được
4. **KHÔNG** tick "Add a README file"
5. Bấm **Create repository**

## Bước 3: Tải code lên GitHub

Có 2 cách, chọn 1 trong 2:

### Cách A — Dùng GitHub Desktop (khuyến nghị, dễ nhất)

1. Tải **GitHub Desktop** tại desktop.github.com (nhẹ, ~100MB, cài nhanh)
2. Đăng nhập tài khoản GitHub trong app
3. **File > Clone repository** → chọn đúng repo bạn vừa tạo ở Bước 2 → chọn thư mục lưu về máy
4. Mở thư mục vừa clone về (rỗng), **copy toàn bộ nội dung BÊN TRONG thư mục `android-app`**
   (mình gửi trong file zip) vào thư mục đó — copy các file/thư mục con, không copy nguyên
   thư mục `android-app` bọc ngoài.
5. Quay lại GitHub Desktop, sẽ thấy danh sách file thay đổi → gõ vào ô "Summary" chữ gì đó
   (vd: "add app") → bấm **Commit to main** → bấm **Push origin**

### Cách B — Tải trực tiếp qua trình duyệt (không cần cài gì thêm)

1. Vào trang repo vừa tạo, bấm dòng chữ **"uploading an existing file"**
2. Kéo thả **toàn bộ các file và thư mục con bên trong `android-app`** vào khung tải lên
   (kéo cả thư mục `app`, `gradle`, `.github`, và các file `.kts`, `.md` — trình duyệt Chrome/Edge
   hỗ trợ kéo cả thư mục). Nhớ kéo cả thư mục ẩn `.github` (chứa file workflow) — nếu máy bạn
   không hiện thư mục ẩn, cần bật "Show hidden files" trong File Explorer trước.
3. Bấm **Commit changes**

## Bước 4: Xem GitHub tự build APK

1. Vào tab **Actions** trên trang repo
2. Sẽ thấy 1 lượt chạy tên "Build APK" đang chạy (chấm vàng) → đợi khoảng 3-6 phút đến khi
   thành dấu tích xanh ✅
3. Bấm vào lượt chạy đó, kéo xuống mục **Artifacts** ở cuối trang
4. Bấm tải file **app-debug-apk** (dạng .zip) → giải nén ra sẽ thấy file **app-debug.apk**

## Bước 5: Cài lên điện thoại

1. Chuyển file `app-debug.apk` vào điện thoại Android (qua cáp USB, Zalo gửi cho chính mình,
   Google Drive...)
2. Mở file đó trên điện thoại để cài. Nếu bị chặn, vào **Cài đặt > Bảo mật** (hoặc khi cài sẽ
   có nút tắt) bật **"Cho phép cài từ nguồn này"**.
3. Mở app, bấm Kết nối để tìm board **ESP32-LED** qua Bluetooth.

## Nếu Actions báo lỗi đỏ ❌

Bấm vào lượt chạy bị lỗi → bấm vào bước bị đỏ để xem chi tiết log → chụp màn hình hoặc copy
đoạn lỗi gửi cho mình, mình sẽ sửa code rồi bạn chỉ cần tải lại (push lại) là Actions tự chạy lại.
