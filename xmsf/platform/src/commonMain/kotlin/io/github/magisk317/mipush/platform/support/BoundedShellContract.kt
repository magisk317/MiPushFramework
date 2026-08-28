package io.github.magisk317.mipush.platform.support

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
            BoundedShellResult(exitCode = -1, stderr = listOf(error.message ?: error::class.simpleName.orEmpty()))
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
