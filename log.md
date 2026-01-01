# 更新日誌

## 2026-01-01
### v1.3.2: 硬體確認服藥
*   **硬體互動 (Pillbox Integration):** 
    *   **ESP32 端:** 在 `input.cpp` 的 `handleButtons()` 函式中新增邏輯。當鬧鐘響起時，短按確認按鈕不僅會停止鬧鐘，還會觸發 `sendMedicationTaken(0)`，透過藍牙向 App 發送「已服藥」訊號。
    *   **App 端:** 檢視並確認了 App 的接收鏈路 (`BluetoothLeManager` -> `MainActivity` -> `MainViewModel` -> `AppRepository`) 已正確實作。收到訊號後，`AppRepository` 中的 `processMedicationTaken` 會自動執行，將對應藥物的庫存減一、新增服藥時間紀錄，並更新日曆與相關 UI，無需額外修改。

### v1.3.1: 智慧放藥引導
*   **文件整理:** 根據 Git 歷史紀錄，此版本主要為「智慧放藥引導 (Smart pill placement guide)」功能的收尾與文件整理，在 `todo.md` 中將其標示為已完成。

### v1.3.0: 智慧藥盒引導 & 藍牙重連
*   **核心功能 (Pillbox Guidance):**
    *   **App 端:** 在 `Medication` 資料模型中新增 `slotNumber: Int` 欄位，並在新增/編輯藥物流程中加入對應的 UI 輸入。`BluetoothLeManager` 新增 `guidePillbox(slot: Int)` 方法，用以發送 `0x42` 指令。
    *   **ESP32 端:** 新增 `CMD_GUIDE_PILLBOX (0x42)` 指令處理邏輯。收到指令後，ESP32 會計算目標角度並驅動伺服馬達，將藥盒旋轉至指定藥槽，同時 `display.cpp` 會顯示引導文字，`hardware.cpp` 則透過 `ledcWrite` 控制 LED 燈條，僅點亮對應藥槽的 LED，提供清晰的視覺引導。
*   **體驗優化 (Robustness):** 
    *   **意外斷線自動重連:** 在 `BluetoothLeManager` 中導入了自動重連機制。當藍牙非使用者手動斷開時，App 會自動嘗試重新連線，並設有 5 次的重試上限與 5 秒的延遲。
    *   **UI 狀態回饋:** 在 `MainViewModel` 中新增了對應的 `StateFlow` (`isReconnecting`)，並在 `strings.xml` 中加入了「正在嘗試重連...」與「重連失敗」的狀態文字。

## 2025-12-31
### v1.2.7: 更新與鬧鐘排程優化
*   **更新流程優化 (Update Flow):**
    *   在 `UpdateManager` 中新增邏輯，當偵測到新舊 App 的簽名不匹配時 (例如從 Dev 版更新到 Official 版)，會跳出明確的警告，告知使用者需要先卸載舊版，避免安裝失敗。
*   **架構重構 (Alarm Scheduling):**
    *   為符合 Android 12+ 的精確鬧鐘權限要求，重構了鬧鐘設定邏輯。在 `MainActivity` 中，會檢查 `SCHEDULE_EXACT_ALARM` 權限，若未授予，則引導使用者至系統設定頁面開啟。

### v1.2.6: Android 12+ 鬧鐘排程優化
*   **架構重構 (Alarm Scheduling):** 為符合 Android 12+ 的精確鬧鐘權限要求，重構了鬧鐘設定邏輯。

### v1.2.5: UI 與本地化修正 (Hotfix)
*   **UI/UX 優化:**
    *   將主畫面的 `TabLayout` 的 `tabMode` 改為 `scrollable`，解決在英文語系下，文字過長導致換行的排版問題。
*   **本地化修正:**
    *   在 `strings.xml (zh-TW)` 中，為 `check_for_updates_summary` 補上中文翻譯，提升更新設定頁面的完整性。
*   **按鈕風格統一:**
    *   更新 `themes.xml`，統一 App 內所有按鈕 (`MaterialButton`) 的視覺風格，確保外觀一致性。

### v1.2.4: BLE & UI 穩定性更新
*   **建置設定 (Build Config):**
    *   修改 `app/build.gradle.kts`，為非正式版 (`main` 或 `dev` 以外的分支) 的 `applicationId` 自動添加 `.branch-name` 後綴。這使得開發版本可以與正式版同時安裝在同一裝置上，方便測試。
*   **藍牙狀態管理重構:**
    *   將 `BluetoothLeManager` 中所有硬編碼的狀態字串 (如 "正在掃描...") 全部替換為 `R.string` 資源引用。`MainViewModel` 現在只處理 `StringRes` ID，實現了狀態顯示的完全本地化。
*   **UI/UX 修復 (多國語言):**
    *   調整了 Tab 標籤的 `textSize` 以避免在英文語系下換行。
    *   修正了 `CONNECT PILLBOX` 按鈕與 `Disconnected` 狀態文字的約束，確保在不同螢幕尺寸下皆能正確對齊。
    *   加寬了下拉式選單的寬度，確保 "How many medications to..." 文字能完整顯示。
