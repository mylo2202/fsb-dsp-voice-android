"""
DSP Module: Phát hiện Voice / Unvoice / Silence trong tín hiệu tiếng nói
  1. Tiền xử lý (chuẩn hóa, lọc DC)
  2. Phân khung + Hamming window
  3. Trích xuất đặc trưng: STE, ZCR, ACF R_max, Spectral Flatness
  4. Phân loại ngưỡng thích ứng => 0 (Silence) / 1 (Unvoice) / 2 (Voice)
  5. Hậu xử lý: Median filter làm mượt nhãn
"""

import numpy as np
from scipy.signal import butter, lfilter


# 1. TIỀN XỬ LÝ (PREPROCESSING)
def preprocess(signal: np.ndarray, sample_rate: int) -> np.ndarray:
    """
    Tiền xử lý tín hiệu:
      - Chuyển về float64
      - Chuẩn hóa biên độ về [-1, 1]
      - Lọc thông cao (High-pass, fc ≈ 80 Hz) để loại DC offset và nhiễu tần thấp

    Tham số:
        signal      : Mảng tín hiệu âm thanh 1 chiều
        sample_rate : Tần số lấy mẫu (Hz)

    Trả về:
        Mảng tín hiệu đã tiền xử lý (float64)
    """
    sig = signal.astype(np.float64)

    # Chuẩn hóa biên độ
    max_val = np.max(np.abs(sig))
    if max_val > 0:
        sig = sig / max_val

    # Thiết kế bộ lọc thông cao Butterworth bậc 4, fc = 80 Hz
    nyquist = sample_rate / 2.0
    fc = 80.0 / nyquist  # Tần số cắt chuẩn hóa
    b, a = butter(4, fc, btype='high')
    sig = lfilter(b, a, sig)

    return sig


# 2. PHÂN KHUNG VÀ HAMMING WINDOW (FRAMING + WINDOWING)
def create_frames(
    signal: np.ndarray,
    sample_rate: int,
    frame_size_ms: float = 25.0,
    frame_stride_ms: float = 10.0
):
    """
    Chia tín hiệu thành các khung ngắn có chồng lấp và nhân cửa sổ Hamming.

    Tham số:
        signal         : Tín hiệu 1 chiều (float)
        sample_rate    : Tần số lấy mẫu
        frame_size_ms  : Độ dài khung (ms), mặc định 25 ms
        frame_stride_ms: Bước dịch khung (ms), mặc định 10 ms (overlap 60%)

    Trả về:
        frames       : Ma trận (num_frames × frame_length) đã nhân Hamming
        frame_length : Số mẫu mỗi khung
        frame_step   : Số mẫu mỗi bước dịch
    """
    frame_length = int(round(frame_size_ms * 1e-3 * sample_rate))
    frame_step   = int(round(frame_stride_ms * 1e-3 * sample_rate))
    signal_length = len(signal)

    # Số khung cần thiết
    num_frames = int(np.ceil(float(max(signal_length - frame_length, 0)) / frame_step)) + 1

    # Padding bằng 0 để khung cuối đầy đủ
    pad_length = (num_frames - 1) * frame_step + frame_length
    pad_signal = np.append(signal, np.zeros(max(pad_length - signal_length, 0)))

    # Tạo ma trận chỉ số rồi index vào mảng
    row_idx = np.tile(np.arange(frame_length), (num_frames, 1))
    col_idx = np.tile(np.arange(num_frames) * frame_step, (frame_length, 1)).T
    indices = (row_idx + col_idx).astype(np.int32)

    frames = pad_signal[indices]

    # Nhân cửa sổ Hamming — giảm rò rỉ phổ (Spectral Leakage)
    frames *= np.hamming(frame_length)

    return frames, frame_length, frame_step


# 3. TRÍCH XUẤT ĐẶC TRƯNG (FEATURE EXTRACTION)
def compute_ste(frames: np.ndarray) -> np.ndarray:
    """
    Short-Time Energy (STE) — Năng lượng ngắn hạn.
      E[i] = Sigma(x[n]^2)

    Voice   → E lớn (dây thanh quản rung mạnh)
    Unvoice → E vừa phải
    Silence → E ≈ 0
    """
    return np.sum(frames ** 2, axis=1)


