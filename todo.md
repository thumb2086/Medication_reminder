# 待辦事項 (To-Do List)

- [ ] (Optional) 觀察降級安裝行為: 跨頻道更新若涉及 VersionCode 降級 (例如 Dev -> Stable)，Android 系統安裝程式可能會拒絕更新。需確認是否需要提示使用者先移除舊版。
- [ ] DevOps: 修正 CI/CD 生成的 APK 檔案名稱不正確
    - 問題: 使用者回報編譯出的 APK 名稱是 `-v1.2.0-nightly-246.apk`，表示 `$appName` 變數遺失或為空。
    - 分析: `app/build.gradle.kts` 中 `appName` 來自 `config.gradle.kts` 的 `extra["appConfig"]`。如果本地編譯正常，但在 CI/CD 異常，可能是 Gradle 配置讀取順序問題，或者是 `archivesBaseName` 設定被覆寫或未生效。
    - 關鍵發現: 檔名 `-v1.2.0...` 代表 `appName` 是空字串。檢查 `finalArchivesBaseName` 的組裝邏輯。
    - 解決: 確保 `appName` 在所有情況下都有預設值，並檢查 `android-cicd.yml` 是否有環境變數干擾。
