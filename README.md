# 智能藥盒提醒 App

這是一款 Android 應用程式，旨在幫助使用者管理他們的藥物服用排程，並透過藍牙低功耗 (BLE) 技術與智能藥盒互動。

## 設計理念 (V4)

從 V4 版本開始，我們採用了以「藥倉 (Slot)」為中心的全新設計理念，以優化與 ESP32 硬體的通訊效率和穩定性。

*   **核心思想轉變：** App 不再告訴 ESP32「請為『維他命C』設定提醒」，而是告訴它：「請為第 3 號藥倉設定一個早上 8:30 的提醒。」
*   **職責分離：**
    *   **ESP32 (智能藥盒):** 作為一個忠實的「時間執行者」，只負責在正確的時間，為指定的藥倉（共 8 個）觸發提醒（如亮燈或發出聲音）。它完全不需要知道藥倉裡裝的是什麼藥。
    *   **Android App:** 作為「大腦」，負責管理哪個藥倉對應哪種藥、藥物庫存、提醒時間等所有複雜 lógica。

這個轉變極大地簡化了 ESP32 的韌體邏輯，使其更穩定、更省電，並將複雜的管理任務交由功能更強大的手機 App 處理。

## 主要功能

*   **藥物排程管理:**
    *   新增、刪除藥物提醒。
    *   透過表單中的下拉式選單，為每種藥物指派到 8 個獨立的藥倉之一。
    *   設定藥物名稱、劑量、總藥量、服用頻率。
    *   自訂服藥的開始與結束日期。
    *   設定一天中不同時段 (早、中、晚、睡前) 的服藥時間。
    *   防止使用者將多種藥物指派到同一個藥倉。
*   **藍牙智能藥盒整合:**
    *   掃描並連接到智能藥盒。
    *   與藥盒同步時間及各藥倉的用藥提醒。
    *   接收藥盒的狀態更新，例如：哪個藥倉的藥物已被取出、藥倉堵塞、感應器錯誤等。
    *   顯示藥盒回傳的即時溫濕度數據。
*   **服藥依從性追蹤:**
    *   透過月曆視覺化呈現每日服藥記錄。
    *   計算並顯示過去 30 天的服藥依從率。
*   **通知與提醒:**
    *   在指定的服藥時間發送本地通知。
    *   當 App 計算出藥物庫存過低時，發出警告提醒。
*   **個人化設定:**
    *   提供亮色、暗色及跟隨系統設定的主題模式，讓使用者可以依據偏好調整介面。

## 通訊協定 (Protocol V4 - Slot-Centric)

App 與 ESP32 之間的通訊協定也進行了相應的簡化。

### App -> ESP32 (命令)

| 命令 ID | 名稱 | 格式 (Bytes) | 說明 |
|---|---|---|---|
| `0x10` | 設定單一藥倉提醒 | `0x10, slotNumber, hour, minute` | 為指定的藥倉 (1-8) 設定一個提醒時間。 |
| `0x11` | 同步時間 | `0x11, year, month, day, hour, min, sec` | (不變) 同步 ESP32 的 RTC 時間。 |
| `0x12` | 取消所有提醒 | `0x12` | 清空 ESP32 上所有的提醒設定。 |
| `0x13` | 取消單一藥倉提醒 | `0x13, slotNumber` | **【新增】** 只取消指定藥倉 (1-8) 的提醒。 |
| `0x20` | 請求狀態 | `0x20` | (不變) 請求回報溫濕度和藥倉狀態。 |

### ESP32 -> App (事件)

| 事件 ID | 名稱 | 格式 (Bytes) | 說明 |
|---|---|---|---|
| `0x81` | 藥物已被取出 | `0x81, slotNumber` | **【簡化】** ESP32 只需回報哪個藥倉被操作了。庫存計算完全交給 App。 |
| `0x82` | 時間同步確認 | `0x82` | (不變) |
| `0x90` | 溫濕度數據 | `0x90, temp_int, temp_dec, hum_int, hum_dec` | (不變) |
| `0xEE` | 錯誤回報 | `0xEE, errorCode` | (不變) |


## 如何開始

1.  **複製專案:**
    ```bash
    git clone https://github.com/your-username/MedicationReminderApp.git
    ```
2.  **開啟專案:**
    *   在 Android Studio 中，選擇 \"Open an Existing Project\"。
    *   選擇您剛剛複製的專案目錄。
3.  **建置專案:**
    *   Android Studio 會自動執行 Gradle 同步。等待同步完成後，即可建置並執行 App。
    *   **注意:** 本專案已將 Java 和 Kotlin 的編譯版本升級至 11。

## 使用說明

