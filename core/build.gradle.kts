plugins {
    id("mipush.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.runtime.core"

    defaultConfig {
        buildConfigField("int", "RUNTIME_API_VERSION", "3")
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.napier)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
