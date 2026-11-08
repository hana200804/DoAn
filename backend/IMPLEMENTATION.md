# Bàn giao back-end theo tài liệu

Nhánh `hana200887/backend-theo-docs` được tách từ `dev` tại `e379e96`. Phần bàn giao gồm mã Java, hợp đồng Protobuf để sinh client Java, migration, test và tài liệu trực tiếp mô tả back-end. Không đưa mã Python AI, mobile, web-admin, trọng số/dữ liệu hoặc thay đổi hạ tầng vào nhánh này.

## Đối chiếu yêu cầu

| Căn cứ trong tài liệu | Phần bổ sung hoặc sửa | File chính |
| --- | --- | --- |
| [FR-12, FR-20, NFR-10](../docs/02-dac-ta-yeu-cau.md) | Chặn mật khẩu vượt 72 byte UTF-8 để tránh BCrypt bỏ đuôi; kiểm mật khẩu trước khi dùng OTP; xử lý đăng ký email trùng đồng thời thành 409. | `AuthService`, `AdminBootstrap`, test auth/Redis |
| [API bản đồ và biển báo](../docs/07-api.md) | Kiểm tra toạ độ hữu hạn/WGS84; lọc mã active, nhóm và độ tin cậy trước phân trang. Corridor quét theo lô rồi lọc khoảng cách trước phân trang, không bỏ biển sau ứng viên thứ 5.000. | `MapController`, `SignController`, `SignRepository` |
| [FR-14, FR-17, FR-18](../docs/02-dac-ta-yeu-cau.md) | Kiểm tra lô/quan sát; chống trùng clientId; giới hạn trọng số mỗi người; tuần tự hoá đoạn gom cụm trong MySQL; quan sát âm không làm mới thời điểm thấy biển. | `ObservationService`, `SignDtos`, `SignRepository` |
| [Thiết kế AI §6, §9](../docs/04-thiet-ke-ai.md) và [hợp đồng gRPC](../docs/07-api.md#7-grpc-ai-service) | Client gRPC có deadline; hàng đợi MySQL có lease/retry/generation. Kết quả phân loại lại là gợi ý duyệt. OCR giữ nguồn/thời điểm, không ghi đè điều kiện đã duyệt tay hoặc áp lại kết quả của nhãn đã sửa. | `proto/`, `ai-client/`, `AiClient`, `AiWorker`, `LearningRepository` |
| [FR-19, FR-23](../docs/02-dac-ta-yeu-cau.md) | Chọn ảnh khó và RANDOM 1%; mỗi quan sát tối đa một task. Duyệt nhãn chính/OCR độc lập, lưu datasetVersion, export ảnh/nhãn/box/SHA-256 qua API ADMIN. | `ReviewSampling`, `LearningController`, `LearningService`, `LearningRepository` |
| [FR-15](../docs/02-dac-ta-yeu-cau.md), [thiết kế AI §10](../docs/04-thiet-ke-ai.md#10-phát-hành-mô-hình) | Đăng ký model, kiểm byte/hash artifact, DRAFT/ACTIVE/RETIRED, thẻ chất lượng, kích hoạt/quay lui; con trỏ theo label map và kiểm tương thích phiên bản app. | `LearningService`, `LearningDtos`, `AppVersion`, `CropStorage` |
| [FR-11, FR-25](../docs/02-dac-ta-yeu-cau.md) | Thống kê cá nhân và quản trị: theo ngày, top người góp, ô độ phủ, tỉ lệ tự xác minh, model active. | `LearningController`, `LearningRepository`, `AdminController` |
| [CSDL](../docs/06-co-so-du-lieu.md), [API](../docs/07-api.md) | Migration bổ sung V3–V5 và hợp đồng REST/gRPC khớp với mã bàn giao; không sửa V1/V2 đã có trên dev. | `sign-service/src/main/resources/db/migration/` |

## Migration và điều kiện vận hành

- V3 thêm `label_tasks`, `observation_analysis`, `model_versions`, `model_release` và khoá gom cụm.
- V4 thêm dấu hiệu biển phụ và metadata nguồn/thời điểm/duyệt tay của điều kiện OCR.
- V5 thêm phiên bản dữ liệu, trạng thái duyệt OCR, trạng thái/thẻ model và con trỏ phát hành theo label map. Model cũ thiếu thẻ trở về DRAFT; phải bổ sung số liệu thật trước khi kích hoạt lại. Artifact cũ cần được chuyển vào bucket models nếu trước đó nằm ở bucket ảnh; migration không di chuyển object S3.
- Worker mặc định tắt (`AI_ENABLED=false`). Chỉ bật sau khi có ai-service triển khai đúng hợp đồng và đặt `AI_TARGET`. Nhánh này bàn giao phía gọi Java, không kèm dịch vụ inference Python hoặc trọng số.
- `MINIO_MODEL_BUCKET` mặc định `models`; `LABEL_DATASET_VERSION` mặc định `v1.0`. Các biến được mô tả trong [README](README.md#biến-môi-trường).
- Back-end kiểm kích thước crop khi complete và khi export. Giới hạn upload trước khi vào kho S3 thuộc cấu hình triển khai, không nằm trong bản bàn giao này.

## Nhật ký chuẩn bị ngày 2026-11-08

1. Kiểm tra `origin` và `dev` mới nhất; tạo worktree riêng để không đưa các thay đổi ngoài back-end vào commit.
2. Chọn 41 file back-end đã thay đổi/thêm mới và ba tài liệu kiến trúc, CSDL, API. Bổ sung tài liệu bàn giao này theo yêu cầu ghi lại công việc.
3. Loại khỏi bản API các hướng dẫn mới về triển khai mobile và gateway S3; sửa README để không phụ thuộc tài liệu AI chưa được đưa lên nhánh này.
4. Kiểm tra bản tách bằng build/format/test; kết quả được ghi bên dưới trước khi commit.

## Kiểm chứng

Ngày 2026-11-08: JDK 21.0.13, Gradle 8.10.2, MySQL 8.0.46 và Redis 7.4.0 riêng cho kiểm thử. Chạy từ `backend/` với JDK 21 và Gradle cache tạm:

```bash
SIGNA_TEST_DB_URL='jdbc:mysql://127.0.0.1:13306/signa_backend_publish?allowPublicKeyRetrieval=true&useSSL=false' \
SIGNA_TEST_REDIS_HOST=127.0.0.1 \
SIGNA_TEST_REDIS_PORT=16379 \
./gradlew spotlessCheck build -Pintegration --no-daemon
```

Kết quả: **BUILD SUCCESSFUL**, 77 test đạt (13 auth, 2 map, 62 sign), gồm 29 test tích hợp; không lỗi hoặc skip. Spotless đạt. CI không đặt biến override nên tiếp tục dùng Testcontainers MySQL 8.4/Redis 7.4 theo cấu hình hiện có.

Markdown của năm tài liệu đã chọn và link file nội bộ đạt. Kiểm tra danh sách commit chỉ có 45 file back-end/tài liệu liên quan; không có file nhị phân, ảnh/trọng số, build output hoặc chuỗi khớp mẫu token/private key. Kết quả trên là kiểm chứng cục bộ của bản tách, không phải kết quả CI remote hoặc nghiệm thu triển khai.

## Các phần chưa nghiệm thu

- Chưa thử toàn bộ luồng với S3 thật, ai-service/trọng số thật hoặc VietMap bằng API key thật trong lượt bàn giao này.
- Chưa đo tải NFR-11/NFR-12; chưa triển khai demo, HTTPS hoặc backup/restore.
- Thẻ model cần số liệu đánh giá thật do người phụ trách AI cung cấp. Kiểm tra cổng phát hành không chứng minh model đạt chất lượng thực tế.
- Giao diện quản trị, tải/cài model và cảnh báo trên điện thoại được bàn giao bởi các phần phụ trách khác.
