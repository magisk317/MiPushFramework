package com.xiaomi.push.service.heartbeat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gate-matrix tests for the MdKa availability chain (stock 7.5.79-C e.h), kept pure by
 * driving [MdKaGate.evaluate] directly.
 */
class MdKaGateTest {

    private fun available(mutate: (MdKaGate.State) -> MdKaGate.State = { it }): Boolean {
        return MdKaGate.evaluate(mutate(MdKaGate.State())).first
    }

    private fun reason(mutate: (MdKaGate.State) -> MdKaGate.State = { it }): String {
        return MdKaGate.evaluate(mutate(MdKaGate.State())).second
    }

    @Test
    fun `all green means available`() {
        assertTrue(available())
    }

    @Test
    fun `unsupported hardware fails first`() {
        assertFalse(available { it.copy(supported = false) })
        assertEquals("not supported", reason { it.copy(supported = false) })
    }

    @Test
    fun `closed socket fails`() {
        assertFalse(available { it.copy(socketOpen = false) })
        assertEquals("socket is closed", reason { it.copy(socketOpen = false) })
    }

    @Test
    fun `fail too much fails`() {
        assertFalse(available { it.copy(failTooMuch = true) })
        assertEquals("fail too much", reason { it.copy(failTooMuch = true) })
    }

    @Test
    fun `fall down fails`() {
        assertFalse(available { it.copy(fallDown = true) })
        assertEquals("fall down", reason { it.copy(fallDown = true) })
    }

    @Test
    fun `cloud switch off fails`() {
        assertFalse(available { it.copy(cloudEnabled = false) })
        assertEquals("cloud config is not enable", reason { it.copy(cloudEnabled = false) })
    }

    @Test
    fun `mobile disconnected fails`() {
        assertFalse(available { it.copy(mobileConnected = false) })
        assertEquals("mobile network is disconnected", reason { it.copy(mobileConnected = false) })
    }

    @Test
    fun `super power mode fails`() {
        assertFalse(available { it.copy(superPowerMode = true) })
        assertEquals("super power mode enable", reason { it.copy(superPowerMode = true) })
    }

    @Test
    fun `second data card needs feature version`() {
        assertTrue(available { it.copy(dataCardPhoneId = 1, featureVersionAtLeast1 = true) })
        assertFalse(available { it.copy(dataCardPhoneId = 1, featureVersionAtLeast1 = false) })
        assertEquals(
            "data card not supported",
            reason { it.copy(dataCardPhoneId = 1, featureVersionAtLeast1 = false) },
        )
    }

    @Test
    fun `no data card fails`() {
        assertFalse(available { it.copy(dataCardPhoneId = -1) })
        assertEquals("data card not supported", reason { it.copy(dataCardPhoneId = -1) })
    }

    @Test
    fun `first data card is always eligible`() {
        assertTrue(available { it.copy(dataCardPhoneId = 0, featureVersionAtLeast1 = false) })
    }

    @Test
    fun `gate order matches stock e h`() {
        // Stock checks supported → socket → failTooMuch → fallDown → cloud → mobile →
        // superPower → dataCard; verify two competing failures report the earlier one.
        assertEquals(
            "socket is closed",
            reason {
                it.copy(socketOpen = false, cloudEnabled = false, superPowerMode = true)
            },
        )
    }
}
