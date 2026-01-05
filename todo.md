# Medication Reminder - Unified Development Roadmap

## v1.5.5: 報告功能重構 (Report Feature Refactoring)
- [x] **將藥物報告功能獨立成一個分頁 (Separate Report into its own Tab):**
    - [x] **UI/UX:** 建立新的 `fragment_report.xml`，並將報告相關的圖表 (BarChart)、時間範圍選擇器 (RadioGroup) 及分享按鈕 (Button) 從 `fragment_history.xml` 遷移至此。
    - [x] **Fragment 邏輯:** 建立 `ReportFragment.kt`，並將 `HistoryFragment.kt` 中與報告圖表、時間範圍計算、分享相關的邏輯遷移至此。
    - [x] **導覽更新:** 修改主畫面 ( vermutlich `MainActivity.kt` 或 navigation graph) 的導覽機制，新增一個「報告」分頁，指向新建的 `ReportFragment`。
- [x] **加入 Log**
- [x] **優化服藥紀錄顯示 (Optimize Medication Record Display):**
    - [x] **無紀錄狀態:** 在 `MainViewModel` 或相關邏輯中，當沒有服藥紀錄時，將遵從率計算結果調整為顯示「無紀錄」字樣，而不是「服藥正確率0%」。
- [x] **題型顏色分級 (Medication Color-Coding):**
    - [x] **資料模型:** 檢視現有資料庫/資料模型，評估如何新增一個欄位來儲存藥物的顏色或分類。
    - [x] **UI/UX:** 設計一套顏色系統，在日曆或列表視圖中，根據藥物或其服用頻次顯示不同的顏色標記。
- [x] **medication_input_item.xml: 在 LinearLayout 的 orientation 屬性中，已將 android.orientation 修正為 android:orientation (第 82 行)。**
- [x] **app/src/main/res/values-en/strings.xml: 已在 app/src/main/res/values-en/strings.xml 中新增了 "color_picker_desc" 的英文翻譯。**
- [x] **MedicationListAdapter.kt: 已將 Color.parseColor(medication.color) 替換為使用 KTX 擴充函數 medication.color.toColorInt()，並新增了 import androidx.core.graphics.toColorInt。**
- [x] **ReminderSettingsFragment.kt: 已將所有 Color.parseColor(color) 替換為 color.toColorInt()，並將 ColorDrawable(colorInt) 替換為 colorInt.toDrawable(requireContext())。同時新增了 import androidx.core.graphics.toColorInt 和 import androidx.core.graphics.drawable.toDrawable。**
- [x] **修正1.5.5相關紀錄檔案更新所附上的問題**
- [x] **修正 `ReminderSettingsFragment.kt` 中的 import 錯誤與變數參考錯誤**

## v1.6.0: 設定擴充與自動化 (Settings Expansion & Automation) 
-  [ ] **角色設定檔匯入功能 (Character Profile Import):**
  - **詳細步驟:**
    - [ ] **UI/UX:** 在設定頁面新增「匯入角色設定」的按鈕。
    - [ ] **檔案存取:** 使用儲存存取框架 (Storage Access Framework) 讓使用者能選取裝置上的 JSON 設定檔。
    - [ ] **資料夾管理:** 建立一個應用程式專用的 `characters_config` 資料夾，用來存放所有匯入的設定檔。
    - [ ] **邏輯修改:** 重構 `CharacterManager`，使其能同時讀取並整合 `assets` 中的預設角色與 `characters_config` 資料夾中的自訂角色。
- [ ] **自動化發布流程 (Automated Release Workflow):**
  - **詳細步驟:**
    - [ ] **CI/CD:** 建立 GitHub Actions 工作流程 (`.github/workflows/android-release.yml`)。
    - [ ] **觸發條件:** 設定工作流程在 Git 儲存庫收到 `v*.*.*` 格式的標籤 (tag) 推送時自動觸發。
    - [ ] **流程實作:** 執行 `assembleRelease` 指令以建置已簽署的 APK，並利用現有的動態版本命名邏輯。
    - [ ] **產出:** 建立一個新的 GitHub Release，並將產生的 APK 作為附件 (Asset) 上傳。

## 未來規劃 (Future Considerations)
- [ ] **藥物照片對照 (Medication Photo ID):** 允許使用者為每種藥物拍攝並儲存一張實際照片，在服藥提醒時顯示以供視覺核對。
- [ ] **雲端備份與還原 (Cloud Backup & Restore):** 跨裝置同步使用者的藥物設定與紀錄。
- [ ] **多使用者支援 (Multi-User Support):** 讓 App 可以建立多個 Profile，方便家人共用裝置。
- [ ] **進階數據分析與洞察 (Advanced Analytics & Insights):** 分析長期服藥依從性趨勢，找出個人行為模式。
- [ ] **Wear OS 副應用 (Wear OS Companion App):** 在手錶上顯示下次服藥提醒，並可直接標記為已服用。
- [ ] **語音互動整合 (Voice Integration):** 整合 Google 助理，用語音查詢或回報服藥狀態。
- [ ] **AR 擴增實境導覽 (Augmented Reality Onboarding):** 以 AR 方式提供更直覺的首次設定體驗。
- [ ] **旅行模式 (Travel Mode):** 跨時區旅行時，自動調整提醒時間並生成打包清單。
- [ ] **AI 生成服藥報告 (AI-Generated Compliance Reports):** 自動生成自然語言格式的服藥總結報告，方便直接提供給醫師。
- [ ] **醫療院所系統串接 (Healthcare Provider Integration - via FHIR):** 串接電子病歷，自動同步處方與服藥紀錄。
- [ ] **照顧者模式 (Caregiver Mode):** 建立獨立的照顧者模式，可遠端監控並協助管理。