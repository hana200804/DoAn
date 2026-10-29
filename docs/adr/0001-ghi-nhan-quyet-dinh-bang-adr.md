# ADR-0001: Ghi nhận quyết định kiến trúc bằng ADR

**Trạng thái:** Proposed

## Bối cảnh

Dự án có nhiều thành phần (ứng dụng di động, ba service Java, một service Python, web quản trị, phần huấn luyện mô hình) và nhiều lựa chọn kỹ thuật có đánh đổi. Nếu lý do lựa chọn chỉ nằm trong trao đổi miệng, thành viên khác khó hiểu vì sao hệ thống được thiết kế như vậy, và báo cáo đồ án thiếu căn cứ khi trình bày lựa chọn.

## Quyết định

Mỗi quyết định ảnh hưởng nhiều thành phần được ghi thành một ADR trong `docs/adr/`, theo [mẫu](template.md), đánh số tăng dần, và được thảo luận qua Pull Request.

## Các phương án đã xem xét

### Phương án A: ADR dạng Markdown trong repo

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp |
| Chi phí | Không |
| Khả năng mở rộng | Tốt — mỗi quyết định một file, có lịch sử Git |
| Mức quen thuộc với nhóm | Cao — cùng quy trình Pull Request như mã nguồn |

**Ưu điểm:** nằm cạnh mã nguồn; review được; dùng lại trực tiếp cho báo cáo.

**Nhược điểm:** cần kỷ luật để viết trước khi code.

### Phương án B: Ghi chú trong wiki hoặc tài liệu chung

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp |
| Chi phí | Không |
| Khả năng mở rộng | Trung bình — dễ lạc trang, không gắn với thay đổi mã |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** dễ viết.

**Nhược điểm:** không có review, không gắn với phiên bản mã nguồn.

## Phân tích đánh đổi

Phương án A tốn thêm một chút công khi ra quyết định nhưng giữ được lịch sử và lý do ngay trong repo — đúng thứ báo cáo đồ án cần trình bày.

## Hệ quả

- Dễ hơn: giải thích thiết kế cho thành viên mới và hội đồng; viết chương thiết kế của báo cáo.
- Khó hơn: phải viết ADR trước khi thay đổi lớn.
- Cần xem lại: nếu số ADR lớn, thêm chỉ mục theo thành phần.

## Việc cần làm

1. [ ] Cả nhóm review các ADR 0002–0006 và chuyển sang `Accepted` hoặc chỉnh sửa