def compute_zcr(frames: np.ndarray) -> np.ndarray:
    """
    Zero-Crossing Rate (ZCR) — Tốc độ qua điểm 0.
      ZCR[i] = Σ|sign(x[n]) − sign(x[n−1])| / (2N)

    Voice   → ZCR thấp (dao động chậm, có chu kỳ)
    Unvoice → ZCR cao  (dao động nhanh như nhiễu)
    """
    signs = np.sign(frames)
    signs[signs == 0] = 1  # tránh 0 gây lỗi khi tính hiệu

    sign_changes = np.abs(np.diff(signs, axis=1))
    zcr = np.sum(sign_changes, axis=1) / (2.0 * frames.shape[1])
    return zcr


def compute_acf_max(
    frames: np.ndarray,
    sample_rate: int,
    f0_min: float = 50.0,
    f0_max: float = 400.0
) -> np.ndarray:
    """
    Đỉnh hàm tự tương quan chuẩn hóa (R_max) — Mức độ có tính chu kỳ.

    Tính autocorrelation bằng FFT (nhanh hơn tính trực tiếp O(N^2)):
      R = IFFT(|FFT(x)|^2)
    Sau đó tìm đỉnh max trong dải lag tương ứng với f0 ∈ [f0_min, f0_max].

    Voice   → R_max cao (đỉnh rõ, chu kỳ pitch ổn định)
    Unvoice → R_max thấp (không có tính chu kỳ)
    """
    num_frames, frame_length = frames.shape
    r_max = np.zeros(num_frames)

    # Quy đổi tần số Pitch sang khoảng lag (số mẫu)
    k_min = max(int(sample_rate / f0_max), 1)
    k_max = min(int(sample_rate / f0_min), frame_length - 1)

    if k_max <= k_min:
        return r_max  # Không đủ dải → trả về 0

    # Kích thước FFT tối thiểu để tránh circular aliasing
    n_fft = int(2 ** np.ceil(np.log2(2 * frame_length - 1)))

    for i in range(num_frames):
        frame = frames[i]
        energy = np.sum(frame ** 2)
        if energy < 1e-10:
            continue  # Khung lặng, bỏ qua

        fft_frame = np.fft.rfft(frame, n=n_fft)
        power = np.abs(fft_frame) ** 2
        autocorr = np.fft.irfft(power)[:frame_length]

        # Chuẩn hóa: R[0] = 1
        autocorr_norm = autocorr / (autocorr[0] + 1e-10)
        r_max[i] = np.max(autocorr_norm[k_min:k_max])

    return r_max


def compute_spectral_flatness(frames: np.ndarray) -> np.ndarray:
    """
    Spectral Flatness (Wiener Entropy) — Độ phẳng phổ.
      SF = geometric_mean(|X[k]|^2) / arithmetic_mean(|X[k]|^2)

    Voice   → SF thấp  (phổ có đỉnh rõ tại harmonics)
    Unvoice → SF cao   (phổ phẳng giống nhiễu trắng)
    """
    n_fft = frames.shape[1]
    power_spectrum = np.abs(np.fft.rfft(frames, n=n_fft)) ** 2 + 1e-12  # tránh log(0)

    log_mean = np.mean(np.log(power_spectrum), axis=1)
    arith_mean = np.mean(power_spectrum, axis=1)

    # SF = exp(log-mean) / arith-mean  (tương đương geometric/arithmetic mean)
    sf = np.exp(log_mean) / (arith_mean + 1e-12)
    return sf


def compute_spectral_centroid(frames: np.ndarray, sample_rate: int) -> np.ndarray:
    """
    Spectral Centroid — trung tâm tần số của phổ từng khung.
    Voice   → thường tập trung ở dải tần thấp hơn so với âm nhạc phức tạp.
    Unvoice → phổ dịch về dải tần cao/đặc trưng riêng hơn.
    """
    n_fft = frames.shape[1]
    spectrum = np.abs(np.fft.rfft(frames, n=n_fft))
    freqs = np.fft.rfftfreq(n_fft, d=1.0 / sample_rate)
    magnitude = spectrum + 1e-12

    centroid = np.sum(freqs[None, :] * magnitude, axis=1) / np.sum(magnitude, axis=1)
    return centroid


