# 待辦事項 (To-Do List)

### 下一步

*   [ ] **驗證 App 內更新邏輯**
    *   編譯並執行 App，確認自動更新與手動更新的行為符合預期。

### 已完成

*   [x] **修正編譯錯誤與更新邏輯**
    *   修正 `strings.xml` 中 "update_channel_stable" not found in default locale 的編譯錯誤。
    *   修正 `UpdateManager.kt` 的跨頻道更新檢查邏輯，確保 App 只檢查當前選擇的更新頻道。
    *   修正 `MainActivity.kt` 中的多餘程式碼限定詞警告。
