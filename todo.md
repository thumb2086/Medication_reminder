# 待辦事項 (To-Do List)

- [ ] DevOps: 驗證 `delete` 事件觸發器是否正確運作。
    - [ ] **注意：** 當您推送代碼 (Push) 時，`cleanup` 作業顯示 **"Skipped" (已略過)** 是**正常的**。因為它的觸發條件是 `delete` 事件。
    - [ ] **測試步驟：**
        1. 確保 `android-cicd.yml` 的變更已合併至預設分支 (`main`)。
        2. 刪除一個測試分支 (例如 `dev` 或 `feat-test`)。
        3. 觀察 Actions 頁面，應該會觸發一個新的 Workflow (由 `delete` 事件觸發)，其中 `cleanup` 作業會執行，而 `build` 作業會被略過。
