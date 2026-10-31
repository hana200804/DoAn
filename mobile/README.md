# Signa — Ứng dụng di động

Ứng dụng Android cho người lái: nhận diện biển báo ngay trên điện thoại, cảnh báo bằng giọng nói, xem bản đồ biển báo và dẫn đường.

## Mục lục

- [Phạm vi](#phạm-vi)
- [Công nghệ](#công-nghệ)
- [Kiến trúc trong ứng dụng](#kiến-trúc-trong-ứng-dụng)
- [Cấu trúc thư mục dự kiến](#cấu-trúc-thư-mục-dự-kiến)
- [Màn hình](#màn-hình)
- [Yêu cầu hiệu năng](#yêu-cầu-hiệu-năng)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Cấu hình](#cấu-hình)
- [Kiểm thử](#kiểm-thử)
- [Tài liệu liên quan](#tài-liệu-liên-quan)

## Phạm vi

| Trong phạm vi | Ngoài phạm vi |
| --- | --- |
| Nhận diện biển báo trên thiết bị (TFLite) theo luồng camera | Huấn luyện mô hình (thuộc [`ai/`](../ai/)) |
| Theo dõi biển qua nhiều khung, bỏ phiếu nhãn | iOS |
| Cảnh báo giọng nói (vượt tốc độ, biển cấm sắp tới) | Điều khiển phương tiện |
| Bản đồ biển báo, tải trước theo tuyến, dẫn đường | Chế độ xe máy |
| Gửi quan sát (kết quả + ảnh cắt) lên server khi có mạng | Gửi toàn bộ khung hình |
| Nhận và thay mô hình mới qua mạng | |

## Công nghệ

| Hạng mục | Lựa chọn |
| --- | --- |
| Ngôn ngữ | Kotlin |
| Giao diện | Jetpack Compose, Material 3 |
| Camera | CameraX (`ImageAnalysis`) |
| Suy luận AI | TensorFlow Lite (GPU delegate, dự phòng CPU qua XNNPACK) |
| Bản đồ | MapLibre Android với style VietMap |
| Mạng | `HttpURLConnection` + `org.json` của nền tảng |
| Lưu trữ cục bộ | File JSON (biển tải trước, hàng đợi quan sát), SharedPreferences (cài đặt, phiên) |
| Kiến trúc | Module `:core` (Kotlin thuần, logic) + `:app` (Android); DI thủ công trong `SignaApp`; Coroutines |
| Đọc cảnh báo | Android TextToSpeech (tiếng Việt) |
| Định vị | `LocationManager` (GPS) của nền tảng |
| SDK | `minSdk 26`, `targetSdk` mới nhất tại thời điểm khởi tạo |

Lý do chọn nhận diện trên thiết bị: [ADR-0002](../docs/adr/0002-nhan-dien-tren-thiet-bi.md).

## Kiến trúc trong ứng dụng

```mermaid
flowchart TB
    subgraph UI["ui/ (Compose)"]
        DriveScreen
        MapScreen
        SettingsScreen
    end
    subgraph Domain["domain/"]
        WarningEngine["WarningEngine<br/>máy trạng thái cảnh báo"]
        UseCases
    end
    subgraph Data["data/"]
        Repo[Repositories]
        Files[(File JSON)]
        Api[HttpURLConnection]
    end
    subgraph Vision["vision/"]
        Analyzer[FrameAnalyzer<br/>CameraX] --> Detector --> Classifier --> Tracker
    end

    Tracker --> WarningEngine
    WarningEngine --> DriveScreen
    Tracker --> Repo
    Repo --> Files
    Repo --> Api
    MapScreen --> UseCases --> Repo
```

Luồng xử lý một khung hình nằm trọn trong `vision/`, chạy trên luồng nền; UI chỉ nhận `StateFlow` kết quả.

## Cấu trúc thư mục dự kiến

```text
mobile/
├── core/src/main/kotlin/com/signa/core/   # Kotlin thuần: Tracker, WarningEngine, Geo (có test)
├── app/src/main/
│   ├── java/com/signa/app/
│   │   ├── SignaApp.kt          # DI thủ công, vòng gửi quan sát
│   │   ├── Models.kt            # nạp bộ mô hình, Detector, Classifier (TFLite)
│   │   ├── Pipeline.kt          # khung hình → phát hiện → phân loại → theo dõi → cảnh báo
│   │   ├── ObservationQueue.kt  # hàng đợi quan sát, gửi theo lô có Idempotency-Key
│   │   ├── SignStore.kt         # biển tải trước quanh vị trí
│   │   ├── Sensors.kt           # GPS, giọng nói
│   │   └── *Screen.kt           # Compose: lái xe, bản đồ, cài đặt + tài khoản
│   └── assets/models/           # bộ mô hình do signa-ai xuất (không commit)
├── gradle/libs.versions.toml
└── local.properties.example
```

## Màn hình

| Màn hình | Chức năng chính | Yêu cầu |
| --- | --- | --- |
| Lái xe | Camera + khung biển nhận diện, tốc độ hiện tại, giới hạn hiện hành, cảnh báo | FR-01 → FR-05 |
| Bản đồ | Biển báo quanh vị trí, lọc theo nhóm/điểm tin cậy, chi tiết biển | FR-06 → FR-08 |
| Dẫn đường | Tìm địa chỉ, chỉ đường, bật nhận diện tự động khi dẫn đường | FR-09 |
| Tra cứu biển | Chụp ảnh → mã biển, ý nghĩa theo QCVN 41 | FR-10 |
| Đóng góp của tôi | Thống kê quan sát đã gửi, trạng thái | FR-11 |
| Tài khoản / Cài đặt | Đăng nhập, loại xe, bật/tắt giọng nói, chỉ gửi qua Wi-Fi | FR-12, FR-13 |

Mã yêu cầu tham chiếu [Đặc tả yêu cầu](../docs/02-dac-ta-yeu-cau.md).

## Yêu cầu hiệu năng

| Chỉ số | Mục tiêu | Cách đo |
| --- | --- | --- |
| Tốc độ xử lý | ≥ 15 FPS trên máy Android tầm trung | Log thời gian mỗi khung, trung bình 5 phút |
| Độ trễ cảnh báo | < 500 ms từ khi biển ổn định đến khi phát âm | Đo bằng video thực địa |
| Pin | < 15 %/giờ khi chạy liên tục | Android Battery Historian |
| Dữ liệu di động | < 20 MB/giờ | Thống kê mạng của ứng dụng |
| Kích thước mô hình | < 15 MB | Kích thước file `.tflite` |

## Cài đặt và chạy

Yêu cầu: Android Studio bản ổn định mới nhất, JDK 17, thiết bị thật Android 8.0+ (emulator không đủ để đo hiệu năng camera).

```bash
cd mobile
cp local.properties.example local.properties   # điền VIETMAP_API_KEY, API_BASE_URL
./gradlew assembleDebug
./gradlew installDebug
```

## Cấu hình

| Khoá (`local.properties`) | Ý nghĩa |
| --- | --- |
| `VIETMAP_API_KEY` | Khoá VietMap SDK |
| `API_BASE_URL` | Địa chỉ nginx của backend: `http://10.0.2.2:8080` trên emulator, `http://<IP máy tính>:8080` trên điện thoại thật cùng mạng |

## Kiểm thử

| Loại | Phạm vi | Lệnh |
| --- | --- | --- |
| Unit | `Tracker`, `WarningEngine`, `Geo` trong `:core` | `./gradlew :core:test` (máy không có SDK: thêm `-PcoreOnly`) |
| Instrumented | Chạy mô hình trên ảnh mẫu (chưa có) | `./gradlew connectedAndroidTest` |
| Thực địa | Video gắn trên xe, đo FPS / cảnh báo sai | Xem [kế hoạch kiểm thử](../docs/08-kiem-thu.md) |

## Tài liệu liên quan

- [Thiết kế AI — chuỗi xử lý trên thiết bị](../docs/04-thiet-ke-ai.md)
- [Đặc tả API](../docs/07-api.md)
- [ADR-0002: Nhận diện trên thiết bị](../docs/adr/0002-nhan-dien-tren-thiet-bi.md)
