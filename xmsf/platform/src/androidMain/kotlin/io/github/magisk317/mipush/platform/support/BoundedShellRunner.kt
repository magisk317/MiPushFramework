package io.github.magisk317.mipush.platform.support

import com.topjohnwu.superuser.Shell
import io.github.magisk317.mipush.common.process.BoundedProcessRunner
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object DefaultBoundedShellRunner : BoundedShellRunner {
    private val executor = ThreadPoolExecutor(
        0, 2, 60L, TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue(16),
        { runnable ->
            Thread(runnable, "mipush-shell-runner").apply { isDaemon = true }
        },
        BlockingRejectedExecutionHandler,
    )

    /**
     * Root-backed log export can issue more commands than the two shell workers can run at once.
     * Apply backpressure instead of dropping an export command when the bounded queue is full.
     */
    private object BlockingRejectedExecutionHandler : RejectedExecutionHandler {
        override fun rejectedExecution(
            runnable: Runnable,
            executor: ThreadPoolExecutor,
        ) {
            if (executor.isShutdown) {
                throw RejectedExecutionException("MiPush shell runner is shut down")
            }
            try {
                executor.queue.put(runnable)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RejectedExecutionException("Interrupted while queuing MiPush shell command", error)
            }
        }
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
        val result = BoundedProcessRunner.run(
            command = command,
            timeoutMillis = timeoutMs,
            workingDirectory = File("/"),
        )
        return BoundedShellResult(
            exitCode = result.exitCode,
            stdout = result.stdout.nonEmptyLines(),
            stderr = result.stderr.nonEmptyLines(),
            timedOut = result.timedOut,
        )
    }

    private fun await(future: Future<BoundedShellResult>, timeoutMs: Long): BoundedShellResult {
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            BoundedShellResult.timedOut()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            BoundedShellResult.failed(e)
        } catch (e: Exception) {
            BoundedShellResult.failed(e)
        }
    }

    private fun String.nonEmptyLines(): List<String> {
        return if (isEmpty()) emptyList() else lineSequence().toList()
    }
}