1.  **新增藥物:**
    *   在「提醒」分頁的表單中，填寫藥物名稱、劑量、總藥量、服用頻率、起訖日期與提醒時間。
    *   **【重要】** 從「藥倉編號」的下拉式選單中，為此藥物選擇一個尚未被佔用的藥倉。
2.  **連接藥盒:**
    *   點擊「提醒」分頁的「連接藥盒」按鈕。
    *   App 會開始掃描附近的藍牙裝置。
    *   從裝置列表中選擇您的智能藥盒進行配對。連接成功後，App 會自動同步所有提醒。
3.  **查看排程與記錄:**
    *   「提醒」分頁的列表會顯示所有已設定的藥物提醒，包含其所在的藥倉編號。
    *   「日曆」分頁會以圓點標示出當天是否已完成所有服藥。
4.  **刪除藥物:**
    *   長按「提醒」分頁的「顯示所有藥物」按鈕，會出現藥物列表。
    *   點擊您想刪除的藥物，並在確認對話框中進行確認。刪除後，對應藥倉的提醒也會被取消。
5.  **主題設定:**
    *   點擊右上角的齒輪圖示，可以開啟主題選擇對話框。
    *   您可以選擇亮色、暗色或跟隨系統設定。

## 專案結構

本專案採用單一 `Activity` 和多 `Fragment` 的現代 Android App 架構。

*   `app/src/main/java/com/example/medicationreminderapp/`:
    *   `MainActivity.kt`: App 的主要 `Activity`，負責管理 `Fragment` 的切換和藍牙連線。
    *   `ui/`:
        *   `ReminderFragment.kt`: 提醒設定頁面的 `Fragment`，包含新增、刪除、顯示藥物提醒的 UI 邏輯。
        *   `CalendarFragment.kt`: 日曆頁面的 `Fragment`，用於顯示服藥紀錄。
        *   `EnvironmentFragment.kt`: 環境監測頁面的 `Fragment`，顯示來自藥盒的溫濕度數據。
        *   `MainViewModel.kt`: `ViewModel`，用於在 `Fragment` 之間共享數據。
    *   `ble/`:
        *   `BluetoothLeManager.kt`: 處理所有藍牙低功耗相關的操作。
    *   `notification/`:
        *   `AlarmReceiver.kt`: 接收系統鬧鐘事件，並觸發藥物提醒通知。
*   `app/src/main/res/`:
    *   `layout/`:
        *   `activity_main.xml`: 主 `Activity` 的 UI 佈局，包含 `BottomNavigationView`。
        *   `fragment_reminder.xml`: 提醒設定頁面的 UI 佈局。
        *   `fragment_calendar.xml`: 日曆頁面的 UI 佈局。
        *   `fragment_environment.xml`: 環境監測頁面的 UI 佈局。
    *   `values/`:
        *   `colors.xml`: 定義亮色主題的顏色。
        *   `themes.xml`: 定義 App 的主題與樣式。
    *   `values-night/`:
        *   `colors.xml`: 定義暗色主題的顏色。
        *   `themes.xml`: 定義暗色模式下的 App 主題與樣式。
    *   `values/strings.xml`: App 中使用的所有字串資源。

## 外部相依套件

*   **[Kizitonwose CalendarView](https://github.com/kizitonwose/CalendarView):** 用於實現高度客製化的月曆介面。
*   **[Google Gson](https://github.com/google/gson):** 用於將物件序列化為 JSON，以及將 JSON 反序列化回物件。
*   **AndroidX Libraries:**
    *   `appcompat`: 提供對舊版 Android 的 UI 元件支援。
    *   `core-ktx`: 提供 Kotlin 的擴充功能。
    *   `constraintlayout`: 用於建立複雜的 UI 佈局。
    *   `fragment-ktx`: 提供 `Fragment` 的 Kotlin 擴充功能。
    *   `lifecycle-viewmodel-ktx`: 提供 `ViewModel` 的 Kotlin 擴充功能。
*   **Material Components for Android:** 提供 Material Design 風格的 UI 元件。

## 權限需求

本應用程式需要在 `AndroidManifest.xml` 中宣告以下權限：

*   `POST_NOTIFICATIONS`: (Android 13+) 用於發送藥物提醒通知。
*   `BLUETOOTH_SCAN`: (Android 12+) 用於掃描附近的藍牙裝置。
*   `BLUETOOTH_CONNECT`: (Android 12+) 用於與已配對的藍牙裝置建立連線。
*   `ACCESS_FINE_LOCATION`: (Android 11 及以下) 掃描藍牙裝置時需要。
*   `SCHEDULE_EXACT_ALARM`: (Android 12+) 用於設定精確的鬧鐘，以準時發出提醒。
