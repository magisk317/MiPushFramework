plugins {
    id("mipush.android.library")
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
    api(project(":runtime-core"))
    implementation(project(":protocol-frozen"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.napier)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
