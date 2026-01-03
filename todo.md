# Medication Reminder - Unified Development Roadmap

## Epic 2: 智慧互動核心 (Smart Interaction Core)
此史詩專注於實現 App 與智慧藥盒的核心互動，包含連線穩定性、放藥引導與服藥確認，打造無縫的硬體整合體驗。

## Epic 3: 架構與數據 (Architecture & Data)
此史詩專注於升級 App 的底層架構，並基於新的資料庫結構提供更豐富的數據管理與洞察功能。

## Epic 4: 便利性與擴展 (Convenience & Expansion)
此史詩專注於提供更多便利工具，並擴展 App 的保護網，提升整體使用價值。

### v1.5.2: 智慧安全網路 (Smart Safety Net)
- [ ] **漏服藥轉發通知 (Missed Dose Alerts):**
  - **詳細步驟:**
    - [ ] 在設定中新增介面，讓使用者可以選擇一位聯絡人。
    - [ ] 實作一個 `AlarmReceiver` 或 `Worker`，在用藥時間過後的一段時間觸發檢查。
    - [ ] 如果藥還沒吃，使用 `SmsManager` 或 `Intent.ACTION_SEND` 發送 SMS。
    - [ ] 確保在發送前取得使用者同意及必要的權限 (`SEND_SMS`)。

### v1.5.3: 核心功能強化 (Core Feature Enhancement)
- [ ] **藥物穩定性警報 (Medication Stability Alert):**
  - **詳細步驟:**
    - [ ] **App 端:** 建立一個可擴充的藥物儲存條件資料庫。
    - [ ] **App 端:** 在新增/編輯藥物時，允許使用者選擇特定藥物並連結到其儲存條件。
    - [ ] **App 端:** 在 `BluetoothLeManager` 中新增邏輯，當收到溫濕度數據時，檢查是否超出安全範圍。
    - [ ] **App 端:** 若超出範圍，觸發一個高優先級的本地通知警告使用者。
  - [ ] **ESP32 端: 環境回報**
    - **詳細步驟:**
      - [ ] 在 `hardware.cpp` 中，實作讀取溫濕度感測器 (DHT) 數據的邏輯。
      - [ ] 透過 `ble_handler.cpp` 定時回報溫濕度數據給 App。

### v1.5.4: 架構重構 (Architecture Refactoring)
- [ ] **架構重構: 角色功能整合與擴充準備**
  - **詳細步驟:**
    - [ ] 分析現有角色主題的靜態資源 (strings.xml, drawables)。
    - [ ] 建立一個 `CharacterManager` 或類似的管理器，用於動態載入與管理角色主題。
    - [ ] 將現有的角色 (酷洛米、櫻桃小丸子等) 重構為統一的資料結構 (如 `CharacterPack`)。
    - [ ] 設計一個 JSON 或其他格式的清單 (manifest)，為未來從網路下載擴充包做準備。

## 未來規劃 (Future Considerations)
- [ ] **藥物照片對照 (Medication Photo ID):** 允許使用者為每種藥物拍攝並儲存一張實際照片，在服藥提醒時顯示以供視覺核對。
- [ ] **雲端備份與還原 (Cloud Backup & Restore):** 跨裝置同步使用者的藥物設定與紀錄。
- [ ] **多使用者支援 (Multi-User Support):** 讓 App 可以建立多個 Profile，方便家人共用裝置。
- [ ] **進階數據分析與洞察 (Advanced Analytics & Insights):** 分析長期服藥依從性趨勢，找出個人行為模式。
- [ ] **Wear OS 副應用 (Wear OS Companion App):** 在手錶上顯示下次服藥提醒，並可直接標記為已服用。
- [ ] **語音互動整合 (Voice Integration):** 整合 Google 助理，用語音查詢或回報服藥狀態。
- [ ] **AR 擴增實境導覽 (Augmented Reality Onboarding):** 以 AR 方式提供更直覺的首次設定體驗。
- [ ] **旅行模式 (Travel Mode):** 跨時區旅行時，自動調整提醒時間並生成打包清單。
- [ ] **AI 生成服藥報告 (AI-Generated Compliance Reports):** 自動生成自然語言格式的服藥總結報告，方便直接提供給醫師。
- [ ] **醫療院所系統串接 (Healthcare Provider Integration - via FHIR):** 串接電子病歷，自動同步處方與服藥紀錄。
- [ ] **照顧者模式 (Caregiver Mode):** 建立獨立的照顧者模式，可遠端監控並協助管理。
