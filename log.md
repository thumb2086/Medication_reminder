# 更新日誌

## 2025-01-27
### DevOps
*   **多頻道 (Multi-Channel) CI/CD 架構:**
    *   **動態頻道:** 支援基於 Git 分支名稱的動態更新頻道 (例如 `dev`, `feat-new-ui`, `fix-login-bug`)。每個分支現在都擁有獨立的 `update_<branch>.json` 更新設定檔與 Nightly Release。
    *   **Gradle 配置:** 更新 `app/build.gradle.kts`，自動將 Git 分支名稱轉換為安全的 `UPDATE_CHANNEL` 並注入 `BuildConfig`。
    *   **GitHub Actions:** 更新 `.github/workflows/android-cicd.yml`，針對 `push` 事件自動生成對應頻道的 JSON 設定檔，並利用 `gh-pages` 部署。
    *   **App 邏輯:** 重構 `UpdateManager.kt` 與 `SettingsFragment.kt`，現在 App 會自動根據建置時的分支 (`BuildConfig.UPDATE_CHANNEL`) 檢查對應的更新來源。
    *   **UI 調整:** 設定頁面中的「更新頻道」選項改為唯讀顯示 (或根據最新改動為可選列表)，直接告知使用者當前所在的頻道。
*   **版本號機制優化:**
    *   **Integer Overflow 修復:** 將 `.github/workflows/android-cicd.yml` 中的時間戳格式從 `yyyyMMddHH` 修改為 `yyMMddHH` (8位數)，防止 Version Code 溢出。
    *   **版本倒退修復:** 引入 `VERSION_CODE_OVERRIDE` 環境變數 (時間戳)，確保無論分支如何切換，新建置的版本號始終大於舊版本，解決 Commit Count 變少導致無法更新的問題。

### UI/UX
*   **設定頁面「關於」區塊:**
    *   在 `SettingsFragment` 中實作了「關於」區塊的連結跳轉。
    *   點擊「作者」、「專案」、「版本」可分別開啟 GitHub Profile, Repo 與 Releases 頁面。
    *   在 `preferences.xml` 新增 `app_project` 設定項。
*   **UI 遮擋修復:** 在 `SettingsFragment` 加入 `OnApplyWindowInsetsListener`，防止底部內容被系統手勢導航條遮擋。
*   **底部導航列修復:** 修復從設定頁面返回時，底部主分頁按鈕顯示異常的問題。

### Bug Fixes
*   **國際化 (i18n):** 修復設定頁面「關於」區塊無英文翻譯問題 (`values-en/strings.xml`)。
*   **BuildConfig 類型錯誤:** 修正 `build.gradle.kts` 中 `UPDATE_CHANNEL` 的定義，確保生成的 Java/Kotlin 代碼類型正確 (`String`)。
*   **UpdateManager 清理:** 移除重複變數宣告與無效的空值檢查。

### Code Quality
*   **SettingsFragment 優化:**
    *   修復未使用導入與代碼警告 (使用 KTX `toUri()`)。
    *   修正 `ListPreference` 動態新增頻道的邏輯錯誤 (型別不匹配)。

## 2025-01-26
### Bug Fixes
*   **App 內更新安裝修復 (Part 3):**
    *   重構 `UpdateManager` 的 `onReceive` 邏輯，優先從 `DownloadManager` 獲取 `COLUMN_LOCAL_URI`。
    *   新增回退機制：若 URI 解析失敗，自動回退至 `Download/` 目錄尋找同名檔案。
    *   增強安裝流程的日誌輸出。
    *   Lint 修復：將 `BuildConfig.DEBUG` 替換為 `ApplicationInfo.FLAG_DEBUGGABLE`，移除多餘的 `@Suppress`。

## 2025-01-25
### Bug Fixes
*   **Android 13+ 廣播接收器修復:**
    *   針對 API 33+ 使用 `Context.RECEIVER_EXPORTED` 註冊廣播接收器，解決 `ACTION_DOWNLOAD_COMPLETE` 無法接收導致無法自動安裝的問題。
*   **CI/CD 版本號解析:**
    *   將 `build.gradle.kts` 中的 `println` 改為 `logger.warn`，避免 Keystore 訊息污染 CI/CD 的版本號變數。
*   **Lint 錯誤修復:** 修復 `UnspecifiedRegisterReceiverFlag` 錯誤與其他 Lint 警告。

### Configuration
*   **雙模組簽章 (Hybrid Signing):** 更新 `build.gradle.kts`，優先讀取環境變數 (CI環境)，失敗則回退至 `local.properties` (本地環境)，解決 CI/CD 簽章失敗問題。

## 2025-01-24
### Bug Fixes
*   **安裝流程優化 (Part 2 & 3):**
    *   **權限檢查:** 在安裝前檢查 `canRequestPackageInstalls()`，若未授權則引導至設定頁面。
    *   **簽名檢查:** Debug 模式下檢測簽名不符風險並提示使用者。
    *   **檔案類型:** 明確設定下載 MIME Type 為 `application/vnd.android.package-archive`。
    *   **檔案驗證:** 增加檔案大小檢查 (< 1KB 視為無效)。
*   **Application ID 統一:** 移除基於分支名稱動態修改 Application ID 的邏輯，確保不同分支建置的 App 可互相更新 (需簽名一致)。
*   **資源清理:** 移除 `provider_paths.xml` 中未使用的命名空間。
*   **安裝路徑:** 更新 `provider_paths.xml` 補上 `<external-files-path>`。