*   **本地化修正:**
    *   在 `strings.xml (zh-TW)` 中新增 "disconnected" 的翻譯 "已斷線"，並確保藍牙連線狀態的更新會使用此資源。

### v1.2.3: Agent Workflow 更新
*   **開發流程文件化:** 更新 `agent.md`，明確定義了「規劃模式 (Mode A)」與「執行模式 (Mode B)」，並強制要求在執行程式碼修改後，必須同步更新所有相關文件 (`log.md`, `todo.md`, `README.md`)，確保文件與程式碼的一致性。

### Bug Fixes & UI Improvements
*   **徹底修復字體大小無法縮放問題 (根本原因)**: 將字體縮放邏輯從 `attachBaseContext` 移至 `MainActivity.kt` 的 `onCreate` 方法中，並確保在 `super.onCreate` 之前執行。這確保了我們的自訂 `Configuration` (包含 `fontScale`) 會在系統套用主題之前生效，從而避免了被主題中寫死的字體大小覆寫的問題。同時，也將縮放比例調整為 `0.8f`, `1.0f`, `1.5f`，以提供更顯著的視覺效果。
*   **優化「頻道失效」通知**: 在 `SettingsFragment.kt` 中加入靜態旗標 `hasShownInvalidChannelWarning`，確保「頻道失效」的彈出式警告在單次 App 生命週期中只會顯示一次，避免在設定頁面中因重複讀取遠端頻道列表而反覆跳出通知，提升使用者體驗。
*   **修正字體大小無法變更錯誤**: 修正 `MainActivity.kt` 中 `attachBaseContext` 方法的拼寫錯誤 (`attachBaseAontext` -> `attachBaseContext`)。此錯誤導致 App 無法正確套用使用者在設定中選擇的字體大小，修正後確保 App 能根據偏好設定，正確調整並顯示「小」、「中」、「大」三種字體尺寸。
*   **程式碼品質提升**: 移除了 `MainActivity.kt` 中未使用的 `import android.content.res.Configuration`，提升程式碼的整潔性。
*   **調整字體縮放比例**: 將「大字體」模式的縮放比例從 `1.1f` 提升至 `1.2f`，使字體放大的效果更為顯著，提升視覺可讀性。
*   **徹底修復字體大小無法縮放問題**: 移除 `activity_main.xml` 中 `Toolbar` 的 `TextView` 上寫死的 `textAppearance` 屬性。此屬性會覆蓋 `attachBaseContext` 中設定的 `fontScale`，導致字體大小設定無效。移除後，`Toolbar` 的標題大小將能正確地隨著系統字體縮放設定而改變。

### Bug Fixes & Code Quality
*   **修正 `strings.xml` 編譯錯誤:** 修正了 `strings.xml` 中因 `update_channel_entries` 引起的 "not found in default locale" 編譯錯誤，確保多國語言資源的一致性。
*   **修正 App 內更新檢查邏輯:**
    *   **釐清自動與手動檢查:** 重構 `UpdateManager.kt` 中的 `checkForUpdates` 方法，確保「自動檢查」嚴格限定於 App 自身的建置頻道 (如 `dev`, `nightly`)，解決了先前會錯誤提示更新的問題。現在只有「手動檢查」會遵循使用者在設定頁面選擇的頻道。
    *   **修正版本比對 (SemVer):** 修正 `isNewerVersion` 方法，使其能正確處理預發行版 (e.g., `1.2.2-dev`) 與穩定版 (e.g., `1.2.2`) 的比對，並修正了邏輯警告。現在，穩定版將被正確地視為比其對應的預發行版更新，符合 SemVer 規範。
*   **程式碼品質提升:** 
    *   **可讀性:** 修正了 `MainActivity.kt` 中多餘的程式碼限定詞，提升了程式碼的可讀性。

### Bug Fixes & Improvements
*   **修正跨頻道更新檢查:** 修改了 `UpdateManager.kt` 的更新邏輯，移除了會自動檢查 Stable 頻道的行為。現在 App 只會檢查當前所選的更新頻道，解決了在 Nightly 版本下，仍然會提示有新版 (Stable) 的問題。
*   **修正 `strings.xml` 編譯錯誤:** 修正了 `strings.xml` 中因 `update_channel_entries` 引起的 "not found in default locale" 編譯錯誤，確保多國語言資源的一致性。
*   **程式碼品質提升:** 
    *   **國際化 (i18n):** 將 `downloadAndInstall()` 和 `installApk()` 中所有硬編碼的 Toast 和對話框訊息，全部抽取至字串資源，並提供了完整的英文翻譯。
    *   **可讀性:** 修正了 `MainActivity.kt` 中多餘的程式碼限定詞，提升了程式碼的可讀性。

