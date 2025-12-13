# 更新日誌

## 2025-01-27
### DevOps
*   **APK 命名修復:**
    *   **問題:** CI/CD 編譯出的 APK 檔名為 `-v1.2.0-nightly-246.apk`，前綴消失。
    *   **原因:** `app/build.gradle.kts` 中的 `appName` 變數在 CI 環境下可能因為 `extra["appConfig"]` 轉型或讀取失敗而變成 null 或空值。
    *   **解決:** 
        *   在 `build.gradle.kts` 中加入嚴格的 `null` 檢查與預設值 fallback (例如 `appName` 預設為 "MedicationReminder")。
        *   將 APK 檔案名稱前綴 (`archivesBaseName`) 強制設定為英文的 "MedicationReminder"，避免因中文 `appName` ("藥到叮嚀") 在某些 CI/CD 環境或檔案系統中產生亂碼或遺失。App 顯示名稱仍維持中文。
    *   **CI/CD 腳本:** `android-cicd.yml` 改為直接尋找 Gradle 生成的 APK，不再依賴不穩定的 `grep` 解析，增強穩定性。

### Code Quality
*   **SettingsFragment 優化:**
    *   **Lint 警告修復:** 移除了 `SettingsFragment.kt` 中多餘的 `@Suppress` 註解。
    *   **重構:** 移除了 `fetchAvailableChannels` 與 `updateChannelList` 中多餘的 `currentChannel` 參數，改為直接引用 `BuildConfig.UPDATE_CHANNEL`。
    *   **Lint 警告修復 (Final):** 新增 `MainViewModel.getCurrentUpdateChannel()` 以繞過常數條件檢查。

### Configuration
*   **Application ID 三軌並行:**
    *   **Side-by-Side 安裝支援升級:** 修正 `app/build.gradle.kts` 的 Application ID 邏輯，區分 Main, Dev, Nightly。

### Configuration & Build Logic (Previous)
*   **版本號策略優化:**
    *   **Git Tag 優先:** 修改 `app/build.gradle.kts`，優先讀取 Git Tag。
    *   **Base Version 升級:** 更新為 `1.2.1`。

### UI/UX (Previous)
*   **SettingsFragment 預設頻道優化:** 修正 Nightly 版的預設更新頻道。
*   **Accessibility 修復:** 修復無障礙標籤。
*   **動態表單角色圖示修復:** 修復表單圖片未切換問題。

### DevOps (Previous)
*   **Release 命名格式修正:** Nightly Release Title 優化為 `<Branch> | <VersionName>`。
*   **跨頻道更新支援:** 允許使用者強制切換頻道並下載更新。
*   **CI/CD 重構:** APK 命名優化、手動清理模式、命名邏輯統一。

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
