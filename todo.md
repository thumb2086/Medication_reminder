# 待辦事項 (To-Do List)

- [x] 優化手動更新體驗
    - [x] `UpdateManager.kt`: 修改 `UpdateInfo` 資料結構，新增 `isNewer` 欄位。恢復 `forceUpdate = isManualCheck` 以便在版本相同時仍能取得安裝包資訊。
    - [x] `SettingsFragment.kt`: 根據 `UpdateInfo.isNewer` 區分 UI 行為。
        - 若有新版本：顯示標準更新對話框。
        - 若無新版本：顯示 Toast「已是最新版本」，並彈出詢問是否重新安裝的對話框。
- [ ] 驗證 APK 檔名與 JSON 內容是否匹配
    - [ ] 檢查 `android-cicd.yml` 是否正確將 `archivesBaseName` (Gradle 產出) 寫入 JSON。
- [ ] 解決 `Nightly` 版本無法更新 `Dev` 版本的權限/路徑問題
    - [ ] 確認 `isManualCheck` 是否正確傳遞並繞過版本號檢查。
    - [ ] 檢查是否因 Application ID 不同 (nightly vs dev) 導致無法直接 "更新" (其實是安裝另一個 App)。提示使用者這會安裝另一個 App，而非原位更新。
