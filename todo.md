# Medication Reminder App - Development Roadmap

## Epic 1: 基礎架構 & 穩定性 (Foundation & Stability)
*此史詩專注於改善 App 的內部架構，提升穩定性、效能與可維護性，為未來所有功能打下堅實的基礎。*

### v1.2.4: 穩定性修正 (Stability Patch)
- [ ] **體驗優化: 藍牙自動重連**
  - **詳細步驟:**
    - [ ] 在 `BluetoothLeManager` 的斷線邏輯中，新增一個 flag 來判斷是否為意外斷線。
    - [ ] 建立一個 `startReconnectSequence()` 方法，使用協程的 `delay` 實現有次數限制、有延遲的重連嘗試。
    - [ ] 在 `BleListener` 介面新增 `onReconnectStarted` 和 `onReconnectFailed` 回調。
    - [ ] 在 `ViewModel` 中實作新的回調，更新 UI 狀態以告知使用者「正在嘗試重連...」。

### v1.3.0: 架構升級 (Infrastructure Upgrade)
- [ ] **基礎架構: 資料庫遷移 (Room)**
  - **詳細步驟:**
    - [ ] 在 `app/build.gradle.kts` 中加入 Room 的依賴。
    - [ ] 建立 `MedicationEntity` 和 `TakenRecordEntity`。
    - [ ] 建立對應的 DAO 介面 (`MedicationDao`, `TakenRecordDao`)。
    - [ ] 建立 `AppDatabase.kt`。
    - [ ] 重構 `AppRepository`，改為呼叫 DAO。
    - [ ] 實作一個一次性的資料遷移邏輯，將舊的 SharedPreferences 數據寫入 Room。

## Epic 2: 核心功能擴展 (Core Feature Expansion)
*此史詩專注於擴展 App 的核心價值，加入與藥物管理和硬體互動直接相關的高價值功能。*

### v1.3.1: 庫存管理 (Inventory Management)
- [ ] **核心功能: 藥物庫存管理與補充提醒**
  - **詳細步驟:**
    - [ ] 在 `MedicationEntity` 中新增 `reminderThreshold: Int` 欄位，並更新資料庫。
    - [ ] 在新增/編輯藥物的介面中，增加設定提醒閾值的輸入框。
    - [ ] 在 `AppRepository` 的 `processMedicationTaken` 方法中，增加檢查庫存是否低於閾值的邏輯。
    - [ ] 若低於閾值，觸發一個本地通知提醒使用者。

### v1.3.2: 韌體更新 (Firmware Updater)
- [ ] **硬體整合: 韌體空中升級 (OTA)**
  - **詳細步驟:**
    - [ ] 準備一個支援 BLE OTA 的 `.ino` 範例韌體。
    - [ ] 在設定中建立新的韌體更新頁面。
    - [ ] 實作選擇 `.bin` 韌體檔案的邏輯。
    - [ ] 在 `BluetoothLeManager` 新增 `startOtaUpdate(firmware: ByteArray)` 方法，將韌體分塊寫入 ESP32。
    - [ ] 實作 UI 上的進度條來顯示更新進度。

## Epic 3: 數據洞察 & 便利工具 (Data Insights & Tools)
*此史詩專注於將數據轉化為有價值的資訊，並提供提升使用便利性的工具。*

### v1.4.0: 數據洞察 (Data Insights)
- [ ] **數據呈現: 詳細服藥報告 & 匯出與分享**
  - **詳細步驟:**
    - [ ] 在 `HistoryFragment` 中新增「週/月/季度」的切換按鈕。
    - [ ] 在 `ViewModel`/`Repository` 新增方法，根據時間範圍從 Room 查詢聚合數據。
    - [ ] 使用圖表庫將數據視覺化。
    - [ ] 建立 `ReportGenerator` 類別，將數據格式化成 PDF 或 CSV。
    - [ ] 新增「分享」按鈕，使用 `Intent.ACTION_SEND` 分享報告。

### v1.4.1: 便利工具 (Convenience Tools)
- [ ] **便利工具: 桌面小工具 (Widget)**
  - **詳細步驟:**
    - [ ] 建立 `AppWidgetProvider` 類別。
    - [ ] 設計 Widget 的 XML 佈局。
    - [ ] 實作一個 `Service` 或 `Worker` 來定期更新 Widget 內容 (下次用藥時間等)。
    - [ ] 處理 Widget 的點擊事件，例如點擊後打開 App。

### v1.4.2: 體驗優化 (UX Polish)
- [ ] **體驗優化: 無縫主題更新**
  - **詳細步驟:**
    - [ ] 研究並選擇一個不停用 `recreate()` 的主題更新方案 (例如，手動遍歷 View 並更新相關屬性)。
    - [ ] 重構 `SettingsFragment` 中的主題切換邏輯。

## Epic 4: 智慧安全網路 (Smart Safety Net)
*此史詩專注於建立一個超越個人提醒的智慧安全網路。*

### v1.5.0: 智慧通知 (Smart Notifications)
- [ ] **漏服藥轉發通知 (Missed Dose Alerts):**
  - **詳細步驟:**
    - [ ] 在設定中新增介面，讓使用者可以選擇一位聯絡人。
    - [ ] 實作一個 `AlarmReceiver` 或 `Worker`，在用藥時間過後的一段時間（例如 1 小時）觸發檢查。
    - [ ] 如果藥還沒吃，使用 `SmsManager` 或 `Intent.ACTION_SEND` 發送 SMS 或啟動通訊軟體。
    - [ ] 確保在發送前取得使用者同意及必要的權限 (`SEND_SMS`)。

## 未來規劃 (Future Considerations)
- [ ] 健康平台整合 (Health App Integration)
- [ ] 用藥間隔衝突警告 (Medication Conflict Warning)
- [ ] 互動式藥盒引導 (Interactive Setup Guide)
- [ ] 藥物資訊整合 (Medication Database Integration)
- [ ] 雲端備份與還原 (Cloud Backup & Restore)
- [ ] 多使用者支援 (Multi-User Support)
- [ ] 藍牙設備綁定 (Device Binding)
- [ ] 智慧提醒微調 (Intelligent Reminders)
