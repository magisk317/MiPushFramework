import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class MipushAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.library")

        project.extensions.configure<LibraryExtension> {
            compileSdk = project.versionInt("compileSdk")

            defaultConfig {
                minSdk = project.versionInt("minSdk")
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

            buildTypes {
                debug {
                    isMinifyEnabled = false
                    isShrinkResources = false
                }
                release {
                    isMinifyEnabled = false
                    isShrinkResources = false
                }
            }

            lint {
                abortOnError = false
            }
        }
    }
}
