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
fun getGitTagVersion(): String? {
    val tag = getGitCommandOutput("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
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
    val branchName = getGitCommandOutput("git", "rev-parse", "--abbrev-ref", "HEAD").let {
        if (it.isBlank() || it == "HEAD" || it == "git-error") "main" else it // Default to main if detached HEAD or error
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
    val safeBranchName = branchName.replace("/", "_").replace("-", "_").replace(Regex("[^a-zA-Z0-9_]"), "")

    // Treat main, master, and unknown as production/default
    val isProduction = safeBranchName == "main" || safeBranchName == "master" || safeBranchName == "unknown"
    val isDev = safeBranchName == "dev"
    
    // Logic: Use environment variables from CI/CD if available, otherwise fallback to local logic
    val envBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()
    val envVersionCodeOverride = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull()
    val envVersionName = System.getenv("VERSION_NAME")

    val finalVersionCode = envVersionCodeOverride ?: envBuildNumber ?: commitCount

    val localVersionName = when {
        isProduction -> baseVersionName
        isDev -> "$baseVersionName dev $commitCount"
        else -> "$baseVersionName nightly $commitCount"
    }
    
    val finalVersionName = envVersionName ?: localVersionName
    
    // Ensure filename doesn't have spaces
    val safeVersionName = finalVersionName.replace(" ", "-")
    
    // Use a hardcoded prefix "MedicationReminder" for file naming to avoid issues with Chinese characters or missing config
    // The display name (app_name) can still use the Chinese name.
    val filePrefix = "MedicationReminder"
    val finalArchivesBaseName = "$filePrefix-v$safeVersionName"
    
    val finalApplicationId = when {
        isProduction -> baseApplicationId
        isDev -> "$baseApplicationId.dev"
        else -> "$baseApplicationId.nightly"
    }
    
    val finalAppName = if (isProduction) appName else "$appName ($branchName)"
    val finalApiUrl = if (isProduction) prodApiUrl else devApiUrl
    val enableLogging = !isProduction

    // --- Dynamic versioning and configuration logic ends ---

    defaultConfig {
        applicationId = finalApplicationId
        minSdk = 29
        targetSdk = 36
        versionCode = if (finalVersionCode > 0) finalVersionCode else 1
        versionName = finalVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", finalArchivesBaseName)

        buildConfigField("String", "API_URL", "\"$finalApiUrl\"")
        buildConfigField("boolean", "ENABLE_LOGGING", enableLogging.toString())
        buildConfigField("String", "UPDATE_CHANNEL", "\"$safeBranchName\"")

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
