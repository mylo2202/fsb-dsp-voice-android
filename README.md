# 🎙️ DSP Voice / Unvoice / Silence Detector (Android App)

Ứng dụng Android thực hiện đề tài môn **Xử lý tín hiệu số (DSP)**: **"Phát hiện các đoạn Voice/unvoice trong đoạn tiếng nói được thu âm dài"**.

Ứng dụng kết hợp giữa **Kotlin Native Android UI** và **Thuật toán DSP bằng Python** tích hợp trực tiếp qua **Chaquopy Bridge**, cho phép phân tích tín hiệu tiếng nói theo thời gian thực với giao diện Dark Mode hiện đại và trực quan.

---

## 🌟 Các Tính Năng Nổi Bật

1. **Nguồn âm thanh linh hoạt**:
   - 🎙️ **Thu âm trực tiếp từ Micro**: Thu âm chuẩn 16-bit PCM 16kHz Mono WAV, hiển thị biên độ thu âm thời gian thực.
   - 📁 **Mở file `.wav` từ máy**: Cho phép chọn các tệp âm thanh WAV có sẵn trong bộ nhớ thiết bị.
   - ⚡ **Tín hiệu Demo giả lập**: Sinh tín hiệu tiếng nói tổng hợp (Voice 120Hz/150Hz/100Hz, Unvoice nhiễu trắng, Silence) để chạy thử nghiệm ngay lập tức.

2. **Trích xuất đặc trưng DSP & Thuật toán Phân loại**:
   - **Short-Time Energy (STE)**: Năng lượng khung ngắn hạn.
   - **Zero-Crossing Rate (ZCR)**: Tốc độ qua điểm 0.
   - **Autocorrelation Peak (ACF $R_{max}$)**: Đỉnh hàm tự tương quan chuẩn hóa (Pitch period).
   - **Spectral Flatness & Centroid**: Độ phẳng phổ và trung tâm phổ tần số.
   - **Bộ ngưỡng thích ứng (Adaptive Thresholds)**: Tự động ước lượng nhiễu nền để tính toán bộ ngưỡng $T_E, T_{ZCR}, T_R, T_{SF}, T_C, T_P$.
   - **Hậu xử lý Median Filter**: Làm mượt nhãn khung, loại bỏ nhiễu phân loại cục bộ.

3. **Giao diện Trực quan hóa Đồ thị Chuyên nghiệp**:
   - 📊 **Waveform & Classification Overlay**: Đồ thị dạng sóng hiển thị dải màu tương ứng với từng trạng thái:
     - 🟢 **Voice (Hữu thanh)**: Tiếng có thanh (dây thanh rung).
     - 🟠 **Unvoice (Vô thanh)**: Tiếng không thanh (phụ âm gió, nhiễu).
     - ⚪ **Silence (Khoảng lặng)**: Khoảng không phát âm / Nhiễu nền.
   - 📈 **Đồ thị Đặc trưng DSP & Đường ngưỡng**: Trực quan hóa đồng bộ STE ($E[i]$ + $T_E$), ZCR ($ZCR[i]$ + $T_{ZCR}$), ACF ($R_{max}[i]$ + $T_R$).
   - ⏯️ **Trình phát Audio & Playhead Cursor**: Đồng bộ con trỏ thời gian khi phát lại âm thanh, hỗ trợ vuốt/chạm đồ thị để tua nhanh đến thời điểm bất kỳ.
   - 📋 **Bảng thống kê tỷ lệ % & Danh sách phân đoạn**: Thống kê % Voice/Unvoice/Silence và danh sách các đoạn phân chia kèm nút nghe thử từng đoạn.

---

## 💻 Yêu Cầu Môi Trường (Prerequisites)

- **Android Studio**: Android Studio Koala / Ladybug / Jellyfish (hoặc bản mới hơn).
- **JDK**: Java Development Kit 17 hoặc Java 21.
- **Android SDK**: API Level 34 / 35 (Min SDK 24 - Android 7.0+).
- **Python**: Python 3.8 trở lên đã cài đặt trên máy phát triển (dùng cho Chaquopy build requirements).

---

## 🚀 Hướng Dẫn Chạy Ứng Dụng (Run App)

### Cách 1: Sử dụng Android Studio (IDE)

1. **Mở dự án**:
   - Mở Android Studio $\rightarrow$ chọn **Open** $\rightarrow$ trỏ tới thư mục `DSPVoiceUnvoice`.
2. **Đồng bộ Gradle**:
   - Chờ Android Studio tự động Sync các file Gradle và tải package Python (`numpy`, `scipy`).
