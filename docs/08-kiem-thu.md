# 08 · Kiểm thử

## Mục lục

1. [Chiến lược tổng thể](#1-chiến-lược-tổng-thể)
2. [Kiểm thử theo thành phần](#2-kiểm-thử-theo-thành-phần)
3. [Đánh giá mô hình](#3-đánh-giá-mô-hình)
4. [Đánh giá trên thiết bị](#4-đánh-giá-trên-thiết-bị)
5. [Thử nghiệm thực địa](#5-thử-nghiệm-thực-địa)
6. [Đánh giá bản đồ tự xác minh](#6-đánh-giá-bản-đồ-tự-xác-minh)
7. [Kiểm thử bảo mật](#7-kiểm-thử-bảo-mật)
8. [Kiểm thử hiệu năng server](#8-kiểm-thử-hiệu-năng-server)
9. [Kiểm thử chấp nhận](#9-kiểm-thử-chấp-nhận)
10. [Tiêu chí phát hành và phân loại lỗi](#10-tiêu-chí-phát-hành-và-phân-loại-lỗi)

## 1. Chiến lược tổng thể

```text
              /  E2E  \            Ít, chậm, độ tin cậy cao: luồng người dùng chính
             /  Tích hợp \          Vừa: service + CSDL thật, hợp đồng gRPC, mô hình trên thiết bị
            /   Unit test  \        Nhiều, nhanh: logic nghiệp vụ, tracker, cảnh báo, gom cụm
           /  Đánh giá AI    \      Riêng cho mô hình: chỉ số trên tập test và thực địa
```

Ưu tiên kiểm thử theo mức rủi ro: **cảnh báo sai hoặc bỏ lỡ biển cấm** gây hậu quả lớn nhất, nên `WarningEngine`, `Tracker`, gom cụm và điểm tin cậy được kiểm thử kỹ nhất.

| Thành phần | Unit | Tích hợp | E2E | Công cụ |
| --- | --- | --- | --- | --- |
| `mobile/` | WarningEngine, Tracker, bỏ phiếu, ước lượng vị trí, ánh xạ nhãn | Chạy mô hình TFLite trên ảnh mẫu, Room | Compose UI test các màn hình chính | JUnit, Turbine, Compose UI Test |
| `backend/` | Gom cụm, điểm tin cậy, trạng thái cụm, kiểm tra quyền | MySQL, Redis, MinIO thật bằng Testcontainers; hợp đồng gRPC | Luồng gửi quan sát đầy đủ | JUnit 5, AssertJ, Testcontainers, RestAssured |
| `ai/` | Phân tích biển phụ, tiền xử lý, chia tập, manifest | ai-service gRPC với mô hình nhỏ | — | pytest, grpcio-testing |
| `web-admin/` | Hook, hàm định dạng | Component với API giả lập | Đăng nhập → xử lý ca mâu thuẫn | Vitest, Testing Library, MSW, Playwright |

### Chỉ tiêu độ phủ dòng lệnh

| Phạm vi | Chỉ tiêu | Công cụ đo |
| --- | --- | --- |
| Logic cốt lõi: `WarningEngine`, `Tracker`, gom cụm, điểm tin cậy, bộ phân tích biển phụ | ≥ 90% | JaCoCo, pytest-cov |
| Phần còn lại của service và module | ≥ 70% | JaCoCo, pytest-cov, Vitest |
| Giao diện | Không đặt chỉ tiêu %; kiểm thử theo luồng | — |

Không viết test cho: getter/setter, mã sinh tự động (gRPC, OpenAPI), cấu hình framework.

## 2. Kiểm thử theo thành phần

### 2.1. Ứng dụng di động

| ID | Đối tượng | Ca kiểm thử | Kết quả mong đợi |
| --- | --- | --- | --- |
| UT-M01 | Tracker | Phát hiện chỉ xuất hiện ở 1 khung | Track không được xác nhận, không có quan sát |
| UT-M02 | Tracker | Phát hiện khớp 3 trong 5 khung | Track chuyển `Confirmed` |
| UT-M03 | Tracker | Track xác nhận rồi mất 11 khung liên tiếp | Track kết thúc, sinh đúng 1 quan sát |
| UT-M04 | Bỏ phiếu | Mã dẫn đầu chiếm 55% tổng phiếu | Track mang trạng thái `SPLIT_VOTE`, không sinh cảnh báo theo mã |
| UT-M05 | WarningEngine | Giới hạn 50, tốc độ 54 km/h trong 2 giây | Không cảnh báo |
| UT-M06 | WarningEngine | Giới hạn 50, tốc độ 54 km/h trong 3 giây | Đúng một cảnh báo "Vượt tốc độ, giới hạn 50" |
| UT-M07 | WarningEngine | Chưa rõ giới hạn, tốc độ 90 km/h | Không cảnh báo tốc độ |
| UT-M08 | WarningEngine | Biển cấm có điều kiện 6h–22h, thời điểm 23h | Không cảnh báo |
| UT-M09 | WarningEngine | Cùng biển từ bản đồ và từ camera | Chỉ đọc một lần |
| UT-M10 | WarningEngine | Ba cảnh báo đến cùng lúc | Đọc theo ưu tiên tốc độ → cấm → nguy hiểm, cách nhau ≥ 4 giây |
| UT-M11 | Ước lượng vị trí | Box cao 50 px, `f_px` 1000, biển cao 0,7 m | Khoảng cách 14 m (sai số < 1%) |
| IT-M01 | Mô hình TFLite | Chạy detector + classifier trên 50 ảnh mẫu có nhãn | Mã đúng trên ≥ 90% ảnh; không lỗi khi dùng GPU delegate và CPU |
| IT-M02 | Hàng đợi quan sát | Tắt mạng, sinh 20 quan sát, bật mạng | Gửi đủ 20, không trùng |
| UI-M01 | Cài đặt | Tắt giọng nói | Cảnh báo chỉ hiện trên màn hình |

### 2.2. Backend

| ID | Đối tượng | Ca kiểm thử | Kết quả mong đợi |
| --- | --- | --- | --- |
| UT-B01 | Gom cụm | 2 người, cùng mã, cách 8 m, cùng hướng, xác suất 0,9 | Cùng một cụm; cụm `VERIFIED` khi điểm ≥ 0,8 |
| UT-B02 | Gom cụm | 1 người gửi 5 lần cùng biển trong một chuyến | `distinct_users = 1`, cụm vẫn `CANDIDATE` |
| UT-B03 | Gom cụm | 2 quan sát cùng mã, cách 5 m, hướng ngược nhau | Hai cụm khác nhau |
| UT-B04 | Điểm tin cậy | Mã thứ hai chiếm 35% tổng trọng số | Cụm chuyển `CONFLICT` |
| UT-B05 | Điểm tin cậy | Cụm `VERIFIED` nhận quan sát âm từ 3 lượt khác nhau | Cụm chuyển `MISSING_SUSPECTED` |
| UT-B06 | Uy tín | Quản trị viên chốt mã khác mã người A gửi | Uy tín A giảm 0,1, không dưới 0,5 |
| IT-B01 | API quan sát | Gửi cùng `Idempotency-Key` hai lần | Cùng `batchId`, không có bản ghi trùng |
| IT-B02 | Luồng quan sát | Tạo lô → tải ảnh lên MinIO → complete | Quan sát được gán cụm; ảnh tồn tại trong bucket |
| IT-B03 | Truy vấn không gian | Một cụm biết trước nằm trong `bbox` | Được trả về; cụm ngoài `bbox` không được trả |
| IT-B04 | gRPC | sign-service gọi ai-service giả lập trả lỗi quá hạn | Quan sát vẫn được xử lý, có cờ thử lại |
| IT-B05 | Phân quyền | Gọi `/admin/conflicts` bằng phiên `DRIVER` | `403 FORBIDDEN` |
| IT-B06 | Phiên | Gọi API bằng phiên đã đăng xuất | `401 UNAUTHENTICATED` |

### 2.3. AI

| ID | Đối tượng | Ca kiểm thử | Kết quả mong đợi |
| --- | --- | --- | --- |
| UT-A01 | Phân tích biển phụ | `"6h-22h"` | `timeWindows = [06:00–22:00]` |
| UT-A02 | Phân tích biển phụ | `"Từ 6h30 đến 22h"` | `timeWindows = [06:30–22:00]` |
| UT-A03 | Phân tích biển phụ | `"Trừ xe buýt"` | `vehicles.exclude = [BUS]` |
| UT-A04 | Phân tích biển phụ | Chuỗi không theo mẫu | `parsed = false`, giữ `rawText` |
| UT-A05 | Chia tập | Hai khung thuộc cùng chuỗi ảnh | Luôn rơi vào cùng một tập |
| UT-A06 | Manifest | Sửa một ảnh rồi sinh lại manifest | Hash của ảnh đó thay đổi, các ảnh khác giữ nguyên |
| IT-A01 | ai-service | Gọi `Reclassify` với ảnh cắt mẫu | Trả top mã giảm dần theo xác suất, đúng `model_version` |

### 2.4. Web quản trị

| ID | Đối tượng | Ca kiểm thử | Kết quả mong đợi |
| --- | --- | --- | --- |
| CT-W01 | Trang ca mâu thuẫn | API trả 3 cụm | Hiển thị 3 cụm kèm ảnh cắt và các mã cạnh tranh |
| CT-W02 | Trang gán nhãn | Sửa box rồi lưu | Gửi đúng `labelBox` đã sửa |
| E2E-W01 | Luồng quản trị | Đăng nhập → chốt mã cho một cụm `CONFLICT` | Cụm biến khỏi danh sách; có bản ghi trong nhật ký |
| E2E-W02 | Phân quyền | Đăng nhập bằng tài khoản `DRIVER` | Bị từ chối |

## 3. Đánh giá mô hình

**Tập dữ liệu:** tập test (chia theo chuỗi ảnh) và tập kiểm thử thực địa ([Dữ liệu §5](05-du-lieu.md#5-phân-chia-tập-dữ-liệu)). Mô hình không bao giờ được chọn tham số trên tập test.

**Chỉ số:**

| Chỉ số | Ý nghĩa | Chỉ tiêu |
| --- | --- | --- |
| mAP@0.5 | Toàn chuỗi: box của detector, nhãn là mã do classifier gán | ≥ 0,89 (NFR-07) |
| mAP@0.5 theo nhóm | Riêng detector, nhãn là 5 nhóm biển | Báo cáo |
| mAP@0.5:0.95 | Chất lượng định vị box | Báo cáo |
| AP theo kích thước box (nhỏ, vừa, lớn) | Khả năng thấy biển ở xa | Báo cáo |
| Precision, recall theo từng mã | Lớp nào yếu | Recall nhóm cấm và tốc độ ≥ 0,90 (NFR-08) |
| Ma trận nhầm lẫn của classifier | Cặp mã hay bị nhầm | Báo cáo |
| ECE trước và sau temperature scaling | Xác suất có đáng tin không | < 0,05 sau hiệu chỉnh |

Mọi chỉ số được báo cáo **tách theo điều kiện**: ngày, đêm, mưa.

**So sánh và loại bỏ từng thành phần** (cùng dữ liệu, cùng cấu hình huấn luyện):

| Thí nghiệm | So sánh |
| --- | --- |
| Đối chứng | YOLOv5s, YOLOv8s, DETR-R50 một tầng |
| Hai tầng | Detector theo nhóm + classifier so với một tầng ([ADR-0003](adr/0003-mo-hinh-hai-tang.md)) |
| Kích thước ảnh | 640 px so với 960 px |
| Tăng cường dữ liệu | Có / không có mưa, sương, nhoè |
| Dữ liệu tổng hợp | Có / không có, trên recall lớp hiếm |
| Lượng tử hoá | FP32, FP16, INT8 |

Mỗi thí nghiệm ghi lại trong `ai/experiments/` kèm cấu hình, phiên bản dữ liệu và commit.

## 4. Đánh giá trên thiết bị

- **Thiết bị:** ít nhất hai điện thoại Android thuộc hai phân khúc khác nhau (xem [SRS §9, Q5](02-dac-ta-yeu-cau.md#9-vấn-đề-cần-làm-rõ)).
- **Cách đo lặp lại được:** chế độ phát lại đưa từng khung của một video thực địa vào chuỗi xử lý trên máy thay cho camera, để mọi lần đo dùng cùng dữ liệu đầu vào.

| Chỉ số | Cách đo | Chỉ tiêu |
| --- | --- | --- |
| FPS | Số khung xử lý xong chia cho thời gian chạy | ≥ 15 (NFR-01) |
| Độ trễ từng bước | Đồng hồ đơn điệu quanh tiền xử lý, detector, classifier, tracker | Theo ngân sách [Thiết kế AI §1](04-thiet-ke-ai.md#1-tổng-quan-chuỗi-xử-lý) |
| Pin | Android Battery Historian khi chạy liên tục | < 15 %/giờ (NFR-04) |
| Nhiệt | Trạng thái nhiệt của hệ thống; FPS khi máy nóng | Không giảm dưới 10 FPS |
| Bộ nhớ | Android Profiler | Không rò rỉ khi chạy dài |
| Dữ liệu di động | Thống kê mạng của ứng dụng | < 20 MB/giờ (NFR-05) |

## 5. Thử nghiệm thực địa

Dùng video thực địa có nhãn chuẩn ([Dữ liệu §4](05-du-lieu.md#4-video-thực-địa)): mỗi tuyến có danh sách biển thật với mã, vị trí, hướng.

| Chỉ số | Định nghĩa | Chỉ tiêu |
| --- | --- | --- |
| Recall theo tuyến | Số biển thật được nhận diện đúng mã / tổng số biển thật áp dụng cho hướng đi | ≥ 0,90 với nhóm cấm và tốc độ |
| Cảnh báo sai / 10 km | Số cảnh báo không ứng với biển thật nào áp dụng, chia cho quãng đường × 10 | ≤ 1 (mục tiêu G3) |
| Độ trễ cảnh báo | Thời điểm bắt đầu đọc câu cảnh báo trừ thời điểm biển đủ rõ để người gán nhãn nhận ra | < 500 ms sau khi biển được xác nhận (NFR-02) |
| Khoảng cách cảnh báo | Khoảng cách từ xe tới biển khi bắt đầu cảnh báo | Báo cáo phân bố |
| Sai số vị trí biển | Khoảng cách giữa vị trí ước lượng và vị trí thật | Trung vị ≤ 10 m; báo cáo cả p90 |

Kết quả báo cáo theo từng điều kiện (ngày, đêm, mưa) và so sánh có / không có tracker.

## 6. Đánh giá bản đồ tự xác minh

So sánh **gom cụm + điểm tin cậy** với phương án đối chứng **khử trùng theo bán kính cố định 20 m** trên cùng dữ liệu ([ADR-0004](adr/0004-diem-tin-cay-ban-do.md)).

**Dữ liệu:** quan sát sinh từ nhiều lượt đi qua cùng tuyến của video thực địa; mỗi lượt được coi là một người dùng.

**Kịch bản:**

| Kịch bản | Cách tạo |
| --- | --- |
| Bình thường | Toàn bộ quan sát thật |
| Nhiễu mô hình | Đổi ngẫu nhiên mã của 10% quan sát sang mã cùng nhóm |
| Người dùng phá | Một người dùng giả gửi quan sát sai mã liên tục tại 20% số biển |
| Biển biến mất | Bỏ một số biển khỏi các lượt đi sau, sinh quan sát âm |

**Chỉ số:**

| Chỉ số | Ý nghĩa |
| --- | --- |
| Precision cụm được công nhận | Tỉ lệ cụm `VERIFIED` (hoặc được công nhận ở phương án đối chứng) ứng với biển thật đúng mã |
| Recall bản đồ | Tỉ lệ biển thật có cụm được công nhận đúng mã |
| Số ca cần quản trị viên | Số mục phải duyệt tay |
| Độ bền | Mức giảm precision ở kịch bản người dùng phá |
| Phát hiện biển mất | Tỉ lệ biển bị bỏ được đánh dấu `MISSING_SUSPECTED` |

## 7. Kiểm thử bảo mật

- [ ] Mọi API cần đăng nhập trả `401` khi thiếu phiên, phiên sai hoặc phiên đã thu hồi
- [ ] Mọi API quản trị trả `403` với vai trò `DRIVER`
- [ ] Người dùng không đọc hoặc sửa được dữ liệu của người khác qua đổi ID trong đường dẫn
- [ ] Đầu vào sai kiểu, quá dài, toạ độ ngoài phạm vi bị từ chối với `400`
- [ ] Đăng nhập và nhập OTP sai nhiều lần bị giới hạn tần suất
- [ ] Mật khẩu lưu dạng BCrypt; không xuất hiện trong log
- [ ] Presigned URL chỉ cho phép `PUT` đúng một khoá và có thời hạn
- [ ] CI quét bí mật trong mã nguồn ở mỗi Pull Request

## 8. Kiểm thử hiệu năng server

Dùng k6 chạy trên môi trường Docker Compose giống bản demo.

| Kịch bản | Tải | Chỉ tiêu |
| --- | --- | --- |
| Truy vấn biển theo vùng | 50 người dùng ảo, vùng chứa 500 cụm | p95 < 300 ms (NFR-11) |
| Tạo lô quan sát | Lô 20 quan sát | ≥ 50 quan sát/giây được tiếp nhận (NFR-12) |
| Worker gom cụm | Đẩy 10 000 quan sát vào stream | Hàng đợi không tăng mãi; không có lỗi khoá lạc quan bị bỏ sót |

## 9. Kiểm thử chấp nhận

Mỗi tiêu chí chấp nhận trong [SRS §7](02-dac-ta-yeu-cau.md#7-tiêu-chí-chấp-nhận) có ít nhất một ca kiểm thử. Bảng truy vết cho các yêu cầu P0:

| Yêu cầu | Ca kiểm thử |
| --- | --- |
| FR-01 | IT-M01, §4 |
| FR-02 | UT-M01 → UT-M04, §5 |
| FR-03 | UT-M05 → UT-M07, §5 |
| FR-04 | UT-M08 → UT-M10, §5 |
| FR-05 | UI test màn hình lái xe |
| FR-06 | IT-B03, UI test màn hình bản đồ |
| FR-12 | IT-B06, §7 |
| FR-13 | UI-M01 |
| FR-14 | IT-M02, IT-B01, IT-B02 |
| FR-17 | UT-B01 → UT-B06, §6 |
| FR-20 | IT-B05, E2E-W02 |
| FR-21 | CT-W01, E2E-W01 |
| FR-24 | Test tích hợp API danh mục biển |

## 10. Tiêu chí phát hành và phân loại lỗi

**Mức độ lỗi:**

| Mức | Mô tả | Ví dụ |
| --- | --- | --- |
| S1 · Nghiêm trọng | Gây cảnh báo sai nguy hiểm hoặc làm ứng dụng dừng khi đang lái | Đọc sai giới hạn cao hơn biển thật; crash ở màn hình lái xe |
| S2 · Cao | Chức năng chính không dùng được | Không gửi được quan sát; bản đồ không tải |
| S3 · Trung bình | Chức năng chạy nhưng sai một phần | Bộ lọc bản đồ không lưu |
| S4 · Thấp | Giao diện, chính tả | Lệch bố cục |

**Điều kiện merge `dev → main`:**

- [ ] CI xanh trên mọi thành phần
- [ ] Toàn bộ ca kiểm thử chấp nhận P0 thuộc milestone đạt
- [ ] Không còn lỗi S1, S2 đang mở
- [ ] Chỉ số mô hình và thực địa không kém bản trên `main`
- [ ] Tài liệu trong `docs/` khớp với hành vi đã cài đặt
