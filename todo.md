# Medication Reminder - Unified Development Roadmap

## Epic 2: 智慧互動核心 (Smart Interaction Core)
此史詩專注於實現 App 與智慧藥盒的核心互動，包含連線穩定性、放藥引導與服藥確認，打造無縫的硬體整合體驗。

### v1.3.0: 藍牙自動重連
- [x] **體驗優化: 藍牙自動重連**
  - **詳細步驟:**
    - [x] 在 `BluetoothLeManager` 的斷線邏輯中，新增一個 flag 來判斷是否為意外斷線。
    - [x] 建立一個 `startReconnectSequence()` 方法，使用協程的 `delay` 實現有次數限制、有延遲的重連嘗試。
    - [x] 在 `BleListener` 介面新增 `onReconnectStarted` 和 `onReconnectFailed` 回調。
    - [x] 在 `ViewModel` 中實作新的回調，更新 UI 狀態以告知使用者「正在嘗試重連...」。

### v1.3.1: 智慧放藥引導
- [x] **App 端: 智慧放藥引導**
  - **詳細步驟:**
    - [x] 在新增/編輯藥物的流程中，增加一個「引導」按鈕。
    - [x] 點擊「引導」後，App 透過藍牙傳送指令到 ESP32。
    - [x] 在 `BluetoothLeManager` 中新增對應的 BLE 通訊方法 (`guidePillbox`)。
- [x] **ESP32 端: 馬達控制**
  - **詳細步驟:**
    - [x] 在 `ble_handler.cpp` 中新增對 `0x42` (CMD_GUIDE_PILLBOX) 指令的處理。
    - [x] 在 `config.h` 中定義 `CMD_GUIDE_PILLBOX`。
    - [x] 在 `hardware.cpp` 中實作 `guideToSlot(int slot)` 函式，驅動馬達轉動到指定藥倉。

### v1.3.2: 硬體確認服藥
- [ ] **App 端: 硬體確認服藥**
  - **詳細步驟:**
    - [ ] 在 `BluetoothLeManager` 中新增監聽來自 ESP32「已服藥」訊號的邏輯。
    - [ ] 當收到訊號時，App 自動將對應的藥物標記為「已服用」，並更新 UI。
    - [ ] 考慮邊界情況，例如在非服藥時間按下按鈕的處理。
- [ ] **ESP32 端: 按鈕回報**
  - **詳細步驟:**
    - [ ] 在 `hardware.cpp` 或 `input.cpp` 中實作讀取藥盒實體按鈕狀態的邏輯。
    - [ ] 當按鈕被按下時，透過 `ble_handler.cpp` 中的 `sendMedicationTaken()` 函式，主動發送「已服藥」訊號給 App。


## Epic 3: 架構與數據 (Architecture & Data)
此史詩專注於升級 App 的底層架構，並基於新的資料庫結構提供更豐富的數據管理與洞察功能。

### v1.4.0: 架構升級 - 資料庫遷移
- [ ] **基礎架構: 資料庫遷移 (Room)**
  - **詳細步驟:**
    - [ ] 在 `app/build.gradle.kts` 中加入 Room 的依賴。
    - [ ] 建立 `MedicationEntity` 和 `TakenRecordEntity`。
    - [ ] 建立對應的 DAO 介面 (`MedicationDao`, `TakenRecordDao`)。
    - [ ] 建立 `AppDatabase.kt`。
    - [ ] 重構 `AppRepository`，改為呼叫 DAO。
    - [ ] 實作一個一次性的資料遷移邏輯，將舊的 SharedPreferences 數據寫入 Room。

### v1.4.1: 數據管理 - 庫存提醒
- [ ] **核心功能: 藥物庫存管理與補充提醒**
  - **詳細步驟:**
    - [ ] 在 `MedicationEntity` 中新增 `reminderThreshold: Int` 欄位，並更新資料庫。
    - [ ] 在新增/編輯藥物的介面中，增加設定提醒閾值的輸入框。
    - [ ] 在 `AppRepository` 的 `processMedicationTaken` 方法中，增加檢查庫存是否低於閾值的邏輯。
    - [ ] 若低於閾值，觸發一個本地通知提醒使用者。

