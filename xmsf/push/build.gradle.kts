plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.xmsf.push"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":pinned"))
    implementation(project(":vendor"))
    implementation(project(":xmsf:runtime"))
    implementation(project(":xmsf:runtime:store"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.kermit)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
