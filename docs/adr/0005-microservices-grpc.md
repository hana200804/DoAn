# ADR-0005: Tách service theo nghiệp vụ, gRPC giữa sign-service và ai-service

**Trạng thái:** Proposed

## Bối cảnh

Phần nghiệp vụ (tài khoản, bản đồ, biển báo) phù hợp với Java Spring Boot; phần chạy mô hình phía server (phân loại lại, OCR) phải dùng Python vì hệ sinh thái học sâu. Hệ thống triển khai trên một máy chủ bằng Docker Compose. Các phần có tải và tần suất thay đổi khác nhau: sign-service xử lý quan sát liên tục, auth-service hiếm khi thay đổi.

## Quyết định

- Ba service Java theo ranh giới nghiệp vụ: **auth-service**, **map-service**, **sign-service**; dùng chung thư viện `backend/common` (xác thực phiên, định dạng lỗi).
- Một service Python **ai-service**, không lưu trạng thái.
- sign-service gọi ai-service qua **gRPC**, hợp đồng định nghĩa trong `backend/proto/`.
- Client gọi trực tiếp các service qua nginx theo đường dẫn; không dùng API Gateway riêng.

## Các phương án đã xem xét

### Phương án A: Một ứng dụng Java dạng modular monolith + ai-service Python

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp — một tiến trình Java |
| Chi phí | Thấp |
| Khả năng mở rộng | Trung bình — mở rộng theo cả khối |
| Mức quen thuộc với nhóm | Cao |

**Ưu điểm:** ít cấu hình, debug dễ, giao dịch CSDL đơn giản.

**Nhược điểm:** một lỗi có thể làm sập mọi chức năng; ranh giới dữ liệu dễ bị phá vỡ theo thời gian.

### Phương án B: Ba service Java + ai-service, gRPC nội bộ (chọn)

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Trung bình — nhiều tiến trình, cấu hình Compose |
| Chi phí | Thấp — vẫn chạy trên một máy |
| Khả năng mở rộng | Tốt — mở rộng riêng sign-service |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** ranh giới dữ liệu rõ ràng; mỗi service được phát triển, kiểm thử độc lập; hợp đồng gRPC có kiểu chặt chẽ.

**Nhược điểm:** thêm chi phí vận hành; cần thư viện chung để tránh lặp mã.

### Phương án C: Toàn bộ backend bằng Python

| Tiêu chí | Đánh giá |
| --- | --- |
| Độ phức tạp | Thấp — một ngôn ngữ |
| Chi phí | Thấp |
| Khả năng mở rộng | Trung bình |
| Mức quen thuộc với nhóm | Trung bình |

**Ưu điểm:** dùng chung mã với phần huấn luyện.

**Nhược điểm:** mất các thành phần sẵn có của Spring (bảo mật, JPA, Flyway, Actuator).

## Phân tích đánh đổi

Ranh giới Java–Python là bắt buộc, nên ít nhất phải có hai tiến trình. Tách tiếp phần Java thành ba service tăng chi phí vận hành nhưng giữ được ranh giới dữ liệu rõ ràng và cho phép mở rộng riêng phần chịu tải. gRPC được chọn thay REST cho đường sign-service → ai-service vì có hợp đồng sinh mã cho cả Java và Python, và truyền nhị phân hiệu quả.

**Phương án dự phòng:** nếu chi phí vận hành quá lớn, gộp auth-service và map-service vào sign-service thành modular monolith (phương án A) nhưng giữ nguyên ranh giới package và CSDL.

## Hệ quả

- Dễ hơn: phát triển song song giữa các thành viên; thay mô hình ở ai-service không ảnh hưởng nghiệp vụ.
- Khó hơn: kiểm thử tích hợp nhiều service; truy vết request (cần `X-Request-Id`).
- Cần xem lại: khi thêm service mới, cân nhắc API Gateway.

## Việc cần làm

1. [ ] Tạo Gradle multi-project với `common`, `auth-service`, `map-service`, `sign-service`
2. [ ] Viết `sign_analyzer.proto` và sinh mã cho Java, Python ([API §7](../07-api.md#7-grpc-ai-service))
3. [ ] Docker Compose chạy đủ các service ([infra/README.md](../../infra/README.md))
