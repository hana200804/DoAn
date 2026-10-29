# Tài liệu dự án Signa

Toàn bộ tài liệu thiết kế của dự án. Tài liệu được viết trước khi code và cập nhật trong cùng Pull Request khi thiết kế thay đổi.

## Danh mục

| # | Tài liệu | Nội dung |
| --- | --- | --- |
| 01 | [Tổng quan](01-tong-quan.md) | Mục tiêu, bài toán, phạm vi, thành phần, các chủ đề chính |
| 02 | [Đặc tả yêu cầu](02-dac-ta-yeu-cau.md) | Tác nhân, user story, yêu cầu chức năng và phi chức năng, tiêu chí chấp nhận |
| 03 | [Kiến trúc hệ thống](03-kien-truc.md) | Thành phần, luồng xử lý, triển khai, vấn đề xuyên suốt |
| 04 | [Thiết kế AI](04-thiet-ke-ai.md) | Phát hiện, phân loại, theo dõi, cảnh báo, biển phụ, định vị, điểm tin cậy, vòng lặp dữ liệu |
| 05 | [Dữ liệu và gán nhãn](05-du-lieu.md) | Nguồn dữ liệu, danh mục nhãn, hướng dẫn gán nhãn, phân chia, phiên bản |
| 06 | [Cơ sở dữ liệu](06-co-so-du-lieu.md) | Lược đồ, quan hệ, chỉ mục, truy vấn không gian |
| 07 | [API](07-api.md) | Quy ước, REST, gRPC, định dạng lỗi |
| 08 | [Kiểm thử](08-kiem-thu.md) | Chiến lược, đánh giá mô hình, thử nghiệm thực địa, tiêu chí phát hành |
| 09 | [Hạng mục công việc](09-hang-muc-cong-viec.md) | Các chủ đề cần hoàn thành, đầu ra, tiêu chí hoàn thành |
| — | [ADR](adr/) | Các quyết định kiến trúc và lý do |

## Quy ước

- Viết bằng tiếng Việt; thuật ngữ kỹ thuật giữ tiếng Anh khi không có từ thông dụng.
- Sơ đồ dùng [Mermaid](https://mermaid.js.org/) để GitHub hiển thị trực tiếp và sửa được bằng văn bản.
- Mã yêu cầu (`FR-xx`, `NFR-xx`) chỉ được định nghĩa trong [Đặc tả yêu cầu](02-dac-ta-yeu-cau.md); tài liệu khác chỉ tham chiếu.
- Thay đổi một quyết định đã chốt: viết ADR mới, ADR cũ chuyển trạng thái *Superseded*.

## Thuật ngữ

| Thuật ngữ | Nghĩa |
| --- | --- |
| QCVN 41 | QCVN 41:2024/BGTVT — Quy chuẩn kỹ thuật quốc gia về báo hiệu đường bộ, ban hành kèm Thông tư 51/2024/TT-BGTVT |
| Nhóm biển | Nhóm theo hình dạng và màu: cấm, hiệu lệnh, nguy hiểm, biển phụ, khác — đầu ra của tầng phát hiện |
| Mã biển | Mã theo QCVN 41, ví dụ `P.123a`; biển tốc độ có hậu tố giá trị, ví dụ `P.127_50` |
| Quan sát | Một lần một xe nhìn thấy (hoặc đi qua mà không thấy) một biển: mã, vị trí ước lượng, xác suất, ảnh cắt |
| Cụm biển | Một biển báo thật trên đường, gộp từ nhiều quan sát |
| Điểm tin cậy | Mức tin rằng cụm biển có thật và đúng mã, tính từ các quan sát |
| Track | Chuỗi phát hiện của cùng một biển qua nhiều khung hình liên tiếp |
| Detector / Classifier | Tầng phát hiện vùng biển / tầng phân loại mã biển |
| Label map | Danh mục nhóm và mã biển mà mô hình hỗ trợ, có số phiên bản |
| On-device | Chạy trên điện thoại, không cần server |
| OTA | Cập nhật mô hình qua mạng (over-the-air) |
