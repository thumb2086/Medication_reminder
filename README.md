# Medication Reminder App (藥到叮嚀)

A smart medication reminder application integrated with an ESP32-based smart pillbox. It helps users track their medication schedule, monitors environmental conditions, and ensures timely intake through a combination of mobile notifications and hardware alerts.

[![Android CI/CD](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml/badge.svg)](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml)

## Features

*   **Smart Reminders:** customizable medication schedules with frequency and time settings.
*   **Hardware Integration:** seamless connection with an ESP32 smart pillbox via Bluetooth Low Energy (BLE).
*   **Real-time Monitoring:** displays real-time temperature and humidity data from the pillbox sensors.
*   **Adherence Tracking:** records medication intake history and generates visual charts for compliance analysis.
*   **Character Themes:** Choose between "Kuromi" and "Chibi Maruko-chan" themes for a personalized experience.
*   **Engineering Mode:** toggle hardware engineering mode directly from the app for diagnostics.
*   **Wi-Fi Configuration:** Configure the ESP32's Wi-Fi credentials directly from the app via BLE. The interface is now enhanced with Material Design visuals, input validation, and clear instructions.
*   **Alarm System:** Set up to 4 alarms on the ESP32 pillbox for standalone reminders.
*   **Interactive Charts:** View temperature and humidity trends with interactive line charts, supporting pan, zoom, and data point inspection.
*   **In-App Updates:** Automatically checks for updates from GitHub Releases.
    *   **Selectable Channels:** Users can choose between **Stable**, **Dev**, or browse **Active Development Branches** directly in the App Settings.
    *   **Cross-Channel Switching:** Freely switch between channels (e.g., from Dev to Stable) to install the latest build of that branch.
    *   **Smart Default Channel:** The app automatically sets the default update channel based on the installed build's type.
        *   **Stable Builds:** Default to `Main` channel.
        *   **Dev Builds:** Default to `Dev` channel.
        *   **Feature Builds:** Default to the specific feature branch channel (e.g., `feat-new-ui`).
    *   **Dynamic Update Checks:** The app intelligently fetches the latest build for the selected channel (e.g., `update_dev.json`, `update_nightly.json`).
    *   **Safety Checks:** Detects if an update belongs to a different channel (Application ID) and warns the user that a separate app instance will be installed instead of an in-place update.
    *   **Stable:** Official releases from the `main` branch.
    *   **Dev:** Cutting-edge builds from the `dev` branch.
    *   **Dynamic Branch Discovery:** The app queries GitHub Releases to find available active branches (tagged as `nightly-<branch>`), allowing you to test specific feature branches easily.
*   **Robust Update Installation:** Smart handling of APK downloads with automatic fallback mechanisms to ensure successful installation on various Android versions (including Android 13+).
*   **Multi-Channel CI/CD:** Supports dynamic "Feature Branch" releases. Every branch gets its own update channel (e.g., `feat-new-ui`), allowing parallel testing without interference.

## Bluetooth Low Energy Protocol

The communication between the App and the ESP32 Smart Pillbox relies on a custom binary protocol over BLE UART Service.

**Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
**TX Characteristic (Write):** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
**RX Characteristic (Notify):** `c8c7c599-809c-43a5-b825-1038aa349e5d`

### Command Reference (App -> ESP32)

| Command Name | OpCode | Parameters | Description |
| :--- | :---: | :--- | :--- |
| **Get Protocol Version** | `0x01` | None | Requests the device protocol version (Returns `0x71`). |
| **Sync Time** | `0x11` | `Year(1B)`, `Month(1B)`, `Day(1B)`, `Hour(1B)`, `Min(1B)`, `Sec(1B)` | Syncs RTC time. Year is relative to 2000 (e.g., 24 for 2024). |
| **Set Wi-Fi** | `0x12` | `LenSSID(1B)`, `SSID(...)`, `LenPass(1B)`, `Password(...)` | Sends Wi-Fi credentials to the device. |
| **Set Engineering Mode** | `0x13` | `Enable(1B)` | `0x01`: Enable, `0x00`: Disable. |
| **Get Eng. Mode Status** | `0x14` | None | Queries if Engineering Mode is active (Returns `0x83`). |
| **Get Status** | `0x20` | None | Requests current medication status (Returns `0x80`). |
| **Get Env Data** | `0x30` | None | Single request for current temperature/humidity (Returns `0x90`). |
| **Get Historic Data** | `0x31` | None | Requests stored environmental history (Returns series of `0x91`, ends with `0x92`). |
| **Subscribe Realtime** | `0x32` | None | Enables automatic pushing of environmental data. |
| **Set Alarm** | `0x41` | `Slot(1B)`, `Hour(1B)`, `Minute(1B)`, `Enable(1B)` | Sets a hardware alarm. `Slot`: 0-3, `Enable`: 1/0. |

