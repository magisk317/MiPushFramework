import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import com.google.devtools.ksp.gradle.KspExtension

class MipushAndroidRoomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.google.devtools.ksp")

        project.extensions.configure<KspExtension> {
            arg("room.schemaLocation", "${project.projectDir}/schemas")
            arg("room.incremental", "true")
        }

        project.dependencies {
            add("implementation", "androidx.room:room-runtime:${project.version("room")}")
            add("implementation", "androidx.room:room-ktx:${project.version("room")}")
            add("ksp", "androidx.room:room-compiler:${project.version("room")}")
        }
    }
}
