import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.SigningConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.io.File
import java.util.Properties

class MipushAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.application")

        project.extensions.configure<ApplicationExtension> {
            compileSdk = project.versionInt("compileSdk")

            defaultConfig {
                minSdk = project.versionInt("minSdk")
                targetSdk = project.versionInt("targetSdk")
            }

            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_25
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_25
            }

            project.extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_25)
                }
            }

            signingConfigs {
                val signing = project.resolveSigningMaterial()
                getByName("debug") { applySigningMaterial(signing) }
                maybeCreate("release").apply { applySigningMaterial(signing) }
            }

            buildTypes {
                debug {
                    signingConfig = signingConfigs.getByName("debug")
                    isMinifyEnabled = false
                    isShrinkResources = false
                }
                release {
                    signingConfig = signingConfigs.getByName("release")
                    isMinifyEnabled = true
                    isShrinkResources = true
                }
            }

            packaging {
                jniLibs {
                    keepDebugSymbols += "**/*.so"
                }
            }
        }
    }

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

    private fun SigningConfig.applySigningMaterial(signing: SigningMaterial) {
        if (signing.keyStoreFile.exists()) {
            storeFile = signing.keyStoreFile
            storePassword = signing.keyStorePassword
            keyAlias = signing.keyAlias
            keyPassword = signing.keyPassword
        }
    }

    private data class SigningMaterial(
        val keyStoreFile: File,
        val keyStorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?
    )
}
