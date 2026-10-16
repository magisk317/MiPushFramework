plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

// The XMSF runtime version axis: versionName tracks the root project version
// (the same source the xmsf application module uses), so the delivery pin can
// never drift from the published runtime.
val xmsfVersionName = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }

android {
    namespace = "io.github.magisk317.mipush.manager.xmsfdelivery"

    defaultConfig {
        buildConfigField("String", "XMSF_DELIVERY_VERSION", "\"$xmsfVersionName\"")
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
            // Play builds never download or install APKs in-process; the
            // delivery degrades to guidance plus post-install verification.
            buildConfigField("boolean", "XMSF_DELIVERY_FULL", "false")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "XMSF_DELIVERY_FULL", "true")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":manager:client"))
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
