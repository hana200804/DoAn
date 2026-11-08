# 07 · API

## Mục lục

1. [Quy ước chung](#1-quy-ước-chung)
2. [Định dạng lỗi](#2-định-dạng-lỗi)
3. [auth-service](#3-auth-service)
4. [map-service](#4-map-service)
5. [sign-service — người lái](#5-sign-service--người-lái)
6. [sign-service — quản trị](#6-sign-service--quản-trị)
7. [gRPC ai-service](#7-grpc-ai-service)
8. [Tài liệu OpenAPI](#8-tài-liệu-openapi)
9. [Bổ sung hợp đồng back-end](#9-bổ-sung-hợp-đồng-back-end)

## 1. Quy ước chung

| Hạng mục | Quy ước |
| --- | --- |
| Tiền tố | `/api/v1` |
| Định dạng | JSON UTF-8, tên trường `camelCase` |
| Xác thực | `Authorization: Bearer <sessionId>` |
| Thời gian | ISO-8601 UTC, ví dụ `2026-01-15T08:30:12.345Z` |
| Toạ độ | `{ "lat": 21.0285, "lng": 105.8048 }`, WGS84 |
| Vùng | `bbox=minLng,minLat,maxLng,maxLat` |
| Phân trang | `page` (từ 0), `size` (≤ 100); trả `{ items, page, size, total }` |
| Chống gửi trùng | Header `Idempotency-Key` cho request tạo lô quan sát |
| Truy vết | Header `X-Request-Id`, trả lại trong response và lỗi |

## 2. Định dạng lỗi

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "details": [
    { "field": "observations[0].location.lat", "issue": "phải nằm trong [-90, 90]" }
  ],
  "requestId": "7f3c2a1e-…"
}
```

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Dữ liệu đầu vào sai |
| 401 | `UNAUTHENTICATED` | Thiếu hoặc hết hạn phiên |
| 403 | `FORBIDDEN` | Không đủ quyền |
| 404 | `NOT_FOUND` | Không có tài nguyên |
| 409 | `CONFLICT` | Trùng email, trùng báo cáo, xung đột phiên bản |
| 422 | `BUSINESS_RULE` | Vi phạm quy tắc nghiệp vụ, ví dụ label map không hỗ trợ |
| 429 | `RATE_LIMITED` | Vượt giới hạn tần suất |
| 500 | `INTERNAL_ERROR` | Lỗi không lường trước |
| 503 | `UPSTREAM_UNAVAILABLE` | VietMap hoặc ai-service không phản hồi |

## 3. auth-service

| Method | Đường dẫn | Quyền | Mô tả | FR |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | Công khai | Đăng ký | FR-12 |
| POST | `/auth/login` | Công khai | Đăng nhập, trả phiên | FR-12, FR-20 |
| POST | `/auth/logout` | Đã đăng nhập | Thu hồi phiên | FR-12 |
| POST | `/auth/password/forgot` | Công khai | Gửi OTP qua email | FR-12 |
| POST | `/auth/password/reset` | Công khai | Đặt lại mật khẩu bằng OTP | FR-12 |
| GET | `/users/me` | Đã đăng nhập | Thông tin tài khoản | FR-12 |
| PATCH | `/users/me` | Đã đăng nhập | Sửa tên hiển thị, loại xe | FR-13 |
| PUT | `/users/me/password` | Đã đăng nhập | Đổi mật khẩu | FR-12 |

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "driver@example.com", "password": "••••••••" }
```

```json
{
  "sessionId": "b1f0…",
  "expiresAt": "2026-02-14T08:30:12Z",
  "user": { "id": "4e0c…", "displayName": "Tài xế A", "role": "DRIVER", "vehicleType": "CAR" }
}
```

Sai mật khẩu quá 10 lần trong 15 phút từ cùng một IP thì trả `429`. Đếm theo email + IP (nginx gửi `X-Real-IP`), nên người ngoài nhập sai liên tục không khoá được chủ tài khoản.

## 4. map-service

| Method | Đường dẫn | Quyền | Mô tả | FR |
| --- | --- | --- | --- | --- |
| GET | `/places/search?q=&lat=&lng=` | Đã đăng nhập | Tìm địa chỉ theo từ khoá (trả `refId`, chưa có toạ độ) | FR-09 |
| GET | `/places/{refId}` | Đã đăng nhập | Toạ độ và địa chỉ đầy đủ của một kết quả tìm kiếm | FR-09 |
| GET | `/places/reverse?lat=&lng=` | Đã đăng nhập | Địa chỉ của một toạ độ | FR-08 |
| GET | `/routes?from=lat,lng&to=lat,lng&vehicle=CAR` | Đã đăng nhập | Tuyến đường và polyline | FR-09 |

## 5. sign-service — người lái

| Method | Đường dẫn | Quyền | Mô tả | FR |
| --- | --- | --- | --- | --- |
| GET | `/sign-types` | Công khai | Danh mục loại biển | FR-08, FR-10 |
| GET | `/signs?bbox=&groups=&minConfidence=` | Công khai | Cụm biển trong vùng | FR-06, FR-07 |
| POST | `/signs/corridor` | Công khai | Cụm biển dọc một polyline | FR-04, FR-09 |
| GET | `/signs/{id}` | Công khai | Chi tiết cụm biển | FR-08 |
| POST | `/signs/{id}/reports` | Đã đăng nhập | Báo cáo biển sai | FR-08 |
| POST | `/observations` | Đã đăng nhập | Tạo lô quan sát, nhận presigned URL | FR-14 |
| POST | `/observations/{batchId}/complete` | Đã đăng nhập | Xác nhận đã tải ảnh, đưa lô vào hàng đợi | FR-14 |
| GET | `/me/contributions` | Đã đăng nhập | Thống kê đóng góp | FR-11 |
| GET | `/models/latest?appVersion=&labelMapVersion=` | Công khai | Phiên bản mô hình mới nhất tương thích | FR-15 |

### Tạo lô quan sát

```http
POST /api/v1/observations
Authorization: Bearer b1f0…
Idempotency-Key: 9d2b6c1e-…
Content-Type: application/json
```

```json
{
  "modelVersion": "model-v1.2",
  "labelMapVersion": 1,
  "deviceModel": "Pixel 6a",
  "observations": [
    {
      "clientId": "o-1",
      "kind": "POSITIVE",
      "signCode": "P.127_50",
      "detectorGroup": "prohibitory",
      "classProbs": [ { "code": "P.127_50", "p": 0.93 }, { "code": "P.127_60", "p": 0.05 } ],
      "voteShare": 0.88,
      "location": { "lat": 21.028511, "lng": 105.804817 },
      "headingDeg": 92,
      "distanceM": 18.5,
      "frames": 11,
      "hasSubSign": false,
      "observedAt": "2026-01-15T08:30:12.345Z"
    },
    {
      "clientId": "o-2",
      "kind": "NEGATIVE",
      "clusterId": "5a7e…",
      "location": { "lat": 21.029102, "lng": 105.806001 },
      "headingDeg": 90,
      "observedAt": "2026-01-15T08:31:02.120Z"
    }
  ]
}
```

```json
{
  "batchId": "c41f…",
  "uploads": [
    { "clientId": "o-1", "cropUploadUrl": "https://…/storage/sign-crops/…?X-Amz-Signature=…", "subSignUploadUrl": null }
  ]
}
```

Gửi lại cùng `Idempotency-Key` trả về đúng `batchId` cũ, không tạo bản ghi mới. `clientId` phải duy nhất trong một lô; phần tử null và xác suất trùng mã/tổng vượt 1 bị từ chối. `observedAt` không được ở tương lai quá 5 phút. Quan sát âm phải trỏ đến cụm tồn tại, cách vị trí gửi ≤ 50 m và lệch hướng ≤ 30°.

Quan sát dương có mã biển không có trong danh mục hoặc đang ngừng dùng bị bỏ riêng (không có trong `uploads`), các quan sát còn lại của lô vẫn được nhận. Như vậy một mã bị ngừng dùng không làm mất cả lô.

### Cụm biển trong vùng

```http
GET /api/v1/signs?bbox=105.80,21.02,105.82,21.04&minConfidence=0.8
```

```json
{
  "items": [
    {
      "id": "5a7e…",
      "signCode": "P.123a",
      "group": "PROHIBITORY",
      "name": "Cấm rẽ trái",
      "location": { "lat": 21.0291, "lng": 105.8061 },
      "headingDeg": 180,
      "confidence": 0.91,
      "status": "VERIFIED",
      "hasSubSign": true,
      "conditions": {
        "timeWindows": [ { "from": "06:00", "to": "22:00" } ],
        "vehicles": { "include": [ "CAR" ], "exclude": [] }
      },
      "observationCount": 14,
      "lastSeenAt": "2026-01-15T08:30:12Z"
    }
  ],
  "page": 0,
  "size": 100,
  "total": 1
}
```

### Phiên bản mô hình

```json
{
  "version": "model-v1.3",
  "labelMapVersion": 1,
  "detector": { "url": "https://…/storage/models/…", "sha256": "…", "sizeBytes": 3481200 },
  "classifier": { "url": "https://…/storage/models/…", "sha256": "…", "sizeBytes": 1270400 }
}
```

Không có phiên bản mới hơn thì trả `204 No Content`.

Request bắt buộc có `appVersion` (dạng số `major[.minor[.patch]]`) và `labelMapVersion`. Có thể thêm `currentVersion` để nhận `204` nếu phiên bản đang kích hoạt chính là bản đang dùng. Không có bản tương thích cũng trả `204`. Khi quản trị viên quay lui, bản đang kích hoạt được trả dù số phiên bản thấp hơn bản trên thiết bị.

`hasSubSign=true` và `conditions=null` nghĩa là chưa đọc được điều kiện biển phụ.

`GET /signs` nhận `page` và `size` (1–100); lọc nhóm, độ tin cậy và trạng thái trước khi phân trang, `total` là tổng kết quả phù hợp. Nhóm biển dùng chữ hoa như response.

## 6. sign-service — quản trị

Mọi đường dẫn yêu cầu vai trò `ADMIN`.

| Method | Đường dẫn | Mô tả | FR |
| --- | --- | --- | --- |
| GET | `/admin/conflicts` | Danh sách cụm `CONFLICT` | FR-21 |
| POST | `/admin/conflicts/{clusterId}/resolve` | Chốt mã `{ signCode }` hoặc loại `{ reject: true, reason }` | FR-21 |
| GET | `/admin/reports?status=PENDING` | Danh sách báo cáo sai | FR-22 |
| POST | `/admin/reports/{id}/resolve` | Chấp nhận hoặc từ chối kèm lý do | FR-22 |
| GET | `/admin/label-tasks?status=OPEN&reason=` | Hàng đợi gán nhãn | FR-23 |
| POST | `/admin/label-tasks/{id}` | Lưu nhãn `{ labelCode, labelBox }` | FR-23 |
| POST | `/admin/label-tasks/{id}/ocr` | Duyệt chữ và điều kiện biển phụ | FR-16, FR-23 |
| POST | `/admin/label-tasks/{id}/skip` | Bỏ qua | FR-23 |
| POST | `/admin/label-tasks/{id}/retry-analysis` | Chạy lại AI đã lỗi sau khi sửa cấu hình/trọng số | FR-19 |
| POST | `/admin/sign-types` | Thêm loại biển | FR-24 |
| PUT | `/admin/sign-types/{code}` | Sửa hoặc ngừng dùng loại biển | FR-24 |
| GET | `/admin/stats/overview` | Số liệu tổng quan | FR-25 |
| GET | `/admin/models` | Danh sách phiên bản mô hình | FR-25 |
| POST | `/admin/models` | Đăng ký file mô hình đã tải lên S3 | FR-15 |
| POST | `/admin/models/{version}/activate` | Kích hoạt hoặc quay lui phiên bản | FR-15 |

### Gán nhãn và thống kê

`GET /admin/label-tasks?status=OPEN&reason=LOW_CONF&page=0&size=100` trả `{ items, page, size, total }`. `status`: `OPEN`, `LABELED`, `SKIPPED`. `reason`: `LOW_CONF`, `SPLIT_VOTE`, `MISMATCH`, `CONFLICT`, `OCR_UNPARSED`, `AI_UNAVAILABLE`, `RANDOM`. Mỗi phần tử có `id`, `observationId`, `signCode`, `imageUrl` (biển chính), `subSignImageUrl` (biển phụ), `analysis` (JSON kết quả/gợi ý AI), `labelCode`, `labelBox`, `ocrText`, `conditions` đã duyệt, `createdAt`.

Lưu nhãn bằng `{ "labelCode": "P.123a", "datasetVersion": "v1.1", "labelBox": { "x": 0, "y": 0, "width": 1, "height": 1 } }`; box là tuỳ chọn và dùng toạ độ chuẩn hoá trên ảnh cắt. Nhãn ghi `datasetVersion` (mặc định từ `LABEL_DATASET_VERSION`); response có `datasetVersion`, `ocrStatus` (`NOT_REQUIRED/PENDING/REVIEWED/SKIPPED`). Tác vụ có OCR chưa duyệt giữ `OPEN` sau khi chốt nhãn chính; nhãn chính đã chốt không được ghi đè. Duyệt OCR có thể gửi sau mà không cần gửi lại nhãn chính. Tác vụ hoàn tất trả `409` khi sửa/bỏ qua lần nữa. Lưu nhãn không tự đổi mã cụm; quyết định cụm đi qua API xử lý mâu thuẫn/báo cáo.

Duyệt OCR bằng `POST /admin/label-tasks/{id}/ocr`:

```json
{
  "rawText": "6h-22h",
  "conditions": { "timeWindows": [ { "from": "06:00", "to": "22:00" } ] },
  "signLabel": { "labelCode": "P.123a" }
}
```

`signLabel` bắt buộc nếu tác vụ `LOW_CONF`, `SPLIT_VOTE`, `MISMATCH`, `CONFLICT`, `RANDOM` chưa có nhãn chính đã duyệt; tuỳ chọn khi nhãn chính đã duyệt hoặc tác vụ chỉ liên quan OCR. `conditions` phải có ít nhất một điều kiện, giờ dạng `HH:mm`, loại xe `CAR|BUS|TRUCK`. Điều kiện được áp dụng lên cụm khi quan sát có trọng số và cùng mã cụm; nếu nhãn chính được sửa sang mã khác thì chỉ lưu kết quả duyệt. Điều kiện đã duyệt tay không bị các lượt AI sau ghi đè. Tác vụ phải có ảnh phụ và còn `OPEN`.

`GET /me/contributions` chỉ trả thống kê của người trong phiên: `observations`, `clusters`, `reputation`, `agreed`, `disagreed`. `GET /admin/stats/overview` trả `clusters`, `verified`, `conflicts`, `observations`, `pendingReports`, `openLabelTasks`, `failedAnalyses`.

### Đăng ký và kích hoạt mô hình

Tải hai file lên bucket `MINIO_MODEL_BUCKET` (mặc định `models`), rồi gửi:

```json
{
  "version": "model-v1.3",
  "labelMapVersion": 1,
  "minAppVersion": "1.0.0",
  "detector": { "objectKey": "models/model-v1.3/detector.tflite", "sha256": "<64 ký tự hex thường>", "sizeBytes": 3481200 },
  "classifier": { "objectKey": "models/model-v1.3/classifier.tflite", "sha256": "<64 ký tự hex thường>", "sizeBytes": 1270400 },
  "metrics": { "datasetVersion": "v1.1", "fieldDatasetVersion": "field-v1", "trainingConfig": "exp007", "commit": "abc123", "referenceDevice": "Pixel 6a", "map50": 0.91, "criticalRecall": 0.94, "int8MapDrop": 0.01, "fps": 16, "falseWarningsPer10Km": 0.5 }
}
```

`POST /admin/models` trả `201`; trùng phiên bản trả `409`. Mỗi file ≤ 100 MiB; SHA-256 và kích thước được kiểm tra với nội dung thật tại lúc đăng ký và kích hoạt. `POST /admin/models/{version}/activate` trả `204`, dùng cả cho phát hành và quay lui. Mỗi label map có con trỏ CSDL bảo đảm chỉ một phiên bản ACTIVE. Model mới là DRAFT; kích hoạt chuyển bản trước sang RETIRED. Thẻ mô hình phải đạt mAP≥0,89, recall≥0,90, giảm INT8<0,02, FPS≥15, cảnh báo sai≤1/10km, tổng file<15MiB và không kém bản đang dùng về mAP/cảnh báo sai. RETIRED được quay lui. `PUT /admin/models/{version}/card` sửa thẻ DRAFT; URL tải có hạn 60 phút. API cung cấp artifact cho mobile; việc tích hợp tải/cài mô hình trong ứng dụng là phần riêng.

## 7. gRPC ai-service

File `backend/proto/signa/ai/v1/sign_analyzer.proto`, dùng chung cho sign-service (Java) và ai-service (Python). Kiểm tra sống dùng chuẩn `grpc.health.v1`.

```proto
syntax = "proto3";

package signa.ai.v1;

option java_multiple_files = true;
option java_package = "com.signa.ai.v1";

service SignAnalyzer {
  // Phân loại lại ảnh cắt bằng mô hình lớn phía server.
  rpc Reclassify(ReclassifyRequest) returns (ReclassifyResponse);
  // Đọc chữ trên biển phụ và trích điều kiện áp dụng.
  rpc ReadSubSign(ReadSubSignRequest) returns (ReadSubSignResponse);
}

message ImageRef {
  string bucket = 1;
  string object_key = 2;
}

message CodeProbability {
  string code = 1;
  float probability = 2;
}

message ReclassifyRequest {
  string request_id = 1;
  ImageRef crop = 2;
  string detector_group = 3;
  int32 label_map_version = 4;
}

message ReclassifyResponse {
  repeated CodeProbability top_codes = 1;  // giảm dần theo xác suất
  string model_version = 2;
}

message ReadSubSignRequest {
  string request_id = 1;
  ImageRef crop = 2;
}

message TimeWindow {
  string from = 1;  // "HH:mm"
  string to = 2;    // "HH:mm"
}

message SubSignConditions {
  repeated TimeWindow time_windows = 1;
  repeated string include_vehicles = 2;
  repeated string exclude_vehicles = 3;
  optional int32 distance_ahead_m = 4;
  optional int32 zone_length_m = 5;
  optional float weight_limit_ton = 6;
}

message ReadSubSignResponse {
  string raw_text = 1;
  float ocr_confidence = 2;
  bool parsed = 3;
  SubSignConditions conditions = 4;
}
```

Hạn chót mỗi lời gọi do sign-service đặt (mặc định 3 giây). Gom cụm dùng kết quả thiết bị; phân loại lại lưu thành gợi ý trong hàng đợi gán nhãn. Worker AI đọc job từ MySQL sau `complete`, dùng lease 60 giây và thử tối đa 5 lần với khoảng chờ 30, 60, 120, 240 giây. RPC lỗi không làm mất lô quan sát; OCR vẫn được gọi nếu classifier chưa sẵn sàng. Lỗi `INVALID_ARGUMENT` dừng thử lại và chuyển duyệt tay.

## 8. Tài liệu OpenAPI

- Mỗi service Java sinh OpenAPI bằng springdoc: `/v3/api-docs`, giao diện tại `/swagger-ui.html`.
- Web quản trị sinh client TypeScript từ OpenAPI của auth-service và sign-service.
- Thay đổi API phải cập nhật tài liệu này trong cùng Pull Request.

## 9. Bổ sung hợp đồng back-end

- `POST /signs/corridor?page=0&size=100`: lọc khoảng cách tới tuyến và mã active trước phân trang, total là tổng phù hợp; không cắt 5.000 ứng viên trước khi lọc.
- `GET /admin/datasets/{version}/export?page=0&size=100` (ADMIN): `{datasetVersion,items,page,size,total}`. Mỗi item có `observationId,labelCode,labelBox,imageUrl,sha256,sequence,labelMapVersion`. Box chuẩn hoá theo ảnh; sequence là hash tài khoản để chia tập không trộn dữ liệu cùng nguồn. URL ảnh hết hạn sau 60 phút; phải kiểm tra SHA-256 khi tải.
- `GET /admin/stats/overview` bổ sung `observationsByDay` (30 ngày), `topContributors` (20 người), `coverage` (ô 0,01 độ), `autoVerifiedRatio`, `activeModels`.
- `GET /me/contributions` bổ sung `verifiedObservations`, `rejectedObservations`.
- Quan sát label map không hỗ trợ trả 422 trước khi lưu lô; ảnh thường được lấy mẫu 1% với cờ RANDOM, ổn định khi retry.
- Đăng ký/đổi/reset mật khẩu cần ≥8 ký tự, tối đa 72 byte UTF-8; đăng nhập không chấp nhận mật khẩu vượt 72 byte để tránh BCrypt bỏ đuôi.

Khi nhãn chính sửa sang mã khác, điều kiện OCR tự động lấy từ chính quan sát đó bị vô hiệu hoá; kết quả AI đến muộn không được áp lại. Duyệt OCR riêng vẫn lưu phiên bản dữ liệu đích. Tác vụ thiếu file ảnh phụ không bị treo phần duyệt nhãn chính, trong khi cờ có biển phụ của cụm vẫn được giữ để bên cảnh báo xử lý thận trọng.
