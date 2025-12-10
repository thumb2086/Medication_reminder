# To-Do List

- [ ] **Fix Installation Issues**:
    - [x] **Application ID Mismatch**:
        - [x] Removed dynamic suffixing of `applicationId` in `app/build.gradle.kts` based on branch names. Now all builds (dev, fix, main) use the same base Application ID. This allows updates between branches (assuming signatures match).
    - [ ] **Signature Mismatch**:
        - [ ] User is manually building locally (Debug key) and trying to update to GitHub Nightly (Release key).
        - [ ] **Action**: Inform user that "Package Invalid" is likely due to signature mismatch. They must uninstall the local debug build before installing the CI/CD release build.
