package io.github.magisk317.mipush.hook.util

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class XposedShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean
        get() = !timedOut && exitCode == 0
}

internal object BoundedRootRunner {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "mipush-xposed-root-runner").apply { isDaemon = true }
    }

    fun run(command: String, timeoutMs: Long = 5_000L): XposedShellResult {
        var process: Process? = null
        return try {
            val started = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = started
            val output = executor.submit(Callable { started.inputStream.bufferedReader().use { it.readText() } })
            val completed = started.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                started.destroyForcibly()
                return XposedShellResult(exitCode = -1, stdout = "", stderr = "timeout", timedOut = true)
            }
            XposedShellResult(
                exitCode = started.exitValue(),
                stdout = runCatching { output.get(1, TimeUnit.SECONDS) }.getOrDefault(""),
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            XposedShellResult(exitCode = -1, stdout = "", stderr = e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            XposedShellResult(exitCode = -1, stdout = "", stderr = e.message ?: e.javaClass.simpleName)
        } finally {
            process?.destroy()
        }
    }
}
