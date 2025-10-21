plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.medicationreminderapp"
    compileSdk = 36

    flavorDimensions += "version"

    defaultConfig {
        applicationId = "com.example.medicationreminderapp"
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("alpha") {
            dimension = "version"
            versionCode = 1
            versionName = "0.0.1"
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha"
            setProperty("archivesBaseName", "藥到叮嚀-v0.0.1-alpha")
        }
        create("beta") {
            dimension = "version"
            versionCode = 11
            versionName = "0.1.1"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            setProperty("archivesBaseName", "藥到叮嚀-v0.1.1-beta")
        }
        create("prod") {
            dimension = "version"
            versionCode = 111
            versionName = "1.1.1"
            setProperty("archivesBaseName", "藥到叮嚀-v1.1.1")
        }
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
