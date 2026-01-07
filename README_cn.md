# Medication Reminder App (藥到叮嚀)

A smart medication reminder application integrated with an ESP32-based smart pillbox. It helps users track their medication schedule, monitors environmental conditions, and ensures timely intake through a combination of mobile notifications and hardware alerts.

[![Android CI/CD](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml/badge.svg)](https://github.com/thumb2086/Medication_reminder/actions/workflows/android-cicd.yml)

## 功能特色

*   **智慧提醒：** 可自訂藥物排程，包含頻率與時間設定。
*   **藥物庫存管理與補充提醒：** 追蹤藥物庫存量，並在存量不足時接收及時通知，確保您永不斷藥。
*   **詳細服藥報告：** 生成包含服藥依從率的綜合藥物報告，透過互動式圖表可視化週、月或季度數據。
*   **硬體整合：** 透過藍牙低功耗 (BLE) 與 ESP32 智慧藥盒無縫連接。
*   **硬體確認服藥:** 當藥盒鬧鐘響起時，直接按下藥盒上的實體按鈕即可確認服藥。訊號會透過藍牙傳回 App，自動更新藥物庫存與服藥紀錄，無需操作手機。
*   **智慧藥盒引導:** 在 App 中點選藥物後，可遠端遙控藥盒旋轉至指定藥槽，同時對應的 LED 燈會亮起，提供清晰的取藥指引。
*   **即時監控：** 顯示來自藥盒感測器的即時溫濕度數據。
*   **服藥追蹤：** 透過多狀態指示器視覺化您的服藥歷史，並計算 30 天的服藥依從率。
    *   **綠點：** 按照計畫完成所有服藥。
    *   **黃點：** 部分服藥 (錯過某些劑量)。
    *   **紅點：** 在排定的日期未服用任何藥物。
*   **字體大小調整：** 使用者可在設定選單中選擇「小」、「中」、「大」三種字體大小，以提升閱讀舒適度。App 主題將立即更新以反映所選尺寸。
*   **角色主題：** 提供「庫洛米」、「櫻桃小丸子」、「蠟筆小新」與「哆啦A夢」等多款主題，打造個人化體驗。
*   **統一 UI 風格：** 全 App 統一的按鈕樣式，提供更一致的使用者體驗。
*   **工程模式：** 可直接從 App 切換硬體工程模式以進行診斷。
*   **Wi-Fi 設定：** 透過 BLE 直接設定 ESP32 的 Wi-Fi 連線。介面已全面升級為 Material Design 風格，並加入輸入驗證與清晰指引。
*   **鬧鐘系統：** 可在 ESP32 藥盒上設定最多 4 組硬體鬧鐘，提供獨立提醒功能。
*   **互動圖表：** 透過互動式折線圖檢視溫濕度趨勢，支援平移、縮放與數據點查看。
*   **App 內更新：** 自動檢查 GitHub Releases 上的新版本。
    *   **頻道選擇：** 使用者可在設定頁面選擇 **Stable (穩定版)**、**Dev (開發版)** 或瀏覽 **活躍中的開發分支**。
    *   **跨頻道切換：** 自由切換頻道 (例如從 Dev 切換到 Stable) 並安裝該分支的最新版本。
    *   **智慧預設頻道：** App 會根據當前安裝的版本自動設定預設更新頻道。
        *   **Stable 版本：** 預設為 `Main` 頻道。
        *   **Dev 版本：** 預設為 `Dev` 頻道。
        *   **功能分支版本：** 預設為該功能分支頻道 (例如 `feat-new-ui`)。
    *   **動態更新檢查：** 智慧抓取對應頻道的最新版本資訊 (例如 `update_dev.json`, `update_nightly.json`)。
        *   **安全檢查：** 偵測更新是否屬於不同頻道 (Application ID)，並警告使用者這將安裝另一個應用程式實例，而非原地更新。
    *   **Stable：** 來自 `main` 分支的正式發布版本。
    *   **Dev：** 來自 `dev` 分支的最新開發版本。
    *   **動態分支發現：** App 會查詢 GitHub Releases 以尋找活躍的分支，讓您能輕鬆測試特定功能分支。
    *   **無效頻道警告：** 自動檢測目前選擇的功能分支是否已被刪除或停止維護，並主動提示使用者切換頻道。
*   **強健的更新安裝：** 智慧處理 APK 下載，具備自動 fallback 機制，確保在各種 Android 版本 (包含 Android 13+). 上皆能成功安裝。
*   **多頻道 CI/CD：** 支援動態「功能分支」發布。每個分支都有獨立的更新頻道 (例如 `feat-new-ui`)，允許並行測試而不互相干擾。

## ESP32 韌體

ESP32 韌體採用模組化架構設計，以實現高可讀性與可維護性，所有主要元件皆存放於 `esp32/src/` 目錄中。

### 核心模組
*   **`main.ino`**: 主進入點，協調不同模組的運作。
*   **`ble_handler`**: 管理所有藍牙低功耗 (BLE) 通訊。
*   **`display`**: 處理所有螢幕繪圖和 UI 邏輯。
*   **`hardware`**: 控制硬體周邊設備（馬達、蜂鳴器、感測器）。此模組已重構，改為使用 **ESP32 原生 LEDC** 周邊來控制伺服馬達，確保對 ESP32-C6 的相容性與精準的 PWM 訊號產生。
*   **`input`**: 管理來自旋轉編碼器和按鈕的使用者輸入。
*   **`storage`**: 處理快閃記憶體儲存操作 (SPIFFS, Preferences)。
*   **`wifi_ota`**: 管理 Wi-Fi 連接、NTP 同步和 OTA 更新。
*   **`config.h`**: 集中化的常數、腳位定義與設定。
*   **`globals.h`**: 全域變數宣告。

### 腳位配置 (`config.h`)

韌體針對 ESP32-C6 開發板進行了特定的腳位配置。**警告：** 使用為內部 Flash 記憶體保留的腳位 (例如 GPIO 6-11) 或其他專用功能腳位 (如 USB 的 GPIO 12/13) 會導致裝置崩潰。預設配置採用了經過測試的安全腳位。

| 功能 | 腳位 | 備註 |
| :--- | :---: | :--- |
| I2C SDA | 22 | 用於 OLED 螢幕 |
| I2C SCL | 21 | 用於 OLED 螢幕 |
| 編碼器 A | 19 | 旋轉編碼器 |
| 編碼器 B | 18 | 旋轉編碼器 |
| 編碼器按鈕 | 20 | 旋轉編碼器按鈕 |
| 確認按鈕 | 23 | | 
| 返回按鈕 | 2 | 位於右側 |
| DHT 感測器 | 1 | 位於左側，DHT11 溫濕度 |
| 蜂鳴器 1 | 4 | 位於左側 |
| 蜂鳴器 2 | 5 | 位於左側 |
| 伺服馬達 | 8 | ESP32-C6 相容 (LEDC) |
| WS2812 LED 燈條| 15 | | 

## 藍牙低功耗協定

App 與 ESP32 智慧藥盒之間的通訊基於自訂的二進位協定，透過 BLE UART 服務傳輸。

**服務 UUID：** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
**TX 特徵值 (寫入)：** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
**RX 特徵值 (通知)：** `c8c7c599-809c-43a5-b825-1038aa349e5d`

### 指令參考 (App -> ESP32)

| 指令名稱         | OpCode | 參數                                                   | 說明                                         |
|:-------------|:------:|:-----------------------------------------------------|:-------------------------------------------|
| **取得協定版本**   | `0x01` | 無                                                    | 請求裝置回報協定版本 (回傳 `0x71`)。                    |
| **同步時間**     | `0x11` | `年(1B)`, `月(1B)`, `日(1B)`, `時(1B)`, `分(1B)`, `秒(1B)` | 同步 RTC 時間。年份為相對於 2000 年的值 (例如 24 代表 2024)。 |
| **設定 Wi-Fi** | `0x12` | `SSID長度(1B)`, `SSID(...)`, `密碼長度(1B)`, `密碼(...)`     | 傳送 Wi-Fi 連線資訊至裝置。                          |
| **設定工程模式**   | `0x13` | `啟用(1B)`                                             | `0x01`：啟用，`0x00`：停用。                       |
| **取得工程模式狀態** | `0x14` | 無                                                    | 查詢目前工程模式是否啟用 (回傳 `0x83`)。                  |
| **取得狀態**     | `0x20` | 無                                                    | 請求目前藥盒狀態 (回傳 `0x80`)。                      |
| **取得環境數據**   | `0x30` | 無                                                    | 單次請求目前溫濕度 (回傳 `0x90`)。                     |
| **取得歷史數據**   | `0x31` | 無                                                    | 請求儲存的環境歷史記錄 (回傳一系列 `0x91`，以 `0x92` 結束)。    |
| **訂閱即時數據**   | `0x32` | 無                                                    | 啟用環境數據自動推送。                                |
| **設定鬧鐘**     | `0x41` | `Slot(1B)`, `時(1B)`, `分(1B)`, `啟用(1B)`               | 設定硬體鬧鐘。`Slot`：0-3，`啟用`：1/0。                |
| **引導藥盒**     | `0x42` | `Slot(1B)`                                           | 指示藥盒旋轉至指定藥槽 (1-8)。                         |

### 回應參考 (ESP32 -> App)

| 回應名稱 | OpCode | 資料負載 | 說明 |
| :--- | :---: | :--- | :--- |
| **協定回報** | `0x71` | `版本(1B)` | 回報協定版本 (例如 `0x03`)。 |
| **狀態回報** | `0x80` | `SlotMask(1B)` | 藥盒格位的狀態位元遮罩。 |
| **服藥通知** | `0x81` | `SlotID(1B)` | 由藥盒實體按鈕觸發，回報使用者已取藥。 |
| **時間同步確認** | `0x82` | 無 | 確認時間已同步。 |
| **工程模式回報** | `0x83` | `狀態(1B)` | `0x01`：已啟用，`0x00`：已停用。 |
| **環境數據** | `0x90` | `溫度(2B)`, `濕度(2B)` | 即時感測器數據。數值為 `Short` (x100)。 |
| **歷史數據** | `0x91` | `時間戳(4B)`, `溫度(2B)`, `濕度(2B)` | 一筆或多筆歷史記錄。 |
| **同步完成** | `0x92` | 無 | 表示歷史數據傳輸結束。 |
| **錯誤回報** | `0xEE` | `錯誤碼(1B)` | `0x02`：感測器錯誤，`0x03`：未知指令，`0x04`：存取錯誤。 |

## 藍牙協定版本控制

為確保 App 與 ESP32 韌體在功能演進過程中的相容性，引入了協定版本控制機制。

*   **握手：** 連線建立後，App 會向 ESP32 請求協定版本 (指令 `0x01`)。
*   **向下相容：** App 會根據回報的版本調整資料解析邏輯。
    *   **第 1 版：** 舊版協定 (單筆歷史記錄)。
    *   **第 2 版：** 批次歷史數據傳輸 (每封包 5 筆記錄) 與優化的整數型感測器數據格式。
    *   **第 3 版：** 新增鬧鐘設定支援 (指令 `0x41`)。

## 快速開始

1.  **複製專案：** `git clone https://github.com/thumb2086/Medication_reminder.git`
2.  **使用 Android Studio 開啟：** 將專案匯入 Android Studio (建議使用 Ladybug | 2024.2.1 或更新版本)。
3.  **建置並執行：** 連接 Android 裝置 (Android 10+) 或使用模擬器執行應用程式。

## CI/CD 與版本控制

本專案使用 GitHub Actions 進行持續整合與自動化版本管理。

*   **Stable 發布：** 推送以 `v` 開頭的標籤 (例如 `v1.1.8`) 時觸發。會在 `main` 頻道建立永久發布版本。
*   **Feature/Dev 發布：** 推送至 `dev`, `fix-*`, 或 `feat-*` 分支時觸發。
    *   為該分支產生專屬的更新頻道 (例如 `update_feat_login.json`)。
    *   建置對應版本名稱的 APK。
    *   安裝特定分支 APK 的測試人員將只會收到該分支的更新。
*   **統一命名：** 所有產出物 (APK) 與版本名稱嚴格遵循 `X.Y.Z-channel-count` 格式 (例如 `1.2.1-dev-255`)，移除空格與特殊字元，確保跨環境行為一致。
*   **分支清理：** 當分支被刪除時，對應的 Nightly Release 與 Tag 也會自動移除，保持發布列表整潔。亦支援透過 GitHub Actions workflow dispatch 手動清理。
*   **版本控制：** `versionCode` 採用 **Git Commit Count** 以確保 Android Build 與 CI Artifacts 的嚴格一致性。`versionName` 則採用 `1.2.1-dev-260` 格式。
*   **發布命名：** Nightly Release 現在使用更清晰的標題格式：`<分支> | <版本名稱>` (例如 `feat-ui | 1.2.0-nightly-205`)，方便識別來源分支與版本細節。
*   **動態基礎版本：** CI/CD 自動偵測最新的 Git Tag (例如 `v1.2.1`) 作為所有後續 Nightly 建置的基礎版本，確保版本名稱始終反映最新的穩定里程碑 (例如 `1.2.1-nightly-xxx`)。
*   **部署並發控制：** 實作並發控制，透過自動取消同一分支上的舊有工作流程，防止 `gh-pages` 部署衝突。

## 授權條款

[MIT License](LICENSE)

---

## 專案架構

本專案主要包含兩大部分：Android 應用程式 (`app/`) 和 ESP32 韌體 (`esp32/`)。

#### Android 應用程式 (`app/`)

-   **`app/src/main/AndroidManifest.xml`**：定義應用程式的核心屬性、權限和元件（活動、服務、廣播接收器）。
-   **`app/src/main/java/com/example/medicationreminderapp/`**：包含 Android 應用程式的 Kotlin 原始碼，組織成多個子套件和頂層檔案：
    -   **`di/`**：依賴注入設定（使用 Hilt）。
        -   `AppModule.kt`：定義用於在應用程式中提供依賴的模組。
    -   **`ui/`**：與 UI 相關的元件，主要為 Fragments 和 ViewModels。
        -   `LogFragment.kt`：顯示應用程式日誌。
        -   `MainViewModel.kt`：`MainActivity` 的 ViewModel，管理與 UI 相關的數據和邏輯。
        -   `ReminderFragment.kt`：用於顯示藥物提醒的 Fragment。
        -   `ViewPagerAdapter.kt`：用於管理 ViewPager 中 Fragment 的適配器。
        -   `EnvironmentFragment.kt`：顯示來自 ESP32 的即時環境數據。
    -   **`adapter/`**：用於 RecyclerView 和其他基於列表的 UI 元件的適配器。
        -   `MedicationListAdapter.kt`：用於顯示藥物列表的適配器。
    -   **`util/`**：用於各種輔助功能的工具類。
        -   `UpdateManager.kt`：處理應用程式內更新邏輯，包括獲取和安裝更新。
        -   `SingleLiveEvent.kt`：用於一次性事件的自訂 LiveData 實作。
    -   **`Medication.kt`**：表示藥物的數據類。
    -   **`BaseActivity.kt`**：提供通用功能（例如主題應用、字體大小調整）的基本 Activity 類。
    -   **`BootReceiver.kt`**：在裝置重新啟動後重新排程鬧鐘的 BroadcastReceiver。
    -   **`MainActivity.kt`**：應用程式的主要入口點，託管各種 Fragment 並管理整體 UI 流程。
    -   **`AlarmReceiver.kt`**：用於處理排程藥物鬧鐘的 BroadcastReceiver。
    -   **`AppRepository.kt`**：中央數據儲存庫，抽象化數據源（例如本地數據庫、藍牙）。
    -   **`AlarmScheduler.kt`**：管理藥物鬧鐘的排程和取消。
    -   **`SnoozeReceiver.kt`**：用於處理通知中貪睡操作的 BroadcastReceiver。
    -   **`HistoryFragment.kt`**：顯示使用者的服藥歷史記錄。
    -   **`SensorDataPoint.kt`**：環境感測器讀數（溫度、濕度、時間戳）的數據類。
    -   **`SettingsFragment.kt`**：顯示和管理應用程式設定（例如主題、語言、更新頻道）。
    -   **`BluetoothLeManager.kt`**：管理與 ESP32 智慧藥盒的藍牙低功耗 (BLE) 通訊。
    -   **`WiFiConfigFragment.kt`**：用於在 ESP32 裝置上配置 Wi-Fi 設定的 Fragment。
    -   **`ImagePickerPreference.kt`**：用於在設定中選擇圖像的自訂 Preference 類。
    -   **`MedicationListFragment.kt`**：用於顯示和管理藥物列表的 Fragment。
    -   **`MedicationTakenReceiver.kt`**：用於處理來自 ESP32 的「已服藥」事件的 BroadcastReceiver。
    -   **`ReminderSettingsFragment.kt`**：用於配置特定提醒設定的 Fragment。
    -   **`MedicationReminderApplication.kt`**：自訂 `Application` 類，主要用於 Hilt 初始化和全域應用程式設定。
-   **`app/src/main/res/`**：包含所有應用程式資源（佈局、可繪製資源、值等）。
    -   **`drawable/`**：可繪製資源（圖示、圖像、XML 可繪製資源）。
    -   **`layout/`**：活動、Fragment 和列表項目的 XML 佈局檔案。
    -   **`menu/`**：定義應用程式選單的 XML 檔案。
    -   **`xml/`**：用於偏好設定和其他配置的 XML 檔案（例如 `preferences.xml`）。
    -   **`values/`**：預設字串、顏色、樣式和主題定義。
    -   **`values-en/`**：字串的英文翻譯。
    -   **`values-night/`**：深色主題模式特有的資源。
    -   **`mipmap-*/`**：不同密度的啟動器圖示。

#### ESP32 韌體 (`esp32/`)

-   **`esp32/src/`**：包含 ESP32 韌體的 C++ 原始碼，組織成模組化元件。
    -   **`main.ino`**：ESP32 程式的主要進入點，協調其他模組的運作。
    -   **`ble_handler.cpp/.h`**：管理藍牙低功耗 (BLE) 通訊。
    -   **`display.cpp/.h`**：處理 OLED 顯示螢幕繪圖和 UI 邏輯。
    -   **`hardware.cpp/.h`**：控制硬體周邊設備（馬達、蜂鳴器、感測器）。
    -   **`input.cpp/.h`**：管理來自旋轉編碼器和按鈕的使用者輸入。
    -   **`storage.cpp/.h`**：處理持久性儲存操作（SPIFFS、Preferences）。
    -   **`wifi_ota.cpp/.h`**：管理 Wi-Fi 連接、NTP 同步和無線 (OTA) 更新。
    -   **`config.h`**：用於硬體腳位定義、常數和其他配置的中央標頭檔。
    -   **`globals.h`**：用於全域變數宣告的標頭檔。

## 角色補充包發布流程

此 App 支援從本儲存庫動態更新角色補充包 (圖片、主題)，讓新角色的發布無需更新 App。

1.  **上傳圖片資源**
    將新的角色圖片 (例如 `snoopy.png`) 上傳至 `app/src/main/res/drawable-nodpi/` 目錄。

2.  **修改 `characters.json`**
    編輯位於本儲存庫根目錄的 `characters.json` 檔案，為新角色新增一個 JSON 物件。每個物件需包含以下欄位：
    *   `id`: 角色的唯一識別碼 (字串，僅限英數與底線)。
    *   `name`: 顯示在 App 中的角色名稱。
    *   `imageResName`: 圖片的完整檔名 (例如 `snoopy.png`)。
    *   `imageUrl`: 圖片在 GitHub 上的 **Raw URL**。

    **如何取得 Raw URL:**
    a. 在 GitHub 儲存庫中點擊您上傳的圖片檔案。
    b. 點擊 **"Raw"** 按鈕。
    c. 從瀏覽器的網址列複製完整的 URL。

    **範例：**
    ```json
    [
      {
        "id": "kuromi",
        "name": "酷洛米",
        "imageResName": "kuromi.png",
        "imageUrl": "https://raw.githubusercontent.com/thumb2086/Medication_reminder/main/app/src/main/res/drawable-nodpi/kuromi.png"
      },
      {
        "id": "snoopy",
        "name": "史努比",
        "imageResName": "snoopy.png",
        "imageUrl": "https://raw.githubusercontent.com/thumb2086/Medication_reminder/main/app/src/main/res/drawable-nodpi/snoopy.png"
      }
    ]
    ```

3.  **Commit & Push**
    將修改後的 `characters.json` 和新的圖片資源 Commit 並 Push 至 `main` 分支。

完成後，已安裝的 App 將在下次啟動時自動檢查並下載新的角色包，使用者即可在設定中看到並選用新角色。