### v1.4.2: 數據洞察 - 服藥報告
- [ ] **數據呈現: 詳細服藥報告 & 匯出與分享**
  - **詳細步驟:**
    - [ ] 在 `HistoryFragment` 中新增「週/月/季度」的切換按鈕。
    - [ ] 在 `ViewModel`/`Repository` 新增方法，根據時間範圍從 Room 查詢聚合數據。
    - [ ] 使用圖表庫將數據視覺化。
    - [ ] 建立 `ReportGenerator` 類別，將數據格式化成 PDF 或 CSV。
    - [ ] 新增「分享」按鈕，使用 `Intent.ACTION_SEND` 分享報告。

## Epic 4: 便利性與擴展 (Convenience & Expansion)
此史詩專注於提供更多便利工具，並擴展 App 的保護網，提升整體使用價值。

### v1.5.0: 韌體空中升級 (OTA)
- [ ] **App 端: 韌體空中升級 (OTA)**
  - **詳細步驟:**
    - [ ] 在設定中建立新的韌體更新頁面。
    - [ ] 實作選擇 `.bin` 韌體檔案的邏輯。
    - [ ] 在 `BluetoothLeManager` 新增 `startOtaUpdate(firmware: ByteArray)` 方法，將韌體分塊寫入 ESP32。
    - [ ] 實作 UI 上的進度條來顯示更新進度。
- [ ] **ESP32 端: 韌體 OTA 功能**
  - **詳細步驟:**
    - [ ] 整合 `ArduinoOTA` 或自訂的 BLE OTA 函式庫。
    - [ ] 建立一個 BLE 特徵，用於接收韌體檔案的分塊數據。
    - [ ] 實作韌體驗證與寫入 Flash 的安全邏輯。

### v1.5.1: 便利工具 - 桌面小工具
- [ ] **便利工具: 桌面小工具 (Widget)**
  - **詳細步驟:**
    - [ ] 建立 `AppWidgetProvider` 類別。
    - [ ] 設計 Widget 的 XML 佈局。
    - [ ] 實作一個 `Service` 或 `Worker` 來定期更新 Widget 內容 (下次用藥時間等)。
    - [ ] 處理 Widget 的點擊事件，例如點擊後打開 App。

### v1.5.2: 智慧安全網路
- [ ] **漏服藥轉發通知 (Missed Dose Alerts):**
  - **詳細步驟:**
    - [ ] 在設定中新增介面，讓使用者可以選擇一位聯絡人。
    - [ ] 實作一個 `AlarmReceiver` 或 `Worker`，在用藥時間過後的一段時間（例如 1 小時）觸發檢查。
    - [ ] 如果藥還沒吃，使用 `SmsManager` 或 `Intent.ACTION_SEND` 發送 SMS 或啟動通訊軟體。
    - [ ] 確保在發送前取得使用者同意及必要的權限 (`SEND_SMS`)。

### v1.5.3: 體驗優化
- [ ] **體驗優化: 無縫主題更新**
  - **詳細步驟:**
    - [ ] 研究並選擇一個不停用 `recreate()` 的主題更新方案 (例如，手動遍歷 View 並更新相關屬性)。
    - [ ] 重構 `SettingsFragment` 中的主題切換邏輯。

### v1.5.4: 藥物穩定性警報
- [ ] **核心功能: 藥物穩定性警報**
  - **詳細步驟:**
    - [ ] **App 端:** 建立一個可擴充的藥物儲存條件資料庫 (可先用 Hardcode 實作)。
    - [ ] **App 端:** 在新增/編輯藥物時，允許使用者選擇特定藥物 (如：胰島素)，並連結到其儲存條件。
    - [ ] **App 端:** 在 `BluetoothLeManager` 中新增邏輯，當收到溫濕度數據時，檢查是否超出與該藥物關聯的安全範圍。
    - [ ] **App 端:** 若超出範圍，觸發一個高優先級的本地通知警告使用者。
  - [ ] **ESP32 端: 環境回報**
    - **詳細步驟:**
      - [ ] 在 `hardware.cpp` 中，實作讀取溫濕度感測器 (DHT) 數據的邏輯。
      - [ ] 透過 `ble_handler.cpp` 中的 `sendSensorDataReport()` 或 `sendRealtimeSensorData()` 函式，將數據定時回報給 App。

## Epic 5: 韌體重構與維護 (Firmware Refactoring & Maintenance)
此史詩專注於改善 ESP32 韌體的程式碼品質與可維護性。

### v22.1.1: Code Cleanup
- [x] **重構:** 移除 `esp32/src/config.h` 中關於 `v22.7` 的多餘註解，避免版本號混淆。

---

## 未來規劃 (Future Considerations)