### Code Quality & Bug Fixes
*   **全面國際化 (i18n) 修正:**
    *   **硬編碼字串移除:** 修正了 `SettingsFragment.kt` 中多處硬編碼的中文字串，包含「頻道已失效」的提示、更新檢查流程中的各種對話框標題與訊息，以及更新頻道的選項文字 (Stable, Current, Dev)。
    *   **字串資源化:** 將所有上述的硬編碼字串，全部抽取至 `values/strings.xml`，並在 `values-en/strings.xml` 中提供了完整的英文翻譯。
    *   **修正字體大小無法變更:** 調整了 `MainActivity.kt` 中 `onCreate()` 的程式碼順序，將 `applyFontSize()` 與 `applyCharacterTheme()` 移至 `super.onCreate()` 前，確保主題能正確被套用，解決了使用者設定字體大小後，介面沒有變化的問題。
*   **修復設定頁面遺失:**
    *   **還原設定頁面:** 透過 Git 歷史紀錄，將被意外重構的 `SettingsFragment.kt` 還原為 `PreferenceFragmentCompat` 版本，恢復了所有遺失的設定選項 (主題、語言、更新頻道等)。
    *   **整合字體大小設定:** 將字體大小調整功能，以 `ListPreference` 的形式重新整合至 `preferences.xml`，使其與其他設定項目的風格和操作保持一致。

### Features
*   **字體大小調整功能:**
    *   **多尺寸主題:** 在 `themes.xml` 中新增了 `Small`, `Medium`, `Large` 三種字體大小的樣式與主題，方便使用者根據視力需求調整。
    *   **設定頁面:** 建立了 `SettingsFragment`，並在其中新增了 `RadioGroup`，讓使用者可以直觀地選擇「小」、「中」、「大」三種字體大小。
    *   **即時應用:** 使用者在設定頁面選擇新的字體大小後，應用程式會立即重新啟動 (`recreate()`)，並載入新的主題，實現即時預覽效果。
    *   **偏好儲存:** 使用者的字體大小選擇會儲存在 `SharedPreferences` 中，確保下次啟動 App 時能自動套用上次的設定。
    *   **程式碼整合:** 在 `MainActivity` 中新增 `applyFontSize()` 函式，並在 `onCreate()` 中呼叫，確保在 App 生命週期早期階段就能正確套用主題。同時，也將 `SettingsFragment` 的導覽功能整合到 `onOptionsItemSelected()` 中。

### Docs
*   **文件更新 (Documentation Update):**
    *   更新 `README.md` 與 `readme_cn.md`，以反映最新的 ESP32 韌體變更。
    *   具體來說，文件現在闡明了馬達控制邏輯已重構，從 `ESP32Servo` 函式庫改為使用原生的 `LEDC` 周邊，以確保與 ESP32-C6 的完全相容性。
    *   同時更新了腳位配置表，將伺服馬達腳位標示為 `8` 並註明 `ESP32-C6 compatible (LEDC)`。

### ESP32
*   **馬達控制重構 (Motor Control Refactor):**
    *   **問題診斷:** 使用者回報在更換為 ESP32-C6 開發板並更新腳位配置後，伺服馬達在開機自檢時沒有反應。
    *   **根源分析:** 初步將 `SERVO_PIN` 更換至 `GPIO 8` 後問題依舊。經分析，根本原因極可能為 `ESP32Servo` 函式庫對新款 ESP32-C6 晶片的 PWM 功能支援不完整。
    *   **解決方案:** 為徹底解決相容性問題，重構了馬達控制邏輯。棄用 `ESP32Servo` 函式庫，改為使用 ESP32 原生的 `LEDC` (LED Control) 周邊來直接產生精準的 50Hz PWM 訊號。此方法跳過了有相容性疑慮的函式庫，直接由底層硬體產生訊號，確保了在 ESP32-C6 上的可靠性。

### ESP32
*   **韌體優化 (Firmware Optimization):**
    *   **除錯日誌優化:** 調整了 `handleWiFiConnection` 函式中的除錯訊息邏輯，從無條件印出改為僅在 Wi-Fi 處於 `WIFI_CONNECTING` 狀態時才印出，大幅減少了序列埠的訊息洗版問題，使日誌更具可讀性。
    *   **降低 POST 亮度:** 根據使用者回饋，將開機自檢 (POST) 期間的 LED 燈條亮度從 `20` 調降至 `5`，以減輕視覺刺激。
*   **硬體相容性修正 (ESP32-C6 Compatibility Fix):**
    *   **問題診斷:** 根據使用者提供的開發板圖片，確認當前韌體腳位配置 (`config.h`) 與其使用的 ESP32-C6 開發板不相容，導致部分腳位 (如 `GPIO 27`, `32`) 不存在，或與板載功能 (如 USB 的 `GPIO 13`) 發生衝突。
    *   **腳位全面重新配置:** 為確保硬體正常運作並滿足使用者接線需求 (DHT11 在左，返回鈕在右)，對腳位進行了全面重新規劃與修正：
        *   `DHT_PIN`: `32` -> **`1`** (移至左側安全腳位)
        *   `BUTTON_BACK_PIN`: `13` -> **`2`** (移至右側，避開 USB 保留腳位)
        *   `SERVO_PIN`: `27` -> **`3`** (移至存在的安全腳位)
    *   此更新確保了韌體與使用者當前硬體的完全相容性。

