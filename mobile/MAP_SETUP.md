# Bản đồ và xem trước tuyến đường

## Phần đã triển khai

- VietMap Android SDK 4.1.1 trong Compose qua AndroidView, quản lý lifecycle và giữ logo/attribution.
- Bản đồ nền thật; biển tốc độ mẫu từ Room hiển thị bằng GeoJSON + SymbolLayer. Chạm biển mở thẻ, từ thẻ mở chi tiết.
- Hai chế độ tìm kiếm: **Địa chỉ** gọi Autocomplete v4/Place v4; **Biển báo** tìm không dấu trong cache và lọc 50/60/80.
- Chọn gợi ý hoặc nhấn giữ bản đồ để đặt điểm đến. Nhấn **Xem tuyến đường** lấy vị trí thiết bị mới rồi gọi Route v4, vẽ hình học đường thật và hiển thị khoảng cách/thời gian dự kiến. **Xem toàn tuyến** thu phóng theo tuyến.
- Ô tô con và xe tải; xe tải cần nhập trọng lượng kg. Cài đặt xe buýt không tự chuyển sang ô tô: người dùng phải chọn loại xe được hỗ trợ.
- Khách và người đăng nhập dùng chung luồng bản đồ. Từ chối quyền/GPS tắt/timeout được báo riêng; không dùng tọa độ biển mẫu làm điểm xuất phát.
- Hủy truy vấn cũ khi nhập hoặc chọn điểm khác; debounce tìm kiếm 450 ms, tối thiểu 3 ký tự. Thay đổi xe, điểm đến hoặc tính lại sẽ xóa kết quả tuyến cũ.
- Lỗi mạng, thiếu key, quyền dịch vụ, hạn mức, không tìm thấy đường và dữ liệu không hợp lệ có thông báo riêng. Không tự tạo đường thẳng hoặc số liệu giả khi API thất bại.

## Cấu hình key

Trong `mobile/local.properties`, giữ dòng `sdk.dir` và thêm key của bạn:

```properties
VIETMAP_API_KEY=key_ban_do
VIETMAP_SEARCH_API_KEY=key_tim_kiem_va_chi_duong
```

Nếu dịch vụ chỉ đường dùng key riêng, thêm `VIETMAP_ROUTE_API_KEY`. Khi không có dòng này, app dùng key search cho Route. Khi không có key search, app dùng key bản đồ cho tìm kiếm. Có thể dùng biến môi trường cùng tên; key phải được cấp quyền cho đúng dịch vụ.

Build lại sau khi đổi key:

```powershell
.\gradlew.bat :app:assembleDebug :app:installDebug
```

`local.properties` không được commit. Không đưa key/URL chứa key vào log hoặc chat. Client VietMap tách khỏi client tài khoản, không gửi Bearer token và không theo redirect. **Key đọc qua BuildConfig vẫn nằm trong APK**; cách này không biến key thành bí mật trên Android. Khi phát hành, cấu hình giới hạn key theo khả năng nhà cung cấp và cân nhắc proxy backend cho Web API.

## Trách nhiệm mã nguồn

| Phần | Vị trí trong `app/src/main/java/com/signa/app/` |
| --- | --- |
| Tọa độ, điểm đến, tuyến, lỗi, lựa chọn xe | `domain/model/GeoPoint.kt`, `MapDirections.kt` |
| Hợp đồng tìm kiếm/chỉ đường | `domain/repository/DirectionsRepository.kt` |
| Retrofit, DTO, key interceptor, giải mã polyline | `data/remote/vietmap/` |
| Gọi dịch vụ, kiểm tra phản hồi, ánh xạ lỗi | `data/repository/VietMapDirectionsRepository.kt` |
| Cấp dependency | `di/DirectionsModule.kt` |
| Lấy vị trí thiết bị | `data/location/FusedLocationRepository.kt` |
| State tìm/lọc/chọn biển và định vị | `ui/map/MapViewModel.kt` |
| State tìm địa chỉ/chọn điểm/tính tuyến, hủy tác vụ cũ | `ui/map/DirectionsViewModel.kt` |
| Xin quyền và nối state với UI | `ui/map/MapRoute.kt` |
| Compose và thẻ kết quả | `ui/map/MapScreen.kt`, `ui/map/components/DirectionsPanels.kt` |
| Render SDK, line/marker, camera và lifecycle | `ui/map/components/VietMapSurface.kt` |

Không gọi Retrofit/GPS bên trong Composable. Room schema 2 và migration 1 → 2 thuộc mốc bản đồ trước, không thay đổi ở phần chỉ đường này.

## Kiểm chứng

- 50 unit test: gồm lỗi HTTP, key riêng/dùng chung, không gửi token tài khoản, ref ID, tọa độ, polyline, thời gian milliseconds, trọng lượng xe tải, redirect và chống kết quả cũ ghi đè state.
- Build debug thành công; lint 0 lỗi, 54 cảnh báo. 13 kiểm thử Android Room/Keystore/DataStore đã qua ở mốc trước; chưa chạy lại trong phần này vì không sửa schema/lưu trữ.
- Đã gọi Autocomplete, Place và Route thành công với key cấu hình trên máy; không lưu giá trị key trong báo cáo.
- Đã kiểm tra xem toàn tuyến thấy cả hai đầu tuyến phía trên thẻ thông tin; xe tải thiếu trọng lượng báo lỗi; nhấn giữ chọn điểm, xóa điểm và chuyển Cài đặt → Bản đồ hoạt động.
- Trên emulator trong chế độ khách: tìm “Hoan Kiem”, chọn Phường Hoàn Kiếm, cấp quyền vị trí và GPS giả lập tại Hà Nội; API trả tuyến 995 m, khoảng 3–4 phút. Đây là kết quả một lần thử, có thể thay đổi theo thời điểm/điểm xuất phát.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Phạm vi còn lại

Hiện là **xem trước tuyến đường**, chưa có dẫn đường từng chặng, đọc rẽ bằng giọng nói, bám vị trí liên tục, tự tính lại khi đi lệch tuyến hoặc nhận diện tự động khi dẫn đường. Chưa đo GPS/độ trễ/pin trên điện thoại thật.

Ba biển mẫu Bình Thạnh có tọa độ minh họa, không phải dữ liệu biển VietMap hoặc biển do backend xác minh. Tải biển backend theo vùng, đồng bộ Room và cảnh báo phía trước thuộc bước tiếp theo. Cache biển không đồng nghĩa đã tải bản đồ/tuyến để sử dụng offline.

## Tài liệu dịch vụ

- [Autocomplete v4](https://maps.vietmap.vn/docs/map-api/autocomplete-version/autocomplete-v4/)
- [Place v4](https://maps.vietmap.vn/docs/map-api/place-v4/)
- [Route v4](https://maps.vietmap.vn/docs/map-api/route-version/route-v4/)
