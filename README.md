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

## Instructions for Use

(Instructions are identical to the previous version and are omitted here for brevity.)

## Bluetooth Protocol

To enable interaction between the app and the pillbox, we have defined a bidirectional communication protocol based on byte arrays. The `BluetoothLeManager` class is responsible for encapsulating commands into byte arrays and sending them, as well as parsing the data received from the pillbox.

### Service and Characteristics UUIDs

- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Write Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8` (App -> Pillbox)
- **Notify Characteristic UUID:** `c8c7c599-809c-43a5-b825-1038aa349e5d` (Pillbox -> App)

### App -> Pillbox (Commands)

1.  **Time Sync (0x11):** `[0]: 0x11`, `[1]: Year-2000`, `[2]: Month`, `[3]: Day`, `[4]: Hour`, `[5]: Minute`, `[6]: Second`
2.  **Send Wi-Fi Credentials (0x12):** `[0]: 0x12`, `[1]: SSID_Len`, `[2...]: SSID`, `[...]: Pass_Len`, `[...]: Password`
3.  **Set Engineering Mode (0x13):** `[0]: 0x13`, `[1]: Enable (0x01/0x00)` - Commands the pillbox to enter or exit engineering mode.
4.  **Request Engineering Mode Status (0x14):** `[0]: 0x14` - Asks the pillbox for its current engineering mode status.
5.  **Request Status (0x20):** `[0]: 0x20` - Asks for the pillbox's general status.
6.  **Request Instant Environment Data (0x30):** `[0]: 0x30`
7.  **Request Historic Environment Data (0x31):** `[0]: 0x31`

### Pillbox -> App (Notifications)

1.  **Box Status Update (0x80):** `[0]: 0x80`, `[1]: Slot Mask`
2.  **Medication Taken Report (0x81):** `[0]: 0x81`, `[1]: Slot Number`
3.  **Time Sync Acknowledged (0x82):** `[0]: 0x82`
4.  **Engineering Mode Status Report (0x83):** `[0]: 0x83`, `[1]: Status (0x01 for enabled)` - Reports the pillbox's current engineering mode.
5.  **Instant Sensor Data Report (0x90):** `[0]: 0x90`, `[1-2]: Temp`, `[3-4]: Hum` (Little Endian, value*100)
6.  **Historic Sensor Data Batch (0x91):** `[0]: 0x91`, `[1...]: 1 to 5 history records`
    - ***Note:*** *This protocol change requires a corresponding firmware update on the ESP32.*
7.  **End of Historic Data Transmission (0x92):** `[0]: 0x92`
8.  **Error Report (0xEE):** `[0]: 0xEE`, `[1]: Error Code`

## Project Structure

(Project structure description is identical to the previous version and is omitted here.)

## Permissions Required

`POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## Recent Updates
*   **0086:** Implemented bidirectional synchronization for the engineering mode state between the App and the pillbox.
    *   **Protocol Extension:** Added opcodes `0x14` (Request Engineering Mode Status) and `0x83` (Report Engineering Mode Status).
    *   **Logic Optimization:** The App now actively requests the pillbox's current engineering mode status upon connection, rather than unidirectionally overwriting it.
    *   **UI Synchronization:** The "Engineering Mode" switch in the settings screen now accurately reflects the pillbox's true state. User interactions with the switch send a command, and the UI is updated only upon receiving confirmation from the pillbox, ensuring eventual consistency.
*   **0085:** Optimized the Bluetooth protocol and app's data handling logic.
    *   **Protocol Optimization:** Changed the format for historical data reporting (`0x91`) from single-record updates to batch processing, allowing up to five records to be transmitted at once, significantly improving data sync efficiency.
    *   **Code Modification:** Updated the `handleIncomingData` function in `BluetoothLeManager.kt` to parse the new batch data format.
    *   **Documentation Update:** Synchronized the Bluetooth protocol documentation in `README.md` and `README_cn.md`.
    *   **Important:** This protocol change requires a corresponding firmware update on the ESP32.
*   **0084:** Adjusted the synchronization timing for historical temperature and humidity data.
    *   **Logic Change**: Removed the logic in `EnvironmentFragment` that automatically requested historical data (`0x31`) upon successful Bluetooth connection.
    *   **User Experience Improvement**: Now, historical data synchronization is only triggered when the user performs a pull-to-refresh gesture on the "Environment Monitoring" page. This avoids unnecessary automatic data transfers and gives the user greater control.
