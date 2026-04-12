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
    implementation(project(":protocol-frozen"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
