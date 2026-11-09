# Kiến trúc Android Signa — nghiệm thu tuần 2

Ứng dụng dùng một module `app`, MVVM + Clean Architecture, Hilt và Coroutines/Flow.
Mặc định chạy độc lập bằng backend giả lập cục bộ. Bản đồ VietMap, GPS, camera,
nhận diện và đồng bộ quan sát thuộc các giai đoạn tiếp theo.

## Cấu trúc hiện tại

```text
com.signa.app/
├── SignaApplication.kt          # Hilt application
├── MainActivity.kt              # Activity, kiểm tra lại phiên khi vào foreground
├── ui/
│   ├── navigation/              # Cổng xác thực, AuthNavHost, graph chính và bottom bar
│   ├── theme/                   # Màu, typography, hình dạng
│   ├── components/              # Thành phần dùng chung
│   ├── preview/                 # Dữ liệu thiết kế, không khởi tạo ViewModel
│   ├── auth/                    # Form, validation, UiState, ViewModel, sự kiện
│   ├── account/                 # Khách, hồ sơ, đăng xuất
│   ├── settings/                # Cài đặt và trạng thái lỗi lưu trữ
│   ├── map/                     # Tìm kiếm, chọn biển
│   ├── sign/                    # Tra cứu, chi tiết theo signId
│   ├── contributions/           # Đóng góp mẫu
│   └── drive/                   # Giao diện lái xe, minh họa camera
├── domain/
│   ├── model/                   # Cài đặt, người dùng, phiên, biển, lỗi xác thực
│   └── repository/              # Interface Kotlin thuần
├── data/
│   ├── fake/                    # Auth giả lập và dữ liệu minh họa
│   ├── location/                # FusedLocationProvider, tách khỏi UI
│   ├── local/
│   │   ├── preferences/         # DataStore cài đặt
│   │   ├── database/            # Room, entity, DAO
│   │   └── session/             # Lưu mã hóa, khôi phục, hết hạn phiên; băm mật khẩu
│   ├── remote/
│   │   ├── api/                 # Retrofit AuthApi
│   │   ├── dto/                 # Request/response, lỗi HTTP
│   │   └── interceptor/         # Bearer, request ID, xử lý 401
│   ├── mapper/                  # Entity ↔ model
│   └── repository/              # Auth API, cài đặt, cache biển
└── di/
    ├── ApplicationScope.kt      # Coroutine scope cấp ứng dụng
    ├── NetworkModule.kt         # JSON, OkHttp, Retrofit, AuthApi
    ├── StorageModule.kt         # Room, DataStore, Clock, scope
    └── RepositoryModule.kt      # Binding repository và lựa chọn fake/API
```

`domain/usecase`, `domain/warning` và `vision` được dành cho nghiệp vụ tiếp theo.
Chưa cần use case chỉ để chuyển tiếp một lời gọi repository. Xử lý camera, GPS,
AI thực tế sẽ được tách khỏi `ui/drive`.

## Luồng phụ thuộc

```text
Screen ← UiState ← ViewModel → domain.repository
                                   ↑
                          data.repository / data.fake
                                   ↓
                         Retrofit / Room / DataStore
```

- Screen nhận state và callback; Route thu thập Flow theo lifecycle, nối điều hướng.
- ViewModel gọi interface, xử lý tải/lỗi và sự kiện kết quả; không gọi DAO hoặc Retrofit.
- Domain không phụ thuộc Android, Compose, Room, Retrofit hoặc Hilt.
- DTO/entity và ánh xạ thuộc `data`; Hilt lắp ghép các dependency tại `di`.
- Component riêng đặt cạnh tính năng; component dùng chung đặt trong `ui/components`.
- `ui/preview/PreviewSamples.kt` chuyển dữ liệu `FakeData` thành UiState cho Preview.
  Preview chỉ giúp xem thiết kế; dữ liệu giả lập chạy trong app nằm ở `data/fake`.

## Khung xác thực và khung chính

`SignaAppViewModel` quan sát phiên và cung cấp `Loading`, `SignedOut`, `Guest`,
`Authenticated`. Khi đang đọc phiên lưu, app hiển thị tải thay vì mở tab tạm thời.

- `AuthNavHost`: Đăng nhập, Đăng ký, Quên mật khẩu, OTP/Đặt lại; không có bottom bar.
- `SignaNavHost`: các tab chính, chi tiết và Đổi mật khẩu trong tài khoản.
- Hai graph có NavController riêng. Khi đổi quyền truy cập, graph cũ bị gỡ bỏ,
  bao gồm back stack và ViewModel của các màn trong graph đó.
