# Smart Pillbox Reminder App

This is an Android application designed to help users manage their medication schedules and interact with a smart pillbox via Bluetooth Low Energy (BLE) technology.

## Design Philosophy

The core design of this project is to keep complex logic processing within the Android App, making the role of the smart pillbox (ESP32) as simple as possible—acting merely as a faithful executor of time and commands. This design significantly simplifies the firmware development on the hardware side, improving system stability and power efficiency.

## Main Features

*   **Tabbed Interface:**
    *   Utilizes `TabLayout` and `ViewPager2` to create a modern tabbed interface, divided into four main functional areas: "Reminder Settings," "Medication List," "Medication History," and "Environment Monitoring," for more intuitive operation.
*   **Complete Medication Management (CRUD):**
    *   On the "Reminder Settings" page, you can dynamically add, edit, and delete medication reminders.
    *   Set the name, dosage, medication time, and start/end dates for each medication. The **total dosage will be calculated automatically**.
    *   An intelligent compartment exclusion mechanism prevents you from assigning multiple medications to the same compartment.
*   **Reliable Alarm Reminders:**
    *   The app sends precise local notifications for each medication time you set.
    *   Alarms remain effective even if the app is not in the foreground or the phone is rebooted.
    *   Notifications include "Taken" and "Snooze" actions for quick interaction.
*   **Smart Pillbox Integration:**
    *   **Guided Filling:** When adding medications, the app guides you through the process one by one. After setting up each medication, the pillbox automatically rotates to the corresponding compartment, allowing you to place the medication directly and confirm with a button on the pillbox, achieving a seamless hardware-software experience.
    *   Scan for and connect to the smart pillbox.
    *   Synchronize time and medication reminders for each compartment with the pillbox.
    *   Receive status updates from the pillbox, such as which compartment's medication has been taken, compartment blockages, sensor errors, etc.
*   **Data Monitoring and Tracking:**
    *   On the "Environment Monitoring" page, when connected via Bluetooth, a **real-time line chart** visualizes the temperature and humidity data sent back from the pillbox. When not connected, a prompt message is displayed. Historical temperature and humidity data from the offline period can be synced and displayed on the chart by performing a **pull-to-refresh** gesture.
    *   On the "Medication History" page, a calendar visualizes daily medication records and calculates and displays the medication adherence rate for the past 30 days.
*   **Settings:**
    *   The settings page can be accessed via the settings icon on the toolbar.
    *   Supports light, dark, and system-following theme switching.
    *   **Character Theme:** The app's theme color is tied to the character selection. Choosing "Kuromi" switches the theme color to purple, "My Melody" to pink, and "Cinnamoroll" to blue, adding a fun, interactive element.
    *   **Engineering Mode:** A switch in the settings, whose state is bidirectionally synced with the pillbox, accurately reflecting the device's current mode.

## Architecture

This app adopts the **MVVM (Model-View-ViewModel)** architecture, which separates the UI from business logic, making the code more modular and easier to maintain.

*   **View:** Comprises `Activity` and `Fragment`, responsible for displaying the UI and handling user interactions.
*   **ViewModel:** The `MainViewModel` holds and manages UI-related data. It survives configuration changes (like screen rotations) and communicates with the data layer. It now uses **Hilt for dependency injection**.
*   **Model:**
    *   **Repository:** The `AppRepository` acts as a single source of truth for all app data, abstracting the data sources from the rest of the app. It is provided as a singleton by **Hilt**.
    *   **Data Sources:**
        *   `BluetoothLeManager`: Manages all BLE communications. It's now injected via **Hilt**.
        *   `SharedPreferences`: Stores medication lists, user settings, etc.

## Instructions for Use

(Instructions are identical to the previous version and are omitted here for brevity.)

## Bluetooth Protocol

To enable interaction between the app and the pillbox, we have defined a bidirectional communication protocol based on byte arrays. The `BluetoothLeManager` class is responsible for encapsulating commands into byte arrays and sending them, as well as parsing the data received from the pillbox.

