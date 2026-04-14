package io.github.magisk317.mipush.platform.support

import io.github.aakira.napier.Napier
import java.io.BufferedReader
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object ShellUtils {
    private const val TAG = "ShellUtils"
    private const val PROCESS_TIMEOUT_SECONDS = 30L

    class CommandResult(
        @JvmField var result: Int,
        @JvmField var successMsg: String?,
        @JvmField var errorMsg: String?
    ) {
        fun isSuccess(): Boolean {
            if (result == 0 && isEmpty(errorMsg) && !isEmpty(successMsg)) {
                return true
            }
            if (result == 0 && isEmpty(errorMsg) && isEmpty(successMsg)) {
                return true
            }
            if (errorMsg != null) {
                if (errorMsg!!.endsWith("not found")) {
                    return false
                }
                return errorMsg!!.startsWith("BusyBox v")
            }
            return false
        }

        override fun toString(): String {
            return "$result:$successMsg,$errorMsg"
        }
    }

    @JvmStatic
    fun execCmd(command: String, isRoot: Boolean): CommandResult {
        return execCmd(arrayOf(command), isRoot, true)
    }

    @JvmStatic
    fun execCmd(commands: List<String>?, isRoot: Boolean): CommandResult {
        return execCmd(commands?.toTypedArray(), isRoot, true)
    }

    @JvmStatic
    fun execCmd(commands: Array<String>?, isRoot: Boolean): CommandResult {
        return execCmd(commands, isRoot, true)
    }

    @JvmStatic
    fun execCmd(command: String, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        return execCmd(arrayOf(command), isRoot, isNeedResultMsg)
    }

    @JvmStatic
    fun execCmd(commands: List<String>?, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        return execCmd(commands?.toTypedArray(), isRoot, isNeedResultMsg)
    }

    @JvmStatic
    fun execCmd(commands: Array<String>?, isRoot: Boolean, isNeedResultMsg: Boolean): CommandResult {
        var result = -1
        if (commands.isNullOrEmpty()) {
            return CommandResult(result, null, null)
        }

        var process: Process? = null
        var successResult: BufferedReader? = null
        var errorResult: BufferedReader? = null
        var successMsg: StringBuilder? = null
        var errorMsg: StringBuilder? = null
        var os: DataOutputStream? = null

        try {
            process = Runtime.getRuntime().exec(if (isRoot) "su" else "sh")
            os = DataOutputStream(process.outputStream)
            for (command in commands) {
                os.write(command.toByteArray())
                os.writeBytes("\n")
                os.flush()
            }
            os.writeBytes("exit\n")
            os.flush()

            // Drain stdout/stderr BEFORE waitFor to avoid deadlock on large output.
            if (isNeedResultMsg) {
                successMsg = StringBuilder()
                errorMsg = StringBuilder()
                successResult = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
                errorResult = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
                var line: String?
                while (true) {
                    line = successResult.readLine() ?: break
                    successMsg.append(line)
                }
                while (true) {
                    line = errorResult.readLine() ?: break
                    errorMsg.append(line)
                }
            }

            val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                Napier.w("Shell process timed out after ${PROCESS_TIMEOUT_SECONDS}s", tag = TAG)
                process.destroyForcibly()
            } else {
                result = process.exitValue()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Napier.e("Shell execution interrupted", e, tag = TAG)
        } catch (e: Exception) {
            Napier.e("Shell execution failed", e, tag = TAG)
        } finally {
            closeIO(os, successResult, errorResult)
            process?.destroyForcibly()
        }

        return CommandResult(result, successMsg?.toString(), errorMsg?.toString())
    }

    private fun closeIO(vararg closeables: Closeable?) {
        for (closeable in closeables) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (e: IOException) {
                    Napier.w("Failed to close IO resource", e, tag = TAG)
                }
            }
        }
    }

    private fun isEmpty(s: String?): Boolean {
        return s == null || s.trim { it <= ' ' }.isEmpty()
    }

    @JvmStatic
    fun exec(command: String): Boolean {
        val result = execCmd(command, true, true)
        return result.isSuccess()
    }

    @JvmStatic
    fun isSuAvailable(): Boolean {
        return execCmd("su --help", false, true).isSuccess()
    }
}
