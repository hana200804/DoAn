# Signa — Web quản trị

Website cho quản trị viên: duyệt các biển báo có mâu thuẫn, xử lý báo cáo sai, gán nhãn ảnh khó cho vòng huấn luyện lại, quản lý danh mục biển và xem thống kê.

> **Trạng thái:** chưa có mã nguồn. Tài liệu này là đặc tả phạm vi và quy ước trước khi code.

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
| UI | Ant Design |
| Dữ liệu | TanStack Query, Axios |
| Định tuyến | React Router |
| Bản đồ | VietMap GL JS |
| Biểu đồ | Recharts |
| Gán nhãn | Canvas (Konva) để vẽ/sửa bounding box |
| Kiểm tra | ESLint, Prettier, Vitest, Playwright |

## Cấu trúc thư mục dự kiến

```text
web-admin/
├── src/
│   ├── pages/           # conflicts, reports, labeling, signs, stats, login
│   ├── features/        # logic theo nghiệp vụ (hooks, API calls)
│   ├── components/      # thành phần dùng chung
│   ├── api/             # client sinh từ OpenAPI của backend
│   ├── routes/
│   └── main.tsx
├── tests/e2e/           # Playwright
├── .env.example
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

> Hướng dẫn chi tiết được bổ sung khi khởi tạo mã nguồn.

```bash
cd web-admin
cp .env.example .env        # VITE_API_BASE_URL, VITE_VIETMAP_API_KEY
npm install
npm run dev                 # http://localhost:5173
```

## Kiểm thử

| Loại | Công cụ | Lệnh |
| --- | --- | --- |
| Unit / component | Vitest + Testing Library | `npm test` |
| End-to-end | Playwright | `npm run test:e2e` |

## Tài liệu liên quan

- [Đặc tả yêu cầu](../docs/02-dac-ta-yeu-cau.md)
- [Đặc tả API](../docs/07-api.md)
