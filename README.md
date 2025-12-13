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
*   **In-App Updates:** Automatically checks for updates from GitHub Releases.
    *   **Selectable Channels:** Users can choose between **Stable**, **Dev**, or **Nightly** update channels directly in the App Settings.
    *   **Dynamic Update Checks:** The app intelligently fetches the latest build for the selected channel (e.g., `update_dev.json`, `update_nightly.json`).
    *   **Stable:** Official releases from the `main` branch.
    *   **Dev/Nightly:** Cutting-edge builds from development branches.
*   **Robust Update Installation:** Smart handling of APK downloads with automatic fallback mechanisms to ensure successful installation on various Android versions (including Android 13+).
*   **Multi-Channel CI/CD:** Supports dynamic "Feature Branch" releases. Every branch gets its own update channel (e.g., `feat-new-ui`), allowing parallel testing without interference.

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

*   **Stable Releases:** Triggered by pushing a tag starting with `v` (e.g., `v1.1.8`). Creates a permanent release on the `stable` channel.
*   **Feature/Dev Releases:** Triggered by pushing to `dev`, `fix-*`, or `feat-*` branches.
    *   Generates a dedicated update channel for that branch (e.g., `update_feat_login.json`).
    *   Builds an APK with a corresponding version name.
    *   Testers installing the APK from a specific branch will only receive updates for that branch.
*   **Versioning:** The `versionCode` is generated based on the build timestamp (`yyMMddHH`) to ensure strictly increasing versions across branches, preventing downgrade issues. The `versionName` includes branch and commit information.

## License

[MIT License](LICENSE)
