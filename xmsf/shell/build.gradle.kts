plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

extra["artifactBaseName"] = "xmsf"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }

/**
 * The debug-build suffix, or null when this build carries no pinned timestamp.
 *
 * Same chain and normalization as magisk.app.packaging, which is what writes the
 * APK versionName: the runtime reports VERSION_NAME to telemetry and to its own
 * log lines, and a CI runtime that calls itself "1.1.2" while dumpsys says
 * "1.1.2-<ts>" is a build set nobody can match to a published artifact.
 */
fun pinnedBuildSuffix(): String? {
    val raw = sequenceOf(
        project.findProperty("buildTs")?.toString(),
        System.getenv("MAGISK_BUILD_TIMESTAMP"),
    ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull() ?: return null

    val compact = raw.filter { it.isDigit() }
    if (compact.length != 14) return null
    return "-${compact.substring(0, 8)}_${compact.substring(8)}"
}

val debugVersionSuffix = pinnedBuildSuffix().orEmpty()
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
        buildConfigField("String", "VERSION_NAME", "\"$versionNameStr$debugVersionSuffix\"")
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
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.libsu.core)
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.anip.sdk)
    implementation(libs.koin.android)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
