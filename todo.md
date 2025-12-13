# 待辦事項 (To-Do List)

- [ ] DevOps: 修復 `android-cicd.yml` Cleanup Job
    - [ ] 將 `github.event.ref` 放入環境變數 `DELETED_BRANCH`，避免直接在 script 中注入導致的潛在問題。
    - [ ] 改用 `tr '/-' '__'` 替換 `sed`，避免正則表達式在 YAML 中的轉義問題。
    - [ ] 增加 `gh release list` 除錯訊息，以便在失敗時確認當前 Release 列表。
