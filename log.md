# 更新日誌

## 配置優化
*   **0103:** **實作版本與分支管理自動化。**
    *   **分支結構調整:** 修改 `config.gradle.kts`，將分支結構從 `main/beta/alpha` 簡化為 `main` (正式版) 與 `dev` (開發版)。
    *   **版本自動化:** 更新 `app/build.gradle.kts`，實作基於 Git 的動態版本管理。
        *   **VersionCode:** 自動使用 Git 總提交次數 (`rev-list --count HEAD`)。
        *   **VersionName:**
            *   `main` 分支: `1.1.8.<commitCount>`
            *   其他分支: `1.1.8-<branch>.<commitCount>-<shortHash>`
        *   **ArchivesBaseName:** 自動格式化為 `藥到叮嚀-v<VersionName>`。
        *   **AppName:** 開發分支會自動加上 `(<branch>)` 後綴。
    *   **修復:** 解決了 `build.gradle.kts` 中執行 Git 指令時因 `TimeUnit` 引用錯誤導致的 Sync 失敗問題，並修正了未使用的參數警告。

## UI/UX 調整
*   **0101:** **修正圖表線條顏色與顯示樣式。**
    *   **樣式調整:** 根據使用者回饋，將環境監測圖表調整為「折線圖」樣式。當有多個數據點時，隱藏圓點，只顯示平滑的曲線；只有在單一數據點時，才顯示圓點以確保可見性。
    *   **顏色優化:** 更新了 `colors.xml` 和 `values-night/colors.xml`，為圖表的溫度與濕度線條設定了在亮色與暗色模式下都具備良好對比度的顏色，解決了深色模式下線條不可見的問題。

## Bug Fixes
*   **0102:** **修復 App 與 ESP32 之間的協定不一致並新增鬧鐘支援。**
    *   **協定修復:** 修正了 `esp32.ino` 中 `CMD_REPORT_ENG_MODE_STATUS` 的定義錯誤 (從 `0x84` 改為 `0x83`)，使其與 App 和文件一致，解決了工程模式狀態無法正確回報的問題。
    *   **功能擴充 (App):** 在 `BluetoothLeManager.kt` 中實作了 `setAlarm` (`0x41`) 指令，允許 App 將提醒設定同步至藥盒。
    *   **錯誤處理:** 在 App 的藍牙管理器中新增了對 `0xEE` (Error Report) 的處理邏輯，現在能識別並記錄感測器錯誤、未知指令等異常狀況。
*   **0100:** **修復即時溫濕度數據解析錯誤。**
    *   **問題:** 藍牙接收到的即時數據 (`0x90`) 仍使用舊版協定解析 (整數+小數)，導致數值顯示異常 (如 140.9%)。
    *   **修正:** 更新 `BluetoothLeManager` 中的解析邏輯，使其與歷史數據 (`0x91`) 一致，採用 Protocol V2 標準 (2-byte 帶號整數 / 100)。

## 架構優化
*   **0099:** **實作 Repository 模式並解決 Hilt 注入限制。**
    *   **架構重構:** 建立了單例 `AppRepository`，將所有資料邏輯（SharedPreferences、藥物列表、溫濕度歷史、服藥狀態）從 `MainViewModel` 中抽離。
    *   **Hilt 修正:** 解決了 `MedicationTakenReceiver` 無法直接注入 `ViewModel` 的 Dagger Hilt 限制。現在，Receiver 透過 Hilt EntryPoint 注入 `AppRepository`，而 `MainViewModel` 也改為依賴這個 Repository，確保了資料存取的單一性與架構的正確性。
*   **0098:** **修復通知「我已服用」後的數據同步與通知取消問題。**
    *   **問題:** `MedicationTakenReceiver` 錯誤地實例化了新的 `MainViewModel`，導致服藥事件無法更新到應用程式的單例 ViewModel 中，服藥紀錄和圖表無法更新。此外，通知有時無法自動消失。
    *   **修正:** 在 `MedicationTakenReceiver.kt` 中導入了 Hilt 的 `EntryPoint` 模式，以確保廣播接收器可以存取到正確的單例 `MainViewModel` 和 `BluetoothLeManager` 實例。同時，確保了 `notificationId` 被正確用於取消通知，解決了通知殘留的問題。
