# To-Do List
- [x] 修改 `SettingsFragment.kt`：將更新頻道選項改為可選列表（ListPreference），而不是唯讀。
- [x] 修改 `UpdateManager.kt`：讀取使用者選擇的更新頻道，而不是只依賴 `BuildConfig.UPDATE_CHANNEL`。
- [x] 修正版本號顯示邏輯：確保從 `Stable` 切換到 `Dev` 或其他頻道時，能正確識別新版本。
- [x] 驗證功能：確保選擇不同頻道後，`checkForUpdates` 會去抓取對應的 `update_<channel>.json`。
- [x] 修復 `values-en/strings.xml` 中資源陣列數量不一致的警告。
- [x] 修復 `UpdateManager.kt` 和 `SettingsFragment.kt` 中 `BuildConfig` 無法解析的錯誤 (已通過 Gradle Build 重建)。