### Service and Characteristics UUIDs

- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Write Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8` (App -> Pillbox)
- **Notify Characteristic UUID:** `c8c7c599-809c-43a5-b825-1038aa349e5d` (Pillbox -> App)

### App -> Pillbox (Commands)

1.  **Request Protocol Version (0x01):** `[0]: 0x01` - Asks the pillbox for its current protocol version.
2.  **Time Sync (0x11):** `[0]: 0x11`, `[1]: Year-2000`, `[2]: Month`, `[3]: Day`, `[4]: Hour`, `[5]: Minute`, `[6]: Second`
3.  **Send Wi-Fi Credentials (0x12):** `[0]: 0x12`, `[1]: SSID_Len`, `[2...]: SSID`, `[...]: Pass_Len`, `[...]: Password`
4.  **Set Engineering Mode (0x13):** `[0]: 0x13`, `[1]: Enable (0x01/0x00)` - Commands the pillbox to enter or exit engineering mode.
5.  **Request Engineering Mode Status (0x14):** `[0]: 0x14` - Asks the pillbox for its current engineering mode status.
6.  **Request Status (0x20):** `[0]: 0x20` - Asks for the pillbox's general status.
7.  **Request Instant Environment Data (0x30):** `[0]: 0x30`
8.  **Request Historic Environment Data (0x31):** `[0]: 0x31`
9.  **Subscribe Realtime Environment Data (0x32):** `[0]: 0x32` - Subscribes to real-time environment data push.
10. **Unsubscribe Realtime Environment Data (0x33):** `[0]: 0x33` - Unsubscribes from real-time environment data push.

### Pillbox -> App (Notifications)

1.  **Protocol Version Report (0x71):** `[0]: 0x71`, `[1]: Version (e.g., 0x02 for V2)` - Reports the pillbox's current protocol version.
2.  **Box Status Update (0x80):** `[0]: 0x80`, `[1]: Slot Mask`
3.  **Medication Taken Report (0x81):** `[0]: 0x81`, `[1]: Slot Number`
4.  **Time Sync Acknowledged (0x82):** `[0]: 0x82`
5.  **Engineering Mode Status Report (0x83):** `[0]: 0x83`, `[1]: Status (0x01 for enabled)` - Reports the pillbox's current engineering mode.
6.  **Instant Sensor Data Report (0x90):** `[0]: 0x90`, `[1-2]: Temp`, `[3-4]: Hum` (Little Endian, value*100)
7.  **Historic Sensor Data Batch (0x91):** `[0]: 0x91`, `[1...]: 1 to 5 history records`
    - ***Note:*** *This protocol change requires a corresponding firmware update on the ESP32.*
8.  **End of Historic Data Transmission (0x92):** `[0]: 0x92`
9.  **Error Report (0xEE):** `[0]: 0xEE`, `[1]: Error Code`

## Project Structure

(Project structure description is identical to the previous version and is omitted here.)

## Permissions Required

`POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## Recent Updates

*   **0099:** **Implemented Repository Pattern and Fixed Hilt Injection Limitations.**
    *   **Architecture Refactor:** Created a singleton `AppRepository` to centralize all data logic (SharedPreferences, medication lists, sensor history, and medication status), decoupling it from the `MainViewModel`.
    *   **Hilt Fix:** Resolved a Dagger Hilt limitation that prevented direct injection of ViewModels into a `BroadcastReceiver`. The receiver now injects the `AppRepository` via a Hilt EntryPoint, ensuring a single source of truth and correct architectural practice.
*   **0098:** **Fixed Notification Sync and Dismissal Issues After Taking Medication.**
    *   **Problem:** The `MedicationTakenReceiver` was incorrectly creating a new `MainViewModel` instance, which prevented medication logs and charts from updating. Notifications also sometimes failed to dismiss.
    *   **Fix:** Implemented a Hilt `EntryPoint` in the receiver to ensure access to the singleton instances of `AppRepository` and `BluetoothLeManager`. Also ensured the `notificationId` was correctly used to cancel the notification.