### Response Reference (ESP32 -> App)

| Response Name | OpCode | Data Payload | Description |
| :--- | :---: | :--- | :--- |
| **Protocol Report** | `0x71` | `Version(1B)` | Reports protocol version (e.g., `0x03`). |
| **Status Report** | `0x80` | `SlotMask(1B)` | Bitmask of medication slots status. |
| **Medication Taken** | `0x81` | `SlotID(1B)` | Notification when a pill is taken. |
| **Time Sync Ack** | `0x82` | None | Acknowledges time synchronization. |
| **Eng. Mode Report** | `0x83` | `Status(1B)` | `0x01`: Enabled, `0x00`: Disabled. |
| **Env Data** | `0x90` | `Temp(2B)`, `Hum(2B)` | Real-time sensor data. Values are `Short` (x100). |
| **Historic Data** | `0x91` | `Timestamp(4B)`, `Temp(2B)`, `Hum(2B)` | One or more historic records. |
| **Sync Complete** | `0x92` | None | Indicates end of historic data transmission. |
| **Error Report** | `0xEE` | `ErrorCode(1B)` | `0x02`: Sensor Error, `0x03`: Unknown Cmd, `0x04`: Access Error. |

## Bluetooth Protocol Versioning

To ensure compatibility between the App and the ESP32 firmware as features evolve, a protocol versioning mechanism has been introduced.

*   **Handshake:** Upon connection, the App requests the protocol version from the ESP32 (Command `0x01`).
*   **Backward Compatibility:** The App adapts its data parsing logic based on the reported version.
    *   **Version 1:** Legacy protocol (single record history).
    *   **Version 2:** Batch history data transfer (5 records per packet) and optimized integer-based sensor data format.
    *   **Version 3:** Adds support for Alarm settings (Command `0x41`).

## Getting Started

1.  **Clone the repository:** `git clone https://github.com/thumb2086/Medication_reminder.git`
2.  **Open in Android Studio:** Import the project into Android Studio (Ladybug | 2024.2.1 or later recommended).
3.  **Build and Run:** Connect an Android device (Android 10+) or use an emulator to run the application.

## CI/CD & Versioning

This project uses GitHub Actions for continuous integration and automated version management.

*   **Stable Releases:** Triggered by pushing a tag starting with `v` (e.g., `v1.1.8`). Creates a permanent release on the `main` channel.
*   **Feature/Dev Releases:** Triggered by pushing to `dev`, `fix-*`, or `feat-*` branches.
    *   Generates a dedicated update channel for that branch (e.g., `update_feat_login.json`).
    *   Builds an APK with a corresponding version name.
    *   Testers installing the APK from a specific branch will only receive updates for that branch.
*   **Unified Naming:** All artifacts (APK) and version names now strictly follow the `X.Y.Z-channel-count` format (e.g., `1.2.1-dev-255`) to eliminate spaces and special characters, ensuring consistent behavior across different environments.
*   **Branch Cleanup:** When a branch is deleted, the corresponding nightly release and tag are automatically removed to keep the release list clean. Manual cleanup is also supported via GitHub Actions workflow dispatch.
*   **Versioning:** The `versionCode` corresponds to the **Git Commit Count** to ensure strict consistency between the Android Build and CI Artifacts. The `versionName` follows the `1.2.1-dev-260` format.
*   **Release Naming:** Nightly releases now use a clearer title format: `<Branch> | <VersionName>` (e.g., `feat-ui | 1.2.0-nightly-205`) to easily identify the source branch and version details.
*   **Dynamic Base Version:** CI/CD automatically detects the latest Git Tag (e.g., `v1.2.1`) as the base version for all subsequent nightly builds, ensuring the version name always reflects the latest stable milestone (e.g., `1.2.1-nightly-xxx`).
*   **Deployment Concurrency:** Implemented concurrency control to prevent conflicting deployments on `gh-pages` by automatically cancelling outdated workflows on the same branch.

## License

[MIT License](LICENSE)
