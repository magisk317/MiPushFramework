package io.github.magisk317.mipush.hook.util

import io.github.magisk317.mipush.common.process.BoundedProcessRunner

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
    fun run(command: String, timeoutMs: Long = 5_000L): XposedShellResult {
        val result = BoundedProcessRunner.run(
            command = listOf("su", "-c", command),
            timeoutMillis = timeoutMs,
            redirectErrorStream = true,
        )
        return XposedShellResult(
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            timedOut = result.timedOut,
        )
    }
}
