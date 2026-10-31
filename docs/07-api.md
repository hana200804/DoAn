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

Gửi lại cùng `Idempotency-Key` trả về đúng `batchId` cũ, không tạo bản ghi mới.

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
| POST | `/admin/label-tasks/{id}/skip` | Bỏ qua | FR-23 |
| POST | `/admin/sign-types` | Thêm loại biển | FR-24 |
| PUT | `/admin/sign-types/{code}` | Sửa hoặc ngừng dùng loại biển | FR-24 |
| GET | `/admin/stats/overview` | Số liệu tổng quan | FR-25 |
| GET | `/admin/models` | Danh sách phiên bản mô hình | FR-25 |
| POST | `/admin/models/{version}/activate` | Kích hoạt hoặc quay lui phiên bản | FR-15 |

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

Hạn chót mỗi lời gọi do sign-service đặt; quá hạn thì quan sát được xử lý tiếp với kết quả từ thiết bị và ghi cờ để thử lại sau.

## 8. Tài liệu OpenAPI

- Mỗi service Java sinh OpenAPI bằng springdoc: `/v3/api-docs`, giao diện tại `/swagger-ui.html`.
- Web quản trị sinh client TypeScript từ OpenAPI của auth-service và sign-service.
- Thay đổi API phải cập nhật tài liệu này trong cùng Pull Request.
