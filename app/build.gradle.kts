plugins {
    id("magisk.android.application")
    id("magisk.app.signing")
    id("magisk.app.packaging")
}

extra["mipushArtifactBaseName"] = "xmsf"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf.app"

    defaultConfig {
        applicationId = "com.xiaomi.xmsf"
        versionCode = pushVersionCode
        versionName = versionNameStr
    }

    flavorDimensions.add("version")

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
    implementation(project(":xmsf"))
    implementation(project(":manager"))
}
