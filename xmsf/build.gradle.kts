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
        // The xmsf app ships through a single channel, so it declares only the
        // version axis and resolves the distribution axis its dependencies carry
        // here. Kept explicit on purpose: if a shared library ever adds a
        // distribution this app does not know about, the mismatch should surface
        // as a build failure rather than resolve to a channel by default.
        missingDimensionStrategy("distribution", "github")
    }

    flavorDimensions += "version"

    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("vc105") {
            dimension = "version"
            versionCode = 105
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
}

dependencies {
    implementation(project(":common"))
    implementation(project(":xmsf:shell"))
    implementation(project(":xmsf:runtime"))
    implementation(project(":manager:ui"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kermit)
}
