# Camera lái xe

## Phạm vi

- CameraX 1.6.1, camera sau, Preview trực tiếp. Không yêu cầu backend, VietMap key hoặc mô hình AI.
- Chỉ xin quyền CAMERA sau khi người dùng bấm **Lái xe → Bắt đầu lái xe**. Từ chối vẫn có thể kết thúc và dùng các phần còn lại.
- Có cấp lại quyền, mở cài đặt, tạm dừng, bật lại và thử lại khi lỗi. Khi quay lại từ cài đặt, app kiểm tra lại quyền.
- Không mở camera ở tab Lái xe trước khi bắt đầu. Nhả Preview khi tạm dừng, màn hình mất RESUMED, kết thúc hoặc Composable bị hủy. Quay lại foreground thì mở lại nếu người dùng chưa tạm dừng.
- Dùng trạng thái STREAMING của PreviewView để báo có hình; không báo thành công ngay khi bind. Nếu không nhận được hình trong 15 giây, hiện lỗi để thử lại.
- Ưu tiên độ phân giải 1280 × 720, cho phép CameraX chọn kích thước gần phù hợp với thiết bị. Preview dùng FILL_CENTER, có thể cắt bớt mép ảnh để lấp khung.
- Theo dõi xoay display; Activity tái tạo sẽ tạo lại session. Giữ màn hình sáng chỉ khi Preview đang stream.
- Chưa chụp/ghi video, lưu ảnh, upload, chạy detector, vẽ box, tích hợp tốc độ GPS vào màn Lái xe hoặc phát cảnh báo. Các giá trị tốc độ/giới hạn chưa biết hiển thị `--`.

## Cấu trúc

```text
ui/drive/
├── DriveScreen.kt                    # Trang chuẩn bị, chưa bật camera
├── ActiveDriveRoute.kt               # Xin quyền, trạng thái thao tác, mở Settings
├── ActiveDriveScreen.kt              # Bố cục, trạng thái, nút điều khiển
└── components/CameraPreview.kt       # Adapter PreviewView → Compose

vision/camera/
├── CameraPreviewStatus.kt            # Trạng thái thiết bị
└── CameraPreviewSession.kt           # CameraX, lifecycle, rotation, nhả tài nguyên
```

Session không nằm trong ViewModel hoặc singleton Hilt vì giữ View và LifecycleOwner của màn hình. Chỉ unbind Preview do session sở hữu, không gọi unbindAll làm ảnh hưởng use case khác. Callback bất đồng bộ đến sau khi đóng session không được mở lại camera.

Khi có AI, bổ sung ImageAnalysis/analyzer tại lớp vision, cùng session/camera và với chiến lược bỏ frame cũ; chuyển kết quả nhận diện về state UI. Không đặt xử lý ảnh/mô hình trong ActiveDriveScreen. Chưa cần tạo analyzer rỗng ở mốc này.

## Chạy và kiểm tra

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Kiểm thử thiết bị `CameraPreviewLifecycleTest` cần thiết bị/emulator có camera sau. Test kiểm tra frame STREAMING và CameraManager availability khi tạm dừng, bật lại, background, foreground và rời preview. Thiết bị không có camera sau sẽ bỏ qua test này.

Chạy riêng test sau khi build (chọn đúng thiết bị bằng `adb -s ...` nếu có nhiều thiết bị):

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.signa.app.vision.camera.CameraPreviewLifecycleTest com.signa.app.test/androidx.test.runner.AndroidJUnitRunner
```

### Kết quả tại mốc camera

- Assemble debug và APK androidTest thành công; 50 unit test đạt; lint 0 lỗi, 58 cảnh báo.
- `CameraPreviewLifecycleTest`: 1 test đạt trên emulator API 36.1 với camera sau emulated. Đã xác nhận tài nguyên camera mở/đóng qua CameraManager, không chỉ kiểm tra nhãn UI.
- Luồng UI khách: từ chối rồi cấp lại quyền; có hình camera; tạm dừng/bật lại; xoay ngang/dọc; Kết thúc/Back rồi bắt đầu phiên mới đều qua. Từ chối hai lần → mở Settings → cho phép khi dùng app → trở lại có hình.
- Không có lỗi AndroidRuntime trong phiên thử. Chưa thử camera vật lý hoặc đo hiệu năng trên điện thoại thật. Hình màu trên ảnh nghiệm thu là feed camera của emulator, không phải kết quả nhận diện AI.

Kịch bản kiểm tra thủ công:

1. Vào chế độ khách, mở Lái xe: chưa xin quyền, chưa mở camera.
2. Bắt đầu → từ chối quyền: có hướng dẫn, nút cấp quyền/cài đặt và kết thúc; không crash.
3. Cấp quyền khi dùng ứng dụng: có hình camera và nhãn hoạt động.
4. Tạm dừng: hình được che, camera được nhả. Bật lại: có hình mới.
5. Home → mở lại app; xoay màn hình; bấm Back hoặc Kết thúc; mở lại phiên mới. Kiểm tra tài nguyên camera và không lỗi vòng đời.
6. Từ chối nhiều lần → mở Settings, cấp quyền rồi trở lại. Chế độ khách phải tiếp tục dùng được.
7. Trên điện thoại thật: thử camera đang bị ứng dụng khác chiếm, chính sách thiết bị chặn camera, màn hình khóa, chất lượng ảnh, xoay 180° và nhiệt/pin. Các trường hợp này không được coi là đã nghiệm thu chỉ qua emulator.

## Tham khảo

- [CameraX Preview](https://developer.android.com/media/camera/camerax/preview)
- [CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera)
- [CameraState và lỗi camera](https://developer.android.com/reference/androidx/camera/core/CameraState)
