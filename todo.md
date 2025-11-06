# To-Do

- [x] **修復 `MainActivity.kt` 中的 SharedPreferences 警告：**
    - [x] 讀取 `MainActivity.kt` 檔案。
    - [x] 找到第 263 行的 `prefs.edit().putBoolean(...).apply()`。
    - [x] 將其修改為使用 KTX 擴充函式的 `prefs.edit { ... }` 形式。
    - [x] **品質檢查:**
        - [x] 執行 `Sync Project with Gradle Files`。
        - [x] 執行 `Build App`。
        - [x] 調用 `analyze_current_file` 確保警告已消除。
    - [x] **更新日誌 (`log.md`):** 將此修正作為一個新的 "Bug Fixes" 項目記錄下來。
    - [x] **任務清理:** 完成後清理 `todo.md`。
# To-Do

- [ ] **今日計畫 (協定版本化與專案優化):**
    - [ ] **分析與規劃:**
        - [ ] 審查 `README_cn.md`，分析當前藍牙協定的結構。
        - [ ] 制定通訊協定版本化方案，增加一個「協定版本號」指令。
        - [ ] 從「架構」、「程式碼品質」、「UI/UX」和「穩定性」四個方面提出具體的優化建議。
    - [ ] **文件與協定修改:**
        - [ ] 在 `README_cn.md` 和 `README.md` 中新增「通訊協定版本化」章節。
        - [ ] 新增 `0x01` (請求協定版本) 和 `0x71` (回報協定版本) 兩個新指令。
        - [ ] 在 `README_cn.md` 和 `README.md` 中新增「未來優化方向」段落，總結分析結果。
    - [ ] **程式碼修改 (關鍵):**
        - [ ] 在 `BluetoothLeManager.kt` 中實作邏輯：連線成功後，立即發送 `0x01` 請求協定版本。
        - [ ] 根據藥盒回報的版本號，動態選擇使用「批次解析 (`0x91`)」還是「單筆解析 (`0x91`)」的邏輯，實現向下兼容。
    - [ ] **專案檢查:**
        - [ ] 執行 `Build App` 和 `Sync Project with Gradle Files`。
        - [ ] 調用 Project Error 檢查版本判斷邏輯是否正確。
    - [ ] **日誌記錄:** 將今天的重大變更寫入 `log.md`。
    - [ ] **任務清理:** 將今天已完成的計畫從 `todo.md` 中移除，並將具體的優化任務加入待辦清單。


