# 智能藥盒提醒 App

這是一款 Android 應用程式，旨在幫助使用者管理他們的藥物服用排程，並透過藍牙低功耗 (BLE) 技術與智能藥盒互動。

## 設計理念

本專案的核心設計是將複雜的邏輯處理保留在 Android App 中，讓智能藥盒 (ESP32) 的職責盡可能單純，只作為一個忠實的時間與指令執行者。這樣的設計大大簡化了硬體端的韌體開發，提高了系統的穩定性與省電效率。

## 主要功能

*   **分頁式介面:**
    *   採用 `TabLayout` 和 `ViewPager2` 打造現代化的分頁式介面，分為「提醒設定」、「藥物列表」、「服藥紀錄」和「環境監測」四大功能區塊，操作更直覺。
*   **完整的藥物管理 (CRUD):**
    *   在「提醒設定」頁面，您可以動態新增、編輯和刪除藥物提醒。
    *   為每種藥物設定名稱、劑量、服藥時間、起訖日期，**總藥量將自動計算**。
    *   智慧的藥倉互斥機制，防止您將多種藥物設定到同一個藥倉。
*   **可靠的鬧鐘提醒:**
    *   在您設定的每個服藥時間，App 都會發送精確的本地通知。
    *   即使 App 未在前景執行，或手機重新開機，鬧鐘依然有效。
*   **藍牙智能藥盒整合:**
    *   **引導式放藥 (Guided Filling):** 新增藥物時，App 會引導您逐一完成。每設定完一筆藥物，藥盒會自動旋轉到對應藥倉，讓您直接放入藥物，並透過藥盒上的按鈕確認，實現軟硬體結合的流暢體驗。
    *   掃描並連接到智能藥盒。
    *   與藥盒同步時間及各藥倉的用藥提醒。
    *   接收藥盒的狀態更新，例如：哪個藥倉的藥物已被取出、藥倉堵塞、感應器錯誤等。
*   **數據監測與追蹤:**
    *   在「環境監測」頁面，當藍牙連接時，透過 **即時折線圖** 視覺化呈現藥盒回傳的溫濕度數據；未連接時，則会顯示提示訊息。當使用者下拉刷新時，App 會從藥盒同步離線期間的 **歷史溫濕度數據**，並完整呈現在圖表上。
    *   在「服藥紀錄」頁面，透過月曆視覺化呈現每日服藥記錄，並計算顯示過去 30 天的服藥依從率。
*   **設定:**
    *   可透過工具列上的設定圖示進入設定頁面。
    *   支援亮色、暗色和跟隨系統主題的切換。
    *   支援在「繁體中文」和「English」之間切換語言。

## 使用說明

1.  **新增藥物 (引導式放藥流程):**
    *   在「提醒設定」分頁，選擇您要一次新增的藥物數量，系統會自動產生對應的輸入欄位。
    *   填寫完所有藥物的資訊（名稱、劑量、頻率、藥倉等）後，點擊「新增藥物提醒」。
    *   **藥盒自動轉動:** App 會鎖定畫面並發送指令，讓藥盒自動轉到 **第一筆藥物** 對應的藥倉。
    *   **放入藥物並確認:** 畫面上會提示您將藥物放入指定的藥倉。完成後，**直接按下藥盒上的實體按鈕**。
    *   **重複流程:** App 收到確認訊號後，會再次發送指令，讓藥盒轉到 **下一筆藥物** 的藥倉。您只需重複「放藥 -> 按按鈕」的動作，直到所有藥物都已放入。
    *   **完成同步:** 全部完成後，App 會將所有提醒一次性同步給藥盒，並解除畫面鎖定。
2.  **編輯或刪除藥物:**
    *   點擊「提醒設定」分頁下方的 **「編輯提醒」** 或 **「刪除提醒」** 按鈕。
    *   App 會彈出一個包含所有已設定藥物的列表。
    *   從列表中選擇您想操作的藥物。
    *   **編輯:** 選擇後，該藥物的資訊會自動填入上方的表單中。修改後點擊「更新藥物」即可。
    *   **刪除:** 選擇後，在跳出的確認對話框中進行確認即可刪除。
3.  **連接藥盒:**
    *   點擊「提醒設定」分頁的「連接藥盒」按鈕。App 會開始掃描並讓您選擇您的智能藥盒進行配對。

## 藍牙通訊協定 (Bluetooth Protocol)

