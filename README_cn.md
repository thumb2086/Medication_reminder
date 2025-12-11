# 藥到叮嚀 (Medication Reminder App)

一個結合 ESP32 智慧藥盒的藥物提醒應用程式。協助使用者追蹤服藥時間、監測環境溫濕度，並透過手機通知與硬體警示雙重提醒，確保按時服藥。

[![Android CI/CD](https://github.com/CPXru/Medication_reminder/actions/workflows/android-cicd.yml/badge.svg)](https://github.com/CPXru/Medication_reminder/actions/workflows/android-cicd.yml)

## 功能特色

*   **智慧提醒:** 可自訂藥物名稱、服用頻率與時間。
*   **硬體整合:** 透過藍牙低功耗 (BLE) 與 ESP32 智慧藥盒無縫連接。
*   **即時監測:** 顯示藥盒回傳的即時溫度與濕度數據。
*   **服藥追蹤:** 自動記錄服藥歷史，並產生圖表分析服藥正確率。
*   **角色主題:** 提供「酷洛米」與「櫻桃小丸子」兩種主題風格供選擇。
*   **工程模式:** 可直接從 App 切換藥盒的工程模式，方便進行硬體診斷。
*   **Wi-Fi 設定:** 透過 BLE 直接設定 ESP32 的 Wi-Fi 連線資訊 (SSID 與密碼)，現已整合至設定頁面中。
*   **鬧鐘系統:** 支援設定最多 4 組硬體鬧鐘，讓藥盒能獨立運作提醒。
*   **互動圖表:** 透過互動式折線圖檢視溫濕度趨勢，支援縮放、拖移與數據點查看。
*   **App 內更新:** 自動檢查 GitHub Releases 上的新版本。使用者可選擇「正式版 (Stable)」、「開發版 (Dev)」或「夜間版 (Nightly)」更新頻道。
*   **穩健的更新安裝:** 智慧處理 APK 下載與安裝，具備自動回退機制，確保在各種 Android 版本 (含 Android 13+) 上皆能順利安裝。

## 藍牙協定版本控制

為了確保 App 功能持續更新的同時，仍能相容不同版本的 ESP32 韌體，我們引入了協定版本控制機制。

*   **握手 (Handshake):** App 連線後會主動詢問藥盒的協定版本 (指令 `0x01`)。
*   **向下相容:** App 會根據回報的版本號，自動調整資料解析邏輯。
    *   **版本 1:** 舊版協定 (單筆歷史數據傳輸)。
    *   **版本 2:** 支援批次歷史數據傳輸 (每包 5 筆) 與優化的整數型感測數據格式。
    *   **版本 3:** 新增鬧鐘設定功能 (指令 `0x41`)。

## 未來優化方向

我們持續致力於提升應用程式的品質與功能，目前的優化規劃包括：

*   **架構層面:**
    *   導入 Hilt 依賴注入 (Dependency Injection)，提升模組化程度。(已完成)
    *   將 `LiveData` 全面遷移至 `StateFlow`，以獲得更好的協程支援與線程安全性。(已完成)
    *   實作 Repository 模式，將資料存取邏輯從 ViewModel 中抽離。(已完成)
*   **程式碼品質:**
    *   持續進行 Linter 檢查，修復潛在的警告與排版問題。
    *   增加單元測試 (Unit Test) 與 UI 測試覆蓋率。
*   **UI/UX:**
    *   優化圖表顯示效能，支援更長區間的歷史數據查看。
    *   新增更多角色主題與客製化選項。
*   **穩定性:**
    *   增強藍牙連線的重連機制與錯誤處理。
    *   優化背景同步功能，確保數據不遺失。

## 開始使用

1.  **複製專案:** `git clone https://github.com/CPXru/Medication_reminder.git`
2.  **開啟專案:** 使用 Android Studio (建議 Ladybug | 2024.2.1 或更新版本) 開啟。
3.  **編譯執行:** 連接 Android 手機 (Android 10+) 或使用模擬器執行。

## CI/CD 與版本自動化

本專案使用 GitHub Actions 進行持續整合與版本自動管理。

*   **正式發布 (Stable Releases):** 推送以 `v` 開頭的標籤 (例如 `v1.1.8`) 會觸發，建立永久 Release。
*   **開發版發布 (Dev Releases):** 推送至 `dev` 分支時觸發，更新 `latest-dev` 滾動發布版本。
*   **Nightly 建置:** 推送至其他分支時觸發，更新 `nightly` 滾動發布版本。
*   **版本號碼:** `versionCode` 對應 Build Number，`versionName` 則根據分支與提交次數動態產生。

## 授權

[MIT License](LICENSE)