- Đăng ký thành công trở về Đăng nhập; chỉ phiên hợp lệ hoặc lựa chọn khách rõ ràng
  mới mở các tab. Khách không được tự khôi phục khi app khởi động lạnh.

## Nguồn dữ liệu

| Hợp đồng | Bản triển khai đang được cung cấp | Hành vi |
| --- | --- | --- |
| SettingsRepository | SettingsRepositoryImpl | DataStore: loại xe, giọng nói, đóng góp, chỉ Wi-Fi |
| AuthRepository | FakeAuthRepository (mặc định) / RemoteAuthRepository | Cùng hợp đồng đăng ký, đăng nhập, đăng xuất, OTP, đặt lại/đổi mật khẩu |
| SignRepository | CachedSignRepository | Đọc Room bằng Flow; nạp biển mẫu khi cache trống |
| ContributionRepository | FakeContributionRepository | Thống kê và quan sát minh họa |

Room `signa.db` có `traffic_signs`, `fake_accounts`, `password_resets` và
`current_session`. Schema 1 và 2 được xuất tại `app/schemas/`. Migration 1 → 2 thêm tọa độ nullable, giữ dữ liệu đã lưu. Chưa có
cache biển theo vùng hoặc hàng đợi upload; phần đó nối tiếp khi làm bản đồ và gửi quan sát.

Camera vẫn là hình minh họa. Bản đồ đã dùng VietMap SDK, GeoJSON và định vị một lần qua LocationRepository; khi thiếu key chỉ có nền trống/marker mẫu, chưa có đường phố. Tìm kiếm lọc biển đã lưu theo tên, mã, vị trí;
chi tiết nhận `signId` ổn định và có trạng thái không tìm thấy. Cache mẫu chỉ phục vụ
kiểm thử khung ứng dụng, không dùng làm dữ liệu cảnh báo ngoài thực tế.

## Tài khoản và phiên

1. Mở app chưa có phiên sẽ vào Đăng nhập, không có bottom bar. Chọn Đăng ký và nhập email, tên, mật khẩu từ 8 ký tự.
2. Đăng ký thành công trở về đăng nhập; email trùng và sai mật khẩu có lỗi riêng.
3. Đăng nhập thành công mở khung các tab, bắt đầu ở Bản đồ. Đóng/mở app sẽ kiểm tra và khôi phục phiên Room hợp lệ trước khi hiển thị khung chính.
4. Quên mật khẩu → Gửi OTP → đọc **mã thử nghiệm** trên màn hình → nhập mã và mật khẩu mới.
5. Đổi mật khẩu cần mật khẩu hiện tại. Đặt lại/đổi thành công yêu cầu đăng nhập lại.
6. Đăng xuất hoặc hết hạn phiên sẽ xóa khung chính và trở về Đăng nhập; nút Back không quay lại các tab.
7. Chế độ khách chỉ mở khi người dùng chủ động chọn “Tiếp tục với khách”. Từ Tài khoản → Đăng nhập sẽ trở lại khung xác thực.

Fake auth lưu tài khoản cục bộ, mật khẩu/OTP dưới dạng PBKDF2-HMAC-SHA256 có salt
(120.000 vòng). OTP có 6 chữ số, sống 5 phút, tối đa 5 lần nhập sai, gửi lại sau 30 giây;
mã dùng thành công bị hủy. Mã thử nghiệm chỉ hiển thị trong chế độ fake, không gửi email.

Phiên fake sống 24 giờ. Phiên API dùng `expiresAt` từ server. Nội dung phiên được
mã hóa AES-GCM bằng khóa Android Keystore, dùng IV mới mỗi lần ghi. Database bị loại
khỏi Android backup vì khóa không chuyển sang thiết bị khác. Không lưu mật khẩu thô.
Hết hạn, dữ liệu mã hóa hỏng, đổi chế độ fake/API hoặc 401 của request đã xác thực
đều hủy phiên và chuyển về khung Đăng nhập. Xóa phiên theo token cũ không xóa nhầm lần đăng nhập mới.
Ứng dụng kiểm tra hết hạn cả khi đang mở và khi trở lại foreground.

Cài đặt mặc định: ô tô con, giọng nói bật, đóng góp tắt, chỉ Wi-Fi bật. DataStore
cập nhật từng trường và có trạng thái tải/lỗi/thử lại; enum lạ trở về ô tô con.

