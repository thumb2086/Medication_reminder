// app/build.gradle.kts

// Apply the external configuration file
apply(from = "../config.gradle.kts")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    // --- Core logic starts ---
    // Determine the current branch from git, or default to "alpha"
    val currentBranch = try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD").start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: java.io.IOException) {
        "alpha"
    }.ifEmpty { "alpha" }
    val branchConfigs: Map<String, Map<String, Any>> by extra
    val config = branchConfigs[currentBranch] ?: branchConfigs["alpha"]!!

    println("✅ Building for branch: '$currentBranch'")
    // --- Core logic ends ---

    defaultConfig {
        applicationId = config["applicationId"] as String
        minSdk = 29
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- Simplified versioning logic ---
        versionCode = config["versionCode"] as Int
        versionName = config["versionName"] as String

        setProperty("archivesBaseName", config["archivesBaseName"] as String)
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
