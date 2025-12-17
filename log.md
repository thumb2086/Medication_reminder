# 更新日誌

## 2025-01-27
### DevOps
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