*   **0092:** **實作即時環境數據訂閱機制與圖表優化。**
    *   **協定升級:** 新增 `0x32` (訂閱) 與 `0x33` (取消訂閱) 指令。App 連線後會自動訂閱，藥盒將每 5 秒主動推送即時溫濕度 (`0x90`)，不再依賴 App 輪詢，大幅降低延遲與頻寬消耗。
    *   **App 優化:**
        *   `EnvironmentFragment`: 將圖表切換為 `LineChart`，啟用 Y 軸數值顯示，並優化了線條寬度與圓點樣式，提升易讀性。
        *   `BluetoothLeManager`: 修正了 `0x90` 指令解析邏輯中，小數點位數計算錯誤的 Bug。
    *   **ESP32 韌體:** 同步更新韌體以支援訂閱模式，並修復了歷史數據傳輸時的時間戳記誤差。
*   **0091:** **修復圖表顯示問題與優化藍牙診斷。**
    *   **問題修復:** 修正了 `EnvironmentFragment` 中 MPAndroidChart 因 Unix Timestamp 數值過大導致 Float 精度丟失，進而無法正確顯示圖表的問題。實作了 Timestamp Offset 機制，將 X 軸數值轉換為相對時間，確保了圖表的顯示精度。
    *   **診斷增強:** 在 `BluetoothLeManager` 中加入了詳細的 Log 輸出 (RX/TX)，印出接收到的原始 Hex 數據，方便開發者區分是 ESP32 未發送數據還是 App 解析錯誤。
    *   **邏輯優化:** 確保 `MainViewModel` 在接收到新的即時感測數據時，會將其正確合併並排序，防止數據亂序導致圖表繪製異常。
*   **0090:** **導入 Hilt 實現依賴注入。**
    *   **重構範圍:** 為 `MainViewModel` 和 `BluetoothLeManager` 導入 Hilt 依賴注入。
    *   **程式碼修改:**
        *   在 `build.gradle.kts` 中設定 Hilt。
        *   建立 `MedicationReminderApplication` 並在 `AndroidManifest.xml` 中註冊。
        *   為 `MainActivity` 和 `MainViewModel` 加上 Hilt 註解。
        *   建立 `AppModule` 來提供 `BluetoothLeManager` 的實例。
    *   **優點:** 降低了元件之間的耦合度，提高了程式碼的可測試性和可維護性。
*   **0089:** **將 `MainViewModel` 中的 `LiveData` 重構為 `StateFlow`。**
    *   **重構範圍:** `isBleConnected`, `bleStatus`, `isEngineeringMode`, `historicSensorData`, `medicationList`, `dailyStatusMap`, and `complianceRate`。
    *   **UI 層更新:** 同步更新了 `MainActivity` 和所有相關的 Fragment (`EnvironmentFragment`, `HistoryFragment`, `MedicationListFragment`, `ReminderSettingsFragment`, `SettingsFragment`)，改為使用 `lifecycleScope.launch` 和 `repeatOnLifecycle` 來收集 `StateFlow` 的更新。
    *   **優點:** 提高了 UI 狀態管理的可預測性、線程安全性，並為未來導入更複雜的非同步數據流操作打下了基礎。

## 功能更新
*   **0088:** **引入藍牙協定版本控制並規劃未來優化方向。**
    *   **協定版本化:** 新增了 `0x01`（請求協定版本）和 `0x71`（回報協定版本）指令。App 現在會主動查詢藥盒的協定版本，並根據版本號動態選擇數據解析邏輯（例如，`0x91` 的單筆或批次解析），實現了向下兼容。
    *   **文件更新:** 在 `README.md` 和 `README_cn.md` 中新增了「通訊協定版本化」章節，並加入了「未來優化方向」的規劃，涵蓋架構、程式碼品質、UI/UX 和穩定性四個方面。
