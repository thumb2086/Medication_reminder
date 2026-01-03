# Medication Reminder App (藥到叮嚀)

A smart medication reminder application integrated with an ESP32-based smart pillbox. It helps users track their medication schedule, monitors environmental conditions, and ensures timely intake through a combination of mobile notifications and hardware alerts.

[![Android CI/CD](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml/badge.svg)](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml)

## Features

*   **Smart Reminders:** customizable medication schedules with frequency and time settings.
*   **Medication Inventory Management & Refill Reminders:** Track medication stock levels and receive timely notifications when supplies run low, ensuring you never run out.
*   **Detailed Medication Reports:** Generate comprehensive medication reports with compliance rates, visualized through interactive charts for weekly, monthly, or quarterly periods.
*   **Hardware Integration:** seamless connection with an ESP32 smart pillbox via Bluetooth Low Energy (BLE).
*   **Hardware-Confirmed Intake:** When the pillbox alarm rings, confirm your dose simply by pressing the physical button on the box. The signal is sent back to the app via BLE, automatically updating your pill inventory and medication log without touching your phone.
*   **Smart Pillbox Guidance:** Remotely guide the pillbox to rotate to the correct compartment by selecting a medication in the app. The corresponding LED will light up, providing clear visual guidance for pill retrieval.
*   **Real-time Monitoring:** displays real-time temperature and humidity data from the pillbox sensors.
*   **Adherence Tracking:** Visualizes medication history with multi-status indicators and calculates a 30-day compliance rate.
    *   **Green Dot:** All doses taken as scheduled.
    *   **Yellow Dot:** Partially taken (missed some doses).
    *   **Red Dot:** No doses taken on a scheduled day.
*   **Font Size Adjustment:** Users can choose between Small, Medium, and Large font sizes in the settings menu to improve readability. The app theme updates instantly to reflect the chosen size.
*   **Character Themes:** Choose between "Kuromi", "Chibi Maruko-chan", "Crayon Shin-chan", and "Doraemon" themes for a personalized experience.
*   **Unified UI:** Consistent button styles across the app for a more cohesive user experience.
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
    *   **Dynamic Branch Discovery:** The app queries GitHub Releases to find available active branches, allowing you to test specific feature branches easily.
    *   **Dead Channel Warning:** Automatically detects if the currently selected feature branch has been deleted or is no longer maintained, prompting the user to switch channels.
*   **Robust Update Installation:** Smart handling of APK downloads with automatic fallback mechanisms to ensure successful installation on various Android versions (including Android 13+).
*   **Multi-Channel CI/CD:** Supports dynamic "Feature Branch" releases. Every branch gets its own update channel (e.g., `feat-new-ui`), allowing parallel testing without interference.

## ESP32 Firmware

The ESP32 firmware is designed for a modular and maintainable architecture, with all major components separated into individual files in the `esp32/src/` directory.

### Core Modules
*   **`main.ino`**: The main entry point that orchestrates the different modules.
*   **`ble_handler`**: Manages all Bluetooth Low Energy (BLE) communication.
*   **`display`**: Handles all screen drawing and UI logic.
*   **`hardware`**: Controls hardware peripherals (motor, buzzer, sensors). This module has been refactored to use the native **ESP32 LEDC** peripheral for servo motor control, ensuring compatibility with ESP32-C6 and providing precise PWM signal generation.
*   **`input`**: Manages user input from the rotary encoder and buttons.
*   **`storage`**: Handles flash storage operations (SPIFFS, Preferences).
*   **`wifi_ota`**: Manages Wi-Fi connectivity, NTP sync, and OTA updates.
*   **`config.h`**: Centralized constants, pin definitions, and configurations.
*   **`globals.h`**: Global variable declarations.

### Pinout Configuration (`config.h`)

The firmware is configured with a specific pinout for ESP32-C6 boards. **Warning:** Using pins reserved for the internal flash memory (like GPIO 6-11) or other dedicated functions (like USB on GPIO 12/13) will cause the device to crash. The default configuration uses safe, tested pins.

| Function | Pin | Notes |
| :--- | :---: | :--- |
| I2C SDA | 22 | For OLED Display |
| I2C SCL | 21 | For OLED Display |
| Encoder A | 19 | Rotary Encoder |
| Encoder B | 18 | Rotary Encoder |
| Encoder Push | 20 | Rotary Encoder Button |
| Confirm Button | 23 | |
| Back Button | 2 | Right Side Pin |
| DHT Sensor | 1 | Left Side Pin, DHT11 Temp/Humid |
| Buzzer 1 | 4 | Left Side Pin |
| Buzzer 2 | 5 | Left Side Pin |
| Servo Motor | 8 | ESP32-C6 compatible (LEDC) |
| WS2812 LED Strip| 15 | |

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
| **Guide Pillbox** | `0x42` | `Slot(1B)` | Rotates the pillbox to the specified slot (1-8). |

### Response Reference (ESP32 -> App)

| Response Name | OpCode | Data Payload | Description |
| :--- | :---: | :--- | :--- |
| **Protocol Report** | `0x71` | `Version(1B)` | Reports protocol version (e.g., `0x03`). |
| **Status Report** | `0x80` | `SlotMask(1B)` | Bitmask of medication slots status. |
| **Medication Taken** | `0x81` | `SlotID(1B)` | Triggered by the physical pillbox button to report a dose was taken. |
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
*   **Branch Cleanup:* When a branch is deleted, the corresponding nightly release and tag are automatically removed to keep the release list clean. Manual cleanup is also supported via GitHub Actions workflow dispatch.
*   **Versioning:** The `versionCode` corresponds to the **Git Commit Count** to ensure strict consistency between the Android Build and CI Artifacts. The `versionName` follows the `1.2.1-dev-260` format.
*   **Release Naming:** Nightly releases now use a clearer title format: `<Branch> | <VersionName>` (e.g., `feat-ui | 1.2.0-nightly-205`) to easily identify the source branch and version details.
*   **Dynamic Base Version:** CI/CD automatically detects the latest Git Tag (e.g., `v1.2.1`) as the base version for all subsequent nightly builds, ensuring the version name always reflects the latest stable milestone (e.g., `1.2.1-nightly-xxx`).
*   **Deployment Concurrency:** Implemented concurrency control to prevent conflicting deployments on `gh-pages` by automatically cancelling outdated workflows on the same branch.

