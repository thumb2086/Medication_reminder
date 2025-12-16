# 待辦事項 (To-Do List)

- [ ] **修復 Application ID 設定:**
    - [ ] 修改 `config.gradle.kts`，將 `baseApplicationId` 從 `com.example.medicationreminderapp` 改回 `com.thumb2086.medication_reminder`，解決無法更新與 Google Play 上架問題。
- [ ] **解決 Baseline Profile 安裝錯誤:**
    - [ ] 修改 `app/build.gradle.kts`，在 `release` buildType 中加入 `baselineProfile.isEnabled = false` (或相應 DSL)，以避免 `INSTALL_BASELINE_PROFILE_FAILED` 錯誤。
- [ ] **驗證 APK 檔名與 JSON 內容匹配:** 檢查 `android-cicd.yml` 是否正確將 `archivesBaseName` (Gradle 產出) 寫入 JSON。
- [x] **UI 修復:**
    - [x] **WiFi 圖示顏色修復:** 將 `ic_wifi.xml` 的填充顏色改為白色，並套用 `?attr/colorControlNormal` tint，以解決在深色模式下不可見的問題。
- [x] **自動化版本號同步:**
    - [x] **CI/CD 自動抓取最近 Tag:** 修改 `android-cicd.yml`，使用 `git describe --tags` 動態獲取最近的 Tag (例如 `v1.2.1`) 作為 Base Version。
    - [x] **Gradle 支援覆寫:** 修改 `app/build.gradle.kts`，支援透過 `-PciBaseVersion` 參數覆寫 `baseVersionName`。
    - [x] **移除舊機制:** 移除之前 `android-cicd.yml` 中使用 `sed` 修改 `config.gradle.kts` 的步驟，改為完全動態傳參。
- [x] **功能優化:**
    - [x] **JSON 檢查:** 確認 `fetchAvailableChannels` 能正確解析 GitHub API 返回的 JSON 結構。
