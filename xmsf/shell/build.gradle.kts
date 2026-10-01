plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

extra["artifactBaseName"] = "xmsf"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf"

    // xmsf preserves stock Xiaomi system-service and hidden-API contracts. These historical
    // findings are audited here because source rewrites would change the packaged ABI.
    lint {
        disable += setOf(
            "AnnotateVersionCheck", "ApplySharedPref", "BatteryLife", "DiscouragedApi",
            "DiscouragedPrivateApi", "ExportedContentProvider", "ExportedService", "InlinedApi",
            "KotlinNullnessAnnotation", "ObsoleteSdkInt",
            "PrivateApi", "SdCardPath", "SignatureOrSystemPermissions", "TrimLambda",
            "UseKtx",
        )
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.layout.projectDirectory.dir("xposed/src/main/compat").asFile.path)
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    flavorDimensions += "version"

    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("vc105") {
            dimension = "version"
        }
    }

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64"))
        }

        buildConfigField("String", "GIT_TAG", "\"$versionNameStr\"")
        val buildTs = project.findProperty("buildTs")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val fullVersionName = if (buildTs != null) "$versionNameStr-$buildTs" else versionNameStr
        buildConfigField("String", "VERSION_NAME", "\"$fullVersionName\"")
    }

    buildTypes {
        debug {
            // Reduce debug APK size
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
                dex {
                    useLegacyPackaging = false
                }
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

tasks.configureEach {
    if (name == "testVc105DebugUnitTest") {
        mustRunAfter("testNormalDebugUnitTest")
    }
}

dependencies {
    implementation(project(":xmsf:platform"))
    implementation(project(":manager:contract"))
    implementation(project(":manager:port"))
    implementation(project(":manager:application"))
    implementation(project(":magisk-xposed-kit:diagnostics"))
    implementation(project(":diagnostics"))
    implementation(project(":core"))
    implementation(project(":settings"))
    implementation(project(":common"))
    implementation(project(":configuration"))
    implementation(project(":vendor"))
    implementation(project(":pinned"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":xmsf:runtime"))
    implementation(project(":xmsf:runtime:store"))
    implementation(project(":xmsf:notification"))
    implementation(project(":xmsf:push"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kermit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.libsu.core)
    implementation(libs.hiddenapibypass)
    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.anip.sdk)
    implementation(libs.koin.android)
    implementation(libs.hyperisland.kit) {
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.conscrypt.openjdk.uber)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}
