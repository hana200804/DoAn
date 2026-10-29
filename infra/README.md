# Signa — Hạ tầng

Cấu hình chạy toàn bộ hệ thống bằng Docker Compose cho môi trường phát triển và máy chủ demo (VPS Ubuntu).

> **Trạng thái:** chưa có cấu hình. Tài liệu này là đặc tả trước khi viết.

## Mục lục

- [Thành phần](#thành-phần)
- [Môi trường](#môi-trường)
- [Cấu trúc thư mục dự kiến](#cấu-trúc-thư-mục-dự-kiến)
- [Chạy toàn bộ hệ thống](#chạy-toàn-bộ-hệ-thống)
- [Triển khai lên VPS](#triển-khai-lên-vps)
- [Sao lưu](#sao-lưu)

## Thành phần

| Container | Ảnh | Cổng | Ghi chú |
| --- | --- | --- | --- |
| `mysql` | `mysql:8` | 3306 | Hai CSDL: `auth_db`, `sign_db` |
| `redis` | `redis:7` | 6379 | Phiên, cache, Redis Streams |
| `minio` | `minio/minio` | 9000 / 9001 | Ảnh cắt biển báo, bucket `sign-crops` |
| `auth-service` | build từ `backend/` | 8081 | |
| `map-service` | build từ `backend/` | 8082 | |
| `sign-service` | build từ `backend/` | 8083 | |
| `ai-service` | build từ `ai/` | 9090 | gRPC, chỉ mở trong mạng nội bộ |
| `web-admin` | build từ `web-admin/`, phục vụ bằng Nginx | 80 | |
| `nginx` | `nginx:stable` | 8080 (local) / 443 (demo) | Điểm vào duy nhất cho app và web, định tuyến theo đường dẫn; TLS trên demo |

## Môi trường

| Môi trường | Mục đích | Cấu hình |
| --- | --- | --- |
| `local` | Phát triển trên máy cá nhân | `docker-compose.yml` |
| `demo` | Máy chủ demo khi bảo vệ | `docker-compose.yml` + `docker-compose.demo.yml` |

## Cấu trúc thư mục dự kiến

```text
infra/
├── docker-compose.yml          # local: hạ tầng + service
├── docker-compose.demo.yml     # ghi đè cho VPS
├── .env.example                # toàn bộ biến môi trường, không có giá trị thật
├── mysql/init/                 # script tạo CSDL, user
├── nginx/                      # cấu hình reverse proxy
└── scripts/                    # backup.sh, restore.sh, deploy.sh
```

## Chạy toàn bộ hệ thống

> Hướng dẫn chi tiết được bổ sung khi khởi tạo mã nguồn.

```bash
cd infra
cp .env.example .env            # điền mật khẩu, khoá API
docker compose up -d --build
docker compose ps
```

## Triển khai lên VPS

1. Merge `dev → main` và gắn tag phiên bản (xem [CONTRIBUTING](../CONTRIBUTING.md#8-phát-hành)).
2. Trên VPS: `git fetch --tags`, `git checkout vX.Y.Z`.
3. `docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build`.
4. Kiểm tra `docker compose ps` và `/actuator/health` của từng service; lỗi thì checkout tag trước đó và chạy lại bước 3.

## Sao lưu

- MySQL: `mysqldump` qua `scripts/backup.sh`.
- MinIO: sao chép các bucket bằng `mc mirror` trong cùng script.
