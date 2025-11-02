### Lint 修復

*   [x] **字串格式**：修正 `strings.xml` 和 `values-en/strings.xml` 中，因為不正確的跳脫字元 (`\`) 和單引號用法，而導致的 `String.format string doesn't match the XML format string` 錯誤。

### UI 修復

*   [x] **工具列與狀態列**：修正工具列在不同輔助顏色下的文字辨識度問題，並實現真正的 Edge-to-Edge 沉浸式效果，解決狀態列背景未填滿的問題。

### 功能開發

*   [x] **工程模式**：
    *   [x] 在設定頁面新增「工程模式」開關。
    *   [x] 定義新的藍牙協定 (`0x13`)，用於通知藥盒 App 的模式切換。
    *   [x] 根據工程模式的狀態，調整 App 內部行為 (需要您提供更詳細的需求)。
