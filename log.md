# 更新日誌

## Code Quality
*   **0127:** **修復 SettingsFragment 中的未使用導入警告。**
    *   **Import Cleanup:** 移除了 `android.net.Uri` 的導入，因為使用了 KTX 擴充 `androidx.core.net.toUri`，不再直接依賴原始 Uri 類別解析字串。
*   **0127:** **修復 SettingsFragment 中的代碼警告。**
    *   **KTX 擴充:** 將 `Uri.parse(url)` 替換為 `url.toUri()`，使代碼更符合 Kotlin 風格。
    *   **未使用的參數:** 將 `catch` 區塊中的 `e` 參數重命名為 `_`，以表明該變數未被使用。
*   **0127:** **修復 SettingsFragment 中的編譯錯誤。**
    *   **ListPreference 處理:** 修正了動態新增更新頻道的邏輯。
        *   將 `isNotEmpty()` 用於 `CharSequence` 檢查而非 `Array`。
        *   正確將 `CharSequence` 型別新增到 `MutableList<CharSequence>`，解決了型別不匹配問題。
        *   將字串格式化結果賦值給 `listPref.summary` 時進行了空值檢查與型別轉換。

## UI/UX
*   **0127:** **實作設定頁面「關於」區塊連結跳轉。**
    *   **SettingsFragment:** 在 `onPreferenceTreeClick` 中實作了 URL 開啟邏輯。
        *   點擊「作者」會開啟 GitHub Profile (https://github.com/thumb2086)。
        *   點擊「專案」會開啟 GitHub Repo (https://github.com/thumb2086/Medication_reminder)。
        *   點擊「版本」會開啟 GitHub Releases 頁面 (https://github.com/thumb2086/Medication_reminder/releases)。
    *   **UI:** 在 `preferences.xml` 中新增了 `app_project` 偏好設定項，並將相關字串資源 (`about_project`) 加入 `strings.xml` (中/英)。

## Bug Fixes
*   **0127:** **修復設定頁面「關於」區塊無英文翻譯問題。**
    *   **國際化 (i18n):** 將 `preferences.xml` 中硬編碼的中文標題 ("關於", "作者", "版本") 提取至 `strings.xml` 資源檔 (`about_category`, `about_author`, `about_version`)。
    *   **翻譯:** 在 `values-en/strings.xml` 中新增了對應的英文翻譯，確保在英文語系下能正確顯示 "About", "Author", "Version"。

## DevOps
*   **0127:** **實作多頻道 (Multi-Channel) CI/CD 架構。**
    *   **動態頻道:** 支援基於 Git 分支名稱的動態更新頻道 (例如 `dev`, `feat-new-ui`, `fix-login-bug`)。每個分支現在都擁有獨立的 `update_<branch>.json` 更新設定檔與 Nightly Release。
    *   **Gradle 配置:** 更新 `app/build.gradle.kts`，自動將 Git 分支名稱轉換為安全的 `UPDATE_CHANNEL` 並注入 `BuildConfig`。
    *   **GitHub Actions:** 更新 `.github/workflows/android-cicd.yml`，針對 `push` 事件自動生成對應頻道的 JSON 設定檔，並利用 `gh-pages` 部署，同時保留其他頻道的設定檔 (`keep_files: true`)。
    *   **App 邏輯:** 重構 `UpdateManager.kt` 與 `SettingsFragment.kt`，現在 App 會自動根據建置時的分支 (`BuildConfig.UPDATE_CHANNEL`) 檢查對應的更新來源，無需使用者手動切換頻道。
    *   **UI 調整:** 設定頁面中的「更新頻道」選項改為唯讀顯示，直接告知使用者當前所在的頻道。

## DevOps
*   **0127:** **修復 Nightly 版本號溢出 (Integer Overflow) 問題。**
    *   **CI/CD:** 將 `.github/workflows/android-cicd.yml` 中的時間戳格式從 `yyyyMMddHH` (10位數, 可能溢出 32-bit Integer) 修改為 `yyMMddHH` (8位數, 如 `25012710`)。
    *   此修改確保了生成的 Version Code (約 25,000,000) 遠小於 Integer 上限 (2,147,483,647)，同時保持了版本號的單調遞增特性。

## DevOps
*   **0127:** **修復 Nightly 版本倒退與無法更新問題。**
    *   **CI/CD 更新:** 修改 `.github/workflows/android-cicd.yml`，在建置流程中引入了 `TIMESTAMP` (格式: `yyyyMMddHH`)。
        *   現在 Gradle 建置時會接收環境變數 `VERSION_CODE_OVERRIDE`，將版本號 (VersionCode) 設定為當前時間戳。
    *   **Gradle 配置:** 修改 `app/build.gradle.kts`，新增讀取 `VERSION_CODE_OVERRIDE` 環境變數的邏輯。
        *   這確保了無論在哪個分支進行建置，只要是較晚建置的版本，其 VersionCode 一定大於舊版本，解決了因切換分支導致 Commit Count 變少而無法更新的問題。

## Bug Fixes
*   **0127:** **修復 UpdateManager 重複宣告與無效條件警告。**
    *   **重複宣告:** 移除了 `UpdateManager.kt` 中意外導致的重複變數宣告 (`currentVersion`, `isUpdateAvailable`)。
    *   **無效條件:** 移除了 `if (responseBody == null)` 的判斷，因為 `response.body` 在 `OkHttp` 的 `response.isSuccessful` 為 true 時理論上不為空，且編譯器提示該條件恆為 false (可能因 Kotlin 的 Null Safety 推斷)，確保代碼簡潔。

## Bug Fixes
*   **0127:** **修復設定頁面 UI 與新增版本資訊。**
    *   **UI 遮擋:** 在 `SettingsFragment` 中加入了 `OnApplyWindowInsetsListener`，動態為列表底部增加 Padding，防止內容被系統手勢導航條遮擋。
    *   **UI 異常:** 修復了從設定頁面切換 App 再返回時，底部主分頁按鈕錯誤顯示的問題 (移除了 `onPause` 中的重置邏輯)。
    *   **版本資訊:** 在設定頁面底部新增了「關於」區塊，顯示作者與當前 App 版本號。

## Bug Fixes
*   **0126:** **修復 App 內更新下載後無法自動安裝問題 (Part 3)。**
    *   **路徑解析修復:** 
        *   重構了 `UpdateManager` 中的 `onReceive` 邏輯。現在會優先嘗試從 `DownloadManager` 的 `COLUMN_LOCAL_URI` 獲取檔案路徑。
        *   新增了**回退機制 (Fallback)**：若 URI 解析失敗或為空，會自動回退至預期的下載目錄 (`Download/`) 尋找同名檔案，解決了因 `DownloadManager` 路徑返回不穩定導致的安裝失敗。
        *   **日誌增強:** 加入了更詳細的 Log 輸出 (`Install target found`, `Could not resolve file via URI`)，方便排查路徑問題。

## Bug Fixes
*   **0126:** **修復 UpdateManager Lint 警告 (Part 2)。**
    *   **Runtime Check:** 將 `BuildConfig.DEBUG` 替換為 `ApplicationInfo.FLAG_DEBUGGABLE` 運行時檢查。
    *   **Lint Warning:** 移除了不再需要的 `@Suppress("ConstantConditionIf")`，徹底解決了「條件恆為假」與「多餘抑制」的警告，同時確保在 Debug 版本中仍能正確彈出簽名不符的提示。

## Bug Fixes
*   **0125:** **修復 UpdateManager Lint 錯誤與警告。**
    *   **Lint Error:** 修復 `UnspecifiedRegisterReceiverFlag` 錯誤。將手動的 `if (Build.VERSION.SDK_INT >= TIRAMISU)` 判斷替換為 `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_EXPORTED)`，這既符合安全規範，也簡化了代碼。
    *   **Lint Warning:** 
        *   為 `BuildConfig.DEBUG` 判斷加入 `@Suppress("ConstantConditionIf")`，消除 Release 建置時的「條件恆為假」警告。
        *   確認 `android.app.AlertDialog` 確有使用，無需移除。

## Bug Fixes
*   **0125:** **修復 App 內更新無法自動安裝問題 (API 33+)。**
    *   **原因:** 在 Android 13 (API 33) 及以上版本，若要接收來自系統服務 (如 `DownloadManager`) 的廣播，動態註冊的 `BroadcastReceiver` 必須明確指定 `RECEIVER_EXPORTED`。先前使用 `ContextCompat.RECEIVER_NOT_EXPORTED` 導致應用程式無法接收 `ACTION_DOWNLOAD_COMPLETE` 廣播，因此下載完成後不會自動觸發安裝流程。
    *   **修正:** 在 `UpdateManager.kt` 中，針對 Android 13+ 改用 `Context.RECEIVER_EXPORTED` 註冊廣播接收器，Android 12 及以下則保持預設行為。

## Bug Fixes
*   **0125:** **修復 CI/CD 版本號解析錯誤。**
    *   **問題:** 在 `app/build.gradle.kts` 中使用 `println` 輸出 Keystore 狀態訊息，導致 CI/CD 流程中的 `VERSION_NAME` 變數抓取到額外的日誌資訊 (`Release keystore not found...`)，造成 APK 檔名格式錯誤與建置失敗。
    *   **修正:** 將 `println` 改為 `logger.warn`。在 Gradle 的 `-q` (安靜模式) 下，`logger.warn` 訊息會被自動隱藏，確保 `printVersionName` task 只輸出純淨的版本號字串。

## Configuration
*   **0125:** **修復 CI/CD 與本地簽章不相容問題。**
    *   **雙模組簽章 (Hybrid Signing):** 更新 `app/build.gradle.kts`，採用「優先讀取環境變數 (Cloud)，失敗則回退至 local.properties (Local)」的策略。這解決了 GitHub Actions 無法讀取 `local.properties` 導致建置失敗的問題，同時保留了本地開發的便利性。
    *   **CI/CD 配置:** 更新 `.github/workflows/android-cicd.yml`，將 GitHub Secrets 對應到新的環境變數 (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEYSTORE_PATH`)，與 Gradle 設定保持一致。

## Bug Fixes
*   **0124:** **優化更新安裝流程與權限檢查 (Part 3)。**
    *   **權限檢查:** 在 `UpdateManager.downloadAndInstall` 中加入了對 `canRequestPackageInstalls()` 的檢查。
        *   若未授權「安裝未知應用程式」，現在會彈出 `AlertDialog` 引導使用者前往設定頁面開啟權限，避免安裝意圖被系統靜默攔截或失敗。
    *   **簽名不符警告:** 加入了 `BuildConfig.DEBUG` 檢查。
        *   若偵測到當前為 Debug 版本 (例如從 Android Studio 直接執行)，會彈出 Toast 警告使用者，說明更新可能會因簽名不符 (Debug vs Release) 而失敗，提示其先卸載測試版。

## Configuration
*   **0124:** **修復手動更新時套件無效問題 (Application ID Mismatch)。**
    *   **Application ID:** 修改 `app/build.gradle.kts`，移除了基於分支名稱動態添加後綴 (如 `.dev`, `.fix_xxx`) 的邏輯。
    *   **原因:** 該邏輯導致不同分支建置出的 App 被系統視為不同應用程式，無法互相更新。現在所有分支建置的 App 擁有統一的 Application ID，確保使用者能從本地測試版更新至 GitHub CI/CD 的 Release/Nightly 版本 (需注意簽章一致性)。

## Bug Fixes
*   **0124:** **修復 UpdateManager 警告。**
    *   **代碼清理:**
        *   移除了 `UpdateManager.kt` 中未使用的 `android.os.Build` 引用。
        *   將 `Uri.parse(uriString)` 替換為 KTX 擴充函式 `uriString.toUri()`，保持代碼風格一致。

## Bug Fixes
*   **0124:** **修復 App 內更新點擊後無反應與安裝失敗問題 (Part 2)。**
    *   **安裝失敗 (套件無效):**
        *   在 `DownloadManager.Request` 中明確設定 `MIME Type` 為 `application/vnd.android.package-archive`，確保下載後的檔案被正確識別為 APK。
        *   優化 `installApk` 邏輯，增加檔案大小檢查 (< 1KB 視為無效)，避免嘗試安裝損毀的檔案或錯誤頁面。
    *   **無法自動開始安裝:**
        *   修改 `downloadAndInstall` 中的 `onReceive` 邏輯，改為從 `DownloadManager` 的查詢結果 (`COLUMN_LOCAL_URI`) 獲取下載檔案的真實路徑，而非依賴硬編碼的假設路徑，解決了因路徑不一致導致找不到檔案的問題。
        *   加入了詳細的 Log 輸出，方便追蹤檔案路徑與安裝意圖的建立過程。

## Bug Fixes
*   **0124:** **修復 XML 命名空間警告。**
    *   **Provider Paths:** 移除了 `res/xml/provider_paths.xml` 中未使用的 `xmlns:android` 命名空間宣告，解決了 lint 警告 `Namespace declaration is never used`。

## Bug Fixes
*   **0124:** **修復 App 內更新點擊後無反應與安裝失敗問題。**
    *   **流程優化:**
        *   `UpdateManager` 現在會在下載前主動刪除舊的 APK 檔案，防止 `DownloadManager` 自動重新命名 (如 `App-1.apk`) 導致安裝路徑錯誤。
        *   新增下載開始與失敗的 Toast 提示，提供更明確的用戶反饋。
        *   強化廣播接收器 (`BroadcastReceiver`) 邏輯，增加對 `DownloadManager` 狀態的查詢，確保僅在下載成功 (`STATUS_SUCCESSFUL`) 時觸發安裝。
        *   為 `installApk` 中的 `startActivity` 增加 `try-catch` 保護，防止潛在崩潰。
    *   **配置修正:** 更新 `res/xml/provider_paths.xml`，補上 `<external-files-path>` 設定，確保 `FileProvider` 正確授權安裝程式讀取 APK 檔案。

## DevOps
*   **0123:** **優化更新檢查邏輯與 CI/CD 配置。**
    *   **UpdateManager 改進:**
        *   實作了 `isNewerVersion` 函式，支援 Semantic Versioning (SemVer) 比較，解決了僅依賴字串比對導致的誤判問題。
        *   新增了對 Nightly 版本 (如 `1.2.0-nightly-161`) 的解析邏輯，優先比對 Commit Count 以確認是否有更新。
        *   修正了從 GitHub Assets 檔名 (`MedicationReminder-<Version>.apk`) 提取版本號的邏輯，現在能正確處理包含連字號的版本字串。
    *   **Config Update:** 將 `config.gradle.kts` 中的 `baseVersionName` 更新為 `1.2.0`。
    *   **CI/CD:** 確認 `.github/workflows/android-cicd.yml` 中 APK 命名邏輯與 `UpdateManager` 的解析邏輯一致 (空白替換為連字號)。

## Bug Fixes
*   **0122:** **修復 UpdateManager 警告與字串資源不一致。**
    *   **警告修復:**
        *   移除 `UpdateManager.kt` 中不必要的安全呼叫 `response.body?.string()` (改為 `response.body.string()`)，因為在 `isSuccessful` 檢查後 `body` 不為空。
        *   將 `catch` 區塊中未使用的參數 `e` 改為 `_` (download receiver) 或正確記錄日誌 (checkForUpdates)。
    *   **資源修復:** 修正了 `values-en/strings.xml` 中 `update_channel_entries` 與 `update_channel_values` 數量與預設 `values/strings.xml` 不一致的問題 (從 2 個選項補齊為 3 個：Stable, Dev, Nightly)。

## DevOps
*   **0121:** **優化更新頻道與策略。**
    *   **更新頻道:** 新增 `Stable`, `Dev`, `Nightly` 三個頻道。
    *   **更新邏輯:**
        *   `Stable`: 檢查 `latest` release，比對 `v` tag。
        *   `Dev`: 檢查 `latest-dev` tag release。
        *   **Nightly:** 檢查 `nightly` tag release。
    *   **UI 更新:** `preferences.xml` 與 `strings.xml` 配合更新頻道選項。

## DevOps
*   **0120:** **實作 App 內自動更新功能。**
    *   **更新機制:**
        *   新增 `UpdateManager` (位於 `util/` package)，負責檢查 GitHub Releases 的最新版本。
        *   **Official Channel:** 檢查 `latest` release，比對 Tag Name (去除 `v` 前綴) 是否與目前 `VERSION_NAME` 不同。
        *   **Nightly Channel:** 檢查 `nightly` tag 的 release，比對 Release Notes 是否包含目前版本號，若不包含則提示更新。
    *   **UI 整合:**
        *   **SettingsFragment:** 新增「更新設定」分類，包含「更新頻道」(Official/Nightly) 與「檢查更新」按鈕。
        *   **MainActivity:** App 啟動時自動檢查更新 (目前預設開啟)，若發現新版本則彈出對話框提示下載。
    *   **權限與依賴:**
        *   新增 `INTERNET` 與 `REQUEST_INSTALL_PACKAGES` 權限。
        *   設定 `FileProvider` 以支援 APK 安裝意圖 (Intent)。
        *   新增 `OkHttp` 依賴用於網路請求。

## UI/UX 調整
*   **0120:** **優化設定頁面視覺與修正警告。**
    *   **圖示新增:** 為設定頁面 (`preferences.xml`) 中的所有選項新增了對應的圖示 (`ic_palette`, `ic_language`, `ic_face`, `ic_update`, `ic_developer_mode`)，提升視覺一致性。
    *   **警告修復:**
        *   更新了 `libs.versions.toml` 中的 `androidx.activity`, `swiperefreshlayout` 與 `okhttp` 版本。
        *   移除了 `MainActivity.kt` 中未使用的變數 `shouldCheck`。
        *   在 `UpdateManager.kt` 中改用 KTX 擴充函式 `String.toUri()`。

## DevOps
*   **0119:** **實作三軌發布策略 (Official/Dev/Nightly)。**
    *   **發布策略:**
        *   **Official (v*):** 當 Tag 為 `v` 開頭時觸發，建立永久 Release，供一般使用者自動更新。
        *   **Dev (dev branch):** 當推送到 `dev` 分支時觸發，更新 `latest-dev` 標籤，供 QA/測試人員使用。
        *   **Nightly (others):** 當推送到其他分支 (如 `fix-xxx`) 時觸發，更新 `nightly` 標籤，供開發者實驗。
    *   **CI/CD 更新:** 修改 `.github/workflows/android-cicd.yml`，實作上述三種觸發條件與發布邏輯。
    *   **版本控制:** App 的 `versionCode` 與 `versionName` 已與 GitHub Actions 深度整合，自動對應 Build Number 與 Git 資訊。

## UI/UX 調整
*   **0118:** **修復歷史記錄頁面月份標題顯示並完善英文翻譯。**
    *   **歷史記錄:**
        *   在 `fragment_history.xml` 中新增了 `monthTitle` TextView，用於顯示當前日曆月份。
        *   在 `HistoryFragment.kt` 中實作了 `monthScrollListener`，當使用者滑動日曆時，月份標題會自動更新。
    *   **英文翻譯:**
        *   完善了 `values-en/strings.xml`，補充了缺失的翻譯（如 `connection_settings`），解決了英文模式下出現中文的問題。
        *   修正了部分英文用詞，使其更自然。

## Bug Fixes
*   **0117:** **修復 Android Application ID 非法字元問題。**
    *   **問題:** `app/build.gradle.kts` 中直接使用 Git 分支名稱作為 Application ID 後綴，當分支名稱包含連字號 `-` 時 (例如 `fix-setlist`)，會導致無效的 Package Name 錯誤。
    *   **修正:** 加入了正規表達式過濾邏輯，將連字號替換為底線 `_`，並移除其他非法字元，確保 Application ID 始終符合 Android 規範。

## UI/UX 調整
*   **0116:** **整合 Wi-Fi 設定至設定頁面。**
    *   **選單簡化:** 移除了 Toolbar 下拉選單中獨立的「Wi-Fi 設定」選項，將其整合進「設定」頁面中，簡化了導覽層級。
    *   **設定頁面:** 在 `preferences.xml` 中新增了「連線設定」分類，並加入「Wi-Fi 設定」選項 (`Preference`)，設定了對應的圖示與說明。
    *   **導覽邏輯:** 修改 `SettingsFragment.kt`，實作了 `onPreferenceTreeClick`，當使用者點擊「Wi-Fi 設定」時，導航至 `WiFiConfigFragment`。
    *   **資源新增:** 新增了 `ic_wifi.xml` 圖示與相關字串資源 (`connection_settings`, `wifi_settings_summary`)。

## UI/UX 調整
*   **0113:** **全面修正 Toolbar 下拉選單與標題位置。**
    *   **策略:** 為了徹底解決下拉選單位置異常（覆蓋 Toolbar 或下方留白過大）以及標題垂直置中問題，進行了系統性的樣式重構。
    *   **Toolbar 標題:** 在 `activity_main.xml` 中，將 `MaterialToolbar` 內 `TextView` 的 `translationY` 增加至 `10dp`，確保標題在視覺上精確置中，抵消系統狀態列或佈局邊距的影響。
    *   **下拉選單 (Popup Menu):**
        *   **主題覆蓋:** 建立了專用的 `ThemeOverlay.App.PopupMenu` 和 `ThemeOverlay.App.Toolbar`，並在 `themes.xml` (Day/Night) 中定義。
        *   **屬性強制:** 在 `ThemeOverlay.App.Toolbar` 中強制指定 `actionOverflowMenuStyle` 和 `popupMenuStyle`。
        *   **XML 引用:** 在 `activity_main.xml` 的 `MaterialToolbar` 中明確加入 `app:popupTheme="@style/ThemeOverlay.App.PopupMenu"`，確保樣式被正確套用。
        *   **位置微調:** 將 `dropDownVerticalOffset` 設定為 `4dp` 並保持 `overlapAnchor=false`，這讓選單能穩定地顯示在 Toolbar 下方，且保留適當的間距，消除了不自然的空白。

## UI/UX 調整
*   **0114:** **修正 Toolbar 標題與下拉選單的根本佈局問題，並優化 Wi-Fi 設定頁面。**
    *   **Toolbar 標題:** 移除了 `activity_main.xml` 中 `TextView` 的 `translationY` Hack，改用標準的 `layout_gravity="center"` 來實現垂直置中，確保 Toolbar 的高度計算正確。
    *   **下拉選單 (Popup Menu):** 移除了 `themes.xml` (Day/Night) 中所有硬編碼的 `dropDownVerticalOffset` (設為 0dp)，並將 `overlapAnchor` 設為 `false`，讓系統根據標準 Material Design 規範自動計算位置，解決了選單下方出現大片空白或位置異常的問題。
    *   **Wi-Fi 設定頁面:** 將 `fragment_wifi_config.xml` 的根佈局改為 `ScrollView`，確保在小螢幕或鍵盤彈出時內容可滾動，且按鈕下方不會出現非預期的空白區域。
    *   **修復:** 修正了因 Data Binding ID 衝突而導致的建置失敗。移除了 `themes.xml` 中可能干擾 Popup Menu 高度計算的 `android:popupBackground` 和 `android:colorBackground` 屬性。

## UI/UX 調整
*   **0115:** **再次修正 Toolbar 下拉選單位置及主題切換時分頁顯示問題。**
    *   **Toolbar 下拉選單 (Popup Menu):** 移除了 `themes.xml` (Day/Night) 中 `Widget.App.PopupMenu` 和 `Widget.App.PopupMenu.Overflow` 樣式裡的 `android:dropDownVerticalOffset` 屬性，讓系統回歸 Material Design 預設的垂直偏移邏輯，避免手動調整造成的衝突和不一致。
    *   **主題切換分頁顯示:** 在 `MainActivity.kt` 的 `setupFragmentNavigation()` 方法中，將初始化 `updateUiForFragment(supportFragmentManager.backStackEntryCount > 0)` 的呼叫延遲到 `Handler(Looper.getMainLooper()).post {}` 區塊中執行。這確保了在 Activity 因主題變更而重建時，FragmentManger 狀態已完全恢復，能正確判斷是否有 Fragment 在 back stack 上，從而正確隱藏或顯示底部分頁。

## UI/UX 調整
*   **0112:** **再次修正 Toolbar 下拉選單位置。**
    *   **選單位置:** 將 `themes.xml` (Day/Night) 中的 `dropDownVerticalOffset` 重設為 `0dp`。
    *   **原因:** 先前設定為 `48dp` 是為了解決下方空白過大的問題，但反而導致選單在某些裝置上位置異常。
    *   **策略:** 先回歸預設值，若仍有問題再嘗試使用 `overlapAnchor=true` 配合微調。

## UI/UX 調整
*   **0111:** **修復 Toolbar 標題位置與下拉選單樣式。**
    *   **標題位置:** 在 `activity_main.xml` 中，將標題 `TextView` 加上 `android:translationY="6dp"`，使其在視覺上更向下微調。
    *   **選單留白:** 修正了 Toolbar 下拉選單 (Popup Menu) 下方出現大片空白的問題。
        *   修改 `themes.xml` (Day/Night)，將 `Widget.App.PopupMenu` 與 `Overflow` 的 `android:overlapAnchor` 改為 `false`。
        *   將 `android:dropDownVerticalOffset` 設定為 `4dp`，讓選單自然顯示在按鈕下方，不再需要負值偏移。

## 文檔更新
*   **0110:** **修復 README 警告。**
    *   **License:** 新增 `LICENSE` 檔案 (MIT License)，解決了 `README.md` 與 `README_cn.md` 中無法解析 `LICENSE` 檔案的警告。

## UI/UX 調整
*   **0110:** **最終修正 Toolbar 下拉選單位置。**
    *   **策略調整:** 發現僅使用 `overlapAnchor=true` 和小幅度的 `0dp` 偏移量仍無法完全消除下方留白。
    *   **樣式修改:** 在 `themes.xml` (Day/Night) 中，將 `android:dropDownVerticalOffset` 的值大幅調整為 `-50dp`。
    *   **預期效果:** 強制將下拉選單向上移動，使其頂部與 Toolbar 的按鈕重疊或緊貼，從而徹底消除下方的不自然留白。

## UI/UX 調整
*   **0109:** **修復 Toolbar 下拉選單高度與位置異常。**
    *   **屬性啟用:** 在 `themes.xml` (Day/Night) 中為 `Widget.App.PopupMenu` 和 `Widget.App.PopupMenu.Overflow` 啟用了 `android:overlapAnchor` (`true`)。
    *   **位置重置:** 將 `android:dropDownVerticalOffset` 重設為 `0dp`。
    *   **原理:** `overlapAnchor=true` 會讓 Popup Menu 的錨點與 Toolbar 按鈕重疊，從而更自然地向下展開，解決了選單懸浮過高或下方留白過多的問題。

## UI/UX 調整
*   **0108:** **再次調整 Toolbar 下拉選單位置。**
    *   **調整:** 將 `Widget.App.PopupMenu` 與 `Widget.App.PopupMenu.Overflow` 的 `android:dropDownVerticalOffset` 從 `4dp` 修改為 `-4dp`。
    *   **目的:** 將選單向上移動，更緊密地覆蓋或接近 Toolbar，減少視覺斷層。

## UI/UX 調整
*   **0107:** **優化 Toolbar 下拉選單 (Popup Menu) 的視覺間距。**
    *   **樣式調整:** 修改 `themes.xml` 和 `values-night/themes.xml`，自定義了 `Widget.App.PopupMenu` 和 `Widget.App.PopupMenu.Overflow` 樣式。
    *   **間距修正:** 設定 `android:dropDownVerticalOffset` 為 `4dp`，縮減了選單與 Toolbar 之間的垂直間距，使其更貼近標題列，解決了選單距離過遠導致視覺不緊湊的問題。
    *   **一致性:** 確保所有角色主題 (Kuromi, MyMelody, Cinnamoroll) 與深/淺色模式皆套用此 Popup Menu 樣式。

## DevOps
*   **0106:** **優化版本號格式與 Nightly 發布策略。**
    *   **版本號格式調整:** 
        *   正式版 (`main`): 使用 `config.gradle.kts` 定義的基礎版本號 (例如 `1.1.8`)。
        *   Nightly 版 (其他分支): 格式改為 `<BaseVersion> nightly <CommitCount>` (例如 `1.1.8 nightly 5`)，符合 Spotube 模式。
    *   **CI/CD 策略更新:** 更新 `.github/workflows/android-cicd.yml`，現在所有非 `main` 分支的推送都會觸發 Nightly Build，並發布到 `nightly` tag。
    *   **Build Fix:** 修正了 `app/build.gradle.kts` 中分支名稱包含連字號 `-` 或其他特殊字元導致的 `Android resource linking failed` 錯誤。現在會自動將分支名稱中的非英數字元替換為底線 `_` 以符合 Android Package Name 規範。
*   **0105:** **實作 CI/CD 自動化流程。**
    *   **GitHub Actions:** 新增 `.github/workflows/android-cicd.yml`，設定自動化建置與發布流程。
    *   **分支策略:**
        *   `main` 分支: 自動發布正式版 Release (Tag: `v<VersionName>`)。
        *   `dev` 分支: 自動發布 Nightly Build (Tag: `nightly`)。
    *   **自動化:** 流程包含 JDK 17 環境設定、Gradle 建置、版本號擷取、APK 更名與 GitHub Release 發布。
    *   **文檔更新:** 更新 `README.md` 與 `README_cn.md`，加入 CI/CD 狀態徽章 (Badge) 與相關說明。

## UI/UX 調整
*   **0104:** **修復標題垂直置中問題。**
    *   **問題:** `activity_main.xml` 中標題 `TextView` 的 `paddingTop="15dp"` 導致標題向下偏移，未正確垂直置中。
    *   **修正:** 移除了 `paddingTop` 屬性，並依賴 `layout_gravity="center"` 確保標題在 Toolbar 中正確置中。

## 配置優化
*   **0103:** **實作版本與分支管理自動化。**
    *   **分支結構調整:** 修改 `config.gradle.kts`，將分支結構從 `main/beta/alpha` 簡化為 `main` (正式版) 與 `dev` (開發版)。
    *   **版本自動化:** 更新 `app/build.gradle.kts`，實作基於 Git 的動態版本管理。
        *   **VersionCode:** 自動使用 Git 總提交次數 (`rev-list --count HEAD`)。
        *   **VersionName:**
            *   `main` 分支: `1.1.8.<commitCount>`
            *   其他分支: `1.1.8-<branch>.<commitCount>-<shortHash>`
        *   **ArchivesBaseName:** 自動格式化為 `藥到叮嚀-v<VersionName>`。
        *   **AppName:** 開發分支會自動加上 `(<branch>)` 後綴。
    *   **修復:** 解決了 `build.gradle.kts` 中執行 Git 指令時因 `TimeUnit` 引用錯誤導致的 Sync 失敗問題，並修正了未使用的參數警告。

## UI/UX 調整
*   **0101:** **修正圖表線條顏色與顯示樣式。**
    *   **樣式調整:** 根據使用者回饋，將環境監測圖表調整為「折線圖」樣式。當有多個數據點時，隱藏圓點，只顯示平滑的曲線；只有在單一數據點時，才顯示圓點以確保可見性。
    *   **顏色優化:** 更新了 `colors.xml`和 `values-night/colors.xml`，為圖表的溫度與濕度線條設定了在亮色與暗色模式下都具備良好對比度的顏色，解決了深色模式下線條不可見的問題。

## Bug Fixes
*   **0102:** **修復 App 與 ESP32 之間的協定不一致並新增鬧鐘支援。**
    *   **協定修復:** 修正了 `esp32.ino` 中 `CMD_REPORT_ENG_MODE_STATUS` 的定義錯誤 (從 `0x84` 改為 `0x83`)，使其與 App 和文件一致，解決了工程模式狀態無法正確回報的問題。
    *   **功能擴充 (App):** 在 `BluetoothLeManager.kt` 中實作了 `setAlarm` (`0x41`) 指令，允許 App 將提醒設定同步至藥盒。
    *   **錯誤處理:** 在 App 的藍牙管理器中新增了對 `0xEE` (Error Report) 的處理邏輯，現在能識別並記錄感測器錯誤、未知指令等異常狀況。
*   **0010:** 新增了設定頁面和藥物列表頁面。
*   **0009:** 優化了藥物提醒表單的驗證。
*   **0008:** 修正了因 Gradle 版本目錄不完整而導致的嚴重建置錯誤。
