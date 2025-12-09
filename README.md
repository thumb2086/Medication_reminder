# Medication Reminder App (藥到叮嚀)

A smart medication reminder application integrated with an ESP32-based smart pillbox. It helps users track their medication schedule, monitors environmental conditions, and ensures timely intake through a combination of mobile notifications and hardware alerts.

[![Android CI/CD](https://github.com/CPXru/Medication_reminder/actions/workflows/android-cicd.yml/badge.svg)](https://github.com/CPXru/Medication_reminder/actions/workflows/android-cicd.yml)

## Features

*   **Smart Reminders:** customizable medication schedules with frequency and time settings.
*   **Hardware Integration:** seamless connection with an ESP32 smart pillbox via Bluetooth Low Energy (BLE).
*   **Real-time Monitoring:** displays real-time temperature and humidity data from the pillbox sensors.
*   **Adherence Tracking:** records medication intake history and generates visual charts for compliance analysis.
*   **Character Themes:** Choose between "Kuromi" and "Chibi Maruko-chan" themes for a personalized experience.
*   **Engineering Mode:** toggle hardware engineering mode directly from the app for diagnostics.
*   **Wi-Fi Configuration:** Configure the ESP32's Wi-Fi credentials directly from the app via BLE, now conveniently located within the App Settings.
*   **Alarm System:** Set up to 4 alarms on the ESP32 pillbox for standalone reminders.
*   **Interactive Charts:** View temperature and humidity trends with interactive line charts, supporting pan, zoom, and data point inspection.

## Bluetooth Protocol Versioning

To ensure compatibility between the App and the ESP32 firmware as features evolve, a protocol versioning mechanism has been introduced.

*   **Handshake:** Upon connection, the App requests the protocol version from the ESP32 (Command `0x01`).
*   **Backward Compatibility:** The App adapts its data parsing logic based on the reported version.
    *   **Version 1:** Legacy protocol (single record history).
    *   **Version 2:** Batch history data transfer (5 records per packet) and optimized integer-based sensor data format.
    *   **Version 3:** Adds support for Alarm settings (Command `0x41`).

## Getting Started

1.  **Clone the repository:** `git clone https://github.com/CPXru/Medication_reminder.git`
2.  **Open in Android Studio:** Import the project into Android Studio (Ladybug | 2024.2.1 or later recommended).
3.  **Build and Run:** Connect an Android device (Android 10+) or use an emulator to run the application.

## CI/CD & Versioning

This project uses GitHub Actions for continuous integration and automated version management.

*   **Tag Releases:** Pushing a tag (e.g., `1.1.9`) triggers a stable release build. The APK version will match the tag name.
*   **Nightly Builds:** Pushes to any branch trigger a nightly build. The version name will be formatted as `branch-nightly` (e.g., `dev-nightly`).
*   **Build Number:** The `versionCode` is automatically incremented based on the GitHub Actions run number.

## License

[MIT License](LICENSE)
