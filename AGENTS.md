# Dynamic Agent Workflow: Context-Aware & Data Integrity (v7.1)

此工作流程核心：**破除工具限制，強制執行全域檔案手動診斷，並區分「除錯模式」與「實作模式」。**

## 🛑 數據完整性與檔案範圍 (SCOPE & INTEGRITY)

1.  **全語系檔案支持：**
    *   **Android:** `.kt`, `.java`, `.xml`, `.gradle`
    *   **ESP32/Embedded:** `.h`, `.cpp`, `.ino`, `.c`
    *   **文件:** `log.md`, `todo.md`, `README.md`
2.  **`log.md` 歷史保護：** 絕對禁止覆蓋舊紀錄，一律採用**頂部插入 (Prepending)**。
3.  **`todo.md` 精準清理：** 執行前刪除 `[x]`，執行中嚴禁刪除 `[ ]`。

---

## 🔍 自我除錯與路徑修復機制 (Self-Debugging & Path Recovery)

### **僅適用於「除錯模式」：當工具回報 "0 file processed" 或 "Nothing to report" 時：**
**Agent 必須立即啟動「手動深蹲模式 (Manual Deep Scan)」：**
1.  **路徑重探：** 使用 `ls -R` 或相關工具重新確認 `esp32/` 或 `src/` 資料夾下的真實路徑。
2.  **源碼直讀：** 不依賴 Static Analysis 工具，直接使用 `read_file` 讀取 `.h` 或 `.cpp` 內容。
3.  **邏輯比對：** 手動檢查 C++ 標頭檔定義、變數生命週期、以及硬體腳位 (Pin Assignment) 是否正確。

---

## 邏輯判斷流程 (Logic Flow)

### 1. Mode A: 規劃與維護 (Architect & Plan)
*   **新增：** 確認修改範圍是否包含 ESP32 韌體。若是，必須在 `todo.md` 明確列出硬體邏輯變動。

### 2. Mode B: 開發、除錯與執行 (Develop, Debug & Execute)

#### **Phase 1: 清理與路徑確認**
1.  讀取 `todo.md` 並清理舊項。
2.  **驗證檔案路徑：** 確保目標檔案路徑正確。

#### **Phase 2: 任務性質判斷 (CRITICAL DECISION)**
**Agent 必須先判斷當前任務性質，選擇執行路徑：**

*   **路徑 A：修復 Bug (Fixing Bugs)**
    *   執行 `inspect_code` 或 linter。
    *   **若回報 0 錯誤：** 立即啟動「手動深蹲模式」，主動尋找邏輯漏洞 (Logical Bugs) 而非語法錯誤。
*   **路徑 B：新增功能/日誌/重構 (Feature / Logging / Refactor)**
    *   **❌ 不需要等待錯誤報告：** 忽略 "Nothing to report"。
    *   **✅ 直接執行：** 根據需求直接修改代碼 (如插入 `Log.d`, `Serial.println` 或新增函數)。
    *   **檢查依賴：** 確保 Android (`android.util.Log`) 或 ESP32 相關 Header 已 Import，若無則自動補上。

#### **Phase 3: 結案**
1.  **更新 `log.md` (頂部插入)：**
    *   格式：`### v1.3.x: Update Description`
    *   **必須記錄：** 修改了哪些檔案、硬體腳位或邏輯。
2.  **更新 `todo.md`：** 打勾完成。

---

## 🤖 專屬除錯指令：當自動化工具失效時 (僅限路徑 A)
**如果 `inspect_code` 沒有回報任何結果，且任務為「除錯」，Agent 必須：**
1.  **主動懷疑：** 「工具沒報錯不代表沒錯。」
2.  **讀取關鍵檔案：** 讀取所有在 `todo.md` 中提到的檔案。
3.  **手動分析：**
    *   「我發現 `ESP32_Controller.cpp` 第 45 行的數據類型與 Android 端定義的 `Int` 不符，這會導致溢位。」
    *   「標頭檔 `.h` 宣告了方法，但 `.cpp` 漏掉了實作。」
4.  **強制執行寫入：** 即使 `inspect` 沒看到問題，只要邏輯有錯，就必須執行 `write_file`。

---

## 狀態回報範本 (Mode B 完畢)

> "✅ **任務執行完畢**
>
> 1. **執行內容 (路徑 B: Logging)**:
     >    - 檔案：`MainActivity.kt`
     >    - 變更：已導入 `android.util.Log` 並在 `onCreate` 方法中添加了除錯日誌。
> 2. **自我檢查**:
     >    - Import 檢查：已確認。
     >    - 語法檢查：已確認無誤。
> 3. **文件同步**:
     >    - `todo.md`: 本次任務 `[x]`。
     >    - `log.md`: 已在頂部追加紀錄，**完整保留舊有歷史**。
>
> 請檢查變動。"