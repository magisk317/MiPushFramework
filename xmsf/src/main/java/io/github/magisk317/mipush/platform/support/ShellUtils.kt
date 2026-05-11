package io.github.magisk317.mipush.platform.support

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
        if (commands.isNullOrEmpty()) {
            return CommandResult(-1, null, null)
        }

        val command = commands.joinToString("\n")
        val shellResult = if (isRoot) {
            AppRootAccessFacade.runRootCommand(command)
        } else {
            AppRootAccessFacade.runShellCommand(command)
        }
        return CommandResult(
            shellResult.exitCode,
            if (isNeedResultMsg) shellResult.stdoutText else null,
            if (isNeedResultMsg) shellResult.stderrText else null,
        )
    }

    private fun isEmpty(s: String?): Boolean {
        return s == null || s.trim { it <= ' ' }.isEmpty()
    }

    @JvmStatic
    fun exec(command: String): Boolean {
        return AppRootAccessFacade.runRootCommand(command).isSuccess
    }

    @JvmStatic
    fun isSuAvailable(): Boolean {
        return AppRootAccessFacade
            .runShellCommand("command -v su >/dev/null 2>&1 || su --help >/dev/null 2>&1")
            .isSuccess
    }
}
