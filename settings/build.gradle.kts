plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.settings"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    api(libs.androidx.datastore.preferences)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
