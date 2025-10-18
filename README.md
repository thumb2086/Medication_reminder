# 智能藥盒提醒 App

這是一款 Android 應用程式，旨在幫助使用者管理他們的藥物服用排程，並透過藍牙低功耗 (BLE) 技術與智能藥盒互動。

## 主要功能

*   **藥物排程管理:**
    *   新增、刪除、修改藥物提醒。
    *   設定藥物名稱、劑量、服用頻率。
    *   自訂服藥的開始與結束日期。
    *   設定一天中不同時段 (早、中、晚、睡前) 的服藥時間。
*   **藍牙智能藥盒整合:**
    *   掃描並連接到智能藥盒。
    *   與藥盒同步時間及用藥提醒。
    *   接收藥盒的狀態更新，例如：藥物已取出、藥倉堵塞、感應器錯誤等。
    *   顯示藥盒回傳的即時溫濕度數據。
*   **服藥依從性追蹤:**
    *   透過月曆視覺化呈現每日服藥記錄。
    *   計算並顯示過去 30 天的服藥依從率。
*   **通知與提醒:**
    *   在指定的服藥時間發送本地通知。
    *   當藥物庫存過低時，發出警告提醒。
*   **個人化設定:**
    *   提供亮色、暗色及跟隨系統設定的主題模式，讓使用者可以依據偏好調整介面。

## 如何開始

1.  **複製專案:**
    ```bash
    git clone https://github.com/your-username/MedicationReminderApp.git
    ```
2.  **開啟專案:**
    *   在 Android Studio 中，選擇 "Open an Existing Project"。
    *   選擇您剛剛複製的專案目錄。
3.  **建置專案:**
    *   Android Studio 會自動執行 Gradle 同步。等待同步完成後，即可建置並執行 App。

## 使用說明

1.  **新增藥物:**
    *   在主畫面點擊「新增藥物」。
    *   填寫藥物名稱、劑量、總藥量、服用頻率、起訖日期與提醒時間。
    *   選擇一個空的藥倉來存放此藥物。
2.  **連接藥盒:**
    *   點擊主畫面的「連接藥盒」按鈕。
    *   App 會開始掃描附近的藍牙裝置。
    *   從裝置列表中選擇您的智能藥盒進行配對。
3.  **查看排程與記錄:**
    *   主畫面的列表會顯示所有已設定的藥物提醒。
    *   月曆會以圓點標示出當天是否已完成所有服藥。
4.  **刪除藥物:**
    *   長按「顯示所有藥物」按鈕，會出現藥物列表。
    *   點擊您想刪除的藥物，並在確認對話框中進行確認。

## 專案結構

*   `app/src/main/java/com/example/medicationreminderapp/`:
    *   `MainActivity.kt`: App 的主要活動，包含大部分的 UI 邏輯、使用者互動及主題設定。
    *   `BluetoothLeManager.kt`: 處理所有藍牙低功耗相關的操作，包括掃描、連接、通訊等。
    *   `AlarmReceiver.kt`: 接收系統鬧鐘事件，並觸發藥物提醒通知。
    *   `Medication.kt`: 定義藥物資料結構的 data class。
*   `app/src/main/res/`:
    *   `layout/activity_main.xml`: 主畫面的 UI 佈局。
    *   `values/strings.xml`: App 中使用的所有字串資源。
    *   `style/Themes.xml`: 定義 App 的主題與樣式。

## 外部相依套件

*   **[Kizitonwose CalendarView](https://github.com/kizitonwose/CalendarView):** 用於實現高度客製化的月曆介面。
*   **[Google Gson](https://github.com/google/gson):** 用於將物件序列化為 JSON，以及將 JSON 反序列化回物件。
*   **AndroidX Libraries:**
    *   `appcompat`: 提供對舊版 Android 的 UI 元件支援。
    *   `core-ktx`: 提供 Kotlin 的擴充功能。
    *   `constraintlayout`: 用於建立複雜的 UI 佈局。
*   **Material Components for Android:** 提供 Material Design 風格的 UI 元件。

## 權限需求

本應用程式需要在 `AndroidManifest.xml` 中宣告以下權限：

*   `POST_NOTIFICATIONS`: (Android 13+) 用於發送藥物提醒通知。
*   `BLUETOOTH_SCAN`: (Android 12+) 用於掃描附近的藍牙裝置。
*   `BLUETOOTH_CONNECT`: (Android 12+) 用於與已配對的藍牙裝置建立連線。
*   `ACCESS_FINE_LOCATION`: (Android 11 及以下) 掃描藍牙裝置時需要。
*   `SCHEDULE_EXACT_ALARM`: (Android 12+) 用於設定精確的鬧鐘，以準時發出提醒。
