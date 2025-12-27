# 待辦事項 (To-Do List)

### 下一步


### 已完成

*   [x] **全面國際化 (i18n) 修正**
    *   找出所有在 `SettingsFragment.kt` 和其他相關檔案中硬編碼 (hardcoded) 的中文字串。
    *   將這些字串抽取到 `values/strings.xml` 中。
    *   在 `values-en/strings.xml` 中提供完整的英文翻譯。
    *   確保 App 在英文介面下，不會再出現任何中文提示。
*   [x] **繼續完成 WiFi 設定頁面**
    *   讓使用者能在此頁面中輸入 Wi-Fi 名稱 (SSID) 與密碼。
    *   將 Wi-Fi 憑證透過藍牙傳送給 ESP32 裝置。
    *   處理傳送成功與失敗的各種情況，並提供明確的 UI 反饋。