3. **Chạy ứng dụng**:
   - Kết nối điện thoại Android thật (đã bật *USB Debugging*) hoặc mở máy ảo (*Android Emulator*).
   - Nhấn nút **Run 'app'** (`Shift + F10`) hoặc bấm biểu tượng tam giác xanh ▶️ trên thanh công cụ.

### Cách 2: Sử dụng Dòng Lệnh (Terminal / Command Line)

1. Kết nối thiết bị Android hoặc khởi chạy máy ảo.
2. Mở Terminal / PowerShell tại thư mục gốc của dự án.
3. Chạy lệnh cài đặt trực tiếp lên thiết bị:

   - **Windows (PowerShell / CMD)**:
     ```powershell
     .\gradlew.bat installDebug
     ```
   - **Linux / macOS**:
     ```bash
     chmod +x gradlew
     ./gradlew installDebug
     ```

---

## 📦 Hướng Dẫn Đóng Gói Thành Phẩm (Build APK / AAB)

### 1. Đóng gói File APK (Debug APK / Release APK)

#### Cách A: Sử dụng Dòng Lệnh (CLI - Khuyên dùng)

- **Tạo Debug APK**:
  - **Windows**:
    ```powershell
    .\gradlew.bat assembleDebug
    ```
  - **Linux / macOS**:
    ```bash
    ./gradlew assembleDebug
    ```
  - **Vị trí file APK sau khi đóng gói**:
    `app/build/outputs/apk/debug/app-debug.apk`

- **Tạo Release APK**:
  - **Windows**:
    ```powershell
    .\gradlew.bat assembleRelease
    ```
  - **Linux / macOS**:
    ```bash
    ./gradlew assembleRelease
    ```
  - **Vị trí file APK sau khi đóng gói**:
    `app/build/outputs/apk/release/app-release.apk`

#### Cách B: Sử dụng Android Studio IDE

1. Trên thanh Menu chọn `Build` $\rightarrow$ `Build Bundle(s) / APK(s)` $\rightarrow$ **`Build APK(s)`**.
2. Sau khi quá trình đóng gói hoàn tất, popup thông báo sẽ xuất hiện ở góc dưới bên phải.
3. Nhấn vào chữ **`locate`** để mở thư mục chứa file `app-debug.apk`.

---

### 2. Đóng gói Android App Bundle (.aab) xuất bản lên Google Play

- **Dùng dòng lệnh**:
  ```powershell
  .\gradlew.bat bundleRelease
  ```
  File thành phẩm nằm tại: `app/build/outputs/bundle/release/app-release.aab`

- **Dùng Android Studio**:
  Vào `Build` $\rightarrow$ `Generate Signed Bundle / APK...` $\rightarrow$ chọn `Android App Bundle` và làm theo các bước hướng dẫn.

---

## 📂 Cấu Trúc Thư Mục Dự Án

```
DSPVoiceUnvoice/
├── ANDROID.md                      # Hướng dẫn tích hợp Chaquopy
├── feature-requirements.md         # Yêu cầu đề tài & Lý thuyết DSP
├── pyproject.toml                  # Metadata cấu hình package Python
├── build.gradle                    # Gradle config cấp Root
├── gradle.properties               # Cấu hình thuộc tính AndroidX & JVM
├── settings.gradle                 # Khai báo Modules & Repositories
└── app/
    ├── build.gradle                # Gradle config module app & Chaquopy
    └── src/main/
        ├── AndroidManifest.xml     # Khai báo Quyền Microphone & Storage
        ├── python/                 # Mã nguồn thuật toán DSP Python
        │   ├── pyproject.toml
        │   └── main/
        │       ├── __init__.py     # Wrapper serialize dữ liệu cho Chaquopy
        │       └── main.py         # Thuật toán DSP core (STE, ZCR, ACF, Thresholds)
        ├── java/com/example/dspvoiceunvoice/
        │   ├── audio/              # Quản lý thu âm, giải mã WAV, phát lại audio
        │   │   ├── AudioRecordManager.kt
        │   │   ├── AudioPlayerManager.kt
        │   │   └── WavDecoder.kt
        │   ├── dsp/                # Engine giao tiếp Chaquopy Python & Data Models
        │   │   ├── PythonDspEngine.kt
        │   │   └── AnalysisResult.kt
        │   ├── ui/                 # Custom Views trực quan hóa đồ thị & Adapters
        │   │   ├── WaveformClassifierView.kt
        │   │   ├── DspFeatureChartView.kt
        │   │   └── SegmentAdapter.kt
        │   └── MainActivity.kt     # Controller điều khiển chính của App
        └── res/                    # Giao diện Layout, Icons & Color themes
            ├── layout/activity_main.xml
            └── layout/item_segment.xml
```
