# 01 · Tổng quan

## 1. Mục tiêu

Xây dựng hệ thống giúp người lái ô tô không bỏ lỡ biển báo: điện thoại gắn trên xe nhận diện biển báo theo QCVN 41 ngay trên máy, cảnh báo bằng giọng nói đúng lúc và đúng điều kiện áp dụng, đồng thời đóng góp kết quả vào một bản đồ biển báo cộng đồng có cơ chế tự xác minh.

## 2. Bài toán và hướng giải quyết

| Vấn đề | Hướng giải quyết | Chi tiết |
| --- | --- | --- |
| Gửi ảnh lên server để nhận diện gây trễ, tốn dữ liệu, mất sóng là dừng | Nhận diện ngay trên điện thoại | [ADR-0002](adr/0002-nhan-dien-tren-thiet-bi.md) |
| Nhận diện từng khung hình riêng lẻ dễ báo sai | Theo dõi biển qua nhiều khung, bỏ phiếu nhãn | [Thiết kế AI §4](04-thiet-ke-ai.md#4-theo-dõi-đa-khung-hình) |
| Chỉ hiển thị biển là chưa đủ — người lái cần được nhắc đúng lúc | Máy trạng thái cảnh báo và giọng nói | [Thiết kế AI §5](04-thiet-ke-ai.md#5-máy-trạng-thái-cảnh-báo) |
| Nhiều biển giống nhau; biển tốc độ có nhiều mức giá trị | Mô hình hai tầng: phát hiện + phân loại | [ADR-0003](adr/0003-mo-hinh-hai-tang.md) |
| Biển phụ có điều kiện (khung giờ, loại xe) dễ hiểu sai | Đọc biển phụ bằng OCR, áp điều kiện khi cảnh báo | [Thiết kế AI §6](04-thiet-ke-ai.md#6-đọc-biển-phụ) |
| Khử trùng biển theo bán kính cố định và duyệt tay không mở rộng được | Gom cụm quan sát và điểm tin cậy | [ADR-0004](adr/0004-diem-tin-cay-ban-do.md) |
| Mô hình không tự tốt lên sau khi phát hành | Vòng lặp dữ liệu và cập nhật mô hình qua mạng | [Thiết kế AI §9](04-thiet-ke-ai.md#9-vòng-lặp-dữ-liệu) |

## 3. Tác nhân

| Tác nhân | Mô tả |
| --- | --- |
| Người lái | Dùng ứng dụng di động khi lái ô tô: nhận cảnh báo, xem bản đồ biển, đóng góp quan sát |
| Quản trị viên | Dùng web quản trị: xử lý ca mâu thuẫn, báo cáo sai, gán nhãn ảnh khó, quản lý danh mục biển |

## 4. Phạm vi

| Trong phạm vi | Ngoài phạm vi |
| --- | --- |
| Biển cấm, hiệu lệnh, nguy hiểm và cảnh báo, biển tốc độ theo từng mức | Biển chỉ dẫn dạng bảng lớn, vạch kẻ đường, đèn tín hiệu |
| Biển phụ ghi khung giờ, loại xe theo mẫu phổ biến | Biển phụ có nội dung tự do |
| Android, điện thoại gắn cố định trên ô tô | iOS, xe máy, camera hành trình chuyên dụng |
| Thử nghiệm thực địa trên một số tuyến đường | Triển khai cho người dùng thật quy mô lớn |
| Cảnh báo hỗ trợ người lái | Điều khiển hoặc can thiệp vào phương tiện |

## 5. Thành phần hệ thống

| Thành phần | Thư mục | Vai trò |
| --- | --- | --- |
| Ứng dụng di động | [`mobile/`](../mobile/) | Nhận diện, theo dõi, cảnh báo, bản đồ, gửi quan sát |
| auth-service | [`backend/`](../backend/) | Tài khoản, phiên đăng nhập, phân quyền |
| map-service | [`backend/`](../backend/) | Tìm kiếm địa chỉ, chỉ đường qua VietMap API |
| sign-service | [`backend/`](../backend/) | Quan sát, gom cụm, điểm tin cậy, báo cáo sai, gán nhãn, phiên bản mô hình |
| ai-service | [`ai/`](../ai/) | OCR biển phụ, phân loại lại ảnh khó |
| Huấn luyện mô hình | [`ai/`](../ai/) | Dữ liệu, huấn luyện, đánh giá, xuất TFLite |
| Web quản trị | [`web-admin/`](../web-admin/) | Ca mâu thuẫn, báo cáo sai, gán nhãn, danh mục, thống kê |
| Hạ tầng | [`infra/`](../infra/) | Docker Compose, cấu hình môi trường |

Kiến trúc chi tiết: [03 · Kiến trúc hệ thống](03-kien-truc.md).

## 6. Các chủ đề cần hoàn thành

| # | Chủ đề | Đầu ra chính |
| --- | --- | --- |
| M1 | Nền tảng hệ thống | Khung mã nguồn các thành phần, hạ tầng Docker, CI, tài khoản người dùng |
| M2 | Dữ liệu | Danh mục nhãn, tập huấn luyện, tập kiểm thử thực địa |
| M3 | Mô hình nhận diện | Detector và classifier đạt chỉ tiêu, bản TFLite INT8 |
| M4 | Nhận diện và cảnh báo trên thiết bị | Màn hình lái xe, tracker, máy trạng thái cảnh báo |
| M5 | Bản đồ biển báo cộng đồng | Gửi quan sát, gom cụm, điểm tin cậy, bản đồ và dẫn đường |
| M6 | Quản trị và vòng lặp dữ liệu | Web quản trị, ai-service, gán nhãn, cập nhật mô hình |
| M7 | Kiểm thử và đánh giá | Kết quả đánh giá mô hình và thực địa, bản demo, báo cáo |

Chi tiết từng chủ đề: [09 · Hạng mục công việc](09-hang-muc-cong-viec.md).
