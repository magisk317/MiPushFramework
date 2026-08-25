import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

extra["artifactBaseName"] = "xmsf"

abstract class SyncEmbeddedIconConfigsTask : DefaultTask() {
    @get:Input
    abstract val remoteBranch: org.gradle.api.provider.Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sync() {
        val files = mapOf(
            "APP_NotifyIconsSupportConfig.json" to "APP/NotifyIconsSupportConfig.json",
            "OS_ColorOS_NotifyIconsSupportConfig.json" to "OS/ColorOS/NotifyIconsSupportConfig.json",
            "OS_MIUI_NotifyIconsSupportConfig.json" to "OS/MIUI/NotifyIconsSupportConfig.json",
        )
        val output = outputDirectory.get().asFile
        val staging = output.resolveSibling("${output.name}.tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        val manifest = StringBuilder("source=https://github.com/fankes/AndroidNotifyIconAdapt\n")
        manifest.append("branch=").append(remoteBranch.get()).append('\n')

        try {
            files.forEach { (fileName, remotePath) ->
            val url = URI(
                "https://raw.githubusercontent.com/fankes/AndroidNotifyIconAdapt/" +
                    "${remoteBranch.get()}/$remotePath",
            ).toURL()
            var bytes: ByteArray? = null
            var lastIOException: IOException? = null
            repeat(3) { attempt ->
                if (bytes != null) return@repeat
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "MiPushFramework/EmbeddedIconConfigs")
                    check(connection.responseCode in 200..299) {
                        "Unable to download $remotePath: HTTP ${connection.responseCode}"
                    }
                    bytes = connection.inputStream.use { it.readBytes() }
                } catch (error: IOException) {
                    lastIOException = error
                    if (attempt < 2) Thread.sleep(1_000L * (attempt + 1))
                } finally {
                    connection.disconnect()
                }
            }
            val downloaded = bytes ?: throw (lastIOException
                ?: IOException("Unable to download $remotePath after 3 attempts"))
            val text = downloaded.toString(Charsets.UTF_8).trim()
            check(text.startsWith("[") && text.endsWith("]")) {
                "Remote icon config is not a JSON array: $remotePath"
            }
            check(text.contains("packageName")) {
                "Remote icon config has no packageName entries: $remotePath"
            }
            staging.resolve(fileName).writeBytes(downloaded)
            val sha = MessageDigest.getInstance("SHA-256")
                .digest(downloaded)
                .joinToString("") { byte -> "%02x".format(byte) }
            manifest.append(fileName).append(" sha256=").append(sha)
                .append(" bytes=").append(downloaded.size).append('\n')
            }
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
        staging.resolve("_source.txt").writeText(manifest.toString())
        output.deleteRecursively()
        check(staging.renameTo(output)) { "Unable to publish embedded icon configs" }
    }
}

val syncEmbeddedIconConfigs = tasks.register<SyncEmbeddedIconConfigsTask>("syncEmbeddedIconConfigs") {
    remoteBranch.convention(providers.gradleProperty("embeddedIconBranch").orElse("main"))
    outputDirectory.set(layout.buildDirectory.dir("generated/assets/embeddedIcons/icon"))
}

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
            "KotlinNullnessAnnotation", "NewerVersionAvailable", "ObsoleteSdkInt", "PluralsCandidate",
            "PrivateApi", "SdCardPath", "SignatureOrSystemPermissions", "TrimLambda",
            "TypographyEllipsis", "UnusedAttribute", "UnusedQuantity", "UseKtx",
        )
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.layout.projectDirectory.dir("xposed/src/main/compat").asFile.path)
            assets.directories.add(layout.buildDirectory.dir("generated/assets/embeddedIcons").get().asFile.path)
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

tasks.matching {
    (it.name.startsWith("process") && it.name.endsWith("Resources")) ||
        (it.name.startsWith("merge") && it.name.endsWith("Assets"))
}.configureEach {
    dependsOn(syncEmbeddedIconConfigs)
}

tasks.configureEach {
    if (name == "testVc105DebugUnitTest") {
        mustRunAfter("testNormalDebugUnitTest")
    }
}

dependencies {
    implementation(project(":manager:contract"))
    implementation(project(":magisk-xposed-kit:diagnostics"))
    implementation(project(":diagnostics"))
    implementation(project(":core"))
    implementation(project(":settings"))
    implementation(project(":common"))
    implementation(project(":configuration"))
    implementation(project(":vendor"))
    implementation(project(":pinned"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":runtime-store-kmp"))
    implementation(project(":xmsf-notification"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.kermit)
    implementation(libs.hyperisland.kit) {
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.libsu.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.conscrypt.openjdk.uber)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.koin.android)
}
