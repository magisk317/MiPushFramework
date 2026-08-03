package io.github.magisk317.mipush.common.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class ChannelNameEnricherTest {
    @Test
    fun `enrich replaces ellipsized app settings name with effective channel name`() {
        val packageName = "com.ruanmei.ithome"
        val channel = NotificationChannel(
            "ch_com.ruanmei.ithome_118557",
            "订阅今日...",
            NotificationManager.IMPORTANCE_HIGH,
        )
        val dump = """
            AppSettings: com.ruanmei.ithome (10357) importance=NONE userSet=false
              NotificationChannel{mId='ch_com.ruanmei.ithome_118557', mName=订阅今日..., mImportance=4}
            NotificationRecord(0x123: pkg=com.ruanmei.ithome user=UserHandle{0} id=1)
              effectiveNotificationChannel=NotificationChannel{mId='ch_com.ruanmei.ithome_118557', mName=订阅今日要闻, mImportance=4}
        """.trimIndent()

        val enriched = ChannelNameEnricher.enrich(packageName, listOf(channel), dump)

        assertEquals("订阅今日要闻", enriched.single().name.toString())
    }

    @Test
    fun `enrich matches equivalent mipush channel ids`() {
        val packageName = "com.example.news"
        val channel = NotificationChannel(
            "mipush|com.example.news|breaking",
            "突发...",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val dump = """
            AppSettings: com.example.news (10001) importance=NONE userSet=false
              NotificationChannel{mId='mipush|com.example.news|breaking', mName=突发..., mImportance=3}
            NotificationRecord(0x123: pkg=com.example.news user=UserHandle{0} id=1)
              effectiveNotificationChannel=NotificationChannel{mId='ch_com.example.news_breaking', mName=突发新闻推送, mImportance=3}
        """.trimIndent()

        val enriched = ChannelNameEnricher.enrich(packageName, listOf(channel), dump)

        assertEquals("突发新闻推送", enriched.single().name.toString())
    }

    @Test
    fun `enrich resolves nms mipush id using only ch effective entry`() {
        val packageName = "com.ruanmei.ithome"
        val channel = NotificationChannel(
            "mipush|com.ruanmei.ithome|118563",
            "关注内...",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val dump = """
            AppSettings: com.ruanmei.ithome (10357) importance=DEFAULT userSet=true
              NotificationChannel{mId='ch_com.ruanmei.ithome_118563', mName=关注内..., mImportance=4}
            NotificationRecord(0x04f0: pkg=com.ruanmei.ithome user=UserHandle{0} id=2)
              effectiveNotificationChannel=NotificationChannel{mId='ch_com.ruanmei.ithome_118563', mName=关注内容更新通知, mImportance=4}
        """.trimIndent()

        val enriched = ChannelNameEnricher.enrich(packageName, listOf(channel), dump)

        assertEquals("关注内容更新通知", enriched.single().name.toString())
    }

    @Test
    fun `enrich keeps full name when dump only has ellipsized app settings`() {
        val packageName = "com.ruanmei.ithome"
        val channel = NotificationChannel(
            "ch_com.ruanmei.ithome_118557",
            "订阅今日要闻",
            NotificationManager.IMPORTANCE_HIGH,
        )
        val dump = """
            AppSettings: com.ruanmei.ithome (10357) importance=NONE userSet=false
              NotificationChannel{mId='ch_com.ruanmei.ithome_118557', mName=订阅今日..., mImportance=4}
        """.trimIndent()

        val enriched = ChannelNameEnricher.enrich(packageName, listOf(channel), dump)

        assertEquals("订阅今日要闻", enriched.single().name.toString())
    }

    @Test
    fun `enrich selects the target uid when an empty package namespace comes first`() {
        val packageName = "com.coolapk.market"
        val channel = NotificationChannel(
            "messages",
            "Message...",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val dump = """
            AppSettings: com.coolapk.market (1000)
            AppSettings: com.example.other (10400) importance=DEFAULT userSet=true
              NotificationChannel{mId='other', mName=Other, mImportance=3}
            AppSettings: com.coolapk.market (10329) importance=DEFAULT userSet=true
              NotificationChannel{mId='messages', mName=Coolapk messages, mImportance=3}
            AppSettings: com.example.trailing (10401)
        """.trimIndent()

        val enriched = ChannelNameEnricher.enrich(
            packageName = packageName,
            channels = listOf(channel),
            notificationDump = dump,
            packageUid = 10329,
        )

        assertEquals("Coolapk messages", enriched.single().name.toString())
    }

    @Test
    fun `resolveName matches equivalent managed channel ids`() {
        val names = mapOf(
            "ch_com.ruanmei.ithome_118557" to "订阅今日要闻",
        )

        val resolved = ChannelNameEnricher.resolveName(
            packageName = "com.ruanmei.ithome",
            channelId = "mipush|com.ruanmei.ithome|118557",
            namesById = names,
        )

        assertEquals("订阅今日要闻", resolved)
    }
}