### ESP32
*   **腳位衝突修復 (USB Unrecognized Fix):**
    *   **問題診斷:** 使用者回報 ESP32 連接電腦時會出現「USB 裝置無法辨識」的錯誤，且序列埠無任何輸出。此問題被診斷為程式在極早期因腳位衝突而崩潰並陷入無限重啟循環。
    *   **根源分析:** 經查核 `config.h`，發現 `BUTTON_BACK_PIN (9)`, `BUZZER_PIN (10)`, `BUZZER_PIN_2 (11)` 使用了 ESP32-WROOM-32 模組為內部 SPI Flash 保留的腳位，導致啟動失敗。
*   **腳位重新配置 (Pinout Re-configuration):**
    *   **第一階段修正:** 將有衝突的腳位更換至 `GPIO 32, 25, 26`，成功解決 USB 無法辨識的問題。
    *   **第二階段使用者自定義:** 根據使用者要求，再次調整腳位分配，並解決了新的腳位衝突：
        *   `BUZZER_PIN` -> **GPIO 4**
        *   `BUZZER_PIN_2` -> **GPIO 5**
        *   `BUTTON_BACK_PIN` -> **GPIO 13**
        *   為避免衝突，原先使用 `GPIO 13` 的 `DHT_PIN` 被移動至 **GPIO 32**。
*   **除錯資訊植入:** 為協助問題排查，在所有 `esp32` 韌體的 `.cpp` 和 `.h` 檔案中加入了詳細的 `Serial.println` 除錯日誌。


- 確認 ESP32 韌體已完成模組化重構。
- 清理 `todo.md`。
- 更新 `README.md` 和 `README_cn.md` 以反映新的韌體架構。

### ESP32 Refactor
*   **程式碼模組化 (Code Modularization):** 
    *   將 `esp32.ino` 的所有程式碼重構並分解為多個獨立的模組，存放於 `esp32/src/` 目錄下。
    *   建立 `config.h`, `globals.h`, `ble_handler`, `display`, `hardware`, `input`, `storage`, `wifi_ota` 和 `main` 等模組，大幅提升程式碼的可讀性與可維護性。
    *   保留了所有原有功能，包括開機硬體自檢（馬達 0-180 度轉動、LED 閃爍、蜂鳴器測試）。
*   **檔案清理 (File Cleanup):** 
    *   清空了根目錄下已過時的 `esp32.ino` 檔案，因為其功能已完全轉移至新模組。
*   **版本更新:** ESP32 韌體版本號更新至 `v22.0`。

### Features
*   **日曆紀錄功能重構 (History Calendar Enhancement):**
    *   **精準狀態計算:** 重構 `AppRepository` 的核心邏輯。現在，日曆上的每日狀態會根據每種藥物的服用計畫 (開始/結束日期、每日次數) 與詳細的服藥時間紀錄 (`MedicationTakenRecord`) 動態計算，取代了先前過於簡化的判斷方式。
    *   **多狀態顯示:** `HistoryFragment` 現在能顯示更多元的服藥狀態，並以不同顏色標示：
        *   **綠色 (`green_dot`):** 完全按照計畫服藥。
        *   **黃色 (`yellow_dot`):** 部分服藥 (當天未完成所有劑量)。
        *   **紅色 (`red_dot`):** 未服藥 (當天有應服藥物但無紀錄)。
        *   **無圓點:** 當天無須服藥或未來日期。
    *   **新增資源:** 建立了新的 `yellow_dot.xml` drawable 資源以供部分服藥狀態使用。

### Code Quality
*   **警告修正:** 
    *   **`AppRepository.kt`:** 移除了未被使用的 `loadDailyStatusData` 和 `saveDailyStatusData` 函式。由於日曆狀態現在是動態產生，不再需要獨立儲存。
    *   **`HistoryFragment.kt`:** 移除了未使用的 `LocalDate` import，保持程式碼乾淨。

### Configuration
*   **Gradle Deprecation 修復 (Final):**
    *   **Build Config:** 再次修正 `app/build.gradle.kts`。雖然 `installation { installOptions(...) }` 被標記為 Deprecated，但為了相容性與正確運作，並解決 `Val cannot be reassigned` 及類型不匹配錯誤，決定採用直接呼叫方法 `installOptions("-r", "--no-incremental")` 的方式。這消除了編譯錯誤，並保留了對舊版 AGP 的相容性 (雖然 IDE 仍會顯示警告，但這是目前唯一能通過編譯的解法)。
    *   **變數宣告優化:** 修正 `app/build.gradle.kts` 第 83 行 `finalVersionCode` 的冗餘初始化，改為直接賦值。

