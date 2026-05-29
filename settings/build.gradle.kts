plugins {
    id("mipush.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.settings"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    api(libs.androidx.datastore.preferences)
}
