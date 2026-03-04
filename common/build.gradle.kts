plugins {
    id("mipush.android.library")
}

android {
    namespace = "top.trumeet.common"

    defaultConfig {
        buildConfigField("String", "PUSH_VERSION_CODE", "\"${libs.versions.versionCode.get()}\"")
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    // javax.inject no longer needed; annotations removed from common caches
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)
}
