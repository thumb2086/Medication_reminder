# 待辦事項 (To-Do List)

- [ ] (Optional) 觀察降級安裝行為: 跨頻道更新若涉及 VersionCode 降級 (例如 Dev -> Stable)，Android 系統安裝程式可能會拒絕更新。需確認是否需要提示使用者先移除舊版。
- [ ] DevOps: 修正 CI/CD 生成的 APK 檔案名稱不正確 (MedicationReminder-nightly.apk)
    - 問題: 使用者回報編譯出的 APK 名稱是 `MedicationReminder-nightly.apk`，而非包含版本的名稱。
    - 分析: `android-cicd.yml` 中的 `Rename APK` 步驟邏輯可能在找不到版本號時 fallback 到 "nightly"。
    - 解決: 檢查 `android-cicd.yml` 中抓取版本號的邏輯 (`./gradlew -q app:properties | grep '^versionName:'`)。
