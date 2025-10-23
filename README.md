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
    *   在「環境監測」頁面，當藍牙連接時，透過 **即時折線圖** 視覺化呈現藥盒回傳的溫濕度數據；未連接時，則會顯示提示訊息。
    *   在「服藥紀錄」頁面，透過月曆視覺化呈現每日服藥記錄，並計算顯示過去 30 天的服藥依從率。
*   **設定:**
    *   可透過工具列上的設定圖示進入設定頁面。
    *   支援亮色、暗色和跟隨系統主題的切換。

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

為實現 App 與藥盒的互動，定義了以下基於 JSON 格式的雙向通訊協定。`BluetoothLeManager` 負責將 App 內部生成的 JSON 物件序列化為字串並發送，同時也負責將從藥盒收到的原始數據反序列化為可處理的 JSON 或對應的資料結構。

### App -> 藥盒 (指令)

1.  **全量更新提醒 (Full Reminder Sync):**
    *   **用途:** 在所有設定完成後，或 App 啟動並連接上藥盒時，一次性將完整的時間表發送給藥盒。
    *   **App 實作:** 由 `ReminderSettingsFragment` 觸發，透過 `MainViewModel` 組裝 JSON，最終由 `MainActivity` 呼叫 `BluetoothLeManager` 發送。
    *   **格式範例:**
        ```json
        {
          "action": "sync_reminders",
          "payload": {
            "sync_time": 1678886400,
            "reminders": [
              { "slot": 1, "times": [ "08:00", "20:00" ] },
              { "slot": 3, "times": [ "09:00" ] },
              { "slot": 5, "times": [ "12:00", "18:00", "22:00" ] }
            ]
          }
        }
        ```

2.  **引導式放藥指令 (Guided Filling Command):**
    *   **用途:** 在「引導式放藥」流程中，命令藥盒旋轉到指定的藥倉。
    *   **App 實作:** 由 `ReminderSettingsFragment` 觸發，透過 `MainViewModel` 發起，最終由 `MainActivity` 呼叫 `BluetoothLeManager` 發送。
    *   **格式範例:**
        ```json
        { "action": "rotate_to_slot", "payload": { "slot": 1 } }
        ```

### 藥盒 -> App (狀態回傳)

1.  **藥物已填充確認 (Slot Filled Confirmation):**
    *   **用途:** 當使用者在藥盒上按下確認按鈕後，藥盒回傳此訊號給 App，表示藥物已放入。
    *   **App 實作:** 在 `MainActivity` 的 `onBoxStatusUpdate()` 回呼中接收並解析，接著呼叫 `MainViewModel.onGuidedFillConfirmed()`。
    *   **格式範例:**
        ```json
        { "status": "slot_filled", "payload": { "slot": 1 } }
        ```

2.  **藥物已取出回報 (Medication Taken Report):**
    *   **用途:** 藥盒偵測到使用者從某個藥倉取藥後，回報給 App。
    *   **App 實作:** 在 `MainActivity` 的 `onMedicationTaken()` 回呼中接收，並轉發給 `MainViewModel` 處理。
    *   **格式範例:**
        ```json
        { "status": "medication_taken", "payload": { "slot": 4 } }
        ```

3.  **環境數據回報 (Environment Data Report):**
    *   **用途:** 藥盒定時回傳感測到的環境溫濕度數據。
    *   **App 實作:** 在 `MainActivity` 的 `onSensorData()` 回呼中接收，並轉發給 `MainViewModel` 更新數據。
    *   **格式範例:**
        ```json
        { "status": "env_data", "payload": { "temp": 25.4, "humidity": 60.1 } }
        ```

4.  **異常狀態回報 (Anomaly Report):**
    *   **用途:** 當藥盒發生異常（如馬達卡住、感測器錯誤）時，回報給 App。
    *   **App 實作:** 在 `MainActivity` 的 `onError()` 回呼中接收並顯示警告。
    *   **格式範例:**
        ```json
        { "status": "box_anomaly", "payload": { "code": "jammed" } }
        ```

