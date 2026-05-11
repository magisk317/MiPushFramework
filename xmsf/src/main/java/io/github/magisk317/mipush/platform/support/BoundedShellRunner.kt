package io.github.magisk317.mipush.platform.support

import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

enum class ShellCommandMode {
    USER,
    ROOT,
}

data class BoundedShellResult(
    val exitCode: Int,
    val stdout: List<String> = emptyList(),
    val stderr: List<String> = emptyList(),
    val timedOut: Boolean = false,
    val skipped: Boolean = false,
) {
    val isSuccess: Boolean
        get() = !timedOut && !skipped && exitCode == 0

    val stdoutText: String
        get() = stdout.joinToString("\n")

    val stderrText: String
        get() = stderr.joinToString("\n")

    companion object {
        fun skipped(reason: String): BoundedShellResult =
            BoundedShellResult(exitCode = -1, stderr = listOf(reason), skipped = true)

        fun timedOut(): BoundedShellResult =
            BoundedShellResult(exitCode = -1, stderr = listOf("timeout"), timedOut = true)

        fun failed(error: Throwable): BoundedShellResult =
            BoundedShellResult(exitCode = -1, stderr = listOf(error.message ?: error.javaClass.simpleName))
    }
}

interface BoundedShellRunner {
    fun run(
        command: String,
        mode: ShellCommandMode = ShellCommandMode.USER,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): BoundedShellResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}

object DefaultBoundedShellRunner : BoundedShellRunner {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "mipush-shell-runner").apply { isDaemon = true }
    }

    override fun run(command: String, mode: ShellCommandMode, timeoutMs: Long): BoundedShellResult {
        return when (mode) {
            ShellCommandMode.USER -> runProcess(listOf("sh", "-c", command), timeoutMs)
            ShellCommandMode.ROOT -> runLibsu(command, timeoutMs)
        }
    }

    private fun runLibsu(command: String, timeoutMs: Long): BoundedShellResult {
        val future = executor.submit(
            Callable {
                val result = Shell.cmd(command).exec()
                BoundedShellResult(
                    exitCode = result.code,
                    stdout = result.out.toList(),
                    stderr = result.err.toList(),
                )
            },
        )
        return await(future, timeoutMs)
    }

    private fun runProcess(command: List<String>, timeoutMs: Long): BoundedShellResult {
        var process: Process? = null
        return try {
            val started = ProcessBuilder(command)
                .directory(File("/"))
                .start()
            process = started
            val stdout = executor.submit(Callable { started.inputStream.bufferedReader().use { it.readLines() } })
            val stderr = executor.submit(Callable { started.errorStream.bufferedReader().use { it.readLines() } })
            val completed = started.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                started.destroyForcibly()
                BoundedShellResult.timedOut()
            } else {
                BoundedShellResult(
                    exitCode = started.exitValue(),
                    stdout = awaitLines(stdout),
                    stderr = awaitLines(stderr),
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            BoundedShellResult.failed(e)
        } catch (e: Exception) {
            BoundedShellResult.failed(e)
        } finally {
            process?.destroy()
        }
    }

    private fun await(future: Future<BoundedShellResult>, timeoutMs: Long): BoundedShellResult {
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            BoundedShellResult.timedOut()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            BoundedShellResult.failed(e)
        } catch (e: Exception) {
            BoundedShellResult.failed(e)
        }
    }

    private fun awaitLines(future: Future<List<String>>): List<String> {
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
