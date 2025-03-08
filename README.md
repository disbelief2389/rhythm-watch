# RhythmWatch – Dynamic Pomodoro Timer

[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-34-green.svg)](https://developer.android.com)

An open-source Android app offering a dynamic Pomodoro experience with adaptive work/break cycles and audio reminders.

![App Preview](screenshots/demo.gif) <!-- Add your own screenshot path later -->

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
   git clone https://github.com/your-username/hybridTime.git
