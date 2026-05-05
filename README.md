# 🚗 Fatigue Detection System (Android + ESP32)

A real-time driver fatigue detection system combining **computer vision (Android app)** and **biometric sensing (ESP32 + MAX30102)** to detect drowsiness and trigger alerts.

---

## 📌 Overview

This project detects fatigue using two parallel systems:

### 1. 📱 Android App (Camera-Based)
- Uses **MediaPipe Face Landmarker**
- Calculates **Eye Aspect Ratio (EAR)**
- Detects prolonged eye closure (drowsiness)

### 2. 🔌 ESP32 + MAX30102 (Sensor-Based)
- Measures **heart rate (BPM)**
- Detects abnormal patterns
- Sends data via **Bluetooth Classic**

👉 If **either system detects fatigue**, alerts are triggered.

---

## 🚀 Features

- 👁️ Real-time eye tracking
- ❤️ Heart rate monitoring
- 🔗 Bluetooth communication (ESP32 → Android)
- 🔔 Alert system:
  - Alarm sound
  - Phone vibration
  - Buzzer + motor (ESP32)
- 📡 Dual fatigue detection (AI + sensor fusion)

---

## 🛠️ Tech Stack

### 📱 Android
- Kotlin
- Jetpack Compose
- CameraX
- MediaPipe Face Landmarker

### 🔌 Hardware
- ESP32
- MAX30102 Pulse Sensor
- Buzzer
- Vibration Motor

---

## 📂 Project Structure
