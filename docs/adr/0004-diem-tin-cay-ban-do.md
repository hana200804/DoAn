# ADR-0004: Gom cụm quan sát và điểm tin cậy Beta cho bản đồ biển báo

**Trạng thái:** Proposed

## Bối cảnh

Nhiều xe đi qua cùng một biển sẽ gửi nhiều quan sát. Hệ thống cần quyết định: các quan sát nào là cùng một biển, biển đó có thật không, đúng mã gì, và khi nào cần con người can thiệp. Quan sát có thể sai do mô hình, do GPS lệch, hoặc do người dùng cố ý gửi sai. Quản trị viên không thể duyệt từng biển khi số quan sát tăng.

## Quyết định

Gom quan sát thành **cụm biển** theo khoảng cách, nhóm biển và hướng đi; tính **điểm tin cậy** của mỗi cụm bằng kỳ vọng hậu nghiệm của phân phối Beta, với trọng số quan sát theo xác suất mô hình, uy tín người gửi và chống lặp. Cụm tự chuyển `VERIFIED` khi điểm ≥ 0,8 và có ít nhất 2 người khác nhau; chỉ cụm `CONFLICT` cần quản trị viên. Chi tiết công thức: [Thiết kế AI §8](../04-thiet-ke-ai.md#8-gom-cụm-và-điểm-tin-cậy).

## Các phương án đã xem xét

### Phương án A: Khử trùng theo bán kính cố định + duyệt tay

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp |
| Chi phí vận hành | Cao — mọi trường hợp nghi trùng đều cần quản trị viên |
| Khả năng mở rộng | Kém |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** dễ cài đặt, dễ giải thích.

**Nhược điểm:** một quan sát sai đầu tiên được công nhận ngay; không dùng được thông tin "nhiều người cùng thấy"; khối lượng duyệt tay tăng theo số quan sát.

### Phương án B: Đếm số người thấy

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp |
| Chi phí vận hành | Thấp |
| Khả năng mở rộng | Tốt |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** đơn giản, chống được một người gửi sai.

**Nhược điểm:** bỏ qua độ chắc chắn của mô hình; không xử lý được mâu thuẫn giữa các mã; không có cách biểu diễn "biển có thể đã mất".

### Phương án C: Gom cụm + điểm tin cậy Beta có trọng số (chọn)

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình |
| Chi phí vận hành | Thấp — quản trị viên chỉ xử lý mâu thuẫn |
| Khả năng mở rộng | Tốt |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** kết hợp được xác suất mô hình, số người, uy tín và quan sát âm trong một công thức giải thích được; tự nhiên biểu diễn mâu thuẫn và biển biến mất.

**Nhược điểm:** có tham số cần hiệu chỉnh (bán kính, ngưỡng, trọng số uy tín); cần quan sát âm từ thiết bị.

## Phân tích đánh đổi

Phương án C phức tạp hơn A và B nhưng vẫn là một công thức đóng, giải thích được trong báo cáo. Điều kiện "ít nhất 2 người khác nhau" của B được giữ lại trong C như một ràng buộc chống gian lận. Phương án A được dùng làm **đối chứng** để đánh giá bằng số liệu ([Kiểm thử §6](../08-kiem-thu.md#6-đánh-giá-bản-đồ-tự-xác-minh)).

## Hệ quả

- Dễ hơn: giảm việc duyệt tay; cảnh báo được cả biển bị che khi xe khác đã thấy; phát hiện biển biến mất.
- Khó hơn: kiểm thử cần dữ liệu từ nhiều lượt đi; phải mô phỏng quan sát sai để kiểm tra độ bền.
- Cần xem lại: các ngưỡng sau khi có dữ liệu thực địa.

## Việc cần làm

1. [ ] Cài đặt gom cụm và điểm tin cậy trong sign-service, có unit test cho từng trạng thái
2. [ ] Cài đặt phương án đối chứng A để so sánh
3. [ ] Thêm quan sát âm vào ứng dụng di động
