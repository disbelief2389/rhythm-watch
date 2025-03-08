
# RhythmWatch – Dynamic Pomodoro Timer

[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-34-green.svg)](https://developer.android.com)

An open-source Android app offering a dynamic Pomodoro experience with adaptive work/break cycles and audio reminders.

<!-- ![App Preview](screenshots/demo.gif) -->

## ✨ Core Features

- **Dynamic Timer System**
  - **Work Mode**: Stopwatch tracks focused work time.
  - **Break Mode**: Auto-configured 1:1 countdown timer based on elapsed work time.
  - Smooth transition between modes with a single tap.

- **Smart Audio Alerts**
  - Custom sound plays when break ends.
  - 30-minute interval reminders (respects device volume settings).
  - Built with Android `MediaPlayer`/`AudioManager`.

- **Background Reliability**
  - Persistent foreground service ensures uninterrupted timing.
  - Survives app minimization and screen-off scenarios.

- **Minimalist UI**
  - Clean, gesture-driven interface.
  - Real-time updates with animated color transitions.

## 🛠️ Technical Overview

### Architecture
| Layer              | Components                          |
|--------------------|-------------------------------------|
| **UI**             | Single `Activity` + Jetpack Compose |
| **Service**        | `TimerService` (Foreground)         |
| **Logic**          | Stopwatch/Countdown state machine   |
| **Audio**          | `MediaPlayer`, Volume integration   |

### Requirements
- Android 8.0+ (API 26+)
- Kotlin 1.9.0
- [Samsung Galaxy S9+](https://www.samsung.com) tested configuration

## 🚀 Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/disbelief2389/rhythm-watch.git
   ```
2. Open project in Android Studio (Arctic Fox 2020.3.1+ recommended)
3. Build & run on device/emulator

## 📋 Usage

1. **Start Working**: Tap screen to begin stopwatch.
2. **Take Break**: Tap again to start 1:1 break timer.
3. **Audio Reminders**:
   - Hear a chime when break ends
   - Get subtle alerts every 30 minutes
4. Background operation continues until manually stopped

## 🔧 Dependencies

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit
- AndroidX Lifecycle - Service state management
- [Material 3](https://m3.material.io) - Theming system

## 🤝 Contributing

PRs welcome! Please follow:
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/fooBar`)
3. Commit changes (`git commit -am 'Add some fooBar'`)
4. Push to branch (`git push origin feature/fooBar`)
5. Open Pull Request

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

**Optimized for focus** • **Respects your workflow** • **Open-source freedom**
