// /config.gradle.kts

// Using extra properties to store configurations, making them accessible elsewhere
extra["branchConfigs"] = mapOf(
    "main" to mapOf(
        "applicationId" to "com.example.medicationreminderapp",
        "versionNameSuffix" to "",
        "apiUrl" to "https://api.production.com",
        "appName" to "藥到叮嚀",
        "enableLogging" to false,
        "versionCode" to 111,
        "versionName" to "1.1.1",
        "archivesBaseName" to "藥到叮嚀-v1.1.1"
    ),
    "beta" to mapOf(
        "applicationId" to "com.example.medicationreminderapp.beta",
        "versionNameSuffix" to "-beta",
        "apiUrl" to "https://api.beta.com",
        "appName" to "藥到叮嚀 (Beta)",
        "enableLogging" to true,
        "versionCode" to 11,
        "versionName" to "0.1.1",
        "archivesBaseName" to "藥到叮嚀-v0.1.1-beta"
    ),
    "alpha" to mapOf(
        "applicationId" to "com.example.medicationreminderapp.alpha",
        "versionNameSuffix" to "-alpha",
        "apiUrl" to "https://api.alpha.com",
        "appName" to "藥到叮嚀 (Alpha)",
        "enableLogging" to true,
        "versionCode" to 1,
        "versionName" to "0.0.4",
        "archivesBaseName" to "藥到叮嚀-v0.0.4-alpha"
    )
)