## Giao diện tài khoản theo mẫu

`AuthScreen` bố trí form riêng theo từng mode. `AuthInputField` dùng nhãn ngoài,
placeholder, icon và ẩn/hiện mật khẩu. `OtpInput` hiển thị 6 ô nhưng dùng một
BasicTextField để hỗ trợ dán mã, xóa và đọc bằng trợ năng. Gửi lại mã gọi
AuthViewModel → AuthRepository; chỉ xóa mã đang nhập khi gửi lại thành công.

`AccountViewModel` kết hợp phiên với SettingsRepository để hiển thị loại xe đã lưu.
ProfileScreen chỉ nhận dữ liệu/callback, quản lý trạng thái mở/đóng hộp thoại
thông tin cá nhân và trợ giúp. Thông tin hiện chỉ đọc, chưa có API sửa hồ sơ.
ContributionSummary chứa các quan sát có biển, trạng thái và thời điểm; UI
ánh xạ thành nhãn trạng thái và ngày, không tự gán kết quả xác minh.
FakeData cung cấp các quan sát minh họa từ danh mục biển mẫu hiện có.

## Cấu hình API

Chạy mặc định không cần backend. Để build chế độ API:

```powershell
.\gradlew.bat :app:assembleDebug -Psigna.useFakeBackend=false -Psigna.apiBaseUrl=http://10.0.2.2:8080/api/v1/
```

URL lấy theo thứ tự: `-Psigna.apiBaseUrl`, biến môi trường `SIGNA_API_BASE_URL`,
`API_BASE_URL` trong `local.properties`, rồi mặc định emulator ở trên.
URL chỉ có host được bổ sung `/api/v1/`; URL đã có path được chuẩn hóa dấu `/` cuối.
Chế độ fake/API là cấu hình lúc build. Build lại không truyền cờ sẽ trở về fake.
Debug cho HTTP để kiểm thử nội bộ; release yêu cầu HTTPS.

AuthApi có login/register/logout/forgot/reset/change-password và users/me theo
`docs/07-api.md`. Đăng nhập đọc `sessionId`, `expiresAt`, `user`; các thao tác
ghi còn lại chỉ yêu cầu HTTP thành công và bỏ qua response body. Các request body
chưa có ví dụ đầy đủ trong đặc tả đang dùng `displayName/email/password`,
`email/otp/newPassword`, `oldPassword/newPassword`; cần đối chiếu khi backend triển khai.
OkHttp chỉ gắn Bearer cho API cùng origin/path và phiên REMOTE hợp lệ, không gắn cho
endpoint công khai hoặc upload ngoài origin. Timeout, JSON lỗi, HTTP 400/401/403/404/
409/422/429/5xx được chuyển thành lỗi UI. Không ghi log token, mật khẩu hoặc OTP.

**Giới hạn nghiệm thu:** API client được kiểm thử bằng MockWebServer; chưa xác minh
đầu cuối với backend thật. Phạm vi “tài khoản chạy với API hoặc dữ liệu giả lập”
của tuần 2 được đáp ứng bằng fake auth đầy đủ ở trên.

## Kiểm tra

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Unit test kiểm tra validation, ViewModel, băm mật khẩu, vòng đời phiên và HTTP giả lập
(serialize DTO, lỗi, timeout, header, 401 và chống xóa phiên mới).
Instrumented test kiểm tra Room trên file thật, Android Keystore, mở lại phiên/cache,
đăng ký trùng, OTP sai/hết hạn/gửi lại, đặt lại/đổi mật khẩu, đăng xuất và DataStore.
Kiểm thử lưu trữ chạy trên Android để sử dụng đúng hành vi filesystem của nền tảng.

Mốc bản đồ đầu tiên và phần cần key/API theo vùng được ghi riêng tại [MAP_SETUP.md](MAP_SETUP.md).

## Camera thật

`vision/camera/CameraPreviewSession` sở hữu CameraX Preview và nhả tài nguyên theo
vòng đời màn hình. `ui/drive/ActiveDriveRoute` xin quyền và điều khiển tạm dừng;
`ActiveDriveScreen` chỉ hiển thị. Chưa có ImageAnalysis hoặc mô hình nhận diện.
Thiết kế và hướng dẫn kiểm tra nằm trong [CAMERA_SETUP.md](CAMERA_SETUP.md).