### 核心體驗 & UI/UX
- [ ] **藥物照片對照 (Medication Photo ID):** 允許使用者為每種藥物拍攝並儲存一張實際照片，在服藥提醒時顯示以供視覺核對。
- [ ] **AR 擴增實境導覽 (Augmented Reality Onboarding):** 以 AR 方式提供更直覺的首次設定體驗。
- [ ] **多使用者支援 (Multi-User Support):** 讓 App 可以建立多個 Profile，方便家人共用裝置。
- [ ] **旅行模式 (Travel Mode):** 跨時區旅行時，自動調整提醒時間並生成打包清單。
- [ ] **藥品條碼掃描設定 (Medication Barcode Scanning Setup):** 掃描藥盒上的條碼快速設定藥物資訊。
- [ ] **UI優化: 常用藥物選單:** 在輸入表單預製常用藥物的下拉式選單。
- [ ] **UI優化: 時間選擇圖示:** 在新增/編輯時間的區塊加入日曆或時鐘圖示，強化視覺引導。

### 數據、AI 與智慧
- [ ] **AI 生成服藥報告 (AI-Generated Compliance Reports):** 自動生成自然語言格式的服藥總結報告，方便直接提供給醫師。
- [ ] **AI 驅動的健康助理 (AI-Powered Health Assistant):** 內建 AI 聊天機器人，回答自然語言提問並提供個人化健康提示。
- [ ] **藥丸視覺辨識 (Pill Visual Recognition):** 用相機辨識藥丸種類，避免使用者誤食。
- [ ] **進階數據分析與洞察 (Advanced Analytics & Insights):** 分析長期服藥依從性趨勢，找出個人行為模式。
- [ ] **藥物資訊與副作用追蹤 (Medication Info & Side-Effect Logging):** 串接藥物資料庫，並提供副作用記錄功能。
- [ ] **症狀嚴重程度分級 (Symptom Severity Grading):** 記錄副作用時，可分級標示其嚴重程度。
- [ ] **藥物劑量遞減/遞增計畫 (Tapering/Titration Schedules):** 支援需逐步增減劑量的複雜服藥計畫。
- [ ] **智慧提醒微調 (Intelligent Reminders):** 根據使用者的作息模式，動態調整提醒時間。

### 生態系整合 & 擴充
- [ ] **醫療院所系統串接 (Healthcare Provider Integration - via FHIR):** 串接電子病歷，自動同步處方與服藥紀錄。
- [ ] **藥局合作與自動補充 (Pharmacy Integration & Automated Refills):** 與藥局 API 合作，一鍵自動補充藥物。
- [ ] **語音互動整合 (Voice Integration):** 整合 Google 助理，用語音查詢或回報服藥狀態。
- [ ] **智慧家庭場景連動 (Smart Home Routine Integration):** 融入 Google Home 的日常安排，自動觸發提醒。
- [ ] **Wear OS 副應用 (Wear OS Companion App):** 在手錶上顯示下次服藥提醒，並可直接標記為已服用。
- [ ] **健身追蹤器數據整合 (Fitness Tracker Integration):** 串接 Fitbit、Garmin 等裝置，交叉比對健康數據。
- [ ] **健康平台整合 (Health App Integration):** 將服藥紀錄寫入 Google Fit 或 Samsung Health。
- [ ] **雲端備份與還原 (Cloud Backup & Restore):** 跨裝置同步使用者的藥物設定與紀錄。

### 安全與隱私
- [ ] **過量服用警報 (Overdose Alert):** 當系統偵測到短時間內重複服藥，將觸發高優先級警報。
- [ ] **緊急資訊卡 (Emergency Info Card):** 生成可分享的數位緊急資訊卡，包含藥物與過敏史。
- [- [ ] **生物辨識取藥授權 (Biometric Dispense Authorization):** 針對管制藥物，增加指紋或臉部辨識安全防線。
- [ ] **藍牙設備綁定 (Device Binding):** 首次配對後，記住裝置 MAC 位址，後續提供一鍵快速連線。
- [ ] **照顧者模式 (Caregiver Mode):** 建立獨立的照顧者模式，可遠端監控並協助管理。

### 使用者參與 & 動機
- [ ] **用藥依從性遊戲化 (Adherence Gamification):** 引入連續紀錄、成就系統等，激勵使用者按時服藥。
- [ ] **客製化提醒與互動 (Customizable Reminders & Interactions):** 允許錄製聲音作為鈴聲，或設定小遊戲鬧鐘。

### 商業模式
- [ ] **訂閱制與進階功能 (Subscription & Premium Features):** 推出 Premium 訂閱方案，解鎖高階功能。
