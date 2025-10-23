# 智能藥盒提醒 App

這是一款 Android 應用程式，旨在幫助使用者管理他們的藥物服用排程，并透過藍牙低功耗 (BLE) 技術與智能藥盒互動。

## 設計理念

本專案的核心設計是將複雜的邏輯處理保留在 Android App 中，讓智能藥盒 (ESP32) 的職責盡可能單純，只作為一個忠實的時間與指令執行者。這樣的設計大大簡化了硬體端的韌體開發，提高了系統的穩定性與省電效率。

## 主要功能

*   **分頁式介面:**
    *   採用 `TabLayout` 和 `ViewPager2` 打造現代化的分頁式介面，分為「提醒設定」、「服藥紀錄」和「環境監測」三大功能區塊，操作更直覺。
*   **藥物排程管理:**
    *   提供獨立的 **新增、編輯、刪除** 按鈕，操作流程清晰。
    *   藥倉總數為 8 格，可新增的藥物數量會根據 **已被佔用的藥倉數量動態調整**。
    *   設定藥物名稱、劑量、服用頻率、起訖日期，**總藥量將自動計算**。
    *   自訂服藥的開始與結束日期及一天中不同時段 (早、中、晚、睡前) 的服藥時間。
    *   智慧檢查機制，防止使用者將多種藥物指派到同一個已被佔用的藥倉。
*   **藍牙智能藥盒整合:**
    *   **引導式放藥 (Guided Filling):** 新增藥物時，App 會引導您逐一完成。每設定完一筆藥物，藥盒會自動旋轉到對應藥倉，讓您直接放入藥物，並透過藥盒上的按鈕確認，實現軟硬體結合的流暢體驗。
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

1.  **新增藥物 (引導式放藥流程):**
    *   在「提醒設定」分頁，選擇您要一次新增的藥物數量，系統會自動產生對應的輸入欄位。
    *   填寫完所有藥物的資訊（名稱、劑量、頻率、藥倉等）後，點擊「新增藥物提醒」。
    *   **藥盒自動轉動:** App 會鎖定畫面並發送指令，讓藥盒自動轉到 **第一筆藥物** 對應的藥倉。
    *   **放入藥物並確認:** 畫面上會提示您將藥物放入指定的藥倉。完成後，**直接按下藥盒上的實體按鈕**。
    *   **重複流程:** App 收到確認訊號後，會再次發送指令，讓藥盒轉到 **下一筆藥物** 的藥倉。您只需重複「放藥 -> 按按鈕」的動作，直到所有藥物都已放入。
    *   **完成同步:** 全部完成後，App 會將所有提醒一次性同步給藥盒，並解除畫面鎖定。
2.  **編輯或刪除藥物:**
    *   點擊「提醒設定」分頁下方的 **「編輯」** 或 **「刪除」** 按鈕。
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
    *   **App 實作:** 在 `MainActivity.kt` 的 `syncRemindersToBox()` 函式中組裝 JSON 並透過 `bluetoothLeManager.sendCommand()` 發送。
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
    *   **App 實作:** 在 `MainActivity.kt` 的 `rotateToSlot()` 函式中組裝 JSON 並透過 `bluetoothLeManager.sendCommand()` 發送。
    *   **格式範例:**
        ```json
        { "action": "rotate_to_slot", "payload": { "slot": 1 } }
        ```

### 藥盒 -> App (狀態回傳)

1.  **藥物已填充確認 (Slot Filled Confirmation):**
    *   **用途:** 當使用者在藥盒上按下確認按鈕後，藥盒回傳此訊號給 App，表示藥物已放入。
    *   **App 實作:** 在 `MainActivity.kt` 的 `onBoxStatusUpdate()` 回呼中接收並解析。接著呼叫 `viewModel.onGuidedFillConfirmed()`。
    *   **格式範例:**
        ```json
        { "status": "slot_filled", "payload": { "slot": 1 } }
        ```

2.  **藥物已取出回報 (Medication Taken Report):**
    *   **用途:** 藥盒偵測到使用者從某個藥倉取藥後，回報給 App。
    *   **App 實作:** 在 `MainActivity.kt` 的 `onMedicationTaken()` 回呼中接收。
    *   **格式範例:**
        ```json
        { "status": "medication_taken", "payload": { "slot": 4 } }
        ```

3.  **環境數據回報 (Environment Data Report):**
    *   **用途:** 藥盒定時回傳感測到的環境溫濕度數據。
    *   **App 實作:** 在 `MainActivity.kt` 的 `onSensorData()` 回呼中接收。
    *   **格式範例:**
        ```json
        { "status": "env_data", "payload": { "temp": 25.4, "humidity": 60.1 } }
        ```

4.  **異常狀態回報 (Anomaly Report):**
    *   **用途:** 當藥盒發生異常（如馬達卡住、感測器錯誤）時，回報給 App。
    *   **App 實作:** 在 `MainActivity.kt` 的 `onError()` 回呼中接收。
    *   **格式範例:**
        ```json
        { "status": "box_anomaly", "payload": { "code": "jammed" } }
        ```

## 專案結構

本專案採用單一 Activity 和多 Fragment 的現代 Android App 架構，確保職責分離和高可擴展性。

*   `MainActivity.kt`: 作為 App 的唯一入口 `Activity`，其角色是「容器」和「總指揮」。
    *   負責承載 `TabLayout` 和 `ViewPager2`，管理三個主要 Fragment 的切換。
    *   創建並持有 `BluetoothLeManager` 的唯一實例，集中管理藍牙連接生命週期。
    *   持有共享的 `MainViewModel`，將藍牙狀態、環境數據等分發給各個 Fragment。

*   `MainViewModel.kt`: 一個共享的 `ViewModel`，用於在 `MainActivity` 和多個 Fragment 之間安全地共享數據。
    *   持有藍牙連接狀態、溫濕度數據 (`LiveData`/`StateFlow`)。
    *   包含業務邏輯，例如處理來自藍牙的數據、更新服藥紀錄等。

*   `ui/ReminderSettingsFragment.kt`: 「提醒設定」頁面的 Fragment。
    *   負責所有藥物相關的 UI 操作，包括動態生成藥物設定卡片。
    *   收集用戶輸入，並透過 `MainViewModel` 將設定指令傳遞給 `MainActivity`。

*   `ui/HistoryFragment.kt`: 「服藥紀錄」頁面的 Fragment。
    *   從 `MainViewModel` 觀察服藥紀錄數據 (`dailyStatusMap`) 並更新日曆。
    *   顯示服藥依從率圖表。

*   `ui/EnvironmentFragment.kt`: 「環境監測」頁面的 Fragment。
    *   從 `MainViewModel` 觀察溫濕度數據並更新 UI，例如儀表盤或歷史圖表。

*   `ble/BluetoothLeManager.kt`: 封裝所有低階藍牙通訊的細節。
    *   負責掃描、連接、發送指令和接收數據。
    *   將原始數據解析後，透過回呼 (callback) 或 `Flow` 傳遞給 `MainActivity`。

## 權限需求

*   `POST_NOTIFICATIONS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SCHEDULE_EXACT_ALARM`