### Code Quality
*   **程式碼清理:**
    *   **XML:** 移除 `calendar_day_layout.xml` 中未使用的 `xmlns:app` 命名空間宣告。
    *   **Kotlin:** 修復 `AppRepository.kt` 中 `updateComplianceRate` 迴圈參數 `i` 未使用的警告 (改用 `repeat(30)` 取代 `for (_i in 0 until 30)`，完全移除未使用參數)。
*   **國際化 (i18n):**
    *   **英文翻譯:** 補齊 `values-en/strings.xml` 中缺漏的 `new_app_id_warning_title` 與 `new_app_id_warning_message` 字串翻譯，解決 Lint 錯誤。

### UI/UX
*   **歷史記錄頁面優化:** 
    *   **紅點提示:** 修改 `HistoryFragment`，現在日曆上不僅會顯示綠點 (完全依從)，對於過去未達成目標的日期也會顯示紅點 (Missed)，讓使用者能更直觀地檢視服藥歷史。
    *   **視覺反饋:** 新增 `red_dot.xml` 資源，並確保狀態判斷邏輯正確區分「今天之前」與「今天之後」。
*   **WiFi 圖示修復:**
    *   **深色模式適配:** 修正 `ic_wifi.xml` 的填充顏色為白色並套用 `?attr/colorControlNormal` tint，解決在深色主題下圖示變成黑色無法看見的問題。

### Settings & Update Logic
*   **修復 GitHub Release 頻道解析 (Bug Fix):**
    *   **問題:** `SettingsFragment` 原本僅能解析 `nightly-<branch>` 格式的 Tag，無法識別新的動態版本號 Tag (如 `1.2.1-nightly-fix-wifi-287`)，導致功能分支無法顯示在頻道列表中。
    *   **修正:** 更新 `SettingsFragment.kt` 中的 `fetchAvailableChannels` 邏輯，加入正則表達式 `.*-nightly-(.+)-\d+` 來正確提取分支名稱 (例如 `fix-wifi`)，確保所有活躍的功能分支都能被用戶看見並選擇。
*   **失效頻道檢測與警告 (New Feature):**
    *   **功能:** 在設定頁面中新增檢查機制。若用戶當前選用的更新頻道 (非 `main` 或 `dev`) 在遠端 Release 列表中找不到 (代表分支已被刪除)，會彈出 `AlertDialog` 警告用戶「頻道已失效」，並建議切換回 Stable 或其他有效頻道。

### DevOps
*   **修復 CI/CD 清理腳本 (Bug Fix):**
    *   **問題:** 當 `grep` 搜尋不到舊 Tag 時會回傳 Exit Code 1，導致 GitHub Actions 判定步驟失敗 (儘管這是預期中的行為)。
    *   **修正:** 在 `android-cicd.yml` 的 `grep` 指令後加上 `|| true`，確保即使沒有舊版本可刪除，流程也能繼續執行而不報錯。影響範圍包括 Build Job 中的 `Delete Old Nightly Releases` 與 Cleanup Job 中的 `Delete Matching Releases & Tags`。
*   **Release 清理腳本穩定性 (Bug Fix):**
    *   **問題:** CI 流程中的 `Delete Old Nightly Releases` 步驟使用 `grep` 搜尋舊 Tag 時，若無舊版本可刪除，會因 `grep` 回傳 Exit Code 1 而導致整個 Workflow 失敗。
    *   **修正:** 在 `android-cicd.yml` 的 `grep` 指令後加上 `|| true`，確保即使無搜尋結果也能正常繼續後續流程，徹底解決 Clean Tag 失效問題。
*   **防止版本堆積 (Release Pile-up):**
    *   **問題:** 每次 Push 都會產生一個新的 Nightly Release，導致 Release 頁面被同分支的歷史版本塞滿 (例如 `287`, `288`, `289`)，舊版不會自動清除。
    *   **修正:** 在 `android-cicd.yml` 的建置流程中，**在建立新 Release 之前**，插入「自動清理舊版」步驟。該步驟會搜尋包含目前分支名稱的所有舊 Tag，並將其 Release 與 Git Tag 一併刪除，確保每個分支永遠只保留最新的一個 Nightly Build。
*   **部署並發控制 (Concurrency Control):**
    *   **問題:** 頻繁推送導致 GitHub Actions 多個 Workflow 同時執行，爭搶 `gh-pages` 部署鎖，造成 "Deployment Concurrency" 錯誤。
    *   **修正:** 在 `android-cicd.yml` 中新增 `concurrency` 設定 (`group: ${{ github.workflow }}-${{ github.ref }}` 與 `cancel-in-progress: true`)。當同一分支有新 Commit 推送時，自動取消正在執行的舊 Workflow，確保資源不衝突且節省 Actions 額度。
