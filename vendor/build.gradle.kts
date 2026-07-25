plugins {
    id("magisk.android.library")
}

android {
    namespace = "com.xiaomi.xmsf.vendor.runtime"

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":pinned"))
    implementation(project(":magisk-xposed-kit:logging"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.napier)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
