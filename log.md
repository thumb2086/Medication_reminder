# 更新日誌

## Bug Fixes
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
    *   **工程模式同步：** 在 `MainActivity.kt` の `onDeviceConnected` 方法中，增加了在藍牙連線成功後，讀取並同步「工程模式」狀態到藥盒的邏輯，確保了兩邊狀態的一致性。
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
*   **0048:** 修正了 `strings.xml` 中因缺少輔助顏色的英文翻譯而導致的編譯錯誤，並同步更新了 `accent_color_entries` 和 `accent_color_values` 陣列。
*   **0046:** 修正了 `SettingsFragment` 中因缺少 `title_settings` 字串資源而導致的 `Unresolved reference` 錯誤。
*   **0015:** 修正了服藥正確率未更新及服藥紀錄頁面時間顯示不清楚的問題。在 `MedicationTakenReceiver` 和 `MainViewModel` 中實作了正確的服藥率計算邏輯，並修正了 `fragment_history.xml` 中的文字顏色，確保其在淺色主題下可見。

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
*   **0032:** 修正了專案中的多個編譯錯誤與警告，包含 `SwipeRefreshLayout` 依賴問題、`MainActivity.kt` 中的錯誤，以及清理未使用的程式碼。
*   **0031:** 清理了 `app/src/main/java/com/example/medicationreminderapp/ui/` 目錄中所有重複且空白的檔案。
*   **0030:** 實作了藍牙協定中「App 主動請求溫濕度數據」的功能，並提供 UI 介面讓使用者觸發此操作。
*   **0029:** 修正了 `app/build.gradle.kts` 中的多個 build 錯誤與警告。處理了 `buildConfigField` 不正確的字串引號問題，並將棄用的 `exec` 方法替換為更現代的 `ProcessBuilder`，確保了 Gradle build script 的穩定性。
*   **0-028:** 修復了因移除 `BluetoothLeManager` 中看似未使用的 `requestStatus()` 和 `syncTime()` 方法而導致的 Build 失敗問題。這兩個方法已被重新加回，確保 `MainActivity` 可以正常呼叫。
*   **0027:** 移除了 `BluetoothLeManager.kt` 中未被使用的 `sendJson` 方法，進一步清理了藍牙通訊的程式碼。
*   **0026:** 清理了專案中多個「未使用宣告」的警告，包括移除藍牙模組中已由 JSON 指令取代的舊方法、移除 `HistoryFragment.kt` 中未被使用的屬性，以及清空了 `ui` 套件中重複且無用的檔案內容，顯著提升了程式碼品質。
*   **0025:** 移除了 `Medication` data class 中未被使用的 `frequency` 欄位及其相關的字串資源，使程式碼更精簡。
*   **0024:** 修復了 IDE 中的多個警告，包括為圖片資源添加無障礙描述、將硬編碼字串移至資源檔，以及清理了 Kotlin 檔案中未使用的導入和參數，提升了程式碼品質與可維護性。
*   **0023:** 在服藥紀錄頁面新增了視覺標示功能。現在，當天所有藥物都按時服用後，日曆上對應的日期下方會顯示一個綠色圓點，讓使用者可以更直觀地追蹤自己的服藥狀況。
*   **0.022:** 簡化了版本號碼的設定，並在藥物清單為空時顯示提示訊息。移除了 `app/build.gradle.kts` 中複雜的 Git 版本控制，改為直接從 `config.gradle.kts` 讀取版本資訊。同時，更新了藥物列表頁面，當沒有提醒事項時，會顯示「無提醒」的文字，改善了使用者體驗。
*   **0021:** 清理了 `SettingsFragment.kt` 中的警告，移除了無用的 `import` 並以更安全的 `let` 區塊取代了不必要的安全呼叫。
*   **0020:** 恢復了遺失的設定功能，包括設定圖示、主題切換和強調色調整。使用者現在可以從工具列再次存取設定頁面並自訂應用程式的外觀。
*   **0019:** 解決了 `fragment_reminder_settings.xml` 中的無障礙警告，並清除了 `MainViewModel.kt` 中未使用的參數。
*   **0018:** 解決了 IDE 中出現的 XML 資源解析錯誤和 Kotlin 檔案中的多個未使用程式碼警告，並透過 Gradle 同步確保了專案狀態的穩定性。
*   **0017:** 解決了 IDE 中出現的 XML 資源解析錯誤和 Kotlin 檔案中的多個未使用程式碼警告，並透過 Gradle 同步確保了專案狀態的穩定性。
*   **0016:** 恢復了編輯和刪除藥物提醒的功能，並重新整合了鬧鐘排程功能。現在，鬧鐘不僅在設定時啟用，還會在裝置重新啟動後自動重設。
*   **0015:** 修正了新增藥物後，藥物列表不會立即更新的 UI 問題。現在透過確保 `LiveData` 接收到新的列表實例而非僅修改現有實例來正確觸發 UI 更新。
*   **0014:** 處理了 IDE 中關於 `MedicationListAdapter.kt` 和 `ReminderSettingsFragment.kt` 的過時警告，並透過 Gradle 同步刷新了專案狀態。
*   **0013:** 清理了專案中的所有警告，包括未使用的匯入、參數和命名空間宣告，並修正了字串資源衝突。
*   **0012:** 清理了專案中的所有警告，包括未使用的匯入、參數和命名空間宣告，並修正了字串資源衝突。
*   **0011:** 修正了因缺少 `androidx.preference:preference-ktx` 依賴項而導致的資源連結錯誤。
*   **0010:** 新增了設定頁面和藥物列表頁面，並恢復了主題設定功能。
*   **0.009:** 優化了藥物提醒表單的驗證，以提供更清晰的錯誤訊息。
*   **0008:** 修正了因 Gradle 版本目錄不完整而導致的嚴重建置錯誤。