## 2025-01-23
### DevOps
*   **更新檢查邏輯優化:**
    *   實作 `isNewerVersion` 支援 SemVer 語意化版本比較。
    *   新增 Nightly 版本 (Commit Count) 比對邏輯。
    *   修正 GitHub Assets 檔名版本號解析。

## 2025-01-22
### Bug Fixes
*   **資源與警告修復:**
    *   修復 `values-en/strings.xml` 中更新頻道選項數量不一致問題。
    *   移除 `UpdateManager` 中多餘的安全呼叫與未使用參數。

## 2025-01-21
### DevOps
*   **更新頻道策略:**
    *   確立 `Stable`, `Dev`, `Nightly` 三頻道策略與對應的 Tag 檢查邏輯。
    *   更新 UI 選項以支援三頻道切換。

## 2025-01-20
### DevOps
*   **App 內自動更新 (In-App Update):**
    *   新增 `UpdateManager`，整合 GitHub Releases API。
    *   實作 Official 與 Nightly 頻道的更新檢查邏輯。
    *   新增 `INTERNET` 與 `REQUEST_INSTALL_PACKAGES` 權限及 `FileProvider` 設定。
    *   在 `MainActivity` 與 `SettingsFragment` 整合更新檢查 UI。

### UI/UX
*   **設定頁面優化:**
    *   為設定選項新增 Material Design 圖示。
    *   升級相關 AndroidX 依賴庫版本。

## 2025-01-19
### DevOps
*   **三軌發布策略:**
    *   定義 Official (`v*`), Dev (`dev` branch), Nightly (others) 的觸發條件。
    *   更新 GitHub Actions 流程以支援自動化發布。

## 2025-01-18
### UI/UX
*   **歷史記錄頁面:**
    *   新增月份標題 (`monthTitle`) 並實作滑動監聽更新邏輯。
    *   完善英文翻譯 (`values-en/strings.xml`)。

## 2025-01-17
### Bug Fixes
*   **Application ID 修復:** 增加正則表達式過濾，處理分支名稱中的連字號等非法字元，避免 Package Name 錯誤。

## 2025-01-16
### UI/UX
*   **Wi-Fi 設定整合:**
    *   將 Wi-Fi 設定從 Toolbar 選單移至設定頁面 (`SettingsFragment`)。
    *   新增對應圖示與導覽邏輯。

## 2025-01-13 ~ 2025-01-15
### UI/UX
*   **Toolbar 與選單優化:**
    *   多次微調 Toolbar 標題置中 (`layout_gravity="center"`).
    *   修復下拉選單 (Popup Menu) 位置異常與留白問題 (使用 `overlapAnchor=false` 與 `dropDownVerticalOffset=0dp` 的標準 Material 實作)。
    *   修復主題切換時 Fragment 重疊或顯示錯誤問題。

## 2025-01-12
### UI/UX
*   **選單位置回滾:** 將 `dropDownVerticalOffset` 重設為 `0dp` 以解決異常。

## 2025-01-11
### UI/UX
*   **Toolbar 微調:** 嘗試使用 `translationY` 與 `overlapAnchor=false` 調整標題與選單位置。

## 2025-01-10
### Documentation
*   **License:** 新增 MIT License 檔案。
### UI/UX
*   **選單位置激進調整:** 嘗試大幅度負值 offset (`-50dp`) 來修復留白 (後續已修正)。

## 2025-01-09
### UI/UX
*   **選單位置調整:** 啟用 `overlapAnchor=true`。

## 2025-01-08
### UI/UX
*   **選單位置調整:** 微調 offset 為 `-4dp`。

## 2025-01-07
### UI/UX
*   **選單樣式自定義:** 建立 `Widget.App.PopupMenu` 樣式並設定 offset 為 `4dp`。

## 2025-01-06
### DevOps
*   **版本號與 CI/CD 優化:**
    *   Nightly 版本號格式改為 `<Base> nightly <CommitCount>`。
    *   所有非 main 分支推送皆觸發 Nightly Build。
    *   修復分支名稱字元導致的資源連結錯誤。

## 2025-01-05
### DevOps
*   **CI/CD 自動化實作:**
    *   建立 `.github/workflows/android-cicd.yml`。
    *   定義 `main` (Stable) 與 `dev` (Nightly) 的發布流程。

## 2025-01-04
### UI/UX
*   **標題置中:** 移除 `paddingTop` 改用 `layout_gravity`。

## 2025-01-03
### Configuration
*   **版本管理自動化:**
    *   `build.gradle.kts` 實作基於 Git 的動態 VersionCode/VersionName。
    *   簡化分支策略為 `main` 與 `dev`。

## 2025-01-02
### Bug Fixes
*   **ESP32 協定與鬧鐘:**
    *   修復 `CMD_REPORT_ENG_MODE_STATUS` 定義錯誤。
    *   新增 `setAlarm` (`0x41`) 指令與錯誤回報處理。

### UI/UX
*   **圖表優化:** (2025-01-01) 將環境監測圖表改為折線圖，優化深色模式對比度。

## 早期更新
*   **0010:** 新增設定頁面和藥物列表頁面。
*   **0009:** 優化藥物提醒表單的驗證。
*   **0008:** 修正 Gradle 版本目錄錯誤。
