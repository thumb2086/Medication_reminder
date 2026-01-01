// app/build.gradle.kts
import java.io.FileInputStream
import java.util.Properties
import java.io.FileNotFoundException

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

// Helper 1: Get Exact Tag (used for Official Releases)
// Returns e.g. "1.2.1" only if the current commit is exactly tagged "v1.2.1"
fun getExactGitTagVersion(): String? {
    val tag = getGitCommandOutput("git", "describe", "--tags", "--exact-match", "--match", "v*")
    return if (tag.startsWith("v")) tag.substring(1) else null
}

// Helper 2: Get Latest Global Tag (Highest Version)
// CHANGED: Use 'git tag' with sort instead of 'git describe' to find v1.2.1 even if not merged into current branch.
fun getLatestGitTagVersion(): String? {
    // List all tags starting with 'v', sort by version (descending)
    // The output is a newline-separated list. We take the first line.
    val allTags = getGitCommandOutput("git", "tag", "--list", "v*", "--sort=-v:refname")
    val firstTag = allTags.lines().firstOrNull { it.isNotBlank() }?.trim()
    return if (firstTag != null && firstTag.startsWith("v")) firstTag.substring(1) else null
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    // --- Dynamic versioning and configuration logic starts ---
    // Safe casting for appConfig
    @Suppress("UNCHECKED_CAST")
    val appConfig = extra["appConfig"] as? Map<String, Any> ?: emptyMap()
    
    // Get base config values with fallback
    val exactGitTag = getExactGitTagVersion()
    val latestGitTag = getLatestGitTagVersion()
    val configVersionName = appConfig["baseVersionName"] as? String ?: "1.0.0"
    
    // 🔥 重要修正：讀取從 CI/CD 傳入的 -PciBaseVersion
    val projectBaseVersion = if (project.hasProperty("ciBaseVersion")) project.property("ciBaseVersion") as String else null
    
    // Priority Logic:
    // 1. Exact Tag (Official Release builds)
    // 2. CI Provided Base Version (CI Nightly builds)
    // 3. Latest Git Tag in Global History (Local Nightly builds)
    // 4. Config.gradle.kts (Hard fallback)
    val baseVersionName = exactGitTag 
        ?: projectBaseVersion 
        ?: latestGitTag 
        ?: configVersionName
    
    val baseApplicationId = appConfig["baseApplicationId"] as? String ?: "com.example.medicationreminderapp"
    // Fallback appName if missing to prevent empty filename prefix
    val appName = appConfig["appName"] as? String ?: "MedicationReminder"
    val prodApiUrl = appConfig["prodApiUrl"] as? String ?: "https://api.production.com"
    val devApiUrl = appConfig["devApiUrl"] as? String ?: "https://api.dev.com"

    // [Step 1] Force Version Code logic
    // Priority: -PciVersionCode > System.getenv("VERSION_CODE_OVERRIDE") > git rev-list
    val projectCiVersionCode = if (project.hasProperty("ciVersionCode")) project.property("ciVersionCode")?.toString()?.toIntOrNull() else null
    val envVersionCodeOverride = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull()
    val envBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
    
    val finalVersionCode = if (projectCiVersionCode != null) {
        println("✅ [Gradle] Force using -PciVersionCode: $projectCiVersionCode")
        projectCiVersionCode
    } else if (envVersionCodeOverride != null) {
        println("✅ [Gradle] Force using ENV variable: $envVersionCodeOverride")
        envVersionCodeOverride
    } else {
        // Fallback to git commit count
         val commitCount = getGitCommandOutput("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
         println("⚠️ [Gradle] Fallback to Git Commit Count: $commitCount")
         commitCount
    }

    // [Step 2] Force Channel Name Logic
    // Priority: -PciChannelName > System.getenv("CHANNEL_NAME") > git branch
    val projectChannelName = if (project.hasProperty("ciChannelName")) project.property("ciChannelName") as String else null
    val envChannelName = System.getenv("CHANNEL_NAME")
    val gitBranchName = getGitCommandOutput("git", "rev-parse", "--abbrev-ref", "HEAD")
    
    val branchName = when {
        !projectChannelName.isNullOrBlank() -> projectChannelName
        !envChannelName.isNullOrBlank() -> envChannelName
        gitBranchName.isNotBlank() && gitBranchName != "HEAD" && gitBranchName != "git-error" -> gitBranchName
        else -> "main"
    }

    // Determine branch-specific configuration
    // [Critical Fix] CI/CD uses `tr '/_' '-'` to sanitize branch names.
    // We MUST match this behavior in Gradle so `BuildConfig.UPDATE_CHANNEL` matches the JSON filename.
    // Old logic: replaced - with _ (Mismatch!)
    // New logic: replace / and _ with - (Match!)
    val normalizedBranchName = branchName.replace("/", "-").replace("_", "-")
    val safeBranchName = normalizedBranchName.replace(Regex("[^a-zA-Z0-9-.]"), "")

    // Treat main, master, and unknown as production/default
    val isProduction = safeBranchName == "main" || safeBranchName == "master"
    val isDev = safeBranchName == "dev"

    val envVersionName = System.getenv("VERSION_NAME")

    // [Unified Naming] Always use hyphens '-' as separators. No spaces.
    // Format: X.Y.Z (Production) or X.Y.Z-channel-COUNT
    // 如果是 CI 環境，使用 BUILD_NUMBER (Run Number) 作為後綴，否則使用 commitCount
    val versionSuffix = projectCiVersionCode ?: envBuildNumber ?: finalVersionCode
    
    val localVersionName = when {
        isProduction -> baseVersionName
        isDev -> "$baseVersionName-dev-$versionSuffix"
        else -> "$baseVersionName-nightly-$versionSuffix"
    }
    
    val finalVersionName = envVersionName ?: localVersionName
    
    // Use a hardcoded prefix "MedicationReminder" for file naming
    // The display name (app_name) can still use the Chinese name.
    val filePrefix = "MedicationReminder"
    val finalArchivesBaseName = "$filePrefix-v$finalVersionName"
    
    val finalApplicationId = when {
        isProduction -> baseApplicationId
        isDev -> "$baseApplicationId.dev"
        else -> "$baseApplicationId.nightly"
    }
    
    val finalAppName = if (isProduction) appName else "$appName ($branchName)"
    val finalApiUrl = if (isProduction) prodApiUrl else devApiUrl
    val enableLogging = !isProduction
    
    // Update Channel: strictly use the sanitized name matching CI/CD
    val updateChannel = if (isProduction) "main" else safeBranchName

    // --- Dynamic versioning and configuration logic ends ---

    defaultConfig {
        applicationId = finalApplicationId
        minSdk = 29
        targetSdk = 36
        versionCode = finalVersionCode
        versionName = finalVersionName
        
        println("✅ Final VersionCode: $versionCode (Source: ${if (projectCiVersionCode != null) "CI/CD (-P)" else if (envVersionCodeOverride != null) "CI/CD (Env)" else "Git Commit Count"})")
        println("✅ Final Channel: $updateChannel")
        println("✅ Final Base Version: $baseVersionName (ExactTag: $exactGitTag, CI: $projectBaseVersion, LatestTag: $latestGitTag, Config: $configVersionName)")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // This sets the output APK name prefix: MedicationReminder-v1.2.1-nightly-255
        setProperty("archivesBaseName", finalArchivesBaseName)

        buildConfigField("String", "API_URL", "\"$finalApiUrl\"")
        buildConfigField("boolean", "ENABLE_LOGGING", enableLogging.toString())
        buildConfigField("String", "UPDATE_CHANNEL", "\"$updateChannel\"")

        // 3. 設定 Application ID 和 Update URL (這部分部分與上方邏輯重複，但為了確保完整性，我們重新梳理)
        // 注意：上方已經設定了 applicationId = finalApplicationId
        // 這裡主要處理 Application ID Suffix (如果需要進一步區分) 和 resValue / buildConfigField

        if (isProduction) {
            buildConfigField("String", "UPDATE_JSON_URL", "\"https://thumb2086.github.io/Medication_reminder/update_main.json\"")
        } else {
             // A. 給包名加上後綴 (讓 fix 版、dev 版可以共存，也可以跟正式版共存)
            // 由於上方 finalApplicationId 已經處理了 dev 和 nightly 的後綴
            // 這裡我們針對 nightly 做更細的區分，如果我們希望每個 feature branch 都獨立
            // 目前邏輯是 nightly 共用一個 ID，如果想要獨立，可以這樣改：
            // 若希望每個 feature branch 獨立，可以使用以下邏輯，但目前維持三軌並行
            // applicationIdSuffix = ".$safeBranchName" 
            
            // B. App 名稱加上分支名 (已在上方 finalAppName 處理)

            // C. 🔥 更新網址必須對應 CI 產生的 JSON 檔名
            // 這樣 fix-app-update 版就會去抓 update_fix-app-update.json
            buildConfigField("String", "UPDATE_JSON_URL", "\"https://thumb2086.github.io/Medication_reminder/update_${updateChannel}.json\"")
        }

        resValue("string", "app_name", finalAppName)
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }

            storePassword = System.getenv("RELEASE_STORE_PASSWORD") 
                            ?: keystoreProperties["store.password"] as String?
            
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") 
                       ?: keystoreProperties["key.alias"] as String?
            
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") 
                          ?: keystoreProperties["key.password"] as String?

            val cloudKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            val localKeystorePath = keystoreProperties["store.file"] as String?

            if (!cloudKeystorePath.isNullOrEmpty()) {
                val keyFile = file(cloudKeystorePath)
                // 🔥 如果路徑指不到檔案，直接讓 Build 失敗！不要讓它偷跑！
                if (!keyFile.exists()) {
                     throw FileNotFoundException("CI Error: Keystore file not found at: $cloudKeystorePath")
                }
                storeFile = keyFile
            } else if (!localKeystorePath.isNullOrEmpty()) {
                storeFile = file(localKeystorePath)
            } else {
                val defaultFile = file("release.keystore")
                if (defaultFile.exists()) {
                     storeFile = defaultFile
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            val releaseConfig = signingConfigs.getByName("release")
            // Check if we have a valid storeFile to sign with
            if (releaseConfig.storeFile?.exists() == true) {
                signingConfig = releaseConfig
            } else {
                // If we are here, it means we didn't throw an exception earlier,
                // but we also don't have a keystore. This might happen in local builds without keys.
                // However, for CI with RELEASE_KEYSTORE_PATH set, we would have crashed already.
                logger.warn("Release keystore not found or configuration incomplete. Falling back to debug signing.")
                signingConfig = signingConfigs.getByName("debug")
            }
            
            // Fix: Disable Baseline Profile to prevent INSTALL_BASELINE_PROFILE_FAILED on emulators/test devices
            // during manual installation of release APKs.
            
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // Fix for INSTALL_BASELINE_PROFILE_FAILED
    // Use installation block with correct property usage for AGP 8+
    installation {
        // 使用 addAll 並傳入一個 List，這符合 AGP 8+ 的規範且不會有編譯錯誤
        installOptions.addAll(listOf("-r", "--no-incremental"))
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

tasks.register("printVersionName") {
    doLast {
        val android = project.extensions.findByName("android") as? com.android.build.gradle.AppExtension
        println(android?.defaultConfig?.versionName ?: "unknown")
    }
}