## 專案結構

本專案採用單一 Activity 和多 Fragment 的現代 Android App 架構，確保職責分離和高可擴展性。

*   `MainActivity.kt`: App 的唯一入口 `Activity`，其角色是「容器」和「總指揮」。
    *   負責承載 `TabLayout` 和 `ViewPager2`，管理主要 Fragment 的切換。
    *   創建並持有 `BluetoothLeManager` 的唯一實例，集中管理藍牙連接生命週期。
    *   接收藍牙回呼，並將所有事件轉發給共享的 `MainViewModel`。

*   `MainViewModel.kt`: 一個共享的 `ViewModel`，作為應用程式的「單一事實來源」(Single Source of Truth)。
    *   持有藍牙連接狀態、溫濕度數據、藥物列表、服藥紀錄等所有共享數據 (`LiveData`)。
    *   包含核心業務 logique，例如處理服藥事件、計算服藥依從率、儲存與讀取數據等。

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
    *   提供應用程式的主題設定。

*   `ble/BluetoothLeManager.kt`: 封裝所有低階藍牙通訊的細節。
    *   負責掃描、連接、發送指令和接收數據。
    *   將從藥盒收到的原始數據解析後，透過回呼 (callback) 傳遞給 `MainActivity`。

*   `AlarmScheduler.kt`: 負責設定和取消系統鬧鐘 (`AlarmManager`) 的輔助類別。

*   `AlarmReceiver.kt`: 一個 `BroadcastReceiver`，當鬧鐘觸發時，負責建立並顯示「該吃藥了！」的通知。

*   `BootReceiver.kt`: 一個 `BroadcastReceiver`，在設備重新啟動時，會自動讀取所有已儲存的藥物提醒，並重新設定鬧鐘。

## 權限需求

`POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## 最近更新

*   **2025/11/04:** 恢復了遺失的設定功能，包括設定圖示、主題切換和輔色調整。使用者現在可以再次從工具列存取設定頁面並自訂應用程式的外觀。
*   **2025/11/03:** 解決了 `fragment_reminder_settings.xml` 中的無障礙功能警告，並清理了 `MainViewModel.kt` 中未使用的參數。
*   **2025/11/02:** 解決了 IDE 中出現的 XML 資源解析錯誤和多個 Kotlin 檔案中的無用程式碼警告，並透過 Gradle 同步確保了專案狀態的穩定。
*   **2025/11/01:** 解決了 IDE 中出現的 XML 資源解析錯誤和多個 Kotlin 檔案中的無用程式碼警告，並透過 Gradle 同步確保了專案狀態的穩定。
*   **2025/10/31:** 恢復了編輯、刪除藥物提醒的功能，並重新整合了鬧鐘排程功能。現在，鬧鐘不僅可以在設定時啟用，還能在設備重啟後自動重新設定。
*   **2025/10/30:** 修正了新增藥物後，藥物列表不會立即更新的 UI 問題。透過確保 `LiveData` 收到的是一個全新的列表實例，而非僅修改現有列表，來正確觸發 UI 更新。
*   **2025/10/29:** 處理了 IDE 中關於 `MedicationListAdapter.kt` 和 `ReminderSettingsFragment.kt` 的過時警告，透過 Gradle 同步刷新了專案狀態。
*   **2025/10/28:** 清理了專案中所有警告，包括無用的導入、參數和命名空間宣告，並修復了字串資源衝突。
*   **2025/10/27:** 清理了專案中所有警告，包括無用的導入、參數和命名空間宣告，並修復了字串資源衝突。
*   **2025/10/26:** 修復了因缺少 `androidx.preference:preference-ktx` 依賴而導致的資源連結錯誤。
*   **2025/10/25:** 新增了設定頁面與藥物列表頁面，並恢復了主題設定功能。
*   **2025/10/24:** 優化了藥物提醒表單的驗證，提供更清晰的錯誤提示。
*   **2025/10/23:** 修復了因 Gradle 版本目錄不完整而導致的嚴重 build 錯誤。
