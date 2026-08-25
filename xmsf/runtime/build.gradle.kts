plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.runtime"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":pinned"))
    implementation(project(":vendor"))
    implementation(project(":xmsf:runtime:store"))
    implementation(project(":magisk-xposed-kit:logging"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
