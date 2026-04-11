plugins {
    id("mipush.android.library")
}

android {
    namespace = "com.oasisfeng.condom"

    defaultConfig {
        buildConfigField("boolean", "DEBUG_CONDOM", "true")
    }

    lint {
        // Disable 'NewApi' checking due to false errors in old version of lint tool
        disable += "NewApi"
        textReport = true
    }

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register<Jar>("sourcesJar") {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}
