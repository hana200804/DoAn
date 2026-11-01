# Signa — Web quản trị

Website cho quản trị viên: duyệt các biển báo có mâu thuẫn, xử lý báo cáo sai, gán nhãn ảnh khó cho vòng huấn luyện lại, quản lý danh mục biển và xem thống kê.

## Mục lục

- [Phạm vi](#phạm-vi)
- [Công nghệ](#công-nghệ)
- [Cấu trúc thư mục dự kiến](#cấu-trúc-thư-mục-dự-kiến)
- [Trang và chức năng](#trang-và-chức-năng)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Kiểm thử](#kiểm-thử)
- [Tài liệu liên quan](#tài-liệu-liên-quan)

## Phạm vi

Admin **không duyệt từng biển**: biển có điểm tin cậy cao được hệ thống tự công nhận; admin chỉ xử lý những ca hệ thống không tự quyết được.

| Trong phạm vi | Ngoài phạm vi |
| --- | --- |
| Hàng đợi ca mâu thuẫn (cùng vị trí, khác mã biển) | Giao diện cho người lái |
| Xử lý báo cáo biển sai từ người dùng | Huấn luyện mô hình trực tiếp từ web |
| Gán nhãn ảnh khó (vẽ lại box, chọn mã biển) | |
| Quản lý danh mục loại biển, biển báo | |
| Thống kê: quan sát theo ngày, người đóng góp, độ phủ bản đồ, phiên bản mô hình | |

## Công nghệ

| Hạng mục | Lựa chọn |
| --- | --- |
| Ngôn ngữ | TypeScript |
| Framework | React 18 + Vite |
| UI | CSS thuần |
| Dữ liệu | `fetch` + hook `useLoad` |
| Định tuyến | React Router |
| Gán nhãn | Canvas HTML (khi làm #44) |
| Kiểm tra | `tsc --noEmit`, Vitest |

## Cấu trúc thư mục dự kiến

```text
web-admin/
├── src/
│   ├── pages/        # Login, Conflicts, Reports, SignTypes
│   ├── api.ts        # gọi /api/v1, phiên trong sessionStorage, lỗi chuẩn (có test)
│   ├── useLoad.ts
│   └── main.tsx      # điều hướng
├── Dockerfile        # build tĩnh, phục vụ bằng nginx
├── package.json
└── vite.config.ts
```

## Trang và chức năng

| Trang | Chức năng | Yêu cầu |
| --- | --- | --- |
| Đăng nhập | Tài khoản vai trò ADMIN | FR-20 |
| Ca mâu thuẫn | Danh sách cụm biển có nhiều mã khác nhau; xem ảnh cắt, chốt mã đúng | FR-21 |
| Báo cáo sai | Danh sách báo cáo từ người dùng; chấp nhận / từ chối kèm lý do | FR-22 |
| Gán nhãn | Hàng đợi ảnh độ tin cậy thấp; sửa box, chọn mã biển, bỏ qua | FR-23 |
| Danh mục biển | Thêm/sửa loại biển, mã QCVN 41, ý nghĩa, ảnh minh hoạ | FR-24 |
| Thống kê | Quan sát/ngày, top người đóng góp, tỉ lệ tự duyệt, bản đồ độ phủ | FR-25 |

## Cài đặt và chạy

```bash
cd web-admin
# /api được chuyển sang nginx của infra/ (mặc định http://localhost:8080, đổi bằng VITE_API_PROXY)
npm install
npm run dev                 # http://localhost:5173
```

## Kiểm thử

| Loại | Công cụ | Lệnh |
| --- | --- | --- |
| Unit | Vitest | `npm test` |

## Tài liệu liên quan

- [Đặc tả yêu cầu](../docs/02-dac-ta-yeu-cau.md)
- [Đặc tả API](../docs/07-api.md)
