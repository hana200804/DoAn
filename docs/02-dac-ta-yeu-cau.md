# 02 · Đặc tả yêu cầu

Tài liệu định nghĩa toàn bộ yêu cầu của hệ thống. Các tài liệu khác chỉ tham chiếu mã yêu cầu (`FR-xx`, `NFR-xx`), không định nghĩa lại.

## Mục lục

1. [Phát biểu vấn đề](#1-phát-biểu-vấn-đề)
2. [Mục tiêu](#2-mục-tiêu)
3. [Không thuộc mục tiêu](#3-không-thuộc-mục-tiêu)
4. [Tác nhân](#4-tác-nhân)
5. [User story](#5-user-story)
6. [Yêu cầu chức năng](#6-yêu-cầu-chức-năng)
7. [Tiêu chí chấp nhận](#7-tiêu-chí-chấp-nhận)
8. [Yêu cầu phi chức năng](#8-yêu-cầu-phi-chức-năng)
9. [Vấn đề cần làm rõ](#9-vấn-đề-cần-làm-rõ)

## 1. Phát biểu vấn đề

Người lái ô tô có thể bỏ lỡ hoặc hiểu sai biển báo do biển bị che khuất, thời tiết xấu, đi tuyến đường lạ hoặc biển phụ có điều kiện phức tạp. Hệ thống cần nhận diện biển báo theo QCVN 41 ngay khi xe đang chạy, nhắc người lái đúng lúc, và tận dụng những gì các xe khác đã thấy để cảnh báo cả những biển không nhìn thấy trực tiếp.

## 2. Mục tiêu

| # | Mục tiêu | Đo bằng | Ngưỡng |
| --- | --- | --- | --- |
| G1 | Nhận diện biển báo theo thời gian thực trên điện thoại, không cần mạng | FPS trên thiết bị tham chiếu | ≥ 15 FPS |
| G2 | Nhận diện chính xác các biển quan trọng | Recall nhóm biển cấm và biển tốc độ trên tập test | ≥ 0,90 |
| G3 | Cảnh báo đúng lúc và ít làm phiền | Số cảnh báo sai trên video thực địa | ≤ 1 trên 10 km |
| G4 | Bản đồ biển tự xác minh, giảm việc duyệt tay | Precision của cụm biển được tự công nhận; số ca cần quản trị viên | Tốt hơn phương án khử trùng theo bán kính cố định trên cùng dữ liệu |
| G5 | Mô hình cải thiện được từ dữ liệu người dùng | mAP sau mỗi vòng huấn luyện lại | Không thấp hơn mô hình đang dùng |

## 3. Không thuộc mục tiêu

| Không làm | Lý do |
| --- | --- |
| Ứng dụng iOS | Tập trung một nền tảng để làm đủ sâu phần AI trên thiết bị |
| Hỗ trợ xe máy | Rung lắc và góc nhìn khác hẳn ô tô; cần dữ liệu và kiểm thử riêng |
| Nhận diện vạch kẻ đường, đèn tín hiệu | Bài toán khác (phân đoạn, trạng thái đèn), không thuộc biển báo |
| Bảng chỉ dẫn lớn trên cao tốc | Nội dung chủ yếu là chữ và hướng đi, ít liên quan cảnh báo vi phạm |
| Can thiệp điều khiển phương tiện | Hệ thống chỉ hỗ trợ; người lái chịu trách nhiệm |

## 4. Tác nhân

| Tác nhân | Mô tả | Kênh |
| --- | --- | --- |
| Người lái | Người dùng đã đăng ký, dùng ứng dụng khi lái ô tô | Ứng dụng di động |
| Khách | Chưa đăng nhập; dùng được nhận diện và cảnh báo cục bộ, không gửi quan sát | Ứng dụng di động |
| Quản trị viên | Vai trò `ADMIN`; vận hành dữ liệu biển báo và gán nhãn | Web quản trị |
| Hệ thống | Tác nhân nền: gom cụm, chấm điểm tin cậy, chọn ảnh khó | sign-service, ai-service |

## 5. User story

### Người lái

| ID | User story | Ưu tiên |
| --- | --- | --- |
| US-01 | Là người lái, tôi muốn được nhắc bằng giọng nói khi vượt tốc độ cho phép để không vi phạm vì không để ý biển | P0 |
| US-02 | Là người lái, tôi muốn được báo trước khi sắp tới biển cấm áp dụng cho xe của mình để kịp đổi hướng | P0 |
| US-03 | Là người lái, tôi muốn ứng dụng vẫn nhận diện và cảnh báo khi mất sóng để dùng được trên mọi tuyến | P0 |
| US-04 | Là người lái, tôi muốn được cảnh báo cả biển bị che khuất nếu xe khác đã thấy để không bỏ lỡ biển | P0 |
| US-05 | Là người lái, tôi muốn thấy biển báo quanh tôi trên bản đồ để chuẩn bị trước cho tuyến đường | P0 |
| US-06 | Là người lái, tôi muốn chỉ gửi dữ liệu khi có Wi-Fi để không tốn dung lượng di động | P0 |
| US-07 | Là người lái, tôi muốn biết biển phụ "6h–22h" có áp dụng lúc này không để không phải tự suy luận khi đang lái | P1 |
| US-08 | Là người lái, tôi muốn tìm đường tới một địa chỉ và được cảnh báo biển dọc tuyến | P1 |
| US-09 | Là người lái, tôi muốn chụp ảnh một biển lạ để biết ý nghĩa của nó | P1 |
| US-10 | Là người lái, tôi muốn báo một biển trên bản đồ là sai để bản đồ chính xác hơn | P1 |

### Quản trị viên

| ID | User story | Ưu tiên |
| --- | --- | --- |
| US-20 | Là quản trị viên, tôi muốn chỉ phải xem những biển hệ thống không tự quyết được để không phải duyệt từng biển | P0 |
| US-21 | Là quản trị viên, tôi muốn quản lý danh mục mã biển và ý nghĩa để ứng dụng hiển thị đúng | P0 |
| US-22 | Là quản trị viên, tôi muốn xử lý báo cáo sai từ người lái để sửa hoặc gỡ biển không đúng | P1 |
| US-23 | Là quản trị viên, tôi muốn gán nhãn nhanh các ảnh mô hình chưa chắc chắn để mô hình lần sau tốt hơn | P1 |
| US-24 | Là quản trị viên, tôi muốn xem thống kê quan sát và độ phủ bản đồ để biết hệ thống hoạt động thế nào | P2 |

## 6. Yêu cầu chức năng

`P0` bắt buộc · `P1` nên có · `P2` mở rộng.

### Ứng dụng di động

| ID | Yêu cầu | Ưu tiên | Story |
| --- | --- | --- | --- |
| FR-01 | Nhận diện biển báo theo luồng camera, chạy hoàn toàn trên thiết bị | P0 | US-01, US-03 |
| FR-02 | Theo dõi mỗi biển qua nhiều khung hình; chỉ công nhận khi kết quả ổn định | P0 | US-01, US-02 |
| FR-03 | Cảnh báo vượt tốc độ so với giới hạn đang có hiệu lực | P0 | US-01 |
| FR-04 | Cảnh báo biển cấm, biển nguy hiểm sắp tới, từ camera hoặc từ bản đồ đã tải trước | P0 | US-02, US-04 |
| FR-05 | Màn hình lái xe: khung biển nhận diện, tốc độ hiện tại, giới hạn đang có hiệu lực | P0 | US-01 |
| FR-06 | Bản đồ biển báo quanh vị trí hiện tại | P0 | US-05 |
| FR-07 | Lọc biển trên bản đồ theo nhóm biển và điểm tin cậy | P1 | US-05 |
| FR-08 | Xem chi tiết biển và báo cáo biển sai | P1 | US-10 |
| FR-09 | Tìm kiếm địa chỉ, chỉ đường, chế độ dẫn đường kèm cảnh báo dọc tuyến | P1 | US-08 |
| FR-10 | Tra cứu biển bằng ảnh chụp | P1 | US-09 |
| FR-11 | Xem thống kê đóng góp của bản thân | P2 | — |
| FR-12 | Đăng ký, đăng nhập, đăng xuất, đổi mật khẩu, quên mật khẩu | P0 | — |
| FR-13 | Cài đặt: loại xe, bật/tắt giọng nói, bật/tắt đóng góp dữ liệu, chỉ gửi qua Wi-Fi | P0 | US-02, US-06 |
| FR-14 | Gửi quan sát lên server; xếp hàng khi không có mạng | P0 | US-04, US-06 |
| FR-15 | Nhận và áp dụng phiên bản mô hình mới qua mạng | P1 | — |
| FR-16 | Đọc biển phụ và áp điều kiện (khung giờ, loại xe) khi cảnh báo | P1 | US-07 |

### Server

| ID | Yêu cầu | Ưu tiên | Story |
| --- | --- | --- | --- |
| FR-17 | Gom quan sát thành cụm biển và tính điểm tin cậy | P0 | US-04, US-20 |
| FR-18 | Đánh dấu biển "có thể đã mất" khi nhiều lượt đi qua không thấy | P2 | US-04 |
| FR-19 | Chọn ảnh khó vào hàng đợi gán nhãn | P1 | US-23 |

### Web quản trị

| ID | Yêu cầu | Ưu tiên | Story |
| --- | --- | --- | --- |
| FR-20 | Đăng nhập với vai trò quản trị viên | P0 | — |
| FR-21 | Xử lý ca mâu thuẫn: chốt mã biển đúng hoặc loại cụm | P0 | US-20 |
| FR-22 | Xử lý báo cáo biển sai | P1 | US-22 |
| FR-23 | Gán nhãn ảnh khó | P1 | US-23 |
| FR-24 | Quản lý danh mục loại biển | P0 | US-21 |
| FR-25 | Thống kê quan sát, người đóng góp, độ phủ, phiên bản mô hình | P2 | US-24 |

## 7. Tiêu chí chấp nhận

### FR-01 · Nhận diện trên thiết bị

- **Cho trước** ứng dụng ở màn hình lái xe và đã được cấp quyền camera,
  **khi** một biển thuộc danh mục xuất hiện trong khung hình với chiều cao ≥ 24 px,
  **thì** biển được khoanh vùng và gán mã QCVN 41 mà không cần kết nối mạng.
- [ ] Ở chế độ máy bay vẫn nhận diện được
- [ ] Đạt NFR-01
- [ ] Không có biển trong khung hình thì không hiển thị khung nào

### FR-02 · Theo dõi đa khung hình

- [ ] Một biển chỉ được công nhận khi xuất hiện ở ít nhất 3 trong 5 khung liên tiếp và nhãn chiếm ≥ 60% phiếu
- [ ] Phát hiện chỉ xuất hiện ở một khung không sinh cảnh báo
- [ ] Mỗi biển sinh đúng **một** quan sát cho mỗi lần đi qua

### FR-03 · Cảnh báo vượt tốc độ

- **Cho trước** giới hạn đang có hiệu lực là 50 km/h,
  **khi** tốc độ GPS vượt 53 km/h liên tục ít nhất 3 giây,
  **thì** ứng dụng đọc "Vượt tốc độ, giới hạn 50" và hiện cảnh báo trên màn hình.
- [ ] Giới hạn trở về trạng thái "chưa rõ" khi gặp biển hết hạn chế tốc độ
- [ ] Khi tắt giọng nói trong cài đặt, chỉ cảnh báo trên màn hình
- [ ] Khi chưa rõ giới hạn, không phát cảnh báo tốc độ

### FR-04 · Cảnh báo biển sắp tới

- **Cho trước** bản đồ đã tải trước có biển cấm rẽ trái áp dụng cho ô tô ở phía trước theo hướng đi,
  **khi** xe tiến vào phạm vi cảnh báo của biển,
  **thì** ứng dụng đọc "Phía trước cấm rẽ trái", kể cả khi camera chưa thấy biển.
- [ ] Biển không áp dụng cho loại xe đã chọn thì không cảnh báo
- [ ] Mỗi biển chỉ cảnh báo một lần cho mỗi lần đi qua
- [ ] Nhiều cảnh báo cùng lúc được đọc theo thứ tự ưu tiên: tốc độ, biển cấm, biển nguy hiểm

### FR-05 · Màn hình lái xe

- [ ] Hiển thị khung và mã của biển đang theo dõi
- [ ] Hiển thị tốc độ hiện tại và biểu tượng giới hạn đang có hiệu lực
- [ ] Màn hình không tự tắt khi đang ở chế độ lái xe
- [ ] Mọi thao tác chính dùng được mà không cần đọc chữ nhỏ

### FR-06 · Bản đồ biển báo

- **Cho trước** người dùng mở màn hình bản đồ,
  **khi** bản đồ hiển thị một khu vực,
  **thì** các cụm biển trạng thái `VERIFIED` trong khu vực đó được hiển thị bằng biểu tượng của mã biển.
- [ ] Kéo hoặc phóng bản đồ thì tải thêm biển của khu vực mới
- [ ] Chạm vào biển thì mở chi tiết (FR-08)

### FR-07 · Lọc biển

- [ ] Lọc theo nhóm biển
- [ ] Lọc theo ngưỡng điểm tin cậy; có thể hiện cả cụm `CANDIDATE`
- [ ] Bộ lọc được giữ lại khi mở lại ứng dụng

### FR-08 · Chi tiết và báo cáo sai

- [ ] Chi tiết gồm ảnh cắt đại diện, mã, tên, ý nghĩa, điểm tin cậy, số lượt nhìn thấy
- [ ] Báo cáo sai bắt buộc chọn lý do: không tồn tại, sai loại, sai vị trí, khác
- [ ] Mỗi người chỉ có một báo cáo đang chờ xử lý cho mỗi biển

### FR-09 · Tìm đường và dẫn đường

- [ ] Tìm địa chỉ theo từ khoá, chọn điểm đến trên bản đồ
- [ ] Hiển thị tuyến đường đề xuất
- [ ] Khi bắt đầu dẫn đường, tải trước biển dọc tuyến (FR-04) và bật nhận diện

### FR-10 · Tra cứu biển bằng ảnh

- [ ] Chụp hoặc chọn ảnh thì hiển thị các biển nhận diện được kèm ý nghĩa
- [ ] Không có biển thì thông báo rõ ràng

### FR-11 · Thống kê cá nhân

- [ ] Hiển thị số quan sát đã gửi, số thuộc cụm được công nhận, số bị loại

### FR-12 · Tài khoản

- [ ] Đăng ký bằng email và mật khẩu ≥ 8 ký tự; email trùng báo lỗi rõ ràng
- [ ] Đăng nhập trả về phiên; đăng xuất thu hồi phiên
- [ ] Quên mật khẩu gửi mã OTP qua email; mã có thời hạn và giới hạn số lần nhập sai
- [ ] Đổi mật khẩu yêu cầu mật khẩu cũ

### FR-13 · Cài đặt

- [ ] Chọn loại xe: ô tô con, xe khách, xe tải; ảnh hưởng FR-04 và FR-16
- [ ] Bật/tắt cảnh báo giọng nói
- [ ] Tuỳ chọn "chỉ gửi dữ liệu qua Wi-Fi", mặc định bật
- [ ] Bật/tắt đóng góp dữ liệu; khi tắt, không gửi quan sát hay ảnh cắt nào

### FR-14 · Gửi quan sát

- **Cho trước** thiết bị không có mạng phù hợp với cài đặt,
  **khi** có quan sát mới,
  **thì** quan sát được lưu vào hàng đợi trên máy và tự gửi khi có mạng phù hợp.
- [ ] Gửi theo lô; gửi lại một lô không tạo bản ghi trùng
- [ ] Khách (chưa đăng nhập) không gửi quan sát
- [ ] Hàng đợi có giới hạn dung lượng; khi đầy, bỏ quan sát cũ nhất

### FR-15 · Cập nhật mô hình

- [ ] Ứng dụng kiểm tra phiên bản mô hình mới khi khởi động
- [ ] Tải xong thì kiểm tra SHA-256 và chạy thử trên ảnh mẫu
- [ ] Mô hình mới chỉ được dùng từ phiên lái kế tiếp
- [ ] Mô hình lỗi hoặc không tương thích label map thì giữ mô hình cũ

### FR-16 · Biển phụ

- [ ] Biển phụ được gắn với biển chính phía trên
- [ ] Điều kiện khung giờ và loại xe được áp dụng khi quyết định cảnh báo
- [ ] Biển phụ chưa đọc được thì cảnh báo kèm câu "có biển phụ, chú ý điều kiện"

### FR-17 · Gom cụm và điểm tin cậy

- **Cho trước** hai người lái khác nhau gửi quan sát cùng mã biển, cùng hướng đi, trong bán kính gom cụm,
  **khi** sign-service xử lý các quan sát,
  **thì** chúng thuộc cùng một cụm và cụm chuyển `VERIFIED` khi điểm tin cậy ≥ 0,8.
- [ ] Một người lái gửi nhiều lần cho cùng một biển chỉ được tính trọng số giới hạn
- [ ] Hai mã biển cạnh tranh trong cùng cụm thì cụm chuyển `CONFLICT`
- [ ] Mọi thay đổi trạng thái được ghi nhật ký

### FR-18 · Biển có thể đã mất

- [ ] Cụm `VERIFIED` nhận quan sát âm từ ít nhất 3 lượt đi qua khác nhau thì chuyển `MISSING_SUSPECTED`

### FR-19 · Chọn ảnh khó

- [ ] Quan sát thoả tiêu chí trong [Thiết kế AI §9](04-thiet-ke-ai.md#9-vòng-lặp-dữ-liệu) sinh một nhiệm vụ gán nhãn
- [ ] Không sinh nhiệm vụ trùng cho cùng một quan sát

### FR-20 · Đăng nhập quản trị

- [ ] Chỉ tài khoản vai trò `ADMIN` đăng nhập được web quản trị
- [ ] Mọi API quản trị trả `403` với vai trò khác

### FR-21 · Ca mâu thuẫn

- **Cho trước** một cụm ở trạng thái `CONFLICT`,
  **khi** quản trị viên chọn mã đúng,
  **thì** cụm chuyển `VERIFIED` với mã đó và hành động được ghi nhật ký kèm người thực hiện.
- [ ] Quản trị viên có thể loại cụm (`REJECTED`) kèm lý do
- [ ] Uy tín của người đóng góp được cập nhật theo kết quả

### FR-22 · Báo cáo sai

- [ ] Chấp nhận báo cáo thì cụm được sửa mã hoặc chuyển `REJECTED`
- [ ] Từ chối báo cáo bắt buộc nhập lý do

### FR-23 · Gán nhãn

- [ ] Hiển thị ảnh cắt, khung gợi ý, mã gợi ý
- [ ] Cho phép sửa khung, chọn mã, bỏ qua
- [ ] Kết quả lưu kèm phiên bản dữ liệu đích

### FR-24 · Danh mục loại biển

- [ ] Thêm, sửa mã, nhóm, tên, ý nghĩa, ảnh minh hoạ, giá trị tốc độ (biển tốc độ)
- [ ] Không xoá mã đang được dùng; chỉ ngừng sử dụng

### FR-25 · Thống kê

- [ ] Số quan sát theo thời gian, người đóng góp nhiều nhất, tỉ lệ cụm tự công nhận, bản đồ độ phủ, phiên bản mô hình đang dùng

## 8. Yêu cầu phi chức năng

| ID | Nhóm | Yêu cầu | Chỉ tiêu |
| --- | --- | --- | --- |
| NFR-01 | Hiệu năng | Tốc độ xử lý khung hình trên thiết bị tham chiếu | ≥ 15 FPS |
| NFR-02 | Hiệu năng | Độ trễ từ khi biển được xác nhận đến khi phát cảnh báo | < 500 ms |
| NFR-03 | Khả dụng | Nhận diện và cảnh báo hoạt động không cần mạng | 100% chức năng FR-01 → FR-05 |
| NFR-04 | Tài nguyên | Pin tiêu thụ khi chạy liên tục; tự giảm tần suất xử lý khi máy nóng | < 15 %/giờ |
| NFR-05 | Tài nguyên | Dữ liệu di động tiêu thụ | < 20 MB/giờ |
| NFR-06 | Tài nguyên | Tổng kích thước mô hình trên thiết bị | < 15 MB |
| NFR-07 | Độ chính xác | mAP@0.5 trên tập test | ≥ 0,89 |
| NFR-08 | Độ chính xác | Recall nhóm biển cấm và biển tốc độ | ≥ 0,90 |
| NFR-09 | Riêng tư | Chỉ gửi ảnh cắt vùng biển; vị trí chỉ gửi kèm quan sát; không lưu lộ trình | Kiểm tra bằng review mã và log mạng |
| NFR-10 | Bảo mật | Mật khẩu BCrypt; phiên có thời hạn; phân quyền theo vai trò; HTTPS ở môi trường demo | Đạt các ca kiểm thử bảo mật |
| NFR-11 | Hiệu năng server | Truy vấn biển theo vùng (≤ 500 biển) | p95 < 300 ms |
| NFR-12 | Hiệu năng server | Tiếp nhận quan sát | ≥ 50 quan sát/giây |
| NFR-13 | Tương thích | Android 8.0 (API 26) trở lên; GPU delegate, dự phòng CPU | Chạy trên ≥ 2 thiết bị khác phân khúc |
| NFR-14 | Bảo trì | Mỗi service có test tự động; API mô tả bằng OpenAPI; cấu hình qua biến môi trường | CI xanh |

Cách đo từng chỉ tiêu: [08 · Kiểm thử](08-kiem-thu.md).

## 9. Vấn đề cần làm rõ

| # | Câu hỏi | Ảnh hưởng | Phụ trách | Chặn công việc |
| --- | --- | --- | --- | --- |
| Q1 | Danh mục mã biển trong phạm vi nhận diện gồm những mã nào | Gán nhãn, huấn luyện | Dữ liệu | Có |
| Q2 | Hiệu lực của biển tốc độ kết thúc ở biển hết hạn chế hay cả ở nút giao — đối chiếu QCVN 41 | Máy trạng thái cảnh báo | AI | Có |
| Q3 | Khi chưa thấy biển tốc độ, có dùng giới hạn mặc định theo loại đường không | FR-03 | AI, Mobile | Không |
| Q4 | Bộ dữ liệu công khai nào được phép sử dụng theo giấy phép | Nguồn dữ liệu | Dữ liệu | Có |
| Q5 | Thiết bị Android tham chiếu để đo hiệu năng | NFR-01, NFR-04 | Mobile | Không |
| Q6 | Kích thước chuẩn của từng nhóm biển dùng để ước lượng khoảng cách | Định vị biển | AI | Không |
