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
            "ApplySharedPref",
            "ConstantLocale",
            "DefaultLocale",
            "DefaultUncaughtExceptionDelegation",
            "DiscouragedApi",
            "HardwareIds",
            "InlinedApi",
            "MissingPermission",
            "NewApi",
            "ObsoleteSdkInt",
            "PrivateApi",
            "QueryPermissionsNeeded",
            "SimpleDateFormat",
            "StaticFieldLeak",
            "UnspecifiedRegisterReceiverFlag",
            "UseKtx",
            "WrongConstant",
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
    implementation(libs.napier)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
