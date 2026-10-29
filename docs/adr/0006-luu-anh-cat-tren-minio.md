# ADR-0006: Chỉ gửi và lưu ảnh cắt vùng biển, đặt trên MinIO

**Trạng thái:** Proposed

## Bối cảnh

Quan sát gửi lên server cần kèm hình ảnh để: quản trị viên kiểm tra ca mâu thuẫn, ai-service phân loại lại ca khó và đọc biển phụ, và vòng lặp dữ liệu có ảnh để gán nhãn. Khung hình đầy đủ chứa người đi đường, biển số xe và tốn băng thông. Lưu ảnh dạng base64 trong cơ sở dữ liệu quan hệ làm phình CSDL và chậm truy vấn.

## Quyết định

- App chỉ gửi **ảnh cắt vùng biển** (và ảnh cắt biển phụ nếu có), không gửi khung hình đầy đủ.
- Ảnh được tải **thẳng từ app lên MinIO** qua presigned URL do sign-service cấp; CSDL chỉ lưu khoá đối tượng.
- Ảnh cắt không được chọn vào nhiệm vụ gán nhãn bị xoá theo chính sách lưu giữ cấu hình được.

## Các phương án đã xem xét

### Phương án A: Base64 trong CSDL

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp |
| Chi phí | Cao — CSDL phình nhanh, sao lưu nặng |
| Khả năng mở rộng | Kém |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** một nơi lưu trữ.

**Nhược điểm:** tăng kích thước mỗi request 33%; truy vấn và sao lưu chậm.

### Phương án B: Khung hình đầy đủ trên object storage

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình |
| Chi phí | Cao — băng thông và dung lượng |
| Khả năng mở rộng | Trung bình |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** dữ liệu dùng được để huấn luyện lại cả detector.

**Nhược điểm:** rủi ro riêng tư lớn; vượt NFR-05 về dữ liệu di động.

### Phương án C: Chỉ ảnh cắt, tải trực tiếp lên MinIO bằng presigned URL (chọn)

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình — thêm bước cấp URL |
| Chi phí | Thấp — mỗi ảnh cắt vài KB |
| Khả năng mở rộng | Tốt — ảnh không đi qua service Java |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** riêng tư; ít băng thông; CSDL gọn; service không phải trung chuyển ảnh.

**Nhược điểm:** không có khung hình đầy đủ để cải thiện detector từ dữ liệu người dùng.

## Phân tích đánh đổi

Riêng tư và dữ liệu di động (NFR-05, NFR-09) quan trọng hơn khả năng huấn luyện lại detector từ dữ liệu người dùng. Detector được cải thiện bằng video thực địa do nhóm tự thu thập ([Dữ liệu §4](../05-du-lieu.md#4-video-thực-địa)).

## Hệ quả

- Dễ hơn: đáp ứng riêng tư; giảm tải service; sao lưu CSDL nhẹ.
- Khó hơn: app xử lý luồng hai bước (tạo lô → tải ảnh → xác nhận); cấu hình MinIO truy cập được từ ngoài qua nginx.
- Cần xem lại: nếu cần cải thiện detector từ dữ liệu người dùng, cân nhắc chế độ tình nguyện gửi khung hình đã làm mờ.

## Việc cần làm

1. [ ] Endpoint tạo lô quan sát trả presigned URL ([API §5](../07-api.md#5-sign-service--người-lái))
2. [ ] Bucket `sign-crops` và chính sách lưu giữ trên MinIO
