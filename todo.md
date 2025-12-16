# 待辦事項 (To-Do List)

- [ ] 強制 CI/CD 統一使用 Git Commit Count
    - [x] `android-cicd.yml`: 新增計算 Commit Count 的步驟，並傳遞給 Gradle 和 JSON。
    - [ ] `app/build.gradle.kts`: (已在上一輪修改，無需再次修改) 確認邏輯能接收 `-PciVersionCode`。
- [ ] 驗證 APK 檔名與 JSON 內容是否匹配
    - [ ] 檢查 `android-cicd.yml` 是否正確將 `archivesBaseName` (Gradle 產出) 寫入 JSON。
- [ ] 解決 `Nightly` 版本無法更新 `Dev` 版本的權限/路徑問題
    - [ ] 確認 `isManualCheck` 是否正確傳遞並繞過版本號檢查。
    - [ ] 檢查是否因 Application ID 不同 (nightly vs dev) 導致無法直接 "更新" (其實是安裝另一個 App)。提示使用者這會安裝另一個 App，而非原位更新。
