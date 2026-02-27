import java.util.Properties
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

apply(from = rootProject.file("gradle/patched-mipush-jar.gradle.kts"))

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val gitTagStr = versionNameStr
val enableBuildSplits = providers.gradleProperty("buildSplits")
    .map { value -> value.isBlank() || !value.equals("false", ignoreCase = true) }
    .orElse(false)
val gitShortSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.orElse("unknown")
val normalVersionCode = libs.versions.pushVersionCodeNormal.get().toInt()
val vc105VersionCode = libs.versions.pushVersionCodeVc105.get().toInt()

@Suppress("UNCHECKED_CAST")
fun <T> Project.requiredExtra(name: String): T = extra[name] as T

val fixedPatchedMiPushJar: Provider<RegularFile> = project.requiredExtra("fixedPatchedMiPushJar")
val fixPatchedMiPushJarStackMaps: TaskProvider<*> = project.requiredExtra("fixPatchedMiPushJarStackMaps")

private data class SigningMaterial(
    val keyStoreFile: java.io.File,
    val keyStorePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?
)

private fun Project.resolveSigningMaterial(): SigningMaterial {
    var keyStoreFile = rootProject.file(".yuuta.jks")
    var keyStorePassword = System.getenv("KEYSTORE_PASS")
    var keyAlias = System.getenv("ALIAS_NAME")
    var keyPassword = System.getenv("ALIAS_PASS")
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        val properties = Properties()
        properties.load(localProperties.inputStream())
        keyStoreFile = properties.getProperty("KEY_LOCATE")?.let { rootProject.file(it) } ?: keyStoreFile
        keyStorePassword = properties.getProperty("KEYSTORE_PASSWORD") ?: keyStorePassword
        keyAlias = properties.getProperty("KEYSTORE_ALIAS") ?: keyAlias
        keyPassword = properties.getProperty("KEY_PASSWORD") ?: keyPassword
    }
    return SigningMaterial(keyStoreFile, keyStorePassword, keyAlias, keyPassword)
}

private fun ApkSigningConfig.applySigningMaterial(signing: SigningMaterial) {
    enableV1Signing = true
    enableV2Signing = true
    enableV3Signing = true
    enableV4Signing = true
    if (signing.keyStoreFile.exists()) {
        storeFile = signing.keyStoreFile
        storePassword = signing.keyStorePassword
        keyAlias = signing.keyAlias
        keyPassword = signing.keyPassword
    }
}

abstract class RenameApkArtifactsTask : DefaultTask() {
    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:InputDirectory
    abstract val apkFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val signatureOutputDir: DirectoryProperty

    @get:Input
    abstract val versionNameWithSha: Property<String>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val buildTypeName: Property<String>

    @TaskAction
    fun executeTask() {
        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
        if (builtArtifacts == null) {
            logger.lifecycle("Skip rename: no APK artifacts under ${apkFolder.get().asFile}")
            return
        }
        val targetDir = apkFolder.get().asFile.toPath()
        val signatureDir = signatureOutputDir.get().asFile.toPath()
        Files.createDirectories(targetDir)
        Files.createDirectories(signatureDir)
        for (element in builtArtifacts.elements) {
            val abi = element.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier ?: "universal"
            val targetName = "xmsf-v${versionNameWithSha.get()}-${flavorName.get()}-${buildTypeName.get()}-${abi}.apk"
            val source = element.path
            val target = targetDir.resolve(targetName)

            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)

            val sourceSignature = source.resolveSibling("${source.fileName}.idsig")
            if (Files.exists(sourceSignature)) {
                val targetSignature = signatureDir.resolve("${targetName}.idsig")
                Files.move(sourceSignature, targetSignature, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

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

    if (enableBuildSplits.get()) {
        splits {
            abi {
                isEnable = true
                reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
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

    signingConfigs {
        val signing = project.resolveSigningMaterial()
        getByName("debug") { applySigningMaterial(signing) }
        getByName("release") { applySigningMaterial(signing) }
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

androidComponents {
    val safeVersionName = versionNameStr.replace(Regex("\\s+"), "_")
    val versionNameWithShaValue = if (Regex("-g[0-9a-fA-F]{7,}").containsMatchIn(safeVersionName)) {
        safeVersionName
    } else {
        "$safeVersionName-g${gitShortSha.get()}"
    }
    onVariants(selector().withBuildType("release")) { variant ->
        val flavor = variant.flavorName?.ifBlank { "default" } ?: "default"
        val renameTask = tasks.register<RenameApkArtifactsTask>(
            "rename${variant.name.replaceFirstChar { it.uppercase() }}Apks"
        ) {
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            signatureOutputDir.set(layout.buildDirectory.dir("outputs/apk-signatures/${variant.name}"))
            versionNameWithSha.set(versionNameWithShaValue)
            flavorName.set(flavor)
            buildTypeName.set(variant.buildType)
        }
        tasks.matching {
            it.name.startsWith("assemble") && it.name.endsWith("Release")
        }.configureEach {
            finalizedBy(renameTask)
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":condom"))
    implementation(project(":mipush_hook"))
    implementation(files(fixedPatchedMiPushJar) {
        builtBy(fixPatchedMiPushJarStackMaps)
    })

    implementation(libs.xlog)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.mockito.android)
    testImplementation(libs.mockito.core)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.markdown)
    implementation(libs.haze.android)
    implementation(libs.androidx.datastore.preferences)
}
