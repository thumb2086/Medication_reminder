# 待辦事項 (To-Do List)

- [x] **自動化版本號同步:**
    - [x] **CI/CD 自動抓取最近 Tag:** 修改 `android-cicd.yml`，使用 `git describe --tags` 動態獲取最近的 Tag (例如 `v1.2.1`) 作為 Base Version。
    - [x] **Gradle 支援覆寫:** 修改 `app/build.gradle.kts`，支援透過 `-PciBaseVersion` 參數覆寫 `baseVersionName`。
    - [x] **移除舊機制:** 移除之前 `android-cicd.yml` 中使用 `sed` 修改 `config.gradle.kts` 的步驟，改為完全動態傳參。
- [x] **修復 CI/CD 清理腳本:**
    - [x] 修改 `android-cicd.yml` 中的 Cleanup Job，改用 `gh release list --json tagName` 搭配 `grep` 進行精準搜尋，解決舊版腳本無法刪除動態 Tag (如 `1.2.1-nightly-fix-wifi-287`) 的問題。
- [ ] **驗證 APK 檔名與 JSON 內容匹配:** 檢查 `android-cicd.yml` 是否正確將 `archivesBaseName` (Gradle 產出) 寫入 JSON。
- [x] **功能優化:**
    - [x] **JSON 檢查:** 確認 `fetchAvailableChannels` 能正確解析 GitHub API 返回的 JSON 結構。
