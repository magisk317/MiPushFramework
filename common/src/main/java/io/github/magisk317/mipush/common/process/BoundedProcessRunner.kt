package io.github.magisk317.mipush.common.process

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

data class BoundedProcessResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
) {
    val isSuccess: Boolean
        get() = !timedOut && exitCode == 0
}

object BoundedProcessRunner {
    const val DEFAULT_TIMEOUT_MILLIS = 8_000L
    const val DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024
    private const val TERMINATION_GRACE_MILLIS = 1_000L
    private const val STREAM_BUFFER_BYTES = 8 * 1024

    private val streamExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "mipush-process-stream").apply { isDaemon = true }
    }

    fun run(
        command: List<String>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        standardInput: String? = null,
        workingDirectory: File? = null,
        redirectErrorStream: Boolean = false,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): BoundedProcessResult {
        if (command.isEmpty()) return failed("command is empty")
        if (timeoutMillis <= 0) return failed("timeout must be positive")
        if (maxOutputBytes <= 0) return failed("max output bytes must be positive")

        var process: Process? = null
        var stdout: Future<CapturedOutput>? = null
        var stderr: Future<CapturedOutput>? = null
        return try {
            val started = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(redirectErrorStream)
                .start()
            process = started
            stdout = readAsync(started.inputStream, maxOutputBytes)
            if (!redirectErrorStream) {
                stderr = readAsync(started.errorStream, maxOutputBytes)
            }
            if (standardInput != null) {
                started.outputStream.bufferedWriter().use { writer ->
                    writer.write(standardInput)
                    writer.flush()
                }
            } else {
                started.outputStream.close()
            }

            if (!started.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                started.destroyForcibly()
                started.waitFor(TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
                runCatching { started.inputStream.close() }
                runCatching { started.errorStream.close() }
                runCatching { started.outputStream.close() }
                stdout.cancel(true)
                stderr?.cancel(true)
                BoundedProcessResult(exitCode = -1, stderr = "timeout", timedOut = true)
            } else {
                val capturedStdout = awaitOutput(stdout)
                val capturedStderr = awaitOutput(stderr)
                BoundedProcessResult(
                    exitCode = started.exitValue(),
                    stdout = capturedStdout.text,
                    stderr = capturedStderr.text,
                    stdoutTruncated = capturedStdout.truncated,
                    stderrTruncated = capturedStderr.truncated,
                )
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            failed(error.message ?: error.javaClass.simpleName)
        } catch (error: IOException) {
            failed(error.message ?: error.javaClass.simpleName)
        } catch (error: SecurityException) {
            failed(error.message ?: error.javaClass.simpleName)
        } catch (error: RejectedExecutionException) {
            failed(error.message ?: error.javaClass.simpleName)
        } catch (error: IllegalArgumentException) {
            failed(error.message ?: error.javaClass.simpleName)
        } finally {
            process?.destroy()
        }
    }

    private fun readAsync(stream: InputStream, maxOutputBytes: Int): Future<CapturedOutput> {
        return streamExecutor.submit(Callable { drain(stream, maxOutputBytes) })
    }

    private fun drain(stream: InputStream, maxOutputBytes: Int): CapturedOutput {
        return stream.use {
            val output = ByteArrayOutputStream(minOf(maxOutputBytes, STREAM_BUFFER_BYTES))
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var truncated = false
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                val remaining = maxOutputBytes - output.size()
                if (remaining > 0) {
                    output.write(buffer, 0, minOf(count, remaining))
                }
                if (count > remaining) truncated = true
            }
            CapturedOutput(output.toString(Charsets.UTF_8.name()), truncated)
        }
    }

    private fun awaitOutput(future: Future<CapturedOutput>?): CapturedOutput {
        if (future == null) return CapturedOutput.EMPTY
        return runCatching { future.get(1, TimeUnit.SECONDS) }.getOrDefault(CapturedOutput.EMPTY)
    }

    private fun failed(message: String): BoundedProcessResult {
        return BoundedProcessResult(exitCode = -1, stderr = message)
    }

    private data class CapturedOutput(val text: String, val truncated: Boolean) {
        companion object {
            val EMPTY = CapturedOutput("", false)
        }
    }
}
