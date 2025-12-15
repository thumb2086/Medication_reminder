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

// Helper to get the latest Git tag version (e.g. v1.2.0 -> 1.2.0)
// CHANGED: Added --exact-match to ensure we only use the tag if the current commit IS the tag.
// If we are ahead of the tag (nightly/dev), this returns empty/error, so we fallback to config.gradle.kts.
fun getGitTagVersion(): String? {
    val tag = getGitCommandOutput("git", "describe", "--tags", "--exact-match", "--match", "v*")
    return if (tag.startsWith("v")) tag.substring(1) else null
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    // --- Dynamic versioning and configuration logic starts ---
    // Safe casting for appConfig
    @Suppress("UNCHECKED_CAST")
    val appConfig = extra["appConfig"] as? Map<String, Any> ?: emptyMap()

    // Get Git info
    val commitCount = getGitCommandOutput("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
    
    // [Fix] In CI/CD (Detached HEAD), git rev-parse returns "HEAD", causing the branch to default to "main".
    // We must prioritize the environment variable passed from CI/CD.
    val envChannelName = System.getenv("CHANNEL_NAME")
    val gitBranchName = getGitCommandOutput("git", "rev-parse", "--abbrev-ref", "HEAD")
    
    val branchName = when {
        !envChannelName.isNullOrBlank() -> envChannelName
        gitBranchName.isNotBlank() && gitBranchName != "HEAD" && gitBranchName != "git-error" -> gitBranchName
        else -> "main"
    }

    // Get base config values with fallback
    val gitTagVersion = getGitTagVersion()
    val configVersionName = appConfig["baseVersionName"] as? String ?: "1.0.0"
    val baseVersionName = gitTagVersion ?: configVersionName
    
    val baseApplicationId = appConfig["baseApplicationId"] as? String ?: "com.example.medicationreminderapp"
    // Fallback appName if missing to prevent empty filename prefix
    val appName = appConfig["appName"] as? String ?: "MedicationReminder"
    val prodApiUrl = appConfig["prodApiUrl"] as? String ?: "https://api.production.com"
    val devApiUrl = appConfig["devApiUrl"] as? String ?: "https://api.dev.com"

    // Determine branch-specific configuration
    // [Critical Fix] CI/CD uses `tr '/_' '-'` to sanitize branch names.
    // We MUST match this behavior in Gradle so `BuildConfig.UPDATE_CHANNEL` matches the JSON filename.
    // Old logic: replaced - with _ (Mismatch!)
    // New logic: replace / and _ with - (Match!)
    val normalizedBranchName = branchName.replace("/", "-").replace("_", "-")
    val safeBranchName = normalizedBranchName.replace(Regex("[^a-zA-Z0-9-]"), "")

    // Treat main, master, and unknown as production/default
    val isProduction = safeBranchName == "main" || safeBranchName == "master"
    val isDev = safeBranchName == "dev"
    
    // Logic: Use environment variables from CI/CD if available, otherwise fallback to local logic
    val envBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
    val envVersionCodeOverride = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull()
    val envVersionName = System.getenv("VERSION_NAME")

    val finalVersionCode = envVersionCodeOverride ?: envBuildNumber ?: commitCount

    // [Unified Naming] Always use hyphens '-' as separators. No spaces.
    // Format: X.Y.Z (Production) or X.Y.Z-channel-COUNT
    val localVersionName = when {
        isProduction -> baseVersionName
        isDev -> "$baseVersionName-dev-$commitCount"
        else -> "$baseVersionName-nightly-$commitCount"
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
        versionCode = if (finalVersionCode > 0) finalVersionCode else 1
        versionName = finalVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // This sets the output APK name prefix: MedicationReminder-v1.2.1-nightly-255
        setProperty("archivesBaseName", finalArchivesBaseName)

        buildConfigField("String", "API_URL", "\"$finalApiUrl\"")
        buildConfigField("boolean", "ENABLE_LOGGING", enableLogging.toString())
        buildConfigField("String", "UPDATE_CHANNEL", "\"$updateChannel\"")

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
                storeFile = file(cloudKeystorePath)
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
            if (releaseConfig.storePassword != null && releaseConfig.storeFile?.exists() == true) {
                signingConfig = releaseConfig
            } else {
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
