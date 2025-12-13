# 更新日誌

## 2025-01-27
### DevOps
*   **APK 命名修復:**
    *   **問題:** 使用者回報 CI/CD 生成的 APK 檔名為 `MedicationReminder-nightly.apk`，表示 `versionName` 抓取失敗。
    *   **原因:** 在 CI 環境中，`./gradlew -q app:properties` 可能會輸出包含 Gradle 守護程序或其他非屬性的資訊，導致 `grep` 和 `awk` 解析失敗，觸發了 fallback 機制。此外，`getProperty("archivesBaseName")` 在某些配置下可能未正確反映。
    *   **解決方案:**
        *   不再依賴 `app:properties` 任務輸出。
        *   改為直接利用 Gradle 輸出的 APK 檔案本身。Gradle Build 會根據 `archivesBaseName` 生成 APK。
        *   修改 `android-cicd.yml`，在 Build 步驟後，直接尋找 `app/build/outputs/apk/release` 目錄下以 `MedicationReminder-` 開頭的 APK。
        *   若 Gradle 配置正確 (`archivesBaseName` 設定為 `$appName-v$safeVersionName`)，生成的 APK 應原本就包含版本號。
        *   腳本將只做簡單的更名或確認，確保最終上傳的檔案名稱正確。

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