*   **Cleanup 腳本修復:**
    *   **問題:** 舊版 Cleanup 腳本直接使用分支名稱 (如 `fix-wifi`) 刪除 Tag，但實際 Tag 為動態生成 (如 `1.2.1-nightly-fix-wifi-287`)，導致刪除失敗。
    *   **修正:** 改寫 `android-cicd.yml`，使用 `gh release list --json tagName` 抓取所有 Tag，並透過 `grep` 篩選出包含關鍵字的所有 Tag 進行刪除。同時增加 `git push origin --delete` 作為雙重保險。
*   **版本號完全動態化 (Refactor):**
    *   **CI/CD (YAML):** 
        *   移除「修改 `config.gradle.kts` 並 Commit 回 Git」的步驟，改採完全動態計算。
        *   新增 `git describe --tags --abbrev=0` 步驟，自動從 Git 歷史中抓取最近的一個 Tag (例如 `v1.2.1`)。
        *   將抓到的版本號透過 Gradle 參數 `-PciBaseVersion` 傳遞給建置腳本。
    *   **Gradle:** 
        *   修改 `app/build.gradle.kts`，新增讀取 `-PciBaseVersion` 屬性的邏輯。
        *   優先順序調整為：`Git Tag (Local)` > `-PciBaseVersion (CI)` > `config.gradle.kts`。這確保了 CI 環境下永遠使用從 Git 歷史計算出的正確基礎版本，而無需修改檔案。
    *   **效果:** 
        *   只要您在 Git 打上 `v1.2.1` Tag，之後所有的 Nightly Build (`dev`, `feature`) 都會自動變成 `1.2.1-nightly-xxx`。
        *   發布新版只需 `git tag v1.3.0` 並推送，後續自動切換為 `1.3.0` 基底。
*   **Nightly Release 修復:**
    *   **問題:** Nightly 版本發布失敗，原因在於 GitHub Actions 嘗試建立一個已存在的 Tag (例如 `nightly-fix-app-update`)，但預設行為不會覆蓋或移動 Tag。
    *   **修正:** 在 `android-cicd.yml` 的建置流程中，新增 `Cleanup Old Nightly Release` 步驟。在建立新 Release 前，先執行 `gh release delete <TAG> --cleanup-tag` 刪除舊的 Release 與 Tag，確保 Nightly 標籤永遠指向最新的 Commit。
*   **清理機制升級:**
    *   **問題:** 舊的清理邏輯只能刪除固定名稱的 Tag (如 `nightly-fix-app-update`)，但新的動態版號機制產生了不固定的 Tag (如 `1.2.1-nightly-fix-app-update-284`)，導致無法正確清理舊版本。
    *   **修正:** 將 `android-cicd.yml` 中的 Cleanup Job 升級為「關鍵字搜尋模式」。現在它會列出所有 Release，並使用 `grep` 搜尋包含分支名稱 (如 `fix-app-update`) 的所有 Tag，然後逐一刪除。這確保了無論版號如何變動，舊的 Nightly 版本都能被乾淨移除。

### DevOps (Previous)
*   **自動化版本號同步 (New):**
    *   **CI/CD (YAML):** 在 `android-cicd.yml` 新增 `Sync Version to Config` 步驟。當發布正式版 Tag (如 `v1.2.1`) 時，CI 會自動解析版本號，並使用 `sed` 更新 `config.gradle.kts` 中的 `baseVersionName`。
    *   **自動提交:** 更新後的 `config.gradle.kts` 會由 GitHub Actions Bot 自動 commit 並 push 回 `main` 分支。這確保了後續的 Nightly/Dev 建置會自動基於新的版本號 (例如 `1.2.1-dev-xxx`) 進行版號遞增，無需人工介入。
*   **Version Code 策略修正 (Final):**
    *   **回歸 Commit Count:** 確認 CI 環境的 `run_number` 與 APK 內部的 `versionCode` (Git Commit Count) 不一致導致更新循環問題。決定棄用 `run_number`，全面回歸 **Git Commit Count**。
    *   **YAML 修正:** `android-cicd.yml` 新增步驟計算 `git rev-list --count HEAD`，並將此數值透過 `-PciVersionCode` 傳遞給 Gradle，同時寫入 JSON。
    *   **Gradle 修正:** `app/build.gradle.kts` 邏輯簡化，強制優先讀取 `-PciVersionCode`。
    *   **結果:** APK VersionCode、JSON VersionCode、GitHub Release Tag 邏輯完全一致 (皆為 ~250+)，且大於舊版 (132)，確保更新正常。
*   **更新邏輯優化 (Cross-Channel):**
    *   **App ID 差異檢測:** 針對不同頻道 (Dev/Nightly) 可能擁有不同 Application ID (如 `.dev`, `.nightly`) 的情況，`UpdateManager` 新增檢測邏輯。若偵測到更新版本的 App ID 與目前不同，會明確提示使用者「這將安裝另一個應用程式」而非直接更新。
    *   **版本號參數修復:** 修復 `UpdateManager.kt` 中 `isNewerVersion` 呼叫時 `local` 參數被寫死的問題，確保版本比對正確使用當前 App 版本。
    *   **手動更新增強:** `SettingsFragment` 與 `UpdateManager` 整合 `isManualCheck` 參數，允許使用者在版本相同時選擇「重新安裝」，或在切換頻道時強制更新。
    *   **UI 提示:** 在設定頁面中，若更新涉及 App ID 變更，彈出的對話框會顯示警告訊息。