*   **0083:** Optimized the Bluetooth data protocol between the App and the ESP32.
    *   **Protocol Change**: Changed the transmission method for temperature and humidity data from a split integer/fractional approach to a more efficient, standard 2-byte signed integer (value multiplied by 100). This modification applies to both real-time (`0x90`) and historical (`0x91`) data parsing, improving data processing stability and efficiency.
*   **0082:** Fixed the vertical alignment of the toolbar title.
    *   **UI Fix**: Successfully moved the title `TextView` down by adding a `paddingTop` attribute in `activity_main.xml`, visually aligning it with the settings icon on the right.
*   **0081:** Fixed a display issue with the toolbar title.
    *   **Problem Analysis**: Setting `padding` directly on the title `TextView` in `activity_main.xml` caused a height calculation error, leading to the content being cut off.
    *   **UI Fix**: Changed `padding` to `layout_marginBottom` to adjust the title's vertical position without affecting the `TextView`'s height, making it appear more visually centered.
*   **0080:** Fixed a critical bug caused by title centering and restored the original title position.
    *   **Problem Analysis**: It was found that in version `0078`, `supportActionBar?.setDisplayShowTitleEnabled(false)` was added to center the title, but this unexpectedly caused `SettingsFragment` and `WiFiConfigFragment` to not display correctly.
    *   **Emergency Fix**: Reverted the related changes in `MainActivity.kt` and `activity_main.xml` to restore normal page display.
    *   **UI Fix**: Modified the `updateUiForFragment` function in `MainActivity.kt` to ensure the back button only appears when navigating to a deeper page, resolving the issue of the back button appearing on the main screen.
*   **0079:** Cleaned up multiple warnings in `MainActivity.kt`, including unused `import`s and unused function parameters.
*   **0078:** Fixed the issue of the toolbar title not being centered.
    *   **Strategy Adjustment**: Removed the `app:title` attribute from `MaterialToolbar` and instead added a `TextView` within `MaterialToolbar` with `android:layout_gravity` set to `center` to achieve title centering.
    *   **Code Cleanup**: Added `supportActionBar?.setDisplayShowTitleEnabled(false)` in `MainActivity.kt` to hide the default title and avoid overlap with the custom title.
*   **0077:** Fixed the issue of the back button color being incorrect on the settings page.
    *   In the `Widget.App.Toolbar` style in `themes.xml`, added the `colorControlNormal` attribute and set it to `?attr/colorOnPrimary` to ensure the back button's color is consistent with other icons and text on the toolbar.
*   **0076:** Thoroughly resolved the issue of incorrect text and icon colors in the toolbar under character themes.
    *   **Strategy Adjustment**: Abandoned the complex approach of dynamically setting status bar colors in `MainActivity.kt` and instead applied a separate theme named `Widget.App.Toolbar` directly to `MaterialToolbar` in `activity_main.xml`.
    *   **Theme Fix**: Defined the `Widget.App.Toolbar` style in `themes.xml` and set its `titleTextColor` and `actionMenuTextColor` to `?attr/colorOnPrimary`, allowing it to correctly inherit the `colorOnPrimary` defined in the character themes (black for light themes, white for dark themes), ensuring that text and icons on the toolbar are clearly visible in all situations.
*   **0075:** Re-fixed the issue of incorrect text and icon colors in the toolbar, ensuring they are clearly visible in all themes.
    *   **Light Theme**: Explicitly set `android:titleTextColor` and `android:actionMenuTextColor` to black for all character themes in `values/themes.xml` to completely resolve the issue of incorrect text color on a light background.
    *   **Dark Theme**: Also explicitly set `android:titleTextColor` and `android:actionMenuTextColor` to white for all character themes in `values-night/themes.xml` to ensure good contrast in dark mode.
*   **0074:** Fixed the issue of incorrect text and icon colors on the toolbar and status bar in light themes (e.g., character themes), and corrected the calendar text color in dark mode.
    *   **Light Theme**: Explicitly set `colorOnPrimary` to black for all character themes in `themes.xml` to ensure toolbar text is clearly visible on a light background.
    *   **Dark Theme**: Modified `fragment_history.xml` to change the calendar's text color to `?android:attr/textColorPrimary`, allowing it to adjust automatically with the theme.
    *   **Status Bar Icons**: Added logic to `MainActivity.kt` to dynamically determine the theme color's brightness and use `WindowInsetsControllerCompat` to set the status bar icon colors, ensuring good contrast in any theme.
