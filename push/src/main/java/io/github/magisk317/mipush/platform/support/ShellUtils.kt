package io.github.magisk317.mipush.platform.support

import java.io.BufferedReader
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ShellUtils {
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

            result = process.waitFor()
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
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            closeIO(os, successResult, errorResult)
            process?.destroy()
        }

        return CommandResult(result, successMsg?.toString(), errorMsg?.toString())
    }

    private fun closeIO(vararg closeables: Closeable?) {
        for (closeable in closeables) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (e: IOException) {
                    e.printStackTrace()
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