*   **0087:** 修正了 `MainActivity.kt` 中因未使用 KTX 擴充函式而產生的 `SharedPreferences.edit` 警告。
*   **0086:** 實現了 App 與藥盒之間工程模式狀態的雙向同步。
    *   **協定擴充:** 新增了 `0x14`（請求工程模式狀態）和 `0x83`（回報工程模式狀態）兩個藍牙指令。
    *   **邏輯優化:** App 在連接成功後，會主動向藥盒請求其當前的工程模式狀態，而不是單向地覆寫它。
    *   **UI 同步:** 設定畫面的「工程模式」開關現在能夠真實反映藥盒的狀態。使用者操作開關時，App 會發送指令，並等待藥盒的狀態回報來更新 UI，確保了狀態的最終一致性。
*   **0085:** 優化了藍牙協定與 App 的數據處理邏輯。
    *   **協定優化:** 將歷史數據回報 (`0x91`) 的格式從單筆更新為批次處理，一次最多可傳輸五筆數據，大幅提高了數據同步效率。
    *   **程式碼修改:** 更新了 `BluetoothLeManager.kt` 中的 `handleIncomingData` 函式，使其能夠解析新的批次數據格式。
    *   **文件更新:** 同步更新了 `README.md` 和 `README_cn.md` 中的藍牙協定文件。
    *   **重要提示:** 此項協定變更需要 ESP32 韌體同步更新。
    *   **0084:** 調整了歷史溫濕度數據的同步時機。
    *   **邏輯修改**: 移除了在 `EnvironmentFragment` 中，每當藍牙連線成功就自動請求歷史數據 (`0x31`) 的邏輯。
    *   **使用者體驗改善**: 現在，歷史數據的同步將只在使用者於「環境監測」頁面執行下拉刷新手勢時觸發，避免了不必要的自動數據傳輸，給予使用者更大的控制權。
*   **0083:** 優化了 App 與 ESP32 之間的藍牙數據協定。
    *   **協定變更**: 將溫濕度數據的傳輸方式，從原本將整數與小數部分拆分的作法，改為更高效、標準的 2-byte 帶正負號整數（原始數值乘以 100）。此修改同時應用於即時數據（`0x90`）和歷史數據（`0x91`）的解析邏輯中，提高了數據處理的穩定性與效率。

## UI/UX 調整
*   **0096:** **圖表可讀性微調。**
    *   **問題:** 在只有單一數據點或數據稀疏的情況下，圖表因沒有圓點而顯得空白。
    *   **調整:** 重新啟用了數據點的圓圈繪製，並設定了適當的大小 (4f) 和空心樣式，同時微調了填充透明度，讓數據在任何情況下都清晰可見。
*   **0094:** **圖表視覺與互動優化。**
    *   **雙軸顯示:** 實作了溫度 (左軸) 與濕度 (右軸) 的雙 Y 軸顯示，解決了數值範圍差異導致的顯示問題。
    *   **視覺簡化:** 移除了圖表上的圓點，僅保留曲線與填充，使畫面更加現代簡潔。加入進場動畫與下拉刷新動畫。
    *   **互動增強:** 新增了 `CustomMarkerView`，當使用者長按圖表時，會顯示選中點的具體時間與數值。

## Bug Fixes
*   **0097:** **修正 ESP32 v21.0 韌體的編譯錯誤。**
    *   **韌體更新:** ESP32 韌體已更新至 v21.0，支援鬧鐘系統 (4 組) 並修復了 Error 3 問題。
    *   **Bug Fix:** 修正了 `esp32.ino` 中因 `INTERVAL_DISPLAY` 變數未定義而導致的編譯錯誤。
    *   **協定升級:** 協定版本已升級至 3，新增 `CMD_SET_ALARM (0x41)` 指令。
*   **0095:** **修復 `EnvironmentFragment` 中的多個程式碼警告。**
    *   **問題:** `String.format` 隱式使用預設 Locale，以及 `setText` 中直接串接字串。
    *   **修正:** 
        *   為所有 `String.format` 呼叫明確加入 `Locale.getDefault()`。
        *   在 `strings.xml` 中新增格式化字串資源 (`marker_view_format`, `chart_value_temp`, `chart_value_humidity`)。
        *   改用 `context.getString(...)` 或 `String.format(...)` 來處理帶有佔位符的字串，避免直接串接。
