// app/build.gradle.kts

// Apply the external configuration file
apply(from = "../config.gradle.kts")

// Function to get the current Git branch
fun getCurrentGitBranch(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD").start()
        process.inputStream.reader().readText().trim()
    } catch (e: Exception) {
        // Provide a default value in environments without Git (like CI/CD)
        println("Could not get git branch, defaulting to 'main'. Error: ${e.message}")
        "main"
    }
}

// Function to get the commit count
fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.inputStream.reader().readText().trim().toInt()
    } catch (_: Exception) {
        1 // Default value
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    // --- Core logic starts ---
    val branchConfigs: Map<String, Map<String, Any>> by extra
    val currentBranch = getCurrentGitBranch()
    val commitCount = getGitCommitCount()
    val config = branchConfigs[currentBranch] ?: branchConfigs["alpha"]!! // Default to alpha if branch config doesn't exist

    println("✅ Building for branch: '$currentBranch' (Commit: $commitCount)")
    // --- Core logic ends ---

    defaultConfig {
        applicationId = config["applicationId"] as String
        minSdk = 29
        targetSdk = 36
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // --- Professional versioning logic ---
        val baseVersionCode: Int
        var baseVersionName: String

        when (currentBranch) {
            "main" -> {
                baseVersionCode = 30000 // Base value for production
                baseVersionName = "1.1.1"
            }
            "beta" -> {
                baseVersionCode = 20000 // Base value for Beta
                baseVersionName = "0.1.1"
            }
            "alpha" -> {
                baseVersionCode = 10000 // Base value for Alpha
                baseVersionName = "0.0.1"
            }
            else -> { // Default for other feature branches
                baseVersionCode = 100
                baseVersionName = "0.0.1"
            }
        }
        
        // Final versionCode is always incremental
        versionCode = baseVersionCode + commitCount
        
        // Final versionName gets a suffix (except for main)
        versionName = if (currentBranch != "main") {
            "$baseVersionName-$currentBranch"
        } else {
            baseVersionName
        }
        
        setProperty("archivesBaseName", "藥到叮嚀-v$versionName")
        // --- Logic ends ---

        // Dynamically set BuildConfig fields for access in Kotlin/Java code
        buildConfigField("String", "API_URL", "\"${config["apiUrl"] as String}\"")
        buildConfigField("boolean", "ENABLE_LOGGING", config["enableLogging"].toString())
        
        // Dynamically set Android resources (e.g., app_name)
        resValue("string", "app_name", config["appName"] as String)
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.gson)
    implementation(libs.calendar.view)
    implementation(libs.mpandroidchart)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}