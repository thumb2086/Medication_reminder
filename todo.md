# To-Do List

- [ ] **Fix Warnings**:
    - [ ] `UpdateManager.kt`: Remove unnecessary safe call `?.string()` on `response.body`.
    - [ ] `UpdateManager.kt`: Remove unused parameter `e` in catch block (or log it properly).
    - [ ] `values-en/strings.xml`: Update `update_channel_entries` and `update_channel_values` to match the 3 items in `values/strings.xml` (Stable, Dev, Nightly).

- [ ] **Bug Fix: Update Check Fails**:
    - [ ] Investigate why "unable to update" was reported (likely due to simplistic string comparison in `UpdateManager` or tag mismatch).
    - [ ] Ensure `latest-dev` and `nightly` tags are correctly parsed.

- [ ] **CI/CD Cleanup**:
    - [ ] Ensure workflows generate `latest-dev` correctly.
