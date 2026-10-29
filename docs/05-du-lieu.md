# 05 · Dữ liệu và gán nhãn

## Mục lục

1. [Nguồn dữ liệu](#1-nguồn-dữ-liệu)
2. [Danh mục nhãn](#2-danh-mục-nhãn)
3. [Hướng dẫn gán nhãn](#3-hướng-dẫn-gán-nhãn)
4. [Video thực địa](#4-video-thực-địa)
5. [Phân chia tập dữ liệu](#5-phân-chia-tập-dữ-liệu)
6. [Tăng cường dữ liệu](#6-tăng-cường-dữ-liệu)
7. [Dữ liệu tổng hợp](#7-dữ-liệu-tổng-hợp)
8. [Mất cân bằng lớp](#8-mất-cân-bằng-lớp)
9. [Phiên bản và lưu trữ](#9-phiên-bản-và-lưu-trữ)
10. [Thống kê cần báo cáo](#10-thống-kê-cần-báo-cáo)

## 1. Nguồn dữ liệu

| Nguồn | Dùng cho | Ghi chú |
| --- | --- | --- |
| Bộ dữ liệu biển báo Việt Nam công khai (Roboflow Universe, Zalo AI Challenge) | Detector, classifier | Kiểm tra giấy phép từng bộ trước khi dùng; gán nhãn lại theo danh mục §2 |
| Video thực địa do nhóm tự quay | Detector, tập kiểm thử thực địa | Nguồn chính cho điều kiện thực tế: điện thoại gắn trên xe, ban đêm, trời mưa |
| Dữ liệu tổng hợp từ mẫu biển QCVN 41 | Classifier | Bổ sung lớp hiếm; không dùng cho tập test |
| Ảnh cắt từ người dùng (vòng lặp dữ liệu) | Classifier | Chỉ ảnh đã được quản trị viên gán nhãn |

Mọi nguồn được ghi trong manifest (§9) để truy được nguồn gốc của từng ảnh.

## 2. Danh mục nhãn

Danh mục có hai cấp, lưu trong `ai/data/labels.yaml` và có số phiên bản (label map version):

- **Nhóm** — đầu ra của detector: `prohibitory`, `mandatory`, `warning`, `auxiliary`, `other`.
- **Mã** — đầu ra của classifier: mã theo QCVN 41:2024/BGTVT. Biển tốc độ tối đa thêm hậu tố giá trị: `P.127_50`.

```yaml
version: 1
groups:
  prohibitory: { id: 0, name: Biển cấm }
  mandatory:   { id: 1, name: Biển hiệu lệnh }
  warning:     { id: 2, name: Biển nguy hiểm và cảnh báo }
  auxiliary:   { id: 3, name: Biển phụ }
  other:       { id: 4, name: Biển khác }
codes:
  - code: P.102
    group: prohibitory
    name: Cấm đi ngược chiều
  - code: P.123a
    group: prohibitory
    name: Cấm rẽ trái
  - code: P.127_50
    group: prohibitory
    name: Tốc độ tối đa cho phép 50 km/h
    speed_kmh: 50
  - code: R.303
    group: mandatory
    name: Nơi giao nhau chạy theo vòng xuyến
  - code: W.207a
    group: warning
    name: Giao nhau với đường không ưu tiên
```

Quy tắc:

- Mã và tên đối chiếu nguyên văn QCVN 41:2024/BGTVT.
- Một mã chỉ được đưa vào danh mục khi có đủ mẫu để huấn luyện và kiểm thử; mã chưa đủ mẫu được gộp vào `other_<nhóm>`.
- Thay đổi danh mục làm tăng `version` và tăng `MAJOR` của phiên bản mô hình ([Thiết kế AI §10](04-thiet-ke-ai.md#10-phát-hành-mô-hình)).
- Bảng `sign_types` trong CSDL được nạp từ file này.
- Biển lắp theo QCVN 41:2019 vẫn còn trên đường trong giai đoạn chuyển tiếp: dữ liệu gồm cả hai kiểu biển; mã nào khác nhau giữa hai phiên bản được ghi bảng đối chiếu trong `labels.yaml`.

## 3. Hướng dẫn gán nhãn

### Khoanh vùng

1. Box ôm sát mép ngoài của biển, gồm cả viền; không gồm cột.
2. Gán nhãn mọi biển có chiều cao ≥ 10 px trong ảnh gốc.
3. Mỗi biển trên cùng một cột có box riêng; biển phụ có box riêng thuộc nhóm `auxiliary`.
4. Biển bị che: vẫn gán nhãn nếu còn nhận ra mã; thêm thẻ `occluded`. Không nhận ra mã thì chỉ gán nhóm, mã `unknown`.

### Không gán nhãn

- Mặt sau của biển.
- Biển quay ngang so với hướng nhìn (góc lệch > 60°) — gắn thẻ `side` nếu vẫn muốn giữ ảnh.
- Hình biển in trên quảng cáo, thân xe, phản chiếu qua kính.

### Thẻ thuộc tính

Ghi ngoài nhãn YOLO, trong manifest: `occluded`, `side`, `night`, `rain`, `blur`, `glare`.

### Kiểm soát chất lượng

- 10% ảnh được hai người gán nhãn độc lập.
- Đạt yêu cầu khi ≥ 95% box có IoU ≥ 0,8 và cùng mã giữa hai người.
- Mỗi bất đồng được giải quyết và, nếu cần, bổ sung thành một quy tắc mới trong hướng dẫn này.

## 4. Video thực địa

| Hạng mục | Thiết lập |
| --- | --- |
| Gắn máy | Giữa kính lái, phía sau gương chiếu hậu, hướng thẳng, khung hình ngang |
| Ghi hình | 1080p, 30 FPS |
| GPS | Ghi vị trí, tốc độ, hướng mỗi giây, đồng bộ dấu thời gian với video |
| Điều kiện | Ngày / đêm / mưa × nội đô / ngoại ô |
| Tuyến | Nhiều tuyến khác nhau; mỗi tuyến đi lặp lại để có nhiều lượt qua cùng một biển |
| Nhãn chuẩn | Danh sách biển thật trên mỗi tuyến: mã, vị trí, hướng — lập từ video và bản đồ |

Cách dùng:

- Trích khung ở 2 FPS để gán nhãn → **tập kiểm thử thực địa** (không bao giờ dùng để huấn luyện).
- Phát lại toàn bộ video qua chuỗi xử lý → đo cảnh báo sai, độ trễ, sai số vị trí ([Kiểm thử §5](08-kiem-thu.md#5-thử-nghiệm-thực-địa)).
- Nhiều lượt đi qua cùng tuyến → mô phỏng nhiều người dùng cho bản đồ tự xác minh.

Video gốc lưu riêng tư. Khi công bố ảnh trong báo cáo, làm mờ khuôn mặt và biển số xe.

## 5. Phân chia tập dữ liệu

- Chia theo **chuỗi ảnh / tuyến đường**, không chia ngẫu nhiên từng khung, để các khung gần giống nhau không rơi vào cả train và test.
- Dữ liệu không phải thực địa: train / val / test = 70 / 15 / 15.
- Tập kiểm thử thực địa đứng riêng, chỉ dùng để đánh giá.
- Mỗi mã có ít nhất 20 box trong tập test; mã không đạt được ghi rõ trong báo cáo.
- Kết quả chia ghi trong manifest, cố định theo phiên bản dữ liệu.

## 6. Tăng cường dữ liệu

| Được dùng | Tham số tham khảo |
| --- | --- |
| Thay đổi độ sáng, độ tương phản | ±30% |
| Nhoè chuyển động | Kernel 3–9 px |
| Mưa, sương mù giả lập | Albumentations `RandomRain`, `RandomFog` |
| Nhiễu, nén JPEG | Chất lượng 40–90 |
| Co giãn, dịch chuyển | Theo mặc định của Ultralytics |
| Xoay nhẹ | ≤ 10° |
| Mosaic (detector) | Theo mặc định của Ultralytics |

| Không được dùng | Lý do |
| --- | --- |
| Lật ngang | Đổi nghĩa biển: cấm rẽ trái thành cấm rẽ phải, hướng đi vòng sai chiều |
| Lật dọc, xoay lớn > 10° | Biển thật không xuất hiện như vậy; một số biển đổi nghĩa |
| Đổi sắc độ mạnh | Màu là đặc trưng phân nhóm (đỏ cấm, xanh hiệu lệnh, vàng cảnh báo) |

## 7. Dữ liệu tổng hợp

1. Lấy hình vẽ chuẩn của từng mã biển theo QCVN 41.
2. Biến đổi ngẫu nhiên: phối cảnh, xoay ≤ 10°, tỉ lệ, ánh sáng, nhoè, nhiễu, che một phần.
3. Dán lên khung nền không có biển lấy từ video thực địa; nhãn sinh tự động.
4. Dữ liệu tổng hợp chiếm tối đa 30% tập huấn luyện classifier và không bao giờ có trong tập validation, test.

## 8. Mất cân bằng lớp

- Lấy mẫu cân bằng theo lớp khi huấn luyện classifier.
- Bổ sung dữ liệu tổng hợp cho lớp hiếm.
- Gộp mã quá hiếm vào `other_<nhóm>`.
- Luôn báo cáo chỉ số **theo từng lớp**, không chỉ trung bình.

## 9. Phiên bản và lưu trữ

| Thành phần | Nơi lưu | Có trong Git |
| --- | --- | --- |
| Ảnh, video | MinIO bucket `datasets` (riêng tư) | Không |
| Manifest `ai/data/manifests/vX.Y.csv` | Repo | Có |
| Danh mục nhãn `ai/data/labels.yaml` | Repo | Có |
| Nhật ký thay đổi `ai/data/CHANGELOG.md` | Repo | Có |

Phiên bản dữ liệu `vMAJOR.MINOR`: tăng `MAJOR` khi đổi danh mục nhãn, tăng `MINOR` khi thêm hoặc sửa ảnh.

Cột của manifest:

| Cột | Ý nghĩa |
| --- | --- |
| `path` | Đường dẫn đối tượng trong bucket |
| `sha256` | Băm nội dung để phát hiện thay đổi |
| `split` | `train`, `val`, `test`, `field` |
| `source` | `public:<tên bộ>`, `field:<mã tuyến>`, `synthetic`, `user` |
| `sequence` | Mã chuỗi ảnh hoặc tuyến, dùng để chia tập |
| `tags` | Thẻ thuộc tính, cách nhau bằng dấu `;` |
| `label_map_version` | Phiên bản danh mục nhãn |

## 10. Thống kê cần báo cáo

- Số box theo mã và theo nhóm, trong từng tập.
- Phân bố kích thước box: nhỏ (< 32² px), vừa, lớn (> 96² px).
- Phân bố theo điều kiện: ngày, đêm, mưa.
- Tỉ lệ theo nguồn dữ liệu.
- Kết quả kiểm soát chất lượng gán nhãn.
