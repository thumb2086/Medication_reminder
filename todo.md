# To-Do List

- [x] **ESP32 韌體優化**:
    - [x] 修改 `esp32.ino`:
        - [x] 修正錯誤代碼 3 (未知指令) 問題。
        - [x] 新增 `CMD_SUBSCRIBE_ENV` (0x32) 與 `CMD_UNSUBSCRIBE_ENV` (0x33) 指令，用於開啟/關閉即時溫濕度推送。
        - [x] 實作定時推送即時溫濕度數據的邏輯 (每 5 秒推送一次)。
        - [x] 確保歷史數據傳輸包含正確的時間戳記。
- [x] **App 優化**:
    - [x] 修改 `BluetoothLeManager.kt`:
        - [x] 新增 `enableRealtimeSensorData()` 與 `disableRealtimeSensorData()` 方法。
        - [x] 在連線成功後自動啟用即時數據推送。
        - [x] 修正 `handleIncomingData` 中 `CMD_REPORT_ENV` (0x90) 的解析邏輯，確保小數點位數正確。
    - [x] 修改 `EnvironmentFragment.kt`:
        - [x] 設定 MPAndroidChart 為折線圖 (LineChart)。
        - [x] 啟用 Y 軸數值顯示 (`axisLeft.setDrawLabels(true)`).
        - [x] 優化圖表樣式 (線寬、圓點等)。
- [x] **文件更新**:
    - [x] 更新 `log.md` 記錄變更。
    - [x] 更新 `README.md` 與 `README_cn.md` 說明新的協定與功能。

- [x] **診斷與日誌**: 在 `BluetoothLeManager.kt` 中加入詳細的 Log (TX/RX)。
- [x] **App 圖表修復**: 修正 MPAndroidChart Timestamp Offset。
- [x] **App 數據邏輯優化**: 確保 `MainViewModel` 排序。
- [x] **藍牙穩定性**: 加強數據長度檢查。
- [x] **文件更新**: 更新 `README.md`。