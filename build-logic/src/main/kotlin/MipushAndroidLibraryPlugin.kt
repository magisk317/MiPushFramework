import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class MipushAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.library")

        project.extensions.configure<LibraryExtension> {
            project.configureMipushAndroidCommon(this)

            defaultConfig {
                minSdk = project.versionInt("minSdk")
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