*   **0093:** **修復藍牙連接時的未知錯誤代碼 3。**
    *   **問題分析:** 錯誤代碼 3 發生在 Android App 傳送新的「請求協定版本 (0x01)」指令時，但 `esp32.ino` 韌體中尚未實作此指令，導致韌體回報未知指令錯誤 (0x03)。
    *   **韌體修復:** 在 `esp32.ino` 中新增了 `CMD_REQUEST_PROTOCOL_VERSION (0x01)` 和 `CMD_REPORT_PROTOCOL_VERSION (0x71)` 的定義。
    *   **邏輯實作:** 在 `handleCommand` 中新增邏輯，當收到 `0x01` 時，回傳當前韌體協定版本 `0x02`，使 App 能夠順利完成協定同步，消除連線錯誤。
*   **0082:** 修正了工具列標題的垂直對齊問題。
    *   **UI 修正**: 透過在 `activity_main.xml` 中，為標題的 `TextView` 加入 `paddingTop` 屬性，成功地將標題向下移動，使其與右側的設定圖示在視覺上對齊。
*   **0081:** 修正了工具列標題的顯示問題。
    *   **問題分析**: 在 `activity_main.xml` 中，直接為標題的 `TextView` 設定 `padding` 會導致其高度計算錯誤，進而發生內容被截斷的問題。
    *   **UI 修正**: 將 `padding` 改為 `layout_marginBottom`，在不影響 `TextView` 高度的前提下，調整了標題的垂直位置，使其在視覺上更置中。
*   **0080:** 修正了因為標題置中而導致的嚴重錯誤，並還原了標題的原始位置。
    *   **問題分析**: 經查，在 `0078` 版中，為了將標題置中而加入了 `supportActionBar?.setDisplayShowTitleEnabled(false)`，但這個修改卻意外地導致 `SettingsFragment` 和 `WiFiConfigFragment` 無法正常顯示。
    *   **緊急修復**: 還原了 `MainActivity.kt` 和 `activity_main.xml` の相關修改，讓頁面恢復正常顯示。
    *   **UI 修正**: 修改了 `MainActivity.kt` 中的 `updateUiForFragment` 函式，確保返回按鈕只在進入下一層頁面時才會顯示，解決了主畫面出現返回按鈕的問題。
*   **0079:** 清理了 `MainActivity.kt` 中的多個警告，包括未使用的 `import` 和未使用的函式參數。
*   **0078:** 修正了工具列標題沒有置中的問題。
    *   **策略調整**: 移除了 `MaterialToolbar` 的 `app:title` 屬性，改為在 `MaterialToolbar` 中加入一個 `TextView`，並設定其 `android:layout_gravity` 為 `center`，以達到標題置中的效果。
    *   **程式碼清理**: 在 `MainActivity.kt` 中，加入了 `supportActionBar?.setDisplayShowTitleEnabled(false)`，以隱藏預設的標題，避免與自訂的標題重疊。
*   **0077:** 修正了在設定頁面中，返回按鈕顏色不正確的問題。
    *   在 `themes.xml` 的 `Widget.App.Toolbar` 樣式中，加入了 `colorControlNormal` 屬性，並將其設定為 `?attr/colorOnPrimary`，確保返回按鈕的顏色能與工具列上的其他圖示和文字顏色保持一致。
*   **0076:** 徹底解決了工具列在角色主題下，文字與圖示顏色不正確的問題。
    *   **策略調整**: 放棄了在 `MainActivity.kt` 中動態設定狀態列顏色的複雜作法，改為在 `activity_main.xml` 中，為 `MaterialToolbar` 直接套用一個名為 `Widget.App.Toolbar` 的獨立主題。
    *   **主題修正**: 在 `themes.xml` 中定義了 `Widget.App.Toolbar` 樣式，並將其 `titleTextColor` 和 `actionMenuTextColor` 設定為 `?attr/colorOnPrimary`，使其能正確地繼承角色主題中定義的 `colorOnPrimary` 顏色（淺色主題為黑色，深色主題為白色），確保了在所有情況下，工具列的文字和圖示都清晰可見。
