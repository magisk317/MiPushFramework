plugins {
    id("mipush.android.application")
    id("mipush.app.packaging")
}

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf.app"

    defaultConfig {
        applicationId = "com.xiaomi.xmsf"
        versionCode = pushVersionCode
        versionName = versionNameStr
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
