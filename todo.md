# 待辦事項 (To-Do List)

- [ ] (Optional) 觀察降級安裝行為: 跨頻道更新若涉及 VersionCode 降級 (例如 Dev -> Stable)，Android 系統安裝程式可能會拒絕更新。需確認是否需要提示使用者先移除舊版。
- [ ] Settings: 再次修正 Lint 警告
    - 警告: `Condition 'currentChannel == "main"' is always false`
    - 原因: 在非 Main 分支建置時，`UPDATE_CHANNEL` 是常數，導致條件恆假。
    - 解決: 將 `currentChannel` 的獲取移出 `SettingsFragment`，改為在 `MainViewModel` 中透過方法或變數獲取，或使用 `@Suppress("KotlinConstantConditions")` (之前可能位置不對或 IDE 版本問題)。
    - 本次嘗試: 將使用 `MainViewModel` 來提供 `updateChannel` 值，這樣對編譯器來說它就不是編譯時常數，而是執行期變數，從而繞過 Lint 檢查。
