pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.greenrobot.greendao") {
                useModule("org.greenrobot:greendao-gradle-plugin:${requested.version}")
            }
            if (requested.id.id == "io.github.wurensen.android-aspectjx") {
                useModule("io.github.wurensen:gradle-android-plugin-aspectjx:${requested.version}")
            }
        }
    }
}

buildscript {
    val securityOverrides: Map<String, String> = run {
        val propsFile = file("gradle/security-overrides.properties")
        if (!propsFile.exists()) {
            emptyMap()
        } else {
            val props = java.util.Properties()
            propsFile.reader().use { reader -> props.load(reader) }
            props.stringPropertyNames().associateWith { name -> props.getProperty(name) }
        }
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val key = "${requested.group}:${requested.name}"
            val forcedVersion = securityOverrides[key]
            if (!forcedVersion.isNullOrBlank() && requested.version != forcedVersion) {
                useVersion(forcedVersion)
                because("Security override from gradle/security-overrides.properties")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

includeBuild("build-logic")

rootProject.name = "MiPushFramework"
include(":condom", ":push", ":common", ":runtime-core", ":magisk-ui-kit")
project(":magisk-ui-kit").projectDir = file("../magisk-ui-kit")
