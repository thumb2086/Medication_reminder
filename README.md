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
    *   On the "Environment Monitoring" page, when connected via Bluetooth, a **real-time line chart** visualizes the temperature and humidity data sent back from the pillbox. When not connected, a prompt message is displayed. When the user pulls to refresh, the app syncs **historical temperature and humidity data** from the offline period and displays it completely on the chart.
    *   On the "Medication History" page, a calendar visualizes daily medication records and calculates and displays the medication adherence rate for the past 30 days.
*   **Settings:**
    *   The settings page can be accessed via the settings icon on the toolbar.
    *   Supports light, dark, and system-following theme switching.
    *   Supports **Character Selection**, allowing users to choose between Kuromi and Chibi Maruko-chan for the display on the main page.
    *   **Engineering Mode:** A switch in the settings allows developers to enable engineering mode, which can be used to trigger special debugging functions on the pillbox.

## Instructions for Use

1.  **Add Medication (Guided Filling Process):**
    *   In the "Reminder Settings" tab, select the number of medications you want to add at once, and the system will automatically generate the corresponding input fields.
    *   After filling in all the medication information (name, dosage, frequency, compartment, etc.), click "Add Medication Reminder."
    *   **Pillbox Rotates Automatically:** The app will lock the screen and send a command to automatically rotate the pillbox to the compartment corresponding to the **first medication**.
    *   **Place Medication and Confirm:** The screen will prompt you to place the medication in the specified compartment. After completion, **press the physical button on the pillbox directly**.
    *   **Repeat the Process:** After receiving the confirmation signal, the app will send another command to rotate the pillbox to the compartment for the **next medication**. Simply repeat the "place medication -> press button" action until all medications have been placed.
    *   **Complete Synchronization:** Once all are finished, the app will sync all reminders to the pillbox at once and unlock the screen.
2.  **Edit or Delete Medication:**
    *   Click the **"Edit Reminder"** or **"Delete Reminder"** button at the bottom of the "Reminder Settings" tab.
    *   The app will pop up a list of all set medications.
    *   Select the medication you want to operate on from the list.
    *   **Edit:** After selection, the medication's information will be automatically filled into the form above. After making changes, click "Update Medication."
    *   **Delete:** After selection, confirm in the pop-up confirmation dialog to delete.
3.  **Connect to Pillbox:**
    *   Click the "Connect to Pillbox" button in the "Reminder Settings" tab. The app will start scanning and allow you to select your smart pillbox for pairing.

## Bluetooth Protocol

To enable interaction between the app and the pillbox, we have defined a bidirectional communication protocol based on byte arrays. The `BluetoothLeManager` class is responsible for encapsulating commands into byte arrays and sending them, as well as parsing the data received from the pillbox.

### Service and Characteristics UUIDs

- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Write Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8` (App -> Pillbox)
- **Notify Characteristic UUID:** `c8c7c599-809c-43a5-b825-1038aa349e5d` (Pillbox -> App)

### App -> Pillbox (Commands)

All commands are sent by writing to the **Write Characteristic**.

1.  **Time Sync:**
    - **Opcode:** `0x11`
    - **Purpose:** Synchronize the app's current time with the pillbox.
    - **Format (7 bytes):**
        - `[0]`: `0x11`
        - `[1]`: `Year - 2000`
        - `[2]`: `Month (1-12)`
        - `[3]`: `Day`
        - `[4]`: `Hour (0-23)`
        - `[5]`: `Minute`
        - `[6]`: `Second`

2.  **Send Wi-Fi Credentials:**
    - **Opcode:** `0x12`
    - **Purpose:** Send Wi-Fi SSID and password to the pillbox.
    - **Format (Variable Length):**
        - `[0]`: `0x12`
        - `[1]`: `SSID Length (S)`
        - `[2...2+S-1]`: `SSID`
        - `[2+S]`: `Password Length (P)`
        - `[3+S...3+S+P-1]`: `Password`

3.  **Set Engineering Mode:**
    - **Opcode:** `0x13`
    - **Purpose:** Enable or disable engineering mode on the pillbox.
    - **Format (2 bytes):**
        - `[0]`: `0x13`
        - `[1]`: `Enable (0x01 for true, 0x00 for false)`

4.  **Request Status:**
    - **Opcode:** `0x20`
    - **Purpose:** Actively query the pillbox for its current status (e.g., whether each compartment contains medication).
    - **Format (1 byte):** `[0]: 0x20`

5.  **Request Instant Environment Data:**
    - **Opcode:** `0x30`
    - **Purpose:** Actively request the current real-time temperature and humidity data from the pillbox.
    - **Format (1 byte):** `[0]: 0x30`

6.  **Request Historic Environment Data:**
    - **Opcode:** `0x31`
    - **Purpose:** Request the pillbox to start transmitting all its stored historical temperature and humidity data.
    - **Format (1 byte):** `[0]: 0x31`

### Pillbox -> App (Notifications)

All notifications are sent via the **Notify Characteristic**. The app parses this data in the `handleIncomingData(data: ByteArray)` method.

1.  **Box Status Update:**
    - **Opcode:** `0x80`
    - **Purpose:** Report the status of each compartment of the pillbox.
    - **Format (2 bytes):** `[0]: 0x80`, `[1]: Slot Mask`

2.  **Medication Taken Report:**
    - **Opcode:** `0x81`
    - **Purpose:** Report to the app after the pillbox detects that the user has taken medication from a compartment.
    - **Format (2 bytes):** `[0]: 0x81`, `[1]: Slot Number`

3.  **Time Sync Acknowledged:**
    - **Opcode:** `0x82`
    - **Purpose:** Confirm that the time synchronized from the app has been successfully received and set.
    - **Format (1 byte):** `[0]: 0x82`

4.  **Instant Sensor Data Report:**
    - **Opcode:** `0x90`
    - **Purpose:** Report the currently sensed environmental temperature and humidity data.
    - **Format (5 bytes):**
        - `[0]`: `0x90`
        - `[1]`: `Temperature Integer Part`
        - `[2]`: `Temperature Fractional Part`
        - `[3]`: `Humidity Integer Part`
        - `[4]`: `Humidity Fractional Part`
    - **Parsing:** `Temperature = byte[1] + byte[2] / 100.0`, `Humidity = byte[3] + byte[4] / 100.0`

5.  **Historic Sensor Data Point:**
    - **Opcode:** `0x91`
    - **Purpose:** Report a single piece of historical temperature and humidity data.
    - **Format (9 bytes):**
        - `[0]`: `0x91`
        - `[1-4]`: `Timestamp (Unix Timestamp, 4 bytes, Little Endian)`
        - `[5]`: `Temperature Integer Part`
        - `[6]`: `Temperature Fractional Part`
        - `[7]`: `Humidity Integer Part`
        - `[8]`: `Humidity Fractional Part`

6.  **End of Historic Data Transmission:**
    - **Opcode:** `0x92`
    - **Purpose:** Inform the app that all historical data has been transmitted.
    - **Format (1 byte):** `[0]: 0x92`

7.  **Error Report:**
    - **Opcode:** `0xEE`
    - **Purpose:** Report to the app when an error occurs in the pillbox.
    - **Format (2 bytes):** `[0]: 0xEE`, `[1]: Error Code`

## Project Structure

This project adopts a modern Android app architecture with a single Activity and multiple Fragments to ensure separation of concerns and high scalability.

*   `MainActivity.kt`: The app's single entry point `Activity`, acting as a "container" and "master controller."
    *   Responsible for hosting `TabLayout` and `ViewPager2`, managing the switching of main Fragments.
    *   Creates and holds the single instance of `BluetoothLeManager`, centralizing the management of the Bluetooth connection lifecycle.
    *   Receives Bluetooth callbacks and forwards all events to the shared `MainViewModel`.

*   `MainViewModel.kt`: A shared `ViewModel` that serves as the "Single Source of Truth" for the application.
    *   Holds all shared data (`LiveData`) such as Bluetooth connection status, temperature/humidity data, medication list, and medication history.
    *   Contains core business logic, such as handling medication-taking events, calculating medication adherence, and storing/reading data.

*   `ReminderSettingsFragment.kt`: The Fragment for the "Reminder Settings" page.
    *   Responsible for all UI operations related to medication settings, including dynamically generating medication setting cards.
    *   Collects user input and uses `MainViewModel` to save new medications in the application.

*   `MedicationListFragment.kt`: The Fragment for the "Medication List" page.
    *   Observes the medication list data from `MainViewModel` and updates the list.

*   `HistoryFragment.kt`: The Fragment for the "Medication History" page.
    *   Observes the medication history data from `MainViewModel` and updates the calendar.
    *   Displays the medication adherence chart.

*   `EnvironmentFragment.kt`: The Fragment for the "Environment Monitoring" page.
    *   Observes the Bluetooth connection status and temperature/humidity data from `MainViewModel`.
    *   Displays a real-time temperature/humidity line chart or a "not connected" prompt based on the status.

*   `SettingsFragment.kt`: The Fragment for the "Settings" page.
    *   Provides application theme settings.

*   `ble/BluetoothLeManager.kt`: Encapsulates all low-level Bluetooth communication details.
    *   Responsible for scanning, connecting, sending commands, and receiving data.
    *   Parses the raw data received from the pillbox and passes it to `MainActivity` via a callback.

*   `AlarmScheduler.kt`: A helper class responsible for setting and canceling system alarms (`AlarmManager`).

*   `AlarmReceiver.kt`: A `BroadcastReceiver` that, when an alarm is triggered, is responsible for creating and displaying a "Time to take your medication!" notification with "Taken" and "Snooze" actions.

*   `SnoozeReceiver.kt`: A `BroadcastReceiver` that handles the "Snooze" action from a medication reminder notification, postponing the reminder for a short period.

*   `MedicationTakenReceiver.kt`: A `BroadcastReceiver` that handles the "Taken" action from a medication reminder notification, marking the medication as taken and updating the medication history.

*   `BootReceiver.kt`: A `BroadcastReceiver` that automatically reads all saved medication reminders and resets the alarms when the device is rebooted.

## Permissions Required

`POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## Bug Fixes
*   **0065:** Fixed `Apostrophe not preceded by \\` Linter errors in `strings.xml` by enclosing all strings containing an apostrophe in double quotes `""`.
*   **0064:** Fixed `String.format string doesn't match the XML format string` Linter errors in `strings.xml` and `values-en/strings.xml` by correcting improper escape characters (`\`) and single quote usage.
*   **0063:** Fixed UI issues with the toolbar and status bar:
    *   **Text Color:** Removed the hardcoded `colorOnPrimary` from the accent color themes in `themes.xml` to allow the system to automatically select the best text color based on the background color, resolving legibility issues with certain colors (like pink).
    *   **Immersive Mode:** Enabled true Edge-to-Edge by calling `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity.kt`, allowing the app content to draw behind the system bars and fixing the issue of the status bar not being completely filled.
*   **0062:** Fixed multiple UI and functionality bugs:
    *   **Compilation Error:** Resolved an `Android resource linking failed` error by setting `preferenceCategoryTitleTextColor` directly in `themes.xml`, which simplified the theme structure and avoided complex, error-prone styles.
    *   **UI Glitch on Back Navigation:** Fixed an issue where the immersive status bar effect would disappear upon returning from the settings page. This was fixed by explicitly calling `updateUiForFragment(false)` in the `onPause` method of `SettingsFragment.kt`, forcing `MainActivity` to redraw the UI.
    *   **Engineering Mode Sync:** Added logic to `onDeviceConnected` in `MainActivity.kt` to sync the "Engineering Mode" status to the pillbox upon successful Bluetooth connection, ensuring consistency between the app and the device.
*   **0061:** Fixed UI display issues and fixed the settings button.
    *   **Status Bar Issue:** By modifying `themes.xml` and `values-night/themes.xml` to set `statusBarColor` to transparent and enable `windowDrawsSystemBarBackgrounds`, an immersive (Edge-to-Edge) effect was achieved, solving the issues of the status bar background not being filled and the color being incorrect.
    *   **Settings Button Disabled:** The navigation logic for the Settings page (`SettingsFragment`) and Wi-Fi Settings page (`WiFiConfigFragment`) in the `onOptionsItemSelected` function of `MainActivity.kt` was uncommented, restoring the functionality of the settings button.
*   **0059:** Fixed status bar overlapping the toolbar title. This was solved by adding `android:fitsSystemWindows="true"` to the `AppBarLayout` in `activity_main.xml`, ensuring the toolbar correctly reserves space for the status bar.
*   **0058:** Fixed the issue where the toolbar color was incorrect when the accent color was set to "Default". This was resolved by reverting the `primary` and `colorPrimary` colors in `colors.xml` and `values-night/colors.xml` back to the Material Design defaults.
*   **0057:** Fixed a compilation error in `MainActivity.kt` caused by layout changes in `activity_main.xml`. Resolved the `Unresolved reference` issue by commenting out references to the removed components (`kuromiImage` and `fragment_container`), allowing the project to be rebuilt.
*   **0056:** Reverted bug fix 0055 to solve a resource linking failure in `AndroidManifest.xml`.
*   **0055:** Re-fixed an issue where an image was obscuring input fields. By listening to `ViewPager2` page change events in `MainActivity.kt`, it's ensured that the Kuromi image is only displayed on non-`ReminderSettingsFragment` pages.
*   **0054:** Fixed UI issues where the layout appeared strange on punch-hole displays and an image was obscuring the input fields. This was resolved by dynamically adjusting the image's visibility in `MainActivity.kt`, ensuring it is hidden when navigating to the `ReminderSettingsFragment`.
*   **0053:** Fixed an issue where the accent color was not correctly synchronized in dark mode. Ensured the Toolbar color changes dynamically with the theme by adding `colorPrimary` to all accent color themes in `values-night/themes.xml` and setting the `MaterialToolbar`'s background color to `?attr/colorPrimary` in `activity_main.xml`.
*   **0052:** Added a disconnect button to the reminder settings page and a new `ic_bluetooth_disabled.xml` icon to the `drawable` directory.
*   **0015:** Fixed an issue where the medication adherence rate was not updating and the time display on the medication history page was unclear. Implemented correct adherence calculation logic in `MedicationTakenReceiver` and `MainViewModel`, and corrected the text color in `fragment_history.xml` to ensure it is visible in the light theme.

## Recent Updates

*   **0060:** Added a character selection feature, allowing users to choose between 'Kuromi' and 'Chibi Maruko-chan'. The character image is now displayed at the bottom of the 'Reminder Settings' page.
*   **0049:** Cleaned up multiple warnings in the project, including deleting the unused `ThemeUtils.kt`, replacing `SharedPreferences.edit()` with KTX extension functions, and fixing an accessibility warning in `fragment_wifi_config.xml`.
*   **0047:** Added "Engineering Mode" and defined a new Bluetooth protocol (`0x13`) for the App to notify the pillbox about mode switching. This feature can be controlled via a switch on the settings page.
*   **0045:** Added back buttons to the settings and Wi-Fi configuration pages and centralized the back button logic in `MainActivity` for consistent and predictable UI.
*   **0044:** Added snooze and "taken" actions to medication reminder notifications, allowing users to interact with reminders directly from the notification. This is handled by the new `SnoozeReceiver` and `MedicationTakenReceiver`.
*   **0043:** Fixed the transparent background issue in `WiFiConfigFragment`, ensuring a clear and visible interface.
*   **0042:** Added a Wi-Fi configuration screen, allowing users to send Wi-Fi credentials (SSID and password) to the ESP32 via a new Bluetooth protocol (opcode 0x12). The SSID input field now includes a dropdown menu with previously entered SSIDs for convenience.
*   **0041:** Fixed accessibility warnings by adding a `contentDescription` to the Kuromi image and replacing "..." with the standard ellipsis character (`…`).
*   **0040:** Fixed a resource linking error by correcting the `textViewStyle` attribute in `themes.xml` and added a decorative Kuromi image to the main screen.
*   **0039:** Fixed several deprecation warnings in `MainActivity.kt`. Updated the handling of the back button to use the new `OnBackPressedDispatcher` and modernized the locale/language setting logic to use the `AppCompatDelegate.setApplicationLocales` API, removing all related deprecated methods.
*   **0038:** Added a back arrow to the settings page (`SettingsFragment`). This allows users to easily navigate back to the previous screen from the settings menu.
*   **0037:** Fixed a UI display issue on the settings page (`SettingsFragment`). The background of the settings menu was previously transparent, causing text to overlap with the UI elements underneath. The issue has been resolved by programmatically setting a background color that respects the current application theme (light/dark), ensuring clear visibility.
*   **0036:** Fixed an issue where the settings icon was not visible in light mode and merged "Theme Settings" and "Language Settings" into an "Appearance" category on the settings page to simplify the interface.
*   **0035:** Added a language switching feature to the settings page, allowing users to manually switch the app\'s display language (Traditional Chinese, English, or follow the system).
*   **0034:** Added English localization to the application to support English-speaking users.
*   **0033:** Implemented historical temperature and humidity data synchronization. Extended the Bluetooth protocol to allow the app to sync all historical temperature and humidity data recorded during the offline period from the pillbox upon connection and display it completely on the chart.
*   **0032:** Fixed multiple compilation errors and warnings in the project, including a `SwipeRefreshLayout` dependency issue, an error in `MainActivity.kt`, and cleaned up unused code.
*   **0031:** Cleaned up all duplicate and empty files in the `app/src/main/java/com/example/medicationreminderapp/ui/` directory.
*   **0030:** Implemented the "App actively requests temperature/humidity data" feature in the Bluetooth protocol and provided a UI for users to trigger this action.
*   **0029:** Fixed multiple build errors and warnings in `app/build.gradle.kts`. Handled incorrect string quotes for `buildConfigField` and replaced the deprecated `exec` method with the more modern `ProcessBuilder` to ensure the stability of the Gradle build script.
*   **0028:** Fixed a build failure caused by removing the seemingly unused `requestStatus()` and `syncTime()` methods from `BluetoothLeManager`. These two methods have been re-added to ensure `MainActivity` can call them normally.
*   **0027:** Removed the unused `sendJson` method from `BluetoothLeManager.kt`, further cleaning up the Bluetooth communication code.
*   **0026:** Cleaned up multiple "unused declaration" warnings in the project, including removing old methods in the Bluetooth module replaced by JSON commands, removing unused properties in `HistoryFragment.kt`, and clearing the content of duplicate and useless files in the `ui` package, significantly improving code quality.
*   **0025:** Removed the unused `frequency` field and its related string resource from the `Medication` data class, making the code more concise.
*   **0024:** Fixed multiple warnings in the IDE, including adding accessibility descriptions to image resources, moving hardcoded strings to resource files, and cleaning up unused imports and parameters in Kotlin files, improving code quality and maintainability.
*   **0023:** Added a visual indicator feature to the medication history page. Now, a green dot is displayed below the corresponding date on the calendar when all medications for that day have been taken on time, allowing users to track their medication status more intuitively.
*   **0022:** Simplified the version number setting and displayed a hint message when the medication list is empty. Removed the complex Git version control in `app/build.gradle.kts` and now read version information directly from `config.gradle.kts`. Also, updated the medication list page to display "No reminders" text when there are no reminders, improving the user experience.
*   **0021:** Cleaned up warnings in `SettingsFragment.kt`, removing unused `import`s and replacing unnecessary safe calls with safer `let` blocks.
*   **0020:** Restored missing settings features, including the settings icon, theme switching, and accent color adjustment. Users can now access the settings page from the toolbar and customize the app's appearance again.
*   **0019:** Resolved accessibility warnings in `fragment_reminder_settings.xml` and cleaned up unused parameters in `MainViewModel.kt`.
*   **0018:** Resolved XML resource parsing errors and multiple unused code warnings in Kotlin files that appeared in the IDE, and ensured project state stability through a Gradle sync.
*   **0017:** Resolved XML resource parsing errors and multiple unused code warnings in Kotlin files that appeared in the IDE, and ensured project state stability through a Gradle sync.
*   **0016:** Restored the ability to edit and delete medication reminders and reintegrated the alarm scheduling function. Now, alarms are not only enabled when set but are also automatically reset after the device reboots.
*   **0015:** Fixed a UI issue where the medication list would not update immediately after adding a new medication. The UI update is now correctly triggered by ensuring that `LiveData` receives a new list instance instead of just modifying the existing one.
*   **0014:** Handled outdated warnings in the IDE regarding `MedicationListAdapter.kt` and `ReminderSettingsFragment.kt` and refreshed the project state with a Gradle sync.
*   **0013:** Cleaned up all warnings in the project, including unused imports, parameters, and namespace declarations, and fixed string resource conflicts.
*   **0012:** Cleaned up all warnings in the project, including unused imports, parameters, and namespace declarations, and fixed string resource conflicts.
*   **0011:** Fixed a resource linking error caused by a missing `androidx.preference:preference-ktx` dependency.
*   **0.010:** Added a settings page and a medication list page, and restored the theme setting function.
*   **0009:** Optimized the validation of the medication reminder form to provide clearer error messages.
*   **0008:** Fixed a critical build error caused by an incomplete Gradle version directory.
