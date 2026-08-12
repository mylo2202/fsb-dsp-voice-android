# Android Integration Guide

This repository includes a Python DSP package that can be called from a native Android app using Chaquopy.

## What is provided

- `main/main.py`: core DSP implementation
- `main/__init__.py`: Android-friendly wrapper
- `pyproject.toml`: package metadata for Python packaging and Chaquopy

## Recommended workflow

1. Add Chaquopy to your Android project.
2. Install `numpy` and `scipy` as Python dependencies.
3. Use the `main` Python package from your Kotlin/Java code.

## Chaquopy setup

In the root `build.gradle` (project-level):

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.1.0"
        classpath "com.chaquo.python:gradle:12.0.0"
    }
}
```

In the app module `build.gradle`:

```groovy
plugins {
    id 'com.android.application'
    id 'com.chaquo.python'
}

android {
    compileSdk 34
    defaultConfig {
        applicationId "com.example.app"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
}

chaquopy {
    python {
        pip {
            install "numpy>=1.26.0"
            install "scipy>=1.11.0"
        }
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.10.0'
}
```

## Copy Python source into Android

Place the repository Python files under `app/src/main/python/`:

- `app/src/main/python/main/__init__.py`
- `app/src/main/python/main/main.py`

You may omit desktop-only files such as `app/gui_app.py`.

## Example Kotlin usage

```kotlin
import com.chaquo.python.PyObject
import com.chaquo.python.Python

fun analyzePcm16Audio(pcm16: ShortArray, sampleRate: Int = 16000): Map<String, Any> {
    val py = Python.getInstance()
    val dsp = py.getModule("main")

    // Convert ShortArray to a Python list
    val pythonList = py.getBuiltins().get("list")!!.call(*pcm16.map { it.toInt() }.toTypedArray())

    val result: PyObject = dsp.callAttr("analyze_pcm16", pythonList, sampleRate)
    return result.asMap()
}
```

## What to expect

The wrapper returns a dictionary with:

- `labels`: list of frame labels (`0 = Silence`, `1 = Unvoice`, `2 = Voice`)
- `frame_times`: list of center times for each frame
- `total_frames`: number of analyzed frames
- `label_counts`: counts for voice/unvoice/silence
- `T_E`, `T_ZCR`, `T_R`, `T_SF`, `T_C`, `T_P`: adaptive thresholds

## Notes

- Pass raw 16-bit PCM samples from Android, not a WAV file path.
- If you need WAV parsing on Android, decode the WAV into PCM samples in Java/Kotlin and pass them into `analyze_pcm16()`.
- For large audio, avoid returning overly large arrays if you only need labels. Use custom wrapper logic if necessary.
