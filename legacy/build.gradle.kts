plugins {
    id("mipush.android.library")
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xiaomi.xmsf.legacy.runtime"

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.napier)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
