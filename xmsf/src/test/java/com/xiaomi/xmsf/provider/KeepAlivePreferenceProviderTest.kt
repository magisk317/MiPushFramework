package com.xiaomi.xmsf.provider

import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeepAlivePreferenceProviderTest {
    private val provider = KeepAlivePreferenceProvider()

    @Test
    fun `missing selection requests all flags`() {
        assertEquals(
            listOf(
                KEEPALIVE_PREF_OOM_ADJ,
                KEEPALIVE_PREF_ANTI_KILL,
                KEEPALIVE_PREF_STANDBY_BYPASS,
                KEEPALIVE_PREF_DOZE_BYPASS,
            ),
            provider.requestedKeys(null),
        )
    }

    @Test
    fun `unknown selection does not widen into a full read`() {
        assertEquals(
            emptyList<String>(),
            provider.requestedKeys(arrayOf("unknown_keepalive_flag")),
        )
    }

    @Test
    fun `known selection preserves requested order`() {
        assertEquals(
            listOf(KEEPALIVE_PREF_ANTI_KILL, KEEPALIVE_PREF_OOM_ADJ),
            provider.requestedKeys(arrayOf(KEEPALIVE_PREF_ANTI_KILL, KEEPALIVE_PREF_OOM_ADJ)),
        )
    }
}
