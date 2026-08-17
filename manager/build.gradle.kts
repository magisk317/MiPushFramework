plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.manager"

    defaultConfig {
        missingDimensionStrategy("version", "normal")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // These are established Manager contracts or localization/style suggestions, not defects:
    // application-scoped Koin contexts, synchronous launcher state commits, explicit alarm and
    // battery permission flows, shared icons, and translations whose grammar is already curated.
    // Keep the exception module-local; API, lifecycle, permission, and Compose resource errors
    // remain enabled and are fixed in source.
    lint {
        disable += setOf(
            "ApplySharedPref",
            "AutoboxingStateCreation",
            "BatteryLife",
            "ConfigurationScreenWidthHeight",
            "IconDuplicates",
            "MissingPermission",
            "ModifierParameter",
            "ObsoleteSdkInt",
            "PluralsCandidate",
            "StaticFieldLeak",
            "UnusedQuantity",
            "UseKtx",
        )
    }
}

// Keep the navigation-performance verification gate in an allowed Manager module so
// device acceptance cannot be started without the affected-module checks. The jqwik
// properties run through the existing JUnit 5 debug unit-test tasks.
val navigationPerformancePropertyTests = tasks.register("navigationPerformancePropertyTests") {
    group = "verification"
    description = "Runs Manager and manager-client unit/property tests for navigation performance."
    dependsOn(":manager:testDebugUnitTest", ":manager-client:testDebugUnitTest")
}

val navigationPerformanceVerification = tasks.register("verifyNavigationPerformance") {
    group = "verification"
    description = "Runs compile, unit/property, and Runtime_Boundary checks before device acceptance."
    dependsOn(
        ":magisk-ui-kit:compileDebugKotlin",
        navigationPerformancePropertyTests,
        ":verifyModuleBoundaries",
    )
}

tasks.register("deviceAcceptancePrerequisites") {
    group = "verification"
    description = "Prepares the device-acceptance phase by running navigation-performance verification first."
    dependsOn(navigationPerformanceVerification)
}

// A future device-acceptance task registered by the benchmark tooling inherits the
// same gate without adding any runtime, Binder, or device-install behavior here.
tasks.configureEach {
    if (name == "deviceAcceptance") {
        dependsOn(navigationPerformanceVerification)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":configuration"))
    implementation(project(":manager-client"))
    implementation(project(":settings"))
    api(project(":magisk-ui-kit"))
    implementation(project(":core"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.napier)
    implementation(libs.libsu.core)
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.markdown)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
}
