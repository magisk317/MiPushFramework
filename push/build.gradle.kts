import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val rootExtra = rootProject.extensions.extraProperties
val versionNameStr = rootExtra["versionName"] as String
val gitTagStr = rootExtra["gitTag"] as String
val gitShortSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.orElse("unknown")
val normalVersionCode = libs.versions.pushVersionCodeNormal.get().toInt()
val vc105VersionCode = libs.versions.pushVersionCodeVc105.get().toInt()

android {
    namespace = "com.xiaomi.xmsf"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xiaomi.xmsf"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        
        versionCode = normalVersionCode
        versionName = versionNameStr
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64"))
        }

        buildConfigField("String", "GIT_TAG", "\"$gitTagStr\"")
    }

    if (project.hasProperty("buildSplits")) {
        splits {
            abi {
                isEnable = true
                reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            signingConfig = signingConfigs.maybeCreate("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("vc105") {
            dimension = "version"
            versionCode = vc105VersionCode
        }
    }

    // aspectjx removed for modernization

    // greendao removed for modernization (Gradle 9.5 incompatibility)

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true

            var locale = project.rootProject.file(".yuuta.jks")
            var keystorePwd = System.getenv("KEYSTORE_PASS")
            var alias = System.getenv("ALIAS_NAME")
            var pwd = System.getenv("ALIAS_PASS")
            
            if (project.rootProject.file("local.properties").exists()) {
                val properties = Properties()
                properties.load(project.rootProject.file("local.properties").inputStream())
                locale = properties.getProperty("KEY_LOCATE")?.let { project.rootProject.file(it) } ?: locale
                keystorePwd = properties.getProperty("KEYSTORE_PASSWORD") ?: keystorePwd
                alias = properties.getProperty("KEYSTORE_ALIAS") ?: alias
                pwd = properties.getProperty("KEY_PASSWORD") ?: pwd
            }

            if (locale.exists()) {
                storeFile = locale
                storePassword = keystorePwd
                keyAlias = alias
                keyPassword = pwd
            }
        }
        getByName("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true

            var locale = project.rootProject.file(".yuuta.jks")
            var keystorePwd = System.getenv("KEYSTORE_PASS")
            var alias = System.getenv("ALIAS_NAME")
            var pwd = System.getenv("ALIAS_PASS")

            if (project.rootProject.file("local.properties").exists()) {
                val properties = Properties()
                properties.load(project.rootProject.file("local.properties").inputStream())
                locale = properties.getProperty("KEY_LOCATE")?.let { project.rootProject.file(it) } ?: locale
                keystorePwd = properties.getProperty("KEYSTORE_PASSWORD") ?: keystorePwd
                alias = properties.getProperty("KEYSTORE_ALIAS") ?: alias
                pwd = properties.getProperty("KEY_PASSWORD") ?: pwd
            }

            if (locale.exists()) {
                storeFile = locale
                storePassword = keystorePwd
                keyAlias = alias
                keyPassword = pwd
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Some environments cannot execute bundled llvm-strip; keep symbols to avoid strip task failures.
            keepDebugSymbols += "**/*.so"
        }
    }

    // composeOptions removed as it is now handled by the compose-compiler plugin
}

tasks.register("renameApks") {
    dependsOn("assembleRelease")
    val apkRootDir = layout.buildDirectory.dir("outputs/apk")
    val safeVersionName = versionNameStr.replace(Regex("\\s+"), "_")
    val versionNameWithSha = run {
        if (Regex("-g[0-9a-fA-F]{7,}").containsMatchIn(safeVersionName)) {
            safeVersionName
        } else {
            "$safeVersionName-g${gitShortSha.get()}"
        }
    }
    doLast {
        val apkRoot = apkRootDir.get().asFile
        if (!apkRoot.exists()) return@doLast

        apkRoot.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .forEach { apk ->
                val name = apk.name
                val flavor = apk.parentFile?.parentFile?.name ?: "default"
                val abi = when {
                    name.contains("arm64-v8a") -> "arm64-v8a"
                    name.contains("armeabi-v7a") -> "armeabi-v7a"
                    name.contains("x86_64") -> "x86_64"
                    name.contains("-x86-") -> "x86"
                    name.contains("universal") -> "universal"
                    else -> "universal"
                }
                val buildType = apk.parentFile?.name ?: "release"
                val targetName = "xmsf-v${versionNameWithSha}-${flavor}-${buildType}-${abi}.apk"
                val target = apk.resolveSibling(targetName)
                if (apk.name != target.name) {
                    apk.renameTo(target)
                }
            }
    }
}

tasks.matching {
    it.name.startsWith("assemble") && it.name.endsWith("Release")
}.configureEach {
    finalizedBy("renameApks")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":condom"))
    implementation(project(":mipush_hook"))
    implementation(files(rootProject.project(":mipush_hook").extensions.extraProperties["mipushLib"] as String))

    implementation(libs.xlog)
    implementation(libs.aspectj.rt)
    implementation(libs.icebox)
    implementation(libs.gson)
    implementation(libs.libsu.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.mockito.android)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.legacy.support.v4)
    implementation(libs.palette)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.markdown)
    implementation(libs.swipeRefresh)
}
