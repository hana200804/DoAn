# Chính sách bảo mật

## Báo cáo lỗ hổng

Không tạo issue công khai cho lỗ hổng bảo mật. Hãy gửi riêng qua mục
[Report a vulnerability](https://github.com/hana200804/DoAn/security/advisories/new) của repository.

## Nguyên tắc xử lý dữ liệu

| Dữ liệu | Cách xử lý |
| --- | --- |
| Mật khẩu | Băm bằng BCrypt; không dùng hàm băm nhanh như MD5, SHA-256 |
| Phiên đăng nhập | Session ID ngẫu nhiên lưu trong Redis, có thời hạn, thu hồi khi đăng xuất |
| Vị trí | Chỉ gửi kèm quan sát biển báo; không lưu lộ trình di chuyển |
| Ảnh camera | Chỉ gửi ảnh cắt vùng biển (box nới 10%), đã bỏ metadata EXIF; không gửi khung hình đầy đủ |
| Đóng góp dữ liệu | Người dùng tắt được trong cài đặt; khi tắt, không gửi quan sát hay ảnh nào |
| Lưu giữ | Ảnh cắt không được chọn gán nhãn bị xoá theo chính sách lưu giữ; xoá tài khoản thì quan sát được ẩn danh hoá |
| Khoá API, mật khẩu hạ tầng | Biến môi trường; không commit; mẫu trong `.env.example` của từng phần |