*   **0072:** Completely redesigned the app's UI and resolved the resulting `Android resource linking failed` build error.
    *   **Theme and Color:** Simplified `themes.xml` and `colors.xml`, removing all accent color themes and defining a clearer, higher-contrast color set to unify the app's visual style.
    *   **Code Cleanup:** Removed unused code related to the old accent color themes from `MainActivity.kt` and `SettingsFragment.kt`.
    *   **Resource Fix:** Corrected an issue in `drawable/ic_slot_filled.xml` that referenced a deleted color, ensuring the project builds successfully.
*   **0071:** Fixed an issue where the text and icon colors on the Toolbar and status bar were incorrect in light themes (e.g., the Chibi Maruko-chan pink theme).
    *   By explicitly specifying `colorOnPrimary` as black in the theme and dynamically adjusting the status bar icons' light/dark appearance using `WindowInsetsController`, this ensures that all text and system icons have good contrast and readability against any light-colored background.
*   **0070:** Fixed an immersive UI issue on devices with display cutouts where the status bar background would not fill correctly.
    *   By enabling `windowLayoutInDisplayCutoutMode` in the app's theme and precisely applying the system insets padding only to the `AppBarLayout`, this completely resolves the issue of the status bar background not extending on devices with cutouts or notches, achieving a perfectly consistent immersive experience across all screen types.
*   **0069:** Integrated the accent color with the character selection feature and resolved the immersive status bar display issue.
    *   **Feature Integration**: In the settings page, the app's theme color is now bound to the a character selection. Choosing "Kuromi" sets the theme color to purple, while choosing "Chibi Maruko-chan" sets it to pink, simplifying the settings and adding an element of fun.
    *   **Immersive UI Fix**: By adjusting the layout in `activity_main.xml` and modifying the `WindowInsetsListener` in `MainActivity.kt`, the issue of the App Bar not filling the status bar has been resolved, achieving a true Edge-to-Edge immersive experience.
*   **0068:** Completely redesigned the app's UI and resolved the resulting `Android resource linking failed` build error.
    *   **Theme and Color:** Simplified `themes.xml` and `colors.xml`, removing all accent color themes and defining a clearer, higher-contrast color set to unify the app's visual style.
    *   **Code Cleanup:** Removed unused code related to the old accent color themes from `MainActivity.kt` and `SettingsFragment.kt`.
    *   **Resource Fix:** Corrected an issue in `drawable/ic_slot_filled.xml` that referenced a deleted color, ensuring the project builds successfully.
*   **0067:** Fixed an `Unresolved reference: WindowInsetsCompat` compilation error in `MainActivity.kt` by adding the missing import.
*   **0066:** Fixed the toolbar and status bar UI issues again:
    *   **Text Color:** Explicitly set a contrasting `colorOnPrimary` for each accent color theme in `themes.xml` to ensure toolbar text and icons are always legible.
    *   **Immersive Mode:** Implemented a `OnApplyWindowInsetsListener` for the `AppBarLayout` in `MainActivity.kt` to correctly handle system insets and apply proper padding, finally achieving a true Edge-to-Edge effect.
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
*   **0032:** Fixed multiple compilation errors and warnings in the.project, including a `SwipeRefreshLayout` dependency issue, an error in `MainActivity.kt`, and cleaned up unused code.
*   **0031:** Cleaned up all duplicate and empty files in the `app/src/main/java/com/example/medicationreminderapp/ui/` directory.
*   **0030:** Implemented the "App actively requests temperature/humidity data" feature in the Bluetooth protocol and provided a UI for users to trigger this action.
*   **0029:** Fixed multiple build errors and warnings in `app/build.gradle.kts`. Handled incorrect string quotes for `buildConfigField` and replaced the deprecated `exec` method with the more modern `ProcessBuilder` to ensure the stability of the Gradle build script.
*   **0-028:** Fixed a build failure caused by removing the seemingly unused `requestStatus()` and `syncTime()` methods from `BluetoothLeManager`. These two methods have been re-added to ensure `MainActivity` can call them normally.
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
