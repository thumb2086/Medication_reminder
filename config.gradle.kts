// /config.gradle.kts

// Using extra properties to store configurations
// Shared constants across all branches
extra["appConfig"] = mapOf(
    "baseApplicationId" to "com.example.medicationreminderapp",
    "baseVersionName" to "1.1.8",
    "appName" to "藥到叮嚀",
    "prodApiUrl" to "https://api.production.com",
    "devApiUrl" to "https://api.dev.com"
)
