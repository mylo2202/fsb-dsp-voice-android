# Đề tài: Phát hiện các đoạn Voice/unvoice trong đoạn tiếng nói được thu âm dài

Chúng ta đang học khóa Xử lý tín hiệu số (DSP). nhóm chúng ta được giao đề tài "Phát hiện các đoạn Voice/unvoice trong đoạn tiếng nói được thu âm dài". ý tưởng, cách tiếp cận, quy trình thực hiện của đề tài này như sau:

* **Voice (Tiếng có thanh/Hữu thanh):** Được tạo ra khi dây thanh quản rung (ví dụ: các nguyên âm *a, e, i, o, u*, phụ âm hữu thanh *b, d, g*). Tín hiệu có **tính chu kỳ rõ rệt**, năng lượng cao, tập trung ở tần số thấp.
* **Unvoice (Tiếng không thanh/Vô thanh):** Tạo ra khi luồng khí đi qua khe hẹp mà dây thanh quản không rung (ví dụ: các phụ âm gió *s, f, x, th, p, t, k*). Tín hiệu **giống như nhiễu (noise)**, năng lượng thấp, tập trung ở tần số cao.
* **Silence (Khoảng lặng):** Đoạn không có tiếng nói (chỉ có nhiễu nền).

Dưới đây là ý tưởng, cách tiếp cận và quy trình chi tiết để triển khai đồ án này một cách chuyên nghiệp.

---

## 1. Ý tưởng cốt lõi (Core Idea)

Do đặc tính vật lý của cơ quan phát âm, đoạn **Voice** và **Unvoice** có sự khác biệt rất rõ ở cả **miền thời gian (Time Domain)** và **miền tần số (Frequency Domain)**.

Ý tưởng cốt lõi là: **Chia tín hiệu tiếng nói dài thành các khung ngắn (Frames), sau đó trích xuất các đặc trưng toán học/DSP đại diện cho Voice và Unvoice từ mỗi khung, cuối cùng áp dụng ngưỡng (Threshold) hoặc bộ phân loại để phân loại từng khung.**

---

## 2. Các đại lượng đặc trưng (Features) cần trích xuất

Để phân biệt Voice / Unvoice / Silence, ta nên sử dụng kết hợp các đặc trưng DSP sau:

### A. Đặc trưng miền thời gian (Time-Domain Features)

1. **Năng lượng khung ngắn hạn (Short-Time Energy - STE):**
* *Voice:* Năng lượng lớn.
* *Unvoice / Silence:* Năng lượng thấp.


2. **Tốc độ qua điểm 0 (Zero-Crossing Rate - ZCR):**
* *Voice:* Tín hiệu biến thiên chậm, chu kỳ dài $\rightarrow$ ZCR thấp.
* *Unvoice:* Tín hiệu biến thiên nhanh như nhiễu $\rightarrow$ ZCR rất cao.


3. **Hàm tự tương quan (Autocorrelation Function - ACF):**
* *Voice:* Xuất hiện các đỉnh (peaks) lặp lại cực kỳ rõ ràng tương ứng với chu kỳ thanh âm (Pitch Period / $T_0$).
* *Unvoice:* Giảm nhanh về 0, không có đỉnh chu kỳ rõ rệt.



### B. Đặc trưng miền tần số (Frequency-Domain Features)

1. **Tỷ lệ phổ năng lượng (Spectral Energy Distribution):**
* *Voice:* Năng lượng tập trung chủ yếu ở dải tần số thấp (dưới 2 kHz).
* *Unvoice:* Năng lượng phân bố rộng hoặc tập trung ở dải tần cao (trên 2-3 kHz).


2. **Độ bằng phẳng của phổ (Spectral Flatness):**
* *Voice:* Phổ có cấu trúc đỉnh/đáy rõ ràng (tính phẳng thấp).
* *Unvoice:* Phổ phẳng tương tự như nhiễu trắng (Spectral Flatness cao).



---

## 3. Quy trình thực hiện đề tài từng bước (Step-by-Step Workflow)

