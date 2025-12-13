# 待辦事項 (To-Do List)

- [ ] DevOps: 確認並優化 `android-cicd.yml` 的 Cleanup 邏輯
    - [ ] 移除 `cleanup` job 中重複的 `Extract Branch Name` 步驟，將邏輯統一整合至刪除步驟中，避免變數傳遞問題。
    - [ ] 確保 `sed` 語法與 Build job 完全一致。
    - [ ] **重要提醒:** 必須告知使用者 Cleanup Workflow 需合併至 Main 分支才生效。
