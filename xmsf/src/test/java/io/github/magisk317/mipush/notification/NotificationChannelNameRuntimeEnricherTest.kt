package io.github.magisk317.mipush.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.magisk317.mipush.common.notification.NotificationDumpCommandContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicInteger

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationChannelNameRuntimeEnricherTest {
    @Test
    fun `runtime dump reader does not execute plain after valid noredact`() {
        val commands = mutableListOf<String>()
        val expected = validDump("noredact")

        val actual = readNotificationServiceDump { command ->
            commands += command
            expected
        }

        assertEquals(expected, actual)
        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
    }

    @Test
    fun `runtime dump reader falls back after failed noredact`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = readNotificationServiceDump { command ->
            commands += command
            if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) null else expected
        }

        assertEquals(expected, actual)
        assertEquals(expectedCommandOrder(), commands)
    }

    @Test
    fun `runtime dump reader falls back after invalid noredact output`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = readNotificationServiceDump { command ->
            commands += command
            if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) {
                "channelId=messages importance=3"
            } else {
                expected
            }
        }

        assertEquals(expected, actual)
        assertEquals(expectedCommandOrder(), commands)
    }

    @Test
    fun `enrich leaves silent probe disabled when none is injected`() {
        val channel = NotificationChannel(
            "messages",
            "Message...",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val dumpCalls = AtomicInteger(0)
        val enricher = NotificationChannelNameRuntimeEnricher.create(
            dumpProvider = {
                dumpCalls.incrementAndGet()
                validDump("Message...")
            },
        )

        val enriched = enricher.enrich("com.example.app", listOf(channel))

        assertEquals("Message...", enriched.single().name.toString())
        assertEquals(1, dumpCalls.get())
    }

    @Test
    fun `enrich probes remaining ellipsized channels then reloads dump`() {
        val packageName = "com.ruanmei.ithome"
        val channel = NotificationChannel(
            "ch_com.ruanmei.ithome_118566",
            "热点新...",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val firstDump = """
            AppSettings: com.ruanmei.ithome (10357) importance=DEFAULT userSet=true
              NotificationChannel{mId='ch_com.ruanmei.ithome_118566', mName=热点新..., mImportance=3}
        """.trimIndent()
        val secondDump = """
            AppSettings: com.ruanmei.ithome (10357) importance=DEFAULT userSet=true
              NotificationChannel{mId='ch_com.ruanmei.ithome_118566', mName=热点新..., mImportance=3}
            NotificationRecord(0x1: pkg=com.ruanmei.ithome user=UserHandle{0} id=1)
              effectiveNotificationChannel=NotificationChannel{mId='ch_com.ruanmei.ithome_118566', mName=热点新闻通知, mImportance=3}
        """.trimIndent()

        val dumps = ArrayDeque(listOf(firstDump, secondDump))
        val probeCalls = AtomicInteger(0)
        val enricher = NotificationChannelNameRuntimeEnricher.create(
            dumpProvider = { dumps.removeFirstOrNull() },
            dumpTtlMillis = 0L,
            probeSettleMillis = 0L,
            sleeper = {},
            channelProber = ChannelNameProber { pkg, ids, userId ->
                probeCalls.incrementAndGet()
                assertEquals(packageName, pkg)
                assertEquals(listOf("ch_com.ruanmei.ithome_118566"), ids)
                assertEquals(999, userId)
                true
            },
        )

        val enriched = enricher.enrich(
            packageName,
            listOf(channel),
            packageUid = 999 * 100_000 + 10_357,
        )

        assertEquals(1, probeCalls.get())
        assertEquals("热点新闻通知", enriched.single().name.toString())
        assertTrue(dumps.isEmpty())
    }

    private fun expectedCommandOrder(): List<String> = listOf(
        NotificationDumpCommandContract.NOREDACT_COMMAND,
        NotificationDumpCommandContract.PLAIN_COMMAND,
    )

    private fun validDump(name: String): String =
        "NotificationChannel{id=messages name=$name importance=3}"
}
