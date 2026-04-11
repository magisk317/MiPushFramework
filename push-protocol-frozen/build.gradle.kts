plugins {
    id("mipush.android.library")
}

android {
    namespace = "com.xiaomi.xmsf.protocol.frozen"

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
