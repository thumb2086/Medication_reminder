# To-Do List

- [ ] **測試與除錯**:
    - [ ] 移除 `MainViewModel` 中的模擬數據 (`loadSimulatedData`)。
    - [ ] 移除 `MainViewModel` 中為了測試圖表而強制設定 `isBleConnected = true` 的邏輯。

- [ ] **App 功能擴充**:
    - [ ] **支援 ESP32 v21.0 鬧鐘功能**:
        - [ ] 實作 `CMD_SET_ALARM (0x41)` 指令，將 App 設定的提醒時間同步到藥盒。
        - [ ] 處理 `CMD_REP_PROTO_VER (0x83)` 回報的版本號 3。
    - [ ] **錯誤處理優化**:
        - [ ] 在 `BluetoothLeManager` 中處理 `CMD_ERROR (0xEE)`，並根據錯誤碼顯示對應訊息 (例如 0x03 未知指令, 0x04 ID 錯誤, 0x05 長度錯誤)。

- [ ] **文件更新**:
    - [ ] 更新 `log.md`。
    - [ ] 更新 `README.md` 和 `README_cn.md` 關於 Protocol v3 的變更。
