# 更新日誌

## 2025-01-27
### DevOps
*   **CI/CD 重構 (Final Attempt):**
    *   **手動清理模式 (Manual Cleanup):** 新增 `workflow_dispatch` 觸發器，允許開發者手動輸入分支名稱（例如 `fix-old-bug`），強制執行 Cleanup Job。這解決了已經刪除的分支無法觸發 Workflow 的問題，提供了補救舊帳的手段。
    *   **命名邏輯統一 (Normalize Name):** 重寫並統一了 Build Job 與 Cleanup Job 的分支名稱轉 Tag 名稱邏輯。
        *   使用 `sed 's/[\/\-]/\_/g'` 將斜線 `/` 與連字號 `-` 一律替換為底線 `_`，確保生成的 Tag 名稱（如 `nightly-fix_bug`）與刪除時尋找的 Tag 名稱絕對一致，避免因字元處理不一致導致刪除失敗。
    *   **錯誤容忍:** 在 `gh release delete` 指令後加上 `|| echo ...`，即使 Tag 不存在也不會讓 Job 失敗，確保清理流程能繼續執行後續的 JSON 刪除步驟。
    *   **Git 操作優化:** Cleanup Job 中的 `git rm` 操作現在包含了使用者設定 (`git config`)，確保 Commit 能順利建立。
*   **CI/CD 修復 (Third Attempt):**
    *   **Cleanup Job 深度優化:**
        *   **環境變數傳遞:** 發現 `Extract Branch Name` 步驟在 cleanup job 中可能無法正確共享變數，因此將分支名稱處理邏輯直接整合進 `Delete Channel Release and Tag` 步驟。
        *   **變數注入:** 使用 `env: DELETED_BRANCH: ${{ github.event.ref }}` 明確注入變數，避免直接在 Shell script 中引用 GitHub Context 可能導致的解析問題。
        *   **字元替換工具:** 改用 `tr '/-' '__'` 替代 `sed`，以避免潛在的正則表達式轉義問題，確保 `-` 能正確替換為 `_`。
        *   **除錯訊息:** 新增 `gh release list` 指令，以便在未來失敗時能查看當前的 Release 列表狀態。
        *   **JSON 清理同步:** 同步更新了 `Remove Channel JSON` 步驟中的分支名稱處理邏輯，確保能正確找到並刪除 `update_<branch>.json`。
    *   **Cleanup Job 邏輯修正 (Previous):** 嘗試修正分支名稱處理邏輯，移除了冗餘步驟。
    *   **Schema Validation 修正:** 修正了 `android-cicd.yml` 中 `on.delete` 觸發器的語法錯誤 (`delete: {}`)。
    *   **Cleanup Job 優化:**
        *   使用 GitHub CLI (`gh release delete`) 替代第三方 Action。
        *   加入 `|| echo ...` 錯誤處理，避免因 Release 不存在而報錯。
*   **Documentation**
    *   **README 更新:**
        *   將 `README.md` 與 `README_cn.md` 中的 GitHub 專案連結從 `CPXru/Medication_reminder` 更新為 `thumb2086/Medication_reminder`，以反映正確的 Releases 位置。
        *   移除了 `README.md` 與 `README_cn.md` 中舊的說明圖片連結。
        *   新增了 **Bluetooth Low Energy Protocol (藍牙低功耗通訊協定)** 章節，詳細列出 App 與 ESP32 之間的通訊指令與回應代碼 (Hex format)。
            *   包含指令: `0x01` (Version), `0x11` (Time Sync), `0x12` (Wi-Fi), `0x13`/`0x14` (Eng Mode), `0x20` (Status), `0x30`/`0x31`/`0x32` (Env Data), `0x41` (Alarm).
            *   包含回應: `0x71`, `0x80`~`0x83`, `0x90`~`0x92`, `0xEE`。
        *   更新了 `README_cn.md` 的「未來優化方向」，反映架構重構的最新狀態 (Hilt, StateFlow, Repository)。

### UI/UX
*   **Accessibility 修復:**
    *   修復 `medication_input_item.xml` 中的 "Missing accessibility label" 警告。
    *   為 `AutoCompleteTextView` 新增 `android:hint` (配合 `transparent` 提示文字顏色)，確保無障礙服務能正確識別輸入框用途，消除 Lint 警告。
*   **動態表單角色圖示修復:**
    *   修復在 `ReminderSettingsFragment` 中，動態生成的藥物輸入表單 (`medication_input_item.xml`) 內的圖片固定顯示為酷洛米的問題。
    *   現在新增或編輯藥物時，表單內的圖片會正確跟隨設定頁面的角色選擇 (酷洛米/櫻桃小丸子) 進行切換。
    *   實作 `updateCharacterImage()` 同步更新所有已存在的動態卡片圖片。
*   **動態更新頻道列表 (SettingsFragment):**
    *   **GitHub Releases 整合:** 移除了手動「自訂頻道」輸入功能，改為自動從 GitHub API 獲取所有 Release Tags。
    *   **智能解析:** 自動過濾出以 `nightly-` 開頭的標籤，並解析出分支名稱 (例如 `nightly-feat-ui` -> `feat-ui`)，動態填充至更新頻道列表供使用者選擇。
    *   **列表排序:** 優先顯示 `Stable (Main)` 與 `Current`，接著顯示自動抓取的其他開發分支。