為實現 App 與藥盒的互動，我們定義了一套基於位元組陣列 (Byte Array) 的雙向通訊協定。`BluetoothLeManager` 類別負責將指令封裝成位元組陣列並發送，同時也負責解析從藥盒收到的數據。

### Service 與 Characteristics UUID

- **Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Write Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8` (App -> 藥盒)
- **Notify Characteristic UUID:** `c8c7c599-809c-43a5-b825-1038aa349e5d` (藥盒 -> App)

### App -> 藥盒 (指令)

所有指令均透過寫入 **Write Characteristic** 發送。

1.  **時間同步 (Time Sync):**
    - **指令碼:** `0x11`
    - **用途:** 將 App 的當前時間同步給藥盒。
    - **格式 (7 bytes):**
        - `[0]`: `0x11`
        - `[1]`: `年 - 2000`
        - `[2]`: `月 (1-12)`
        - `[3]`: `日`
        - `[4]`: `時 (0-23)`
        - `[5]`: `分`
        - `[6]`: `秒`

2.  **請求藥盒狀態 (Request Status):**
    - **指令碼:** `0x20`
    - **用途:** 主動向藥盒查詢其當前狀態（例如各藥倉是否有藥）。
    - **格式 (1 byte):** `[0]: 0x20`

3.  **請求即時環境數據 (Request Instant Environment Data):**
    - **指令碼:** `0x30`
    - **用途:** 主動向藥盒請求目前的即時溫濕度數據。
    - **格式 (1 byte):** `[0]: 0x30`

4.  **請求歷史環境數據 (Request Historic Environment Data):**
    - **指令碼:** `0x31`
    - **用途:** 請求藥盒開始傳輸其儲存的所有歷史溫濕度數據。
    - **格式 (1 byte):** `[0]: 0x31`

### 藥盒 -> App (通知)

所有通知均透過 **Notify Characteristic** 發送。App 在 `handleIncomingData(data: ByteArray)` 方法中解析這些數據。

1.  **藥盒狀態回報 (Box Status Update):**
    - **指令碼:** `0x80`
    - **用途:** 回報藥盒各藥倉的狀態。
    - **格式 (2 bytes):** `[0]: 0x80`, `[1]: 藥倉狀態位元遮罩 (Slot Mask)`

2.  **藥物已取出回報 (Medication Taken Report):**
    - **指令碼:** `0x81`
    - **用途:** 藥盒偵測到使用者從某個藥倉取藥後，回報給 App。
    - **格式 (2 bytes):** `[0]: 0x81`, `[1]: 藥倉編號 (Slot Number)`

3.  **時間同步確認 (Time Sync Acknowledged):**
    - **指令碼:** `0x82`
    - **用途:** 確認已成功接收並設定 App 同步過來的時間。
    - **格式 (1 byte):** `[0]: 0x82`

4.  **即時溫濕度數據回報 (Instant Sensor Data Report):**
    - **指令碼:** `0x90`
    - **用途:** 回報當前感測到的環境溫濕度數據。
    - **格式 (5 bytes):**
        - `[0]`: `0x90`
        - `[1]`: `溫度整數部分`
        - `[2]`: `溫度小數部分`
        - `[3]`: `濕度整數部分`
        - `[4]`: `濕度小數部分`
    - **解析:** `溫度 = byte[1] + byte[2] / 100.0` , `濕度 = byte[3] + byte[4] / 100.0`

5.  **歷史溫濕度數據點 (Historic Sensor Data Point):**
    - **指令碼:** `0x91`
    - **用途:** 回報一筆歷史溫濕度數據。
    - **格式 (9 bytes):**
        - `[0]`: `0x91`
        - `[1-4]`: `時間戳 (Unix Timestamp, 4 bytes, Little Endian)`
        - `[5]`: `溫度整數部分`
        - `[6]`: `溫度小數部分`
        - `[7]`: `濕度整數部分`
        - `[8]`: `濕度小數部分`

6.  **歷史數據傳輸結束 (End of Historic Data Transmission):**
    - **指令碼:** `0x92`
    - **用途:** 告知 App，所有的歷史數據都已經傳送完畢。
    - **格式 (1 byte):** `[0]: 0x92`

7.  **異常狀態回報 (Error Report):**
    - **指令碼:** `0xEE`
    - **用途:** 當藥盒發生異常時回報給 App。
    - **格式 (2 bytes):** `[0]: 0xEE`, `[1]: 錯誤碼 (Error Code)`

## 專案結構

本專案採用單一 Activity 和多 Fragment 的現代 Android App 架構，確保職責分離和高可擴展性。

*   `MainActivity.kt`: App 的唯一入口 `Activity`，其角色是「容器」和「總指揮」。
    *   負責承載 `TabLayout` 和 `ViewPager2`，管理主要 Fragment 的切換。
    *   創建並持有 `BluetoothLeManager` 的唯一實例，集中管理藍牙連接生命週期。
    *   接收藍牙回呼，並將所有事件轉發給共享的 `MainViewModel`。

*   `MainViewModel.kt`: 一個共享的 `ViewModel`，作為應用程式的「單一事實來源」(Single Source of Truth)。
    *   持有藍牙連接狀態、溫濕度數據、藥物列表、服藥紀錄等所有共享數據 (`LiveData`)。
    *   包含核心業務 oblique，例如處理服藥事件、計算服藥依從率、儲存與讀取數據等。

*   `ReminderSettingsFragment.kt`: 「提醒設定」頁面的 Fragment。
    *   負責所有與藥物設定相關的 UI 操作，包括動態生成藥物設定卡片。
    *   收集用戶輸入，並透過 `MainViewModel` 將新藥物儲存到應用程式中。

*   `MedicationListFragment.kt`: 「藥物列表」頁面的 Fragment。
    *   從 `MainViewModel` 觀察藥物列表數據並更新列表。

*   `HistoryFragment.kt`: 「服藥紀錄」頁面的 Fragment。
    *   從 `MainViewModel` 觀察服藥紀錄數據並更新日曆。
    *   顯示服藥依從率圖表。

*   `EnvironmentFragment.kt`: 「環境監測」頁面的 Fragment。
    *   從 `MainViewModel` 觀察藍牙連接狀態和溫濕度數據。
    *   根據狀態，顯示即時的溫濕度折線圖或「未連接」的提示。

*   `SettingsFragment.kt`: 「設定」頁面的 Fragment。
    *   提供應用程式的主題和語言設定。

*   `ble/BluetoothLeManager.kt`: 封裝所有低階藍牙通訊的細節。
    *   負責掃描、連接、發送指令和接收數據。
    *   將從藥盒收到的原始數據解析後，透過回呼 (callback) 傳遞給 `MainActivity`。

*   `AlarmScheduler.kt`: 負責設定和取消系統鬧鐘 (`AlarmManager`) 的輔助類別。

*   `AlarmReceiver.kt`: 一個 `BroadcastReceiver`，當鬧鐘觸發時，負責建立並顯示「該吃藥了！」的通知。

*   `BootReceiver.kt`: 一個 `BroadcastReceiver`，在設備重新啟動時，會自動讀取所有已儲存的藥物提醒，並重新設定鬧鐘。

## 權限需求

`POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## Bug Fixes

