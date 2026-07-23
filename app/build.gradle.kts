plugins {
    id("magisk.android.application")
    id("magisk.app.signing")
    id("magisk.app.packaging")
}

extra["artifactBaseName"] = "xmsf"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf.app"

    defaultConfig {
        applicationId = "com.xiaomi.xmsf"
        versionCode = pushVersionCode
        versionName = versionNameStr
    }

    flavorDimensions += listOf("version", "composition")

    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("vc105") {
            dimension = "version"
            versionCode = 105
        }
        // Default split packaging: runtime-only APK. Manager UI lives in :mipush.
        create("split") {
            dimension = "composition"
            buildConfigField("boolean", "BUNDLED_MANAGER", "false")
        }
        // Comparison / regression baseline that still packages manager UI in-process.
        create("bundled") {
            dimension = "composition"
            buildConfigField("boolean", "BUNDLED_MANAGER", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":xmsf"))
    "bundledImplementation"(project(":manager"))

    implementation(libs.kotlinx.coroutines.android)
}