def compute_spectral_peakiness(frames: np.ndarray) -> np.ndarray:
    """
    Spectral Peakiness — mức độ phổ bị tập trung vào một vài bin.
    Pure-tone/music-like signals có peakiness rất cao.
    Speech voiced thường có peakiness thấp hơn vì phổ có nhiều harmonic/formant.
    """
    n_fft = frames.shape[1]
    power_spectrum = np.abs(np.fft.rfft(frames, n=n_fft)) ** 2 + 1e-12
    peak_energy = np.max(power_spectrum, axis=1)
    mean_energy = np.mean(power_spectrum, axis=1)
    return peak_energy / mean_energy


# 4. PHÂN LOẠI VOICE/UNVOICE CLASSIFICATION
def classify_frames(
    ste: np.ndarray,
    zcr: np.ndarray,
    r_max: np.ndarray,
    spectral_flatness: np.ndarray,
    spectral_centroid: np.ndarray,
    spectral_peakiness: np.ndarray,
    noise_frame_count: int = 20
):
    """
    Phân loại từng khung thành Silence (0), Unvoice (1), Voice (2).

    Thuật toán: Dual-Threshold thích ứng dựa trên STE + ZCR + ACF R_max.

    Bước 1 — Ước lượng nhiễu nền từ `noise_frame_count` khung đầu tiên
             (giả định các khung này là nhiễu nền / khoảng lặng trước khi nói).

    Bước 2 — Xác định ngưỡng thích ứng:
             T_E      : ngưỡng phân tách Silence vs. (Voice/Unvoice)
             T_ZCR    : ngưỡng phân tách Voice (ZCR thấp) vs. Unvoice (ZCR cao)
             T_R      : ngưỡng xác nhận tính chu kỳ (ACF R_max)
             T_SF     : ngưỡng phân tách phổ có cấu trúc (Voice) vs. phổ phẳng như nhiễu (Unvoice)
             T_C      : ngưỡng centroid phổ để giữ tính “speech-like” cho Voice
             T_P      : ngưỡng peakiness để loại các sine-tone/music-like bị tập trung quá mạnh

    Bước 3 — Phân loại sơ bộ:
             Silence  ← STE < T_E
             Voice    ← STE ≥ T_E VÀ có tính chu kỳ rõ (R_max ≥ T_R VÀ ZCR < T_ZCR)
                         VÀ phổ có đặc trưng speech-like (SF thấp, centroid thấp, peakiness vừa phải)
             Unvoice  ← còn lại

    Bước 4 — Làm mượt nhãn bằng Median Filter (loại nhiễu phân loại đơn lẻ).

    Trả về:
        labels  : Mảng nhãn đã làm mượt
        T_E     : Ngưỡng năng lượng
        T_ZCR   : Ngưỡng ZCR
        T_R     : Ngưỡng ACF
        T_SF    : Ngưỡng Spectral Flatness
        T_C     : Ngưỡng Spectral Centroid
        T_P     : Ngưỡng Spectral Peakiness
    """
    n = noise_frame_count

    # Bước 1: Ước lượng nhiễu nền
    noise_ste_mean = np.mean(ste[:n])
    noise_ste_std  = np.std(ste[:n])

    noise_zcr_mean = np.mean(zcr[:n])
    noise_zcr_std  = np.std(zcr[:n])
    noise_sf_mean  = np.mean(spectral_flatness[:n])
    noise_sf_std   = np.std(spectral_flatness[:n])

    # Bước 2: Ngưỡng thích ứng
    # T_E: cao hơn mức nhiễu 3 sigma, có sàn tối thiểu
    T_E   = noise_ste_mean + 3.0 * noise_ste_std + 1e-4

    # T_ZCR: ưu tiên ước lượng từ nhiễu, có sàn 0.18
    T_ZCR = max(noise_zcr_mean + 2.0 * noise_zcr_std, 0.18)

    # T_R: ngưỡng cố định cho ACF (0.35 là giá trị điển hình cho giọng nói)
    T_R   = 0.35

    # T_SF: phổ phẳng như nhiễu thường có giá trị lớn hơn ngưỡng này
    T_SF  = max(noise_sf_mean + 2.0 * noise_sf_std, 0.25)

    # T_C: giữ Voice ở dải tần thấp hơn, giúp loại âm nhạc/phức hợp tần số cao
    T_C   = 2500.0

    # T_P: pure-tone/music-like có peakiness rất cao, speech voiced thì thấp hơn
    T_P   = 70.0

    # Bước 3: Phân loại sơ bộ
    labels = np.zeros(len(ste), dtype=int)  # mặc định: Silence

    for i in range(len(ste)):
        if ste[i] < T_E:
            labels[i] = 0  # Silence
        else:
            is_periodic = (r_max[i] >= T_R) and (zcr[i] < T_ZCR)
            is_noise_like = (zcr[i] >= T_ZCR * 1.15) or (spectral_flatness[i] >= T_SF)
            is_speech_like = (
                is_periodic
                and not is_noise_like
                and (spectral_centroid[i] < T_C)
                and (spectral_peakiness[i] < T_P)
            )

            if is_speech_like:
                labels[i] = 2  # Voice
            else:
                labels[i] = 1  # Unvoice (bao gồm tiếng vỗ tay, nhiễu, âm nhạc và âm vô thanh)

    # Bước 4: Làm mượt với Median Filter tự cài đặt
    smoothed_labels = median_smoothing(labels, size=7)

    return smoothed_labels, T_E, T_ZCR, T_R, T_SF, T_C, T_P


