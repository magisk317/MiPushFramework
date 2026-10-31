import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("magisk.android.library")
    id("magisk.android.distribution")
    alias(libs.plugins.kotlin.serialization)
}

// The XMSF runtime version axis: versionName tracks the root project version
// (the same source the xmsf application module uses), so the delivery pin can
// never drift from the published runtime.
val xmsfVersionName = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }

/**
 * The debug-build suffix, or null when this build carries no pinned timestamp.
 *
 * magisk.app.packaging appends exactly this suffix (from -PbuildTs or
 * MAGISK_BUILD_TIMESTAMP, normalized to yyyyMMdd_HHmmss) to every debug APK's
 * versionName, and CI pins it to the pipeline creation time. The delivery pin
 * has to carry it too: a pin of the bare "1.1.2" ranks above the "1.1.2-<ts>"
 * runtime built by the same pipeline, so every CI build set reported its own
 * runtime as outdated.
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

val debugVersionSuffix = pinnedBuildSuffix()

android {
    namespace = "io.github.magisk317.mipush.manager.xmsfdelivery"

    defaultConfig {
        buildConfigField("String", "XMSF_DELIVERY_VERSION", "\"$xmsfVersionName\"")
        // Both runtime version codes, so the variant a device runs is resolved
        // against the same numbers the runtime APK is built with.
        buildConfigField("long", "XMSF_RUNTIME_NORMAL_VERSION_CODE", "${libs.versions.pushVersionCode.get()}L")
        buildConfigField("long", "XMSF_RUNTIME_VC105_VERSION_CODE", "${libs.versions.vc105VersionCode.get()}L")
    }

    buildFeatures {
        buildConfig = true
    }
}

if (debugVersionSuffix != null) {
    // Only debug variants take the suffix, mirroring the packaging plugin: a
    // release variant built while the CI timestamp is still exported must keep
    // pinning the published release version.
    val components = extensions.getByType<LibraryAndroidComponentsExtension>()
    components.onVariants(components.selector().all()) { variant ->
        if (variant.buildType != "debug") return@onVariants
        val fields = variant.buildConfigFields ?: return@onVariants
        fields.put(
            "XMSF_DELIVERY_VERSION",
            BuildConfigField<java.io.Serializable>(
                "String",
                "\"$xmsfVersionName$debugVersionSuffix\"",
                "XMSF runtime version this build was pinned against",
            ),
        )
    }
}

dependencies {
    implementation(project(":manager:client"))
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
