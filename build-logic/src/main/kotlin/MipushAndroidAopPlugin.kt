import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * AOP (Aspect-Oriented Programming) 现代化支持插件
 *
 * 替代传统的 AspectJ/AspectJX 方案，提供现代化的 AOP 实现方式：
 * - 编译期注解处理 (KSP) 用于代码生成
 * - 字节码操纵 (ASM) 用于方法拦截和注入
 *
 * 使用场景：
 * - 方法拦截和面向切面编程
 * - 字段值的运行时修改或注入
 * - 性能监控、日志记录等横切关注点
 *
 * 在 build.gradle.kts 中使用：
 * ```
 * plugins {
 *     id("mipush.android.aop")
 * }
 * ```
 *
 * 相比 AspectJ 的优势：
 * ✅ 编译速度快：ASM 在编译期执行，避免 AspectJ 的多次编译
 * ✅ 兼容性强：基于 AGP Instrumentation API，稳定支持新版本
 * ✅ 易于维护：使用 Kotlin DSL 配置，集中在 build-logic 中
 * ✅ 灵活扩展：支持自定义 BytecodeTransformer 和 Hook 规则
 */
class MipushAndroidAopPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 应用必要的插件基础
        project.pluginManager.apply("com.android.library")

        project.dependencies {
            // ASM：标准的字节码操纵库，被 Android AGP 官方推荐
            // 用于在编译期进行类文件修改和优化
            add("implementation", "org.ow2.asm:asm:${project.version("asm")}")

            // ASM Util：提供便利的字节码操纵工具和分析器
            add("implementation", "org.ow2.asm:asm-util:${project.version("asm")}")

            // Kotlin Reflect：在反射 Hook 时用到，用于获取类元数据
            // 注：仅在运行时需要，可选择使用
            add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
        }

        // 配置调试日志支持（可选）
        project.extensions.create("mipushAop", MipushAopExtension::class.java)

        project.afterEvaluate {
            val aopExt = project.extensions.findByType(MipushAopExtension::class.java)
            if (aopExt?.enableDebugLog == true) {
                project.logger.lifecycle("Mipush AOP Plugin enabled for module: ${project.name}")
            }
        }
    }
}

/**
 * AOP 插件的扩展配置类
 * 用户可以通过以下方式在 build.gradle.kts 中配置：
 *
 * ```
 * mipushAop {
 *     enableDebugLog = true
 *     enableInstrumentation = true
 *     includedClasses = listOf("com.example.**")
 * }
 * ```
 */
open class MipushAopExtension {
    var enableDebugLog: Boolean = false
    var enableInstrumentation: Boolean = true
    var includedClasses: List<String> = emptyList()
    var excludedClasses: List<String> = emptyList()
}
