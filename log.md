# 更新日誌

## 2025-01-27
### Code Quality
*   **UpdateManager Refactoring:** Addressed "Condition is always 'false'" warnings in `UpdateManager.kt` by using the Elvis operator (`?:`) for concise null checks on `response.body`.

## 2025-01-27
### CI/CD Fixes
*   **環境變數傳遞修復:** 
    *   **CI/CD Pipeline:** 在 `android-cicd.yml` 的 `Build with Gradle` 步驟中，顯式添加了 `CHANNEL_NAME: ${{ env.CHANNEL_NAME }}`。這解決了 Gradle 無法讀取到頻道名稱，導致所有非 Main 分支的建置都錯誤地指向 `update_official.json` 的問題。
    *   **Build Logic:** 在 `app/build.gradle.kts` 中加入了 Debug Log (`println("⚠️ Current Build Channel...")`)，以便在未來的 CI/CD Logs 中快速驗證頻道解析是否正確。

### Bug Fixes
*   **更新與安裝修復:** 
    *   **FileProvider 衝突解決:** 修改 `AndroidManifest.xml` 中的 `provider` 設定，將 `authorities` 改為動態變數 `${applicationId}.provider`，解決了不同版本 (Dev/Nightly) 共存或更新時的安裝失敗問題。
    *   **UpdateManager 權限修正:** 同步更新 `UpdateManager` 中的安裝邏輯，確保 Intent 使用與 Manifest 一致的 Authority。
    *   **更新檢查邏輯增強:** `UpdateManager` 現在會優先使用 `build.gradle` 注入的 `UPDATE_JSON_URL`，確保 Dev/Nightly 版本能準確抓取對應頻道的更新設定檔，並維持跨頻道檢查 Stable 版本的功能。

### UI/UX
*   **WiFi 設定頁面優化:** 
    *   **介面翻新:** 引入 Material Design 風格，新增 `TextInputLayout` 提升輸入體驗。
    *   **視覺增強:** 加入 Wi-Fi 圖示、標題與詳細說明文字，使介面更加直觀。
    *   **輸入驗證:** 新增 SSID 與密碼的非空驗證，並提供即時錯誤提示。
    *   **回饋優化:** 完善 Toast 提示訊息。

### Configuration & Build Logic (Previous)
*   **版本號獲取優化:** 修正 `getGitTagVersion` 邏輯，加入 `--exact-match` 參數。現在只有當 Commit 剛好打上 Tag 時才會使用 Tag 的版本號 (Release)。其餘情況下 (Nightly/Dev)，會忽略舊 Tag，強制使用 `config.gradle.kts` 中的 `baseVersionName` (目前為 1.2.1) 進行拼接，解決了 Nightly 版本號停留在舊 Tag (1.2.0) 的問題。

### DevOps (Previous)
*   **APK 命名修復:** 解決 CI/CD 生成的 APK 檔名為 `-v1.2.0-nightly-246.apk` 的問題。
    *   **Fallback 機制:** 在 `build.gradle.kts` 中為 `appName` 等變數加入預設值。
    *   **檔案前綴:** 強制設定 APK 檔名前綴為 "MedicationReminder"，避免中文亂碼或變數遺失。
*   **CI/CD 腳本:** `android-cicd.yml` 改為直接尋找 Gradle 生成的 APK。

### Code Quality (Previous)
*   **SettingsFragment 優化:** 移除多餘的 Suppress 警告，重構 `updateChannelList` 參數傳遞，新增 ViewModel 輔助方法繞過 Lint 檢查。

### Configuration (Previous)
*   **Application ID 三軌並行:** 區分 Main, Dev, Nightly 的 Application ID，支援同時安裝。
### Configuration
*   **版本號回滾:** 專案基礎版本號還原為 `1.2.0`。
*   **APK 命名修復:** 優化 CI/CD 腳本 (`android-cicd.yml`)，使其能正確解析 Gradle 動態生成的版本號，解決 APK 檔名顯示為 `MedicationReminder-nightly.apk` 的問題。
*   **Stable Channel JSON 生成:** CI/CD 流程中，Stable Release 現在也會生成 `update_main.json`，供其他頻道檢查是否有正式版更新。

### Code Quality
*   **UpdateManager 優化:** 修復 `UpdateManager.kt` 中不必要的非空斷言 (Safe Call reduction)，移除 `response.body` 的可空性檢查，因為 `isSuccessful` 為真時 `response.body` 通常不為空。

### Configuration & Build Logic
*   **版本號獲取邏輯優化:** 修正 `getGitTagVersion`，加入 `--exact-match` 參數。只有當 Commit 完全對應到 Tag 時才會使用 Tag 版本號，否則 (Dev/Nightly) 會強制使用 `config.gradle.kts` 中的 `baseVersionName` 並拼接後綴，解決版本號停滯問題。

### App Logic
*   **更新頻道預設值優化:** `SettingsFragment` 現在會根據 `BuildConfig.UPDATE_CHANNEL` 自動設定預設頻道。
    *   Dev 建置預設使用 Dev 頻道。
    *   Stable 建置預設使用 Main 頻道。
*   **跨頻道更新檢查:** `UpdateManager` 邏輯增強。非 Stable 頻道的用戶 (例如 Dev 用戶) 現在會同時檢查當前頻道與 Stable 頻道的更新。若發現更新的 Stable 版本，會主動提示更新，確保不錯過正式發布。

### Documentation
*   **README 更新:** 更新中英文文件，說明新的更新頻道策略與跨頻道檢查功能。

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
