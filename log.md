我修正了設定選單的顯示問題。

之前的問題是，設定選單的背景是透明的，導致文字與下方的 UI 重疊。我透過修改 `SettingsFragment.kt` 檔案，在 `onViewCreated` 方法中，為 Fragment 的 View 設定了與當前主題一致的背景顏色，解決了這個問題。

我在設定頁面左上角加上了返回箭頭。

我做了以下更改：

1.  **修改了 `SettingsFragment.kt`**：讓它在顯示時，於 `ActionBar` 上顯示返回箭頭。
2.  **修改了 `MainActivity.kt`**：加入了對返回箭頭點擊事件的處理，讓使用者可以點擊返回箭頭回到上一頁。

我修正了 `MainActivity.kt` 中的棄用警告。

我做了以下更改：

1.  **更新返回按鈕的處理方式**：使用新的 `OnBackPressedDispatcher` 來處理返回按鈕事件，取代已被棄用的 `onBackPressed()`。
2.  **更新語言設定的邏輯**：我用 `AppCompatDelegate.setApplicationLocales` 這個現代的 API 來處理語言切換，這會自動處理畫面的重新建立與資源更新，並移除已被棄用的 `setLocale`、`updateBaseContextLocale` 和 `attachBaseContext` 方法。
3.  **移除 `SettingsFragment.kt` 中的 `recreate()` 呼叫**：因為新的語言設定 API 會自動處理，不再需要手動呼叫。