# To-Do List

- [ ] **Fix Build Error: Release Signing**:
    - [x] Modified `app/build.gradle.kts` to fall back to `debug` signing config if `release` keystore is missing. This allows the release build to compile and run locally (though it will have a debug signature).