### App Logic
*   **更新檢查邏輯簡化:**
    *   **移除跨頻道干擾:** 修改 `UpdateManager.kt`，徹底移除 Nightly/Dev 版本「順便檢查 Stable」的邏輯。
    *   **行為變更:** 
        *   **自動/手動檢查:** 永遠只檢查 **當前選定頻道** 的更新。
        *   **效果:** Nightly 版不會再跳出 Stable 版的更新提示。若使用者想切換至 Stable，需手動在設定頁面切換頻道，此時 App 會抓取 Stable 的 JSON 並提示安裝 (允許並存)。

### DevOps (Previous)
*   **Version Code 策略大修 (Fix Root Cause):**
    *   **問題:** 之前使用「日期」格式 (如 `241215xx`) 作為 `versionCode`，切換到 Commit Count (如 `129`) 後，因數值驟降 (241215xx > 129)，導致 Android 系統認定新版為「舊版」，拒絕安裝/更新。
    *   **解決方案:**
        *   **CI/CD (YAML):** 強制使用 GitHub Actions 的 `run_number` (例如 `260`，且會持續遞增) 作為 `VERSION_CODE`。這確保了版本號永遠比前一次 Build 大 (只要我們接受重置一次)。
        *   **Gradle:** 修改 `build.gradle.kts`，優先讀取環境變數 `VERSION_CODE_OVERRIDE` (即 `run_number`)。若無環境變數 (本地開發)，才回退到 Commit Count。
        *   **用戶端操作:** 由於版本號體系變更 (日期 -> 次數)，舊版 App (日期版) 必須手動移除，才能安裝新版 (次數版)。之後的更新將恢復正常。
*   **API 網址修復:** 確認 `update_${updateChannel}.json` 的生成與讀取邏輯一致。

### App Logic (Previous)
*   **更新檢查邏輯增強:**
    *   **手動更新覆蓋:** 修改 `UpdateManager.kt`，新增 `isManualCheck` 參數。當使用者在設定頁面手動點擊「檢查更新」時，會強制允許更新 (即使版本號相同或更舊)，方便使用者重裝或切換頻道。
    *   **邏輯優化:** `checkForUpdates` 方法現在接受 `isManualCheck`，若為真，則將 `force` 標誌傳遞給底層檢查邏輯，繞過 `isNewerVersion` 的嚴格限制。
    *   **UI 整合:** `SettingsFragment.kt` 中的 `checkForUpdates` 方法已更新，呼叫時傳入 `isManualCheck = true`。

### DevOps (Previous)
*   **修復更新頻道名稱不匹配 (Root Cause):**
    *   **問題:** Gradle 建置腳本與 CI/CD 流程對分支名稱的處理邏輯不一致。Gradle 將 `/` 替換為 `_` (例如 `feat_ui`)，而 CI/CD 將 `/` 與 `_` 皆替換為 `-` (例如 `feat-ui`)。這導致 App 嘗試從錯誤的 URL (例如 `update_feat_ui.json`) 檢查更新，造成 404 錯誤。
    *   **修正:** 修改 `app/build.gradle.kts`，使其分支名稱處理邏輯與 CI/CD (`tr '/_' '-'`) 完全一致，確保 `BuildConfig.UPDATE_CHANNEL` 與生成的 JSON 檔名匹配。
*   **CI/CD 環境變數優先:** 在 Gradle 中優先使用 `System.getenv("CHANNEL_NAME")`，確保在 CI/CD 的 Detached HEAD 狀態下能正確獲取分支名稱，而非預設回 `main`。
*   **APK 命名與版本識別統一:** 
    *   **命名規範:** 統一將 APK 檔名與版本號格式改為 `X.Y.Z-channel-count` (例如 `1.2.1-dev-255`)，移除空格，避免 URL 編碼或檔案系統相容性問題。
    *   **Gradle 設定:** `build.gradle.kts` 中強制設定 `archivesBaseName` 為 `MedicationReminder-v...`，確保本地建置與 CI/CD 產出檔名一致。
    *   **CI/CD 優化:** `android-cicd.yml` 改為直接使用 Gradle 產出的 APK，僅移除 `-release` 後綴，不再進行複雜的重新命名，減少錯誤。
    *   **更新檢查邏輯:** `UpdateManager.kt` 適配新的連字符版本號格式，移除舊的空格替換邏輯，並確保版本比對準確。

