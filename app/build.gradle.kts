import java.util.Properties

// Deliberately dependency-free: the app uses only Android framework APIs, so the only things downloaded
// are the Android Gradle Plugin (with its built-in Kotlin) and JUnit for the unit tests.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.binarybug.storagetracker"

    // Installed SDK platform is android-36.1 (API 36, minor 1).
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.binarybug.storagetracker"
        minSdk = 31 // Android 12
        targetSdk = 34
        // Version lives in version.properties so a release bump is a one-line edit.
        val version = Properties().apply { file("version.properties").inputStream().use { load(it) } }
        versionCode = version.getProperty("versionCode").toInt()
        // Android's App info page shows only this string, so the developer name goes here.
        versionName = "${version.getProperty("versionName")} by binaryBug"
    }

    // Release signing: the key and its passwords are NOT in the project. Provide them as Gradle properties, for example in
    // ~/.gradle/gradle.properties: releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword.
    // Without them the release build is produced unsigned (an unsigned APK cannot be installed on a phone).
    val releaseStoreFile = providers.gradleProperty("releaseStoreFile").orNull
    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("releaseStorePassword").get()
                keyAlias = providers.gradleProperty("releaseKeyAlias").get()
                keyPassword = providers.gradleProperty("releaseKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