*   **0075:** 再次修正了工具列的文字與圖示顏色問題，確保在所有主題下都清晰可見。
    *   **淺色主題**: 在 `values/themes.xml` 中，為所有角色主題明確指定了 `android:titleTextColor` 和 `android:actionMenuTextColor` 為黑色，徹底解決了淺色背景下文字顏色不正確的問題。
    *   **深色主題**: 在 `values-night/themes.xml` 中，也為所有角色主題明確指定了 `android:titleTextColor` 和 `android:actionMenuTextColor` 為白色，確保在深色模式下同樣具有良好的對比度。
*   **0074:** 修正了 App 在淺色主題（如角色主題）下，工具列和狀態列的文字與圖示顏色不正確的問題，並修正了深色模式下日曆文字的顏色。
    *   **淺色主題**: 在 `themes.xml` 中，為所有角色主題明確指定 `colorOnPrimary` 為黑色，確保工具列上的文字在淺色背景下清晰可見。
    *   **深色主題**: 修改了 `fragment_history.xml`，將日曆的文字顏色改為 `?android:attr/textColorPrimary`，使其能隨著主題自動調整。
    *   **狀態列圖示**: 在 `MainActivity.kt` 中，加入了動態判斷主題顏色亮度的邏輯，並使用 `WindowInsetsControllerCompat` 來設定狀態列圖示的顏色，確保在任何主題下都具有良好的對比度。

## 功能更新
*   **0073:** 徹底重構了 App 的顏色架構，並對 UI 進行了現代化調整，以提升老年使用者的可讀性與易用性。
    *   **顏色重構**: 將所有顏色定義集中到 `values/colors.xml` 中，並為淺色和深色模式建立了獨立的顏色集。同時，新增了「大耳狗」角色主題，並確保所有角色主題在不同模式下都能正確顯示。
    *   **提升可讀性**: 全面調整了文字顏色，確保在任何背景下都具備足夠的對比度。此外，也放大了 App 內的文字，讓內容更易於閱讀。
    *   **UI 現代化**: 採用 Material 3 設計風格，並更新了所有主題，讓 App 的視覺外觀更加現代化。
*   **0072:** 徹底重新設計了 App 的 UI，並解決了因此產生的 `Android resource linking failed` 建置錯誤。
    *   **主題与顏色**：簡化了 `themes.xml` 和 `colors.xml`，移除了所有 accent color 主題，並定義了一組更清晰、對比度更高的顏色，統一了 App 的視覺風格。
    *   **程式碼清理**：移除了 `MainActivity.kt` 和 `SettingsFragment.kt` 中與舊 accent color 主題相關的無用程式碼。
    *   **資源修復**：修正了 `drawable/ic_slot_filled.xml` 中使用到已刪除顏色的問題，確保了專案能成功建置。

## 功能更新
*   **0071:** 修正了在淺色主題下（如櫻桃小丸子的粉色主題），Toolbar 和狀態列上的文字與圖示顏色不正確的問題。
    *   透過在主題中明確指定 `colorOnPrimary` 為黑色，並使用 `WindowInsetsController` 動態調整狀態列圖示的明暗，確保了在任何淺色背景下，所有文字和系統圖示都具有良好的對比度和可讀性。
*   **0070:** 修正了在挖孔螢幕上，狀態列背景無法正確填滿的沉浸式 UI 問題。
    *   透過在 App 主題中啟用 `windowLayoutInDisplayCutoutMode`，並精確地將系統邊襯區 (insets) 的 `padding` 只套用在 `AppBarLayout` 上，徹底解決了在有挖孔或劉海的裝置上，狀態列背景無法延伸的問題，實現了在所有螢幕類型上都一致的完美沉浸式體驗。
*   **0069:** 將輔助顏色功能與角色選擇功能綁定，並徹底解決了狀態列的沉浸式顯示問題。
    *   **功能整合**: 在設定頁面中，將「角色選擇」與 App 的主題顏色進行綁定。選擇「酷洛米」會將主題色切換為紫色，選擇「櫻桃小丸子」則會切換為粉紅色，簡化了設定選項，並增加了互動的趣味性。
    *   **沉浸式 UI 修復**: 透過調整 `activity_main.xml` 的佈局方式，將背景顏色設定在 `AppBarLayout` 上，並修改 `MainActivity.kt` 中的 `WindowInsetsListener`，成功解決了 App Bar 無法填滿狀態列的問題，實現了真正的 Edge-to-Edge 沉浸式體驗。
