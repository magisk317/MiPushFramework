plugins {
    id("mipush.android.library")
    id("mipush.android.aop")  // 启用现代化 AOP 支持
}

val mipushLibPath = "${projectDir}/libs/miuipushsdkshared_3_7_9.jar"

android {
    namespace = "com.nihility"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.startup.runtime)
    compileOnly(files(mipushLibPath))
    implementation(libs.napier)
}
