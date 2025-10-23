### Plan: Restore and Update README.md (2025/10/23)

**Objective:** Restore the detailed content to `README.md` based on the user-provided text from `AGENTS.md`, while ensuring all information is updated to reflect the current, refactored project architecture.

**Action Plan:**

1.  **Adopt Provided Text:** Use the detailed `README.md` content from `AGENTS.md` as the new baseline.

2.  **Update Project Structure Section:**
    -   Correct the file paths for all Fragments (e.g., `ui/ReminderFragment.kt` -> `ReminderSettingsFragment.kt`).
    -   Refine the descriptions of `MainActivity.kt`, `MainViewModel.kt`, and all Fragments to match their current roles (e.g., `MainActivity` as a coordinator, `ViewModel` as the data hub).

3.  **Update Bluetooth Protocol Section:**
    -   Abstract the implementation details (e.g., remove specific function names like `syncRemindersToBox` in `MainActivity.kt`) to align with the new architecture where Fragments and ViewModels handle this logic.

4.  **Write to `README.md`:** Overwrite the current `README.md` with the new, corrected, and detailed content.

5.  **Log Changes:** Record the update in `log.md`.