## License

[MIT License](LICENSE)

---

## Project Structure

This project consists of two main parts: the Android application (`app/`) and the ESP32 firmware (`esp32/`).

#### Android Application (`app/`)

-   **`app/src/main/AndroidManifest.xml`**: Defines the application's core properties, permissions, and components (activities, services, receivers).
-   **`app/src/main/java/com/example/medicationreminderapp/`**: Contains the Kotlin source code for the Android application, organized into several sub-packages and top-level files:
    -   **`di/`**: Dependency Injection setup (using Hilt).
        -   `AppModule.kt`: Defines modules for providing dependencies across the application.
    -   **`ui/`**: UI-related components, primarily Fragments and ViewModels.
        -   `LogFragment.kt`: Displays application logs.
        -   `MainViewModel.kt`: ViewModel for `MainActivity`, managing UI-related data and logic.
        -   `ReminderFragment.kt`: Fragment for displaying medication reminders.
        -   `ViewPagerAdapter.kt`: Adapter for managing fragments in a ViewPager.
        -   `EnvironmentFragment.kt`: Displays real-time environmental data from the ESP32.
    -   **`adapter/`**: Adapters for RecyclerViews and other list-based UI components.
        -   `MedicationListAdapter.kt`: Adapter for displaying the list of medications.
    -   **`util/`**: Utility classes for various helper functions.
        -   `UpdateManager.kt`: Handles in-app update logic, including fetching and installing updates.
        -   `SingleLiveEvent.kt`: A custom LiveData implementation for one-time events.
    -   **`Medication.kt`**: Data class representing a medication.
    -   **`BaseActivity.kt`**: A base Activity class providing common functionality (e.g., theme application, font size adjustment).
    -   **`BootReceiver.kt`**: BroadcastReceiver that re-schedules alarms after device reboot.
    -   **`MainActivity.kt`**: The main entry point of the application, hosting various fragments and managing overall UI flow.
    -   **`AlarmReceiver.kt`**: BroadcastReceiver for handling scheduled medication alarms.
    -   **`AppRepository.kt`**: The central data repository, abstracting data sources (e.g., local database, Bluetooth).
    -   **`AlarmScheduler.kt`**: Manages scheduling and canceling medication alarms.
    -   **`SnoozeReceiver.kt`**: BroadcastReceiver for handling snooze actions from notifications.
    -   **`HistoryFragment.kt`**: Displays the user's medication intake history.
    -   **`SensorDataPoint.kt`**: Data class for environmental sensor readings (temperature, humidity, timestamp).
    -   **`SettingsFragment.kt`**: Displays and manages application settings (e.g., theme, language, update channel).
    -   **`BluetoothLeManager.kt`**: Manages Bluetooth Low Energy (BLE) communication with the ESP32 smart pillbox.
    -   **`WiFiConfigFragment.kt`**: Fragment for configuring Wi-Fi settings on the ESP32 device.
    -   **`ImagePickerPreference.kt`**: Custom Preference class for selecting images in settings.
    -   **`MedicationListFragment.kt`**: Fragment for displaying and managing the list of medications.
    -   **`MedicationTakenReceiver.kt`**: BroadcastReceiver for handling "medication taken" events from the ESP32.
    -   **`ReminderSettingsFragment.kt`**: Fragment for configuring specific reminder settings.
    -   **`MedicationReminderApplication.kt`**: Custom `Application` class, primarily used for Hilt initialization and global application setup.
-   **`app/src/main/res/`**: Contains all application resources (layouts, drawables, values, etc.).
    -   **`drawable/`**: Drawable resources (icons, images, XML drawables).
    -   **`layout/`**: XML layout files for activities, fragments, and list items.
    -   **`menu/`**: XML files defining application menus.
    -   **`xml/`**: XML files for preferences and other configurations (e.g., `preferences.xml`).
    -   **`values/`**: Default string, color, style, and theme definitions.
    -   **`values-en/`**: English translations for strings.
    -   **`values-night/`**: Resources specific to dark theme mode.
    -   **`mipmap-*/`**: Launcher icons for different densities.

#### ESP32 Firmware (`esp32/`)

-   **`esp32/src/`**: Contains the C++ source code for the ESP32 firmware, organized into modular components.
    -   **`main.ino`**: The main entry point of the ESP32 program, coordinating the other modules.
    -   **`ble_handler.cpp/.h`**: Manages Bluetooth Low Energy (BLE) communication.
    -   **`display.cpp/.h`**: Handles OLED display drawing and UI logic.
    -   **`hardware.cpp/.h`**: Controls hardware peripherals (motor, buzzer, sensors).
    -   **`input.cpp/.h`**: Manages user input from the rotary encoder and buttons.
    -   **`storage.cpp/.h`**: Handles persistent storage operations (SPIFFS, Preferences).
    -   **`wifi_ota.cpp/.h`**: Manages Wi-Fi connectivity, NTP synchronization, and Over-The-Air (OTA) updates.
    -   **`config.h`**: Centralized header for hardware pin definitions, constants, and other configurations.
    -   **`globals.h`**: Header for global variable declarations.
