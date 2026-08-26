plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.xmsf.notification"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":xmsf:platform"))
    implementation(project(":pinned"))
    implementation(project(":vendor"))
    implementation(project(":settings"))
    implementation(project(":xmsf:runtime"))
    implementation(project(":xmsf:runtime:store"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.kermit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hyperisland.kit) {
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
