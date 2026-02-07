import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

val mipushLibPath = "${projectDir}/libs/miuipushsdkshared_3_7_9.jar"
extra.set("mipushLib", mipushLibPath)

android {
    namespace = "com.nihility"
    compileSdk = libs.versions.compileSdk.get().toInt()
    
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.startup.runtime)
    compileOnly(files(mipushLibPath))
    implementation(libs.aspectj.rt)
    implementation(libs.xlog)
}

// aspectjx removed for modernization
