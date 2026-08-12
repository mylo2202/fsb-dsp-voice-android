"""Package entrypoint for fsb-dsp-voice DSP code.

This module exposes an Android-friendly wrapper compatible with Chaquopy.
"""

from __future__ import annotations

import numpy as np

from .main import generate_test_signal, run_analysis

__all__ = [
    "run_analysis",
    "generate_test_signal",
    "analyze_pcm16",
    "analyze_float",
]


def _to_python_type(value):
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, dict):
        return {k: _to_python_type(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_to_python_type(v) for v in value]
    return value


def _serialize_result(result: dict) -> dict:
    labels = result["labels"].tolist()
    voice_count = int(np.count_nonzero(result["labels"] == 2))
    unvoice_count = int(np.count_nonzero(result["labels"] == 1))
    silence_count = int(np.count_nonzero(result["labels"] == 0))

    serialized = {
        "sample_rate": int(result.get("sample_rate", 0)),
        "frame_length": int(result["frame_length"]),
        "frame_step": int(result["frame_step"]),
        "total_frames": len(labels),
        "frame_times": _to_python_type(result["frame_times"]),
        "time_axis": _to_python_type(result["time_axis"]),
        "labels": labels,
        "label_counts": {
            "voice": voice_count,
            "unvoice": unvoice_count,
            "silence": silence_count,
        },
        "ste": _to_python_type(result["ste"]),
        "zcr": _to_python_type(result["zcr"]),
        "r_max": _to_python_type(result["r_max"]),
        "spectral_flatness": _to_python_type(result["spectral_flatness"]),
        "T_E": float(result["T_E"]),
        "T_ZCR": float(result["T_ZCR"]),
        "T_R": float(result["T_R"]),
        "T_SF": float(result["T_SF"]),
        "T_C": float(result["T_C"]),
        "T_P": float(result["T_P"]),
    }
    return serialized


def analyze_float(signal, sample_rate: int = 16000, noise_frame_count: int = 20) -> dict:
    """Analyze float waveform samples in the range [-1.0, 1.0]."""
    signal_array = np.asarray(signal, dtype=np.float64)
    result = run_analysis(signal_array, sample_rate, noise_frame_count=noise_frame_count)
    result["sample_rate"] = sample_rate
    return _serialize_result(result)


def analyze_pcm16(pcm16, sample_rate: int = 16000, noise_frame_count: int = 20) -> dict:
    """Analyze 16-bit PCM audio data passed from Android."""
    pcm16_array = np.asarray(pcm16, dtype=np.int16)
    float_signal = pcm16_array.astype(np.float64) / 32768.0
    result = run_analysis(float_signal, sample_rate, noise_frame_count=noise_frame_count)
    result["sample_rate"] = sample_rate
    return _serialize_result(result)
