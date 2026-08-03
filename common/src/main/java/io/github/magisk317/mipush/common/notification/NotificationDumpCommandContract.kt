package io.github.magisk317.mipush.common.notification

object NotificationDumpCommandContract {
    const val NOREDACT_COMMAND = "dumpsys notification --noredact"
    const val PLAIN_COMMAND = "dumpsys notification"

    fun readDump(
        runCommand: (String) -> String?,
        isUsable: (String) -> Boolean,
    ): String? {
        val preferred = usableOutput(runCommand(NOREDACT_COMMAND), isUsable)
        return preferred ?: usableOutput(runCommand(PLAIN_COMMAND), isUsable)
    }

    private fun usableOutput(
        output: String?,
        isUsable: (String) -> Boolean,
    ): String? = output?.takeIf(isUsable)
}
