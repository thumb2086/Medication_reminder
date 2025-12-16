# 待辦事項 (To-Do List)

- [ ] **驗證 APK 檔名與 JSON 內容匹配:** 檢查 `android-cicd.yml` 是否正確將 `archivesBaseName` (Gradle 產出) 寫入 JSON。
- [ ] **解決 Nightly 版本無法更新 Dev 版本問題:**
    - [x] **修復 isNewerVersion 參數寫死:** `UpdateManager.kt` 中 `isNewerVersion` 的 `local` 參數已修復。
    - [x] **確認 isManualCheck 邏輯:** `SettingsFragment.kt` 和 `UpdateManager.kt` 已更新，當 `isManualCheck` 為真時會繞過版本檢查。
    - [x] **Application ID 檢查:** `UpdateManager.kt` 新增 `isDifferentAppId` 邏輯，`SettingsFragment` 提示使用者這是安裝新 App。
- [ ] **功能優化:**
    - [ ] **JSON 檢查:** 確認 `fetchAvailableChannels` 能正確解析 GitHub API 返回的 JSON 結構。