*   **0060:** 新增了角色更換功能，讓使用者可以在「酷洛米」和「櫻桃小丸子」之間進行選擇。角色圖片會顯示在「提醒設定」頁面的最下方。

## Bug Fixes
*   **0068:** 徹底重新設計了 App 的 UI，並解決了因此產生的 `Android resource linking failed` 建置錯誤。
    *   **主題與顏色**：簡化了 `themes.xml` 和 `colors.xml`，移除了所有 accent color 主題，並定義了一組更清晰、對比度更高的顏色，統一了 App 的視覺風格。
    *   **程式碼清理**：移除了 `MainActivity.kt` 和 `SettingsFragment.kt` 中與舊 accent color 主題相關的無用程式碼。
    *   **資源修復**：修正了 `drawable/ic_slot_filled.xml` 中使用到已刪除顏色的問題，確保了專案能成功建置。
*   **0067:** 修正了 `MainActivity.kt` 中因缺少 `WindowInsetsCompat` 的匯入而導致的 `Unresolved reference` 編譯錯誤。
*   **0066:** 再次修正了工具列與狀態列的 UI 顯示問題：
    *   **文字顏色**：在 `themes.xml` 中，為每個輔助顏色主題明確地指定了與之對比的 `colorOnPrimary`，確保工具列上的文字和圖示在任何主題下都清晰可見。
    *   **沉浸式效果**：在 `MainActivity.kt` 中，為 `AppBarLayout` 加入了 `OnApplyWindowInsetsListener`，使其能夠正確處理系統邊襯區 (insets)，並動態調整 `padding`，從而完美解決了狀態列背景無法填滿的問題。