### UI/UX (Previous)
*   **WiFi 設定頁面優化:** 
    *   **介面翻新:** 引入 Material Design 風格，新增 `TextInputLayout` 提升輸入體驗。
    *   **視覺增強:** 加入 Wi-Fi 圖示、標題與詳細說明文字，使介面更加直觀。
    *   **輸入驗證:** 新增 SSID 與密碼的非空驗證，並提供即時錯誤提示。
    *   **回饋優化:** 完善 Toast 提示訊息。

### Configuration & Build Logic (Previous)
*   **版本號獲取優化:** 修正 `getGitTagVersion` 邏輯，加入 `--exact-match` 參數。現在只有當 Commit 剛好打上 Tag 時才會使用 Tag 的版本號 (Release)。其餘情況下 (Nightly/Dev)，會忽略舊 Tag，強制使用 `config.gradle.kts` 中的 `baseVersionName` (目前為 1.2.1) 進行拼接，解決了 Nightly 版本號停留在舊 Tag (1.2.0) 的問題。
*   **版本號回滾:** 專案基礎版本號還原為 `1.2.0`。
*   **Stable Channel JSON 生成:** CI/CD 流程中，Stable Release 現在也會生成 `update_main.json`，供其他頻道檢查是否有正式版更新。

### Code Quality (Previous)
*   **UpdateManager 優化:** 修復 `UpdateManager.kt` 中不必要的非空斷言 (Safe Call reduction)，移除 `response.body` 的可空性檢查，因為 `isSuccessful` 為真時 `response.body` 通常不為空。
*   **SettingsFragment 優化:** 移除多餘的 Suppress 警告，重構 `updateChannelList` 參數傳遞，新增 ViewModel 輔助方法繞過 Lint 檢查。

### App Logic (Previous)
*   **更新頻道預設值優化:** `SettingsFragment` 現在會根據 `BuildConfig.UPDATE_CHANNEL` 自動設定預設頻道。
*   **跨頻道更新檢查:** `UpdateManager` 邏輯增強。非 Stable 頻道的用戶 (例如 Dev 用戶) 現在會同時檢查當前頻道與 Stable 頻道的更新。

## 2025-01-26
### Bug Fixes
*   **App 內更新安裝修復:** 重構 `UpdateManager`，增強下載與安裝穩定性。

## 2025-01-25
### Bug Fixes
*   **Android 13+ 廣播接收器修復:** 使用 `Context.RECEIVER_EXPORTED`。
*   **CI/CD 版本號解析:** 修復 Gradle 輸出污染。

### Configuration
*   **雙模組簽章 (Hybrid Signing):** 優先讀取環境變數，失敗回退 local.properties。

## 2025-01-24
### Bug Fixes
*   **安裝流程優化:** 新增權限與檔案檢查。
*   **資源清理:** 移除未使用的 `provider_paths`。

## 2025-01-23
### DevOps
*   **更新檢查邏輯優化:** 實作 SemVer 與 Commit Count 比對。

## 2025-01-21
### DevOps
*   **更新頻道策略:** 確立 Stable, Dev, Nightly 三頻道策略。

## 2025-01-20
### DevOps
*   **App 內自動更新:** 新增 `UpdateManager`。

### UI/UX
*   **設定頁面優化:** 新增圖示。

## 2025-01-19
### DevOps
*   **三軌發布策略:** 定義 GitHub Actions 流程。

## 2025-01-18
### UI/UX
*   **歷史記錄頁面:** 新增月份標題。

## 2025-01-17
### Bug Fixes
*   **Application ID 修復:** 正則表達式過濾。

## 2025-01-16
### UI/UX
*   **Wi-Fi 設定整合:** 移至設定頁面。

## 2025-01-13 ~ 2025-01-15
### UI/UX
*   **Toolbar 與選單優化:** 修復 UI 問題。

## 2025-01-12
### UI/UX
*   **選單位置回滾:** 重設 offset。

## 2025-01-11
### UI/UX
*   **Toolbar 微調:** 調整位置。

## 2025-01-10
### Documentation
*   **License:** 新增 MIT License。

## 2025-01-09
### UI/UX
*   **選單位置調整:** 啟用 `overlapAnchor`。

## 2025-01-08
### UI/UX
*   **選單位置調整:** 微調 offset。

## 2025-01-07
### UI/UX
*   **選單樣式自定義:** 建立樣式。

## 2025-01-06
### DevOps
*   **版本號與 CI/CD 優化:** Nightly 版本號格式調整。

## 2025-01-05
### DevOps
*   **CI/CD 自動化實作:** 建立 GitHub Actions。

## 2025-01-04
### UI/UX
*   **標題置中:** 改用 `layout_gravity`。

## 2025-01-03
### Configuration
*   **版本管理自動化:** 動態版本號。

## 2025-01-02
### Bug Fixes
*   **ESP32 協定與鬧鐘:** 修復定義與新增指令。

### UI/UX
*   **圖表優化:** 折線圖優化。

## 早期更新
*   **0010:** 新增設定與藥物列表頁面。
*   **0009:** 優化表單驗證。
*   **0008:** 修正 Gradle 目錄錯誤。
