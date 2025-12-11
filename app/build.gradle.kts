// app/build.gradle.kts
import java.io.FileInputStream
import java.util.Properties

// Apply the external configuration file
apply(from = "../config.gradle.kts")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Helper function to run a command and get its output
fun getGitCommandOutput(vararg command: String): String {
    return try {
        val proc = ProcessBuilder(*command)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor() // Wait for the process to complete
        output
    } catch (_: java.io.IOException) {
        "git-error" // Return a default value on error
    }
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    // --- Dynamic versioning and configuration logic starts ---
    val appConfig: Map<String, Any> by extra

    // Get Git info
    val commitCount = getGitCommandOutput("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
    val branchName = getGitCommandOutput("git", "rev-parse", "--abbrev-ref", "HEAD").let {
        if (it.isBlank() || it == "HEAD" || it == "git-error") "main" else it // Default to main if detached HEAD or error
    }

    // Get base config values
    val baseApplicationId = appConfig["baseApplicationId"] as String
    val baseVersionName = appConfig["baseVersionName"] as String
    val appName = appConfig["appName"] as String
    val prodApiUrl = appConfig["prodApiUrl"] as String
    val devApiUrl = appConfig["devApiUrl"] as String

    // Determine branch-specific configuration
    val safeBranchName = branchName.replace("-", "_").replace(Regex("[^a-zA-Z0-9_]"), "")

    // Treat main, master, and unknown as production/default
    val isProduction = safeBranchName == "main" || safeBranchName == "master" || safeBranchName == "unknown"
    
    // Logic: Use environment variables from CI/CD if available, otherwise fallback to local logic
    val envBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
    // Allow overriding versionCode directly (e.g. from timestamp) to prevent regression on branch switch
    val envVersionCodeOverride = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull()
    val envVersionName = System.getenv("VERSION_NAME")

    // Priority: Override (Timestamp) > BuildNumber (CI run) > CommitCount (Local)
    val finalVersionCode = envVersionCodeOverride ?: envBuildNumber ?: commitCount

    val localVersionName = if (isProduction) {
        baseVersionName
    } else {
        // Requested format: "1.0.0 nightly <Code>"
        // Use finalVersionCode (timestamp in CI, commit count locally) for the suffix
        // This ensures the App's UpdateManager sees a higher number for new CI builds compared to local builds or old branches.
        "$baseVersionName nightly $finalVersionCode"
    }
    
    val finalVersionName = envVersionName ?: localVersionName
    
    // Ensure filename doesn't have spaces
    val safeVersionName = finalVersionName.replace(" ", "-")
    val finalArchivesBaseName = "$appName-v$safeVersionName"
    
    // REMOVED: Dynamic Application ID suffixing. 
    // Consistent ID ensures updates work across branches (assuming signatures match).
    val finalApplicationId = baseApplicationId 
    
    val finalAppName = if (isProduction) appName else "$appName ($branchName)"
    val finalApiUrl = if (isProduction) prodApiUrl else devApiUrl
    val enableLogging = !isProduction

    // --- Dynamic versioning and configuration logic ends ---

    defaultConfig {
        applicationId = finalApplicationId
        minSdk = 29
        targetSdk = 36
        // Ensure versionCode is at least 1
        versionCode = if (finalVersionCode > 0) finalVersionCode else 1
        versionName = finalVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", finalArchivesBaseName)

        // Dynamically set BuildConfig fields
        buildConfigField("String", "API_URL", "\"$finalApiUrl\"")
        buildConfigField("boolean", "ENABLE_LOGGING", enableLogging.toString())

        // Dynamically set Android resources (e.g., app_name)
        resValue("string", "app_name", finalAppName)
    }

    signingConfigs {
        create("release") {
            // 1. 嘗試載入 local.properties (為了本機開發)
            val keystorePropertiesFile = rootProject.file("local.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }

            // 2. 設定邏輯：優先讀取系統環境變數 (Cloud)，讀不到則讀取 local.properties (Local)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") 
                            ?: keystoreProperties["store.password"] as String?
            
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") 
                       ?: keystoreProperties["key.alias"] as String?
            
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") 
                          ?: keystoreProperties["key.password"] as String?

            // 3. 處理 Keystore 檔案路徑
            // 在 GitHub Actions 中，通常會把 Base64 解碼後的檔案路徑設為環境變數
            val cloudKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            val localKeystorePath = keystoreProperties["store.file"] as String?

            if (!cloudKeystorePath.isNullOrEmpty()) {
                storeFile = file(cloudKeystorePath)
            } else if (!localKeystorePath.isNullOrEmpty()) {
                storeFile = file(localKeystorePath)
            } else {
                // 如果兩邊都找不到，預設找 release.keystore，避免報錯但可能無法簽名
                val defaultFile = file("release.keystore")
                if (defaultFile.exists()) {
                     storeFile = defaultFile
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Safety check: Only apply signing config if password exists AND file exists
            // to avoid gradle sync/build failures if keystore is missing locally.
            // If missing, fallback to debug signing to ensure build succeeds locally.
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storePassword != null && releaseConfig.storeFile?.exists() == true) {
                signingConfig = releaseConfig
            } else {
                // Use logger.info or logger.warn instead of println to avoid polluting stdout which is captured by CI/CD scripts
                logger.warn("Release keystore not found or configuration incomplete. Falling back to debug signing.")
                signingConfig = signingConfigs.getByName("debug")
            }
            
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Temporary fix for Lint crash during release build (AGP/Kotlin issue)
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.gson)
    implementation(libs.calendar.view)
    implementation(libs.mpandroidchart)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

kapt {
    correctErrorTypes = true
}

// Task to print the version name for CI/CD
tasks.register("printVersionName") {
    // Use legacy AppExtension to get the version name safely
    doLast {
        val android = project.extensions.findByName("android") as? com.android.build.gradle.AppExtension
        println(android?.defaultConfig?.versionName ?: "unknown")
    }
}