def median_smoothing(labels: np.ndarray, size: int = 7) -> np.ndarray:
    """Smooth labels with a median filter using NumPy only."""
    pad = size // 2
    padded = np.pad(labels, pad, mode="edge")
    smoothed = np.empty_like(labels)
    for i in range(len(labels)):
        smoothed[i] = int(np.median(padded[i : i + size]))
    return smoothed


# 5. PIPELINE HOÀN CHỈNH
def run_analysis(signal: np.ndarray, sample_rate: int, noise_frame_count: int = 20) -> dict:
    """
    Chạy toàn bộ pipeline phân tích từ đầu đến cuối.

    Trả về dict chứa:
        signal_preprocessed : Tín hiệu sau tiền xử lý
        frames              : Ma trận khung
        frame_length        : Số mẫu mỗi khung
        frame_step          : Số mẫu mỗi bước dịch
        frame_times         : Mốc thời gian giữa mỗi khung (giây)
        time_axis           : Trục thời gian của tín hiệu gốc (giây)
        ste                 : Short-Time Energy
        zcr                 : Zero-Crossing Rate
        r_max               : ACF R_max
        spectral_flatness   : Spectral Flatness
        labels              : Nhãn phân loại đã làm mượt (0/1/2)
        T_E                 : Ngưỡng năng lượng
        T_ZCR               : Ngưỡng ZCR
        T_R                 : Ngưỡng ACF
    """
    # 1. Tiền xử lý
    sig = preprocess(signal, sample_rate)

    # 2. Phân khung 25ms / stride 10ms
    frames, frame_length, frame_step = create_frames(sig, sample_rate,
                                                      frame_size_ms=25.0,
                                                      frame_stride_ms=10.0)

    # 3. Trích xuất đặc trưng
    ste = compute_ste(frames)
    zcr = compute_zcr(frames)
    r_max = compute_acf_max(frames, sample_rate)
    sf  = compute_spectral_flatness(frames)
    spectral_centroid = compute_spectral_centroid(frames, sample_rate)
    spectral_peakiness = compute_spectral_peakiness(frames)

    # 4. Phân loại
    labels, T_E, T_ZCR, T_R, T_SF, T_C, T_P = classify_frames(
        ste, zcr, r_max, sf, spectral_centroid, spectral_peakiness,
        noise_frame_count=noise_frame_count
    )

    # 5. Trục thời gian
    num_frames = len(frames)
    frame_times = (np.arange(num_frames) * frame_step + frame_length / 2.0) / sample_rate
    time_axis = np.arange(len(sig)) / sample_rate

    return {
        "signal_preprocessed": sig,
        "frames": frames,
        "frame_length": frame_length,
        "frame_step": frame_step,
        "frame_times": frame_times,
        "time_axis": time_axis,
        "ste": ste,
        "zcr": zcr,
        "r_max": r_max,
        "spectral_flatness": sf,
        "labels": labels,
        "T_E": T_E,
        "T_ZCR": T_ZCR,
        "T_R": T_R,
        "T_SF": T_SF,
        "T_C": T_C,
        "T_P": T_P,
    }


