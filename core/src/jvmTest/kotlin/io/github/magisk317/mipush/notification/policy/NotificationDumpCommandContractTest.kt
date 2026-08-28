package io.github.magisk317.mipush.notification.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationDumpCommandContractTest {
    @Test
    fun `valid noredact dump does not execute plain fallback`() {
        val commands = mutableListOf<String>()
        val expected = validDump("noredact")

        val actual = NotificationDumpCommandContract.readDump(
            runCommand = { command ->
                commands += command
                expected
            },
            isUsable = ::isBraceChannelDump,
        )

        assertEquals(expected, actual)
        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
    }

    @Test
    fun `failed noredact dump executes plain fallback`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = NotificationDumpCommandContract.readDump(
            runCommand = { command ->
                commands += command
                if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) null else expected
            },
            isUsable = ::isBraceChannelDump,
        )

        assertEquals(expected, actual)
        assertEquals(
            listOf(
                NotificationDumpCommandContract.NOREDACT_COMMAND,
                NotificationDumpCommandContract.PLAIN_COMMAND,
            ),
            commands,
        )
    }

    @Test
    fun `invalid noredact output executes plain fallback`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = NotificationDumpCommandContract.readDump(
            runCommand = { command ->
                commands += command
                if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) {
                    "notification service unavailable"
                } else {
                    expected
                }
            },
            isUsable = ::isBraceChannelDump,
        )

        assertEquals(expected, actual)
        assertEquals(
            listOf(
                NotificationDumpCommandContract.NOREDACT_COMMAND,
                NotificationDumpCommandContract.PLAIN_COMMAND,
            ),
            commands,
        )
    }

    @Test
    fun `returns null when neither dump is usable`() {
        val commands = mutableListOf<String>()

        val actual = NotificationDumpCommandContract.readDump(
            runCommand = { command ->
                commands += command
                if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) null else ""
            },
            isUsable = ::isBraceChannelDump,
        )

        assertNull(actual)
        assertEquals(
            listOf(
                NotificationDumpCommandContract.NOREDACT_COMMAND,
                NotificationDumpCommandContract.PLAIN_COMMAND,
            ),
            commands,
        )
    }

    @Test
    fun `caller validator can accept a consumer specific format`() {
        val commands = mutableListOf<String>()
        val expected = "channelId=messages importance=3"

        val actual = NotificationDumpCommandContract.readDump(
            runCommand = { command ->
                commands += command
                expected
            },
            isUsable = { output -> output.contains("channelId=") },
        )

        assertEquals(expected, actual)
        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
    }

    private fun validDump(name: String): String =
        "NotificationChannel{id=messages name=$name importance=3}"

    private fun isBraceChannelDump(output: String): Boolean =
        output.contains("NotificationChannel{")
}
