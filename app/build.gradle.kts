// app/build.gradle.kts

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
    val envVersionName = System.getenv("VERSION_NAME")

    val finalVersionCode = envBuildNumber ?: commitCount

    val localVersionName = if (isProduction) {
        baseVersionName
    } else {
        // Requested format: "1.0.0 nightly 5"
        "$baseVersionName nightly $commitCount"
    }
    
    val finalVersionName = envVersionName ?: localVersionName
    
    // Ensure filename doesn't have spaces
    val safeVersionName = finalVersionName.replace(" ", "-")
    val finalArchivesBaseName = "$appName-v$safeVersionName"
    
    val finalApplicationId = if (isProduction) baseApplicationId else "$baseApplicationId.$safeBranchName"
    val finalAppName = if (isProduction) appName else "$appName ($branchName)"
    val finalApiUrl = if (isProduction) prodApiUrl else devApiUrl
    val enableLogging = !isProduction

    // --- Dynamic versioning and configuration logic ends ---

    defaultConfig {
        applicationId = finalApplicationId
        minSdk = 29
        targetSdk = 36
        versionCode = finalVersionCode
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
            // System.getenv 用於讀取 GitHub Actions 設定的環境變數
            val keystorePath = System.getenv("KEYSTORE_PATH")
            storeFile = if (keystorePath != null) file(keystorePath) else file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