*   **0065:** 修正了 `strings.xml` 中因單引號使用不當而引起的 `Apostrophe not preceded by \\` Linter 錯誤。透過將所有包含單引號的字串用雙引號 `""` 包起來，解決了這個問題。
*   **0064:** 修正了 `strings.xml` 和 `values-en/strings.xml` 中因不正確的跳脫字元 (`\`) 和單引號用法而導致的 `String.format string doesn't match the XML format string` Linter 錯誤。
*   **0063:** 修正了工具列與狀態列的 UI 顯示問題：
    *   **文字顏色**：移除了在 `themes.xml` 中為不同輔助顏色主題寫死的 `colorOnPrimary`，讓系統能根據背景顏色自動選擇最佳的文字顏色，解決了在某些顏色 (如粉紅色) 下，工具列文字不易辨識的問題。
    *   **沉浸式效果**：在 `MainActivity.kt` 中呼叫 `WindowCompat.setDecorFitsSystemWindows(window, false)`，實現了真正的 Edge-to-Edge 效果，讓 App 內容能延伸至系統列，解決了狀態列背景未填滿的問題。
*   **0062:** 修正了多個 UI 和功能上的錯誤：
    *   **編譯錯誤：** 透過在 `themes.xml` 中直接設定 `preferenceCategoryTitleTextColor`，取代了先前複雜且容易出錯的 `preferenceTheme`，解決了 `Android resource linking failed` 的編譯問題。
    *   **返回 UI 異常：** 在 `SettingsFragment.kt` 的 `onPause` 方法中，主動呼叫 `updateUiForFragment(false)`，強制 `MainActivity` 在返回時重新整理 UI，確保了狀態列的沉浸式效果不會消失。
*   **0061:** 修正了 UI 顯示問題和設定按鈕的功能異常。
    *   **狀態列問題：** 透過修改 `themes.xml` 和 `values-night/themes.xml`，將 `statusBarColor` 設為透明並啟用 `windowDrawsSystemBarBackgrounds`，實現了沉浸式 (Edge-to-Edge) 效果，解決了狀態列背景未填滿和顏色不正確的問題。
    *   **設定按鈕無效：** 取消了 `MainActivity.kt` 中 `onOptionsItemSelected` 函式裡對設定頁面 (`SettingsFragment`) 和 Wi-Fi 設定頁面 (`WiFiConfigFragment`) 導覽邏輯的註解，恢復了設定按鈕的正常功能。
*   **0059:** 修正了狀態列遮擋工具列標題的問題。透過在 `activity_main.xml` 的 `AppBarLayout` 中加上 `android:fitsSystemWindows="true"` 屬性，確保了工具列會為狀態列預留出正確的空間，解決了內容重疊的問題。
*   **0058:** 修正了當輔助顏色設為「預設」時，工具列顏色不正確的問題。透過將 `colors.xml` 和 `values-night/colors.xml` 中的 `primary` 和 `colorPrimary` 顏色改回 Material Design 的預設值，確保了在未選擇特殊輔助顏色時，App 會顯示正確的預設主題顏色。
*   **0057:** 修正了 `MainActivity.kt` 中因 `activity_main.xml` 佈局變更而導致的編譯錯誤。透過註解對已移除元件 (`kuromiImage` 和 `fragment_container`) 的參考，解決了 `Unresolved reference` 的問題，讓專案能重新建置。
*   **0056:** 復原了 0055 號的 Bug 修復，以解決 `AndroidManifest.xml` 的資源連結失敗問題。
*   **0055:** 再次修正了圖片遮擋輸入欄位的 UI 問題。透過在 `MainActivity.kt` 中監聽 `ViewPager2` 的頁面切換事件，確保只有在非 `ReminderSettingsFragment` 的頁面，酷洛米圖片才會顯示。
*   **0054:** 修正了 `ReminderSettingsFragment` 在挖孔螢幕上顯示異常，以及圖片遮擋輸入欄位的 UI 問題。透過在 `MainActivity.kt` 中動態調整圖片可見性，確保了在進入 `ReminderSettingsFragment` 時，圖片會被隱藏，避免遮擋。
*   **0053:** 修正了輔助顏色在暗色模式下無法正確同步的問題。透過在 `values-night/themes.xml` 中為所有輔助顏色主題加上 `colorPrimary`，並在 `activity_main.xml` 中將 `MaterialToolbar` 的背景顏色設定為 `?attr/colorPrimary`，確保了 Toolbar 顏色能隨著主題動態變更。
*   **0052:** 為提醒設定頁面新增了中斷藍牙連線的功能，並在 `drawable` 目錄中新增了 `ic_bluetooth_disabled.xml` 圖示。
*   **0051:** 修正了 App 與 ESP32 的連線問題、輔助顏色設定不一致，以及主頁面出現多餘標題列的問題。同時也解決了 `AndroidManifest.xml` 中的屬性警告。
*   **0050:** 修正了因刪除 `ThemeUtils.kt` 後，`SettingsFragment` 中出現 `Unresolved reference` 的編譯錯誤。
*   **0048:** 修正了因缺少輔助顏色的英文翻譯而導致的編譯錯誤。
*   **0046:** 修正了 `SettingsFragment` 中因缺少 `title_settings` 字串資源而導致的 `Unresolved reference` 錯誤。
*   **0015:** 修正了服藥正確率未更新及服藥紀錄頁面時間顯示不清楚的問題。

## 最近更新

*   **0049:** 清理了專案中的多個警告，包括刪除未使用的 `ThemeUtils.kt`、將 `SharedPreferences.edit()` 替換為 KTX 擴充函式，以及修正 `fragment_wifi_config.xml` 中的無障礙功能警告。
*   **0047:** 新增了「工程模式」，並定義了新的藍牙協定 (`0x13`)，讓 App 可以通知藥盒進入或離開工程模式。此功能可透過設定頁面中的開關進行控制。
*   **0045:** 為設定頁面與 Wi-Fi 設定頁面新增了返回按鈕，並將返回按鈕的邏輯集中到 `MainActivity` 中管理，確保了 UI 的一致性與可預測性。
*   **0044:** 為藥物提醒通知新增「稍後提醒」與「已服用」操作，讓使用者能直接從通知中心進行互動。此功能由新的 `SnoozeReceiver` 和 `MedicationTakenReceiver` 處理。
*   **0043:** 修正了 `WiFiConfigFragment` 的背景透明問題，確保介面清晰可見。
*   **0042:** 新增 Wi-Fi 設定介面，讓使用者可以輸入 Wi-Fi 的 SSID 和密碼，並透過新的藍牙協定 (指令碼 0x12) 將憑證傳送給 ESP32。SSID 輸入框現在會提供之前輸入過的紀錄，方便使用者操作。
*   **0041:** 修正了無障礙功能警告，為酷洛米圖片加上 `contentDescription`，並將 "..." 替換為標準的省略號字元 (`…`)。
*   **0040:** 修正了 `themes.xml` 中的資源連結錯誤，並在主畫面上加入酷洛米圖案作為裝飾。
*   **0039:** 修正了 `MainActivity.kt` 中的多個棄用警告。更新了返回按鈕的處理方式以使用新的 `OnBackPressedDispatcher`，並將地區/語言設定邏輯現代化為使用 `AppCompatDelegate.setApplicationLocales` API，移除了所有相關的已棄用方法。
*   **0038:** 在設定頁面 (`SettingsFragment`) 新增了返回箭頭。讓使用者可以輕鬆地從設定選單返回到上一個畫面。
*   **0037:** 修正了設定頁面 (`SettingsFragment`) 的 UI 顯示問題。先前設定選單的背景是透明的，導致文字與下方的 UI 元件重疊。此問題已透過以編程方式設定一個遵循當前應用程式主題 (亮色/暗色) 的背景顏色來解決，確保了畫面的清晰可見度。
*   **0036:** 修正了設定圖示在亮色模式下不可見的問題，並將設定頁面中的「主題設定」和「語言設定」合併為一個「外觀」分類，以簡化介面。
*   **0035:** 在設定頁面中新增了語言切換功能，讓使用者可以手動切換應用程式的顯示語言 (繁體中文、英文或跟隨系統)。
*   **0034:** 為應用程式新增英文本地化，以支援英語系使用者。
*   **0033:** 實作了歷史溫濕度數據同步功能。擴充了藍牙協定，允許 App 在連接時，從藥盒同步離線期間記錄的所有溫濕度歷史數據，並在圖表上完整呈現。
*   **0032:** 修正了專案中的多個編譯錯誤與警告。
*   **0031:** 清理了 `app/src/main/java/com/example/medicationreminderapp/ui/` 目錄中所有重複且空白的檔案。
*   **0030:** 實作了藍牙協定中「App 主動請求溫濕度數據」的功能。
*   **0029:** 修正了 `app/build.gradle.kts` 中的多個 build 錯誤與警告。
*   **0-028:** 修復了因移除 `BluetoothLeManager` 中看似未使用的 `requestStatus()` 和 `syncTime()` 方法而導致的 Build 失敗問題。
*   **0027:** 移除了 `BluetoothLeManager.kt` 中未被使用的 `sendJson` 方法。
*   **0026:** 清理了專案中多個「未使用宣告」的警告。
*   **0025:** 移除了 `Medication` data class 中未被使用的 `frequency` 欄位。
*   **0024:** 修復了 IDE 中的多個警告。
*   **0023:** 在服藥紀錄頁面新增了視覺標示功能。
*   **0.022:** 簡化了版本號碼的設定。
*   **0021:** 清理了 `SettingsFragment.kt` 中的警告。
*   **0020:** 恢復了遺失的設定功能。
*   **0019:** 解決了 `fragment_reminder_settings.xml` 中的無障礙警告。
*   **0018:** 解決了 IDE 中出現的 XML 資源解析錯誤。
*   **0017:** 解決了 IDE 中出現的 XML 資源解析錯誤。
*   **0016:** 恢復了編輯和刪除藥物提醒的功能。
*   **0015:** 修正了新增藥物後，藥物列表不會立即更新的 UI 問題。
*   **0014:** 處理了 IDE 中關於 `MedicationListAdapter.kt` 和 `ReminderSettingsFragment.kt` 的過時警告。
*   **0013:** 清理了專案中的所有警告。
*   **0012:** 清理了專案中的所有警告。
*   **0011:** 修正了因缺少 `androidx.preference:preference-ktx` 依賴項而導致的資源連結錯誤。
*   **0010:** 新增了設定頁面和藥物列表頁面。
*   **0009:** 優化了藥物提醒表單的驗證。
*   **0008:** 修正了因 Gradle 版本目錄不完整而導致的嚴重建置錯誤。
