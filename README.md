# 智能藥盒提醒 App

這是一款 Android 應用程式，旨在幫助使用者管理他們的藥物服用排程，並透過藍牙低功耗 (BLE) 技術與智能藥盒互動。

## 設計理念

本專案的核心設計是將複雜的邏輯處理保留在 Android App 中，讓智能藥盒 (ESP32) 的職責盡可能單純，只作為一個忠實的時間與指令執行者。App 透過以「藥倉 (Slot)」為中心的通訊模式，告知藥盒「在幾點鐘對第幾號藥倉進行提醒」，而藥盒無需知道裡面裝的是什麼藥物。這樣的設計大大簡化了硬體端的韌體開發，提高了系統的穩定性與省電效率。

## 主要功能

*   **分頁式介面:**
    *   採用 `TabLayout` 和 `ViewPager2` 打造現代化的分頁式介面，分為「提醒設定」、「服藥紀錄」和「環境監測」三大功能區塊，操作更直覺。
*   **藥物排程管理:**
    *   提供獨立的 **新增、編輯、刪除** 按鈕，操作流程清晰。
    *   藥倉總數為 8 格，可新增的藥物數量會根據 **已被佔用的藥倉數量動態調整**。
    *   新增多筆藥物時，系統會 **自動指派** 下一個可用的藥倉編號，簡化輸入流程。
    *   設定藥物名稱、劑量、總藥量、服用頻率。
    *   自訂服藥的開始與結束日期。
    *   設定一天中不同時段 (早、中、晚、睡前) 的服藥時間。
    *   智慧檢查機制，防止使用者將多種藥物指派到同一個已被佔用的藥倉。
*   **藍牙智能藥盒整合:**
    *   掃描並連接到智能藥盒。
    *   與藥盒同步時間及各藥倉的用藥提醒。
    *   接收藥盒的狀態更新，例如：哪個藥倉的藥物已被取出、藥倉堵塞、感應器錯誤等。
*   **數據監測與追蹤:**
    *   在「環境監測」頁面，透過 **即時折線圖** 視覺化呈現藥盒回傳的溫濕度數據。
    *   在「服藥紀錄」頁面，透過月曆視覺化呈現每日服藥記錄。
    *   計算並顯示過去 30 天的服藥依從率。
*   **通知與提醒:**
    *   在指定的服藥時間發送本地通知。
    *   當 App 計算出藥物庫存過低時，發出警告提醒。
*   **個人化設定:**
    *   提供亮色、暗色及跟隨系統設定的主題模式。

## 使用說明

1.  **新增藥物:**
    *   在「提醒設定」分頁的表單中，從下拉選單選擇您要一次新增的藥物數量。此數量會根據 **剩餘的藥倉數** 動態提供。
    *   系統會自動為您產生對應數量的藥物輸入欄位，並預先選擇可用的藥倉編號。
    *   填寫藥物名稱、劑量、總藥量、服用頻率、起訖日期與提醒時間後，點擊「新增藥物提醒」。
2.  **編輯或刪除藥物:**
    *   點擊「提醒設定」分頁下方的 **「編輯」** 或 **「刪除」** 按鈕。
    *   App 會彈出一個包含所有已設定藥物的列表。
    *   從列表中選擇您想操作的藥物。
    *   **編輯:** 選擇後，該藥物的資訊會自動填入上方的表單中（預設顯示一筆），同時按鈕會變為 **「更新藥物」**。方便您修改後直接點擊更新。
    *   **刪除:** 選擇後，在跳出的確認對話框中進行確認即可刪除。
3.  **查看藥物:**
    *   點擊「顯示所有藥物」按鈕可展開所有藥物的詳細資訊。此列表會在您新增或刪除藥物後 **自動更新**。
4.  **連接藥盒:**
    *   點擊「提醒設定」分頁的「連接藥盒」按鈕。
    *   App 會開始掃描並讓您選擇您的智能藥盒進行配對。連接成功後，App 會自動同步所有提醒。
5.  **查看記錄與數據:**
    *   切換到「服藥紀錄」分頁，查看您的服藥月曆與依從率。
    *   切換到「環境監測」分頁，查看即時的溫濕度變化圖表。

## 專案結構

本專案採用單一 `Activity` 和多 `Fragment` 的現代 Android App 架構。

*   `app/src/main/java/com/example/medicationreminderapp/`:
    *   `MainActivity.kt`: App 的主要 `Activity`，負責管理 `ViewPager2`、`TabLayout` 及藍牙連線。
    *   `ui/`:
        *   `ReminderFragment.kt`: **提醒設定**頁面的 `Fragment`，包含新增、編輯、刪除藥物提醒的 UI 邏輯。
        *   `LogFragment.kt`: **服藥紀錄**頁面的 `Fragment`，用於顯示服藥月曆與依從率。
        *   `EnvironmentFragment.kt`: **環境監測**頁面的 `Fragment`，顯示來自藥盒的溫濕度數據折線圖。
        *   `MainViewModel.kt`: `ViewModel`，用於在 `Fragment` 之間共享數據，如藍牙狀態、藥物列表等。
        *   `ViewPagerAdapter.kt`: 用於管理三個主要 `Fragment` 的轉接器。
    *   `ble/`:
        *   `BluetoothLeManager.kt`: 處理所有藍牙低功耗相關的操作。
    *   `notification/`:
        *   `AlarmReceiver.kt`: 接收系統鬧鐘事件，並觸發藥物提醒通知。
*   `app/src/main/res/`:
    *   `layout/`:
        *   `activity_main.xml`: 主 `Activity` 的 UI 佈局，包含 `Toolbar`、`TabLayout` 和 `ViewPager2`。
        *   `fragment_reminder.xml`: 提醒設定頁面的 UI 佈局。
        *   `fragment_log.xml`: 服藥紀錄頁面的 UI 佈局。
        *   `fragment_environment.xml`: 環境監測頁面的 UI 佈局。
    *   `values/themes.xml`: 定義 App 的主題與樣式 (使用 `NoActionBar` 版本以支援自訂 `Toolbar`)。

## 外部相依套件

*   **[Kizitonwose CalendarView](https://github.com/kizitonwose/CalendarView):** 用於實現高度客製化的月曆介面。
*   **[MPAndroidChart](https://github.com/PhilJay/MPAndroidChart):** 用於繪製即時溫濕度折線圖。
*   **[Google Gson](https://github.com/google/gson):** 用於將物件序列化為 JSON，以及將 JSON 反序列化回物件。
*   **AndroidX Libraries & Material Components for Android**

## 權限需求

本應用程式需要在 `AndroidManifest.xml` 中宣告以下權限：

*   `POST_NOTIFICATIONS`: (Android 13+) 用於發送藥物提醒通知。
*   `BLUETOOTH_SCAN`: (Android 12+) 用於掃描附近的藍牙裝置。
*   `BLUETOOTH_CONNECT`: (Android 12+) 用于與已配對的藍牙裝置建立連線。
*   `ACCESS_FINE_LOCATION`: (Android 11 及以下) 掃描藍牙裝置時需要。
*   `SCHEDULE_EXACT_ALARM`: (Android 12+) 用於設定精確的鬧鐘，以準時發出提醒。