1. **1. Tiền xử lý tín hiệu (Pre-processing):** Chuẩn hóa dữ liệu đầu vào.
* **Đọc tệp âm thanh:** Nạp tệp `.wav` (khuyên dùng tần số lấy mẫu $F_s = 16\text{ kHz}$ hoặc $8\text{ kHz}$).
* **Chuẩn hóa biên độ (Normalization):** Đưa biên độ về khoảng $[-1, 1]$ để tránh tràn số và giúp việc đặt ngưỡng nhất quán.
* **Lọc loại bỏ DC offset và Nhiễu tần số thấp:** Dùng bộ lọc thông cao (High-pass filter) tần số cắt khoảng $50 - 80\text{ Hz}$.
* **Phân khung (Framing):** Chia tín hiệu thành các khung ngắn $20 - 30\text{ ms}$ (ví dụ $F_s = 16\text{ kHz}$ thì $1\text{ khung} = 320 - 480\text{ mẫu}$). Các khung đè lên nhau (Overlap) $50\%$ để tránh mất mát thông tin biên.
* **Nhân cửa sổ (Windowing):** Nhân mỗi khung với cửa sổ **Hamming** hoặc **Hanning** để giảm hiện tượng rò rỉ phổ (Spectral Leakage).


2. **2. Trích xuất đặc trưng (Feature Extraction):** Tính toán tham số cho từng khung.
Tính toán các giá trị cho từng khung $i$:

* Tính năng lượng $E[i]$ (Short-Time Energy).
* Tính tốc độ qua điểm 0 $ZCR[i]$.
* (Nâng cao) Tính biến độ đỉnh tự tương quan $R_{max}[i]$ hoặc năng lượng phổ tần số cao/thấp.


3. **3. Phát hiện khoảng lặng (VAD - Voice Activity Detection):** Tách riêng vùng Silence.
* Trước khi phân biệt Voice/Unvoice, cần loại bỏ các đoạn Silence (khoảng lặng).
* Sử dụng $E[i]$ của $100 - 200\text{ ms}$ đầu tiên (giả định là nhiễu nền) để ước lượng ngưỡng nhiễu $T_E$.
* Khung nào có $E[i] < T_E$ $\rightarrow$ Gán nhãn **Silence**.


4. **4. Phân loại Voice / Unvoice:** Định nhãn cho các khung còn lại.

* Kết hợp $E[i]$ và $ZCR[i]$:
* Nếu $E[i] > T_{E\_voice}$ VÀ $ZCR[i] < T_{ZCR}$ $\rightarrow$ **Voice**.
* Nếu $E[i]$ thấp hơn chút nhưng $ZCR[i] > T_{ZCR}$ $\rightarrow$ **Unvoice**.

5. **5. Hậu xử lý (Post-processing):** Mịn hóa kết quả.
* Trong tiếng nói thực tế, trạng thái Voice/Unvoice không thay đổi đột ngột trong $10-20\text{ ms}$.
* Áp dụng kỹ thuật **Smooth / Median Filtering**: Nếu một khung duy nhất bị gán là Voice nằm giữa 5 khung Unvoice, hãy sửa nó thành Unvoice (loại bỏ nhiễu phân loại).


6. **6. Biểu diễn kết quả và Đánh giá:** Trực quan hóa đồ thị.
* Vẽ dạng sóng tiếng nói theo thời gian $x(t)$.
* Vẽ đường biểu diễn kết quả phân loại bên dưới (ví dụ: $1 = \text{Voice}$, $0.5 = \text{Unvoice}$, $0 = \text{Silence}$) trùng khớp về trục thời gian.


---

## 4. Gợi ý danh sách nhiệm vụ

| STT | Nhiệm vụ chính | Sản phẩm đầu ra |
| --- | --- | --- |
| **1** | Chuẩn bị tệp âm thanh thu âm, viết code Tiền xử lý (Đọc file, Framing, Windowing, Lọc DC). | Hàm `framing()` và `windowing()` |
| **2** | Viết code trích xuất đặc trưng ($E$, $ZCR$, $ACF$). | Hàm `extract_features()` trả về các mảng $E$, $ZCR$ |
| **3** | Xây dựng thuật toán quyết định ngưỡng (Thresholding) & Hậu xử lý. | Hàm `classify_voice_unvoice()` |
| **4** | Thiết kế Giao diện (GUI) hoặc Trực quan hóa đồ thị. | Đồ thị kết quả đẹp mắt |



---