*   **0015:** 修正了服藥正確率未更新及服藥紀錄頁面時間顯示不清楚的問題。在 `MedicationTakenReceiver` 和 `MainViewModel` 中實作了正確的服藥率計算邏輯，並修正了 `fragment_history.xml` 中的文字顏色，確保其在淺色主題下可見。

## 最近更新

*   **0039:** 修正了 `MainActivity.kt` 中的多個棄用警告。更新了返回按鈕的處理方式以使用新的 `OnBackPressedDispatcher`，並將地區/語言設定邏輯現代化為使用 `AppCompatDelegate.setApplicationLocales` API，移除了所有相關的已棄用方法。
*   **0038:** 在設定頁面 (`SettingsFragment`) 新增了返回箭頭。讓使用者可以輕鬆地從設定選單返回到上一個畫面。
*   **0037:** 修正了設定頁面 (`SettingsFragment`) 的 UI 顯示問題。先前設定選單的背景是透明的，導致文字與下方的 UI 元件重疊。此問題已透過以編程方式設定一個遵循當前應用程式主題 (亮色/暗色) 的背景顏色來解決，確保了畫面的清晰可見度。
*   **0036:** 修正了設定圖示在亮色模式下不可見的問題，並將設定頁面中的「主題設定」和「語言設定」合併為一個「外觀」分類，以簡化介面。
*   **0035:** 在設定頁面中新增了語言切換功能，讓使用者可以手動切換應用程式的顯示語言 (繁體中文、英文或跟隨系統)。
*   **0034:** 為應用程式新增英文本地化，以支援英語系使用者。
*   **0033:** 實作了歷史溫濕度數據同步功能。擴充了藍牙協定，允許 App 在連接時，從藥盒同步離線期間記錄的所有溫濕度歷史數據，並在圖表上完整呈現。
*   **0032:** 修正了專案中的多個編譯錯誤與警告，包含 `SwipeRefreshLayout` 依賴問題、`MainActivity.kt` 中的錯誤，以及清理未使用的程式碼。
*   **0031:** 清理了 `app/src/main/java/com/example/medicationreminderapp/ui/` 目錄中所有重複且空白的檔案。
*   **0030:** 實作了藍牙協定中「App 主動請求溫濕度數據」的功能，並提供 UI 介面讓使用者觸發此操作。
*   **0029:** 修正了 `app/build.gradle.kts` 中的多個 build 錯誤與警告。處理了 `buildConfigField` 不正確的字串引號問題，並將棄用的 `exec` 方法替換為更現代的 `ProcessBuilder`，確保了 Gradle build script 的穩定性。
*   **0028:** 修復了因移除 `BluetoothLeManager` 中看似未使用的 `requestStatus()` 和 `syncTime()` 方法而導致的 Build 失敗問題。這兩個方法已被重新加回，確保 `MainActivity` 可以正常呼叫。
*   **0027:** 移除了 `BluetoothLeManager.kt` 中未被使用的 `sendJson` 方法，進一步清理了藍牙通訊的程式碼。
*   **0026:** 清理了專案中多個「未使用宣告」的警告，包括移除藍牙模組中已由 JSON 指令取代的舊方法、移除 `HistoryFragment.kt` 中未被使用的屬性，以及清空了 `ui` 套件中重複且無用的檔案內容，顯著提升了程式碼品質。
*   **0025:** 移除了 `Medication` data class 中未被使用的 `frequency` 欄位及其相關的字串資源，使程式碼更精簡。
*   **0024:** 修復了 IDE 中的多個警告，包括為圖片資源添加無障礙描述、將硬編碼字串移至資源檔，以及清理了 Kotlin 檔案中未使用的導入和參數，提升了程式碼品質與可維護性。
*   **0023:** 在服藥紀錄頁面新增了視覺標示功能。現在，當天所有藥物都按時服用後，日曆上對應的日期下方會顯示一個綠色圓點，讓使用者可以更直觀地追蹤自己的服藥狀況。
*   **0022:** 簡化了版本號碼的設定，並在藥物清單為空時顯示提示訊息。移除了 `app/build.gradle.kts` 中複雜的 Git 版本控制，改為直接從 `config.gradle.kts` 讀取版本資訊。同時，更新了藥物列表頁面，當沒有提醒事項時，會顯示「無提醒」的文字，改善了使用者體驗。
*   **0021:** 清理了 `SettingsFragment.kt` 中的警告，移除了無用的 `import` 並以更安全的 `let` 區塊取代了不必要的安全呼叫。
*   **0.020:** Restored missing settings features, including the settings icon, theme switching, and accent color adjustment. Users can now access the settings page from the toolbar and customize the app's appearance again.
*   **0019:** Resolved accessibility warnings in `fragment_reminder_settings.xml` and cleaned up unused parameters in `MainViewModel.kt`.
*   **0018:** Resolved XML resource parsing errors and multiple unused code warnings in Kotlin files that appeared in the IDE, and ensured project state stability through a Gradle sync.
*   **0017:** Resolved XML resource parsing errors and multiple unused code warnings in Kotlin files that appeared in the IDE, and ensured project state stability through a Gradle sync.
*   **0016:** Restored the ability to edit and delete medication reminders and reintegrated the alarm scheduling function. Now, alarms are not only enabled when set but are also automatically reset after the device reboots.
*   **0015:** Fixed a UI issue where the medication list would not update immediately after adding a new medication. The UI update is now correctly triggered by ensuring that `LiveData` receives a new list instance instead of just modifying the existing one.
*   **0014:** Handled outdated warnings in the IDE regarding `MedicationListAdapter.kt` and `ReminderSettingsFragment.kt` and refreshed the project state with a Gradle sync.
*   **0013:** Cleaned up all warnings in the project, including unused imports, parameters, and namespace declarations, and fixed string resource conflicts.
*   **0012:** Cleaned up all warnings in the project, including unused imports, parameters, and namespace declarations, and fixed string resource conflicts.
*   **0011:** Fixed a resource linking error caused by a missing `androidx.preference:preference-ktx` dependency.
*   **0010:** Added a settings page and a medication list page, and restored the theme setting function.
*   **0009:** Optimized the validation of the medication reminder form to provide clearer error messages.
*   **0008:** Fixed a critical build error caused by an incomplete Gradle version directory.
