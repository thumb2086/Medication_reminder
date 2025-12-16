# 更新日誌

## 2025-01-27
### DevOps
*   **簽章衝突修復 (Root Cause: 相對路徑陷阱):**
    *   **問題:** 雖然 Secret 設定正確，但 Gradle 建置時因路徑問題找不到解碼後的 `release.keystore`，導致 Silent Fallback 至 Debug Key。這造成了不同版本間的簽章不一致，手機拒絕更新。
    *   **解決方案:**
        *   **CI/CD (YAML):** 在 `android-cicd.yml` 中，將傳遞給 Gradle 的 `RELEASE_KEYSTORE_PATH` 改為絕對路徑 (`${{ github.workspace }}/release.keystore`)，確保 Gradle 一定能找到檔案。
        *   **Gradle 防呆:** 修改 `app/build.gradle.kts`，在 Release Config 中增加檢查邏輯。若指定了 `RELEASE_KEYSTORE_PATH` 但檔案不存在，直接拋出 `FileNotFoundException` 中斷建置，避免再次發生 Silent Fallback。
*   **Version Code 策略大修 (Fix Root Cause):**
    *   **問題:** 之前使用「日期」格式 (如 `241215xx`) 作為 `versionCode`，切換到 Commit Count (如 `129`) 後，因數值驟降 (241215xx > 129)，導致 Android 系統認定新版為「舊版」，拒絕安裝/更新。
    *   **解決方案:**
        *   **CI/CD (YAML):** 強制使用 GitHub Actions 計算出的 `git rev-list --count HEAD` (Commit Count) 作為 `VERSION_CODE`。
        *   **Gradle:** 修改 `build.gradle.kts`，優先讀取環境變數 `VERSION_CODE_OVERRIDE` (即 Commit Count)。若無環境變數 (本地開發)，才回退到本地計算的 Commit Count。
        *   **用戶端操作:** 由於版本號體系變更 (日期 -> 次數)，舊版 App (日期版) 必須手動移除，才能安裝新版 (次數版)。之後的更新將恢復正常。
*   **API 網址修復:** 確認 `update_${updateChannel}.json` 的生成與讀取邏輯一致。

### App Logic
*   **更新檢查邏輯增強:**
    *   **自我更新循環修復:** 修改 `UpdateManager.kt`，將版本號比對邏輯從 `>=` 改為 `>` (Strictly Greater)，徹底解決同版本無限自我更新的 Bug。
    *   **手動更新覆蓋:** 修改 `UpdateManager.kt`，新增 `isManualCheck` 參數。當使用者在設定頁面手動點擊「檢查更新」時，會強制允許更新 (即使版本號相同或更舊)，方便使用者重裝或切換頻道。
    *   **邏輯優化:** `checkForUpdates` 方法現在接受 `isManualCheck`，若為真，則將 `force` 標誌傳遞給底層檢查邏輯，繞過 `isNewerVersion` 的嚴格限制。
    *   **UI 整合:** `SettingsFragment.kt` 中的 `checkForUpdates` 方法已更新，呼叫時傳入 `isManualCheck = true`。`MainActivity.kt` 啟動時自動檢查則傳入 `isManualCheck = false`。

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
