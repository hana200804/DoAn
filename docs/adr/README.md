# Quyết định kiến trúc (ADR)

Mỗi quyết định ảnh hưởng nhiều thành phần được ghi thành một Architecture Decision Record theo [mẫu](template.md): bối cảnh, các phương án, đánh đổi và hệ quả.

## Danh sách

| ADR | Quyết định | Trạng thái |
| --- | --- | --- |
| [0001](0001-ghi-nhan-quyet-dinh-bang-adr.md) | Ghi nhận quyết định kiến trúc bằng ADR | Proposed |
| [0002](0002-nhan-dien-tren-thiet-bi.md) | Nhận diện biển báo trên thiết bị, server xử lý ca khó | Proposed |
| [0003](0003-mo-hinh-hai-tang.md) | Mô hình nhận diện hai tầng: phát hiện theo nhóm + phân loại mã | Proposed |
| [0004](0004-diem-tin-cay-ban-do.md) | Gom cụm quan sát và điểm tin cậy Beta cho bản đồ biển báo | Proposed |
| [0005](0005-microservices-grpc.md) | Tách service theo nghiệp vụ, gRPC giữa sign-service và ai-service | Proposed |
| [0006](0006-luu-anh-cat-tren-minio.md) | Chỉ gửi và lưu ảnh cắt vùng biển, đặt trên MinIO | Proposed |

## Quy trình

1. Sao chép [template.md](template.md) thành `NNNN-ten-ngan.md`, trạng thái `Proposed`.
2. Mở Pull Request từ nhánh `docs/adr-NNNN-ten-ngan`.
3. Thảo luận trong PR; khi cả nhóm đồng ý thì chuyển `Accepted` và merge.
4. Không sửa nội dung một ADR đã `Accepted`. Muốn thay đổi thì viết ADR mới và chuyển ADR cũ thành `Superseded by ADR-NNNN`.