*   **0094:** **Chart Visuals and Interaction Improvements.**
    *   **Dual Axis Display:** Implemented dual Y-axis display for temperature (left) and humidity (right), resolving display issues caused by scale differences.
    *   **Visual Simplification:** Removed data point circles from the chart, keeping only the curves and fill for a cleaner, modern look. Added entry and pull-to-refresh animations.
    *   **Enhanced Interaction:** Added a `CustomMarkerView` that displays the specific time and value of a selected point upon long-press.
*   **0093:** **Fixed Unknown Error Code 3 during Bluetooth connection.**
    *   **Analysis:** Error code 3 occurred when the app sent the new "Request Protocol Version (0x01)" command, which was unimplemented in the `esp32.ino` firmware.
    *   **Fix:** Updated firmware to handle `0x01` and reply with version `0x02`.
*   **0092:** **Implemented Real-time Environment Data Subscription.**
    *   **Protocol Upgrade:** Added `0x32` (Subscribe) and `0x33` (Unsubscribe) commands.
    *   **App Optimization:** `EnvironmentFragment` switched to `LineChart`.
*   **0090:** **Implemented Dependency Injection with Hilt.**
    *   **Refactoring Scope:** Integrated Hilt to manage dependencies for `MainViewModel` and `BluetoothLeManager`.
    *   **Code Changes:**
        *   Configured Hilt in the `build.gradle.kts` files.
        *   Created a `MedicationReminderApplication` class and registered it in the `AndroidManifest.xml`.
        *   Annotated `MainActivity` and `MainViewModel` for Hilt integration.
        *   Created an `AppModule` to provide the `BluetoothLeManager` instance.
    *   **Benefits:** Decoupled components, improving the code's testability and maintainability.
*   **0089:** **Refactored `LiveData` to `StateFlow` in `MainViewModel`.**
    *   **Refactoring Scope:** `isBleConnected`, `bleStatus`, `isEngineeringMode`, `historicSensorData`, `medicationList`, `dailyStatusMap`, and `complianceRate`。
    *   **UI Layer Update:** Updated `MainActivity` and all relevant Fragments to collect the `StateFlow` updates using `lifecycleScope.launch` and `repeatOnLifecycle`.
    *   **Benefits:** Improved predictability and thread safety of UI state management.
*   **0088:** **Introduced Bluetooth protocol versioning and outlined future optimization directions.**
    *   **Protocol Versioning:** Added opcodes `0x01` (Request Protocol Version) and `0x71` (Report Protocol Version).
    *   **Documentation Update:** Added sections for "Protocol Versioning" and "Future Optimization Directions" to the README files.
