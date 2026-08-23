plugins {
    id("magisk.android.library")
}

android {
    namespace = "com.xiaomi.xmsf.vendor.runtime"

    // This module mirrors stock Xiaomi SDK/runtime behavior. These checks either prescribe
    // source rewrites that would change that ABI or cannot see permissions supplied by xmsf.
    // Keep the exception local so first-party modules retain the full repository lint policy.
    lint {
        disable += setOf(
            // Version checks are redundant with minSdk 28 but serve as defensive coding.
            // Removing them from frozen vendor code risks introducing regressions.
            "ObsoleteSdkInt",
            // Mechanical Kotlin extension replacements in vendor code risk behavior changes.
            "UseKtx",
            // Custom Xiaomi protocol constants don't match Android framework expectations.
            // Changing them would break protocol compatibility.
            "WrongConstant",
            // Package visibility queries are handled by xmsf manifest's <queries> element.
            "QueryPermissionsNeeded",
            // Permissions are declared in xmsf's manifest and inherited at runtime.
            // Vendor code cannot declare its own permissions.
            "MissingPermission",
            // Hidden/reflection APIs are required for XMSF functionality.
            "DiscouragedApi",
            // Stock behavior overrides crash handler for telemetry; changing breaks parity.
            "DefaultUncaughtExceptionDelegation",
            // Core XMSF requires hidden Android API access.
            "PrivateApi",
            // Device identifiers required for push registration protocol.
            "HardwareIds",
        )
    }

    buildTypes {
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":pinned"))
    implementation(project(":magisk-xposed-kit:logging"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kermit)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
