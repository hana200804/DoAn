# Hướng dẫn đóng góp

Quy ước làm việc chung của nhóm Signa. Mọi thành viên đọc hết trước khi tạo nhánh đầu tiên.

## Mục lục

1. [Mô hình nhánh](#1-mô-hình-nhánh)
2. [Quy trình một công việc](#2-quy-trình-một-công-việc)
3. [Commit](#3-commit)
4. [Pull Request và review](#4-pull-request-và-review)
5. [Issue, nhãn và milestone](#5-issue-nhãn-và-milestone)
6. [Định nghĩa "Xong"](#6-định-nghĩa-xong)
7. [Quy ước mã nguồn](#7-quy-ước-mã-nguồn)
8. [Phát hành](#8-phát-hành)
9. [Quyết định kiến trúc](#9-quyết-định-kiến-trúc)

## 1. Mô hình nhánh

```text
main     ●───────────────────────●──────────────────────●   (chỉ merge khi xong milestone, gắn tag)
          \                     /                      /
dev        ●──●──●──●──●──●──●──●──●──●──●──●──●──●──●      (nhánh tích hợp — mọi PR vào đây)
              \  /     \    /        \     /
feature/*      ●●       ●●●●          ●●●●●
```

| Nhánh | Mục đích | Tạo từ | Merge vào | Đẩy trực tiếp |
| --- | --- | --- | --- | --- |
| `main` | Bản ổn định, mỗi lần merge là một bản demo được | — | — | ❌ |
| `dev` | Nhánh tích hợp, nơi mọi PR được merge vào | `main` | `main` (cuối milestone) | ❌ |
| `feature/<issue>-<mô-tả>` | Tính năng mới | `dev` | `dev` | ✅ người phụ trách |
| `fix/<issue>-<mô-tả>` | Sửa lỗi | `dev` | `dev` | ✅ người phụ trách |
| `docs/<mô-tả>` | Chỉ sửa tài liệu | `dev` | `dev` | ✅ |
| `exp/<mô-tả>` | Thí nghiệm AI, có thể không merge | `dev` | `dev` (nếu giữ) | ✅ |
| `hotfix/<mô-tả>` | Sửa gấp trên bản đã phát hành | `main` | `main` **và** `dev` | ✅ |

Tên nhánh: chữ thường, không dấu, nối bằng `-`. Ví dụ: `feature/12-tflite-export`, `fix/31-gps-null`, `exp/yolov8s-960px`.

## 2. Quy trình một công việc

```bash
# 1. Cập nhật dev
git switch dev
git pull origin dev

# 2. Tạo nhánh từ issue #12
git switch -c feature/12-tflite-export

# 3. Làm việc, commit nhỏ và thường xuyên
git add <file>
git commit -m "feat(ai): xuất YOLOv8n sang TFLite INT8"

# 4. Trước khi mở PR: cập nhật theo dev
git fetch origin
git rebase origin/dev

# 5. Đẩy và mở PR vào dev
git push -u origin feature/12-tflite-export
gh pr create --base dev --fill
```

Không rebase nhánh đã có người khác dùng chung. Không `push --force` lên `dev` hoặc `main`.

## 3. Commit

Theo [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <mô tả ngắn, ≤ 72 ký tự>

<giải thích vì sao thay đổi, nếu cần>

Refs #<số issue>
```

| `type` | Dùng khi |
| --- | --- |
| `feat` | Thêm tính năng |
| `fix` | Sửa lỗi |
| `docs` | Chỉ tài liệu |
| `refactor` | Đổi cấu trúc, không đổi hành vi |
| `perf` | Cải thiện hiệu năng |
| `test` | Thêm/sửa test |
| `build` / `ci` | Build, CI |
| `chore` | Việc lặt vặt khác |
| `exp` | Thí nghiệm AI |

`scope`: `mobile`, `backend`, `auth`, `map`, `sign`, `ai`, `data`, `web`, `infra`, `docs`.

## 4. Pull Request và review

- PR luôn nhắm vào **`dev`** (trừ PR phát hành `dev → main` và `hotfix`).
- Mỗi PR liên kết ít nhất một issue: `Closes #12`.
- Điền đủ [mẫu PR](.github/pull_request_template.md), kèm ảnh chụp màn hình nếu đổi giao diện.
- Cần ít nhất **1 approve** từ thành viên khác; CI phải xanh.
- Kích thước khuyến nghị: < 400 dòng thay đổi. Lớn hơn thì tách PR.
- Kiểu merge: **Squash and merge** vào `dev`; **Create a merge commit** cho `dev → main`.

Khi review, kiểm tra theo thứ tự: đúng yêu cầu → đúng logic → có test → dễ đọc → tài liệu.

## 5. Issue, nhãn và milestone

Mẫu issue: **Báo lỗi**, **Tính năng**, **Công việc**, **Thí nghiệm AI**.

| Nhóm nhãn | Giá trị |
| --- | --- |
| `type:` | `feature`, `bug`, `task`, `docs`, `experiment` |
| `area:` | `mobile`, `backend`, `ai`, `data`, `web-admin`, `infra`, `docs` |
| `priority:` | `P0` (bắt buộc), `P1` (nên có), `P2` (sau này) |
| `status:` | `blocked`, `needs-review`, `duplicate`, `wontfix` |

Mỗi issue: đúng một `type:*`, ít nhất một `area:*`, một `priority:*`, thuộc một milestone, có tiêu chí hoàn thành kiểm tra được.

Mỗi milestone tương ứng một chủ đề trong [Hạng mục công việc](docs/09-hang-muc-cong-viec.md): `M1` → `M7`.

## 6. Định nghĩa "Xong"

- [ ] Đáp ứng tiêu chí chấp nhận trong issue
- [ ] Đã review và merge vào `dev`
- [ ] Có test tự động cho logic mới (xem [kế hoạch kiểm thử](docs/08-kiem-thu.md))
- [ ] Tài liệu trong `docs/` hoặc README của thư mục đã cập nhật
- [ ] Không còn TODO chưa gắn issue
- [ ] Thí nghiệm AI: kết quả ghi trong `ai/experiments/` kèm cấu hình, phiên bản dữ liệu và commit hash

## 7. Quy ước mã nguồn

| Phần | Định dạng / kiểm tra | Test |
| --- | --- | --- |
| `mobile/` (Kotlin) | ktlint, Detekt | JUnit, Compose UI test |
| `backend/` (Java) | Spotless + google-java-format, Checkstyle | JUnit 5, Testcontainers |
| `ai/` (Python) | Ruff (lint + format), mypy cho `ai/src/` | pytest |
| `web-admin/` (TS) | ESLint, Prettier | Vitest, Playwright |

**Không bao giờ commit:** khoá API, mật khẩu, file `.env`, keystore, trọng số mô hình, ảnh dữ liệu thô. Dữ liệu và trọng số lưu theo [kế hoạch dữ liệu](docs/05-du-lieu.md); bí mật cấu hình qua biến môi trường và `.env.example`.

## 8. Phát hành

Khi hoàn thành một milestone:

1. Mở PR `dev → main`, tiêu đề `release: vX.Y.0 – <tên milestone>`.
2. Ít nhất hai thành viên duyệt; chạy lại toàn bộ kiểm thử.
3. Merge, gắn tag `vX.Y.0` trên `main`, tạo GitHub Release kèm APK và ghi chú thay đổi.

Đánh số phiên bản theo [SemVer](https://semver.org/): `0.x` cho các bản trung gian; `1.0.0` là bản hoàn chỉnh của đồ án.

## 9. Quyết định kiến trúc

Quyết định ảnh hưởng nhiều module (chọn thư viện, đổi giao thức, đổi lược đồ CSDL, đổi mô hình AI) phải có ADR trong [`docs/adr/`](docs/adr/) **trước khi code**, theo [mẫu ADR](docs/adr/template.md).
