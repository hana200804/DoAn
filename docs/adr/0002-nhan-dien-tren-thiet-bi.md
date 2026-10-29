# ADR-0002: Nhận diện biển báo trên thiết bị, server xử lý ca khó

**Trạng thái:** Proposed

## Bối cảnh

Cảnh báo biển báo chỉ có giá trị nếu đến trước khi xe đi qua biển. Ở 60 km/h, xe đi 1 m mỗi 60 ms. Nhận diện phía server cần gửi ảnh qua mạng di động: độ trễ dao động lớn, tốn dữ liệu, và ngừng hoạt động khi mất sóng — đúng ở những đoạn đường người lái cần hỗ trợ. Đồng thời, gửi khung hình đầy đủ lên server làm lộ hình ảnh người và phương tiện xung quanh.

Ràng buộc: điện thoại Android tầm trung; mô hình phải nhỏ (NFR-06) và đạt ≥ 15 FPS (NFR-01).

## Quyết định

Chạy toàn bộ chuỗi nhận diện, theo dõi và cảnh báo **trên điện thoại** bằng TensorFlow Lite. Server chỉ nhận **quan sát đã gộp** và ảnh cắt vùng biển, dùng mô hình lớn hơn để **xử lý bất đồng bộ** các ca khó (phân loại lại, đọc biển phụ). Đây là phương án C bên dưới.

## Các phương án đã xem xét

### Phương án A: Nhận diện trên server

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp ở app, trung bình ở server |
| Chi phí | Cao — GPU server, băng thông |
| Khả năng mở rộng | Kém — chi phí tăng theo số xe × FPS |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** dùng được mô hình lớn; cập nhật mô hình chỉ ở server.

**Nhược điểm:** độ trễ mạng, không chạy khi mất sóng, tốn dữ liệu, rủi ro riêng tư.

### Phương án B: Chỉ trên thiết bị

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình |
| Chi phí | Thấp |
| Khả năng mở rộng | Tốt |
| Mức quen thuộc với nhóm | Trung bình — cần học TFLite, lượng tử hoá |

**Ưu điểm:** độ trễ thấp, chạy offline, riêng tư.

**Nhược điểm:** mô hình bị giới hạn kích thước; không có cách xử lý ca khó; không có bản đồ chung.

### Phương án C: Trên thiết bị + server xử lý ca khó (chọn)

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình – cao |
| Chi phí | Thấp — server chỉ xử lý một phần nhỏ quan sát |
| Khả năng mở rộng | Tốt |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** giữ được độ trễ thấp và khả năng offline của B; vẫn có mô hình lớn cho ca khó và dữ liệu cho bản đồ chung.

**Nhược điểm:** hai nơi chạy mô hình, cần quản lý phiên bản mô hình và label map.

## Phân tích đánh đổi

Yêu cầu cốt lõi là cảnh báo đúng lúc và chạy khi mất sóng (NFR-02, NFR-03); phương án A không đáp ứng được. Giữa B và C, C thêm độ phức tạp ở server nhưng đó là thứ cần cho bản đồ cộng đồng (FR-17) và vòng lặp dữ liệu (FR-19). Giới hạn năng lực của mô hình trên thiết bị được bù bằng thiết kế hai tầng ([ADR-0003](0003-mo-hinh-hai-tang.md)) và theo dõi đa khung.

## Hệ quả

- Dễ hơn: cảnh báo không phụ thuộc mạng; chi phí server thấp; giảm rủi ro riêng tư.
- Khó hơn: tối ưu mô hình cho điện thoại (INT8, GPU delegate); đo hiệu năng trên nhiều thiết bị; phân phối mô hình qua mạng.
- Cần xem lại: nếu thiết bị tham chiếu không đạt 15 FPS, cân nhắc giảm kích thước ảnh đầu vào hoặc chỉ chạy classifier khi cần.

## Việc cần làm

1. [ ] Xuất YOLOv8n sang TFLite INT8 và đo FPS trên thiết bị tham chiếu
2. [ ] Định nghĩa định dạng quan sát gửi lên server ([API](../07-api.md))
3. [ ] Thiết kế cơ chế cập nhật mô hình qua mạng ([Kiến trúc §5.4](../03-kien-truc.md#54-cập-nhật-mô-hình))