# 6. TÍN HIỆU GIẢ LẬP ĐỂ DEMO
def generate_test_signal(sample_rate: int = 16000) -> np.ndarray:
    """
    Tạo tín hiệu tiếng nói giả lập để demo / kiểm thử khi không có file WAV.

    Cấu trúc (tổng 3 giây):
        0.0 - 0.3s : Silence (nhiễu nền cực nhỏ)
        0.3 - 1.1s : Voice   (sine 120 Hz — nguyên âm)
        1.1 - 1.5s : Unvoice (nhiễu trắng biên độ thấp — phụ âm gió 's')
        1.5 - 1.8s : Voice   (sine 150 Hz — nguyên âm khác)
        1.8 - 2.2s : Unvoice (nhiễu trắng)
        2.2 - 2.5s : Voice   (sine 100 Hz)
        2.5 - 3.0s : Silence
    """
    rng = np.random.default_rng(seed=42)
    total = 3.0  # giây
    t = np.linspace(0, total, int(sample_rate * total), endpoint=False)

    def seg(start, end):
        """Lấy slice mẫu theo khoảng thời gian."""
        return slice(round(start * sample_rate), round(end * sample_rate))

    sig = np.zeros(len(t))

    # Silence (nhiễu nền nhỏ ~-40 dB)
    s = seg(0.0, 0.3);  sig[s] = rng.normal(0, 0.003, len(sig[s]))
    s = seg(2.5, 3.0);  sig[s] = rng.normal(0, 0.003, len(sig[s]))

    # Voice (sine wave + nhỏ nhiễu)
    # Dùng len(sig[s]) để đảm bảo n luôn khớp với kích thước slice thực tế
    # (tránh lỗi floating-point: int(0.8 * 16000) có thể trả về 12799 thay vì 12800)
    for start, end, f0 in [(0.3, 1.1, 120), (1.5, 1.8, 150), (2.2, 2.5, 100)]:
        s = seg(start, end)
        n = len(sig[s])
        t_seg = np.linspace(0, end - start, n, endpoint=False)
        sig[s] = 0.8 * np.sin(2 * np.pi * f0 * t_seg) + rng.normal(0, 0.02, n)

    # Unvoice (nhiễu trắng biên độ vừa)
    for start, end in [(1.1, 1.5), (1.8, 2.2)]:
        s = seg(start, end)
        sig[s] = rng.normal(0, 0.18, len(sig[s]))

    return sig