*   **SettingsFragment 優化:**
    *   **設定頁面「關於」區塊:** 實作連結跳轉 (GitHub Profile, Repo, Releases)。
    *   **UI 遮擋修復:** 加入 `OnApplyWindowInsetsListener` 防止底部內容被系統手勢導航條遮擋。
    *   **底部導航列修復:** 修復從設定頁面返回時，底部主分頁按鈕顯示異常的問題。

### Code Quality
*   **SettingsFragment 警告修復:**
    *   **未使用導入:** 移除了 `SettingsFragment.kt` 中未使用的 `android.content.Context`。
    *   **多餘條件:** 移除了 `setupUpdateChannelPreference` 中 `if (currentChannel != "main")` 與 `currentChannel.isNotEmpty()` 的多餘檢查，簡化了列表構建邏輯，直接判斷 `entryValues` 是否包含當前頻道。
    *   **資源清理:** 移除了 `strings.xml` 中未使用的 `add_custom_channel_title`, `add_custom_channel_hint` 資源，解決了翻譯遺失的 Error。
    *   **排版修正:** 將 `update_channel_summary` 中的省略號從 `...` 替換為標準的 Unicode 省略號 `…` (&#8230;)。

### DevOps (Previous)
*   **CI/CD 問題排查:**
    *   **Delete Trigger:** 確認 GitHub Actions 的 `delete` 事件觸發器需要 Workflow 檔案存在於 **預設分支 (Default Branch, 通常是 main)** 才能生效。若只在開發分支修改了 `.yml` 但未合併至 Main，刪除其他分支時將不會觸發 Cleanup Job。
*   **CI/CD 修復:**
    *   **Cleanup Job 升級:** 將 `dev-drprasad/delete-tag-and-release` 升級至 `v1.1`，並修正 Token 傳遞方式，解決刪除分支後 Release 未能正確移除的問題。
*   **分支發布管理 (Branch Release Cleanup):**
    *   **GitHub Actions:** 在 `.github/workflows/android-cicd.yml` 中新增了 `delete` 事件觸發器。
    *   **自動清理:** 當 Git 分支被刪除時，Workflow 會自動執行 `cleanup` 作業，刪除對應的 `nightly-<branch>` Release 和 Tag，並清理 `gh-pages` 分支上的 `update_<branch>.json` 設定檔，保持發布環境整潔。
*   **版本號顯示修復:**
    *   **Dev 頻道顯示:** 更新 `app/build.gradle.kts`，當分支為 `dev` 時，版本號將顯示為 `<Version> dev <CommitCount>` (例如 `1.2.0 dev 201`)，不再顯示為 `nightly`。
    *   **Nightly 頻道:** 非 `main` 且非 `dev` 的分支維持 `<Version> nightly <CommitCount>` 格式。
*   **多頻道 (Multi-Channel) CI/CD 架構:**
    *   **動態頻道:** 支援基於 Git 分支名稱的動態更新頻道 (例如 `dev`, `feat-new-ui`, `fix-login-bug`)。每個分支現在都擁有獨立的 `update_<branch>.json` 更新設定檔與 Nightly Release。
    *   **Gradle 配置:** 更新 `app/build.gradle.kts`，自動將 Git 分支名稱轉換為安全的 `UPDATE_CHANNEL` 並注入 `BuildConfig`。
    *   **GitHub Actions:** 更新 `.github/workflows/android-cicd.yml`，針對 `push` 事件自動生成對應頻道的 JSON 設定檔，並利用 `gh-pages` 部署。
    *   **App 邏輯:** 重構 `UpdateManager.kt` 與 `SettingsFragment.kt`，現在 App 會自動根據建置時的分支 (`BuildConfig.UPDATE_CHANNEL`) 檢查對應的更新來源。
    *   **UI 調整:** 設定頁面中的「更新頻道」選項改為唯讀顯示 (或根據最新改動為可選列表)，直接告知使用者當前所在的頻道。

### UI/UX (Previous)
*   **設定頁面「關於」區塊:**
    *   在 `SettingsFragment` 中實作了「關於」區塊的連結跳轉。
    *   點擊「作者」、「專案」、「版本」可分別開啟 GitHub Profile, Repo 與 Releases 頁面。
    *   在 `preferences.xml` 新增 `app_project` 設定項。
*   **UI 遮擋修復:** 在 `SettingsFragment` 加入 `OnApplyWindowInsetsListener`，防止底部內容被系統手勢導航條遮擋。
*   **底部導航列修復:** 修復從設定頁面返回時，底部主分頁按鈕顯示異常的問題。

### Bug Fixes
*   **國際化 (i18n):** 修復設定頁面「關於」區塊無英文翻譯問題 (`values-en/strings.xml`)。
*   **UpdateManager 清理:** 移除重複變數宣告與無效的空值檢查。
*   **BuildConfig 類型錯誤:** 修正 `build.gradle.kts` 中 `UPDATE_CHANNEL` 的定義。
*   **SettingsFragment 優化:** 修復 `ListPreference` 動態新增頻道的邏輯錯誤。

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
