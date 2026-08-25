plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.xmsf.notification"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":pinned"))
    implementation(project(":vendor"))
    implementation(libs.kermit)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