# DEMO TERMINAL
if __name__ == "__main__":
    import matplotlib.pyplot as plt
    import scipy.io.wavfile as wav
    import os

    print("=" * 60)
    print("DSP Demo: Voice / Unvoice / Silence Detection")
    print("=" * 60)

    # Nạp tín hiệu
    wav_path = "speech.wav"
    if os.path.isfile(wav_path):
        print(f"Đọc file: {wav_path}")
        sr, raw = wav.read(wav_path)
        raw = raw.astype(np.float64)
        if raw.ndim > 1:
            raw = raw[:, 0]  # Stereo → Mono
    else:
        print("Không tìm thấy 'speech.wav'. Dùng tín hiệu giả lập...")
        sr = 16000
        raw = generate_test_signal(sr)

    print(f"Sample rate : {sr} Hz")
    print(f"Độ dài      : {len(raw)/sr:.2f} giây ({len(raw)} mẫu)")

    # Chạy pipeline
    print("\nĐang phân tích...")
    res = run_analysis(raw, sr)

    sig    = res["signal_preprocessed"]
    t_sig  = res["time_axis"]
    t_fr   = res["frame_times"]
    ste    = res["ste"]
    zcr    = res["zcr"]
    r_max  = res["r_max"]
    sf     = res["spectral_flatness"]
    labels = res["labels"]
    T_E    = res["T_E"]
    T_ZCR  = res["T_ZCR"]
    T_R    = res["T_R"]

    # Thống kê
    total_frames = len(labels)
    p_voice   = np.sum(labels == 2) / total_frames * 100
    p_unvoice = np.sum(labels == 1) / total_frames * 100
    p_silence = np.sum(labels == 0) / total_frames * 100
    print(f"\n Kết quả phân loại ({total_frames} khung):")
    print(f"   Voice   : {p_voice:.1f}%")
    print(f"   Unvoice : {p_unvoice:.1f}%")
    print(f"   Silence : {p_silence:.1f}%")
    print("\n Ngưỡng thích ứng:")
    print(f"   T_E   = {T_E:.6f}")
    print(f"   T_ZCR = {T_ZCR:.4f}")
    print(f"   T_R   = {T_R:.2f}")
    print(f"   T_SF  = {res['T_SF']:.4f}")
    print(f"   T_C   = {res['T_C']:.1f} Hz")
    print(f"   T_P   = {res['T_P']:.1f}")

    # Đồ thị
    fig, axs = plt.subplots(5, 1, figsize=(13, 11), sharex=True)
    fig.suptitle("Phân tích Voice / Unvoice / Silence", fontsize=14, fontweight='bold')

    LABEL_COLORS = {2: ('green', 0.25), 1: ('orange', 0.25), 0: ('gray', 0.15)}

    # Hàm tô màu vùng phân loại lên một axes
    def shade_regions(ax, t_fr, labels):
        for lbl, (color, alpha) in LABEL_COLORS.items():
            ax.fill_between(t_fr, ax.get_ylim()[0], ax.get_ylim()[1],
                            where=(labels == lbl), color=color, alpha=alpha)

    # 1) Waveform
    axs[0].plot(t_sig, sig, color='#2563EB', linewidth=0.6, alpha=0.85)
    axs[0].set_title("1. Tín hiệu tiếng nói (Waveform)")
    axs[0].set_ylabel("Biên độ")
    axs[0].grid(True, alpha=0.4)

    # 2) STE
    axs[1].plot(t_fr, ste, color='#DC2626', linewidth=1.2, label='STE')
    axs[1].axhline(T_E, color='black', linestyle='--', linewidth=1.0,
                   label=f'T_E = {T_E:.5f}')
    axs[1].set_title("2. Short-Time Energy (STE) + Ngưỡng")
    axs[1].set_ylabel("Năng lượng")
    axs[1].legend(loc='upper right', fontsize=8)
    axs[1].grid(True, alpha=0.4)

    # 3) ZCR
    axs[2].plot(t_fr, zcr, color='#16A34A', linewidth=1.2, label='ZCR')
    axs[2].axhline(T_ZCR, color='black', linestyle='--', linewidth=1.0,
                   label=f'T_ZCR = {T_ZCR:.3f}')
    axs[2].set_title("3. Zero-Crossing Rate (ZCR) + Ngưỡng")
    axs[2].set_ylabel("Tỷ lệ ZCR")
    axs[2].legend(loc='upper right', fontsize=8)
    axs[2].grid(True, alpha=0.4)

    # 4) ACF R_max
    axs[3].plot(t_fr, r_max, color='#7C3AED', linewidth=1.2, label='ACF R_max')
    axs[3].axhline(T_R, color='black', linestyle='--', linewidth=1.0,
                   label=f'T_R = {T_R:.2f}')
    axs[3].set_title("4. Đỉnh Tự Tương Quan Chuẩn Hóa (ACF R_max) + Ngưỡng")
    axs[3].set_ylabel("R_max")
    axs[3].set_ylim(-0.1, 1.05)
    axs[3].legend(loc='upper right', fontsize=8)
    axs[3].grid(True, alpha=0.4)

    # 5) Kết quả phân loại
    label_map = {0: 'Silence', 1: 'Unvoice', 2: 'Voice'}
    color_map  = {0: 'gray',   1: 'orange',  2: 'green'}
    for lbl, name in label_map.items():
        axs[4].fill_between(t_fr, 0, labels,
                            where=(labels == lbl),
                            color=color_map[lbl], alpha=0.5, label=name)
    axs[4].step(t_fr, labels, where='mid', color='black', linewidth=1.0)
    axs[4].set_title("5. Kết quả phân loại (0=Silence | 1=Unvoice | 2=Voice)")
    axs[4].set_yticks([0, 1, 2])
    axs[4].set_yticklabels(['Silence', 'Unvoice', 'Voice'])
    axs[4].set_xlabel("Thời gian (giây)")
    axs[4].legend(loc='upper right', fontsize=8)
    axs[4].grid(True, alpha=0.4)

    plt.tight_layout()
    plt.savefig("analysis_result.png", dpi=150, bbox_inches='tight')
    print("\n Đồ thị đã lưu thành 'analysis_result.png'")
    plt.show()