*   **0086:** Implemented bidirectional synchronization for the engineering mode state between the App and the pillbox.
*   **0085:** Optimized the Bluetooth protocol and app's data handling logic by introducing batch processing for historical data.
*   **0084:** Adjusted the synchronization timing for historical temperature and humidity data to be user-triggered via pull-to-refresh.
*   **0083:** Optimized the Bluetooth data protocol for temperature and humidity transmission.
*   **0082:** Fixed the vertical alignment of the toolbar title.
*   **0081:** Fixed a display issue with the toolbar title.
*   **0080:** Fixed a critical bug caused by title centering and restored the original title position.
*   **0079:** Cleaned up multiple warnings in `MainActivity.kt`.
*   **0078:** Fixed the issue of the toolbar title not being centered.
*   **0077:** Fixed the issue of the back button color being incorrect on the settings page.
*   **0076:** Thoroughly resolved the issue of incorrect text and icon colors in the toolbar under character themes.
*   **0075:** Re-fixed the issue of incorrect text and icon colors in the toolbar.
*   **0074:** Fixed the issue of incorrect text and icon colors on the toolbar and status bar in light themes.
*   **0072:** Completely redesigned the app's UI and resolved a resulting build error.
*   **0071:** Fixed an issue where the text and icon colors on the Toolbar and status bar were incorrect in light themes.
*   **0070:** Fixed an immersive UI issue on devices with display cutouts.
*   **0069:** Integrated the accent color with the character selection feature and resolved an immersive status bar display issue.
*   **0068:** Completely redesigned the app's UI and resolved a resulting build error.
*   **0067:** Fixed a compilation error in `MainActivity.kt`.
*   **0066:** Fixed the toolbar and status bar UI issues again.
*   **0065:** Fixed Linter errors in `strings.xml`.
*   **0064:** Fixed Linter errors in `strings.xml` and `values-en/strings.xml`.
*   **0063:** Fixed UI issues with the toolbar and status bar.
*   **0062:** Fixed multiple UI and functionality bugs.
*   **0061:** Fixed UI display issues and fixed the settings button.
*   **0059:** Fixed status bar overlapping the toolbar title.
*   **0058:** Fixed the issue where the toolbar color was incorrect when the accent color was set to "Default".
*   **0057:** Fixed a compilation error in `MainActivity.kt`.
*   **0056:** Reverted bug fix 0055 to solve a resource linking failure in `AndroidManifest.xml`.
*   **0055:** Re-fixed an issue where an image was obscuring input fields.
*   **0054:** Fixed UI issues where the layout appeared strange on punch-hole displays.
*   **0053:** Fixed an issue where the accent color was not correctly synchronized in dark mode.
*   **0052:** Added a disconnect button to the reminder settings page.
*   **0015:** Fixed an issue where the medication adherence rate was not updating.
*   **0060:** Added a character selection feature.
*   **0049:** Cleaned up multiple warnings in the project.
*   **0047:** Added "Engineering Mode" and defined a new Bluetooth protocol.
*   **0045:** Added back buttons to the settings and Wi-Fi configuration pages.
*   **0044:** Added snooze and "taken" actions to medication reminder notifications.
*   **0043:** Fixed the transparent background issue in `WiFiConfigFragment`.
*   **0042:** Added a Wi-Fi configuration screen.
*   **0041:** Fixed accessibility warnings.
*   **0040:** Fixed a resource linking error and added a decorative Kuromi image.
*   **0039:** Fixed several deprecation warnings in `MainActivity.kt`.
*   **0038:** Added a back arrow to the settings page.
*   **0037:** Fixed a UI display issue on the settings page.
*   **0036:** Fixed an issue where the settings icon was not visible in light mode.
*   **0035:** Added a language switching feature.
*   **0034:** Added English localization.
*   **0033:** Implemented historical temperature and humidity data synchronization.
*   **0032:** Fixed multiple compilation errors and warnings.
*   **0031:** Cleaned up all duplicate and empty files.
*   **0030:** Implemented the "App actively requests temperature/humidity data" feature.
*   **0029:** Fixed multiple build errors and warnings in `app/build.gradle.kts`.
*   **0-028:** Fixed a build failure.
*   **0027:** Removed the unused `sendJson` method.
*   **0026:** Cleaned up multiple "unused declaration" warnings.
*   **0025:** Removed the unused `frequency` field.
*   **0024:** Fixed multiple warnings in the IDE.
*   **0023:** Added a visual indicator feature to the medication history page.
*   **0022:** Simplified the version number setting.
*   **0021:** Cleaned up warnings in `SettingsFragment.kt`.
*   **0020:** Restored missing settings features.
*   **0019:** Resolved accessibility warnings.
*   **0018:** Resolved XML resource parsing errors.
*   **0017:** Resolved XML resource parsing errors.
*   **0016:** Restored the ability to edit and delete medication reminders.
*   **0015:** Fixed a UI issue where the medication list would not update immediately.
*   **0014:** Handled outdated warnings in the IDE.
*   **0013:** Cleaned up all warnings in the project.
*   **0012:** Cleaned up all warnings in the project.
*   **0011:** Fixed a resource linking error.
*   **0.010:** Added a settings page and a medication list page.
*   **0009:** Optimized the validation of the medication reminder form.
*   **0008:** Fixed a critical build error.