# ADR-0003: Mô hình nhận diện hai tầng — phát hiện theo nhóm, phân loại mã

**Trạng thái:** Proposed

## Bối cảnh

Danh mục QCVN 41 có nhiều mã biển, trong đó nhiều biển rất giống nhau (cùng hình tròn viền đỏ, khác ký hiệu bên trong; biển tốc độ chỉ khác con số). Số mẫu giữa các mã chênh lệch lớn: biển phổ biến có hàng trăm mẫu, biển hiếm chỉ vài mẫu. Mô hình phải chạy trên điện thoại (ADR-0002), nên không thể dùng detector lớn để bù cho số lớp nhiều.

## Quyết định

Tách nhận diện thành hai tầng:

1. **Detector** (YOLOv8n) chỉ phát hiện vùng biển và phân 5 **nhóm** theo hình dạng, màu.
2. **Classifier** (YOLOv8n-cls) phân loại **mã biển** trên ảnh cắt 96 × 96 của từng box.

## Các phương án đã xem xét

### Phương án A: Một detector cho toàn bộ mã biển

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp — một mô hình |
| Chi phí tính toán | Một lần suy luận mỗi khung |
| Khả năng mở rộng | Kém — thêm mã phải huấn luyện lại toàn bộ detector, cần ảnh đầy đủ có box |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** đơn giản, quy trình huấn luyện có sẵn.

**Nhược điểm:** lớp hiếm bị lấn át; biển nhỏ trong khung hình 640 px khó phân biệt chi tiết; không tận dụng được ảnh cắt từ người dùng.

### Phương án B: Detector theo nhóm + classifier theo mã (chọn)

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình — hai mô hình, cần ghép kết quả |
| Chi phí tính toán | Detector mỗi khung + classifier cho một số ảnh cắt |
| Khả năng mở rộng | Tốt — thêm mã chỉ cần huấn luyện lại classifier bằng ảnh cắt |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** mỗi nhóm có nhiều mẫu nên detector ổn định; classifier nhìn ảnh cắt ở độ phân giải phù hợp; dùng được ảnh cắt từ vòng lặp dữ liệu và ảnh tổng hợp; hai tầng kiểm tra chéo nhau (cờ `MISMATCH`).

**Nhược điểm:** thêm độ trễ của classifier; lỗi detector không được classifier sửa.

### Phương án C: Detector theo nhóm + so khớp mẫu biển

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình |
| Chi phí tính toán | Thấp |
| Khả năng mở rộng | Tốt về số mã |
| Mức quen thuộc với nhóm | Thấp |

**Ưu điểm:** thêm mã không cần huấn luyện.

**Nhược điểm:** kém bền với góc nghiêng, mờ, ánh sáng xấu — đúng các điều kiện thực tế khi lái xe.

## Phân tích đánh đổi

Phương án B đổi một ít thời gian suy luận lấy độ chính xác trên các mã giống nhau và khả năng mở rộng danh mục. Chi phí classifier được kiểm soát bằng lịch chạy theo track ([Thiết kế AI §4](../04-thiet-ke-ai.md#4-theo-dõi-đa-khung-hình)). Phương án A vẫn được huấn luyện làm đối chứng để chứng minh lựa chọn bằng số liệu.

## Hệ quả

- Dễ hơn: thêm mã biển; huấn luyện lại từ ảnh cắt của người dùng; xử lý biển tốc độ theo giá trị.
- Khó hơn: quản lý hai mô hình và một label map dùng chung; đo độ trễ tổng.
- Cần xem lại: nếu classifier không cải thiện so với phương án A trên tập test, quay lại phương án A.

## Việc cần làm

1. [ ] Thí nghiệm so sánh A và B trên cùng tập test (recall theo lớp, mAP, FPS)
2. [ ] Định nghĩa label map gồm nhóm và mã ([Dữ liệu §2](../05-du-lieu.md#2-danh-mục-nhãn))
