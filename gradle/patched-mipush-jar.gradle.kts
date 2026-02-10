import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.9.1")
    }
}

abstract class FixJarStackMapsTask : DefaultTask() {
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun rewriteStackMaps() {
        class HookMethodVisitor(mv: MethodVisitor, private val methodName: String) :
            MethodVisitor(Opcodes.ASM9, mv) {
            override fun visitCode() {
                super.visitCode()
                when (methodName) {
                    "onCreate" -> {
                        mv.visitVarInsn(Opcodes.ALOAD, 0)
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/magisk317/push/hook/ExplicitHookBridge",
                            "onServiceCreate",
                            "(Lcom/xiaomi/push/service/XMPushService;)V",
                            false
                        )
                    }

                    "onStartCommand" -> {
                        mv.visitVarInsn(Opcodes.ALOAD, 1)
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/magisk317/push/hook/ExplicitHookBridge",
                            "onStartCommand",
                            "(Landroid/content/Intent;)V",
                            false
                        )
                    }

                    "onDestroy" -> {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/magisk317/push/hook/ExplicitHookBridge",
                            "onDestroy",
                            "()V",
                            false
                        )
                    }
                }
            }
        }

        class HookClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM9, cv) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return if (name == "onCreate" || name == "onStartCommand" || name == "onDestroy") {
                    HookMethodVisitor(mv, name)
                } else {
                    mv
                }
            }
        }

        class SafeClassWriter(flags: Int) : ClassWriter(flags) {
            override fun getCommonSuperClass(type1: String, type2: String): String {
                return try {
                    super.getCommonSuperClass(type1, type2)
                } catch (_: Throwable) {
                    "java/lang/Object"
                }
            }
        }

        val input = inputJar.get().asFile
        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        JarFile(input).use { jarFile ->
            JarOutputStream(output.outputStream().buffered()).use { jarOut ->
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val newEntry = JarEntry(entry.name).apply {
                        time = entry.time
                    }
                    jarOut.putNextEntry(newEntry)
                    jarFile.getInputStream(entry).use { stream ->
                        val original = stream.readBytes()
                        val rewritten = if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            try {
                                val reader = ClassReader(original)
                                val writer =
                                    SafeClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                                if (entry.name.contains("XMPushService")) {
                                    logger.lifecycle("ASM: Found entry: ${entry.name}")
                                }
                                if (entry.name == "com/xiaomi/push/service/XMPushService.class") {
                                    logger.lifecycle("ASM: Patching target class: ${entry.name}")
                                    val hooker = HookClassVisitor(writer)
                                    reader.accept(hooker, ClassReader.EXPAND_FRAMES)
                                } else {
                                    reader.accept(writer, ClassReader.EXPAND_FRAMES)
                                }
                                writer.toByteArray()
                            } catch (_: Throwable) {
                                original
                            }
                        } else {
                            original
                        }
                        jarOut.write(rewritten)
                    }
                    jarOut.closeEntry()
                }
            }
        }
    }
}

val originalMiPushJar = rootProject.project(":mipush_hook").file("libs/miuipushsdkshared_3_7_9.jar")
val patchedMiPushJarUnpackedDir = layout.buildDirectory.dir("intermediates/patched-libs/miuipushsdkshared_3_7_9")
val patchedMiPushExcludes = listOf(
    "com/xiaomi/push/service/timers/AlarmManagerTimer.class",
    "com/xiaomi/push/service/ClientEventDispatcher.class",
    "com/xiaomi/clientreport/manager/ClientReportClient.class",
    "com/xiaomi/push/service/clientReport/PushClientReportManager.class"
)
val unpackPatchedMiPushJar = tasks.register<Sync>("unpackPatchedMiPushJar") {
    inputs.property("patchedMiPushExcludes", patchedMiPushExcludes)
    from(zipTree(originalMiPushJar)) {
        patchedMiPushExcludes.forEach { exclude(it) }
    }
    into(patchedMiPushJarUnpackedDir)
}
val repackPatchedMiPushJar = tasks.register<Zip>("repackPatchedMiPushJar") {
    dependsOn(unpackPatchedMiPushJar)
    from(patchedMiPushJarUnpackedDir)
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/patched-libs"))
    archiveFileName.set("miuipushsdkshared_3_7_9_patched.jar")
}
val fixedPatchedMiPushJar = layout.buildDirectory.file("intermediates/patched-libs/miuipushsdkshared_3_7_9_patched_fixed.jar")
val repackedPatchedMiPushJar = repackPatchedMiPushJar.flatMap { it.archiveFile }
val fixPatchedMiPushJarStackMaps = tasks.register<FixJarStackMapsTask>("fixPatchedMiPushJarStackMaps") {
    dependsOn(repackPatchedMiPushJar)
    inputJar.set(repackedPatchedMiPushJar)
    outputJar.set(fixedPatchedMiPushJar)
}

extra["fixedPatchedMiPushJar"] = fixedPatchedMiPushJar
extra["fixPatchedMiPushJarStackMaps"] = fixPatchedMiPushJarStackMaps
