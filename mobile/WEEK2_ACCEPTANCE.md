# Nghiệm thu tuần 2 — Khung ứng dụng

Ngày kiểm tra: 08/11/2026. Chế độ chạy: `USE_FAKE_BACKEND=true`.

## Phạm vi đã hoàn thành

| Hạng mục | Kết quả |
| --- | --- |
| Kiến trúc | MVVM, phân lớp UI/domain/data, Hilt, Coroutines/Flow; quy ước tại ARCHITECTURE.md |
| Điều hướng | Khung xác thực riêng không có tab; đăng nhập mở Bản đồ và các tab chính; đăng xuất/hết hạn gỡ back stack chính |
| Cài đặt | DataStore lưu loại xe, giọng nói, đóng góp và chỉ Wi-Fi; có trạng thái lỗi/thử lại |
| API client | Retrofit/OkHttp, DTO, cấu hình URL, Bearer, request ID, timeout, ánh xạ lỗi HTTP |
| Room | Database/DAO/entity; cache biển mẫu đọc bằng Flow, giữ dữ liệu khi mở lại |
| Tài khoản giả lập | Đăng ký, kiểm tra email trùng, đăng nhập/đăng xuất, quên mật khẩu/OTP, đặt lại/đổi mật khẩu |
| Phiên | Room + Android Keystore AES-GCM; khôi phục khi mở lại, hết hạn, xử lý 401 và phiên hỏng |
| Chế độ khách | Chỉ vào khi chủ động chọn “Tiếp tục với khách” từ màn đăng nhập; tài khoản hiển thị trạng thái khách |

Fake auth yêu cầu đăng ký trước, mật khẩu tối thiểu 8 ký tự. OTP thử nghiệm hiện
trên màn hình, có hạn 5 phút, tối đa 5 lần nhập sai và chờ 30 giây để gửi lại.
Phiên fake có hạn 24 giờ; đặt lại/đổi mật khẩu yêu cầu đăng nhập lại.

## Kết quả tự động

- Build APK debug: thành công.
- Unit test: **30/30 qua**, gồm form/ViewModel, cổng xác thực, chờ khôi phục phiên, chế độ khách chủ động, đăng xuất/hết hạn, băm mật khẩu và HTTP qua MockWebServer.
- Instrumented test lưu trữ: **12/12 qua** ở lần nghiệm thu trước khi chỉnh giao diện, trên `Medium_Phone_API_36.1`, Android 16.
  Phạm vi gồm Room trên file, Keystore, khôi phục phiên/cache, OTP, đổi mật khẩu và DataStore.
- Android lint: **0 lỗi, 40 cảnh báo**. Các cảnh báo còn lại được lưu trong báo cáo lint.
- `git diff --check`: qua.

Kiểm tra trực tiếp qua giao diện máy ảo cũng đã qua:

- Đăng nhập/Đăng ký/Quên mật khẩu không có tab chính; chủ động chọn khách mới mở Bản đồ.
- Đăng nhập mở Bản đồ; khôi phục phiên hợp lệ mở khung chính; đăng xuất trở về Đăng nhập
  và bấm Back/mở lại không quay lại các tab khi chưa xác thực.
- Đăng ký → đăng nhập → đóng tiến trình → mở lại → khôi phục hồ sơ → đăng xuất.
- Quên mật khẩu → nhận OTP thử nghiệm → email được điền sẵn ở màn đặt lại
  → đổi mật khẩu bằng OTP → đăng nhập bằng mật khẩu mới.
- Đổi mật khẩu từ hồ sơ → trở về đăng nhập → dùng mật khẩu mới → đăng xuất về khung Đăng nhập.

```powershell
cd mobile
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

Báo cáo cục bộ sau khi chạy:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Giao diện theo mẫu đã thống nhất

Áp dụng ảnh mẫu gồm Đăng nhập, Đăng ký, Quên mật khẩu, Đặt lại mật khẩu,
Tài khoản và Đóng góp của tôi. Gồm logo Signa, nhãn/biểu tượng ô nhập,
ẩn/hiện mật khẩu, OTP 6 ô dùng một input hỗ trợ dán/xóa, gửi lại mã,
thẻ hồ sơ/loại xe, menu có biểu tượng, thống kê và thẻ quan sát có trạng thái.
Chế độ khách được giữ qua nút “Tiếp tục với khách”; khung xác thực không có bottom bar.

Thông tin cá nhân mở hộp thoại chỉ đọc; Trợ giúp mở hướng dẫn sử dụng.
Đổi loại xe trong Cài đặt được phản ánh trên hồ sơ. Dữ liệu đóng góp vẫn là
minh họa, dùng các biển tốc độ hiện có; trạng thái/thời điểm nằm ở dữ liệu giả lập.
Có Compose Preview cho các form, hồ sơ, khách và đóng góp.

Luồng giao diện mới đã thử trên máy ảo: nhập OTP 6 ô, đặt lại mật khẩu/đăng nhập,
thông tin cá nhân/trợ giúp, đăng xuất, chọn khách và mở chi tiết biển từ đóng góp.
APK cuối cũng đã thử đăng ký tài khoản mới, đăng nhập vào hồ sơ, đọc đúng tiếng Việt
trên thẻ đóng góp, đăng xuất rồi tiếp tục dùng ở chế độ khách.

## Giới hạn và phần nối tiếp

- Tài khoản được nghiệm thu bằng dữ liệu giả lập cục bộ; chưa chạy đầu cuối với backend thật.
  API client đã kiểm tra bằng MockWebServer. Các body auth chưa có ví dụ đầy đủ
  trong đặc tả cần được đối chiếu với backend khi triển khai.
- Bản đồ/camera hiện là minh họa. Cache Room dùng biển mẫu, chưa tải biển theo vùng.
- Tích hợp VietMap là công việc tiếp theo theo thứ tự đã thống nhất.
- AI, GPS, cảnh báo, gửi quan sát và kiểm thử hiệu năng thuộc các giai đoạn sau.

Cách chạy fake/API và vị trí từng lớp: xem [ARCHITECTURE.md](ARCHITECTURE.md).
